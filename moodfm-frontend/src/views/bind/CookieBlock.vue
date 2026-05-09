<template>
  <div>
    <div style="margin-bottom: 14px;">
      <div class="between" style="margin-bottom: 8px;">
        <div class="meta">COOKIE</div>
        <a class="meta" style="color: var(--ink-2); text-decoration: underline; cursor: pointer;">如何获取 →</a>
      </div>
      <textarea
        v-model="cookieValue"
        class="field"
        placeholder="MUSIC_U=...; __csrf=...; NMTID=...;"
        :style="{ minHeight: '160px', fontFamily: 'var(--mono)', fontSize: '12px' }"
      />
    </div>

    <div v-if="error" class="meta" style="margin-bottom: 12px; color: var(--mood-b);">· {{ error }}</div>

    <button class="btn" style="width: 100%; height: 48px;" :disabled="loading" @click="submit">
      {{ loading ? '验证中...' : '验证并保存 →' }}
    </button>
    <div class="meta" style="margin-top: 14px; color: var(--ink-3); line-height: 1.6;">
      · 高级方式 · 适合扫码与短信都不便时使用<br />
      · Cookie 会本地加密 · 服务端不留存明文
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { platformApi } from '@/api/platform'

const props = defineProps<{ platform: string }>()

const cookieValue = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!cookieValue.value.trim()) { error.value = '请粘贴 Cookie 内容'; return }
  loading.value = true
  try {
    await platformApi.bindCookie(props.platform, cookieValue.value.trim())
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '验证失败，请检查 Cookie 是否有效'
  } finally {
    loading.value = false
  }
}
</script>
