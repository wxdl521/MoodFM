<template>
  <div :style="{
    display: 'flex',
    flexDirection: isMobile ? 'column' : 'row',
    alignItems: 'center',
    gap: isMobile ? '32px' : '56px',
    paddingTop: isMobile ? '20px' : '60px',
  }">
    <MoodBlob :size="isMobile ? 280 : 380" :drift="true" geometry="blob" />
    <div>
      <div class="meta">CHAPTER FOUR · 准备好了</div>
      <h1 class="display" :style="{ fontSize: isMobile ? '60px' : '120px', margin: '12px 0 0' }">
        You're <em>tuned</em>.
      </h1>
      <div class="display-cn" :style="{ fontSize: isMobile ? '26px' : '36px', marginTop: '12px' }">
        调好了。今晚第一档电台准备启动。
      </div>
      <p :style="{ marginTop: '20px', fontSize: '15px', lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: '460px', fontFamily: 'var(--serif-cn)' }">
        已绑定 1 个平台 · 偏好已记录 · 你的播放历史会持续优化推荐。
        点"进入电台"——我们已经替你准备了第一首。
      </p>
      <div class="row" style="margin-top: 28px; gap: 10px; color: var(--ink-3);">
        <span class="meta">FIRST UP →</span>
        <span :style="{ fontFamily: 'var(--serif-en)', fontSize: '22px', fontStyle: 'italic', color: 'var(--ink)' }">weightless</span>
        <span :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', color: 'var(--ink-2)' }">· Marconi Union</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import MoodBlob from '@/components/common/MoodBlob.vue'

defineProps<{ isMobile: boolean }>()
</script>
