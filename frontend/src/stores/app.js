import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import api from '../api/client'
import { flightsApi } from '../api/flights'
import { airlinesApi } from '../api/airlines'
import { bookingsApi } from '../api/bookings'
import { mawbsApi } from '../api/mawbs'
import { receiptsApi } from '../api/receipts'
import { uldsApi } from '../api/ulds'
import { uldAwbsApi } from '../api/uldAwbs'

import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'

// Inferir UldType desde el código del ULD
export function inferUldType(uldNumber) {
  const code = (uldNumber || '').toUpperCase().substring(0, 3)
  const map = { PMC: 'PMC', PAH: 'PAH', PAG: 'PAG', PAJ: 'PAJ', AAY: 'AAY',
                AAZ: 'AAZ', AAD: 'AAD', PIP: 'PIP', AMP: 'AMP', AMJ: 'AMJ', PMH: 'PMH' }
  return map[code] || 'PMC'
}

export const useAppStore = defineStore('app', () => {

  // ── Estado global ──────────────────────────────────────────────
  const flights    = shallowRef([])
  const airlines   = shallowRef([])
  const bookings   = shallowRef([])
  const mawbs      = shallowRef([])
  const receipts   = shallowRef([])
  const ulds       = shallowRef([])
  const uldAwbs    = shallowRef([])


  const selectedFlightId = ref(null)
  const loading          = ref(false)
  const error            = ref(null)

  const toast = useToastStore()

  // ── Computed ───────────────────────────────────────────────────
  const selectedFlight = computed(() =>
    flights.value.find(f => f.id === selectedFlightId.value) || null
  )

  const bookingsByFlight = computed(() =>
    selectedFlightId.value
      ? bookings.value.filter(b => b.flightId === selectedFlightId.value)
      : bookings.value
  )

  const mawbsByFlight = computed(() =>
    selectedFlightId.value
      ? mawbs.value.filter(m => m.flightId === selectedFlightId.value)
      : mawbs.value
  )

  const uldsByFlight = computed(() =>
    selectedFlightId.value
      ? ulds.value.filter(u => u.flightId === selectedFlightId.value)
      : ulds.value
  )

  // ── Actions ────────────────────────────────────────────────────

  async function loadFlights({ page = 0, size = 50 } = {}) {
    try {
      loading.value = true
      const res = await flightsApi.getAll({ page, size })
      flights.value = res.data.content || res.data
      if (flights.value.length && !selectedFlightId.value) {
        selectedFlightId.value = flights.value[0].id
      }
    } catch (e) { toast.error(extractError(e)); error.value = e.message } finally { loading.value = false }
  }

  async function loadAirlines() {
    try {
      const res = await airlinesApi.getAll()
      airlines.value = res.data
    } catch { /* silent — non-critical */ }
  }

  async function createFlight(dto) {
    const res = await flightsApi.create(dto)
    flights.value.unshift(res.data)
    return res.data
  }

  async function updateFlight(id, dto) {
    const res = await flightsApi.update(id, dto)
    const idx = flights.value.findIndex(f => f.id === id)
    if (idx >= 0) flights.value[idx] = res.data
    return res.data
  }

  async function deleteFlight(id) {
    await flightsApi.delete(id)
    flights.value = flights.value.filter(f => f.id !== id)
  }

  async function loadBookings(flightId, { page = 0, size = 50 } = {}) {
    try {
      loading.value = true
      const params = { page, size }
      if (flightId) params.flightId = flightId
      const res = await bookingsApi.getAll(params)
      bookings.value = res.data.content || res.data
    } catch (e) { toast.error(extractError(e)); error.value = e.message } finally { loading.value = false }
  }

  async function createBooking(dto) {
    const res = await bookingsApi.create(dto)
    bookings.value.unshift(res.data)
    return res.data
  }

  async function updateBooking(id, dto) {
    const res = await bookingsApi.update(id, dto)
    const idx = bookings.value.findIndex(b => b.id === id)
    if (idx >= 0) bookings.value[idx] = res.data
    return res.data
  }

  async function deleteBooking(id) {
    await bookingsApi.delete(id)
    bookings.value = bookings.value.filter(b => b.id !== id)
  }

  async function loadMawbs(flightId, { page = 0, size = 500 } = {}) {
    if (!flightId) return
    try {
      loading.value = true
      const res = await mawbsApi.getByFlight(flightId, { page, size })
      mawbs.value = res.data.content || res.data
    } catch (e) { toast.error(extractError(e)); error.value = e.message } finally { loading.value = false }
  }

  async function loadAllMawbs({ page = 0, size = 500 } = {}) {
    try {
      loading.value = true
      const res = await mawbsApi.getAll({ page, size })
      mawbs.value = res.data.content || res.data
    } catch (e) { toast.error(extractError(e)); error.value = e.message } finally { loading.value = false }
  }

  async function createMawb(dto) {
    const res = await mawbsApi.create(dto)
    const data = res.data
    const mawb = data.mawb || data
    mawbs.value.push(mawb)
    return data
  }

  async function loadReceipts({ page = 0, size = 500 } = {}) {
    try {
      loading.value = true
      const res = await receiptsApi.getAll({ page, size })
      receipts.value = (res.data.content || res.data) || []
    } catch (e) { toast.error(extractError(e)); error.value = e.message } finally { loading.value = false }
  }

  async function emitReceipt(payload) {
    const res = await api.post('/warehouse/receipts/emit', payload)
    return res.data
  }

  async function loadUlds(flightId, { page = 0, size = 50 } = {}) {
    try {
      loading.value = true
      const params = { page, size }
      if (flightId) params.flightId = flightId
      const res = await uldsApi.getAll(params)
      ulds.value = res.data.content || res.data
    } catch (e) { toast.error(extractError(e)); error.value = e.message } finally { loading.value = false }
  }

  async function loadUldAwbs() {
    try {
      const res = await uldAwbsApi.getAll()
      uldAwbs.value = res.data
    } catch { /* non-critical */ }
  }

  async function dispatchUld(localUld, flightId) {
    const flight = flights.value.find(f => f.id === flightId) || selectedFlight.value
    const dto = {
      airlineId:     flight?.airlineId || null,
      flightId:      flightId,
      uldNumber:     localUld.id,
      uldType:       inferUldType(localUld.id),
      config:        localUld.config || null,
      position:      localUld.pos || null,
      sealNumber:    localUld.sealNumber || null,
      grossWeightLbs: localUld.grossWeight || 0,
      tareLbs:       localUld.tareWeight || 0,
      status:        'BUILT',
      notes:         localUld.notes ?? null,
      destination:   localUld.destination ?? null,
      builtBy:       localUld.builtBy ?? null,
    }
    let saved
    if (localUld.backendId) {
      const res = await uldsApi.update(localUld.backendId, dto)
      saved = res.data
      const idx = ulds.value.findIndex(u => u.id === localUld.backendId)
      if (idx >= 0) ulds.value[idx] = saved
    } else {
      const res = await uldsApi.create(dto)
      saved = res.data
      ulds.value.unshift(saved)
    }
    // Crear los uld-awbs vinculados
    for (const mawb of (localUld.mawbs || [])) {
      if (mawb.awbNumber && saved.id) {
        await uldAwbsApi.create({
          uldId:       saved.id,
          mawbId:      mawb.mawbId || null,
          mawbLabel:   mawb.awbNumber,
          description: mawb.commodityType || mawb.description || 'DRY_CARGO',
          destination: mawb.destination || mawb.dest || null,
          pieces:      mawb.pieces || mawb.pcs || 0,
          piecesPct:   mawb.piecesPct || mawb.percentage || 0,
        })
      }
    }
    return saved
  }

  async function selectFlight(flightId) {
    selectedFlightId.value = flightId
    await Promise.all([
      loadBookings(flightId),
      loadMawbs(flightId),
      loadUlds(flightId),
    ])
  }

  return {
    // state
    flights, airlines, bookings, mawbs, receipts, ulds, uldAwbs,
    selectedFlightId, selectedFlight, loading, error,
    // computed
    bookingsByFlight, mawbsByFlight, uldsByFlight,
    // actions
    loadFlights, loadAirlines, createFlight, updateFlight, deleteFlight,
    loadBookings, createBooking, updateBooking, deleteBooking,
    loadMawbs, loadAllMawbs, createMawb,
    loadReceipts, emitReceipt,
    loadUlds, loadUldAwbs, dispatchUld,
    selectFlight,
  }
})
