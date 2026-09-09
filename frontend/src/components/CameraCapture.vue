<template>
  <Teleport to="body">
    <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" @click.self="close">
      <div class="bg-white rounded-lg shadow-2xl overflow-hidden mx-4" style="max-width: 520px; width: 100%;">
        <div class="flex items-center justify-between px-4 py-2.5 border-b border-slate-200">
          <span class="text-xs font-mono font-bold uppercase tracking-widest text-slate-700">Capturar Foto</span>
          <button @click="close" class="text-slate-400 hover:text-slate-700 transition text-sm">✕</button>
        </div>
        <div class="relative bg-black min-h-[240px] flex items-center justify-center">
          <video v-show="streamReady" ref="videoRef" autoplay playsinline muted class="w-full max-h-[360px] object-contain" />
          <div v-if="!streamReady && !errorMsg" class="text-xs font-mono text-slate-400 uppercase tracking-widest">Iniciando cámara...</div>
          <div v-if="errorMsg" class="text-xs font-mono text-slate-500 px-4 text-center break-words">{{ errorMsg }}</div>
        </div>
        <div class="flex justify-between items-center px-4 py-3 border-t border-slate-200 bg-slate-50">
          <button @click="close" class="text-xs px-3 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider font-bold text-slate-600 hover:bg-white transition">
            Cancelar
          </button>
          <div class="flex gap-2">
            <button v-if="errorMsg" @click="startCamera"
              class="flex items-center gap-1.5 text-xs px-4 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-slate-700 bg-white border border-slate-300 hover:bg-slate-100 transition active:scale-95">
              ↻ Reintentar
            </button>
            <button v-if="streamReady" @click="capture"
              class="flex items-center gap-1.5 text-xs px-4 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-white bg-slate-950 hover:bg-slate-800 transition active:scale-95">
              <component :is="icons.Camera" :size="13" :stroke-width="2.5" /> Capturar
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { useIcons } from '../composables/useIcons'

const icons = useIcons()

const props = defineProps({
  show: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'captured'])

const videoRef = ref(null)
const streamReady = ref(false)
const errorMsg = ref('')

let mediaStream = null

async function startCamera() {
  streamReady.value = false
  errorMsg.value = ''
  if (!navigator.mediaDevices?.getUserMedia) {
    errorMsg.value = 'Cámara no disponible en este navegador. Usa HTTPS o un dispositivo con cámara.'
    return
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    })
    // Si el modal se cerró mientras resolvía, no colgar el stream
    if (!props.show) {
      stream.getTracks().forEach(t => t.stop())
      return
    }
    mediaStream = stream
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
      streamReady.value = true
    }
  } catch (e) {
    if (e?.name === 'NotAllowedError' || e?.name === 'SecurityError') {
      errorMsg.value = 'Permiso de cámara denegado. Permite el acceso en la configuración del navegador.'
    } else if (e?.name === 'NotFoundError' || e?.name === 'DevicesNotFoundError') {
      errorMsg.value = 'No se encontró una cámara disponible.'
    } else if (e?.name === 'NotReadableError') {
      errorMsg.value = 'La cámara está en uso por otra aplicación. Ciérrala e intenta de nuevo.'
    } else {
      errorMsg.value = 'Error al acceder a la cámara: ' + (e?.message || e)
    }
  }
}

function stopCamera() {
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop())
    mediaStream = null
  }
  streamReady.value = false
}

function capture() {
  const video = videoRef.value
  if (!video) return
  // No capturar hasta que haya un frame real decodificado (evita canvas 0×0 / imagen vacía)
  if (video.readyState < 2 || !video.videoWidth || !video.videoHeight) {
    errorMsg.value = 'La cámara aún no está lista. Espera un instante e intenta de nuevo.'
    return
  }
  try {
    const canvas = document.createElement('canvas')
    const MAX_DIM = 720
    let w = video.videoWidth
    let h = video.videoHeight
    if (w > MAX_DIM || h > MAX_DIM) {
      const scale = Math.min(MAX_DIM / w, MAX_DIM / h, 1)
      w = Math.round(w * scale)
      h = Math.round(h * scale)
    }
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0, w, h)
    const dataUrl = canvas.toDataURL('image/jpeg', 0.4)
    if (!dataUrl || dataUrl === 'data:,') {
      errorMsg.value = 'No se pudo procesar la imagen. Intenta de nuevo.'
      return
    }
    emit('captured', dataUrl)
    close()
  } catch (e) {
    errorMsg.value = 'Error al capturar la imagen: ' + (e?.message || e)
  }
}

function close() {
  stopCamera()
  emit('close')
}

watch(() => props.show, async (val) => {
  if (val) {
    await nextTick()
    startCamera()
  } else {
    stopCamera()
  }
})

onUnmounted(() => {
  stopCamera()
})
</script>
