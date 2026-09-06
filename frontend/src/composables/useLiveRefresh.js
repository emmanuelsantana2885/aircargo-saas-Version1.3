import { onMounted, onUnmounted, unref } from 'vue'

export function useLiveRefresh(refresh, { interval = 30000, pauses = [] } = {}) {
  let timer = null

  const paused = () => {
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return true
    return pauses.some(p => (typeof p === 'function' ? p() : unref(p)))
  }

  const run = () => {
    if (paused()) return
    Promise.resolve(refresh()).catch(() => {})
  }

  const onShow = () => {
    if (typeof document === 'undefined' || !document.hidden) run()
  }

  onMounted(() => {
    timer = setInterval(run, interval)
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onShow)
    if (typeof window !== 'undefined') window.addEventListener('focus', onShow)
  })
  onUnmounted(() => {
    if (timer) clearInterval(timer)
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onShow)
    if (typeof window !== 'undefined') window.removeEventListener('focus', onShow)
  })

  return { run }
}