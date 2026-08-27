<template>
  <Teleport to="body">
    <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="$emit('close')">
      <div class="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 overflow-hidden">
        <div class="flex items-center justify-between px-6 py-4 border-b border-slate-200">
          <h3 class="text-[15px] font-black text-slate-950 uppercase tracking-wide font-mono">{{ t('passwordChange.title') }}</h3>
          <button @click="$emit('close')" class="text-slate-400 hover:text-slate-600 transition-colors">
            <component :is="icons.X" :size="18" />
          </button>
        </div>
        <div class="px-6 py-5 space-y-4">
          <div v-if="!auth.mustChangePassword">
            <label class="ds-label">{{ t('passwordChange.currentPassword') }}</label>
            <input v-model="form.currentPassword" type="password" class="ds-input w-full" />
          </div>
          <div>
            <label class="ds-label">{{ t('passwordChange.newPassword') }}</label>
            <input v-model="form.newPassword" type="password" class="ds-input w-full"
              :class="passwordMatch === false ? 'border-red-400' : ''" />
            <div v-if="form.newPassword" class="flex items-center gap-2 mt-1">
              <div class="flex-1 h-1.5 bg-slate-200 rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all" :style="{ width: strengthPercent + '%', background: strengthColor }"></div>
              </div>
              <span class="text-[11px] font-mono" :style="{ color: strengthColor }">{{ strengthLabel }}</span>
            </div>
          </div>
          <div>
            <label class="ds-label">{{ t('passwordChange.confirmPassword') }}</label>
            <input v-model="form.confirmPassword" type="password" class="ds-input w-full"
              :class="passwordMatch === false ? 'border-red-400' : ''" />
            <p v-if="passwordMatch === false" class="text-[11px] text-red-500 mt-1">{{ t('passwordChange.passwordMismatch') }}</p>
          </div>
          <div v-if="auth.mfaEnabled">
            <label class="ds-label">{{ t('passwordChange.totpCode') }}</label>
            <input v-model="form.totpCode" type="text" maxlength="6" pattern="[0-9]*" inputmode="numeric"
              class="ds-input w-full font-mono text-center tracking-[0.3em]" placeholder="000000" />
          </div>
          <p v-if="error" class="text-[12px] text-red-600 font-mono">{{ error }}</p>
        </div>
        <div class="flex items-center justify-end gap-2 px-6 py-4 border-t border-slate-200 bg-slate-50">
          <button @click="$emit('close')" class="ds-btn-secondary">{{ t('common.cancel') }}</button>
          <button @click="submit" :disabled="!isValid || saving" class="ds-btn-primary">
            {{ saving ? t('common.saving') : t('passwordChange.changeButton') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
import { useToastStore } from '@/stores/toast'
import { useIcons } from '../composables/useIcons'

const icons = useIcons()

const { t } = useI18n()
const auth = useAuthStore()
const toast = useToastStore()

defineProps({ show: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

const form = ref({ currentPassword: '', newPassword: '', confirmPassword: '', totpCode: '' })
const error = ref('')
const saving = ref(false)

const passwordMatch = computed(() => {
  if (!form.value.confirmPassword) return null
  return form.value.newPassword === form.value.confirmPassword
})

const strength = computed(() => {
  const p = form.value.newPassword
  if (!p) return 0
  let s = 0
  if (p.length >= 8) s++
  if (p.length >= 12) s++
  if (/[A-Z]/.test(p)) s++
  if (/[0-9]/.test(p)) s++
  if (/[^A-Za-z0-9]/.test(p)) s++
  return Math.min(s, 4)
})
const strengthPercent = computed(() => (strength.value / 4) * 100)
const strengthColor = computed(() => ['#ef4444', '#f59e0b', '#eab308', '#22c55e'][strength.value - 1] || '#e2e8f0')
const strengthLabel = computed(() => t('passwordChange.strength.' + Math.max(strength.value, 0)))

const isValid = computed(() => {
  if (!form.value.newPassword || form.value.newPassword.length < 8) return false
  if (form.value.newPassword !== form.value.confirmPassword) return false
  if (!auth.mustChangePassword && !form.value.currentPassword) return false
  if (auth.mfaEnabled && !form.value.totpCode) return false
  return true
})

async function submit() {
  error.value = ''
  saving.value = true
  try {
    const res = await authApi.changePassword(
      form.value.newPassword,
      form.value.currentPassword || undefined,
      form.value.totpCode || undefined
    )
    if (res.data.token) {
      // el nuevo token se recibe como cookie httpOnly; nada que guardar en JS
      auth.persist()
    }
    auth.mustChangePassword = false
    auth.hasPasswordSet = true
    auth.persist()
    toast.success(t('passwordChange.success'))
    emit('close')
  } catch (e) {
    error.value = e.response?.data?.error || t('passwordChange.error')
  } finally {
    saving.value = false
  }
}
</script>
