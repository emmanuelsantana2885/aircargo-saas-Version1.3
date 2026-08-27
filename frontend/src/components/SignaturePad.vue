<template>
  <div class="signature-pad-wrapper">
    <div
      class="relative border border-slate-200 rounded overflow-hidden bg-white shadow-pencil-marine cursor-crosshair"
      :style="{ width: width + 'px', height: height + 'px' }"
      @mousedown="startDraw"
      @mousemove="draw"
      @mouseup="endDraw"
      @mouseleave="endDraw"
      @touchstart.prevent="startDrawTouch"
      @touchmove.prevent="drawTouch"
      @touchend="endDraw">
      <canvas
        ref="canvasRef"
        :width="width"
        :height="height"
        class="absolute inset-0" />
      <div
        v-if="isEmpty"
        class="absolute inset-0 flex items-center justify-center pointer-events-none">
        <span class="text-xs font-mono text-slate-300 uppercase tracking-widest">Firma aquí</span>
      </div>
    </div>
    <div class="flex justify-between items-center mt-1.5">
      <button
        @click="clear"
        class="text-[11px] font-mono text-slate-400 hover:text-slate-500 uppercase tracking-wider transition flex items-center gap-1">
        <component :is="icons.Trash" :size="11" :stroke-width="2" /> Limpiar
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useIcons } from '../composables/useIcons'

const icons = useIcons()

const props = defineProps({
  modelValue: { type: String, default: '' },
  width: { type: Number, default: 320 },
  height: { type: Number, default: 120 },
  lineWidth: { type: Number, default: 2 },
  lineColor: { type: String, default: '#0f172a' },
})

const emit = defineEmits(['update:modelValue'])

const canvasRef = ref(null)
const drawing = ref(false)
const isEmpty = ref(true)

let ctx = null

function getCtx() {
  if (!ctx && canvasRef.value) {
    ctx = canvasRef.value.getContext('2d')
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
  }
  return ctx
}

function startDraw(e) {
  drawing.value = true
  const c = getCtx()
  if (!c) return
  const rect = canvasRef.value.getBoundingClientRect()
  c.beginPath()
  c.moveTo(e.clientX - rect.left, e.clientY - rect.top)
}

function draw(e) {
  if (!drawing.value) return
  const c = getCtx()
  if (!c) return
  const rect = canvasRef.value.getBoundingClientRect()
  c.lineWidth = props.lineWidth
  c.strokeStyle = props.lineColor
  c.lineTo(e.clientX - rect.left, e.clientY - rect.top)
  c.stroke()
  isEmpty.value = false
}

function endDraw() {
  if (drawing.value) {
    drawing.value = false
    emit('update:modelValue', canvasRef.value.toDataURL())
  }
}

function startDrawTouch(e) {
  drawing.value = true
  const c = getCtx()
  if (!c) return
  const touch = e.touches[0]
  const rect = canvasRef.value.getBoundingClientRect()
  c.beginPath()
  c.moveTo(touch.clientX - rect.left, touch.clientY - rect.top)
}

function drawTouch(e) {
  if (!drawing.value) return
  const c = getCtx()
  if (!c) return
  const touch = e.touches[0]
  const rect = canvasRef.value.getBoundingClientRect()
  c.lineWidth = props.lineWidth
  c.strokeStyle = props.lineColor
  c.lineTo(touch.clientX - rect.left, touch.clientY - rect.top)
  c.stroke()
  isEmpty.value = false
}

function clear() {
  const c = getCtx()
  if (!c) return
  c.clearRect(0, 0, props.width, props.height)
  isEmpty.value = true
  emit('update:modelValue', '')
}

watch(() => props.modelValue, (val) => {
  if (!val && !isEmpty.value) {
    clear()
  } else if (val && isEmpty.value) {
    const img = new Image()
    img.onload = () => {
      const c = getCtx()
      if (!c) return
      c.clearRect(0, 0, props.width, props.height)
      c.drawImage(img, 0, 0)
      isEmpty.value = false
    }
    img.src = val
  }
})

onMounted(() => {
  getCtx()
  if (props.modelValue) {
    const img = new Image()
    img.onload = () => {
      const c = getCtx()
      if (!c) return
      c.drawImage(img, 0, 0)
      isEmpty.value = false
    }
    img.src = props.modelValue
  }
})
</script>
