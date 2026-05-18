<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import '@/assets/styles/admin.css'
import { notificationsAdminApi, AdminNotification } from '@/api/admin'

const tab = ref<'compose' | 'history'>('compose')
const sending = ref(false)
const loading = ref(false)

const history = ref<AdminNotification[]>([])

const form = ref({
  title: '', body: '', type: 'system', target: 'all',
  scheduleNow: true, scheduleTime: '',
})

const templates = [
  { name: '周报生成通知', title: '本周情绪周报已生成 🎵', body: '您的本周音乐心情报告已就绪，快来查看您的专属情绪曲线吧！' },
  { name: 'Cookie 过期提醒', title: '您的音乐平台授权即将过期', body: '您绑定的 [平台名称] 账号授权将在 [X] 天后失效，请及时前往设置中重新授权，以确保电台正常使用。' },
  { name: '新功能上线', title: 'MoodFM 新功能上线啦！', body: '我们悄悄更新了 [功能名称]，让每次听歌体验更贴心。进入应用探索吧 →' },
  { name: '系统维护通知', title: '系统维护通知', body: 'MoodFM 将于 [时间] 进行例行维护，届时服务将暂停约 [时长]。感谢您的耐心等待！' },
]

const typeLabels: Record<string, string> = { system: '系统通知', weekly: '周报', feature: '新功能', push: '推送' }
const targetLabels: Record<string, string> = { all: '全体用户', expiring: 'Cookie 即将过期', new: '近 7 日新注册', inactive: '30 日不活跃' }

const sendStats = computed(() => {
  const sent = history.value.filter(h => h.status === 'sent')
  const totalSent = sent.reduce((a, b) => a + b.sentCount, 0)
  const totalOpened = sent.reduce((a, b) => a + b.openedCount, 0)
  return {
    total: history.value.length,
    sentCount: sent.length,
    totalSent,
    openRate: totalSent ? `${Math.round(totalOpened / totalSent * 100)}%` : '—',
  }
})

function toast(msg: string, type = 'ok') { (window as any).__adminToast?.(msg, type) }

function applyTemplate(t: typeof templates[number]) {
  form.value.title = t.title; form.value.body = t.body
  toast(`已应用模板：${t.name}`)
}

async function fetchList() {
  loading.value = true
  try {
    history.value = await notificationsAdminApi.list()
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)

async function sendNotif() {
  if (!form.value.title || !form.value.body) { toast('请填写标题和内容', 'warn'); return }
  sending.value = true
  const scheduleNow = form.value.scheduleNow
  try {
    const newNotif = await notificationsAdminApi.create({
      title: form.value.title,
      body: form.value.body,
      type: form.value.type,
      targetGroup: form.value.target,
      status: scheduleNow ? 'sent' : 'scheduled',
      scheduledAt: scheduleNow ? undefined : form.value.scheduleTime || undefined,
    })
    if (scheduleNow) {
      await notificationsAdminApi.send(newNotif.id)
    }
    history.value = await notificationsAdminApi.list()
    form.value = { title: '', body: '', type: 'system', target: 'all', scheduleNow: true, scheduleTime: '' }
    sending.value = false
    toast(scheduleNow ? '通知已发送' : '通知已排期')
    tab.value = 'history'
  } catch {
    toast('操作失败，请稍后重试', 'warn')
    sending.value = false
  }
}

async function saveDraft() {
  try {
    await notificationsAdminApi.create({
      title: form.value.title || '(未命名草稿)',
      body: form.value.body || '',
      type: form.value.type,
      targetGroup: form.value.target,
      status: 'draft',
    })
    history.value = await notificationsAdminApi.list()
    toast('已保存为草稿')
  } catch {
    toast('保存失败，请稍后重试', 'warn')
  }
}

async function deleteNotif(id: number) {
  history.value = history.value.filter(h => h.id !== id)
  try {
    await notificationsAdminApi.remove(id)
    toast('已删除')
  } catch {
    toast('删除失败，请稍后重试', 'warn')
    history.value = await notificationsAdminApi.list()
  }
}

function statusClass(s: string) { return s === 'sent' ? 'ok' : s === 'scheduled' ? 'info' : 'neutral' }
function statusLabel(s: string) { return s === 'sent' ? '已发送' : s === 'scheduled' ? '已排期' : '草稿' }

function openRate(h: AdminNotification) {
  if (h.sentCount <= 0) return '—'
  return `${Math.round(h.openedCount / h.sentCount * 100)}%`
}

function formatTime(h: AdminNotification) {
  const raw = h.sentAt ?? h.scheduledAt ?? h.createdAt ?? '—'
  return raw === '—' ? '—' : raw.slice(0, 16).replace('T', ' ')
}
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">Notifications</span>
        <span class="topbar-title-cn">通知管理</span>
      </div>
      <div class="topbar-actions">
        <button :class="['btn btn-sm', tab === 'compose' ? 'btn-primary' : 'btn-ghost']" @click="tab = 'compose'">撰写通知</button>
        <button :class="['btn btn-sm', tab === 'history' ? 'btn-primary' : 'btn-ghost']" @click="tab = 'history'">历史记录</button>
      </div>
    </div>

    <div class="content-area">
      <!-- Stats -->
      <div class="stat-grid" style="margin-bottom:24px;">
        <div class="stat-card s1"><div class="stat-label">总通知数</div><div class="stat-value" style="font-size:22px;">{{ sendStats.total }}</div></div>
        <div class="stat-card s2"><div class="stat-label">已发送</div><div class="stat-value" style="font-size:22px;">{{ sendStats.sentCount }}</div></div>
        <div class="stat-card s3"><div class="stat-label">累计触达</div><div class="stat-value" style="font-size:22px;">{{ sendStats.totalSent.toLocaleString() }}</div></div>
        <div class="stat-card s4"><div class="stat-label">平均打开率</div><div class="stat-value" style="font-size:22px;">{{ sendStats.openRate }}</div></div>
      </div>

      <!-- Compose -->
      <div v-if="tab === 'compose'">
        <div class="grid-12">
          <!-- Templates -->
          <div class="card">
            <div class="card-head"><span class="card-title">快速模板</span></div>
            <div class="card-body" style="padding:10px;">
              <div
                v-for="t in templates" :key="t.name"
                style="padding:12px;border:1px solid var(--rule);border-radius:8px;cursor:pointer;margin-bottom:8px;transition:border-color .14s;"
                @click="applyTemplate(t)"
                @mouseenter="($event.currentTarget as HTMLElement).style.borderColor = 'var(--ink-3)'"
                @mouseleave="($event.currentTarget as HTMLElement).style.borderColor = 'var(--rule)'"
              >
                <div class="mono fs-12 t-ink" style="margin-bottom:4px;">{{ t.name }}</div>
                <div class="mono fs-10 t-ink3" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ t.title }}</div>
              </div>
            </div>
          </div>

          <!-- Form -->
          <div class="card">
            <div class="card-head"><span class="card-title">撰写通知</span></div>
            <div class="card-body">
              <div class="form-group">
                <label class="form-label">通知标题</label>
                <input class="form-input" v-model="form.title" placeholder="简洁有力的标题…" />
              </div>
              <div class="form-group">
                <label class="form-label">通知内容</label>
                <textarea class="form-input" v-model="form.body" placeholder="通知正文，支持 [变量] 占位符…" style="min-height:120px;"></textarea>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label">通知类型</label>
                  <select class="form-select" v-model="form.type">
                    <option value="system">系统通知</option>
                    <option value="weekly">周报</option>
                    <option value="feature">新功能</option>
                    <option value="push">推送</option>
                  </select>
                </div>
                <div class="form-group">
                  <label class="form-label">目标用户</label>
                  <select class="form-select" v-model="form.target">
                    <option value="all">全体用户</option>
                    <option value="expiring">Cookie 即将过期</option>
                    <option value="new">近 7 日新注册</option>
                    <option value="inactive">30 日不活跃</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label class="form-label">发送时间</label>
                <div class="row gap-12" style="margin-top:6px;">
                  <label class="row gap-8" style="cursor:pointer;">
                    <input type="radio" v-model="form.scheduleNow" :value="true" />
                    <span class="mono fs-12 t-ink2">立即发送</span>
                  </label>
                  <label class="row gap-8" style="cursor:pointer;">
                    <input type="radio" v-model="form.scheduleNow" :value="false" />
                    <span class="mono fs-12 t-ink2">定时发送</span>
                  </label>
                </div>
                <input v-if="!form.scheduleNow" type="datetime-local" class="form-input" v-model="form.scheduleTime" style="margin-top:8px;" />
              </div>
              <div class="row gap-8" style="justify-content:flex-end;margin-top:8px;">
                <button class="btn btn-ghost btn-sm" @click="saveDraft">存为草稿</button>
                <button class="btn btn-primary btn-sm" @click="sendNotif" :disabled="sending">
                  {{ sending ? '发送中…' : (form.scheduleNow ? '立即发送' : '排期发送') }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- History -->
      <div v-if="tab === 'history'">
        <div class="card">
          <div class="card-head"><span class="card-title">发送历史</span></div>
          <table class="tbl">
            <thead>
              <tr>
                <th>标题</th><th>类型</th><th>目标</th><th>发送数</th><th>打开率</th><th>时间</th><th>状态</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in history" :key="h.id">
                <td class="mono fs-12 t-ink" style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ h.title }}</td>
                <td><span class="badge neutral">{{ typeLabels[h.type] || h.type }}</span></td>
                <td class="mono fs-11 t-ink3">{{ targetLabels[h.targetGroup] || h.targetGroup }}</td>
                <td class="mono fs-12 t-ink2">{{ h.sentCount > 0 ? h.sentCount.toLocaleString() : '—' }}</td>
                <td class="mono fs-12 t-ink2">{{ openRate(h) }}</td>
                <td class="mono fs-10 t-ink3">{{ formatTime(h) }}</td>
                <td><span :class="['badge', statusClass(h.status)]"><span class="badge-dot"></span>{{ statusLabel(h.status) }}</span></td>
                <td><button class="btn btn-danger btn-sm" @click="deleteNotif(h.id)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
