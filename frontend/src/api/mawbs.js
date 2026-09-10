import api from './client'

export const mawbsApi = {
  getAll: (params = {}) => api.get('/cargo/mawbs', { params }),
  getByFlight: (flightId, params = {}) => api.get(`/cargo/mawbs/flight/${flightId}`, { params }),
  create: (dto) => api.post('/cargo/mawbs', dto),
  getByAwbNumber: (awbNumber) => api.get(`/cargo/mawbs/awb/${encodeURIComponent(awbNumber)}`),
  update: (mawbId, dto) => api.put(`/cargo/mawbs/${mawbId}`, dto),
  updateStatus: (mawbId, status) => api.patch(`/cargo/mawbs/${mawbId}/status`, JSON.stringify(status), { headers: { 'Content-Type': 'application/json' } }),
  getSupportingDocs: (mawbId) => api.get(`/cargo/mawbs/${mawbId}/supporting-docs`),
  updateSupportingDocs: (mawbId, docs) => api.put(`/cargo/mawbs/${mawbId}/supporting-docs`, docs),
  getSupportingDocsPdf: (mawbId) => api.get(`/cargo/mawbs/${mawbId}/supporting-docs/pdf`, { responseType: 'blob' }),
}
