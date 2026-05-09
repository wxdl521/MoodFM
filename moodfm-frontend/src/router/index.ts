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
    { path: '/playlists/:id', component: () => import('@/views/library/Playlist.vue'), meta: { requiresAuth: true } },
    { path: '/loved', component: () => import('@/views/library/Loved.vue'), meta: { requiresAuth: true } },
    { path: '/history', component: () => import('@/views/library/History.vue'), meta: { requiresAuth: true } },
    { path: '/blacklist', component: () => import('@/views/library/Blacklist.vue'), meta: { requiresAuth: true } },
    { path: '/songs/:id', component: () => import('@/views/library/SongDetail.vue'), meta: { requiresAuth: true } },
    { path: '/insights', component: () => import('@/views/insights/Insights.vue'), meta: { requiresAuth: true } },
    { path: '/insights/calendar', component: () => import('@/views/insights/Calendar.vue'), meta: { requiresAuth: true } },
    { path: '/insights/weekly', component: () => import('@/views/insights/Weekly.vue'), meta: { requiresAuth: true } },
    { path: '/settings', component: () => import('@/views/settings/Settings.vue'), meta: { requiresAuth: true } },
    { path: '/profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/ErrorPage.vue') },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/auth'
  }
})

export default router
