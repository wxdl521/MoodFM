// 简洁封装。scope 是模块/动作标签，便于过滤
// 例：'player:audio-load' / 'home:startRadio' / 'lib:loved-toggle'
export const logger = {
  warn(scope: string, err: unknown) {
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      console.warn(`[${scope}]`, err)
    }
    // 生产环境如果以后接 Sentry，在这里加上报
  },
  info(scope: string, msg: string) {
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      console.info(`[${scope}]`, msg)
    }
  }
}
