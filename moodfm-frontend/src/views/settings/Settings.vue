<template>
  <div style="min-height: 100vh; background: var(--bg); padding-bottom: 100px;">

    <div class="page-pad" style="max-width: 880px;">
      <div class="meta">CONTROL ROOM · 调台室</div>
      <h1 class="display" style="font-size: clamp(52px, 7vw, 108px); margin: 10px 0 0;">Settings.</h1>
      <div class="display-cn" style="font-size: clamp(18px, 2vw, 28px); color: var(--ink-2); margin-bottom: 32px;">
        所有的旋钮 · 都在这里
      </div>

      <div v-if="loading" class="meta" style="color: var(--ink-3); padding: 24px 0;">加载中…</div>

      <div v-else>
        <div
          v-for="(g, gi) in settingGroups"
          :key="gi"
          style="margin-bottom: 32px;"
        >
          <div class="row" style="gap: 12px; align-items: baseline; margin-bottom: 14px;">
            <span class="meta">№ {{ String(gi + 1).padStart(2, '0') }}</span>
            <span class="serif-en" :style="{ fontSize: 'clamp(24px, 2.5vw, 36px)' }">
              {{ g.en.toLowerCase() }}<span style="color: var(--ink-3);">.</span>
            </span>
            <span :style="{ fontFamily: 'var(--serif-cn)', fontSize: '16px', color: 'var(--ink-2)' }">{{ g.title }}</span>
          </div>

          <div style="border: 1px solid var(--rule); border-radius: 14px; background: var(--paper); overflow: hidden;">
            <div
              v-for="(item, ii) in g.items"
              :key="ii"
              class="setting-row"
              :style="{ borderBottom: ii < g.items.length - 1 ? '1px solid var(--rule)' : 'none' }"
            >
              <div style="flex: 1; min-width: 0;">
                <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '15px', color: item.danger ? 'var(--mood-b)' : 'var(--ink)' }">{{ item.l }}</div>
                <div v-if="item.sub" class="meta" style="margin-top: 2px; color: var(--ink-3);">· {{ item.sub }}</div>
              </div>

              <button
                v-if="item.kind === 'toggle'"
                class="toggle-btn"
                :class="{ 'toggle-btn--on': item.value }"
                @click="toggleItem(item)"
              >
                <span class="toggle-thumb" :style="{ transform: item.value ? 'translateX(18px)' : 'translateX(0)' }" />
              </button>

              <div v-else-if="item.kind === 'segment'" class="segment-ctrl">
                <button
                  v-for="(opt, oi) in item.options"
                  :key="oi"
                  class="segment-opt"
                  :class="{ 'segment-opt--active': item.segValue === oi }"
                  @click="setSegment(item, oi)"
                >{{ opt }}</button>
              </div>

              <div v-else-if="item.kind === 'slider'" style="display: flex; align-items: center; gap: 10px; flex-shrink: 0;">
                <span class="mono" style="font-size: 12px; color: var(--ink-2); min-width: 28px; text-align: right;">{{ item.sliderValue }}s</span>
                <input
                  type="range"
                  :min="item.min"
                  :max="item.max"
                  :value="item.sliderValue"
                  style="width: 100px; accent-color: var(--ink);"
                  @input="setSlider(item, ($event.target as HTMLInputElement).valueAsNumber)"
                />
              </div>

              <button
                v-else-if="item.kind === 'link'"
                class="link-ctrl"
                @click="item.action && item.action()"
              >
                <span v-if="item.hint" class="meta" style="color: var(--ink-2);">{{ item.hint }}</span>
                <span style="font-size: 14px; color: var(--ink-3);">›</span>
              </button>

              <span v-else-if="item.kind === 'value'" class="mono" style="font-size: 12px; color: var(--ink-3); flex-shrink: 0;">{{ item.hint }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showConfirm"
      style="position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; padding: 24px;"
      @click.self="showConfirm = false"
    >
      <div style="background: var(--paper); border: 1px solid var(--rule); border-radius: 20px; padding: 32px; max-width: 380px; width: 100%;">
        <div class="meta" style="color: var(--mood-b);">危险操作 · DANGER ZONE</div>
        <h3 :style="{ fontFamily: 'var(--serif-cn)', fontSize: '20px', margin: '12px 0 8px' }">{{ confirmTitle }}</h3>
        <p :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', color: 'var(--ink-2)', lineHeight: 1.6, margin: '0 0 24px' }">{{ confirmBody }}</p>
        <div class="row" style="gap: 10px; justify-content: flex-end;">
          <button class="btn-pill" @click="showConfirm = false">取消</button>
          <button class="btn" style="background: var(--mood-b); border-color: var(--mood-b); color: #fff;" @click="confirmAction">确认</button>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import { historyApi } from '@/api/history'
import type { MoodPreset, Theme } from '@/types'
import { logger } from '@/utils/logger'

const router = useRouter()
const uiStore = useUiStore()
const authStore = useAuthStore()

const loading = ref(false)
const showConfirm = ref(false)
const confirmTitle = ref('')
const confirmBody = ref('')
let pendingConfirm: (() => void) | null = null

function confirmAction() {
  pendingConfirm?.()
  showConfirm.value = false
  pendingConfirm = null
}

function askConfirm(title: string, body: string, action: () => void) {
  confirmTitle.value = title
  confirmBody.value = body
  pendingConfirm = action
  showConfirm.value = true
}

const THEME_OPTIONS = ['明', '暗', '系统']
const QUALITY_OPTIONS = ['标准', '高品', '无损']
const PRESET_OPTIONS: MoodPreset[] = ['dusk', 'melancholy', 'energetic', 'focused', 'calm']
const PRESET_LABELS = ['黄昏', '忧郁', '高能', '专注', '平静']

interface SettingItem {
  l: string
  kind: 'toggle' | 'segment' | 'slider' | 'link' | 'value'
  sub?: string
  danger?: boolean
  value?: boolean
  options?: string[]
  segValue?: number
  min?: number
  max?: number
  sliderValue?: number
  hint?: string
  action?: () => void
}

interface SettingGroup {
  title: string
  en: string
  items: SettingItem[]
}

const settingGroups = ref<SettingGroup[]>([
  {
    title: '账号',
    en: 'ACCOUNT',
    items: [
      { l: '个人资料', kind: 'link', hint: '', action: () => router.push('/profile') },
      { l: '平台管理', kind: 'link', hint: '网易云 · QQ 音乐', action: () => router.push('/bind') },
      { l: '修改密码', kind: 'link', hint: '', action: () => router.push('/profile') },
    ],
  },
  {
    title: '外观',
    en: 'APPEARANCE',
    items: [
      {
        l: '主题',
        kind: 'segment',
        options: THEME_OPTIONS,
        segValue: uiStore.theme === 'dark' ? 1 : 0,
      },
      {
        l: '心情色调',
        kind: 'segment',
        options: PRESET_LABELS,
        segValue: PRESET_OPTIONS.indexOf(uiStore.moodPreset),
      },
      { l: '减弱动效', kind: 'toggle', value: false, sub: '流体 blob、过渡都收敛' },
    ],
  },
  {
    title: '电台偏好',
    en: 'RADIO PREFERENCES',
    items: [
      { l: '流派偏好', kind: 'link', hint: 'Ambient · Folk · +5', action: () => {} },
      { l: '默认场景', kind: 'link', hint: '深夜', action: () => {} },
      { l: '露出冷门曲目', kind: 'toggle', value: true, sub: '让 AI 偶尔推荐你曲库外的歌' },
      { l: '自动续播', kind: 'toggle', value: true, sub: '听完一档自动开下一档' },
    ],
  },
  {
    title: '声音',
    en: 'AUDIO',
    items: [
      { l: '音质', kind: 'segment', options: QUALITY_OPTIONS, segValue: 1 },
      { l: '交叉渐变时长', kind: 'slider', min: 0, max: 12, sliderValue: 4 },
      { l: '按时间均衡', kind: 'toggle', value: true, sub: '夜深时自动收紧低音' },
    ],
  },
  {
    title: '隐私',
    en: 'PRIVACY',
    items: [
      { l: '通知设置', kind: 'link', hint: '周报 · 提醒', action: () => router.push('/settings/notifications') },
      { l: '分享情绪周报到公开主页', kind: 'toggle', value: false },
      { l: '匿名贡献心情数据', kind: 'toggle', value: true, sub: '帮助改善推荐 · 永不识别个人' },
      { l: '黑名单管理', kind: 'link', hint: '12 首', action: () => router.push('/settings/blacklist') },
      { l: '导出我的数据 (JSON)', kind: 'link', sub: '下载全量播放、反馈、心情数据', action: () => window.open('/api/export/json') },
      { l: '导出播放记录 (CSV)', kind: 'link', sub: '导出 CSV 格式播放历史', action: () => window.open('/api/export/csv') },
      { l: '清除播放历史', kind: 'link', danger: true, hint: '', action: () => askConfirm('清除播放历史', '此操作无法撤销，你的全部播放历史将被永久删除。', async () => { await historyApi.clear() }) },
      { l: '清除所有数据', kind: 'link', danger: true, sub: '删除全部播放、反馈、心情数据（保留账号）', action: () => askConfirm('清除所有数据', '此操作将永久删除你的所有播放记录、反馈事件、心情会话、周报和平台绑定。账号本身会保留。', async () => { await userApi.deleteAllData() }) },
    ],
  },
  {
    title: '关于',
    en: 'ABOUT',
    items: [
      { l: '版本', kind: 'value', hint: '1.4.2 (2026.05)' },
      { l: '服务条款 · 隐私政策', kind: 'link', hint: '', action: () => {} },
      { l: '反馈与建议', kind: 'link', hint: '', action: () => {} },
      { l: '退出登录', kind: 'link', danger: true, hint: '', action: () => askConfirm('退出登录', '确认退出当前账号？', () => authStore.logout()) },
    ],
  },
])

function toggleItem(item: SettingItem) {
  item.value = !item.value
  savePreferences()
}

function setSegment(item: SettingItem, oi: number) {
  item.segValue = oi
  const groupIdx = settingGroups.value.findIndex(g => g.items.includes(item))
  if (groupIdx === 1) {
    if (item.l === '主题') {
      uiStore.setTheme(oi === 1 ? 'dark' : 'light')
    } else if (item.l === '心情色调') {
      uiStore.setMoodPreset(PRESET_OPTIONS[oi])
    }
  }
  savePreferences()
}

function setSlider(item: SettingItem, value: number) {
  item.sliderValue = value
  savePreferences()
}

let saveTimer: ReturnType<typeof setTimeout>
function savePreferences() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    const audioGroup = settingGroups.value.find(g => g.en === 'AUDIO')
    const prefGroup = settingGroups.value.find(g => g.en === 'RADIO PREFERENCES')
    const autoPlay = prefGroup?.items.find(i => i.l === '自动续播')?.value ?? true
    const crossfade = audioGroup?.items.find(i => i.l === '交叉渐变时长')?.sliderValue ?? 4
    try {
      await userApi.updatePreferences({ autoPlay, crossfadeDuration: crossfade, theme: uiStore.theme })
    } catch (err) {
      // 用户主动操作：偏好保存失败，本页无 toast 组件，先 logger 记录
      logger.warn('settings:save-preferences', err)
    }
  }, 600)
}

onMounted(async () => {
  try {
    const res = await userApi.getPreferences()
    if (res) {
      const prefs = res
      const prefGroup = settingGroups.value.find(g => g.en === 'RADIO PREFERENCES')
      const autoPlayItem = prefGroup?.items.find(i => i.l === '自动续播')
      if (autoPlayItem) autoPlayItem.value = prefs.autoPlay ?? true
      const audioGroup = settingGroups.value.find(g => g.en === 'AUDIO')
      const crossfadeItem = audioGroup?.items.find(i => i.l === '交叉渐变时长')
      if (crossfadeItem) crossfadeItem.sliderValue = prefs.crossfadeDuration ?? 4
    }
  } catch (err) {
    // silent: 偏好加载失败时回退默认值
    logger.warn('settings:load-preferences', err)
  }
})
</script>

<style scoped>
.page-pad {
  padding: 40px 56px 0;
}

@media (max-width: 768px) {
  .page-pad {
    padding: 24px 18px 0;
  }
}

.setting-row {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.toggle-btn {
  width: 42px;
  height: 24px;
  border-radius: 999px;
  padding: 2px;
  border: none;
  background: var(--rule);
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
  transition: background 0.2s;
}

.toggle-btn--on {
  background: var(--ink);
}

.toggle-thumb {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--paper);
  transition: transform 0.2s;
}

.segment-ctrl {
  display: flex;
  gap: 0;
  border: 1px solid var(--rule);
  border-radius: 999px;
  padding: 2px;
  flex-shrink: 0;
}

.segment-opt {
  padding: 4px 10px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  background: transparent;
  color: var(--ink-2);
  font-family: var(--serif-cn);
  font-size: 12px;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}

.segment-opt--active {
  background: var(--ink);
  color: var(--bg);
}

.link-ctrl {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
  color: var(--ink-3);
  padding: 0;
}
</style>
