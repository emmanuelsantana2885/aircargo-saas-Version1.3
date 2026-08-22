import api from './client'

export const uldsApi = {
  getAll: (params = {}) => api.get('/ulds', { params }),
  getByFlight: (flightId, airlineId) => api.get('/ulds', { params: { airlineId, flightId } }),
  getById: (id) => api.get(`/ulds/${id}`),
  create: (dto) => api.post('/ulds', dto),
  update: (id, dto) => api.put(`/ulds/${id}`, dto),
  patch: (id, dto) => api.patch(`/ulds/${id}`, dto),
  delete: (id) => api.delete(`/ulds/${id}`),
  assignFlight: (id, flightId) => api.patch(`/ulds/${id}/flight`, { flightId }),
}
