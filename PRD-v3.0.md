# MoodFM · 产品需求文档 v3.1

> 更新日期：2026-05-16
> 版本说明：在 v3.0 基础上完成管理后台全模块真实后端对接——全局黑名单 CRUD + PlayerService 过滤、Feature Flags/KV 配置 Redis 落地、AI 权重/Prompt Redis 存储、场景模板 MySQL CRUD、通知管理后端接口、平台绑定统计与 Cookie 到期列表、数据分析 5 项真实 SQL、审计日志后端建表

---

## 1. 产品概述

### 1.1 产品定位

MoodFM 是一款以「情绪驱动」为核心理念的 AI 音乐电台应用。用户通过描述当下心情、选择场景预设或拖动心情色盘，由 AI 实时分析情绪参数并从用户绑定的音乐平台（网易云音乐、QQ 音乐）召回、排序、生成个性化播放队列；同时沉淀情绪数据，生成可视化数据洞察与每周 AI 情绪周报。

### 1.2 核心价值主张

1. **情绪即入口**：不需要搜歌名，只需描述此刻感受，AI 自动完成选曲全流程。
2. **平台打通**：一次绑定网易云 / QQ 音乐账号，跨平台召回用户真实收藏与喜好。
3. **越听越懂你**：播放行为（完整收听、跳过、喜欢、音量调高）持续反馈给推荐引擎，队列动态重排。
4. **情绪可视化**：心情曲线、流派雷达、AI 生成周报文字摘要，让音乐消费变成可回溯的情绪档案。

### 1.3 目标用户

- **主力用户**：18-35 岁对音乐有一定品味、愿意探索非主流内容、日常有明显情绪化使用场景（通勤、深夜、专注工作）的年轻用户。
- **次要用户**：已有网易云 / QQ 音乐高价值账号，希望让收藏数据「活起来」的重度乐迷。
- **管理侧**：运营、产品、数据分析人员通过管理后台进行用户管理、内容审核与系统配置。

---

## 2. 功能模块总览

| 模块 | 描述 | 优先级 | 实现状态 |
|------|------|--------|----------|
| 用户注册与登录 | 邮箱/手机注册、JWT 鉴权、设备记录 | P0 | 已实现 |
| 音乐平台绑定 | 网易云/QQ 音乐 Cookie 绑定、有效期管理 | P0 | 已实现 |
| 情绪电台（核心） | AI 分析心情 → 6 路并行召回 → 重排 → 播放队列 | P0 | 已实现 |
| 播放器 | 播放/暂停/下一首/反馈/队列展示 | P0 | 已实现 |
| 个人音乐库 | 播放历史、喜欢列表、智能播放列表、黑名单 | P1 | 已实现 |
| 数据洞察 | 心情曲线、流派雷达、TOP 艺人、AI 周报 | P1 | 已实现 |
| 心情日历 | 月度心情日历（Beta） | P2 | 已实现（Feature Flag 控制） |
| 用户设置 | 头像/昵称、平台管理、通知偏好、数据导出 | P1 | 已实现 |
| 管理员仪表盘 | 总用户、DAU、累计播放、收听时长、DAU 折线图 | P1 | 已实现（KPI 指标真实；健康监控/最近注册用户仍为静态） |
| 用户管理（Admin） | 列表/搜索/封禁/角色修改 | P1 | 已实现 |
| 音乐管理（Admin） | 全局歌手/歌曲/关键词黑名单 CRUD | P1 | 已实现（后端 GlobalBlacklist 表 + PlayerService 全局过滤） |
| 平台管理（Admin） | 双平台健康监控、Cookie 到期用户列表 | P2 | 已实现（绑定统计 + Cookie 到期列表真实；延迟/状态监控仍为静态） |
| AI 引擎配置（Admin） | 推荐权重、场景模板、Prompt 模板编辑 | P2 | 已实现（权重/Prompt 接入 Redis；场景模板 CRUD 接入 scene_template 表） |
| 数据分析（Admin） | DAU 趋势、流派分布、平台占比、心情分布、TOP 歌曲 | P2 | 已实现（全部 5 项统计接入真实后端 SQL） |
| 通知管理（Admin） | 撰写通知、快速模板、发送历史、打开率统计 | P2 | 已实现（通知 CRUD + 立即发送接入后端；实际推送服务规划中） |
| 系统设置（Admin） | Feature Flags、KV 配置、管理员账号、审计日志 | P2 | 已实现（Flags/KV 读写 Redis；审计日志后端建表并记录操作） |
| 向量召回 | 基于 Qdrant 的语义相似歌曲召回 | P2 | 已实现 |
| 歌曲种子电台 | 基于单首歌启动相似电台 | P2 | 已实现 |

---

## 3. 用户端功能详情

### 3.1 用户注册与登录

- 支持邮箱注册（用户名 + 邮箱 + 密码）
- 支持手机号注册 + 短信验证码登录（`/api/auth/sms/send`）
- JWT 双 Token 体系：
  - Access Token：默认 30 分钟有效期（配置 `access-token-expire-ms: 1800000`）
  - Refresh Token：默认 7 天；「记住我」模式 30 天
- 登录时记录设备 IP 与 User-Agent
- 账号状态字段 `status`：1=正常，0=软删除/封禁；`lockUntil` 字段支持临时锁定
- 邮箱验证字段 `emailVerified`（已建模，验证流程规划中）
- 登出接口同时将 Access Token 与 Refresh Token 加入黑名单（Redis）

### 3.2 音乐平台绑定

- 支持平台：`netease`（网易云音乐）、`qqmusic`（QQ 音乐）
- 绑定方式：
  - Cookie 手动粘贴绑定（由 Feature Flag `cookie_login` 控制）
  - 二维码扫码绑定（由 Feature Flag `qr_login` 控制，二维码超时 90 秒）
- Cookie 使用 AES-256-GCM 加密存储于 `platform_bindings.cookie_encrypted`
- 每个用户可绑定多个平台账号，设置一个默认平台（`is_default=1`）
- 有效期字段 `expires_at`、最后验证时间 `last_validated_at`、有效标志 `is_valid`
- 平台绑定有效性检测：`getValidBinding` 方法，失效时抛出业务异常

### 3.3 情绪电台（核心功能）

#### 3.3.1 启动方式

| 方式 | 描述 |
|------|------|
| 方式 A · 自由文字 | 用户输入自然语言心情描述，AI 解析为结构化 MoodParams |
| 方式 B · 场景预设 | 通勤、学习、跑步、写作、睡前、派对、深夜 等快捷标签 |
| 方式 C · 心情色盘 | SVG 二维拖拽（情绪 valence × energy 坐标系） |
| Just Play | 随机启动，无需任何输入 |
| 种子歌曲 | 从某首歌出发，生成相似风格电台 |
| 继续上次 | 从首页直接恢复最近一次 MoodSession |

#### 3.3.2 推荐管道

1. **AI 情绪分析**：LLM 将原始输入解析为 `MoodParams`（valence、energy、vibeKeywords、preferredGenres、sceneInferred、energyCurve、avoidKeywords 等）
2. **6 路并行召回**（Java Virtual Thread）：
   - 用户收藏歌曲（liked）
   - 平台个性推荐（recommend）
   - 流派关键词搜索（genre-search）
   - 氛围关键词搜索（vibe-search）
   - 探索新歌搜索（explore-search）
   - 向量语义召回（Qdrant，基于 MoodParams 构建嵌入查询）
3. **反馈过滤**：过滤近 200 条历史中严重负分歌曲（跳过 < 30s × -3 权重）
4. **黑名单关键词过滤**：从 UserProfile 读取用户个人黑名单关键词，大小写不敏感子串匹配
5. **AI 重排**：将去重后候选（最多 60 首）送入 LLM，按 `song-ranking.txt` Prompt 模板输出带推荐理由的排序列表（目标 15-20 首）
6. **播放 URL 批量获取**：单次 API 调用批量拉取所有歌曲播放链接
7. **队列写入 Redis**：原子事务替换，TTL 2 小时

#### 3.3.3 播放队列动态管理

- **动态重排（Feature 1）**：每消费 3 首触发异步重排，将最新候选预插队列头部
- **队列自动补充（Feature 2）**：队列剩余 < 5 首时，后台异步补充 20 首
- **歌曲持久化（Feature 3）**：新歌曲自动入库 `songs` 表 + `platform_song_mapping`，并异步写入 Qdrant 向量索引

#### 3.3.4 会话时长控制

- 用户可选择：15 分钟 / 30 分钟 / 60 分钟 / 120 分钟 / 无限
- 会话到期通过 Redis Key TTL 实现，过期后 `isSessionExpired` 返回 true

### 3.4 播放器

- 基于 Howler.js 实现音频播放（浏览器端）
- 操作：播放/暂停、下一首、喜欢（like）、拉黑（dislike）
- 每次播放行为上报 `FeedbackEvent`（事件类型：play / skip / like / dislike / volume_up）
- 上报 `PlayRecord`（已播放秒数、总时长、操作结果）
- 迷你播放器固定在页面底部，全局常驻

### 3.5 个人音乐库

- **播放历史**（`/history`）：按时间倒序展示，含歌曲、艺人、时长、播放时间
- **喜欢列表**（`/likes`）：收藏的歌曲，支持从播放器直接添加
- **个人黑名单**（`/settings/blacklist`）：
  - 黑名单歌手：存于 `user_profiles.blacklist_artists`（JSON 数组）
  - 黑名单歌曲：存于 `user_profiles.blacklist_songs`（JSON 数组）
  - 黑名单关键词：存于 `user_profiles.blacklist_keywords`（JSON 数组），影响召回过滤
- **普通播放列表**（`/playlists`）：用户手动创建的歌单
- **智能播放列表**（`/playlists/smart/:type`）：基于规则自动生成，如「近 7 天最常听」等
- **歌曲详情**（`/song/:id`）：单首歌详细信息

### 3.6 数据洞察

入口路径：`/insights`，支持 7 天 / 30 天 / 90 天切换。

| 图表/模块 | 描述 | 数据来源 |
|-----------|------|----------|
| 心情摘要卡片 | 总收听时长、TOP 流派、AI 准确率、心情主旋律 | InsightsService |
| 心情曲线（FIG.01） | 用户心情分 vs 听歌情绪分折线，双 Y 轴 | MoodTrendVO |
| 流派雷达（FIG.02） | 8 维流派分布雷达图 | GenreRadarItemVO |
| TOP 艺人（FIG.03） | Top 5 艺人 + 收听时长 + 占比进度条 | TopItemsVO |
| AI 周报摘要 | 由 LLM 生成的情绪文字摘要，含标题和正文 | WeeklyReport.aiSummary |
| 历史周报 | 历史各期周报卡片 | WeeklyReport 表 |
| 分享长图 | 使用 html2canvas 截图并下载为 PNG | 前端本地生成 |

子页面：
- `/insights/calendar`：月度心情日历，每天标注情绪颜色（Beta，Feature Flag 控制）
- `/insights/weekly/:week?`：完整版每周报告
- `/insights/annual/:year?`：年度报告

### 3.7 用户设置

- **个人资料**（`/profile`）：头像上传（MIME 白名单 + 5MB 限制）、昵称修改
- **平台管理**（`/settings/platforms`）：查看绑定状态、重新绑定、切换默认平台
- **通知偏好**（`/settings/notifications`）：
  - 每周报告推送（`weeklyReport`）
  - Cookie 到期提醒（`cookieExpiry`）
  - 新功能通知（`newFeatures`）
  - 存储于 `user_profiles.notification_prefs`（JSON）
- **数据导出**（`/api/export/`）：支持导出播放记录等个人数据

### 3.8 用户偏好

- 流派偏好（`genreWeights`）：JSON 数组，e.g. `["Ambient","Folk","Indie"]`
- 语言偏好（`languagePreferences`）：JSON 数组，e.g. `["中文","English"]`
- 偏好会在每次召回时合并进搜索关键词，强化推荐契合度

---

## 4. 管理后台功能详情

后台路由前缀：`/admin`，需 `ADMIN` 角色，前端路由守卫 `requiresAdmin: true`。

### 4.1 仪表盘（Dashboard）—— `/admin/dashboard`

**真实数据接口**（`/api/admin/stats`、`/api/admin/dashboard/activity`）：

| 指标 | 说明 | 数据来源 |
|------|------|----------|
| 总用户数 | `users` 表总计 | UserMapper.selectCount |
| 日活用户（DAU） | 当日有播放记录的独立用户数 | PlayRecordMapper，按 `played_at` 分组 |
| 累计播放次数 | `play_records` 表总条数 | PlayRecordMapper.selectCount |
| 总收听时长（小时） | SUM(played_seconds) / 3600 | PlayRecordMapper.sumAllPlayedSeconds |
| 今日新增用户 | 当天注册用户数 | UserMapper.countNewUsersOnDate |
| DAU 折线数据 | 最近 N 天（1-90）每日 DAU | PlayRecordMapper.selectDailyDau |

**前端 Mock 数据**（待后端接入）：
- 系统健康状态（API 服务、推荐引擎、任务队列、数据库、CDN、邮件服务）
- 平台接入状态（网易云、QQ 音乐延迟/绑定数/Cookie 待刷新）
- 最近注册用户列表（Dashboard 小组件）
- DAU/MAU 双轴折线图（MAU 轴数据为 Mock）

**告警卡片**：Cookie 到期提醒、AI 模型状态（当前为前端静态）

### 4.2 用户管理 —— `/admin/users`

**已实现后端接口**（`/api/admin/users`）：

- 用户列表（分页，默认每页 50，最大 100）：展示 id、username、email、phone、role、status、createdAt
- 启用/禁用用户（`PUT /api/admin/users/{id}/status`）：status 0/1
- 角色修改（`PUT /api/admin/users/{id}/role`）：USER / ADMIN
- 最近注册用户（`GET /api/admin/users/recent?limit=5`）

**前端额外功能**（部分为 Mock）：
- 搜索/筛选用户
- 用户详情弹窗

### 4.3 音乐管理（全局黑名单）—— `/admin/music`

数据表：`global_blacklist`，字段 `type`（artist / song / keyword）、`value`、`artist`、`scope`、`reason`、`addedBy`。

- **歌手黑名单**：添加/删除/搜索，影响全局召回过滤
- **歌曲黑名单**：按曲名 + 艺人添加/删除
- **关键词黑名单**：支持设置匹配范围（标题/全字段/专辑名/风格标签）

> **v3.1 已实现**：后端 `GlobalBlacklist` 表 CRUD（`AdminContentController`）、`PlayerServiceImpl.filterGlobalBlacklist()` 在召回阶段过滤全局禁止歌手/歌曲/关键词；前端 `AdminMusic.vue` 完全对接 `blacklistAdminApi`。

### 4.4 平台管理 —— `/admin/platforms`

- 双平台（网易云/QQ 音乐）在线状态监控（延迟、可用率、绑定用户数）
- Cookie 待刷新用户数展示
- 发送 Cookie 刷新提醒 / 强制刷新操作（规划中）

> **v3.1 已实现**：`AdminPlatformController` 提供 `GET /api/admin/platforms/stats`（从 `platform_bindings` 按平台统计绑定数/即将到期/已过期）和 `GET /api/admin/platforms/cookie-expiry`（列出 7 天内到期的 Cookie 用户）；前端 `AdminPlatforms.vue` 完全对接 `platformAdminApi`。平台延迟/可用率监控仍为前端静态展示。

### 4.5 AI 引擎配置 —— `/admin/ai`

- 推荐权重调节（预设 6 个维度）
- 场景模板管理（启用/禁用/编辑场景预设，对应 `MoodParams.sceneInferred` 映射）
- Prompt 模板编辑（`song-ranking.txt` 内容可视化编辑）

> **v3.1 已实现**：`AdminConfigController` 将推荐权重与 Prompt 模板存储于 Redis（`admin:ai_weights`、`admin:ai_prompt`）；`AdminSceneController` 管理 `scene_template` 表（`V4` 迁移脚本预置 7 个场景），支持新增/编辑/启用切换；前端 `AdminAIEngine.vue` 完全对接 `aiConfigAdminApi` + `sceneAdminApi`。

### 4.6 数据分析 —— `/admin/analytics`

- DAU/MAU 趋势（7d/30d/90d，部分接入 `/api/admin/dashboard/activity` 真实 DAU）
- 流派分布条形图
- 平台使用占比
- 心情分布雷达图
- TOP 5 播放歌曲

> **v3.1 已实现**：`AdminAnalyticsController` 提供 5 个统计接口；`PlayRecordMapper` 新增 `selectAdminGenreCounts`、`selectAdminPlatformCounts`、`selectAdminTopSongs`；`MoodSessionMapper` 新增 `selectAdminMoodDist`（6 象限 CASE WHEN）；前端 `AdminAnalytics.vue` 移除全部 Mock 生成器，完全接入 `analyticsAdminApi`；MAU 轴已移除（无历史 MAU 数据）。

### 4.7 通知管理 —— `/admin/notifications`

- **撰写通知**：标题、正文、类型（系统/周报/新功能/推送）、目标用户（全体/Cookie 到期/近 7 日新注册/30 日不活跃）、立即发送或定时发送
- **快速模板**：预设 4 个模板（周报生成通知、Cookie 过期提醒、新功能上线、系统维护通知）
- **发送历史**：历史记录列表，含发送数、打开数、打开率统计

> **v3.1 已实现**：`AdminContentController` 提供通知 CRUD（`admin_notification` 表）和立即发送接口（标记 `status=sent`，`sentCount` 模拟 1000）；前端 `AdminNotifications.vue` 完全对接 `notificationsAdminApi`；实际推送服务（APNs/FCM）规划接入。

### 4.8 系统设置 —— `/admin/system`

**Feature Flags**（计划存入 Redis，有默认值）：

| Key | 说明 | 默认 |
|-----|------|------|
| ai_recommendations | AI 个性推荐引擎开关 | 开 |
| weekly_report | 自动生成周报 | 开 |
| cross_platform_sync | 跨平台歌单同步 | 开 |
| share_long_image | 长图分享功能 | 开 |
| qr_login | 扫码登录绑定 | 开 |
| cookie_login | Cookie 高级登录 | 开 |
| mood_calendar | 心情日历（Beta） | 关 |
| social_profile | 公开个人主页 | 关 |
| maintenance_mode | 维护模式 | 关 |

**KV 配置**（计划存入 Redis，有默认值）：

| Key | 默认值 | 说明 |
|-----|--------|------|
| recommendation.count | 15 | 每次电台推荐歌曲数 |
| recommendation.replan_after | 3 | 每隔 N 首重排队列 |
| session.max_duration_hours | 4 | 单次会话最长时长（小时） |
| cookie.expiry_warn_days | 3 | Cookie 到期前 N 天发警告 |
| qrcode.timeout_seconds | 90 | 二维码超时秒数 |
| report.generation_day | 1 | 周报生成日（0=周日） |
| blacklist.max_per_user | 200 | 每用户最大黑名单条数 |

**管理员账号管理**：列表展示（超级管理员/运营/分析师角色）、新增、启用/禁用。

**操作审计日志**：记录管理员操作（操作内容、管理员、对象、级别 ok/warn/info、时间）。
> **v3.1 已实现**：`admin_audit_log` 表已建立（`V3` 迁移脚本）；`AdminContentController.writeAudit()` 在黑名单增删、通知发送等操作时自动写入；`GET /api/admin/audit-log?limit=50` 接口已对接前端 `AdminSystem.vue`。

---

## 5. 权限体系

| 角色 | 说明 | 可访问 |
|------|------|--------|
| USER | 普通用户 | 用户端所有功能（`/home`、`/player`、`/insights` 等） |
| ADMIN | 管理员 | 用户端全部功能 + `/admin/*` 管理后台全部模块 |

- **后端**：Spring Security，`/api/admin/**` 接口通过 `hasRole("ADMIN")` 保护
- **前端**：路由守卫 `meta: { requiresAdmin: true }`，非 ADMIN 角色跳转至 `/home`
- **Token 黑名单**：登出后 Token 存入 Redis 黑名单，防止重放

---

## 6. 技术架构

### 6.1 技术栈

| 层级 | 技术 | 版本/说明 |
|------|------|-----------|
| 前端框架 | Vue 3 + TypeScript | 3.4.x + TS 5.4 |
| 前端构建 | Vite | 5.4.x |
| 前端状态 | Pinia | 2.1.x |
| 前端路由 | Vue Router | 4.3.x |
| 前端图表 | ECharts + vue-echarts | 5.5.x + 6.7.x |
| 前端音频 | Howler.js | 2.2.x |
| 前端截图 | html2canvas | 1.4.x（洞察分享长图） |
| 前端 WebSocket | @stomp/stompjs | 7.0.x |
| 前端 PWA | vite-plugin-pwa | 0.20.x |
| 前端工具库 | @vueuse/core、dayjs、axios | - |
| 后端框架 | Spring Boot 3 | 3.3.6 |
| 后端运行时 | Java 21（Virtual Threads 已启用） | - |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL 8 | - |
| 缓存（分布式） | Redis（Lettuce 连接池） | - |
| 缓存（本地） | Caffeine（users=10min，songs/platformMappings=1h） | - |
| AI 接入 | Spring AI 1.1.6（OpenAI 兼容接口，默认接 DeepSeek） | - |
| 向量库 | Qdrant（gRPC 接入） | 客户端 1.12.0 |
| 文本嵌入 | 智谱 AI embedding-3 | 可配置换用其他模型 |
| 鉴权 | JWT（JJWT 0.12.6），双 Token 体系 | - |
| 熔断降级 | Resilience4j | 2.2.0 |
| 实时通信 | Spring WebSocket + STOMP | - |
| API 文档 | SpringDoc OpenAPI 3（Swagger UI 挂 /docs） | 2.6.0 |
| 音乐 API 适配器 | moodfm-music-adapter（独立 Node.js 服务） | 默认端口 3000 |

### 6.2 数据库表概览

| 表名 | 说明 |
|------|------|
| `users` | 用户主表，含角色、状态、锁定时间 |
| `user_profiles` | 用户偏好扩展表（1:1），存流派偏好、语言偏好、黑名单、通知偏好 |
| `platform_bindings` | 平台绑定（1用户:N平台），含加密 Cookie、有效期 |
| `songs` | 歌曲主表，含音乐特征 JSON（genre/bpm/energy/language/valence） |
| `platform_song_mapping` | 歌曲 ID 与平台内部 ID 的映射 |
| `mood_sessions` | 每次电台会话记录，含原始输入、MoodParams JSON、场景、时长 |
| `play_records` | 播放记录，含已播放秒数、总时长、操作（completed/skipped/liked）|
| `feedback_events` | 细粒度反馈事件（play/skip/like/dislike/volume_up），附 JSON 数据 |
| `weekly_reports` | 每周情绪报告，含完整 VO JSON 和 AI 生成摘要 |
| `global_blacklist` | 全局黑名单（type: artist/song/keyword），由管理员维护 |
| `admin_audit_log` | 管理员操作审计日志（operator/operation/module/level/created_at） |
| `admin_notification` | 通知记录（title/body/type/target_group/status/scheduled_at/sent_at/sent_count/opened_count） |
| `scene_template` | AI 引擎场景模板（key/name/cn/active/songs/accuracy），预置 7 个场景 |

平台扩展字段（v3.1 迁移）：
- `platform_bindings.cookie_expires_at`：Cookie 过期时间（用于到期告警）

---

## 7. 数据模型（核心表字段说明）

### users
`id`、`username`、`email`、`phone`、`password_hash`、`avatar_url`、`status`（1=正常/0=软删除）、`role`（USER/ADMIN）、`email_verified`、`lock_until`、`created_at`、`updated_at`

### user_profiles
`user_id`（与 users.id 一致）、`genre_weights`（JSON 数组）、`language_preferences`（JSON 数组）、`blacklist_artists`（JSON 数组）、`blacklist_songs`（JSON 数组）、`blacklist_keywords`（JSON 数组）、`notification_prefs`（JSON 对象）、`updated_at`

### platform_bindings
`id`、`user_id`、`platform`（netease/qqmusic/spotify）、`platform_username`、`cookie_encrypted`（AES-256-GCM）、`is_default`、`is_valid`、`last_validated_at`、`expires_at`、`created_at`、`updated_at`

### mood_sessions
`id`、`user_id`、`raw_input`、`mood_params`（JSON，含 MoodVector+vibeKeywords 等）、`scene`、`started_at`、`duration_minutes`

### songs
`id`、`title`、`artist`、`album`、`duration_seconds`、`cover_url`、`features`（JSON：`{genre, bpm, energy, language, valence}`）、`created_at`、`updated_at`

### play_records
`id`、`user_id`、`session_id`、`song_id`、`platform`、`played_seconds`、`total_seconds`、`action`（completed/skipped/liked/disliked）、`played_at`

### feedback_events
`id`、`user_id`、`session_id`、`song_id`、`event_type`（play/skip/like/dislike/volume_up）、`event_data`（JSON，如 `{playedSeconds: N}`）、`created_at`

---

## 8. 非功能需求

### 8.1 性能目标

- 普通 API 响应时间 P99 < 500ms
- 电台推荐生成（startRadio）< 3 秒（6 路并行召回 + AI 重排）
- 并发召回超时保护：12 秒兜底，超时取已完成部分
- 支持并发 1000 用户（目标）
- Redis 连接池：最大活跃连接 20

### 8.2 安全要求

- Access Token 默认 30 分钟过期，Refresh Token 7 天
- 管理员接口需 `ADMIN` 角色，Spring Security `hasRole("ADMIN")` 保护
- 平台 Cookie 使用 AES-256-GCM 加密存储，密钥通过环境变量注入（`COOKIE_ENCRYPTION_KEY`）
- JWT Secret 通过环境变量注入（`JWT_SECRET`），不得硬编码
- CORS 白名单从环境变量读取，启动时自动 trim 并 fail-fast 验证（`CORS_ALLOWED_ORIGINS`）
- 文件上传：MIME 类型白名单过滤 + 5MB 大小限制
- Token 登出黑名单：Redis 存储已失效 Token

### 8.3 可用性

- 各召回路径独立 try-catch，单路失败不影响整体推荐
- Qdrant 向量召回失败静默降级（返回空列表）
- Spring AI 调用失败兜底：返回打乱后的前 20 首候选
- Resilience4j 熔断降级配置（具体阈值待调优）
- Spring Actuator 暴露 health/info/metrics 端点

---

## 9. 版本规划

| 版本 | 主要内容 | 状态 |
|------|---------|------|
| v1.0 | 基础音乐播放、平台绑定（网易云/QQ）、用户注册登录 | 已完成 |
| v2.0 | AI 情绪电台核心管道（6路召回/重排/反馈）、播放历史、黑名单、数据洞察、周报 | 已完成 |
| v3.0 | 管理后台 8 模块前端实现（Dashboard/用户/音乐/平台/AI/分析/通知/系统）、数据库表结构设计、Feature Flags 体系设计 | 已完成 |
| v3.1 | 管理后台全模块真实后端：全局黑名单 CRUD + PlayerService 过滤、Feature Flags/KV Redis 落地、AI 权重/Prompt/场景模板、通知后端、平台绑定统计、数据分析 5 项 SQL、审计日志建表 | 已完成 |
| v4.0（规划） | 社交功能、公开个人主页、多语言支持、Spotify 平台接入、实际推送服务（APNs/FCM） | 规划中 |

---

## 10. 已知技术债务 / 待优化项

### 10.1 遗留 Mock / 待接入真实后端

| 功能点 | 当前状态 | 说明 |
|--------|----------|------|
| 平台健康监控（延迟/可用率） | 前端静态 | 无服务端健康探针；展示硬编码数值 |
| Dashboard 最近注册用户小组件 | 前端静态 Mock | `/api/admin/users/recent` 接口已存在，Dashboard 前端未对接 |
| 通知实际推送 | 模拟发送 | `sentCount` 硬写 1000，未对接 APNs/FCM/WebPush |
| 管理员账号管理 | 前端静态 | 超级管理员/运营/分析师角色管理尚未建表实现 |

### 10.2 功能设计缺口

- **邮箱验证流程**：`email_verified` 字段已建模，注册后邮箱验证逻辑尚未实现。
- **短信 OTP**：`/api/auth/sms/send` 接口路由已声明，发送逻辑待接入真实短信服务商。
- **Spotify 平台**：`platform_bindings.platform` 字段注释中已预留 `spotify`，适配器代码尚未实现。
- **心情色盘坐标映射**：`MoodWheel` 组件 `onWheelChange` 回调目前为空（`// Future: map coordinates to mood preset`）。
- **今日推荐卡片**：Home 页「今日推荐」为纯前端时间段文字，未对接后端推荐接口。
- **跨平台歌单同步**：Feature Flag `cross_platform_sync` 已定义，具体同步逻辑未实现。
- **公开个人主页**：Feature Flag `social_profile` 已定义，功能未实现。
- **WebSocket 实时推送**：依赖已引入（STOMP + WebSocket），具体推送场景未完全接入。

### 10.3 性能/工程优化

- 管理员用户列表使用 `LIMIT ... OFFSET` 深翻页性能问题，高页码查询需改为游标分页。
- `MoodAnalysisService.analyze()` 同步调用 LLM，若 LLM 响应慢会直接阻塞 startRadio 流程，可考虑加超时熔断。
- 歌曲持久化的批量 OR 查询（`WHERE (title=? AND artist=?) OR ...`）在候选数多时语句膨胀，可改为 IN + 应用层合并。
- Qdrant 向量索引为播放时异步写入，初期数据库数据稀少时向量召回质量有限，需考虑冷启动策略。
