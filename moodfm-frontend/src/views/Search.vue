<template>
  <div class="search-page">
    <div class="mood-blob drift" style="width:600px;height:600px;right:-180px;top:-180px;opacity:0.25;z-index:0;" />


    <div class="search-wrap">
      <!-- Mode tabs -->
      <div class="mode-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.mode"
          class="mode-tab"
          :class="{ 'mode-tab--active': activeMode === tab.mode }"
          @click="switchMode(tab.mode)"
        >
          <span class="mono" style="font-size:10px;letter-spacing:.14em;margin-right:6px;">{{ tab.en }}</span>
          {{ tab.label }}
        </button>
      </div>

      <!-- Search input -->
      <div class="search-bar-wrap">
        <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="7.5"/><line x1="16.8" y1="16.8" x2="22" y2="22"/>
        </svg>
        <input
          ref="inputEl"
          v-model="query"
          class="search-input"
          :placeholder="activePlaceholder"
          autocomplete="off"
          @keydown.esc="query = ''"
        />
        <button v-if="query" class="search-clear" @click="query = ''; inputEl?.focus()">×</button>
      </div>

      <!-- Mode hint -->
      <div class="meta search-hint">{{ activeHint }}</div>

      <!-- Results -->
      <div v-if="loading" class="search-state">
        <div class="mono" style="font-size:12px;letter-spacing:.14em;color:var(--ink-3)">SEARCHING…</div>
      </div>

      <div v-else-if="query.length >= 2 && songs.length === 0 && !loading" class="search-state">
        <div style="font-family:var(--serif-en);font-style:italic;font-size:28px;color:var(--ink-3)">Nothing.</div>
        <div v-if="notice" class="meta" style="margin-top:8px;color:var(--ink-3)">· {{ notice }}</div>
        <div v-else class="meta" style="margin-top:8px;color:var(--ink-3)">· 换个词试试</div>
      </div>

      <TransitionGroup
        v-else-if="songs.length > 0"
        tag="div"
        name="result"
        class="results-list"
      >
        <div
          v-for="(song, i) in songs"
          :key="song.platform + ':' + song.platformSongId"
          class="result-row"
          :style="[
            playingIndex === i ? { opacity: '0.6', pointerEvents: 'none' } : {},
            { '--i': Math.min(i, 8) }
          ]"
          @click="playSong(song, i)"
        >
          <div class="result-index mono">{{ String(i + 1).padStart(2, '0') }}</div>
          <img v-if="song.coverUrl" :src="song.coverUrl" class="result-cover" :alt="song.title" />
          <div v-else class="result-cover result-cover--empty" />
          <div class="result-info">
            <div class="result-title">{{ song.title }}</div>
            <div class="meta result-sub">{{ song.artist }}<span v-if="song.album"> · {{ song.album }}</span></div>
          </div>
          <div class="result-meta">
            <span v-if="song.platform" class="platform-badge" :class="'platform--' + song.platform">{{ platformLabel(song.platform) }}</span>
            <span v-if="song.durationSeconds" class="mono" style="font-size:11px;color:var(--ink-3);margin-left:12px;">{{ formatDur(song.durationSeconds) }}</span>
          </div>
        </div>
        <div v-if="hasMore && !loading" class="load-more-wrap">
          <button class="load-more-btn" @click="loadMore">加载更多</button>
        </div>
      </TransitionGroup>

      <div v-else-if="!query" class="search-state">
        <div style="font-family:var(--serif-en);font-style:italic;font-size:28px;color:var(--ink-3)">Search.</div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { usePlayerStore } from '@/stores/player'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import { searchApi } from '@/api/search'
import { radioApi } from '@/api/radio'
import type { SearchMode } from '@/api/search'
import type { SongVO, Song } from '@/types'
import { logger } from '@/utils/logger'

const router = useRouter()
const player = usePlayerStore()

type TabDef = { mode: SearchMode; label: string; en: string; placeholder: string; hint: string }

const tabs: TabDef[] = [
  {
    mode: 'keyword',
    label: '关键词',
    en: 'KW',
    placeholder: '歌名、艺人、专辑…',
    hint: '精确搜索 · 结果来自你绑定的平台',
  },
  {
    mode: 'mood',
    label: '心情',
    en: 'MOOD',
    placeholder: '描述此刻的感受，比如：深夜忧郁、想要振奋…',
    hint: '语义搜索 · 用情绪找到对应的歌',
  },
]

const activeMode = ref<SearchMode>('keyword')
const activeTab = computed(() => tabs.find(t => t.mode === activeMode.value)!)
const activePlaceholder = computed(() => activeTab.value.placeholder)
const activeHint = computed(() => activeTab.value.hint)

const query = ref('')
const loading = ref(false)
const songs = ref<SongVO[]>([])
const notice = ref<string | undefined>()
const inputEl = ref<HTMLInputElement | null>(null)
const currentLimit = ref(20)
const hasMore = ref(true)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
// AbortController for search requests — cancelled on each new query/mode change
let searchAbortController: AbortController | null = null
// AbortController for play URL requests — cancelled when user clicks a different song
let playAbortController: AbortController | null = null

async function doSearch(limit: number) {
  // Cancel any in-flight search request before starting a new one
  if (searchAbortController) {
    searchAbortController.abort()
  }
  searchAbortController = new AbortController()
  const signal = searchAbortController.signal

  try {
    const res = await searchApi.search(query.value.trim(), activeMode.value, limit, signal)
    songs.value = res.songs
    notice.value = res.notice
    hasMore.value = res.songs.length >= limit && limit < 50
  } catch (e: unknown) {
    if (axios.isCancel(e)) return  // Request was intentionally cancelled — do nothing
    songs.value = []
  } finally {
    if (!signal.aborted) loading.value = false  // Only clear loading if this request wasn't cancelled
  }
}

watch([query, activeMode], () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  currentLimit.value = 20
  if (query.value.length < 2) {
    songs.value = []
    notice.value = undefined
    hasMore.value = true
    return
  }
  loading.value = true
  debounceTimer = setTimeout(() => doSearch(currentLimit.value), 400)
})

function loadMore() {
  currentLimit.value = 50
  loading.value = true
  doSearch(currentLimit.value)
}

function switchMode(m: SearchMode) {
  activeMode.value = m
  songs.value = []
  notice.value = undefined
  inputEl.value?.focus()
}

function platformLabel(p: string): string {
  return p === 'netease' ? '网易云' : p === 'qqmusic' ? 'QQ音乐' : p
}

function formatDur(secs: number): string {
  const m = Math.floor(secs / 60)
  const s = secs % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function voToSong(vo: SongVO): Song {
  return {
    id: String(vo.id ?? ''),
    title: vo.title,
    artist: vo.artist,
    album: vo.album,
    platform: (vo.platform ?? 'netease') as Song['platform'],
    platformSongId: vo.platformSongId ?? '',
    duration: vo.durationSeconds ?? 0,
    coverUrl: vo.coverUrl,
    audioUrl: vo.playUrl ?? undefined,
    recommendReason: vo.recommendReason,
  }
}

const playingIndex = ref<number | null>(null)

async function playSong(song: SongVO, index: number) {
  // Cancel any in-flight getSongUrl request from a previous click
  if (playAbortController) {
    playAbortController.abort()
  }
  playAbortController = new AbortController()
  const playSignal = playAbortController.signal

  playingIndex.value = index
  try {
    const mapped = voToSong(song)

    // Fetch play URL if platform info is available
    if (song.platform && song.platformSongId) {
      try {
        const url = await radioApi.getSongUrl(song.platform, song.platformSongId)
        if (url) mapped.audioUrl = url
      } catch (err) {
        // silent: 获取播放 URL 失败时跳到 Player，由播放页统一处理「无法播放」UI
        logger.warn('search:get-song-url', err)
      }
    }

    // If user already clicked another song while we were fetching, bail out
    if (playSignal.aborted) return

    // Set this song + remaining results as queue
    const queueSongs = songs.value.slice(index + 1).map(voToSong)
    player.setSessionId('')
    player.setSong(mapped)
    player.setQueue(queueSongs)
    player.setPlaying(true)
    router.push('/player')
  } finally {
    if (!playSignal.aborted) playingIndex.value = null
  }
}
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
  padding-bottom: 100px;
}

.search-wrap {
  position: relative;
  z-index: 2;
  max-width: 760px;
  margin: 0 auto;
  padding: 40px 56px 80px;
}

@media (max-width: 768px) {
  .search-wrap { padding: 24px 22px 80px; }
}

/* Mode tabs */
.mode-tabs {
  display: flex;
  gap: 0;
  border: 1px solid var(--rule);
  border-radius: 999px;
  padding: 3px;
  width: fit-content;
  margin-bottom: 24px;
}

.mode-tab {
  padding: 7px 18px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  background: transparent;
  color: var(--ink-3);
  font-family: var(--serif-cn);
  font-size: 14px;
  transition: background 0.15s, color 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.mode-tab--active {
  background: var(--ink);
  color: var(--bg);
}

.mode-tab:not(.mode-tab--active):hover {
  transform: scale(1.04);
  color: var(--ink-2);
}

.mode-tab:active {
  transform: scale(0.95);
}

/* Search bar */
.search-bar-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 16px;
  color: var(--ink-3);
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 56px;
  padding: 0 48px;
  font-family: var(--serif-cn);
  font-size: 18px;
  background: var(--paper);
  border: 1px solid var(--rule);
  border-radius: 16px;
  color: var(--ink);
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: var(--ink-3);
}

.search-clear {
  position: absolute;
  right: 14px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: var(--bg-2);
  color: var(--ink-2);
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-hint {
  margin-top: 10px;
  margin-bottom: 32px;
  color: var(--ink-3);
}

/* State placeholders */
.search-state {
  text-align: center;
  padding: 60px 0;
}

/* Results */
.results-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.12s;
}

.result-row:hover {
  background: var(--bg-2);
}

.result-index {
  width: 22px;
  text-align: right;
  font-size: 11px;
  color: var(--ink-3);
  flex-shrink: 0;
}

.result-cover {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.result-cover--empty {
  background: var(--bg-2);
  border: 1px solid var(--rule);
}

.result-info {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-family: var(--serif-cn);
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-sub {
  margin-top: 2px;
  color: var(--ink-2);
}

.result-meta {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.platform-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  color: #fff;
  font-family: var(--serif-cn);
  letter-spacing: .02em;
}

.platform--netease {
  background: #e74c3c;
}

.platform--qqmusic {
  background: #27ae60;
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}

.load-more-btn {
  padding: 7px 28px;
  border-radius: 999px;
  border: 1px solid var(--rule);
  background: transparent;
  color: var(--ink-2);
  font-family: var(--serif-cn);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.load-more-btn:hover {
  background: var(--bg-2);
  border-color: var(--ink-3);
}

/* ── Search result enter ───────────────────────────── */
.result-enter-active {
  animation: result-in 0.35s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: calc(var(--i, 0) * 0.04s);
}

.result-leave-active {
  position: absolute;
  opacity: 0;
  transition: opacity 0.15s;
}

@keyframes result-in {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>
