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
          <div class="pulse-ring" :class="{ active: player.isPlaying }" />
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
            TRACK {{ currentIndex }} / {{ totalTracks }} · NETEASE CLOUD
          </div>

          <!-- Title -->
          <h1 class="display player-title">
            {{ songTitle }}<em style="opacity: .5">.</em>
          </h1>
          <div style="font-family: var(--serif-cn); font-size: 24px; margin-top: 8px; opacity: .9">
            {{ songArtist }}
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
          <div class="progress-area" @click="handleProgressClick">
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: `${player.progress * 100}%` }" />
              <div
                class="progress-thumb"
                :style="{ left: `${player.progress * 100}%` }"
              />
            </div>
            <div class="mono between" style="font-size: 11px; margin-top: 8px; opacity: .8">
              <span>{{ formatTime(currentTime) }}</span>
              <span>{{ formatTime(totalDuration) }}</span>
            </div>
          </div>

          <!-- Main controls -->
          <div class="ctrl-row">
            <button class="ctrl-btn" aria-label="上一首" @click="handlePrev">
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
            <span class="mono" style="font-size: 10px; opacity: .7">0{{ i + 4 }}</span>
            <span style="font-family: var(--serif-cn); font-size: 13px">{{ song.title }}</span>
            <span style="font-size: 12px; opacity: .7">· {{ song.artist }}</span>
            <span class="mono" style="font-size: 10px; opacity: .6">{{ formatTime(song.duration) }}</span>
          </div>
        </template>
        <template v-else>
          <div
            v-for="item in placeholderQueue"
            :key="item.n"
            class="queue-item"
          >
            <span class="mono" style="font-size: 10px; opacity: .7">{{ item.idx }}</span>
            <span style="font-family: var(--serif-cn); font-size: 13px">{{ item.n }}</span>
            <span style="font-size: 12px; opacity: .7">· {{ item.a }}</span>
            <span class="mono" style="font-size: 10px; opacity: .6">{{ item.t }}</span>
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

    <!-- ── Feedback Banner（连续跳过提示）────────────────────────────── -->
    <Transition name="fade">
      <div
        v-if="bannerArtist"
        style="position:fixed;bottom:100px;left:50%;transform:translateX(-50%);z-index:60;
               background:var(--ink);color:var(--bg);border-radius:24px;
               padding:14px 20px;display:flex;align-items:center;gap:14px;
               max-width:360px;width:calc(100% - 40px);box-shadow:0 8px 32px rgba(0,0,0,.3);"
      >
        <div style="flex:1;font-family:var(--serif-cn);font-size:14px;line-height:1.5;">
          连续跳过了 3 首，要屏蔽「{{ bannerArtist }}」吗？
        </div>
        <button
          style="background:var(--bg);color:var(--ink);border:none;border-radius:16px;padding:7px 14px;cursor:pointer;font-size:13px;flex-shrink:0;font-family:var(--serif-cn);"
          @click="handleAddBlacklist"
        >屏蔽</button>
        <button
          style="background:transparent;color:rgba(255,255,255,.6);border:none;cursor:pointer;font-size:18px;flex-shrink:0;line-height:1;"
          @click="bannerArtist = null"
        >✕</button>
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
import MoodBlob from '@/components/common/MoodBlob.vue'
import { blacklistApi } from '@/api/blacklist'
import { songApi, type LyricLine } from '@/api/song'

const router = useRouter()
const player = usePlayerStore()
const radio  = useRadioStore()
const ui     = useUiStore()
const audio  = useAudioPlayer()

// ── Local state ─────────────────────────────────────────────────────────────
const liked         = ref(false)
const showLyrics    = ref(false)
const bannerArtist  = ref<string | null>(null)

// ── Lyrics state ─────────────────────────────────────────────────────────────
const lyricsLines    = ref<LyricLine[]>([])
const lyricsLoading  = ref(false)
const lyricsScrollEl = ref<HTMLElement | null>(null)

// Progress simulation when no real audio
let simTimer: ReturnType<typeof setInterval> | null = null

// ── Computed from stores ─────────────────────────────────────────────────────
const stationName = computed(() =>
  radio.session?.moodText || radio.moodText || 'Late-night Reset',
)

const songTitle  = computed(() => player.currentSong?.title  ?? 'weightless')
const songArtist = computed(() => player.currentSong?.artist ?? 'Marconi Union')

const queueCount   = computed(() => player.queue.length)
const totalTracks  = computed(() => player.queue.length + 1)
const currentIndex = computed(() => '01') // simplified; would need index tracking

const totalDuration = computed(() =>
  audio.duration.value > 0 ? audio.duration.value : player.duration,
)
const currentTime = computed(() =>
  audio.currentTime.value > 0 ? audio.currentTime.value : player.progress * totalDuration.value,
)

const whyText = computed(() => {
  const mood = radio.moodText
  if (!mood) return '这首曲子拥有 60 BPM 的稳定节奏，适合当下的心境，让神经系统放慢下来。'
  return `你的心情是「${mood}」——所以选了这首，让感受先被接住。`
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
  lyricsLoading.value = true
  try {
    const lines = await songApi.lyrics(song.id)
    lyricsLines.value = Array.isArray(lines) ? lines.filter(l => l.text?.trim()) : []
  } catch {
    lyricsLines.value = []
  } finally {
    lyricsLoading.value = false
  }
}

watch(showLyrics, (open) => {
  if (open && !lyricsLines.value.length) loadLyrics()
})

watch(() => player.currentSong?.id, () => {
  lyricsLines.value = []
})

watch(activeLyricIdx, async (idx) => {
  if (!showLyrics.value || idx < 0 || !lyricsScrollEl.value) return
  await nextTick()
  const el = lyricsScrollEl.value.children[idx] as HTMLElement | undefined
  el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
})

// ── Queue placeholder (desktop strip) ───────────────────────────────────────
const placeholderQueue = [
  { idx: '04', n: 'spiegel im spiegel', a: 'Arvo Pärt',  t: '9:24' },
  { idx: '05', n: 'an ending (ascent)',  a: 'Brian Eno',  t: '4:18' },
  { idx: '06', n: 'avril 14th',          a: 'Aphex Twin', t: '2:05' },
  { idx: '07', n: '横超',                a: '青葉市子',   t: '4:12' },
  { idx: '08', n: 'cirrus',              a: 'Bonobo',     t: '5:48' },
]

// ── Playback handlers ────────────────────────────────────────────────────────
function handlePlayPause() {
  if (player.isPlaying) {
    audio.pause()
  } else {
    // Try to resume; fall back to simulated progress
    if (player.currentSong?.audioUrl) {
      audio.play()
    } else {
      player.togglePlay()
      startSimProgress()
    }
  }
}

function handleNext() {
  player.next()
  const song = player.currentSong
  if (song?.audioUrl) {
    audio.load(song.audioUrl).then(() => audio.play())
  } else {
    player.setProgress(0)
  }
}

function handlePrev() {
  player.prev()
  const song = player.currentSong
  if (song?.audioUrl) {
    audio.load(song.audioUrl).then(() => audio.play())
  } else {
    player.setProgress(0)
  }
}

function handleSkip() {
  player.incrementSkipStreak()
  if (player.skipStreak >= 3 && player.currentSong?.artist) {
    bannerArtist.value = player.currentSong.artist
    player.resetSkipStreak()
  }
  handleNext()
}

async function handleAddBlacklist() {
  if (!bannerArtist.value) return
  try {
    await blacklistApi.add({ type: 'artist', value: bannerArtist.value, label: bannerArtist.value })
  } catch {
    // 静默失败，不影响体验
  }
  bannerArtist.value = null
}

function toggleLike() {
  liked.value = !liked.value
}

function handleDislike() {
  handleNext()
}

function handleSwitchSource() {
  // TODO: integrate platform switching
}

function handleShare() {
  if (navigator.share && player.currentSong) {
    navigator.share({
      title: `MoodFM: ${player.currentSong.title}`,
      text: `正在收听 ${player.currentSong.artist} - ${player.currentSong.title}`,
    }).catch(() => undefined)
  }
}

// ── Progress bar click ───────────────────────────────────────────────────────
function handleProgressClick(e: MouseEvent) {
  const el = e.currentTarget as HTMLElement
  const rect = el.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  if (totalDuration.value > 0 && player.currentSong?.audioUrl) {
    audio.seek(ratio * totalDuration.value)
  } else {
    player.setProgress(ratio)
  }
}

// ── Simulated progress (no real audio) ──────────────────────────────────────
function startSimProgress() {
  stopSimProgress()
  if (!player.currentSong?.audioUrl && player.isPlaying) {
    simTimer = setInterval(() => {
      const next = (player.progress + 0.001) % 1
      player.setProgress(next)
    }, 200)
  }
}

function stopSimProgress() {
  if (simTimer !== null) {
    clearInterval(simTimer)
    simTimer = null
  }
}

watch(() => player.isPlaying, (val) => {
  if (val && !player.currentSong?.audioUrl) startSimProgress()
  else stopSimProgress()
})

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
    // No song loaded — redirect back to home per spec
    router.replace('/home')
    return
  }
  if (player.currentSong.audioUrl && !player.isPlaying) {
    audio.load(player.currentSong.audioUrl).then(() => audio.play())
  } else if (!player.currentSong.audioUrl) {
    // No real audio URL (dev/demo mode) — simulate progress
    if (!player.isPlaying) player.togglePlay()
    startSimProgress()
  }
})

onUnmounted(() => {
  stopSimProgress()
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
  transition: left 0.2s linear;
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
  transition: background 0.15s;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.22);
}

.ctrl-btn--primary {
  width: 64px;
  height: 64px;
  background: #fff;
  color: var(--ink);
  border-color: transparent;
}

.ctrl-btn--primary:hover {
  background: rgba(255, 255, 255, 0.9);
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
  transition: background 0.15s;
}

.chip-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.chip-btn--active {
  background: rgba(255, 255, 255, 0.12);
}

.chip-btn--share {
  display: none;
}

@media (min-width: 900px) {
  .chip-btn--share { display: inline-flex; }
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
</style>
