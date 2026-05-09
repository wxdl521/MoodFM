<template>
  <div :style="{
    padding: '20px',
    border: '1px solid var(--rule)',
    borderRadius: '18px',
    background: 'var(--paper)',
    position: 'relative',
    opacity: disabled ? 0.5 : 1,
  }">
    <div class="row" style="gap: 14px;">
      <div :style="{
        width: '48px', height: '48px', borderRadius: '50%',
        background: bound ? 'linear-gradient(135deg, var(--mood-a), var(--mood-c))' : 'var(--bg-2)',
        color: bound ? '#fff' : 'var(--ink-3)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'var(--serif-cn)', fontSize: '20px', fontWeight: 600,
        flexShrink: 0,
      }">{{ logo }}</div>
      <div style="flex: 1; min-width: 0;">
        <div class="row" style="gap: 8px;">
          <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '18px', fontWeight: 500 }">{{ name }}</div>
          <span v-if="bound" class="meta" style="padding: 2px 8px; border: 1px solid var(--ink); border-radius: 999px; color: var(--ink);">BOUND</span>
        </div>
        <div class="meta" style="margin-top: 2px;">{{ en }}</div>
      </div>
      <button v-if="!disabled" @click="$emit('bind')"
        :class="bound ? 'btn-pill' : 'btn'"
        style="height: 38px; padding: 0 16px; flex-shrink: 0;">
        {{ bound ? '重新绑定' : '绑定 →' }}
      </button>
    </div>
    <div class="row" style="margin-top: 16px; gap: 24px; padding-top: 16px; border-top: 1px dashed var(--rule);">
      <div>
        <div class="meta" style="color: var(--ink-3);">状态</div>
        <div :style="{ fontFamily: 'var(--mono)', fontSize: '13px', marginTop: '4px', color: 'var(--ink)' }">{{ status }}</div>
      </div>
      <div v-if="bound && user">
        <div class="meta" style="color: var(--ink-3);">账号</div>
        <div :style="{ fontFamily: 'var(--mono)', fontSize: '13px', marginTop: '4px', color: 'var(--ink)' }">{{ user }}</div>
      </div>
      <div v-if="bound && cookieDays">
        <div class="meta" :style="{ color: cookieLow ? 'var(--mood-b)' : 'var(--ink-3)' }">Cookie 剩余</div>
        <div :style="{ fontFamily: 'var(--mono)', fontSize: '13px', marginTop: '4px', color: cookieLow ? 'var(--mood-b)' : 'var(--ink)' }">{{ cookieDays }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  logo: string
  name: string
  en: string
  status: string
  bound?: boolean
  disabled?: boolean
  user?: string
  cookieDays?: string
}>()

defineEmits<{ bind: [] }>()

const cookieLow = computed(() => props.cookieDays ? parseInt(props.cookieDays) < 7 : false)
</script>
