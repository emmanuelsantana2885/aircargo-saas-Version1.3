<template>
  <div class="fixed inset-0 z-[100] flex bg-slate-900/60 backdrop-blur-sm">
    <!-- Top bar -->
    <div class="absolute top-0 left-0 right-0 h-14 bg-slate-950 flex items-center gap-3 px-4 z-20">
      <span class="text-[13px] font-mono font-black uppercase tracking-widest text-white whitespace-nowrap">
        Editor de Etiquetas — {{ typeLabel }}
      </span>
      <select v-model="loadId" class="bg-slate-800 text-white border border-slate-700 rounded px-2 py-1 text-[12px] font-mono max-w-[220px]">
        <option value="">— Cargar plantilla —</option>
        <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} {{ t.isDefault ? '(default)' : '' }}</option>
      </select>
      <button @click="loadSelected" class="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 text-white text-[12px] font-mono">Cargar</button>
      <button @click="newTemplate" class="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 text-white text-[12px] font-mono">Nueva</button>
      <div class="flex-1"></div>
      <span v-if="dirty" class="text-[11px] font-mono text-amber-300 uppercase">sin guardar</span>
      <button @click="save" :disabled="saving"
        class="px-3 py-1 rounded bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-[12px] font-mono font-bold">
        {{ saving ? 'Guardando...' : currentId ? 'Actualizar' : 'Guardar' }}
      </button>
      <button @click="$emit('close')" class="w-8 h-8 rounded hover:bg-slate-700 text-white text-[14px] font-mono">✕</button>
    </div>

    <!-- Left: toolbox + element list -->
    <div class="w-56 bg-slate-100 border-r border-slate-300 flex flex-col pt-14 shrink-0">
      <div class="p-3 border-b border-slate-200">
        <div class="text-[11px] font-black uppercase tracking-widest text-slate-500 mb-2">Añadir elemento</div>
        <div class="grid grid-cols-2 gap-1.5">
          <button v-for="tool in tools" :key="tool.type" @click="addElement(tool.type)"
            class="py-1.5 rounded border border-slate-300 bg-white hover:border-slate-950 text-[11px] font-mono font-bold text-slate-700 transition">
            + {{ tool.label }}
          </button>
        </div>
      </div>
      <div class="flex-1 overflow-y-auto p-3 space-y-1">
        <div class="text-[11px] font-black uppercase tracking-widest text-slate-500 mb-1">Elementos ({{ elements.length }})</div>
        <button v-for="(el, i) in elements" :key="el.id" @click="selectElement(i)"
          class="w-full text-left px-2 py-1.5 rounded border text-[12px] font-mono transition flex items-center justify-between gap-1"
          :class="selectedIndex === i ? 'border-slate-950 bg-white shadow-sm' : 'border-slate-200 bg-white hover:border-slate-400'">
          <span class="truncate text-slate-700">
            <span class="font-black text-slate-950">{{ el.type }}</span>
            <span class="text-slate-400"> · {{ Math.round(el.x) }},{{ Math.round(el.y) }}</span>
          </span>
          <span @click.stop="removeElement(i)" class="text-slate-300 hover:text-rose-500 px-1">✕</span>
        </button>
        <div v-if="!elements.length" class="text-[12px] font-mono text-slate-400 text-center pt-6">Sin elementos</div>
      </div>
      <div class="p-3 border-t border-slate-200">
        <label class="block text-[11px] font-mono font-black text-slate-600 mb-1">Plantilla por defecto</label>
        <label class="flex items-center gap-2 text-[12px] font-mono text-slate-700 cursor-pointer">
          <input type="checkbox" v-model="isDefault" class="accent-slate-950" />
          Usar como default {{ typeLabel }}
        </label>
        <button v-if="currentId" @click="remove" class="mt-2 w-full py-1 rounded border border-rose-200 text-rose-600 hover:bg-rose-50 text-[12px] font-mono">Eliminar plantilla</button>
      </div>
    </div>

    <!-- Center: canvas -->
    <div class="flex-1 bg-slate-200 flex items-center justify-center pt-14 overflow-auto">
      <div class="relative bg-white shadow-xl border border-slate-400" :style="canvasStyle">
        <!-- grid dots -->
        <div class="absolute inset-0 pointer-events-none" :style="gridStyle"></div>
        <div v-for="(el, i) in elements" :key="el.id"
          class="absolute cursor-move"
          :class="selectedIndex === i ? 'z-20 outline outline-2 outline-slate-950' : 'z-10 hover:outline hover:outline-1 hover:outline-slate-400'"
          :style="elementStyle(el)"
          @mousedown.stop="startDrag(i, $event)"
          @click.stop="selectElement(i)">
          <span v-if="el.type === 'text'" class="font-mono" :style="textStyle(el)">{{ resolveElementValue(el, type) }}</span>
          <span v-else-if="el.type === 'line'" class="block" style="height:1px;background:#000"></span>
          <span v-else-if="el.type === 'rect'" class="block" style="width:100%;height:100%;border:1px solid #000"></span>
          <span v-else class="flex items-center justify-center text-[9px] font-mono text-slate-400 font-bold uppercase"
            :style="{ width: '100%', height: '100%' }">
            {{ el.type === 'qrcode' ? 'QR' : 'CODE128' }}
            <br />{{ el.barcodeFormat }}
          </span>
          <span v-if="selectedIndex === i" @mousedown.stop="startResize(i, $event)"
            class="absolute bottom-0 right-0 w-3 h-3 bg-slate-950 cursor-se-resize"></span>
        </div>
        <div v-if="!elements.length" class="absolute inset-0 flex items-center justify-center text-[13px] font-mono text-slate-300">
          Añade elementos desde el panel izquierdo
        </div>
      </div>
    </div>

    <!-- Right: properties -->
    <div class="w-72 bg-slate-100 border-l border-slate-300 flex flex-col pt-14 shrink-0 overflow-y-auto">
      <div class="p-3 border-b border-slate-200">
        <div class="text-[11px] font-black uppercase tracking-widest text-slate-500 mb-2">Propiedades de la etiqueta</div>
        <label class="block mb-2">
          <span class="text-[11px] font-mono text-slate-600">Nombre</span>
          <input v-model="name" type="text" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
        </label>
        <label class="block mb-2">
          <span class="text-[11px] font-mono text-slate-600">Tamaño (pulgadas)</span>
          <select v-model="sizePreset" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" @change="applySizePreset">
            <option v-for="sz in SIZE_PRESETS" :key="sz.label" :value="sz.label">{{ sz.label }}</option>
            <option value="custom">Personalizado</option>
          </select>
        </label>
        <div class="grid grid-cols-2 gap-2 mb-2">
          <label>
            <span class="text-[11px] font-mono text-slate-600">Ancho (in)</span>
            <input v-model.number="widthInches" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
          </label>
          <label>
            <span class="text-[11px] font-mono text-slate-600">Alto (in)</span>
            <input v-model.number="heightInches" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
          </label>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <label>
            <span class="text-[11px] font-mono text-slate-600">Orientación</span>
            <select v-model="orientation" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white">
              <option value="HORIZONTAL">Horizontal</option>
              <option value="VERTICAL">Vertical</option>
            </select>
          </label>
          <label>
            <span class="text-[11px] font-mono text-slate-600">DPI</span>
            <input v-model.number="dpi" type="number" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
          </label>
        </div>
        <div class="mt-2 text-[11px] font-mono text-slate-400">
          Dimensiones efectivas: {{ eff.w.toFixed(1) }} x {{ eff.h.toFixed(1) }} pulg.
        </div>
      </div>

      <div v-if="selectedElement" class="p-3 border-b border-slate-200">
        <div class="text-[11px] font-black uppercase tracking-widest text-slate-500 mb-2">Elemento: {{ selectedElement.type }}</div>

        <label v-if="isTextLike" class="block mb-2">
          <span class="text-[11px] font-mono text-slate-600">Fuente de datos</span>
          <select v-model="selectedElement.dataSource" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white">
            <option value="TEXT">Texto fijo</option>
            <option v-for="f in FIELDS[type]" :key="f.key" :value="f.key">{{ f.label }} ({{ f.key }})</option>
          </select>
        </label>

        <label v-if="isTextLike && selectedElement.dataSource === 'TEXT'" class="block mb-2">
          <span class="text-[11px] font-mono text-slate-600">Texto</span>
          <input v-model="selectedElement.text" type="text" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
        </label>

        <div class="grid grid-cols-2 gap-2 mb-2">
          <label><span class="text-[11px] font-mono text-slate-600">X (mm)</span>
            <input v-model.number="selectedElement.x" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
          <label><span class="text-[11px] font-mono text-slate-600">Y (mm)</span>
            <input v-model.number="selectedElement.y" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
          <label v-if="!isLine"><span class="text-[11px] font-mono text-slate-600">Ancho (mm)</span>
            <input v-model.number="selectedElement.w" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
          <label v-if="!isLine"><span class="text-[11px] font-mono text-slate-600">Alto (mm)</span>
            <input v-model.number="selectedElement.h" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
        </div>

        <template v-if="isTextLike">
          <div class="grid grid-cols-2 gap-2 mb-2">
            <label><span class="text-[11px] font-mono text-slate-600">Fuente (mm)</span>
              <input v-model.number="selectedElement.fontSize" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
            <label><span class="text-[11px] font-mono text-slate-600">Alinear</span>
              <select v-model="selectedElement.align" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white">
                <option value="left">Izquierda</option>
                <option value="center">Centro</option>
                <option value="right">Derecha</option>
              </select></label>
          </div>
          <label class="flex items-center gap-2 mb-2 text-[12px] font-mono text-slate-700 cursor-pointer">
            <input type="checkbox" v-model="selectedElement.bold" class="accent-slate-950" /> Negrita
          </label>
        </template>

        <template v-if="selectedElement.type === 'barcode' || selectedElement.type === 'qrcode'">
          <label class="block mb-2">
            <span class="text-[11px] font-mono text-slate-600">Formato</span>
            <select v-model="selectedElement.barcodeFormat" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white">
              <option value="CODE128">CODE128</option>
              <option value="QR">QR</option>
            </select>
          </label>
          <label class="block mb-2"><span class="text-[11px] font-mono text-slate-600">Altura código (mm)</span>
            <input v-model.number="selectedElement.barcodeHeight" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
        </template>

        <button @click="removeElement(selectedIndex)" class="mt-2 w-full py-1 rounded border border-rose-200 text-rose-600 hover:bg-rose-50 text-[12px] font-mono">Eliminar elemento</button>
      </div>
      <div v-else class="p-3 text-[12px] font-mono text-slate-400">
        Selecciona un elemento del lienzo para editar sus propiedades.
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useToastStore } from '../../stores/toast'
import { useConfirm } from '../../composables/useConfirm'
import { extractError } from '../../utils/error'
import { labelTemplatesApi } from '../../api/labelTemplates'
import { SIZE_PRESETS, FIELDS, effectiveSize, defaultElement, resolveElementValue } from '../../utils/labelConfig'

const props = defineProps({
  type: { type: String, default: 'CARGO' },
  show: { type: Boolean, default: true },
})
const emit = defineEmits(['close', 'saved'])

const toast = useToastStore()
const { confirm } = useConfirm()

const typeLabel = computed(() => props.type === 'PALLET' ? 'Pallet' : 'Cargo')

const templates = ref([])
const currentId = ref(null)
const name = ref('')
const sizePreset = ref('4 x 6')
const widthInches = ref(4)
const heightInches = ref(6)
const orientation = ref('HORIZONTAL')
const dpi = ref(203)
const isDefault = ref(false)
const elements = ref([])
const selectedIndex = ref(-1)
const loadId = ref('')
const dirty = ref(false)
const saving = ref(false)

const eff = computed(() => effectiveSize({ widthInches: widthInches.value, heightInches: heightInches.value, orientation: orientation.value }))

const scale = computed(() => Math.min(4, 760 / Math.max(1, eff.value.w * 25.4)))
const canvasStyle = computed(() => ({
  width: Math.round(eff.value.w * 25.4 * scale.value) + 'px',
  height: Math.round(eff.value.h * 25.4 * scale.value) + 'px',
}))
const gridStyle = computed(() => {
  const step = 5 * scale.value
  return { backgroundImage: 'radial-gradient(circle, #cbd5e1 1px, transparent 1px)', backgroundSize: `${step}px ${step}px` }
})

function elementStyle(el) {
  const s = scale.value
  return {
    left: (el.x * s) + 'px',
    top: (el.y * s) + 'px',
    width: (el.w * s) + 'px',
    height: (el.type === 'line' ? 1 : el.h * s) + 'px',
  }
}
function textStyle(el) {
  return { fontSize: (el.fontSize * scale.value * 3.3) + 'px', fontWeight: el.bold ? 'bold' : 'normal', textAlign: el.align }
}

const selectedElement = computed(() => elements.value[selectedIndex.value] ?? null)
const isTextLike = computed(() => selectedElement.value?.type === 'text')
const isLine = computed(() => selectedElement.value?.type === 'line')

const tools = [
  { type: 'text', label: 'Texto' },
  { type: 'barcode', label: 'Código 128' },
  { type: 'qrcode', label: 'QR' },
  { type: 'line', label: 'Línea' },
  { type: 'rect', label: 'Rectángulo' },
]

function addElement(type) {
  elements.value.push(defaultElement(type, elements.value.length))
  selectedIndex.value = elements.value.length - 1
  dirty.value = true
}
function selectElement(i) { selectedIndex.value = i }
function removeElement(i) {
  elements.value.splice(i, 1)
  if (selectedIndex.value >= elements.value.length) selectedIndex.value = elements.value.length - 1
  dirty.value = true
}

function applySizePreset() {
  const p = SIZE_PRESETS.find(x => x.label === sizePreset.value)
  if (p) { widthInches.value = p.w; heightInches.value = p.h }
}

function newTemplate() {
  currentId.value = null
  name.value = ''
  sizePreset.value = '4 x 6'
  widthInches.value = 4
  heightInches.value = 6
  orientation.value = 'HORIZONTAL'
  dpi.value = 203
  isDefault.value = false
  elements.value = []
  selectedIndex.value = -1
  dirty.value = true
}

async function loadSelected() {
  if (!loadId.value) return
  const res = await labelTemplatesApi.get(loadId.value)
  applyDto(res.data)
}

function applyDto(t) {
  currentId.value = t.id
  name.value = t.name || ''
  widthInches.value = Number(t.widthInches || 4)
  heightInches.value = Number(t.heightInches || 6)
  orientation.value = t.orientation || 'HORIZONTAL'
  dpi.value = t.dpi || 203
  isDefault.value = !!t.isDefault
  const sizeMatch = SIZE_PRESETS.find(x => x.w === widthInches.value && x.h === heightInches.value)
  sizePreset.value = sizeMatch ? sizeMatch.label : 'custom'
  let parsed = []
  try { parsed = JSON.parse(t.configJson || '{"elements":[]}').elements || [] } catch {}
  elements.value = parsed
  selectedIndex.value = -1
  dirty.value = false
}

async function save() {
  if (!name.value) { toast.warning('Ingresa un nombre para la plantilla'); return }
  if (!elements.value.length) { toast.warning('La plantilla no tiene elementos'); return }
  const payload = {
    name: name.value,
    type: props.type,
    widthInches: widthInches.value,
    heightInches: heightInches.value,
    orientation: orientation.value,
    dpi: dpi.value,
    isDefault: isDefault.value,
    configJson: JSON.stringify({ elements: elements.value.map(el => ({ ...el })) }),
  }
  saving.value = true
  try {
    if (currentId.value) {
      await labelTemplatesApi.update(currentId.value, payload)
      toast.success('Plantilla actualizada')
    } else {
      const res = await labelTemplatesApi.create(payload)
      currentId.value = res.data?.id || currentId.value
      toast.success('Plantilla guardada')
    }
    dirty.value = false
    await refreshTemplates()
    emit('saved')
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!currentId.value) return
  if (!(await confirm({ message: `¿Eliminar la plantilla "${name.value}"?`, danger: true }))) return
  try {
    await labelTemplatesApi.remove(currentId.value)
    toast.success('Plantilla eliminada')
    newTemplate()
    await refreshTemplates()
    emit('saved')
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function refreshTemplates() {
  try {
    const res = await labelTemplatesApi.list(props.type)
    templates.value = res.data || []
  } catch (e) {
    console.warn('Failed to load templates', e)
  }
}

// ── Drag / resize ──
let dragState = null
function startDrag(i, e) {
  selectElement(i)
  const el = elements.value[i]
  dragState = { mode: 'drag', idx: i, startX: e.clientX, startY: e.clientY, origX: el.x, origY: el.y }
  document.addEventListener('pointermove', onPointerMove)
  document.addEventListener('pointerup', endPointer)
}
function startResize(i, e) {
  const el = elements.value[i]
  dragState = { mode: 'resize', idx: i, startX: e.clientX, startY: e.clientY, origW: el.w, origH: el.h }
  document.addEventListener('pointermove', onPointerMove)
  document.addEventListener('pointerup', endPointer)
}
function onPointerMove(e) {
  if (!dragState) return
  const dx = (e.clientX - dragState.startX) / scale.value
  const dy = (e.clientY - dragState.startY) / scale.value
  const el = elements.value[dragState.idx]
  if (dragState.mode === 'drag') {
    el.x = Math.max(0, Math.round((dragState.origX + dx) * 10) / 10)
    el.y = Math.max(0, Math.round((dragState.origY + dy) * 10) / 10)
  } else {
    el.w = Math.max(2, Math.round((dragState.origW + dx) * 10) / 10)
    el.h = Math.max(1, Math.round((dragState.origH + dy) * 10) / 10)
  }
  dirty.value = true
}
function endPointer() {
  dragState = null
  document.removeEventListener('pointermove', onPointerMove)
  document.removeEventListener('pointerup', endPointer)
}

function onKeydown(e) {
  if (!props.show) return
  if ((e.key === 'Delete' || e.key === 'Backspace') && selectedIndex.value >= 0 &&
      !/INPUT|SELECT|TEXTAREA/.test(e.target.tagName)) {
    removeElement(selectedIndex.value)
  }
}

onMounted(() => {
  refreshTemplates()
  newTemplate()
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  endPointer()
})
</script>
