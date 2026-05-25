import type { Platform } from '@/types'

export function isPlatform(p: string | undefined | null): p is Platform {
  return p === 'netease' || p === 'qqmusic'
}

export function toPlatform(p?: string | null): Platform {
  return isPlatform(p) ? p : 'netease'
}
