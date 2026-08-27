<template>
  <div class="px-5 py-4">
    <div class="flex items-center justify-between mb-5 pb-3 border-b border-slate-200">
      <div class="flex items-center gap-1">
        <template v-for="(step, idx) in steps" :key="step.key">
          <div class="flex items-center gap-1.5 cursor-pointer" @click="currentStep = idx + 1">
            <div class="w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-mono font-black border-2 transition-all"
              :class="currentStep === idx + 1
                ? 'bg-slate-950 text-white border-slate-950 scale-110'
                : currentStep > idx + 1
                  ? 'bg-slate-600 text-white border-slate-600'
                  : 'bg-white text-slate-400 border-slate-300'">
              <component :is="icons.Check" v-if="currentStep > idx + 1" :size="11" :stroke-width="3" />
              <span v-else>{{ idx + 1 }}</span>
            </div>
            <span class="text-[11px] font-mono uppercase tracking-wide font-bold hidden lg:inline"
              :class="currentStep === idx + 1 ? 'text-slate-950' : 'text-slate-400'">
              {{ step.label }}
            </span>
          </div>
          <div v-if="idx < steps.length - 1" class="w-6 h-px mx-1" :class="currentStep > idx + 1 ? 'bg-slate-500' : 'bg-slate-200'"></div>
        </template>
      </div>
      <button @click="$emit('cancel')" class="text-slate-400 hover:text-slate-700 transition">
        <component :is="icons.X" :size="16" :stroke-width="2" />
      </button>
    </div>

    <!-- Step 1 -->
    <div v-if="currentStep === 1" class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div class="space-y-3">
        <div v-for="field in step1Fields" :key="field.key">
          <label class="text-[11px] font-mono uppercase tracking-wider font-bold text-slate-400 block mb-1">{{ field.label }}</label>
          <input :value="form[field.key]"
            @input="form[field.key] = field.numeric ? ($event.target.valueAsNumber || null) : $event.target.value"
            :type="field.numeric ? 'number' : 'text'"
            :maxlength="field.maxlength"
            :placeholder="field.placeholder"
            class="w-full text-xs font-mono px-3 py-2 rounded border border-slate-200 bg-white outline-none focus:border-slate-950 transition shadow-pencil-marine" />
          <div v-if="field.key === 'awbReportedPieces'" class="mt-1.5 flex items-center gap-1.5 text-[11px] font-mono flex-wrap">
            <span class="text-slate-400 uppercase tracking-wide">Total medido (Paso 2):</span>
            <span class="font-black" :class="piecesMismatch ? 'text-slate-500' : 'text-slate-700'">{{ totalQty }}</span>
            <span v-if="piecesMismatch" class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-slate-50 border border-slate-200 text-slate-600 uppercase font-bold">
              <component :is="icons.AlertTriangle" :size="10" :stroke-width="2.5" /> No coincide
            </span>
            <span v-else class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-slate-100 border border-slate-300 text-slate-700 uppercase font-bold">
              <component :is="icons.Check" :size="10" :stroke-width="2.5" /> Coincide
            </span>
          </div>
        </div>
      </div>
      <div class="bg-white rounded border border-slate-200 p-4 shadow-pencil-marine">
        <h4 class="text-xs font-mono uppercase tracking-wider font-bold text-slate-400 mb-3">Estado del Embarque</h4>
        <div class="space-y-2.5">
          <label v-for="cb in checkboxes" :key="cb.key" class="flex items-center gap-2 text-xs font-mono text-slate-700 cursor-pointer"
            :class="cb.disabled ? 'opacity-60' : ''">
            <input type="checkbox" v-model="form[cb.key]" :disabled="cb.disabled"
              class="w-3.5 h-3.5 rounded border-slate-300 accent-slate-950" />
            {{ cb.label }}
          </label>
        </div>
        <p class="text-[11px] font-mono text-slate-400 mt-3 pt-3 border-t border-slate-100">* Campos bloqueados solo para Administradores</p>
      </div>
    </div>

    <!-- Step 2 -->
    <div v-if="currentStep === 2">
      <div class="flex items-center justify-between mb-3 flex-wrap gap-2">
        <h3 class="text-xs font-mono uppercase tracking-widest font-bold text-slate-400">Detalle de Piezas por Grupo Dimensional</h3>
        <div class="flex items-center gap-2 text-[11px] font-mono">
          <span class="text-slate-400 uppercase tracking-wide">Reportado:</span>
          <span class="font-black text-slate-700">{{ form.awbReportedPieces ?? '—' }}</span>
          <span class="text-slate-300">/</span>
          <span class="text-slate-400 uppercase tracking-wide">Total:</span>
          <span class="font-black" :class="piecesMismatch ? 'text-slate-500' : 'text-slate-700'">{{ totalQty }}</span>
          <span v-if="piecesMismatch" class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-slate-50 border border-slate-200 text-slate-600 uppercase font-bold">
            <component :is="icons.AlertTriangle" :size="10" :stroke-width="2.5" /> Revisar
          </span>
        </div>
      </div>
      <p class="text-[11px] font-mono text-slate-400 mb-3 italic">Cada fila = un grupo de piezas con las mismas dimensiones. Ej: 57 piezas de 17×18×5 in.</p>

      <!-- Grid table using CSS grid for perfect alignment -->
      <div class="border border-slate-200 rounded overflow-hidden shadow-pencil-marine bg-white">

        <!-- Header -->
        <div style="display:grid; grid-template-columns: 36px 1fr 1fr 1fr 1fr 1.4fr 1.4fr 28px; background:#f8fafc; border-bottom:1px solid #e2e8f0; font-size:9px; font-family:monospace; color:#94a3b8; text-transform:uppercase; letter-spacing:0.05em; font-weight:700;">
          <div style="padding:8px 4px; text-align:center;">#</div>
          <div style="padding:8px 6px; text-align:center;">Qty</div>
          <div style="padding:8px 6px; text-align:center;">L (in)</div>
          <div style="padding:8px 6px; text-align:center;">W (in)</div>
          <div style="padding:8px 6px; text-align:center;">H (in)</div>
          <div style="padding:8px 6px; text-align:center;">Dim Wt (lbs)</div>
          <div style="padding:8px 6px; text-align:center;">Scale Wt (lbs)</div>
          <div style="padding:8px 4px; text-align:center;"></div>
        </div>

        <!-- Rows -->
        <div v-for="(group, i) in form.pieceGroups" :key="i"
          class="row-pencil"
          style="display:grid; grid-template-columns: 36px 1fr 1fr 1fr 1fr 1.4fr 1.4fr 28px; border-bottom:1px solid #f1f5f9; align-items:center;">

          <div style="padding:8px 4px; text-align:center; font-family:monospace; font-weight:900; font-size:11px; color:#020617;" class="relative z-10">{{ i + 1 }}</div>

          <div style="padding:4px;" class="relative z-10">
            <input type="number" min="1"
              :value="group.qty !== null ? group.qty : ''"
              @input="group.qty = $event.target.value !== '' ? parseInt($event.target.value) : null; calcGroupDim(i)"
              style="width:100%; box-sizing:border-box; text-align:center; font-family:monospace; font-weight:700; font-size:11px; padding:4px 2px; border:1px solid #cbd5e1; border-radius:4px; background:#f8fafc; outline:none;" />
          </div>

          <div style="padding:4px;" class="relative z-10">
            <input type="number" step="0.1"
              :value="group.lengthIn !== null ? group.lengthIn : ''"
              @input="group.lengthIn = $event.target.value !== '' ? parseFloat($event.target.value) : null; calcGroupDim(i)"
              style="width:100%; box-sizing:border-box; text-align:center; font-family:monospace; font-size:11px; padding:4px 2px; border:1px solid #e2e8f0; border-radius:4px; outline:none;" />
          </div>

          <div style="padding:4px;" class="relative z-10">
            <input type="number" step="0.1"
              :value="group.widthIn !== null ? group.widthIn : ''"
              @input="group.widthIn = $event.target.value !== '' ? parseFloat($event.target.value) : null; calcGroupDim(i)"
              style="width:100%; box-sizing:border-box; text-align:center; font-family:monospace; font-size:11px; padding:4px 2px; border:1px solid #e2e8f0; border-radius:4px; outline:none;" />
          </div>

          <div style="padding:4px;" class="relative z-10">
            <input type="number" step="0.1"
              :value="group.heightIn !== null ? group.heightIn : ''"
              @input="group.heightIn = $event.target.value !== '' ? parseFloat($event.target.value) : null; calcGroupDim(i)"
              style="width:100%; box-sizing:border-box; text-align:center; font-family:monospace; font-size:11px; padding:4px 2px; border:1px solid #e2e8f0; border-radius:4px; outline:none;" />
          </div>

          <!-- Dim Wt — solo lectura, calculado -->
          <div style="padding:8px 6px; text-align:center; font-family:monospace; font-weight:700; font-size:11px; color:#475569;" class="relative z-10">
            {{ group.dimWeightLbs != null ? group.dimWeightLbs.toFixed(2) : '—' }}
          </div>

          <!-- Scale Wt — entrada báscula, entero -->
          <div style="padding:4px;" class="relative z-10">
            <input type="number" step="1" min="0"
              :value="group.scaleWeightLbs !== null ? group.scaleWeightLbs : ''"
              @input="group.scaleWeightLbs = $event.target.value !== '' ? Math.round(Number($event.target.value)) : null"
              style="width:100%; box-sizing:border-box; text-align:center; font-family:monospace; font-size:11px; padding:4px 2px; border:1px solid #e2e8f0; border-radius:4px; outline:none;" />
          </div>

          <div style="padding:4px; text-align:center;" class="relative z-10">
            <button v-if="form.pieceGroups.length > 1" @click="removeGroup(i)"
              class="text-slate-300 hover:text-slate-500 transition">
              <component :is="icons.Trash" :size="13" :stroke-width="1.8" />
            </button>
          </div>
        </div>

        <!-- Footer totals -->
        <div style="display:grid; grid-template-columns: 36px 1fr 1fr 1fr 1fr 1.4fr 1.4fr 28px; background:#f8fafc; border-top:2px solid #cbd5e1; font-family:monospace; font-weight:900; font-size:10px; color:#0f172a; align-items:center;">
          <div style="padding:8px 4px; text-align:center; grid-column: 1/3;">{{ totalQty }} pzs</div>
          <div style="grid-column: 3/6;"></div>
          <div style="padding:8px 6px; text-align:center;">{{ totalDimWeight > 0 ? totalDimWeight.toFixed(2) : '—' }}</div>
          <div style="padding:8px 6px; text-align:center;">{{ totalScaleWeight > 0 ? totalScaleWeight.toFixed(2) : '—' }}</div>
          <div></div>
        </div>
      </div>

      <div class="flex items-center gap-2 mt-3">
        <button @click="addGroup"
          class="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider font-bold text-slate-600 hover:bg-slate-50 transition">
          <component :is="icons.Plus" :size="12" :stroke-width="2.5" /> Agregar Grupo Dimensional
        </button>
      </div>
    </div>

    <!-- Step 3 -->
    <div v-if="currentStep === 3">
      <h3 class="text-xs font-mono uppercase tracking-widest font-bold text-slate-400 mb-3">Remarks / Observaciones</h3>
      <textarea v-model="form.remarks" rows="6" placeholder="DRY CARGO, NOTAS ADICIONALES..."
        class="w-full text-xs font-mono px-3 py-2.5 rounded border border-slate-200 bg-white outline-none focus:border-slate-950 transition shadow-pencil-marine resize-none"></textarea>
    </div>

    <!-- Step 4 -->
    <div v-if="currentStep === 4">
      <h3 class="text-xs font-mono uppercase tracking-widest font-bold text-slate-400 mb-3">Evidencias y Documentos</h3>
      <div class="grid grid-cols-3 gap-3">
        <button v-for="ev in evidenceButtons" :key="ev.label"
          class="pencil-sketch flex flex-col items-center gap-2 py-6 rounded border border-slate-200 bg-white shadow-pencil-marine hover:border-slate-400 transition">
          <component :is="ev.icon" :size="20" :stroke-width="1.6" class="text-slate-500 relative z-10" />
          <span class="text-[11px] font-mono uppercase tracking-wide font-bold text-slate-500 relative z-10">{{ ev.label }}</span>
        </button>
      </div>
    </div>

    <!-- Step 5 -->
    <div v-if="currentStep === 5">
      <h3 class="text-xs font-mono uppercase tracking-widest font-bold text-slate-400 mb-3">Firmas</h3>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div v-for="sig in signatureFields" :key="sig.key">
          <label class="text-[11px] font-mono uppercase tracking-wider font-bold text-slate-400 block mb-1">{{ sig.label }}</label>
          <input v-model="form[sig.key]" placeholder="Nombre completo"
            class="w-full text-xs font-mono px-3 py-2 rounded border border-slate-200 bg-white outline-none focus:border-slate-950 transition shadow-pencil-marine" />
        </div>
      </div>
    </div>

    <!-- Navigation -->
    <div class="flex justify-end gap-2 mt-6 pt-4 border-t border-slate-200">
      <button v-if="currentStep > 1" @click="currentStep--"
        class="text-xs px-3.5 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider font-bold text-slate-600 hover:bg-slate-50 transition">
        Anterior
      </button>
      <button v-if="currentStep < 5" @click="currentStep++"
        class="text-xs px-3.5 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-white bg-slate-950 hover:bg-slate-800 shadow-pencil-marine transition active:scale-95">
        Siguiente
      </button>
      <button v-if="currentStep === 5" @click="handleSubmit"
        class="flex items-center gap-1.5 text-xs px-3.5 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-white bg-slate-800 hover:bg-slate-700 shadow-pencil-marine transition active:scale-95">
        <component :is="icons.Check" :size="12" :stroke-width="2.5" /> Guardar Recibo Completo
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useIcons } from '../composables/useIcons'

const icons = useIcons()

const props = defineProps({
  initialData: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['submit', 'cancel'])
const currentStep = ref(1)

const steps = [
  { key: 'general',      label: 'Datos Generales' },
  { key: 'pieces',       label: 'Piezas' },
  { key: 'observations', label: 'Observaciones' },
  { key: 'evidence',     label: 'Evidencias' },
  { key: 'signatures',   label: 'Firmas' },
]

const form = ref({
  gatewayCfs: 'SDQ',
  shipperName: props.initialData.shipperName || '',
  mawbNumber: props.initialData.mawbNumber || '',
  origin: 'SDQ',
  destination: 'MIA',
  awbReportedPieces: props.initialData.pieces || null,
  reportedWeightKg: props.initialData.reportedWeightKg || null,
  cashOnly: false,
  bookedInAcoms: true,
  docsProvided: false,
  customsCompleted: true,
  preBuilt: false,
  looseTender: false,
  remarks: '',
  deliveredBy: '',
  receivedBy: '',
  brokerName: '',
  pieceGroups: [{ qty: null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeightLbs: null }]
})

const totalQty = computed(() =>
  form.value.pieceGroups.reduce((sum, g) => sum + (g.qty || 0), 0)
)
const totalDimWeight = computed(() =>
  form.value.pieceGroups.reduce((sum, g) => sum + (g.dimWeightLbs || 0), 0)
)
const totalScaleWeight = computed(() =>
  form.value.pieceGroups.reduce((sum, g) => sum + (g.scaleWeightLbs || 0), 0)
)
const piecesMismatch = computed(() =>
  form.value.awbReportedPieces != null && form.value.awbReportedPieces !== totalQty.value
)

const step1Fields = [
  { key: 'gatewayCfs',        label: 'Gateway / CFS Name' },
  { key: 'shipperName',       label: 'Shipper Name' },
  { key: 'mawbNumber',        label: 'MAWB Number *', placeholder: '406-XXXXXXXX' },
  { key: 'origin',            label: 'Origin', maxlength: 3 },
  { key: 'destination',       label: 'Destination', maxlength: 3 },
  { key: 'awbReportedPieces', label: 'AWB Reported Pieces (declarado por shipper)', numeric: true },
  { key: 'reportedWeightKg',  label: 'Shipper Reported Weight (kg)', numeric: true },
]

const checkboxes = [
  { key: 'cashOnly',         label: 'Cash Only' },
  { key: 'bookedInAcoms',    label: 'Booked in ACOMS',          disabled: true },
  { key: 'docsProvided',     label: 'Documents Provided' },
  { key: 'customsCompleted', label: 'Export Customs Completed', disabled: true },
  { key: 'preBuilt',         label: 'Pre-built / Shipper Built' },
  { key: 'looseTender',      label: 'Loose Tender' },
]

const evidenceButtons = [
  { label: 'Capturar Foto',        icon: icons.Camera },
  { label: 'Subir PDF / Imágenes', icon: icons.FileUpload },
  { label: 'Otros Archivos',       icon: icons.Paperclip },
]

const signatureFields = [
  { key: 'deliveredBy', label: 'Entregado Por' },
  { key: 'receivedBy',  label: 'Recibido Por' },
  { key: 'brokerName',  label: 'Representante Broker' },
]

function addGroup() {
  form.value.pieceGroups.push({ qty: null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeightLbs: null })
}
function removeGroup(i) {
  form.value.pieceGroups.splice(i, 1)
}
function calcGroupDim(i) {
  const g = form.value.pieceGroups[i]
  const l = g.lengthIn || 0
  const w = g.widthIn  || 0
  const h = g.heightIn || 0
  const q = g.qty      || 0
  g.dimWeightLbs = (l > 0 && w > 0 && h > 0 && q > 0) ? (l * w * h / 194) * q : null
}
function handleSubmit() {
  emit('submit', {
    ...form.value,
    pieces: totalQty.value,
    totalDimWeightLbs: totalDimWeight.value,
    totalScaleWeightLbs: totalScaleWeight.value
  })
}
</script>

<style scoped>
.shadow-pencil-marine {
  box-shadow: 0px 1px 2px rgba(15,32,67,0.08), 1px 3px 6px rgba(15,32,67,0.06), 3px 6px 12px rgba(15,32,67,0.04);
}
.pencil-sketch { position: relative; overflow: hidden; transition: transform 0.15s cubic-bezier(0.16,1,0.3,1), box-shadow 0.15s cubic-bezier(0.16,1,0.3,1); }
.pencil-sketch::before { content: ""; position: absolute; inset: 0; opacity: 0.22; transition: opacity 0.2s ease; pointer-events: none; background-image: repeating-linear-gradient(45deg, rgba(71,85,105,0.07) 0px, rgba(71,85,105,0.07) 0.6px, transparent 0.6px, transparent 3px); }
.pencil-sketch:hover { transform: translate(-0.5px,-0.5px); box-shadow: 0px 1px 2px rgba(15,32,67,0.12), 2px 5px 8px rgba(15,32,67,0.09), 4px 10px 16px rgba(15,32,67,0.06); }
.pencil-sketch:hover::before { opacity: 0.6; }
.row-pencil { position: relative; }
.row-pencil::before { content: ""; position: absolute; inset: 0; opacity: 0; transition: opacity 0.12s ease-in-out; pointer-events: none; background-image: repeating-linear-gradient(45deg, rgba(15,32,67,0.05) 0px, rgba(15,32,67,0.05) 0.6px, transparent 0.6px, transparent 2px); }
.row-pencil:hover::before { opacity: 1; }
.row-pencil:hover { background-color: rgba(243,247,254,0.5); }
</style>
