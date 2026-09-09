<template>
  <div v-if="active" class="border-2 border-dashed rounded-lg p-4 mb-4 transition-all duration-300"
       :class="flashClass">
    <!-- Header -->
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-2">
        <div class="w-2.5 h-2.5 rounded-full animate-pulse" :class="scanning ? 'bg-emerald-500' : 'bg-slate-300'"></div>
        <span class="text-[13px] font-bold text-slate-900 uppercase tracking-wider">
          Modo Scan — ULD: {{ uldNumber }}
        </span>
        <span v-if="lastResult" class="text-[12px] font-bold px-2 py-0.5 rounded-full"
              :class="lastResult.success ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'">
          {{ lastResult.message || lastResult.error }}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <button @click="$emit('exit-scan')" class="text-[12px] font-bold text-slate-500 hover:text-red-600 uppercase px-2 py-1 rounded hover:bg-red-50">
          ✕ Salir
        </button>
      </div>
    </div>

    <!-- Info for unsaved ULD -->
    <div v-if="!uldId" class="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3 flex items-center gap-2">
      <span class="text-amber-600 text-sm">⚡</span>
      <span class="text-[12px] font-bold text-amber-800 uppercase tracking-wide">
        Escanee un código ULD (PMC-XXXXX) para crearlo automáticamente, luego escanee piezas MAWB.
      </span>
    </div>

    <!-- Scan Input -->
    <div class="relative mb-3">
      <input
        ref="scanInput"
        v-model="scanCode"
        @keydown.enter.prevent="processScan"
        type="text"
        placeholder="Escanee código de barras o escriba MAWB/ULD..."
        class="w-full bg-white border-2 border-slate-300 rounded-lg px-4 py-3 text-[15px] font-mono font-bold text-slate-900
               focus:outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200
               placeholder:text-slate-300 placeholder:font-sans placeholder:font-normal placeholder:text-[14px]"
        :disabled="processing"
      />
      <div v-if="processing" class="absolute right-3 top-1/2 -translate-y-1/2">
        <div class="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
      </div>
    </div>

    <!-- Scan History -->
    <div v-if="history.length" class="max-h-32 overflow-y-auto space-y-1 mb-2">
      <div v-for="(entry, i) in history" :key="i"
           class="flex items-center gap-2 text-[12px] py-1 px-2 rounded"
           :class="entry.warning ? 'bg-amber-50 text-amber-700' : entry.success ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'">
        <span>{{ entry.success ? (entry.warning ? '⚠' : '✓') : '✗' }}</span>
        <span class="font-bold">{{ entry.awbNumber }}</span>
        <span v-if="entry.pieceNumber">Pieza #{{ entry.pieceNumber }}</span>
        <span v-if="entry.totalOnUld">({{ entry.totalOnUld }}/{{ entry.maxAllowed }})</span>
        <span v-if="entry.warning" class="ml-1 text-amber-600 italic truncate max-w-[120px]" :title="entry.warning">sin Booking</span>
        <span class="ml-auto opacity-60">{{ entry.time }}</span>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-2 mt-2">
      <button @click="undoLast" :disabled="!canUndo"
              class="text-[12px] font-bold px-3 py-1.5 rounded border transition-colors"
              :class="canUndo ? 'border-slate-300 text-slate-600 hover:bg-slate-100' : 'border-slate-200 text-slate-300 cursor-not-allowed'">
        ↩ Deshacer Último
      </button>
      <button @click="openCamera"
              class="text-[12px] font-bold px-3 py-1.5 rounded border border-blue-300 text-blue-600 hover:bg-blue-50 transition-colors ml-auto">
        📷 Cámara
      </button>
    </div>

    <!-- Camera Modal -->
    <div v-if="showCamera" class="fixed inset-0 bg-black/60 z-50 flex items-center justify-center" @click.self="closeCamera">
      <div class="bg-white rounded-xl p-6 max-w-lg w-full mx-4 shadow-2xl">
        <div class="flex items-center justify-between mb-4">
          <span class="text-[14px] font-bold text-slate-900 uppercase">Escanear con Cámara</span>
          <button @click="closeCamera" class="text-slate-400 hover:text-red-500 text-lg font-bold">✕</button>
        </div>
        <div class="relative w-full rounded-lg overflow-hidden border border-slate-200 bg-black" style="min-height: 280px;">
          <video ref="cameraVideo" autoplay playsinline muted class="w-full h-full object-contain" style="min-height: 280px;"></video>
          <div v-if="!cameraReady && !cameraError" class="absolute inset-0 flex items-center justify-center bg-black/50 text-white text-[13px] font-bold z-10">
            Iniciando cámara...
          </div>
          <div v-if="cameraError" class="absolute inset-0 flex flex-col items-center justify-center bg-black/70 text-white text-[12px] font-bold z-10 px-4 text-center">
            <span>{{ cameraError }}</span>
            <button @click="retryCamera"
              class="mt-3 text-[12px] font-bold px-3 py-1.5 rounded border border-white/60 text-white hover:bg-white/10 transition">
              ↻ Reintentar
            </button>
          </div>
          <div v-if="cameraReady && !lastDetect" class="absolute bottom-2 left-2 bg-black/60 text-white text-[11px] px-2 py-1 rounded z-10">
            Enfrente el código de barras
          </div>
        </div>
        <p class="text-[12px] text-slate-400 mt-3 text-center">Apunte la cámara al código de barras de la pieza</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, onBeforeUnmount } from 'vue'
import { scanApi } from '@/api/scan.js'
import { uldTypeCatalogApi } from '@/api/uldTypeCatalog'

const props = defineProps({
  active: { type: Boolean, default: false },
  uldId: { type: String, default: '' },
  uldNumber: { type: String, default: 'NUEVO' },
})

const emit = defineEmits(['piece-added', 'piece-removed', 'exit-scan', 'update-mawb-pieces', 'ULD_FOUND', 'uld-number-scanned'])

const scanInput = ref(null)
const scanCode = ref('')
const processing = ref(false)
const scanning = ref(false)
const lastResult = ref(null)
const flashClass = ref('')
const history = ref([])
const showCamera = ref(false)
const cameraVideo = ref(null)
const cameraReady = ref(false)
const cameraError = ref('')
const lastDetect = ref(false)
const canUndo = computed(() => history.value.length > 0 && history.value[0].success)

// Tipos ULD desde el catálogo dinámico (normas IATA); fallback a lista legacy
const LEGACY_ULD_TYPES = ['PMC', 'PAH', 'PAG', 'PAJ', 'AAY', 'AAZ', 'AAD', 'PIP', 'AMP', 'AMJ', 'PMH']
const uldCodes = ref([...LEGACY_ULD_TYPES])
const uldCodeRe = computed(() => new RegExp(`^(${uldCodes.value.join('|')})[-\\s]?[A-Z0-9]{4,}`, 'i'))
uldTypeCatalogApi.getAll(true)
  .then(res => {
    const codes = (res.data || []).map(x => x.code).filter(Boolean)
    if (codes.length) uldCodes.value = codes
  })
  .catch(() => {})

let cameraStream = null
let detectTimer = null
let scanLoop = null
let lastDetectedCode = ''
let lastDetectedAt = 0

// Auto-focus when activated
watch(() => props.active, async (val) => {
  if (val) {
    await nextTick()
    scanInput.value?.focus()
  } else {
    closeCamera()
  }
})

// Clean up on unmount
onBeforeUnmount(() => {
  closeCamera()
})

async function processScan(codeOverride) {
  const code = codeOverride || scanCode.value?.trim()
  if (!code || processing.value) return

  processing.value = true
  scanning.value = true

  try {
    const isUld = uldCodeRe.value.test(code)

    if (isUld && !props.uldId) {
      lastResult.value = { success: true, message: `ULD ${code} asignado`, awbNumber: code }
      flash('emerald')
      emit('uld-number-scanned', code)
    } else if (!isUld && !props.uldId) {
      lastResult.value = { success: false, error: 'Escanee un código ULD (PMC-XXXXX) antes de registrar piezas', awbNumber: code, time: 'ahora' }
      history.value.unshift(lastResult.value)
      flash('red')
    } else {
      const res = await scanApi.piece({
        uldId: props.uldId,
        awbNumber: code,
        source: 'BARCODE',
      })

      const entry = { awbNumber: code, time: 'ahora' }
      if (res.data.success) {
        Object.assign(entry, {
          success: true,
          message: `Pieza #${res.data.pieceNumber} — ${res.data.awbNumber}`,
          pieceNumber: res.data.pieceNumber,
          mawbId: res.data.mawbId,
          totalOnUld: res.data.totalOnUld,
          maxAllowed: res.data.totalOnUld + res.data.availablePieces,
          warning: res.data.warning || null,
        })
        flash(res.data.warning ? 'amber' : 'emerald')
        emit('piece-added', res.data)
      } else {
        Object.assign(entry, { success: false, error: res.data.error })
        flash('red')
      }
      lastResult.value = entry
      history.value.unshift(entry)
      if (history.value.length > 10) history.value.pop()
    }
  } catch (e) {
    const msg = e.response?.data?.error || e.message || 'Error de conexión'
    const entry = { success: false, error: msg, awbNumber: code, time: 'ahora' }
    lastResult.value = entry
    history.value.unshift(entry)
    if (history.value.length > 10) history.value.pop()
    flash('red')
  } finally {
    processing.value = false
    scanning.value = false
    scanCode.value = ''
    await nextTick()
    scanInput.value?.focus()
  }
}

async function undoLast() {
  if (!canUndo.value) return
  const last = history.value[0]
  try {
    await scanApi.undoLast(props.uldId, last.mawbId)
    history.value.shift()
    lastResult.value = { success: true, message: 'Última pieza eliminada', awbNumber: last.awbNumber }
    flash('amber')
    emit('piece-removed', { awbNumber: last.awbNumber, pieceNumber: last.pieceNumber })
  } catch {
    lastResult.value = { success: false, error: 'No se pudo deshacer', awbNumber: last.awbNumber }
  }
}

async function openCamera() {
  showCamera.value = true
  cameraReady.value = false
  cameraError.value = ''
  lastDetect.value = false
  await nextTick()

  const video = cameraVideo.value
  if (!video) { closeCamera(); return }

  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = 'Cámara no disponible. Usa HTTPS o un navegador con cámara.'
    return
  }

  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } },
      audio: false,
    })

    // Si el modal se cerró mientras resolvía, no colgar el stream
    if (!showCamera.value) {
      stopStream()
      return
    }

    video.srcObject = cameraStream
    await video.play()
    cameraReady.value = true

    // Espera a que haya un frame real antes de arrancar los detectores
    await waitForFrame(video)

    // BarcodeDetector (Chrome/Edge)
    if ('BarcodeDetector' in window) {
      try {
        await BarcodeDetector.getSupportedFormats()
        const bd = new BarcodeDetector({
          formats: ['code_128', 'code_39', 'code_39_vin', 'ean_13', 'ean_8', 'upc_a', 'upc_e', 'codabar', 'i2of5'],
        })
        detectTimer = setInterval(async () => {
          if (!cameraStream?.active || processing.value || !video.videoWidth) return
          try {
            const codes = await bd.detect(video)
            if (codes.length > 0 && shouldScan(codes[0].rawValue)) {
              lastDetect.value = true
              processScan(markScanned(codes[0].rawValue))
            }
          } catch {}
        }, 250)
        return
      } catch {}
    }

    // ZBar fallback (Firefox etc.)
    const { scanImageData } = await import('@undecaf/zbar-wasm')

    const offscreen = document.createElement('canvas')
    const ctx = offscreen.getContext('2d')

    scanLoop = setInterval(async () => {
      if (!cameraStream?.active || processing.value || !video.videoWidth) return
      try {
        offscreen.width = video.videoWidth || 640
        offscreen.height = video.videoHeight || 480
        ctx.drawImage(video, 0, 0)
        const id = ctx.getImageData(0, 0, offscreen.width, offscreen.height)
        const symbols = await scanImageData(id)
        if (symbols.length > 0) {
          const code = symbols[0].decode()
          if (code && shouldScan(code)) {
            lastDetect.value = true
            processScan(markScanned(code))
          }
        }
      } catch (e) {
        if (e?.message === 'index out of bounds') return
        console.warn('[Scan] ZBar error:', e)
      }
    }, 400)
  } catch (err) {
    console.error('[Scan] Camera error:', err)
    stopStream()
    cameraReady.value = false
    if (err?.name === 'NotAllowedError' || err?.name === 'SecurityError') {
      cameraError.value = 'Permiso de cámara denegado. Permite el acceso en la configuración del navegador.'
    } else if (err?.name === 'NotFoundError' || err?.name === 'DevicesNotFoundError') {
      cameraError.value = 'No se encontró una cámara disponible.'
    } else if (err?.name === 'NotReadableError') {
      cameraError.value = 'La cámara está en uso por otra aplicación. Ciérrala e intenta de nuevo.'
    } else {
      cameraError.value = 'Error de cámara: ' + (err?.message || err)
    }
  }
}

function waitForFrame(video, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const check = () => {
      if (video.videoWidth > 0 && video.videoHeight > 0) {
        resolve()
        return
      }
      if (Date.now() - start > timeoutMs) {
        reject(new Error('No se recibieron frames de la cámara'))
        return
      }
      setTimeout(check, 50)
    }
    check()
  })
}

// Evita re-disparar el mismo código mientras sigue enfrente (ventana de 3s)
function shouldScan(code) {
  if (!code || processing.value) return false
  if (code === lastDetectedCode && Date.now() - lastDetectedAt < 3000) return false
  return true
}

function markScanned(code) {
  lastDetectedCode = code
  lastDetectedAt = Date.now()
  return code
}

async function retryCamera() {
  cameraError.value = ''
  cameraReady.value = false
  stopStream()
  openCamera()
}

function stopStream() {
  if (cameraStream) {
    cameraStream.getTracks().forEach(t => t.stop())
    cameraStream = null
  }
}

async function closeCamera() {
  if (detectTimer) { clearInterval(detectTimer); detectTimer = null }
  if (scanLoop) { clearInterval(scanLoop); scanLoop = null }
  stopStream()
  cameraReady.value = false
  cameraError.value = ''
  lastDetect.value = false
  showCamera.value = false
}

const FLASH_CLASSES = {
  emerald: 'border-emerald-400 bg-emerald-50',
  green: 'border-green-400 bg-green-50',
  amber: 'border-amber-400 bg-amber-50',
  red: 'border-red-400 bg-red-50',
}

function flash(color) {
  flashClass.value = FLASH_CLASSES[color] || ''
  setTimeout(() => { flashClass.value = '' }, 600)
}
</script>
