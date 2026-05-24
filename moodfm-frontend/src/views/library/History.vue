<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import MoodBlob from '@/components/common/MoodBlob.vue'
import LibraryStateView from '@/components/common/LibraryStateView.vue'
import { historyApi } from '@/api/history'

const router = useRouter()
const player = usePlayerStore()
const loading = ref(true)
const loadingMore = ref(false)
const error = ref<string | null>(null)

interface DayItem { t: string; n: string; a: string; coverUrl?: string; durationPlayed?: number; song?: any }
interface DayGroup { date: string; cn: string; items: DayItem[] }

const days = ref<DayGroup[]>([])
const page = ref(1)
const total = ref(0)
const scene = ref<string | null>(null)
const startDate = ref<string>('')
const endDate = ref<string>('')
const sentinel = ref<HTMLElement | null>(null)

const SCENE_OPTIONS = [
  { label: '全部', value: null },
  { label: '深夜', value: '深夜' },
  { label: '通勤', value: '通勤' },
  { label: '专注', value: '专注' },
  { label: '运动', value: '运动' },
  { label: '放松', value: '放松' },
]
const showFilter = ref(false)

const WEEK_DAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function formatDateHeader(iso: string): string {
  const d = new Date(iso)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}·${dd} ${WEEK_DAYS[d.getDay()]}`
}

function relativeDateLabel(iso: string): string {
  const today = new Date()
  const d = new Date(iso)
  today.setHours(0, 0, 0, 0)
  d.setHours(0, 0, 0, 0)
  const diffDays = Math.round((today.getTime() - d.getTime()) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays === 2) return '前天'
  return `${diffDays} 天前`
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function dayKey(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

function groupItems(items: any[]): DayGroup[] {
  const grouped: Record<string, DayGroup> = {}
  const order: string[] = []
  for (const item of items) {
    const key = dayKey(item.playedAt)
    if (!grouped[key]) {
      grouped[key] = {
        date: formatDateHeader(item.playedAt),
        cn: relativeDateLabel(item.playedAt),
        items: [],
      }
      order.push(key)
    }
    grouped[key].items.push({
      t: formatTime(item.playedAt),
      n: item.song?.title ?? '未知歌曲',
      a: item.song?.artist ?? '—',
      coverUrl: item.song?.coverUrl,
      durationPlayed: item.durationPlayed,
      song: item.song,
    })
  }
  return order.map(k => grouped[k])
}

let allItems: any[] = []

async function loadPage(p: number, append: boolean) {
  const params: Record<string, any> = { page: p, pageSize: 20 }
  if (scene.value) params.scene = scene.value
  if (startDate.value) params.startDate = startDate.value
  if (endDate.value) params.endDate = endDate.value
  const result = await historyApi.list(params as any)
  const items = (result as any).items ?? []
  total.value = (result as any).total ?? 0
  if (append) {
    allItems = [...allItems, ...items]
  } else {
    allItems = items
  }
  days.value = groupItems(allItems)
}

async function loadInitial() {
  loading.value = true
  error.value = null
  page.value = 1
  try {
    await loadPage(1, false)
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value) return
  if (allItems.length >= total.value) return
  loadingMore.value = true
  page.value++
  try {
    await loadPage(page.value, true)
  } catch {
    page.value--
  } finally {
    loadingMore.value = false
  }
}

function playSong(it: DayItem) {
  if (it.song) {
    player.setSong(it.song)
    player.setPlaying(true)
    router.push('/player')
  }
}

function selectScene(val: string | null) {
  scene.value = val
  showFilter.value = false
  loadInitial()
}

function clearFilters() {
  scene.value = null
  startDate.value = ''
  endDate.value = ''
  showFilter.value = false
  loadInitial()
}

function onDateChange() {
  loadInitial()
}

let observer: IntersectionObserver | null = null

onMounted(() => {
  loadInitial()
  observer = new IntersectionObserver((entries) => {
    if (entries[0]?.isIntersecting) loadMore()
  }, { threshold: 0.1 })
  if (sentinel.value) observer.observe(sentinel.value)
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:80px;">
    <div style="position:sticky;top:62px;z-index:5;background:var(--bg);
                border-bottom:1px solid var(--rule);">
      <div style="padding:22px 56px;display:flex;justify-content:space-between;align-items:center;">
        <div class="meta">SECTION VII · HISTORY · 历史记录</div>
        <div style="position:relative;">
          <button class="btn-pill" @click="showFilter = !showFilter">
            {{ scene ?? '场景' }}
          </button>
          <div v-if="showFilter" style="position:absolute;right:0;top:36px;background:var(--paper);border:1px solid var(--rule);
                      border-radius:12px;min-width:120px;z-index:10;overflow:hidden;">
            <button
              v-for="opt in SCENE_OPTIONS"
              :key="opt.label"
              style="display:block;width:100%;padding:10px 16px;border:none;background:transparent;
                     text-align:left;font-family:var(--serif-cn);font-size:14px;cursor:pointer;
                     border-bottom:1px solid var(--rule);"
              :style="{ color: scene === opt.value ? 'var(--ink)' : 'var(--ink-2)', fontWeight: scene === opt.value ? 600 : 400 }"
              @click="selectScene(opt.value)"
            >{{ opt.label }}</button>
          </div>
        </div>
      </div>
      <div class="filter-bar">
        <div class="filter-group">
          <label class="filter-label">日期范围</label>
          <input
            type="date"
            class="filter-date"
            :value="startDate"
            placeholder="开始日期"
            @change="startDate = ($event.target as HTMLInputElement).value; onDateChange()"
          />
          <span class="filter-sep">—</span>
          <input
            type="date"
            class="filter-date"
            :value="endDate"
            placeholder="结束日期"
            @change="endDate = ($event.target as HTMLInputElement).value; onDateChange()"
          />
        </div>
        <button
          v-if="scene || startDate || endDate"
          class="btn-pill filter-clear"
          @click="clearFilters()"
        >清除筛选</button>
      </div>
    </div>

    <div style="padding:40px 56px;">
      <div class="meta">A WEEK IN PLAYBACK</div>
      <h1 class="display" style="font-size:108px;margin:10px 0 0;">What you <em>heard</em>.</h1>
      <div class="display-cn" style="font-size:28px;color:var(--ink-2);margin-bottom:36px;">
        <template v-if="scene || startDate || endDate">
          <template v-if="scene">场景：{{ scene }}</template>
          <template v-if="startDate || endDate">
            {{ scene ? ' · ' : '' }}{{ startDate || '…' }} — {{ endDate || '…' }}
          </template>
        </template>
        <template v-else>这一周，你听过的每一首</template>
      </div>

      <LibraryStateView
        :loading="loading"
        :error="error"
        :empty="days.length === 0"
        :skeleton-rows="6"
        skeleton-layout="list"
        empty-title="还没有播放记录"
        empty-description="播放任意歌曲后会自动出现在这里，按日期分组。"
        empty-glyph="◷"
        @retry="loadInitial"
      >
        <div style="position:relative;padding-left:28px;">
          <div style="position:absolute;left:10px;top:8px;bottom:8px;width:1px;background:var(--rule);" />
          <div v-for="(d, di) in days" :key="di" style="margin-bottom:32px;">
            <div class="row" style="margin-left:-28px;gap:12px;margin-bottom:14px;">
              <div style="width:21px;height:21px;border-radius:50%;background:var(--ink);flex-shrink:0;margin-top:4px;" />
              <div>
                <div class="meta">{{ d.date.toUpperCase() }}</div>
                <div style="font-family:var(--serif-cn);font-size:24px;font-weight:500;margin-top:2px;">{{ d.cn }}</div>
              </div>
            </div>
            <div
              v-for="(it, ii) in d.items"
              :key="ii"
              class="row"
              style="gap:14px;padding:10px 0;border-bottom:1px dashed var(--rule);cursor:pointer;"
              @click="playSong(it)"
            >
              <span class="mono" style="font-size:11px;color:var(--ink-3);width:50px;flex-shrink:0;">{{ it.t }}</span>
              <img v-if="it.coverUrl" :src="it.coverUrl" style="width:40px;height:40px;border-radius:6px;object-fit:cover;flex-shrink:0;" />
              <MoodBlob v-else :size="40" :drift="false" geometry="blob" style="flex-shrink:0;" />
              <div style="flex:1;min-width:0;">
                <div class="row" style="gap:8px;align-items:baseline;flex-wrap:wrap;">
                  <span style="font-family:var(--serif-en);font-style:italic;font-size:18px;
                               white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ it.n }}</span>
                  <span style="font-size:12px;color:var(--ink-3);">· {{ it.a }}</span>
                </div>
              </div>
            </div>
          </div>

          <div ref="sentinel" style="height:1px;" />
          <div v-if="loadingMore" class="meta" style="margin-left:-28px;color:var(--ink-3);padding:12px 0;">加载中…</div>
          <div v-else-if="allItems.length >= total && total > 0" class="meta" style="margin-left:-28px;color:var(--ink-3);padding:12px 0;">已加载全部 {{ total }} 条记录</div>
        </div>
      </LibraryStateView>
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  padding: 10px 56px 14px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-label {
  font-family: var(--serif-cn);
  font-size: 13px;
  color: var(--ink-3);
  white-space: nowrap;
}
.filter-date {
  font-family: var(--serif-cn);
  font-size: 13px;
  padding: 6px 10px;
  border: 1px solid var(--rule);
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  outline: none;
  cursor: pointer;
}
.filter-date:focus {
  border-color: var(--ink-3);
}
.filter-sep {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--ink-3);
}
.filter-clear {
  font-size: 12px;
  padding: 5px 12px;
  margin-left: auto;
}
@media (max-width: 640px) {
  .filter-bar {
    padding: 10px 20px 14px;
    flex-direction: column;
    align-items: stretch;
  }
  .filter-group {
    flex-wrap: wrap;
  }
  .filter-clear {
    margin-left: 0;
    align-self: flex-end;
  }
}
</style>
