import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '@/api/user'
import type { User, UserPreferences } from '@/types'

export const useUserStore = defineStore('user', () => {
  const profile = ref<User | null>(null)
  const preferences = ref<UserPreferences | null>(null)

  async function fetchProfile() {
    const res = await userApi.getProfile()
    profile.value = res
    return res
  }

  async function updateProfile(data: Partial<User>) {
    const res = await userApi.updateProfile(data)
    profile.value = res
    return res
  }

  async function fetchPreferences() {
    const res = await userApi.getPreferences()
    preferences.value = res
    return res
  }

  async function updatePreferences(data: Partial<UserPreferences>) {
    const res = await userApi.updatePreferences(data)
    preferences.value = res
    return res
  }

  return { profile, preferences, fetchProfile, updateProfile, fetchPreferences, updatePreferences }
})
