import { ref } from 'vue'
import { commodityTypesApi } from '../api/commodityTypes'

const commodities = ref([])
const loading = ref(false)
let loaded = false

const LEGACY_COMMODITIES = new Set([
  'SDQ_SDF', 'SDQ_MIA', 'WWEF', 'FCC', 'EMPTY_ULD', 'EMPTY_BAGS', 'NETS'
])

export function useCommodities() {
  async function loadCommodities(force = false) {
    if (loaded && !force) return
    loading.value = true
    try {
      const res = await commodityTypesApi.getAll(true)
      commodities.value = res.data || []
      loaded = true
    } catch (e) {
      console.warn('Failed to load commodity types:', e)
    } finally {
      loading.value = false
    }
  }

  function getCodeList() {
    return commodities.value.map(c => c.code)
  }

  function getMap() {
    const map = {}
    for (const c of commodities.value) {
      map[c.code] = {
        label: c.label,
        short: c.code.length > 4 ? c.code.slice(0, 4) : c.code,
        color: c.color || '#94a3b8',
        description: c.description || '',
        isLegacy: false
      }
    }
    return map
  }

  function resolveCommodity(code) {
    if (!code) return null
    const map = getMap()
    if (map[code]) return { ...map[code], isLegacy: false }
    if (LEGACY_COMMODITIES.has(code)) {
      return { label: code, short: code.slice(0, 4), color: '#9ca3af', description: 'Legacy type (not in DB)', isLegacy: true }
    }
    return { label: code, short: code.slice(0, 4), color: '#9ca3af', description: 'Unknown commodity type', isLegacy: true }
  }

  function invalidate() {
    loaded = false
  }

  return { commodities, loading, loadCommodities, getCodeList, getMap, resolveCommodity, invalidate }
}
