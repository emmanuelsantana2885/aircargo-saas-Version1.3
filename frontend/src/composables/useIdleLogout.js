import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { captureForms, saveDraft, setReturnTo } from '@/utils/formDraft'

/**
 * Cierre de sesión automático por inactividad (10 minutos).
 *
 * · A los 8 min muestra un aviso con cuenta regresiva y opción de continuar.
 * · A los 10 min: snapshot de formularios en edición → logout limpio
 *   (revoca cookies en el servidor) → /login con aviso y retorno a la vista.
 */
const IDLE_MS = 10 * 60 * 1000
const WARN_MS = 8 * 60 * 1000
const TICK_MS = 1000

const warningSeconds = ref(null) // null = sin aviso activo
let lastActivity = Date.now()
let timer = null
let bound = false
let running = false

function touch() {
  lastActivity = Date.now()
  if (warningSeconds.value !== null) warningSeconds.value = null // actividad cancela el aviso
}

function bindListeners() {
  if (bound) return
  bound = true
  const opts = { passive: true }
  window.addEventListener('pointerdown', touch, opts)
  window.addEventListener('pointermove', touch, opts)
  window.addEventListener('keydown', touch, opts)
  window.addEventListener('wheel', touch, opts)
  window.addEventListener('touchstart', touch, opts)
  window.addEventListener('scroll', touch, opts)
}

export function useIdleLogout(onExpire) {
  const router = useRouter()

  const secondsLeft = computed(() =>
    warningSeconds.value === null ? null : Math.max(0, Math.ceil(warningSeconds.value / TICK_MS))
  )

  function tick() {
    const idle = Date.now() - lastActivity
    if (idle >= IDLE_MS) {
      expire()
    } else if (idle >= WARN_MS) {
      warningSeconds.value = IDLE_MS - idle
    }
  }

  async function expire() {
    stop()
    try {
      // 1. preservar trabajo no guardado + vista de retorno
      saveDraft({ route: router.currentRoute.value.fullPath, forms: captureForms() })
      setReturnTo(router.currentRoute.value.fullPath)
    } catch {}
    try {
      // 2. logout limpio: revoca cookies y sesión en el servidor
      await onExpire?.()
    } catch {}
    // 3. al volver a entrar se ofrece restaurar el borrador
    router.push('/login?idle=1')
  }

  function start() {
    if (running) return
    running = true
    touch()
    bindListeners()
    timer = setInterval(tick, TICK_MS)
  }

  function stop() {
    running = false
    warningSeconds.value = null
    if (timer) clearInterval(timer)
    timer = null
  }

  function continueWorking() {
    touch()
    fetch('/api/auth/heartbeat').catch(() => {}) // mantiene la sesión viva en el servidor
  }

  onBeforeUnmount(stop)

  return { start, stop, continueWorking, warningSeconds: secondsLeft }
}
