<template>
  <div :data-theme="uiStore.theme" :data-mood="uiStore.moodPreset" style="overflow-x:hidden;min-height:100vh;">
    <NavBar v-if="route.meta.showNav" />
    <Transition :name="transitionName" mode="out-in">
      <RouterView :key="routerKey" />
    </Transition>

    <!-- Global mini player: shown on every page except auth/onboarding/player/admin (controlled by route.meta.hideMiniPlayer) -->
    <MiniPlayer v-if="showMiniPlayer" />

    <!-- Global notification toasts -->
    <div v-if="notifications.length" style="position:fixed;top:80px;right:24px;z-index:200;display:flex;flex-direction:column;gap:8px;">
      <div
        v-for="n in notifications"
        :key="n.id"
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
          @click="dismiss(n.id); $router.push('/bind')"
        >去绑定</button>
        <button
          style="background:transparent;border:none;cursor:pointer;color:var(--ink-3);font-size:16px;padding:0 4px;"
          @click="dismiss(n.id)"
        >×</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { useNotifications } from '@/composables/useNotifications'
import { useNavDirection } from '@/composables/useNavDirection'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'

const route = useRoute()
const uiStore = useUiStore()
const authStore = useAuthStore()
const { notifications, connect, dismiss } = useNotifications()
const { transitionName } = useNavDirection()

// Global mini player visibility — opt-out via route meta
const showMiniPlayer = computed(() => !route.meta.hideMiniPlayer)

// 顶层 RouterView 的 key：admin 子路由共用 '/admin'，让 AdminLayout（含侧边栏）
// 在 admin→admin 切换时不被销毁重建；其它路由按 path 触发顶层 transition。
const routerKey = computed(() =>
  route.path.startsWith('/admin') ? '/admin' : route.path,
)

onMounted(() => { authStore.validate() })

watch(() => authStore.user?.id, (id) => {
  if (id) connect(id)
}, { immediate: true })
</script>

<style>
/* ── Slide-left: entering a deeper / forward page ──────────── */
.slide-left-enter-active {
  transition: transform 320ms cubic-bezier(0.16, 1, 0.3, 1),
              opacity   320ms cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-left-leave-active {
  transition: transform 200ms ease,
              opacity   200ms ease;
}
.slide-left-enter-from {
  transform: translateX(100%);
  opacity: 0;
}
.slide-left-leave-to {
  transform: translateX(-25%);
  opacity: 0;
}

/* ── Slide-right: returning to a shallower / previous page ──── */
.slide-right-enter-active {
  transition: transform 320ms cubic-bezier(0.16, 1, 0.3, 1),
              opacity   320ms cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-right-leave-active {
  transition: transform 200ms ease,
              opacity   200ms ease;
}
.slide-right-enter-from {
  transform: translateX(-25%);
  opacity: 0;
}
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ── prefers-reduced-motion: opacity fade only, no slide ────── */
@media (prefers-reduced-motion: reduce) {
  .slide-left-enter-active,
  .slide-left-leave-active,
  .slide-right-enter-active,
  .slide-right-leave-active {
    transition: opacity 100ms ease !important;
  }
  .slide-left-enter-from,
  .slide-left-leave-to,
  .slide-right-enter-from,
  .slide-right-leave-to {
    transform: none !important;
    opacity: 0;
  }
}

/* ── Legacy .page-* kept as fallback ─────────────────────────── */
.page-enter-active {
  animation: page-blur-in 0.3s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.page-leave-active {
  animation: page-fade-out 0.15s ease both;
}

@keyframes page-blur-in {
  from { opacity: 0; filter: blur(3px); transform: scale(0.985); }
  to   { opacity: 1; filter: blur(0);   transform: scale(1); }
}

@keyframes page-fade-out {
  from { opacity: 1; }
  to   { opacity: 0; }
}
</style>
