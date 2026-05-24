<script setup lang="ts">
import { ref, onMounted } from 'vue'
import '@/assets/styles/admin.css'
import { blacklistAdminApi, GlobalBlacklist } from '@/api/admin'
import { logger } from '@/utils/logger'

const tab = ref<'artists' | 'songs' | 'keywords'>('artists')
const search = ref('')
const showAdd = ref(false)
const loading = ref(false)
const addForm = ref({ name: '', artist: '', reason: '', scope: '标题' })

const artists = ref<GlobalBlacklist[]>([])
const songs = ref<GlobalBlacklist[]>([])
const keywords = ref<GlobalBlacklist[]>([])

function toast(msg: string, type = 'ok') { (window as any).__adminToast?.(msg, type) }

async function fetchTab(type: 'artist' | 'song' | 'keyword', q?: string) {
  loading.value = true
  try {
    const data = await blacklistAdminApi.list(type, q || undefined)
    if (type === 'artist') artists.value = data
    else if (type === 'song') songs.value = data
    else keywords.value = data
  } catch (err) {
    logger.warn('admin:music-fetch', err)
    toast('操作失败，请重试', 'warn')
  } finally {
    loading.value = false
  }
}

function tabType(): 'artist' | 'song' | 'keyword' {
  if (tab.value === 'artists') return 'artist'
  if (tab.value === 'songs') return 'song'
  return 'keyword'
}

onMounted(() => fetchTab('artist'))

function onSearchInput() {
  fetchTab(tabType(), search.value)
}

async function removeArtist(id: number) {
  try {
    await blacklistAdminApi.remove(id)
    artists.value = artists.value.filter(a => a.id !== id)
    toast('已从黑名单移除')
  } catch (err) {
    logger.warn('admin:music-remove-artist', err)
    toast('操作失败，请重试', 'warn')
  }
}

async function removeSong(id: number) {
  try {
    await blacklistAdminApi.remove(id)
    songs.value = songs.value.filter(s => s.id !== id)
    toast('已从黑名单移除')
  } catch (err) {
    logger.warn('admin:music-remove-song', err)
    toast('操作失败，请重试', 'warn')
  }
}

async function removeKeyword(id: number) {
  try {
    await blacklistAdminApi.remove(id)
    keywords.value = keywords.value.filter(k => k.id !== id)
    toast('已从黑名单移除')
  } catch (err) {
    logger.warn('admin:music-remove-keyword', err)
    toast('操作失败，请重试', 'warn')
  }
}

function openAdd() { addForm.value = { name: '', artist: '', reason: '', scope: '标题' }; showAdd.value = true }
function closeAdd() { showAdd.value = false }

async function submitAdd() {
  if (!addForm.value.name) return
  try {
    if (tab.value === 'artists') {
      await blacklistAdminApi.add({ type: 'artist', value: addForm.value.name, reason: addForm.value.reason || '手动添加', addedBy: 'admin' })
    } else if (tab.value === 'songs') {
      await blacklistAdminApi.add({ type: 'song', value: addForm.value.name, artist: addForm.value.artist || undefined, reason: addForm.value.reason || '手动添加', addedBy: 'admin' })
    } else {
      await blacklistAdminApi.add({ type: 'keyword', value: addForm.value.name, scope: addForm.value.scope, reason: addForm.value.reason || '手动添加', addedBy: 'admin' })
    }
    toast('已添加至黑名单')
    closeAdd()
    await fetchTab(tabType(), search.value)
  } catch (err) {
    logger.warn('admin:music-add', err)
    toast('操作失败，请重试', 'warn')
  }
}

function switchTab(t: 'artists' | 'songs' | 'keywords') {
  tab.value = t
  search.value = ''
  fetchTab(t === 'artists' ? 'artist' : t === 'songs' ? 'song' : 'keyword')
}
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">Music</span>
        <span class="topbar-title-cn">音乐管理</span>
      </div>
      <div class="topbar-actions">
        <button class="btn btn-primary btn-sm" @click="openAdd">+ 添加黑名单</button>
      </div>
    </div>

    <div class="content-area">
      <div class="alert info">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        全局黑名单对所有用户生效。用户个人黑名单在各自账号设置中管理。
      </div>

      <!-- Tabs -->
      <div class="tabs">
        <div :class="['tab-item', tab === 'artists'  ? 'active' : '']" @click="switchTab('artists')">
          歌手黑名单 <span class="tab-count">{{ artists.length }}</span>
        </div>
        <div :class="['tab-item', tab === 'songs'    ? 'active' : '']" @click="switchTab('songs')">
          歌曲黑名单 <span class="tab-count">{{ songs.length }}</span>
        </div>
        <div :class="['tab-item', tab === 'keywords' ? 'active' : '']" @click="switchTab('keywords')">
          关键词过滤 <span class="tab-count">{{ keywords.length }}</span>
        </div>
      </div>

      <!-- Search -->
      <div class="toolbar">
        <div class="search-wrap">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="7"/><path d="M21 21l-4-4"/>
          </svg>
          <input class="search-input" v-model="search" placeholder="搜索…" @input="onSearchInput" />
        </div>
      </div>

      <!-- Artists -->
      <div class="card" v-if="tab === 'artists'">
        <table class="tbl">
          <thead><tr><th>歌手</th><th>加入原因</th><th>添加人</th><th>添加时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5"><div class="empty"><div class="empty-title">加载中…</div></div></td>
            </tr>
            <tr v-else-if="artists.length === 0">
              <td colspan="5"><div class="empty"><div class="empty-title">No entries.</div></div></td>
            </tr>
            <tr v-for="a in artists" :key="a.id">
              <td class="mono fs-12 t-ink">{{ a.value }}</td>
              <td class="mono fs-11 t-ink3">{{ a.reason }}</td>
              <td><span class="badge" :class="a.addedBy === 'system' ? 'info' : 'neutral'">{{ a.addedBy }}</span></td>
              <td class="mono fs-11 t-ink3">{{ a.createdAt.slice(0, 10) }}</td>
              <td><button class="btn btn-danger btn-sm" @click="removeArtist(a.id)">移除</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Songs -->
      <div class="card" v-if="tab === 'songs'">
        <table class="tbl">
          <thead><tr><th>歌曲</th><th>艺人</th><th>加入原因</th><th>添加人</th><th>添加时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6"><div class="empty"><div class="empty-title">加载中…</div></div></td>
            </tr>
            <tr v-else-if="songs.length === 0">
              <td colspan="6"><div class="empty"><div class="empty-title">No entries.</div></div></td>
            </tr>
            <tr v-for="s in songs" :key="s.id">
              <td class="mono fs-12 t-ink">{{ s.value }}</td>
              <td class="mono fs-11 t-ink3">{{ s.artist }}</td>
              <td class="mono fs-11 t-ink3">{{ s.reason }}</td>
              <td><span class="badge" :class="s.addedBy === 'system' ? 'info' : 'neutral'">{{ s.addedBy }}</span></td>
              <td class="mono fs-11 t-ink3">{{ s.createdAt.slice(0, 10) }}</td>
              <td><button class="btn btn-danger btn-sm" @click="removeSong(s.id)">移除</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Keywords -->
      <div class="card" v-if="tab === 'keywords'">
        <table class="tbl">
          <thead><tr><th>关键词</th><th>过滤范围</th><th>添加人</th><th>添加时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5"><div class="empty"><div class="empty-title">加载中…</div></div></td>
            </tr>
            <tr v-else-if="keywords.length === 0">
              <td colspan="5"><div class="empty"><div class="empty-title">No entries.</div></div></td>
            </tr>
            <tr v-for="k in keywords" :key="k.id">
              <td class="mono fs-12 t-ink">{{ k.value }}</td>
              <td><span class="badge neutral">{{ k.scope }}</span></td>
              <td><span class="badge" :class="k.addedBy === 'system' ? 'info' : 'neutral'">{{ k.addedBy }}</span></td>
              <td class="mono fs-11 t-ink3">{{ k.createdAt.slice(0, 10) }}</td>
              <td><button class="btn btn-danger btn-sm" @click="removeKeyword(k.id)">移除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Add modal -->
    <div class="modal-overlay" v-if="showAdd" @click.self="closeAdd">
      <div class="modal">
        <div class="modal-head">
          <span class="modal-title">添加至黑名单</span>
          <button class="modal-close" @click="closeAdd">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">{{ tab === 'artists' ? '歌手名称' : tab === 'songs' ? '歌曲名称' : '关键词' }}</label>
            <input class="form-input" v-model="addForm.name" placeholder="输入名称…" />
          </div>
          <div class="form-group" v-if="tab === 'songs'">
            <label class="form-label">艺人</label>
            <input class="form-input" v-model="addForm.artist" placeholder="可选" />
          </div>
          <div class="form-group" v-if="tab === 'keywords'">
            <label class="form-label">过滤范围</label>
            <select class="form-select" v-model="addForm.scope">
              <option>标题</option><option>全字段</option><option>专辑名</option><option>风格标签</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">加入原因</label>
            <input class="form-input" v-model="addForm.reason" placeholder="说明原因（可选）" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="closeAdd">取消</button>
          <button class="btn btn-primary btn-sm" @click="submitAdd">确认添加</button>
        </div>
      </div>
    </div>
  </div>
</template>
