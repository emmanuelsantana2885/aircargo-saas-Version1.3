<template>
  <div class="ds-modal-backdrop" style="z-index:100">
    <div class="ds-modal-panel max-w-sm text-center">
      <div class="mx-auto mb-3 w-12 h-12 rounded-full flex items-center justify-center"
           style="background:var(--warn-bg); color:var(--warn)">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5" stroke-linecap="round"/><path d="M9 2h6"/>
        </svg>
      </div>
      <h2 class="text-[15px] font-bold mb-1" style="color:var(--text)">{{ t('idle.title') }}</h2>
      <p class="text-[12.5px] mb-1" style="color:var(--text-2)">{{ t('idle.message') }}</p>
      <p class="text-[26px] font-extrabold font-mono my-2" style="color:var(--warn)">{{ secondsLeft }}s</p>
      <p class="text-[11px] mb-4" style="color:var(--text-3)">{{ t('idle.preserve') }}</p>
      <button type="button" class="btn primary w-full justify-center" @click="$emit('continue')">
        {{ t('idle.continue') }}
      </button>
      <button type="button" class="mt-2 text-[11.5px] underline" style="color:var(--text-3)" @click="$emit('logoutNow')">
        {{ t('idle.logoutNow') }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

defineProps({ secondsLeft: { type: Number, default: 0 } })
defineEmits(['continue', 'logoutNow'])
const { t } = useI18n()
</script>
