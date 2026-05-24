<template>
  <div class="home-page" :data-mood="currentMoodPreset">
    <!-- Background mood blob decoration -->
    <div
      class="mood-blob drift"
      style="width: 760px; height: 760px; right: -220px; top: -260px; opacity: 0.4; z-index: 0;"
    />


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
              @input="onTextInput"
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
          <MoodWheel ref="moodWheelRef" :size="340" @change="onWheelChange" />
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
          <div class="recommend-card" style="cursor:pointer;" @click="handleRecommendPlay">
            <div class="recommend-bg" />
            <div class="recommend-content">
              <div class="mono" style="font-size: 10px; letter-spacing: .16em; opacity: .85">{{ recommendTimeLabel }}</div>
              <div class="serif-en recommend-title">{{ recommendTitle }}</div>
              <div style="font-size: 14px; margin-top: 4px; opacity: .9">{{ recommendSubtitle }}</div>
              <div class="mono" style="font-size: 10px; letter-spacing: .12em; opacity: .7; margin-top: 8px">▶ 立即播放</div>
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

    <!-- Error toast -->
    <Transition name="fade">
      <div v-if="errorToast" class="error-toast">
        {{ errorToast }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useRadioStore } from '@/stores/radio'
import { usePlayerStore } from '@/stores/player'
import { useUiStore } from '@/stores/ui'
import MoodBlob from '@/components/common/MoodBlob.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import MoodWheel from './MoodWheel.vue'
import type { RadioSession } from '@/types'
import { logger } from '@/utils/logger'

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

// Generated text from the MoodWheel (kept separate from `moodInput` so the
// three input methods can be mutually exclusive).
const wheelMoodText = ref('')

// Track which input source the user most recently used. Only that source's
// value will be sent to the backend; switching sources clears the others.
const lastInputSource = ref<'text' | 'scene' | 'wheel' | null>(null)

// Ref to the MoodWheel component so we can reset its dot position.
const moodWheelRef = ref<InstanceType<typeof MoodWheel> | null>(null)

const scenes = ['通勤', '学习', '跑步', '写作', '睡前', '派对', '深夜']

// ── Input-source mux ────────────────────────────────────────────────────────
function clearText() {
  moodInput.value = ''
}
function clearScene() {
  selectedScene.value = ''
  radio.setScene('')
}
function clearWheel() {
  wheelMoodText.value = ''
  moodWheelRef.value?.reset()
}

function onTextInput() {
  // Only treat as a fresh "text" input when the textarea actually has content.
  // Deleting back to empty should NOT clobber a previously-chosen scene/wheel.
  if (!moodInput.value.trim()) return
  if (lastInputSource.value === 'text') return
  lastInputSource.value = 'text'
  clearScene()
  clearWheel()
}

// ── Error toast ─────────────────────────────────────────────────────────────
const errorToast = ref<string | null>(null)
let errorToastTimer: ReturnType<typeof setTimeout> | null = null
function showError(msg: string) {
  errorToast.value = msg
  if (errorToastTimer) clearTimeout(errorToastTimer)
  errorToastTimer = setTimeout(() => { errorToast.value = null }, 4000)
}

const durationOptions = [
  { value: 15, label: '15min' },
  { value: 30, label: '30min' },
  { value: 60, label: '60min' },
  { value: 120, label: '120min' },
  { value: 0, label: '无限' },
]

function handleSceneSelect(scene: string) {
  const toggled = scene === selectedScene.value ? '' : scene
  selectedScene.value = toggled
  radio.setScene(toggled)
  if (toggled) {
    lastInputSource.value = 'scene'
    clearText()
    clearWheel()
  } else if (lastInputSource.value === 'scene') {
    // User toggled the scene back off → nothing is selected anymore.
    lastInputSource.value = null
  }
}

// ── MoodWheel callback ──────────────────────────────────────────────────────
function onWheelChange(x: number, y: number) {
  // Any wheel drag counts as choosing the "wheel" source — switching away
  // from text/scene even if the user landed near centre.
  if (lastInputSource.value !== 'wheel') {
    lastInputSource.value = 'wheel'
    clearText()
    clearScene()
  }

  const dist = Math.hypot(x, y)
  if (dist < 0.12) {
    // Near-centre → no strong mood signal, leave wheelMoodText empty so that
    // submission falls back to the generic '随机' value.
    wheelMoodText.value = ''
    return
  }

  let text = ''
  const warm = x       // +1=warm/energetic  -1=cool/calm
  const bright = -y    // +1=bright/active   -1=dark/quiet  (y is inverted: top=-1)

  if (bright > 0.35 && warm > 0.35)       text = '精力充沛，想听节奏感强又温热的音乐'
  else if (bright > 0.35 && warm < -0.35) text = '心情明亮，想要一些清爽有活力的旋律'
  else if (bright < -0.35 && warm > 0.35) text = '有些疲惫，想沉浸在温柔的慢旋律里'
  else if (bright < -0.35 && warm < -0.35) text = '需要专注，想要清冷纯粹的背景音'
  else if (bright > 0.4)                   text = '状态不错，想要节奏感强一点的音乐'
  else if (bright < -0.4)                  text = '有些慵懒，想要轻柔安静的陪伴'
  else if (warm > 0.4)                     text = '心里有些温热，想要有情绪的旋律'
  else if (warm < -0.4)                    text = '心境清淡，想要简单不复杂的音乐'
  else                                     text = '心情平平，随便来点什么吧'

  wheelMoodText.value = text
}

// ── Radio actions ───────────────────────────────────────────────────────────
async function handleStartRadio() {
  // Only the most recently used input source contributes to the request —
  // the other two have already been visually cleared.
  let text = ''
  let scene: string | undefined
  if (lastInputSource.value === 'text') {
    text = moodInput.value.trim()
  } else if (lastInputSource.value === 'scene') {
    text = selectedScene.value
    scene = selectedScene.value || undefined
  } else if (lastInputSource.value === 'wheel') {
    text = wheelMoodText.value
  }
  if (!text) text = '随机'
  radio.setMoodText(text)
  try {
    await radio.startRadio({ moodText: text, scene, durationMinutes: sessionDuration.value === 0 ? null : sessionDuration.value })
    router.push('/player')
  } catch (err) {
    logger.warn('home:startRadio', err)
    showError('调台失败 · 请检查网络或稍后重试')
  }
}

async function handleRecommendPlay() {
  const text = recommendTitle.value
  radio.setMoodText(text)
  try {
    await radio.startRadio({ moodText: text, durationMinutes: sessionDuration.value === 0 ? null : sessionDuration.value })
    router.push('/player')
  } catch (err) {
    logger.warn('home:recommendPlay', err)
    showError('调台失败 · 请检查网络或稍后重试')
  }
}

async function handleJustPlay() {
  radio.setMoodText('随机')
  try {
    await radio.startRadio({ moodText: '随机', durationMinutes: sessionDuration.value === 0 ? null : sessionDuration.value })
    router.push('/player')
  } catch (err) {
    logger.warn('home:justPlay', err)
    showError('调台失败 · 请检查网络或稍后重试')
  }
}

async function resumeSession(session: RadioSession) {
  radio.setMoodText(session.moodText)
  if (session.scene) radio.setScene(session.scene)
  try {
    await radio.startRadio({
      moodText: session.moodText || '随机',
      scene: session.scene || undefined,
      durationMinutes: sessionDuration.value === 0 ? null : sessionDuration.value,
    })
    router.push('/player')
  } catch (err) {
    logger.warn('home:resumeSession', err)
    showError('继续电台失败 · 请检查网络或稍后重试')
  }
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
  } catch (err) {
    // silent: 时间格式化失败仅意味着展示空串
    logger.warn('home:format-session-date', err)
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
  } catch (err) {
    // silent: 最近会话加载失败时静默回退到 placeholder
    logger.warn('home:fetch-recent-sessions', err)
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
  transition: box-shadow 0.22s ease, border-color 0.22s ease;
}

.just-play-card:hover {
  border-color: var(--ink-3);
  box-shadow: 0 4px 20px rgba(110, 92, 217, 0.1);
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
  box-shadow: 0 4px 16px rgba(110, 92, 217, 0.12);
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
  transition: border-color 0.18s ease,
            transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
            box-shadow 0.22s ease;
  overflow: hidden;
}

.recent-card:hover {
  border-color: var(--ink-3);
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(110, 92, 217, 0.14), 0 2px 8px rgba(26,23,20,0.06);
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

/* ── Home enter animations ─────────────────────────── */
@keyframes home-rise {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

.home-left {
  animation: home-rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.08s both;
}

.home-right {
  animation: home-rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.18s both;
}

/* ── Recent cards stagger ─────────────────────────── */
@keyframes card-rise {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}

.recent-card:nth-child(1) { animation: card-rise 0.4s cubic-bezier(0.16, 1, 0.3, 1) 0.12s both; }
.recent-card:nth-child(2) { animation: card-rise 0.4s cubic-bezier(0.16, 1, 0.3, 1) 0.19s both; }
.recent-card:nth-child(3) { animation: card-rise 0.4s cubic-bezier(0.16, 1, 0.3, 1) 0.26s both; }
.recent-card:nth-child(4) { animation: card-rise 0.4s cubic-bezier(0.16, 1, 0.3, 1) 0.33s both; }

/* ── Error toast ─────────────────────────────────────── */
.error-toast {
  position: fixed;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 60;
  background: var(--ink);
  color: var(--bg);
  border-radius: 24px;
  padding: 14px 22px;
  font-family: var(--serif-cn);
  font-size: 14px;
  line-height: 1.5;
  max-width: 360px;
  width: calc(100% - 40px);
  text-align: center;
  box-shadow: 0 8px 32px rgba(0,0,0,.3);
}

.fade-enter-active,
.fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}
</style>
