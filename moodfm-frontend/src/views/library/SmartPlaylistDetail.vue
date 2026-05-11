<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { playlistApi, type SmartPlaylistDetail } from '@/api/playlist'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref<string | null>(null)
const playlist = ref<SmartPlaylistDetail | null>(null)

const typeLabel: Record<string, string> = {
  'weekly-loves': '本周红心',
  'late-night': '深夜最爱',
  'energy': '高能时刻',
  'discoveries': '新发现',
}

onMounted(async () => {
  const type = route.params.type as string
  try {
    playlist.value = await playlistApi.getSmart(type)
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
      <button class="btn-pill" @click="router.back()">← 返回</button>
      <div class="meta">SMART · {{ typeLabel[$route.params.type as string] ?? '智能歌单' }}</div>
      <div></div>
    </div>

    <div style="padding:40px 56px;">
      <div v-if="loading" style="text-align:center;padding:60px 0;color:var(--ink-3);">加载中...</div>
      <div v-else-if="error" style="text-align:center;padding:60px 0;color:var(--ink-3);">{{ error }}</div>
      <template v-else-if="playlist">
        <h1 class="display" style="font-size:80px;margin:10px 0 8px;">{{ playlist.name }}</h1>
        <div class="display-cn" style="font-size:20px;color:var(--ink-2);margin-bottom:32px;">{{ playlist.songCount }} 首歌曲</div>

        <div v-if="playlist.songs.length === 0" style="text-align:center;padding:40px 0;color:var(--ink-3);">
          暂无符合条件的歌曲
        </div>
        <div v-else>
          <div
            v-for="(song, i) in playlist.songs"
            :key="i"
            style="display:flex;align-items:center;padding:14px 0;border-bottom:1px solid var(--rule);gap:16px;"
          >
            <div class="meta" style="width:32px;text-align:right;color:var(--ink-3);">
              {{ String(i + 1).padStart(2, '0') }}
            </div>
            <div style="flex:1;min-width:0;">
              <div style="font-family:var(--serif-cn);font-size:16px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                {{ song.title }}
              </div>
              <div class="meta" style="margin-top:2px;color:var(--ink-3);">{{ song.artist }}</div>
            </div>
            <div class="meta" style="color:var(--ink-3);white-space:nowrap;">{{ song.playCount }} 次</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
