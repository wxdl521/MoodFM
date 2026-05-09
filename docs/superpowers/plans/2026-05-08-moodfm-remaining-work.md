# MoodFM 剩余工作实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补全 MoodFM 全部缺失页面、功能与后端接口，达到 PRD M1 完整可用、M2 数据闭环的状态。

**Architecture:** 前端 React（现有 screens/ + components/atoms）对接 Spring Boot 3 后端 REST + WebSocket；音频播放由 Howler.js 驱动；实时反馈通过 WebSocket 上报；状态由 Zustand stores 管理。

**Tech Stack:** React 18, React Router, Zustand, Howler.js, Axios, Spring Boot 3, Spring WebSocket, MyBatis-Plus, Redis, MySQL 8

---

## 文件地图（全局）

### 前端新增文件

| 文件 | 职责 |
|------|------|
| `src/screens/Profile.jsx` | P17 个人资料页 |
| `src/screens/Platforms.jsx` | P18 平台管理页 |
| `src/screens/Settings.jsx` | P16 设置中心 |
| `src/screens/Blacklist.jsx` | P19 黑名单管理 |
| `src/screens/History.jsx` | P12 历史记录 |
| `src/screens/Loved.jsx` | P11 我的红心 |
| `src/screens/PlaylistList.jsx` | P9 歌单列表 |
| `src/screens/SongDetail.jsx` | P8 歌曲详情 |
| `src/screens/ErrorPage.jsx` | P20 错误/404 页 |
| `src/components/player/QueueDrawer.jsx` | 播放队列抽屉 |
| `src/components/player/LyricsView.jsx` | 全屏歌词 |
| `src/components/player/RecommendCard.jsx` | AI 推荐解释卡片 |
| `src/components/player/FeedbackBanner.jsx` | 连续跳过提示 |
| `src/hooks/useAudioPlayer.js` | Howler.js 音频播放逻辑 |
| `src/hooks/useWebSocket.js` | WebSocket 连接与事件上报 |
| `src/store/radioStore.js` | AI 电台会话 + 动态重排状态 |
| `src/store/userStore.js` | 用户资料 + 偏好 + 黑名单 |
| `src/api/history.js` | 历史记录 API |
| `src/api/blacklist.js` | 黑名单 API |
| `src/api/song.js` | 歌曲详情 API |

### 前端修改文件

| 文件 | 修改内容 |
|------|---------|
| `src/App.jsx` | 补全所有缺失路由 |
| `src/screens/Player.jsx` | 接入 Howler + WS + QueueDrawer + LyricsView + RecommendCard |
| `src/screens/Home.jsx` | 静默启动 + 恢复上次电台 |
| `src/store/playerStore.js` | 扩展 skip/like/dislike 动作 |

### 后端新增文件

| 文件 | 职责 |
|------|------|
| `controller/UserController.java` | 资料修改、头像、设备管理、注销 |
| `controller/RadioController.java` | 完整 AI 电台接口（召回+重排+解释） |
| `controller/PlaylistController.java` | 歌单列表/详情 |
| `controller/HistoryController.java` | 播放历史查询 |
| `controller/BlacklistController.java` | 黑名单 CRUD |
| `controller/SongController.java` | 歌曲详情 + 相似歌曲 |
| `service/user/UserService.java` | 用户资料业务 |
| `service/radio/RecallService.java` | 5 路并行召回 |
| `service/radio/RecommendService.java` | 重排 + 曲序规划 |
| `service/radio/ExplainService.java` | AI 推荐解释生成 |
| `service/report/ReportScheduler.java` | 周报定时任务 |

---

## Phase 1 — M1 补全（核心可用）

> 目标：让已登录用户能完成「输入心情 → 真实播放音乐 → 反馈上报 → 管理账号」完整闭环。
> 预计工作量：3-4 天

---

### Task 1：补全前端路由（App.jsx）

**Files:**
- Modify: `moodfm-frontend/src/App.jsx`

- [ ] **Step 1：添加所有缺失路由**

```jsx
// App.jsx — 在现有 import 下方补充
import Profile    from './screens/Profile';
import Platforms  from './screens/Platforms';
import Settings   from './screens/Settings';
import Blacklist  from './screens/Blacklist';
import History    from './screens/History';
import Loved      from './screens/Loved';
import PlaylistList from './screens/PlaylistList';
import SongDetail from './screens/SongDetail';
import ErrorPage  from './screens/ErrorPage';

// Routes 内补充（RequireAuth 包裹）
<Route path="/profile"              element={<RequireAuth><Profile /></RequireAuth>} />
<Route path="/settings"             element={<RequireAuth><Settings /></RequireAuth>} />
<Route path="/settings/platforms"   element={<RequireAuth><Platforms /></RequireAuth>} />
<Route path="/settings/blacklist"   element={<RequireAuth><Blacklist /></RequireAuth>} />
<Route path="/history"              element={<RequireAuth><History /></RequireAuth>} />
<Route path="/likes"                element={<RequireAuth><Loved /></RequireAuth>} />
<Route path="/playlists"            element={<RequireAuth><PlaylistList /></RequireAuth>} />
<Route path="/song/:id"             element={<RequireAuth><SongDetail /></RequireAuth>} />
<Route path="*"                     element={<ErrorPage />} />
```

- [ ] **Step 2：创建空壳组件让路由不报错**

每个新 screen 先用最小占位：

```jsx
// 示例：src/screens/Profile.jsx（其余文件同样结构先占位）
export default function Profile() {
  return <div style={{ padding: 40, color: 'var(--ink)' }}>个人资料页（开发中）</div>;
}
```

- [ ] **Step 3：启动开发服务器验证所有路由可访问**

```bash
cd moodfm-frontend && npm run dev
# 浏览器逐一访问 /profile /settings /history /likes /playlists
# 预期：各页面显示占位文字，不报 404 或白屏
```

- [ ] **Step 4：提交**

```bash
git add moodfm-frontend/src/App.jsx moodfm-frontend/src/screens/
git commit -m "feat(frontend): register all missing routes with placeholder screens"
```

---

### Task 2：radioStore — AI 电台会话状态

**Files:**
- Create: `moodfm-frontend/src/store/radioStore.js`
- Modify: `moodfm-frontend/src/store/playerStore.js`

- [ ] **Step 1：创建 radioStore**

```js
// src/store/radioStore.js
import { create } from 'zustand';
import { radioApi } from '../api/radio';

export const useRadioStore = create((set, get) => ({
  session: null,       // { id, moodSummary, scene, startedAt }
  queue: [],           // SongVO[]
  currentIndex: 0,
  feedbackBuffer: [],  // 待上报的反馈事件

  startSession: async (payload) => {
    // payload: { text?, scene?, valence?, energy? }
    const res = await radioApi.start(payload);
    set({ session: res.session, queue: res.queue, currentIndex: 0, feedbackBuffer: [] });
    return res;
  },

  pushFeedback: (event) => {
    // event: { songId, eventType, playedSeconds, totalSeconds }
    set(s => ({ feedbackBuffer: [...s.feedbackBuffer, event] }));
  },

  flushFeedback: async () => {
    const { feedbackBuffer, session } = get();
    if (!feedbackBuffer.length || !session) return;
    await radioApi.feedback({ sessionId: session.id, events: feedbackBuffer });
    set({ feedbackBuffer: [] });
  },

  appendQueue: (songs) => set(s => ({ queue: [...s.queue, ...songs] })),

  advance: () => set(s => {
    const next = s.currentIndex + 1;
    // 队列剩余 < 3 首时触发补充（由 Player 监听触发）
    return { currentIndex: Math.min(next, s.queue.length - 1) };
  }),

  resetSession: () => set({ session: null, queue: [], currentIndex: 0, feedbackBuffer: [] }),
}));
```

- [ ] **Step 2：扩展 playerStore 增加 skip/like/dislike 动作**

```js
// playerStore.js 新增
skipSong: () => {
  const { session, currentIndex, queue } = get(); // 注意：此处 get 来自 radioStore，需协同
  // playerStore 只管播放状态，反馈交给 radioStore.pushFeedback
  set({ currentIndex: currentIndex + 1, progress: 0 });
},
```

> 注意：playerStore 与 radioStore 保持职责分离——playerStore 管播放UI状态（playing/progress），radioStore 管会话逻辑（队列/反馈）。

- [ ] **Step 3：提交**

```bash
git add moodfm-frontend/src/store/radioStore.js moodfm-frontend/src/store/playerStore.js
git commit -m "feat(store): add radioStore for session/queue/feedback management"
```

---

### Task 3：useAudioPlayer Hook（Howler.js 接入）

**Files:**
- Create: `moodfm-frontend/src/hooks/useAudioPlayer.js`

前提：确认 Howler.js 已安装

```bash
cd moodfm-frontend && npm list howler
# 若不存在：npm install howler
```

- [ ] **Step 1：创建 useAudioPlayer**

```js
// src/hooks/useAudioPlayer.js
import { useEffect, useRef, useCallback } from 'react';
import { Howl } from 'howler';
import { useRadioStore } from '../store/radioStore';
import { usePlayerStore } from '../store/playerStore';

export function useAudioPlayer() {
  const howlRef = useRef(null);
  const { queue, currentIndex, advance, pushFeedback, session } = useRadioStore();
  const { setPlaying, setProgress, playing } = usePlayerStore();

  const currentSong = queue[currentIndex];

  const destroyHowl = useCallback(() => {
    if (howlRef.current) {
      howlRef.current.unload();
      howlRef.current = null;
    }
  }, []);

  // 每首歌换时重建 Howl 实例
  useEffect(() => {
    if (!currentSong?.streamUrl) return;
    destroyHowl();

    const startTime = Date.now();

    howlRef.current = new Howl({
      src: [currentSong.streamUrl],
      html5: true,
      onplay: () => setPlaying(true),
      onpause: () => setPlaying(false),
      onend: () => {
        const elapsed = Math.round((Date.now() - startTime) / 1000);
        pushFeedback({
          songId: currentSong.id,
          eventType: 'completed',
          playedSeconds: elapsed,
          totalSeconds: currentSong.durationSeconds,
          sessionId: session?.id,
        });
        advance();
      },
      onloaderror: (_, err) => console.error('Howler load error', err),
    });

    // 进度更新
    const timer = setInterval(() => {
      if (howlRef.current?.playing()) {
        const pos = howlRef.current.seek();
        setProgress(pos / currentSong.durationSeconds);
      }
    }, 500);

    return () => {
      clearInterval(timer);
      destroyHowl();
    };
  }, [currentSong?.id]); // eslint-disable-line

  const play  = () => howlRef.current?.play();
  const pause = () => howlRef.current?.pause();
  const seek  = (ratio) => {
    const dur = howlRef.current?.duration() ?? 0;
    howlRef.current?.seek(dur * ratio);
  };

  const skip = () => {
    const elapsed = Math.round((howlRef.current?.seek() ?? 0));
    const total   = currentSong?.durationSeconds ?? 0;
    const signal  = elapsed < 30 ? 'skip_early' : 'skip_late';
    pushFeedback({ songId: currentSong?.id, eventType: signal, playedSeconds: elapsed, totalSeconds: total, sessionId: session?.id });
    destroyHowl();
    advance();
  };

  const like = () => {
    pushFeedback({ songId: currentSong?.id, eventType: 'like', sessionId: session?.id });
  };

  return { play, pause, seek, skip, like, playing };
}
```

- [ ] **Step 2：在 Player.jsx 顶部引入并使用**

```jsx
// Player.jsx 顶部
import { useAudioPlayer } from '../hooks/useAudioPlayer';

// 组件内
const { play, pause, seek, skip, like } = useAudioPlayer();
// 替换原有 mock 按钮的 onClick
```

- [ ] **Step 3：联调验证**

```
1. 启动后端（确保 /api/radio/stream 或 streamUrl 有效）
2. 前端登录 → 首页输入心情 → 进入播放器
3. 预期：歌曲实际播放、进度条移动、skip 后切下一首
```

- [ ] **Step 4：提交**

```bash
git add moodfm-frontend/src/hooks/useAudioPlayer.js moodfm-frontend/src/screens/Player.jsx
git commit -m "feat(player): integrate Howler.js real audio playback with skip/like feedback"
```

---

### Task 4：useWebSocket Hook（实时反馈上报）

**Files:**
- Create: `moodfm-frontend/src/hooks/useWebSocket.js`
- Modify: `moodfm-frontend/src/screens/Player.jsx`

- [ ] **Step 1：创建 useWebSocket**

```js
// src/hooks/useWebSocket.js
import { useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '../store/authStore';

export function useWebSocket(url, onMessage) {
  const wsRef  = useRef(null);
  const token  = useAuthStore(s => s.token);

  useEffect(() => {
    if (!token) return;
    const ws = new WebSocket(`${url}?token=${token}`);
    wsRef.current = ws;

    ws.onmessage = (e) => {
      try { onMessage(JSON.parse(e.data)); } catch {}
    };
    ws.onerror = (e) => console.error('[WS] error', e);

    return () => ws.close();
  }, [url, token]); // eslint-disable-line

  const send = useCallback((data) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(data));
    }
  }, []);

  return { send };
}
```

- [ ] **Step 2：在 Player.jsx 中接入 WebSocket**

```jsx
// Player.jsx
import { useWebSocket } from '../hooks/useWebSocket';
import { useRadioStore } from '../store/radioStore';

// 组件内
const { session } = useRadioStore();
const { send } = useWebSocket(
  `${import.meta.env.VITE_WS_URL}/ws/feedback`,
  (msg) => {
    // 处理服务端推送（如 cookie 失效通知）
    if (msg.type === 'cookie_invalid') {
      // toast 提示 + 跳转 /bind
    }
  }
);

// 在 useAudioPlayer 的 skip/like 回调后追加
send({ sessionId: session?.id, songId: currentSong?.id, eventType: 'skip_early', ... });
```

- [ ] **Step 3：在 .env 添加 WebSocket URL**

```env
# moodfm-frontend/.env
VITE_WS_URL=ws://localhost:8080
```

- [ ] **Step 4：提交**

```bash
git add moodfm-frontend/src/hooks/useWebSocket.js moodfm-frontend/src/screens/Player.jsx moodfm-frontend/.env
git commit -m "feat(player): add WebSocket real-time feedback reporting"
```

---

### Task 5：P17 个人资料页（Profile.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/Profile.jsx`
- Create: `moodfm-frontend/src/api/user.js`（已存在则追加接口）
- Create: `moodfm-frontend/src/store/userStore.js`

- [ ] **Step 1：创建 userStore**

```js
// src/store/userStore.js
import { create } from 'zustand';
import { userApi } from '../api/user';

export const useUserStore = create((set) => ({
  profile: null,   // { username, email, phone, avatarUrl }
  preferences: {}, // { defaultPlatform, preferredGenres, preferredLanguages, defaultScene }
  blacklist: { artists: [], songs: [], keywords: [] },

  fetchProfile: async () => {
    const data = await userApi.getProfile();
    set({ profile: data });
  },

  updateProfile: async (fields) => {
    const data = await userApi.updateProfile(fields);
    set({ profile: data });
  },

  fetchPreferences: async () => {
    const data = await userApi.getPreferences();
    set({ preferences: data });
  },

  updatePreferences: async (fields) => {
    const data = await userApi.updatePreferences(fields);
    set({ preferences: data });
  },

  fetchBlacklist: async () => {
    const data = await userApi.getBlacklist();
    set({ blacklist: data });
  },
}));
```

- [ ] **Step 2：user.js API 层**

```js
// src/api/user.js（追加或新建）
import { client } from './client';

export const userApi = {
  getProfile:        () => client.get('/api/user/profile').then(r => r.data.data),
  updateProfile:     (body) => client.put('/api/user/profile', body).then(r => r.data.data),
  changePassword:    (body) => client.post('/api/user/password', body).then(r => r.data),
  uploadAvatar:      (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return client.post('/api/user/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data.data);
  },
  getPreferences:    () => client.get('/api/user/preferences').then(r => r.data.data),
  updatePreferences: (body) => client.put('/api/user/preferences', body).then(r => r.data.data),
  getBlacklist:      () => client.get('/api/user/blacklist').then(r => r.data.data),
  addBlacklist:      (body) => client.post('/api/user/blacklist', body).then(r => r.data),
  removeBlacklist:   (id) => client.delete(`/api/user/blacklist/${id}`).then(r => r.data),
  getDevices:        () => client.get('/api/user/devices').then(r => r.data.data),
  revokeDevice:      (deviceId) => client.delete(`/api/user/devices/${deviceId}`).then(r => r.data),
  deleteAccount:     () => client.delete('/api/user/account').then(r => r.data),
};
```

- [ ] **Step 3：实现 Profile.jsx（参照 round5.jsx 中 Profile 的样式）**

```jsx
// src/screens/Profile.jsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { useUserStore } from '../store/userStore';

export default function Profile() {
  const navigate = useNavigate();
  const { profile, fetchProfile, updateProfile, uploadAvatar } = useUserStore();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ username: '', email: '' });

  useEffect(() => { fetchProfile(); }, []);

  useEffect(() => {
    if (profile) setForm({ username: profile.username, email: profile.email });
  }, [profile]);

  const handleAvatarChange = async (e) => {
    const file = e.target.files[0];
    if (file) await uploadAvatar(file);
  };

  const handleSave = async () => {
    await updateProfile(form);
    setEditing(false);
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 480, margin: '0 auto' }}>
        {/* 顶部导航 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 32 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}>
            <Icon.chevL />
          </button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>个人资料</h1>
        </div>

        {/* 头像 */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <label style={{ cursor: 'pointer' }}>
            <div style={{ width: 88, height: 88, borderRadius: '50%', background: 'var(--accent)', margin: '0 auto 8px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32 }}>
              {profile?.avatarUrl ? <img src={profile.avatarUrl} alt="" style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} /> : '🎵'}
            </div>
            <div className="mono" style={{ fontSize: 11, opacity: 0.6 }}>点击更换头像</div>
            <input type="file" accept="image/*" style={{ display: 'none' }} onChange={handleAvatarChange} />
          </label>
        </div>

        {/* 表单 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {['username', 'email'].map(field => (
            <div key={field}>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginBottom: 4 }}>{field === 'username' ? '昵称' : '邮箱'}</div>
              {editing
                ? <input value={form[field]} onChange={e => setForm(f => ({ ...f, [field]: e.target.value }))}
                    style={{ width: '100%', padding: '10px 12px', background: 'var(--surface)', border: '1px solid var(--rule)', borderRadius: 8, color: 'var(--ink)', fontSize: 15 }} />
                : <div style={{ padding: '10px 0', borderBottom: '1px solid var(--rule)', fontSize: 15 }}>{profile?.[field] || '—'}</div>
              }
            </div>
          ))}
        </div>

        {/* 操作按钮 */}
        <div style={{ marginTop: 32, display: 'flex', gap: 12 }}>
          {editing
            ? <>
                <button onClick={handleSave} style={{ flex: 1, padding: '12px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 8, cursor: 'pointer', fontFamily: 'var(--serif-cn)' }}>保存</button>
                <button onClick={() => setEditing(false)} style={{ flex: 1, padding: '12px', background: 'transparent', color: 'var(--ink)', border: '1px solid var(--rule)', borderRadius: 8, cursor: 'pointer' }}>取消</button>
              </>
            : <button onClick={() => setEditing(true)} style={{ flex: 1, padding: '12px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 8, cursor: 'pointer', fontFamily: 'var(--serif-cn)' }}>编辑资料</button>
          }
        </div>

        {/* 修改密码入口 */}
        <div style={{ marginTop: 16, padding: '14px 0', borderTop: '1px solid var(--rule)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
          onClick={() => {/* TODO: 弹出密码修改弹窗 */}}>
          <span>修改密码</span>
          <Icon.chevR />
        </div>

        {/* 注销账号 */}
        <div style={{ marginTop: 40, textAlign: 'center' }}>
          <button style={{ background: 'none', border: 'none', color: '#e05', fontSize: 13, cursor: 'pointer', opacity: 0.7 }}>
            注销账号
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4：后端 UserController 接口（Spring Boot）**

创建 `moodfm-backend/src/main/java/com/moodfm/controller/UserController.java`：

```java
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public R<UserVO> getProfile(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(userService.getProfile(ud.getUsername()));
    }

    @PutMapping("/profile")
    public R<UserVO> updateProfile(@AuthenticationPrincipal UserDetails ud,
                                   @RequestBody @Valid UpdateProfileRequest req) {
        return R.ok(userService.updateProfile(ud.getUsername(), req));
    }

    @PostMapping("/avatar")
    public R<String> uploadAvatar(@AuthenticationPrincipal UserDetails ud,
                                  @RequestParam MultipartFile file) {
        return R.ok(userService.uploadAvatar(ud.getUsername(), file));
    }

    @PostMapping("/password")
    public R<Void> changePassword(@AuthenticationPrincipal UserDetails ud,
                                  @RequestBody @Valid ChangePasswordRequest req) {
        userService.changePassword(ud.getUsername(), req);
        return R.ok();
    }

    @GetMapping("/devices")
    public R<List<DeviceVO>> getDevices(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(userService.getDevices(ud.getUsername()));
    }

    @DeleteMapping("/devices/{deviceId}")
    public R<Void> revokeDevice(@AuthenticationPrincipal UserDetails ud,
                                @PathVariable String deviceId) {
        userService.revokeDevice(ud.getUsername(), deviceId);
        return R.ok();
    }

    @DeleteMapping("/account")
    public R<Void> deleteAccount(@AuthenticationPrincipal UserDetails ud) {
        userService.softDelete(ud.getUsername());
        return R.ok();
    }
}
```

- [ ] **Step 5：验证并提交**

```
1. 后端启动，Swagger UI 检查 /api/user/* 接口列表
2. 前端 /profile 页面正常显示用户名/邮箱
3. 编辑昵称 → 保存 → 刷新后数据持久
```

```bash
git add moodfm-frontend/src/screens/Profile.jsx moodfm-frontend/src/store/userStore.js moodfm-frontend/src/api/user.js
git add moodfm-backend/src/main/java/com/moodfm/controller/UserController.java
git commit -m "feat: P17 profile page with avatar upload and profile editing"
```

---

### Task 6：P18 平台管理页（Platforms.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/Platforms.jsx`

> 注：Bind.jsx 负责首次绑定（引导流程），Platforms.jsx 负责已绑定后的管理（解绑/重新绑定/设置默认）。

- [ ] **Step 1：创建 Platforms.jsx（参照 round5.jsx 的 Platforms 样式）**

```jsx
// src/screens/Platforms.jsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { platformApi } from '../api/platform';

const PLATFORMS = [
  { key: 'netease', label: '网易云音乐', icon: '🎵' },
  { key: 'qqmusic', label: 'QQ 音乐',   icon: '🎶' },
];

export default function Platforms() {
  const navigate = useNavigate();
  const [bindings, setBindings] = useState([]);
  const [loading, setLoading]   = useState(true);

  useEffect(() => {
    platformApi.getBindings().then(data => { setBindings(data); setLoading(false); });
  }, []);

  const bound = (key) => bindings.find(b => b.platform === key);

  const handleUnbind = async (key) => {
    if (!window.confirm(`确定解绑 ${key} 吗？操作不可撤销（历史记录保留）。`)) return;
    await platformApi.unbind(key);
    setBindings(b => b.filter(x => x.platform !== key));
  };

  const handleSetDefault = async (key) => {
    await platformApi.setDefault(key);
    setBindings(b => b.map(x => ({ ...x, isDefault: x.platform === key })));
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 480, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 32 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /></button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>平台管理</h1>
        </div>

        {loading ? <div className="mono" style={{ opacity: 0.5 }}>加载中…</div> : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {PLATFORMS.map(({ key, label, icon }) => {
              const b = bound(key);
              return (
                <div key={key} style={{ padding: '18px 20px', border: '1px solid var(--rule)', borderRadius: 12, background: 'var(--surface)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                    <span style={{ fontSize: 24 }}>{icon}</span>
                    <div>
                      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>{label}</div>
                      <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>
                        {b ? (b.isDefault ? '默认音源 · ' : '') + (b.platformUsername || '已绑定') : '未绑定'}
                      </div>
                    </div>
                    {b?.isDefault && <span style={{ marginLeft: 'auto', fontSize: 11, background: 'var(--accent)', padding: '2px 8px', borderRadius: 20 }}>默认</span>}
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                    {b ? (
                      <>
                        {!b.isDefault && (
                          <button onClick={() => handleSetDefault(key)} style={{ flex: 1, padding: '8px', background: 'transparent', border: '1px solid var(--rule)', borderRadius: 8, cursor: 'pointer', color: 'var(--ink)', fontSize: 13 }}>
                            设为默认
                          </button>
                        )}
                        <button onClick={() => handleUnbind(key)} style={{ flex: 1, padding: '8px', background: 'transparent', border: '1px solid #e05', borderRadius: 8, cursor: 'pointer', color: '#e05', fontSize: 13 }}>
                          解绑
                        </button>
                      </>
                    ) : (
                      <button onClick={() => navigate('/bind', { state: { platform: key } })} style={{ flex: 1, padding: '8px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13 }}>
                        去绑定
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* 安全说明 */}
        <details style={{ marginTop: 32, fontSize: 13, opacity: 0.6 }}>
          <summary style={{ cursor: 'pointer' }}>你的账号信息如何被保护？</summary>
          <p style={{ marginTop: 8, lineHeight: 1.6 }}>
            你的 Cookie 使用 AES-256-GCM 加密后存储在本地服务器，不上传至任何第三方。
            前端仅展示绑定状态，不展示完整 Cookie。你可以随时解绑。
          </p>
        </details>
      </div>
    </div>
  );
}
```

> 后端：`platformApi.unbind(key)` 和 `platformApi.setDefault(key)` 对应的接口已在 PlatformBindingController 中补充，参照现有 `PlatformBindingController.java` 模式添加 `DELETE /api/platform/{platform}` 和 `PUT /api/platform/{platform}/default`。

- [ ] **Step 2：提交**

```bash
git add moodfm-frontend/src/screens/Platforms.jsx
git commit -m "feat: P18 platform management page with unbind and set-default"
```

---

### Task 7：P16 设置中心（Settings.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/Settings.jsx`

- [ ] **Step 1：创建 Settings.jsx（参照 Front-end styles/screens/settings.jsx 样式）**

```jsx
// src/screens/Settings.jsx
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { useAuthStore } from '../store/authStore';

function SettingRow({ label, sub, right, onClick, danger }) {
  return (
    <div onClick={onClick} style={{ padding: '14px 0', borderBottom: '1px solid var(--rule)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: onClick ? 'pointer' : 'default' }}>
      <div>
        <div style={{ fontSize: 15, color: danger ? '#e05' : 'var(--ink)' }}>{label}</div>
        {sub && <div style={{ fontSize: 12, opacity: 0.5, marginTop: 2 }}>{sub}</div>}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, opacity: 0.6 }}>
        {right}
        {onClick && <Icon.chevR />}
      </div>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div style={{ marginBottom: 32 }}>
      <div className="mono" style={{ fontSize: 11, letterSpacing: '.12em', opacity: 0.45, marginBottom: 8 }}>{title}</div>
      {children}
    </div>
  );
}

export default function Settings() {
  const navigate = useNavigate();
  const logout   = useAuthStore(s => s.logout);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 480, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 32 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /></button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>设置</h1>
        </div>

        <Section title="账号">
          <SettingRow label="个人资料" onClick={() => navigate('/profile')} />
          <SettingRow label="平台管理" onClick={() => navigate('/settings/platforms')} />
          <SettingRow label="修改密码" onClick={() => {/* 弹窗 */}} />
        </Section>

        <Section title="电台偏好">
          <SettingRow label="流派偏好" right="Ambient · Folk · +" onClick={() => {}} />
          <SettingRow label="语言偏好" right="中文 · EN" onClick={() => {}} />
          <SettingRow label="默认场景" right="深夜" onClick={() => {}} />
        </Section>

        <Section title="数据">
          <SettingRow label="历史记录" onClick={() => navigate('/history')} />
          <SettingRow label="黑名单管理" onClick={() => navigate('/settings/blacklist')} />
          <SettingRow label="导出我的数据" sub="导出 JSON / CSV" onClick={() => {}} />
        </Section>

        <Section title="账号安全">
          <SettingRow label="退出登录" danger onClick={() => { logout(); navigate('/'); }} />
        </Section>
      </div>
    </div>
  );
}
```

- [ ] **Step 2：提交**

```bash
git add moodfm-frontend/src/screens/Settings.jsx
git commit -m "feat: P16 settings center with navigation to sub-settings"
```

---

## Phase 2 — M2 资产与数据页面

> 目标：补全个人音乐资产（歌单/红心/历史）、黑名单管理、歌曲详情。
> 预计工作量：3-4 天

---

### Task 8：P9 歌单列表页（PlaylistList.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/PlaylistList.jsx`

- [ ] **Step 1：创建 PlaylistList.jsx（参照 playlist-list.jsx 样式）**

```jsx
// src/screens/PlaylistList.jsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { playlistApi } from '../api/playlist';

export default function PlaylistList() {
  const navigate = useNavigate();
  const [playlists, setPlaylists] = useState([]);
  const [loading, setLoading]     = useState(true);

  useEffect(() => {
    playlistApi.list().then(d => { setPlaylists(d); setLoading(false); });
  }, []);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /></button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>我的歌单</h1>
        </div>

        {loading ? <div className="mono" style={{ opacity: 0.5 }}>加载中…</div> : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 16 }}>
            {playlists.map(pl => (
              <div key={pl.id} onClick={() => navigate(`/playlist/${pl.id}`)} style={{ cursor: 'pointer' }}>
                <div style={{ aspectRatio: '1', background: 'var(--surface)', borderRadius: 10, overflow: 'hidden', marginBottom: 8 }}>
                  {pl.coverUrl && <img src={pl.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
                </div>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.4 }}>{pl.name}</div>
                <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>{pl.trackCount} 首</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 2：确认 playlistApi.list() 后端接口存在**

后端 `PlaylistController.java` 需提供：`GET /api/playlists` → 返回用户所有歌单列表。

- [ ] **Step 3：提交**

```bash
git add moodfm-frontend/src/screens/PlaylistList.jsx
git commit -m "feat: P9 playlist list page"
```

---

### Task 9：P11 我的红心页（Loved.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/Loved.jsx`
- Modify: `moodfm-frontend/src/api/playlist.js`（追加 loved 接口）

- [ ] **Step 1：在 playlistApi 追加接口**

```js
// src/api/playlist.js 追加
loved: () => client.get('/api/songs/liked').then(r => r.data.data),
toggleLike: (songId) => client.post(`/api/songs/${songId}/like`).then(r => r.data),
```

- [ ] **Step 2：创建 Loved.jsx（参照 loved.jsx 样式）**

```jsx
// src/screens/Loved.jsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { playlistApi } from '../api/playlist';

export default function Loved() {
  const navigate  = useNavigate();
  const [songs, setSongs]   = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    playlistApi.loved().then(d => { setSongs(d); setLoading(false); });
  }, []);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /></button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>我喜欢的音乐</h1>
          <span className="mono" style={{ fontSize: 12, opacity: 0.5, marginLeft: 4 }}>{songs.length} 首</span>
        </div>

        {loading ? <div className="mono" style={{ opacity: 0.5 }}>加载中…</div> : songs.map((s, i) => (
          <div key={s.id} onClick={() => navigate(`/song/${s.id}`)}
            style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--rule)', cursor: 'pointer' }}>
            <div className="mono" style={{ width: 24, textAlign: 'right', opacity: 0.4, fontSize: 12 }}>{i + 1}</div>
            <div style={{ width: 44, height: 44, borderRadius: 6, background: 'var(--surface)', overflow: 'hidden', flexShrink: 0 }}>
              {s.coverUrl && <img src={s.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.title}</div>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>{s.artist}</div>
            </div>
            <div className="mono" style={{ fontSize: 12, opacity: 0.4 }}>
              {Math.floor(s.durationSeconds / 60)}:{String(s.durationSeconds % 60).padStart(2, '0')}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3：提交**

```bash
git add moodfm-frontend/src/screens/Loved.jsx moodfm-frontend/src/api/playlist.js
git commit -m "feat: P11 loved songs page"
```

---

### Task 10：P12 历史记录页（History.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/History.jsx`
- Create: `moodfm-frontend/src/api/history.js`

- [ ] **Step 1：创建 history API**

```js
// src/api/history.js
import { client } from './client';

export const historyApi = {
  list: (params) => client.get('/api/history', { params }).then(r => r.data.data),
  // params: { page, pageSize, scene?, startDate?, endDate? }
};
```

- [ ] **Step 2：创建 History.jsx（参照 history.jsx 样式）**

```jsx
// src/screens/History.jsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { historyApi } from '../api/history';
import dayjs from 'dayjs';

const SCENES = ['全部', '通勤', '学习', '跑步', '写作', '睡前', '派对'];

export default function History() {
  const navigate = useNavigate();
  const [records, setRecords] = useState([]);
  const [scene, setScene]     = useState('全部');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    historyApi.list({ scene: scene === '全部' ? undefined : scene, pageSize: 50 })
      .then(d => { setRecords(d); setLoading(false); });
  }, [scene]);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /></button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>历史记录</h1>
        </div>

        {/* 场景筛选 */}
        <div style={{ display: 'flex', gap: 8, overflowX: 'auto', paddingBottom: 12, marginBottom: 20 }}>
          {SCENES.map(s => (
            <button key={s} onClick={() => setScene(s)}
              style={{ flexShrink: 0, padding: '6px 14px', borderRadius: 20, border: '1px solid var(--rule)', background: scene === s ? 'var(--ink)' : 'transparent', color: scene === s ? 'var(--bg)' : 'var(--ink)', cursor: 'pointer', fontSize: 13 }}>
              {s}
            </button>
          ))}
        </div>

        {loading ? <div className="mono" style={{ opacity: 0.5 }}>加载中…</div> : records.map(r => (
          <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--rule)' }}>
            <div style={{ width: 44, height: 44, borderRadius: 6, background: 'var(--surface)', overflow: 'hidden', flexShrink: 0 }}>
              {r.song?.coverUrl && <img src={r.song.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14 }}>{r.song?.title}</div>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>{r.song?.artist}</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>{dayjs(r.playedAt).format('MM-DD HH:mm')}</div>
              <div style={{ fontSize: 11, marginTop: 2, opacity: 0.6 }}>
                {r.action === 'completed' ? '✓ 听完' : r.action === 'liked' ? '♥ 喜欢' : '→ 跳过'}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3：后端 HistoryController**

```java
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final PlayRecordMapper playRecordMapper;

    @GetMapping
    public R<IPage<PlayRecordVO>> list(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false)    String scene) {

        LambdaQueryWrapper<PlayRecord> q = new LambdaQueryWrapper<PlayRecord>()
                .eq(PlayRecord::getUserId, getUserId(ud))
                .eq(scene != null, PlayRecord::getScene, scene)
                .orderByDesc(PlayRecord::getPlayedAt);

        return R.ok(playRecordMapper.selectPage(new Page<>(page, pageSize), q)
                                    .convert(PlayRecordVO::from));
    }
}
```

- [ ] **Step 4：提交**

```bash
git add moodfm-frontend/src/screens/History.jsx moodfm-frontend/src/api/history.js
git add moodfm-backend/src/main/java/com/moodfm/controller/HistoryController.java
git commit -m "feat: P12 history page with scene filter"
```

---

### Task 11：P19 黑名单管理页（Blacklist.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/Blacklist.jsx`
- Create: `moodfm-frontend/src/api/blacklist.js`
- Modify: `moodfm-frontend/src/store/userStore.js`（补充黑名单操作）

- [ ] **Step 1：blacklist API**

```js
// src/api/blacklist.js
import { client } from './client';

export const blacklistApi = {
  getAll:   ()           => client.get('/api/user/blacklist').then(r => r.data.data),
  add:      (entry)      => client.post('/api/user/blacklist', entry).then(r => r.data),
  // entry: { type: 'artist'|'song'|'keyword', value: string, label?: string }
  remove:   (id)         => client.delete(`/api/user/blacklist/${id}`).then(r => r.data),
};
```

- [ ] **Step 2：创建 Blacklist.jsx（参照 round5.jsx 的 Blocklist 样式）**

```jsx
// src/screens/Blacklist.jsx
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { blacklistApi } from '../api/blacklist';

const TABS = ['歌手', '歌曲', '关键词'];
const TYPE_MAP = { '歌手': 'artist', '歌曲': 'song', '关键词': 'keyword' };

export default function Blacklist() {
  const navigate = useNavigate();
  const [tab, setTab]       = useState('歌手');
  const [items, setItems]   = useState([]);
  const [input, setInput]   = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    blacklistApi.getAll().then(d => { setItems(d); setLoading(false); });
  }, []);

  const filtered = items.filter(i => i.type === TYPE_MAP[tab]);

  const handleAdd = async () => {
    if (!input.trim()) return;
    const entry = await blacklistApi.add({ type: TYPE_MAP[tab], value: input.trim(), label: input.trim() });
    setItems(prev => [...prev, entry.data]);
    setInput('');
  };

  const handleRemove = async (id) => {
    await blacklistApi.remove(id);
    setItems(prev => prev.filter(i => i.id !== id));
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '24px 20px' }}>
      <div style={{ maxWidth: 480, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /></button>
          <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, margin: 0 }}>黑名单管理</h1>
        </div>

        {/* Tab 切换 */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          {TABS.map(t => (
            <button key={t} onClick={() => setTab(t)}
              style={{ padding: '6px 18px', borderRadius: 20, border: '1px solid var(--rule)', background: tab === t ? 'var(--ink)' : 'transparent', color: tab === t ? 'var(--bg)' : 'var(--ink)', cursor: 'pointer', fontSize: 13 }}>
              {t}
            </button>
          ))}
        </div>

        {/* 添加输入 */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          <input value={input} onChange={e => setInput(e.target.value)}
            placeholder={`添加${tab}到黑名单…`} onKeyDown={e => e.key === 'Enter' && handleAdd()}
            style={{ flex: 1, padding: '10px 14px', background: 'var(--surface)', border: '1px solid var(--rule)', borderRadius: 8, color: 'var(--ink)', fontSize: 14 }} />
          <button onClick={handleAdd} style={{ padding: '10px 16px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 8, cursor: 'pointer' }}>添加</button>
        </div>

        {/* 列表 */}
        {loading ? <div className="mono" style={{ opacity: 0.5 }}>加载中…</div> : filtered.length === 0
          ? <div className="mono" style={{ opacity: 0.4, textAlign: 'center', paddingTop: 40 }}>黑名单为空</div>
          : filtered.map(item => (
            <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderBottom: '1px solid var(--rule)' }}>
              <span style={{ fontSize: 14 }}>{item.label || item.value}</span>
              <button onClick={() => handleRemove(item.id)} style={{ background: 'none', border: 'none', color: '#e05', cursor: 'pointer', fontSize: 13 }}>移除</button>
            </div>
          ))
        }
      </div>
    </div>
  );
}
```

- [ ] **Step 3：提交**

```bash
git add moodfm-frontend/src/screens/Blacklist.jsx moodfm-frontend/src/api/blacklist.js
git commit -m "feat: P19 blacklist management page for artists/songs/keywords"
```

---

### Task 12：P8 歌曲详情页（SongDetail.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/SongDetail.jsx`
- Create: `moodfm-frontend/src/api/song.js`

- [ ] **Step 1：song API**

```js
// src/api/song.js
import { client } from './client';

export const songApi = {
  get:     (id) => client.get(`/api/songs/${id}`).then(r => r.data.data),
  similar: (id) => client.get(`/api/songs/${id}/similar`).then(r => r.data.data),
  lyrics:  (id) => client.get(`/api/songs/${id}/lyrics`).then(r => r.data.data),
};
```

- [ ] **Step 2：创建 SongDetail.jsx（参照 round5.jsx SongDetail 样式）**

```jsx
// src/screens/SongDetail.jsx
import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { songApi } from '../api/song';
import { playlistApi } from '../api/playlist';

export default function SongDetail() {
  const { id }        = useParams();
  const navigate      = useNavigate();
  const [song, setSong]       = useState(null);
  const [lyrics, setLyrics]   = useState([]);
  const [similar, setSimilar] = useState([]);
  const [liked, setLiked]     = useState(false);

  useEffect(() => {
    Promise.all([songApi.get(id), songApi.lyrics(id), songApi.similar(id)])
      .then(([s, l, sim]) => { setSong(s); setLyrics(l); setSimilar(sim); setLiked(s.liked); });
  }, [id]);

  const toggleLike = async () => {
    await playlistApi.toggleLike(id);
    setLiked(l => !l);
  };

  if (!song) return <div style={{ padding: 40, color: 'var(--ink)' }}>加载中…</div>;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', padding: '0 0 80px' }}>
      {/* 顶部导航 */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)', padding: '18px 20px', borderBottom: '1px solid var(--rule)', display: 'flex', justifyContent: 'space-between' }}>
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}><Icon.chevL /> 返回</button>
        <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>SONG · 单曲</div>
        <button style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)' }}>···</button>
      </div>

      <div style={{ maxWidth: 640, margin: '0 auto', padding: '32px 20px' }}>
        {/* 封面 + 信息 */}
        <div style={{ display: 'flex', gap: 24, marginBottom: 32, alignItems: 'flex-start' }}>
          <div style={{ width: 140, height: 140, borderRadius: 12, overflow: 'hidden', flexShrink: 0, background: 'var(--surface)' }}>
            {song.coverUrl && <img src={song.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
          </div>
          <div>
            <h2 style={{ fontFamily: 'var(--serif-cn)', fontSize: 22, margin: '0 0 8px' }}>{song.title}</h2>
            <div style={{ opacity: 0.6, marginBottom: 16 }}>{song.artist} · {song.album}</div>
            <button onClick={toggleLike} style={{ background: 'none', border: 'none', fontSize: 20, cursor: 'pointer' }}>
              {liked ? '♥' : '♡'}
            </button>
          </div>
        </div>

        {/* 歌词 */}
        {lyrics.length > 0 && (
          <div style={{ marginBottom: 32 }}>
            <div className="mono" style={{ fontSize: 11, opacity: 0.45, marginBottom: 12 }}>LYRICS · 歌词</div>
            {lyrics.map((line, i) => (
              <div key={i} style={{ padding: '4px 0', fontFamily: 'var(--serif-cn)', fontSize: 15, lineHeight: 1.8, opacity: 0.8 }}>
                {line.text}
              </div>
            ))}
          </div>
        )}

        {/* 相似歌曲 */}
        {similar.length > 0 && (
          <div>
            <div className="mono" style={{ fontSize: 11, opacity: 0.45, marginBottom: 12 }}>SIMILAR · 相似歌曲</div>
            {similar.map(s => (
              <div key={s.id} onClick={() => navigate(`/song/${s.id}`)}
                style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--rule)', cursor: 'pointer' }}>
                <div style={{ width: 40, height: 40, borderRadius: 6, background: 'var(--surface)', overflow: 'hidden', flexShrink: 0 }}>
                  {s.coverUrl && <img src={s.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14 }}>{s.title}</div>
                  <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>{s.artist}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3：提交**

```bash
git add moodfm-frontend/src/screens/SongDetail.jsx moodfm-frontend/src/api/song.js
git commit -m "feat: P8 song detail page with lyrics and similar tracks"
```

---

## Phase 3 — M2 播放器增强

> 目标：让播放器达到 PRD 完整描述的体验。
> 预计工作量：2-3 天

---

### Task 13：QueueDrawer 播放队列抽屉

**Files:**
- Create: `moodfm-frontend/src/components/player/QueueDrawer.jsx`
- Modify: `moodfm-frontend/src/screens/Player.jsx`

- [ ] **Step 1：创建 QueueDrawer.jsx**

```jsx
// src/components/player/QueueDrawer.jsx
import { useRadioStore } from '../../store/radioStore';

export function QueueDrawer({ open, onClose }) {
  const { queue, currentIndex } = useRadioStore();
  const upcoming = queue.slice(currentIndex + 1, currentIndex + 6);

  if (!open) return null;

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 100,
      background: 'rgba(0,0,0,0.6)',
      display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
    }} onClick={onClose}>
      <div onClick={e => e.stopPropagation()} style={{
        background: 'var(--bg)', borderRadius: '20px 20px 0 0',
        padding: '20px 20px 40px', maxHeight: '60vh', overflowY: 'auto',
      }}>
        <div className="mono" style={{ fontSize: 11, opacity: 0.4, marginBottom: 16, textAlign: 'center' }}>
          接下来 · UP NEXT
        </div>
        {upcoming.length === 0
          ? <div style={{ textAlign: 'center', opacity: 0.4, fontFamily: 'var(--serif-cn)' }}>队列为空</div>
          : upcoming.map((s, i) => (
            <div key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '10px 0', borderBottom: '1px solid var(--rule)' }}>
              <div style={{ width: 40, height: 40, borderRadius: 6, background: 'var(--surface)', overflow: 'hidden', flexShrink: 0 }}>
                {s.coverUrl && <img src={s.coverUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14 }}>{s.title}</div>
                <div className="mono" style={{ fontSize: 11, opacity: 0.5 }}>{s.artist}</div>
              </div>
              <div className="mono" style={{ fontSize: 11, opacity: 0.4 }}>{i + currentIndex + 2}</div>
            </div>
          ))
        }
      </div>
    </div>
  );
}
```

- [ ] **Step 2：在 Player.jsx 引入并绑定队列按钮**

```jsx
import { QueueDrawer } from '../components/player/QueueDrawer';

// 组件内 state
const [queueOpen, setQueueOpen] = useState(false);

// JSX 中
<button onClick={() => setQueueOpen(true)}>队列</button>
<QueueDrawer open={queueOpen} onClose={() => setQueueOpen(false)} />
```

- [ ] **Step 3：提交**

```bash
git add moodfm-frontend/src/components/player/QueueDrawer.jsx moodfm-frontend/src/screens/Player.jsx
git commit -m "feat(player): add QueueDrawer to show upcoming songs"
```

---

### Task 14：RecommendCard — AI 推荐解释卡片

**Files:**
- Create: `moodfm-frontend/src/components/player/RecommendCard.jsx`
- Modify: `moodfm-frontend/src/screens/Player.jsx`
- Modify: `moodfm-frontend/src/store/radioStore.js`（存储当前歌曲解释文字）

- [ ] **Step 1：创建 RecommendCard.jsx**

```jsx
// src/components/player/RecommendCard.jsx
export function RecommendCard({ explanation }) {
  if (!explanation) return null;
  return (
    <div style={{
      background: 'rgba(255,255,255,0.08)',
      borderRadius: 12,
      padding: '12px 16px',
      backdropFilter: 'blur(8px)',
      border: '1px solid rgba(255,255,255,0.12)',
      margin: '0 0 16px',
    }}>
      <div className="mono" style={{ fontSize: 10, opacity: 0.5, marginBottom: 6, letterSpacing: '.1em' }}>
        AI 推荐理由
      </div>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.7, opacity: 0.85 }}>
        {explanation}
      </div>
    </div>
  );
}
```

- [ ] **Step 2：扩展 radioStore 存储解释文字**

```js
// radioStore.js 追加 state 字段
explanations: {},  // { [songId]: '解释文字' }

setExplanation: (songId, text) => set(s => ({
  explanations: { ...s.explanations, [songId]: text },
})),
```

> 后端 `ExplainService` 在生成队列时同步写入，或通过 WebSocket 在歌曲开始播放时推送 `{ type: 'explain', songId, text }` 给前端。

- [ ] **Step 3：在 Player.jsx 中使用**

```jsx
import { RecommendCard } from '../components/player/RecommendCard';
import { useRadioStore } from '../store/radioStore';

const { explanations, queue, currentIndex } = useRadioStore();
const currentSong = queue[currentIndex];
const explanation = explanations[currentSong?.id];

// JSX 中播放控制上方
<RecommendCard explanation={explanation} />
```

- [ ] **Step 4：提交**

```bash
git add moodfm-frontend/src/components/player/RecommendCard.jsx moodfm-frontend/src/screens/Player.jsx moodfm-frontend/src/store/radioStore.js
git commit -m "feat(player): add AI recommendation explanation card"
```

---

### Task 15：FeedbackBanner — 连续跳过提示

**Files:**
- Create: `moodfm-frontend/src/components/player/FeedbackBanner.jsx`
- Modify: `moodfm-frontend/src/hooks/useAudioPlayer.js`

- [ ] **Step 1：创建 FeedbackBanner.jsx**

```jsx
// src/components/player/FeedbackBanner.jsx
export function FeedbackBanner({ show, artistName, onAddBlacklist, onDismiss }) {
  if (!show) return null;
  return (
    <div style={{
      position: 'fixed', bottom: 100, left: '50%', transform: 'translateX(-50%)',
      background: 'var(--ink)', color: 'var(--bg)', borderRadius: 24,
      padding: '12px 20px', display: 'flex', alignItems: 'center', gap: 14, zIndex: 50,
      maxWidth: 340, width: 'calc(100% - 40px)',
    }}>
      <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
        连续跳过了 3 首，要屏蔽「{artistName}」吗？
      </div>
      <button onClick={onAddBlacklist} style={{ background: 'var(--bg)', color: 'var(--ink)', border: 'none', borderRadius: 16, padding: '6px 12px', cursor: 'pointer', fontSize: 12, flexShrink: 0 }}>屏蔽</button>
      <button onClick={onDismiss} style={{ background: 'transparent', color: 'rgba(255,255,255,0.5)', border: 'none', cursor: 'pointer', fontSize: 18, flexShrink: 0 }}>✕</button>
    </div>
  );
}
```

- [ ] **Step 2：在 useAudioPlayer 中追踪连续跳过计数**

```js
// useAudioPlayer.js 顶部增加 ref
const skipStreakRef = useRef(0);
const lastSkippedArtistRef = useRef(null);

// skip 函数内
const skip = () => {
  skipStreakRef.current += 1;
  lastSkippedArtistRef.current = currentSong?.artist;
  // ... 原有逻辑
  if (skipStreakRef.current >= 3) {
    onSkipStreak?.(lastSkippedArtistRef.current); // 回调给 Player.jsx
    skipStreakRef.current = 0;
  }
};

// useAudioPlayer 接收 onSkipStreak prop
export function useAudioPlayer({ onSkipStreak } = {}) { ... }
```

- [ ] **Step 3：在 Player.jsx 中绑定**

```jsx
const [bannerArtist, setBannerArtist] = useState(null);
const { skip } = useAudioPlayer({ onSkipStreak: setBannerArtist });

// JSX
<FeedbackBanner
  show={!!bannerArtist}
  artistName={bannerArtist}
  onAddBlacklist={async () => {
    await blacklistApi.add({ type: 'artist', value: bannerArtist, label: bannerArtist });
    setBannerArtist(null);
  }}
  onDismiss={() => setBannerArtist(null)}
/>
```

- [ ] **Step 4：提交**

```bash
git add moodfm-frontend/src/components/player/FeedbackBanner.jsx moodfm-frontend/src/hooks/useAudioPlayer.js moodfm-frontend/src/screens/Player.jsx
git commit -m "feat(player): add consecutive-skip banner with blacklist prompt"
```

---

### Task 16：P20 错误页（ErrorPage.jsx）

**Files:**
- Create: `moodfm-frontend/src/screens/ErrorPage.jsx`

- [ ] **Step 1：创建 ErrorPage.jsx**

```jsx
// src/screens/ErrorPage.jsx
import { useNavigate } from 'react-router-dom';

export default function ErrorPage() {
  const navigate = useNavigate();
  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 48, opacity: 0.15 }}>404</div>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, opacity: 0.6 }}>这首歌找不到了</div>
      <button onClick={() => navigate('/home')} style={{ marginTop: 8, padding: '10px 24px', background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 24, cursor: 'pointer', fontFamily: 'var(--serif-cn)' }}>
        回到首页
      </button>
    </div>
  );
}
```

- [ ] **Step 2：提交**

```bash
git add moodfm-frontend/src/screens/ErrorPage.jsx
git commit -m "feat: P20 404 error page"
```

---

## Phase 4 — 后端完整化（并行进行）

> 以下后端任务可与前端 Phase 1-3 并行推进。

### Task 17：RadioController 完整 AI 电台接口

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/controller/RadioController.java`
- Create: `moodfm-backend/src/main/java/com/moodfm/service/radio/RecallService.java`
- Create: `moodfm-backend/src/main/java/com/moodfm/service/radio/RecommendService.java`

- [ ] **Step 1：RadioController**

```java
@RestController
@RequestMapping("/api/radio")
@RequiredArgsConstructor
public class RadioController {

    private final MoodAnalysisService moodAnalysisService;
    private final RecallService       recallService;
    private final RecommendService    recommendService;

    /** 开始新会话 */
    @PostMapping("/start")
    public R<RadioStartVO> start(@AuthenticationPrincipal UserDetails ud,
                                 @RequestBody MoodInputRequest req) {
        Long userId = getUserId(ud);
        MoodParams params = moodAnalysisService.analyze(req.getText(), req.getScene());

        MoodSession session = moodSessionService.create(userId, req, params);

        List<SongVO> queue = recallService.recall(userId, params)
                .stream()
                .collect(recommendService.rerank(params))
                .limit(20)
                .collect(Collectors.toList());

        queueService.save(userId, session.getId(), queue);

        return R.ok(new RadioStartVO(SessionVO.from(session), queue));
    }

    /** 获取当前队列（断点续播） */
    @GetMapping("/queue")
    public R<RadioQueueVO> queue(@AuthenticationPrincipal UserDetails ud) {
        Long userId = getUserId(ud);
        return R.ok(queueService.getCurrent(userId));
    }
}
```

- [ ] **Step 2：RecallService（5路并行）**

```java
@Service
@RequiredArgsConstructor
public class RecallService {

    private final MusicAdapterClient adapter;
    private final SongMapper         songMapper;

    public List<SongVO> recall(Long userId, MoodParams params) {
        // Virtual Thread 并行 5 路
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var f1 = executor.submit(() -> recallPersonal(userId, params));
            var f2 = executor.submit(() -> recallSimilarArtist(userId, params));
            var f3 = executor.submit(() -> recallPlatformNative(userId, params));
            var f4 = executor.submit(() -> recallKeyword(params));
            var f5 = executor.submit(() -> recallExplore(userId, params));

            return Stream.of(f1, f2, f3, f4, f5)
                .flatMap(f -> { try { return f.get().stream(); } catch (Exception e) { return Stream.empty(); } })
                .distinct()
                .collect(Collectors.toList());
        }
    }

    private List<SongVO> recallPersonal(Long userId, MoodParams p) { /* 用户红心+收藏 */ return List.of(); }
    private List<SongVO> recallSimilarArtist(Long userId, MoodParams p) { return List.of(); }
    private List<SongVO> recallPlatformNative(Long userId, MoodParams p) { return List.of(); }
    private List<SongVO> recallKeyword(MoodParams p) { return List.of(); }
    private List<SongVO> recallExplore(Long userId, MoodParams p) { return List.of(); }
}
```

- [ ] **Step 3：提交**

```bash
git add moodfm-backend/src/main/java/com/moodfm/controller/RadioController.java
git add moodfm-backend/src/main/java/com/moodfm/service/radio/
git commit -m "feat(backend): RadioController + 5-way parallel RecallService skeleton"
```

---

### Task 18：周报定时生成（ReportScheduler）

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/scheduler/ReportScheduler.java`

- [ ] **Step 1：创建 ReportScheduler**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final WeeklyReportMapper reportMapper;
    private final PlayRecordMapper   recordMapper;
    private final MoodAnalysisService aiService;
    private final UserMapper         userMapper;

    /** 每周一凌晨 2 点为所有活跃用户生成上周报告 */
    @Scheduled(cron = "0 0 2 * * MON")
    public void generateWeeklyReports() {
        LocalDate weekEnd   = LocalDate.now().minusDays(1);          // 上周日
        LocalDate weekStart = weekEnd.minusDays(6);                  // 上周一

        userMapper.selectActiveUserIds(weekStart).forEach(userId -> {
            try {
                buildReport(userId, weekStart, weekEnd);
            } catch (Exception e) {
                log.error("Failed to generate report for user {}", userId, e);
            }
        });
    }

    private void buildReport(Long userId, LocalDate start, LocalDate end) {
        List<PlayRecord> records = recordMapper.findByUserAndDateRange(userId, start, end);
        if (records.isEmpty()) return;

        // 统计数据
        Map<String, Object> data = computeStats(records);

        // AI 总结
        String summary = aiService.summarizeWeek(data);

        WeeklyReport report = new WeeklyReport();
        report.setUserId(userId);
        report.setWeekStart(start);
        report.setWeekEnd(end);
        report.setData(JSON.toJSONString(data));
        report.setAiSummary(summary);

        reportMapper.insertOrUpdate(report);
    }

    private Map<String, Object> computeStats(List<PlayRecord> records) {
        // 计算：总时长、流派分布、高峰时段、跳过率等
        return Map.of(
            "totalMinutes",  records.stream().mapToInt(r -> r.getPlayedSeconds() / 60).sum(),
            "completedCount", records.stream().filter(r -> "completed".equals(r.getAction())).count(),
            "skipCount",      records.stream().filter(r -> r.getAction().startsWith("skip")).count()
        );
    }
}
```

- [ ] **Step 2：提交**

```bash
git add moodfm-backend/src/main/java/com/moodfm/scheduler/ReportScheduler.java
git commit -m "feat(backend): weekly report scheduler with AI summary"
```

---

## 计划总览

| 阶段 | 内容 | 估时 | 优先级 |
|------|------|------|--------|
| **Phase 1** | 路由补全 + radioStore + Howler + WebSocket + Profile + Platforms + Settings | 3-4 天 | M1 必做 |
| **Phase 2** | 歌单列表 + 红心 + 历史 + 黑名单 + 歌曲详情 | 3-4 天 | M2 |
| **Phase 3** | QueueDrawer + RecommendCard + FeedbackBanner + 错误页 | 2-3 天 | M2 |
| **Phase 4** | 后端 RadioController + RecallService + 周报 Scheduler | 与上述并行 | M1-M2 |
| **总计** | | **8-11 天** | |

---

## 自检：PRD 覆盖确认

| PRD 需求 | 对应 Task | 状态 |
|---------|---------|------|
| 真实音频播放（Howler.js）| Task 3 | ✅ 已计划 |
| WebSocket 实时反馈 | Task 4 | ✅ 已计划 |
| 个人资料页 P17 | Task 5 | ✅ 已计划 |
| 平台管理页 P18 | Task 6 | ✅ 已计划 |
| 设置中心 P16 | Task 7 | ✅ 已计划 |
| 歌单列表 P9 | Task 8 | ✅ 已计划 |
| 我的红心 P11 | Task 9 | ✅ 已计划 |
| 历史记录 P12 | Task 10 | ✅ 已计划 |
| 黑名单管理 P19 | Task 11 | ✅ 已计划 |
| 歌曲详情 P8 | Task 12 | ✅ 已计划 |
| QueueDrawer | Task 13 | ✅ 已计划 |
| AI 推荐解释卡片 | Task 14 | ✅ 已计划 |
| 连续跳过黑名单提示 | Task 15 | ✅ 已计划 |
| 错误页 P20 | Task 16 | ✅ 已计划 |
| RadioController（AI 召回）| Task 17 | ✅ 已计划 |
| 周报定时生成 | Task 18 | ✅ 已计划 |
| 全屏歌词（LyricsView）| — | ⚠️ 未拆 Task，可在 Phase 3 追加 |
| 静默启动 | — | ⚠️ 未拆 Task，在 Home.jsx 内追加 |
| 数据导出 CSV/JSON | — | ⚠️ 作为 Settings 内次要功能，可在 Task 7 扩展 |
| P21 通知页 | — | ⚠️ 可选，优先级低 |
| 年度报告（M4）| — | 不在当前计划范围 |
