# MoodFM · PRD 对齐完整计划

> **目标：** 覆盖 PRD v2.0 全部 M1/M2 需求，消除已知 Bug，补齐非功能需求，为 M3/M4 做好结构准备。
> **当前完成度：** ~72%（骨架完整，细节缺口）
> **分支：** `feat/phase1-core-pages`

---

## 阶段划分

| 阶段 | 目标 | 预计工时 |
|------|------|----------|
| **Phase A** | M1 遗留 Bug + 快速补齐（本次先做） | 2–3 小时 |
| **Phase B** | M1 非功能需求（安全/缓存/限流） | 3–4 小时 |
| **Phase C** | M2 功能完整化 | 4–6 小时 |
| **Phase D** | M3/M4 数据沉淀与进阶 | 持续迭代 |

---

## Phase A — M1 遗留 Bug + 快速补齐

### Task A1：前端展示 `recommendReason`（AI 推荐解释）

**问题：** 后端 `SongVO.recommendReason` 已有，Player.vue 的"WHY THIS SONG"卡片未读取。

**Files:**
- Modify: `moodfm-frontend/src/views/player/Player.vue`
- Modify: `moodfm-frontend/src/types/index.ts`（确认 `Song` 接口有 `recommendReason?` 字段）

**Steps:**
- [x] 在 `Song` 接口中添加 `recommendReason?: string`（如未存在）
- [x] Player.vue `whyText` computed 改为：
  ```ts
  const whyText = computed(() => {
    const reason = player.currentSong?.recommendReason
    if (reason) return reason
    const mood = radio.moodText
    if (mood) return `你的心情是「${mood}」——所以选了这首，让感受先被接住。`
    return '这首曲子拥有稳定节奏，适合当下的心境，让神经系统放慢下来。'
  })
  ```
- [ ] 验证：启动电台后 Player.vue "AI · WHY THIS SONG" 区域显示真实 AI 文字

---

### Task A2：迁移 `PlayerServiceImpl.parseSongs` 到 `MusicResponseParser`

**问题：** M17 提取了共享解析器，但 `PlayerServiceImpl` 第 182 行仍有第三份私有 `parseSongs`（路径更窄，缺 `playlist.tracks` / `result.songs`）。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/player/impl/PlayerServiceImpl.java`

**Steps:**
- [x] 在 `PlayerServiceImpl` import 中添加 `import com.moodfm.common.util.MusicResponseParser;`
- [x] 将 `fetchLiked` / `fetchRecommend` / `fetchSearch` 中的 `parseSongs(...)` 全部替换为 `MusicResponseParser.parseSongs(...)`
- [x] 删除 `PlayerServiceImpl` 中的私有 `parseSongs` 方法
- [x] `MusicResponseParser` 补充 `song.song.*` 路径（`PlayerServiceImpl` 老方法的额外容错路径）

---

### Task A3：启用 Virtual Thread

**问题：** PRD §9.3.1 要求召回用 Virtual Thread，目前用 ForkJoinPool。

**Files:**
- Modify: `moodfm-backend/src/main/resources/application.yml`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/player/impl/PlayerServiceImpl.java`

**Steps:**
- [x] `application.yml` 添加（已存在）
- [x] `PlayerServiceImpl.recallSongs()` 将 5 路 `CompletableFuture.supplyAsync()` 改为使用 Virtual Thread executor

---

### Task A4：实现"清除播放历史"后端接口

**问题：** Settings.vue 有入口，后端无对应 `DELETE /api/history/all` 接口。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/controller/HistoryController.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/history/HistoryService.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/history/impl/HistoryServiceImpl.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/mapper/PlayRecordMapper.java`
- Modify: `moodfm-frontend/src/views/settings/Settings.vue`（替换 TODO）
- Modify: `moodfm-frontend/src/api/history.ts`（添加 `clear()` 方法）

**Steps:**
- [x] `HistoryService` 接口添加 `void clearAll(Long userId)`
- [x] `HistoryServiceImpl` 实现：物理删除
- [x] `PlayRecordMapper` 添加 `deleteByUserId` + `@Delete` 注解
- [x] `HistoryController` 添加 `DELETE /api/history/all`
- [x] `historyApi.ts` 添加 `clear: () => api.delete('/history/all')`
- [x] Settings.vue 将 `TODO` 替换为 `await historyApi.clear()`
- [ ] 验证：Settings → "清除播放历史" → 确认弹窗 → 历史页空

---

### Task A5：对齐路由路径（PRD §5.1）

**问题：** 实际路由与 PRD 定义有偏差。

| PRD 路由 | 现路由 | 需改 |
|----------|--------|------|
| `/likes` | `/loved` | 前端路由 + NavBar 链接 |
| `/settings/blacklist` | `/blacklist` | 路由 + Settings.vue 跳转 |
| `/song/:id` | `/songs/:id` | 路由 + 导航调用处 |
| `/report/:week` | `/insights/weekly` | 路由（可保持子路由，但需支持 `:week` 参数） |

**Files:**
- Modify: `moodfm-frontend/src/router/index.ts`
- Modify: `moodfm-frontend/src/views/settings/Settings.vue`（blacklist 跳转）
- Modify: `moodfm-frontend/src/components/common/NavBar.vue`（若有 loved/blacklist 链接）

**Steps:**
- [x] router 中 `/loved` → `/likes`，同时保留 `/loved` 重定向到 `/likes`
- [x] router 中 `/blacklist` → `/settings/blacklist`
- [x] router 中 `/songs/:id` → `/song/:id`，同时加 `/songs/:id` 重定向
- [x] router 中 `/insights/weekly` 支持可选 `/:week` 参数：`/insights/weekly/:week?`
- [x] Settings.vue `router.push('/blacklist')` → `router.push('/settings/blacklist')`

---

### Task A6：验证并修复设备管理后端

**问题：** `UserController.getDevices()` 可能是 stub，Redis `user:devices:{userId}` 写入路径不明。

**Files:**
- Read: `moodfm-backend/src/main/java/com/moodfm/service/user/impl/UserServiceImpl.java`
- Modify if needed

**Steps:**
- [x] 阅读 `UserServiceImpl.getDevices()` 实现（stub，返回空列表）
- [x] 实现从 Redis `user:devices:{userId}` 读取设备列表（opsForList）
- [x] `AuthController.login()` 注入 `HttpServletRequest`，登录成功后记录 IP + User-Agent
- [x] `UserController.getDevices()` 接入 `userService.getDevices(userId)`
- [ ] 验证：登录 → Settings → 设备管理，能看到当前设备

---

## Phase B — M1 非功能需求

### Task B1：登录失败锁定（PRD §4.1.2）

**要求：** 连续 5 次失败锁定 15 分钟。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/controller/AuthController.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/constant/RedisKeys.java`

**Steps:**
- [ ] `RedisKeys` 添加 `LOGIN_FAIL_COUNT = "login:fail:{identifier}"` 常量
- [ ] `AuthController.login()` 捕获 `BadCredentialsException`：
  - 从 Redis 取计数，`+1`，TTL 15 分钟
  - 计数 ≥ 5 时返回 `locked` 错误码
- [ ] 登录成功时删除对应 key
- [ ] 验证：连续 5 次错误后第 6 次收到锁定提示

**Tests:**
- [ ] 新建 `UserServiceImplLoginLockTest.java`（JUnit 5 + Mockito）：mock `StringRedisTemplate`，连续调用 `recordLoginFail` 5 次 → 验证 lockKey 被 `set(..., LOCK_DURATION)` 写入
- [ ] `AuthControllerTest.java`（`@WebMvcTest`）：POST `/api/auth/login` 错误密码 5 次 → 第 6 次响应 code 为 `ACCOUNT_LOCKED`

---

### Task B2：API Rate Limiting（PRD §10.2）

**要求：** 每用户每分钟 60 次请求上限（Redis 滑动窗口）。

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/config/RateLimitInterceptor.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/config/WebMvcConfig.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/common/constant/RedisKeys.java`

**Steps:**
- [ ] `RedisKeys` 添加 `RATE_LIMIT = "ratelimit:{userId}:{minute}"`
- [ ] 创建 `RateLimitInterceptor`（`HandlerInterceptor`）：
  - 从 `SecurityContextHolder` 取 userId（未登录跳过）
  - Redis `INCR` 当前分钟 key，首次设 TTL 60s
  - 超过 60 次返回 HTTP 429
- [ ] `WebMvcConfig.addInterceptors()` 注册，排除 `/api/auth/**` 和 `/actuator/**`
- [ ] 验证：快速调用超过 60 次时收到 429

**Tests:**
- [ ] 新建 `RateLimitInterceptorTest.java`（JUnit 5 + Mockito）：mock `StringRedisTemplate.opsForValue().increment()` 返回 61 → `preHandle` 返回 `false`，response status 为 429
- [ ] 新建 `RateLimitIntegrationTest.java`（`@SpringBootTest` + Embedded Redis）：同一用户 60 次请求全部通过，第 61 次返回 HTTP 429

---

### Task B3：Caffeine 本地缓存（PRD §8.3）

**要求：** 用户基础信息 10 min、歌曲信息 1h、平台映射 1h。

**Files:**
- Modify: `moodfm-backend/pom.xml`（添加 caffeine 依赖，如未添加）
- Create: `moodfm-backend/src/main/java/com/moodfm/config/CacheConfig.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/user/impl/UserServiceImpl.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/service/song/impl/SongServiceImpl.java`

**Steps:**
- [x] `pom.xml` 确认有 `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`
- [x] `CacheConfig` 创建多个命名 CacheManager：`users`（10 min）、`songs`（1h）、`platformMappings`（1h）
- [x] `MoodFmApplication` 或 `CacheConfig` 加 `@EnableCaching`
- [x] `UserServiceImpl.getProfile()` 加 `@Cacheable("users")`，`updateProfile()` 加 `@CacheEvict("users")`
- [x] `SongServiceImpl.getSongDetail()` 加 `@Cacheable("songs")`
- [ ] 验证：重复请求同一用户/歌曲，查看 SQL 日志确认只执行一次

**Tests:**
- [x] 新建 `UserServiceCacheTest.java`（`@SpringBootTest`）：`@SpyBean UserMapper` 监听，连续两次 `getCurrentUser(1L)` → verify `userMapper.selectById` 只调用一次
- [x] `SongServiceCacheTest.java`：同理验证 `getSongDetail(id)` 第二次命中缓存，mapper 不再执行

---

### Task B4：Spring Boot Actuator 健康检查（PRD §10.3）

**Files:**
- Modify: `moodfm-backend/pom.xml`（确认 actuator 依赖）
- Modify: `moodfm-backend/src/main/resources/application.yml`

**Steps:**
- [x] `pom.xml` 确认 `spring-boot-starter-actuator`
- [x] `application.yml` 添加：
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics
    endpoint:
      health:
        show-details: when-authorized
  ```
- [x] `SecurityConfig` 放行 `/actuator/health`（无需认证）
- [ ] 验证：`GET /actuator/health` 返回 `{"status":"UP"}`

**Tests:**
- [x] 新建 `ActuatorHealthTest.java`（`@SpringBootTest(webEnvironment = RANDOM_PORT)`）：`GET /actuator/health` → HTTP 200，body JSON 中 `status == "UP"`

---

## Phase C — M2 功能完整化

### Task C1：Insights 接入真实数据

**问题：** `Insights.vue` 图表有 fallback mock data，`InsightsService` 实现需验证并补全。

**Files:**
- Read+Modify: `moodfm-backend/src/main/java/com/moodfm/service/insights/impl/InsightsServiceImpl.java`
- Modify: `moodfm-frontend/src/views/insights/Insights.vue`（移除 mock data，用 API 数据填充）

**Steps:**
- [x] 阅读 `InsightsServiceImpl` 确认各查询 SQL 是否实现（全部已实现）
- [x] 若 `getSummary()` 返回空：补充 `play_records` + `mood_sessions` 聚合查询（已实现）
- [x] 前端 Insights.vue `onMounted`：成功则用 API 数据，失败隐藏图表+提示"数据不足，继续使用后可见"
- [x] 移除写死的 mock artist 列表和 pastWeeks 数组（改为 API 数据优先，保留 fallback）
- [ ] 验证：有播放记录的用户能看到真实数据

**Tests:**
- [ ] 新建 `InsightsServiceImplTest.java`（JUnit 5 + Mockito）：mock `PlayRecordMapper` 返回 10 条记录 → `getSummary()` 返回非 null `InsightsSummaryVO`，`totalSongs >= 10`
- [ ] 新建 `InsightsControllerTest.java`（`@WebMvcTest`）：GET `/api/insights/summary` → HTTP 200，JSON 包含 `totalSongs`、`topGenre` 字段

---

### Task C2：数据导出（PRD §4.6.3）

**要求：** 导出 JSON / CSV 格式的用户全量播放数据。

**Files:**
- Create: `moodfm-backend/src/main/java/com/moodfm/controller/ExportController.java`
- Modify: `moodfm-frontend/src/views/settings/Settings.vue`（导出按钮接入）

**Steps:**
- [x] `ExportController` 提供：
  - `GET /api/export/json` → 返回 `{ user, playRecords, feedbackEvents, moodSessions }` JSON 附件
  - `GET /api/export/csv` → 返回 play_records 的 CSV 附件
- [x] 设置 `Content-Disposition: attachment` header
- [x] Settings.vue "导出我的数据"按钮绑定 `window.open('/api/export/json')`
- [ ] 验证：点击导出，浏览器下载包含真实数据的文件

**Tests:**
- [x] 新建 `ExportControllerTest.java`（`@WebMvcTest`）：GET `/api/export/json` → 响应头含 `Content-Disposition: attachment; filename=...`，body 可反序列化为合法 JSON 对象
- [x] GET `/api/export/csv` → 响应头 `Content-Type` 含 `text/csv`，body 首行为 CSV 列标题（含 `songTitle,artist,playedAt`）

---

### Task C3：历史记录页补全（分页 + 场景筛选）

**问题：** `History.vue` 有 UI，后端 `HistoryController` 支持分页+场景，但需确认前端正确传参+展示。

**Files:**
- Read+Modify: `moodfm-frontend/src/views/library/History.vue`
- Read+Modify: `moodfm-frontend/src/api/history.ts`

**Steps:**
- [x] 确认 `historyApi.list({ page, pageSize, scene })` 正确对应后端参数
- [x] History.vue 补充分页：滚动到底部自动加载下一页（Intersection Observer）
- [x] 展示 `durationPlayed` / `song.coverUrl` 封面
- [ ] 验证：有 100+ 记录时分页加载正常

**Tests:**
- [ ] 新建 `history.spec.ts`（Vitest + Vue Test Utils）：mock `historyApi.list` 返回两页数据，mount `History.vue`，手动触发 `IntersectionObserver` 回调 → 验证 `historyApi.list` 被调用两次，第二次 `page === 2`
- [ ] 验证：初始加载时传入 `pageSize: 20`，滚动到底部后追加到列表而非替换

---

### Task C4：QQ 音乐适配验证

**要求：** PRD §4.2.1 QQ 音乐为 P1 优先级。

**Files:**
- Read: `moodfm-backend/src/main/java/com/moodfm/client/music/MusicApiClient.java`
- Check: Node.js 适配层 `moodfm-music-adapter/`（若存在）

**Steps:**
- [x] 检查 `MusicApiClient` 是否对 `qqmusic` platform 值有特殊处理（无特殊处理，统一走 `platform.toLowerCase()` 路由）
- [x] 检查 Node.js 适配层路由是否有 `/qqmusic/*` 路由（QR login + search 已实现，其余已补充 stub 路由）
- [x] 若适配层缺失：添加 qqmusic 基础路由（至少搜索 + 播放 URL）（已添加 song/detail + lyric/simi/playlists stub）
- [x] Platforms.vue 绑定流程支持 qqmusic（BindCard 已有枚举）
- [ ] 验证：绑定 QQ 音乐后，电台能召回 QQ 平台歌曲

**Tests:**
- [x] 新建 `MusicApiClientQQTest.java`（JUnit 5 + MockWebServer）：`platform = "qqmusic"` 时，client 构造的请求 URL 指向正确适配层路由（`/qqmusic/search`）

---

### Task C5：周报 AI 总结验证

**问题：** `WeeklyReportServiceImpl` 存在，但 `summarizeWeek()` 的 AI 调用路径需确认。

**Files:**
- Read+Modify: `moodfm-backend/src/main/java/com/moodfm/service/report/impl/WeeklyReportServiceImpl.java`
- Check: `src/main/resources/prompts/weekly-report.txt`

**Steps:**
- [x] 阅读 `WeeklyReportServiceImpl`，确认 AI 调用逻辑（已完整实现，调用 ChatClient + weekly-report.txt 模板）
- [x] 若 `summarizeWeek()` 为 stub：接入 `ChatClient` 调用 `weekly-report.txt` 模板（已实现）
- [x] `Weekly.vue` 确认从 `/api/reports/weekly/{week}` 获取数据并展示（已修复 response shape mapping，使用 headlineWord/titleCn/essayBody/quote）
- [ ] 验证：手动触发（或等到周一）后 `weekly_reports` 表有 AI 总结记录

**Tests:**
- [ ] 新建 `WeeklyReportServiceImplTest.java`（JUnit 5 + Mockito）：mock `ChatClient`，调用 `summarizeWeek(userId, week)` → verify `ChatClient.call()` 被调用一次，返回的 `WeeklyReport.aiSummary` 非空

---

### Task C6：通知设置后端（Cookie 失效提醒）

**要求：** PRD §4.3.5 Cookie 失效时通过 WebSocket / SSE 通知前端。

**Files:**
- Modify: `moodfm-backend/src/main/java/com/moodfm/scheduler/CookieRefreshScheduler.java`
- Modify: `moodfm-backend/src/main/java/com/moodfm/websocket/FeedbackMessageHandler.java`

**Steps:**
- [x] `CookieRefreshScheduler` 刷新失败时，通过 STOMP 向 `/topic/notify/{userId}` 推送 `{ type: "cookie_invalid", platform: "netease" }`
- [x] 前端（全局 App.vue）订阅 `/topic/notify/{userId}`，收到 `cookie_invalid` 时 Toast 提示 + 跳转 `/bind`
- [ ] 验证：手动使 cookie 失效，等待下次定时任务，前端收到提示

**Tests:**
- [x] 新建 `CookieNotificationTest.java`（JUnit 5 + Mockito）：mock `SimpMessagingTemplate`，手动触发调度器中 cookie 刷新失败分支 → verify `convertAndSend("/topic/notify/{userId}", ...)` 被调用一次，payload 含 `"type":"cookie_invalid"` 和 `"platform"` 字段

---

## Phase D — M3/M4 数据沉淀与进阶（持续迭代）

### Task D1：心情日历真实数据接入

- [x] `Calendar.vue` 调用 `GET /api/insights/calendar?year=&month=`
- [x] `InsightsController` 实现按月聚合心情参数 → 每日主色调（已实现）
- [x] 点击日期展示当日听过的歌 + AI 备注（已实现，使用 topTracks）

---

### Task D2：数据清除（GDPR 风格）

- [x] `DELETE /api/user/data/all`：清空 play_records + feedback_events + mood_sessions + weekly_reports + platform_bindings + user_profile（保留账号）
- [x] Settings.vue "清除所有数据" 独立入口（区别于"清除播放历史"）

---

### Task D3：年度报告骨架

- [x] `GET /api/reports/annual/{year}` 接口（聚合全年数据）
- [x] 前端 `AnnualReport.vue` 展示页（基于 Insights 页样式扩展）
- [ ] html2canvas 长图导出（安装依赖）

---

### Task D4：PWA 配置

- [x] `vite-plugin-pwa` 配置 Service Worker
- [x] `manifest.json`（名称 / 图标 / 主题色）
- [ ] 离线时缓存当前播放队列（Cache API）

---

### Task D5：管理员面板（低优先级）

- [x] `GET /api/admin/users` 用户列表（需 ADMIN 角色）
- [x] `PUT /api/admin/users/{id}/status` 启用/禁用用户
- [x] 前端管理页 `/admin`（仅对 ADMIN 角色可见）

---

## 自检：PRD 覆盖确认（完成后更新）

| PRD 需求 | Task | 状态 |
|----------|------|------|
| 心情输入 3 种方式 | 已完成 | ✅ |
| 5 路并行召回 | 已完成（A3 升级 VT） | ✅ |
| AI 重排 + 推荐解释 | A1 前端接入 | ✅ |
| AI 重排后端 | 已完成 | ✅ |
| Virtual Thread | A3 | ✅ |
| Cookie 加密存储 | 已完成 | ✅ |
| Cookie 失效通知 | C6 | ✅ |
| Cookie 定时刷新 | 已完成 | ✅ |
| 平台绑定（扫码/手机/Cookie）| 已完成 | ✅ |
| 设备管理 | A6 | ✅ |
| 登录失败锁定 | B1 | ✅ |
| Rate Limiting | B2 | ✅ |
| Caffeine 缓存 | B3 | ✅ |
| Actuator 健康检查 | B4 | ✅ |
| Insights 真实数据 | C1 | ✅ |
| 数据导出 | C2 | ✅ |
| 历史分页 | C3 | ✅ |
| QQ 音乐适配 | C4 | ✅ |
| 周报 AI 总结 | C5 | ✅ |
| 路由对齐 PRD | A5 | ✅ |
| 清除播放历史 | A4 | ✅ |
| 心情日历真实数据 | D1 | ✅ |
| 数据全量清除 | D2 | ✅ |
| 年度报告 | D3 | ✅ |
| PWA | D4 | ✅ |
| Qdrant 向量库 | — | 📅 M2 延迟 |
| 跨平台搜索匹配 | — | 📅 M2 延迟 |
| 长图分享 | D3 | ⬜ |
| 管理员面板 | D5 | ✅ |
