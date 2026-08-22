<template>
  <div class="space-y-3 md:space-y-4">
    <!-- KPI Header -->
    <div class="flex items-center gap-4 p-3 bg-white rounded border border-slate-200 text-[13px] font-mono text-slate-500">
      <span>ULDs: <strong class="text-slate-900">{{ flightUlds.length }}</strong></span>
      <span>Pos: <strong class="text-slate-900">{{ flightPositions }}</strong></span>
      <span>Gross: <strong class="text-slate-900">{{ grossLbs }} lbs</strong></span>
      <span>Net: <strong class="text-slate-900">{{ netLbs }} lbs</strong></span>
      <span>Payload: <strong class="text-emerald-700">{{ payloadLbs }} lbs</strong></span>
    </div>

    <!-- Tab Bar -->
    <div class="flex gap-0 border-b border-slate-200">
      <button v-for="tab in tabs" :key="tab.id" @click="activeTab = tab.id"
        class="px-4 py-2 text-[13px] font-mono font-bold uppercase tracking-wider transition border-b-2 -mb-px"
        :class="activeTab === tab.id
          ? 'text-slate-900 border-slate-900'
          : 'text-slate-400 border-transparent hover:text-slate-600'">
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab: Distribución -->
    <template v-if="activeTab === 'dist'">
      <!-- Commodity Breakdown Horizontal Bar Chart -->
      <section class="p-3 bg-white rounded border border-slate-200">
        <div class="flex items-center justify-between mb-2">
          <h4 class="text-[12px] font-mono font-bold uppercase tracking-wider text-slate-500">Desglose por Commodity (lbs)</h4>
          <span class="text-[12px] font-mono text-slate-400">Total: {{ totalCommodityWeight }} lbs</span>
        </div>
        <div class="space-y-2" v-if="commodityBreakdown.length">
          <div v-for="(c, idx) in commodityBreakdown" :key="idx" class="group">
            <div class="flex items-center gap-2 mb-1">
              <span class="w-2 h-2 rounded-full" :style="{ background: c.color }"></span>
              <span class="text-[12px] font-mono text-slate-600 w-16">{{ c.shortLabel }}</span>
              <span class="text-[12px] font-mono text-slate-900 font-medium tabular-nums">{{ c.weight.toFixed(0) }} lbs</span>
              <span class="text-[11px] text-slate-400">({{ c.pieces }} pcs)</span>
            </div>
            <div class="h-3 bg-slate-100 rounded-full overflow-hidden ml-6">
              <div class="h-full rounded-full transition-all duration-500" :style="{ width: c.pct + '%', background: c.color }"></div>
            </div>
          </div>
        </div>
        <div v-else class="text-center py-4 text-[13px] text-slate-400 font-mono">Sin datos de commodity</div>
      </section>

      <!-- ULD Detail Table -->
      <section class="p-3 bg-white rounded border border-slate-200 overflow-x-auto">
        <h4 class="text-[12px] font-mono font-bold uppercase tracking-wider text-slate-500 mb-2">ULDs en este vuelo</h4>
        <table class="w-full text-[12px] font-mono border-collapse" style="min-width: 700px;">
          <thead>
            <tr class="bg-slate-50 border-b border-slate-200 text-slate-500 uppercase tracking-wider">
              <th class="px-2 py-1.5 text-left">Pos</th>
              <th class="px-2 py-1.5 text-left">ULD</th>
              <th class="px-2 py-1.5 text-left">Tipo</th>
              <th class="px-2 py-1.5 text-right">Gross lbs</th>
              <th class="px-2 py-1.5 text-right">Tare lbs</th>
              <th class="px-2 py-1.5 text-right">Net lbs</th>
              <th class="px-2 py-1.5 text-left">Contenido (MAWBs)</th>
              <th class="px-2 py-1.5 text-center">Seal</th>
              <th class="px-2 py-1.5 text-center">Estado</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in flightUlds" :key="u.id" class="border-b border-slate-100 hover:bg-slate-50/50">
              <td class="px-2 py-1.5 text-slate-400">{{ u.position || '—' }}</td>
              <td class="px-2 py-1.5 font-medium text-slate-900">{{ u.uldNumber }}</td>
              <td class="px-2 py-1.5 text-slate-600">{{ u.uldType || inferUldType(u.uldNumber) }}</td>
              <td class="px-2 py-1.5 text-right tabular-nums">{{ u.grossWeightLbs || 0 }}</td>
              <td class="px-2 py-1.5 text-right tabular-nums text-slate-500">{{ u.tareLbs || 0 }}</td>
              <td class="px-2 py-1.5 text-right tabular-nums font-medium">{{ (u.grossWeightLbs || 0) - (u.tareLbs || 0) }}</td>
              <td class="px-2 py-1.5 text-slate-600">
                <div v-if="uldMawbs(u.id).length" class="flex flex-wrap gap-1">
                  <span v-for="m in uldMawbs(u.id)" :key="m.id"
                    class="px-1.5 py-0.5 text-[11px] font-mono rounded bg-slate-100 border border-slate-200 hover:bg-slate-200 transition"
                    :style="commodityChipStyle(m.commodityType)"
                    :title="`${m.awbNumber} • ${m.pieces} pcs • ${m.commodityType || 'DRY_CARGO'}`">
                    {{ m.awbNumber }}
                  </span>
                </div>
                <span v-else class="text-slate-300 italic">Vacío</span>
              </td>
              <td class="px-2 py-1.5 text-center text-slate-500 font-mono">{{ u.sealNumber || '—' }}</td>
              <td class="px-2 py-1.5 text-center">
                <span class="px-1.5 py-0.5 rounded text-[11px] font-medium" :style="uldStatusStyle(u.status)">
                  {{ uldStatusLabel(u.status) }}
                </span>
              </td>
            </tr>
          </tbody>
          <tbody v-if="flightUlds.length === 0">
            <tr>
              <td colspan="9" class="text-center py-4 text-slate-400 font-mono text-[13px]">No hay ULDs asignados a este vuelo</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Utilization -->
      <section class="p-3 bg-white rounded border border-slate-200">
          <h4 class="text-[12px] font-mono font-bold uppercase tracking-wider text-slate-500 mb-2">Utilización</h4>
          <div class="grid grid-cols-1 gap-2 text-[12px]">
            <div class="bg-slate-50 p-2 rounded border border-slate-200">
              <div class="text-slate-400">Posiciones Main Deck</div>
              <div class="font-mono font-bold text-slate-900">{{ mdPositionCount }} / {{ flight.totalPositions || '—' }}</div>
            </div>
            <div class="bg-slate-50 p-2 rounded border border-slate-200">
              <div class="text-slate-400">Utilización MD</div>
              <div class="font-mono font-bold text-slate-900">{{ mdUtilization }}%</div>
            </div>
            <div class="bg-slate-50 p-2 rounded border border-slate-200">
              <div class="text-slate-400">Posiciones Belly</div>
              <div class="font-mono font-bold text-slate-900">{{ bellyCount }}</div>
            </div>
          </div>
        </section>
    </template>


  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAppStore } from '../stores/app'
import { useCommodities } from '../composables/useCommodities'

const props = defineProps({
  flight: { type: Object, required: true },
  flightId: { type: String, required: true },
})

const appStore = useAppStore()
const { commodities: dbCommodities } = useCommodities()
const activeTab = ref('dist')
const tabs = [
  { id: 'dist', label: 'Distribución' },
]

// ── Data Access ──────────────────────────────────────────────
const flightUlds = computed(() => appStore.ulds.filter(u => u.flightId === props.flightId))
const flightMawbs = computed(() => appStore.mawbs.filter(m => m.flightId === props.flightId))

function uldMawbs(uldId) {
  const links = appStore.uldAwbs?.filter?.(l => l.uldId === uldId) || []
  return links.map(l => appStore.mawbs.find(m => m.id === l.mawbLabel || m.awbNumber === l.mawbLabel)).filter(Boolean)
}

// ── Helpers ──────────────────────────────────────────────────
function inferUldType(uldNumber) {
  const code = (uldNumber || '').toUpperCase().substring(0, 3)
  const map = { PMC: 'PMC', PAH: 'PAH', PAG: 'PAG', PAJ: 'PAJ', AAY: 'AAY', AAZ: 'AAZ', AAD: 'AAD', PIP: 'PIP', AMP: 'AMP', AMJ: 'AMJ' }
  return map[code] || 'UNK'
}

function isBellyPosition(position) {
  if (!position) return false
  const p = position.toString().trim().toUpperCase()
  return p === '31' || p === '34' || p === 'AB' || p === 'A' || p === 'B' || p === 'LOOSE' || p === 'BULK' || p.includes('BELLY')
}

// ── Commodity Breakdown (dynamic from DB) ──────────────────────────────────────
const COMMODITY_MAP = computed(() => {
  const map = {}
  for (const c of dbCommodities.value) {
    const shortLen = Math.min(c.code.length, 4)
    map[c.code] = { label: c.label, short: c.code.slice(0, shortLen), color: c.color || '#94a3b8' }
  }
  return map
})
const fallbackCommodity = { label: 'DRY CARGO', short: 'DRY', color: '#64748b' }

// Dispatched weight per MAWB within this flight
function mawbDispatchedWeightLbs(mawb) {
  const receivedKg = Number(mawb.chargeableWeightKg || mawb.reportedWeightKg || 0)
  const receivedPcs = Number(mawb.pieces || 0)
  if (!receivedKg || !receivedPcs) return 0
  const uldIds = new Set(flightUlds.value.map(u => u.id))
  const links = appStore.uldAwbs?.filter?.(l => l.mawbLabel === mawb.awbNumber && uldIds.has(l.uldId)) || []
  const dispatchedPcs = links.reduce((s, l) => s + (Number(l.pieces) || 0), 0)
  if (!dispatchedPcs) return 0
  return (receivedKg * 2.20462 / receivedPcs) * dispatchedPcs
}

function mawbDispatchedPieces(mawb) {
  const uldIds = new Set(flightUlds.value.map(u => u.id))
  const links = appStore.uldAwbs?.filter?.(l => l.mawbLabel === mawb.awbNumber && uldIds.has(l.uldId)) || []
  return links.reduce((s, l) => s + (Number(l.pieces) || 0), 0)
}

const commodityBreakdown = computed(() => {
  const map = {}
  flightMawbs.value.forEach(m => {
    const type = m.commodityType || 'DRY_CARGO'
    const info = COMMODITY_MAP.value[type] || fallbackCommodity
    const weight = mawbDispatchedWeightLbs(m)
    const pieces = mawbDispatchedPieces(m)
    if (!map[type]) {
      map[type] = { type, label: info.label, shortLabel: info.short, color: info.color, weight: 0, pieces: 0 }
    }
    map[type].weight += weight
    map[type].pieces += pieces
  })
  const arr = Object.values(map).filter(c => c.weight > 0 || c.pieces > 0).sort((a, b) => b.weight - a.weight)
  const total = arr.reduce((s, c) => s + c.weight, 0)
  return arr.map(c => ({ ...c, pct: total > 0 ? (c.weight / total) * 100 : 0 }))
})

const totalCommodityWeight = computed(() =>
  commodityBreakdown.value.reduce((s, c) => s + c.weight, 0).toFixed(0)
)

// ── Utilization ──────────────────────────────────────────────
const mdPositionCount = computed(() =>
  flightUlds.value.filter(u => !isBellyPosition(u.position)).length
)

const mdUtilization = computed(() => {
  const total = props.flight.totalPositions
  return total > 0 ? Math.round((mdPositionCount.value / total) * 100) : 0
})

const bellyCount = computed(() =>
  flightUlds.value.filter(u => isBellyPosition(u.position)).length
)

// ── ULD Status ───────────────────────────────────────────────
function uldStatusStyle(status) {
  const map = {
    OPEN:      { background: '#e2e8f0', color: '#475569' },
    BUILT:     { background: '#dbeafe', color: '#1e40af' },
    SEALED:    { background: '#fef08a', color: '#854d0e' },
    LOADED:    { background: '#dcfce7', color: '#166534' },
    LEFT_BEHIND: { background: '#fee2e2', color: '#991b1b' },
  }
  return map[status] || { background: '#f1f5f9', color: '#64748b' }
}

function uldStatusLabel(status) {
  const map = { OPEN: 'OPN', BUILT: 'BUILT', SEALED: 'SEAL', LOADED: 'LDD', LEFT_BEHIND: 'LFT' }
  return map[status] || status?.slice(0, 3) || '—'
}

function commodityChipStyle(type) {
  const info = COMMODITY_MAP.value[type] || fallbackCommodity
  return {
    background: info.color + '15',
    borderColor: info.color + '40',
    color: info.color,
  }
}

// ── KPIs ─────────────────────────────────────────────────────
const grossLbs = computed(() =>
  flightUlds.value.reduce((s, u) => s + (Number(u.grossWeightLbs) || 0), 0)
)

const totalTareLbs = computed(() =>
  flightUlds.value.reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
)

const bellyTareLbs = computed(() =>
  flightUlds.value
    .filter(u => isBellyPosition(u.position))
    .reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
)

const netLbs = computed(() => grossLbs.value - totalTareLbs.value)
const payloadLbs = computed(() => grossLbs.value - bellyTareLbs.value)

const flightPositions = computed(() =>
  new Set(flightUlds.value.map(u => u.position).filter(Boolean)).size
)
</script>
