<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import { insightsApi } from '@/api/insights'

const MOOD_PALETTE: Record<string, [string, string]> = {
  calm:       ['#e9f0e6', '#9bb1a4'],
  dusk:       ['#f3d7c6', '#d28a9a'],
  blue:       ['#cbd6e8', '#6b7c9a'],
  energy:     ['#f8c87b', '#d96343'],
  focus:      ['#cfe1d8', '#6f9a87'],
  melancholy: ['#cbd6e8', '#6b7c9a'],
  energetic:  ['#f8c87b', '#d96343'],
  focused:    ['#cfe1d8', '#6f9a87'],
  empty:      ['transparent', 'transparent'],
}

const now      = new Date()
const year     = ref(now.getFullYear())
const month    = ref(now.getMonth() + 1)
const todayDay = now.getDate()

interface DayCell { day: number; future: boolean; empty: boolean; mood: string; tracks: number; minutes: number; isToday: boolean }

const days     = ref<DayCell[]>([])
const selected = ref(todayDay)
const viewMode = ref<'mood'|'volume'>('mood')
const loading  = ref(true)
const topTracks = ref<{ t: string; a: string; m: string }[]>([])

const sel = computed(() => days.value[selected.value - 1])

const lead = computed(() => new Date(year.value, month.value - 1, 1).getDay())

const cells = computed(() => {
  const c: (DayCell | null)[] = [...Array(lead.value).fill(null), ...days.value]
  while (c.length % 7) c.push(null)
  return c
})

const MOOD_LABELS: Record<string, string> = {
  calm: '平静 · CALM', dusk: '温柔 · TENDER', blue: '忧郁 · MELANCHOLY',
  energy: '高能 · ENERGETIC', focus: '专注 · FOCUSED',
  melancholy: '忧郁 · MELANCHOLY', energetic: '高能 · ENERGETIC', focused: '专注 · FOCUSED',
}
const DAY_CN = ['日','一','二','三','四','五','六']

const legendMoods = [
  { k:'calm',   l:'平静' },
  { k:'dusk',   l:'温柔' },
  { k:'blue',   l:'忧郁' },
  { k:'energy', l:'高能' },
  { k:'focus',  l:'专注' },
]

function daysInMonth(y: number, m: number) {
  return new Date(y, m, 0).getDate()
}

function buildGrid(apiData: any[]): DayCell[] {
  const total = daysInMonth(year.value, month.value)
  const sameMonth = now.getFullYear() === year.value && now.getMonth() + 1 === month.value
  const map = new Map<number, any>(apiData.map(d => [new Date(d.date).getDate(), d]))
  return Array.from({ length: total }, (_, i) => {
    const d = i + 1
    const future = sameMonth ? d > todayDay : (
      year.value > now.getFullYear() ||
      (year.value === now.getFullYear() && month.value > now.getMonth() + 1)
    )
    const entry = map.get(d)
    const hasMood = !!entry?.hasMood
    return {
      day: d,
      future,
      empty: future || !hasMood,
      mood: entry?.mood ?? 'empty',
      tracks: entry?.songCount ?? 0,
      minutes: Math.round((entry?.songCount ?? 0) * 3.5),
      isToday: sameMonth && d === todayDay,
    }
  })
}

async function loadDayDetail(day: number) {
  const dateStr = `${year.value}-${String(month.value).padStart(2,'0')}-${String(day).padStart(2,'0')}`
  try {
    const detail = await insightsApi.getDayDetail(dateStr)
    topTracks.value = ((detail as any).songs ?? []).slice(0, 4).map((s: any) => ({
      t: s.title ?? s.name ?? '—',
      a: s.artist ?? '—',
      m: '—',
    }))
  } catch {
    topTracks.value = []
  }
}

async function selectDay(day: number) {
  selected.value = day
  await loadDayDetail(day)
}

function cellBg(cell: DayCell) {
  if (viewMode.value === 'mood') return MOOD_PALETTE[cell.mood]?.[0] ?? 'transparent'
  const intensity = Math.min(1, cell.minutes / 80)
  return cell.empty ? 'transparent' : `rgba(33,30,28,${0.15 + intensity * 0.7})`
}

function cellBorder(cell: DayCell, isSel: boolean) {
  if (isSel || cell.isToday) return '2px solid var(--ink)'
  if (viewMode.value === 'mood' && !cell.empty) return `1.5px solid ${MOOD_PALETTE[cell.mood]?.[1] ?? 'var(--rule)'}`
  return '1px solid var(--rule)'
}

onMounted(async () => {
  try {
    const apiData = await insightsApi.getCalendar(year.value, month.value)
    days.value = buildGrid(apiData as any[])
    if (days.value[todayDay - 1] && !days.value[todayDay - 1].empty) {
      await loadDayDetail(todayDay)
    }
  } catch {
    days.value = buildGrid([])
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);">
    <NavBar />

    <div style="position:sticky;top:62px;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;align-items:center;justify-content:space-between;">
      <RouterLink to="/insights" class="btn-pill" style="text-decoration:none;">← Home</RouterLink>
      <div class="meta">SECTION V · MOOD CALENDAR · 心情日历</div>
      <div class="row" style="gap:6px;">
        <button :class="['btn-pill', viewMode==='mood'?'active':'']" @click="viewMode='mood'">心情</button>
        <button :class="['btn-pill', viewMode==='volume'?'active':'']" @click="viewMode='volume'">时长</button>
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1.4fr 1fr;">
      <div style="padding:40px 56px;border-right:1px solid var(--rule);">
        <div class="meta">YOUR EMOTIONAL MONTH · 2026</div>
        <h1 class="display" style="font-size:120px;margin:8px 0 0;line-height:.95;"><em>May.</em></h1>
        <div class="display-cn" style="font-size:28px;margin-top:4px;color:var(--ink-2);">五月 · 五月让人想睡。</div>

        <div class="row" style="margin-top:20px;gap:16px;flex-wrap:wrap;">
          <button class="btn-pill">‹</button>
          <div class="meta" style="min-width:120px;">MAY 2026 · 五月</div>
          <button class="btn-pill">›</button>
        </div>

        <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:8px;margin-top:22px;">
          <div
            v-for="d in ['SUN','MON','TUE','WED','THU','FRI','SAT']"
            :key="d"
            class="meta"
            style="text-align:center;color:var(--ink-3);"
          >{{ d }}</div>
        </div>

        <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:8px;margin-top:8px;">
          <template v-for="(cell,i) in cells" :key="i">
            <div v-if="!cell" style="aspect-ratio:1;min-height:56px;" />
            <button
              v-else
              :disabled="cell.future"
              :style="{
                aspectRatio:'1', minHeight:'56px', borderRadius:'6px',
                background: cellBg(cell),
                border: cellBorder(cell, cell.day===selected),
                color: cell.empty?'var(--ink-3)':'var(--ink)',
                cursor: cell.future?'default':'pointer',
                opacity: cell.future?0.35:1,
                padding:'6px', display:'flex', flexDirection:'column',
                justifyContent:'space-between', alignItems:'flex-start',
                fontFamily:'var(--mono)',
                transform: cell.day===selected ? 'scale(1.06)' : 'scale(1)',
                transition: 'transform .15s',
              }"
              @click="!cell.future && selectDay(cell.day)"
            >
              <span :style="{ fontSize:'14px', fontWeight: cell.isToday?700:500 }">{{ cell.day }}</span>
              <span v-if="!cell.empty" style="font-size:9px;opacity:.7;">{{ cell.tracks }}♪</span>
            </button>
          </template>
        </div>

        <div style="margin-top:28px;">
          <div class="meta" style="margin-bottom:10px;">LEGEND · {{ viewMode==='mood'?'按心情上色':'按收听时长' }}</div>
          <div v-if="viewMode==='mood'" class="row" style="gap:14px;flex-wrap:wrap;">
            <div v-for="m in legendMoods" :key="m.k" class="row" style="gap:8px;align-items:center;">
              <div :style="{ width:'14px',height:'14px',borderRadius:'4px',
                             background:MOOD_PALETTE[m.k][0],
                             border:`1.5px solid ${MOOD_PALETTE[m.k][1]}` }" />
              <span :style="{ fontFamily:'var(--serif-cn)',fontSize:'13px' }">{{ m.l }}</span>
            </div>
          </div>
          <div v-else class="row" style="gap:6px;align-items:center;">
            <span class="meta">LESS</span>
            <div
              v-for="o in [0.15,0.35,0.55,0.75,0.95]"
              :key="o"
              :style="{ width:'18px',height:'18px',borderRadius:'4px',background:`rgba(33,30,28,${o})` }"
            />
            <span class="meta">MORE</span>
          </div>
        </div>
      </div>

      <div style="padding:40px 40px;background:var(--bg-2);min-height:calc(100vh - 73px);">
        <template v-if="sel && !sel.empty">
          <div class="meta">SELECTED · 选中</div>
          <div class="row" style="margin-top:10px;align-items:baseline;gap:14px;">
            <h2 class="display" style="font-size:80px;margin:0;">{{ sel.day }}</h2>
            <div>
              <div :style="{ fontFamily:'var(--serif-cn)',fontSize:'18px' }">五月 · 周{{ DAY_CN[(sel.day+4)%7] }}</div>
              <div class="meta" style="margin-top:4px;">MAY {{ String(sel.day).padStart(2,'0') }} · 2026</div>
            </div>
          </div>

          <div style="margin-top:20px;padding:22px;background:var(--paper);border-radius:16px;border:1px solid var(--rule);">
            <div class="row" style="gap:16px;align-items:center;">
              <div :style="{
                width:'76px',height:'76px',borderRadius:'50%',flexShrink:0,
                background:`radial-gradient(circle at 30% 30%,${MOOD_PALETTE[sel.mood][0]},${MOOD_PALETTE[sel.mood][1]})`,
                boxShadow:'0 6px 24px rgba(0,0,0,.06)',
              }" />
              <div>
                <div class="meta">DOMINANT MOOD</div>
                <div :style="{ fontFamily:'var(--serif-en)',fontStyle:'italic',fontSize:'28px',marginTop:'4px' }">
                  {{ MOOD_LABELS[sel.mood]?.split(' · ')[1] }}
                </div>
                <div :style="{ fontFamily:'var(--serif-cn)',fontSize:'16px',color:'var(--ink-2)' }">
                  {{ MOOD_LABELS[sel.mood]?.split(' · ')[0] }}
                </div>
              </div>
            </div>
            <div class="row" style="margin-top:18px;padding-top:16px;border-top:1px dashed var(--rule);gap:28px;">
              <div><div class="meta">TRACKS</div><div class="mono" style="font-size:18px;margin-top:4px;">{{ sel.tracks }}</div></div>
              <div><div class="meta">MINUTES</div><div class="mono" style="font-size:18px;margin-top:4px;">{{ sel.minutes }}</div></div>
              <div><div class="meta">SESSIONS</div><div class="mono" style="font-size:18px;margin-top:4px;">{{ Math.max(1,Math.floor(sel.tracks/4)) }}</div></div>
            </div>
          </div>

          <div style="margin-top:24px;">
            <div class="meta" style="margin-bottom:12px;">TOP TRACKS · 当天最常听</div>
            <div
              v-for="(t,i) in topTracks"
              :key="t.t"
              class="row"
              :style="{ gap:'12px',padding:'10px 0',borderBottom:i<topTracks.length-1?'1px solid var(--rule)':'none' }"
            >
              <span class="mono" style="color:var(--ink-3);width:22px;flex-shrink:0;">0{{ i+1 }}</span>
              <div style="flex:1;min-width:0;">
                <div :style="{ fontFamily:'var(--serif-en)',fontStyle:'italic',fontSize:'18px' }">{{ t.t }}</div>
                <div class="meta" style="margin-top:2px;">{{ t.a }}</div>
              </div>
              <div class="mono" style="color:var(--ink-3);flex-shrink:0;">{{ t.m }}</div>
            </div>
          </div>
          <button class="btn" style="margin-top:24px;width:100%;height:46px;">重听这一天 ▶</button>
        </template>
        <div v-else class="meta" style="text-align:center;padding:60px;color:var(--ink-3);">
          · NO DATA · 这一天没有听
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>
