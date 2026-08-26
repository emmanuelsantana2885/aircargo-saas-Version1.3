import client from './client'

export const backupsApi = {
  getConfig: () => client.get('/backup/config'),
  updateConfig: (dto) => client.put('/backup/config', dto),
  getStats: () => client.get('/backup/stats'),
  getHistory: (page = 0, size = 20) => client.get('/backup/history', { params: { page, size } }),
  getLatest: () => client.get('/backup/latest'),
  trigger: (type = 'MANUAL') => client.post('/backup/trigger', null, { params: { type } }),
}
