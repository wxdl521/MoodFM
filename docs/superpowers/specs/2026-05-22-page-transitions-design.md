# MoodFM 页面切换动画设计方案

**日期**: 2026-05-22
**状态**: 已批准，待实施

---

## 目标

为 MoodFM 所有路由切换添加方向感滑动动画，用滑入方向直观反映导航层级关系：进入子页面向左，返回上级向右，同级按导航顺序决定方向。

**不改变任何业务逻辑，纯视觉层变更。**

---

## 路由层级与顺序定义

每条路由在 `meta` 中挂 `depth`（层级）和 `order`（同级顺序，depth ≥ 2 时有效）。

### Depth 0 — 认证流程

| 路由 | order |
|------|-------|
| `/` | 0 |
| `/auth` | 1 |
| `/onboarding` | 2 |

### Depth 1 — 主入口

| 路由 | order |
|------|-------|
| `/home` | 0 |

### Depth 2 — 主功能区（按底部导航顺序）

| 路由 | order |
|------|-------|
| `/search` | 0 |
| `/player` | 1 |
| `/playlists` | 2 |
| `/likes` | 3 |
| `/history` | 4 |
| `/insights` | 5 |
| `/settings` | 6 |
| `/bind` | 7 |
| `/profile` | 8 |

### Depth 3 — 子页面

| 路由 | 父级 |
|------|------|
| `/playlists/:id` | /playlists |
| `/playlists/smart/:type` | /playlists |
| `/song/:id` | /player |
| `/insights/calendar` | /insights |
| `/insights/weekly/:week` | /insights |
| `/insights/annual/:year` | /insights |
| `/settings/platforms` | /settings |
| `/settings/blacklist` | /settings |
| `/settings/notifications` | /settings |

### Depth 4 — Admin

所有 `/admin/*` 路由统一 depth=4，order 不参与方向计算（进入 admin 固定向左）。

---

## 方向判断规则

```
from.depth < to.depth   → slide-left   （进入子页面）
from.depth > to.depth   → slide-right  （返回上级）
from.depth == to.depth  → 比较 order
  from.order < to.order → slide-left
  from.order > to.order → slide-right
  order 相等或缺失      → slide-left（默认）
```

---

## 动画参数

### slide-left（向左）

| 阶段 | 属性 | 值 |
|------|------|----|
| 新页进入 | transform | `translateX(100%) → translateX(0)` |
| 新页进入 | opacity | `0 → 1` |
| 新页进入 | duration | `320ms` |
| 新页进入 | easing | `cubic-bezier(0.16, 1, 0.3, 1)` |
| 旧页离开 | transform | `translateX(0) → translateX(-25%)` |
| 旧页离开 | opacity | `1 → 0` |
| 旧页离开 | duration | `200ms` |
| 旧页离开 | easing | `ease` |

### slide-right（向右）

| 阶段 | 属性 | 值 |
|------|------|----|
| 新页进入 | transform | `translateX(-25%) → translateX(0)` |
| 新页进入 | opacity | `0 → 1` |
| 新页进入 | duration | `320ms` |
| 新页进入 | easing | `cubic-bezier(0.16, 1, 0.3, 1)` |
| 旧页离开 | transform | `translateX(0) → translateX(100%)` |
| 旧页离开 | opacity | `1 → 0` |
| 旧页离开 | duration | `200ms` |
| 旧页离开 | easing | `ease` |

### prefers-reduced-motion 降级

当系统开启减弱动画时，所有切换退回为纯 opacity fade（100ms），无位移。

---

## 架构

### 新建：`src/composables/useNavDirection.ts`

职责：监听路由变化，对比 from/to 的 depth 和 order，输出当前应用的 transition name。

```ts
// 导出
const transitionName = ref<'slide-left' | 'slide-right'>('slide-left')
```

使用 `router.beforeEach` 在路由跳转前更新 `transitionName`，确保 `<Transition>` 在渲染前已拿到正确的名称。

### 修改：`src/router/index.ts`

为每条路由添加 `meta: { depth: number, order?: number }`。redirect 路由不加 meta（不触发 transition）。

### 修改：`src/App.vue`

```html
<!-- 将固定 name="page" 改为动态绑定 -->
<Transition :name="transitionName" mode="out-in">
  <RouterView :key="$route.path" />
</Transition>
```

CSS 从原有的 `.page-*` 扩展为 `.slide-left-*` 和 `.slide-right-*`，保留原 `.page-*` 以防万一。`prefers-reduced-motion` 媒体查询覆盖所有 transition。

---

## 不在范围内

- MiniPlayer 的进场动画（已有，不动）
- 按钮 / 卡片的微交互动画（不在此次范围）
- Admin 页面内部子路由的切换动画（统一 slide-left，不单独定制）
