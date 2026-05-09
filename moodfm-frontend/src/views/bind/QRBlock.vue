<template>
  <div style="text-align: center;">
    <div style="display: inline-block; position: relative; padding: 24px; border: 1px solid var(--rule); border-radius: 18px; background: var(--bg);">
      <div style="width: 200px; height: 200px; position: relative; background: #fff; padding: 8px;">
        <svg viewBox="0 0 17 17" width="184" height="184">
          <rect v-for="cell in qrCells" :key="`${cell.x}-${cell.y}`"
            :x="cell.x" :y="cell.y" width="1" height="1" :fill="cell.color" />
          <g v-for="(corner, i) in finderCorners" :key="i">
            <rect :x="corner[0]" :y="corner[1]" width="7" height="7" fill="#000" />
            <rect :x="corner[0]+1" :y="corner[1]+1" width="5" height="5" fill="#fff" />
            <rect :x="corner[0]+2" :y="corner[1]+2" width="3" height="3" fill="#000" />
          </g>
        </svg>

        <Transition name="fade">
          <div v-if="status === 'expired'"
            style="position: absolute; inset: 0; background: rgba(255,255,255,0.92); display: flex; flex-direction: column; align-items: center; justify-content: center;">
            <div class="meta">QR EXPIRED</div>
            <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', marginTop: '4px', color: 'var(--ink-2)' }">二维码已过期</div>
            <button class="btn" style="margin-top: 12px; height: 36px;" @click="refresh">刷新</button>
          </div>
        </Transition>
      </div>
    </div>

    <div class="meta" style="margin-top: 18px;">
      STATUS ·
      <span :style="{
        color: status === 'waiting' ? 'var(--ink)'
          : status === 'expired' ? 'var(--mood-b)'
          : 'var(--mood-c)'
      }">
        <span v-if="status === 'waiting'">等待扫码</span>
        <span v-else-if="status === 'scanned'">已扫码 · 请在手机上确认</span>
        <span v-else-if="status === 'success'">登录成功</span>
        <span v-else>二维码已过期</span>
      </span>
    </div>

    <div v-if="status === 'waiting'" :style="{ marginTop: '8px', fontFamily: 'var(--mono)', fontSize: '13px', color: 'var(--ink-2)' }">
      {{ countdown }}s 后过期 · refresh in {{ countdown }}s
    </div>

    <div :style="{ marginTop: '16px', fontFamily: 'var(--serif-cn)', fontSize: '13px', color: 'var(--ink-2)' }">
      打开{{ platform }} App → 我的 → 扫一扫
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps<{ platform: string }>()

const countdown = ref(90)
const status = ref<'waiting' | 'scanned' | 'success' | 'expired'>('waiting')

const qrCells = computed(() => {
  const cells: { x: number; y: number; color: string }[] = []
  for (let y = 0; y < 17; y++) {
    for (let x = 0; x < 17; x++) {
      const inCorner = (x < 7 && y < 7) || (x >= 10 && y < 7) || (x < 7 && y >= 10)
      if (!inCorner) {
        cells.push({ x, y, color: ((x * 3 + y * 7 + x * y) % 2 === 0) ? '#000' : '#fff' })
      }
    }
  }
  return cells
})

const finderCorners = [[0, 0], [10, 0], [0, 10]] as const

let timer: ReturnType<typeof setInterval> | null = null

function startTimer() {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    if (status.value !== 'waiting') return
    countdown.value--
    if (countdown.value <= 0) {
      status.value = 'expired'
      if (timer) clearInterval(timer)
    }
  }, 1000)
}

function refresh() {
  countdown.value = 90
  status.value = 'waiting'
  startTimer()
}

onMounted(startTimer)
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
