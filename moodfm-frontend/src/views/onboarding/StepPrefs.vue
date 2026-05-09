<template>
  <div>
    <div class="meta">CHAPTER THREE · 偏好</div>
    <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '96px', margin: '12px 0 0' }">
      What's in your
    </h1>
    <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '96px', margin: 0 }">
      <em>weather</em>?
    </h1>
    <div class="display-cn" :style="{ fontSize: isMobile ? '20px' : '26px', marginTop: '12px', color: 'var(--ink-2)' }">
      说说你的"音乐天气"——这些会成为电台的初始风向。
    </div>

    <div style="margin-top: 36px;">
      <div class="meta" style="margin-bottom: 12px;">FIELD A · 流派 / GENRES (多选)</div>
      <div style="display: flex; flex-wrap: wrap; gap: 8px;">
        <button
          v-for="g in allGenres"
          :key="g"
          :class="['btn-pill', { active: selectedGenres.has(g) }]"
          @click="toggle(selectedGenres, g)"
        >{{ g }}</button>
      </div>
    </div>

    <div style="margin-top: 28px;">
      <div class="meta" style="margin-bottom: 12px;">FIELD B · 语言 / LANGUAGES (多选)</div>
      <div style="display: flex; flex-wrap: wrap; gap: 8px;">
        <button
          v-for="l in allLang"
          :key="l"
          :class="['btn-pill', { active: selectedLang.has(l) }]"
          @click="toggle(selectedLang, l)"
        >{{ l }}</button>
      </div>
    </div>

    <div style="margin-top: 28px;">
      <div class="meta" style="margin-bottom: 12px;">FIELD C · 默认场景 / DEFAULT SCENE (单选)</div>
      <div style="display: flex; flex-wrap: wrap; gap: 8px;">
        <button
          v-for="s in allScene"
          :key="s"
          :class="['btn-pill', { active: selectedScene === s }]"
          @click="selectedScene = s"
        >{{ s }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

defineProps<{ isMobile: boolean }>()

const allGenres = ['Ambient', 'Classical', 'Folk', 'Indie', 'Electronic', 'Jazz', 'Hip-Hop', 'Rock', 'Pop', 'R&B', 'Post-Rock', 'Bossa Nova']
const allLang = ['中文', 'English', '日本語', '한국어', 'Français', 'Español', 'Instrumental']
const allScene = ['通勤', '学习', '跑步', '写作', '睡前', '深夜', '派对', '咖啡馆']

const selectedGenres = reactive(new Set(['Ambient', 'Folk', 'Indie']))
const selectedLang = reactive(new Set(['中文', 'English', '日本語']))
const selectedScene = ref('深夜')

function toggle(set: Set<string>, value: string) {
  if (set.has(value)) set.delete(value)
  else set.add(value)
}
</script>
