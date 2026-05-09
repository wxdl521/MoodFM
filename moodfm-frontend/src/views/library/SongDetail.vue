<template>
  <div style="min-height: 100vh; background: var(--bg); padding-bottom: 100px;">
    <div
      class="topbar"
      style="position: sticky; top: 0; z-index: 50; background: var(--bg); border-bottom: 1px solid var(--rule); display: flex; align-items: center; justify-content: space-between;"
    >
      <button class="btn-pill" @click="$router.back()">← 返回</button>
      <div class="meta">SONG DETAIL · 歌曲详情</div>
      <div style="width: 60px;" />
    </div>

    <div v-if="loading" class="page-pad">
      <div class="skeleton-hero" />
      <div class="skeleton-row" style="margin-top: 24px;" />
      <div class="skeleton-row" />
    </div>

    <div v-else-if="error" class="page-pad">
      <div class="meta" style="color: var(--ink-3);">加载失败 · {{ error }}</div>
    </div>

    <template v-else-if="song">
      <div class="hero-section">
        <div
          class="mood-blob drift"
          style="width: 700px; height: 700px; right: -200px; top: -200px; opacity: 0.3; position: absolute;"
        />

        <div style="position: relative; z-index: 2;" class="hero-grid">
          <div class="cover-wrap">
            <div class="cover-box">
              <MoodBlob :size="coverSize" :drift="true" geometry="blob" />
            </div>
          </div>

          <div>
            <div class="meta">{{ platformLabel }} · {{ song.tags?.join(' · ') ?? '' }}</div>
            <h1 class="display" style="font-size: clamp(44px, 6.5vw, 100px); margin: 12px 0 0; line-height: 0.95;">
              {{ song.title }}
            </h1>
            <div class="display-cn" style="font-size: clamp(18px, 2.5vw, 28px); margin-top: 6px; color: var(--ink-2);">
              {{ song.artist }}
            </div>

            <div class="row" style="margin-top: 20px; gap: 28px; flex-wrap: wrap; color: var(--ink-3);">
              <div v-if="song.album">
                <div class="meta">ALBUM</div>
                <div class="mono" style="font-size: 13px; color: var(--ink); margin-top: 2px;">{{ song.album }}</div>
              </div>
              <div>
                <div class="meta">DURATION</div>
                <div class="mono" style="font-size: 13px; color: var(--ink); margin-top: 2px;">{{ formatDuration(song.duration) }}</div>
              </div>
              <div v-if="song.bpm">
                <div class="meta">BPM</div>
                <div class="mono" style="font-size: 13px; color: var(--ink); margin-top: 2px;">{{ song.bpm }}</div>
              </div>
            </div>

            <div class="row" style="margin-top: 28px; gap: 10px; flex-wrap: wrap;">
              <button class="btn" style="height: 48px; padding: 0 22px; font-size: 14px;" @click="playSong">
                ▶ 播放
              </button>
              <button class="btn-pill" style="height: 48px;" @click="addToQueue">+ 队列</button>
              <button
                class="btn-pill like-btn"
                :class="{ active: liked }"
                :style="liked ? { color: 'var(--mood-b)', borderColor: 'var(--mood-b)' } : {}"
                style="height: 48px; font-size: 18px;"
                @click="toggleLike"
              >{{ liked ? '♥' : '♡' }}</button>
              <button class="btn-pill" style="height: 48px;" @click="addToBlacklist">
                ⊘ 拉黑
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="page-pad">
        <div v-if="song.tags && song.tags.length > 0" class="glass-card" style="margin-bottom: 40px;">
          <div class="meta" style="margin-bottom: 10px;">AI 推荐理由 · WHY THIS SONG</div>
          <p style="font-family: var(--serif-cn); font-size: 15px; line-height: 1.8; color: var(--ink-2); margin: 0;">
            根据你的情绪画像，这首歌的风格与你当前的心情高度匹配。
          </p>
          <div class="row" style="gap: 8px; flex-wrap: wrap; margin-top: 14px;">
            <span
              v-for="tag in song.tags"
              :key="tag"
              class="meta"
              style="padding: 4px 10px; border: 1px solid var(--rule); border-radius: 999px; color: var(--ink-2);"
            >{{ tag }}</span>
          </div>
        </div>

        <div>
          <div class="meta" style="margin-bottom: 16px;">RELATED · 相关歌曲</div>
          <div class="meta" style="color: var(--ink-3); padding: 24px 0;">暂无相关歌曲</div>
        </div>
      </div>
    </template>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { songApi } from '@/api/song'
import { blacklistApi } from '@/api/blacklist'
import { playlistApi } from '@/api/playlist'
import { usePlayerStore } from '@/stores/player'
import type { Song } from '@/types'

const route = useRoute()
const player = usePlayerStore()

const song = ref<Song | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const liked = ref(false)

const coverSize = window.innerWidth < 768 ? 220 : 300

const platformLabel = computed(() => {
  const p = song.value?.platform
  if (p === 'netease') return '网易云'
  if (p === 'qqmusic') return 'QQ音乐'
  return p ?? ''
})

function formatDuration(secs: number) {
  if (!secs) return '--:--'
  const m = Math.floor(secs / 60)
  const s = secs % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function playSong() {
  if (!song.value) return
  player.setSong(song.value)
  if (!player.isPlaying) player.togglePlay()
}

function addToQueue() {
  if (!song.value) return
  player.addToQueue([song.value])
}

async function toggleLike() {
  if (!song.value) return
  try {
    await playlistApi.toggleLike(song.value.id)
    liked.value = !liked.value
  } catch {}
}

async function addToBlacklist() {
  if (!song.value) return
  try {
    await blacklistApi.add({
      type: 'song',
      value: song.value.id,
      label: song.value.title,
    })
  } catch {}
}

onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await songApi.get(id)
    song.value = res.data
  } catch (e: any) {
    error.value = e?.message ?? '未知错误'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.topbar {
  padding: 18px 56px;
  height: 60px;
}

@media (max-width: 768px) {
  .topbar {
    padding: 14px 18px;
  }
}

.hero-section {
  position: relative;
  overflow: hidden;
  padding: 56px 56px 48px;
  border-bottom: 1px solid var(--rule);
}

@media (max-width: 768px) {
  .hero-section {
    padding: 28px 22px 32px;
  }
}

.hero-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 48px;
  align-items: end;
}

@media (max-width: 768px) {
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 28px;
  }
}

.cover-wrap {
  display: flex;
  justify-content: flex-start;
}

.cover-box {
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 28px 64px rgba(0, 0, 0, 0.22);
  width: 300px;
  height: 300px;
}

@media (max-width: 768px) {
  .cover-box {
    width: 220px;
    height: 220px;
  }
}

.page-pad {
  padding: 40px 56px;
}

@media (max-width: 768px) {
  .page-pad {
    padding: 24px 18px;
  }
}

.glass-card {
  padding: 24px 28px;
  border-radius: 16px;
  border: 1px solid var(--rule);
  background: var(--paper);
  backdrop-filter: blur(12px);
}

.like-btn.active {
  border-color: var(--mood-b);
}

.skeleton-hero {
  height: 320px;
  border-radius: 18px;
  background: var(--bg-2);
  animation: pulse 1.6s ease-in-out infinite;
}

.skeleton-row {
  height: 48px;
  border-radius: 8px;
  background: var(--bg-2);
  margin-bottom: 8px;
  animation: pulse 1.6s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}
</style>
