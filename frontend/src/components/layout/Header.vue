<template>
  <header class="flex items-center justify-between px-4 md:px-6 border-b flex-shrink-0 flex-wrap gap-y-1 relative overflow-hidden"
    style="min-height: 44px; border-color: var(--border); background: linear-gradient(135deg, #1e293b 0%, #334155 25%, #1e3a5f 50%, #2d3a4e 75%, #1e293b 100%); background-size: 200% 200%;">
    <div class="absolute inset-0 opacity-[0.08]" style="background-image: repeating-linear-gradient(45deg, transparent, transparent 2px, rgba(148,163,184,0.3) 2px, rgba(148,163,184,0.3) 3px), repeating-linear-gradient(-45deg, transparent, transparent 3px, rgba(100,116,139,0.2) 3px, rgba(100,116,139,0.2) 4px);"></div>
    <div class="absolute inset-0 opacity-[0.04]" style="background-image: radial-gradient(circle at 30% 50%, rgba(148,163,184,0.4) 0%, transparent 60%), radial-gradient(circle at 70% 30%, rgba(100,116,139,0.3) 0%, transparent 50%);"></div>

    <div class="flex items-center gap-2 relative z-10">
      <!-- Mobile hamburger -->
      <button v-if="isMobile" @click="$emit('toggleSidebar')"
        class="flex items-center justify-center w-8 h-8 rounded hover:bg-white/10 transition lg:hidden">
        <component :is="icons.Menu" :size="20" :stroke-width="2" style="color: white" />
      </button>
      <component :is="icons.ChevronRight" :size="12" style="color: rgba(255,255,255,0.4)" :stroke-width="2" class="hidden sm:block" />
      <span class="text-[13px] md:text-xs font-bold uppercase text-white tracking-wide" style="text-shadow: 0 1px 2px rgba(0,0,0,0.3)">{{ title }}</span>
    </div>

    <div class="flex items-center gap-2 md:gap-4 relative z-10">
      <LanguageSwitcher />
      <span class="text-[12px] md:text-xs text-slate-300">{{ date }}</span>
      <button @click="toggleIconLib"
        :title="iconLib === 'tabler' ? 'Switch to Lucide icons' : 'Switch to Tabler icons'"
        class="flex items-center justify-center w-8 h-8 rounded hover:bg-white/10 transition text-[10px] font-bold"
        style="color: rgba(255,255,255,0.7)">
        {{ iconLib === 'tabler' ? 'TB' : 'LC' }}
      </button>
      <button @click="cycleFont" :title="t('header.fontHint')"
        class="flex items-center justify-center w-8 h-8 rounded hover:bg-white/10 transition text-[9px] font-bold tracking-tight"
        style="color: rgba(255,255,255,0.7)">
        {{ fontLabel }}
      </button>
      <button @click="cycleDensity" :title="t('header.densityHint')"
        class="flex items-center justify-center w-8 h-8 rounded hover:bg-white/10 transition text-[9px] font-bold tracking-tight"
        style="color: rgba(255,255,255,0.7)">
        {{ densityLabel }}
      </button>
      <button @click="toggleTheme" :title="theme === 'tokyo' ? t('header.themeLight') : t('header.themeDark')"
        class="flex items-center justify-center w-8 h-8 rounded hover:bg-white/10 transition">
        <component :is="icons.Moon" v-if="theme === 'light'" :size="17" style="color: white" :stroke-width="1.8" />
        <component :is="icons.Sun" v-else :size="17" style="color: #ff9e64" :stroke-width="1.8" />
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getTheme, setTheme } from '../../utils/theme'
import { getFont, setFont } from '../../utils/font'
import { getDensity, setDensity } from '../../utils/density'
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

function checkViewport() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
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
