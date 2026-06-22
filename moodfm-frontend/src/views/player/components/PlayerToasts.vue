<template>
  <Transition name="fade">
    <div
      v-if="blacklistToast"
      class="player-toast"
    >
      <div class="player-toast__text">
        已自动拉黑 {{ blacklistToast }}（连续跳过 3 次）
      </div>
    </div>
  </Transition>

  <Transition name="fade">
    <div
      v-if="infoToast"
      class="player-toast"
    >
      <div class="player-toast__text">{{ infoToast }}</div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
defineProps<{
  blacklistToast: string | null
  infoToast: string | null
}>()
</script>

<style scoped>
.player-toast {
  position: fixed;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 60;
  background: var(--ink);
  color: var(--bg);
  border-radius: 24px;
  padding: 14px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  max-width: 360px;
  width: calc(100% - 40px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, .3);
}

.player-toast__text {
  flex: 1;
  font-family: var(--serif-cn);
  font-size: 14px;
  line-height: 1.5;
}

.fade-enter-active,
.fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from,
.fade-leave-to     { opacity: 0; }
</style>