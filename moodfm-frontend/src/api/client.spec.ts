import { describe, it, expect } from 'vitest'
import { unwrapResponse } from './client'

describe('unwrapResponse', () => {
  it('R<Void> 成功（code=200 无 data 字段）应当 resolve 而非 reject', () => {
    // 同步断言：直接返回 undefined，而非被 reject 的 Promise（包 Promise.resolve 会掩盖回归）
    const result = unwrapResponse({ data: { code: 200, message: '操作成功' } })
    expect(result).toBeUndefined()
  })

  it('R<T> 成功应返回 data', () => {
    expect(
      unwrapResponse({ data: { code: 200, message: 'ok', data: { id: 1 } } }),
    ).toEqual({ id: 1 })
  })

  it('业务失败（code≠200）应 reject 并携带 code', async () => {
    await expect(
      unwrapResponse({ data: { code: 410, message: '电台会话已结束' } }) as Promise<unknown>,
    ).rejects.toMatchObject({ code: 410, message: '电台会话已结束' })
  })

  it('非 R 包装的响应应原样返回', () => {
    expect(unwrapResponse({ data: 'plain-text' })).toBe('plain-text')
  })
})
