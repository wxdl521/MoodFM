import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('moodfm_token'))
  const user = ref<User | null>(
    (() => {
      const raw = localStorage.getItem('moodfm_user')
      return raw ? JSON.parse(raw) : null
    })()
  )

  const isLoggedIn = computed(() => !!token.value)

  async function login(credentials: { email: string; password: string }) {
    const res = await authApi.login(credentials)
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('moodfm_token', res.data.token)
    localStorage.setItem('moodfm_user', JSON.stringify(res.data.user))
    return res.data
  }

  async function register(data: { username: string; email: string; password: string }) {
    const res = await authApi.register(data)
    return res.data
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('moodfm_token')
    localStorage.removeItem('moodfm_user')
    window.location.href = '/auth'
  }

  async function fetchMe() {
    const res = await authApi.me()
    user.value = res.data
    localStorage.setItem('moodfm_user', JSON.stringify(res.data))
    return res.data
  }

  return { token, user, isLoggedIn, login, register, logout, fetchMe }
})
