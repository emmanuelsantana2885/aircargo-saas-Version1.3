import api from './client'

export const authApi = {
  login: (email, password, totpCode) => api.post('/auth/login', { email, password, totpCode }),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  setPassword: (email, newPassword, currentPassword) =>
    api.post('/auth/set-password', { email, newPassword, currentPassword }),
  validateResetToken: (token) => api.post('/auth/reset-password/validate', { token }),
  setPasswordWithToken: (token, newPassword) =>
    api.post('/auth/set-password-token', { token, newPassword }),
  changePassword: (newPassword, currentPassword, totpCode) =>
    api.post('/auth/change-password', { newPassword, currentPassword, totpCode }),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),
  me: () => api.get('/auth/me'),
  mfaSetup: () => api.post('/auth/mfa/setup'),
  mfaEnable: (secret, totpCode) => api.post('/auth/mfa/enable', { secret, totpCode }),
}
