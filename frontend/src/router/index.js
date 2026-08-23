import { createRouter, createWebHistory } from 'vue-router'

const viewPermissions = {
  '/':            'DASHBOARD',
  '/flights':     'FLIGHTS',
  '/load-planning': 'LOAD_PLANNING',
  '/ulds':        'ULDS',
  '/mawbs':       'MAWBS',
  '/bookings':    'BOOKINGS',
  '/receipts':    'RECEIPTS',
  '/users':       'USERS',
  '/settings':    'SETTINGS',
  '/security':    'SECURITY',
  '/exports':     'EXPORTS',
  '/api-catalog': 'API_CATALOG',
}

function hasPermission(role, path) {
  const view = viewPermissions[path]
  if (!view) return true
  switch (role) {
    case 'SUPER_USER': return true
    case 'ADMIN': return ['DASHBOARD', 'FLIGHTS', 'MAWBS', 'LOAD_PLANNING', 'ULDS', 'BOOKINGS', 'RECEIPTS', 'USERS', 'SETTINGS', 'SECURITY', 'EXPORTS'].includes(view)
    case 'READ_ONLY': return true
    case 'WAREHOUSE_ASSISTANT': return view === 'RECEIPTS' || view === 'DASHBOARD'
    case 'OPERATIONS': return ['DASHBOARD', 'FLIGHTS', 'MAWBS', 'LOAD_PLANNING', 'ULDS'].includes(view)
    case 'TRAFFIC': return ['DASHBOARD', 'BOOKINGS', 'MAWBS', 'LOAD_PLANNING', 'ULDS'].includes(view)
    case 'LOAD_PLANNER': return ['DASHBOARD', 'FLIGHTS', 'LOAD_PLANNING', 'ULDS'].includes(view)
    case 'BI_USER': return ['DASHBOARD', 'BI', 'API_CATALOG', 'SETTINGS'].includes(view)
    default: return false
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('../views/PrivacyPolicyView.vue'),
    },
    {
      path: '/set-password',
      name: 'set-password',
      component: () => import('../views/SetPasswordView.vue'),
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue'),
    },
    {
      path: '/mfa-setup',
      name: 'mfa-setup',
      component: () => import('../views/MfaSetupView.vue'),
    },
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { view: 'DASHBOARD' },
    },
    {
      path: '/flights',
      name: 'flights',
      component: () => import('../views/FlightsView.vue'),
      meta: { view: 'FLIGHTS' },
    },
    {
      path: '/load-planning',
      name: 'load-planning',
      component: () => import('../views/LoadPlanningView.vue'),
      meta: { view: 'LOAD_PLANNING' },
    },
    {
      path: '/ulds',
      name: 'ulds',
      component: () => import('../views/UldsView.vue'),
      meta: { view: 'ULDS' },
    },
    {
      path: '/mawbs',
      name: 'mawbs',
      component: () => import('../views/MawbsView.vue'),
      meta: { view: 'MAWBS' },
    },
    {
      path: '/bookings',
      name: 'bookings',
      component: () => import('../views/BookingsView.vue'),
      meta: { view: 'BOOKINGS' },
    },
    {
      path: '/receipts',
      name: 'receipts',
      component: () => import('../views/WarehouseReceiptsView.vue'),
      meta: { view: 'RECEIPTS' },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../views/UsersView.vue'),
      meta: { view: 'USERS' },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { view: 'SETTINGS' },
    },
    {
      path: '/security',
      name: 'security',
      component: () => import('../views/SecurityView.vue'),
      meta: { view: 'SECURITY' },
    },
    {
      path: '/exports',
      name: 'exports',
      component: () => import('../views/ExportsView.vue'),
      meta: { view: 'EXPORTS' },
    },
    {
      path: '/api-catalog',
      name: 'api-catalog',
      component: () => import('../views/ApiCatalogView.vue'),
      meta: { view: 'API_CATALOG' },
    },
    {
      path: '/home',
      redirect: '/'
    },
  ]
})

const publicPaths = ['/login', '/set-password', '/change-password', '/mfa-setup', '/privacy']

router.beforeEach((to) => {
  const stored = localStorage.getItem('aircargo_auth')
  if (!publicPaths.includes(to.path) && !stored) {
    return '/login'
  }
  if (publicPaths.includes(to.path)) {
    if (stored) {
      const parsed = JSON.parse(stored)
      if (parsed.token && parsed.selectedSiteId) {
        return '/'
      }
    }
    return
  }
  if (to.path !== '/login' && stored) {
    try {
      const { role, selectedSiteId, token } = JSON.parse(stored)
      if (token && !selectedSiteId) {
        return '/login'
      }
      if (role && to.meta?.view && !hasPermission(role, to.path)) {
        return '/'
      }
    } catch {}
  }
})

export default router
