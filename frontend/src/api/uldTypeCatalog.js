import api from './client'

export const uldTypeCatalogApi = {
  getAll: (activeOnly = false) => api.get('/uld-type-catalog', { params: { activeOnly } }),
  getById: (id) => api.get(`/uld-type-catalog/${id}`),
  create: (data) => api.post('/uld-type-catalog', data),
  update: (id, data) => api.put(`/uld-type-catalog/${id}`, data),
  remove: (id) => api.delete(`/uld-type-catalog/${id}`),
}
