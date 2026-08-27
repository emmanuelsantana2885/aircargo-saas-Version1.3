<template>
  <div class="p-3 md:p-5 bg-white text-slate-900 font-sans antialiased select-none min-h-screen">
    <div class="max-w-7xl mx-auto">
    <div class="ds-section-header mb-4">
      <h1 class="ds-title">{{ t('users.title') }}</h1>
      <div class="flex gap-1">
        <button v-for="tab in tabs" :key="tab.key" @click="activeTab = tab.key"
          :class="activeTab === tab.key ? 'ds-btn-primary' : 'ds-btn-secondary'">
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- ────────────── TAB: CONNECTED USERS ────────────── -->
    <template v-if="activeTab === 'connected'">
      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 700px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th style="width: 16px"></th>
              <th>{{ t('users.table.name') }}</th>
              <th>{{ t('users.table.email') }}</th>
              <th>{{ t('users.table.role') }}</th>
              <th>{{ t('users.table.lastHeartbeat') }}</th>
              <th>{{ t('users.table.lastLogin') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in connected" :key="u.userId"
              class="border-b border-slate-100 transition-colors hover:bg-blue-50">
              <td><span class="w-1.5 h-1.5 rounded-full inline-block bg-green-500"></span></td>
              <td class="font-medium text-slate-900">{{ u.fullName || u.email }}</td>
              <td class="text-slate-500">{{ u.email }}</td>
              <td><span class="ds-label bg-slate-100 px-2 py-0.5 rounded">{{ roleLabel(u.role) }}</span></td>
              <td class="text-[12px] text-slate-500">{{ formatDate(u.lastHeartbeat) }}</td>
              <td class="text-[12px] text-slate-500">{{ formatDate(u.lastLogin) }}</td>
            </tr>
            <tr v-if="connected.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-sm italic text-slate-400">{{ t('users.noConnected') }}</td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </template>
    <template v-if="activeTab === 'audit'">
      <div class="flex items-center justify-between mb-3">
        <div class="flex gap-2">
          <select v-model="filterUser" class="ds-input !w-auto !py-1">
            <option value="">{{ t('users.audit.allUsers') }}</option>
            <option v-for="u in userOptions" :key="u.id" :value="u.id">{{ u.email }}</option>
          </select>
          <button @click="loadLogs" class="ds-btn-primary !px-3 !py-1 !text-[12px]">{{ t('common.update') }}</button>
        </div>
      </div>
      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 600px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th>{{ t('users.audit.date') }}</th>
              <th>{{ t('users.audit.user') }}</th>
              <th>{{ t('users.audit.action') }}</th>
              <th>{{ t('users.audit.entity') }}</th>
              <th>{{ t('users.audit.details') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(log, idx) in logs" :key="log.id"
              class="border-b border-slate-100 transition-colors hover:bg-blue-50"
              :class="idx % 2 !== 0 ? 'bg-slate-50/50' : ''">
              <td class="whitespace-nowrap text-[12px] text-slate-500">{{ formatDate(log.createdAt) }}</td>
              <td class="text-slate-900">{{ log.fullName || log.email || '—' }}</td>
              <td><span class="px-1.5 py-0.5 rounded text-[12px] font-medium" :style="actionColor(log.action)">{{ log.action }}</span></td>
              <td class="text-slate-900">{{ log.entityType || '—' }}</td>
              <td class="max-w-xs truncate text-[12px] text-slate-500">{{ log.details || '—' }}</td>
            </tr>
            <tr v-if="logs.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm italic text-slate-400">{{ t('users.audit.empty') }}</td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </template>

    <!-- ────────────── TAB: ROLES & PERMISOS ────────────── -->
    <template v-if="activeTab === 'roles'">
      <!-- Role selector -->
      <div class="flex items-center gap-4 mb-4">
        <label class="ds-label shrink-0">{{ t('users.rolesTitle') }}</label>
        <select v-model="selectedRole" @change="onRoleChange"
          class="ds-input !w-auto min-w-[200px]"
          :class="selectedRole ? '!bg-green-50 !text-green-800 !border-2 !border-green-500 !font-semibold' : ''">
          <option value="">{{ t('users.selectRole') }}</option>
          <option v-for="r in allRoles" :key="r.role" :value="r.role">
            {{ roleLabel(r.role) }} ({{ countAccess(r.views) }}/{{ r.views.length }})
          </option>
        </select>

        <button @click="showConnectedOnly = !showConnectedOnly"
          class="ds-btn-secondary !px-3 !py-1.5 !text-[12px]"
          :class="showConnectedOnly ? '!bg-green-500 !text-white !border-green-500' : ''">
          <span class="w-1.5 h-1.5 rounded-full" :class="showConnectedOnly ? 'bg-white' : 'bg-slate-400'"></span>
          {{ showConnectedOnly ? t('users.tabs.connected') : t('common.all') }}
        </button>
        <span v-if="roleUsers.length" class="ds-stat">{{ roleUsers.length }} usuario(s)</span>
      </div>

      <div v-if="selectedRole" class="space-y-5">
        <!-- Users by role -->
        <div class="ds-table-section">
          <div class="ds-section-header px-4 py-2">
            <span class="ds-label">{{ showConnectedOnly ? 'Connected users with role' : 'All users with role' }} {{ roleLabel(selectedRole) }}</span>
          </div>
          <div class="table-scroll-wrapper flex-1 min-h-0">
          <table class="w-full text-sm" style="min-width: 700px">
            <thead>
              <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2 [&>th]:text-left [&>th]:font-semibold">
                <th>Nombre</th>
                <th>Email</th>
                <th>Último Login</th>
                <th class="text-center" style="width: 100px">Transacciones</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in roleUsers" :key="userIdOf(u)"
                class="border-t transition-all cursor-pointer"
                :class="selectedUserId === userIdOf(u)
                  ? 'bg-blue-50 border-l-4 border-l-blue-600 font-semibold'
                  : 'border-slate-100 hover:bg-slate-50'"
                @click="selectUser(u)">
                <td :class="selectedUserId === userIdOf(u) ? 'text-blue-700' : 'text-slate-900'">
                  <span class="w-1.5 h-1.5 rounded-full inline-block mr-1.5" :class="u.userId ? 'bg-green-500' : 'bg-slate-300'"></span>
                  {{ u.fullName || '—' }}
                </td>
                <td :class="selectedUserId === userIdOf(u) ? 'text-blue-700' : 'text-slate-500'">{{ u.email }}</td>
                <td class="text-[12px]" :class="selectedUserId === userIdOf(u) ? 'text-blue-700' : 'text-slate-500'">{{ formatDate(u.lastLogin || u.lastHeartbeat) }}</td>
                <td class="text-center">
                  <span v-if="selectedUserId === userIdOf(u)" class="text-[12px] font-medium px-2 py-0.5 rounded bg-blue-600 text-white">
                    {{ userAuditLogs.length }} eventos
                  </span>
                  <span v-else class="text-[12px] hover:text-blue-600 transition-colors text-slate-400">Ver</span>
                </td>
              </tr>
              <tr v-if="!roleUsers.length">
                <td colspan="4" class="px-4 py-8 text-center text-sm italic text-slate-400">No hay usuarios con este rol</td>
              </tr>
            </tbody>
          </table>
          </div>
        </div>

        <!-- User audit transactions -->
        <div v-if="selectedUserId" class="ds-table-section">
          <div class="ds-section-header px-4 py-2">
            <span class="ds-label">Transacciones de {{ selectedUserFullName }}</span>
            <span class="ds-stat">{{ userAuditLogs.length }} registros</span>
          </div>
          <div class="overflow-x-auto flex-1 min-h-0">
            <table class="w-full text-sm" style="min-width: 900px">
              <thead>
                <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-3 [&>th]:py-2 [&>th]:text-left [&>th]:font-semibold">
                  <th style="width: 90px"># Transacción</th>
                  <th style="width: 130px">{{ t('users.audit.date') }}</th>
                  <th>{{ t('users.audit.action') }}</th>
                  <th>{{ t('users.audit.entity') }}</th>
                  <th style="width: 100px">{{ t('users.audit.entityId') }}</th>
                  <th>{{ t('users.audit.details') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(log, idx) in userAuditLogs" :key="log.id"
                  class="border-b border-slate-100 transition-colors hover:bg-blue-50"
                  :class="idx % 2 !== 0 ? 'bg-slate-50/50' : ''">
                  <td class="font-mono text-[11px] text-blue-600 font-semibold">{{ (log.id || '').slice(0, 8) }}</td>
                  <td class="whitespace-nowrap text-[12px] text-slate-500">{{ formatDate(log.createdAt) }}</td>
                  <td><span class="px-1.5 py-0.5 rounded text-[11px] font-medium" :style="actionColor(log.action)">{{ log.action }}</span></td>
                  <td class="text-[12px] text-slate-900">{{ log.entityType || '—' }}</td>
                  <td class="font-mono text-[11px] text-slate-500">{{ (log.entityId || '').slice(0, 8) || '—' }}</td>
                  <td class="max-w-[200px] truncate text-[12px] text-slate-500">{{ log.details || '—' }}</td>
                </tr>
                <tr v-if="!userAuditLogs.length">
                  <td colspan="6" class="px-4 py-8 text-center text-sm italic text-slate-400">{{ t('users.audit.empty') }} para este usuario</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Role permission grid -->
        <div v-if="selectedRoleData" class="ds-table-section">
          <div class="ds-section-header px-4 py-2">
            <span class="ds-label">Permisos del rol {{ roleLabel(selectedRole) }}</span>
            <div class="flex gap-2">
              <button @click="toggleAllViews(true)" class="ds-btn-secondary !px-2 !py-1 !text-[11px]">Select All</button>
              <button @click="toggleAllViews(false)" class="ds-btn-secondary !px-2 !py-1 !text-[11px]">Deselect All</button>
              <button @click="saveRolePermissions"
                class="px-3 py-1 rounded text-[11px] font-bold transition-all"
                :class="hasChanges
                  ? 'bg-green-600 text-white hover:bg-green-700'
                  : 'bg-slate-200 text-slate-400 cursor-not-allowed'"
                :disabled="!hasChanges">
                {{ saving ? t('common.saving') : t('common.save') }}
              </button>
            </div>
          </div>
          <div v-for="cat in categories" :key="cat" class="border-b border-slate-100">
            <div class="px-4 py-1.5 text-[11px] font-bold uppercase tracking-wider bg-slate-50 text-slate-500">
              {{ categoryLabel(cat) }}
              <span class="ml-2 font-normal normal-case">{{ catViews(cat).length }} {{ t('users.tabItems') }}</span>
            </div>
            <div class="divide-y divide-slate-100">
              <div v-for="v in catViews(cat)" :key="v.viewCode"
                class="flex items-center justify-between px-4 py-2 hover:bg-slate-50 transition-colors">
                <div class="flex items-center gap-3 min-w-0">
                  <button @click="toggleView(v.viewCode)"
                    class="w-7 h-4 rounded-sm border transition-all shrink-0 relative flex items-center"
                    :class="localPerms[v.viewCode]
                      ? 'bg-blue-600 border-blue-600'
                      : 'bg-white border-slate-300'">
                    <span class="w-[11px] h-[11px] rounded-sm absolute transition-all"
                      :class="localPerms[v.viewCode]
                        ? 'bg-white left-[11px]'
                        : 'bg-slate-400 left-[1px]'"></span>
                  </button>
                  <div class="min-w-0">
                    <div class="text-[12px] font-semibold text-slate-900">{{ v.viewName }}</div>
                    <div class="text-[11px] truncate max-w-md text-slate-500">{{ v.viewDescription }}</div>
                  </div>
                </div>
                <div class="text-[10px] font-mono px-1.5 py-0.5 rounded shrink-0" :class="localPerms[v.viewCode] ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-400'">
                  {{ localPerms[v.viewCode] ? 'AUTHORIZED' : 'RESTRICTED' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- No role selected -->
      <div v-if="!selectedRole" class="flex items-center justify-center h-64 text-sm italic text-slate-400">
        Selecciona un rol del menú desplegable para administrar permisos y ver usuarios.
      </div>

      <!-- ── View Master Data: mini table ── -->
      <div class="mt-6 ds-table-section">
        <div class="ds-section-header px-4 py-2">
          <span class="ds-label">Catálogo de Transacciones</span>
          <button @click="openViewEditor(null)" class="ds-btn-primary !px-3 !py-1.5 !text-[12px]">+ Nueva Transacción</button>
        </div>
        <div class="table-scroll-wrapper flex-1 min-h-0">
        <table class="w-full text-sm" style="min-width: 800px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2 [&>th]:text-left [&>th]:font-semibold">
              <th>Código</th>
              <th>Nombre</th>
              <th>Categoría</th>
              <th>Descripción</th>
              <th class="text-center" style="width: 100px">Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="view in allViews" :key="view.id"
              class="border-b border-slate-100 transition-colors hover:bg-slate-50">
              <td class="font-mono text-[12px] text-slate-900">{{ view.code }}</td>
              <td class="text-sm font-medium text-slate-900">{{ view.name }}</td>
              <td class="text-[12px] text-slate-500">{{ categoryLabel(view.category) }}</td>
              <td class="text-[12px] text-slate-500">{{ view.description }}</td>
              <td class="text-center">
                <div class="flex gap-1 justify-center">
                  <button @click="openViewEditor(view)" class="ds-btn-secondary !px-2 !py-1 !text-[11px]">Editar</button>
                  <button @click="deleteView(view)" class="ds-btn-secondary !px-2 !py-1 !text-[11px]">Eliminar</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <!-- ── View Editor Modal ── -->
      <div v-if="showViewEditor" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ editingView ? 'Editar Transacción' : 'Nueva Transacción' }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código *</label>
              <input v-model="viewForm.code" maxlength="50" placeholder="NUEVA_VISTA" class="ds-input font-mono">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Nombre *</label>
              <input v-model="viewForm.name" maxlength="100" placeholder="Nombre de la vista" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Categoría</label>
              <select v-model="viewForm.category" class="ds-input">
                <option value="PRINCIPAL">Principal</option>
                <option value="OPERACIONES">Operaciones</option>
                <option value="CONFIGURACION">Configuración</option>
                <option value="ADMINISTRACION">Administración</option>
              </select>
            </div>
            <div>
              <label class="ds-label block mb-0.5">Descripción</label>
              <input v-model="viewForm.description" maxlength="255" placeholder="Descripción de la transacción" class="ds-input">
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveView" class="ds-btn-primary flex-1 justify-center">Guardar</button>
              <button @click="showViewEditor = false" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usersApi } from '../api/users'

const { t } = useI18n()
import { rolesApi } from '../api/roles'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import { extractError } from '../utils/error'

const toast = useToastStore()
const { confirm } = useConfirm()
const auth = useAuthStore()

const activeTab = ref('connected')
const tabs = computed(() => {
  const items = [
    { key: 'connected', label: t('users.tabs.connected') },
    { key: 'audit', label: t('users.tabs.audit') },
  ]
  if (auth.role === 'SUPER_USER' || auth.role === 'ADMIN') {
    items.push({ key: 'roles', label: t('users.tabs.roles') })
  }
  return items
})

const connected = ref([])
const logs = ref([])
const userOptions = ref([])
const filterUser = ref('')
const allRoles = ref([])
const allViews = ref([])
const selectedRole = ref(null)
const localPerms = ref({})
const saving = ref(false)
const showViewEditor = ref(false)
const editingView = ref(null)
const viewForm = ref({ code: '', name: '', description: '', category: 'PRINCIPAL' })
const allUsers = ref([])
const roleUsers = ref([])
const selectedUserId = ref(null)
const userAuditLogs = ref([])
const showConnectedOnly = ref(true)

const selectedUserFullName = computed(() => {
  if (!selectedUserId.value) return ''
  const u = connected.value.find(u => u.userId === selectedUserId.value)
    || allUsers.value.find(u => u.id === selectedUserId.value)
  return u ? (u.fullName || u.email) : ''
})

const selectedRoleData = computed(() => {
  if (!selectedRole.value) return null
  return allRoles.value.find(r => r.role === selectedRole.value) || null
})

const categories = computed(() => {
  if (!selectedRoleData.value) return []
  const cats = [...new Set(selectedRoleData.value.views.map(v => v.category))]
  return cats.sort()
})

function catViews(cat) {
  if (!selectedRoleData.value) return []
  return selectedRoleData.value.views.filter(v => v.category === cat)
}

const hasChanges = computed(() => {
  if (!selectedRoleData.value) return false
  return selectedRoleData.value.views.some(v => localPerms.value[v.viewCode] !== v.canAccess)
})

function countAccess(views) {
  return views.filter(v => v.canAccess).length
}

function categoryLabel(cat) {
  const labels = {
    PRINCIPAL: t('users.categories.principal'),
    OPERACIONES: t('users.categories.operations'),
    CONFIGURACION: t('users.categories.config'),
    ADMINISTRACION: t('users.categories.admin'),
  }
  return labels[cat] || cat
}

function roleLabel(r) {
  const labels = {
    READ_ONLY: 'Solo Lectura',
    WAREHOUSE_ASSISTANT: 'Warehouse Asst',
    OPERATIONS: 'Operations',
    TRAFFIC: 'Traffic',
    LOAD_PLANNER: 'Load Planner',
    ADMIN: 'Admin',
    SUPER_USER: 'SuperUser',
    BI_USER: 'BI User',
  }
  return labels[r] || r
}

function formatDate(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  return d.toLocaleDateString('es-DO', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function actionColor(action) {
  if (!action) return {}
  const a = action.toUpperCase()
  if (a.includes('CREATE') || a.includes('CREAR')) return { background: '#dcfce7', color: '#166534' }
  if (a.includes('UPDATE') || a.includes('EDIT') || a.includes('ACTUALIZ')) return { background: '#dbeafe', color: '#1e40af' }
  if (a.includes('DELETE') || a.includes('ELIMIN')) return { background: '#fee2e2', color: '#991b1b' }
  if (a.includes('LOGIN') || a.includes('PASSWORD') || a.includes('RESET')) return { background: '#fef3c7', color: '#92400e' }
  return { background: '#f1f5f9', color: '#475569' }
}

// ── Tab: Connected ──
async function loadConnected() {
  try {
    const res = await usersApi.getConnected()
    connected.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

// ── Tab: Audit ──
async function loadLogs() {
  try {
    const res = await usersApi.getAuditLogs(filterUser.value || undefined)
    logs.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

async function loadUserOptions() {
  try {
    const res = await usersApi.getAll()
    userOptions.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

// ── Tab: Roles ──
async function loadRoles() {
  try {
    const res = await rolesApi.getAllRoles()
    allRoles.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

async function loadViews() {
  try {
    const res = await rolesApi.getAllViews()
    allViews.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

async function loadAllUsers() {
  try {
    const res = await usersApi.getAll()
    allUsers.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

function filterRoleUsers() {
  if (!selectedRole.value) {
    roleUsers.value = []
    return
  }
  if (showConnectedOnly.value) {
    roleUsers.value = connected.value.filter(u => u.role === selectedRole.value)
  } else {
    roleUsers.value = allUsers.value.filter(u => u.role === selectedRole.value)
  }
}

function onRoleChange() {
  selectedUserId.value = null
  userAuditLogs.value = []
  if (!selectedRole.value) {
    roleUsers.value = []
    localPerms.value = {}
    return
  }
  filterRoleUsers()
  const data = allRoles.value.find(r => r.role === selectedRole.value)
  if (data) {
    localPerms.value = {}
    for (const v of data.views) {
      localPerms.value[v.viewCode] = v.canAccess
    }
  }
}

function userIdOf(u) {
  return u ? (u.userId || u.id) : null
}

async function selectUser(u) {
  selectedUserId.value = userIdOf(u)
  if (!selectedUserId.value) return
  try {
    const res = await usersApi.getAuditLogs(selectedUserId.value)
    userAuditLogs.value = res.data
  } catch (e) {
    toast.error(extractError(e))
    userAuditLogs.value = []
  }
}

function toggleView(viewCode) {
  localPerms.value[viewCode] = !localPerms.value[viewCode]
}

function toggleAllViews(val) {
  if (!selectedRoleData.value) return
  for (const v of selectedRoleData.value.views) {
    localPerms.value[v.viewCode] = val
  }
}

async function saveRolePermissions() {
  if (!selectedRole.value || !hasChanges.value) return
  saving.value = true
  try {
    await rolesApi.updateRole(selectedRole.value, localPerms.value)
      toast.success(`Permisos actualizados para ${roleLabel(selectedRole.value)}`)
    await loadRoles()
    onRoleChange()
  } catch (e) { toast.error(extractError(e)) }
  finally { saving.value = false }
}

// View CRUD
function openViewEditor(view) {
  editingView.value = view
  if (view) {
    viewForm.value = { code: view.code, name: view.name, description: view.description || '', category: view.category }
  } else {
    viewForm.value = { code: '', name: '', description: '', category: 'PRINCIPAL' }
  }
  showViewEditor.value = true
}

async function saveView() {
  if (!viewForm.value.code || !viewForm.value.name) {
    toast.error(t('users.toast.requiredFields'))
    return
  }
  try {
    if (editingView.value) {
      await rolesApi.updateView(editingView.value.id, viewForm.value)
      toast.success(t('users.toast.viewUpdated'))
    } else {
      await rolesApi.createView(viewForm.value)
      toast.success(t('users.toast.viewCreated'))
    }
    showViewEditor.value = false
    await Promise.all([loadViews(), loadRoles()])
    if (selectedRole.value) onRoleChange()
  } catch (e) { toast.error(extractError(e)) }
}

async function deleteView(view) {
  if (!(await confirm({ message: t('users.toast.confirmDeleteView', { name: view.name, code: view.code }), danger: true }))) return
  try {
    await rolesApi.deleteView(view.id)
    toast.success(t('users.toast.viewDeleted'))
    await Promise.all([loadViews(), loadRoles()])
    if (selectedRole.value) onRoleChange()
  } catch (e) { toast.error(extractError(e)) }
}

watch(showConnectedOnly, () => {
  if (selectedRole.value) filterRoleUsers()
})

const tabsLoaded = { connected: true, audit: false, roles: false }

watch(activeTab, (tab) => {
  if (tab === 'audit' && !tabsLoaded.audit) {
    tabsLoaded.audit = true
    loadLogs()
    loadUserOptions()
  }
  if (tab === 'roles' && !tabsLoaded.roles && (auth.role === 'SUPER_USER' || auth.role === 'ADMIN')) {
    tabsLoaded.roles = true
    Promise.all([loadRoles(), loadViews(), loadAllUsers()])
  }
})

let connectedTimer = null

onMounted(async () => {
  loadConnected()
  connectedTimer = setInterval(loadConnected, 30000)
})

onUnmounted(() => {
  if (connectedTimer) clearInterval(connectedTimer)
})
</script>
