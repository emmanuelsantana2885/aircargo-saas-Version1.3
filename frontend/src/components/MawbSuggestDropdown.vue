<template>
  <div ref="root" class="mawb-suggest relative">
    <input
      v-model="text"
      @input="onInput"
      @focus="onFocus"
      @blur="onBlur"
      :placeholder="placeholder"
      class="w-full border-b border-slate-200 focus:outline-none focus:border-slate-950 py-1 bg-transparent font-bold tracking-tight text-slate-950 text-[13px]"
    />

    <Teleport to="body">
      <div v-if="open" class="mawb-suggest__panel" :style="panelStyle" role="listbox">
        <div class="mawb-suggest__header">
          <span class="font-bold">{{ header }}</span>
          <span v-if="suggestions.length" class="mawb-suggest__count">{{ suggestions.length }}</span>
          <button type="button" class="mawb-suggest__close" @click="$emit('close')" aria-label="Cerrar">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div class="mawb-suggest__list" role="listbox">
          <button
            v-for="s in suggestions"
            :key="s.id"
            type="button"
            role="option"
            class="mawb-suggest__item"
            @mousedown.prevent
            @click="$emit('select', s)"
          >
            <slot name="suggestion" :item="s" />
          </button>
          <p v-if="!suggestions.length" class="mawb-suggest__empty">{{ emptyText }}</p>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  suggestions: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  header: { type: String, default: '' },
  emptyText: { type: String, default: 'Sin coincidencias' },
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'input', 'focus', 'blur', 'select', 'close'])

const text = ref(props.modelValue)
const root = ref(null)
const panelStyle = ref(null)

watch(() => props.modelValue, (v) => { text.value = v })

const onInput = () => emit('input', text.value)
const onFocus = () => emit('focus')
const onBlur = () => emit('blur')

function positionPanel() {
  if (!root.value || !props.open) return
  const r = root.value.querySelector('input').getBoundingClientRect()
  const vw = window.innerWidth
  const vh = window.innerHeight
  const panelW = Math.min(430, Math.max(320, vw - 16))
  const estimatedH = Math.min(Math.max(160, vh * 0.5), 320)

  let top = r.bottom + 6
  let left = r.left

  if (top + estimatedH > vh - 8) {
    top = Math.max(8, r.top - estimatedH - 6)
    if (top === 8) top = Math.min(r.top, vh - estimatedH - 8)
  }
  if (left + panelW > vw - 8) left = Math.max(8, vw - panelW - 8)
  if (left < 8) left = 8

  panelStyle.value = {
    top: `${top}px`,
    left: `${left}px`,
    width: `${panelW}px`,
  }
}

watch(() => props.open, async (v) => {
  if (v) {
    await nextTick()
    positionPanel()
  }
})

function onScroll() { if (props.open) positionPanel() }
function onResize() { if (props.open) positionPanel() }

onMounted(() => {
  window.addEventListener('scroll', onScroll, true)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.mawb-suggest__panel {
  position: fixed;
  z-index: 9999;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 12px;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.2), 0 2px 6px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-height: min(60vh, 360px);
}
.mawb-suggest__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  font-size: 13px;
  color: #0f172a;
  letter-spacing: 0.02em;
}
.mawb-suggest__count {
  font-size: 11px;
  font-weight: 700;
  background: #e0f2fe;
  color: #0369a1;
  border-radius: 999px;
  padding: 1px 7px;
}
.mawb-suggest__close {
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  background: transparent;
  border: none;
}
.mawb-suggest__close:hover { background: #e2e8f0; color: #0f172a; }
.mawb-suggest__list {
  padding: 6px;
  overflow-y: auto;
}
.mawb-suggest__item {
  width: 100%;
  display: block;
  text-align: left;
  padding: 8px 9px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background 0.12s ease;
}
.mawb-suggest__item:hover { background: #f1f5f9; }
.mawb-suggest__item + .mawb-suggest__item { margin-top: 2px; }
.mawb-suggest__empty { padding: 16px; font-size: 13px; color: #94a3b8; }

:root[data-theme='tokyo'] .mawb-suggest__panel { background: #0f172a; border-color: #475569; }
:root[data-theme='tokyo'] .mawb-suggest__header { background: #1e293b; color: #e2e8f0; border-bottom-color: #334155; }
:root[data-theme='tokyo'] .mawb-suggest__count { background: #164e63; color: #7dd3fc; }
:root[data-theme='tokyo'] .mawb-suggest__close { color: #94a3b8; }
:root[data-theme='tokyo'] .mawb-suggest__close:hover { background: #334155; color: #e2e8f0; }
:root[data-theme='tokyo'] .mawb-suggest__item:hover { background: #1e293b; }
:root[data-theme='tokyo'] .mawb-suggest__empty { color: #64748b; }
</style>