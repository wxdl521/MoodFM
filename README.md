# MoodFM

MoodFM 是一个基于“当下心情”生成私人音乐电台的全栈项目。用户可以绑定音乐平台账号，输入自然语言心情、场景或心情坐标，由后端通过 Spring AI 生成心情参数，再召回候选歌曲、重排队列并返回可播放电台。

当前仓库包含三部分：

- `moodfm-backend`：Spring Boot 3 后端，负责认证、平台绑定、AI 电台、播放队列、数据洞察和周报。
- `moodfm-frontend`：Vue + Vite 前端，包含登录、引导、绑定、首页、电台播放器、洞察、日历、周报页面。
- `moodfm-music-adapter`：Node.js 音乐平台适配层，封装网易云音乐和 QQ 音乐相关接口。

`Front-end styles/` 只是样式模板和视觉参考，不是当前运行应用的主代码。

## 技术栈

| 模块 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.3.6, Spring Security, JWT, Spring AI, MyBatis-Plus, WebSocket/STOMP |
| 前端 | Vue 3, Vite, Vue Router, Pinia, Axios, Howler, html2canvas |
| 音乐适配器 | Node.js 18+, Express, NeteaseCloudMusicApi, Axios |
| 数据与缓存 | MySQL 8, Redis 7 |
| 部署 | Docker Compose |

## 已实现功能

### 账号与权限

- 邮箱/手机号 + 密码注册。
- 登录、刷新 Token、登出。
- Spring Security + JWT 鉴权。
- Access Token 黑名单机制。
- 获取当前用户信息：`GET /api/users/me`。
- 保存用户偏好：`PUT /api/users/preferences`。

当前偏好保存主要覆盖 `genres` 和 `languages`，前端引导里的 `defaultScene` 暂未完整入库。

### 音乐平台绑定

- 获取绑定列表：`GET /api/platforms`。
- 网易云 / QQ 音乐 Cookie 绑定：`POST /api/platforms/{platform}/bind/cookie`。
- 生成扫码绑定二维码：`POST /api/platforms/{platform}/qr/generate`。
- 轮询扫码状态：`GET /api/platforms/{platform}/qr/status`。
- 设置默认平台：`PUT /api/platforms/{platform}/default`。
- 解绑平台：`DELETE /api/platforms/{platform}`。
- Cookie 使用 AES 加密保存。

当前 Cookie 绑定只做加密保存，不校验 Cookie 是否真实有效。真实播放需要有效平台 Cookie。

### AI 心情电台

- 启动电台：`POST /api/radio/start`。
- 支持后端 DTO 中的三类输入：
  - `text`：自然语言心情描述。
  - `scene`：场景预设。
  - `valence` + `energy`：心情坐标。
- Spring AI 根据 `src/main/resources/prompts/mood-analysis.txt` 生成结构化心情参数。
- 基于心情参数从多个来源召回候选歌曲。
- 使用 `song-ranking.txt` 对候选歌曲重排并生成推荐理由。
- 队列写入 Redis，支持下一批歌曲：`GET /api/radio/next`。
- 获取播放地址：`GET /api/radio/url`。

LLM 调用失败时，后端会使用默认心情参数兜底，保证主链路尽量可用。

### 播放器与反馈

- 前端播放器支持播放、暂停、上一首、下一首、进度条和当前队列展示。
- 通过 Howler 加载真实音频 URL。
- 后端已实现 WebSocket/STOMP 播放反馈入口：
  - 连接端点：`/ws`
  - 发送目的地：`/app/feedback`
  - ack 订阅：`/user/queue/feedback-ack`
- 反馈事件会尝试写入 `feedback_events`，部分 completed/skip 事件会写入 `play_records`。

### 数据洞察与日历

- 综合摘要：`GET /api/insights/summary?days=7`。
- 心情趋势：`GET /api/insights/mood-trend?days=7`。
- 流派雷达：`GET /api/insights/genre-radar?days=7`。
- Top 艺人 / 歌曲：`GET /api/insights/top-items?days=7`。
- 月度心情日历：`GET /api/insights/calendar?year=YYYY&month=M`。
- 某天详情：`GET /api/insights/day/YYYY-MM-DD`。

这些能力依赖 `play_records`、`songs`、`mood_sessions` 等数据。没有播放记录时，页面会以空状态或默认数据展示。

### 周报

- 生成或获取上一个完整自然周的周报：`POST /api/reports/weekly/generate`。
- 周报列表：`GET /api/reports/weekly`。
- 周报详情：`GET /api/reports/weekly/{id}`。
- 使用 `weekly-report.txt` 生成周报文案。
- 前端周报页支持长图分享预览和 html2canvas 下载。

周报生成需要上周有播放记录；无数据时返回业务错误。

### 音乐适配器

- 健康检查：`GET /health`。
- 网易云搜索、推荐、喜欢歌曲、歌曲 URL、扫码登录相关接口。
- QQ 音乐搜索接口。

QQ 音乐的 liked/recommend/song-url 当前多为占位或不完整实现。

## 当前未实现或不完整的功能

以下内容在代码或 UI 中可能已经出现入口，但当前不应视为完整功能：

- 前端首页调台时发送的文本和心情色盘字段与后端 DTO 不完全一致：
  - 前端发送 `textInput/moodX/moodY`
  - 后端接收 `text/valence/energy`
  这会导致部分前端调台请求可能走默认心情兜底。
- 前端 `radioApi.getSessions()` 请求 `GET /api/radio/sessions`，后端没有对应接口。
- 前端 `radioApi.feedback()` 请求 `POST /api/radio/feedback`，后端没有 REST 反馈接口；真实反馈入口是 WebSocket `/app/feedback`。
- Playlist 页面有 UI，但后端没有 playlist 相关接口。
- 手机号验证码绑定接口未实现：
  - `POST /api/platforms/{platform}/phone/code`
  - `POST /api/platforms/{platform}/bind/phone`
- QQ 音乐适配器能力不完整。
- Spotify、YouTube Music、Bilibili 等平台未接入。
- Qdrant、embedding 向量召回、真正的语义相似度检索未实现。
- AI 推荐解释目前主要基于候选歌名/艺人和心情参数，缺少歌曲特征库支撑。
- WebSocket 播放反馈写入 `play_records` 时需关注数据库约束：`play_records.platform` 是 NOT NULL，但当前部分写入路径可能没有设置 platform。
- Docker 全量启动时，前端容器内 Nginx 代理配置需要复核：当前后端端口是 `8081`，部分前端 Nginx 配置可能仍指向 `backend:8080`。
- 年度报告、分享电台、移动端 App、语音输入、黑名单管理等仍属于未来规划。

## 项目结构

```text
MoodFM/
├─ moodfm-backend/          # Spring Boot 后端
├─ moodfm-frontend/         # Vue/Vite 前端
├─ moodfm-music-adapter/    # Node.js 音乐平台适配层
├─ mysql/                   # MySQL 初始化脚本
├─ Front-end styles/        # 样式模板 / 视觉参考
├─ docker-compose.yml
└─ .env.example
```

## 环境变量

复制 `.env.example` 为 `.env`，并按需填写：

```bash
cp .env.example .env
```

关键变量：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 应用数据库账号 |
| `JWT_SECRET` | JWT 签名密钥，建议使用长随机字符串 |
| `COOKIE_ENCRYPTION_KEY` | Cookie 加密密钥，AES-256-GCM 需要 32 字节 Base64 |
| `LLM_BASE_URL` | OpenAI 兼容 LLM 接口地址 |
| `LLM_API_KEY` | LLM API Key |
| `LLM_MODEL` | LLM 模型名 |
| `SPRING_PROFILES_ACTIVE` | Spring Profile，默认 `dev` |

不要把真实 `.env` 提交到 Git。

## 本地启动

推荐用本地联调方式启动，避免前端容器代理端口差异影响调试。

### 1. 启动基础依赖和音乐适配器

```bash
docker compose up -d mysql redis music-adapter
```

### 2. 启动后端

要求：

- Java 21
- Maven 3.6.3 或更高版本

```bash
cd moodfm-backend
mvn spring-boot:run
```

后端地址：

- API：`http://localhost:8081/api`
- 健康检查：`http://localhost:8081/api/health`
- Swagger：`http://localhost:8081/docs`

### 3. 启动前端

```bash
cd moodfm-frontend
npm install
npm run dev
```

前端地址：`http://localhost:5173`

### 4. 单独启动音乐适配器

如果不使用 Docker 启动适配器：

```bash
cd moodfm-music-adapter
npm install
npm start
```

适配器地址：`http://localhost:3000`

## Docker Compose

可以尝试全量启动：

```bash
docker compose up -d --build
```

服务端口：

| 服务 | 地址 |
|---|---|
| 前端容器 | `http://localhost:5173` |
| 后端 | `http://localhost:8081` |
| 音乐适配器 | `http://localhost:3000` |
| MySQL | `localhost:3306` |
| Redis | `localhost:6379` |

如果前端容器访问后端异常，优先检查 `moodfm-frontend/nginx.conf` 中代理目标端口是否与后端 `8081` 一致。

## 测试与验证

前端构建：

```bash
cd moodfm-frontend
npm run build
```

后端测试：

```bash
cd moodfm-backend
mvn test
```

注意：当前后端 `pom.xml` 配置 Java 21。本地如果仍是 Java 17，或者 Maven 低于 3.6.3，会在编译阶段失败。

基础冒烟项：

- `GET http://localhost:8081/api/health`
- `GET http://localhost:3000/health`
- 前端打开 `/`、`/auth`、`/home`、`/player`
- 注册、登录、平台 Cookie 绑定、启动电台
- 造播放数据后检查 `/insights`、`/calendar`、`/weekly/:id`

## 开发注意事项

- 当前根目录 `.gitignore` 会忽略 `.env`、构建产物、依赖目录和本地工具文件。
- 真实平台 Cookie 属于敏感信息，只应保存在本地或数据库加密字段中。
- `MoodFM_PRD_v2.0.md` 和 `TEST_FLOW.md` 是本地项目说明文档，已从远端仓库移除；如果需要保留本地但不上传，请不要强制添加。
- 新增功能时建议先对齐前后端 API 字段，尤其是电台启动和播放反馈链路。
