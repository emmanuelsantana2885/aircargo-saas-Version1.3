<template>
  <div class="p-3 md:p-5 bg-white text-slate-900 font-sans antialiased select-none min-h-screen">
    <div class="max-w-7xl mx-auto">

      <!-- Header -->
      <div class="ds-section-header mb-4">
        <div>
          <h1 class="ds-title">API Catalog</h1>
          <p class="ds-subtitle">Complete API reference for BI tools and external integrations</p>
        </div>
        <div class="flex gap-2">
          <a href="/swagger-ui.html" target="_blank"
            class="ds-btn-secondary text-xs inline-flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" /></svg>
            Swagger UI
          </a>
        </div>
      </div>

      <!-- Search -->
      <div class="mb-4">
        <input v-model="search" type="text" placeholder="Search endpoints, services, paths..."
          class="ds-input w-full" />
      </div>

      <!-- Stats bar -->
      <div class="flex gap-4 mb-4 text-xs">
        <div class="ds-stat">{{ filteredServices.length }} services</div>
        <div class="ds-stat">{{ totalEndpoints }} endpoints</div>
        <div class="ds-stat">{{ uniqueMethods }} HTTP methods</div>
      </div>

      <!-- Service cards -->
      <div v-for="svc in filteredServices" :key="svc.service" class="ds-table-section mb-3">
        <div class="ds-section-header px-4 py-2">
          <div>
            <span class="ds-label text-[12px]">{{ svc.description }}</span>
            <span class="ml-2 text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">{{ svc.basePath }}/**</span>
          </div>
          <span class="ds-stat">{{ svc.endpoints.length }} endpoints</span>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-xs">
            <thead>
              <tr class="bg-slate-50 text-[11px] font-semibold text-slate-600">
                <th class="px-4 py-2 text-left w-20">Method</th>
                <th class="px-4 py-2 text-left w-48">Path</th>
                <th class="px-4 py-2 text-left">Description</th>
                <th class="px-4 py-2 text-left w-40">Parameters</th>
                <th class="px-4 py-2 text-left w-56">Auth</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(ep, idx) in filteredEndpoints(svc)" :key="idx"
                class="border-b border-slate-100 hover:bg-slate-50/80">
                <td class="px-4 py-2">
                  <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold uppercase"
                    :class="methodClass(ep.method)">{{ ep.method }}</span>
                </td>
                <td class="px-4 py-2 font-mono text-[11px] text-slate-700">{{ svc.basePath }}{{ ep.path }}</td>
                <td class="px-4 py-2 text-slate-600">{{ ep.description }}</td>
                <td class="px-4 py-2">
                  <span v-if="ep.parameters && ep.parameters.length" class="text-[10px] text-slate-500">
                    {{ ep.parameters.join(', ') }}
                  </span>
                  <span v-else class="text-[10px] text-slate-300">&mdash;</span>
                </td>
                <td class="px-4 py-2">
                  <span class="text-[10px] text-slate-500">{{ ep.auth }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- BI Quick Access -->
      <div class="ds-table-section mt-6">
        <div class="ds-section-header px-4 py-2">
          <span class="ds-label text-[12px]">BI Quick Access &mdash; Live Endpoints</span>
        </div>
        <div class="p-4 grid grid-cols-2 md:grid-cols-4 gap-3">
          <button v-for="bi in biEndpoints" :key="bi.path" @click="previewBi(bi)"
            class="p-3 rounded-lg border border-slate-200 hover:border-blue-300 hover:bg-blue-50/50 transition-all text-left group">
            <div class="text-[11px] font-bold text-slate-700 group-hover:text-blue-700">{{ bi.label }}</div>
            <div class="text-[10px] font-mono text-slate-400 mt-1">GET {{ bi.path }}</div>
          </button>
        </div>
      </div>

      <!-- BI Preview Modal -->
      <div v-if="biPreview" class="ds-modal-backdrop" @click.self="biPreview = null">
        <div class="ds-modal-panel max-w-3xl max-h-[80vh] flex flex-col">
          <div class="ds-modal-header">
            <h3 class="ds-modal-title">{{ biPreview.label }}</h3>
            <button @click="biPreview = null" class="text-slate-400 hover:text-slate-600">
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
          </div>
          <div class="flex-1 overflow-auto p-4">
            <div v-if="biLoading" class="text-center py-8 text-sm text-slate-400">Loading...</div>
            <pre v-else class="text-[10px] font-mono text-slate-600 bg-slate-50 p-3 rounded-lg overflow-auto max-h-96">{{ JSON.stringify(biData, null, 2) }}</pre>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { catalogApi } from '../api/catalog'
import { biApi } from '../api/bi'

const search = ref('')
const catalog = ref([])
const biPreview = ref(null)
const biData = ref(null)
const biLoading = ref(false)

const biEndpoints = [
  { label: 'Dashboard KPIs', path: '/api/bi/dashboard', fn: () => biApi.getDashboard() },
  { label: 'Summary', path: '/api/bi/summary', fn: () => biApi.getSummary() },
  { label: 'By Location', path: '/api/bi/by-location', fn: () => biApi.getByLocation() },
  { label: 'Timeline', path: '/api/bi/timeline', fn: () => biApi.getTimeline() },
  { label: 'Top MAWBs', path: '/api/bi/top-mawbs', fn: () => biApi.getTopMawbs() },
  { label: 'Flight Performance', path: '/api/bi/flight-performance', fn: () => biApi.getFlightPerformance() },
  { label: 'Daily Data', path: '/api/bi/daily', fn: () => biApi.getDaily() },
  { label: 'Flights', path: '/api/bi/flights', fn: () => biApi.getFlights() },
]

const filteredServices = computed(() => {
  if (!search.value) return catalog.value
  const q = search.value.toLowerCase()
  return catalog.value.filter(svc =>
    svc.service.toLowerCase().includes(q) ||
    svc.description.toLowerCase().includes(q) ||
    svc.basePath.toLowerCase().includes(q) ||
    svc.endpoints.some(ep =>
      ep.path.toLowerCase().includes(q) ||
      ep.description.toLowerCase().includes(q) ||
      ep.method.toLowerCase().includes(q)
    )
  )
})

const totalEndpoints = computed(() => catalog.value.reduce((sum, s) => sum + s.endpoints.length, 0))
const uniqueMethods = computed(() => {
  const methods = new Set()
  catalog.value.forEach(s => s.endpoints.forEach(e => methods.add(e.method)))
  return methods.size
})

function filteredEndpoints(svc) {
  if (!search.value) return svc.endpoints
  const q = search.value.toLowerCase()
  return svc.endpoints.filter(ep =>
    ep.path.toLowerCase().includes(q) ||
    ep.description.toLowerCase().includes(q) ||
    ep.method.toLowerCase().includes(q)
  )
}

function methodClass(method) {
  return {
    'GET': 'bg-emerald-100 text-emerald-700',
    'POST': 'bg-blue-100 text-blue-700',
    'PUT': 'bg-amber-100 text-amber-700',
    'PATCH': 'bg-purple-100 text-purple-700',
    'DELETE': 'bg-red-100 text-red-700',
  }[method] || 'bg-slate-100 text-slate-600'
}

async function previewBi(bi) {
  biPreview.value = bi
  biLoading.value = true
  biData.value = null
  try {
    const res = await bi.fn()
    biData.value = res.data
  } catch (e) {
    biData.value = { error: e.message || 'Failed to load' }
  } finally {
    biLoading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await catalogApi.getCatalog()
    catalog.value = res.data
  } catch (e) {
    console.error('Failed to load API catalog', e)
  }
})
</script>
