<template>
  <div :data-theme="uiStore.theme" :data-mood="uiStore.moodPreset" style="overflow-x:hidden;min-height:100vh;">
    <NavBar v-if="route.meta.showNav" />
    <Transition :name="transitionName" mode="out-in">
      <RouterView :key="routerKey" />
    </Transition>

    <!-- Global mini player: shown on every page except auth/onboarding/player/admin (controlled by route.meta.hideMiniPlayer) -->
    <MiniPlayer v-if="showMiniPlayer" />

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { useNavDirection } from '@/composables/useNavDirection'
import { useElectronBridge } from '@/composables/useElectronBridge'
import NavBar from '@/components/common/NavBar.vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'

const route = useRoute()
const uiStore = useUiStore()
const authStore = useAuthStore()
const { transitionName } = useNavDirection()
useElectronBridge()

// Global mini player visibility — opt-out via route meta
const showMiniPlayer = computed(() => !route.meta.hideMiniPlayer)

// 顶层 RouterView 的 key：admin 子路由共用 '/admin'，让 AdminLayout（含侧边栏）
// 在 admin→admin 切换时不被销毁重建；其它路由按 path 触发顶层 transition。
const routerKey = computed(() =>
  route.path.startsWith('/admin') ? '/admin' : route.path,
)

onMounted(() => { authStore.validate() })

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
