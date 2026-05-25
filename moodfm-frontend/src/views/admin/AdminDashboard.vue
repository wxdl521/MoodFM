<script setup lang="ts">
import { ref, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import api from '@/api/client'
import { logger } from '@/utils/logger'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const AVATAR_COLORS = ['#ff8a5b', '#6e5cd9', '#2d3a8c', '#e85a8a', '#4e9e78', '#f0a500', '#3d9be9']
const PLATFORM_NAMES: Record<string, string> = {
  netease: '网易云音乐', qqmusic: 'QQ 音乐', qq: 'QQ 音乐', spotify: 'Spotify',
}

function avatarColor(id: number) { return AVATAR_COLORS[id % AVATAR_COLORS.length] }

function relativeTime(iso: string) {
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} 小时前`
  return `${Math.floor(hrs / 24)} 天前`
}

const stats = ref({ totalUsers: 0, activeToday: 0, totalPlays: 0, totalListeningHours: 0, newUsersToday: 0 })
const alerts = ref<{ type: string; text: string }[]>([])
const systemStatus = ref([
  { label: 'API 服务', status: 'ok', val: '正常' },
  { label: '数据库',   status: 'ok', val: '已连接' },
])
const platforms = ref<{ name: string; key: string; status: string; bound: number; expiring: number; expired: number }[]>([])
const recentUsers = ref<{ id: number; username: string; email: string; createdAt: string; role: string }[]>([])

const chartOption = ref({
  grid: { top: 16, right: 20, bottom: 24, left: 50 },
  tooltip: { trigger: 'axis', textStyle: { fontFamily: 'JetBrains Mono', fontSize: 11 } },
  xAxis: { type: 'category', data: [] as string[], axisLine: { lineStyle: { color: 'rgba(26,23,20,0.1)' } }, axisLabel: { fontFamily: 'JetBrains Mono', fontSize: 10, color: '#8a8276' } },
  yAxis: { type: 'value', minInterval: 1, axisLabel: { fontFamily: 'JetBrains Mono', fontSize: 10, color: '#8a8276' }, splitLine: { lineStyle: { color: 'rgba(26,23,20,0.05)' } } },
  series: [{ name: 'DAU', type: 'line', data: [] as number[], smooth: true, lineStyle: { color: '#ff8a5b', width: 2 }, areaStyle: { color: 'rgba(255,138,91,0.07)' }, symbol: 'circle', symbolSize: 6, itemStyle: { color: '#ff8a5b' } }],
})

onMounted(async () => {
  // 统计数字
  try {
    const data = await api.get('/admin/stats')
    if (data) Object.assign(stats.value, data)
  } catch (err) {
    // silent: dashboard stats 加载失败时显示 0
    logger.warn('admin:dashboard-stats', err)
  }

  // 最近注册用户
  try {
    const users = await api.get('/admin/users/recent?limit=5')
    if (Array.isArray(users)) recentUsers.value = users
  } catch (err) {
    // silent: 最近用户列表加载失败时显示空
    logger.warn('admin:dashboard-recent-users', err)
  }

  // DAU 折线图
  try {
    const activity = await api.get('/admin/dashboard/activity?days=7') as { labels?: string[]; dau?: number[] } | null
    if (activity?.labels && activity?.dau) {
      chartOption.value.xAxis.data = activity.labels
      chartOption.value.series[0].data = activity.dau
    }
  } catch (err) {
    // silent: 活跃趋势加载失败时展示空图
    logger.warn('admin:dashboard-activity', err)
  }

  // 平台绑定统计
  try {
    const pStats = await api.get('/admin/platforms/stats')
    if (pStats && typeof pStats === 'object') {
      platforms.value = Object.entries(pStats as Record<string, any>).map(([key, v]) => ({
        name: PLATFORM_NAMES[key] ?? key,
        key,
        status: v.expiring > 0 ? 'warn' : 'ok',
        bound: v.bound ?? 0,
        expiring: v.expiring ?? 0,
        expired: v.expired ?? 0,
      }))
      const expiringTotal = platforms.value.reduce((s, p) => s + p.expiring, 0)
      if (expiringTotal > 0) {
        alerts.value.push({ type: 'warn', text: `共 ${expiringTotal} 名用户的 Cookie 将在 3 天内过期，建议提醒刷新` })
      }
    }
  } catch (err) {
    // silent: 平台统计加载失败时显示空
    logger.warn('admin:dashboard-platform-stats', err)
  }
})
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">Dashboard</span>
        <span class="topbar-title-cn">仪表盘</span>
      </div>
      <div class="topbar-actions">
        <span class="mono fs-10 t-ink3">最后更新 · 刚刚</span>
        <router-link to="/admin/users" class="btn btn-ghost btn-sm">用户管理 →</router-link>
      </div>
    </div>

    <div class="content-area">
      <!-- Alerts -->
      <div v-for="a in alerts" :key="a.text" :class="['alert', a.type]">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        {{ a.text }}
      </div>

      <!-- Stat cards -->
      <div class="stat-grid">
        <div class="stat-card s1">
          <div class="stat-label">TOTAL USERS</div>
          <div class="stat-value">{{ stats.totalUsers.toLocaleString() }}</div>
          <div class="stat-meta cn">注册用户</div>
        </div>
        <div class="stat-card s2">
          <div class="stat-label">DAU</div>
          <div class="stat-value">{{ stats.activeToday.toLocaleString() }}</div>
          <div class="stat-meta cn">日活用户</div>
        </div>
        <div class="stat-card s3">
          <div class="stat-label">SONGS TODAY</div>
          <div class="stat-value">{{ stats.totalPlays.toLocaleString() }}</div>
          <div class="stat-meta cn">累计播放</div>
        </div>
        <div class="stat-card s4">
          <div class="stat-label">LISTENING HRS</div>
          <div class="stat-value">{{ stats.totalListeningHours }}</div>
          <div class="stat-meta cn">总收听时长 h</div>
        </div>
      </div>

      <!-- Activity chart + System health -->
      <div class="grid-21 mb-20">
        <div class="card">
          <div class="card-head">
            <span class="card-title">活跃用户趋势</span>
            <span class="badge live"><span class="badge-dot"></span>LIVE</span>
          </div>
          <div class="card-body">
            <div class="chart-wrap">
              <VChart :option="chartOption" autoresize style="height:100%;width:100%" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><span class="card-title">系统健康</span></div>
          <div class="card-body" style="padding:10px 0 4px;">
            <div v-for="s in systemStatus" :key="s.label" class="between" style="padding:8px 18px;">
              <div class="row gap-8">
                <div :class="['health-dot', s.status]"></div>
                <span class="mono fs-12 t-ink2">{{ s.label }}</span>
              </div>
              <span class="mono fs-10 t-ink3">{{ s.val }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Platform status + Recent users -->
      <div class="grid-2">
        <div class="card">
          <div class="card-head"><span class="card-title">平台接入状态</span></div>
          <div class="card-body" style="padding:0;">
            <div v-if="platforms.length === 0" style="padding:24px 18px;" class="mono fs-11 t-ink3">暂无平台绑定数据</div>
            <div v-for="p in platforms" :key="p.key" style="padding:16px 18px; border-bottom:1px solid var(--rule);">
              <div class="between mb-14">
                <div class="row gap-8">
                  <div :class="['health-dot', p.status]"></div>
                  <span class="mono fs-12 t-ink">{{ p.name }}</span>
                </div>
                <span :class="['badge', p.status]">{{ p.status === 'ok' ? '正常' : '待刷新' }}</span>
              </div>
              <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;">
                <div>
                  <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:3px;">绑定用户</div>
                  <div class="mono fs-12 t-ink2">{{ p.bound.toLocaleString() }}</div>
                </div>
                <div>
                  <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:3px;">Cookie 待刷新</div>
                  <div class="mono fs-12" :style="p.expiring > 0 ? 'color:var(--warn)' : ''">{{ p.expiring }}</div>
                </div>
                <div>
                  <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:3px;">已失效</div>
                  <div class="mono fs-12" :style="p.expired > 0 ? 'color:var(--error,#e85a8a)' : ''">{{ p.expired }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <span class="card-title">最近注册</span>
            <router-link to="/admin/users" class="btn btn-ghost btn-sm">全部用户 →</router-link>
          </div>
          <table class="tbl">
            <thead><tr><th>用户</th><th>注册时间</th><th>角色</th></tr></thead>
            <tbody>
              <tr v-if="recentUsers.length === 0">
                <td colspan="3" class="mono fs-11 t-ink3" style="text-align:center;padding:20px;">暂无用户数据</td>
              </tr>
              <tr v-for="u in recentUsers" :key="u.id">
                <td>
                  <div class="row gap-8">
                    <div class="u-avatar" :style="'background:' + avatarColor(u.id)">{{ (u.username || '?')[0].toUpperCase() }}</div>
                    <div>
                      <div class="mono fs-12 t-ink">{{ u.username }}</div>
                      <div class="mono fs-10 t-ink3">{{ u.email }}</div>
                    </div>
                  </div>
                </td>
                <td class="mono fs-10 t-ink3">{{ relativeTime(u.createdAt) }}</td>
                <td>
                  <span v-if="u.role === 'ADMIN'" class="platform-tag" style="background:rgba(110,92,217,0.12);color:#6e5cd9;">ADMIN</span>
                  <span v-else class="mono fs-10 t-ink3">USER</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
