<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import api from '@/api/client'

const router = useRouter()
const loading = ref(true)
const users = ref<Array<{
  id: number; username: string; email: string; phone: string
  role: string; status: number; createdAt: string
}>>([])

const stats = ref({
  totalUsers: 0,
  activeToday: 0,
  totalPlays: 0,
  totalListeningHours: 0,
})

async function loadStats() {
  try {
    const data = await api.get('/admin/stats')
    if (data) Object.assign(stats.value, data)
  } catch {
    // silently fail
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const data = await api.get('/admin/users')
    users.value = data as any[]
  } catch {
    // likely not admin
  } finally {
    loading.value = false
  }
}

async function toggleStatus(u: { id: number; status: number }) {
  const newStatus = u.status === 1 ? 0 : 1
  try {
    await api.put(`/admin/users/${u.id}/status`, { status: newStatus })
    u.status = newStatus
  } catch {
    // silently fail — status remains unchanged
  }
}

onMounted(() => {
  loadStats()
  loadUsers()
})
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:100px;">
    <NavBar />

    <div style="padding:40px 56px;">
      <div class="meta">ADMIN · 管理面板</div>
      <h1 class="display" style="font-size:clamp(52px,7vw,108px);margin:10px 0 0;">Dashboard.</h1>
      <div class="display-cn" style="font-size:20px;color:var(--ink-2);margin-bottom:32px;">
        系统概览
      </div>

      <!-- Stats grid -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="meta">总用户数</div>
          <div class="stat-value">{{ stats.totalUsers }}</div>
        </div>
        <div class="stat-card">
          <div class="meta">今日活跃</div>
          <div class="stat-value">{{ stats.activeToday }}</div>
        </div>
        <div class="stat-card">
          <div class="meta">总播放次数</div>
          <div class="stat-value">{{ stats.totalPlays }}</div>
        </div>
        <div class="stat-card">
          <div class="meta">总收听时长</div>
          <div class="stat-value">{{ stats.totalListeningHours }}h</div>
        </div>
      </div>

      <!-- User list header -->
      <div class="meta" style="margin-bottom:14px;">USERS · 用户管理</div>
      <div class="display-cn" style="font-size:16px;color:var(--ink-2);margin-bottom:24px;">
        共 {{ users.length }} 位用户
      </div>

      <div v-if="loading" class="meta" style="color:var(--ink-3);">加载中…</div>

      <div v-else style="border:1px solid var(--rule);border-radius:14px;overflow:hidden;background:var(--paper);">
        <div style="display:grid;grid-template-columns:60px 1fr 1fr 80px 80px 120px;gap:0;padding:12px 18px;
                    border-bottom:1px solid var(--rule);background:var(--bg);">
          <div class="meta">ID</div>
          <div class="meta">用户名</div>
          <div class="meta">邮箱</div>
          <div class="meta">角色</div>
          <div class="meta">状态</div>
          <div class="meta" style="text-align:right;">操作</div>
        </div>
        <div
          v-for="u in users"
          :key="u.id"
          style="display:grid;grid-template-columns:60px 1fr 1fr 80px 80px 120px;gap:0;padding:12px 18px;
                 border-bottom:1px solid var(--rule);align-items:center;"
        >
          <span class="mono" style="font-size:12px;color:var(--ink-3);">{{ u.id }}</span>
          <span style="font-family:var(--serif-cn);font-size:14px;">{{ u.username }}</span>
          <span style="font-family:var(--serif-cn);font-size:13px;color:var(--ink-2);">{{ u.email }}</span>
          <span class="meta" :style="{ color: u.role === 'ADMIN' ? 'var(--mood-a)' : 'var(--ink-3)' }">{{ u.role }}</span>
          <span :style="{ fontFamily:'var(--mono)',fontSize:'12px',color: u.status===1 ? '#4a9' : 'var(--mood-b)' }">
            {{ u.status === 1 ? '正常' : '禁用' }}
          </span>
          <button
            class="btn-pill"
            style="font-size:11px;justify-self:end;"
            :style="{ color: u.status===1 ? 'var(--mood-b)' : '#4a9' }"
            @click="toggleStatus(u)"
          >{{ u.status === 1 ? '禁用' : '启用' }}</button>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 40px;
}

.stat-card {
  border: 1px solid var(--rule);
  border-radius: 14px;
  padding: 20px;
  background: var(--paper);
}

.stat-value {
  font-family: var(--mono);
  font-size: 32px;
  margin-top: 8px;
  color: var(--ink);
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .stat-card {
    padding: 14px;
  }
  .stat-value {
    font-size: 24px;
  }
}
</style>
