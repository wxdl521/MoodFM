<template>
  <div style="min-height: 100vh; background: var(--bg); padding-bottom: 100px;">

    <div class="page-pad" style="max-width: 880px;">
      <div class="meta">NOTIFICATIONS · 通知</div>
      <h1 class="display" style="font-size: clamp(52px, 7vw, 108px); margin: 10px 0 0;">Notify.</h1>
      <div class="display-cn" style="font-size: clamp(18px, 2vw, 28px); color: var(--ink-2); margin-bottom: 32px;">
        选择你想收到的通知
      </div>

      <div v-if="loading" class="meta" style="color: var(--ink-3); padding: 24px 0;">加载中…</div>

      <div v-else>
        <!-- Notification toggles -->
        <div style="border: 1px solid var(--rule); border-radius: 14px; background: var(--paper); overflow: hidden; margin-bottom: 32px;">
          <div class="setting-row" style="border-bottom: 1px solid var(--rule);">
            <div style="flex: 1; min-width: 0;">
              <div style="font-family: var(--serif-cn); font-size: 15px;">推送周报</div>
              <div class="meta" style="margin-top: 2px; color: var(--ink-3);">· 每周推送你的收听报告</div>
            </div>
            <button class="toggle-btn" :class="{ 'toggle-btn--on': settings.weeklyReport }" @click="toggle('weeklyReport')">
              <span class="toggle-thumb" :style="{ transform: settings.weeklyReport ? 'translateX(18px)' : 'translateX(0)' }" />
            </button>
          </div>

          <div class="setting-row" style="border-bottom: 1px solid var(--rule);">
            <div style="flex: 1; min-width: 0;">
              <div style="font-family: var(--serif-cn); font-size: 15px;">Cookie 过期提醒</div>
              <div class="meta" style="margin-top: 2px; color: var(--ink-3);">· 平台 Cookie 即将过期时提醒</div>
            </div>
            <button class="toggle-btn" :class="{ 'toggle-btn--on': settings.cookieExpiry }" @click="toggle('cookieExpiry')">
              <span class="toggle-thumb" :style="{ transform: settings.cookieExpiry ? 'translateX(18px)' : 'translateX(0)' }" />
            </button>
          </div>

          <div class="setting-row">
            <div style="flex: 1; min-width: 0;">
              <div style="font-family: var(--serif-cn); font-size: 15px;">新功能通知</div>
              <div class="meta" style="margin-top: 2px; color: var(--ink-3);">· 有新功能上线时通知你</div>
            </div>
            <button class="toggle-btn" :class="{ 'toggle-btn--on': settings.newFeatures }" @click="toggle('newFeatures')">
              <span class="toggle-thumb" :style="{ transform: settings.newFeatures ? 'translateX(18px)' : 'translateX(0)' }" />
            </button>
          </div>
        </div>

        <!-- Weekly report schedule -->
        <div v-if="settings.weeklyReport" style="border: 1px solid var(--rule); border-radius: 14px; background: var(--paper); overflow: hidden;">
          <div class="setting-row" style="border-bottom: 1px solid var(--rule);">
            <div style="flex: 1; min-width: 0;">
              <div style="font-family: var(--serif-cn); font-size: 15px;">周报推送日</div>
              <div class="meta" style="margin-top: 2px; color: var(--ink-3);">· 选择每周几推送</div>
            </div>
            <div class="segment-ctrl">
              <button
                v-for="(day, i) in dayOptions"
                :key="i"
                class="segment-opt"
                :class="{ 'segment-opt--active': (settings.weeklyReportDay ?? 0) === i }"
                @click="setDay(i)"
              >{{ day }}</button>
            </div>
          </div>

          <div class="setting-row">
            <div style="flex: 1; min-width: 0;">
              <div style="font-family: var(--serif-cn); font-size: 15px;">周报推送时间</div>
              <div class="meta" style="margin-top: 2px; color: var(--ink-3);">· 选择几点推送</div>
            </div>
            <div class="segment-ctrl">
              <button
                v-for="h in hourOptions"
                :key="h.value"
                class="segment-opt"
                :class="{ 'segment-opt--active': (settings.weeklyReportHour ?? 9) === h.value }"
                @click="setHour(h.value)"
              >{{ h.label }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import { userApi } from '@/api/user'
import { logger } from '@/utils/logger'

const loading = ref(true)

const settings = reactive({
  weeklyReport: true,
  cookieExpiry: true,
  newFeatures: true,
  weeklyReportDay: 0,
  weeklyReportHour: 9,
})

const dayOptions = ['一', '二', '三', '四', '五', '六', '日']

const hourOptions = [
  { value: 6, label: '6:00' },
  { value: 8, label: '8:00' },
  { value: 9, label: '9:00' },
  { value: 12, label: '12:00' },
  { value: 18, label: '18:00' },
  { value: 21, label: '21:00' },
]

function toggle(key: 'weeklyReport' | 'cookieExpiry' | 'newFeatures') {
  settings[key] = !settings[key]
  save()
}

function setDay(day: number) {
  settings.weeklyReportDay = day
  save()
}

function setHour(hour: number) {
  settings.weeklyReportHour = hour
  save()
}

let saveTimer: ReturnType<typeof setTimeout>
function save() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(async () => {
    try {
      await userApi.updateNotificationSettings({
        weeklyReport: settings.weeklyReport,
        cookieExpiry: settings.cookieExpiry,
        newFeatures: settings.newFeatures,
        weeklyReportDay: settings.weeklyReportDay,
        weeklyReportHour: settings.weeklyReportHour,
      })
    } catch (err) {
      // 用户主动操作：通知设置保存失败，本页无 toast 组件，先 logger 记录
      logger.warn('settings:notifications-save', err)
    }
  }, 400)
}

onMounted(async () => {
  try {
    const res = await userApi.getNotificationSettings()
    if (res) {
      settings.weeklyReport = res.weeklyReport ?? true
      settings.cookieExpiry = res.cookieExpiry ?? true
      settings.newFeatures = res.newFeatures ?? true
      settings.weeklyReportDay = res.weeklyReportDay ?? 0
      settings.weeklyReportHour = res.weeklyReportHour ?? 9
    }
  } catch (err) {
    // silent: 通知设置加载失败时回退到默认值
    logger.warn('settings:notifications-load', err)
  } finally {
    loading.value = false
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
  flex-wrap: wrap;
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
</style>
