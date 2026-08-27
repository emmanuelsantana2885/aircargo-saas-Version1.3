<template>
  <div v-if="isMobile && mobileOpen" @click="mobileOpen = false"
    class="fixed inset-0 bg-black/40 z-40 transition-opacity lg:hidden"></div>

  <aside :style="sidebarStyle"
    class="flex flex-col flex-shrink-0 h-full border-r transition-all duration-300 ease-out relative"
    :class="isMobile ? (mobileOpen ? 'translate-x-0' : '-translate-x-full') : ''"
    style="background: #ffffff; border-color: #e2e8f0; box-shadow: 4px 0 32px rgba(15,23,42,0.08)">

    <button v-if="!isMobile" @click="collapsed = !collapsed"
      class="absolute -right-3.5 top-5 z-20 w-7 h-7 flex items-center justify-center transition-opacity hover:opacity-70 active:opacity-50"
      style="background: #2563eb; color: white">
      <component :is="icons.LayoutSidebarFilled" :size="16" :stroke-width="2" />
    </button>

    <!-- Logo -->
    <div class="px-4 py-4 border-b relative overflow-hidden" style="border-color: #e2e8f0; background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 25%, #e2e8f0 50%, #f8fafc 75%, #ffffff 100%);">
      <div class="absolute inset-0 opacity-[0.05]" style="background-image: repeating-linear-gradient(45deg, transparent, transparent 2px, rgba(37,99,235,0.25) 2px, rgba(37,99,235,0.25) 3px), repeating-linear-gradient(-45deg, transparent, transparent 3px, rgba(37,99,235,0.15) 3px, rgba(37,99,235,0.15) 4px);"></div>
      <div class="absolute inset-0 opacity-[0.04]" style="background-image: radial-gradient(circle at 30% 50%, rgba(37,99,235,0.25) 0%, transparent 60%), radial-gradient(circle at 70% 30%, rgba(124,58,237,0.15) 0%, transparent 50%);"></div>
      <div class="flex items-center" :class="showCollapsed ? 'justify-center' : 'gap-2.5'">
        <div v-if="!showCollapsed" class="overflow-hidden whitespace-nowrap relative z-10">
          <div class="font-extrabold title" style="font-size: 18px; letter-spacing: 0.08em; color: #1e293b;">AirCargo</div>
          <div class="text-[13px] font-medium tracking-wide" style="color: #64748b">{{ auth.selectedSite?.code || 'SDQ' }} Operations</div>
        </div>
        <div v-else class="w-8 h-8 flex items-center justify-center shrink-0" style="background: #2563eb">
          <component :is="icons.PlaneDeparture" :size="20" color="white" :stroke-width="1.8" />
        </div>
      </div>
    </div>

    <!-- Nav -->
    <nav class="flex-1 px-2 py-4 space-y-0.5 overflow-y-auto overflow-x-hidden" style="background: #ffffff;">
      <div v-if="!showCollapsed" class="text-xs font-bold mb-2 px-2" style="color: #64748b; letter-spacing: .1em; text-transform: uppercase">{{ t('sidebar.siteLabel') }}</div>

      <RouterLink v-for="item in mainMenu" :key="item.path" :to="item.path"
        class="nav-link group flex items-center whitespace-nowrap rounded-lg transition-all duration-200 ease-out"
        :class="[showCollapsed ? 'justify-center px-0 py-2.5' : 'gap-3 px-3 py-2.5', isActive(item.path) ? 'nav-active' : 'nav-default']"
        :style="!isActive(item.path) ? { color: item.color } : {}"
        :title="showCollapsed ? item.label : ''"
        @click="isMobile && (mobileOpen = false)">
        <div class="ico-frame shrink-0" :class="isActive(item.path) && 'ico-frame-active'" :style="{ '--ic': item.color }">
          <component :is="item.icon" :size="showCollapsed ? 21 : 19" :stroke-width="1.5" :color="item.color" />
        </div>
        <template v-if="!showCollapsed">
          <span class="nav-label font-bold" :style="isActive(item.path) ? { borderBottom: `2px solid ${item.color}`, paddingBottom: '1px' } : {}">{{ item.label }}</span>
        </template>
      </RouterLink>

      <div v-if="!showCollapsed" class="text-xs font-bold mt-4 mb-2 px-2" style="color: #64748b; letter-spacing: .1em; text-transform: uppercase">{{ t('settings.title') }}</div>

      <RouterLink v-for="item in settingsMenu" :key="item.path" :to="item.path"
        class="nav-link group flex items-center whitespace-nowrap rounded-lg transition-all duration-200 ease-out"
        :class="[showCollapsed ? 'justify-center px-0 py-2.5' : 'gap-3 px-3 py-2.5', isActive(item.path) ? 'nav-active' : 'nav-default']"
        :style="!isActive(item.path) ? { color: item.color } : {}"
        :title="showCollapsed ? item.label : ''"
        @click="isMobile && (mobileOpen = false)">
        <div class="ico-frame shrink-0" :class="isActive(item.path) && 'ico-frame-active'" :style="{ '--ic': item.color }">
          <component :is="item.icon" :size="showCollapsed ? 21 : 19" :stroke-width="1.5" :color="item.color" />
        </div>
        <span v-if="!showCollapsed" class="nav-label font-bold" :style="isActive(item.path) ? { borderBottom: `2px solid ${item.color}`, paddingBottom: '1px' } : {}">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <!-- User -->
    <div class="px-2 py-3 border-t" style="border-color: #e2e8f0; background: #f8fafc;">
      <div class="flex items-center px-2 py-2" style="background: #f1f5f9; border-radius: 8px;"
        :class="showCollapsed ? 'justify-center' : 'gap-2.5'">
        <div class="w-8 h-8 flex items-center justify-center shrink-0 rounded-full"
          :style="{ background: roleIcon.bg, color: roleIcon.fg }">
          <component :is="roleIcon.icon" :size="16" :stroke-width="1.8" />
        </div>
        <template v-if="!showCollapsed">
          <div class="flex-1 min-w-0">
            <div class="text-xs font-bold truncate" style="color: #1e293b">{{ auth.fullName || auth.email }}</div>
            <div class="text-[11px] truncate" :style="{ color: roleIcon.fg }">{{ roleLabel }}</div>
          </div>
          <div class="flex items-center gap-1.5">
            <button @click="showPasswordChange = true" :title="t('sidebar.changePassword')" class="hover:opacity-70 transition-opacity">
              <component :is="icons.Key" :size="16" style="color: #64748b" :stroke-width="1.5" />
            </button>
            <button @click="handleLogout" :title="t('sidebar.logout')" class="hover:opacity-70 transition-opacity">
              <component :is="icons.Logout" :size="16" style="color: #64748b" :stroke-width="1.5" />
            </button>
          </div>
        </template>
      </div>
    </div>
  </aside>

  <PasswordChangeModal :show="showPasswordChange" @close="showPasswordChange = false" />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import { useIcons } from '../../composables/useIcons'
import PasswordChangeModal from '../PasswordChangeModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()
const icons = useIcons()
const collapsed = ref(false)
const mobileOpen = ref(false)
const isMobile = ref(false)
const isTablet = ref(false)
const showPasswordChange = ref(false)

const roleConfig = {
  SUPER_USER:        { iconKey: 'CrownFilled',       bg: 'rgba(234,179,8,.15)',  fg: '#b45309' },
  ADMIN:             { iconKey: 'ShieldLock',         bg: 'rgba(37,99,235,.12)',  fg: '#2563eb' },
  OPERATIONS:        { iconKey: 'AirTrafficControl',  bg: 'rgba(22,163,74,.12)',  fg: '#16a34a' },
  TRAFFIC:           { iconKey: 'ArrowsExchange',     bg: 'rgba(124,58,237,.12)', fg: '#7c3aed' },
  LOAD_PLANNER:      { iconKey: 'Scale',              bg: 'rgba(7,148,148,.12)',  fg: '#0891b2' },
  WAREHOUSE_ASSISTANT:{ iconKey: 'Forklift',          bg: 'rgba(217,119,6,.12)',  fg: '#d97706' },
  READ_ONLY:         { iconKey: 'Eye',                bg: 'rgba(100,116,139,.12)',fg: '#64748b' },
}
const roleIcon = computed(() => {
  const cfg = roleConfig[auth.role] || { iconKey: 'User', bg: 'rgba(100,116,139,.12)', fg: '#64748b' }
  return { icon: icons.value[cfg.iconKey], bg: cfg.bg, fg: cfg.fg }
})
const roleLabel = computed(() => t(`users.roles.${auth.role}`) || auth.role?.replace('_', ' ') || '')

const showCollapsed = computed(() => {
  if (isMobile.value) return false
  if (isTablet.value) return true
  return collapsed.value
})
const isActive = (path) => path === '/' ? route.path === '/' : route.path.startsWith(path)

const sidebarStyle = computed(() => {
  if (isMobile.value) return { width: '260px' }
  if (isTablet.value) return { width: '60px' }
  return { width: collapsed.value ? '60px' : 'var(--sidebar-width)' }
})

function checkViewport() {
  const w = window.innerWidth
  isMobile.value = w < 768
  isTablet.value = w >= 768 && w < 1024
  if (isMobile.value || isTablet.value) mobileOpen.value = false
}
function handleLogout() { auth.logout(); router.push('/login') }
defineExpose({ mobileOpen, isMobile })
onMounted(() => { checkViewport(); window.addEventListener('resize', checkViewport) })
onUnmounted(() => { window.removeEventListener('resize', checkViewport) })

const allMenuItems = computed(() => [
  { path: '/',              label: t('sidebar.dashboard'),   iconKey: 'Gauge',          view: 'DASHBOARD',     color: '#e11d48' },
  { path: '/bookings',      label: t('sidebar.bookings'),    iconKey: 'CalendarEvent',  view: 'BOOKINGS',      color: '#2563eb' },
  { path: '/receipts',      label: t('sidebar.receipts'),    iconKey: 'FileInvoice',    view: 'RECEIPTS',      color: '#d97706' },
  { path: '/flights',       label: t('sidebar.flights'),     iconKey: 'PlaneDeparture', view: 'FLIGHTS',       color: '#7c3aed' },
  { path: '/mawbs',         label: t('sidebar.mawbs'),       iconKey: 'ClipboardList',  view: 'MAWBS',         color: '#16a34a' },
  { path: '/load-planning', label: t('sidebar.loadPlanning'),iconKey: 'Route',          view: 'LOAD_PLANNING', color: '#475569' },
  { path: '/ulds',          label: t('sidebar.ulds'),        iconKey: 'Package',        view: 'ULDS',          color: '#0891b2' },
  { path: '/exports',       label: 'Reviews / Audit',        iconKey: 'LayoutGrid',     view: 'EXPORTS',       color: '#ea580c' },
].map(item => ({ ...item, icon: computed(() => icons.value[item.iconKey]) })))
const mainMenu = computed(() => allMenuItems.value.filter(item => auth.canView(item.view)))
const settingsMenu = computed(() => {
  const items = []
  if (auth.canView('USERS')) items.push({ path: '/users', label: t('sidebar.users'), icon: computed(() => icons.value.Users), color: '#334155' })
  if (auth.canView('SETTINGS')) items.push({ path: '/settings', label: t('sidebar.settings'), icon: computed(() => icons.value.Settings), color: '#6d28d9' })
  if (auth.canView('SECURITY')) items.push({ path: '/security', label: t('sidebar.security'), icon: computed(() => icons.value.Key), color: '#dc2626' })
  if (auth.canView('API_CATALOG')) items.push({ path: '/api-catalog', label: 'API Catalog', icon: computed(() => icons.value.Api), color: '#0e7490' })
  return items
})
</script>

<style scoped>
.nav-default { color: #64748b; }
.nav-default:hover { background: rgba(37,99,235,.05); color: #1e293b; }
.nav-active { background: rgba(37,99,235,.10); color: #1e293b; font-weight: 700; }
.nav-label { font-size: 14px; letter-spacing: 0.02em; }

/* Icon frame — light theme */
.ico-frame {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  border-radius: 8px; position: relative;
  border: 1.5px solid color-mix(in srgb, var(--ic) 20%, transparent);
  background: color-mix(in srgb, var(--ic) 4%, transparent);
  transition: all 0.2s ease;
}
.ico-frame::before {
  content: ''; position: absolute; top: -1px; right: -1px;
  width: 6px; height: 6px; border-top: 1.5px solid var(--ic); border-right: 1.5px solid var(--ic);
  border-radius: 0 3px 0 0; opacity: 0; transition: opacity 0.2s;
}
.nav-default:hover .ico-frame {
  border-color: color-mix(in srgb, var(--ic) 40%, transparent);
  background: color-mix(in srgb, var(--ic) 10%, transparent);
}
.ico-frame-active {
  border-color: color-mix(in srgb, var(--ic) 60%, transparent) !important;
  background: color-mix(in srgb, var(--ic) 15%, transparent) !important;
  border-width: 1.5px;
}
.ico-frame-active::before { opacity: 0.6; }
</style>
