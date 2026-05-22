<template>
  <div :data-theme="uiStore.theme" :data-mood="uiStore.moodPreset">
    <Transition name="page" mode="out-in">
      <RouterView :key="$route.path" />
    </Transition>

    <!-- Global notification toasts -->
    <div v-if="notifications.length" style="position:fixed;top:80px;right:24px;z-index:200;display:flex;flex-direction:column;gap:8px;">
      <div
        v-for="(n, i) in notifications"
        :key="i"
        style="background:var(--paper);border:1px solid var(--mood-b);border-radius:12px;padding:14px 18px;
               max-width:320px;box-shadow:0 4px 20px rgba(0,0,0,.12);display:flex;align-items:center;gap:10px;"
      >
        <div style="flex:1;">
          <div style="font-family:var(--serif-cn);font-size:14px;font-weight:500;">
            {{ n.type === 'cookie_invalid' ? '平台凭证即将失效' : '通知' }}
          </div>
          <div style="font-family:var(--serif-cn);font-size:12px;color:var(--ink-2);margin-top:2px;">
            {{ n.type === 'cookie_invalid' ? `${n.platform} 平台的登录凭证即将过期，请重新绑定。` : n.message }}
          </div>
        </div>
        <button
          v-if="n.type === 'cookie_invalid'"
          style="font-family:var(--serif-cn);font-size:12px;padding:4px 10px;border:1px solid var(--rule);
                 border-radius:6px;background:transparent;cursor:pointer;white-space:nowrap;"
          @click="dismiss(i); $router.push('/bind')"
        >去绑定</button>
        <button
          style="background:transparent;border:none;cursor:pointer;color:var(--ink-3);font-size:16px;padding:0 4px;"
          @click="dismiss(i)"
        >×</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch, onMounted } from 'vue'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { useNotifications } from '@/composables/useNotifications'

const uiStore = useUiStore()
const authStore = useAuthStore()
const { notifications, connect, dismiss } = useNotifications()

// Validate token on app start
onMounted(() => { authStore.validate() })

// Connect notification WebSocket when user is logged in
watch(() => authStore.user?.id, (id) => {
  if (id) connect(id)
}, { immediate: true })
</script>

<style>
/* ── Page transitions ──────────────────────────────── */
.page-enter-active {
  animation: page-blur-in 0.3s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.page-leave-active {
  animation: page-fade-out 0.15s ease both;
}

@keyframes page-blur-in {
  from {
    opacity: 0;
    filter: blur(3px);
    transform: scale(0.985);
  }
  to {
    opacity: 1;
    filter: blur(0);
    transform: scale(1);
  }
}

@keyframes page-fade-out {
  from { opacity: 1; }
  to   { opacity: 0; }
}
</style>
