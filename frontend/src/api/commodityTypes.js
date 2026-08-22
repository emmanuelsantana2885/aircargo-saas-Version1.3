import client from './client'

export const commodityTypesApi = {
  getAll(activeOnly = false) {
    return client.get('/commodity-types', { params: { activeOnly } })
  },
  getById(id) {
    return client.get(`/commodity-types/${id}`)
  },
  create(dto, totpToken) {
    return client.post('/commodity-types', dto, {
      headers: totpToken ? { 'X-TOTP-Token': totpToken } : {}
    })
  },
  update(id, dto, totpToken) {
    return client.put(`/commodity-types/${id}`, dto, {
      headers: totpToken ? { 'X-TOTP-Token': totpToken } : {}
    })
  },
  delete(id, totpToken) {
    return client.delete(`/commodity-types/${id}`, {
      headers: totpToken ? { 'X-TOTP-Token': totpToken } : {}
    })
  },
  restoreDefaults(totpToken) {
    return client.post('/commodity-types/restore-defaults', null, {
      headers: totpToken ? { 'X-TOTP-Token': totpToken } : {}
    })
  },
}
