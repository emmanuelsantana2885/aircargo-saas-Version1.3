<template>
  <div class="ds-page">

    <!-- Header -->
    <header class="ds-section-header">
      <div>
        <h1 class="ds-title">{{ t('flights.title') }}</h1>
        <p class="ds-subtitle">{{ t('flights.subtitle') }}</p>
      </div>
      <div class="flex items-center gap-2 md:gap-3 flex-wrap">
        <div v-if="store.error" class="text-[14px] font-mono text-slate-600 bg-slate-50 border border-slate-200 px-3 py-1.5 rounded-lg">
          {{ store.error }}
        </div>
        <span class="ds-chip">{{ t('flights.countFlights', { n: filteredFlights.length }) }}</span>
        <FilterBar
          v-model:search-text="searchText"
          v-model:destination="destFilter"
          v-model:date-from="dateFrom"
          v-model:date-to="dateTo"
          :show-period-presets="true"
          :show-date-from="true"
          :show-date-to="true"
          :show-destination="true"
          :show-count="true"
          :filtered-count="filteredFlights.length"
          :total-count="store.flights.length"
          :search-placeholder="t('flights.searchPlaceholder')"
          container-class="!gap-1"
        />
        <button @click="openCreate" class="ds-btn-primary">
          <component :is="icons.Plus" :size="14" :stroke-width="2.5" /> {{ t('flights.newFlight') }}
        </button>
      </div>
    </header>

    <!-- Table -->
    <section class="ds-table-section">

      <div class="table-scroll-wrapper flex-1 min-h-0">
      <div class="ds-table-header" style="min-width: 960px">
        <div class="col-span-2">{{ t('flights.table.flight') }}</div>
        <div class="col-span-2">{{ t('flights.table.route') }}</div>
        <div class="col-span-1">{{ t('flights.table.aircraft') }}</div>
        <div class="col-span-1">{{ t('flights.table.tail') }}</div>
        <div class="col-span-1 text-center">{{ t('flights.table.date') }}</div>
        <div class="col-span-1 text-center">POS</div>
        <div class="col-span-1 text-center">{{ t('flights.table.payloadKg') }}</div>
        <div class="col-span-1 text-center">{{ t('flights.table.availPayload') }}</div>
        <div class="col-span-1 text-center">{{ t('flights.table.status') }}</div>
        <div class="col-span-1 text-center">{{ t('flights.table.actions') }}</div>
      </div>

      <EmptyState v-if="store.loading" :title="t('common.loading')" loading />

      <EmptyState v-else-if="filteredFlights.length === 0" :title="t('flights.empty')" :hint="t('flights.emptyHint')" :icon="icons.PlaneDeparture">
        <button @click="openCreate" class="ds-btn-secondary mt-1">
          + {{ t('flights.createFirstFlight') }}
        </button>
      </EmptyState>

      <div v-else class="divide-y divide-slate-100 text-[13px] text-slate-950 overflow-y-auto flex-1 min-h-0 scrollbar-none">
        <div v-for="f in filteredFlights" :key="f.id"
          class="ds-table-row group"
          @click="selectFlight(f)">

          <div class="col-span-2 font-mono font-black text-slate-950 relative z-10 flex items-center gap-2">
            <span class="text-[13px] font-bold text-white bg-slate-800 rounded-md px-2 py-0.5 uppercase tracking-wider">{{ airlineCode(f) }}</span>
            <span>{{ f.flightNumber }}</span>
          </div>
          <div class="col-span-2 font-semibold text-slate-950 relative z-10">
            {{ f.origin }} <span class="text-slate-400 mx-1">→</span> {{ f.destination }}
          </div>
          <div class="col-span-1 font-mono text-[13px] text-slate-950 relative z-10">{{ f.aircraftType }}</div>
          <div class="col-span-1 font-mono text-[13px] text-slate-950 relative z-10">{{ f.aircraftReg || 'TMP-' + f.flightNumber }}</div>
          <div class="col-span-1 text-center font-mono text-[13px] text-slate-950 relative z-10">{{ f.flightDate }}</div>
          <div class="col-span-1 text-center font-mono font-black text-slate-950 relative z-10">{{ f.totalPositions || '—' }}</div>
          <div class="col-span-1 text-center font-mono font-black text-slate-950 relative z-10">
            {{ f.maxPayloadKg ? Number(f.maxPayloadKg).toLocaleString() : '—' }}
          </div>
          <div class="col-span-1 text-center font-mono font-bold text-[13px] relative z-10">
            <span v-if="flightAvailable(f) != null"
              :class="flightAvailable(f) >= 0 ? 'text-emerald-700' : 'text-red-600'">
              {{ flightAvailable(f).toLocaleString() }} lbs
            </span>
            <span v-else class="text-slate-400">—</span>
          </div>

          <!-- Status flow -->
          <div class="col-span-1 flex justify-center relative z-10">
            <div class="flex items-center gap-2">
              <div v-for="step in statusSteps" :key="step.key"
                class="flex flex-col items-center">
                <span class="h-2.5 w-2.5 rounded-full border-2 transition-all"
                  :class="f.status === step.key ? step.active : (statusOrder.indexOf(f.status) > statusOrder.indexOf(step.key) ? 'bg-slate-400 border-slate-500' : 'bg-slate-100 border-slate-300')">
                </span>
                <span class="text-[12px] font-mono mt-0.5 font-bold uppercase"
                  :class="f.status === step.key ? step.labelClass : 'text-slate-400'">
                  {{ step.label }}
                </span>
              </div>
            </div>
          </div>

          <div class="col-span-1 flex justify-center gap-1.5 relative z-10 ds-row-actions">
            <button @click.stop="openEdit(f)"
              class="w-7 h-7 flex items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:border-slate-950 hover:text-slate-950 transition">
              <component :is="icons.Pencil" :size="13" :stroke-width="2" />
            </button>
            <button @click.stop="confirmDelete(f)"
              class="w-7 h-7 flex items-center justify-center rounded-lg border border-slate-100 text-slate-400 hover:border-red-300 hover:text-red-500 transition">
              <component :is="icons.Trash" :size="13" :stroke-width="2" />
            </button>
          </div>
        </div>
      </div>
      </div>
    </section>

    <!-- Modal -->
    <div v-if="showModal" class="ds-modal-backdrop" @click.self="closeModal">
      <div class="ds-modal-panel">

        <div class="ds-modal-header">
          <h2 class="ds-modal-title">
            {{ editingFlight ? t('flights.editFlight') : t('flights.newFlight') }}
          </h2>
          <button @click="closeModal" class="text-slate-400 hover:text-slate-950 transition">
            <component :is="icons.X" :size="18" :stroke-width="2" />
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="ds-label">{{ t('flights.form.flightNumber') }} *</label>
            <input v-model="form.flightNumber" type="text" placeholder="335" class="ds-input" />
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.flightDate') }} *</label>
            <LocaleDatePicker v-model="form.flightDate" class="w-full" />
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.origin') }}</label>
            <input v-model="form.origin" type="text" placeholder="SDQ" maxlength="3" class="ds-input uppercase" />
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.destination') }}</label>
            <input v-model="form.destination" type="text" placeholder="MIA" maxlength="3" class="ds-input uppercase" />
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.aircraftType') }}</label>
            <select v-model="form.aircraftType" class="ds-input">
              <option v-for="ty in aircraftTypes" :key="ty" :value="ty">{{ ty }}</option>
            </select>
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.tail') }}</label>
            <input v-model="form.aircraftReg" type="text" placeholder="N-372-UP" class="ds-input uppercase" />
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.airline') }} *</label>
            <select v-model="form.airlineId" class="ds-input">
              <option value="" disabled>{{ t('flights.form.selectAirline') }}</option>
              <option v-for="a in airlines" :key="a.id" :value="a.id">{{ a.code }} — {{ a.name }}</option>
            </select>
            <p v-if="airlinesError" class="text-[12px] font-mono text-slate-400 mt-1">{{ t('flights.form.airlinesLoadError') }}</p>
          </div>
          <div>
            <label class="ds-label">{{ t('flights.form.uldPositions') }}</label>
            <input v-model.number="form.totalPositions" type="number" placeholder="31" class="ds-input" />
          </div>
          <div>
            <label class="ds-label">Max Payload (kg)</label>
            <input v-model.number="form.maxPayloadKg" type="number" placeholder="45000" class="ds-input" />
          </div>
          <div class="col-span-2">
            <label class="ds-label">{{ t('flights.form.status') }}</label>
            <select v-model="form.status" class="ds-input">
              <option v-for="s in flightStatuses" :key="s" :value="s">{{ s }}</option>
            </select>
          </div>
        </div>

        <div class="flex justify-end gap-2 mt-6 pt-4 border-t border-slate-200">
          <button @click="closeModal" class="ds-btn-secondary">
            {{ t('common.cancel') }}
          </button>
          <button @click="saveForm" :disabled="saving" class="ds-btn-primary">
            <component :is="icons.Check" v-if="!saving" :size="14" :stroke-width="2.5" />
            <span>{{ saving ? t('common.saving') : (editingFlight ? t('common.update') : t('flights.newFlight')) }}</span>
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { airlinesApi } from '../api/airlines'
import { uldsApi } from '../api/ulds'
import { useIcons } from '../composables/useIcons'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import { extractError } from '../utils/error'
import FilterBar from '../components/FilterBar.vue'
import LocaleDatePicker from '../components/LocaleDatePicker.vue'
import EmptyState from '../components/EmptyState.vue'

const icons = useIcons()
const { t } = useI18n()
const store = useAppStore()
const router = useRouter()
const toast = useToastStore()
const { confirm } = useConfirm()

const airlines = ref([])
const airlinesError = ref(false)
const searchText = ref('')
const destFilter = ref('')
const dateFrom = ref('')
const dateTo = ref('')

const flightWeights = ref({})

function isBellyPos(pos) {
  if (!pos) return false
  const u = pos.trim().toUpperCase()
  if (u === 'LOOSE' || u === 'BULK') return true
  const bellyMap = { B757: ['31','34'], B767: ['A','B'], A300: ['A','B'], A310: ['A','B'], A330: ['A','B'], B777: ['A','B'], B747: ['31','32','33','34'], MD11: ['31','34'] }
  for (const [, positions] of Object.entries(bellyMap)) {
    if (positions.map(p => p.toUpperCase()).includes(u)) return true
  }
  return false
}

function loadFlightWeights() {
  uldsApi.getAll({ size: 500 }).then(res => {
    const ulds = res.data?.content || res.data || []
    const map = {}
    for (const uld of ulds) {
      const fid = uld.flightId
      if (!fid) continue
      if (!map[fid]) map[fid] = { effectiveGross: 0, gross: 0, tare: 0, uldCount: 0 }
      const w = uld.grossWeightLbs || 0
      const t = uld.tareLbs || 0
      map[fid].gross += w
      map[fid].tare += t
      map[fid].uldCount++
      if (isBellyPos(uld.position)) {
        map[fid].effectiveGross += (w - t)
      } else {
        map[fid].effectiveGross += w
      }
    }
    flightWeights.value = map
  }).catch(() => {})
}

function flightAvailable(f) {
  const fw = flightWeights.value[f.id]
  if (!fw || !f.maxPayloadKg) return null
  const payloadLbs = f.maxPayloadKg * 2.20462
  return Math.round(payloadLbs - fw.effectiveGross)
}

const filteredFlights = computed(() => {
  let list = store.flights
  if (dateFrom.value) list = list.filter(f => f.flightDate >= dateFrom.value)
  if (dateTo.value) list = list.filter(f => f.flightDate <= dateTo.value)
  if (destFilter.value) {
    const d = destFilter.value.toUpperCase()
    list = list.filter(f => (f.destination || '').toUpperCase() === d)
  }
  const q = searchText.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(f => {
    const haystack = [
      f.flightNumber, f.origin, f.destination, f.aircraftType,
      f.aircraftReg, f.status, f.flightDate,
      airlineCode(f),
    ].filter(Boolean).join(' ').toLowerCase()
    return haystack.includes(q)
  })
})

onMounted(async () => {
  await Promise.all([
    store.loadFlights(),
    airlinesApi.getAll().then(r => { airlines.value = r.data }).catch((e) => { toast.error(extractError(e)); airlinesError.value = true }),
  ])
  loadFlightWeights()
})

// ── Enums ─────────────────────────────────────────────────────
const aircraftTypes  = ['B767','B757','B737','B747','B777','A300','A310','A330','MD11','DC8','OTHER']
const flightStatuses = ['SCHEDULED','BOARDING','DEPARTED','ARRIVED','CANCELLED','DELAYED']
const statusSteps    = [
  { key: 'SCHEDULED', label: 'SCHED', active: 'bg-slate-500 border-slate-600', labelClass: 'text-slate-700' },
  { key: 'BOARDING',  label: 'BOARD', active: 'bg-slate-500 border-slate-600',  labelClass: 'text-slate-700' },
  { key: 'DEPARTED',  label: 'DEP',   active: 'bg-slate-500 border-slate-600', labelClass: 'text-slate-700' },
  { key: 'ARRIVED',   label: 'ARR',   active: 'bg-slate-500 border-slate-600', labelClass: 'text-slate-700' },
]
const statusOrder = ['SCHEDULED','BOARDING','DEPARTED','ARRIVED','CANCELLED','DELAYED']

// ── Modal ─────────────────────────────────────────────────────
const showModal     = ref(false)
const editingFlight = ref(null)
const saving        = ref(false)

const emptyForm = () => ({
  airlineId:      '',
  flightNumber:   '',
  origin:         'SDQ',
  destination:    'MIA',
  aircraftType:   'B767',
  aircraftReg:    '',
  flightDate:     new Date().toISOString().split('T')[0],
  status:         'SCHEDULED',
  totalPositions: 31,
  maxPayloadKg:   45000,
})
const form = ref(emptyForm())

function openCreate() {
  editingFlight.value = null
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(f) {
  editingFlight.value = f
  form.value = {
    airlineId:      f.airlineId || '',
    flightNumber:   f.flightNumber || '',
    origin:         f.origin,
    destination:    f.destination,
    aircraftType:   f.aircraftType,
    aircraftReg:    f.aircraftReg || '',
    flightDate:     f.flightDate,
    status:         f.status,
    totalPositions: f.totalPositions,
    maxPayloadKg:   f.maxPayloadKg,
  }
  showModal.value = true
}

function airlineCode(f) {
  const a = airlines.value.find(x => x.id === f.airlineId)
  return a?.code || (f.flightNumber ? 'UPS' : '—')
}

function closeModal() {
  showModal.value = false
  editingFlight.value = null
}

async function saveForm() {
  if (!form.value.airlineId || !form.value.flightNumber || !form.value.flightDate) {
    toast.warning(t('flights.validation.requiredFields'))
    return
  }
  try {
    saving.value = true
    if (editingFlight.value) {
      await store.updateFlight(editingFlight.value.id, form.value)
    } else {
      const created = await store.createFlight(form.value)
      store.selectedFlightId = created.id
    }
    closeModal()
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    saving.value = false
  }
}

async function confirmDelete(f) {
  if (!(await confirm({ message: t('flights.confirmDeleteWithNumber', { number: f.flightNumber }), danger: true }))) return
  try {
    await store.deleteFlight(f.id)
  } catch (e) {
    toast.error(extractError(e))
  }
}

function selectFlight(f) {
  store.selectedFlightId = f.id
  router.push({ name: 'load-planning', query: { flightId: f.id } })
}
</script>


