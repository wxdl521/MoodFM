import { ref, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'
import router from '@/router'

type TransitionName = 'slide-left' | 'slide-right'

export function getDirection(
  from: RouteLocationNormalized,
  to: RouteLocationNormalized,
): TransitionName {
  const fromDepth = from.meta.depth ?? 0
  const toDepth   = to.meta.depth   ?? 0

  if (fromDepth !== toDepth) {
    return fromDepth < toDepth ? 'slide-left' : 'slide-right'
  }

  const fromOrder = from.meta.order ?? 0
  const toOrder   = to.meta.order   ?? 0
  // equal order or both missing → default slide-left per spec
  return fromOrder <= toOrder ? 'slide-left' : 'slide-right'
}

export function useNavDirection(): { transitionName: Ref<TransitionName> } {
  const transitionName = ref<TransitionName>('slide-left')

  const removeGuard = router.beforeEach((to, from) => {
    transitionName.value = getDirection(from, to)
  })

  onUnmounted(removeGuard)

  return { transitionName }
}
