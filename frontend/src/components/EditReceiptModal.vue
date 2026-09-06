<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" @click.self="close">
      <div class="bg-white rounded-lg shadow-2xl overflow-hidden mx-4 flex flex-col" style="max-width: 960px; width: 100%; max-height: 90vh;">
        <div class="flex items-center justify-between px-4 py-2.5 border-b border-slate-300 bg-slate-50 shrink-0">
          <div class="flex items-center gap-2">
            <span class="text-[14px] font-mono font-bold uppercase tracking-widest text-slate-950">
              Editar Recibo
            </span>
            <span v-if="loading" class="text-[12px] font-mono text-slate-400 uppercase">Cargando...</span>
          </div>
          <button @click="close" class="text-slate-400 hover:text-slate-700 transition text-sm">✕</button>
        </div>

        <div v-if="loading" class="flex items-center justify-center py-20 text-[13px] font-mono text-slate-400 uppercase tracking-widest">
          Cargando datos del recibo...
        </div>

        <div v-else class="flex-1 overflow-y-auto p-4 space-y-4">
          <!-- HEADER SECTION -->
          <div class="grid grid-cols-4 gap-3">
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Shipper</label>
              <input v-model="form.shipperName" type="text"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition" />
            </div>
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Consignee</label>
              <input v-model="form.consigneeName" type="text"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition" />
            </div>
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Origin</label>
              <input v-model="form.origin" type="text" maxlength="3"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition uppercase" />
            </div>
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Destination</label>
              <input v-model="form.destination" type="text" maxlength="3"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition uppercase" />
            </div>
          </div>

          <div class="grid grid-cols-4 gap-3">
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Pzas AWB</label>
              <input v-model.number="form.awbReportedPieces" type="number" min="0"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition" />
            </div>
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">DIM Factor KG</label>
              <input v-model.number="form.dimFactorKg" type="number" min="0"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition" />
            </div>
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">DIM Factor LBS</label>
              <input v-model.number="form.dimFactorLbs" type="number" min="0"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition" />
            </div>
            <div>
              <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Gateway CFS</label>
              <input v-model="form.gatewayCfs" type="text" maxlength="10"
                class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition uppercase" />
            </div>
          </div>

          <!-- CHECKBOXES -->
          <div class="flex flex-wrap gap-3 px-1">
            <label v-for="cb in checkboxes" :key="cb.key" class="flex items-center gap-1.5 cursor-pointer">
              <input v-model="form[cb.key]" type="checkbox" class="accent-slate-700 w-3 h-3" />
              <span class="text-[12px] font-mono font-bold text-slate-600 uppercase">{{ cb.label }}</span>
            </label>
          </div>

          <!-- REMARKS -->
          <div>
            <label class="block text-[12px] font-mono font-bold text-slate-500 uppercase mb-0.5">Remarks</label>
            <textarea v-model="form.remarks" rows="2"
              class="w-full text-[13px] font-mono px-2 py-1 rounded border border-slate-300 outline-none focus:border-slate-500 transition resize-none"></textarea>
          </div>

          <!-- PIECES TABLE -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <span class="text-[13px] font-mono font-bold uppercase tracking-wider text-slate-950">
                Piezas ({{ pieces.length }})
              </span>
              <div class="flex items-center gap-2 text-[12px] font-mono text-slate-500">
                <span>Totales:</span>
                <span class="font-bold text-slate-700">{{ totalScaleLbs.toFixed(1) }} LBS</span>
                <span class="text-slate-400">|</span>
                <span class="font-bold text-slate-700">{{ totalScaleKg.toFixed(1) }} KG</span>
                <span class="text-slate-400">|</span>
                <span class="font-bold text-emerald-700">Charge: {{ totalChargeKg.toFixed(1) }} KG / {{ totalChargeLbs.toFixed(1) }} LBS</span>
              </div>
            </div>
            <div class="overflow-x-auto border border-slate-300 rounded">
              <table class="w-full text-[12px] font-mono">
                <thead>
                  <tr class="bg-slate-100 border-b border-slate-300">
                    <th class="px-2 py-1 text-left font-bold text-slate-600 uppercase">#</th>
                    <th class="px-2 py-1 text-left font-bold text-slate-600 uppercase">Pzas</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">L (in)</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">W (in)</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">H (in)</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">Scale LBS</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">Dim LBS</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">Charge LBS</th>
                    <th class="px-2 py-1 text-right font-bold text-slate-600 uppercase">Charge KG</th>
                    <th class="px-1 py-1"></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(p, i) in pieces" :key="i" class="border-b border-slate-200 hover:bg-slate-50 transition">
                    <td class="px-2 py-1 text-slate-500">{{ i + 1 }}</td>
                    <td class="px-2 py-1">
                      <input v-model.number="p.pieces" type="number" min="1"
                        class="w-12 text-[12px] font-mono px-1 py-0.5 rounded border border-slate-300 text-right outline-none focus:border-slate-500" />
                    </td>
                    <td class="px-2 py-1">
                      <input v-model.number="p.lengthIn" type="number" step="0.1" min="0"
                        class="w-14 text-[12px] font-mono px-1 py-0.5 rounded border border-slate-300 text-right outline-none focus:border-slate-500" />
                    </td>
                    <td class="px-2 py-1">
                      <input v-model.number="p.widthIn" type="number" step="0.1" min="0"
                        class="w-14 text-[12px] font-mono px-1 py-0.5 rounded border border-slate-300 text-right outline-none focus:border-slate-500" />
                    </td>
                    <td class="px-2 py-1">
                      <input v-model.number="p.heightIn" type="number" step="0.1" min="0"
                        class="w-14 text-[12px] font-mono px-1 py-0.5 rounded border border-slate-300 text-right outline-none focus:border-slate-500" />
                    </td>
                    <td class="px-2 py-1">
                      <input v-model.number="p.scaleWeightLbs" type="number" step="0.1" min="0"
                        class="w-16 text-[12px] font-mono px-1 py-0.5 rounded border border-slate-300 text-right outline-none focus:border-slate-500" />
                    </td>
                    <td class="px-2 py-1 text-right text-slate-500">{{ (p.dimWeightLbs || 0).toFixed(1) }}</td>
                    <td class="px-2 py-1 text-right font-bold text-slate-700">{{ (p.chargeableLbs || 0).toFixed(1) }}</td>
                    <td class="px-2 py-1 text-right font-bold text-emerald-700">{{ (p.chargeableKg || 0).toFixed(1) }}</td>
                    <td class="px-1 py-1">
                      <button v-if="pieces.length > 1" @click="removePiece(i)"
                        class="text-slate-300 hover:text-red-500 transition px-0.5" title="Eliminar pieza">✕</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <button @click="addPiece"
              class="mt-1 text-[12px] font-mono font-bold text-slate-500 hover:text-slate-700 uppercase tracking-wider transition">
              + Agregar Pieza
            </button>
          </div>
        </div>

        <!-- FOOTER -->
        <div class="flex items-center justify-between px-4 py-2.5 border-t border-slate-300 bg-slate-50 shrink-0">
          <span v-if="saving" class="text-[12px] font-mono text-amber-600 uppercase">Guardando...</span>
          <span v-else-if="errorMsg" class="text-[12px] font-mono text-red-500 uppercase">{{ errorMsg }}</span>
          <span v-else class="text-[12px] font-mono text-slate-400">Los cambios sobrescribirán el recibo actual</span>
          <div class="flex items-center gap-2">
            <button @click="close"
              class="text-[12px] px-3 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider font-bold text-slate-600 hover:bg-white transition">
              Cancelar
            </button>
            <button @click="save" :disabled="saving"
              class="text-[12px] px-3 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-white bg-slate-950 hover:bg-slate-800 transition disabled:opacity-50">
              {{ saving ? 'Guardando...' : 'Guardar Cambios' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { receiptsApi } from '@/api/receipts'

const emit = defineEmits(['saved', 'close'])

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')

const receiptId = ref(null)
const mawbId = ref(null)
const hawbId = ref(null)

const form = ref({
  gatewayCfs: 'SDQ',
  shipperName: '',
  consigneeName: '',
  origin: 'SDQ',
  destination: 'MIA',
  awbReportedPieces: 0,
  dimFactorKg: 366,
  dimFactorLbs: 194,
  cashOnly: false,
  bookedInAcoms: false,
  docsProvided: false,
  customsCompleted: false,
  preBuilt: false,
  remarks: '',
})

const checkboxes = [
  { key: 'cashOnly', label: 'Cash Only' },
  { key: 'bookedInAcoms', label: 'Booked in ACOMS' },
  { key: 'docsProvided', label: 'Documents Provided' },
  { key: 'customsCompleted', label: 'Export Customs Completed' },
  { key: 'preBuilt', label: 'Pre-built' },
]

const pieces = ref([])

function calcPiece(pi) {
  const p = pieces.value[pi]
  if (!p) return
  const l = p.lengthIn || 0
  const w = p.widthIn || 0
  const h = p.heightIn || 0
  const qty = p.pieces || 1
  const vol = l * w * h * qty
  const dfKg = form.value.dimFactorKg || 366
  const dfLbs = form.value.dimFactorLbs || 194
  p.dimWeightKg = vol > 0 ? vol / dfKg : 0
  p.dimWeightLbs = vol > 0 ? vol / dfLbs : 0
  p.scaleWeightKg = p.scaleWeightLbs ? p.scaleWeightLbs / 2.20462 : 0
  p.chargeableKg = Math.max(p.dimWeightKg, p.scaleWeightKg)
  p.chargeableLbs = Math.max(p.scaleWeightLbs || 0, p.dimWeightLbs || 0)
}

function recalcAll() {
  pieces.value.forEach((_, i) => calcPiece(i))
}

const totalScaleLbs = computed(() => pieces.value.reduce((s, p) => s + (p.scaleWeightLbs || 0), 0))
const totalScaleKg = computed(() => pieces.value.reduce((s, p) => s + (p.scaleWeightKg || 0), 0))
const totalChargeKg = computed(() => pieces.value.reduce((s, p) => s + (p.chargeableKg || 0), 0))
const totalChargeLbs = computed(() => pieces.value.reduce((s, p) => s + (p.chargeableLbs || 0), 0))

function addPiece() {
  pieces.value.push({
    pieces: 1, hawbId: null,
    lengthIn: 0, widthIn: 0, heightIn: 0,
    scaleWeightLbs: 0, scaleWeightKg: 0,
    dimWeightLbs: 0, dimWeightKg: 0,
    chargeableLbs: 0, chargeableKg: 0,
  })
}

function removePiece(i) {
  pieces.value.splice(i, 1)
}

watch(() => pieces.value.map(p => [p.lengthIn, p.widthIn, p.heightIn, p.pieces, p.scaleWeightLbs]), () => {
  recalcAll()
}, { deep: true })

watch(() => [form.value.dimFactorKg, form.value.dimFactorLbs], () => {
  recalcAll()
})

async function open(receiptIdParam) {
  receiptId.value = receiptIdParam
  visible.value = true
  loading.value = true
  errorMsg.value = ''
  pieces.value = []

  try {
    const [receiptRes, piecesRes] = await Promise.all([
      receiptsApi.getById(receiptIdParam),
      receiptsApi.getPieces(receiptIdParam),
    ])
    const r = receiptRes.data
    mawbId.value = r.mawbId
    hawbId.value = r.hawbId

    form.value = {
      gatewayCfs: r.gatewayCfs || 'SDQ',
      shipperName: r.shipperName || '',
      consigneeName: r.consigneeName || '',
      origin: r.origin || 'SDQ',
      destination: r.destination || 'MIA',
      awbReportedPieces: r.awbReportedPieces || 0,
      dimFactorKg: r.dimFactorIntl ? Number(r.dimFactorIntl) : 366,
      dimFactorLbs: r.dimFactorDom ? Number(r.dimFactorDom) : 194,
      cashOnly: r.cashOnly || false,
      bookedInAcoms: r.bookedInAcoms || false,
      docsProvided: r.docsProvided || false,
      customsCompleted: r.customsCompleted || false,
      preBuilt: r.preBuilt || false,
      remarks: r.remarks || '',
    }

    const rawPieces = piecesRes.data || []
    if (rawPieces.length > 0) {
      pieces.value = rawPieces.map(p => ({
        pieces: p.pieces ?? 1,
        hawbId: p.hawbId || null,
        lengthIn: p.lengthIn != null ? Number(p.lengthIn) : 0,
        widthIn: p.widthIn != null ? Number(p.widthIn) : 0,
        heightIn: p.heightIn != null ? Number(p.heightIn) : 0,
        scaleWeightLbs: p.scaleWeightLbs != null ? Number(p.scaleWeightLbs) : 0,
        scaleWeightKg: p.scaleWeightKg != null ? Number(p.scaleWeightKg) : 0,
        dimWeightLbs: p.dimWeightLbs != null ? Number(p.dimWeightLbs) : 0,
        dimWeightKg: p.dimWeightKg != null ? Number(p.dimWeightKg) : 0,
        chargeableLbs: p.chargeableLbs != null ? Number(p.chargeableLbs) : 0,
        chargeableKg: p.chargeableKg != null ? Number(p.chargeableKg) : 0,
      }))
    } else {
      addPiece()
    }
    recalcAll()
  } catch (e) {
    errorMsg.value = 'Error cargando recibo: ' + (e.message || e)
  } finally {
    loading.value = false
  }
}

function close() {
  visible.value = false
  emit('close')
}

async function save() {
  saving.value = true
  errorMsg.value = ''

  try {
    const totalPcs = pieces.value.reduce((s, p) => s + (p.pieces || 1), 0)
    const hId = hawbId.value || null
    const validHawbId = hId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(hId) ? hId : null
    const payload = {
      mawbId: mawbId.value,
      gatewayCfs: form.value.gatewayCfs || 'SDQ',
      shipperName: form.value.shipperName,
      consigneeName: form.value.consigneeName,
      origin: form.value.origin,
      destination: form.value.destination,
      awbReportedPieces: form.value.awbReportedPieces || totalPcs,
      dimFactorIntl: form.value.dimFactorKg || 366,
      dimFactorDom: form.value.dimFactorLbs || 194,
      pieceCount: totalPcs,
      cashOnly: form.value.cashOnly,
      bookedInAcoms: form.value.bookedInAcoms,
      docsProvided: form.value.docsProvided,
      customsCompleted: form.value.customsCompleted,
      preBuilt: form.value.preBuilt,
      remarks: form.value.remarks || '',
      hawbId: validHawbId,
      startDatetime: new Date().toISOString(),
      receiptDate: new Date().toISOString(),
      pieces: pieces.value.map((p, i) => ({
        pieceNumber: i + 1,
        pieces: p.pieces || 1,
        hawbId: (p.hawbId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(p.hawbId)) ? p.hawbId : null,
        lengthIn: p.lengthIn || 0,
        widthIn: p.widthIn || 0,
        heightIn: p.heightIn || 0,
        scaleWeightLbs: p.scaleWeightLbs || 0,
        scaleWeightKg: p.scaleWeightKg || 0,
        dimWeightLbs: p.dimWeightLbs || 0,
        dimWeightKg: p.dimWeightKg || 0,
        chargeableLbs: p.chargeableLbs || 0,
        chargeableKg: p.chargeableKg || 0,
      })),
      supportingDocs: '[]',
    }

    await receiptsApi.update(receiptId.value, payload)
    emit('saved', { receiptId: receiptId.value, mawbId: mawbId.value })
    close()
  } catch (e) {
    errorMsg.value = 'Error guardando: ' + (e.response?.data?.error || e.message || e)
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
