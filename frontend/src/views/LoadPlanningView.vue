<template>
  <div class="ds-page">

    <header class="ds-section-header flex-row px-3 py-3">
      <div class="flex items-center gap-6">
        <div class="flex flex-col gap-0.5">
           <span class="ds-label">{{ t('common.date') }}</span>
          <LocaleDatePicker v-model="selectedDate" class="w-[140px] cursor-pointer" @change="onDateChange" />
        </div>
        <div class="h-8 w-[1px] bg-slate-200"></div>
        <div class="flex flex-col gap-0.5">
           <span class="ds-label">{{ t('loadPlanning.colFlightNumber') }}</span>
          <select v-model="selectedFlightId" @change="syncFlightMetadata" class="ds-input cursor-pointer min-w-[180px]">
            <option value="" disabled>{{ t('common.selectFlight') }}</option>
            <option v-for="flight in flightDatabase" :key="flight.id" :value="flight.id">
              {{ airlineCodeById(flight.airlineId) }}-{{ flight.flightNumber }} ({{ flight.origin }}→{{ flight.destination }})
            </option>
          </select>
        </div>
        <div class="h-8 w-[1px] bg-slate-200"></div>
        <div class="flex flex-col justify-center">
           <span class="ds-label">{{ t('loadPlanning.airline') }}</span>
          <span class="text-[14px] font-black text-slate-950 uppercase tracking-widest">{{ activeAirlineLabel }}</span>
        </div>
        <div class="h-8 w-[1px] bg-slate-200"></div>
        <div class="flex flex-col justify-center">
           <span class="ds-label">{{ t('loadPlanning.colAircraftTail') }}</span>
          <span class="text-[14px] font-black text-slate-950 uppercase tracking-wider">{{ activeFlightMeta.aircraftReg || '-' }}</span>
        </div>
        <div class="h-8 w-[1px] bg-slate-200"></div>
        <div class="flex flex-col justify-center">
           <span class="ds-label">{{ t('loadPlanning.colRoute') }}</span>
          <span class="text-[14px] font-black text-slate-950 uppercase tracking-widest">{{ (activeFlightMeta.origin || '') + '→' + (activeFlightMeta.destination || '-') }}</span>
        </div>
      </div>
      <div class="flex items-center gap-1.5">
        <button v-if="activeFlightMeta.id && activeFlightMeta.status !== 'DEPARTED' && activeFlightMeta.status !== 'ARRIVED' && activeFlightMeta.status !== 'CANCELLED'"
          @click="dispatchFlight" :title="t('loadPlanning.dispatchFlight')"
          class="ds-btn-primary px-2 py-1.5">
          <component :is="icons.PlaneDeparture" :size="16" :stroke-width="2.5" />
        </button>
        <button @click="triggerImport" :title="t('loadPlanning.uploadManifest')"
          class="ds-btn-secondary px-2 py-1.5">
          <component :is="icons.FileUpload" :size="16" :stroke-width="2" />
        </button>
        <button @click="exportPalletSheets" :title="t('loadPlanning.palletSheets')"
          class="ds-btn-primary px-2 py-1.5 bg-slate-600 hover:bg-slate-500 border-slate-600 hover:border-slate-500">
          <component :is="icons.FileDescription" :size="16" :stroke-width="2" />
        </button>
        <button @click="exportToXLSX" :title="t('loadPlanning.exportManifest')"
          class="ds-btn-primary px-2 py-1.5">
          <component :is="icons.FileExport" :size="16" :stroke-width="2" />
        </button>
      </div>
    </header>

    <!-- ULD Position Summary -->
    <section v-if="activeManifest.length > 0"
      class="flex items-center gap-3 py-1.5 px-3 bg-white border border-slate-400 rounded-lg mb-1 shrink-0 overflow-x-auto lp-scroll-x text-[13px] font-mono">
      <span class="ds-label mb-0 shrink-0">{{ t('loadPlanning.positions') }}</span>
      <div v-for="p in positionSummary" :key="p.pos"
        class="flex items-center gap-1.5 px-2 py-0.5 rounded whitespace-nowrap"
        :class="[p.isBelly ? 'bg-slate-50 border border-slate-300' : 'bg-slate-100 border border-slate-300']">
        <span class="font-black text-slate-950">{{ p.pos }}</span>
        <span class="text-slate-950">{{ p.count }} ULD</span>
        <span class="text-slate-950 text-[13px]">({{ p.pcs }} pcs)</span>
        <span v-if="p.isBelly" class="text-[13px] font-bold text-slate-500 bg-slate-100 px-1 rounded">BELLY</span>
      </div>
      <span class="text-slate-300 mx-1">|</span>
      <span class="text-slate-950 font-bold whitespace-nowrap">A/C: {{ aircraftType }}</span>
      <span class="text-slate-950 font-bold whitespace-nowrap">{{ t('loadPlanning.lblTotalUlds') }} {{ activeManifest.length }}</span>
      <span class="text-slate-300 mx-1">|</span>
      <span class="text-slate-950 font-bold whitespace-nowrap">{{ t('loadPlanning.lblGross') }} {{ calculatedTotals.gross.toLocaleString() }} lbs</span>
      <span class="text-slate-950 font-bold whitespace-nowrap">{{ t('loadPlanning.lblPayload') }} {{ calculatedTotals.payloadLbs.toLocaleString() }} lbs</span>
      <span class="text-slate-300 mx-1">|</span>
      <span class="font-bold whitespace-nowrap px-2 py-0.5 rounded"
        :class="calculatedTotals.availableLbs >= 0 ? 'bg-emerald-100 text-emerald-800 border border-emerald-300' : 'bg-red-100 text-red-800 border border-red-300'">
        {{ t('loadPlanning.lblAvailable') }} {{ calculatedTotals.availableLbs.toLocaleString() }} lbs
      </span>
    </section>

    <!-- Flight ULD Cards -->
    <section v-if="activeFlightMeta.id && (activeManifest.length > 0 || floatingUlds.length > 0)"
      class="floating-drop-zone flex gap-2 py-2 overflow-x-auto shrink-0 lp-scroll-x"
      @dragover.prevent="dragOverFloating = true"
      @dragleave="onDragLeaveFloating"
      @drop.prevent="onDropFloating"
      :class="dragOverFloating ? 'ring-2 ring-slate-400 ring-offset-2 rounded-lg bg-slate-100' : ''">
      <div v-for="(uldGroup, uIdx) in activeManifest" :key="uIdx"
        draggable="true"
        @dragstart="onDragStart(uldGroup.uldId, $event)"
        class="flex-shrink-0 bg-white border rounded-lg px-3 py-2 flex items-center gap-3 text-[13px]"
        :class="getCardBorderStyle(uldGroup.status)">
        <span class="font-black text-slate-950 uppercase tracking-tight">{{ uldGroup.uld }}</span>
        <span class="h-2 w-2 rounded-full" :class="getStatusDotColor(uldGroup.status)"></span>
        <span class="text-slate-950 font-bold uppercase text-[13px]">{{ lpStatus(uldGroup.status) }}</span>
        <span class="text-slate-950 font-bold">{{ uldGroup.items.length }} MAWB</span>
        <span class="text-slate-950">{{ (uldGroup.weight || 0).toLocaleString() }} lb</span>
        <select :value="uldGroup.flightId" @change="onTransferRequest(uldGroup.uldId, uldGroup.uld, uldGroup.flightId, $event.target.value)"
          :disabled="!isLiveUld(uldGroup.uldId)"
          class="ml-1 bg-slate-100 border border-slate-400 rounded px-2 py-1 text-[13px] font-bold text-slate-950 focus:outline-none cursor-pointer"
          :class="{ 'opacity-50 cursor-not-allowed': !isLiveUld(uldGroup.uldId) }">
          <option v-for="f in flightOptionsFor(uldGroup.flightId)" :key="f.id" :value="f.id">
            {{ flightOptionLabel(f) }}
          </option>
        </select>
      </div>
      <div v-if="floatingUlds.length > 0" class="border-l border-slate-400 pl-2 flex gap-2">
        <div v-for="uld in floatingUlds" :key="uld.id"
          draggable="true"
          @dragstart="onDragStart(uld.id, $event)"
          class="flex-shrink-0 bg-white border border-slate-300 border-dashed rounded-lg px-3 py-2 flex items-center gap-2 text-[13px] cursor-grab active:cursor-grabbing select-none">
          <span class="font-black text-slate-700 uppercase tracking-tight">{{ uld.uldNumber || 'SIN-ULD' }}</span>
          <span class="text-slate-500 text-[13px] font-bold">{{ t('ulds.noFlight') }}</span>
          <select :value="uld.flightId" @change="onTransferRequest(uld.id, uld.uldNumber, null, $event.target.value)"
            class="bg-white border border-slate-300 rounded px-2 py-1 text-[13px] font-bold text-slate-950 focus:outline-none cursor-pointer">
            <option value="" disabled>{{ t('ulds.actions.assignFlight') }}</option>
            <option v-for="f in assignableFlights" :key="f.id" :value="f.id">
              {{ flightOptionLabel(f) }}
            </option>
          </select>
          <button @click.stop="deleteUld(uld)" :title="t('ulds.actions.delete')"
            class="p-1 rounded hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors">
            <component :is="icons.Trash" size="16" />
          </button>
        </div>
      </div>
    </section>

    <section @dragover.prevent="dragOver = true" @dragleave="onDragLeave" @drop="onDrop"
      class="flex-1 min-h-0 border border-slate-300 rounded-lg overflow-hidden shadow-pencil-marine bg-white flex flex-col mb-1 transition-shadow duration-150"
      :class="dragOver ? 'ring-2 ring-slate-400 ring-offset-2' : ''">
      <div class="overflow-x-auto flex flex-col flex-1 min-h-0 lp-scroll-x">
      <div class="bg-slate-700 text-white text-[13px] font-bold uppercase tracking-wider lp-grid py-3.5 px-5 items-center shrink-0 border-b border-slate-500 whitespace-nowrap shadow-sm">
        <span class="flex items-center gap-1">
          <svg class="w-2.5 h-2.5 text-slate-950" viewBox="0 0 8 8" fill="none"><circle cx="2" cy="2" r="1" fill="currentColor" /><circle cx="6" cy="2" r="1" fill="currentColor" /><circle cx="2" cy="6" r="1" fill="currentColor" /><circle cx="6" cy="6" r="1" fill="currentColor" /></svg>
          ULD
        </span>
        <span class="text-center">PCS</span>
        <span class="text-center">%</span>
        <span class="text-center">{{ t('loadPlanning.colGross') }}</span>
        <span class="text-center">{{ t('loadPlanning.colTare') }}</span>
        <span class="text-center">{{ t('loadPlanning.colType') }}</span>
        <span class="text-center">{{ t('loadPlanning.colSeal') }}</span>
        <span class="text-center">{{ t('loadPlanning.colPos') }}</span>
        <span class="text-center">{{ t('loadPlanning.colDesc') }}</span>
        <span class="text-center">{{ t('loadPlanning.colMawb') }}</span>
        <span class="text-center">{{ t('loadPlanning.colDest') }}</span>
      </div>

      <div v-if="activeManifest.length === 0"
        class="flex-1 flex flex-col items-center justify-center text-slate-950 text-[14px] gap-2"
        :class="dragOver ? 'bg-slate-50' : ''">
        <span>{{ t('loadPlanning.emptySelectFlight') }}</span>
        <span v-if="floatingUlds.length > 0" class="text-[13px] text-slate-300">{{ t('loadPlanning.dragFloatingHint') }}</span>
      </div>

      <div v-else class="divide-y divide-slate-200 text-[13px] overflow-y-auto flex-1 min-h-0 scrollbar-none"
        @dragover.prevent="onRowDragOver"
        @drop.prevent="onRowDrop">
        <div v-for="(uldGroup, uIdx) in activeManifest" :key="uldGroup.uldId"
          draggable="true"
          @dragstart="onRowDragStart(uldGroup.uldId, $event)"
          @dragenter="onRowDragEnter(uIdx)"
          class="bg-white lp-container-block transition-all duration-150"
          :class="[getRowBgStyle(uldGroup.status), { 'opacity-40': rowDragging === uldGroup.uldId, 'ring-2 ring-slate-400': rowDropIndex === uIdx }]">
          <div v-if="uldGroup.items.length === 0" class="lp-grid py-2 px-5 items-center text-slate-950">
            <span
              @pointerdown="onTableUldPointerDown(uldGroup.uldId, $event)"
              class="font-semibold text-slate-950 truncate cursor-grab active:cursor-grabbing select-none">{{ uldGroup.uld }}</span>
            <span class="text-center">0</span>
            <span class="text-center">-</span>
            <span class="text-center">{{ (uldGroup.weight || 0).toLocaleString() }}</span>
            <span class="text-center">{{ (uldGroup.tara || 0).toLocaleString() }}</span>
            <span class="text-center">{{ uldGroup.config }}</span>
            <span class="text-center truncate">{{ uldGroup.sello || '-' }}</span>
            <span class="text-center" :class="posCellClass(uldGroup.pos)">
              <input :value="uldGroup.pos || ''" @blur="e => updatePosition(uldGroup.uldId, e.target.value)"
                @keydown.enter="e => { e.target.blur(); updatePosition(uldGroup.uldId, e.target.value) }"
                class="w-full bg-transparent outline-none border-b border-transparent focus:border-slate-400 text-center text-[13px] font-mono" />
            </span>
            <span class="text-center text-slate-950 italic">—</span>
            <span class="text-center text-slate-950 italic">{{ t('loadPlanning.emptyUld') }}</span>
            <span class="text-center">-</span>
          </div>
          <div v-for="(item, iIdx) in uldGroup.items" :key="iIdx" class="lp-grid py-2 px-5 items-center border-b border-slate-300 last:border-b-0">
            <span v-if="iIdx === 0"
              @pointerdown="onTableUldPointerDown(uldGroup.uldId, $event)"
              class="font-semibold text-slate-950 truncate cursor-grab active:cursor-grabbing select-none">{{ uldGroup.uld }}</span>
            <span v-else class="text-slate-200 text-center">—</span>
            <span class="text-center">{{ item.pcs }}</span>
            <span class="text-center">{{ item.volumePct ? item.volumePct + '%' : '-' }}</span>
            <span v-if="iIdx === 0" class="text-center">{{ (uldGroup.weight || 0).toLocaleString() }}</span>
            <span v-else class="text-slate-200 text-center">—</span>
            <span v-if="iIdx === 0" class="text-center">{{ (uldGroup.tara || 0).toLocaleString() }}</span>
            <span v-else class="text-slate-200 text-center">—</span>
            <span v-if="iIdx === 0" class="text-center">{{ uldGroup.config }}</span>
            <span v-else class="text-slate-200 text-center">—</span>
            <span v-if="iIdx === 0" class="text-center truncate">{{ uldGroup.sello || '-' }}</span>
            <span v-else class="text-slate-200 text-center">—</span>
            <span v-if="iIdx === 0" class="text-center" :class="posCellClass(uldGroup.pos)">
              <input :value="uldGroup.pos || ''" @blur="e => updatePosition(uldGroup.uldId, e.target.value)"
                @keydown.enter="e => { e.target.blur(); updatePosition(uldGroup.uldId, e.target.value) }"
                class="w-full bg-transparent outline-none border-b border-transparent focus:border-slate-400 text-center text-[13px] font-mono" />
            </span>
            <span v-else class="text-slate-200 text-center">—</span>
            <span class="text-center font-mono truncate">{{ item.description }}</span>
            <span class="text-center font-mono truncate">{{ item.mawb }}</span>
            <span class="text-center">{{ item.destino }}</span>
          </div>
        </div>
      </div>
      </div>
    </section>

    <footer class="p-3 border border-slate-300 bg-white rounded-b-lg shrink-0 flex flex-col gap-1 text-[13px]">
      <!-- Undo toast -->
      <div class="flex justify-between items-center">
        <div class="flex gap-4">
          <span><strong>ULDs:</strong> {{ calculatedTotals.uldsCount }}</span>
          <span><strong>Total PCS:</strong> {{ calculatedTotals.pcs.toLocaleString() }}</span>
          <span><strong>Gross:</strong> {{ calculatedTotals.gross.toLocaleString() }} lbs</span>
          <span><strong>Tare:</strong> {{ calculatedTotals.tare.toLocaleString() }} lbs</span>
          <span><strong>Payload:</strong> {{ calculatedTotals.payloadPct }}%</span>
        </div>
        <div class="flex gap-2">
          <span class="inline-block w-2.5 h-2.5 rounded-sm bg-slate-300"></span> Left Behind
          <span class="inline-block w-2.5 h-2.5 rounded-sm bg-slate-400"></span> Incomplete
          <span class="inline-block w-2.5 h-2.5 rounded-sm bg-slate-500"></span> In Ramp
        </div>
      </div>
    </footer>
    <input type="file" ref="fileInput" @change="handleFileImport" accept=".xlsx, .xls" class="hidden" />

    <!-- Flight picker for ULDs without reassignment history -->
    <div v-if="pendingFlightPick"
      class="fixed bottom-16 left-1/2 -translate-x-1/2 z-50 flex items-center gap-3 bg-white border border-slate-300 shadow-lg rounded-lg px-4 py-2.5 text-[13px]">
      <span class="text-slate-900">
        ULD <strong>{{ pendingFlightPick.uldNumber }}</strong>
      </span>
      <template v-if="!showFlightPicker">
        <button @click="showFlightPicker = true"
          class="bg-slate-950 hover:bg-slate-900 text-white font-bold px-3 py-1 rounded text-[14px] uppercase tracking-wider transition-all whitespace-nowrap">
          Asignar a otro vuelo
        </button>
        <button @click="detachToFloating"
          class="bg-slate-100 hover:bg-slate-200 border border-slate-300 text-slate-950 font-bold px-3 py-1 rounded text-[13px] uppercase tracking-wider transition-all whitespace-nowrap">
          Dejar flotante
        </button>
        <button @click="cancelFlightPick"
          class="text-slate-950 hover:text-slate-950 font-bold text-[12px] leading-none ml-1">&times;</button>
      </template>
      <template v-else>
        <select v-model="flightPickValue"
          class="bg-slate-100 border border-slate-400 rounded px-3 py-1.5 text-[14px] font-bold text-slate-950 focus:outline-none cursor-pointer">
          <option value="" selected disabled>Vuelo destino</option>
          <option v-for="f in assignableFlights" :key="f.id" :value="f.id">
            {{ flightOptionLabel(f) }} ({{ f.origin }}→{{ f.destination }})
          </option>
        </select>
        <button @click="confirmFlightPick(flightPickValue)"
          class="bg-slate-950 hover:bg-slate-900 text-white font-bold px-3 py-1 rounded text-[13px] uppercase tracking-wider transition-all">
          Reasignar
        </button>
        <button @click="showFlightPicker = false"
          class="text-slate-950 hover:text-slate-950 font-bold text-[12px] leading-none ml-1">&times;</button>
      </template>
    </div>
    <div v-if="showUndoToast && undoAction"
      class="fixed bottom-16 left-1/2 -translate-x-1/2 z-50 flex items-center gap-3 bg-white border border-slate-300 shadow-lg rounded-lg px-4 py-2.5 text-[13px]">
      <span class="text-slate-900">
        ULD <strong>{{ undoAction.uldNumber }}</strong> reasignado
      </span>
      <button @click="undoLastReassign"
        class="bg-slate-100 hover:bg-slate-200 text-slate-900 font-bold px-3 py-1 rounded text-[13px] uppercase tracking-wider transition-all">
        Deshacer
      </button>
      <button @click="showUndoToast = false"
        class="text-slate-400 hover:text-slate-600 font-bold text-[12px] leading-none ml-1">&times;</button>
    </div>

    <!-- Transfer reason dialog -->
    <div v-if="pendingTransfer"
      class="ds-modal-backdrop"
      @click.self="cancelTransfer">
      <div class="ds-modal-panel max-w-md p-5">
        <h3 class="ds-modal-title mb-3">
          Transferir ULD
        </h3>
        <div class="text-[14px] text-slate-950 mb-3">
          ULD <strong>{{ pendingTransfer.uldNumber }}</strong>
          <span v-if="fromFlightLabel" class="mx-1">→ {{ fromFlightLabel }}</span>
          <span class="mx-1">→</span>
          <strong>{{ toFlightLabel }}</strong>
        </div>
        <label class="ds-label">
          Motivo de la transferencia *
        </label>
        <textarea v-model="transferReason" rows="3" placeholder="Ej: Rebalanceo de carga, cambio de ruta..."
          class="ds-input resize-none mb-4"></textarea>
        <div class="flex justify-end gap-2">
          <button @click="cancelTransfer"
            class="ds-btn-secondary">
            Cancelar
          </button>
          <button @click="confirmTransfer" :disabled="!transferReason.trim()"
            class="ds-btn-primary">
            Confirmar Transferencia
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LocaleDatePicker from '../components/LocaleDatePicker.vue'
import { useUldsStore } from '../stores/ulds'
import { useAppStore } from '../stores/app'
import api from '../api/client'
import { uldsApi } from '../api/ulds'
import * as XLSX from 'xlsx'
import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'
import { useIcons } from '../composables/useIcons'
import { useConfirm } from '../composables/useConfirm'

const icons = useIcons()
const uldsStore = useUldsStore()
const appStore = useAppStore()
const route = useRoute()
const toast = useToastStore()
const { confirm } = useConfirm()
const { t, te, locale } = useI18n()

function lpStatus(status) {
  const key = `loadPlanning.status.${status}`
  return te(key) ? t(key) : status
}

function airlineCodeById(airlineId) {
  const a = appStore.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

const selectedFlightId = computed({
  get: () => uldsStore.selectedFlightId,
  set: (val) => uldsStore.selectFlight(val)
})

const fileInput = ref(null)
const selectedDate = ref('')
const allUlds = ref([])
const loadPlan = ref(null)
const draggedUldId = ref(null)
const dragOver = ref(false)
const dragOverFloating = ref(false)

// Transfer state (with reason)
const pendingTransfer = ref(null) // { uldId, fromFlightId, toFlightId, uldNumber }
const transferReason = ref('')

const fromFlightLabel = computed(() => {
  if (!pendingTransfer.value?.fromFlightId) return null
  const f = uldsStore.flights.find(x => x.id === pendingTransfer.value.fromFlightId)
  return f ? `${airlineCodeById(f.airlineId)}-${f.flightNumber}` : null
})
const toFlightLabel = computed(() => {
  if (!pendingTransfer.value?.toFlightId) return ''
  const f = uldsStore.flights.find(x => x.id === pendingTransfer.value.toFlightId)
  return f ? `${airlineCodeById(f.airlineId)}-${f.flightNumber}` : '(sin vuelo)'
})

// Track previous flight per ULD for reverse drag (uldId → fromFlightId)
const reassignmentHistory = ref({})

// Undo state
const undoAction = ref(null)
const showUndoToast = ref(false)
let undoTimeout = null

// Pending flight picker for ULDs without reassignment history
const pendingFlightPick = ref(null) // { uldId, uldNumber }
const flightPickValue = ref('')
const showFlightPicker = ref(false)

const flightDatabase = computed(() => {
  if (!selectedDate.value) return uldsStore.flights
  return uldsStore.flights.filter(f => {
    if (!f.flightDate) return true
    const fd = new Date(f.flightDate).toISOString().split('T')[0]
    return fd === selectedDate.value
  })
})

const ASSIGNABLE_STATUSES = new Set(['SCHEDULED', 'BOARDING', 'DELAYED'])
const intlLoc = computed(() => (locale.value || 'es').startsWith('en') ? 'en-US' : 'es-DO')

function fmtFlightDate(d) {
  if (!d) return ''
  const [y, m, day] = String(d).slice(0, 10).split('-')
  if (!y || !m || !day) return String(d)
  return new Date(y, m - 1, day).toLocaleDateString(intlLoc.value, { day: '2-digit', month: 'short' })
}

function flightOptionLabel(f) {
  const base = `${airlineCodeById(f.airlineId)}-${f.flightNumber}`
  return fmtFlightDate(f.flightDate) ? `${fmtFlightDate(f.flightDate)} · ${base}` : base
}

// Vuelos asignables: semana actual + próxima, incluye hoy, excluye ya despachados.
// Garantiza al menos 10 próximos vuelos, sin exceder 15.
const assignableFlights = computed(() => {
  const today = new Date().toISOString().split('T')[0]
  const dow = new Date().getDay()
  const daysToThisSunday = dow === 0 ? 0 : 7 - dow
  const endNextWeek = new Date()
  endNextWeek.setDate(endNextWeek.getDate() + daysToThisSunday + 7)
  const endNextWeekStr = endNextWeek.toISOString().split('T')[0]

  const byDate = (a, b) => a.flightDate.localeCompare(b.flightDate) || a.flightNumber.localeCompare(b.flightNumber)

  const eligible = uldsStore.flights.filter(f =>
    f.flightDate && ASSIGNABLE_STATUSES.has(f.status) && f.flightDate >= today
  )
  const inWindow = eligible
    .filter(f => f.flightDate <= endNextWeekStr)
    .sort(byDate)

  if (inWindow.length >= 10) return inWindow.slice(0, 15)
  const extended = [...eligible].sort(byDate).slice(0, 15)
  return extended.length >= 10 ? extended : extended
})

function flightOptionsFor(currentFlightId) {
  const opts = [...assignableFlights.value]
  if (currentFlightId && !opts.some(f => f.id === currentFlightId)) {
    const cur = uldsStore.flights.find(f => f.id === currentFlightId)
    if (cur) opts.unshift(cur)
  }
  return opts
}

const activeFlightMeta = computed(() => uldsStore.selectedFlight || {})

const activeAirlineLabel = computed(() => {
  const f = activeFlightMeta.value
  if (!f?.id) return '-'
  const a = appStore.airlines.find(x => x.id === f.airlineId)
  const code = a?.code || 'AIR'
  return `${code} ${f.flightNumber || ''}`.trim()
})

const aircraftType = computed(() => {
  const reg = activeFlightMeta.value.aircraftReg || ''
  const upper = reg.toUpperCase()
  if (upper.includes('B767') || upper.includes('767')) return 'B767'
  if (upper.includes('B757') || upper.includes('757')) return 'B757'
  if (upper.includes('A300') || upper.includes('A30')) return 'A300'
  if (upper.includes('B747') || upper.includes('747')) return 'B747'
  if (upper.includes('B777') || upper.includes('777')) return 'B777'
  return reg || '—'
})

const bellyPositions = computed(() => {
  const ac = aircraftType.value
  if (ac === 'B757') return ['31', '34']
  if (ac === 'B767') return ['A', 'B']
  if (ac === 'A300' || ac === 'A310' || ac === 'A330' || ac === 'B777') return ['A', 'B']
  if (ac === 'B747') return ['31', '32', '33', '34']
  if (ac === 'MD11') return ['31', '34']
  return []
})

function isBellyPosition(pos) {
  if (!pos) return false
  const upper = pos.trim().toUpperCase()
  if (upper === 'LOOSE' || upper === 'BULK') return true
  return bellyPositions.value.map(p => p.toUpperCase()).includes(upper)
}

function posCellClass(pos) {
  if (!pos) return ''
  return isBellyPosition(pos)
    ? 'pos-belly'
    : 'pos-regular'
}

const positionSummary = computed(() => {
  const posMap = {}
  for (const uld of activeManifest.value) {
    const pos = (uld.pos || '').trim().toUpperCase() || '—'
    if (!posMap[pos]) posMap[pos] = { pos, count: 0, pcs: 0 }
    posMap[pos].count++
    posMap[pos].pcs += uld.items.reduce((s, i) => s + (i.pcs || 0), 0)
  }
  const bellySet = new Set(bellyPositions.value.map(p => p.toUpperCase()))
  return Object.values(posMap).map(p => ({
    ...p,
    isBelly: bellySet.has(p.pos)
  })).sort((a, b) => {
    const aIsBelly = a.isBelly ? 1 : 0
    const bIsBelly = b.isBelly ? 1 : 0
    return bIsBelly - aIsBelly
  })
})

const activeManifest = computed(() => {
  const liveMap = {}
  for (const uld of uldsStore.activeUlds || []) liveMap[uld.id] = uld
  const allUldsMap = {}
  for (const uld of allUlds.value || []) allUldsMap[uld.id] = uld
  const planIds = loadPlan.value?.ulds ? new Set(loadPlan.value.ulds.map(u => u.id)) : new Set()
  const merged = []
  for (const uld of uldsStore.activeUlds || []) merged.push(uld)
  for (const uld of loadPlan.value?.ulds || []) {
    if (!planIds.has(uld.id) || liveMap[uld.id]) continue
    // Skip plan ULDs that exist in allUlds but are NOT assigned to this flight (stale cache or unassigned)
    if (allUldsMap[uld.id] && !liveMap[uld.id]) continue
    merged.push(uld)
  }
  return merged.map(uld => ({
    uldId: uld.id,
    flightId: uld.flightId || selectedFlightId.value || '',
    uld: uld.uldNumber || uld.id || 'SIN-ULD',
    pos: uld.position || null,
    config: uld.uldType || '-',
    sello: uld.sealNumber || '',
    weight: uld.grossWeightLbs || uld.grossWeight || 0,
    tara: uld.tareLbs || uld.tareWeight || 0,
    status: uld.status || 'IN_RAMP',
    items: (uld.awbs || uld.mawbs || uld.items || []).map(m => ({
      mawb: m.mawbLabel || m.awbNumber || m.id || '',
      pcs: m.pieces || 0,
      volumePct: m.piecesPct || m.percentage || null,
      description: m.description || m.commodityType || 'DRY CARGO',
      destino: m.destination || '-'
    }))
  }))
})

const floatingUlds = computed(() => {
  // Usar una copia estática para evitar problemas de reactivity timing
  const currentActiveManifest = activeManifest.value
  const currentAllUlds = [...allUlds.value]
  const manifestIds = new Set(currentActiveManifest.map(u => u.uldId))
  return currentAllUlds.filter(u => !u.flightId && u.status !== 'OPEN' && !manifestIds.has(u.id))
})

function isLiveUld(uldId) {
  return allUlds.value.some(u => u.id === uldId)
}

const calculatedTotals = computed(() => {
  let uldsCount = activeManifest.value.length
  let pcs = 0, gross = 0, tare = 0, effectiveGross = 0

  activeManifest.value.forEach(uld => {
    const w = uld.weight || 0
    const t = uld.tara || 0
    const n = w - t
    gross += w
    tare += t
    if (isBellyPosition(uld.pos)) {
      effectiveGross += n
    } else {
      effectiveGross += w
    }
    uld.items.forEach(item => { pcs += item.pcs || 0 })
  })

  const payloadKg = activeFlightMeta.value.maxPayloadKg || 0
  const payloadLbs = payloadKg * 2.20462
  const availableLbs = payloadLbs - effectiveGross

  return {
    uldsCount,
    pcs,
    gross,
    tare,
    effectiveGross,
    payloadKg,
    payloadLbs: Math.round(payloadLbs),
    availableLbs: Math.round(availableLbs),
    payloadPct: uldsCount ? Math.round(((gross - tare) / Math.max(gross, 1)) * 100) : 0
  }
})

function getRowBgStyle(status) {
  switch (status) {
    case 'LEFT_BEHIND': return 'bg-slate-50'
    case 'INCOMPLETE': return 'bg-slate-100'
    case 'IN_RAMP': return 'bg-white'
    case 'COMPLETED':
    default: return 'bg-white'
  }
}

function getCardBorderStyle(status) {
  switch (status) {
    case 'LEFT_BEHIND': return 'border-slate-300'
    case 'BUILT': return 'border-slate-400'
    case 'SEALED': return 'border-slate-500'
    case 'LOADED': return 'border-slate-950/30'
    case 'OPEN':
    default: return 'border-slate-400'
  }
}

function getStatusDotColor(status) {
  switch (status) {
    case 'LEFT_BEHIND': return 'bg-slate-400'
    case 'BUILT': return 'bg-slate-500'
    case 'SEALED': return 'bg-slate-600'
    case 'LOADED': return 'bg-slate-950'
    case 'OPEN':
    default: return 'bg-slate-300'
  }
}

onMounted(async () => {
  if (!appStore.airlines.length) await appStore.loadAirlines()
  await uldsStore.loadFlights()
  if (uldsStore.flights.length) {
    const flightIdFromQuery = route.query.flightId
    if (flightIdFromQuery && uldsStore.flights.find(f => f.id === flightIdFromQuery)) {
      selectedDate.value = ''
      selectedFlightId.value = flightIdFromQuery
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value)
      ])
    } else {
      const today = new Date().toISOString().split('T')[0]
      const matching = uldsStore.flights.filter(f => {
        if (!f.flightDate) return true
        return new Date(f.flightDate).toISOString().split('T')[0] === today
      })
      if (matching.length) {
        selectedDate.value = today
        selectedFlightId.value = matching[0].id
      } else {
        const sorted = [...uldsStore.flights].sort((a, b) => {
          if (!a.flightDate) return 1
          if (!b.flightDate) return -1
          return new Date(b.flightDate) - new Date(a.flightDate)
        })
        if (sorted.length) {
          selectedDate.value = sorted[0].flightDate ? new Date(sorted[0].flightDate).toISOString().split('T')[0] : ''
          selectedFlightId.value = sorted[0].id
        }
      }
      if (selectedFlightId.value) {
        await Promise.all([
          uldsStore.loadUldsForFlight(selectedFlightId.value),
          fetchLoadPlan(selectedFlightId.value)
        ])
      }
    }
  }
  await loadAllUlds()
})

onUnmounted(() => {
  document.removeEventListener('pointermove', onTableUldPointerMove)
  document.removeEventListener('pointerup', onTableUldPointerUp)
})

async function loadAllUlds() {
  try {
    const airlineId = appStore.selectedFlight?.airlineId
    const res = await api.get('/ulds', airlineId ? { params: { airlineId } } : undefined)
    allUlds.value = res.data
  } catch (e) {
    toast.error(extractError(e))
    console.error('Error loading all ULDs:', e)
  }
}

async function fetchLoadPlan(flightId) {
  if (!flightId) { loadPlan.value = null; return }
  try {
    const res = await api.get(`/load-planning/flight/${flightId}`)
    loadPlan.value = res.data
  } catch (e) {
    loadPlan.value = null
    if (e.response?.status === 404) {
      // Aún no hay plan de carga para este vuelo — estado normal, no es un error.
      return
    }
    toast.error(extractError(e))
    console.error('Error fetching load plan:', e)
  }
}

async function onDateChange() {
  if (flightDatabase.value.length) {
    selectedFlightId.value = flightDatabase.value[0].id
    await Promise.all([
      uldsStore.loadUldsForFlight(selectedFlightId.value),
      fetchLoadPlan(selectedFlightId.value)
    ])
  }
}

function syncFlightMetadata() {
  if (selectedFlightId.value) {
    uldsStore.loadUldsForFlight(selectedFlightId.value)
    fetchLoadPlan(selectedFlightId.value)
  }
}

async function reassignFlight(uldGroup, silent = false) {
  // flightId === null es válido: desasigna el ULD y lo regresa a la franja flotante
  if (!uldGroup.uldId) return
  const fromFlightId = allUlds.value.find(u => u.id === uldGroup.uldId)?.flightId || null
  const uldNumber = allUlds.value.find(u => u.id === uldGroup.uldId)?.uldNumber || uldGroup.uldId
  try {
    await api.patch(`/ulds/${uldGroup.uldId}/flight`, { flightId: uldGroup.flightId })
    // Always store the origin flight in history for reverse drag
    if (fromFlightId) {
      reassignmentHistory.value[uldGroup.uldId] = fromFlightId
    }
    if (!silent) {
      if (undoTimeout) clearTimeout(undoTimeout)
      undoAction.value = { uldId: uldGroup.uldId, fromFlightId, toFlightId: uldGroup.flightId, uldNumber }
      showUndoToast.value = true
      undoTimeout = setTimeout(() => { showUndoToast.value = false; undoAction.value = null }, 6000)
    }
    if (selectedFlightId.value) {
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value),
        loadAllUlds()
      ])
    }
  } catch (e) {
    toast.error('Error reasignando vuelo: ' + (e.response?.data?.error || e.response?.data?.message || e.message))
  }
}

async function updatePosition(uldId, newPos) {
  if (!uldId) return
  const trimmed = (newPos || '').trim()
  try {
    await uldsApi.patch(uldId, { position: trimmed })
    if (selectedFlightId.value) {
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value)
      ])
    }
  } catch (e) {
    toast.error('Error actualizando posición: ' + (e.response?.data?.message || e.message))
  }
}

function onTransferRequest(uldId, uldNumber, fromFlightId, toFlightId) {
  if (!toFlightId || toFlightId === fromFlightId) return
  if (!isLiveUld(uldId)) {
    toast.warning('Este ULD solo existe en el plan de carga y no se puede transferir desde aquí. Use la vista de ULDs para reasignarlo.')
    return
  }
  pendingTransfer.value = { uldId, fromFlightId, toFlightId, uldNumber }
  transferReason.value = ''
}

function cancelTransfer() {
  pendingTransfer.value = null
  transferReason.value = ''
}

async function confirmTransfer() {
  if (!pendingTransfer.value || !transferReason.value.trim()) return
  const { uldId, toFlightId } = pendingTransfer.value
  const reason = transferReason.value.trim()
  pendingTransfer.value = null
  transferReason.value = ''
  try {
    await api.post(`/ulds/${uldId}/transfer`, {
      destinationFlightId: toFlightId,
      reason
    })
    if (selectedFlightId.value) {
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value)
      ])
    }
    loadAllUlds() // non-blocking refresh
  } catch (e) {
    toast.error('Error transfiriendo ULD: ' + (e.response?.data?.error || e.response?.data?.message || e.message))
  }
}

async function dispatchFlight() {
  if (!selectedFlightId.value) return
  const ulds = uldsStore.activeUlds || []
  const pendingAwbs = new Set()
  for (const u of ulds) {
    for (const m of (u.awbs || [])) {
      const awbNum = m.mawbLabel || ''
      if (!awbNum) continue
      const hasReceipt = appStore.receipts.some(r => {
        const mawb = r.mawb || {}
        return mawb.awbNumber === awbNum
      })
      if (!hasReceipt) pendingAwbs.add(awbNum)
    }
  }
  if (pendingAwbs.size > 0) {
    toast.warning(`No se puede despachar: ${pendingAwbs.size} MAWB(s) no tienen recibo de bodega:\n${[...pendingAwbs].join('\n')}\n\nComplete los recibos pendientes antes de despachar.`)
    return
  }
  if (!(await confirm({ message: '¿Despachar el vuelo? Se marcarán todos los ULDs como LOADED y no se podrá modificar.' }))) return
  try {
    await api.post(`/load-planning/flight/${selectedFlightId.value}/close`)
    toast.success('Vuelo despachado correctamente.')
    await Promise.all([
      uldsStore.loadUldsForFlight(selectedFlightId.value),
      fetchLoadPlan(selectedFlightId.value),
      uldsStore.loadFlights()
    ])
  } catch (e) {
    toast.error('Error despachando vuelo: ' + (e.response?.data?.error || e.response?.data?.message || e.message))
  }
}

async function undoLastReassign() {
  if (!undoAction.value) return
  const action = undoAction.value
  undoAction.value = null
  showUndoToast.value = false
  if (undoTimeout) clearTimeout(undoTimeout)
  if (action.fromFlightId) {
    await reassignFlight({ uldId: action.uldId, flightId: action.fromFlightId }, true)
  }
}

function cancelFlightPick() { pendingFlightPick.value = null; showFlightPicker.value = false }

async function detachToFloating() {
  if (!pendingFlightPick.value) return
  const { uldId } = pendingFlightPick.value
  pendingFlightPick.value = null
  showFlightPicker.value = false
  try {
    await api.patch(`/ulds/${uldId}/flight`, { flightId: null })
    if (selectedFlightId.value) {
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value)
      ])
    }
    loadAllUlds() // non-blocking refresh
  } catch (e) {
    toast.error('Error desasignando vuelo: ' + (e.response?.data?.error || e.response?.data?.message || e.message))
  }
}

async function confirmFlightPick(flightId) {
  if (!pendingFlightPick.value || !flightId) return
  const { uldId } = pendingFlightPick.value
  pendingFlightPick.value = null
  showFlightPicker.value = false
  await reassignFlight({ uldId, flightId }, false)
}

async function deleteUld(uld) {
  if (!(await confirm({ message: `¿Eliminar ULD ${uld.uldNumber || ''} definitivamente? Esta acción no se puede deshacer.`, danger: true }))) return
  try {
    await api.delete(`/ulds/${uld.id}`)
    if (selectedFlightId.value) {
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value)
      ])
    }
    loadAllUlds() // non-blocking refresh
  } catch (e) {
    toast.error('Error eliminando ULD: ' + (e.response?.data?.error || e.response?.data?.message || e.message))
  }
}

function onDragStart(uldId, e) {
  draggedUldId.value = uldId
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', uldId)
  }
}

function onDragLeave(e) {
  const section = e.currentTarget
  if (!section.contains(e.relatedTarget)) {
    dragOver.value = false
  }
}

async function onDrop(e) {
  dragOver.value = false
  const uldId = draggedUldId.value || (e.dataTransfer?.getData('text/plain'))
  draggedUldId.value = null
  if (!uldId || !selectedFlightId.value) return
  await reassignFlight({ uldId, flightId: selectedFlightId.value })
}

function onDragLeaveFloating(e) {
  const section = e.currentTarget
  if (!section.contains(e.relatedTarget)) dragOverFloating.value = false
}

/** Soltar un ULD asignado sobre la franja flotante = desasignarlo del vuelo */
async function onDropFloating(e) {
  dragOverFloating.value = false
  const uldId = draggedUldId.value || rowDragging.value || (e.dataTransfer?.getData('text/plain'))
  draggedUldId.value = null
  rowDragging.value = null
  rowDropIndex.value = -1
  if (!uldId) return
  const uld = allUlds.value.find(u => u.id === uldId)
  if (!uld?.flightId) return  // ya está flotando
  await reassignFlight({ uldId, flightId: null })
  // Un ULD en estado OPEN sin vuelo no aparece en la franja flotante
  // (filtro existente): devolverlo a IN_RAMP para que siga visible.
  if ((uld.status || '').toUpperCase() === 'OPEN') {
    try { await uldsApi.patch(uldId, { status: 'IN_RAMP' }) } catch {}
    if (selectedFlightId.value) {
      await uldsStore.loadUldsForFlight(selectedFlightId.value)
    }
    loadAllUlds()
  }
}

// Row drag-and-drop reorder
const rowDragging = ref(null)
const rowDropIndex = ref(-1)

function onRowDragStart(uldId, e) {
  rowDragging.value = uldId
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', uldId)
  }
}

function onRowDragOver() { /* preventDefault handled by @dragover.prevent */ }

function onRowDragEnter(uIdx) {
  if (rowDragging.value) rowDropIndex.value = uIdx
}

async function onRowDrop() {
  const draggedId = rowDragging.value
  const targetIdx = rowDropIndex.value
  rowDragging.value = null
  rowDropIndex.value = -1
  if (!draggedId || targetIdx < 0) return
  const currentIdx = activeManifest.value.findIndex(g => g.uldId === draggedId)
  if (currentIdx === targetIdx) return
  // Reorder positions based on new index
  const sorted = [...activeManifest.value]
  const [moved] = sorted.splice(currentIdx, 1)
  sorted.splice(targetIdx, 0, moved)
  // Assign sequential positions
  const positions = ['1L', '2L', '3L', '4L', '5L', '6L', '7L', '8L', '9L', '10L',
    '1R', '2R', '3R', '4R', '5R', '6R', '7R', '8R', '9R', '10R',
    '11L', '12L', '13L', '14L', '15L', '16L', '17L', '18L',
    '11R', '12R', '13R', '14R', '15R', '16R', '17R', '18R',
    '31', '32', '33', '34', 'A', 'B']
  toast.info('Reordenando posiciones...')
  // Update positions sequentially
  for (let i = 0; i < sorted.length; i++) {
    const uld = sorted[i]
    const newPos = positions[i] || (i + 1).toString()
    if (uld.pos !== newPos) {
      try {
        await api.patch(`/ulds/${uld.uldId}`, { position: newPos })
      } catch { /* continue reorder */ }
    }
  }
  // Reload
  await loadAllUlds()
  if (selectedFlightId.value) {
    await Promise.all([
      uldsStore.loadUldsForFlight(selectedFlightId.value),
      fetchLoadPlan(selectedFlightId.value)
    ])
  }
  toast.success('Posiciones reordenadas')
}

// Pointer-based drag from table rows to floating cards section
let pointerDragData = null
const RING_CLASSES = ['ring-4', 'ring-slate-400', 'ring-offset-2', 'rounded-lg']

function onTableUldPointerDown(uldId, e) {
  pointerDragData = { uldId, startX: e.clientX, startY: e.clientY, moved: false }
  document.addEventListener('pointermove', onTableUldPointerMove)
  document.addEventListener('pointerup', onTableUldPointerUp)
}

function onTableUldPointerMove(e) {
  if (!pointerDragData) return
  const dx = e.clientX - pointerDragData.startX
  const dy = e.clientY - pointerDragData.startY
  if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
    pointerDragData.moved = true
  }
  if (!pointerDragData.moved) return
  // Check if cursor is over the floating cards section
  const floatingSection = document.querySelector('.floating-drop-zone')
  if (floatingSection) {
    const rect = floatingSection.getBoundingClientRect()
    const over = (
      e.clientX >= rect.left && e.clientX <= rect.right &&
      e.clientY >= rect.top && e.clientY <= rect.bottom
    )
    dragOverFloating.value = over
    // Direct DOM classList for immediate visual feedback (Vue async batching may skip intermediate states)
    if (over) {
      floatingSection.classList.add(...RING_CLASSES)
    } else {
      floatingSection.classList.remove(...RING_CLASSES)
    }
  }
}

async function onTableUldPointerUp() {
  document.removeEventListener('pointermove', onTableUldPointerMove)
  document.removeEventListener('pointerup', onTableUldPointerUp)
  const floatingSection = document.querySelector('.floating-drop-zone')
  if (floatingSection) {
    requestAnimationFrame(() => floatingSection.classList.remove(...RING_CLASSES))
  }
  const data = pointerDragData
  pointerDragData = null
  if (!data || !data.moved) {
    dragOverFloating.value = false
    return
  }
  const wasOverFloating = dragOverFloating.value
  dragOverFloating.value = false
  if (wasOverFloating && data.uldId) {
    const uld = allUlds.value.find(u => u.id === data.uldId)
    if (!uld?.flightId) return
    await reassignFlight({ uldId: data.uldId, flightId: null })
  }
}

function triggerImport() { fileInput.value?.click() }
async function handleFileImport(e) {
  const file = e.target.files?.[0]
  if (!file) return
  toast.info('Procesando ' + file.name + '...')
  try {
    const data = await file.arrayBuffer()
    const wb = XLSX.read(data, { type: 'array' })
    const ws = wb.Sheets[wb.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json(ws, { header: 1 })
    // Find header row and data
    let headerIdx = -1
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i].map(c => String(c || '').toUpperCase().trim())
      if (row.some(c => c === 'ULD')) { headerIdx = i; break }
    }
    if (headerIdx < 0) { toast.error('No se encontró fila de encabezados (ULD)'); return }
    const headers = rows[headerIdx].map(c => String(c || '').trim().toUpperCase())
    const uldCol = headers.indexOf('ULD')
    const posCol = headers.indexOf('POS')
    const pcsCol = headers.indexOf('PCS')
    const sealCol = headers.indexOf('SEAL')
    const mawbCol = headers.indexOf('MAWB')
    if (uldCol < 0) { toast.error('No se encontró columna ULD'); return }
    const importData = []
    for (let i = headerIdx + 1; i < rows.length; i++) {
      const row = rows[i]
      if (!row || !row[uldCol] || String(row[uldCol]).trim() === '') continue
      importData.push({
        uldNumber: String(row[uldCol]).trim().toUpperCase(),
        position: posCol >= 0 ? String(row[posCol] || '').trim() : '',
        sealNumber: sealCol >= 0 ? String(row[sealCol] || '').trim() : '',
        pieces: pcsCol >= 0 ? parseInt(row[pcsCol] || 0, 10) : 0,
        mawb: mawbCol >= 0 ? String(row[mawbCol] || '').trim() : '',
      })
    }
    if (importData.length === 0) { toast.error('No se encontraron datos de ULD'); return }
    // Match ULDs by number and update
    let updated = 0
    for (const item of importData) {
      // Find ULD by number in this flight
      const match = allUlds.value.find(u =>
        (u.uldNumber || '').toUpperCase() === item.uldNumber
      )
      if (match) {
        const updatePayload = {}
        if (item.position) updatePayload.position = item.position
        if (item.sealNumber) updatePayload.sealNumber = item.sealNumber
        if (Object.keys(updatePayload).length > 0) {
          try {
            await uldsApi.patch(match.id, updatePayload)
            updated++
          } catch { /* skip individual failure */ }
        }
      } else {
        toast.warning('ULD ' + item.uldNumber + ' no encontrado en el sistema')
      }
    }
    await loadAllUlds()
    if (selectedFlightId.value) {
      await Promise.all([
        uldsStore.loadUldsForFlight(selectedFlightId.value),
        fetchLoadPlan(selectedFlightId.value)
      ])
    }
    toast.success('Importación completa: ' + updated + ' ULDs actualizados')
  } catch (err) {
    toast.error('Error procesando archivo: ' + extractError(err))
  }
  e.target.value = ''
}
async function exportPalletSheets() {
  const flightId = selectedFlightId.value
  if (!flightId) {
    toast.warning('Seleccione un vuelo primero')
    return
  }
  if (!activeManifest.value.length) {
    toast.warning('No hay ULDs asignados a este vuelo. Primero debe importar un manifiesto (XLSX) o asignar ULDs al vuelo.')
    return
  }
  try {
    const res = await api.get(`/load-planning/flight/${flightId}/pallet-sheets`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
    const a = document.createElement('a')
    a.href = url
    const cd = res.headers['content-disposition']
    const match = cd && cd.match(/filename=(.+)/)
    a.download = match ? match[1] : 'PALLET_SHEETS.pdf'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (err) {
    if (err.response?.status === 404) {
      toast.warning('No se encontró el plan de carga para este vuelo. Verifique que el vuelo exista y tenga ULDs asignados.')
    } else {
      toast.error('Error al generar PDF. Verifique la consola para más detalles.')
      console.error('Error generando pallet sheets:', err)
    }
  }
}
function exportToXLSX() {
  const flight = activeFlightMeta.value
  if (!flight || !activeManifest.value.length) {
    toast.warning('No hay datos para exportar')
    return
  }
  const rows = [
    ['LOAD PLAN — ' + (flight.flightNumber || '')],
    ['Vuelo: ' + airlineCodeById(flight.airlineId) + '-' + (flight.flightNumber || ''), 'Fecha: ' + (flight.flightDate || ''), 'Ruta: ' + (flight.origin || '') + '→' + (flight.destination || ''), 'Aeronave: ' + (flight.aircraftReg || '')],
    [],
    ['ULD', 'PCS', '%', 'GROSS', 'TARE', 'TYPE', 'SEAL', 'POS', 'DESC', 'MAWB', 'DEST']
  ]
  for (const uldGroup of activeManifest.value) {
    if (uldGroup.items.length === 0) {
      rows.push([
        uldGroup.uld, 0, '-',
        uldGroup.weight || 0, uldGroup.tara || 0,
        uldGroup.config, uldGroup.sello || '-', uldGroup.pos || '-',
        '—', 'empty', '-'
      ])
    } else {
      for (let i = 0; i < uldGroup.items.length; i++) {
        const item = uldGroup.items[i]
        rows.push([
          i === 0 ? uldGroup.uld : '',
          item.pcs || 0,
          item.volumePct ? item.volumePct + '%' : '-',
          i === 0 ? (uldGroup.weight || 0) : '',
          i === 0 ? (uldGroup.tara || 0) : '',
          i === 0 ? uldGroup.config : '',
          i === 0 ? (uldGroup.sello || '-') : '',
          i === 0 ? (uldGroup.pos || '-') : '',
          item.description || '',
          item.mawb || '',
          item.destino || '-'
        ])
      }
    }
  }
  rows.push([])
  const totals = calculatedTotals.value
  rows.push(['Totales', '', '', '', '', '', '', '', '', '', ''])
  rows.push(['ULDs: ' + totals.uldsCount, 'PCS: ' + totals.pcs.toLocaleString(), 'Gross: ' + totals.gross.toLocaleString() + ' lb', 'Tare: ' + totals.tare.toLocaleString() + ' lb', '', '', '', '', '', '', ''])
  const ws = XLSX.utils.aoa_to_sheet(rows)
  ws['!cols'] = [
    { wch: 14 }, { wch: 8 }, { wch: 6 }, { wch: 10 }, { wch: 10 },
    { wch: 8 }, { wch: 12 }, { wch: 6 }, { wch: 20 }, { wch: 18 }, { wch: 6 }
  ]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Load Plan')
  const flightNum = flight.flightNumber || selectedFlightId.value
  XLSX.writeFile(wb, `LOAD_PLAN_${airlineCodeById(flight.airlineId)}-${flightNum}.xlsx`)
}

watch(() => uldsStore.activeUlds, (newUlds) => {
  console.warn('LoadPlanning actualizado con', newUlds?.length || 0, 'ULDs')
}, { deep: true })

watch(calculatedTotals, (t) => {
  console.warn('[WEIGHT] Gross:', t.gross, 'lbs | Tare:', t.tare, 'lbs | Effective Gross:', t.effectiveGross, 'lbs | Payload:', t.payloadLbs, 'lbs (' + t.payloadKg + ' kg) | Disponible:', t.availableLbs, 'lbs')
}, { deep: true })
</script>

<style scoped>
.shadow-pencil-marine { box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
.lp-grid { display: grid; grid-template-columns: 10fr 5fr 4fr 6fr 4fr 4fr 12fr 4fr 14fr 14fr 4fr; gap: 4px; min-width: 960px; }
.lp-grid > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lp-container-block:hover { background-color: #f8fafc !important; }
.lp-scroll-x::-webkit-scrollbar { height: 8px; display: block; }
.lp-scroll-x::-webkit-scrollbar-track { background: #e2e8f0; border-radius: 4px; }
.lp-scroll-x::-webkit-scrollbar-thumb { background: #94a3b8; border-radius: 4px; }
.lp-scroll-x::-webkit-scrollbar-thumb:hover { background: #64748b; }
.lp-scroll-x { scrollbar-width: auto; scrollbar-color: #94a3b8 #e2e8f0; }
.pos-regular {
  background: #dbeafe;
  border-radius: 4px;
  padding: 2px 4px;
}
.pos-regular input {
  color: #ea580c;
  font-weight: 700;
}
.pos-belly {
  background: #fef3c7;
  border-radius: 4px;
  padding: 2px 4px;
  border: 1px solid #f59e0b;
}
.pos-belly input {
  color: #b45309;
  font-weight: 700;
}
</style>
