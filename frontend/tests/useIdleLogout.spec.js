import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    currentRoute: { value: { fullPath: '/receipts?mawbId=123' } },
    push: pushMock,
  }),
}))

import { useIdleLogout } from '@/composables/useIdleLogout'
import { loadDraft, popReturnTo } from '@/utils/formDraft'

describe('useIdleLogout', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    pushMock.mockClear()
    sessionStorage.clear()
    document.body.innerHTML = '<input id="campo" type="text" />'
    document.getElementById('campo').value = 'dato importante sin guardar'
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('no avisa antes de los 8 min', () => {
    const { start, stop, warningSeconds } = useIdleLogout()
    start()
    vi.advanceTimersByTime(8 * 60 * 1000 - 1000)
    expect(warningSeconds.value).toBeNull()
    stop()
  })

  it('a los 8 min activa la cuenta regresiva; la actividad la cancela y evita el logout', () => {
    const { start, stop, warningSeconds } = useIdleLogout()
    start()
    vi.advanceTimersByTime(8 * 60 * 1000 + 500)
    expect(warningSeconds.value).not.toBeNull()

    window.dispatchEvent(new Event('pointerdown')) // "Seguir trabajar" / interacción
    expect(warningSeconds.value).toBeNull()

    for (let i = 0; i < 15; i++) { // 15 min más con actividad cada minuto
      vi.advanceTimersByTime(60 * 1000)
      window.dispatchEvent(new Event('keydown'))
    }
    expect(warningSeconds.value).toBeNull()
    expect(pushMock).not.toHaveBeenCalled()
    stop()
  })

  it('a los 10 min: preserva borrador + returnTo, hace logout y navega a /login?idle=1', async () => {
    let expired = false
    const { start, stop } = useIdleLogout(async () => { expired = true })
    start()

    await vi.advanceTimersByTimeAsync(10 * 60 * 1000 + 500)

    expect(expired).toBe(true)
    expect(loadDraft().route).toBe('/receipts?mawbId=123')
    expect(loadDraft().forms.length).toBeGreaterThan(0)
    expect(loadDraft().forms[0].v).toBe('dato importante sin guardar')
    expect(popReturnTo()).toBe('/receipts?mawbId=123')
    expect(pushMock).toHaveBeenCalledWith('/login?idle=1')
    stop()
  })
})
