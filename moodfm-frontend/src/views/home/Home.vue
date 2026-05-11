<template>
  <div class="home-page" :data-mood="currentMoodPreset">
    <!-- Background mood blob decoration -->
    <div
      class="mood-blob drift"
      style="width: 760px; height: 760px; right: -220px; top: -260px; opacity: 0.4; z-index: 0;"
    />

    <!-- Top nav -->
    <NavBar />

    <!-- Main two-column content -->
    <div class="home-content">
      <!-- LEFT — Input area -->
      <div class="home-left">
        <div class="meta">{{ greetEnUpper }} · {{ todayLabel }}</div>
        <h1 class="display home-headline">
          {{ greetEn }}<em style="color: var(--ink-3)">,</em>
        </h1>
        <div class="display-cn home-subheadline">
          {{ greetCn }}，{{ userName }}。<span style="color: var(--ink-3)">今晚想听点什么？</span>
        </div>

        <!-- Method A: text input -->
        <div class="home-section">
          <div class="meta" style="margin-bottom: 10px">方式 A · IN YOUR OWN WORDS</div>
          <div class="textarea-wrap">
            <textarea
              v-model="moodInput"
              class="field"
              placeholder="此刻你在哪里、心里是什么颜色？比如：加班到很晚，需要一点不打扰我的电流声…"
              rows="4"
            />
            <button
              class="btn btn-inline"
              :disabled="radio.isLoading"
              @click="handleStartRadio"
            >
              {{ radio.isLoading ? '调台中…' : '调台' }}
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </button>
          </div>
        </div>

        <!-- Method B: preset scenes -->
        <div class="home-section">
          <div class="meta" style="margin-bottom: 10px">方式 B · PRESET SCENES</div>
          <div class="scene-tags">
            <button
              v-for="s in scenes"
              :key="s"
              class="btn-pill"
              :class="{ active: selectedScene === s }"
              @click="handleSceneSelect(s)"
            >
              {{ s }}
            </button>
          </div>
        </div>

        <!-- Method C: Just play -->
        <div class="just-play-card">
          <div>
            <div class="just-play-title" style="font-family: var(--serif-en); font-style: italic">Just play.</div>
            <div style="font-family: var(--serif-cn); font-size: 14px; color: var(--ink-2); margin-top: 2px">什么都别问 · 直接给我一个电台</div>
          </div>
          <button class="btn" :disabled="radio.isLoading" @click="handleJustPlay">
            启动
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
          </button>
        </div>

        <!-- Session duration selector -->
        <div class="home-section">
          <div class="meta" style="margin-bottom: 10px">电台时长 · SESSION DURATION</div>
          <div class="duration-ctrl">
            <button
              v-for="opt in durationOptions"
              :key="opt.value"
              class="duration-opt"
              :class="{ 'duration-opt--active': sessionDuration === opt.value }"
              @click="sessionDuration = opt.value"
            >{{ opt.label }}</button>
          </div>
        </div>
      </div>

      <!-- RIGHT — MoodWheel + Resume + Recommendation -->
      <div class="home-right">
        <!-- Mood compass (SVG-based) -->
        <div class="meta" style="margin-bottom: 12px">方式 C · MOOD COMPASS · 心情色盘</div>
        <div class="mood-wheel-wrap">
          <MoodWheel :size="340" @change="onWheelChange" />
        </div>
        <div class="meta" style="text-align: center; margin-top: 14px; color: var(--ink-2)">
          DRAG · 拖动球点选择此刻的心情坐标
        </div>

        <!-- Resume last session -->
        <div class="home-section">
          <div class="meta" style="margin-bottom: 10px">继续 · CONTINUE</div>
          <div v-if="lastSession" class="resume-card">
            <MoodBlob :size="56" :drift="false" geometry="circle" />
            <div class="resume-info">
              <div style="font-family: var(--serif-cn); font-size: 15px; font-weight: 500">
                {{ lastSession.moodText || '上次的电台' }}
              </div>
              <div class="meta" style="margin-top: 2px">
                {{ lastSession.scene ? lastSession.scene + ' · ' : '' }}{{ formatSessionDate(lastSession.createdAt) }}
              </div>
            </div>
            <button class="btn-pill" @click="resumeSession(lastSession)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
            </button>
          </div>
          <div v-else class="resume-card resume-card--empty">
            <div class="meta" style="color: var(--ink-3)">暂无最近电台记录</div>
          </div>
        </div>

        <!-- Today's recommendation -->
        <div class="home-section">
          <div class="meta" style="margin-bottom: 10px">今日推荐 · FOR THE HOUR</div>
          <div class="recommend-card">
            <div class="recommend-bg" />
            <div class="recommend-content">
              <div class="mono" style="font-size: 10px; letter-spacing: .16em; opacity: .85">{{ recommendTimeLabel }}</div>
              <div class="serif-en recommend-title">{{ recommendTitle }}</div>
              <div style="font-size: 14px; margin-top: 4px; opacity: .9">{{ recommendSubtitle }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Recent sessions strip -->
    <div class="home-recent">
      <div class="between" style="margin-bottom: 14px">
        <div class="meta">最近的电台 · RECENT STATIONS</div>
        <RouterLink to="/history" class="meta" style="color: var(--ink-2); text-decoration: none">查看全部 →</RouterLink>
      </div>
      <div class="recent-grid">
        <div
          v-for="(s, i) in displayedSessions"
          :key="s.sessionId"
          class="recent-card"
          :data-mood="s.moodPreset || 'dusk'"
          @click="resumeSession(s)"
        >
          <MoodBlob :size="260" :drift="false" geometry="blob" style="margin-bottom: 10px; pointer-events: none" />
          <div class="meta">№ 0{{ i + 1 }} · {{ (s.scene || s.moodPreset || '电台').toUpperCase() }}</div>
          <div style="font-family: var(--serif-cn); font-size: 18px; font-weight: 500; margin-top: 4px">
            {{ s.moodText || '情绪电台' }}
          </div>
          <div class="meta" style="margin-top: 6px; color: var(--ink-2)">{{ formatSessionDate(s.createdAt) }}</div>
        </div>

        <!-- Placeholder cards when not enough sessions -->
        <template v-if="displayedSessions.length < 4">
          <div
            v-for="p in placeholders"
            :key="'ph-' + p.id"
            class="recent-card recent-card--placeholder"
            :data-mood="p.mood"
          >
            <MoodBlob :size="260" :drift="false" geometry="blob" style="margin-bottom: 10px; pointer-events: none" />
            <div class="meta">{{ p.tag }}</div>
            <div style="font-family: var(--serif-cn); font-size: 18px; font-weight: 500; margin-top: 4px">{{ p.title }}</div>
            <div class="meta" style="margin-top: 6px; color: var(--ink-2)">{{ p.sub }}</div>
          </div>
        </template>
      </div>
    </div>

    <!-- Mini player (fixed bottom) -->
    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useRadioStore } from '@/stores/radio'
import { usePlayerStore } from '@/stores/player'
import { useUiStore } from '@/stores/ui'
import NavBar from '@/components/common/NavBar.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import MoodWheel from './MoodWheel.vue'
import type { RadioSession } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const radio = useRadioStore()
const player = usePlayerStore()
const ui = useUiStore()

// ── Greeting ───────────────────────────────────────────────────────────────
const hour = new Date().getHours()
const greetEn = hour < 11 ? 'Good morning' : hour < 18 ? 'Good afternoon' : hour < 23 ? 'Good evening' : 'Late night'
const greetCn = hour < 11 ? '早安' : hour < 18 ? '下午好' : hour < 23 ? '晚上好' : '深夜好'
const greetEnUpper = greetEn.toUpperCase()
const todayLabel = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })

const userName = computed(() => auth.user?.username ?? '旅人')

// ── Mood input ──────────────────────────────────────────────────────────────
const moodInput = ref('')
const selectedScene = ref('')
const sessionDuration = ref(30)

const scenes = ['通勤', '学习', '跑步', '写作', '睡前', '派对', '深夜', '+ 自定义']

const durationOptions = [
  { value: 15, label: '15min' },
  { value: 30, label: '30min' },
  { value: 60, label: '60min' },
  { value: 120, label: '120min' },
  { value: 0, label: '无限' },
]

function handleSceneSelect(scene: string) {
  if (scene === '+ 自定义') return
  selectedScene.value = scene === selectedScene.value ? '' : scene
  radio.setScene(selectedScene.value)
}

// ── MoodWheel callback ──────────────────────────────────────────────────────
function onWheelChange(_x: number, _y: number) {
  // Future: map coordinates to mood preset
}

// ── Radio actions ───────────────────────────────────────────────────────────
async function handleStartRadio() {
  const text = moodInput.value.trim() || selectedScene.value || '随机'
  radio.setMoodText(text)
  try {
    await radio.startRadio({ moodText: text, scene: selectedScene.value || undefined, durationMinutes: sessionDuration.value || undefined })
    router.push('/player')
  } catch {
    // silently fail – backend may not be running in dev
    router.push('/player')
  }
}

async function handleJustPlay() {
  radio.setMoodText('随机')
  try {
    await radio.startRadio({ moodText: '随机', durationMinutes: sessionDuration.value || undefined })
  } catch {
    // dev: navigate anyway
  }
  router.push('/player')
}

function resumeSession(session: RadioSession) {
  radio.setMoodText(session.moodText)
  if (session.scene) radio.setScene(session.scene)
  if (session.moodPreset) ui.setMoodPreset(session.moodPreset)
  router.push('/player')
}

// ── Sessions ────────────────────────────────────────────────────────────────
const lastSession = computed<RadioSession | null>(() => radio.recentSessions[0] ?? null)
const displayedSessions = computed(() => radio.recentSessions.slice(0, 4))

const currentMoodPreset = computed(() => ui.moodPreset)

// Placeholder cards shown when no sessions yet
const placeholderData = [
  { id: 1, title: '通勤的余光', tag: '平静 · 中能量', sub: '14 首 · 52 MIN', mood: 'dusk' },
  { id: 2, title: '雨打窗台',   tag: '忧郁 · 低能量', sub: '9 首 · 34 MIN',  mood: 'melancholy' },
  { id: 3, title: '清晨慢跑',   tag: '明亮 · 高能量', sub: '18 首 · 61 MIN', mood: 'energetic' },
  { id: 4, title: '专注写作',   tag: '冷静 · 中能量', sub: '12 首 · 48 MIN', mood: 'focused' },
]
const placeholders = computed(() =>
  placeholderData.slice(0, Math.max(0, 4 - displayedSessions.value.length)),
)

function formatSessionDate(isoStr: string): string {
  try {
    const d = new Date(isoStr)
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

// ── Recommendation card (time-based) ───────────────────────────────────────
const recommendTimeLabel = computed(() => {
  if (hour >= 5 && hour < 12) return '05:00 — 12:00'
  if (hour >= 12 && hour < 18) return '12:00 — 18:00'
  if (hour >= 18 && hour < 23) return '18:00 — 23:00'
  return '23:00 — 02:00'
})
const recommendTitle = computed(() => {
  if (hour >= 5 && hour < 12) return 'morning haze'
  if (hour >= 12 && hour < 18) return 'afternoon float'
  if (hour >= 18 && hour < 23) return 'evening drift'
  return 'night float'
})
const recommendSubtitle = computed(() => {
  if (hour >= 5 && hour < 12) return '晨雾漂浮电台 · 10 首 · 38 min'
  if (hour >= 12 && hour < 18) return '午后漫游电台 · 15 首 · 55 min'
  if (hour >= 18 && hour < 23) return '黄昏流浪电台 · 12 首 · 46 min'
  return '深夜漂浮电台 · 13 首 · 49 min'
})

// ── Lifecycle ───────────────────────────────────────────────────────────────
onMounted(async () => {
  try {
    await radio.fetchRecentSessions()
  } catch {
    // no-op in dev
  }
})
</script>

<style scoped>
/* ── Page wrapper ─────────────────────────────────────────────────── */
.home-page {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
  padding-bottom: 100px; /* room for MiniPlayer */
}

/* ── Two-column main content ─────────────────────────────────────── */
.home-content {
  position: relative;
  z-index: 2;
  padding: 40px 56px 80px;
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 56px;
}

@media (max-width: 900px) {
  .home-content {
    grid-template-columns: 1fr;
    padding: 24px 22px 80px;
    gap: 28px;
  }
}

/* ── Greeting ─────────────────────────────────────────────────────── */
.home-headline {
  font-size: 96px;
  margin: 8px 0 0;
  line-height: 0.92;
}

.home-subheadline {
  font-size: 40px;
  margin-top: 4px;
}

@media (max-width: 900px) {
  .home-headline    { font-size: 52px; }
  .home-subheadline { font-size: 28px; }
}

/* ── Section spacer ──────────────────────────────────────────────── */
.home-section {
  margin-top: 28px;
}

/* ── Textarea with inline button ─────────────────────────────────── */
.textarea-wrap {
  position: relative;
}

.textarea-wrap .field {
  padding-right: 120px;
  font-size: 16px;
  min-height: 120px;
  resize: none;
}

.btn-inline {
  position: absolute;
  right: 10px;
  bottom: 10px;
  height: 40px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 900px) {
  .textarea-wrap .field {
    padding-right: 16px;
    font-size: 15px;
    min-height: 110px;
  }

  .btn-inline {
    position: static;
    width: 100%;
    justify-content: center;
    margin-top: 10px;
    height: 46px;
  }
}

/* ── Scene tags ──────────────────────────────────────────────────── */
.scene-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ── Just play card ──────────────────────────────────────────────── */
.just-play-card {
  margin-top: 28px;
  padding: 22px 26px;
  border: 1px solid var(--rule);
  border-radius: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  background: var(--paper);
}

.just-play-title {
  font-size: 30px;
  line-height: 1.1;
}

@media (max-width: 900px) {
  .just-play-card {
    padding: 18px;
  }
  .just-play-title { font-size: 24px; }
}

/* ── MoodWheel wrapper ───────────────────────────────────────────── */
.mood-wheel-wrap {
  display: flex;
  justify-content: center;
}

/* ── Resume card ─────────────────────────────────────────────────── */
.resume-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--rule);
  border-radius: 16px;
  align-items: center;
  background: var(--paper);
  cursor: pointer;
  transition: border-color 0.15s;
}

.resume-card:hover {
  border-color: var(--ink-3);
}

.resume-card--empty {
  justify-content: center;
  cursor: default;
}

.resume-card--empty:hover {
  border-color: var(--rule);
}

.resume-info {
  flex: 1;
  min-width: 0;
}

/* ── Recommendation card ─────────────────────────────────────────── */
.recommend-card {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  min-height: 120px;
  color: var(--paper);
}

.recommend-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 20%, var(--mood-a) 0%, transparent 60%),
    radial-gradient(circle at 80% 30%, var(--mood-b) 0%, transparent 60%),
    radial-gradient(circle at 60% 90%, var(--mood-d) 0%, transparent 70%);
  background-color: var(--mood-c);
}

.recommend-content {
  position: relative;
  padding: 16px;
}

.recommend-title {
  font-size: 36px;
  line-height: 1;
  margin-top: 6px;
}

/* ── Recent sessions strip ───────────────────────────────────────── */
.home-recent {
  position: relative;
  padding: 0 56px 100px;
}

@media (max-width: 900px) {
  .home-recent {
    padding: 0 22px 140px;
  }
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 900px) {
  .recent-grid {
    grid-template-columns: repeat(3, 240px);
    overflow-x: auto;
    padding-bottom: 8px;
  }
}

@media (max-width: 600px) {
  .recent-grid {
    grid-template-columns: repeat(3, 200px);
  }
}

.recent-card {
  border: 1px solid var(--rule);
  border-radius: 16px;
  padding: 14px;
  background: var(--paper);
  cursor: pointer;
  transition: border-color 0.15s, transform 0.15s;
  overflow: hidden;
}

.recent-card:hover {
  border-color: var(--ink-3);
  transform: translateY(-2px);
}

.recent-card--placeholder {
  cursor: default;
  opacity: 0.6;
}

.recent-card--placeholder:hover {
  border-color: var(--rule);
  transform: none;
}

/* ── Duration selector ─────────────────────────────────────────────── */
.duration-ctrl {
  display: flex;
  gap: 0;
  border: 1px solid var(--rule);
  border-radius: 999px;
  padding: 2px;
  width: fit-content;
}

.duration-opt {
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  background: transparent;
  color: var(--ink-2);
  font-family: var(--mono);
  font-size: 12px;
  letter-spacing: 0.05em;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}

.duration-opt--active {
  background: var(--ink);
  color: var(--bg);
}
</style>
