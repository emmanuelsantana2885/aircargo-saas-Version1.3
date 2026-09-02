<template>
  <div ref="root" class="uld-type-dropdown relative">
    <button
      type="button"
      class="ds-input w-full flex items-center justify-between gap-2 cursor-pointer text-left"
      :class="{ 'uld-type-dropdown--open': open }"
      :aria-haspopup="true"
      :aria-expanded="open"
      @click="toggle"
    >
      <span class="truncate font-bold tracking-wide" :class="!modelValue ? 'text-slate-400 font-normal' : ''">
        {{ modelValue || placeholder }}
      </span>
      <svg
        class="uld-type-dropdown__chevron shrink-0 transition-transform duration-200"
        :class="open ? 'rotate-180' : ''"
        width="14" height="14" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"
      >
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        ref="panel"
        class="uld-type-dropdown__panel"
        :style="panelStyle"
        role="listbox"
      >
        <div class="uld-type-dropdown__header">
          <span class="font-bold">{{ title }}</span>
          <button type="button" class="uld-type-dropdown__close" @click="open = false" aria-label="Cerrar">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div class="uld-type-dropdown__grid" role="listbox">
          <button
            v-for="item in items"
            :key="item.code"
            type="button"
            role="option"
            :class="[
              'uld-type-dropdown__option',
              { 'uld-type-dropdown__option--active': item.code === modelValue }
            ]"
            @click="select(item.code)"
          >
            <span class="uld-type-dropdown__code">{{ item.code }}</span>
            <span v-if="item.description" class="uld-type-dropdown__desc">{{ item.description }}</span>
            <svg
              v-if="item.code === modelValue"
              class="uld-type-dropdown__check shrink-0"
              width="16" height="16" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"
            >
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </button>
          <p v-if="!items.length" class="uld-type-dropdown__empty">{{ emptyText }}</p>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'

defineProps({
  modelValue: { type: String, default: '' },
  items: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  title: { type: String, default: '' },
  emptyText: { type: String, default: 'Sin opciones' },
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)
const panel = ref(null)
const panelStyle = ref(null)

const toggle = () => (open.value = !open.value)
const select = (code) => {
  emit('update:modelValue', code)
  open.value = false
}

function positionPanel() {
  if (!root.value || !open.value) return
  const r = root.value.getBoundingClientRect()
  const vw = window.innerWidth
  const vh = window.innerHeight
  const panelW = Math.min(360, Math.min(340, vw - 16))
  const estimatedH = Math.min(Math.max(120, vh * 0.45), 360)

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

function onClickOutside(e) {
  if (!open.value) return
  if (root.value?.contains(e.target)) return
  if (panel.value?.contains(e.target)) return
  open.value = false
}

function onKeydown(e) {
  if (e.key === 'Escape' && open.value) open.value = false
}

function onScroll() {
  if (open.value) positionPanel()
}

function onResize() {
  if (open.value) positionPanel()
}

onMounted(() => {
  document.addEventListener('mousedown', onClickOutside)
  document.addEventListener('touchstart', onClickOutside, { passive: true })
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onScroll, true)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onClickOutside)
  document.removeEventListener('touchstart', onClickOutside)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', onResize)
})

watch(open, async (v) => {
  if (v) {
    await nextTick()
    positionPanel()
  }
})
</script>

<style scoped>
.uld-type-dropdown__panel {
  position: fixed;
  z-index: 9999;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.18), 0 2px 6px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-height: min(55vh, 380px);
}
.uld-type-dropdown__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 1);
  background: #f8fafc;
  font-size: 13px;
  color: #0f172a;
  letter-spacing: 0.02em;
}
.uld-type-dropdown__close {
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
.uld-type-dropdown__close:hover {
  background: #e2e8f0;
  color: #0f172a;
}
.uld-type-dropdown__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 3px;
  padding: 8px;
  overflow-y: auto;
}
.uld-type-dropdown__option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s ease;
}
.uld-type-dropdown__option:hover {
  background: #f1f5f9;
}
.uld-type-dropdown__option--active {
  background: #e0f2fe;
  box-shadow: inset 0 0 0 1px rgba(14, 165, 233, 0.5);
}
.uld-type-dropdown__code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.03em;
  flex-shrink: 0;
}
.uld-type-dropdown__desc {
  font-size: 11px;
  color: #64748b;
  line-height: 1.2;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.uld-type-dropdown__check {
  color: #0284c7;
  flex-shrink: 0;
}
.uld-type-dropdown__empty {
  padding: 16px;
  font-size: 13px;
  color: #94a3b8;
}

:root[data-theme='tokyo'] .uld-type-dropdown__panel {
  background: #0f172a;
  border-color: #475569;
}
:root[data-theme='tokyo'] .uld-type-dropdown__header {
  background: #1e293b;
  color: #e2e8f0;
  border-bottom-color: #334155;
}
:root[data-theme='tokyo'] .uld-type-dropdown__close { color: #94a3b8; }
:root[data-theme='tokyo'] .uld-type-dropdown__close:hover { background: #334155; color: #e2e8f0; }
:root[data-theme='tokyo'] .uld-type-dropdown__option:hover { background: #1e293b; }
:root[data-theme='tokyo'] .uld-type-dropdown__option--active {
  background: #164e63;
  box-shadow: inset 0 0 0 1px rgba(56, 189, 248, 0.6);
}
:root[data-theme='tokyo'] .uld-type-dropdown__code { color: #e2e8f0; }
:root[data-theme='tokyo'] .uld-type-dropdown__desc { color: #94a3b8; }
:root[data-theme='tokyo'] .uld-type-dropdown__check { color: #38bdf8; }
:root[data-theme='tokyo'] .uld-type-dropdown__empty { color: #64748b; }
</style>