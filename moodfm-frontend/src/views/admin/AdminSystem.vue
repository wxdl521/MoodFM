<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import '@/assets/styles/admin.css'
import { configAdminApi, auditLogAdminApi, AdminAuditLog } from '@/api/admin'
import { logger } from '@/utils/logger'

interface FlagItem { key: string; label: string; desc: string; on: boolean }
interface KvItem { key: string; val: string; desc: string }
interface AdminAccount { id: number; name: string; email: string; role: string; lastLogin: string; active: boolean }

const tab = ref<'flags' | 'config' | 'admins' | 'audit'>('flags')

const flags = ref<FlagItem[]>([
  { key: 'ai_recommendations',  label: 'AI 个性推荐',     desc: '启用基于用户画像的 AI 推荐引擎',         on: true  },
  { key: 'weekly_report',       label: '自动生成周报',     desc: '每周一 09:00 自动生成并推送用户周报',     on: true  },
  { key: 'cross_platform_sync', label: '跨平台歌单同步',   desc: '允许用户在多个音乐平台间同步歌单',        on: true  },
  { key: 'share_long_image',    label: '长图分享功能',     desc: '允许用户生成并分享情绪周报长图',          on: true  },
  { key: 'qr_login',            label: '扫码登录绑定',     desc: '启用平台二维码登录授权方式',             on: true  },
  { key: 'cookie_login',        label: 'Cookie 高级登录',  desc: '允许用户通过手动粘贴 Cookie 绑定平台',    on: true  },
  { key: 'mood_calendar',       label: '心情日历',         desc: '启用心情日历页面（Beta 功能）',           on: false },
  { key: 'social_profile',      label: '公开个人主页',     desc: '允许用户开启公开个人主页展示周报',        on: false },
  { key: 'maintenance_mode',    label: '维护模式',         desc: '开启后所有用户将看到维护提示页面',        on: false },
])

const kvConfig = ref<KvItem[]>([
  { key: 'recommendation.count',        val: '15',  desc: '每次电台推荐的歌曲数量' },
  { key: 'recommendation.replan_after', val: '3',   desc: '每隔 N 首后重新规划队列' },
  { key: 'session.max_duration_hours',  val: '4',   desc: '单次电台会话最长时长（小时）' },
  { key: 'cookie.expiry_warn_days',     val: '3',   desc: 'Cookie 到期前 N 天发送警告' },
  { key: 'qrcode.timeout_seconds',      val: '90',  desc: '二维码登录超时时间（秒）' },
  { key: 'report.generation_day',       val: '1',   desc: '周报生成日（0=周日, 1=周一…）' },
  { key: 'blacklist.max_per_user',      val: '200', desc: '每用户最大黑名单条目数' },
])

const auditLog = ref<AdminAuditLog[]>([])
const loadingAudit = ref(false)
let auditLoaded = false

const admins = ref<AdminAccount[]>([
  { id: 1, name: 'Admin',  email: 'admin@moodfm.com',    role: 'super',     lastLogin: '2026-05-16 14:22', active: true },
  { id: 2, name: 'Ops',    email: 'ops@moodfm.com',      role: 'operator',  lastLogin: '2026-05-15 10:30', active: true },
  { id: 3, name: 'Ana',    email: 'analyst@moodfm.com',  role: 'analyst',   lastLogin: '2026-05-14 18:00', active: true },
])

const roleLabels: Record<string, string> = { super: '超级管理员', operator: '运营', analyst: '分析师' }
const roleClass:  Record<string, string> = { super: 'danger',     operator: 'info', analyst: 'neutral' }

const editingKv = ref<KvItem | null>(null)
const showKvModal = ref(false)
const showAdminModal = ref(false)
const newAdmin = ref({ name: '', email: '', role: 'operator' })

function toast(msg: string, type = 'ok') { (window as any).__adminToast?.(msg, type) }

async function loadFlags() {
  try {
    const result = await configAdminApi.getFlags()
    for (const f of flags.value) {
      if (f.key in result) f.on = result[f.key] === 'true'
    }
  } catch (err) {
    logger.warn('admin:system-load-flags', err)
    toast('加载功能开关失败，请重试', 'warn')
  }
}

async function loadKv() {
  try {
    const result = await configAdminApi.getKv()
    for (const item of kvConfig.value) {
      if (item.key in result) item.val = result[item.key]
    }
  } catch (err) {
    logger.warn('admin:system-load-kv', err)
    toast('加载应用配置失败，请重试', 'warn')
  }
}

async function loadAudit() {
  if (auditLoaded) return
  loadingAudit.value = true
  try {
    auditLog.value = await auditLogAdminApi.list(50)
    auditLoaded = true
  } catch (err) {
    logger.warn('admin:system-load-audit', err)
    toast('操作失败，请重试', 'warn')
  } finally {
    loadingAudit.value = false
  }
}

watch(tab, (val) => {
  if (val === 'audit') loadAudit()
})

onMounted(() => {
  loadFlags()
  loadKv()
})

async function toggleFlag(f: FlagItem) {
  if (f.key === 'maintenance_mode' && !f.on) {
    if (!confirm('确定要开启维护模式吗？所有用户将看到维护提示页面。')) return
  }
  const next = !f.on
  try {
    await configAdminApi.setFlag(f.key, next)
    f.on = next
    toast(`${f.on ? '已启用：' : '已禁用：'}${f.label}`)
  } catch (err) {
    logger.warn('admin:system-toggle-flag', err)
    toast('操作失败，请重试', 'warn')
  }
}

function openKv(item: KvItem) { editingKv.value = { ...item }; showKvModal.value = true }

async function saveKv() {
  if (!editingKv.value) return
  try {
    await configAdminApi.setKv(editingKv.value.key, editingKv.value.val)
    const idx = kvConfig.value.findIndex(k => k.key === editingKv.value!.key)
    if (idx > -1) kvConfig.value[idx].val = editingKv.value.val
    showKvModal.value = false
    toast(`配置已更新：${editingKv.value.key}`)
  } catch (err) {
    logger.warn('admin:system-save-kv', err)
    toast('操作失败，请重试', 'warn')
  }
}

function addAdmin() {
  if (!newAdmin.value.name || !newAdmin.value.email) { toast('请填写姓名和邮箱', 'warn'); return }
  admins.value.push({
    id: Date.now(),
    name: newAdmin.value.name, email: newAdmin.value.email,
    role: newAdmin.value.role, lastLogin: '从未登录', active: true,
  })
  showAdminModal.value = false
  newAdmin.value = { name: '', email: '', role: 'operator' }
  toast('已添加管理员账号')
}

function removeAdmin(a: AdminAccount) {
  if (a.role === 'super') { toast('不能删除超级管理员', 'warn'); return }
  admins.value = admins.value.filter(x => x.id !== a.id)
  toast(`已移除管理员：${a.name}`)
}

function exportAudit() { toast('审计日志已导出') }
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">System</span>
        <span class="topbar-title-cn">系统设置</span>
      </div>
    </div>

    <div class="content-area">
      <div class="tabs">
        <div :class="['tab-item', tab === 'flags'  ? 'active' : '']" @click="tab = 'flags'">功能开关 <span class="tab-count">{{ flags.length }}</span></div>
        <div :class="['tab-item', tab === 'config' ? 'active' : '']" @click="tab = 'config'">应用配置</div>
        <div :class="['tab-item', tab === 'admins' ? 'active' : '']" @click="tab = 'admins'">管理员账号</div>
        <div :class="['tab-item', tab === 'audit'  ? 'active' : '']" @click="tab = 'audit'">审计日志</div>
      </div>

      <!-- Feature Flags -->
      <div v-if="tab === 'flags'">
        <div class="card">
          <div class="card-head"><span class="card-title">功能开关 · Feature Flags</span></div>
          <div class="card-body" style="padding:0;">
            <div v-for="f in flags" :key="f.key" class="kv-row" style="padding:14px 18px;">
              <div class="grow">
                <div class="kv-key">{{ f.label }}</div>
                <div class="kv-desc">{{ f.desc }}</div>
                <div class="mono t-ink3" style="margin-top:2px;font-size:9px;letter-spacing:.1em;">{{ f.key }}</div>
              </div>
              <div class="row gap-10">
                <span class="mono fs-10" :style="f.on ? 'color:var(--ok)' : 'color:var(--ink-3)'">{{ f.on ? 'ON' : 'OFF' }}</span>
                <label class="toggle">
                  <input type="checkbox" :checked="f.on" @change="toggleFlag(f)" />
                  <span class="toggle-track"></span>
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- App Config -->
      <div v-if="tab === 'config'">
        <div class="card">
          <div class="card-head">
            <span class="card-title">应用配置 · Key-Value</span>
            <span class="mono fs-10 t-ink3">点击行编辑</span>
          </div>
          <div class="card-body" style="padding:0;">
            <div
              v-for="item in kvConfig" :key="item.key"
              class="kv-row" style="padding:14px 18px;cursor:pointer;transition:background .12s;"
              @click="openKv(item)"
              @mouseenter="($event.currentTarget as HTMLElement).style.background = 'var(--bg-2)'"
              @mouseleave="($event.currentTarget as HTMLElement).style.background = ''"
            >
              <div class="grow">
                <div class="kv-key mono" style="font-size:12px;">{{ item.key }}</div>
                <div class="kv-desc">{{ item.desc }}</div>
              </div>
              <div class="row gap-10">
                <span class="mono fs-12 t-ink2" style="background:var(--bg-2);padding:3px 10px;border-radius:4px;">{{ item.val }}</span>
                <span class="t-ink3" style="font-size:12px;">›</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Admin Accounts -->
      <div v-if="tab === 'admins'">
        <div class="card">
          <div class="card-head">
            <span class="card-title">管理员账号</span>
            <button class="btn btn-primary btn-sm" @click="showAdminModal = true">+ 添加管理员</button>
          </div>
          <table class="tbl">
            <thead><tr><th>账号</th><th>角色</th><th>最后登录</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="a in admins" :key="a.id">
                <td>
                  <div class="mono fs-12 t-ink">{{ a.name }}</div>
                  <div class="mono fs-10 t-ink3">{{ a.email }}</div>
                </td>
                <td><span :class="['badge', roleClass[a.role] || 'neutral']">{{ roleLabels[a.role] || a.role }}</span></td>
                <td class="mono fs-11 t-ink3">{{ a.lastLogin }}</td>
                <td><span class="badge ok"><span class="badge-dot"></span>活跃</span></td>
                <td>
                  <button class="btn btn-danger btn-sm" @click="removeAdmin(a)" :disabled="a.role === 'super'">移除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Audit Log -->
      <div v-if="tab === 'audit'">
        <div class="card">
          <div class="card-head">
            <span class="card-title">操作审计日志 · 最近 {{ auditLog.length }} 条</span>
            <button class="btn btn-ghost btn-sm" @click="exportAudit">↓ 导出</button>
          </div>
          <div class="card-body" style="padding:6px 18px;">
            <div v-if="loadingAudit" class="mono fs-11 t-ink3" style="padding:18px 0;text-align:center;">加载中…</div>
            <div v-else v-for="log in auditLog" :key="log.id" class="audit-item">
              <div :class="['audit-dot', log.level]" style="margin-top:5px;"></div>
              <div class="grow">
                <div class="mono fs-12 t-ink">{{ log.operation }}</div>
                <div class="mono fs-10 t-ink3" style="margin-top:3px;">{{ log.operator }} · {{ log.module }}</div>
              </div>
              <div class="mono fs-10 t-ink3" style="white-space:nowrap;">{{ log.createdAt.slice(0,16).replace('T',' ') }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- KV edit modal -->
    <div class="modal-overlay" v-if="showKvModal" @click.self="showKvModal = false">
      <div class="modal" v-if="editingKv">
        <div class="modal-head">
          <span class="modal-title">编辑配置</span>
          <button class="modal-close" @click="showKvModal = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">Key</label>
            <input class="form-input" :value="editingKv.key" disabled style="opacity:.6;" />
          </div>
          <div class="form-group">
            <label class="form-label">说明</label>
            <div class="mono fs-11 t-ink3" style="padding:8px 0;">{{ editingKv.desc }}</div>
          </div>
          <div class="form-group">
            <label class="form-label">Value</label>
            <input class="form-input" v-model="editingKv.val" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="showKvModal = false">取消</button>
          <button class="btn btn-primary btn-sm" @click="saveKv">保存</button>
        </div>
      </div>
    </div>

    <!-- Add admin modal -->
    <div class="modal-overlay" v-if="showAdminModal" @click.self="showAdminModal = false">
      <div class="modal">
        <div class="modal-head">
          <span class="modal-title">添加管理员</span>
          <button class="modal-close" @click="showAdminModal = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">姓名</label>
            <input class="form-input" v-model="newAdmin.name" placeholder="管理员姓名" />
          </div>
          <div class="form-group">
            <label class="form-label">邮箱</label>
            <input class="form-input" v-model="newAdmin.email" placeholder="admin@moodfm.com" />
          </div>
          <div class="form-group">
            <label class="form-label">角色</label>
            <select class="form-select" v-model="newAdmin.role">
              <option value="operator">运营</option>
              <option value="analyst">分析师</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="showAdminModal = false">取消</button>
          <button class="btn btn-primary btn-sm" @click="addAdmin">创建账号</button>
        </div>
      </div>
    </div>
  </div>
</template>
