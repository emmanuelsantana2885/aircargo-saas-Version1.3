<template>
  <div class="min-h-screen flex items-center justify-center p-3 md:p-8" style="background: var(--bg)">
    <div class="w-full max-w-sm p-6 md:p-8 rounded-lg shadow-md" style="background: var(--surface); border: 1px solid var(--border)">
      <div v-if="route.query.idle === '1'" class="mb-4 p-3 rounded-xl text-[12.5px]" style="background: var(--warn-bg); color: var(--warn); border: 1px solid var(--warn)">
          {{ t('idle.expired') }}
        </div>
        <template v-if="step === 'credentials'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <IconPlaneDeparture :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.brand') }}</h1>
          <p class="text-sm mt-1" style="color: var(--muted)">{{ t('login.tagline') }}</p>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-4">
          <div>
            <label class="block text-xs font-medium mb-1" style="color: var(--text)">{{ t('login.email') }}</label>
            <input
              v-model="loginEmail"
              type="email"
              required
              :placeholder="t('login.emailPlaceholder')"
              class="w-full px-3 py-2.5 rounded text-sm outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text)"
              :disabled="loading"
            />
          </div>

          <div v-if="needsPassword">
            <label class="block text-xs font-medium mb-1" style="color: var(--text)">{{ t('login.password') }}</label>
            <input
              v-model="password"
              type="password"
              required
              placeholder="••••••••"
              class="w-full px-3 py-2.5 rounded text-sm outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text)"
              :disabled="loading"
            />
          </div>

          <button
            type="submit"
            :disabled="loading || !loginEmail || (needsPassword && !password)"
            class="w-full py-2.5 rounded text-sm font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.signingIn') }}
            </span>
            <span v-else>{{ t('login.loginBtn') }}</span>
          </button>

          <p v-if="errorMsg" class="text-xs text-center" style="color: var(--muted)">{{ errorMsg }}</p>
          <p v-if="showSetupLink" class="text-xs text-center">
            <a href="#" @click.prevent="goToSetPassword" style="color: var(--accent)" class="underline">
              {{ t('login.setPasswordLink') }}
            </a>
          </p>
        </form>
        <p class="text-center text-[11px] mt-4" style="color: var(--muted)">
          <router-link to="/privacy" class="hover:underline">{{ t('privacy.link') }}</router-link>
        </p>
      </template>

      <template v-if="step === 'mfa'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <IconShieldLock :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.mfa.title') }}</h1>
          <p class="text-sm mt-1" style="color: var(--muted)">{{ t('login.mfa.subtitle') }}</p>
        </div>

        <form @submit.prevent="handleMfa" class="space-y-4">
          <div>
            <label class="block text-xs font-medium mb-1" style="color: var(--text)">{{ t('login.mfa.codeLabel') }}</label>
            <input
              v-model="totpCode"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="6"
              required
              placeholder="000000"
              class="w-full px-3 py-2.5 rounded text-sm text-center font-mono tracking-[0.5em] outline-none transition-all border-slate-300"
              style="background: var(--bg); color: var(--text); font-size: 18px"
              :disabled="loading"
              autofocus
            />
          </div>

          <button
            type="submit"
            :disabled="loading || totpCode.length !== 6"
            class="w-full py-2.5 rounded text-sm font-semibold transition-all"
            :class="loading ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            <span v-if="loading" class="inline-flex items-center gap-2">
              <span class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              {{ t('login.mfa.verifying') }}
            </span>
            <span v-else>{{ t('login.mfa.verify') }}</span>
          </button>

          <p v-if="errorMsg" class="text-xs text-center" style="color: var(--muted)">{{ errorMsg }}</p>

          <button
            @click="handleBackToLogin"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('common.back') }}
          </button>
        </form>
      </template>

      <template v-if="step === 'site-select'">
        <div class="text-center mb-6">
          <div class="w-12 h-12 rounded-lg flex items-center justify-center mx-auto mb-3" style="background: var(--accent)">
            <IconBuildingStore :size="28" color="white" :stroke-width="2" />
          </div>
          <h1 class="text-xl font-bold" style="color: var(--text)">{{ t('login.stepSite.title') }}</h1>
          <p class="text-sm mt-1" style="color: var(--muted)">{{ t('login.stepSite.subtitle') }}</p>
        </div>

        <div class="space-y-3">
          <div>
            <label class="block text-xs font-medium mb-1" style="color: var(--text)">{{ t('sidebar.siteLabel') }}</label>
            <select
              v-model="selectedSite"
              class="w-full px-3 py-2.5 rounded text-sm outline-none border-slate-300"
              style="background: var(--bg); color: var(--text)"
            >
              <option v-for="site in auth.sites" :key="site.id" :value="site.id">
                {{ site.name }} ({{ site.code }})
              </option>
            </select>
          </div>

          <button
            @click="handleSiteConfirm"
            :disabled="!selectedSite"
            class="w-full py-2.5 rounded text-sm font-semibold transition-all"
            :class="!selectedSite ? 'opacity-60' : 'hover:brightness-110 active:scale-[0.98]'"
            style="background: var(--accent); color: white"
          >
            {{ t('login.stepSite.continue') }} {{ selectedSiteLabel }}
          </button>

          <button
            @click="handleBackToLogin"
            class="w-full py-1.5 rounded text-xs font-medium transition-all hover:brightness-110"
            style="background: transparent; color: var(--muted)"
          >
            {{ t('common.back') }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { IconPlaneDeparture, IconBuildingStore, IconShieldLock } from '@tabler/icons-vue'
import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'

const { t } = useI18n()
const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()

const loginEmail = ref('')
const password = ref('')
const totpCode = ref('')
const loading = ref(false)
const errorMsg = ref('')
const needsPassword = ref(false)
const showSetupLink = ref(false)
const step = ref('credentials')
const selectedSite = ref(null)
const pendingEmail = ref('')
const pendingPassword = ref('')

const selectedSiteLabel = computed(() => {
  if (!selectedSite.value) return ''
  const site = auth.sites.find(s => s.id === selectedSite.value)
  return site ? `${site.name}` : ''
})

async function handleLogin() {
  errorMsg.value = ''
  showSetupLink.value = false
  loading.value = true
  try {
    await auth.login(loginEmail.value, password.value)
    if (auth.mustChangePassword) {
      router.push('/change-password')
      return
    }
    proceedAfterLogin()
  } catch (e) {
    const status = e.response?.status
    const data = e.response?.data
    if (status === 428 && data?.mfaRequired) {
      pendingEmail.value = loginEmail.value
      pendingPassword.value = password.value
      step.value = 'mfa'
      totpCode.value = ''
      errorMsg.value = ''
      loading.value = false
      return
    }
    toast.error(extractError(e))
    if (status === 428) {
      needsPassword.value = true
      showSetupLink.value = true
      errorMsg.value = t('login.error.passwordRequired')
    } else if (status === 401) {
      if (step.value === 'mfa' || data?.error) {
        errorMsg.value = data?.error || t('login.error.invalidCode')
      } else if (needsPassword.value) {
        errorMsg.value = t('login.error.wrongPassword')
      } else {
        errorMsg.value = t('login.error.invalidCredentials')
      }
    } else if (status === 403) {
      errorMsg.value = data?.error || t('login.error.inactive')
    } else {
      errorMsg.value = t('login.error.generic')
    }
  } finally {
    loading.value = false
  }
}

async function handleMfa() {
  errorMsg.value = ''
  loading.value = true
  try {
    await auth.login(pendingEmail.value, pendingPassword.value, totpCode.value)
    if (auth.mustChangePassword) {
      router.push('/change-password')
      return
    }
    proceedAfterLogin()
  } catch (e) {
    const status = e.response?.status
    const data = e.response?.data
    toast.error(extractError(e))
    if (status === 401) {
      errorMsg.value = data?.error || t('login.error.invalidCode')
      totpCode.value = ''
    } else if (status === 403) {
      errorMsg.value = data?.error || t('login.error.locked')
    } else {
      errorMsg.value = t('login.error.generic')
    }
  } finally {
    loading.value = false
  }
}

function proceedAfterLogin() {
  pendingEmail.value = ''
  pendingPassword.value = ''
  if (auth.sites.length === 0) {
    errorMsg.value = t('login.error.noSites')
    return
  }
  if (auth.sites.length === 1) {
    selectedSite.value = auth.sites[0].id
    auth.confirmSite(selectedSite.value)
    navigateAfterSiteConfirm()
    return
  }
  selectedSite.value = auth.sites[0].id
  step.value = 'site-select'
}

import { popReturnTo, loadDraft, restoreForms } from '@/utils/formDraft'

function navigateAfterSiteConfirm() {
  const draft = loadDraft()
  if (draft && draft.route && draft.forms?.length) {
    router.push(draft.route)
    setTimeout(() => {
      const n = restoreForms(draft.forms)
      clearDraft()
      if (n > 0) toast.success(t('idle.restored', { n }))
    }, 600)
    return
  }
  router.push(popReturnTo() || '/')
}

function handleSiteConfirm() {
  if (!selectedSite.value) return
  auth.confirmSite(selectedSite.value)
  navigateAfterSiteConfirm()
}

function handleBackToLogin() {
  step.value = 'credentials'
  pendingEmail.value = ''
  pendingPassword.value = ''
  totpCode.value = ''
  errorMsg.value = ''
  auth.logout()
}

function goToSetPassword() {
  router.push(`/set-password?email=${encodeURIComponent(loginEmail.value)}`)
}
</script>
