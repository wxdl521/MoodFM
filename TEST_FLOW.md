# MoodFM 当前实现功能测试流程

本文档只依据当前代码中已经实现的功能编写。`Front-end styles/` 仅作为前端设计稿，不纳入功能测试范围；`MoodFM_PRD_v2.0.md` 中尚未落地的功能不作为通过标准。

## 1. 测试范围

### 纳入测试

- 前端实际路由：`/`、`/auth`、`/onboarding`、`/bind`、`/home`、`/player`、`/insights`、`/calendar`、`/weekly/:id`。
- 后端实际接口：健康检查、注册登录、Token 刷新与登出、用户信息、偏好保存、音乐平台绑定、AI 电台启动、下一批歌曲、歌曲播放 URL、洞察统计、心情日历、周报生成/列表/详情。
- 音乐适配器实际路由：`/health`、`/netease/**`、`/qqmusic/**`。
- 数据库表：`users`、`user_profiles`、`platform_bindings`、`mood_sessions`、`songs`、`play_records`、`feedback_events`、`weekly_reports`。

### 不纳入通过标准

- `Front-end styles/` 下的页面和组件。
- PRD 中但当前后端未实现的功能。
- 前端已写但后端未实现的接口：
  - `GET /api/radio/sessions`
  - `POST /api/radio/feedback`
  - `GET /api/playlists/{id}`
  - `POST /api/radio/play-from-playlist`
  - `POST /api/platforms/{platform}/phone/code`
  - `POST /api/platforms/{platform}/bind/phone`
- Bilibili/Spotify 等未实现平台。

## 2. 环境准备

### 推荐本地联调方式

1. 启动基础依赖和音乐适配器：

   ```bash
   docker compose up -d mysql redis music-adapter
   ```

2. 启动后端：

   ```bash
   cd moodfm-backend
   mvn spring-boot:run
   ```

3. 启动前端：

   ```bash
   cd moodfm-frontend
   npm run dev
   ```

4. 访问地址：

   - 前端：`http://localhost:5173`
   - 后端健康检查：`http://localhost:8081/api/health`
   - 音乐适配器健康检查：`http://localhost:3000/health`
   - Swagger：`http://localhost:8081/docs`

### 环境前置检查

- `.env` 至少准备：`JWT_SECRET`、`COOKIE_ENCRYPTION_KEY`、`MYSQL_USER`、`MYSQL_PASSWORD`、`LLM_API_KEY`。
- 如果不配置有效 `LLM_API_KEY`，心情分析和周报文案会走默认/兜底逻辑，电台仍可测试基本链路。
- 如果使用完整 `docker compose up` 测试前端容器，需要先关注一个当前配置风险：前端 `nginx.conf` 代理到 `backend:8080`，但后端配置端口是 `8081`。本地 Vite 代理是正确的，建议优先用本地联调方式。

## 3. 冒烟测试

| 编号 | 操作 | 期望结果 |
|---|---|---|
| S-01 | 打开 `/` | Landing 页面正常渲染，登录/开始按钮可跳转 `/auth` |
| S-02 | 未登录直接访问 `/home`、`/bind`、`/insights` | 被重定向到 `/auth` |
| S-03 | 请求 `GET /api/health` | 返回 `status=ok`、`service=moodfm-backend` |
| S-04 | 请求 `GET http://localhost:3000/health` | 返回 `status=ok`、`service=moodfm-music-adapter` |
| S-05 | 前端执行 `npm run build` | 构建成功，无阻断错误 |
| S-06 | 后端执行 `mvn test` 或 `mvn -q test` | 编译与测试阶段通过 |

## 4. 认证与账号测试

### 注册

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| A-01 | 邮箱注册成功 | `/auth?mode=signup` 输入昵称、邮箱、8 位以上含字母数字密码 | 注册成功，前端保存 `moodfm_token` 和 `moodfm_user`，跳转 `/onboarding` |
| A-02 | 重复邮箱 | 使用 A-01 的邮箱再次注册 | 返回业务错误，提示账号已存在 |
| A-03 | 用户名边界 | 用户名 1 位、21 位分别注册 | 返回参数校验错误 |
| A-04 | 邮箱格式 | 输入非法邮箱 | 返回参数校验错误 |
| A-05 | 密码格式 | 输入少于 8 位或不含数字/字母的密码 | 返回参数校验错误 |
| A-06 | 邮箱/手机号缺失 | 直接调用注册接口，仅传 username/password | 返回“邮箱或手机号至少填一个”类错误 |

### 登录、刷新、登出

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| A-07 | 登录成功 | 用已注册邮箱和密码登录 | 返回 accessToken、refreshToken、user，跳转 `/home` |
| A-08 | 错误密码 | 连续输错密码 | 返回密码错误；连续 5 次后账号进入 15 分钟锁定 |
| A-09 | 获取当前用户 | 携带 token 请求 `GET /api/users/me` | 返回当前用户 id、username、email/phone |
| A-10 | 刷新 Token | 调用 `POST /api/auth/refresh?refreshToken=xxx` | 返回新的 accessToken 和 refreshToken；旧 refreshToken 失效 |
| A-11 | 登出 | 调用 `POST /api/auth/logout` 或前端退出 | accessToken 被加入 Redis 黑名单；再次访问受保护接口应失败 |
| A-12 | 无 Token 权限 | 不带 Authorization 请求 `/api/users/me` | 被安全拦截，不能返回用户数据 |

## 5. 新手引导与偏好测试

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| O-01 | 引导页访问 | 登录后访问 `/onboarding` | 4 步引导可正常切换 |
| O-02 | 绑定页跳转 | 第 2 步点击平台绑定 | 跳转 `/bind?returnTo=onboarding` |
| O-03 | 偏好保存 | 第 3 步选择 genres/languages/defaultScene 后下一步 | 前端写入 `localStorage.moodfm_prefs`，后端 `user_profiles` 写入 genre/language JSON |
| O-04 | 偏好更新 | 修改偏好后再次保存 | `user_profiles` 原记录被更新而非重复插入 |

说明：当前后端 `savePreferences` 只持久化 genres 和 languages，`defaultScene` 暂未入库，测试时记录为当前实现限制。

## 6. 音乐平台绑定测试

### 绑定列表与 Cookie 绑定

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| P-01 | 初始列表 | 登录后请求 `GET /api/platforms` | 新用户返回空数组 |
| P-02 | Cookie 绑定网易云 | 在 `/bind` 选择 NetEase，Cookie 方式输入非空 Cookie | 返回成功，列表出现 `netease`，第一条绑定自动设为默认 |
| P-03 | Cookie 绑定 QQ 音乐 | 选择 QQ Music，Cookie 方式输入非空 Cookie | 返回成功，列表出现 `qqmusic` |
| P-04 | 设置默认平台 | 调用 `PUT /api/platforms/{platform}/default` | 目标平台 `isDefault=true`，其他平台 `isDefault=false` |
| P-05 | 解绑平台 | 调用 `DELETE /api/platforms/{platform}` | 绑定记录被删除，列表不再显示 |

当前后端 Cookie 绑定只做加密保存，不校验 Cookie 是否真实有效。因此功能测试可用假 Cookie 验证保存链路，真实播放 URL 测试需使用有效平台 Cookie。

### QR 绑定

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| P-06 | 生成 QR | 调用 `POST /api/platforms/netease/qr/generate` 或 `qqmusic` | 返回 `key`、`qrimg`/`qrurl` |
| P-07 | 轮询状态 | 用 key 调用 `GET /api/platforms/{platform}/qr/status?key=xxx` | 返回 waiting/scanned/success/expired/error 中一种状态 |
| P-08 | QR 过期 | 等待超时后继续轮询 | 返回 expired 或可重新生成 |
| P-09 | QR 成功后列表 | 扫码确认成功后刷新绑定列表 | 对应平台出现在绑定列表中 |

说明：网易云适配器中已处理扫码成功但 Cookie 取不到的情况，可能返回 error 并提示改用 Cookie 方式。这属于当前实现允许的降级结果。

## 7. 音乐适配器测试

| 编号 | 接口 | 步骤 | 期望结果 |
|---|---|---|---|
| M-01 | `GET /health` | 请求音乐适配器健康检查 | 返回 ok |
| M-02 | `GET /netease/search?keywords=xxx&limit=5` | 不带 Cookie 搜索歌曲 | 返回 `code=200` 和 `songs` 数组 |
| M-03 | `GET /netease/user/liked-songs` | 不带 `X-Cookie` | 返回 401 或 cookie required |
| M-04 | `GET /netease/recommend/songs` | 带有效 `X-Cookie` | 返回每日推荐歌曲数组；无效 Cookie 可失败 |
| M-05 | `GET /netease/song/url?id=xxx` | 带有效 `X-Cookie` | 返回 `data[0].url`，若版权/账号限制则可能为空 |
| M-06 | `GET /qqmusic/search?keywords=xxx&limit=5` | 搜索 QQ 音乐 | 返回 `code=200`；外部接口失败时允许空数组 |
| M-07 | QQ liked/recommend/song URL | 带任意 Cookie 调用 | 当前实现多为占位空结果，记录为限制，不作为失败 |

## 8. AI 电台与播放器测试

### 后端接口主流程

前置条件：

- 用户已登录。
- 至少绑定一个有效平台，推荐先绑定 `netease`。
- 音乐适配器可访问。

| 编号 | 测试点 | 请求/操作 | 期望结果 |
|---|---|---|---|
| R-01 | 文本启动电台 | `POST /api/radio/start`，body: `{"text":"今天加班很累，想听放松的音乐","durationMinutes":30}` | 返回 `sessionId`、`scene`、`moodSummary`、`songs`、`totalCount` |
| R-02 | 场景启动电台 | body: `{"scene":"study","durationMinutes":30}` | 返回可播放队列；无 LLM 时使用默认心情兜底 |
| R-03 | 心情坐标启动 | body: `{"valence":0.7,"energy":0.4,"durationMinutes":30}` | 参数合法，生成队列 |
| R-04 | 参数边界 | `durationMinutes=9/121`、`valence=-0.1/1.1`、`energy=-0.1/1.1` | 返回参数校验错误 |
| R-05 | 未绑定平台启动 | 新用户不绑定平台直接启动 | 返回平台未绑定类错误 |
| R-06 | 获取下一批 | `GET /api/radio/next?sessionId=xxx` | 返回 Redis 队列前 5 首；无缓存返回空数组 |
| R-07 | 获取播放 URL | `GET /api/radio/url?platform=netease&songId=xxx` | 有效 Cookie 且平台返回 URL 时返回字符串；否则返回获取播放地址失败 |

### 前端页面流程

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| R-08 | Home 文本调台 | `/home` 输入文字点击调台 | 成功时跳转 `/player`，播放器显示当前歌曲 |
| R-09 | Home 场景调台 | 点击预设场景 | 成功时跳转 `/player` |
| R-10 | Home 心情色盘 | 拖动色盘后点击按心情调台 | 页面能发起请求并进入播放器 |
| R-11 | Player 空队列 | 直接访问 `/player` 且无队列 | 显示“还没有电台”并可返回 `/home` |
| R-12 | Player 控制 | 播放/暂停、上一首、下一首、进度条拖动 | UI 状态切换正确；真实音频取决于平台 URL |

当前前端 `Home.jsx` 的文本和心情色盘请求字段与后端 DTO 不完全一致：前端发送 `textInput/moodX/moodY`，后端接收 `text/valence/energy`。因此直接 API 测试要使用后端字段；前端链路可验证是否能启动，但心情内容可能走默认兜底。

## 9. 播放反馈测试

当前后端实现的是 WebSocket/STOMP：

- 连接端点：`/ws`
- 发送目的地：`/app/feedback`
- ack 订阅：`/user/queue/feedback-ack`

测试数据格式：

```json
{
  "sessionId": 1,
  "songId": 1,
  "eventType": "completed",
  "playedSeconds": 180,
  "totalSeconds": 180
}
```

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| F-01 | 未认证 WebSocket | 不带用户身份发送反馈 | 后端忽略，不写入数据 |
| F-02 | completed 反馈 | 认证后发送 `eventType=completed` | 写入 `feedback_events`；尝试写入 `play_records` |
| F-03 | skip 反馈 | 认证后发送 `eventType=skip`，playedSeconds 小于 30 | `action=skipped_early` |
| F-04 | like/dislike 反馈 | 发送 `like` 或 `dislike` | 写入 `feedback_events`，不写入 `play_records` |

注意：当前前端播放器调用的是不存在的 REST 接口 `POST /api/radio/feedback`，会被后端拒绝或 404/500，且前端 catch 后静默。播放反馈应作为接口层 WebSocket 单测/联调用例，前端按钮只验证 UI 点击不崩溃。

还需要重点观察一个数据约束风险：`play_records.platform` 在建表 SQL 中是 NOT NULL，但当前 `FeedbackServiceImpl` 写入播放记录时没有设置 platform；如果数据库严格执行该约束，completed/skip 反馈可能写入失败。

## 10. 洞察与心情日历测试

前置条件：洞察接口依赖 `play_records`、`songs`、`mood_sessions`。如果播放器反馈链路暂不可用，建议手动造数。

示例造数思路：

```sql
-- 先查出当前测试用户 id
SELECT id, username, email FROM users ORDER BY id DESC LIMIT 5;

-- 插入歌曲
INSERT INTO songs (title, artist, album, duration_seconds, features)
VALUES
('Test Calm Song', 'Tester', 'MoodFM QA', 180, '{"genre":"ambient","energy":0.3}'),
('Test Energy Song', 'Tester', 'MoodFM QA', 210, '{"genre":"pop","energy":0.8}');

-- 插入心情会话，user_id 替换成真实用户 id
INSERT INTO mood_sessions (user_id, raw_input, mood_params, scene, duration_minutes, started_at)
VALUES
(1, 'qa calm', '{"mood":{"valence":0.7,"energy":0.3}}', 'study', 30, NOW()),
(1, 'qa energy', '{"mood":{"valence":0.8,"energy":0.8}}', 'workout', 30, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 插入播放记录，song_id 替换成真实歌曲 id
INSERT INTO play_records (user_id, session_id, song_id, platform, played_seconds, total_seconds, action, played_at)
VALUES
(1, NULL, 1, 'netease', 180, 180, 'completed', NOW()),
(1, NULL, 2, 'netease', 60, 210, 'skipped', DATE_SUB(NOW(), INTERVAL 1 DAY));
```

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| I-01 | 综合摘要 | `GET /api/insights/summary?days=7` | 返回总收听时长、Top Genre、AI 准确率、心情主旋律等字段 |
| I-02 | 心情曲线 | `GET /api/insights/mood-trend?days=7` | 返回 labels、userMood、songMood、lowPointIndex |
| I-03 | 流派雷达 | `GET /api/insights/genre-radar?days=7` | 返回 8 个固定流派及 0-1 value |
| I-04 | Top 项 | `GET /api/insights/top-items?days=7` | 返回 artists、songs 数组 |
| I-05 | 空数据 | 新用户直接访问 `/insights` | 页面显示空/默认状态，不崩溃 |
| I-06 | 7/30/90 天切换 | 前端点击 7、30、90 天 | 重新请求并刷新图表 |
| I-07 | 月历 | `GET /api/insights/calendar?year=YYYY&month=M` | 返回月份、首日星期、天数、days 数组 |
| I-08 | 日期详情 | `GET /api/insights/day/YYYY-MM-DD` | 返回当天 mood、tracks、minutes、sessions、topTracks |
| I-09 | 日历页面 | `/calendar` 切换月份、心情/时长视图、点击有数据日期 | 月历格子和右侧详情正常更新 |

## 11. 周报测试

周报生成只针对“上一个完整自然周”，且需要上周有播放记录。

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| W-01 | 无上周数据 | 新用户调用 `POST /api/reports/weekly/generate` | 返回 `code=400`，提示本周/上周无足够收听数据 |
| W-02 | 造上周数据 | 向 `play_records` 插入上周一到周日的记录 | 周报具备生成条件 |
| W-03 | 生成周报 | 调用 `POST /api/reports/weekly/generate` | 返回周报 VO 并写入 `weekly_reports` |
| W-04 | 幂等生成 | 再次调用生成接口 | 返回已有周报，不重复生成同一周记录 |
| W-05 | 周报列表 | `GET /api/reports/weekly` | 返回当前用户的周报列表 |
| W-06 | 周报详情 | `GET /api/reports/weekly/{id}` | 返回 title、stats、essayBody、mostPlayedSong、topDiscoveries 等字段 |
| W-07 | 前端详情页 | 打开 `/weekly/{id}` | 页面加载周报详情，分享长图弹窗可打开 |
| W-08 | 分享长图 | 点击分享/下载图片 | html2canvas 生成图片；失败时页面给出失败提示 |

LLM 调用失败时，周报文案有默认兜底；只要上周播放数据存在，生成流程仍应可完成。

## 12. 权限与安全回归

| 编号 | 测试点 | 步骤 | 期望结果 |
|---|---|---|---|
| SEC-01 | 公开接口 | 未登录访问 `/api/health`、`/api/auth/register`、`/api/auth/login`、`/api/auth/refresh` | 可访问 |
| SEC-02 | 受保护接口 | 未登录访问 `/api/users/me`、`/api/platforms`、`/api/radio/start` | 被拒绝 |
| SEC-03 | 黑名单 Token | 登出后继续使用旧 accessToken | 被拒绝 |
| SEC-04 | CORS | 前端 `localhost:5173` 请求后端 | 请求正常，预检通过 |
| SEC-05 | Cookie 加密 | 绑定 Cookie 后查询数据库 | `platform_bindings.cookie_encrypted` 不应出现明文 Cookie |

## 13. 当前实现限制清单

以下内容测试时应记录为“当前未实现/实现不完整”，不要按 PRD 预期判失败：

- 前端 `authApi.me()` 写的是 `/user/me`，后端实际是 `/users/me`；目前应使用 `userApi.me()`。
- 前端 `radioApi.getSessions()` 没有对应后端接口，Home 最近电台区域可能一直为空。
- 前端 `radioApi.feedback()` 没有对应 REST 后端，真实反馈入口是 WebSocket `/app/feedback`。
- 前端 playlist 页面有完整 UI，但后端没有 playlist 相关接口。
- 前端手机号绑定 UI 有调用，但后端没有手机号绑定接口。
- QQ 音乐适配器的 liked/recommend/song-url 多为占位空结果。
- 前端 Docker Nginx 代理端口与后端端口存在不一致风险。
- 播放记录依赖反馈或造数；当前反馈写入 `play_records` 可能受数据库字段约束影响。

## 14. 验收标准

一次完整回归至少满足：

- 健康检查、前后端构建、后端编译通过。
- 注册、登录、刷新、登出、鉴权拦截通过。
- 用户偏好可保存到 `user_profiles`。
- 至少一种平台可完成 Cookie 绑定、列表查询、设置默认、解绑。
- NetEase 搜索可用，电台启动接口可返回队列或明确业务错误；未绑定平台时必须返回平台未绑定错误。
- 播放器页面在有队列/无队列两种状态下均不崩溃。
- 通过造数后，洞察页、日历页、周报生成/详情页可正常展示。
- 当前实现限制清单中的缺口已被记录，不混入 PRD 未实现功能验收。
