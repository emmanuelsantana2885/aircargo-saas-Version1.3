<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div>
        <h1 class="ds-title">{{ t('dashboard.title') }}</h1>
        <p class="ds-subtitle">{{ t('dashboard.subtitle') }}</p>
      </div>
      <div class="flex items-center gap-3 text-[12px] font-mono font-bold flex-wrap">
        <span class="ds-stat">
          <span class="h-2 w-2 rounded-full" style="background: var(--accent)"></span> {{ t('dashboard.live') }}
        </span>
        <span class="ds-divider"></span>
        <span class="ds-stat">{{ t('dashboard.flightsCount', { n: filteredFlights.length }) }}</span>
        <span class="ds-divider"></span>
        <button @click="descargarReporte" class="ds-btn-primary">
         <span class="text-[14px] font-semibold leading-none">↓</span> {{ t('dashboard.downloadReport') }}
        </button>
      </div>
    </header>

    <!-- Tabs -->
    <div class="flex gap-1 mb-4">
      <button @click="activeTab = 'flights'"
        :class="activeTab === 'flights' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('dashboard.tabs.flights') }}
      </button>
      <button @click="activeTab = 'weight-report'"
        :class="activeTab === 'weight-report' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('dashboard.tabs.weightReport') }}
      </button>
    </div>

    <!-- ============ FLIGHTS TAB ============ -->
    <template v-if="activeTab === 'flights'">
    <FilterBar
      v-model:date-from="dateFrom"
      v-model:date-to="dateTo"
      :show-date-from="true"
      :show-date-to="true"
      :show-search="false"
      container-class="my-3 shrink-0"
    />
    <section class="flex items-center gap-4 my-1 shrink-0">
      <div class="text-[12px] font-mono text-slate-500 flex items-center gap-4">
        <span class="ds-stat">{{ t('dashboard.totalNet') }} <strong class="text-slate-950">{{ totalNetPayload }} {{ t('common.lbs') }}</strong></span>
        <span class="ds-stat">{{ t('dashboard.totalUlds') }} <strong class="text-slate-950">{{ totalUldsCount }}</strong></span>
        <span class="ds-stat">{{ t('dashboard.totalMawbs') }} <strong class="text-slate-950">{{ totalMawbsCount }}</strong></span>
      </div>
    </section>

    <section class="ds-table-section mb-1.5">
      <div ref="tableWrapper" class="overflow-auto flex-1 min-h-0 scrollbar-none">
        <div class="table-scroll-wrapper h-full">
        <table class="w-full border-collapse text-[13px] font-mono" :style="{ minWidth: tableMinWidth + 'px' }">
          <thead class="sticky top-0 z-20">
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider border-b border-slate-200 shadow-sm font-mono [&>th]:px-2 [&>th]:py-2.5 [&>th]:whitespace-nowrap">
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-8 sticky left-0 z-10 bg-slate-800">#</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-8 sticky left-8 z-10 bg-slate-800">
                <button @click="toggleAllExpanded" class="flex items-center justify-center gap-1 hover:opacity-70 transition"
                  :title="allExpanded ? t('dashboard.collapseAll') : t('dashboard.expandAll')">
                  <span class="text-[14px]">{{ allExpanded ? '▲' : '▼' }}</span>
                </button>
              </th>
              <th class="text-left px-2 py-2.5 whitespace-nowrap sticky left-16 z-10 bg-slate-800">{{ t('dashboard.table.flight') }}</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap sticky left-[140px] z-10 bg-slate-800">{{ t('dashboard.table.route') }}</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap sticky left-[220px] z-10 bg-slate-800">{{ t('dashboard.table.date') }}</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap">{{ t('dashboard.table.status') }}</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-16">{{ t('dashboard.table.ulds') }}</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-14">{{ t('dashboard.table.pos') }}</th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24">{{ t('dashboard.table.gross') }}</th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24">{{ t('dashboard.table.tare') }}</th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24">{{ t('dashboard.table.net') }}</th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-12">{{ t('dashboard.table.docs') }}</th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 text-emerald-600">{{ t('dashboard.table.payload') }}</th>
              <!-- Commodity columns - dynamic based on filtered flights -->
              <th v-for="c in visibleCommodities" :key="c.type"
                class="text-right px-2 py-2.5 whitespace-nowrap w-20 text-[12px]"
                :style="{ background: c.color + '20', borderLeft: '1px solid ' + c.color + '40' }"
                :title="c.label">
                <div class="flex items-center justify-end gap-1">
                  <span class="w-1.5 h-1.5 rounded-full" :style="{ background: c.color }"></span>
                  <span class="font-mono">{{ c.short }}</span>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading" class="h-32">
              <td :colspan="14 + visibleCommodities.length" class="text-center text-[14px] font-mono text-slate-400 ">{{ t('dashboard.loadingData') }}</td>
            </tr>
            <tr v-else-if="filteredFlights.length === 0" class="h-32">
              <td :colspan="14 + visibleCommodities.length" class="text-center text-[14px] font-mono text-slate-400 uppercase tracking-widest">{{ t('dashboard.noFlightsInRange') }}</td>
            </tr>
            <template v-for="(f, fi) in filteredFlights" :key="f.id">
              <tr class="border-b border-slate-100 transition-colors duration-150 hover:bg-slate-50/80"
                :class="{ 'bg-slate-50/50': isExpanded(f.id) }">
                <td class="text-center px-2 py-2 text-slate-400 sticky left-0 z-10 bg-white">{{ fi + 1 }}</td>
                <td class="text-center px-2 py-2 sticky left-8 z-10 bg-white">
                  <button @click="toggleExpand(f.id)"
                    class="flex items-center justify-center w-6 h-6 rounded hover:bg-slate-200 transition text-slate-500 hover:text-slate-900"
                    :aria-expanded="isExpanded(f.id)"
                    :title="isExpanded(f.id) ? t('dashboard.collapseDetail') : t('dashboard.expandDetail')">
                    <span class="text-[12px] transition-transform duration-200" :style="{ transform: isExpanded(f.id) ? 'rotate(180deg)' : '' }">▼</span>
                  </button>
                </td>
                <td class="px-2 py-2 font-mono text-slate-950 sticky left-16 z-10 bg-white">UPS-{{ f.flightNumber }}</td>
                <td class="text-center px-2 py-2 text-slate-700 sticky left-[140px] z-10 bg-white">{{ f.origin }}→{{ f.destination }}</td>
                <td class="text-center px-2 py-2 text-slate-500 sticky left-[220px] z-10 bg-white">{{ f.flightDate }}</td>
                <td class="text-center px-2 py-2">
                  <span class="inline-flex items-center gap-1">
                    <span :class="getStatusDot(f.status)" class="inline-block w-2 h-2 rounded-full"></span>
                    <span class="px-1.5 py-0.5 rounded text-[12px] font-medium" :style="statusStyle(f.status)">{{ statusLabel(f.status) }}</span>
                  </span>
                </td>
                <td class="text-center px-2 py-2 font-mono text-slate-900">{{ flightUlds(f.id).length }}</td>
                <td class="text-center px-2 py-2 font-mono text-slate-600">{{ flightPositions(f.id) }}<span class="text-slate-300">/</span>{{ f.totalPositions || '—' }}</td>
                <td class="text-right px-2 py-2 font-mono text-slate-950">{{ grossLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-mono text-slate-600">{{ totalTareLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-mono text-slate-900">{{ netLbs(f.id) }}</td>
                <td class="text-center px-2 py-2 text-slate-400 font-mono">5</td>
                <td class="text-right px-2 py-2 font-bold text-emerald-700" style="font-family: 'SF Mono', 'Fira Code', monospace;">{{ payloadLbs(f.id) }}</td>
                <!-- Commodity payload columns -->
                <td v-for="c in visibleCommodities" :key="c.type"
                  class="text-right px-2 py-2 font-mono text-slate-900 tabular-nums"
                  :style="{ background: c.color + '08' }"
                  :title="commodityTooltip(f.id, c.type)">
                  {{ commodityPayload(f.id, c.type) || '—' }}
                </td>
              </tr>

              <!-- Drill-down row -->
              <tr v-show="isExpanded(f.id)" class="bg-slate-50/30 border-t border-slate-200">
                <td :colspan="14 + visibleCommodities.length" class="p-0">
                  <div class="p-3 md:p-4 border-t border-slate-200" style="animation: slideDown 0.2s ease-out;">
                    <FlightDetail :flight="f" :flight-id="f.id" />
                  </div>
                </td>
              </tr>
            </template>

            <!-- Totals row -->
            <tr class="bg-slate-50 border-t-2 border-slate-300 font-bold hover:bg-slate-100 transition-colors">
              <td class="text-center px-2 py-2 text-slate-400">Σ</td>
              <td class="text-center px-2 py-2"></td>
              <td class="px-2 py-2 text-slate-500 sticky left-16 z-10 bg-slate-50">{{ t('dashboard.table.total') }}</td>
              <td class="text-center px-2 py-2 sticky left-[140px] z-10 bg-slate-50"></td>
              <td class="text-center px-2 py-2 sticky left-[220px] z-10 bg-slate-50"></td>
              <td class="text-center px-2 py-2"></td>
              <td class="text-center px-2 py-2">{{ totalUldsCount }}</td>
              <td class="text-center px-2 py-2">{{ totalPositionsAll }}<span class="text-slate-300">/</span>{{ totalMaxPositionsAll }}</td>
              <td class="text-right px-2 py-2">{{ totalGrossAll }}</td>
              <td class="text-right px-2 py-2">{{ totalTareAll }}</td>
              <td class="text-right px-2 py-2">{{ totalNetAll }}</td>
              <td class="text-center px-2 py-2">{{ filteredFlights.length * 5 }}</td>
              <td class="text-right px-2 py-2 text-emerald-700">{{ totalNetPayload }}</td>
              <td v-for="c in visibleCommodities" :key="c.type"
                class="text-right px-2 py-2 text-slate-900 tabular-nums"
                :style="{ background: c.color + '15' }">
                {{ totalCommodityPayload(c.type) }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </section>
    </template>
    <!-- ============ END FLIGHTS TAB ============ -->

    <!-- ============ WEIGHT REPORT TAB ============ -->
    <template v-if="activeTab === 'weight-report'">
    <div class="flex items-end gap-2 shrink-0">
      <FilterBar
        v-model:date-from="wrDateFrom"
        v-model:date-to="wrDateTo"
        v-model:commodity="wrCommodity"
        v-model:search-text="wrFlightNumber"
        :show-period-presets="true"
        :show-date-from="true"
        :show-date-to="true"
        :show-commodity="true"
        :show-search="true"
        :show-search-button="true"
        :show-clear="true"
        :loading="wrLoading"
        :search-label="t('common.flight')"
        :search-placeholder="t('dashboard.wr.searchPlaceholder')"
        :search-button-label="t('common.search')"
        @search="loadWeightReport"
        @clear="wrDateFrom = ''; wrDateTo = ''; wrCommodity = ''; wrFlightNumber = ''; loadWeightReport()"
      />
      <button v-if="wrRows.length" @click="exportWeightCSV" class="ds-btn-secondary mb-0.5">
        <span class="text-[14px] font-semibold leading-none">&#8595;</span> {{ t('dashboard.wr.exportCsv') }}
      </button>
    </div>

    <!-- Summary Cards -->
    <div v-if="wrSummary" class="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
      <div class="ds-card">
        <div class="ds-card-label">{{ t('dashboard.wr.summary.totalRows') }}</div>
        <div class="ds-card-value">{{ wrSummary.totalRows }}</div>
      </div>
      <div class="ds-card">
        <div class="ds-card-label">{{ t('dashboard.wr.summary.receivedPieces') }}</div>
        <div class="ds-card-value">{{ wrSummary.totalReceivedPieces }}</div>
      </div>
      <div class="ds-card">
        <div class="ds-card-label">{{ t('dashboard.wr.summary.physicalWeightLbs') }}</div>
        <div class="ds-card-value text-emerald-700">{{ formatNum(wrSummary.totalPhysicalWeightLbs) }}</div>
      </div>
      <div class="ds-card">
        <div class="ds-card-label">{{ t('dashboard.wr.summary.dispatchedWeightLbs') }}</div>
        <div class="ds-card-value text-amber-700">{{ formatNum(wrSummary.totalDispatchedWeightLbs) }}</div>
      </div>
    </div>

    <!-- Per-Commodity Breakdown -->
    <div v-if="wrSummary?.byCommodity && Object.keys(wrSummary.byCommodity).length > 1" class="mb-4">
      <div class="text-[12px] font-bold text-slate-500 uppercase tracking-wider mb-2">{{ t('dashboard.wr.byCommodity') }}</div>
      <div class="flex flex-wrap gap-2">
        <div v-for="(data, code) in wrSummary.byCommodity" :key="code"
          class="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 bg-white text-[12px]">
          <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :style="{ background: commodityColor(code) }"></span>
          <span class="font-bold text-slate-800">{{ code }}</span>
          <span class="text-slate-400">|</span>
          <span class="text-slate-600">{{ t('dashboard.wr.piecesUnit', { n: data.totalReceivedPieces }) }}</span>
          <span class="text-slate-400">|</span>
          <span class="font-semibold text-emerald-700">{{ formatNum(data.totalPhysicalWeightLbs) }} {{ t('common.lbs') }}</span>
          <span class="text-slate-400">→</span>
          <span class="font-semibold text-amber-700">{{ t('dashboard.wr.dispatchedLbs', { n: formatNum(data.totalDispatchedWeightLbs) }) }}</span>
        </div>
      </div>
    </div>

    <!-- Weight Report Table -->
    <section class="ds-table-section">
      <div class="overflow-auto flex-1 min-h-0 scrollbar-none" style="max-height:60vh">
        <table class="w-full border-collapse text-[12px] font-mono">
          <thead class="bg-slate-100 sticky top-0 z-10">
            <tr>
              <th class="text-left px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.awb') }}</th>
              <th class="text-left px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.shipper') }}</th>
              <th class="text-left px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.consignee') }}</th>
              <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.dest') }}</th>
              <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.commodity') }}</th>
              <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.flight') }}</th>
              <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.date') }}</th>
              <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.pcsRec') }}</th>
              <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.physicalLbs') }}</th>
              <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.dispatchedLbs') }}</th>
              <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('dashboard.wr.table.pcsDisp') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in wrRows" :key="idx"
              class="hover:bg-blue-50/30 border-b border-slate-100">
              <td class="px-2 py-1.5 font-bold text-slate-900">{{ row.awbNumber }}</td>
              <td class="px-2 py-1.5 text-slate-600">{{ row.shipperName }}</td>
              <td class="px-2 py-1.5 text-slate-600">{{ row.consigneeName }}</td>
              <td class="text-center px-2 py-1.5">{{ row.destination }}</td>
              <td class="text-center px-2 py-1.5">
                <span v-if="row.commodityType"
                  class="inline-block px-1.5 py-0.5 rounded text-[10px] font-bold"
                  :style="{ background: commodityColor(row.commodityType) + '18', color: commodityColor(row.commodityType) }">
                  {{ row.commodityType }}
                </span>
              </td>
              <td class="text-center px-2 py-1.5 font-semibold">{{ row.flightNumber }}</td>
              <td class="text-center px-2 py-1.5">{{ row.flightDate }}</td>
              <td class="text-right px-2 py-1.5 tabular-nums">{{ row.receivedPieces }}</td>
              <td class="text-right px-2 py-1.5 tabular-nums font-semibold text-emerald-700">{{ formatNum(row.physicalWeightLbs) }}</td>
              <td class="text-right px-2 py-1.5 tabular-nums font-semibold text-amber-700">{{ formatNum(row.dispatchedWeightLbs) }}</td>
              <td class="text-right px-2 py-1.5 tabular-nums">{{ row.dispatchedPieces }}</td>
            </tr>
            <tr v-if="!wrRows.length && !wrLoading">
              <td colspan="11" class="text-center py-8 text-slate-400">{{ t('dashboard.wr.noData') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    </template>
    <!-- ============ END WEIGHT REPORT TAB ============ -->

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { downloadCSV } from '../utils/csv'
import FlightDetail from '../components/FlightDetail.vue'
import { useCommodities } from '../composables/useCommodities'
import { biApi } from '../api/bi'
import FilterBar from '../components/FilterBar.vue'

const { t } = useI18n()
const appStore = useAppStore()
const { commodities: dbCommodities, loadCommodities } = useCommodities()

const dateFrom = ref('')
const dateTo = ref('')
const loading = ref(false)
const expandedFlights = ref(new Set())
const activeTab = ref('flights')

watch(activeTab, (tab) => {
  if (tab === 'weight-report' && !wrRows.value.length && !wrLoading.value) {
    loadWeightReport()
  }
})

// Weight report state
const wrDateFrom = ref('')
const wrDateTo = ref('')
const wrCommodity = ref('')
const wrFlightNumber = ref('')
const wrLoading = ref(false)
const wrRows = ref([])
const wrSummary = ref(null)

function formatNum(v) {
  if (v == null) return '0'
  const n = Number(v)
  return isNaN(n) ? '0' : n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 1 })
}

function commodityColor(code) {
  const c = dbCommodities.value.find(x => x.code === code)
  return c?.color || '#6b7280'
}

async function loadWeightReport() {
  wrLoading.value = true
  try {
    const params = {}
    if (wrDateFrom.value) params.dateFrom = wrDateFrom.value
    if (wrDateTo.value) params.dateTo = wrDateTo.value
    if (wrCommodity.value) params.commodityType = wrCommodity.value
    if (wrFlightNumber.value) params.awbNumber = wrFlightNumber.value
    const [rowsRes, sumRes] = await Promise.all([
      biApi.getWeightReport(params),
      biApi.getWeightSummary(params),
    ])
    wrRows.value = rowsRes.data
    wrSummary.value = sumRes.data
  } catch (e) {
    console.error('Weight report error:', e)
    wrRows.value = []
    wrSummary.value = null
  } finally {
    wrLoading.value = false
  }
}

function exportWeightCSV() {
  if (!wrRows.value.length) return
  const headers = [
    t('dashboard.wr.csvHeaders.awb'),
    t('dashboard.wr.csvHeaders.shipper'),
    t('dashboard.wr.csvHeaders.consignee'),
    t('dashboard.wr.csvHeaders.dest'),
    t('dashboard.wr.csvHeaders.commodity'),
    t('dashboard.wr.csvHeaders.flight'),
    t('dashboard.wr.csvHeaders.date'),
    t('dashboard.wr.csvHeaders.pcsRec'),
    t('dashboard.wr.csvHeaders.physicalLbs'),
    t('dashboard.wr.csvHeaders.dispatchedLbs'),
    t('dashboard.wr.csvHeaders.pcsDisp'),
  ]
  const csvRows = [headers.join(',')]
  for (const r of wrRows.value) {
    csvRows.push([
      r.awbNumber, r.shipperName, r.consigneeName, r.destination,
      r.commodityType, r.flightNumber, r.flightDate,
      r.receivedPieces, r.physicalWeightLbs, r.dispatchedWeightLbs, r.dispatchedPieces
    ].map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))
  }
  const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${t('dashboard.wr.csvFilename')}-${new Date().toISOString().slice(0,10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const filteredFlights = computed(() => {
  let list = appStore.flights
  if (dateFrom.value) {
    list = list.filter(f => f.flightDate >= dateFrom.value)
  }
  if (dateTo.value) {
    list = list.filter(f => f.flightDate <= dateTo.value)
  }
  return list
})

const allUlDs = computed(() => appStore.ulds)
const allMawbs = computed(() => appStore.mawbs)

function flightUlds(flightId) {
  return allUlDs.value.filter(u => u.flightId === flightId)
}

function flightMawbs(flightId) {
  return allMawbs.value.filter(m => m.flightId === flightId)
}

function flightPositions(flightId) {
  const ulds = flightUlds(flightId)
  return new Set(ulds.map(u => u.position).filter(Boolean)).size
}

function grossLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds.reduce((s, u) => s + (Number(u.grossWeightLbs) || 0), 0)
}

function isBellyPosition(position) {
  if (!position) return false
  const p = position.toString().trim().toUpperCase()
  return p === '31' || p === '34' || p === 'AB' || p === 'A' || p === 'B' || p === 'LOOSE' || p === 'BULK' || p.includes('BELLY')
}

function totalTareLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds.reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
}

function bellyTareLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds
    .filter(u => isBellyPosition(u.position))
    .reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
}

function netLbs(flightId) {
  return grossLbs(flightId) - totalTareLbs(flightId)
}

function payloadLbs(flightId) {
  return grossLbs(flightId) - bellyTareLbs(flightId)
}

// ── Commodity definitions & ordering (dynamic from DB) ──────────────────────────
const COMMODITY_ORDER = computed(() => dbCommodities.value.map(c => c.code))

const COMMODITY_MAP = computed(() => {
  const map = {}
  for (const c of dbCommodities.value) {
    const shortLen = Math.min(c.code.length, 4)
    map[c.code] = { label: c.label, short: c.code.slice(0, shortLen), color: c.color || '#94a3b8' }
  }
  return map
})

// ULD IDs per flight (cached for fast lookup during commodity calculations)
const _uldIdCache = new Map()
function flightUldIdSet(flightId) {
  if (!_uldIdCache.has(flightId)) {
    _uldIdCache.set(flightId, new Set(appStore.ulds.filter(u => u.flightId === flightId).map(u => u.id)))
  }
  return _uldIdCache.get(flightId)
}

// Invalidate cache when ulds change
watch(() => appStore.ulds.length, () => _uldIdCache.clear())

// Dispatched weight per MAWB within a specific flight:
// only counts ULD-AWB links whose ULD belongs to that flight.
// Formula: (receivedWeight / receivedPieces) * dispatchedPieces
function mawbDispatchedWeightLbs(mawb, flightId) {
  const receivedKg = Number(mawb.chargeableWeightKg || mawb.reportedWeightKg || 0)
  const receivedPcs = Number(mawb.pieces || 0)
  if (!receivedKg || !receivedPcs) return 0
  const uldIds = flightUldIdSet(flightId)
  const links = appStore.uldAwbs?.filter?.(l => l.mawbLabel === mawb.awbNumber && uldIds.has(l.uldId)) || []
  const dispatchedPcs = links.reduce((s, l) => s + (Number(l.pieces) || 0), 0)
  if (!dispatchedPcs) return 0
  return (receivedKg * 2.20462 / receivedPcs) * dispatchedPcs
}

// Commodity payload per flight (sum of per-MAWB dispatched weights)
function commodityPayload(flightId, commodityType) {
  const mawbs = flightMawbs(flightId)
  const totalLbs = mawbs
    .filter(m => (m.commodityType || 'DRY_CARGO') === commodityType)
    .reduce((s, m) => s + mawbDispatchedWeightLbs(m, flightId), 0)
  return totalLbs > 0 ? Math.round(totalLbs) : null
}

function commodityTooltip(flightId, commodityType) {
  const mawbs = flightMawbs(flightId)
  const items = mawbs.filter(m => (m.commodityType || 'DRY_CARGO') === commodityType)
  if (!items.length) return t('dashboard.tooltip.commodityZero', { label: COMMODITY_MAP.value[commodityType]?.label || commodityType })
  const totalLbs = items.reduce((s, m) => s + mawbDispatchedWeightLbs(m, flightId), 0)
  const uldIds = flightUldIdSet(flightId)
  const totalPcs = items.reduce((s, m) => {
    const links = appStore.uldAwbs?.filter?.(l => l.mawbLabel === m.awbNumber && uldIds.has(l.uldId)) || []
    return s + links.reduce((ps, l) => ps + (Number(l.pieces) || 0), 0)
  }, 0)
  const awbNumbers = items.map(m => m.awbNumber).join(', ')
  return t('dashboard.tooltip.commodity', { label: COMMODITY_MAP.value[commodityType]?.label || commodityType, lbs: Math.round(totalLbs), pcs: totalPcs }) + ` • ${awbNumbers}`
}

// Visible commodities = those with dispatched payload > 0 in ANY filtered flight
const visibleCommodities = computed(() => {
  const activeTypes = new Set()
  filteredFlights.value.forEach(f => {
    flightMawbs(f.id).forEach(m => {
      const type = m.commodityType || 'DRY_CARGO'
      if (mawbDispatchedWeightLbs(m, f.id) > 0) activeTypes.add(type)
    })
  })
  return COMMODITY_ORDER.value
    .filter(t => activeTypes.has(t))
    .map(t => ({ type: t, ...COMMODITY_MAP.value[t] }))
})

// Table min-width for horizontal scroll
const tableMinWidth = computed(() => {
  const base = 1100 // fixed columns
  const commodityCols = visibleCommodities.value.length * 80 // 80px per commodity col
  return base + commodityCols
})

// Totals
const totalNetPayload = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + payloadLbs(f.id), 0)
})

const totalUldsCount = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightUlds(f.id).length, 0)
})

const totalMawbsCount = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightMawbs(f.id).length, 0)
})

const totalPositionsAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightPositions(f.id), 0)
})

const totalMaxPositionsAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + (f.totalPositions || 0), 0)
})

const totalGrossAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + grossLbs(f.id), 0)
})

const totalTareAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + totalTareLbs(f.id), 0)
})

const totalNetAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + netLbs(f.id), 0)
})

function totalCommodityPayload(commodityType) {
  const total = filteredFlights.value.reduce((s, f) => s + (commodityPayload(f.id, commodityType) || 0), 0)
  return total > 0 ? total : '—'
}

// Expand logic
const allExpanded = computed(() => {
  return filteredFlights.value.length > 0 && filteredFlights.value.every(f => expandedFlights.value.has(f.id))
})

function toggleExpand(flightId) {
  if (expandedFlights.value.has(flightId)) {
    expandedFlights.value.delete(flightId)
  } else {
    expandedFlights.value.add(flightId)
  }
}

function toggleAllExpanded() {
  if (allExpanded.value) {
    expandedFlights.value.clear()
  } else {
    filteredFlights.value.forEach(f => expandedFlights.value.add(f.id))
  }
}

function isExpanded(flightId) {
  return expandedFlights.value.has(flightId)
}

function descargarReporte() {
  // Build headers: fixed + commodity columns
  const fixedHeaders = [
    t('dashboard.csvHeaders.flightNumber'),
    t('dashboard.csvHeaders.route'),
    t('dashboard.csvHeaders.date'),
    t('dashboard.csvHeaders.status'),
    t('dashboard.csvHeaders.uldCount'),
    t('dashboard.csvHeaders.positions'),
    t('dashboard.csvHeaders.grossLbs'),
    t('dashboard.csvHeaders.tareLbs'),
    t('dashboard.csvHeaders.netLbs'),
    t('dashboard.csvHeaders.payloadLbs'),
  ]
  const commodityHeaders = visibleCommodities.value.map(c => c.short)
  const headers = [...fixedHeaders, ...commodityHeaders]

  const rows = filteredFlights.value.map(f => {
    const fixed = [
      `UPS-${f.flightNumber}`,
      `${f.origin}→${f.destination}`,
      f.flightDate || '',
      statusLabel(f.status),
      flightUlds(f.id).length,
      flightPositions(f.id),
      grossLbs(f.id),
      totalTareLbs(f.id),
      netLbs(f.id),
      payloadLbs(f.id),
    ]
    const commodityVals = visibleCommodities.value.map(c => commodityPayload(f.id, c.type) || '')
    return [...fixed, ...commodityVals]
  })
  downloadCSV(headers, rows, `${t('dashboard.csvFilename')}-${new Date().toISOString().slice(0, 10)}.csv`)
}

function getStatusDot(status) {
  if (status === 'SCHEDULED') return 'bg-slate-300'
  if (status === 'BOARDING') return 'bg-slate-400'
  if (status === 'DEPARTED') return 'bg-slate-600'
  if (status === 'ARRIVED') return 'bg-slate-800'
  if (status === 'CANCELLED') return 'bg-slate-200'
  if (status === 'DELAYED') return 'bg-slate-400'
  return 'bg-slate-200'
}

function statusStyle(status) {
  const map = {
    SCHEDULED: { background: '#e2e8f0', color: '#475569' },
    BOARDING: { background: '#e2e8f0', color: '#475569' },
    DEPARTED: { background: '#94a3b8', color: '#fff' },
    ARRIVED: { background: '#1e293b', color: '#fff' },
    CANCELLED: { background: '#f1f5f9', color: '#94a3b8' },
    DELAYED: { background: '#fef08a', color: '#854d0e' },
  }
  return map[status] || { background: '#e2e8f0', color: '#475569' }
}

function statusLabel(status) {
  const map = {
    SCHEDULED: 'SCH',
    BOARDING: 'BRD',
    DEPARTED: 'DPT',
    ARRIVED: 'ARR',
    CANCELLED: 'CNL',
    DELAYED: 'DLY',
  }
  return map[status] || status?.slice(0, 3) || '—'
}

onMounted(async () => {
  loading.value = true
  await loadCommodities()
  await appStore.loadFlights()
  if (appStore.flights.length) {
    await Promise.all([
      appStore.loadUlds(),
      appStore.loadAllMawbs(),
      appStore.loadUldAwbs(),

    ])
  }
  loading.value = false
})
</script>

<style scoped>
@keyframes slideDown {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

tr[v-show="true"] td > div {
  animation: slideDown 0.2s ease-out;
}

.ds-card {
  @apply bg-white border border-slate-200 rounded-lg px-3 py-2.5;
}
.ds-card-label {
  @apply text-[10px] font-mono font-semibold text-slate-500 uppercase tracking-wide mb-1;
}
.ds-card-value {
  @apply text-[18px] font-mono font-bold text-slate-900;
}
.ds-btn-secondary {
  @apply px-3 py-1.5 rounded-md text-[12px] font-semibold font-mono border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition;
}
</style>