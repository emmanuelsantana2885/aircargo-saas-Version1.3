import api from './client'

export const usersApi = {
  getAll: (airlineId) => api.get('/users', { params: { airlineId } }),
  getById: (id) => api.get(`/users/${id}`),
  create: (dto) => api.post('/users', dto),
  update: (id, dto) => api.put(`/users/${id}`, dto),
  delete: (id) => api.delete(`/users/${id}`),
  resetPassword: (id) => api.post(`/users/${id}/reset-password`),
  getConnected: () => api.get('/users/connected'),
  getAuditLogs: (userId) => api.get('/audit-logs', { params: { userId } }),
  heartbeat: () => api.get('/auth/heartbeat'),
  mfaSetup: (id) => api.post(`/users/${id}/mfa/setup`),
  mfaEnable: (id, secret, totpCode) => api.post(`/users/${id}/mfa/enable`, { secret, totpCode }),
  mfaDisable: (id) => api.post(`/users/${id}/mfa/disable`),
  mfaLock: (id) => api.post(`/users/${id}/mfa/lock`),
  mfaUnlock: (id) => api.post(`/users/${id}/mfa/unlock`),
  generateResetLink: (id) => api.post(`/users/${id}/generate-reset-link`),
}
