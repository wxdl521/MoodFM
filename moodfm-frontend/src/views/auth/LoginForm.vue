<template>
  <div>
    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" style="color: var(--ink-2);">邮箱 / 手机 · EMAIL OR PHONE</label>
      </div>
      <input
        v-model="email"
        type="text"
        class="field"
        placeholder="you@somewhere.com"
        :style="{ borderColor: errors.email ? 'var(--mood-b)' : 'var(--rule)' }"
      />
      <div v-if="errors.email" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.email }}</div>
    </div>

    <div style="margin-bottom: 16px;">
      <div class="between" style="margin-bottom: 8px;">
        <label class="meta" style="color: var(--ink-2);">密码 · PASSWORD</label>
      </div>
      <input
        v-model="password"
        type="password"
        class="field"
        placeholder="·······"
        :style="{ borderColor: errors.password ? 'var(--mood-b)' : 'var(--rule)' }"
      />
      <div v-if="errors.password" class="meta" style="margin-top: 6px; color: var(--mood-b);">· {{ errors.password }}</div>
    </div>

    <label class="row" style="gap: 10px; font-family: var(--serif-cn); font-size: 13px; color: var(--ink-2); margin-bottom: 18px; cursor: pointer;"
      @click="remember = !remember">
      <span :style="{
        width: '16px', height: '16px', borderRadius: '4px',
        border: '1px solid var(--ink)',
        background: remember ? 'var(--ink)' : 'transparent',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
      }">
        <span v-if="remember" style="width: 8px; height: 8px; background: var(--bg); border-radius: 2px;" />
      </span>
      <span>30 天内记住我 · Remember me</span>
    </label>

    <button class="btn" style="width: 100%; height: 50px;" :disabled="loading" @click="submit">
      {{ loading ? '登录中...' : '登录 · SIGN IN →' }}
    </button>

    <div class="row" style="margin-top: 24px; gap: 12px; align-items: center;">
      <hr class="rule" style="flex: 1;" />
      <span class="meta">OR</span>
      <hr class="rule" style="flex: 1;" />
    </div>
    <div class="row" style="gap: 10px; margin-top: 20px;">
      <button class="btn-pill" style="flex: 1; height: 44px;">网易云账号</button>
      <button class="btn-pill" style="flex: 1; height: 44px;">QQ 音乐账号</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

const props = defineProps<{ loading: boolean }>()
const emit = defineEmits<{ submit: [{ email: string; password: string }] }>()

const email = ref('')
const password = ref('')
const remember = ref(true)
const errors = reactive({ email: '', password: '' })

function validate() {
  errors.email = ''
  errors.password = ''
  if (!email.value.trim()) errors.email = '请输入邮箱或手机号'
  if (!password.value) errors.password = '请输入密码'
  return !errors.email && !errors.password
}

function submit() {
  if (!validate()) return
  emit('submit', { email: email.value.trim(), password: password.value })
}
</script>
