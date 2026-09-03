import api from './client'

export const biApi = {
  getFlights:   (params = {}) => api.get('/bi/flights', { params }),
  getBookings:  (params = {}) => api.get('/bi/bookings', { params }),
  getMawbs:     (params = {}) => api.get('/bi/mawbs', { params }),
  getReceipts:  (params = {}) => api.get('/bi/receipts', { params }),
  getUlds:      (params = {}) => api.get('/bi/ulds', { params }),
  getDashboard: () => api.get('/bi/dashboard'),
  getDaily:     (params = {}) => api.get('/bi/daily', { params }),
  getWeightReport: (params = {}) => api.get('/bi/weight-report', { params }),
  getWeightSummary: (params = {}) => api.get('/bi/weight-summary', { params }),
  getSummary:   (params = {}) => api.get('/bi/summary', { params }),
  getByLocation: () => api.get('/bi/by-location'),
  getTimeline:  (params = {}) => api.get('/bi/timeline', { params }),
  getTopMawbs:  (limit = 10) => api.get('/bi/top-mawbs', { params: { limit } }),
  getFlightPerformance: (params = {}) => api.get('/bi/flight-performance', { params }),
}
