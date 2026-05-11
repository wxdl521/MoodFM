import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/views/Landing.vue') },
    { path: '/auth', component: () => import('@/views/auth/Auth.vue') },
    { path: '/onboarding', component: () => import('@/views/Onboarding.vue'), meta: { requiresAuth: true } },
    { path: '/bind', component: () => import('@/views/Bind.vue'), meta: { requiresAuth: true } },
    { path: '/home', component: () => import('@/views/home/Home.vue'), meta: { requiresAuth: true } },
    { path: '/player', component: () => import('@/views/player/Player.vue'), meta: { requiresAuth: true } },
    { path: '/playlists', component: () => import('@/views/library/PlaylistList.vue'), meta: { requiresAuth: true } },
    { path: '/playlists/smart/:type', component: () => import('@/views/library/SmartPlaylistDetail.vue'), meta: { requiresAuth: true } },
    { path: '/playlists/:id', component: () => import('@/views/library/Playlist.vue'), meta: { requiresAuth: true } },
    { path: '/likes', component: () => import('@/views/library/Loved.vue'), meta: { requiresAuth: true } },
    { path: '/loved', redirect: '/likes' },
    { path: '/history', component: () => import('@/views/library/History.vue'), meta: { requiresAuth: true } },
    { path: '/settings/blacklist', component: () => import('@/views/library/Blacklist.vue'), meta: { requiresAuth: true } },
    { path: '/settings/notifications', component: () => import('@/views/settings/Notifications.vue'), meta: { requiresAuth: true } },
    { path: '/blacklist', redirect: '/settings/blacklist' },
    { path: '/song/:id', component: () => import('@/views/library/SongDetail.vue'), meta: { requiresAuth: true } },
    { path: '/songs/:id', redirect: to => `/song/${to.params.id}` },
    { path: '/insights', component: () => import('@/views/insights/Insights.vue'), meta: { requiresAuth: true } },
    { path: '/insights/calendar', component: () => import('@/views/insights/Calendar.vue'), meta: { requiresAuth: true } },
    { path: '/insights/weekly/:week?', component: () => import('@/views/insights/Weekly.vue'), meta: { requiresAuth: true } },
    { path: '/insights/annual/:year?', component: () => import('@/views/insights/AnnualReport.vue'), meta: { requiresAuth: true } },
    { path: '/settings', component: () => import('@/views/settings/Settings.vue'), meta: { requiresAuth: true } },
    { path: '/settings/platforms', component: () => import('@/views/settings/Platforms.vue'), meta: { requiresAuth: true } },
    { path: '/profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
    { path: '/admin', component: () => import('@/views/admin/Admin.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/ErrorPage.vue') },
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
