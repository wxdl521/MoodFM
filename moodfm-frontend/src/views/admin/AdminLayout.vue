<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { AdminToastType } from '@/types'
import '@/assets/styles/admin.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

interface Toast { id: number; msg: string; type: AdminToastType }
const toasts = ref<Toast[]>([])

function showToast(msg: string, type: AdminToastType = 'ok') {
  const id = Date.now() + Math.random()
  toasts.value.push({ id, msg, type })
  setTimeout(() => {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx > -1) toasts.value.splice(idx, 1)
  }, 3200)
}

window.__adminToast = showToast

const NAV = [
  {
    group: '核心',
    items: [
      { path: '/admin/dashboard', cn: '仪表盘',   icon: 'M3 3h7v8H3zm11 0h7v8h-7zM3 14h7v7H3zm11 0h7v7h-7z' },
      { path: '/admin/users',     cn: '用户管理',  icon: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75' },
      { path: '/admin/music',     cn: '音乐管理',  icon: 'M9 18V5l12-2v13M6 21a3 3 0 100-6 3 3 0 000 6zM18 19a3 3 0 100-6 3 3 0 000 6z' },
      { path: '/admin/platforms', cn: '平台管理',  icon: 'M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71' },
    ]
  },
  {
    group: '智能',
    items: [
      { path: '/admin/ai',        cn: 'AI 引擎',  icon: 'M12 2a4 4 0 014 4v1h1a3 3 0 013 3v6a3 3 0 01-3 3H7a3 3 0 01-3-3v-6a3 3 0 013-3h1V6a4 4 0 014-4zM9 13a1 1 0 102 0 1 1 0 00-2 0zm5 0a1 1 0 102 0 1 1 0 00-2 0z' },
      { path: '/admin/analytics', cn: '数据分析',  icon: 'M3 3v18h18M7 16l4-4 4 4 4-8' },
    ]
  },
  {
    group: '运营',
    items: [
      { path: '/admin/notifications', cn: '通知管理', icon: 'M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 01-3.46 0' },
      { path: '/admin/system',        cn: '系统设置', icon: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z' },
    ]
  }
]

function isActive(path: string) {
  return route.path === path
}

const adminUsername = computed(() => auth.user?.username ?? 'Admin')
const adminInitial = computed(() => (auth.user?.username?.[0] ?? 'A').toUpperCase())
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="sidebar-brand-mark">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <path d="M9 18V5l12-2v13"/>
            <circle cx="6" cy="18" r="3"/>
            <circle cx="18" cy="16" r="3"/>
          </svg>
        </div>
        <div class="sidebar-brand-text">
          <div class="logo-name">MoodFM</div>
          <div class="logo-sub">Admin Console</div>
        </div>
      </div>

      <nav class="sidebar-nav">
        <div v-for="group in NAV" :key="group.group">
          <div class="nav-group-label">{{ group.group }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="['nav-item', isActive(item.path) ? 'active' : '']"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
              <path :d="item.icon"/>
            </svg>
            <span>{{ item.cn }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-avatar">{{ adminInitial }}</div>
        <div>
          <div class="sidebar-user-name">{{ adminUsername }}</div>
          <div class="sidebar-user-role">Super Admin</div>
        </div>
      </div>
    </aside>

    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="admin-panel" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </main>

    <div class="toast-wrap">
      <div v-for="t in toasts" :key="t.id" :class="['toast', t.type]">{{ t.msg }}</div>
    </div>
  </div>
</template>

<style scoped>
/* Admin 内部子页切换：侧边栏静止，只内容区 6px 上滑 + 淡入。
   时间 180ms 离场 / 220ms 入场，与 admin 简洁工具感匹配，不抢侧边栏注意。 */
.admin-panel-enter-active {
  transition: opacity 220ms ease, transform 220ms cubic-bezier(0.16, 1, 0.3, 1);
}
.admin-panel-leave-active {
  transition: opacity 140ms ease;
}
.admin-panel-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.admin-panel-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .admin-panel-enter-active,
  .admin-panel-leave-active {
    transition: opacity 100ms ease !important;
  }
  .admin-panel-enter-from {
    transform: none !important;
  }
}
</style>
