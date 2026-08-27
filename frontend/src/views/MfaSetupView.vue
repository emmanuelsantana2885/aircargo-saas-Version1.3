<template>
  <div class="min-h-screen flex items-center justify-center p-3 md:p-5" style="background: var(--bg)">
    <div class="w-full max-w-sm p-6 md:p-8 rounded-lg shadow-md" style="background: var(--surface); border: 1px solid var(--border)">
      <!-- Header -->
      <div class="text-center mb-6">
        <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
          <component :is="icons.ShieldLock" :size="28" color="white" :stroke-width="2" />
        </div>
        <h1 class="text-xl font-bold" style="color: var(--text)">Configurar autenticación MFA</h1>
        <p class="text-sm mt-1" style="color: var(--muted)">Escanea el código QR con tu aplicación de autenticación</p>
      </div>

      <!-- Step 1: Show QR -->
      <div v-if="step === 'qr'" class="space-y-4">
        <div v-if="loading" class="text-center py-8">
          <span class="w-6 h-6 border-2 border-slate-300 border-t-black rounded-full animate-spin inline-block"></span>
          <p class="text-xs mt-2" style="color: var(--muted)">Generando código QR...</p>
        </div>

        <div v-else-if="qrUrl" class="flex flex-col items-center">
          <!-- QR Code rendered via image URL from otpauth:// -->
          <div class="bg-white p-4 rounded-lg border border-slate-200 mb-3">
            <img :src="qrImage" alt="QR Code" class="w-48 h-48" />
          </div>

          <p class="text-xs text-center mb-2" style="color: var(--muted)">
            Si no puedes escanear, ingresa este código manualmente:
          </p>
          <div class="bg-slate-100 rounded px-3 py-2 text-center">
            <code class="text-xs font-mono break-all" style="color: var(--text)">{{ secret }}</code>
          </div>

          <button
            @click="step = 'verify'"
            class="mt-4 w-full py-2.5 rounded text-sm font-semibold transition-all hover:brightness-110 active:scale-[0.98]"
            style="background: var(--accent); color: white"
          >
            Ya escaneé el código
          </button>
        </div>

        <p v-if="errorMsg" class="text-xs text-center" style="color: #dc2626">{{ errorMsg }}</p>
      </div>

      <!-- Step 2: Verify TOTP -->
      <div v-if="step === 'verify'" class="space-y-4">
        <p class="text-xs text-center" style="color: var(--muted)">
          Ingresa el código de 6 dígitos de tu aplicación de autenticación
        </p>

        <div>
          <input
            v-model="totpCode"
            type="text"
            inputmode="numeric"
            pattern="[0-9]*"
            maxlength="6"
            required
            placeholder="000000"
            class="w-full px-3 py-2.5 rounded text-center font-mono tracking-[0.5em] outline-none transition-all border-slate-300"
            style="background: var(--bg); color: var(--text); font-size: 18px"
            :disabled="saving"
            autofocus
          />
        </div>

        <button
          @click="handleVerify"
          :disabled="saving || totpCode.length !== 6"
          class="w-full py-2.5 rounded text-sm font-semibold transition-all"
          :class="saving ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
          style="background: var(--accent); color: white"
        >
          <span v-if="saving" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            Verificando...
          </span>
          <span v-else>Habilitar MFA</span>
        </button>

        <p v-if="errorMsg" class="text-xs text-center" style="color: #dc2626">{{ errorMsg }}</p>

        <button
          @click="step = 'qr'"
          class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
          style="background: transparent; color: var(--muted)"
        >
          Volver al QR
        </button>
      </div>

      <!-- Step 3: Success -->
      <div v-if="step === 'success'" class="text-center space-y-4">
        <div class="w-12 h-12 rounded-full flex items-center justify-center mx-auto" style="background: #dcfce7">
          <component :is="icons.Check" :size="24" color="#16a34a" :stroke-width="2" />
        </div>
        <h2 class="text-lg font-bold" style="color: var(--text)">MFA habilitado correctamente</h2>
        <p class="text-xs" style="color: var(--muted)">
          Ya puedes usar tu aplicación de autenticación para iniciar sesión
        </p>
        <button
          @click="router.push('/change-password')"
          class="w-full py-2.5 rounded text-sm font-semibold transition-all hover:brightness-110 active:scale-[0.98]"
          style="background: var(--accent); color: white"
        >
          Continuar al cambio de contraseña
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'
import { useIcons } from '../composables/useIcons'
import { useToastStore } from '../stores/toast'

const icons = useIcons()
import { extractError } from '../utils/error'

const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()

const step = ref('qr')
const loading = ref(true)
const saving = ref(false)
const errorMsg = ref('')
const secret = ref('')
const qrUrl = ref('')
const totpCode = ref('')

// Generate QR image URL using a third-party service
const qrImage = ref('')

onMounted(async () => {
  try {
    const res = await authApi.mfaSetup()
    secret.value = res.data.secret
    qrUrl.value = res.data.otpAuthUrl
    // Use Google Charts API for QR (no library needed)
    qrImage.value = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrUrl.value)}`
  } catch (e) {
    errorMsg.value = extractError(e)
    toast.error(extractError(e))
  } finally {
    loading.value = false
  }
})

async function handleVerify() {
  errorMsg.value = ''
  saving.value = true
  try {
    await authApi.mfaEnable(secret.value, totpCode.value)
    auth.mfaEnabled = true
    auth.persist()
    step.value = 'success'
  } catch (e) {
    toast.error(extractError(e))
    const data = e.response?.data
    errorMsg.value = data?.error || 'Código inválido. Intenta de nuevo.'
    totpCode.value = ''
  } finally {
    saving.value = false
  }
}
</script>
