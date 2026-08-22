<template>
  <div class="ds-page max-w-[1600px] mx-auto">

    <!-- ═══ HEADER ═══ -->
    <div class="ds-section-header mb-4 shrink-0">
      <div class="flex items-center gap-3">
        <h1 class="ds-title">Reviews -- Datos</h1>
        <div v-if="rows.length" class="h-4 w-[1px] bg-slate-300"></div>
        <span v-if="rows.length" class="ds-stat">
          {{ rows.length }} registro(s)
        </span>
      </div>
      <div class="flex items-center gap-2">
        <button @click="handleExport" :disabled="!rows.length"
          class="ds-btn-secondary disabled:opacity-40 disabled:cursor-not-allowed">
          <IconDownload :size="14" />
          CSV
        </button>
      </div>
    </div>

    <!-- ═══ FILTER BAR ═══ -->
    <FilterBar
      v-model:date-from="dateFrom"
      v-model:date-to="dateTo"
      :show-period-presets="true"
      :show-date-from="true"
      :show-date-to="true"
      :show-search="false"
      :show-search-button="true"
      :show-clear="true"
      :loading="loading"
      search-button-label="Consultar"
      loading-label="Consultando..."
      @search="loadData"
      @clear="clearFilters"
      container-class="mb-4 shrink-0 p-3 rounded-lg border border-slate-200 bg-slate-50"
    >
      <div class="h-6 w-[1px] bg-slate-300 self-end mb-0.5"></div>
      <div class="flex flex-col gap-0.5">
        <label class="ds-label">Tipo</label>
        <select v-model="exportType" class="ds-input !w-auto min-w-[130px]">
          <option v-for="t in types" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
      </div>
    </FilterBar>

    <!-- ═══ TABLE ═══ -->
    <div class="ds-table-section">
      <!-- Table header bar -->
      <div class="ds-table-header px-4 py-2 flex items-center justify-between shrink-0">
        <span class="text-[13px] font-bold text-white uppercase tracking-wider font-mono">
          {{ typeLabel }} — Datos
        </span>
        <span class="text-[12px] font-mono text-slate-300">
          {{ rows.length > 0 ? rows.length + ' registro(s)' : '' }}
        </span>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex-1 flex items-center justify-center">
        <div class="flex items-center gap-2">
          <div class="w-4 h-4 border-2 border-slate-300 border-t-slate-800 rounded-full animate-spin"></div>
          <span class="text-[13px] font-mono text-slate-500 uppercase tracking-wider">Cargando datos...</span>
        </div>
      </div>

      <!-- Error -->
      <div v-else-if="tableError" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <IconAlertCircle :size="32" class="mx-auto mb-2 text-red-400" />
          <span class="text-[13px] font-mono text-red-500">{{ tableError }}</span>
        </div>
      </div>

      <!-- Empty -->
      <div v-else-if="!rows.length" class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <IconSearch :size="32" class="mx-auto mb-2 text-slate-300" />
          <p class="text-[13px] font-mono text-slate-400 uppercase tracking-wider">
            Selecciona tipo y presiona Consultar
          </p>
        </div>
      </div>

      <!-- Data -->
      <div v-else class="flex-1 min-h-0 overflow-auto">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="text-[13px] font-mono" :style="tableStyle">
          <colgroup>
            <col v-for="(col, ci) in cols" :key="col" :style="colStyle(ci)" />
          </colgroup>
          <thead class="sticky top-0 z-20">
            <tr class="bg-slate-700 text-white">
              <th v-for="(col, ci) in cols" :key="col"
                class="text-left px-3 py-2 font-bold uppercase tracking-wider whitespace-nowrap relative border-r border-slate-600 last:border-r-0"
                :class="isNumCol(col) ? 'text-center' : ''"
                :style="colStyle(ci)">
                {{ col }}
                <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40"
                  @pointerdown="startColResize(ci, $event)">
                  <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in rows" :key="ri"
              class="border-t border-slate-200 hover:bg-blue-50 transition-colors"
              :class="ri % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'">
              <td v-for="col in cols" :key="col"
                class="px-3 py-1.5 whitespace-nowrap overflow-hidden text-ellipsis"
                :class="isNumCol(col) ? 'text-center font-semibold' : ''"
                :style="cellStyle(col, row[col])">
                {{ row[col] }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { exportData } from '../api/exports'
import {
  IconSearch,
  IconDownload,
  IconAlertCircle,
} from '@tabler/icons-vue'
import FilterBar from '../components/FilterBar.vue'

const types = [
  { value: 'MAWBS', label: 'MAWBs' },
  { value: 'BOOKINGS', label: 'Bookings' },
  { value: 'RECEIPTS', label: 'Receipts' },
  { value: 'FLIGHTS', label: 'Flights' },
  { value: 'ULDS', label: 'ULDs' },
  { value: 'HAWBS', label: 'HAWBs' },
]

const exportType = ref('RECEIPTS')
const dateFrom = ref('')
const dateTo = ref('')

const rows = ref([])
const cols = ref([])
const loading = ref(false)
const tableError = ref('')

const typeLabel = computed(() => types.find(t => t.value === exportType.value)?.label || exportType.value)

const tableStyle = computed(() => ({
  tableLayout: 'fixed',
  minWidth: Math.max(900, cols.value.length * 140) + 'px',
}))

function isNumCol(col) {
  return ['pieces', 'skids', 'actual kg', 'chargeable kg', 'reported weight kg', 'chargeable weight kg', 'weight kg', 'tare lbs', 'reserved kg'].includes(col)
}

function cellStyle(col, val) {
  if (!val || val === '') return { color: 'var(--muted, #94a3b8)' }
  if (col === 'transaction type') {
    const colors = { CREATE: '#34d399', UPDATE: '#fbbf24', READ: '#818cf8', DELETE: '#f87171' }
    return { color: colors[val] || 'var(--text, #1e293b)', fontWeight: '700' }
  }
  if (col === 'user role') {
    return { color: '#c084fc', fontWeight: '600' }
  }
  return { color: 'var(--text, #1e293b)' }
}

function clearFilters() {
  exportType.value = 'RECEIPTS'
  dateFrom.value = ''
  dateTo.value = ''
}

// ── Column resize ──
const DEFAULT_COL_WIDTH = 140
const colWidths = reactive({})
let resizeColIndex = null
let resizeStartX = 0
let resizeStartWidth = 0

function startColResize(colIdx, e) {
  resizeColIndex = colIdx
  resizeStartX = e.clientX
  resizeStartWidth = colWidths[colIdx] || DEFAULT_COL_WIDTH
  document.addEventListener('pointermove', onColResize)
  document.addEventListener('pointerup', stopColResize)
  e.preventDefault()
}

function onColResize(e) {
  if (resizeColIndex === null) return
  const diff = e.clientX - resizeStartX
  colWidths[resizeColIndex] = Math.max(60, resizeStartWidth + diff)
}

function stopColResize() {
  resizeColIndex = null
  document.removeEventListener('pointermove', onColResize)
  document.removeEventListener('pointerup', stopColResize)
}

function colStyle(colIdx) {
  const w = colWidths[colIdx] || DEFAULT_COL_WIDTH
  return { width: w + 'px', minWidth: w + 'px', maxWidth: w + 'px' }
}

async function loadData() {
  loading.value = true
  tableError.value = ''

  try {
    const res = await exportData(exportType.value, 'csv', dateFrom.value || null, dateTo.value || null, false)
    const text = await res.data.text()
    const parsed = parseCsv(text)
    cols.value = parsed.headers
    rows.value = parsed.rows
  } catch {
    tableError.value = 'Error al consultar datos'
    cols.value = []
    rows.value = []
  } finally {
    loading.value = false
  }
}

function parseCsv(text) {
  const lines = text.split('\n').filter(l => l.trim())
  if (!lines.length) return { headers: [], rows: [] }

  function splitLine(line) {
    const result = []
    let current = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (ch === '"') {
        if (inQuotes && i + 1 < line.length && line[i + 1] === '"') {
          current += '"'
          i++
        } else {
          inQuotes = !inQuotes
        }
      } else if (ch === ',' && !inQuotes) {
        result.push(current)
        current = ''
      } else {
        current += ch
      }
    }
    result.push(current.replace(/\r$/, ''))
    return result
  }

  const headers = splitLine(lines[0])
  const rows = []
  for (let i = 1; i < lines.length; i++) {
    const vals = splitLine(lines[i])
    const row = {}
    for (let j = 0; j < headers.length; j++) {
      row[headers[j]] = vals[j] || ''
    }
    rows.push(row)
  }
  return { headers, rows }
}

function handleExport() {
  exportData(exportType.value, 'csv', dateFrom.value || null, dateTo.value || null, false)
    .then(res => {
      const blob = new Blob([res.data], { type: 'text/csv' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `AUDIT_${exportType.value}_${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    })
    .catch(() => {
      tableError.value = 'Error al exportar CSV'
    })
}
</script>
