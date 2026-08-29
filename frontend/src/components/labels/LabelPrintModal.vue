<template>
  <div class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm">
    <div class="bg-white rounded-xl shadow-2xl w-[560px] max-h-[90vh] flex flex-col border border-slate-300">
      <div class="flex items-center justify-between px-4 py-3 border-b border-slate-200 shrink-0">
        <div>
          <h3 class="text-[14px] font-black uppercase tracking-widest text-slate-950 font-mono">Imprimir {{ typeLabel }} Label</h3>
          <p class="text-[11px] font-mono text-slate-400 font-semibold">{{ items.length }} seleccionado(s)</p>
        </div>
        <button @click="$emit('close')" class="w-8 h-8 rounded hover:bg-slate-100 text-slate-500 text-[14px] font-mono">✕</button>
      </div>

      <div class="p-4 overflow-y-auto flex-1 space-y-4">
        <div v-if="!templates.length && loading" class="text-[13px] font-mono text-slate-400 text-center py-8">Cargando plantillas...</div>
        <div v-else-if="!templates.length" class="text-[13px] font-mono text-slate-400 text-center py-8">
          No hay plantillas {{ typeLabel }} configuradas.
          <div v-if="isSuperUser" class="mt-2">
            <button @click="openDesigner" class="px-3 py-1.5 rounded bg-slate-950 text-white text-[12px] font-mono font-bold">Crear plantilla</button>
          </div>
        </div>
        <template v-else>
          <div class="grid grid-cols-2 gap-3">
            <label class="block col-span-2">
              <span class="text-[11px] font-mono font-black text-slate-600 uppercase tracking-widest">Plantilla</span>
              <div class="flex gap-2 mt-1">
                <select v-model="templateId" class="flex-1 px-2 py-1.5 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white">
                  <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} {{ t.isDefault ? '(default)' : '' }}</option>
                </select>
                <button v-if="isSuperUser" @click="openDesigner" title="Editar plantillas" class="px-2.5 rounded border border-slate-300 hover:border-slate-950 text-slate-600 hover:text-slate-950 transition text-[14px]">&#9998;</button>
              </div>
            </label>
            <label class="block">
              <span class="text-[11px] font-mono font-black text-slate-600 uppercase tracking-widest">Formato</span>
              <select v-model="format" class="w-full mt-1 px-2 py-1.5 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white">
                <option value="PDF">PDF</option>
                <option value="ZPL">ZPL (Zebra)</option>
              </select>
            </label>
            <label class="block">
              <span class="text-[11px] font-mono font-black text-slate-600 uppercase tracking-widest">Copias</span>
              <input v-model.number="quantity" type="number" min="1" max="99" class="w-full mt-1 px-2 py-1.5 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
            </label>
          </div>

          <div v-if="currentTemplate">
            <div class="text-[11px] font-mono font-black text-slate-600 uppercase tracking-widest mb-1.5">Vista previa</div>
            <div class="bg-slate-100 rounded-lg p-4 flex justify-center overflow-auto">
              <div class="bg-white shadow border border-slate-300 relative" :style="previewCanvasStyle">
                <div v-for="el in previewElements" :key="el.id"
                  class="absolute"
                  :style="previewElementStyle(el)">
                  <span v-if="el.type === 'text'" class="font-mono" :style="previewTextStyle(el)">{{ resolveValue(el) }}</span>
                  <span v-else-if="el.type === 'line'" class="block" style="height:1px;background:#000"></span>
                  <span v-else-if="el.type === 'rect'" class="block" style="width:100%;height:100%;border:1px solid #000"></span>
                  <span v-else class="flex items-center justify-center text-[8px] font-mono text-slate-400 font-bold uppercase" style="width:100%;height:100%">
                    {{ el.type === 'qrcode' ? 'QR' : 'CODE128' }}
                  </span>
                </div>
              </div>
            </div>
            <p class="text-[10px] font-mono text-slate-400 mt-1">El código de barras/QR se genera al imprimir (ZPL/PDF).</p>
          </div>
        </template>
      </div>

      <div class="px-4 py-3 border-t border-slate-200 flex justify-end gap-2 shrink-0">
        <button @click="$emit('close')" class="ds-btn-secondary">Cancelar</button>
        <button @click="download" :disabled="!currentTemplate || downloading"
          class="ds-btn-primary disabled:opacity-50">
          {{ downloading ? 'Generando...' : format === 'ZPL' ? '&#128438; Descargar ZPL' : '&#11015; Descargar PDF' }}
        </button>
      </div>
    </div>

    <LabelDesignerModal v-if="designerOpen" :type="type" @close="designerOpen = false" @saved="reload" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useToastStore } from '../../stores/toast'
import { extractError } from '../../utils/error'
import { labelTemplatesApi, labelsApi } from '../../api/labelTemplates'
import { effectiveSize, resolveElementValue } from '../../utils/labelConfig'
import LabelDesignerModal from './LabelDesignerModal.vue'

const props = defineProps({
  type: { type: String, default: 'CARGO' },
  items: { type: Array, default: () => [] },
  show: { type: Boolean, default: true },
})
defineEmits(['close'])
const toast = useToastStore()
const isSuperUser = computed(() => (localStorage.getItem('aircargo_auth') ? JSON.parse(localStorage.getItem('aircargo_auth')).role === 'SUPER_USER' : false))

const typeLabel = computed(() => props.type === 'PALLET' ? 'Pallet' : 'Cargo')
const templates = ref([])
const loading = ref(false)
const templateId = ref('')
const format = ref('PDF')
const quantity = ref(1)
const downloading = ref(false)
const designerOpen = ref(false)
function openDesigner() {
  designerOpen.value = true
}

const currentTemplate = computed(() => templates.value.find(t => t.id === templateId.value) || null)

const previewElements = computed(() => {
  const t = currentTemplate.value
  if (!t) return []
  try { return JSON.parse(t.configJson || '{"elements":[]}').elements || [] } catch { return [] }
})

const previewScale = computed(() => {
  if (!currentTemplate.value) return 1
  const eff = effectiveSize(currentTemplate.value)
  return Math.min(2.5, 380 / Math.max(1, eff.w * 25.4))
})

const previewCanvasStyle = computed(() => {
  const eff = effectiveSize(currentTemplate.value)
  const sc = previewScale.value
  return { width: Math.round(eff.w * 25.4 * sc) + 'px', height: Math.round(eff.h * 25.4 * sc) + 'px' }
})

function previewElementStyle(el) {
  const sc = previewScale.value
  return {
    left: (el.x * sc) + 'px',
    top: (el.y * sc) + 'px',
    width: (el.w * sc) + 'px',
    height: (el.type === 'line' ? 1 : el.h * sc) + 'px',
  }
}
function previewTextStyle(el) {
  const sc = previewScale.value
  return { fontSize: (el.fontSize * sc * 3.3) + 'px', fontWeight: el.bold ? 'bold' : 'normal', textAlign: el.align }
}

function resolveValue(el) {
  if (el.dataSource === 'TEXT' || !el.dataSource) return el.text || ''
  return resolveElementValue(el, props.type)
}

async function load() {
  loading.value = true
  try {
    const res = await labelTemplatesApi.list(props.type)
    templates.value = res.data || []
    if (templateId.value && templates.value.some(t => t.id === templateId.value)) return
    const def = templates.value.find(t => t.isDefault) || templates.value[0]
    templateId.value = def?.id || ''
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    loading.value = false
  }
}

async function reload() {
  designerOpen.value = false
  await load()
}

async function download() {
  if (!currentTemplate.value) return
  if (!props.items.length) { toast.warning('No hay elementos seleccionados'); return }
  const payload = {
    templateId: currentTemplate.value.id,
    format: format.value,
    ids: props.items,
    quantity: quantity.value,
  }
  downloading.value = true
  try {
    if (props.type === 'PALLET') await labelsApi.downloadPallet(payload)
    else await labelsApi.downloadCargo(payload)
    toast.success('Etiquetas generadas')
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    downloading.value = false
  }
}

watch(() => props.show, (v) => { if (v) load() })

onMounted(load)
</script>
