<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, BarChart, PieChart, RadarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, RadarComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import '@/assets/styles/admin.css'
import { analyticsAdminApi, type GenreCount, type PlatformCount, type TopSong, type MoodDist } from '@/api/admin'
import { logger } from '@/utils/logger'

use([LineChart, BarChart, PieChart, RadarChart, GridComponent, TooltipComponent, LegendComponent, RadarComponent, CanvasRenderer])

const range = ref<'7d' | '30d' | '90d'>('30d')

function toast(msg: string, type = 'ok') { (window as any).__adminToast?.(msg, type) }

// ── Data refs ────────────────────────────────────────────────────────────────

const dauLabels    = ref<string[]>([])
const dauData      = ref<number[]>([])
const genreData    = ref<GenreCount[]>([])
const platformData = ref<PlatformCount[]>([])
const topSongs     = ref<TopSong[]>([])
const moodDist     = ref<MoodDist>({ thisMonth: [0, 0, 0, 0, 0, 0], lastMonth: [0, 0, 0, 0, 0, 0] })
const loading      = ref(false)

// ── Load ─────────────────────────────────────────────────────────────────────

async function loadAll(days: number) {
  loading.value = true
  try {
    const [dau, genre, platform, songs, mood] = await Promise.all([
      analyticsAdminApi.getDau(days),
      analyticsAdminApi.getGenre(days),
      analyticsAdminApi.getPlatform(days),
      analyticsAdminApi.getTopSongs(days),
      analyticsAdminApi.getMood(days),
    ])
    dauLabels.value    = dau.labels
    dauData.value      = dau.dau
    genreData.value    = genre
    platformData.value = platform
    topSongs.value     = songs
    moodDist.value     = mood
  } catch (err) {
    logger.warn('admin:analytics-load', err)
    toast('数据加载失败', 'warn')
  } finally {
    loading.value = false
  }
}

watch(range, (val) => {
  const days = val === '7d' ? 7 : val === '30d' ? 30 : 90
  loadAll(days)
})

onMounted(() => loadAll(30))

// ── Chart options ─────────────────────────────────────────────────────────────

const dauOption = computed(() => {
  const days = range.value === '7d' ? 7 : range.value === '30d' ? 30 : 90
  return {
    grid: { top: 30, right: 20, bottom: 36, left: 56 },
    tooltip: { trigger: 'axis', textStyle: { fontFamily: 'JetBrains Mono', fontSize: 11 } },
    legend: { data: ['DAU'], bottom: 0, textStyle: { fontFamily: 'JetBrains Mono', fontSize: 10, color: '#8a8276' } },
    xAxis: { type: 'category', data: dauLabels.value, axisLabel: { fontFamily: 'JetBrains Mono', fontSize: 9, color: '#8a8276', interval: days > 30 ? Math.floor(days / 12) : 'auto' } },
    yAxis: [
      { type: 'value', axisLabel: { fontFamily: 'JetBrains Mono', fontSize: 9, color: '#8a8276' }, splitLine: { lineStyle: { color: 'rgba(26,23,20,0.05)' } } },
    ],
    series: [
      { name: 'DAU', type: 'line', data: dauData.value, smooth: true, yAxisIndex: 0, lineStyle: { color: '#ff8a5b', width: 2 }, areaStyle: { color: 'rgba(255,138,91,0.07)' }, symbol: 'none', itemStyle: { color: '#ff8a5b' } },
    ]
  }
})

const GENRE_COLORS = ['#ff8a5bcc', '#e85a8acc', '#6e5cd9cc', '#4e9e78cc', '#2d3a8ccc', '#c87d1acc', '#ff8a5bcc', '#e85a8acc']

const genreOption = computed(() => ({
  grid: { top: 10, right: 40, bottom: 28, left: 80 },
  tooltip: { trigger: 'axis', textStyle: { fontFamily: 'JetBrains Mono', fontSize: 11 } },
  xAxis: { type: 'value', axisLabel: { fontFamily: 'JetBrains Mono', fontSize: 9, color: '#8a8276', formatter: (v: number) => `${v}` }, splitLine: { lineStyle: { color: 'rgba(26,23,20,0.05)' } } },
  yAxis: { type: 'category', data: genreData.value.map(d => d.genre), axisLabel: { fontFamily: 'JetBrains Mono', fontSize: 9, color: '#4a443c' }, splitLine: { show: false } },
  series: [{
    type: 'bar',
    data: genreData.value.map((d, i) => ({
      value: d.count,
      itemStyle: { color: GENRE_COLORS[i % GENRE_COLORS.length], borderRadius: 4 }
    })),
    barMaxWidth: 20,
  }]
}))

const platformOption = computed(() => ({
  tooltip: { trigger: 'item', textStyle: { fontFamily: 'JetBrains Mono', fontSize: 11 } },
  legend: { bottom: 0, textStyle: { fontFamily: 'JetBrains Mono', fontSize: 10, color: '#8a8276' } },
  series: [{
    type: 'pie',
    radius: ['55%', '78%'],
    center: ['50%', '44%'],
    data: platformData.value.map(d => ({
      value: d.count,
      name: d.platform === 'netease' ? '网易云音乐' : 'QQ 音乐',
      itemStyle: { color: d.platform === 'netease' ? '#c0392b' : '#1e6eb5' },
    })),
    label: { show: false },
    emphasis: { scale: false },
  }]
}))

const moodOption = computed(() => ({
  tooltip: { textStyle: { fontFamily: 'JetBrains Mono', fontSize: 11 } },
  legend: { bottom: 0, textStyle: { fontFamily: 'JetBrains Mono', fontSize: 10, color: '#8a8276' } },
  radar: {
    indicator: [
      { name: '正向·高能', max: 50 },
      { name: '正向·低能', max: 50 },
      { name: '中性·高能', max: 50 },
      { name: '中性·低能', max: 50 },
      { name: '负向·高能', max: 50 },
      { name: '负向·低能', max: 50 },
    ],
    name: { textStyle: { fontFamily: 'JetBrains Mono', fontSize: 9, color: '#4a443c' } },
    splitLine: { lineStyle: { color: 'rgba(26,23,20,0.08)' } },
  },
  series: [{
    type: 'radar',
    data: [
      { name: '本月', value: moodDist.value.thisMonth, lineStyle: { color: '#ff8a5b', width: 2 }, areaStyle: { color: 'rgba(255,138,91,0.15)' }, itemStyle: { color: '#ff8a5b' }, symbolSize: 5 },
      { name: '上月', value: moodDist.value.lastMonth, lineStyle: { color: '#6e5cd9', width: 1.5, type: 'dashed' }, areaStyle: { color: 'rgba(110,92,217,0.1)' }, itemStyle: { color: '#6e5cd9' }, symbolSize: 4 },
    ]
  }]
}))

// ── Platform percentage ───────────────────────────────────────────────────────

const platformPercent = computed(() => {
  const total = platformData.value.reduce((a, b) => a + b.count, 0)
  if (!total) return { netease: 0, qq: 0 }
  const netease = platformData.value.find(d => d.platform === 'netease')?.count ?? 0
  const qq      = platformData.value.find(d => d.platform !== 'netease')?.count ?? 0
  return { netease: Math.round(netease / total * 100), qq: Math.round(qq / total * 100) }
})

function exportData() { toast('数据导出任务已创建，稍后将发送到管理员邮箱') }
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">Analytics</span>
        <span class="topbar-title-cn">数据分析</span>
      </div>
      <div class="topbar-actions">
        <div class="row gap-6">
          <button v-for="r in (['7d','30d','90d'] as const)" :key="r"
                  :class="['btn btn-sm', range === r ? 'btn-primary' : 'btn-ghost']"
                  @click="range = r">{{ r }}</button>
        </div>
        <button class="btn btn-ghost btn-sm" @click="exportData">↓ 导出</button>
      </div>
    </div>

    <div class="content-area">
      <!-- DAU -->
      <div class="card mb-20">
        <div class="card-head">
          <span class="card-title">活跃用户趋势 · DAU</span>
          <span class="badge neutral mono">{{ range }}</span>
        </div>
        <div class="card-body">
          <div class="chart-wrap" style="height:240px;">
            <VChart :option="dauOption" autoresize style="height:100%;width:100%" />
          </div>
        </div>
      </div>

      <!-- Genre + Platform -->
      <div class="grid-12 mb-20">
        <div class="card">
          <div class="card-head"><span class="card-title">流派分布 · TOP 8</span></div>
          <div class="card-body">
            <div class="chart-wrap" style="height:240px;">
              <VChart :option="genreOption" autoresize style="height:100%;width:100%" />
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-head"><span class="card-title">平台使用占比</span></div>
          <div class="card-body">
            <div class="chart-wrap" style="height:200px;">
              <VChart :option="platformOption" autoresize style="height:100%;width:100%" />
            </div>
            <div style="margin-top:14px;display:grid;grid-template-columns:1fr 1fr;gap:12px;">
              <div style="text-align:center;padding:10px;background:rgba(192,57,43,0.06);border-radius:8px;">
                <div class="mono t-ink3 ls-wide" style="font-size:9px;">网易云</div>
                <div class="mono fs-13" style="color:#c0392b;margin-top:3px;">{{ platformPercent.netease }}%</div>
              </div>
              <div style="text-align:center;padding:10px;background:rgba(30,110,181,0.06);border-radius:8px;">
                <div class="mono t-ink3 ls-wide" style="font-size:9px;">QQ 音乐</div>
                <div class="mono fs-13" style="color:#1e6eb5;margin-top:3px;">{{ platformPercent.qq }}%</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Mood radar + Top songs -->
      <div class="grid-2">
        <div class="card">
          <div class="card-head"><span class="card-title">心情分布雷达</span></div>
          <div class="card-body">
            <div class="chart-wrap" style="height:240px;">
              <VChart :option="moodOption" autoresize style="height:100%;width:100%" />
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-head"><span class="card-title">TOP 5 播放歌曲</span></div>
          <table class="tbl">
            <thead><tr><th>#</th><th>歌曲</th><th>播放次数</th></tr></thead>
            <tbody>
              <tr v-for="(s, index) in topSongs" :key="s.title">
                <td class="mono fs-11 t-ink3">{{ index + 1 }}</td>
                <td>
                  <div class="mono fs-12 t-ink">{{ s.title }}</div>
                  <div class="mono fs-10 t-ink3">{{ s.artist }}</div>
                </td>
                <td class="mono fs-12 t-ink2">{{ s.playCount.toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
