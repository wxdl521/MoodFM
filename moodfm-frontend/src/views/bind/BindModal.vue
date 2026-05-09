<template>
  <div :style="{
    background: 'var(--paper)',
    borderRadius: inline ? '20px' : '0',
    border: inline ? '1px solid var(--rule)' : 'none',
    padding: '28px',
  }">
    <div class="between" style="margin-bottom: 18px;">
      <div>
        <div class="meta">PLATFORM</div>
        <div class="display" style="font-size: 36px; margin-top: 4px;">
          <em>{{ platform }}</em>
        </div>
      </div>
      <button v-if="!inline" class="btn-pill" @click="$emit('close')" style="font-size: 18px; line-height: 1; padding: 0 14px;">×</button>
    </div>

    <div class="row" style="gap: 0; border-bottom: 1px solid var(--rule); margin-bottom: 24px;">
      <button
        v-for="t in tabs"
        :key="t.k"
        @click="$emit('update:tab', t.k as 'qr' | 'phone' | 'cookie')"
        :style="{
          background: 'transparent', border: 'none', padding: '12px 14px',
          cursor: 'pointer', position: 'relative',
          fontFamily: 'var(--serif-cn)', fontSize: '14px',
          color: tab === t.k ? 'var(--ink)' : 'var(--ink-3)',
        }">
        <span class="mono" style="font-size: 10px; letter-spacing: .16em; color: var(--ink-3); margin-right: 6px;">{{ t.e }}</span>
        {{ t.l }}
        <div v-if="tab === t.k" style="position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; background: var(--ink);" />
      </button>
    </div>

    <QRBlock v-if="tab === 'qr'" :platform="platform" />
    <PhoneBlock v-else-if="tab === 'phone'" :platform="platform" />
    <CookieBlock v-else-if="tab === 'cookie'" :platform="platform" />
  </div>
</template>

<script setup lang="ts">
import QRBlock from './QRBlock.vue'
import PhoneBlock from './PhoneBlock.vue'
import CookieBlock from './CookieBlock.vue'

defineProps<{
  platform: string
  tab: 'qr' | 'phone' | 'cookie'
  inline: boolean
}>()

defineEmits<{
  'update:tab': ['qr' | 'phone' | 'cookie']
  close: []
}>()

const tabs = [
  { k: 'qr', l: '扫码登录', e: 'QR' },
  { k: 'phone', l: '手机号', e: 'PHONE' },
  { k: 'cookie', l: 'Cookie', e: 'ADV' },
] as const
</script>
