<template>
  <div style="min-height: 100vh; background: var(--bg); padding-bottom: 100px;">

    <div class="page-pad">
      <div class="meta">SECTION · BLACKLIST · 黑名单</div>
      <h1 class="display" style="font-size: clamp(52px, 7.5vw, 108px); margin: 10px 0 0;">
        Black<em>list</em>.
      </h1>
      <div class="display-cn" style="font-size: clamp(18px, 2vw, 26px); margin-top: 6px; color: var(--ink-2); margin-bottom: 8px;">
        被屏蔽的歌曲
      </div>
      <p style="font-size: 14px; color: var(--ink-3); line-height: 1.6; margin: 0 0 36px; max-width: 560px;">
        被加入黑名单的歌曲不会在电台中出现。
      </p>

      <div v-if="loading" style="padding: 24px 0;">
        <div v-for="i in 5" :key="i" class="skeleton-row" />
      </div>

      <div v-else-if="error" class="meta" style="color: var(--ink-3); padding: 48px 0;">
        加载失败 · {{ error }}
      </div>

      <div v-else-if="items.length === 0" class="empty-state">
        <div class="display" style="font-size: 56px; opacity: 0.18;">∅</div>
        <div class="meta" style="margin-top: 16px; color: var(--ink-3);">黑名单为空 · 电台将正常推荐所有歌曲</div>
      </div>

      <div v-else>
        <div class="list-header row" style="padding: 10px 16px; color: var(--ink-3); border-bottom: 1px solid var(--rule);">
          <div class="meta" style="flex: 2.5;">SONG · 歌曲</div>
          <div class="meta" style="flex: 1.5;">ARTIST · 艺术家</div>
          <div class="meta" style="width: 120px;">ADDED · 加入时间</div>
          <div class="meta" style="width: 72px; text-align: right;">操作</div>
        </div>

        <div
          v-for="item in items"
          :key="item.id"
          class="list-row row"
        >
          <div style="flex: 2.5; min-width: 0;">
            <div style="font-family: var(--serif-en); font-size: 17px; font-style: italic; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{{ item.label ?? item.value }}</div>
          </div>
          <div style="flex: 1.5; min-width: 0; font-family: var(--serif-cn); font-size: 13px; color: var(--ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            {{ item.artistLabel ?? '—' }}
          </div>
          <div class="mono" style="width: 120px; font-size: 11px; color: var(--ink-3);">{{ item.addedAt }}</div>
          <div style="width: 72px; text-align: right;">
            <button
              class="remove-btn btn-pill"
              :disabled="removingId === item.id"
              @click="removeItem(item)"
            >{{ removingId === item.id ? '…' : '移除' }}</button>
          </div>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import { blacklistApi } from '@/api/blacklist'

interface BlacklistItem {
  id: string
  type: string
  value: string
  label?: string
  artistLabel?: string
  addedAt?: string
}

const items = ref<BlacklistItem[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const removingId = ref<string | null>(null)

function formatDate(iso?: string) {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`
}

async function removeItem(item: BlacklistItem) {
  removingId.value = item.id
  try {
    await blacklistApi.remove(item.id)
    items.value = items.value.filter(i => i.id !== item.id)
  } catch {}
  removingId.value = null
}

onMounted(async () => {
  try {
    const res = await blacklistApi.getAll()
    const raw: any[] = (res as any[]) ?? []
    items.value = raw.map(r => ({
      id: r.id ?? r._id ?? String(Math.random()),
      type: r.type ?? 'song',
      value: r.value ?? '',
      label: r.label ?? r.songName ?? r.value,
      artistLabel: r.artistName ?? r.artist ?? undefined,
      addedAt: formatDate(r.createdAt ?? r.addedAt),
    }))
  } catch (e: any) {
    error.value = e?.message ?? '未知错误'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-pad {
  padding: 40px 56px;
}

@media (max-width: 768px) {
  .page-pad {
    padding: 24px 18px;
  }
}

.list-header {
  display: flex;
}

@media (max-width: 768px) {
  .list-header {
    display: none;
  }
}

.list-row {
  padding: 14px 16px;
  border-bottom: 1px solid var(--rule);
  border-radius: 8px;
  transition: background 0.12s;
}

.list-row:hover {
  background: var(--bg-2);
}

.remove-btn {
  font-size: 12px;
  padding: 4px 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.remove-btn:not(:disabled):hover {
  background: var(--ink);
  color: var(--bg);
  border-color: var(--ink);
}

.remove-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-state {
  padding: 80px 0;
  text-align: center;
}

.skeleton-row {
  height: 56px;
  border-radius: 8px;
  background: var(--bg-2);
  margin-bottom: 4px;
  animation: pulse 1.6s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.6; }
}
</style>
