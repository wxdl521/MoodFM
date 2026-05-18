import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Theme, MoodPreset } from '@/types'

export const useUiStore = defineStore('ui', () => {
  const theme = ref<Theme>((localStorage.getItem('moodfm_theme') as Theme) || 'light')
  const moodPreset = ref<MoodPreset>('dusk')
  const queueDrawerOpen = ref(false)

  function setTheme(t: Theme) {
    theme.value = t
    localStorage.setItem('moodfm_theme', t)
    document.documentElement.setAttribute('data-theme', t)
  }

  function setMoodPreset(p: MoodPreset) {
    moodPreset.value = p
  }

  function toggleQueueDrawer() {
    queueDrawerOpen.value = !queueDrawerOpen.value
  }

  return { theme, moodPreset, queueDrawerOpen, setTheme, setMoodPreset, toggleQueueDrawer }
})
