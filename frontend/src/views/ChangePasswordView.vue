<template>
  <div class="min-h-screen flex items-center justify-center p-3 md:p-5" style="background: var(--bg)">
    <div class="w-full max-w-sm p-6 md:p-8 rounded-lg shadow-md" style="background: var(--surface); border: 1px solid var(--border)">
      <!-- Header -->
      <div class="text-center mb-6">
        <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
          <component :is="icons.Lock" :size="28" color="white" :stroke-width="2" />
        </div>
        <h1 class="text-xl font-bold" style="color: var(--text)">
          {{ mustChange ? 'Cambiar contraseña requerido' : 'Cambiar contraseña' }}
        </h1>
        <p class="text-sm mt-1" style="color: var(--muted)">
          {{ mustChange
            ? 'Debes establecer una nueva contraseña para continuar'
            : 'Ingresa tu contraseña actual y la nueva contraseña'
          }}
        </p>
      </div>

      <!-- No MFA warning -->
      <div v-if="!auth.mfaEnabled && !hasMfaSetup" class="mb-4 p-4 rounded-lg" style="background: #fef3c7; border: 1px solid #f59e0b">
        <p class="text-xs font-semibold" style="color: #92400e">Autenticación de dos factores requerida</p>
        <p class="text-xs mt-1" style="color: #92400e">
          Antes de cambiar tu contraseña, debes configurar la autenticación de dos factores.
        </p>
        <button
          @click="router.push('/mfa-setup')"
          class="mt-3 px-4 py-2 rounded text-xs font-semibold transition-all hover:brightness-110"
          style="background: #f59e0b; color: white"
        >
          Configurar MFA
        </button>
      </div>

      <!-- Password form -->
      <form v-if="auth.mfaEnabled || hasMfaSetup" @submit.prevent="handleChangePassword" class="space-y-4">
        <!-- Current password (only if not forced change) -->
        <div v-if="!mustChange">
          <label class="block text-xs font-medium mb-1" style="color: var(--text)">Contraseña actual</label>
          <input
            v-model="currentPassword"
            type="password"
            required
            placeholder="••••••••"
            class="w-full px-3 py-2.5 rounded text-base md:text-sm outline-none transition-all border-slate-300"
            style="background: var(--bg); color: var(--text)"
            :disabled="saving"
          />
        </div>

        <!-- TOTP Code -->
        <div>
          <label class="block text-xs font-medium mb-1" style="color: var(--text)">Código de autenticación (TOTP)</label>
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

        <!-- New password -->
        <div>
          <label class="block text-xs font-medium mb-1" style="color: var(--text)">Nueva contraseña</label>
          <input
            v-model="newPassword"
            type="password"
            required
            minlength="12"
            placeholder="Mínimo 12 caracteres"
            class="w-full px-3 py-2.5 rounded text-base md:text-sm outline-none transition-all border-slate-300"
            style="background: var(--bg); color: var(--text)"
            :disabled="saving"
          />
          <ul v-if="newPassword" class="mt-2 space-y-1">
            <li
              v-for="rule in passwordRules"
              :key="rule.key"
              class="flex items-center gap-1.5 text-[11px]"
              :style="{ color: rule.met ? '#16a34a' : 'var(--muted)' }"
            >
              <span>{{ rule.met ? '✓' : '○' }}</span>
              <span>{{ rule.label }}</span>
            </li>
          </ul>
        </div>

        <!-- Confirm password -->
        <div>
          <label class="block text-xs font-medium mb-1" style="color: var(--text)">Confirmar contraseña</label>
          <input
            v-model="confirmPassword"
            type="password"
            required
            minlength="12"
            placeholder="Repite la contraseña"
            class="w-full px-3 py-2.5 rounded text-base md:text-sm outline-none transition-all border-slate-300"
            style="background: var(--bg); color: var(--text)"
            :disabled="saving"
          />
        </div>

        <!-- Error -->
        <p v-if="errorMsg" class="text-xs text-center" style="color: #dc2626">{{ errorMsg }}</p>
        <p v-if="successMsg" class="text-xs text-center" style="color: #16a34a">{{ successMsg }}</p>

        <!-- Submit -->
        <button
          type="submit"
          :disabled="saving || !canSubmit"
          class="w-full py-2.5 rounded text-sm font-semibold transition-all"
          :class="saving ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
          style="background: var(--accent); color: white"
        >
          <span v-if="saving" class="inline-flex items-center gap-2">
            <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            Guardando...
          </span>
          <span v-else>Cambiar contraseña</span>
        </button>

        <p v-if="!mustChange" class="text-xs text-center">
          <router-link to="/" style="color: var(--accent)" class="underline">
            Volver al inicio
          </router-link>
        </p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'
import { useIcons } from '../composables/useIcons'
import { useToastStore } from '../stores/toast'

const icons = useIcons()
import { extractError } from '../utils/error'
import { checkPasswordStrength, isStrongPassword, passwordRuleLabels } from '../utils/password'

const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const totpCode = ref('')
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const mustChange = computed(() => auth.mustChangePassword)
const hasMfaSetup = ref(false)

const canSubmit = computed(() => {
  if (!isStrongPassword(newPassword.value)) return false
  if (newPassword.value !== confirmPassword.value) return false
  if (totpCode.value.length !== 6) return false
  if (!mustChange.value && !currentPassword.value) return false
  return true
})

const passwordRules = computed(() => {
  const strength = checkPasswordStrength(newPassword.value)
  return passwordRuleLabels.map(r => ({ key: r.key, label: r.label, met: strength[r.key] }))
})

onMounted(async () => {
  // Check if user has MFA set up
  try {
    const res = await authApi.me()
    hasMfaSetup.value = res.data.mfaEnabled === true
  } catch (e) {
    console.warn('Failed to check MFA status:', e)
  }
})

async function handleChangePassword() {
  errorMsg.value = ''
  successMsg.value = ''

  if (newPassword.value !== confirmPassword.value) {
    errorMsg.value = 'Las contraseñas no coinciden'
    return
  }

  if (!isStrongPassword(newPassword.value)) {
    errorMsg.value = 'La contraseña debe tener al menos 12 caracteres e incluir mayúsculas, minúsculas, números y caracteres especiales.'
    return
  }

  saving.value = true
  try {
    const res = await authApi.changePassword(
      newPassword.value,
      mustChange.value ? undefined : currentPassword.value,
      totpCode.value
    )

    // Update token if returned
    if (res.data.token) {
      // el nuevo token se recibe como cookie httpOnly; nada que guardar en JS
      auth.mustChangePassword = false
      auth.persist()
    }

    successMsg.value = 'Contraseña cambiada correctamente'

    setTimeout(() => {
      router.push('/')
    }, 1500)
  } catch (e) {
    toast.error(extractError(e))
    const status = e.response?.status
    const data = e.response?.data
    if (status === 401) errorMsg.value = data?.error || 'Código inválido o contraseña incorrecta'
    else if (status === 400) errorMsg.value = data?.error || 'Debes configurar MFA primero'
    else errorMsg.value = 'Error al cambiar la contraseña'
  } finally {
    saving.value = false
  }
}
</script>
