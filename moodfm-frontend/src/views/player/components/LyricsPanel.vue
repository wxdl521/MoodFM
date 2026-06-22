<template>
  <Transition name="fade">
    <div v-if="open" class="lyrics-overlay" @click="emit('close')">
      <div class="lyrics-inner" @click.stop>
        <div class="mono" style="font-size: 10px; letter-spacing: .18em; opacity: .7; margin-bottom: 20px">
          LYRICS · 歌词 · {{ songTitle }}
        </div>

        <div v-if="lyricsLoading" style="font-family: var(--serif-cn); font-size: 16px; opacity: .6; padding: 40px 0">
          加载中…
        </div>

        <div v-else-if="!lyricsLines.length" style="font-family: var(--serif-cn); font-size: 18px; line-height: 2.2; opacity: .9">
          <p>暂无歌词</p>
          <p style="font-size: 13px; opacity: .6; margin-top: 16px">此曲只应天上有，人间哪得几回闻</p>
        </div>

        <div v-else :ref="bindScrollEl" class="lyrics-lines">
          <p
            v-for="(line, i) in lyricsLines"
            :key="i"
            :class="['lyric-line', { 'lyric-line--active': i === activeLyricIdx }]"
          >{{ line.text }}</p>
        </div>

        <button class="chip-btn" style="margin-top: 24px" @click="emit('close')">关闭</button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import type { LyricLine } from '@/api/song'

defineProps<{
  open: boolean
  songTitle: string
  lyricsLines: LyricLine[]
  lyricsLoading: boolean
  activeLyricIdx: number
}>()

const emit = defineEmits<{ close: [] }>()

const lyricsScrollEl = defineModel<HTMLElement | null>('lyricsScrollEl')

function bindScrollEl(el: unknown) {
  lyricsScrollEl.value = el instanceof HTMLElement ? el : null
}
</script>

<style scoped>
.lyrics-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.72);
  backdrop-filter: blur(20px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.lyrics-inner {
  max-width: 520px;
  width: 100%;
  color: #fff;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-height: 85vh;
}

.lyrics-lines {
  width: 100%;
  max-height: 58vh;
  overflow-y: auto;
  scrollbar-width: none;
  padding: 0 8px;
}

.lyrics-lines::-webkit-scrollbar { display: none; }

.lyric-line {
  font-family: var(--serif-cn);
  font-size: 16px;
  line-height: 2.4;
  opacity: 0.35;
  transition: opacity 0.3s ease, font-size 0.25s ease, color 0.25s ease;
  margin: 0;
  cursor: default;
}

.lyric-line--active {
  font-size: 22px;
  opacity: 1;
  color: #fff;
}

.chip-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: transparent;
  color: #fff;
  cursor: pointer;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: .12em;
  text-transform: uppercase;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.chip-btn:hover { background: rgba(255, 255, 255, 0.1); }

.fade-enter-active,
.fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from,
.fade-leave-to     { opacity: 0; }
</style>