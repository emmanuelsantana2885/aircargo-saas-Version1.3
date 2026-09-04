<template>
  <div class="flex flex-wrap items-end gap-2 shrink-0" :class="containerClass">
    <!-- Period Presets -->
    <div v-if="showPeriodPresets" class="flex flex-col gap-0.5">
      <label class="ds-label">{{ t('filterBar.period') }}</label>
      <select :value="activePeriod" @change="onPeriodChange($event.target.value)"
        class="ds-input w-auto min-w-[120px]">
        <option value="custom">{{ t('filterBar.periods.custom') }}</option>
        <option value="today">{{ t('filterBar.periods.today') }}</option>
        <option value="week">{{ t('filterBar.periods.week') }}</option>
        <option value="month">{{ t('filterBar.periods.month') }}</option>
        <option value="quarter">{{ t('filterBar.periods.quarter') }}</option>
        <option value="year">{{ t('filterBar.periods.year') }}</option>
      </select>
    </div>

    <!-- Date From -->
    <div v-if="showDateFrom" class="flex flex-col gap-0.5">
      <label class="ds-label">{{ t('filterBar.from') }}</label>
      <LocaleDatePicker :model-value="dateFrom" @update:model-value="onDateFromValue"
        class="w-[150px]" />
    </div>

    <!-- Date To -->
    <div v-if="showDateTo" class="flex flex-col gap-0.5">
      <label class="ds-label">{{ t('filterBar.to') }}</label>
      <LocaleDatePicker :model-value="dateTo" @update:model-value="onDateToValue"
        class="w-[150px]" />
    </div>

    <!-- Separator -->
    <div v-if="(showDateFrom || showPeriodPresets) && hasDropdowns" class="h-6 w-[1px] bg-slate-200 self-end mb-0.5"></div>

    <!-- Commodity Dropdown -->
    <div v-if="showCommodity" class="flex flex-col gap-0.5">
      <label class="ds-label">{{ t('filterBar.commodity') }}</label>
      <select :value="commodity" @change="$emit('update:commodity', $event.target.value)"
        class="ds-input w-auto min-w-[130px]">
        <option value="">{{ t('filterBar.all') }}</option>
        <option v-for="c in commodities" :key="c.code" :value="c.code">{{ c.label }}</option>
      </select>
    </div>

    <!-- Status Dropdown -->
    <div v-if="showStatus" class="flex flex-col gap-0.5 relative">
      <label class="ds-label">{{ t('filterBar.status') }}</label>
      <button @click="statusOpen = !statusOpen"
        class="ds-input w-auto min-w-[130px] flex items-center gap-1.5 cursor-pointer">
        <span v-if="status" class="inline-block w-2 h-2 rounded-full" :class="statusColor(status)"></span>
        <span class="truncate">{{ statusLabel }}</span>
        <svg class="ml-auto shrink-0" xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9" /></svg>
      </button>
      <div v-if="statusOpen" class="absolute top-full left-0 mt-1.5 bg-white border border-slate-200 rounded-xl shadow-xl shadow-slate-900/10 z-50 min-w-[160px] overflow-hidden">
        <div @click="$emit('update:status', ''); statusOpen = false"
          class="px-3 py-2 text-[12px] font-mono hover:bg-slate-100 cursor-pointer border-b border-slate-100"
          :class="!status ? 'bg-slate-50 font-bold' : ''">{{ t('filterBar.all') }}</div>
        <div class="max-h-64 overflow-y-auto">
          <div v-for="opt in statusOptions" :key="opt.value"
            @click="$emit('update:status', opt.value); statusOpen = false"
            class="px-3 py-2 text-[12px] font-mono hover:bg-slate-100 cursor-pointer flex items-center gap-2"
            :class="status === opt.value ? 'bg-slate-50 font-bold' : ''">
            <span class="w-2 h-2 rounded-full" :class="opt.dotClass"></span>
            {{ opt.label }}
          </div>
        </div>
      </div>
    </div>

    <!-- Separator between dropdowns and search fields -->
    <div v-if="hasDropdowns && hasTextFields" class="h-6 w-[1px] bg-slate-200 self-end mb-0.5"></div>

    <!-- AWB Number search -->
    <div v-if="showMawb" class="flex flex-col gap-0.5 flex-1 min-w-[140px] max-w-[200px]">
      <label class="ds-label">{{ t('filterBar.mawb') }}</label>
      <input type="text" :value="mawbNumber" @input="$emit('update:mawbNumber', $event.target.value)"
        placeholder="AWB..." class="ds-input" />
    </div>

    <!-- Shipper search -->
    <div v-if="showShipper" class="flex flex-col gap-0.5 flex-1 min-w-[140px] max-w-[200px]">
      <label class="ds-label">{{ t('filterBar.shipper') }}</label>
      <input type="text" :value="shipperName" @input="$emit('update:shipperName', $event.target.value)"
        placeholder="Shipper..." class="ds-input" />
    </div>

    <!-- Consignee search -->
    <div v-if="showConsignee" class="flex flex-col gap-0.5 flex-1 min-w-[140px] max-w-[200px]">
      <label class="ds-label">{{ t('filterBar.consignee') }}</label>
      <input type="text" :value="consigneeName" @input="$emit('update:consigneeName', $event.target.value)"
        placeholder="Consignee..." class="ds-input" />
    </div>

    <!-- HAWB search -->
    <div v-if="showHawb" class="flex flex-col gap-0.5 flex-1 min-w-[120px] max-w-[180px]">
      <label class="ds-label">{{ t('filterBar.hawb') }}</label>
      <input type="text" :value="hawbNumber" @input="$emit('update:hawbNumber', $event.target.value)"
        placeholder="HAWB..." class="ds-input" />
    </div>

    <!-- Destination search -->
    <div v-if="showDestination" class="flex flex-col gap-0.5 flex-1 min-w-[80px] max-w-[100px]">
      <label class="ds-label">{{ t('filterBar.dest') }}</label>
      <input type="text" :value="destination" @input="$emit('update:destination', $event.target.value)"
        placeholder="MIA" class="ds-input font-mono uppercase" maxlength="3" />
    </div>

    <!-- Generic Search (legacy) -->
    <div v-if="showSearch && !showMawb" class="flex flex-col gap-0.5 flex-1 min-w-[180px]">
      <label v-if="searchLabel" class="ds-label">{{ searchLabel }}</label>
      <div class="flex items-center gap-1 relative">
        <svg class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
        <input type="text" :value="searchText" @input="onSearchInput"
          :placeholder="searchPlaceholder"
          class="ds-input flex-1 pl-8" />
        <span v-if="searchText" @click="$emit('update:searchText', '')"
          class="text-[14px] text-slate-400 hover:text-slate-600 cursor-pointer px-1" :title="t('filterBar.tooltipClear')">&#10005;</span>
      </div>
    </div>

    <!-- Custom Slot -->
    <slot></slot>

    <!-- Search Button (server-side filtering) -->
    <div v-if="showSearchButton" class="self-end mb-0.5">
      <button @click="$emit('search')" :disabled="loading"
        class="ds-btn-primary flex items-center gap-1.5">
        <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
        {{ loading ? loadingLabel : searchButtonLabel }}
      </button>
    </div>

    <!-- Clear Button -->
    <div v-if="showClear && hasActiveFilters" class="self-end mb-0.5">
      <button @click="onClear" class="ds-btn-secondary">{{ t('filterBar.clear') }}</button>
    </div>

    <!-- Row Count -->
    <div v-if="showCount" class="self-end mb-0.5 ml-auto">
      <span class="ds-chip !text-[12px] !py-1">
        {{ filteredCount }} / {{ totalCount }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCommodities } from '../composables/useCommodities'
import LocaleDatePicker from './LocaleDatePicker.vue'

const { t } = useI18n()

const props = defineProps({
  dateFrom: { type: String, default: '' },
  dateTo: { type: String, default: '' },
  commodity: { type: String, default: '' },
  status: { type: String, default: '' },
  searchText: { type: String, default: '' },
  mawbNumber: { type: String, default: '' },
  shipperName: { type: String, default: '' },
  consigneeName: { type: String, default: '' },
  hawbNumber: { type: String, default: '' },
  destination: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  filteredCount: { type: Number, default: 0 },
  totalCount: { type: Number, default: 0 },
  containerClass: { type: String, default: '' },
  showDateFrom: { type: Boolean, default: false },
  showDateTo: { type: Boolean, default: false },
  showCommodity: { type: Boolean, default: false },
  showStatus: { type: Boolean, default: false },
  showSearch: { type: Boolean, default: true },
  showSearchButton: { type: Boolean, default: false },
  showClear: { type: Boolean, default: false },
  showCount: { type: Boolean, default: false },
  showPeriodPresets: { type: Boolean, default: false },
  showMawb: { type: Boolean, default: false },
  showShipper: { type: Boolean, default: false },
  showConsignee: { type: Boolean, default: false },
  showHawb: { type: Boolean, default: false },
  showDestination: { type: Boolean, default: false },
  searchLabel: { type: String, default: '' },
  searchPlaceholder: { type: String, default: 'Search...' },
  searchButtonLabel: { type: String, default: 'Search' },
  loadingLabel: { type: String, default: 'Loading...' },
  statusOptions: { type: Array, default: () => [] },
  debounceMs: { type: Number, default: 0 },
})

const emit = defineEmits([
  'update:dateFrom', 'update:dateTo', 'update:commodity',
  'update:status', 'update:searchText', 'search', 'clear',
  'update:mawbNumber', 'update:shipperName', 'update:consigneeName',
  'update:hawbNumber', 'update:destination'
])

const { commodities } = useCommodities()
const statusOpen = ref(false)
const activePeriod = ref('custom')

const hasDropdowns = computed(() => props.showCommodity || props.showStatus)
const hasTextFields = computed(() => props.showMawb || props.showShipper || props.showConsignee || props.showHawb || props.showDestination || props.showSearch)

const hasActiveFilters = computed(() =>
  props.dateFrom || props.dateTo || props.commodity || props.status || props.searchText ||
  props.mawbNumber || props.shipperName || props.consigneeName || props.hawbNumber || props.destination
)

const statusLabel = computed(() => {
  if (!props.status) return t('filterBar.all')
  const opt = props.statusOptions.find(o => o.value === props.status)
  return opt?.label || props.status
})

function statusColor(s) {
  const opt = props.statusOptions.find(o => o.value === s)
  return opt?.dotClass || 'bg-slate-400'
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

function getPeriodDates(period) {
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = now.getMonth()
  switch (period) {
    case 'today':
      return { from: today(), to: today() }
    case 'week': {
      const d = new Date(now)
      const day = d.getDay()
      d.setDate(d.getDate() - (day === 0 ? 6 : day - 1))
      return { from: d.toISOString().slice(0, 10), to: today() }
    }
    case 'month':
      return { from: `${yyyy}-${String(mm + 1).padStart(2, '0')}-01`, to: today() }
    case 'quarter': {
      const qm = Math.floor(mm / 3) * 3
      return { from: `${yyyy}-${String(qm + 1).padStart(2, '0')}-01`, to: today() }
    }
    case 'year':
      return { from: `${yyyy}-01-01`, to: today() }
    default:
      return null
  }
}

function onPeriodChange(period) {
  activePeriod.value = period
  const dates = getPeriodDates(period)
  if (dates) {
    emit('update:dateFrom', dates.from)
    emit('update:dateTo', dates.to)
  }
}

function onDateFromValue(v) {
  activePeriod.value = 'custom'
  emit('update:dateFrom', v)
}

function onDateToValue(v) {
  activePeriod.value = 'custom'
  emit('update:dateTo', v)
}

function onClear() {
  activePeriod.value = 'custom'
  emit('update:mawbNumber', '')
  emit('update:shipperName', '')
  emit('update:consigneeName', '')
  emit('update:hawbNumber', '')
  emit('update:destination', '')
  emit('clear')
}

let debounceTimer = null
function onSearchInput(e) {
  const val = e.target.value
  if (props.debounceMs > 0) {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => emit('update:searchText', val), props.debounceMs)
  } else {
    emit('update:searchText', val)
  }
}
</script>
