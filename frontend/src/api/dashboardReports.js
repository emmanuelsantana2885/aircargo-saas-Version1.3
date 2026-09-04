import api from './client'

export const dashboardBuilderApi = {
  getFields: () => api.get('/dashboard-builder/fields'),
  evaluate: (cfg) => api.post('/dashboard-builder/evaluate', cfg),
  pivot: (cfg) => api.post('/dashboard-builder/pivot', cfg),
  listReports: (userId) => api.get('/dashboard-builder/reports', { params: userId ? { userId } : {} }),
  getReport: (id) => api.get(`/dashboard-builder/reports/${id}`),
  createReport: (payload) => api.post('/dashboard-builder/reports', payload),
  updateReport: (id, payload) => api.put(`/dashboard-builder/reports/${id}`, payload),
  deleteReport: (id, userId) => api.delete(`/dashboard-builder/reports/${id}`, { params: userId ? { userId } : {} }),
}