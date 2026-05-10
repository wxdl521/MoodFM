import { defineStore } from 'pinia'
import { ref } from 'vue'
import { radioApi } from '@/api/radio'
import type { RadioSession, Feedback } from '@/types'

export const useRadioStore = defineStore('radio', () => {
  const session = ref<RadioSession | null>(null)
  const moodText = ref('')
  const scene = ref('')
  const isLoading = ref(false)
  const recentSessions = ref<RadioSession[]>([])

  async function startRadio(data: { moodText: string; scene?: string }) {
    isLoading.value = true
    try {
      const res = await radioApi.startRadio(data)
      // interceptor already unwrapped R<RadioSession> → RadioSession
      session.value = res
      return res
    } finally {
      isLoading.value = false
    }
  }

  async function sendFeedback(data: Feedback) {
    const res = await radioApi.feedback(data)
    return res
  }

  async function fetchRecentSessions() {
    const res = await radioApi.getSessions(5)
    recentSessions.value = res
    return res
  }

  function setMoodText(t: string) {
    moodText.value = t
  }

  function setScene(s: string) {
    scene.value = s
  }

  return { session, moodText, scene, isLoading, recentSessions, startRadio, sendFeedback, fetchRecentSessions, setMoodText, setScene }
})
