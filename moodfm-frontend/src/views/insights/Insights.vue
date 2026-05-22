<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { insightsApi } from '@/api/insights'
import html2canvas from 'html2canvas'

use([LineChart, RadarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const period = ref('7天')
const loading = ref(true)
const error = ref<string | null>(null)
const contentRef = ref<HTMLElement | null>(null)

async function handleShare() {
  const el = contentRef.value
  if (!el) return
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
    a.download = `moodfm-insights-${period.value}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('Share image generation failed:', e)
  }
}

// Reactive data — populated from API; fallback values shown until loaded
const statCards = ref([
  { en: 'TOTAL · LISTENING', value: '—', cn: '本周听歌时长', sub: '—' },
  { en: 'TOP · GENRE',       value: '—', cn: '最常听的流派', sub: '—' },
  { en: 'AI · ACCURACY',     value: '—', cn: 'AI 推荐准确率', sub: '红心 ÷ 跳过' },
  { en: 'MOOD · KEY',        value: '—', cn: '心情主旋律',   sub: '—' },
])

const artists = ref<Array<{ n: string; en: string; t: string; p: number }>>([])

// ── Dynamic week / date labels ────────────────────────────────────
function getISOWeek(d: Date): number {
  const tmp = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
  tmp.setUTCDate(tmp.getUTCDate() + 4 - (tmp.getUTCDay() || 7))
  const yearStart = new Date(Date.UTC(tmp.getUTCFullYear(), 0, 1))
  return Math.ceil(((tmp.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
}
function computeDateRange(d: number): string {
  const end = new Date()
  const start = new Date(); start.setDate(end.getDate() - d + 1)
  const fmt = (dt: Date) => `${dt.getMonth() + 1}/${String(dt.getDate()).padStart(2, '0')}`
  return `${fmt(start)} → ${fmt(end)}`
}
const now = new Date()
const headerWeekLabel = ref(`WEEK ${getISOWeek(now)} / ${now.getFullYear()}`)
const headerDateRange = ref(computeDateRange(7))

const headline = ref('tender')
const headlineAlt = ref('awake')
const headlineCn = ref('温柔的清醒')
const essayBody = ref('')
const weeklyReportId = ref<number | null>(null)
const previousReports = ref<Array<{ id: number; week: number; year: number; title: string; mood: string }>>([])

// Chart data refs — initialized empty, filled by API
const trendDates = ref<string[]>([])
const trendMood  = ref<number[]>([])
const trendSong  = ref<number[]>([])
const genreValues = ref<number[]>([0, 0, 0, 0, 0, 0, 0, 0])

const RADAR_AXES = ['Ambient 环境', 'Classical 古典', 'Folk 民谣', 'Indie 独立', 'Electronic 电子', 'Jazz 爵士', 'Pop 流行', 'Rock 摇滚']
const GENRE_KEY: Record<string, string> = {
  ambient: 'Ambient 环境', classical: 'Classical 古典', folk: 'Folk 民谣',
  indie: 'Indie 独立', electronic: 'Electronic 电子', jazz: 'Jazz 爵士',
  pop: 'Pop 流行', rock: 'Rock 摇滚',
}

function formatMinutes(m: number): string {
  const h = Math.floor(m / 60); const rem = m % 60
  return h > 0 ? `${h}h ${rem}m` : `${rem}m`
}

function formatPlayCount(count: number): string {
  const mins = count * 4
  return formatMinutes(mins)
}

const moodLineOption = computed(() => ({
  grid: { left: 40, right: 24, top: 16, bottom: 28 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#f4efe6',
    borderColor: '#d9d3c8',
    textStyle: { color: '#1a1714', fontFamily: 'var(--serif-cn)', fontSize: 13 },
  },
  xAxis: {
    type: 'category',
    data: trendDates.value,
    axisLine: { lineStyle: { color: '#d9d3c8' } },
    axisLabel: { color: '#8a8276', fontFamily: 'var(--mono)', fontSize: 10 },
    axisTick: { show: false },
    boundaryGap: false,
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 10,
    splitLine: { lineStyle: { color: '#d9d3c8', type: 'dashed' } },
    axisLabel: { color: '#8a8276', fontFamily: 'var(--mono)', fontSize: 10 },
  },
  series: [
    {
      name: '用户心情',
      type: 'line',
      data: trendMood.value,
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { color: '#e85a8a', width: 2 },
      itemStyle: { color: '#e85a8a' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(232,90,138,0.18)' }, { offset: 1, color: 'rgba(232,90,138,0)' }],
        },
      },
    },
    {
      name: '听歌情绪',
      type: 'line',
      data: trendSong.value,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#6e5cd9', width: 1.5, type: 'dashed' },
    },
  ],
}))

const radarOption = computed(() => ({
  tooltip: {
    backgroundColor: '#f4efe6',
    borderColor: '#d9d3c8',
    textStyle: { color: '#1a1714', fontFamily: 'var(--serif-cn)', fontSize: 13 },
  },
  radar: {
    indicator: RADAR_AXES.map(name => ({ name, max: 100 })),
    radius: '62%',
    splitLine: { lineStyle: { color: '#d9d3c8' } },
    axisLine: { lineStyle: { color: '#d9d3c8' } },
    axisName: { color: '#4a443c', fontFamily: 'var(--serif-cn)', fontSize: 11 },
    splitArea: { show: false },
  },
  series: [{
    type: 'radar',
    data: [{ value: genreValues.value, name: '流派分布' }],
    lineStyle: { color: '#e85a8a', width: 1.5 },
    areaStyle: {
      color: {
        type: 'radial', x: 0.5, y: 0.5, r: 0.5,
        colorStops: [{ offset: 0, color: 'rgba(255,138,91,0.55)' }, { offset: 1, color: 'rgba(110,92,217,0.25)' }],
      },
    },
    symbol: 'none',
  }],
}))

const days = computed(() => period.value === '7天' ? 7 : period.value === '30天' ? 30 : 90)

async function fetchInsights() {
  loading.value = true
  try {
    const d = days.value
    const [trend, radar, topItems, summary] = await Promise.all([
      insightsApi.getMoodTrend(d).catch(() => null as any),
      insightsApi.getGenreRadar(d).catch(() => [] as any[]),
      insightsApi.getTopItems(d).catch(() => null as any),
      insightsApi.getSummary(d).catch(() => null as any),
    ])

    // MoodTrendVO: { labels, userMood, songMood } — values are 0-1, scale to 0-10 for chart
    if (trend?.labels?.length) {
      trendDates.value = trend.labels
      trendMood.value = trend.userMood.map((v: number) => Math.round(v * 100) / 10)
      trendSong.value = trend.songMood.map((v: number) => Math.round(v * 100) / 10)
    }

    // GenreRadarItemVO[]: { genre, value(0-1) }
    if (radar.length > 0) {
      const map: Record<string, number> = {}
      for (const r of radar) {
        const key = GENRE_KEY[(r.genre as string).toLowerCase()] ?? r.genre
        map[key] = Math.round(r.value * 100)
      }
      genreValues.value = RADAR_AXES.map(a => map[a] ?? 0)
    }

    // TopItemsVO: { artists: [{ name, tag, totalTime, proportion }] }
    if (topItems?.artists?.length) {
      artists.value = topItems.artists.slice(0, 5).map((a: any) => ({
        n: a.name,
        en: a.tag || 'ARTIST',
        t: a.totalTime || '—',
        p: a.proportion ?? 0,
      }))
    }

    // InsightsSummaryVO: { headline, headlineAlt, headlineCn, stats, aiSummary, previousReports, essayBody }
    if (summary) {
      headline.value = summary.headline || 'tender'
      headlineAlt.value = summary.headlineAlt || 'awake'
      headlineCn.value = summary.headlineCn || '温柔的清醒'
      essayBody.value = summary.essayBody || ''
      weeklyReportId.value = summary.weeklyReportId ?? null
      previousReports.value = summary.previousReports || []

      const s = summary.stats
      if (s) {
        statCards.value[0].value = s.totalListeningTime || '—'
        statCards.value[0].sub = s.weekLabel || '—'
        statCards.value[1].value = s.topGenre || '—'
        statCards.value[1].sub = s.topGenreRatio ? `${s.topGenreRatio} 占比` : '—'
        statCards.value[2].value = s.aiAccuracy || '—'
        statCards.value[3].value = s.moodKey || '—'
        statCards.value[3].sub = s.moodSub || '—'
        if (s.weekRange) headerDateRange.value = s.weekRange.replace(' — ', ' → ')
      }
    }
  } catch {
  } finally {
    loading.value = false
  }
}

watch(period, () => {
  headerDateRange.value = computeDateRange(days.value)
  fetchInsights()
})

onMounted(() => { fetchInsights() })
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);position:relative;padding-bottom:100px;">
    <div class="mood-blob drift" style="width:680px;height:680px;left:-180px;top:-200px;opacity:.35;" />

    <NavBar />

    <div style="position:sticky;top:62px;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;align-items:center;">
      <div class="row" style="gap:10px;">
        <RouterLink to="/home" class="btn-pill" style="text-decoration:none;">← Home</RouterLink>
        <div class="meta" style="margin-left:8px;">SECTION IV · INSIGHTS</div>
      </div>
      <div class="row" style="gap:6px;">
        <button
          v-for="p in ['7天','30天','90天']"
          :key="p"
          :class="['btn-pill', period===p ? 'active' : '']"
          @click="period=p"
        >{{ p }}</button>
      </div>
    </div>

    <div ref="contentRef" style="padding:40px 56px;">
      <div class="meta">YOUR EMOTIONAL ATLAS · 个人情绪图鉴 · {{ headerWeekLabel }}</div>
      <h1 class="display" style="font-size:144px;margin:12px 0 0;">You felt</h1>
      <h1 class="display" style="font-size:144px;margin:0;color:var(--ink-3);">
        <em :style="{ background:'linear-gradient(120deg,var(--mood-a),var(--mood-b),var(--mood-c))',WebkitBackgroundClip:'text',color:'transparent' }">{{ headline }}</em>
        <span style="color:var(--ink);"> &amp; </span>
        <em :style="{ background:'linear-gradient(120deg,var(--mood-c),var(--mood-d))',WebkitBackgroundClip:'text',color:'transparent' }">{{ headlineAlt }}</em>.
      </h1>
      <div class="display-cn" style="font-size:32px;margin-top:16px;color:var(--ink-2);">
        这一周，你的心情主旋律是<span style="color:var(--ink);">「{{ headlineCn }}」</span>。
      </div>

      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:1px;margin-top:40px;
                  background:var(--rule);border:1px solid var(--rule);">
        <div v-for="s in statCards" :key="s.en" style="background:var(--bg);padding:24px;">
          <div class="meta">{{ s.en }}</div>
          <div class="display" style="font-size:60px;margin-top:8px;">{{ s.value }}</div>
          <div :style="{ fontFamily:'var(--serif-cn)',fontSize:'14px',color:'var(--ink-2)',marginTop:'4px' }">{{ s.cn }}</div>
          <div class="meta" style="margin-top:6px;color:var(--ink-3);">{{ s.sub }}</div>
        </div>
      </div>

      <div style="margin-top:56px;">
        <div class="between">
          <div>
            <div class="meta">FIG. 01 · MOOD CURVE</div>
            <div class="display-cn" style="font-size:30px;margin-top:4px;">心情曲线 vs 听歌情绪</div>
          </div>
          <div class="meta">{{ headerDateRange }}</div>
        </div>
        <div style="margin-top:20px;height:280px;border:1px solid var(--rule);border-radius:18px;
                    padding:24px 24px 8px;background:var(--paper);">
          <VChart :option="moodLineOption" autoresize style="width:100%;height:100%;" />
        </div>
        <div class="row" style="gap:18px;margin-top:12px;font-family:var(--serif-cn);font-size:13px;color:var(--ink-2);">
          <div class="row" style="gap:6px;align-items:center;">
            <span style="width:18px;height:2px;background:var(--mood-b);display:inline-block;" />
            用户心情
          </div>
          <div class="row" style="gap:6px;align-items:center;">
            <span style="width:18px;height:0;border-top:2px dashed var(--mood-c);display:inline-block;" />
            听歌情绪
          </div>
        </div>
      </div>

      <div style="margin-top:56px;display:grid;grid-template-columns:1fr 1fr;gap:48px;">
        <div>
          <div class="meta">FIG. 02 · GENRE</div>
          <div class="display-cn" style="font-size:30px;margin-top:4px;">流派分布</div>
          <div style="margin-top:20px;height:360px;">
            <VChart :option="radarOption" autoresize style="width:100%;height:100%;" />
          </div>
        </div>
        <div>
          <div class="meta">FIG. 03 · TOP ARTISTS</div>
          <div class="display-cn" style="font-size:30px;margin-top:4px;">本周 TOP 艺人</div>
          <div style="margin-top:20px;">
            <div v-if="artists.length === 0 && !loading"
              style="padding:32px 0;text-align:center;font-family:var(--serif-cn);font-size:14px;color:var(--ink-3);">
              暂无数据 · 开始听歌后将自动更新
            </div>
            <div v-for="(a,i) in artists" :key="a.n" style="padding:14px 0;border-bottom:1px solid var(--rule);">
              <div class="between">
                <div class="row" style="gap:12px;">
                  <div class="display" style="font-size:28px;color:var(--ink-3);width:32px;">0{{ i+1 }}</div>
                  <div>
                    <div :style="{ fontFamily:'var(--serif-cn)',fontSize:'17px',fontWeight:500 }">{{ a.n }}</div>
                    <div class="meta" style="margin-top:2px;">{{ a.en }}</div>
                  </div>
                </div>
                <div class="meta" style="color:var(--ink-2);">{{ a.t }}</div>
              </div>
              <div style="margin-top:10px;height:2px;background:var(--rule);position:relative;">
                <div :style="{ position:'absolute',left:0,top:0,bottom:0,width:(a.p*100)+'%',
                               background:'linear-gradient(90deg,var(--mood-a),var(--mood-c))' }" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style="margin-top:56px;position:relative;overflow:hidden;border-radius:24px;padding:40px;color:#fff;">
        <div style="position:absolute;inset:0;background:radial-gradient(circle at 15% 20%,var(--mood-a) 0%,transparent 55%),
                    radial-gradient(circle at 85% 30%,var(--mood-b) 0%,transparent 55%),
                    radial-gradient(circle at 70% 90%,var(--mood-c) 0%,transparent 60%);background-color:var(--mood-d);" />
        <div style="position:relative;">
          <div class="mono" style="font-size:10px;letter-spacing:.2em;opacity:.85;">AI · WEEKLY ESSAY · 周报节选</div>
          <h2 class="display" style="font-size:60px;margin:12px 0 16px;text-shadow:0 2px 30px rgba(0,0,0,.2);">
            "{{ headline }}, but never {{ headlineAlt }}."
          </h2>
          <p v-if="essayBody" :style="{ fontFamily:'var(--serif-cn)',fontSize:'18px',lineHeight:1.7,maxWidth:'720px' }">
            {{ essayBody }}
          </p>
          <p v-else :style="{ fontFamily:'var(--serif-cn)',fontSize:'18px',lineHeight:1.7,maxWidth:'720px' }">
            继续使用后，AI 将为你生成专属周报。
          </p>
          <div class="row" style="margin-top:24px;gap:10px;">
            <RouterLink to="/insights/weekly" class="btn" style="background:#fff;color:var(--ink);border-color:#fff;text-decoration:none;">查看完整周报 →</RouterLink>
            <button class="btn btn-ghost btn-ghost--on-dark" style="color:#fff;border-color:rgba(255,255,255,.4);" @click="handleShare">生成分享长图</button>
          </div>
        </div>
      </div>

      <div style="margin-top:56px;">
        <div class="meta">PREVIOUS ISSUES · 历史报告</div>
        <div class="display-cn" style="font-size:30px;margin-top:4px;margin-bottom:20px;">过往周报</div>
        <div v-if="previousReports.length" style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;">
          <div
            v-for="r in previousReports"
            :key="r.id"
            :data-mood="r.mood"
            style="padding:14px;border:1px solid var(--rule);border-radius:16px;background:var(--paper);cursor:pointer;"
          >
            <MoodBlob :size="240" :drift="false" geometry="blob" style="margin-bottom:12px;" />
            <div class="meta">WEEK {{ r.week }} · {{ r.year }}</div>
            <div :style="{ fontFamily:'var(--serif-cn)',fontSize:'17px',fontWeight:500,marginTop:'4px' }">{{ r.title }}</div>
          </div>
        </div>
        <div v-else style="padding:40px 0;text-align:center;border:1px solid var(--rule);border-radius:16px;background:var(--paper);">
          <div style="font-family:var(--serif-en);font-style:italic;font-size:22px;color:var(--ink-3);">No reports yet.</div>
          <div style="font-family:var(--serif-cn);font-size:13px;color:var(--ink-3);margin-top:6px;">持续使用后，系统将每周自动生成一份专属周报</div>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>
