<template>
  <div class="relative flex-1 min-w-0" ref="root">
    <!-- Trigger: current selection, expandable -->
    <button
      type="button"
      @click.stop="toggleOpen"
      class="fp-trigger"
      :class="{ 'fp-open': open }"
    >
      <span class="truncate">{{ selectedLabel }}</span>
      <span class="ml-1 text-[8px] opacity-70">&#9662;</span>
    </button>

    <!-- Popup panel -->
    <transition name="fp">
      <div v-if="open" ref="panel" class="fp-panel" @click.stop>
        <div class="fp-header">
          <input
            v-model="query"
            type="text"
            class="fp-search"
            :placeholder="t('db.pickSearch')"
            @keydown.stop
          />
          <button class="fp-close" @click="open = false" :title="t('db.pickClose')">&#10005;</button>
        </div>
        <div class="fp-body">
          <div v-for="group in filteredGroups" :key="group.source" class="fp-group">
            <div class="fp-group-head" @click="toggleGroup(group.source)">
              <span class="fp-dot" :style="{ background: sourceColor(group.source) }"></span>
              <span class="fp-group-label">{{ t('db.sources.' + group.source) }}</span>
              <span class="fp-count">{{ group.fields.length }}</span>
              <button v-if="openGroups[group.source]" class="fp-col" @click.stop="selectMany(group)">
                {{ t('db.pickAll') }}
              </button>
              <span class="fp-caret">{{ openGroups[group.source] ? '&#9662;' : '&#9656;' }}</span>
            </div>
            <div v-if="openGroups[group.source]" class="fp-items">
              <div
                v-for="f in group.fields"
                :key="f.key"
                class="fp-item"
                :class="{ 'fp-item-on': isSelected(f.key) }"
                @click.stop="select(f.key)"
              >
                <input
                  v-if="multi"
                  type="checkbox"
                  class="fp-check"
                  :checked="isSelected(f.key)"
                  @click.stop
                  @change.stop="select(f.key)"
                />
                <span class="fp-item-label" :title="f.hint || f.key">{{ f.label }}</span>
                <span v-if="f.unit" class="fp-item-unit">{{ f.unit }}</span>
              </div>
            </div>
          </div>
          <div v-if="!filteredGroups.length" class="fp-empty">{{ t('db.pickNoMatch') }}</div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  fieldsBySource: { type: Array, default: () => [] },
  modelValue: { type: [String, Array], default: '' },
  multi: { type: Boolean, default: false },
  onlyNumeric: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  labelOf: { type: Function, default: (k) => k },
})

const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const open = ref(false)
const query = ref('')
const openGroups = reactive({})
const root = ref(null)
const panel = ref(null)

const selectedLabel = computed(() => {
  if (props.multi) {
    const v = props.modelValue || []
    if (!v.length) return props.placeholder || t('db.pickNone')
    if (v.length === 1) return props.labelOf?.(v[0]) || v[0]
    return `${v.length} ${t('db.pickSelected')}`
  }
  const v = props.modelValue
  if (!v || v === '') return props.placeholder || t('db.pickNone')
  return props.labelOf?.(v) || v
})

const filteredGroups = computed(() => {
  const q = query.value.trim().toLowerCase()
  return props.fieldsBySource
    .map((g) => ({
      source: g.source,
      fields: g.fields
        .filter((f) => !props.onlyNumeric || f.type === 'number')
        .filter((f) => !q || (f.label || '').toLowerCase().includes(q) || f.key.toLowerCase().includes(q)),
    }))
    .filter((g) => g.fields.length)
})

function sourceColor(source) {
  const c = {
    uld: '#0ea5e9', flight: '#8b5cf6', airline: '#64748b', mawb: '#10b981',
    booking: '#f59e0b', receipt: '#ef4444', scenario: '#94a3b8',
  }
  return c[source] || '#94a3b8'
}

function isSelected(key) {
  if (props.multi) return (props.modelValue || []).includes(key)
  return props.modelValue === key
}

function select(key) {
  if (props.multi) {
    const cur = (props.modelValue || []).slice()
    const i = cur.indexOf(key)
    if (i >= 0) cur.splice(i, 1)
    else cur.push(key)
    emit('update:modelValue', cur)
  } else {
    emit('update:modelValue', key)
    close()
  }
}

function selectMany(group) {
  if (!props.multi) return
  const keys = group.fields.map((f) => f.key)
  const cur = (props.modelValue || []).slice()
  for (const k of keys) if (!cur.includes(k)) cur.push(k)
  emit('update:modelValue', cur)
}

function toggleGroup(source) {
  openGroups[source] = !openGroups[source]
}

function toggleOpen() {
  open.value = !open.value
  if (open.value && !Object.values(openGroups).some(Boolean)) {
    for (const g of props.fieldsBySource) openGroups[g.source] = true
  }
}

function close() {
  open.value = false
  query.value = ''
}

function onClickOutside(e) {
  if (open.value && root.value && !root.value.contains(e.target)) close()
}
function onEscape(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('mousedown', onClickOutside)
  document.addEventListener('keydown', onEscape)
})
onUnmounted(() => {
  document.removeEventListener('mousedown', onClickOutside)
  document.removeEventListener('keydown', onEscape)
})

defineExpose({ close })
</script>

<style scoped>
.fp-trigger {
  display: flex; align-items: center; justify-content: space-between; width: 100%;
  min-width: 0; padding: 6px 8px; font-size: 12px; font-family: var(--font-family);
  background: var(--surface, #fff); color: var(--text, #111); border: 1px solid var(--border, #999);
  border-radius: 0; text-align: left; cursor: pointer; line-height: 1.2;
}
.fp-trigger:hover { outline: 2px solid var(--accent, #000); outline-offset: -1px; }
.fp-open { outline: 2px solid var(--accent, #000); outline-offset: -1px; }
.fp-panel {
  position: absolute; z-index: 60; top: calc(100% + 4px); left: 0; right: 0; min-width: 220px;
  max-width: 380px; background: #fff; border: 1px solid #cbd5e1; border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.18); overflow: hidden;
}
.fp-header { display: flex; align-items: center; gap: 6px; padding: 8px; border-bottom: 1px solid #e2e8f0; }
.fp-search {
  flex: 1; min-width: 0; font-size: 12px; font-family: var(--font-family);
  padding: 5px 8px; border: 1px solid #cbd5e1; border-radius: 6px; outline: none;
}
.fp-close {
  flex: 0 0 auto; width: 22px; height: 22px; line-height: 1; border: 1px solid #e2e8f0;
  border-radius: 6px; background: #f8fafc; color: #64748b; cursor: pointer; font-size: 12px;
}
.fp-close:hover { background: #eef2f7; }
.fp-body { max-height: 300px; overflow-y: auto; }
.fp-group { border-bottom: 1px solid #f1f5f9; }
.fp-group-head {
  display: flex; align-items: center; gap: 6px; padding: 6px 10px; cursor: pointer;
  font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; color: #475569;
}
.fp-group-head:hover { background: #f8fafc; }
.fp-dot { width: 8px; height: 8px; border-radius: 99px; flex: 0 0 auto; }
.fp-group-label { flex: 1; }
.fp-count { font-size: 10px; color: #94a3b8; font-family: var(--font-family); }
.fp-col { font-size: 10px; color: #0ea5e9; text-transform: none; font-weight: 600; cursor: pointer; }
.fp-col:hover { text-decoration: underline; }
.fp-caret { font-size: 9px; color: #94a3b8; }
.fp-items { max-height: 190px; overflow-y: auto; padding: 2px 4px 6px; }
.fp-item {
  display: flex; align-items: center; gap: 6px; padding: 4px 8px; border-radius: 6px;
  cursor: pointer; font-size: 12px; color: #334155; font-family: var(--font-family);
}
.fp-item:hover { background: #f1f5f9; }
.fp-item-on { background: #eff6ff; color: #1e3a8a; }
.fp-check { flex: 0 0 auto; }
.fp-item-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-item-unit { font-size: 9px; color: #94a3b8; text-transform: uppercase; }
.fp-empty { padding: 12px; font-size: 12px; color: #94a3b8; text-align: center; }

:root[data-theme='tokyo'] .fp-panel { background: #1e293b; border-color: #334155; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
:root[data-theme='tokyo'] .fp-header { border-bottom-color: #334155; }
:root[data-theme='tokyo'] .fp-search { background: #0f172a; color: #e2e8f0; border-color: #475569; }
:root[data-theme='tokyo'] .fp-group { border-bottom-color: #334155; }
:root[data-theme='tokyo'] .fp-group-head { color: #cbd5e1; }
:root[data-theme='tokyo'] .fp-group-head:hover { background: #0f172a; }
:root[data-theme='tokyo'] .fp-item { color: #e2e8f0; }
:root[data-theme='tokyo'] .fp-item:hover { background: #334155; }
:root[data-theme='tokyo'] .fp-item-on { background: rgb(122 162 247 / 0.18); color: #7aa2f7; }
:root[data-theme='tokyo'] .fp-empty { color: #64748b; }

.fp-enter-active, .fp-leave-active { transition: opacity 0.12s; }
.fp-enter-from, .fp-leave-to { opacity: 0; }
</style>