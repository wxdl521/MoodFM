import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    depth?: number
    order?: number
    showNav?: boolean
  }
}

// 路由层级，用于页面切换方向（深 ↔ 浅 决定 slide-left / slide-right）
export const DEPTH = {
  AUTH: 0,        // /landing /login /register
  MAIN: 1,        // /home
  FEATURE: 2,     // /player /library
  SUB: 3,         // /library/playlist/:id /library/history 等子页
  ADMIN: 4,       // /admin/*
} as const

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // ── Depth 0: Auth flow ────────────────────────────────────────────
    { path: '/',           component: () => import('@/views/Landing.vue'),        meta: { depth: DEPTH.AUTH, order: 0 } },
    { path: '/auth',       component: () => import('@/views/auth/Auth.vue'),      meta: { depth: DEPTH.AUTH, order: 1 } },
    { path: '/onboarding', component: () => import('@/views/Onboarding.vue'),     meta: { requiresAuth: true, depth: DEPTH.AUTH, order: 2 } },

    // ── Depth 1: Main entry ───────────────────────────────────────────
    { path: '/home', component: () => import('@/views/home/Home.vue'), meta: { requiresAuth: true, depth: DEPTH.MAIN, order: 0, showNav: true } },

    // ── Depth 2: Main features (bottom nav order) ─────────────────────
    { path: '/search',    component: () => import('@/views/Search.vue'),               meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 0, showNav: true } },
    { path: '/player',    component: () => import('@/views/player/Player.vue'),        meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 1 } },
    { path: '/playlists', component: () => import('@/views/library/PlaylistList.vue'), meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 2, showNav: true } },
    { path: '/likes',     component: () => import('@/views/library/Loved.vue'),        meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 3 } },
    { path: '/history',   component: () => import('@/views/library/History.vue'),      meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 4, showNav: true } },
    { path: '/insights',  component: () => import('@/views/insights/Insights.vue'),    meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 5, showNav: true } },
    { path: '/settings',  component: () => import('@/views/settings/Settings.vue'),    meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 6, showNav: true } },
    { path: '/bind',      component: () => import('@/views/Bind.vue'),                 meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 7 } },
    { path: '/profile',   component: () => import('@/views/Profile.vue'),              meta: { requiresAuth: true, depth: DEPTH.FEATURE, order: 8, showNav: true } },

    // ── Depth 3: Sub-pages ────────────────────────────────────────────
    { path: '/playlists/smart/:type',  component: () => import('@/views/library/SmartPlaylistDetail.vue'), meta: { requiresAuth: true, depth: DEPTH.SUB } },
    { path: '/playlists/:id',          component: () => import('@/views/library/Playlist.vue'),            meta: { requiresAuth: true, depth: DEPTH.SUB } },
    { path: '/song/:id',               component: () => import('@/views/library/SongDetail.vue'),          meta: { requiresAuth: true, depth: DEPTH.SUB } },
    { path: '/insights/calendar',      component: () => import('@/views/insights/Calendar.vue'),           meta: { requiresAuth: true, depth: DEPTH.SUB, showNav: true } },
    { path: '/insights/weekly/:week?', component: () => import('@/views/insights/Weekly.vue'),             meta: { requiresAuth: true, depth: DEPTH.SUB, showNav: true } },
    { path: '/insights/annual/:year?', component: () => import('@/views/insights/AnnualReport.vue'),       meta: { requiresAuth: true, depth: DEPTH.SUB, showNav: true } },
    { path: '/settings/platforms',     component: () => import('@/views/settings/Platforms.vue'),          meta: { requiresAuth: true, depth: DEPTH.SUB } },
    { path: '/settings/blacklist',     component: () => import('@/views/library/Blacklist.vue'),           meta: { requiresAuth: true, depth: DEPTH.SUB, showNav: true } },
    { path: '/settings/notifications', component: () => import('@/views/settings/Notifications.vue'),     meta: { requiresAuth: true, depth: DEPTH.SUB, showNav: true } },

    // ── Depth 4: Admin ───────────────────────────────────────────────────
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true, depth: DEPTH.ADMIN },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard',     component: () => import('@/views/admin/AdminDashboard.vue'),     meta: { depth: DEPTH.ADMIN } },
        { path: 'users',         component: () => import('@/views/admin/AdminUsers.vue'),         meta: { depth: DEPTH.ADMIN } },
        { path: 'music',         component: () => import('@/views/admin/AdminMusic.vue'),         meta: { depth: DEPTH.ADMIN } },
        { path: 'platforms',     component: () => import('@/views/admin/AdminPlatforms.vue'),     meta: { depth: DEPTH.ADMIN } },
        { path: 'ai',            component: () => import('@/views/admin/AdminAIEngine.vue'),      meta: { depth: DEPTH.ADMIN } },
        { path: 'analytics',     component: () => import('@/views/admin/AdminAnalytics.vue'),     meta: { depth: DEPTH.ADMIN } },
        { path: 'notifications', component: () => import('@/views/admin/AdminNotifications.vue'), meta: { depth: DEPTH.ADMIN } },
        { path: 'system',        component: () => import('@/views/admin/AdminSystem.vue'),        meta: { depth: DEPTH.ADMIN } },
      ]
    },

    // ── Redirects ─────────────────────────────────────────────────────
    { path: '/loved',     redirect: '/likes' },
    { path: '/blacklist', redirect: '/settings/blacklist' },
    { path: '/songs/:id', redirect: to => `/song/${to.params.id}` },

    // ── Fallback ──────────────────────────────────────────────────────
    { path: '/:pathMatch(.*)*', component: () => import('@/views/ErrorPage.vue'), meta: { depth: DEPTH.AUTH } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/auth'
  }
  if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
    return '/home'
  }
})

export default router
