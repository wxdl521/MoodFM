import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    depth?: number
    order?: number
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // ── Depth 0: Auth flow ────────────────────────────────────────────
    { path: '/',           component: () => import('@/views/Landing.vue'),        meta: { depth: 0, order: 0 } },
    { path: '/auth',       component: () => import('@/views/auth/Auth.vue'),      meta: { depth: 0, order: 1 } },
    { path: '/onboarding', component: () => import('@/views/Onboarding.vue'),     meta: { requiresAuth: true, depth: 0, order: 2 } },

    // ── Depth 1: Main entry ───────────────────────────────────────────
    { path: '/home', component: () => import('@/views/home/Home.vue'), meta: { requiresAuth: true, depth: 1, order: 0 } },

    // ── Depth 2: Main features (bottom nav order) ─────────────────────
    { path: '/search',    component: () => import('@/views/Search.vue'),               meta: { requiresAuth: true, depth: 2, order: 0 } },
    { path: '/player',    component: () => import('@/views/player/Player.vue'),        meta: { requiresAuth: true, depth: 2, order: 1 } },
    { path: '/playlists', component: () => import('@/views/library/PlaylistList.vue'), meta: { requiresAuth: true, depth: 2, order: 2 } },
    { path: '/likes',     component: () => import('@/views/library/Loved.vue'),        meta: { requiresAuth: true, depth: 2, order: 3 } },
    { path: '/history',   component: () => import('@/views/library/History.vue'),      meta: { requiresAuth: true, depth: 2, order: 4 } },
    { path: '/insights',  component: () => import('@/views/insights/Insights.vue'),    meta: { requiresAuth: true, depth: 2, order: 5 } },
    { path: '/settings',  component: () => import('@/views/settings/Settings.vue'),    meta: { requiresAuth: true, depth: 2, order: 6 } },
    { path: '/bind',      component: () => import('@/views/Bind.vue'),                 meta: { requiresAuth: true, depth: 2, order: 7 } },
    { path: '/profile',   component: () => import('@/views/Profile.vue'),              meta: { requiresAuth: true, depth: 2, order: 8 } },

    // ── Depth 3: Sub-pages ────────────────────────────────────────────
    { path: '/playlists/smart/:type',  component: () => import('@/views/library/SmartPlaylistDetail.vue'), meta: { requiresAuth: true, depth: 3 } },
    { path: '/playlists/:id',          component: () => import('@/views/library/Playlist.vue'),            meta: { requiresAuth: true, depth: 3 } },
    { path: '/song/:id',               component: () => import('@/views/library/SongDetail.vue'),          meta: { requiresAuth: true, depth: 3 } },
    { path: '/insights/calendar',      component: () => import('@/views/insights/Calendar.vue'),           meta: { requiresAuth: true, depth: 3 } },
    { path: '/insights/weekly/:week?', component: () => import('@/views/insights/Weekly.vue'),             meta: { requiresAuth: true, depth: 3 } },
    { path: '/insights/annual/:year?', component: () => import('@/views/insights/AnnualReport.vue'),       meta: { requiresAuth: true, depth: 3 } },
    { path: '/settings/platforms',     component: () => import('@/views/settings/Platforms.vue'),          meta: { requiresAuth: true, depth: 3 } },
    { path: '/settings/blacklist',     component: () => import('@/views/library/Blacklist.vue'),           meta: { requiresAuth: true, depth: 3 } },
    { path: '/settings/notifications', component: () => import('@/views/settings/Notifications.vue'),     meta: { requiresAuth: true, depth: 3 } },

    // ── Depth 4: Admin ───────────────────────────────────────────────────
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true, depth: 4 },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard',     component: () => import('@/views/admin/AdminDashboard.vue'),     meta: { depth: 4 } },
        { path: 'users',         component: () => import('@/views/admin/AdminUsers.vue'),         meta: { depth: 4 } },
        { path: 'music',         component: () => import('@/views/admin/AdminMusic.vue'),         meta: { depth: 4 } },
        { path: 'platforms',     component: () => import('@/views/admin/AdminPlatforms.vue'),     meta: { depth: 4 } },
        { path: 'ai',            component: () => import('@/views/admin/AdminAIEngine.vue'),      meta: { depth: 4 } },
        { path: 'analytics',     component: () => import('@/views/admin/AdminAnalytics.vue'),     meta: { depth: 4 } },
        { path: 'notifications', component: () => import('@/views/admin/AdminNotifications.vue'), meta: { depth: 4 } },
        { path: 'system',        component: () => import('@/views/admin/AdminSystem.vue'),        meta: { depth: 4 } },
      ]
    },

    // ── Redirects ─────────────────────────────────────────────────────
    { path: '/loved',     redirect: '/likes' },
    { path: '/blacklist', redirect: '/settings/blacklist' },
    { path: '/songs/:id', redirect: to => `/song/${to.params.id}` },

    // ── Fallback ──────────────────────────────────────────────────────
    { path: '/:pathMatch(.*)*', component: () => import('@/views/ErrorPage.vue'), meta: { depth: 0 } },
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
