import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { User } from '@/types'
import { logger } from '@/utils/logger'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('moodfm_token'))
  const user = ref<User | null>(
    (() => {
      const raw = localStorage.getItem('moodfm_user')
      let parsed = null
      try { parsed = raw ? JSON.parse(raw) : null } catch (err) {
        // silent: localStorage 数据损坏时静默回退 null
        logger.warn('auth:parse-user-cache', err)
        parsed = null
      }
      return parsed
    })()
  )

  const isLoggedIn = computed(() => !!token.value)

  async function login(credentials: { account: string; password: string; rememberMe?: boolean }) {
    const res = await authApi.login(credentials)
    // interceptor already unwrapped R<AuthResponse> → LoginVO
    token.value = res.accessToken
    user.value = res.user
    localStorage.setItem('moodfm_token', res.accessToken)
    localStorage.setItem('moodfm_user', JSON.stringify(res.user))
    if (res.refreshToken) localStorage.setItem('moodfm_refresh_token', res.refreshToken)
    return res
  }

  async function register(data: { username: string; email: string; password: string }) {
    const res = await authApi.register(data)
    return res
  }

  async function logout() {
    try {
      if (token.value) {
        await authApi.logout(token.value)
      }
    } catch (err) {
      // silent: 登出请求失败也无妨，仍然清空本地状态走到 /auth
      logger.warn('auth:logout-api', err)
    }
    token.value = null
    user.value = null
    localStorage.removeItem('moodfm_token')
    localStorage.removeItem('moodfm_user')
    localStorage.removeItem('moodfm_refresh_token')
    window.location.href = '/auth'
  }

  async function fetchMe() {
    const res = await authApi.me()
    user.value = res
    localStorage.setItem('moodfm_user', JSON.stringify(res))
    return res
  }

  async function validate() {
    if (!token.value) return
    try {
      await fetchMe()
    } catch (err) {
      // silent: token 失效后会走 logout 重定向到 /auth，用户能看到登录界面
      logger.warn('auth:validate', err)
      logout()
    }
  }

  function setTokens(accessToken: string, newRefreshToken?: string) {
    token.value = accessToken
    if (newRefreshToken) localStorage.setItem('moodfm_refresh_token', newRefreshToken)
  }

  return { token, user, isLoggedIn, login, register, logout, fetchMe, validate, setTokens }
})
