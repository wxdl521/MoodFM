# MoodFM · 心绪电台 — 产品需求文档（PRD v2.0）

> **一句话定位**：基于多平台音乐账号 + AI 情绪理解的私人电台，让每一首歌都恰好是你此刻需要的。

> **v2.0 更新说明**：技术栈整体替换为 Spring Boot 3 + Java 21 + Vue 3 + Tailwind CSS + MySQL 8 + Spring AI，更贴近本项目实际开发环境。

---

## 目录

1. [项目概述](#1-项目概述)
2. [目标用户与场景](#2-目标用户与场景)
3. [用户角色与权限](#3-用户角色与权限)
4. [核心功能模块](#4-核心功能模块)
5. [页面与界面清单](#5-页面与界面清单)
6. [核心组件清单](#6-核心组件清单)
7. [关键用户流程](#7-关键用户流程)
8. [数据模型](#8-数据模型)
9. [技术架构](#9-技术架构)
10. [非功能需求](#10-非功能需求)
11. [MVP 路线图](#11-mvp-路线图)
12. [风险与边界](#12-风险与边界)

---

## 1. 项目概述

### 1.1 项目背景

现有音乐平台的"心情推荐"普遍存在三个问题：

- **理解浅**：只能基于单一标签（"伤感""励志"）匹配，无法捕捉用户当下的复合状态
- **算法封闭**：用户无法干预、无法解释，推什么是黑盒
- **跨平台割裂**：用户的音乐资产分散在网易云、QQ 音乐等平台，无法统一调度

### 1.2 产品定位

MoodFM 是一个**自托管的多用户 AI 音乐电台平台**。每位用户绑定自己的音乐平台账号（网易云 / QQ 音乐），通过自然语言或场景输入表达当下心情，AI 综合用户画像、心情语义、上下文环境（时间、天气、历史）生成连续电台流，并在播放过程中根据实时反馈动态调整后续曲序。

### 1.3 核心价值主张

| 维度 | 价值 |
|------|------|
| **AI 含金量** | 心情语义化 + 多路召回 + 向量检索 + 曲序规划 + 反馈学习，AI 贯穿全链路 |
| **个人化** | 调用用户自己的歌单、红心、播放历史作为推荐基础，越用越懂你 |
| **跨平台统一** | 一次登录，调度网易云 / QQ 音乐资源，统一播放体验 |
| **数据沉淀** | 听歌行为 + 心情数据双重沉淀，输出周报 / 月报 / 个人音乐画像 |
| **隐私可控** | 自部署、Cookie 加密存储、用户数据不外传 |

### 1.4 项目代号

- **中文名**：心绪电台
- **英文名**：MoodFM
- **设计 tone**：深夜电台 × 私人专属 × 极简氛围感（视觉细节由设计方决定）

---

## 2. 目标用户与场景

### 2.1 目标用户画像

- **核心用户**：18-30 岁，重度音乐用户（每日听歌 1 小时以上）
- **特征**：拥有至少一个音乐平台会员，有跨平台听歌习惯，对推荐算法不满
- **典型用户**：
  - 学生（学习/通勤/睡前听歌）
  - 内容创作者（写作/设计需要背景音乐）
  - 加班党（需要根据状态切换歌单）

### 2.2 核心使用场景

| 场景 | 用户行为 | 期望体验 |
|------|---------|---------|
| **通勤路上** | 打开 App → 一键开始电台 | AI 自动识别时间/位置，推送适合通勤的能量曲线 |
| **加班疲惫** | 输入"累但还得撑两小时" | 推荐能保持专注但不催眠的歌单 |
| **深夜情绪** | 输入"今晚想要 emo 一下" | 从用户红心歌单中筛 emo 系，加少量探索新歌 |
| **运动健身** | 选择"跑步"场景预设 | 高 BPM、强节奏，曲序按运动阶段设计 |
| **复盘回顾** | 进入"我的画像"页面 | 看到本周心情曲线 + 听歌轨迹 + AI 总结 |

---

## 3. 用户角色与权限

### 3.1 角色定义

| 角色 | 描述 | 权限 |
|------|------|------|
| **游客** | 未登录用户 | 仅可查看产品介绍页、注册、登录 |
| **普通用户** | 注册并登录的用户 | 使用全部功能、管理自己的数据 |
| **管理员** | 系统部署者 | 用户管理、系统监控、配置管理 |

### 3.2 权限矩阵

| 功能 | 游客 | 普通用户 | 管理员 |
|------|:----:|:--------:|:------:|
| 浏览首页 | ✅ | ✅ | ✅ |
| 注册/登录 | ✅ | - | - |
| 绑定音乐账号 | ❌ | ✅ | ✅ |
| 使用 AI 电台 | ❌ | ✅ | ✅ |
| 查看个人画像 | ❌ | ✅ | ✅ |
| 用户管理 | ❌ | ❌ | ✅ |
| 系统监控 | ❌ | ❌ | ✅ |

### 3.3 认证机制

- 基于 Spring Security 6 + JWT
- Access Token 有效期 30 分钟
- Refresh Token 有效期 7 天（"记住我"延长至 30 天）
- 密码使用 BCrypt 加密
- 支持多设备登录，Token 信息存于 Redis 便于强制下线

---

## 4. 核心功能模块

### 4.1 用户系统（M1）

#### 4.1.1 注册

- **方式**：邮箱 + 密码 / 手机号 + 验证码（OTP，开发环境控制台输出）
- **校验**：邮箱格式、密码强度（≥8 位，含字母+数字）
- **流程**：注册 → 邮箱验证（可选）→ 进入引导页
- **字段**：用户名（昵称）、邮箱/手机、密码

#### 4.1.2 登录

- **方式**：邮箱/手机号 + 密码
- **会话**：JWT token（access 30min + refresh 7d）
- **失败处理**：连续 5 次失败锁定 15 分钟
- **记住我**：可选，延长 refresh token 至 30d

#### 4.1.3 个人资料

- 头像上传（支持本地上传 / 从绑定的音乐平台同步）
- 昵称修改
- 邮箱/手机绑定与修改
- 密码修改
- 注销账号（软删除 + 30 天冷却）

#### 4.1.4 安全设置

- 登录设备管理（查看 / 强制下线，基于 Redis 中的 token 列表）
- 登录历史记录
- 两步验证（可选，TOTP）

---

### 4.2 音乐平台账号绑定（M1，核心）

> **这是项目最关键也最敏感的模块**。每个用户必须绑定至少一个音乐平台账号才能使用电台功能。

#### 4.2.1 支持的平台

| 平台 | 优先级 | 接入方式 |
|------|--------|---------|
| 网易云音乐 | P0 | NeteaseCloudMusicApi (Node.js 微服务) |
| QQ 音乐 | P1 | QQMusicApi 类开源方案 (Node.js 微服务) |
| Spotify | P2（海外用户）| 官方 OAuth |
| YouTube Music | P3 | 第三方包装 |

#### 4.2.2 绑定流程（网易云示例）

**方式一：扫码登录（推荐）**

1. 用户点击"绑定网易云" → Spring Boot 后端调用 Node.js 微服务生成二维码
2. 前端展示二维码 + 倒计时（90 秒）
3. 用户用网易云 App 扫码确认
4. 后端轮询登录状态（每 2 秒一次，可使用 SSE 推送给前端）
5. 成功后获取 cookie → AES-256-GCM 加密存储到 MySQL → 显示绑定成功

**方式二：手机号 + 密码登录**

1. 用户输入手机号 + 密码
2. 后端调用登录接口
3. 可能触发短信验证码 → 用户输入
4. 获取 cookie → 加密存储

**方式三：手动 Cookie 输入（高级用户）**

1. 用户提供从浏览器导出的 cookie
2. 后端验证 cookie 有效性
3. 成功后存储

#### 4.2.3 Cookie 管理

- **加密存储**：使用 AES-256-GCM 加密，密钥由 Spring Boot 环境变量管理（推荐通过 `application.yml` + 环境变量注入）
- **有效性检测**：每次使用前验证，失效时通过 SSE / WebSocket 通知前端
- **定时刷新**：基于 `@Scheduled` 定时任务，每 24h 调用刷新接口延长有效期
- **隔离原则**：每用户每平台独立 cookie，**绝不共享**
- **安全展示**：前端只显示"已绑定"状态 + 部分掩码（如 `****abcd`），不展示完整 cookie

#### 4.2.4 多平台共存

- 一个用户可同时绑定多个平台
- 设置默认平台（影响默认音源）
- 单首歌曲优先级：默认平台 > 已绑定平台 > 跨平台搜索匹配

#### 4.2.5 解绑

- 解绑前确认 → 清除 cookie → 保留历史播放数据
- 可重新绑定

---

### 4.3 AI 心情电台（M1，核心）

> **产品的灵魂模块**。AI 能力主要通过 Spring AI 框架统一调度。

#### 4.3.1 心情输入方式

| 方式 | 描述 | 示例 |
|------|------|------|
| **自然语言** | 一句话描述当下状态 | "今天开会被 push 得有点烦" |
| **场景预设** | 一键选择常见场景 | 通勤 / 学习 / 跑步 / 睡前 / 派对 / 写作 |
| **心情色盘** | 可视化二维滑块（情绪 × 能量） | 拖动选择 |
| **静默启动** | 不输入，AI 根据时间 + 历史推断 | 早上 9 点自动推通勤包 |
| **歌曲种子** | 选一首歌，"基于这首给我一个电台" | 类似日推但更精准 |

#### 4.3.2 心情语义化（AI 模块 1）

通过 Spring AI 调用 LLM（DeepSeek / Claude / 通义千问可配置切换），输入用户原始表达 → 输出结构化心情参数：

```json
{
  "mood": {
    "valence": 0.3,
    "energy": 0.6,
    "tension": 0.7
  },
  "tempo_range": [80, 110],
  "preferred_genres": ["indie", "alternative"],
  "preferred_languages": ["zh", "en"],
  "preferred_eras": ["2010s", "2020s"],
  "avoid_keywords": ["过于欢快", "情歌"],
  "vibe_keywords": ["夜晚", "通勤", "微醺"],
  "scene_inferred": "下班路上",
  "energy_curve": "starting_low_then_lifting",
  "duration_estimate_minutes": 30
}
```

**实现方式**：使用 Spring AI 的 `ChatClient` + `OutputParser`（结构化输出解析），保证 JSON 输出可靠性。

#### 4.3.3 多路召回（AI 模块 2）

**5 路并行召回（基于 `CompletableFuture` 异步并行执行），每路 20-50 首：**

1. **个人路径**：从用户红心 / 收藏歌单 / 高频播放中匹配心情
2. **相似艺人**：基于用户常听艺人的相似艺人代表作
3. **平台原生**：调用平台日推 / 私人 FM / 心情电台
4. **关键词路径**：搜索符合 vibe 的高质量公共歌单，抽歌
5. **探索路径**：随机注入 1-2 首"用户没听过但特征匹配"的歌

#### 4.3.4 AI 重排 + 曲序规划（AI 模块 3）

- **打分**：每首歌基于（心情向量相似度 + 历史反馈分 + 多样性分）综合打分
  - M1 阶段：基于关键词标签匹配 + 用户历史反馈分
  - M2 阶段：引入 Qdrant 向量库，做真正的 embedding 相似度计算
- **曲序**：LLM 输入候选 + 心情参数，输出"按情绪弧线排序"的播放队列
- **多样性**：避免连续 2 首同一艺人；流派切换平滑

#### 4.3.5 电台播放器

**播放器核心能力：**

- 播放 / 暂停 / 上一首 / 下一首 / 跳过
- 进度条拖动 / 音量调节
- 歌词同步显示（逐字）
- 收藏 / 红心 / 加入歌单（同步到对应平台）
- 切换音源（同名歌曲在不同平台版本切换）

**电台特殊行为：**

- **永远有下一首**：队列长度始终 ≥ 5 首
- **动态重排**：每播完 3 首，根据反馈重新规划后 5 首
- **会话持续**：单次会话默认 30 分钟，可自定义

#### 4.3.6 实时反馈机制

| 行为 | 信号强度 | 用途 |
|------|---------|------|
| 完整播完 | +1 | 累计正反馈 |
| 跳过 < 30s | -3 | 强负反馈，特征加入"避免" |
| 跳过 30s-完整 | -1 | 弱负反馈 |
| 点红心 | +5 | 强正反馈，特征加入"偏好" |
| 加入歌单 | +5 | 强正反馈 |
| 暂停超过 5 分钟 | 0 | 视为离开，会话结束 |
| 调高音量 | +0.5 | 隐式正反馈 |

**实现**：前端通过 WebSocket（Spring WebSocket）上报播放事件，后端实时处理 + 更新会话画像。

#### 4.3.7 推荐解释

每首歌下方显示一句话解释：

- "你提到加班到现在，给你放点不那么吵但能撑住你的电子氛围"
- "你最近反复听陈奕迅，这首林宥嘉的《浪费》气质类似但你没听过"
- "基于你下午跳过了所有节奏强的歌，切换到 lo-fi 频道"

---

### 4.4 个人音乐资产（M2）

#### 4.4.1 歌单管理

- 同步用户在各平台的歌单（只读）
- 自定义歌单（在 MoodFM 创建，可双向同步到任一平台）
- 智能歌单（基于规则自动维护，例：「最近一周的红心」）

#### 4.4.2 收藏与红心

- 同步显示各平台的红心歌曲（合并视图，去重）
- 跨平台收藏：在 A 平台收藏的歌，可一键在 B 平台也收藏

#### 4.4.3 播放历史

- 完整记录每次播放（哪首歌、什么场景、播完还是跳过）
- 按时间 / 心情 / 场景多维筛选

---

### 4.5 数据洞察与画像（M2）

#### 4.5.1 实时画像

**展示内容：**

- 当前会话的心情参数可视化
- 当前会话的歌曲特征分布（流派、年代、语言、BPM）
- 推荐命中率（本会话）

#### 4.5.2 周报 / 月报

**自动生成方式**：基于 Spring `@Scheduled` 定时任务，每周一凌晨自动生成上周报告，写入 `weekly_reports` 表。

**自动生成内容：**

- 心情曲线图（情绪 × 能量随时间变化）
- 听歌时长 + 高峰时段
- 流派偏好雷达图
- 新发现 vs 经典回听比例
- AI 文字总结：

  > "你这周整体偏忧郁，周三晚上反复听了《XXX》共 6 次。和上周相比，你听民谣的比例提升了 30%。"

- 推荐有效性分析（推荐成功率、跳过率）
- **可生成长图分享**（前端基于 html2canvas 实现）

#### 4.5.3 年度报告

类似 Spotify Wrapped，年终一键生成可分享的年度听歌画像。

#### 4.5.4 心情日历

- 月视图：每天用一种主色调显示当日心情
- 点击日期查看：当日心情详情、听过的歌、AI 备注

---

### 4.6 设置中心（M2）

#### 4.6.1 偏好设置

- 默认音乐源平台
- 偏好语言（中文 / 英文 / 日韩 / 其他）
- 偏好流派（多选）
- 默认电台时长
- 默认场景（开启时自动选）

#### 4.6.2 黑名单管理

- 黑名单歌手（永远不推）
- 黑名单歌曲（永远不推）
- 黑名单关键词（如"前任"主题）
- 自动黑名单：连续跳过 3 次的歌曲询问是否加入

#### 4.6.3 隐私设置

- 数据导出（JSON/CSV）
- 数据清除（保留账号、清空所有播放数据）
- 是否允许匿名数据用于改进推荐

#### 4.6.4 通知设置

- 周报推送时间
- 新功能通知
- Cookie 失效提醒

---

## 5. 页面与界面清单

> **此部分为前端设计师的核心参考。每个页面给出：核心元素、状态、交互。视觉风格由设计方决定。**

### 5.1 页面总览

| 编号 | 页面名 | 路由 | 模块 | 优先级 |
|------|--------|------|------|--------|
| P1 | 落地页 | `/` | 营销 | M1 |
| P2 | 注册页 | `/signup` | 用户系统 | M1 |
| P3 | 登录页 | `/login` | 用户系统 | M1 |
| P4 | 引导页 | `/onboarding` | 用户系统 | M1 |
| P5 | 平台绑定页 | `/bind` | 账号绑定 | M1 |
| P6 | **首页 / 电台主界面** | `/home` | 电台 | M1 |
| P7 | **播放器页** | `/player` | 电台 | M1 |
| P8 | 歌曲详情页 | `/song/:id` | 电台 | M2 |
| P9 | 歌单列表页 | `/playlists` | 资产 | M2 |
| P10 | 歌单详情页 | `/playlist/:id` | 资产 | M2 |
| P11 | 我的红心页 | `/likes` | 资产 | M2 |
| P12 | 历史记录页 | `/history` | 资产 | M2 |
| P13 | **个人画像页** | `/insights` | 数据 | M2 |
| P14 | 周报详情页 | `/report/:week` | 数据 | M2 |
| P15 | 心情日历页 | `/calendar` | 数据 | M2 |
| P16 | 设置中心 | `/settings` | 设置 | M2 |
| P17 | 个人资料页 | `/profile` | 用户系统 | M1 |
| P18 | 平台管理页 | `/settings/platforms` | 账号绑定 | M1 |
| P19 | 黑名单管理页 | `/settings/blacklist` | 设置 | M2 |

### 5.2 关键页面详细设计

#### **P6 — 首页 / 电台主界面（核心页面）**

**布局区域：**

1. **顶部导航**
   - Logo + 当前用户头像 + 通知图标
   - 平台切换器（显示当前默认平台 + 快速切换）

2. **欢迎卡片**（动态文案）
   - "晚上好，[用户名]，今晚想听点什么？"
   - 文案根据时间段变化（早上 / 下午 / 晚上 / 深夜）

3. **心情输入区（核心交互）**
   - 大输入框（默认 placeholder："聊聊你现在的状态…"）
   - 下方一行场景快捷按钮（横向滚动）：通勤 / 学习 / 运动 / 写作 / 睡前 / 派对 / 自定义+
   - 右下角："心情色盘"图标按钮（点击展开二维滑块）

4. **快速开始**
   - "继续上次未听完的电台"（如有）
   - "今日推荐场景"（基于时间 + 历史智能推断，例：早 9 点显示"通勤模式"）

5. **最近电台会话**（卡片横滑）
   - 每张卡片：心情标签 / 歌曲数 / 时间 / 主色调
   - 点击 → 重新开始相似电台

6. **底部播放控制条**（如正在播放）
   - 当前歌曲封面 + 标题 + 艺人 + 播放控制（最小化版）

**状态：**
- 已绑定平台 / 未绑定平台（未绑定时显示绑定引导卡片）
- 有正在播放 / 无正在播放
- 首次使用 / 老用户

---

#### **P7 — 播放器页（核心页面）**

**布局区域：**

1. **顶部**
   - 返回箭头 / 当前电台主题（如"加班疲惫电台"）/ 更多操作
   - 音源平台标识（小图标显示当前歌来自哪个平台）

2. **专辑封面区（中央视觉焦点）**
   - 大尺寸封面（圆角或圆形）
   - 背景：封面提取主色 → 模糊渐变 → 流动动画
   - 唱片旋转动画（可选开关）

3. **歌曲信息**
   - 歌名（大字）
   - 艺人 / 专辑（小字）
   - 红心按钮 / 加入歌单按钮 / 分享按钮

4. **AI 推荐解释卡片**
   - 卡片样式，半透明
   - 一句话："为什么推这首：[AI 生成的解释]"
   - 可点击展开看更多上下文

5. **进度条**
   - 已播放 / 总时长
   - 可拖动跳转

6. **播放控制**
   - 上一首 / 播放暂停（大按钮）/ 下一首
   - 跳过按钮（独立，强调反馈）

7. **次级操作**
   - 歌词按钮（点击展开全屏歌词）
   - 队列按钮（查看接下来要播的）
   - 不喜欢按钮（强负反馈）
   - 加入黑名单（艺人级）

8. **底部播放队列预览**（可上滑展开）
   - 接下来 3-5 首
   - 拖动可调整顺序

**全屏歌词模式：**
- 黑色半透明背景
- 当前句高亮放大
- 逐字滚动效果（如平台支持）

**状态：**
- 播放中 / 暂停 / 加载中 / 错误
- 有歌词 / 无歌词
- 收藏 / 未收藏

---

#### **P5 — 平台绑定页（关键流程）**

**布局区域：**

1. **页面标题**："绑定你的音乐账号"
2. **副标题**："登录你的音乐平台账号，让 AI 真正懂你的音乐品味"
3. **平台卡片列表**：
   - 网易云音乐（含 logo + "未绑定" / "已绑定 [掩码用户名]"）
   - QQ 音乐（同上）
   - 每张卡片：绑定按钮 / 解绑按钮 / 状态指示
4. **安全说明**：
   - 折叠面板："你的账号信息如何被保护？"
   - 内容：本地加密、不上传、不分享、可随时解绑

**绑定弹窗（扫码方式）：**
- 标题："使用网易云 App 扫码登录"
- 二维码（中央，大尺寸）
- 倒计时（90 秒）
- 状态文字："等待扫码…" → "扫码成功，请在手机确认" → "登录成功"
- 切换登录方式按钮

**状态：**
- 未绑定 / 二维码生成中 / 等待扫码 / 等待确认 / 成功 / 失败 / 过期重试

---

#### **P13 — 个人画像页**

**布局区域：**

1. **顶部数据卡片（3-4 个）**
   - 本周听歌时长
   - 最常听的流派
   - AI 推荐准确率
   - 心情主旋律

2. **本周心情曲线**（折线图，使用 ECharts）
   - X 轴：时间（7 天）
   - Y 轴：情绪值
   - 双线：你的心情 vs 听歌情绪

3. **流派分布**（雷达图 / 饼图，使用 ECharts）

4. **TOP 艺人 / 歌曲**（横滑卡片）

5. **AI 总结卡片**
   - 大段 AI 生成文字总结本周
   - "查看完整周报"按钮

6. **历史报告列表**
   - 本月每周报告 + 上月汇总
   - 每张卡片：日期范围 / 关键标签 / 主色调

**状态：**
- 数据充足 / 数据不足（< 7 天，引导继续使用）

---

### 5.3 移动端适配

所有页面需做响应式设计，前端基于 Tailwind CSS 的响应式工具类实现：

| 页面 | 移动端适配 |
|------|----------|
| 首页 | 必须，单栏布局 |
| 播放器页 | 必须，沉浸式全屏 |
| 个人画像 | 必须，图表自适应 |
| 设置中心 | 必须，列表式 |
| 周报详情 | 必须，可滚动长图样式 |

---

## 6. 核心组件清单

> **设计师可作为组件库基础。前端基于 Vue 3 + Tailwind CSS 自行实现，不依赖第三方 UI 库。**

### 6.1 通用组件

- **Button**：Primary / Secondary / Ghost / Icon / Danger
- **Input**：Text / Password / Search / Textarea
- **Select**：单选 / 多选 / 标签式
- **Modal / Dialog**：标准弹窗 / 全屏弹窗 / 抽屉
- **Toast**：成功 / 失败 / 警告 / 信息
- **Avatar**：圆形头像，支持图片 / 文字 fallback
- **Badge**：状态徽章（成功 / 失败 / 进行中）
- **Skeleton**：加载占位
- **EmptyState**：空状态插画 + 引导文字
- **LoadingSpinner**：加载动画

### 6.2 业务组件

- **PlatformBadge**：平台小图标 + 名称（网易云 / QQ 音乐）
- **MoodWheel**：二维心情色盘（情绪 × 能量滑块）
- **SceneSelector**：场景预设按钮组
- **MoodInput**：心情输入框 + 场景按钮 + 色盘组合
- **SongCard**：歌曲卡片（封面 + 标题 + 艺人 + 操作按钮）
- **SongListItem**：列表式歌曲条目
- **PlayerControls**：播放控制按钮组
- **PlayerProgress**：进度条
- **LyricsView**：歌词展示组件
- **QueueDrawer**：播放队列抽屉
- **RecommendationCard**：AI 推荐解释卡片
- **MoodCurveChart**：心情曲线图（ECharts 封装）
- **GenreRadarChart**：流派雷达图（ECharts 封装）
- **WeeklyReportCard**：周报卡片
- **CalendarMoodCell**：心情日历单元格
- **PlatformBindCard**：平台绑定卡片
- **QRCodeBox**：二维码展示框（带倒计时）

### 6.3 反馈组件

- **FeedbackButton**：跳过 / 红心 / 不喜欢按钮组
- **FeedbackBanner**：连续跳过提示 → 是否加入黑名单

---

## 7. 关键用户流程

### 7.1 首次使用流程

```
落地页 → 注册 → 邮箱/手机验证 → 引导页（介绍核心功能）
→ 平台绑定（必须绑定至少一个）→ 偏好设置（流派/语言）
→ 首页 → 第一次电台体验
```

### 7.2 日常使用流程

```
打开首页 → [选择心情输入方式] → AI 生成电台
→ 进入播放器 → 听歌 + 反馈（跳过/红心）
→ 会话结束 → 数据沉淀
```

### 7.3 异常流程

| 场景 | 处理 |
|------|------|
| Cookie 失效 | Toast 提示 + 引导重新绑定 |
| 平台 API 不可用 | 降级提示 + 切换到其他绑定平台 |
| 没有任何绑定 | 强制跳转到绑定页 |
| AI 生成失败 | 降级到平台原生推荐 |
| 网络断开 | 缓存当前队列继续播放 |

---

## 8. 数据模型

### 8.1 核心表结构（MySQL 8.0）

```sql
-- 用户表
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(100) UNIQUE,
  phone VARCHAR(20) UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(500),
  status TINYINT DEFAULT 1 COMMENT '1正常 0软删除',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_email (email),
  INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 平台账号绑定表
CREATE TABLE platform_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL COMMENT 'netease / qqmusic / spotify',
  platform_user_id VARCHAR(100),
  platform_username VARCHAR(100),
  cookie_encrypted TEXT COMMENT 'AES-256-GCM 加密',
  is_default TINYINT DEFAULT 0,
  is_valid TINYINT DEFAULT 1,
  last_validated_at DATETIME,
  expires_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_platform (user_id, platform),
  INDEX idx_user (user_id),
  CONSTRAINT fk_binding_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 心情会话表
CREATE TABLE mood_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  raw_input TEXT COMMENT '用户原始输入',
  mood_params JSON COMMENT 'AI 解析后的结构化参数',
  scene VARCHAR(50),
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME,
  duration_minutes INT,
  INDEX idx_user_started (user_id, started_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 歌曲缓存表（跨平台统一）
CREATE TABLE songs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  artist VARCHAR(255) NOT NULL,
  album VARCHAR(255),
  duration_seconds INT,
  cover_url VARCHAR(500),
  features JSON COMMENT '流派/BPM/能量/语言等特征',
  -- 注意：embedding 字段在 M2 阶段引入 Qdrant 后存于 Qdrant，MySQL 仅保留映射 ID
  qdrant_point_id VARCHAR(64) COMMENT 'M2 阶段使用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_title_artist (title, artist),
  FULLTEXT INDEX ft_title_artist (title, artist) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 平台歌曲映射表
CREATE TABLE platform_song_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  song_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  platform_song_id VARCHAR(100) NOT NULL,
  available TINYINT DEFAULT 1,
  UNIQUE KEY uk_platform_song (platform, platform_song_id),
  INDEX idx_song (song_id),
  CONSTRAINT fk_mapping_song FOREIGN KEY (song_id) REFERENCES songs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 播放记录表
CREATE TABLE play_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  song_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  played_seconds INT,
  total_seconds INT,
  action VARCHAR(20) COMMENT 'completed / skipped / liked / disliked',
  played_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_played (user_id, played_at),
  INDEX idx_session (session_id),
  CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_record_song FOREIGN KEY (song_id) REFERENCES songs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户画像表
CREATE TABLE user_profiles (
  user_id BIGINT PRIMARY KEY,
  genre_weights JSON COMMENT '流派偏好权重',
  artist_weights JSON COMMENT '艺人偏好权重',
  language_preferences JSON,
  blacklist_artists JSON,
  blacklist_songs JSON,
  blacklist_keywords JSON,
  -- M2 阶段使用 Qdrant 存储用户偏好向量
  qdrant_user_vector_id VARCHAR(64),
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 周报表
CREATE TABLE weekly_reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  week_start DATE NOT NULL,
  week_end DATE NOT NULL,
  data JSON COMMENT '完整报告数据',
  ai_summary TEXT,
  generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_week (user_id, week_start),
  CONSTRAINT fk_report_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 反馈事件表（实时上报落库）
CREATE TABLE feedback_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  song_id BIGINT,
  event_type VARCHAR(30) COMMENT 'play / skip / like / dislike / volume_up',
  event_data JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_created (user_id, created_at),
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 8.2 缓存策略（Redis 7）

| 数据 | TTL | Key 格式 |
|------|-----|---------|
| 歌曲播放 URL | 25 分钟 | `song:url:{platform}:{song_id}` |
| 用户当前队列 | 1 小时 | `queue:{user_id}` |
| 平台 API 响应 | 5 分钟 | `api:{platform}:{endpoint}:{params_hash}` |
| 二维码登录状态 | 90 秒 | `qrlogin:{key}` |
| AI 心情解析结果 | 1 小时 | `mood:{input_hash}` |
| 用户 JWT Token 黑名单 | Token 剩余有效期 | `jwt:blacklist:{token_id}` |
| 用户登录设备列表 | 7 天 | `user:devices:{user_id}` |

### 8.3 本地缓存（Caffeine）

热点数据通过 Caffeine 做 JVM 内本地缓存，减少 Redis 压力：

| 数据 | 容量 | TTL |
|------|------|-----|
| 用户基础信息 | 10000 | 10 分钟 |
| 歌曲基础信息 | 50000 | 1 小时 |
| 平台映射 | 50000 | 1 小时 |

---

## 9. 技术架构

### 9.1 技术栈总览

| 层 | 选型 | 版本 |
|----|------|------|
| **运行环境** | JDK | 21 (LTS, 启用 Virtual Thread) |
| **Web 框架** | Spring Boot | 3.3+ |
| **安全** | Spring Security + JWT (jjwt) | 6.x |
| **ORM** | MyBatis-Plus | 3.5+ |
| **数据库** | MySQL | 8.0+ |
| **本地缓存** | Caffeine | 3.x |
| **分布式缓存** | Redis | 7.x |
| **向量数据库（M2）** | Qdrant | 1.x（Docker 部署）|
| **AI 框架** | Spring AI | 1.x |
| **LLM 服务** | DeepSeek / Claude / 通义千问 | 可配置 |
| **Embedding 服务** | BGE-M3（本地）或 通义 embedding API | M2 阶段引入 |
| **任务调度** | Spring Scheduler + @Async | 内置 |
| **WebSocket** | Spring WebSocket | 6.x |
| **API 文档** | SpringDoc OpenAPI 3 | 2.x |
| **音乐 API 适配** | Node.js 微服务（NeteaseCloudMusicApi）| - |
| **前端框架** | Vue 3 + TypeScript | 3.4+ |
| **构建工具** | Vite | 5.x |
| **状态管理** | Pinia | 2.x |
| **样式方案** | Tailwind CSS | 3.x |
| **图表库** | ECharts | 5.x |
| **HTTP 客户端** | Axios | 1.x |
| **音频播放器** | Howler.js | 2.x |
| **长图分享** | html2canvas | 1.x |
| **部署** | Docker + Docker Compose | - |
| **反向代理** | Nginx | - |

### 9.2 服务架构图

```
┌─────────────────────────────────────────────┐
│   Vue 3 + TypeScript + Tailwind CSS + Vite  │
│   Pinia + Vue Router + Axios + ECharts      │
└────────────────────┬────────────────────────┘
                     │ HTTPS (REST + WebSocket)
                     ▼
┌─────────────────────────────────────────────┐
│              Nginx 反向代理                  │
└────────────────────┬────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────┐
│   Spring Boot 3.x 主服务（Java 21）          │
│  ┌──────────────────────────────────────┐  │
│  │  Controller (REST + WebSocket)       │  │
│  │  Spring Security + JWT 过滤器        │  │
│  └──────────────────────────────────────┘  │
│  ┌──────────────────────────────────────┐  │
│  │  Service Layer                       │  │
│  │  ├─ UserService                      │  │
│  │  ├─ PlatformBindingService           │  │
│  │  ├─ MoodAnalysisService (Spring AI)  │  │
│  │  ├─ RecallService (5 路并行召回)     │  │
│  │  ├─ RecommendService (重排+曲序)     │  │
│  │  ├─ PlayerService (队列管理)         │  │
│  │  ├─ FeedbackService                  │  │
│  │  ├─ ProfileService                   │  │
│  │  └─ ReportService (定时生成周报)     │  │
│  └──────────────────────────────────────┘  │
│  ┌──────────────────────────────────────┐  │
│  │  Repository (MyBatis-Plus)           │  │
│  └──────────────────────────────────────┘  │
└──┬─────────┬─────────┬─────────┬───────────┘
   │         │         │         │
   ▼         ▼         ▼         ▼
┌─────┐  ┌──────┐  ┌────────┐  ┌─────────────┐
│MySQL│  │Redis │  │Qdrant  │  │ 音乐 API    │
│ 8.0 │  │ 7    │  │(M2阶段)│  │ Node.js     │
└─────┘  └──────┘  └────────┘  │ 适配层       │
                                │ ┌─────────┐ │
                                │ │网易云API│ │
                                │ │QQ音乐API│ │
                                │ └─────────┘ │
                                └──────┬──────┘
                                       │
                                       ▼
                              网易云 / QQ 音乐
                              （第三方平台）
                  
                  ┌──────────────────┐
                  │  LLM 服务        │
                  │  (Spring AI 调用)│
                  │  ├─ DeepSeek    │
                  │  ├─ Claude      │
                  │  └─ 通义千问     │
                  └──────────────────┘
```

### 9.3 关键设计决策

#### 9.3.1 Java 21 Virtual Thread 的应用

Java 21 引入的虚拟线程（Virtual Thread）非常适合本项目的 IO 密集场景：

- **多路并行召回**：5 路召回涉及大量 HTTP 调用（音乐平台 API），用虚拟线程可大幅提升并发能力
- **WebSocket 处理**：海量长连接场景下，虚拟线程比传统线程池更省资源
- **平台 API 调用**：调用网易云、QQ 音乐 API 是 IO 密集型操作

**配置方式**：

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

#### 9.3.2 Spring AI 的统一抽象

通过 Spring AI 统一调度多个 LLM 厂商：

```java
@Service
public class MoodAnalysisService {
    private final ChatClient chatClient;
    
    public MoodParams analyze(String userInput) {
        return chatClient.prompt()
            .user(userInput)
            .system(MOOD_ANALYSIS_PROMPT)
            .call()
            .entity(MoodParams.class);  // 自动结构化解析
    }
}
```

切换 LLM 只需改配置：

```yaml
spring:
  ai:
    openai:
      base-url: https://api.deepseek.com  # 切换到 DeepSeek
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat
```

#### 9.3.3 Node.js 适配层的边界

Node.js 微服务**只做一件事**：包装第三方音乐平台 API，提供统一的 RESTful 接口给 Spring Boot。

- 不存数据
- 不做业务逻辑
- 不直接对外
- 通过 Docker 内网与 Spring Boot 通信
- 失败有重试 + 熔断机制（Spring Boot 端用 Resilience4j 实现）

#### 9.3.4 向量库的引入时机

**M1 阶段（不引入 Qdrant）**：

- 推荐基于关键词标签匹配 + 用户历史反馈打分
- 简单但可用，先把全链路跑通

**M2 阶段（引入 Qdrant）**：

- 歌曲 embedding 计算后存入 Qdrant
- 用户偏好向量计算并存储
- 支持真正的语义相似度检索

这样早期开发不需要维护额外组件，等数据积累到值得做向量化时再引入。

### 9.4 项目结构（推荐）

**后端（Spring Boot）：**

```
moodfm-backend/
├── pom.xml
├── src/main/java/com/moodfm/
│   ├── MoodFmApplication.java
│   ├── common/             # 通用工具、常量、异常
│   ├── config/             # 配置类（Security/Redis/AI 等）
│   ├── controller/         # REST 控制器
│   ├── service/            # 业务服务
│   │   ├── user/
│   │   ├── platform/       # 平台绑定
│   │   ├── ai/             # AI 相关（心情分析、推荐）
│   │   ├── player/         # 播放器逻辑
│   │   ├── feedback/
│   │   └── report/
│   ├── domain/             # 领域模型
│   │   ├── entity/         # 数据库实体
│   │   ├── dto/            # 数据传输对象
│   │   └── vo/             # 视图对象
│   ├── mapper/             # MyBatis-Plus Mapper
│   ├── client/             # 外部服务调用
│   │   ├── music/          # 音乐 API 客户端
│   │   └── llm/            # LLM 客户端封装
│   ├── security/           # JWT、过滤器
│   ├── websocket/          # WebSocket 处理
│   └── scheduler/          # 定时任务
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── mapper/             # MyBatis XML
│   └── prompts/            # AI Prompt 模板
└── src/test/java/

```

**前端（Vue 3）：**

```
moodfm-frontend/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.json
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── router/             # Vue Router
│   ├── stores/             # Pinia stores
│   ├── api/                # Axios 接口封装
│   ├── components/         # 全局组件
│   │   ├── common/         # 通用组件
│   │   ├── business/       # 业务组件
│   │   └── charts/         # 图表组件
│   ├── views/              # 页面级组件
│   │   ├── home/
│   │   ├── player/
│   │   ├── auth/
│   │   ├── insights/
│   │   └── settings/
│   ├── composables/        # 组合式函数
│   ├── utils/              # 工具
│   ├── types/              # TypeScript 类型
│   └── assets/
└── public/
```

**Node.js 适配层：**

```
moodfm-music-adapter/
├── package.json
├── server.js
├── routes/
│   ├── netease.js
│   └── qqmusic.js
└── Dockerfile
```

### 9.5 部署方案

**docker-compose.yml 顶层结构（示意）：**

```yaml
services:
  mysql:
    image: mysql:8.0
    # 持久化、初始化脚本

  redis:
    image: redis:7-alpine

  qdrant:                    # M2 阶段添加
    image: qdrant/qdrant:latest
    profiles: [m2]

  backend:
    build: ./moodfm-backend
    depends_on: [mysql, redis]
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JAVA_OPTS=-XX:+UseZGC

  music-adapter:
    build: ./moodfm-music-adapter
    # 内网暴露给 backend

  frontend:
    build: ./moodfm-frontend
    # 构建后 nginx 托管

  nginx:
    image: nginx:alpine
    ports: [80, 443]
    # 反向代理 frontend + backend
```

**资源建议：**

- M1 阶段：2 核 4G 服务器
- M2 阶段（含 Qdrant）：4 核 8G 服务器

---

## 10. 非功能需求

### 10.1 性能

- 首页加载 < 2s
- 心情输入到电台启动 < 5s（含 AI 调用）
- 歌曲切换无缝（预加载下一首）
- 单次 AI 推荐请求 P95 < 3s
- WebSocket 心跳间隔 30s
- API 响应 P99 < 1s（不含 AI 调用）

### 10.2 安全

- 所有 Cookie AES-256-GCM 加密存储，密钥通过环境变量注入
- HTTPS 强制（Nginx 层 TLS 终结）
- Spring Security 防 CSRF / XSS
- MyBatis-Plus 参数化查询防 SQL 注入
- 敏感操作二次验证（解绑、删账号）
- API rate limit（Spring Boot 层 + Redis 实现，每用户每分钟 60 次）
- JWT 黑名单机制支持强制下线

### 10.3 可用性

- 单点故障容忍：核心服务支持横向扩展（Stateless Spring Boot 服务）
- 平台 API 故障降级（Resilience4j 熔断 + 降级）
- 数据库定时备份（每日）
- 健康检查接口（Spring Boot Actuator）

### 10.4 兼容性

- 浏览器：Chrome / Safari / Firefox / Edge 最近 2 个版本
- 移动端：iOS Safari 15+ / Android Chrome 100+
- 暂不需要原生 App

### 10.5 国际化

- 一期仅中文
- 二期考虑英文（Vue I18n 架构预留）

### 10.6 可观测性

- 日志：Logback + 按级别分文件
- 指标：Spring Boot Actuator + Micrometer
- 关键链路 traceId 贯穿（手动埋点或 Spring Cloud Sleuth）
- 慢 SQL 监控（Druid 或 p6spy）

---

## 11. MVP 路线图

### Phase 1（M1）：核心可用，2-3 周

**后端：**
- [x] 项目脚手架（Spring Boot 3 + Java 21 + MyBatis-Plus）
- [x] 用户注册登录（Spring Security + JWT）
- [x] 网易云账号绑定（扫码 + 手机号，对接 Node.js 适配层）
- [x] 心情分析服务（Spring AI 接入 DeepSeek）
- [x] 简单推荐（关键词召回 + 历史反馈打分）
- [x] WebSocket 实时反馈
- [x] 基础 API 文档（SpringDoc）

**前端：**
- [x] 项目脚手架（Vue 3 + Vite + Tailwind）
- [x] 注册登录页
- [x] 平台绑定页（含扫码弹窗）
- [x] 首页（心情输入）
- [x] 播放器页（基础控件）
- [x] 红心 / 跳过反馈

**部署：**
- [x] Docker Compose 一键起服务
- [x] Nginx 配置
- [x] HTTPS 证书

### Phase 2（M2）：体验完整，2-3 周

**后端：**
- [ ] 引入 Qdrant 向量数据库
- [ ] 歌曲 embedding 计算（异步任务）
- [ ] 用户偏好向量更新
- [ ] 多路召回（5 路并行，使用 Virtual Thread）
- [ ] AI 重排 + 曲序规划
- [ ] AI 推荐解释生成
- [ ] QQ 音乐适配
- [ ] 跨平台搜索匹配

**前端：**
- [ ] 场景预设 / 心情色盘
- [ ] 电台模式（连续播放 + 动态重排）
- [ ] AI 推荐解释卡片
- [ ] 全屏歌词
- [ ] 个人画像页（基础版）

### Phase 3（M3）：数据沉淀，2 周

- [ ] 周报系统（定时任务 + AI 总结）
- [ ] 心情日历
- [ ] 黑名单管理
- [ ] 历史记录页 + 多维筛选

### Phase 4（M4）：进阶能力，持续

- [ ] 年度报告
- [ ] 长图分享（html2canvas）
- [ ] 多端同步
- [ ] 第三方平台扩展（Spotify）
- [ ] 性能优化 + 可观测性增强

---

## 12. 风险与边界

### 12.1 技术风险

| 风险 | 影响 | 应对 |
|------|------|------|
| 第三方音乐 API 不稳定 | 核心功能不可用 | 多平台冗余 + Caffeine/Redis 缓存 + 降级策略 |
| Cookie 频繁失效 | 用户体验差 | 定时刷新（@Scheduled）+ 异常感知 + 主动 SSE 提示 |
| LLM 调用成本超支 | 运营成本失控 | Spring AI 配置多模型分级（粗筛便宜模型）+ 结果缓存 |
| 平台风控 | 账号被封 | rate limit + 模拟真实行为 + 不同用户 cookie 隔离 |
| MySQL 单机性能瓶颈 | 大数据量后查询慢 | 读写分离 + 索引优化 + 必要时引入分库分表 |

### 12.2 法律边界

- **仅限自用 / 朋友间小范围使用**，不得对外提供服务
- 不分发音频文件，仅做播放调度
- 不破解平台付费内容
- 用户需对自己绑定账号的合规使用负责
- 遵守平台用户协议

### 12.3 产品边界

- **不做**：音乐下载、KTV 评分、社交（评论/关注）、直播
- **不做**：电台主播 UGC（让用户做电台给别人听）—— 太复杂且涉及版权
- **暂不做**：移动 App（先 PWA 满足移动需求）

---

## 附录

### A. 关键术语

- **心情参数**：AI 解析用户输入后输出的结构化数据，包含 valence / energy / tempo 等
- **会话（Session）**：一次完整的电台收听过程，从开始到主动结束或超时
- **召回**：从大量候选中选出符合条件的子集
- **重排**：对召回结果重新排序
- **画像**：基于历史行为构建的用户长期偏好模型
- **Virtual Thread**：Java 21 引入的轻量级线程，特别适合 IO 密集型场景

### B. 参考产品

- Spotify：Discover Weekly、Daylist、Wrapped
- 网易云：私人 FM、心动模式、年度听歌报告
- Apple Music：For You
- Last.fm：scrobble + 数据分析

### C. 关键依赖参考

**后端 pom.xml 核心依赖**：

```
- spring-boot-starter-web (3.3+)
- spring-boot-starter-security
- spring-boot-starter-data-redis
- spring-boot-starter-websocket
- spring-boot-starter-validation
- spring-boot-starter-actuator
- spring-ai-openai-spring-boot-starter (1.x)
- mybatis-plus-spring-boot3-starter (3.5+)
- mysql-connector-j
- jjwt-api / jjwt-impl / jjwt-jackson
- caffeine
- resilience4j-spring-boot3
- springdoc-openapi-starter-webmvc-ui (2.x)
- qdrant-client (M2 阶段)
```

**前端 package.json 核心依赖**：

```
- vue (3.4+)
- vue-router (4.x)
- pinia (2.x)
- typescript (5.x)
- vite (5.x)
- tailwindcss (3.x)
- axios (1.x)
- echarts (5.x) + vue-echarts
- howler (2.x)
- html2canvas (1.x)
- @vueuse/core
- dayjs
```

### D. 待定事项

- [ ] 是否引入"分享电台"功能（A 把自己的电台分享给 B）
- [ ] 是否引入"语音输入"心情
- [ ] 是否需要桌面客户端（Tauri？）
- [ ] 是否对接智能家居（"Hey Siri 打开 MoodFM 加班模式"）

---

**文档版本**：v2.0  
**创建日期**：2026-05-02  
**主要变更**：技术栈整体替换为 Spring Boot 3 + Java 21 + Vue 3 + Tailwind + MySQL 8 + Spring AI  
**状态**：待评审
