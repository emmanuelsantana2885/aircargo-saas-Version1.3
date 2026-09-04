import axios from 'axios'
import { useToastStore } from '../stores/toast'
import { handleApiError } from '../utils/error'

// Los tokens viajan en cookies httpOnly: el navegador los envía solo.
// Este cliente solo gestiona el refresh transparente ante un 401.
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

let isRefreshing = false
let failedQueue = []

function processQueue(error) {
  failedQueue.forEach(p => (error ? p.reject(error) : p.resolve()))
  failedQueue = []
}

function readStoredSession() {
  try {
    const raw = localStorage.getItem('aircargo_auth')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// Solo limpia y redirige con recarga cuando existía una sesión REALMENTE
// establecida (userId + sitio confirmado). Si el usuario está a mitad del
// login (aún sin sitio), NO se borra el estado ni se recarga: LoginView
// conserva y restaura el paso del multi-step en sessionStorage y evita el
// rebote a la pantalla de contraseña.
function clearLocalSession() {
  const stored = readStoredSession()
  const hadFullSession = !!stored?.userId && !!stored?.selectedSiteId
  if (hadFullSession) {
    localStorage.removeItem('aircargo_auth')
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
}

api.interceptors.response.use(
  res => res,
  async err => {
    const originalRequest = err.config
    if (err.response?.status === 401 && !originalRequest._retry
        && !originalRequest.url?.includes('/auth/refresh')) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(() => api(originalRequest))
      }
      originalRequest._retry = true
      isRefreshing = true

      try {
        // el refresh token viaja en su cookie httpOnly; no se envía nada en el body
        await axios.post('/api/auth/refresh', {}, { withCredentials: true })
        processQueue(null)
        return api(originalRequest)
      } catch (refreshErr) {
        processQueue(refreshErr)
        // El refresh falló: si no había una sesión plena (login en curso),
        // rechazamos en silencio para no forzar la vuelta a la pantalla de
        // contraseña; LoginView retoma el paso exacto del flujo.
        clearLocalSession()
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }
    // 428 = flujo esperado del login (falta contraseña / MFA): lo maneja LoginView
    if (err.response?.status === 428) return Promise.reject(err)
    if (err.response?.status === 403) {
      const url = err.config?.url || ''
      console.warn('[API 403] Sin permiso para:', err.config?.method?.toUpperCase(), url)
      return Promise.reject(err)
    }
    handleApiError(err, useToastStore())
    return Promise.reject(err)
  }
)

export default api
