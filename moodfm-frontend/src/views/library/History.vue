<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { historyApi } from '@/api/history'

const router = useRouter()
const player = usePlayerStore()
const loading = ref(true)
const error = ref<string | null>(null)

interface DayItem { t: string; n: string; a: string; tag: string; mood: string }
interface DayGroup { date: string; cn: string; items: DayItem[] }

const days = ref<DayGroup[]>([])

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

onMounted(async () => {
  try {
    const result = await historyApi.list({ pageSize: 100 })
    const items = (result as any).items ?? result ?? []
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
        n: item.song.title,
        a: item.song.artist,
        tag: '音乐',
        mood: 'calm',
      })
    }
    days.value = order.map(k => grouped[k])
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:80px;">
    <div style="position:sticky;top:0;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;align-items:center;">
      <button class="btn-pill" @click="router.back()">← Home</button>
      <div class="meta">SECTION VII · HISTORY · 历史记录</div>
      <button class="btn-pill">筛选</button>
    </div>

    <div style="padding:40px 56px;">
      <div class="meta">A WEEK IN PLAYBACK</div>
      <h1 class="display" style="font-size:108px;margin:10px 0 0;">What you <em>heard</em>.</h1>
      <div class="display-cn" style="font-size:28px;color:var(--ink-2);margin-bottom:36px;">这一周，你听过的每一首</div>

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
            :data-mood="it.mood"
            class="row"
            style="gap:14px;padding:10px 0;border-bottom:1px dashed var(--rule);cursor:pointer;"
            @click="player.play()"
          >
            <span class="mono" style="font-size:11px;color:var(--ink-3);width:50px;flex-shrink:0;">{{ it.t }}</span>
            <MoodBlob :size="40" :drift="false" geometry="blob" style="flex-shrink:0;" />
            <div style="flex:1;min-width:0;">
              <div class="row" style="gap:8px;align-items:baseline;flex-wrap:wrap;">
                <span style="font-family:var(--serif-en);font-style:italic;font-size:18px;
                             white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ it.n }}</span>
                <span style="font-size:12px;color:var(--ink-3);">· {{ it.a }}</span>
              </div>
            </div>
            <span class="meta" style="padding:3px 10px;border-radius:999px;border:1px solid var(--rule);
                                      color:var(--ink-2);white-space:nowrap;flex-shrink:0;">· {{ it.tag }}</span>
          </div>
        </div>
        <button class="btn-pill" style="margin-left:-28px;">加载更多 ↓</button>
      </div>
    </div>
  </div>
</template>
