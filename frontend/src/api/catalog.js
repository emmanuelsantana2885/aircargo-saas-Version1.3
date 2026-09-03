import api from './client'

export const catalogApi = {
  getCatalog() {
    return api.get('/catalog')
  },
}