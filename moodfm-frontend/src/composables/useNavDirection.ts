import { ref } from 'vue'
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

const transitionName = ref<TransitionName>('slide-left')

router.beforeEach((to, from) => {
  transitionName.value = getDirection(from, to)
})

export function useNavDirection(): { transitionName: Ref<TransitionName> } {
  return { transitionName }
}
