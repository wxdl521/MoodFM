<template>
  <div class="player-page" :data-mood="ui.moodPreset">
    <!-- Full-bleed mood backdrop -->
    <div class="player-backdrop" />
    <!-- Semi-transparent dark overlay -->
    <div class="player-overlay" />

    <!-- Foreground content -->
    <div class="player-fg">

      <!-- ── Top bar ──────────────────────────────────────────────── -->
      <div class="player-topbar">
        <button class="player-back-btn" @click="goBack">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
          <span class="mono" style="font-size: 11px; letter-spacing: .16em">BACK</span>
        </button>

        <div class="player-station-info">
          <div class="mono" style="font-size: 10px; letter-spacing: .2em; opacity: .7">
            STATION · NOW PLAYING
          </div>
          <div style="font-family: var(--serif-en); font-style: italic; font-size: 22px; margin-top: 2px">
            {{ stationName }}
          </div>
          <div style="font-size: 13px; opacity: .85; margin-top: 1px">
            {{ radio.scene || radio.moodText || '情绪电台' }} · {{ queueCount }} 首
          </div>
        </div>

        <button class="player-more-btn" @click="ui.toggleQueueDrawer()">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <circle cx="12" cy="5"  r="1.5"/>
            <circle cx="12" cy="12" r="1.5"/>
            <circle cx="12" cy="19" r="1.5"/>
          </svg>
        </button>
      </div>

      <!-- ── Main body ─────────────────────────────────────────────── -->
      <div class="player-body">

        <!-- LEFT: MoodBlob with pulse ring -->
        <div class="player-cover-wrap">
          <div class="pulse-ring" :class="{ active: player.isPlaying, loading: isAudioLoading && !player.isPlaying }" />
          <MoodBlob
            :size="440"
            geometry="blob"
            :drift="player.isPlaying"
            class="player-blob"
          />
        </div>

        <!-- RIGHT: Track info + controls -->
        <div class="player-track">
          <div class="mono" style="font-size: 10px; letter-spacing: .2em; opacity: .7">
            TRACK {{ currentIndex }} / {{ totalTracks }} · {{ platformLabel }}
          </div>

          <!-- Title -->
          <h1 class="display player-title">
            {{ songTitle }}<em style="opacity: .5">.</em>
          </h1>
          <div style="display: flex; align-items: center; gap: 10px; margin-top: 8px;">
            <div style="font-family: var(--serif-cn); font-size: 24px; opacity: .9">{{ songArtist }}</div>
            <span v-if="isFallback"
              class="platform-fallback-badge">
              via 网易云
            </span>
          </div>

          <!-- AI "why this song" card -->
          <div class="why-card">
            <div class="mono" style="font-size: 10px; letter-spacing: .18em; opacity: .7">AI · WHY THIS SONG</div>
            <div style="margin-top: 8px; font-family: var(--serif-cn); font-size: 15px; line-height: 1.6">
              {{ whyText }}
            </div>
            <div class="meta" style="margin-top: 10px; color: rgba(255,255,255,0.7)">
              {{ songTags }}
            </div>
          </div>

          <!-- Progress bar -->
          <div
            ref="progressEl"
            class="progress-area"
            @mousedown="handleProgressDown"
            @touchstart.passive="handleProgressDown"
          >
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: `${displayProgress * 100}%` }" />
              <div
                class="progress-thumb"
                :class="{ 'progress-thumb--dragging': isDragging }"
                :style="{ left: `${displayProgress * 100}%` }"
              />
            </div>
            <div class="mono between" style="font-size: 11px; margin-top: 8px; opacity: .8">
              <span>{{ formatTime(displayCurrentTime) }}</span>
              <span>{{ formatTime(totalDuration) }}</span>
            </div>
          </div>

          <!-- Main controls -->
          <div class="ctrl-row">
            <button
              class="ctrl-btn"
              aria-label="上一首"
              :disabled="!canPrev"
              :class="{ 'ctrl-btn--disabled': !canPrev }"
              @click="handlePrev"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z"/></svg>
            </button>

            <button
              class="ctrl-btn ctrl-btn--primary"
              :aria-label="player.isPlaying ? '暂停' : '播放'"
              @click="handlePlayPause"
            >
              <!-- Pause -->
              <svg v-if="player.isPlaying" width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 19h4V5H6zm8-14v14h4V5z"/>
              </svg>
              <!-- Play -->
              <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z"/>
              </svg>
            </button>

            <button class="ctrl-btn" aria-label="下一首" @click="handleNext">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 18l8.5-6L6 6zm8.5-6 3.5 2.5V9.5z"/><rect x="16" y="6" width="2" height="12"/></svg>
            </button>

            <button class="ctrl-btn" style="margin-left: 12px" aria-label="跳过" @click="handleSkip">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="5 4 15 12 5 20"/>
                <line x1="19" y1="4" x2="19" y2="20"/>
              </svg>
            </button>
          </div>

          <!-- Secondary action chips -->
          <div class="chip-row">
            <button
              class="chip-btn"
              :class="{ 'chip-btn--active': liked }"
              @click="toggleLike"
            >
              <svg
                width="14" height="14" viewBox="0 0 24 24"
                :fill="liked ? 'var(--mood-a)' : 'none'"
                :stroke="liked ? 'var(--mood-a)' : 'currentColor'"
                stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                :style="liked ? 'transform:scale(1.15); transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);' : 'transform:scale(1); transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);'"
              >
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
              {{ liked ? '已红心' : '红心' }}
            </button>

            <button class="chip-btn" @click="handleDislike">不喜欢</button>

            <button class="chip-btn" @click="handleSwitchSource">
              切换音源 · QQ
            </button>

            <button class="chip-btn" @click="showLyrics = !showLyrics">歌词</button>

            <button class="chip-btn" @click="ui.toggleQueueDrawer()">
              队列 · {{ queueCount }}
            </button>

            <button class="chip-btn chip-btn--share" @click="handleShare">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
              </svg>
              分享
            </button>
          </div>
        </div>
      </div>

      <!-- ── Queue strip (desktop only) ─────────────────────────────── -->
      <div class="queue-strip">
        <div class="mono" style="font-size: 10px; letter-spacing: .18em; opacity: .7; white-space: nowrap; flex-shrink: 0">
          UP NEXT →
        </div>
        <template v-if="player.queue.length > 0">
          <div
            v-for="(song, i) in player.queue.slice(0, 5)"
            :key="song.id"
            class="queue-item"
          >
            <span class="mono" style="font-size: 10px; opacity: .7">{{ String(player.trackNumber + i + 1).padStart(2, '0') }}</span>
            <span style="font-family: var(--serif-cn); font-size: 13px">{{ song.title }}</span>
            <span style="font-size: 12px; opacity: .7">· {{ song.artist }}</span>
            <span class="mono" style="font-size: 10px; opacity: .6">{{ formatTime(song.duration) }}</span>
          </div>
        </template>
        <template v-else>
          <div class="queue-empty mono">
            队列已空 · 当前为最后一首
          </div>
        </template>
      </div>

    </div><!-- /player-fg -->

    <!-- ── Queue Drawer ──────────────────────────────────────────────── -->
    <Transition name="fade">
      <div
        v-if="ui.queueDrawerOpen"
        style="position:fixed;inset:0;z-index:50;background:rgba(0,0,0,0.55);display:flex;flex-direction:column;justify-content:flex-end;"
        @click="ui.toggleQueueDrawer()"
      >
        <div style="background:var(--bg);border-radius:20px 20px 0 0;padding:24px 24px 48px;max-height:65vh;overflow-y:auto;" @click.stop>
          <div class="mono" style="font-size:10px;letter-spacing:.2em;opacity:.5;text-align:center;margin-bottom:20px;">
            UP NEXT · 接下来播放 · {{ player.queue.length }} 首
          </div>
          <div v-if="player.queue.length === 0" style="text-align:center;padding:32px 0;font-family:var(--serif-cn);font-size:15px;color:var(--ink-3);">
            队列为空
          </div>
          <div
            v-for="(song, i) in player.queue.slice(0, 12)"
            :key="song.id ?? i"
            class="row"
            style="gap:14px;padding:12px 0;border-bottom:1px solid var(--rule);align-items:center;"
          >
            <span class="mono" style="font-size:11px;color:var(--ink-3);width:24px;flex-shrink:0;">{{ String(i + 1).padStart(2,'0') }}</span>
            <div style="flex:1;min-width:0;">
              <div style="font-family:var(--serif-en);font-style:italic;font-size:17px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ song.title }}</div>
              <div class="meta" style="margin-top:2px;">{{ song.artist }}</div>
            </div>
            <span class="mono" style="font-size:11px;color:var(--ink-3);flex-shrink:0;">{{ formatTime(song.duration) }}</span>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ── Auto-blacklist Toast ─────────────────────────────────────── -->
    <Transition name="fade">
      <div
        v-if="blacklistToast"
        style="position:fixed;bottom:100px;left:50%;transform:translateX(-50%);z-index:60;
               background:var(--ink);color:var(--bg);border-radius:24px;
               padding:14px 20px;display:flex;align-items:center;gap:14px;
               max-width:360px;width:calc(100% - 40px);box-shadow:0 8px 32px rgba(0,0,0,.3);"
      >
        <div style="flex:1;font-family:var(--serif-cn);font-size:14px;line-height:1.5;">
          已自动拉黑 {{ blacklistToast }}（连续跳过 3 次）
        </div>
      </div>
    </Transition>

    <!-- ── Info Toast (skip / share fallback) ────────────────────────── -->
    <Transition name="fade">
      <div
        v-if="infoToast"
        style="position:fixed;bottom:100px;left:50%;transform:translateX(-50%);z-index:60;
               background:var(--ink);color:var(--bg);border-radius:24px;
               padding:14px 20px;display:flex;align-items:center;gap:14px;
               max-width:360px;width:calc(100% - 40px);box-shadow:0 8px 32px rgba(0,0,0,.3);"
      >
        <div style="flex:1;font-family:var(--serif-cn);font-size:14px;line-height:1.5;">
          {{ infoToast }}
        </div>
      </div>
    </Transition>

    <!-- Lyrics overlay -->
    <Transition name="fade">
      <div v-if="showLyrics" class="lyrics-overlay" @click="showLyrics = false">
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

          <div v-else ref="lyricsScrollEl" class="lyrics-lines">
            <p
              v-for="(line, i) in lyricsLines"
              :key="i"
              :class="['lyric-line', { 'lyric-line--active': i === activeLyricIdx }]"
            >{{ line.text }}</p>
          </div>

          <button class="chip-btn" style="margin-top: 24px" @click="showLyrics = false">关闭</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useRadioStore } from '@/stores/radio'
import { useUiStore } from '@/stores/ui'
import { useAudioPlayer } from '@/composables/useAudioPlayer'
import { useWebSocket } from '@/composables/useWebSocket'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { blacklistApi } from '@/api/blacklist'
import { songApi, type LyricLine } from '@/api/song'
import { radioApi } from '@/api/radio'
import { playlistApi } from '@/api/playlist'
import { logger } from '@/utils/logger'

const router = useRouter()
const player = usePlayerStore()
const radio  = useRadioStore()
const ui     = useUiStore()
const audio  = useAudioPlayer()
const ws     = useWebSocket()

// ── Local state ─────────────────────────────────────────────────────────────
const liked             = ref(false)
const showLyrics        = ref(false)
const blacklistToast    = ref<string | null>(null)
const infoToast         = ref<string | null>(null)
const lastSkippedArtist = ref<string | null>(null)
const consecutiveSkips  = ref(0)
let infoToastTimer: ReturnType<typeof setTimeout> | null = null

// ── Progress drag state ─────────────────────────────────────────────────────
const progressEl = ref<HTMLElement | null>(null)
const isDragging = ref(false)
const dragRatio  = ref(0)

function showInfoToast(msg: string, durationMs = 2500) {
  infoToast.value = msg
  if (infoToastTimer) clearTimeout(infoToastTimer)
  infoToastTimer = setTimeout(() => { infoToast.value = null }, durationMs)
}

// ── Lyrics state ─────────────────────────────────────────────────────────────
const lyricsLines    = ref<LyricLine[]>([])
const lyricsLoading  = ref(false)
const lyricsScrollEl = ref<HTMLElement | null>(null)
// Tracks the songId currently loaded/loading in lyricsLines. Used for dedup
// so切歌 watcher 不会对同一首歌重复发请求，且能丢弃过期 fetch 结果。
const lyricsLoadedSongId = ref<string | number | null>(null)

// ── Volume feedback tracking (Feature: volume_up implicit feedback) ──
let lastVolumeSentTs = 0
let prevVolume = player.volume

watch(() => player.volume, (newVol) => {
  const now = Date.now()
  // Only send when volume increased by > 10%, debounce 30s
  if (newVol > prevVolume && (newVol - prevVolume) > 0.1 && now - lastVolumeSentTs > 30_000) {
    lastVolumeSentTs = now
    const song = player.currentSong
    const sid = player.sessionId
    if (song && sid) {
      // silent: feedback 上报失败不影响播放
      radioApi.sendFeedback('volume_up', {
        sessionId: Number(sid),
        songId: Number(song.id),
      }).catch(err => { logger.warn('player:feedback-volume', err) })
    }
  }
  prevVolume = newVol
})

// ── Computed from stores ─────────────────────────────────────────────────────
const stationName = computed(() =>
  radio.session?.moodText || radio.moodText || 'Late-night Reset',
)

const songTitle  = computed(() => player.currentSong?.title  ?? 'weightless')
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

const queueCount   = computed(() => player.queue.length)
const totalTracks  = computed(() => player.queue.length + 1)
const currentIndex = computed(() => String(player.trackNumber).padStart(2, '0'))

const totalDuration = computed(() =>
  audio.duration.value > 0 ? audio.duration.value : player.duration,
)
const currentTime = computed(() =>
  audio.currentTime.value > 0 ? audio.currentTime.value : player.progress * totalDuration.value,
)

// While dragging, render the dragRatio instead of the actual playback progress
const displayProgress = computed(() =>
  isDragging.value ? dragRatio.value : player.progress,
)
const displayCurrentTime = computed(() =>
  isDragging.value ? dragRatio.value * totalDuration.value : currentTime.value,
)

const canPrev = computed(() => player.history.length > 0)

// Loading = has a track URL but Howl hasn't reached ready / loaded a different URL
const isAudioLoading = computed(() => {
  const url = player.currentSong?.audioUrl
  if (!url) return false
  return audio.loadedUrl.value !== url || !audio.isReady.value
})

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

// ── Lyrics logic ─────────────────────────────────────────────────────────────
const activeLyricIdx = computed(() => {
  if (!lyricsLines.value.length) return -1
  const ms = currentTime.value * 1000
  let idx = 0
  for (let i = 0; i < lyricsLines.value.length; i++) {
    if (lyricsLines.value[i].time <= ms) idx = i
    else break
  }
  return idx
})

async function loadLyrics() {
  const song = player.currentSong
  if (!song?.id) return
  // Dedup: 同一首歌已加载/正在加载就跳过，避免切歌 watcher 与「打开面板」兜底重复请求
  if (lyricsLoadedSongId.value === song.id) return
  const targetId = song.id
  lyricsLoadedSongId.value = targetId
  lyricsLoading.value = true
  try {
    const lines = await songApi.lyrics(targetId)
    // Stale guard: 请求返回时用户可能已切到下一首，避免覆盖新歌词
    if (lyricsLoadedSongId.value !== targetId) return
    lyricsLines.value = Array.isArray(lines) ? lines.filter(l => l.text?.trim()) : []
  } catch (err) {
    // silent: 预取/加载失败时面板显示「暂无歌词」就够了，不弹 toast
    logger.warn('player:lyrics-prefetch', err)
    if (lyricsLoadedSongId.value === targetId) {
      lyricsLines.value = []
      // 失败时把状态回退，方便用户手动打开面板时仍可触发重试 fallback
      lyricsLoadedSongId.value = null
    }
  } finally {
    if (lyricsLoadedSongId.value === targetId || lyricsLoadedSongId.value === null) {
      lyricsLoading.value = false
    }
  }
}

// Fallback: 罕见 race（预取失败/被丢弃，用户已点开面板）— 重新尝试一次
watch(showLyrics, (open) => {
  if (open && !lyricsLines.value.length && !lyricsLoading.value) loadLyrics()
})

watch(() => player.currentSong?.id, async (songId) => {
  // 切歌：清空旧歌词、重置 dedup token，然后总是预取（不再判断 showLyrics）
  lyricsLines.value = []
  lyricsLoadedSongId.value = null
  liked.value = false
  if (songId) {
    // 总是预取：歌词只有几 KB，切歌即开始 fetch，打开面板时瞬时显示
    loadLyrics()
    try {
      const res = await playlistApi.isLiked(songId)
      liked.value = res.liked
    } catch (err) {
      // silent: liked 状态查询失败时默认 false，不阻塞用户播放体验
      logger.warn('player:is-liked', err)
    }
  }
}, { immediate: true })

watch(activeLyricIdx, async (idx) => {
  if (!showLyrics.value || idx < 0 || !lyricsScrollEl.value) return
  await nextTick()
  const el = lyricsScrollEl.value.children[idx] as HTMLElement | undefined
  el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
})

// ── Playback handlers ────────────────────────────────────────────────────────
function handlePlayPause() {
  if (player.isPlaying) {
    audio.pause()
  } else {
    if (player.currentSong?.audioUrl) {
      if (audio.isReady.value) {
        audio.play()
      } else {
        audio.load(player.currentSong.audioUrl).then(() => audio.play()).catch(err => {
          logger.warn('player:audio-load-playpause', err)
          showInfoToast('该歌曲无法播放')
        })
      }
    } else if (player.queue.length > 0) {
      _advanceQueue()
    } else {
      player.togglePlay()
    }
  }
}

// Shared: advance queue + load audio. Does NOT send feedback (callers handle that).
// Skips songs without audioUrl automatically.
function _advanceQueue() {
  player.next()
  const song = player.currentSong
  if (song?.audioUrl) {
    audio.load(song.audioUrl)
      .then(() => audio.play())
      .catch(err => {
        logger.warn('player:audio-load-advance', err)
        const failedTitle = song?.title
        if (failedTitle) showInfoToast(`「${failedTitle}」无法播放，已跳过`)
        else showInfoToast('该歌曲无法播放，已跳过')
        if (player.queue.length > 0) _advanceQueue()
        else { audio.stop(); player.setPlaying(false) }
      })
  } else if (player.queue.length > 0) {
    // No audio URL — skip to next song instead of showing fake progress
    const skippedTitle = song?.title
    if (skippedTitle) showInfoToast(`「${skippedTitle}」无法播放，已跳过`)
    else showInfoToast('该歌曲无法播放，已跳过')
    _advanceQueue()
  } else {
    // No audio URL and queue empty — stop, no fake progress
    if (song) showInfoToast('该歌曲无法播放，队列已空')
    audio.stop()
    player.setPlaying(false)
  }
}

function _sendSkipFeedback(song: typeof player.currentSong, sid: string | null, playedSecs: number, totalSecs: number) {
  if (!song?.id || !sid) return
  // silent: feedback 上报失败不影响播放
  radioApi.sendFeedback('skip', {
    sessionId: Number(sid),
    songId: Number(song.id),
    playedSeconds: playedSecs,
    totalSeconds: totalSecs,
    platform: song.platform || 'netease',
  }).catch(err => { logger.warn('player:feedback-skip', err) })
}

function handleNext() {
  const prevSong = player.currentSong
  const sid = player.sessionId
  const playedSecs = Math.round(audio.currentTime.value) || 0
  const totalSecs = Math.round(audio.duration.value || player.duration) || 0
  _advanceQueue()
  _sendSkipFeedback(prevSong, sid, playedSecs, totalSecs)
}

function handlePrev() {
  player.prev()
  const song = player.currentSong
  if (song?.audioUrl) {
    audio.load(song.audioUrl).then(() => audio.play()).catch(err => {
      logger.warn('player:audio-load-prev', err)
      const failedTitle = song?.title
      showInfoToast(failedTitle ? `「${failedTitle}」无法播放` : '该歌曲无法播放')
    })
  }
}

function handleSkip() {
  const skippedSong = player.currentSong
  const sid = player.sessionId
  const playedSecs = Math.round(audio.currentTime.value) || 0
  const totalSecs = Math.round(audio.duration.value || player.duration) || 0
  const skippedArtist = skippedSong?.artist

  _advanceQueue()
  _sendSkipFeedback(skippedSong, sid, playedSecs, totalSecs)

  if (!skippedArtist) return

  if (skippedArtist === lastSkippedArtist.value) {
    consecutiveSkips.value++
  } else {
    lastSkippedArtist.value = skippedArtist
    consecutiveSkips.value = 1
  }

  if (consecutiveSkips.value >= 3) {
    blacklistApi.add({ type: 'artist', value: skippedArtist, label: skippedArtist }).catch(err => {
      // 用户主动操作：连续跳过 3 次自动拉黑失败需提示
      logger.warn('player:auto-blacklist-add', err)
      showInfoToast('自动拉黑失败，请稍后重试')
    })
    blacklistToast.value = skippedArtist
    consecutiveSkips.value = 0
    lastSkippedArtist.value = null
    setTimeout(() => { blacklistToast.value = null }, 3000)
  }
}

async function toggleLike() {
  const song = player.currentSong
  if (!song?.id) return
  try {
    const res = await playlistApi.toggleLike(song.id)
    liked.value = res.liked
  } catch (err) {
    // 用户主动操作：红心切换失败需要提示
    logger.warn('player:toggle-like', err)
    showInfoToast('操作失败，请稍后重试')
  }
}

async function handleDislike() {
  const song = player.currentSong
  const sid = player.sessionId
  if (song && sid) {
    try {
      await radioApi.sendFeedback('dislike', { songId: Number(song.id), sessionId: Number(sid) })
    } catch (err) {
      // silent: dislike 是用户操作，但即使 feedback 失败也仍然要 handleNext
      // 体验上跳过下一首已经是用户期望，不再额外弹 toast 干扰
      logger.warn('player:feedback-dislike', err)
    }
  }
  handleNext()
}

function handleSwitchSource() {
  // TODO: integrate platform switching
}

async function handleShare() {
  const song = player.currentSong
  if (!song) return
  const title = `MoodFM: ${song.title}`
  const text = `正在收听 ${song.artist} - ${song.title}`
  const shareUrl = typeof window !== 'undefined' ? window.location.href : ''

  // Prefer native Web Share API when available
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text, url: shareUrl })
      return
    } catch (err: unknown) {
      // User-cancelled (AbortError) — do nothing, no fallback needed
      if (err instanceof Error && err.name === 'AbortError') return
      // Other share error — fall through to clipboard fallback
    }
  }

  // Fallback: copy link to clipboard
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(shareUrl)
      showInfoToast('链接已复制')
    } else {
      // Legacy fallback: temporary textarea + execCommand
      const ta = document.createElement('textarea')
      ta.value = shareUrl
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(ta)
      showInfoToast(ok ? '链接已复制' : '复制失败，请手动复制')
    }
  } catch (err) {
    // 用户主动分享：复制失败已有 toast，再加 logger 方便调试
    logger.warn('player:share-clipboard', err)
    showInfoToast('复制失败，请手动复制')
  }
}

// ── Progress bar drag (mouse + touch) ────────────────────────────────────────
function getEventClientX(e: MouseEvent | TouchEvent): number {
  if ('touches' in e) {
    return e.touches[0]?.clientX ?? (e as TouchEvent).changedTouches[0]?.clientX ?? 0
  }
  return (e as MouseEvent).clientX
}

function computeRatio(clientX: number): number {
  const el = progressEl.value
  if (!el) return 0
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0) return 0
  return Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
}

function handleProgressDown(e: MouseEvent | TouchEvent) {
  if (!progressEl.value) return
  // Don't preventDefault on touchstart (passive listener) — would break scroll
  // For mouse: prevent text selection during drag
  if ('clientX' in e) e.preventDefault()
  dragRatio.value = computeRatio(getEventClientX(e))
  isDragging.value = true
  window.addEventListener('mousemove', handleProgressMove)
  window.addEventListener('mouseup', handleProgressUp)
  window.addEventListener('touchmove', handleProgressMove, { passive: false })
  window.addEventListener('touchend', handleProgressUp)
  window.addEventListener('touchcancel', handleProgressUp)
}

function handleProgressMove(e: MouseEvent | TouchEvent) {
  if (!isDragging.value) return
  // Only preventDefault when it's actually possible (cancelable touchmove or mousemove)
  if (e.cancelable) e.preventDefault()
  dragRatio.value = computeRatio(getEventClientX(e))
}

function handleProgressUp() {
  if (!isDragging.value) {
    cleanupProgressListeners()
    return
  }
  const ratio = dragRatio.value
  isDragging.value = false
  cleanupProgressListeners()
  if (totalDuration.value > 0 && player.currentSong?.audioUrl) {
    audio.seek(ratio * totalDuration.value)
  } else {
    player.setProgress(ratio)
  }
}

function cleanupProgressListeners() {
  window.removeEventListener('mousemove', handleProgressMove)
  window.removeEventListener('mouseup', handleProgressUp)
  window.removeEventListener('touchmove', handleProgressMove)
  window.removeEventListener('touchend', handleProgressUp)
  window.removeEventListener('touchcancel', handleProgressUp)
}

// ── Navigation ───────────────────────────────────────────────────────────────
function goBack() {
  router.push('/home')
}

// ── Format helper ────────────────────────────────────────────────────────────
function formatTime(secs: number): string {
  if (!secs || secs < 0) return '0:00'
  const m = Math.floor(secs / 60)
  const s = Math.floor(secs % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

// ── Lifecycle ────────────────────────────────────────────────────────────────
onMounted(() => {
  if (!player.currentSong) {
    router.replace('/home')
    return
  }
  if (player.sessionId) ws.connect(player.sessionId)
  if (player.currentSong.audioUrl) {
    // Load if: no Howl loaded yet, OR the loaded URL differs from the current song
    // (new session). Skip reload only when the same URL is already loaded (back-nav to a paused song).
    if (audio.loadedUrl.value !== player.currentSong.audioUrl) {
      audio.load(player.currentSong.audioUrl)
        .then(() => audio.play())
        .catch(err => {
          logger.warn('player:audio-load-mount', err)
          // URL failed (CORS / expired / no VIP) — skip to next song
          const failedTitle = player.currentSong?.title
          if (player.queue.length > 0) {
            if (failedTitle) showInfoToast(`「${failedTitle}」无法播放，已跳过`)
            else showInfoToast('该歌曲无法播放，已跳过')
            handleNext()
          } else {
            // Queue empty — stop instead of showing fake progress
            if (failedTitle) showInfoToast(`「${failedTitle}」无法播放`)
            else showInfoToast('该歌曲无法播放')
            audio.stop()
            player.setPlaying(false)
          }
        })
    }
  } else {
    // No real audio URL — try next song with a URL
    if (player.queue.length > 0) {
      _advanceQueue()
    } else {
      player.setPlaying(false)
    }
  }
})

onUnmounted(() => {
  if (infoToastTimer) {
    clearTimeout(infoToastTimer)
    infoToastTimer = null
  }
  cleanupProgressListeners()
})
</script>

<style scoped>
/* ── Base ─────────────────────────────────────────────────────────── */
.player-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
}

/* ── Backdrop ─────────────────────────────────────────────────────── */
.player-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 25%, var(--mood-a) 0%, transparent 55%),
    radial-gradient(circle at 80% 20%, var(--mood-b) 0%, transparent 55%),
    radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
    radial-gradient(circle at 10% 80%, var(--mood-d) 0%, transparent 65%);
  background-color: var(--mood-d);
  filter: saturate(1.1);
}

.player-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.18);
}

/* ── Foreground ───────────────────────────────────────────────────── */
.player-fg {
  position: relative;
  z-index: 2;
  color: #fff;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ── Top bar ──────────────────────────────────────────────────────── */
.player-topbar {
  padding: 24px 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .player-topbar { padding: 18px 22px; }
}

.player-back-btn,
.player-more-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  color: #fff;
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  transition: background 0.15s;
}

.player-back-btn:hover,
.player-more-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.player-station-info {
  text-align: center;
  flex: 1;
  padding: 0 16px;
}

/* ── Body ─────────────────────────────────────────────────────────── */
.player-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 48px;
  padding: 48px 56px;
  align-items: center;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .player-body {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 24px 22px;
  }
}

/* ── Cover / Blob ─────────────────────────────────────────────────── */
.player-cover-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
}

.pulse-ring {
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
  pointer-events: none;
}

.pulse-ring.active {
  animation: pulse-ring 4s ease-out infinite;
}

.pulse-ring.loading {
  animation: pulse-ring-loading 1.6s ease-in-out infinite;
}

@keyframes pulse-ring-loading {
  0%, 100% { transform: scale(1.00); opacity: 0.45; }
  50%      { transform: scale(1.04); opacity: 0.75; }
}

@media (max-width: 900px) {
  .pulse-ring {
    width: 320px;
    height: 320px;
  }
}

.player-blob {
  position: relative;
  z-index: 1;
}

/* ── Track info ───────────────────────────────────────────────────── */
.player-track {
  display: flex;
  flex-direction: column;
}

.player-title {
  font-size: 96px;
  margin: 10px 0 0;
  line-height: 0.9;
  color: #fff;
}

@media (max-width: 900px) {
  .player-title { font-size: 52px; }
}

/* ── AI card ──────────────────────────────────────────────────────── */
.why-card {
  margin-top: 24px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.14);
}

/* ── Progress ─────────────────────────────────────────────────────── */
.progress-area {
  margin-top: 28px;
  cursor: pointer;
  /* Extend the touch target vertically without changing visual height */
  padding: 10px 0;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}

.progress-track {
  height: 2px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 1px;
  position: relative;
}

.progress-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: #fff;
  border-radius: 1px;
  transition: width 0.2s linear;
}

.progress-thumb {
  position: absolute;
  top: -3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  transform: translateX(-50%);
  transition: left 0.2s linear, width 0.15s ease, height 0.15s ease, top 0.15s ease;
}

.progress-thumb--dragging {
  width: 14px;
  height: 14px;
  top: -6px;
  transition: width 0.15s ease, height 0.15s ease, top 0.15s ease;
}

/* While dragging, remove the smoothing transition so motion tracks the pointer */
.progress-area:has(.progress-thumb--dragging) .progress-fill,
.progress-area:has(.progress-thumb--dragging) .progress-thumb {
  transition: width 0.15s ease, height 0.15s ease, top 0.15s ease;
}

/* ── Controls ─────────────────────────────────────────────────────── */
.ctrl-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .ctrl-row { justify-content: center; }
}

.ctrl-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.22);
}

.ctrl-btn:active {
  transform: scale(0.88);
  transition-duration: 0.07s;
}

.ctrl-btn--disabled,
.ctrl-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.ctrl-btn--disabled:hover,
.ctrl-btn:disabled:hover {
  background: rgba(255, 255, 255, 0.12);
}

.ctrl-btn--disabled:active,
.ctrl-btn:disabled:active {
  transform: none;
}

.ctrl-btn--primary {
  width: 64px;
  height: 64px;
  background: #fff;
  color: var(--ink);
  border-color: transparent;
  transition: background 0.15s, transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.ctrl-btn--primary:hover {
  background: rgba(255, 255, 255, 0.9);
  transform: scale(1.06);
}

.ctrl-btn--primary:active {
  transform: scale(0.91);
  transition-duration: 0.07s;
}

/* ── Chip row ─────────────────────────────────────────────────────── */
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 22px;
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

.chip-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.chip-btn:active {
  transform: scale(0.93);
  transition-duration: 0.07s;
}

.chip-btn--active {
  background: rgba(255, 255, 255, 0.12);
}

/* ── Queue strip ──────────────────────────────────────────────────── */
.queue-strip {
  display: none;
  padding: 18px 56px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  gap: 16px;
  align-items: center;
  overflow-x: auto;
  flex-shrink: 0;
}

@media (min-width: 900px) {
  .queue-strip { display: flex; }
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}

.queue-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.queue-empty {
  font-size: 10px;
  letter-spacing: .18em;
  opacity: 0.55;
  padding: 8px 0;
  white-space: nowrap;
}

/* ── Lyrics overlay ───────────────────────────────────────────────── */
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

/* ── Platform fallback badge ──────────────────────────────────────── */
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

/* ── Transitions ──────────────────────────────────────────────────── */
.fade-enter-active,
.fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from,
.fade-leave-to     { opacity: 0; }

/* pulse-ring animation defined in global CSS; duplicate here as fallback */
@keyframes pulse-ring {
  0%   { transform: scale(0.96); opacity: 0.7; }
  100% { transform: scale(1.18); opacity: 0; }
}

/* ── Player content enter ──────────────────────────────────────────── */
@keyframes player-rise {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: translateY(0); }
}

.player-fg {
  animation: player-rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.05s both;
}

.player-title {
  animation: player-rise 0.55s cubic-bezier(0.16, 1, 0.3, 1) 0.14s both;
}

.why-card {
  animation: player-rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.24s both;
}

.ctrl-row {
  animation: player-rise 0.45s cubic-bezier(0.16, 1, 0.3, 1) 0.34s both;
}

.chip-row {
  animation: player-rise 0.4s cubic-bezier(0.16, 1, 0.3, 1) 0.42s both;
}

/* ── Like pulse ─────────────────────────────────────────────────────── */
@keyframes heart-pulse {
  0%   { transform: scale(1); }
  40%  { transform: scale(1.35); }
  70%  { transform: scale(0.9); }
  100% { transform: scale(1); }
}
</style>
