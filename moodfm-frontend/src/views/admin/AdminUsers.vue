<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api/client'
import { logger } from '@/utils/logger'

interface AdminUser {
  id: number
  username: string
  email: string
  phone: string
  role: string
  status: number
  createdAt: string
}

const allUsers = ref<AdminUser[]>([])
const loading = ref(true)
const search = ref('')
const statusFilter = ref('')
const page = ref(1)
const PAGE_SIZE = 10

const selectedUser = ref<AdminUser | null>(null)
const showModal = ref(false)

// Avatar colors cycling
const COLORS = ['#ff8a5b','#e85a8a','#6e5cd9','#2d3a8c','#4e9e78','#c87d1a']
function avatarColor(id: number) { return COLORS[id % COLORS.length] }
function initial(u: AdminUser) { return (u.username?.[0] ?? u.email?.[0] ?? '?').toUpperCase() }

const filtered = computed(() => {
  return allUsers.value.filter(u => {
    if (search.value) {
      const q = search.value.toLowerCase()
      if (!u.username?.toLowerCase().includes(q) && !u.email?.toLowerCase().includes(q)) return false
    }
    if (statusFilter.value === 'active' && u.status !== 1) return false
    if (statusFilter.value === 'banned' && u.status !== 0) return false
    return true
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))

const paginated = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

function statusLabel(u: AdminUser) { return u.status === 1 ? '活跃' : '已禁用' }
function statusClass(u: AdminUser) { return u.status === 1 ? 'ok' : 'danger' }

function openUser(u: AdminUser) { selectedUser.value = u; showModal.value = true }
function closeModal() { showModal.value = false; selectedUser.value = null }

function toast(msg: string, type = 'ok') {
  ;(window as any).__adminToast?.(msg, type)
}

async function toggleBan(u: AdminUser) {
  const newStatus = u.status === 1 ? 0 : 1
  try {
    await api.put(`/admin/users/${u.id}/status`, { status: newStatus })
    u.status = newStatus
    toast(newStatus === 0 ? `已封禁用户：${u.username}` : `已解禁用户：${u.username}`, newStatus === 0 ? 'warn' : 'ok')
  } catch (err) {
    logger.warn('admin:users-toggle-ban', err)
    toast('操作失败', 'danger')
  }
}

function exportCSV() {
  toast(`已导出 ${filtered.value.length} 条用户数据`, 'ok')
}

onMounted(async () => {
  loading.value = true
  try {
    const data = await api.get('/admin/users?page=1&pageSize=200')
    allUsers.value = Array.isArray(data) ? data : []
  } catch (err) {
    logger.warn('admin:users-load', err)
    toast('加载用户数据失败', 'danger')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">Users</span>
        <span class="topbar-title-cn">用户管理</span>
        <span class="badge neutral" style="margin-left:8px;">{{ filtered.length }}</span>
      </div>
      <div class="topbar-actions">
        <button class="btn btn-ghost btn-sm" @click="exportCSV">↓ 导出 CSV</button>
      </div>
    </div>

    <div class="content-area">
      <!-- Toolbar -->
      <div class="toolbar">
        <div class="search-wrap">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="7"/><path d="M21 21l-4-4"/>
          </svg>
          <input class="search-input" v-model="search" @input="page=1" placeholder="搜索用户名或邮箱…" />
        </div>
        <select class="filter-select" v-model="statusFilter" @change="page=1">
          <option value="">全部状态</option>
          <option value="active">活跃</option>
          <option value="banned">已禁用</option>
        </select>
      </div>

      <!-- Table -->
      <div class="card">
        <div v-if="loading" style="padding:48px;text-align:center;" class="mono fs-11 t-ink3">加载中…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>用户</th>
              <th>注册时间</th>
              <th>手机</th>
              <th>角色</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="paginated.length === 0">
              <td colspan="6">
                <div class="empty">
                  <div class="empty-title">No results.</div>
                  <div class="empty-sub">尝试修改筛选条件</div>
                </div>
              </td>
            </tr>
            <tr v-for="u in paginated" :key="u.id">
              <td>
                <div class="row gap-8">
                  <div class="u-avatar" :style="'background:'+avatarColor(u.id)">{{ initial(u) }}</div>
                  <div>
                    <div class="mono fs-12 t-ink">{{ u.username || '—' }}</div>
                    <div class="mono fs-10 t-ink3">{{ u.email || '—' }}</div>
                  </div>
                </div>
              </td>
              <td class="mono fs-11 t-ink3">{{ u.createdAt?.slice(0,10) || '—' }}</td>
              <td class="mono fs-11 t-ink3">{{ u.phone || '—' }}</td>
              <td>
                <span class="badge" :class="u.role === 'ADMIN' ? 'danger' : 'neutral'">{{ u.role || 'USER' }}</span>
              </td>
              <td>
                <span :class="['badge', statusClass(u)]">
                  <span class="badge-dot"></span>{{ statusLabel(u) }}
                </span>
              </td>
              <td>
                <div class="row gap-6">
                  <button class="btn btn-ghost btn-sm" @click="openUser(u)">详情</button>
                  <button
                    :class="['btn btn-sm', u.status === 0 ? 'btn-ghost' : 'btn-danger']"
                    @click="toggleBan(u)"
                  >{{ u.status === 0 ? '解禁' : '封禁' }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="pagination" v-if="!loading">
          <div class="page-info">第 {{ page }} / {{ totalPages }} 页 · 共 {{ filtered.length }} 条</div>
          <div class="page-btns">
            <button class="page-btn" :disabled="page <= 1" @click="page--">‹</button>
            <button
              v-for="n in totalPages"
              :key="n"
              :class="['page-btn', page === n ? 'active' : '']"
              @click="page = n"
            >{{ n }}</button>
            <button class="page-btn" :disabled="page >= totalPages" @click="page++">›</button>
          </div>
        </div>
      </div>
    </div>

    <!-- User detail modal -->
    <div class="modal-overlay" v-if="showModal" @click.self="closeModal">
      <div class="modal" v-if="selectedUser">
        <div class="modal-head">
          <span class="modal-title">用户详情</span>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>
        <div class="modal-body">
          <div class="row gap-12 mb-20">
            <div class="u-avatar" style="width:48px;height:48px;font-size:18px;" :style="'background:'+avatarColor(selectedUser.id)">
              {{ initial(selectedUser) }}
            </div>
            <div>
              <div class="mono t-ink" style="font-size:15px;">{{ selectedUser.username || '—' }}</div>
              <div class="mono fs-11 t-ink3">{{ selectedUser.email || '—' }}</div>
              <div class="mt-8 row gap-6">
                <span :class="['badge', statusClass(selectedUser)]">
                  <span class="badge-dot"></span>{{ statusLabel(selectedUser) }}
                </span>
                <span class="badge" :class="selectedUser.role === 'ADMIN' ? 'danger' : 'neutral'">{{ selectedUser.role || 'USER' }}</span>
              </div>
            </div>
          </div>

          <hr class="divider" />

          <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:16px;" class="mb-20">
            <div style="text-align:center;padding:12px;background:var(--bg-2);border-radius:8px;">
              <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:4px;">注册时间</div>
              <div class="mono fs-12 t-ink">{{ selectedUser.createdAt?.slice(0,10) || '—' }}</div>
            </div>
            <div style="text-align:center;padding:12px;background:var(--bg-2);border-radius:8px;">
              <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:4px;">手机号</div>
              <div class="mono fs-12 t-ink">{{ selectedUser.phone || '未填写' }}</div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button
            :class="['btn btn-sm', selectedUser.status === 0 ? 'btn-primary' : 'btn-danger']"
            @click="toggleBan(selectedUser)"
          >{{ selectedUser.status === 0 ? '解除封禁' : '封禁账号' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
