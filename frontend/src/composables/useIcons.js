import { computed, shallowRef, watch } from 'vue'
import { iconLib } from '@/utils/iconLib'

const maps = new Map()
const current = shallowRef({})

async function loadLib(lib) {
  if (maps.has(lib)) return maps.get(lib)
  let map
  if (lib === 'lucide') map = (await import('./lucideIcons')).lucideIcons
  else if (lib === 'mdi') map = (await import('@/mdi')).mdiIcons
  else map = (await import('./tablerIcons')).tablerIcons
  maps.set(lib, map)
  return map
}

async function ensure(lib) {
  try {
    const map = await loadLib(lib)
    if (map !== current.value) current.value = map
  } catch {
    // never let icon loading break the UI
  }
}

watch(iconLib, (lib) => ensure(lib), { immediate: true })

if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
  window.requestIdleCallback(() => {
    const alt = iconLib.value === 'tabler'
      ? ['lucide', 'mdi']
      : iconLib.value === 'lucide'
        ? ['tabler', 'mdi']
        : ['tabler', 'lucide']
    alt.forEach((lib) => loadLib(lib).catch(() => {}))
  }, { timeout: 3000 })
}

export function useIcons() {
  return computed(() => current.value)
}