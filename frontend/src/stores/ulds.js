import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import api from '../api/client'
import { useAppStore, inferUldType } from './app'

export const useUldsStore = defineStore('ulds', () => {
  const flights = shallowRef([])
  const selectedFlightId = ref(null)
  const uldsByFlight = shallowRef({})
  const floatingUlDs = shallowRef([])
  const loading = ref(false)
  const error = ref(null)

  const isShowingFloating = computed(() => selectedFlightId.value === '__floating__')

  const selectedFlight = computed(() =>
    isShowingFloating.value ? null : (flights.value.find(f => f.id === selectedFlightId.value) || null)
  )

  const activeUlds = computed(() => {
    if (isShowingFloating.value) return floatingUlDs.value
    if (!selectedFlightId.value) return []
    return uldsByFlight.value[selectedFlightId.value] || []
  })

  function apiError(e) {
    if (e instanceof Error) {
      const axios = e.response
      if (axios) {
        const d = axios.data
        if (d?.message && typeof d.message === 'string') return d.message
      }
      return e.message
    }
    return String(e)
  }

  async function loadFlights() {
    try {
      loading.value = true
      error.value = null
      const appStore = useAppStore()
      const params = {}
      if (appStore.selectedFlight?.airlineId) params.airlineId = appStore.selectedFlight.airlineId
      const res = await api.get('/flights', { params })
      flights.value = res.data.content || res.data
      if (flights.value.length && !selectedFlightId.value) {
        const first = flights.value[0]
        if (first) await selectFlight(first.id)
      }
    } catch (e) {
      error.value = 'Error cargando vuelos: ' + apiError(e)
    } finally {
      loading.value = false
    }
  }

  async function loadUldsForFlight(flightId) {
    try {
      loading.value = true
      const appStore = useAppStore()
      const params = { flightId }
      if (appStore.selectedFlight?.airlineId) params.airlineId = appStore.selectedFlight.airlineId
      const res = await api.get('/ulds', { params })
      uldsByFlight.value = { ...uldsByFlight.value, [flightId]: res.data?.content || res.data }
    } catch (e) {
      error.value = 'Error cargando ULDs: ' + apiError(e)
    } finally {
      loading.value = false
    }
  }

  async function loadFloatingUlDs() {
    try {
      loading.value = true
      const appStore = useAppStore()
      const params = {}
      if (appStore.selectedFlight?.airlineId) params.airlineId = appStore.selectedFlight.airlineId
      const res = await api.get('/ulds', { params })
      floatingUlDs.value = (res.data?.content || res.data || []).filter(u => !u.flightId)
      selectedFlightId.value = '__floating__'
    } catch (e) {
      error.value = 'Error cargando ULDs flotantes: ' + apiError(e)
    } finally {
      loading.value = false
    }
  }

  async function selectFlight(flightId) {
    if (flightId === '__floating__') {
      await loadFloatingUlDs()
      return
    }
    selectedFlightId.value = flightId
    await loadUldsForFlight(flightId)
  }

  async function dispatchUld(uld, flightId) {
    if (!flightId) throw new Error('flightId UUID requerido')

    const appStore = useAppStore()
    const dto = {
      airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
      flightId: flightId,
      uldNumber: uld.id || uld.uldNumber,
      uldType: uld.uldType || inferUldType(uld.id || uld.uldNumber),
      config: uld.config || null,
      position: uld.pos ?? uld.position ?? null,
      sealNumber: uld.sealNumber ?? null,
      grossWeightLbs: uld.grossWeightLbs || uld.grossWeight || 0,
      tareLbs: uld.tareLbs || uld.tareWeight || 0,
      status: uld.status || 'BUILT',
      notes: uld.notes ?? (uld.filledBy ? `Llenado por: ${uld.filledBy}` : null),
    }

    let res
    if (uld.backendId) {
      res = await api.put(`/ulds/${uld.backendId}`, dto)
    } else {
      res = await api.post('/ulds', dto)
    }

    await loadUldsForFlight(flightId)
    return res.data
  }

  return {
    flights, selectedFlightId, selectedFlight,
    uldsByFlight, floatingUlDs, activeUlds, loading, error,
    isShowingFloating,
    loadFlights, loadUldsForFlight, loadFloatingUlDs, selectFlight, dispatchUld
  }
})
