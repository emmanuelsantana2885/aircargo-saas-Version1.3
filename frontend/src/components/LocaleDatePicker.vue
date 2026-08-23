<template>
  <div ref="root" class="relative">
    <button type="button" @click="open = !open"
      class="ds-input w-full flex items-center justify-between gap-1.5 text-left cursor-pointer"
      :class="{ 'text-slate-400': !modelValue }">
      <span>{{ display }}</span>
      <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
        class="shrink-0 text-slate-400">
        <rect x="3" y="4" width="18" height="18" rx="2" />
        <line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" />
        <line x1="3" y1="10" x2="21" y2="10" />
      </svg>
    </button>

    <div v-if="open"
      class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded-lg shadow-xl z-50 p-2.5 w-[260px] select-none">

      <!-- Navegación simple: flecha · mes · año · flecha -->
      <div class="flex items-center gap-1 mb-2">
        <button type="button" @click="nav(-1)" class="cal-nav" title="-1">&lsaquo;</button>
        <select v-model.number="view.m" class="ds-input flex-1 min-w-0 text-[12px] font-bold py-1 px-1 cursor-pointer">
          <option v-for="(m, i) in monthsFull" :key="'m' + i" :value="i">{{ m }}</option>
        </select>
        <select v-model.number="view.y" class="ds-input w-[72px] text-[12px] font-bold py-1 px-1 cursor-pointer">
          <option v-for="y in years" :key="'y' + y" :value="y">{{ y }}</option>
        </select>
        <button type="button" @click="nav(1)" class="cal-nav" title="+1">&rsaquo;</button>
      </div>

      <!-- Días de la semana -->
      <div class="grid grid-cols-7 mb-1">
        <span v-for="wd in weekdayLabels" :key="'w' + wd"
          class="text-center text-[10px] font-mono font-bold text-slate-400 uppercase py-0.5">{{ wd }}</span>
      </div>

      <!-- Grid de días -->
      <div class="grid grid-cols-7 gap-y-0.5">
        <button v-for="(d, i) in gridDays" :key="i" type="button"
          class="h-8 text-[13px] rounded-md transition-colors mx-auto w-full max-w-9"
          :class="dayClass(d)"
          @click="pick(d)">
          {{ d.date.getDate() }}
        </button>
      </div>

      <!-- Acciones -->
      <div class="flex items-center gap-2 mt-2 pt-2 border-t border-slate-100">
        <button type="button" @click="goToday"
          class="flex-1 text-center text-[12px] font-bold py-1.5 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors">
          {{ t('filterBar.periods.today') }}
        </button>
        <button v-if="modelValue" type="button" @click="pick(null)"
          class="text-[12px] font-bold py-1.5 px-3 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-500 transition-colors">
          {{ t('common.clear') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  modelValue: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'change'])

const { t, locale } = useI18n()
const root = ref(null)
const open = ref(false)
const view = ref({ y: 0, m: 0 })

// Meses/días vía Intl del navegador — i18n nativo, sin depender de arrays de vue-i18n
// (t() sobre arrays los interpreta como plurales y no devuelve la lista)
const intlLocale = computed(() => (locale.value || 'es').startsWith('en') ? 'en-US' : 'es-DO')
const cap = s => s ? s.charAt(0).toUpperCase() + s.slice(1) : s
const monthsFull = computed(() =>
  Array.from({ length: 12 }, (_, i) =>
    cap(new Date(2026, i, 1).toLocaleDateString(intlLocale.value, { month: 'long' }))))
const monthsShort = computed(() =>
  Array.from({ length: 12 }, (_, i) =>
    cap(new Date(2026, i, 1).toLocaleDateString(intlLocale.value, { month: 'short' }).replace('.', ''))))
const weekdayLabels = computed(() => {
  // 2026-01-05 fue lunes; lunes primero como el grid
  return Array.from({ length: 7 }, (_, i) =>
    new Date(2026, 0, 5 + i).toLocaleDateString(intlLocale.value, { weekday: 'short' }).replace('.', '').toUpperCase())
})

function todayIso() {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}

watch(open, (v) => {
  if (!v) return
  const base = props.modelValue ? parseIso(props.modelValue) : new Date()
  view.value = { y: base.getFullYear(), m: base.getMonth() }
})

function parseIso(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

const years = computed(() => {
  const cy = new Date().getFullYear()
  const list = []
  for (let y = cy - 10; y <= cy + 2; y++) list.push(y)
  return list
})

const gridDays = computed(() => {
  const first = new Date(view.value.y, view.value.m, 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(view.value.y, view.value.m, 1 - offset)
  const days = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    days.push({
      date: d,
      iso: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
      outside: d.getMonth() !== view.value.m,
    })
  }
  return days
})

const display = computed(() => {
  if (!props.modelValue) return '--/--/----'
  const d = parseIso(props.modelValue)
  const mon = monthsShort.value[d.getMonth()]
  return locale.value === 'en' ? `${mon} ${d.getDate()}, ${d.getFullYear()}` : `${d.getDate()} ${mon} ${d.getFullYear()}`
})

function dayClass(d) {
  if (d.iso === props.modelValue) return 'bg-slate-900 text-white font-bold'
  if (d.iso === todayIso()) return 'bg-blue-50 text-blue-700 font-bold ring-1 ring-blue-300'
  if (d.outside) return 'text-slate-300 hover:bg-slate-100'
  if ([0, 6].includes(d.date.getDay())) return 'text-slate-400 hover:bg-slate-100'
  return 'text-slate-700 hover:bg-slate-100'
}

function pick(d) {
  emit('update:modelValue', d ? d.iso : '')
  emit('change', d ? d.iso : '')
  open.value = false
}

function goToday() {
  pick({ date: null, iso: todayIso() })
}

function nav(deltaMonths) {
  let m = view.value.m + deltaMonths
  let y = view.value.y
  while (m < 0) { m += 12; y-- }
  while (m > 11) { m -= 12; y++ }
  view.value = { y, m }
}

function onDocClick(e) {
  if (open.value && root.value && !root.value.contains(e.target)) open.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))
</script>

<style scoped>
.cal-nav {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #475569;
  font-size: 15px;
  flex-shrink: 0;
}
.cal-nav:hover {
  background: #e2e8f0;
  color: #0f172a;
}
</style>
