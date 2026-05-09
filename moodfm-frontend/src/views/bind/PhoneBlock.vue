<template>
  <div>
    <div style="margin-bottom: 14px;">
      <div class="meta" style="margin-bottom: 8px;">手机号 · PHONE</div>
      <input v-model="phone" class="field" placeholder="+86 138 ····" type="tel" />
    </div>

    <div style="margin-bottom: 18px; position: relative;">
      <div class="meta" style="margin-bottom: 8px;">验证码 · OTP</div>
      <input v-model="code" class="field" placeholder="6 位" style="padding-right: 130px;" />
      <button @click="sendCode" :style="{
        position: 'absolute', right: '6px', bottom: '6px',
        height: '36px', padding: '0 14px', borderRadius: '999px',
        background: countdown > 0 ? 'transparent' : 'var(--ink)',
        color: countdown > 0 ? 'var(--ink-3)' : 'var(--bg)',
        border: countdown > 0 ? '1px solid var(--rule)' : 'none',
        cursor: countdown > 0 ? 'not-allowed' : 'pointer',
        fontFamily: 'var(--mono)', fontSize: '11px', letterSpacing: '.14em', textTransform: 'uppercase',
      }">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button>
    </div>

    <div v-if="error" class="meta" style="margin-bottom: 12px; color: var(--mood-b);">· {{ error }}</div>

    <button class="btn" style="width: 100%; height: 48px;" :disabled="loading" @click="submit">
      {{ loading ? '验证中...' : '验证并绑定 →' }}
    </button>
    <div class="meta" style="margin-top: 14px; color: var(--ink-3);">
      · 与 App 内手机号一致 · 验证后立即同步曲库
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { platformApi } from '@/api/platform'

const props = defineProps<{ platform: string }>()

const phone = ref('')
const code = ref('')
const countdown = ref(0)
const loading = ref(false)
const error = ref('')

let timer: ReturnType<typeof setInterval> | null = null

async function sendCode() {
  if (countdown.value > 0 || !phone.value.trim()) return
  try {
    await platformApi.sendPhoneCode(props.platform, phone.value.trim())
  } catch {}
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function submit() {
  error.value = ''
  if (!phone.value.trim()) { error.value = '请输入手机号'; return }
  if (!code.value.trim()) { error.value = '请输入验证码'; return }
  loading.value = true
  try {
    await platformApi.bindPhone(props.platform, { phone: phone.value.trim(), code: code.value.trim() })
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '绑定失败，请重试'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
