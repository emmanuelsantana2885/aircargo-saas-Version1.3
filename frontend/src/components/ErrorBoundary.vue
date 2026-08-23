<template>
  <slot v-if="!hasError"></slot>
  <div v-else class="min-h-[60vh] flex flex-col items-center justify-center gap-4 p-10 text-center">
    <div class="w-14 h-14 rounded-full bg-red-100 text-red-600 flex items-center justify-center text-2xl font-bold">!</div>
    <h2 class="text-lg font-bold text-slate-900">{{ t('errorBoundary.title') }}</h2>
    <p class="text-[13px] text-slate-500 max-w-md">{{ t('errorBoundary.message') }}</p>
    <div class="flex gap-2">
      <button type="button" class="ds-btn-primary" @click="recover">{{ t('errorBoundary.retry') }}</button>
      <button type="button" class="ds-btn-secondary" @click="goHome">{{ t('errorBoundary.home') }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onErrorCaptured } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const hasError = ref(false)

// Navegar a otra vista limpia el estado de error
watch(() => route.fullPath, () => { hasError.value = false })

// Captura errores de render de las vistas hijas (evita la pantalla en blanco)
onErrorCaptured((err, instance, info) => {
  console.error('[ErrorBoundary]', info, err)
  hasError.value = true
  return false
})

function recover() {
  hasError.value = false
}

function goHome() {
  router.push('/')
}
</script>
