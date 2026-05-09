<template>
  <div class="error-page">
    <div class="mood-blob drift blob-1" />
    <div class="mood-blob drift blob-2" />

    <div class="error-content">
      <div class="meta" style="letter-spacing: 0.22em;">SIGNAL LOST · 频道失联</div>

      <div class="display error-code">404</div>

      <h1 class="display" style="font-size: clamp(36px, 5vw, 80px); margin: 0; line-height: 1.0; font-style: italic;">
        Channel not found.
      </h1>

      <div class="display-cn" style="font-size: clamp(18px, 2vw, 28px); margin-top: 12px; color: var(--ink-2);">
        此频道似乎不存在
      </div>

      <p :style="{ fontFamily: 'var(--serif-cn)', fontSize: 'clamp(14px, 1.2vw, 17px)', color: 'var(--ink-2)', lineHeight: 1.7, maxWidth: '420px', margin: '20px auto 0' }">
        你调到了一个空白的频率——这里什么声音都没有。也许是链接出错，也许那首歌已经下线。
      </p>

      <div class="row" style="gap: 12px; margin-top: 32px; justify-content: center; flex-wrap: wrap;">
        <RouterLink to="/home" class="btn" style="text-decoration: none; display: inline-flex; align-items: center;">
          回到电台 · Back home
        </RouterLink>
        <button class="btn-pill" @click="$router.back()">‹ 返回上页</button>
      </div>

      <div class="meta" style="margin-top: 48px; color: var(--ink-3);">MoodFM · YOUR PRIVATE RADIO STATION</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.error-page {
  position: relative;
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  text-align: center;
  padding: 40px 24px;
}

.blob-1 {
  width: 600px;
  height: 600px;
  left: -220px;
  top: -180px;
  opacity: 0.28;
}

.blob-2 {
  width: 500px;
  height: 500px;
  right: -160px;
  bottom: -160px;
  opacity: 0.22;
}

.error-content {
  position: relative;
  z-index: 2;
  max-width: 600px;
}

.error-code {
  font-size: clamp(96px, 18vw, 260px);
  line-height: 0.85;
  margin: 16px 0 8px;
  background: linear-gradient(120deg, var(--mood-a), var(--mood-b), var(--mood-c));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
}
</style>
