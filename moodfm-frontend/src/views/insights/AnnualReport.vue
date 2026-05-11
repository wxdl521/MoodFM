<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { reportsApi } from '@/api/reports'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const now = new Date()
const year = ref(now.getFullYear())
const loading = ref(true)
const error = ref<string | null>(null)

const report = ref<{
  totalListeningTime: string; totalTracks: number; topGenre: string; topMood: string
  topArtists: Array<{ name: string; totalTime: string; playCount: number }>
  topSongs: Array<{ title: string; artist: string; playCount: number }>
  monthlyStats: Array<{ month: number; tracks: number; minutes: number }>
}>({
  totalListeningTime: '—', totalTracks: 0, topGenre: '—', topMood: '—',
  topArtists: [], topSongs: [], monthlyStats: [],
})

const barOption = computed(() => ({
  grid: { left: 40, right: 16, top: 16, bottom: 28 },
  tooltip: { trigger: 'axis', backgroundColor: 'var(--paper)', borderColor: 'var(--rule)' },
  xAxis: {
    type: 'category',
    data: ['1月','2月','3月','4月','5月','6月','7月','8月','9月','10月','11月','12月'],
    axisLine: { lineStyle: { color: 'var(--rule)' } },
    axisLabel: { color: 'var(--ink-3)', fontSize: 10 },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: 'var(--rule)', type: 'dashed' } },
    axisLabel: { color: 'var(--ink-3)', fontSize: 10 },
  },
  series: [{
    type: 'bar',
    data: report.value.monthlyStats.map(m => m.minutes),
    itemStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: 'var(--mood-a)' }, { offset: 1, color: 'var(--mood-c)' }],
      },
      borderRadius: [4, 4, 0, 0],
    },
    barWidth: '60%',
  }],
}))

onMounted(async () => {
  try {
    const data = await reportsApi.getAnnualReport(year.value)
    report.value = data
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:100px;">
    <NavBar />

    <div style="position:sticky;top:62px;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;align-items:center;">
      <RouterLink to="/insights" class="btn-pill" style="text-decoration:none;">← Insights</RouterLink>
      <div class="meta">ANNUAL REPORT · 年度报告</div>
      <div class="row" style="gap:6px;">
        <button class="btn-pill" @click="year--; loading=true;">{{ year - 1 }}</button>
        <button class="btn-pill active">{{ year }}</button>
      </div>
    </div>

    <div v-if="loading" class="meta" style="padding:80px 56px;color:var(--ink-3);">加载中…</div>
    <div v-else-if="error" class="meta" style="padding:80px 56px;color:var(--mood-b);">{{ error }}</div>
    <div v-else style="padding:40px 56px;">
      <div class="meta">YOUR YEAR IN MUSIC · {{ year }}</div>
      <h1 class="display" style="font-size:clamp(72px,10vw,144px);margin:12px 0 0;">{{ year }}.</h1>
      <div class="display-cn" style="font-size:28px;margin-top:8px;color:var(--ink-2);">
        这一年，你听了 <strong style="color:var(--ink);">{{ report.totalTracks }}</strong> 首歌。
      </div>

      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:1px;margin-top:40px;
                  background:var(--rule);border:1px solid var(--rule);">
        <div style="background:var(--bg);padding:28px;">
          <div class="meta">TOTAL LISTENING</div>
          <div class="display" style="font-size:52px;margin-top:8px;">{{ report.totalListeningTime }}</div>
          <div class="meta" style="margin-top:4px;color:var(--ink-3);">年度收听时长</div>
        </div>
        <div style="background:var(--bg);padding:28px;">
          <div class="meta">TOP GENRE</div>
          <div class="display" style="font-size:52px;margin-top:8px;">{{ report.topGenre }}</div>
          <div class="meta" style="margin-top:4px;color:var(--ink-3);">最常听流派</div>
        </div>
        <div style="background:var(--bg);padding:28px;">
          <div class="meta">TRACKS</div>
          <div class="display" style="font-size:52px;margin-top:8px;">{{ report.totalTracks }}</div>
          <div class="meta" style="margin-top:4px;color:var(--ink-3);">首歌曲</div>
        </div>
      </div>

      <div style="margin-top:56px;">
        <div class="meta">FIG. 01 · MONTHLY LISTENING · 月度收听</div>
        <div style="margin-top:16px;height:280px;border:1px solid var(--rule);border-radius:16px;padding:20px;background:var(--paper);">
          <VChart :option="barOption" autoresize style="width:100%;height:100%;" />
        </div>
      </div>

      <div style="margin-top:56px;display:grid;grid-template-columns:1fr 1fr;gap:48px;">
        <div>
          <div class="meta">TOP ARTISTS · 年度艺人</div>
          <div style="margin-top:16px;">
            <div v-for="(a,i) in report.topArtists.slice(0,5)" :key="a.name"
                 style="padding:12px 0;border-bottom:1px solid var(--rule);">
              <div class="row" style="gap:12px;align-items:baseline;">
                <span class="display" style="font-size:24px;color:var(--ink-3);width:32px;">0{{ i+1 }}</span>
                <div style="flex:1;">
                  <div style="font-family:var(--serif-cn);font-size:16px;font-weight:500;">{{ a.name }}</div>
                  <div class="meta" style="margin-top:2px;">{{ a.totalTime }} · {{ a.playCount }} 次</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div>
          <div class="meta">TOP SONGS · 年度歌曲</div>
          <div style="margin-top:16px;">
            <div v-for="(s,i) in report.topSongs.slice(0,5)" :key="s.title"
                 style="padding:12px 0;border-bottom:1px solid var(--rule);">
              <div class="row" style="gap:12px;align-items:baseline;">
                <span class="display" style="font-size:24px;color:var(--ink-3);width:32px;">0{{ i+1 }}</span>
                <div style="flex:1;">
                  <div style="font-family:var(--serif-en);font-style:italic;font-size:16px;">{{ s.title }}</div>
                  <div class="meta" style="margin-top:2px;">{{ s.artist }} · {{ s.playCount }} 次</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>
