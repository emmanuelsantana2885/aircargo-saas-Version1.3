<template>
  <div class="ds-empty py-10 px-4 animate-fade-up">
    <div class="relative mb-1">
      <div v-if="loading" class="ds-spinner absolute -inset-4 opacity-40" />
      <div v-else class="w-16 h-16 rounded-2xl flex items-center justify-center bg-gradient-to-br from-slate-100 to-slate-200/60 border border-slate-200"
        style="box-shadow: 0 8px 24px -12px rgba(15,23,42,0.2);">
        <component v-if="icon" :is="icon" :size="30" class="text-slate-400" :stroke-width="1.4" />
      </div>
    </div>
    <p class="uppercase tracking-widest text-center max-w-sm">{{ title }}</p>
    <p v-if="hint" class="normal-case tracking-normal text-[13px] text-slate-400 max-w-md text-center leading-relaxed">
      {{ hint }}
    </p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  hint: { type: String, default: '' },
  icon: { type: [Object, Function], default: null },
  loading: { type: Boolean, default: false },
})
</script>
