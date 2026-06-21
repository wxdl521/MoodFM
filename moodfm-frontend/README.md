# MoodFM 前端

MoodFM 的 Web 前端，基于 **Vue 3 + Vite + TypeScript**。包含登录/引导/平台绑定、首页、AI 电台播放器、洞察、心情日历、周报与后台管理等页面，并通过 `moodfm-electron` 复用为桌面客户端。

> 项目整体说明（架构、后端 API、Docker、桌面壳、环境变量）见仓库根目录 [`README.md`](../README.md)。

## 技术栈

Vue 3、Vite、Vue Router、Pinia、Axios、Howler（音频）、ECharts（图表）、html2canvas（周报长图）、Vitest（单测）、vue-tsc（类型检查）。

## 本地开发

```bash
npm install
npm run dev          # Vite dev server，默认 http://localhost:5173
```

开发时需要后端（`http://localhost:8081/api`）与音乐适配器在运行，启动方式见根目录 README 的「本地启动」。

## 构建与检查

```bash
npm run build        # vite build（含类型检查）
npm run lint         # eslint（如已配置）

# 严格类型检查：必须用本地 bin，npx 会拉到不兼容的 vue-tsc
./node_modules/.bin/vue-tsc --noEmit
```

## 目录结构

```text
src/
├─ api/          # Axios 封装 + 各模块 API 声明
├─ assets/       # 静态资源、全局样式
├─ components/   # 通用 UI 组件（含 MiniPlayer 等）
├─ composables/  # useAudioPlayer / useElectronBridge / useNavDirection 等
├─ router/       # 路由 + meta（depth / hideMiniPlayer 等）
├─ stores/       # Pinia store（radio / player / playlist 等）
├─ types/        # TS 类型（Song / SongVO / Window 全局增强等）
├─ utils/        # logger / platform 守卫等
└─ views/        # 页面（含 admin/ 后台与 library/ 媒体库子页）
```

## 注意事项

- 反馈上报（`sendFeedback`）会携带当前歌曲的 `platform`；缺失时留空交由后端解析，**不要**在前端硬编码回退平台。
- 新增功能时先与后端对齐 API 字段，尤其是电台启动与播放反馈链路。
