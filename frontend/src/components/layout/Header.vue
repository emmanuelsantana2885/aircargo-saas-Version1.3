<template>
  <header class="flex items-center justify-between px-4 md:px-6 border-b flex-shrink-0 flex-wrap gap-y-1 relative overflow-hidden"
    style="min-height: 44px; border-color: #0f172a; background: linear-gradient(135deg, #0b1226 0%, #182c57 26%, #1b3f8f 52%, #1a5680 78%, #0b1226 100%); background-size: 200% 200%; animation: ds-gradient-pan 18s ease-in-out infinite;">
    <div class="absolute inset-0 opacity-[0.06]" style="background-image: repeating-linear-gradient(45deg, transparent, transparent 2px, rgba(148,163,184,0.3) 2px, rgba(148,163,184,0.3) 3px), repeating-linear-gradient(-45deg, transparent, transparent 3px, rgba(100,116,139,0.2) 3px, rgba(100,116,139,0.2) 4px);"></div>
    <div class="absolute inset-0 opacity-[0.05]" style="background-image: radial-gradient(circle at 30% 50%, rgba(148,163,184,0.4) 0%, transparent 60%), radial-gradient(circle at 70% 30%, rgba(100,116,139,0.3) 0%, transparent 50%);"></div>
    <div class="absolute inset-x-0 bottom-0 h-px" style="background: linear-gradient(90deg, transparent, rgba(255,255,255,0.35), transparent);"></div>

    <div class="flex items-center gap-2 relative z-10">
      <!-- Mobile hamburger -->
      <button v-if="isMobile" @click="$emit('toggleSidebar')"
        class="flex items-center justify-center w-8 h-8 rounded-lg transition hover:bg-white/10">
        <component :is="icons.Menu" :size="20" :stroke-width="2" style="color: white" />
      </button>
      <component :is="icons.ChevronRight" :size="12" style="color: rgba(255,255,255,0.4)" :stroke-width="2" class="hidden sm:block" />
      <span class="text-[13px] md:text-xs font-bold uppercase text-white tracking-wide" style="text-shadow: 0 1px 2px rgba(0,0,0,0.3)">{{ title }}</span>
    </div>

    <div class="flex items-center gap-2 md:gap-3 relative z-10 header-actions">
      <div class="hidden sm:flex items-center rounded-lg border border-white/10 bg-white/5 px-2 py-1" style="backdrop-filter: blur(6px);">
        <LanguageSwitcher />
      </div>
      <span class="text-[12px] md:text-xs text-slate-300 hidden md:block">{{ date }}</span>
      <button @click="toggleIconLib"
        :title="iconLib === 'tabler' ? 'Switch to Lucide icons' : iconLib === 'lucide' ? 'Switch to Material Design icons' : 'Switch to Tabler icons'"
        class="flex items-center justify-center w-8 h-8 rounded-lg transition hover:bg-white/10 text-[10px] font-bold"
        style="color: rgba(255,255,255,0.7); border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);">
        {{ iconLib === 'tabler' ? 'TB' : iconLib === 'lucide' ? 'LC' : 'MD' }}
      </button>
      <button @click="cycleFont" :title="t('header.fontHint')"
        class="flex items-center justify-center w-8 h-8 rounded-lg transition hover:bg-white/10 text-[9px] font-bold tracking-tight"
        style="color: rgba(255,255,255,0.7); border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);">
        {{ fontLabel }}
      </button>
      <button @click="cycleDensity" :title="t('header.densityHint')"
        class="flex items-center justify-center w-8 h-8 rounded-lg transition hover:bg-white/10 text-[9px] font-bold tracking-tight"
        style="color: rgba(255,255,255,0.7); border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);">
        {{ densityLabel }}
      </button>
      <div class="w-px h-6" style="background: rgba(255,255,255,0.15);"></div>
      <button @click="toggleTheme" :title="theme === 'tokyo' ? t('header.themeLight') : t('header.themeDark')"
        class="flex items-center justify-center w-8 h-8 rounded-lg transition hover:bg-white/10"
        style="border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);">
        <component :is="icons.Moon" v-if="theme === 'light'" :size="17" style="color: white" :stroke-width="1.8" />
        <component :is="icons.Sun" v-else :size="17" style="color: #ff9e64" :stroke-width="1.8" />
      </button>
      <div class="relative" ref="accentBtnRef" @click.stop>
        <button @click="openAccentPop" :title="t('header.accentHint')"
          class="flex items-center justify-center w-8 h-8 rounded-lg transition hover:bg-white/10"
          style="border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);">
          <span class="w-[18px] h-[18px] rounded-md ring-2 ring-white/30" :style="{ background: accent ? ACCENTS[accent].accent : 'var(--accent)' }"></span>
        </button>
      </div>
    </div>
  </header>
  <Teleport to="body">
    <div v-if="accentOpen" @click.stop class="fixed z-[100]"
      :style="{ top: accentPopStyle.top, right: accentPopStyle.right }">
      <div class="p-2 rounded-xl shadow-xl w-[220px]"
        style="background: #0f172a; border: 1px solid rgba(255,255,255,0.12); box-shadow: 0 24px 56px -16px rgba(0,0,0,0.6);">
        <div class="px-1 pb-1.5 flex items-center justify-between">
          <span class="text-[11px] font-bold uppercase tracking-wide" style="color: rgba(255,255,255,0.7)">{{ t('header.accentTitle') }}</span>
          <button @click="resetAccent" class="text-[10px] font-bold underline" :class="accent ? 'text-slate-300 hover:text-white' : 'opacity-40 pointer-events-none text-slate-400'">{{ t('header.accentAuto') }}</button>
        </div>
        <div class="grid grid-cols-4 gap-1.5">
          <button v-for="(a, key) in ACCENTS" :key="key" @click="pickAccent(key)"
            class="w-9 h-9 rounded-lg transition-transform hover:scale-110 flex items-center justify-center"
            :style="{ background: a.accent }" :title="a.label">
            <span v-if="accent === key" class="text-[13px] font-bold text-white" style="text-shadow: 0 1px 2px rgba(0,0,0,0.45)">✓</span>
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getTheme, setTheme } from '../../utils/theme'
import { getFont, setFont } from '../../utils/font'
import { getDensity, setDensity } from '../../utils/density'
import { ACCENTS, getAccent, setAccent } from '../../utils/accent'
import { iconLib, toggleIconLib } from '../../utils/iconLib'
import { useIcons } from '../../composables/useIcons'
import LanguageSwitcher from '../LanguageSwitcher.vue'

defineEmits(['toggleSidebar'])

const { t } = useI18n()
const route = useRoute()
const isMobile = ref(false)
const theme = ref(getTheme())
const font = ref(getFont())
const density = ref(getDensity())
const accent = ref(getAccent())
const accentOpen = ref(false)
const accentPopStyle = reactive({ top: '0px', right: '0px' })
const accentBtnRef = ref(null)
const icons = useIcons()
const FONT_ORDER = ['combo', 'cascadia', 'bodoni', 'consolas', 'nerd', 'sans']
const FONT_LABEL = { combo: 'CMB', cascadia: 'CSC', bodoni: 'BDN', consolas: 'CON', nerd: 'NRD', sans: 'SNS' }
const fontLabel = computed(() => FONT_LABEL[font.value] || 'FNT')
const densityLabel = computed(() => density.value === 'compact' ? 'CMP' : 'COM')

function cycleFont() {
  const idx = FONT_ORDER.indexOf(font.value)
  font.value = setFont(FONT_ORDER[(idx + 1) % FONT_ORDER.length])
}

function cycleDensity() {
  density.value = setDensity(density.value === 'compact' ? 'comfortable' : 'compact')
}

function toggleTheme() {
  theme.value = setTheme(theme.value === 'tokyo' ? 'light' : 'tokyo')
}

function openAccentPop() {
  accentOpen.value = !accentOpen.value
  if (accentOpen.value && accentBtnRef.value) {
    const r = accentBtnRef.value.getBoundingClientRect()
    accentPopStyle.top = `${Math.round(r.bottom + 8)}px`
    accentPopStyle.right = `${Math.max(8, Math.round(window.innerWidth - r.right))}px`
  }
}

function pickAccent(key) {
  accent.value = setAccent(key)
  accentOpen.value = false
}

function resetAccent() {
  accent.value = setAccent(null)
  accentOpen.value = false
}

function onDocClick() {
  if (accentOpen.value) accentOpen.value = false
}

function onKeydown(e) {
  if (e.key === 'Escape' && accentOpen.value) accentOpen.value = false
}

function checkViewport() {
  isMobile.value = window.innerWidth < 768
  if (accentOpen.value && accentBtnRef.value) {
    const r = accentBtnRef.value.getBoundingClientRect()
    accentPopStyle.top = `${Math.round(r.bottom + 8)}px`
    accentPopStyle.right = `${Math.max(8, Math.round(window.innerWidth - r.right))}px`
  }
}

onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})

const titles = computed(() => ({
  '/': t('header.titles.dashboard'),
  '/bookings': t('header.titles.bookings'),
  '/receipts': t('header.titles.receipts'),
  '/flights': t('header.titles.flights'),
  '/mawbs': t('header.titles.mawbs'),
  '/load-planning': t('header.titles.loadPlanning'),
  '/ulds': t('header.titles.ulds'),
  '/exports': 'Reviews / Audit',
  '/users': t('header.titles.users'),
  '/settings': t('header.titles.settings'),
  '/security': t('header.titles.security'),
}))
const title = computed(() => titles.value[route.path] || 'AirCargo')
const date = computed(() => {
  const localeCode = t('common.monthsShort[0]') === 'Jan' ? 'en-US' : 'es-DO'
  return new Intl.DateTimeFormat(localeCode, { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date())
})
</script>
<style scoped>
@media (max-width: 767px) {
  header {
    padding-top: max(0px, env(safe-area-inset-top));
  }
}
@media (max-width: 480px) {
  .header-actions {
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .header-actions::-webkit-scrollbar { display: none; }
}
</style>
