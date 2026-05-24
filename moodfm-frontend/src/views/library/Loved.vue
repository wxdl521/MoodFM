<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'
import { useAudioPlayer } from '@/composables/useAudioPlayer'
import MoodBlob from '@/components/common/MoodBlob.vue'
import LibraryStateView from '@/components/common/LibraryStateView.vue'
import { playlistApi } from '@/api/playlist'
import type { Song } from '@/types'

const router = useRouter()
const player = usePlayerStore()
const audio = useAudioPlayer()
const src = ref('全部')
const loading = ref(true)
const error = ref<string | null>(null)

interface SongItem { t: string; a: string; al: string; d: string; src: string; mood: string; when: string }

const songs = ref<SongItem[]>([])
const fullSongs = ref<Song[]>([])

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function toPlatformLabel(p: string): string {
  return p === 'netease' ? '网易云' : p === 'qqmusic' ? 'QQ' : p
}

function playSong(index: number) {
  const song = fullSongs.value[index]
  if (!song) return
  player.setSong(song)
  if (song.audioUrl) {
    audio.load(song.audioUrl).then(() => audio.play())
  }
  router.push('/player')
}

function playRandom() {
  if (fullSongs.value.length === 0) return
  const idx = Math.floor(Math.random() * fullSongs.value.length)
  playSong(idx)
}

async function loadLoved() {
  loading.value = true
  error.value = null
  try {
    const data = await playlistApi.loved()
    fullSongs.value = data
    songs.value = data.map(s => ({
      t: s.title,
      a: s.artist,
      al: s.album ?? '',
      d: formatDuration((s as any).durationSeconds ?? s.duration),
      src: toPlatformLabel((s as any).platform ?? 'netease'),
      mood: 'calm',
      when: '',
    }))
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadLoved()
})

const filtered = computed(() =>
  src.value === '全部' ? songs.value : songs.value.filter(s => s.src === src.value || s.src === '两端均有')
)

function filteredIndex(displayIndex: number): number {
  if (src.value === '全部') return displayIndex
  const song = filtered.value[displayIndex]
  return songs.value.indexOf(song)
}
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:80px;">
    <div style="position:sticky;top:0;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;align-items:center;">
      <button class="btn-pill" @click="router.back()">← Home</button>
      <div class="meta">SECTION VI · LOVED · 我的红心</div>
      <div style="width:60px;" />
    </div>

    <div style="padding:40px 56px;display:grid;grid-template-columns:1.1fr 2fr;gap:48px;">
      <div>
        <div class="meta">A LIVING DOCUMENT · 持续累积</div>
        <h1 class="display" style="font-size:120px;margin:10px 0 0;"><em>♥</em> Loved.</h1>
        <div class="display-cn" style="font-size:30px;margin-top:6px;color:var(--ink-2);">红心歌单 · 跨平台合并</div>

        <div style="margin-top:28px;padding:18px;border:1px solid var(--rule);border-radius:14px;background:var(--paper);">
          <div class="row" style="gap:8px;align-items:baseline;">
            <div class="display" style="font-size:40px;">{{ songs.length }}</div>
            <div class="meta">SONGS · 首</div>
          </div>
          <div class="meta" style="margin-top:10px;color:var(--ink-3);">· 网易云 86 · QQ 音乐 22 · 重叠 16</div>
          <hr class="rule" style="margin:16px 0;" />
          <div class="row" style="gap:8px;">
            <button class="btn" style="flex:1;" @click="playRandom">随机播放 ▶</button>
            <button class="btn-pill">导出</button>
          </div>
        </div>

        <div style="margin-top:22px;">
          <div class="meta" style="margin-bottom:8px;">来源 / SOURCE</div>
          <div style="display:flex;flex-wrap:wrap;gap:6px;">
            <button
              v-for="s in ['全部','网易云','QQ','两端均有']"
              :key="s"
              :class="['btn-pill', src === s ? 'active' : '']"
              @click="src = s"
            >{{ s }}</button>
          </div>
        </div>
      </div>

      <div>
        <LibraryStateView
          :loading="loading"
          :error="error"
          :empty="filtered.length === 0"
          :skeleton-rows="6"
          skeleton-layout="list"
          empty-title="还没有红心歌曲"
          empty-description="在播放器里点♥就能把喜欢的歌加进来，多端的红心会自动合并。"
          empty-glyph="♥"
          @retry="loadLoved"
        >
          <div
            v-for="(s, i) in filtered"
            :key="i"
            :data-mood="s.mood"
            class="row"
            style="gap:14px;padding:14px 4px;border-bottom:1px solid var(--rule);cursor:pointer;"
            @click="playSong(filteredIndex(i))"
          >
            <div class="mono" style="font-size:11px;color:var(--ink-3);width:26px;flex-shrink:0;">{{ String(i + 1).padStart(2, '0') }}</div>
            <MoodBlob :size="56" :drift="false" geometry="blob" style="flex-shrink:0;" />
            <div style="flex:1;min-width:0;">
              <div class="row" style="gap:8px;align-items:baseline;">
                <span style="font-family:var(--serif-en);font-style:italic;font-size:20px;
                             white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ s.t }}</span>
                <span style="font-size:12px;color:var(--ink-3);white-space:nowrap;">· {{ s.a }}</span>
              </div>
              <div class="meta" style="margin-top:2px;color:var(--ink-3);">{{ s.al }}</div>
            </div>
            <span class="meta" style="color:var(--ink-3);min-width:70px;">{{ s.src }}</span>
            <span class="mono" style="font-size:12px;color:var(--ink-2);width:50px;text-align:right;flex-shrink:0;">{{ s.d }}</span>
            <span style="color:var(--mood-b);font-size:18px;flex-shrink:0;">♥</span>
          </div>
        </LibraryStateView>
      </div>
    </div>
  </div>
</template>
