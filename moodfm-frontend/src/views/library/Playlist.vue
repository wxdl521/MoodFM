<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { playlistApi } from '@/api/playlist'
import { songApi } from '@/api/song'
import type { Song } from '@/types'
import { toPlatform } from '@/utils/platform'

const router = useRouter()
const route = useRoute()
const player = usePlayerStore()
const loading = ref(true)
const error = ref<string | null>(null)
const playlist = ref<any>(null)

interface TrackItem {
  id: string
  platformSongId: string
  t: string
  a: string
  al: string
  durationSecs: number
  m: string
  mood: string
  liked: boolean
  coverUrl?: string
}

const tracks = ref<TrackItem[]>([])

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

const metaItems = computed(() => [
  { l: 'TRACKS',   v: String(playlist.value?.trackCount ?? tracks.value.length) },
  { l: 'DURATION', v: `${Math.round(tracks.value.length * 3.5)} MIN` },
  { l: 'UPDATED',  v: '每日同步' },
  { l: 'SOURCE',   v: playlist.value?.platform === 'netease' ? '网易云' : playlist.value?.platform === 'qqmusic' ? 'QQ 音乐' : '—' },
])

const activeIdx = ref(0)
const likedMap = ref<Record<number, boolean>>({})
const hovIdx = ref(-1)

onMounted(async () => {
  const id = route.params.id as string
  if (!id) { loading.value = false; return }
  try {
    const data = await playlistApi.getPlaylist(id)
    playlist.value = data
    tracks.value = (data.tracks ?? []).map(s => ({
      id: String(s.id),
      platformSongId: s.platformSongId ?? '',
      t: s.title,
      a: s.artist,
      al: s.album ?? '',
      durationSecs: s.duration,
      m: formatDuration(s.duration),
      mood: 'CALM',
      liked: false,
      coverUrl: s.coverUrl,
    }))
    likedMap.value = Object.fromEntries(tracks.value.map((_, i) => [i, false]))
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})

async function playSong(it: TrackItem) {
  try {
    const resp = await songApi.getAudioUrl(it.id)
    if (!resp?.url) {
      alert('该歌曲暂无可用播放地址')
      return
    }
    const song: Song = {
      id: it.id,
      title: it.t,
      artist: it.a,
      album: it.al || undefined,
      platform: toPlatform(playlist.value?.platform),
      platformSongId: it.platformSongId,
      duration: it.durationSecs,
      coverUrl: it.coverUrl ?? playlist.value?.coverUrl,
      audioUrl: resp.url,
    }
    player.setSong(song)
    player.setPlaying(true)
    router.push('/player')
  } catch (e: any) {
    console.error(e)
    if (e?.code === 404) {
      alert('该歌曲暂无可用播放地址')
    } else {
      alert('获取播放地址失败，请稍后重试')
    }
  }
}

async function playAll() {
  if (tracks.value.length === 0) return
  const ids = tracks.value.map(t => parseInt(t.id)).filter(n => !isNaN(n))
  try {
    const urlMap = await songApi.batchAudioUrls(ids)
    const queue: Song[] = tracks.value
      .filter(t => urlMap[t.id])
      .map(t => ({
        id: t.id,
        title: t.t,
        artist: t.a,
        album: t.al || undefined,
        platform: toPlatform(playlist.value?.platform),
        platformSongId: t.platformSongId,
        duration: t.durationSecs,
        coverUrl: t.coverUrl ?? playlist.value?.coverUrl,
        audioUrl: urlMap[t.id],
      }))
    if (queue.length === 0) {
      alert('该歌单暂无可播放的歌曲')
      return
    }
    player.setSong(queue[0])
    player.setQueue(queue.slice(1))
    player.setPlaying(true)
    router.push('/player')
  } catch (e: any) {
    console.error(e)
    alert('获取播放列表失败，请稍后重试')
  }
}
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:80px;">
    <div style="position:sticky;top:0;z-index:5;background:var(--bg);padding:18px 56px;
                border-bottom:1px solid var(--rule);display:flex;align-items:center;justify-content:space-between;">
      <button class="btn-pill" @click="router.back()">← 返回</button>
      <div class="meta">PLAYLIST · 歌单详情</div>
      <button class="btn-pill">···</button>
    </div>

    <div style="position:relative;overflow:hidden;padding:56px 56px 48px;border-bottom:1px solid var(--rule);">
      <div class="mood-blob drift" style="width:800px;height:800px;right:-150px;top:-220px;opacity:.45;" />
      <div style="position:relative;z-index:2;display:grid;grid-template-columns:320px 1fr;gap:48px;align-items:end;">
        <div>
          <div style="position:relative;width:300px;height:300px;border-radius:18px;overflow:hidden;
                      box-shadow:0 24px 60px rgba(0,0,0,.18);">
            <MoodBlob :size="300" :drift="true" geometry="blob" />
            <div style="position:absolute;left:14px;bottom:12px;color:#fff;text-shadow:0 1px 4px rgba(0,0,0,.4);">
              <div class="mono" style="font-size:10px;letter-spacing:.2em;opacity:.9;">NIGHT FLOAT · 010</div>
            </div>
          </div>
        </div>
        <div>
          <div class="meta">CURATED FOR YOU · 为你编排</div>
          <h1 class="display" style="font-size:108px;margin:12px 0 0;line-height:.95;">Night <em>float</em>.</h1>
          <div class="display-cn" style="font-size:34px;margin-top:4px;color:var(--ink-2);">夜浮 · 给加完班的你</div>
          <p style="margin-top:16px;font-size:14px;line-height:1.7;color:var(--ink-2);max-width:520px;font-family:var(--serif-cn);">
            一份漂在凌晨水面上的 ambient 合集——人声极少、低频温和、不会打扰任何思绪。AI 根据你过去 30 天 23:00 后的收听偏好编排。
          </p>
          <div class="row" style="margin-top:20px;gap:24px;color:var(--ink-3);">
            <div v-for="m in metaItems" :key="m.l">
              <div class="meta">{{ m.l }}</div>
              <div class="mono" style="font-size:14px;color:var(--ink);margin-top:2px;">{{ m.v }}</div>
            </div>
          </div>
          <div class="row" style="margin-top:24px;gap:10px;flex-wrap:wrap;">
            <button class="btn" style="height:48px;padding:0 22px;" @click="playAll()">▶ 播放 · PLAY ALL</button>
            <button class="btn-pill" style="height:48px;">♡ 收藏</button>
            <button class="btn-pill" style="height:48px;">↓ 下载</button>
            <button class="btn-pill" style="height:48px;">↗ 分享</button>
          </div>
        </div>
      </div>
    </div>

    <div style="padding:40px 56px;">
      <div class="between" style="margin-bottom:16px;">
        <div class="meta">TRACK LIST · 曲目</div>
        <div class="row" style="gap:8px;">
          <button class="btn-pill">排序 ↓</button>
          <button class="btn-pill">筛选</button>
        </div>
      </div>
      <div class="row" style="padding:10px 16px;color:var(--ink-3);border-bottom:1px solid var(--rule);">
        <div class="meta" style="width:32px;">#</div>
        <div class="meta" style="flex:2.5;">TITLE · 标题</div>
        <div class="meta" style="flex:1.5;">ALBUM · 专辑</div>
        <div class="meta" style="width:100px;">MOOD</div>
        <div style="width:40px;" />
        <div class="meta" style="width:60px;text-align:right;">TIME</div>
      </div>
      <div
        v-for="(t, i) in tracks"
        :key="i"
        class="row"
        :style="{
          padding: '12px 16px',
          borderRadius: '8px',
          cursor: 'pointer',
          background: i === activeIdx || i === hovIdx ? 'var(--bg-2)' : 'transparent',
          transition: 'background .1s',
        }"
        @mouseenter="hovIdx = i"
        @mouseleave="hovIdx = -1"
        @click="activeIdx = i; playSong(t)"
      >
        <div style="width:32px;font-family:var(--mono);font-size:13px;color:var(--ink-3);">
          {{ i === activeIdx ? '▸' : String(i + 1).padStart(2, '0') }}
        </div>
        <div style="flex:2.5;min-width:0;">
          <div style="font-family:var(--serif-en);font-style:italic;font-size:19px;">{{ t.t }}</div>
          <div class="meta" style="margin-top:2px;">{{ t.a }}</div>
        </div>
        <div style="flex:1.5;min-width:0;font-family:var(--serif-cn);font-size:13px;color:var(--ink-2);
                    white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ t.al }}</div>
        <div style="width:100px;">
          <span class="meta" style="padding:3px 8px;border:1px solid var(--rule);border-radius:999px;color:var(--ink-2);">{{ t.mood }}</span>
        </div>
        <div style="width:40px;text-align:center;">
          <button
            @click.stop="likedMap[i] = !likedMap[i]"
            style="background:none;border:none;cursor:pointer;font-size:18px;line-height:1;"
            :style="{ color: likedMap[i] ? 'var(--mood-b)' : 'var(--ink-3)' }"
          >{{ likedMap[i] ? '♥' : '♡' }}</button>
        </div>
        <div class="mono" style="width:60px;text-align:right;font-size:12px;color:var(--ink-3);">{{ t.m }}</div>
      </div>
    </div>
  </div>
</template>
