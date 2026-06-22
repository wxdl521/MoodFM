<template>
  <div class="player-track">
    <div class="mono" style="font-size: 10px; letter-spacing: .2em; opacity: .7">
      TRACK {{ currentIndex }} / {{ totalTracks }} · {{ platformLabel }}
    </div>

    <h1 class="display player-title">
      {{ songTitle }}<em style="opacity: .5">.</em>
    </h1>
    <div style="display: flex; align-items: center; gap: 10px; margin-top: 8px;">
      <div style="font-family: var(--serif-cn); font-size: 24px; opacity: .9">{{ songArtist }}</div>
      <span v-if="isFallback" class="platform-fallback-badge">
        via 网易云
      </span>
    </div>

    <div class="why-card">
      <div class="mono" style="font-size: 10px; letter-spacing: .18em; opacity: .7">AI · WHY THIS SONG</div>
      <div style="margin-top: 8px; font-family: var(--serif-cn); font-size: 15px; line-height: 1.6">
        {{ whyText }}
      </div>
      <div class="meta" style="margin-top: 10px; color: rgba(255,255,255,0.7)">
        {{ songTags }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { useRadioStore } from '@/stores/radio'

const player = usePlayerStore()
const radio = useRadioStore()

const songTitle = computed(() => player.currentSong?.title ?? 'weightless')
const songArtist = computed(() => player.currentSong?.artist ?? 'Marconi Union')

const platformLabel = computed(() => {
  switch (player.currentSong?.platform) {
    case 'qqmusic': return 'QQ MUSIC'
    case 'netease': return 'NETEASE CLOUD'
    default:        return 'NETEASE CLOUD'
  }
})

const isFallback = computed(() =>
  player.currentSong?.urlSource === 'netease_fallback',
)

const totalTracks = computed(() => player.queue.length + 1)
const currentIndex = computed(() => String(player.trackNumber).padStart(2, '0'))

const whyText = computed(() => {
  const reason = player.currentSong?.recommendReason
  if (reason) return reason
  const mood = radio.moodText
  if (mood) return `你的心情是「${mood}」——所以选了这首，让感受先被接住。`
  return '这首曲子拥有稳定节奏，适合当下的心境，让神经系统放慢下来。'
})

const songTags = computed(() => {
  const tags = player.currentSong?.tags
  if (tags && tags.length) return '· ' + tags.join(' · ')
  return '· 低唤醒 · 环境音 · BPM 60'
})
</script>

<style scoped>
.player-track {
  display: flex;
  flex-direction: column;
}

.player-title {
  font-size: 96px;
  margin: 10px 0 0;
  line-height: 0.9;
  color: #fff;
  animation: player-rise 0.55s cubic-bezier(0.16, 1, 0.3, 1) 0.14s both;
}

@media (max-width: 900px) {
  .player-title { font-size: 52px; }
}

.why-card {
  margin-top: 24px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  animation: player-rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.24s both;
}

.platform-fallback-badge {
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: .12em;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.55);
  flex-shrink: 0;
  white-space: nowrap;
}

@keyframes player-rise {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>