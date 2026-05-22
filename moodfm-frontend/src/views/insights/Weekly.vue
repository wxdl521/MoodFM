<template>
  <div style="min-height: 100vh; background: var(--bg); padding-bottom: 100px;">

    <div style="position: sticky; top: 62px; z-index: 10; background: var(--bg); border-bottom: 1px solid var(--rule); padding: 0 56px; display: flex; align-items: center; justify-content: space-between; height: 52px;">
      <RouterLink to="/insights" class="btn-pill" style="text-decoration: none; display: inline-flex; align-items: center; gap: 4px;">‹ Insights</RouterLink>
      <div class="meta desktop-only">WEEKLY REPORT · 周报</div>
      <button class="btn" style="height: 36px; padding: 0 14px;" :disabled="shareLoading" @click="handleShare">{{ shareLoading ? '生成中...' : '分享长图 →' }}</button>
    </div>

    <div v-if="loading" class="page-pad">
      <div v-for="i in 4" :key="i" class="skeleton-block" :style="{ height: i === 1 ? '200px' : '120px', marginBottom: '24px' }" />
    </div>

    <div v-else-if="error" class="page-pad meta" style="color: var(--ink-3); padding-top: 48px;">
      加载失败 · {{ error }}
    </div>

    <div v-else ref="reportRef">
      <div class="hero-section">
        <div class="mood-blob drift" style="width: 700px; height: 700px; right: -200px; top: -200px; opacity: 0.45; pointer-events: none;" />
        <div style="position: relative; z-index: 2; max-width: 920px;">
          <div class="meta">A WEEKLY DISPATCH · WEEK {{ currentWeekLabel }} · {{ weekDateRange }}</div>
          <h1 class="display hero-h1">{{ heroTitle1 }}</h1>
          <h1 class="display hero-h1"><em>{{ heroHeadline }}</em> {{ heroHeadline2 }}</h1>
          <div class="display-cn" style="font-size: clamp(24px, 3vw, 48px); margin-top: 24px; color: var(--ink-2);">
            {{ heroTitleCn }}
          </div>
          <p :style="{ marginTop: '28px', fontSize: 'clamp(14px, 1.2vw, 18px)', lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: '640px', fontFamily: 'var(--serif-cn)' }">
            这一周你听了 <strong style="color: var(--ink);">{{ report.totalTracks }} 首歌、{{ report.listenTime }}</strong>。情绪曲线整体偏向"温柔"与"专注"，深夜段出现两次"轻度忧郁"波峰。
          </p>
        </div>
      </div>

      <div class="numbers-grid">
        <div
          v-for="(s, i) in numberStats"
          :key="i"
          class="number-cell"
          :style="{ borderRight: i < 3 ? '1px solid var(--rule)' : 'none' }"
        >
          <div class="meta">{{ s.l }}</div>
          <div class="display" style="font-size: clamp(36px, 4vw, 64px); margin-top: 8px;">{{ s.n }}</div>
          <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '13px', color: 'var(--ink-2)', marginTop: '2px' }">{{ s.cn }}</div>
        </div>
      </div>

      <div class="editorial-section">
        <div class="editorial-label-col">
          <div class="meta">CHAPTER ONE</div>
          <h2 class="display editorial-h2">The week, in one <em>shape</em>.</h2>
          <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '16px', color: 'var(--ink-2)', marginTop: '6px' }">一周一形</div>
        </div>
        <div>
          <div style="display: flex; justify-content: center; padding: 0;">
            <MoodBlob :size="320" :drift="true" geometry="blob" />
          </div>
          <p :style="{ marginTop: '24px', fontSize: 'clamp(14px, 1.2vw, 17px)', lineHeight: 1.8, color: 'var(--ink-2)', fontFamily: 'var(--serif-cn)' }">
            如果用一个形状来描述这一周——大概是这样：边缘柔软，中间有一团暖光，左下角藏着一小片冷色调。这是 AI 根据你 <em>{{ report.totalTracks }} 首歌</em>的情绪曲线生成的"周形"。
          </p>
        </div>
      </div>

      <div style="border-bottom: 1px solid var(--rule);">
        <div class="page-pad" style="padding-top: 40px; padding-bottom: 40px;">
          <div class="meta" style="margin-bottom: 20px;">FIG. 01 · 7-DAY MOOD CURVE · 情绪曲线</div>
          <div style="height: 260px;">
            <VChart :option="weekLineOption" autoresize style="width: 100%; height: 100%;" />
          </div>
        </div>
      </div>

      <div class="pullout-section">
        <div class="display" style="font-size: clamp(22px, 3.5vw, 48px); font-style: italic; line-height: 1.3; max-width: 880px; margin: 0 auto;">
          "{{ quote || '继续使用后，AI 将为你生成专属金句。' }}"
        </div>
        <div class="meta" style="margin-top: 24px; color: var(--ink-3);">— AI · MOODFM EDITORIAL</div>
      </div>

      <div class="discoveries-section">
        <div class="between" style="margin-bottom: 24px; align-items: flex-end;">
          <div>
            <div class="meta">CHAPTER TWO · 本周新发现</div>
            <h2 class="display" style="font-size: clamp(28px, 3.5vw, 56px); margin: 8px 0 0;">
              {{ report.newDiscovered }} <em>discoveries</em>.
            </h2>
          </div>
        </div>
        <div class="discoveries-grid">
          <div
            v-for="(tk, i) in discoveries"
            :key="i"
            class="discovery-card"
          >
            <MoodBlob :size="180" :drift="false" geometry="blob" />
            <div class="meta" style="margin-top: 10px;">№ {{ String(i + 1).padStart(2, '0') }} · {{ tk.m }}</div>
            <div class="serif-en" style="font-size: 17px; font-style: italic; margin-top: 4px;">{{ tk.t }}</div>
            <div class="meta" style="margin-top: 2px;">{{ tk.a }} · {{ tk.g }}</div>
          </div>
        </div>
      </div>

      <div class="week-nav-bar page-pad" style="padding-top: 32px; padding-bottom: 32px; border-top: 1px solid var(--rule);">
        <div class="meta" style="margin-bottom: 12px;">WEEK NAVIGATION · 切换周</div>
        <div class="row" style="gap: 10px; flex-wrap: wrap;">
          <button
            v-for="w in weekOptions"
            :key="w.id"
            class="btn-pill"
            :class="{ active: currentWeekId === w.id }"
            @click="loadWeek(w.id)"
          >
            WEEK {{ w.label }}
          </button>
        </div>
      </div>

      <div class="page-pad" style="padding-top: 40px; padding-bottom: 40px; text-align: center; border-top: 1px solid var(--rule);">
        <div class="meta">SHARE · 分享</div>
        <h2 class="display" style="font-size: clamp(28px, 3.5vw, 56px); margin: 8px 0 16px;">
          A week worth <em>keeping</em>.
        </h2>
        <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '15px', color: 'var(--ink-2)', maxWidth: '480px', margin: '0 auto 24px' }">
          导出一张可分享的长图，把这一周的"形状"留在朋友圈。
        </div>
        <div class="row" style="gap: 10px; justify-content: center; flex-wrap: wrap;">
          <button class="btn" :disabled="shareLoading" @click="handleShare">{{ shareLoading ? '生成中...' : '生成分享长图 →' }}</button>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { reportsApi } from '@/api/reports'
import html2canvas from 'html2canvas'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const loading = ref(true)
const error = ref<string | null>(null)
const currentWeekId = ref('')
const reportRef = ref<HTMLElement | null>(null)
const shareLoading = ref(false)

const currentWeekLabel = computed(() => currentWeekId.value)

interface WeekOption { id: string; label: string; range: string }
const weekOptions = ref<WeekOption[]>([])

const weekDateRange = computed(() => {
  const w = weekOptions.value.find(o => o.id === currentWeekId.value)
  return w?.range ?? '—'
})

interface Report {
  totalTracks: string
  listenTime: string
  newDiscovered: string
  moodMatch: string
  moodData: number[]
}

const report = ref<Report>({
  totalTracks: '—',
  listenTime: '—',
  newDiscovered: '—',
  moodMatch: '—',
  moodData: [5, 6, 4, 7, 5, 8, 6],
})

const heroTitle1 = ref('A week of')
const heroHeadline = ref('quiet')
const heroHeadline2 = ref('light.')
const heroTitleCn = ref('一周 · 安静、温柔、有一点点蓝。')
const essayBody = ref('')
const quote = ref('')

const numberStats = computed(() => [
  { n: String(report.value.totalTracks), l: 'TRACKS', cn: '首' },
  { n: report.value.listenTime, l: 'LISTEN TIME', cn: '收听时长' },
  { n: String(report.value.newDiscovered), l: 'NEW DISCOVERED', cn: '新发现' },
  { n: report.value.moodMatch, l: 'MOOD MATCH', cn: '匹配度' },
])

const discoveries = ref<{ t: string; a: string; g: string; m: string }[]>([])

const weekLineOption = computed(() => ({
  grid: { left: 40, right: 24, top: 16, bottom: 28 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'var(--paper)',
    borderColor: 'var(--rule)',
    textStyle: { color: 'var(--ink)', fontFamily: 'var(--serif-cn)', fontSize: 13 },
  },
  xAxis: {
    type: 'category',
    data: ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'],
    axisLine: { lineStyle: { color: 'var(--rule)' } },
    axisLabel: { color: 'var(--ink-3)', fontFamily: 'var(--mono)', fontSize: 10 },
    axisTick: { show: false },
    boundaryGap: false,
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 10,
    splitLine: { lineStyle: { color: 'var(--rule)', type: 'dashed' } },
    axisLabel: { color: 'var(--ink-3)', fontFamily: 'var(--mono)', fontSize: 10 },
  },
  series: [{
    name: '情绪曲线',
    type: 'line',
    data: report.value.moodData,
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { color: 'var(--mood-b)', width: 2 },
    itemStyle: { color: 'var(--mood-b)' },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: 'rgba(232,90,138,0.22)' }, { offset: 1, color: 'rgba(232,90,138,0)' }],
      },
    },
    markPoint: {
      data: [{ type: 'min', name: 'low point', label: { formatter: 'low point', fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 12, color: 'var(--ink)' } }],
      symbol: 'circle',
      symbolSize: 14,
      itemStyle: { color: 'transparent', borderColor: 'var(--mood-b)', borderWidth: 1.5 },
    },
  }],
}))

function formatMinutes(mins: number): string {
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}

function formatDateRange(start: string, end: string): string {
  const months = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC']
  const s = new Date(start)
  const e = new Date(end)
  return `${months[s.getMonth()]} ${String(s.getDate()).padStart(2,'0')} — ${months[e.getMonth()]} ${String(e.getDate()).padStart(2,'0')}`
}

function applyReport(res: any) {
  heroTitle1.value = res.titleEn1 || 'A week of'
  heroHeadline.value = res.headlineWord || 'quiet'
  heroHeadline2.value = res.headlineWord2 || 'light.'
  heroTitleCn.value = res.titleCn || '一周 · 安静、温柔、有一点点蓝。'
  essayBody.value = res.essayBody || ''
  quote.value = res.quote || ''

  const s = res.stats ?? {}
  report.value = {
    totalTracks: s.tracks ?? '—',
    listenTime: s.listenTime ?? '—',
    newDiscovered: s.newDiscovered ?? '—',
    moodMatch: s.moodMatch ?? '—',
    moodData: res.moodData ?? [5, 6, 4, 7, 5, 8, 6],
  }

  discoveries.value = (res.topDiscoveries ?? []).slice(0, 4).map((d: any) => ({
    t: d.title,
    a: d.artist,
    g: d.genre ?? 'Music',
    m: (d.mood ?? 'calm').toUpperCase(),
  }))
}

async function loadWeek(id: string) {
  currentWeekId.value = id
  loading.value = true
  error.value = null
  try {
    const res = await reportsApi.getWeeklyReport(id)
    applyReport(res)
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleShare() {
  const el = reportRef.value
  if (!el) return

  shareLoading.value = true
  try {
    const canvas = await html2canvas(el, {
      backgroundColor: '#f4efe6',
      scale: 2,
      useCORS: true,
    })
    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!blob) return
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `moodfm-weekly-${currentWeekId.value || 'report'}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('Share image generation failed:', e)
  } finally {
    shareLoading.value = false
  }
}

onMounted(async () => {
  try {
    const [latest, all] = await Promise.all([
      reportsApi.generate().catch(() => null),
      reportsApi.listReports().catch(() => []),
    ])
    weekOptions.value = all.map(r => ({
      id: r.id,
      label: r.id,
      range: formatDateRange(r.weekStart, r.weekEnd),
    }))
    const target = latest ?? all[0] ?? null
    if (target) {
      currentWeekId.value = target.id
      applyReport(target)
    }
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-pad {
  padding-left: 56px;
  padding-right: 56px;
}

@media (max-width: 768px) {
  .page-pad {
    padding-left: 18px;
    padding-right: 18px;
  }
}

.desktop-only {
  display: block;
}

@media (max-width: 768px) {
  .desktop-only {
    display: none;
  }
}

.hero-section {
  position: relative;
  overflow: hidden;
  padding: 80px 56px 100px;
  border-bottom: 1px solid var(--rule);
}

@media (max-width: 768px) {
  .hero-section {
    padding: 40px 18px 60px;
  }
}

.hero-h1 {
  font-size: clamp(56px, 10vw, 160px);
  margin: 20px 0 0;
  line-height: 0.92;
}

.numbers-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-bottom: 1px solid var(--rule);
}

@media (max-width: 768px) {
  .numbers-grid {
    grid-template-columns: 1fr 1fr;
  }
}

.number-cell {
  padding: 40px 32px;
}

@media (max-width: 768px) {
  .number-cell {
    padding: 24px 18px;
    border-right: none !important;
  }

  .number-cell:nth-child(odd) {
    border-right: 1px solid var(--rule) !important;
  }

  .number-cell:nth-child(1),
  .number-cell:nth-child(2) {
    border-bottom: 1px solid var(--rule);
  }
}

.editorial-section {
  padding: 80px 56px;
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 56px;
  border-bottom: 1px solid var(--rule);
}

@media (max-width: 768px) {
  .editorial-section {
    padding: 40px 18px;
    grid-template-columns: 1fr;
    gap: 24px;
  }
}

.editorial-label-col {
  /* aligns the chapter header */
}

.editorial-h2 {
  font-size: clamp(32px, 3.5vw, 56px);
  margin: 8px 0 0;
  line-height: 1.05;
}

.pullout-section {
  padding: 80px 56px;
  text-align: center;
  border-bottom: 1px solid var(--rule);
  background: var(--bg-2);
}

@media (max-width: 768px) {
  .pullout-section {
    padding: 48px 18px;
  }
}

.discoveries-section {
  padding: 72px 56px;
  border-bottom: 1px solid var(--rule);
}

@media (max-width: 768px) {
  .discoveries-section {
    padding: 40px 18px;
  }
}

.discoveries-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 768px) {
  .discoveries-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.discovery-card {
  padding: 16px;
  border: 1px solid var(--rule);
  border-radius: 14px;
  background: var(--paper);
}

.week-nav-bar {
  border-bottom: 1px solid var(--rule);
}

.skeleton-block {
  background: var(--bg-2);
  border-radius: 12px;
  animation: pulse 1.6s ease-in-out infinite;
}

.page-pad {
  padding-left: 56px;
  padding-right: 56px;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}
</style>
