<template>
  <div>
    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" :style="{ color: errors.username ? 'var(--mood-b)' : 'var(--ink-2)' }">昵称 · NAME</label>
      </div>
      <input v-model="username" type="text" class="field" placeholder="你叫什么"
        :style="{ borderColor: errors.username ? 'var(--mood-b)' : 'var(--rule)' }" />
      <div v-if="errors.username" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.username }}</div>
    </div>

    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" :style="{ color: errors.email ? 'var(--mood-b)' : 'var(--ink-2)' }">邮箱 · EMAIL</label>
      </div>
      <input v-model="email" type="text" class="field" placeholder="you@somewhere.com"
        :style="{ borderColor: errors.email ? 'var(--mood-b)' : 'var(--rule)' }" />
      <div v-if="errors.email" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.email }}</div>
    </div>

    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" :style="{ color: errors.password ? 'var(--mood-b)' : 'var(--ink-2)' }">密码 · PASSWORD</label>
        <span class="meta" style="color: var(--ink-3);">≥ 8 位 · 字母+数字</span>
      </div>
      <input v-model="password" type="password" class="field" placeholder="至少 8 位"
        :style="{ borderColor: errors.password ? 'var(--mood-b)' : 'var(--rule)' }" />
      <div v-if="errors.password" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.password }}</div>
    </div>

    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" :style="{ color: errors.confirm ? 'var(--mood-b)' : 'var(--ink-2)' }">确认密码 · CONFIRM</label>
      </div>
      <input v-model="confirm" type="password" class="field" placeholder="再输一次"
        :style="{ borderColor: errors.confirm ? 'var(--mood-b)' : 'var(--rule)' }" />
      <div v-if="errors.confirm" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.confirm }}</div>
    </div>

    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" :style="{ color: errors.code ? 'var(--mood-b)' : 'var(--ink-2)' }">验证码 · ONE-TIME CODE</label>
      </div>
      <div style="position: relative;">
        <input v-model="code" type="text" class="field" placeholder="6 位"
          :style="{ paddingRight: '130px', borderColor: errors.code ? 'var(--mood-b)' : 'var(--rule)' }" />
        <div style="position: absolute; right: 6px; top: 6px; bottom: 6px;">
          <button @click="sendCode" :style="{
            height: '36px', padding: '0 14px', borderRadius: '999px',
            background: countdown > 0 ? 'transparent' : 'var(--ink)',
            color: countdown > 0 ? 'var(--ink-3)' : 'var(--bg)',
            border: countdown > 0 ? '1px solid var(--rule)' : 'none',
            fontFamily: 'var(--mono)', fontSize: '11px', letterSpacing: '.14em', textTransform: 'uppercase',
            cursor: countdown > 0 ? 'not-allowed' : 'pointer',
          }">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button>
        </div>
      </div>
      <div v-if="errors.code" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.code }}</div>
    </div>

    <label class="row" style="gap: 10px; font-family: var(--serif-cn); font-size: 13px; color: var(--ink-2); margin-top: 4px; margin-bottom: 16px; cursor: pointer;"
      @click="agree = !agree">
      <span :style="{
        width: '16px', height: '16px', borderRadius: '4px',
        border: '1px solid var(--ink)',
        background: agree ? 'var(--ink)' : 'transparent',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
      }">
        <span v-if="agree" style="width: 8px; height: 8px; background: var(--bg); border-radius: 2px;" />
      </span>
      <span>
        我已阅读并同意
        <span style="color: var(--ink); text-decoration: underline;">服务条款</span>
        与
        <span style="color: var(--ink); text-decoration: underline;">隐私政策</span>
      </span>
    </label>

    <button class="btn" style="width: 100%; height: 50px;" :disabled="loading" @click="submit">
      {{ loading ? '注册中...' : '注册并启动 · CREATE ACCOUNT →' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'

const props = defineProps<{ loading: boolean }>()
const emit = defineEmits<{ submit: [{ username: string; email: string; password: string }] }>()

const username = ref('')
const email = ref('')
const password = ref('')
const confirm = ref('')
const code = ref('')
const agree = ref(true)
const countdown = ref(0)
const errors = reactive({ username: '', email: '', password: '', confirm: '', code: '' })

let timer: ReturnType<typeof setInterval> | null = null

function sendCode() {
  if (countdown.value > 0) return
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

onUnmounted(() => { if (timer) clearInterval(timer) })

function validate() {
  errors.username = ''
  errors.email = ''
  errors.password = ''
  errors.confirm = ''
  errors.code = ''
  if (!username.value.trim()) errors.username = '请输入昵称'
  if (!email.value.trim()) errors.email = '请输入邮箱'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) errors.email = '邮箱格式不正确'
  if (!password.value) errors.password = '请输入密码'
  else if (password.value.length < 8) errors.password = '密码至少 8 位'
  if (!confirm.value) errors.confirm = '请确认密码'
  else if (confirm.value !== password.value) errors.confirm = '两次密码不一致'
  if (!agree.value) errors.code = '请同意服务条款'
  return !errors.username && !errors.email && !errors.password && !errors.confirm && !errors.code
}

function submit() {
  if (!validate()) return
  emit('submit', { username: username.value.trim(), email: email.value.trim(), password: password.value })
}
</script>
