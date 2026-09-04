<template>
  <div class="ds-empty py-10 px-4">
    <div v-if="loading" class="ds-spinner" />
    <component v-else-if="icon" :is="icon" :size="36" class="text-slate-300" :stroke-width="1.2" />
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
