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
    // interceptor already unwrapped R<AuthResponse> → AuthResponse
    token.value = res.token
    user.value = res.user
    localStorage.setItem('moodfm_token', res.token)
    localStorage.setItem('moodfm_user', JSON.stringify(res.user))
    return res
  }

  async function register(data: { username: string; email: string; password: string }) {
    const res = await authApi.register(data)
    return res
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
    user.value = res
    localStorage.setItem('moodfm_user', JSON.stringify(res))
    return res
  }

  return { token, user, isLoggedIn, login, register, logout, fetchMe }
})
