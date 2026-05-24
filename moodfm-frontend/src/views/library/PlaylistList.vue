<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MoodBlob from '@/components/common/MoodBlob.vue'
import LibraryStateView from '@/components/common/LibraryStateView.vue'
import { usePlaylistStore } from '@/stores/playlist'

const router = useRouter()
const tab = ref('我的')
const store = usePlaylistStore()

const isEmpty = computed(() => store.lists.length === 0 && store.smartPlaylists.length === 0)

function reload() {
  store.load(true).catch(() => { /* error is captured on the store */ })
}

onMounted(() => {
  store.load().catch(() => { /* error is captured on the store */ })
})
</script>

<template>
  <div class="page-root">
    <div class="sticky-header">
      <div class="meta">SECTION V · LIBRARY · 我的歌单</div>
      <button class="btn-pill">+ 新建</button>
    </div>

    <div class="page-pad">
      <h1 class="display page-title">Your <em>library</em>.</h1>
      <div class="display-cn page-subtitle">跨平台合并 · 一处看完</div>

      <div class="row tabs-row">
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

      <LibraryStateView
        :loading="store.loading && store.lists.length === 0"
        :error="store.error"
        :empty="isEmpty"
        :skeleton-rows="8"
        skeleton-layout="grid"
        empty-title="还没有歌单"
        empty-description="登录后绑定网易云 / QQ 音乐，或在上方点击「+ 新建」即可创建第一个歌单。"
        empty-glyph="♪"
        @retry="reload"
      >
      <!-- Smart Playlists -->
      <div v-if="store.smartPlaylists.length" class="smart-section">
        <div class="meta section-label">SMART · 智能歌单</div>
        <div class="smart-grid">
          <div
            v-for="sp in store.smartPlaylists"
            :key="sp.type"
            class="smart-card"
            @mouseover="($event.currentTarget as HTMLElement).style.borderColor='var(--ink)'"
            @mouseleave="($event.currentTarget as HTMLElement).style.borderColor='var(--rule)'"
            @click="router.push('/playlists/smart/' + sp.type)"
          >
            <div class="smart-icon">
              <span v-if="sp.icon === 'heart'">&#10084;</span>
              <span v-else-if="sp.icon === 'moon'">&#9790;</span>
              <span v-else-if="sp.icon === 'zap'">&#9889;</span>
              <span v-else-if="sp.icon === 'compass'">&#9788;</span>
            </div>
            <div class="card-title">{{ sp.name }}</div>
            <div class="meta meta-sub">{{ sp.songCount }} 首</div>
          </div>
        </div>
      </div>

      <div class="meta section-label">YOUR LIBRARY · {{ store.lists.length }} 个歌单</div>
      <div class="library-grid">
        <div
          v-for="(l, i) in store.lists"
          :key="i"
          :data-mood="l.mood"
          class="library-card"
          @click="router.push('/playlists/' + l.id)"
        >
          <div class="cover-wrap">
            <MoodBlob :size="230" :drift="false" geometry="blob" />
            <div
              v-if="l.ai"
              class="meta badge badge-ai"
            >AI</div>
            <div
              class="meta badge badge-src"
            >{{ l.src }}</div>
          </div>
          <div class="meta card-index">№ {{ String(i + 1).padStart(2, '0') }} · {{ l.en }}</div>
          <div class="card-title card-title-spaced">{{ l.t }}</div>
          <div class="meta meta-sub">{{ l.n }} 首 · {{ l.m }} MIN</div>
        </div>
      </div>
      </LibraryStateView>
    </div>
  </div>
</template>

<style scoped>
/* Page shell */
.page-root {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 80px;
}

.sticky-header {
  position: sticky;
  top: 62px;
  z-index: 5;
  background: var(--bg);
  padding: 22px 56px;
  border-bottom: 1px solid var(--rule);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-pad {
  padding: 40px 56px;
}

/* Heading area */
.page-title {
  font-size: 120px;
  margin: 10px 0 8px;
}

.page-subtitle {
  font-size: 28px;
  color: var(--ink-2);
  margin-bottom: 24px;
}

/* Tabs */
.tabs-row {
  gap: 0;
  border-bottom: 1px solid var(--rule);
  margin-bottom: 28px;
  overflow-x: auto;
}

/* Section labels (meta) */
.section-label {
  margin-bottom: 14px;
}

/* Smart playlists */
.smart-section {
  margin-bottom: 36px;
}

.smart-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.smart-card {
  cursor: pointer;
  padding: 20px;
  border: 1px solid var(--rule);
  border-radius: 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.smart-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

/* Generic card title (shared by smart + library) */
.card-title {
  font-family: var(--serif-cn);
  font-size: 18px;
  font-weight: 500;
}

.card-title-spaced {
  margin-top: 4px;
  text-wrap: pretty;
}

.meta-sub {
  margin-top: 4px;
  color: var(--ink-3);
}

/* Library grid */
.library-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.library-card {
  cursor: pointer;
}

.cover-wrap {
  position: relative;
}

.badge {
  position: absolute;
  padding: 3px 8px;
  border-radius: 999px;
}

.badge-ai {
  top: 8px;
  left: 8px;
  background: var(--ink);
  color: var(--bg);
}

.badge-src {
  bottom: 8px;
  right: 8px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--ink);
}

.card-index {
  margin-top: 10px;
}
</style>
