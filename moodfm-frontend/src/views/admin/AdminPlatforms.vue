<script setup lang="ts">
import { ref, onMounted } from 'vue'
import '@/assets/styles/admin.css'
import { platformAdminApi, type CookieExpiryRow } from '@/api/admin'
import type { AdminToastType } from '@/types'
import { logger } from '@/utils/logger'

interface Endpoint { name: string; status: 'ok' | 'warn'; latency: string }
interface Platform {
  key: string; name: string; color: string
  status: 'ok' | 'warn'; latency: number; uptime: string
  callsToday: number; callsLimit: number
  bound: number; expiring: number; expired: number
  lastSync: string
  endpoints: Endpoint[]
}

const platforms = ref<Platform[]>([
  {
    key: 'netease', name: '网易云音乐', color: '#c0392b',
    status: 'ok', latency: 124, uptime: '99.92%',
    callsToday: 48320, callsLimit: 100000,
    bound: 0, expiring: 0, expired: 0,
    lastSync: '2 分钟前',
    endpoints: [
      { name: '搜索 API',   status: 'ok',   latency: '112ms' },
      { name: '播放 URL',   status: 'ok',   latency: '88ms'  },
      { name: '歌词获取',   status: 'ok',   latency: '145ms' },
      { name: '用户登录',   status: 'ok',   latency: '210ms' },
      { name: '二维码登录', status: 'ok',   latency: '198ms' },
    ],
  },
  {
    key: 'qq', name: 'QQ 音乐', color: '#1e6eb5',
    status: 'warn', latency: 89, uptime: '99.76%',
    callsToday: 31450, callsLimit: 80000,
    bound: 0, expiring: 0, expired: 0,
    lastSync: '8 分钟前',
    endpoints: [
      { name: '搜索 API',   status: 'ok',   latency: '76ms'  },
      { name: '播放 URL',   status: 'ok',   latency: '92ms'  },
      { name: '歌词获取',   status: 'warn', latency: '380ms' },
      { name: '用户登录',   status: 'ok',   latency: '188ms' },
      { name: '二维码登录', status: 'warn', latency: '450ms' },
    ],
  },
])

const cookieTable = ref<CookieExpiryRow[]>([])
const loadingStats = ref(false)

function toast(msg: string, type: AdminToastType = 'ok') { window.__adminToast?.(msg, type) }

function callsPercent(p: Platform) { return Math.round(p.callsToday / p.callsLimit * 100) }

function syncAll(p: Platform) {
  toast(`正在同步 ${p.name} 所有 Cookie…`, 'info')
  setTimeout(() => toast(`${p.name} 同步完成`), 1800)
}
function notifyUser(row: CookieExpiryRow) { toast(`已向 ${row.username} 发送 Cookie 刷新提醒`) }
function forceRefresh(row: CookieExpiryRow) { toast(`已为 ${row.username} 触发强制刷新`) }

function rowStatus(row: CookieExpiryRow): 'warn' | 'danger' {
  return row.daysLeft <= 0 ? 'danger' : 'warn'
}

onMounted(async () => {
  loadingStats.value = true
  try {
    const [stats, expiry] = await Promise.all([
      platformAdminApi.getStats(),
      platformAdminApi.getCookieExpiry(),
    ])
    for (const p of platforms.value) {
      const key = p.key === 'qq' ? 'qqmusic' : p.key
      const s = stats[p.key] ?? stats[key] ?? stats['qqmusic']
      if (s) { p.bound = s.bound; p.expiring = s.expiring; p.expired = s.expired }
    }
    cookieTable.value = expiry
  } catch (err) {
    logger.warn('admin:platforms-load', err)
    toast('平台数据加载失败', 'warn')
  } finally {
    loadingStats.value = false
  }
})
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">Platforms</span>
        <span class="topbar-title-cn">平台管理</span>
      </div>
      <div class="topbar-actions">
        <button class="btn btn-ghost btn-sm" @click="syncAll(platforms[0])">↻ 同步全部</button>
      </div>
    </div>

    <div class="content-area">
      <!-- Platform cards -->
      <div class="grid-2 mb-20">
        <div class="card" v-for="p in platforms" :key="p.key">
          <div class="card-head">
            <div class="row gap-8">
              <div :class="['health-dot', p.status]"></div>
              <span class="card-title">{{ p.name }}</span>
            </div>
            <div class="row gap-8">
              <span :class="['badge', p.status]">{{ p.status === 'ok' ? '正常' : '警告' }}</span>
              <button class="btn btn-ghost btn-sm" @click="syncAll(p)">↻ 同步</button>
            </div>
          </div>
          <div class="card-body">
            <!-- Stats row -->
            <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px;">
              <div style="text-align:center;">
                <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:4px;">延迟</div>
                <div class="mono fs-13 t-ink">{{ p.latency }}ms</div>
              </div>
              <div style="text-align:center;">
                <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:4px;">可用率</div>
                <div class="mono fs-13 t-ok">{{ p.uptime }}</div>
              </div>
              <div style="text-align:center;">
                <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:4px;">绑定用户</div>
                <div class="mono fs-13 t-ink">{{ p.bound.toLocaleString() }}</div>
              </div>
              <div style="text-align:center;">
                <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:4px;">最后同步</div>
                <div class="mono fs-11 t-ink3">{{ p.lastSync }}</div>
              </div>
            </div>

            <!-- API usage bar -->
            <div class="mb-14">
              <div class="between mb-8">
                <span class="mono t-ink3 ls-wide" style="font-size:9px;">今日 API 调用量</span>
                <span class="mono fs-11 t-ink2">{{ p.callsToday.toLocaleString() }} / {{ p.callsLimit.toLocaleString() }}</span>
              </div>
              <div style="height:4px;background:var(--bg-2);border-radius:2px;overflow:hidden;">
                <div :style="`height:100%;border-radius:2px;background:${callsPercent(p) > 80 ? 'var(--warn)' : p.color};width:${callsPercent(p)}%`"></div>
              </div>
              <div class="mono t-ink3" style="margin-top:4px;font-size:9px;">{{ callsPercent(p) }}% 已使用</div>
            </div>

            <!-- Endpoints -->
            <div class="mono t-ink3 ls-wide" style="font-size:9px;margin-bottom:8px;">接口状态</div>
            <div v-for="ep in p.endpoints" :key="ep.name" class="between" style="padding:6px 0;border-bottom:1px solid var(--rule);">
              <div class="row gap-8">
                <div :class="['health-dot', ep.status]" style="width:6px;height:6px;"></div>
                <span class="mono fs-11 t-ink2">{{ ep.name }}</span>
              </div>
              <span class="mono fs-10" :style="ep.status === 'warn' ? 'color:var(--warn)' : 'color:var(--ink-3)'">{{ ep.latency }}</span>
            </div>

            <!-- Cookie alerts -->
            <div class="between mt-14">
              <div class="row gap-16">
                <div>
                  <div class="mono t-ink3 ls-wide" style="font-size:9px;">Cookie 即将过期</div>
                  <div class="mono fs-13" :style="p.expiring > 0 ? 'color:var(--warn)' : 'color:var(--ink-2)'">{{ p.expiring }} 用户</div>
                </div>
                <div>
                  <div class="mono t-ink3 ls-wide" style="font-size:9px;">已过期</div>
                  <div class="mono fs-13" :style="p.expired > 0 ? 'color:var(--danger)' : 'color:var(--ink-2)'">{{ p.expired }} 用户</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Cookie monitoring table -->
      <div class="card">
        <div class="card-head">
          <span class="card-title">Cookie 健康监控 · 近期到期</span>
          <span class="badge warn"><span class="badge-dot"></span>{{ cookieTable.length }} 条待处理</span>
        </div>
        <table class="tbl">
          <thead>
            <tr>
              <th>用户</th><th>平台</th><th>到期时间</th><th>剩余天数</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in cookieTable" :key="`${row.userId}-${row.platform}`">
              <td>
                <div class="mono fs-12 t-ink">{{ row.username }}</div>
                <div class="mono fs-10 t-ink3">UID {{ row.userId }}</div>
              </td>
              <td>
                <span v-if="row.platform === 'netease'" class="platform-tag pt-netease">网易云</span>
                <span v-else class="platform-tag pt-qq">QQ 音乐</span>
              </td>
              <td class="mono fs-11 t-ink3">{{ row.expiresAt.slice(0, 10) }}</td>
              <td>
                <span class="mono fs-12" :style="row.daysLeft === 0 ? 'color:var(--danger)' : row.daysLeft <= 2 ? 'color:var(--warn)' : 'color:var(--ink-2)'">
                  {{ row.daysLeft === 0 ? '已过期' : `${row.daysLeft} 天` }}
                </span>
              </td>
              <td>
                <span :class="['badge', rowStatus(row)]">
                  <span class="badge-dot"></span>
                  {{ rowStatus(row) === 'danger' ? '已过期' : '即将过期' }}
                </span>
              </td>
              <td>
                <div class="row gap-6">
                  <button class="btn btn-ghost btn-sm" @click="notifyUser(row)">发提醒</button>
                  <button class="btn btn-ghost btn-sm" @click="forceRefresh(row)">强制刷新</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
