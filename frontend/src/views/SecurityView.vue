<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div>
        <h1 class="ds-title">{{ t('security.title') }}</h1>
        <p class="ds-subtitle">{{ t('security.subtitle') }}</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="ds-stat">
          <span class="h-2 w-2 rounded-full bg-green-500"></span>
          {{ t('security.activeSessions', { n: sessions.length }) }}
        </span>
        <button @click="loadAll" class="ds-btn-secondary">
          <IconRefresh :size="14" /> {{ t('common.refresh') }}
        </button>
      </div>
    </header>

    <section class="mb-6">
      <h2 class="text-[13px] font-black uppercase tracking-wider text-slate-700 mb-2 font-mono">{{ t('security.activeSessionsTitle') }}</h2>
      <div class="ds-table-section">
        <div class="overflow-auto">
          <table class="w-full text-[12px] font-mono">
            <thead><tr class="bg-slate-100 border-b border-slate-200">
              <th class="text-left px-3 py-2">{{ t('security.table.user') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.role') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.lastHeartbeat') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.lastLogin') }}</th>
              <th class="text-center px-3 py-2">{{ t('security.table.status') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="s in sessions" :key="s.userId" class="border-b border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2 font-semibold">{{ s.fullName || s.email }}</td>
                <td class="px-3 py-2"><span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600">{{ s.role }}</span></td>
                <td class="px-3 py-2 text-slate-500">{{ formatTime(s.lastHeartbeat) }}</td>
                <td class="px-3 py-2 text-slate-500">{{ formatTime(s.lastLogin) }}</td>
                <td class="px-3 py-2 text-center"><span class="w-2 h-2 rounded-full inline-block bg-green-500"></span></td>
              </tr>
              <tr v-if="!sessions.length"><td colspan="5" class="text-center py-6 text-slate-400">{{ t('security.noSessions') }}</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="mb-6">
      <div class="flex items-center justify-between mb-2">
        <h2 class="text-[13px] font-black uppercase tracking-wider text-slate-700 font-mono">{{ t('security.auditLogTitle') }}</h2>
        <select v-model="auditFilter" class="ds-input text-[11px] py-1 px-2 min-w-[120px]">
          <option value="">{{ t('security.allEvents') }}</option>
          <option v-for="a in auditActions" :key="a" :value="a">{{ a }}</option>
        </select>
      </div>
      <div class="ds-table-section">
        <div class="overflow-auto" style="max-height: 400px">
          <table class="w-full text-[12px] font-mono">
            <thead class="sticky top-0"><tr class="bg-slate-100 border-b border-slate-200">
              <th class="text-left px-3 py-2">{{ t('security.table.timestamp') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.user') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.action') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.entity') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.ipAddress') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.details') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="log in filteredAuditLogs" :key="log.id" class="border-b border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2 text-slate-500 whitespace-nowrap">{{ formatTime(log.createdAt) }}</td>
                <td class="px-3 py-2 font-semibold">{{ log.fullName || log.email }}</td>
                <td class="px-3 py-2"><span class="px-1.5 py-0.5 rounded text-[10px] font-bold" :class="actionBadgeClass(log.action)">{{ log.action }}</span></td>
                <td class="px-3 py-2 text-slate-500">{{ log.entityType }}</td>
                <td class="px-3 py-2 text-slate-500 font-mono">{{ log.ipAddress || '—' }}</td>
                <td class="px-3 py-2 text-slate-500 max-w-[200px] truncate">{{ log.details || '—' }}</td>
              </tr>
              <tr v-if="!filteredAuditLogs.length"><td colspan="6" class="text-center py-6 text-slate-400">{{ t('security.noAuditLogs') }}</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section>
      <h2 class="text-[13px] font-black uppercase tracking-wider text-slate-700 mb-2 font-mono">{{ t('security.userBlockTitle') }}</h2>
      <div class="ds-table-section">
        <div class="overflow-auto">
          <table class="w-full text-[12px] font-mono">
            <thead><tr class="bg-slate-100 border-b border-slate-200">
              <th class="text-left px-3 py-2">{{ t('security.table.user') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.role') }}</th>
              <th class="text-left px-3 py-2">{{ t('security.table.email') }}</th>
              <th class="text-center px-3 py-2">{{ t('security.table.status') }}</th>
              <th class="text-center px-3 py-2">{{ t('security.table.actions') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="u in allUsers" :key="u.id" class="border-b border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2 font-semibold">{{ u.fullName || '—' }}</td>
                <td class="px-3 py-2"><span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600">{{ u.role }}</span></td>
                <td class="px-3 py-2 text-slate-500">{{ u.email }}</td>
                <td class="px-3 py-2 text-center">
                  <span v-if="u.blocked" class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-700">{{ t('security.blocked') }}</span>
                  <span v-else-if="u.isActive" class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-green-100 text-green-700">{{ t('security.active') }}</span>
                  <span v-else class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-500">{{ t('security.inactive') }}</span>
                </td>
                <td class="px-3 py-2 text-center">
                  <button v-if="u.id !== auth.userId" @click="toggleBlock(u)"
                    class="text-[11px] font-bold px-2 py-1 rounded transition-all"
                    :class="u.blocked ? 'bg-green-100 text-green-700 hover:bg-green-200' : 'bg-red-100 text-red-700 hover:bg-red-200'">
                    {{ u.blocked ? t('security.unblock') : t('security.block') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/client'
import { useToastStore } from '@/stores/toast'
import { IconRefresh } from '@tabler/icons-vue'

const { t } = useI18n()
const auth = useAuthStore()
const toast = useToastStore()
const sessions = ref([])
const auditLogs = ref([])
const allUsers = ref([])
const auditFilter = ref('')
const auditActions = ['LOGIN', 'PASSWORD_SET', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'USER_BLOCKED', 'USER_UNBLOCKED', 'CREATE', 'UPDATE', 'DELETE', 'MFA_ENABLED', 'MFA_DISABLED', 'MFA_LOCKED', 'MFA_UNLOCKED']

const filteredAuditLogs = computed(() => {
  if (!auditFilter.value) return auditLogs.value
  return auditLogs.value.filter(l => l.action === auditFilter.value)
})

function formatTime(ts) {
  if (!ts) return '\u2014'
  return new Date(ts).toLocaleString()
}

function actionBadgeClass(action) {
  if (['LOGIN'].includes(action)) return 'bg-blue-100 text-blue-700'
  if (['PASSWORD_SET', 'PASSWORD_CHANGED', 'PASSWORD_RESET'].includes(action)) return 'bg-amber-100 text-amber-700'
  if (['USER_BLOCKED', 'USER_UNBLOCKED', 'MFA_LOCKED', 'MFA_UNLOCKED'].includes(action)) return 'bg-red-100 text-red-700'
  if (['CREATE', 'USER_CREATED'].includes(action)) return 'bg-purple-100 text-purple-700'
  if (['DELETE', 'USER_DELETED'].includes(action)) return 'bg-red-100 text-red-700'
  return 'bg-slate-100 text-slate-600'
}

const POLL_INTERVAL_MS = 30000
let pollTimer = null

async function loadAll() {
  const [sessRes, auditRes, usersRes] = await Promise.allSettled([
    api.get('/audit-logs/connected'),
    api.get('/audit-logs/security'),
    api.get('/users'),
  ])
  if (sessRes.status === 'fulfilled') {
    sessions.value = sessRes.value.data || []
  } else {
    console.error('Failed to load connected sessions:', sessRes.reason)
  }
  if (auditRes.status === 'fulfilled') {
    auditLogs.value = auditRes.value.data || []
  } else {
    console.error('Failed to load audit logs:', auditRes.reason)
  }
  if (usersRes.status === 'fulfilled') {
    const ud = usersRes.value.data
    allUsers.value = Array.isArray(ud) ? ud : (ud?.content || [])
  } else {
    console.error('Failed to load users:', usersRes.reason)
  }
}

async function toggleBlock(user) {
  const action = user.blocked ? 'unblock' : 'block'
  const name = user.fullName || user.email
  if (!confirm(t('security.confirmBlock', { action, name }))) return
  try {
    await api.post('/auth/' + action + '/' + user.id)
    user.blocked = !user.blocked
    toast.success(user.blocked ? t('security.blockedUser', { name }) : t('security.unblockedUser', { name }))
  } catch (e) {
    toast.error(e.response?.data?.error || t('security.blockError'))
  }
}

onMounted(() => {
  loadAll()
  pollTimer = setInterval(loadAll, POLL_INTERVAL_MS)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>
