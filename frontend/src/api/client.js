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

function clearLocalSession() {
  localStorage.removeItem('aircargo_auth')
  window.location.href = '/login'
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
        clearLocalSession()
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }
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
