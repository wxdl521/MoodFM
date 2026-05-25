import { ref } from 'vue'
import type { Ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'
import router from '@/router'

type TransitionName = 'slide-left' | 'slide-right'

export function getDirection(
  from: RouteLocationNormalized,
  to: RouteLocationNormalized,
): TransitionName {
  // Admin 内部切换：App.vue 顶层 RouterView key 共用 '/admin' 后顶层 Transition
  // 不会触发，子页淡入由 AdminLayout 内部 router-view 的 Transition 负责，
  // 所以这里返回什么不会真的生效 —— 但仍按规则返回，便于其它出入 admin 的方向计算。
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
