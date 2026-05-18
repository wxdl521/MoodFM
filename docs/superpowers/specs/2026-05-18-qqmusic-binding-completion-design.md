# QQ 音乐绑定功能全面补全设计

**日期：** 2026-05-18  
**方案：** B — 务实降级 + 绑定体验完善  
**状态：** 已审核通过

---

## 背景

MoodFM 已实现 QQ 音乐平台绑定的骨架代码（entity、service、controller、adapter），但存在以下关键缺口：

| 缺口 | 严重程度 |
|------|---------|
| `qqmusic/song/url` 是空 stub，歌曲无法播放 | 致命 |
| QR 扫码 Cookie 提取有时失败（v4.x 已知 bug） | 高 |
| 手机号绑定后端接口未实现 | 中 |
| Cookie 绑定时无有效性校验 | 中 |
| Cookie 无过期检测机制 | 中 |
| 歌单接口全是 stub | 低 |

---

## 架构概览

改动涉及三层，互相独立：

```
前端 (Vue)            后端 (Spring Boot)          适配器 (Node.js)
──────────────────    ────────────────────────    ──────────────────────────
Phone tab 接通    →   手机号绑定 2 个接口      →  QQ ptlogin 手机 SMS 流程
播放器来源标注    ←   PlayerServiceImpl fallback ← QQ vkey URL 接口（新增）
Cookie 过期展示   ←   定时过期检测任务             QR Cookie 提取修复
                      Cookie 绑定时校验         ←  /user/profile 校验接口
                                               →  歌单接口补全（playlists/tracks/lyric）
```

**核心约定：**
- 播放 URL fallback 由 `PlayerServiceImpl` 处理，其他层不感知平台切换
- Fallback 结果带 `source` 字段（`qqmusic` / `netease_fallback`）透传到前端
- Cookie 加密/解密逻辑不变（AES-256-GCM），只新增校验步骤

---

## Section 1：适配器层（qqmusic.js）

### 1.1 播放 URL — 实现 `GET /qqmusic/song/url`

使用 QQ 音乐 `musicu.fcg` 多路复用接口获取 vkey：

```
POST https://u.y.qq.com/cgi-bin/musicu.fcg
body: {
  req_0: {
    module: "vkey.GetVkeyServer",
    method: "CgiGetVkey",
    param: { guid, songmid: [songId], songtype: [0], uin, loginflag: 1, platform: "20" }
  }
}
```

返回 `sip`（CDN 域名列表）+ `vkey`，拼接播放 URL：
```
https://{sip[0]}{filename}?vkey={vkey}&guid={guid}&uin={uin}&fromtag=66
```

- `uin` 从 Cookie 中提取（`uin=oXXXXXXXX` 字段，去掉前缀 `o`）
- `guid` 为随机生成的设备 ID（UUID 去连字符）
- `filename` 格式：`M500{songmid}.mp3`（普通品质）或 `M800{songmid}.mp3`（高品质）
- 免费账号可获取 128kbps/320kbps，无需 VIP
- 接口失败时返回 `{ url: null }`，触发后端 fallback

### 1.2 QR 码 Cookie 提取修复

803 状态（扫码成功）时，从 `ptlogin2` HTTP 响应手动提取 `Set-Cookie` 头，合并字段：
```
uin, skey, p_uin, p_skey, pt4_token, supertoken, ptcz, qrsig
```

不再依赖 v4.x 库的 cookie 解析逻辑，改为直接操作响应头字段。

### 1.3 手机号登录（新增两个端点）

**`POST /qqmusic/phone/code`**
- 参数：`{ phone }`
- 调用 QQ ptlogin2 发送 SMS 验证码
- 返回：`{ success: true, ticket }` （ticket 用于后续验证步骤）

**`POST /qqmusic/phone/verify`**
- 参数：`{ phone, code, ticket }`
- 验证码换 Cookie，成功后返回与 QR 803 状态相同格式：
  ```json
  { "status": 803, "cookie": "...", "username": "..." }
  ```

### 1.4 歌单接口补全

| 端点 | QQ 音乐 API | 认证 |
|------|------------|------|
| `GET /qqmusic/user/playlists` | `fcg_get_profile_homepage.fcg` | 需要 Cookie |
| `GET /qqmusic/playlist/tracks` | `fcg_v8_playlist_cp.fcg` | 需要 Cookie |
| `GET /qqmusic/lyric` | `fcg_query_lyric_new.fcg` | 公开 |

### 1.5 Cookie 轻量校验接口（新增）

**`GET /qqmusic/user/profile`**
- 调用 `fcg_get_profile_homepage.fcg` 获取用户信息
- 成功返回 `{ valid: true, username: "..." }`
- 失败（Cookie 无效/过期）返回 `{ valid: false }`

---

## Section 2：后端层

### 2.1 播放 URL Fallback — 修改 `PlayerServiceImpl`

```java
// PlayerServiceImpl.getSongUrlWithFallback(song, platform, cookie)
SongUrlResult result = musicApiClient.getSongUrl(song.getPlatformId(), platform, cookie);

if (result.getUrl() == null && platform.equals("qqmusic")) {
    // 搜索网易云同名歌曲
    String query = song.getTitle() + " " + song.getArtist();
    List<SongDto> hits = musicApiClient.searchSongs(query, "netease", null);
    if (!hits.isEmpty()) {
        String neteaseUrl = musicApiClient.getSongUrl(hits.get(0).getPlatformId(), "netease", null);
        return new SongUrlResult(neteaseUrl, "netease_fallback");
    }
}
return new SongUrlResult(result.getUrl(), "qqmusic");
```

`SongUrlResult` 新增 `source` 字段（`"qqmusic"` 或 `"netease_fallback"`），透传到播放接口响应体。

### 2.2 手机号绑定接口 — 新增到 `PlatformBindingController`

**`POST /api/platforms/{platform}/phone/code`**
- 请求：`{ "phone": "13800138000" }`
- 调用 `MusicApiClient.sendPhoneCode(platform, phone)`
- 返回：`{ "ticket": "xxx" }`

**`POST /api/platforms/{platform}/bind/phone`**
- 请求：`{ "phone": "...", "code": "...", "ticket": "..." }`
- 调用 `MusicApiClient.verifyPhoneCode(platform, phone, code, ticket)`
- 获取 cookie + username 后走现有 `saveBinding()` 路径
- 成功返回绑定信息

### 2.3 Cookie 绑定时校验 — 修改 `PlatformBindingServiceImpl.bindByCookie()`

```java
// 绑定前验证
String username = musicApiClient.validateCookie(platform, cookie);
if (username == null) {
    throw new BusinessException(ResultCode.COOKIE_INVALID); // 新增错误码
}
// 校验通过，写入 platformUsername，再保存
saveBinding(userId, platform, cookie, username);
```

### 2.4 Cookie 过期检测定时任务 — 新增 `CookieExpiryChecker`

```java
@Component
public class CookieExpiryChecker {

    @Scheduled(cron = "0 0 */6 * * *")  // 每 6 小时
    public void checkAllBindings() {
        List<PlatformBinding> bindings = platformBindingMapper.findAllValid();
        for (PlatformBinding b : bindings) {
            String decryptedCookie = AesUtil.decrypt(b.getCookieEncrypted());
            String username = musicApiClient.validateCookie(b.getPlatform(), decryptedCookie);
            if (username == null) {
                platformBindingMapper.markInvalid(b.getId());
            }
            platformBindingMapper.updateLastValidated(b.getId());
        }
    }
}
```

QQ 音乐 Cookie 无法自动续期，检测到失效后标记 `is_valid=0`，用户须手动重绑。

### 2.5 `MusicApiClient` 新增方法

```java
String sendPhoneCode(String platform, String phone);
CookieResult verifyPhoneCode(String platform, String phone, String code, String ticket);
String validateCookie(String platform, String cookie); // 返回 username 或 null
```

### 2.6 新增错误码 — `ResultCode`

```java
COOKIE_INVALID(40031, "Cookie 无效或已过期，请重新获取"),
PHONE_CODE_SEND_FAILED(40032, "短信验证码发送失败"),
PHONE_CODE_VERIFY_FAILED(40033, "短信验证码错误或已过期"),
```

---

## Section 3：前端层

### 3.1 Phone 标签页接通 — `Bind.vue`

- `sendPhoneCode()` 接通 `POST /api/platforms/{platform}/phone/code`
- `bindPhone()` 接通 `POST /api/platforms/{platform}/bind/phone`，传递 `ticket` 字段
- `platform.ts` 已有方法签名，只需补全请求体和响应处理

### 3.2 Cookie 过期时间展示

**`Bind.vue`（绑定卡片）：**
- 已绑定状态下显示过期时间（`expiresAt` 字段）
- `is_valid === 0`：红色警告 + "已过期，请重新绑定" + 重绑按钮
- `expiresAt` 在 7 天内：黄色警告 "Cookie 将于 X 天后过期"

**`settings/Platforms.vue`（设置页）：**
- 绑定列表项显示有效状态图标（绿色/黄色/红色）

### 3.3 播放器来源标注

当 `source === 'netease_fallback'` 时，在播放器封面右下角显示：
```
┌──────────────────┐
│                  │
│   [封面图片]    │
│             ┌──┐│
│             │网易││
│             └──┘│
└──────────────────┘
```
- 样式：`position: absolute; bottom: 4px; right: 4px` 的半透明小圆角标签
- 文字："via 网易云"，字号 10px，不影响主体布局
- 当 `source === 'qqmusic'` 时标签不显示

---

## 错误处理

| 场景 | 用户提示 |
|------|---------|
| QQ vkey 接口返回失败 | 静默 fallback 到网易云，播放器显示来源标签 |
| 网易云 fallback 也失败 | 提示"该歌曲暂时无法播放" |
| Cookie 校验失败（绑定时）| 提示"Cookie 无效，请重新获取" |
| 手机验证码发送失败 | 提示具体错误，支持重新发送 |
| Cookie 过期（定时检测）| 设置页显示红色过期状态 |

---

## 测试要点

- [ ] QQ vkey 接口：有效 Cookie 下能返回可播放 URL
- [ ] Fallback：vkey 失败后自动切换网易云，播放器显示来源标签
- [ ] QR 扫码：803 状态下 Cookie 正确提取，不再依赖库解析
- [ ] 手机号绑定：完整发码→验证→保存流程
- [ ] Cookie 校验：无效 Cookie 被拒绝，有效 Cookie 保存并写入用户名
- [ ] 定时任务：过期 Cookie 被标记 `is_valid=0`，设置页反映状态
- [ ] 歌单接口：有效 Cookie 下能获取歌单列表和曲目

---

## 实施优先级

| 优先级 | 任务 | 层 |
|--------|------|----|
| P0 | 实现 `song/url` vkey 接口 | 适配器 |
| P0 | 后端播放 URL fallback 逻辑 | 后端 |
| P1 | 修复 QR Cookie 提取 | 适配器 |
| P1 | 手机号绑定完整链路 | 适配器 + 后端 + 前端 |
| P1 | Cookie 绑定时有效性校验 | 适配器 + 后端 |
| P2 | Cookie 过期检测定时任务 | 后端 |
| P2 | 前端 Cookie 过期展示 | 前端 |
| P2 | 播放器来源标注 | 前端 |
| P3 | 歌单/歌词接口补全 | 适配器 |
