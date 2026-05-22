<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { playlistApi, type SmartPlaylistSummary } from '@/api/playlist'

const router = useRouter()
const tab = ref('我的')
const loading = ref(true)
const error = ref<string | null>(null)

interface PlaylistItem { id: string; t: string; en: string; n: number; m: number; src: string; mood: string; desc: string; ai: boolean }

const lists = ref<PlaylistItem[]>([])
const smartPlaylists = ref<SmartPlaylistSummary[]>([])

function toPlatformLabel(p: string): string {
  return p === 'netease' ? '网易云' : p === 'qqmusic' ? 'QQ' : p
}

onMounted(async () => {
  try {
    const [playlistData, smartData] = await Promise.all([
      playlistApi.list(),
      playlistApi.listSmart().catch(() => []),
    ])
    lists.value = playlistData.map(pl => ({
      id: pl.id,
      t: pl.name,
      en: pl.name.toUpperCase().slice(0, 16),
      n: pl.trackCount,
      m: Math.round(pl.trackCount * 3.5),
      src: toPlatformLabel(pl.platform),
      mood: 'calm',
      desc: pl.description ?? '',
      ai: false,
    }))
    smartPlaylists.value = smartData
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:80px;">
    <div style="position:sticky;top:62px;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;align-items:center;">
      <div class="meta">SECTION V · LIBRARY · 我的歌单</div>
      <button class="btn-pill">+ 新建</button>
    </div>

    <div style="padding:40px 56px;">
      <h1 class="display" style="font-size:120px;margin:10px 0 8px;">Your <em>library</em>.</h1>
      <div class="display-cn" style="font-size:28px;color:var(--ink-2);margin-bottom:24px;">跨平台合并 · 一处看完</div>

      <div class="row" style="gap:0;border-bottom:1px solid var(--rule);margin-bottom:28px;overflow-x:auto;">
        <button
          v-for="t in ['我的','收藏','AI 生成','网易云','QQ 音乐']"
          :key="t"
          :style="{
            background: 'transparent',
            border: 'none',
            padding: '12px 16px',
            cursor: 'pointer',
            fontFamily: 'var(--serif-cn)',
            fontSize: '15px',
            whiteSpace: 'nowrap',
            color: tab === t ? 'var(--ink)' : 'var(--ink-3)',
            borderBottom: tab === t ? '2px solid var(--ink)' : '2px solid transparent',
            marginBottom: '-1px'
          }"
          @click="tab = t"
        >{{ t }}</button>
      </div>

      <!-- Smart Playlists -->
      <div v-if="smartPlaylists.length" style="margin-bottom:36px;">
        <div class="meta" style="margin-bottom:14px;">SMART · 智能歌单</div>
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;">
          <div
            v-for="sp in smartPlaylists"
            :key="sp.type"
            style="cursor:pointer;padding:20px;border:1px solid var(--rule);border-radius:12px;
                   transition:border-color .2s,box-shadow .2s;"
            @mouseover="($event.currentTarget as HTMLElement).style.borderColor='var(--ink)'"
            @mouseleave="($event.currentTarget as HTMLElement).style.borderColor='var(--rule)'"
            @click="router.push('/playlists/smart/' + sp.type)"
          >
            <div style="font-size:28px;margin-bottom:8px;">
              <span v-if="sp.icon === 'heart'">&#10084;</span>
              <span v-else-if="sp.icon === 'moon'">&#9790;</span>
              <span v-else-if="sp.icon === 'zap'">&#9889;</span>
              <span v-else-if="sp.icon === 'compass'">&#9788;</span>
            </div>
            <div style="font-family:var(--serif-cn);font-size:18px;font-weight:500;">{{ sp.name }}</div>
            <div class="meta" style="margin-top:4px;color:var(--ink-3);">{{ sp.songCount }} 首</div>
          </div>
        </div>
      </div>

      <div class="meta" style="margin-bottom:14px;">YOUR LIBRARY · {{ lists.length }} 个歌单</div>
      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:20px;">
        <div
          v-for="(l, i) in lists"
          :key="i"
          :data-mood="l.mood"
          style="cursor:pointer;"
          @click="router.push('/playlists/' + l.id)"
        >
          <div style="position:relative;">
            <MoodBlob :size="230" :drift="false" geometry="blob" />
            <div
              v-if="l.ai"
              class="meta"
              style="position:absolute;top:8px;left:8px;padding:3px 8px;
                     background:var(--ink);color:var(--bg);border-radius:999px;"
            >AI</div>
            <div
              class="meta"
              style="position:absolute;bottom:8px;right:8px;padding:3px 8px;
                     background:rgba(255,255,255,.86);color:var(--ink);border-radius:999px;"
            >{{ l.src }}</div>
          </div>
          <div class="meta" style="margin-top:10px;">№ {{ String(i + 1).padStart(2, '0') }} · {{ l.en }}</div>
          <div style="font-family:var(--serif-cn);font-size:18px;font-weight:500;margin-top:4px;text-wrap:pretty;">{{ l.t }}</div>
          <div class="meta" style="margin-top:4px;color:var(--ink-3);">{{ l.n }} 首 · {{ l.m }} MIN</div>
        </div>
      </div>
    </div>
  </div>
</template>
