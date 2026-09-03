<template>
  <div class="ds-page max-w-6xl mx-auto">
    <div class="ds-section-header mb-4">
      <h1 class="ds-title">{{ t('settings.title') }}</h1>
    </div>

    <!-- Tabs: en móvil scroll horizontal (evita desbordar/amontonar) -->
    <div class="flex gap-1 mb-4 settings-tabs">
      <button v-if="auth.role !== 'BI_USER'" @click="activeTab = 'users'"
        :class="activeTab === 'users' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.users') }}
      </button>
      <button v-if="auth.role === 'SUPER_USER'" @click="activeTab = 'sites'"
        :class="activeTab === 'sites' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.sites') }}
      </button>
      <button v-if="canManageSettings" @click="activeTab = 'airlines'"
        :class="activeTab === 'airlines' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.airlines') }}
      </button>
      <button v-if="canManageSettings" @click="activeTab = 'uldconfig'"
        :class="activeTab === 'uldconfig' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.uldConfig') }}
      </button>
      <button v-if="canManageSettings" @click="activeTab = 'commodities'"
        :class="activeTab === 'commodities' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.commodities') }}
      </button>
      <button v-if="canManageSettings" @click="openBackupsTab()"
        :class="activeTab === 'backups' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.backups') }}
      </button>
      <button v-if="canManageSettings || auth.role === 'BI_USER'" @click="activeTab = 'api'"
        :class="activeTab === 'api' ? 'ds-btn-primary' : 'ds-btn-secondary'">
        {{ t('settings.tabs.apiBi') }}
      </button>

    </div>

    <!-- ============ USERS TAB ============ -->
    <template v-if="activeTab === 'users'">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-3">
          <div class="relative flex-1 max-w-xs">
            <input v-model="searchQuery" :placeholder="t('settings.users.searchPlaceholder')"
              class="ds-input">
          </div>
          <span class="ds-stat">{{ t('settings.users.userCount', { n: filteredUsers.length }) }}</span>
        </div>
        <button @click="openCreate" class="ds-btn-primary">
          + {{ t('settings.users.newUser') }}
        </button>
      </div>

      <!-- Users table -->
      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 800px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th>{{ t('settings.users.email') }}</th>
              <th>{{ t('settings.users.fullName') }}</th>
              <th>{{ t('settings.users.role') }}</th>
              <th>{{ t('settings.users.sites') }}</th>
              <th class="text-center" style="width: 80px">{{ t('settings.users.active') }}</th>
              <th class="text-center" style="width: 100px">{{ t('settings.users.password') }}</th>
              <th class="text-center" style="width: 80px">{{ t('settings.users.mfa') }}</th>
              <th class="text-right" style="width: 240px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in filteredUsers" :key="user.id"
              class="border-b border-slate-100 transition-colors hover:bg-slate-50/80">
              <td class="text-slate-900">{{ user.email }}</td>
              <td class="text-slate-900">{{ user.fullName }}</td>
              <td>
                <span class="ds-label bg-slate-100 px-2 py-0.5 rounded">
                  {{ roleLabel(user.role) }}
                </span>
              </td>
              <td>
                <span v-for="site in userSiteNames(user.siteIds)" :key="site"
                  class="ds-label bg-slate-100 px-1.5 py-0.5 rounded mr-1 mb-0.5">
                  {{ site }}
                </span>
                <span v-if="!user.siteIds?.length" class="text-[12px] text-slate-400">—</span>
              </td>
              <td class="text-center">
                <span class="text-[12px] font-medium px-2 py-0.5 rounded"
                  :class="user.isActive ? 'bg-slate-200 text-slate-900' : 'bg-slate-100 text-slate-400'">
                  {{ user.isActive ? t('common.yes') : t('common.no') }}
                </span>
              </td>
              <td class="text-center">
                <span class="text-[12px] font-medium"
                  :class="user.mustChangePassword ? 'text-red-600' : (user.passwordSet ? 'text-slate-900' : 'text-slate-400')">
                  {{ user.mustChangePassword ? t('settings.users.passwordStatus.pending') : (user.passwordSet ? t('settings.users.passwordStatus.set') : t('settings.users.passwordStatus.none')) }}
                </span>
              </td>
              <td class="text-center">
                <span class="text-[12px] font-medium px-2 py-0.5 rounded"
                  :class="user.mfaEnabled
                    ? (user.mfaLocked
                      ? 'bg-red-50 text-red-800'
                      : 'bg-green-50 text-green-800')
                    : 'bg-slate-100 text-slate-400'">
                  {{ user.mfaLocked ? t('settings.users.mfaStatus.locked') : (user.mfaEnabled ? t('settings.users.mfaStatus.active') : t('settings.users.mfaStatus.inactive')) }}
                </span>
              </td>
              <td class="text-right">
                <div class="flex gap-1 justify-end flex-wrap">
                  <button @click="startEdit(user)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.edit') }}</button>
                  <button @click="resetPass(user)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('settings.users.resetPassword') }}</button>
                  <button @click="genTempPassword(user)"
                    class="px-2 py-1 rounded text-[12px] font-medium transition-all hover:brightness-110 bg-blue-50 text-blue-700">{{ t('settings.users.genTemp') }}</button>
                  <template v-if="user.mfaEnabled">
                    <button v-if="!user.mfaLocked" @click="lockMfaUser(user)"
                      class="px-2 py-1 rounded text-[12px] font-medium transition-all hover:brightness-110 bg-red-50 text-red-800">{{ t('settings.users.lockMfa') }}</button>
                    <button v-if="user.mfaLocked" @click="unlockMfaUser(user)"
                      class="px-2 py-1 rounded text-[12px] font-medium transition-all hover:brightness-110 bg-green-50 text-green-800">{{ t('settings.users.unlockMfa') }}</button>
                    <button @click="disableMfaUser(user)"
                      class="px-2 py-1 rounded text-[12px] font-medium transition-all hover:brightness-110 bg-red-50 text-red-800">{{ t('settings.users.disableMfa') }}</button>
                  </template>
                  <button v-if="!user.mfaEnabled" @click="openMfaSetup(user)"
                    class="px-2 py-1 rounded text-[12px] font-medium transition-all hover:brightness-110 bg-green-50 text-green-800">{{ t('settings.users.enableMfa') }}</button>
                  <button @click="removeUser(user)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.delete') }}</button>
                </div>
              </td>
            </tr>
            <tr v-if="filteredUsers.length === 0">
              <td colspan="8" class="px-4 py-8 text-center text-sm italic text-slate-400">
                {{ t('settings.users.empty') }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <!-- Edit modal -->
      <div v-if="editingUser" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md max-h-[90vh] overflow-y-auto">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.users.editUser') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.users.email') }}</label>
              <input v-model="editForm.email" type="email" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.users.fullName') }}</label>
              <input v-model="editForm.fullName" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.users.role') }}</label>
              <select v-model="editForm.role" class="ds-input">
                <option v-for="r in roles" :key="r" :value="r">{{ roleLabel(r) }}</option>
              </select>
            </div>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.users.sites') }}</label>
              <div class="space-y-1 max-h-32 overflow-y-auto">
                <label v-for="site in allSites" :key="site.id"
                  class="flex items-center gap-2 text-sm cursor-pointer text-slate-900">
                  <input type="checkbox" :value="site.id" v-model="editForm.siteIds"
                    class="rounded border-slate-300">
                  {{ site.name }} ({{ site.code }})
                </label>
              </div>
            </div>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.users.active') }}</label>
              <select v-model="editForm.isActive" class="ds-input">
                <option :value="true">Sí</option>
                <option :value="false">No</option>
              </select>
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveEdit" class="ds-btn-primary flex-1 justify-center">{{ t('common.save') }}</button>
              <button @click="cancelEdit" class="ds-btn-secondary flex-1 justify-center">{{ t('common.cancel') }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Create modal -->
      <div v-if="showCreate" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.users.newUser') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Email</label>
              <input v-model="createForm.email" type="email" required class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Nombre</label>
              <input v-model="createForm.fullName" required class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Rol</label>
              <select v-model="createForm.role" class="ds-input">
                <option v-for="r in roles" :key="r" :value="r">{{ roleLabel(r) }}</option>
              </select>
            </div>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.users.sites') }}</label>
              <div class="space-y-1 max-h-32 overflow-y-auto">
                <label v-for="site in allSites" :key="site.id"
                  class="flex items-center gap-2 text-sm cursor-pointer text-slate-900">
                  <input type="checkbox" :value="site.id" v-model="createForm.siteIds"
                    class="rounded border-slate-300">
                  {{ site.name }} ({{ site.code }})
                </label>
              </div>
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveCreate" class="ds-btn-primary flex-1 justify-center">{{ t('common.add') }}</button>
              <button @click="showCreate = false" class="ds-btn-secondary flex-1 justify-center">{{ t('common.cancel') }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- MFA Setup modal -->
      <div v-if="showMfaSetup" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-sm">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.users.mfaSetup') }}</h2>
          </div>
          <div class="p-6">
            <p class="text-[13px] mb-4 text-slate-500">
              {{ t('settings.users.mfaQrDesc') }}
            </p>
            <div class="text-center mb-4">
              <div class="inline-block p-3 rounded-lg border border-slate-200 bg-white">
                <img v-if="mfaOtpAuthUrl" :src="`https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(mfaOtpAuthUrl)}`"
                  alt="QR Code" class="w-[180px] h-[180px]" />
              </div>
            </div>
            <div class="mb-4">
              <label class="ds-label block mb-0.5">{{ t('settings.users.mfaSecretKey') }}</label>
              <code class="ds-input block text-[12px] break-all font-mono">{{ mfaSecret }}</code>
            </div>
            <div class="mb-4">
              <label class="ds-label block mb-0.5">{{ t('settings.users.mfaVerifyCode') }}</label>
              <input v-model="mfaVerifyCode" type="text" inputmode="numeric" maxlength="6"
                placeholder="000000"
                class="ds-input text-center font-mono tracking-wider"
                @keyup.enter="confirmMfaEnable" />
            </div>
            <div class="flex gap-2">
              <button @click="confirmMfaEnable"
                :disabled="mfaVerifyCode.length !== 6"
                class="ds-btn-primary flex-1 justify-center disabled:opacity-40">{{ t('settings.users.enableMfa') }}</button>
              <button @click="cancelMfaSetup" class="ds-btn-secondary flex-1 justify-center">{{ t('common.cancel') }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Temp Password modal -->
      <div v-if="showTempPassword" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-sm">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.users.tempPassword.title') }}</h2>
          </div>
          <div class="p-6">
            <p class="text-[13px] mb-4 text-slate-500">
              {{ t('settings.users.tempPassword.share') }}
            </p>
            <div class="mb-4 p-3 rounded-lg bg-green-50 border border-green-200">
              <label class="block text-[12px] font-medium mb-1 text-green-800">{{ t('settings.users.tempPassword.label') }}</label>
              <div class="flex items-center gap-2">
                <code class="flex-1 text-sm font-mono break-all px-2 py-1 rounded bg-white text-green-800 border border-green-200">
                  {{ generatedPassword }}
                </code>
                <button @click="copyPassword"
                  class="px-2 py-1 rounded text-[12px] font-medium transition-all hover:brightness-110 bg-green-100 text-green-800">
                  {{ t('common.copy') }}
                </button>
              </div>
            </div>
            <p class="text-[12px] mb-4 text-slate-400">
              {{ t('settings.users.tempPassword.mustChange') }}
            </p>
            <button @click="showTempPassword = false" class="ds-btn-primary w-full justify-center">
              {{ t('common.close') }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ SITES TAB (SuperUser only) ============ -->
    <template v-if="activeTab === 'sites'">
      <div class="flex items-center justify-between mb-3">
        <span class="ds-stat">{{ t('settings.sites.count', { n: allSites.length }) }}</span>
        <button @click="openSiteCreate" class="ds-btn-primary">
          + {{ t('settings.sites.newSite') }}
        </button>
      </div>

      <!-- Sites table -->
      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 500px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th>{{ t('settings.sites.code') }}</th>
              <th>{{ t('settings.sites.name') }}</th>
              <th>{{ t('settings.sites.country') }}</th>
              <th class="text-center" style="width: 80px">{{ t('settings.sites.active') }}</th>
              <th class="text-right" style="width: 140px">{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="site in allSites" :key="site.id"
              class="border-b border-slate-100 transition-colors hover:bg-slate-50/80">
              <td class="font-mono font-semibold text-slate-900">{{ site.code }}</td>
              <td class="text-slate-900">{{ site.name }}</td>
              <td class="text-slate-900">{{ site.country || '—' }}</td>
              <td class="text-center">
                <span class="text-[12px] font-medium px-2 py-0.5 rounded"
                  :class="site.isActive ? 'bg-slate-200 text-slate-900' : 'bg-slate-100 text-slate-400'">
                  {{ site.isActive ? t('common.yes') : t('common.no') }}
                </span>
              </td>
              <td class="text-right">
                <div class="flex gap-1 justify-end">
                  <button @click="startSiteEdit(site)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.edit') }}</button>
                  <button @click="removeSite(site)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.delete') }}</button>
                </div>
              </td>
            </tr>
            <tr v-if="allSites.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm italic text-slate-400">
                {{ t('settings.sites.empty') }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <!-- Site edit modal -->
      <div v-if="editingSite" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md max-h-[90vh] overflow-y-auto">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.sites.editSite') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código</label>
              <input v-model="siteForm.code" maxlength="10" required class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Nombre</label>
              <input v-model="siteForm.name" required class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">País</label>
              <input v-model="siteForm.country" maxlength="60" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Activo</label>
              <select v-model="siteForm.isActive" class="ds-input">
                <option :value="true">Sí</option>
                <option :value="false">No</option>
              </select>
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveSiteEdit" class="ds-btn-primary flex-1 justify-center">Guardar</button>
              <button @click="editingSite = null" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Site create modal -->
      <div v-if="showSiteCreate" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.sites.newSite') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código</label>
              <input v-model="siteCreateForm.code" maxlength="10" required class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Nombre</label>
              <input v-model="siteCreateForm.name" required class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">País</label>
              <input v-model="siteCreateForm.country" maxlength="60" class="ds-input">
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveSiteCreate" class="ds-btn-primary flex-1 justify-center">Crear</button>
              <button @click="showSiteCreate = false" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ AIRLINES TAB (ADMIN / SuperUser) ============ -->
    <template v-if="activeTab === 'airlines'">
      <div class="flex items-center justify-between mb-3">
        <span class="ds-stat">{{ t('settings.airlines.count', { n: airlines.length }) }}</span>
        <button @click="openAirlineCreate" class="ds-btn-primary">
          + {{ t('settings.airlines.newAirline') }}
        </button>
      </div>

      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 600px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th>Código</th>
              <th>Nombre</th>
              <th>IATA</th>
              <th>País</th>
              <th class="text-center" style="width: 80px">Activo</th>
              <th class="text-right" style="width: 140px">Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in airlines" :key="a.id"
              class="border-b border-slate-100 transition-colors hover:bg-slate-50/80">
              <td class="font-mono font-semibold text-slate-900">{{ a.code }}</td>
              <td class="text-slate-900">{{ a.name }}</td>
              <td class="font-mono text-slate-700">{{ a.iataCode || '—' }}</td>
              <td class="text-slate-900">{{ a.country || '—' }}</td>
              <td class="text-center">
                <span class="text-[12px] font-medium px-2 py-0.5 rounded"
                  :class="a.isActive ? 'bg-slate-200 text-slate-900' : 'bg-slate-100 text-slate-400'">
                  {{ a.isActive ? t('common.yes') : t('common.no') }}
                </span>
              </td>
              <td class="text-right">
                <div class="flex gap-1 justify-end">
                  <button @click="startAirlineEdit(a)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.edit') }}</button>
                  <button @click="removeAirline(a)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.delete') }}</button>
                </div>
              </td>
            </tr>
            <tr v-if="airlines.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-sm italic text-slate-400">
                {{ t('settings.airlines.empty') }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <!-- Airline edit modal -->
      <div v-if="editingAirline" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.airlines.editAirline') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código</label>
              <input v-model="airlineForm.code" maxlength="10" required class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Nombre</label>
              <input v-model="airlineForm.name" required class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Código IATA</label>
              <input v-model="airlineForm.iataCode" maxlength="3" class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">País</label>
              <input v-model="airlineForm.country" maxlength="60" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Activo</label>
              <select v-model="airlineForm.isActive" class="ds-input">
                <option :value="true">Sí</option>
                <option :value="false">No</option>
              </select>
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveAirlineEdit" class="ds-btn-primary flex-1 justify-center">Guardar</button>
              <button @click="editingAirline = null" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Airline create modal -->
      <div v-if="showAirlineCreate" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.airlines.newAirline') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código</label>
              <input v-model="airlineCreateForm.code" maxlength="10" required class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Nombre</label>
              <input v-model="airlineCreateForm.name" required class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Código IATA</label>
              <input v-model="airlineCreateForm.iataCode" maxlength="3" class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">País</label>
              <input v-model="airlineCreateForm.country" maxlength="60" class="ds-input">
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveAirlineCreate" class="ds-btn-primary flex-1 justify-center">Crear</button>
              <button @click="showAirlineCreate = false" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ ULD CONFIG TAB (ADMIN / SuperUser) ============ -->
    <template v-if="activeTab === 'uldconfig'">
      <div class="flex items-center justify-between mb-3 flex-wrap gap-2">
        <div class="flex items-center gap-3">
          <span class="ds-stat">{{ t('settings.uldConfig.count', { n: typeConfigs.length }) }}</span>
          <select v-model="configAirlineId" @change="loadTypeConfig" class="ds-input !w-auto font-mono">
            <option v-for="a in airlines" :key="a.id" :value="a.id">{{ a.code }} — {{ a.name }}</option>
          </select>
        </div>
        <div class="flex gap-2">
          <button @click="openNewUldType" class="ds-btn-secondary">{{ t('settings.uldConfig.newType') }}</button>
          <button @click="addTypeConfigRow" class="ds-btn-primary">
            + {{ t('settings.uldConfig.addRow') }}
          </button>
        </div>
      </div>

      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 700px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th>Tipo ULD</th>
              <th class="text-right">Tara default (lbs)</th>
              <th class="text-right">Max Gross (lbs)</th>
              <th>Notas</th>
              <th class="text-right" style="width: 120px">Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(cfg, idx) in typeConfigs" :key="idx"
              class="border-b border-slate-100">
              <td>
                <select v-model="cfg.uldType" class="ds-input font-mono" :title="typeOptionFor(cfg.uldType)?.description || ''">
                  <option v-for="tp in typeOptions" :key="tp.code" :value="tp.code">{{ tp.code }}{{ tp.description ? ' — ' + tp.description : '' }}</option>
                </select>
              </td>
              <td>
                <input v-model.number="cfg.defaultTareLbs" type="number" step="0.1" class="ds-input text-right font-mono">
              </td>
              <td>
                <input v-model.number="cfg.maxGrossLbs" type="number" step="0.1" class="ds-input text-right font-mono">
              </td>
              <td>
                <input v-model="cfg.notes" class="ds-input" :placeholder="t('settings.uldConfig.notesPlaceholder')">
              </td>
              <td class="text-right">
                <div class="flex gap-1 justify-end">
                  <button @click="removeTypeConfigRow(idx)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('settings.uldConfig.remove') }}</button>
                </div>
              </td>
            </tr>
            <tr v-if="typeConfigs.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm italic text-slate-400">
                {{ t('settings.uldConfig.empty') }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <div class="flex items-center justify-end gap-2 mt-3">
        <button @click="loadTypeConfig" class="ds-btn-secondary">{{ t('settings.uldConfig.discard') }}</button>
        <button @click="saveTypeConfig" class="ds-btn-primary">{{ t('settings.uldConfig.bulkSave') }}</button>
      </div>

      <!-- IATA ULD type catalog management -->
      <div class="ds-table-section mt-4">
        <div class="px-4 py-2 bg-slate-100 border-b border-slate-200 flex items-center justify-between">
          <span class="text-[12px] font-bold uppercase tracking-wider text-slate-600">{{ t('settings.uldConfig.catalog.title', { n: uldCatalog.length }) }}</span>
          <span class="text-[11px] text-slate-400">{{ t('settings.uldConfig.catalog.hint') }}</span>
        </div>
        <div class="overflow-y-auto" style="max-height: 280px">
          <table class="w-full text-[13px]">
            <thead>
              <tr class="bg-slate-50 text-[11px] uppercase text-slate-500 [&>th]:px-4 [&>th]:py-1.5 [&>th]:text-left [&>th]:font-semibold">
                <th style="width: 90px">{{ t('settings.uldConfig.uldType') }}</th>
                <th>{{ t('settings.uldConfig.newTypeDesc') }}</th>
                <th style="width: 110px">{{ t('settings.uldConfig.catalog.state') }}</th>
                <th class="text-right" style="width: 110px">{{ t('settings.uldConfig.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tp in uldCatalog" :key="tp.id || tp.code" class="border-b border-slate-100 hover:bg-slate-50">
                <td class="px-4 py-1.5 font-mono font-bold">{{ tp.code }}</td>
                <td class="px-4 py-1.5 text-slate-600">{{ tp.description || '—' }}</td>
                <td class="px-4 py-1.5">
                  <button v-if="tp.id" @click="toggleUldTypeActive(tp)"
                    class="text-[11px] font-bold px-2 py-0.5 rounded-full border transition-colors"
                    :class="tp.isActive ? 'bg-emerald-50 text-emerald-700 border-emerald-300' : 'bg-slate-100 text-slate-400 border-slate-200'">
                    {{ tp.isActive ? t('settings.uldConfig.catalog.active') : t('settings.uldConfig.catalog.inactive') }}
                  </button>
                  <span v-else class="text-[11px] text-slate-300">legacy</span>
                </td>
                <td class="px-4 py-1.5 text-right">
                  <div class="flex gap-1 justify-end">
                    <button @click="startEditUldType(tp)" class="ds-btn-secondary !px-2 !py-0.5 !text-[11px]" :disabled="!tp.id">✎</button>
                    <button @click="removeCatalogEntry(tp)" class="ds-btn-secondary !px-2 !py-0.5 !text-[11px] hover:!bg-red-50 hover:!text-red-600" :disabled="!tp.id">✕</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- New/edit ULD type modal (IATA catalog) -->
      <div v-if="showNewUldType" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ editingUldType ? t('settings.uldConfig.editTypeTitle') : t('settings.uldConfig.newTypeTitle') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <p v-if="!editingUldType" class="text-[12px] text-slate-500 leading-relaxed">{{ t('settings.uldConfig.newTypeHelp') }}</p>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.uldConfig.uldType') }}</label>
              <input v-model="newUldTypeForm.code" maxlength="5" required
                :disabled="!!editingUldType"
                placeholder="PMC / AKE / RKN"
                class="ds-input font-mono uppercase disabled:bg-slate-100 disabled:text-slate-400">
            </div>
            <div>
              <label class="ds-label block mb-0.5">{{ t('settings.uldConfig.newTypeDesc') }}</label>
              <input v-model="newUldTypeForm.description" maxlength="120"
                :placeholder="t('settings.uldConfig.newTypeDescPlaceholder')" class="ds-input">
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveNewUldType" class="ds-btn-primary flex-1 justify-center">{{ t('common.save') }}</button>
              <button @click="closeUldTypeModal" class="ds-btn-secondary flex-1 justify-center">{{ t('common.cancel') }}</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ COMMODITIES TAB (ADMIN / SuperUser + TOTP) ============ -->
    <template v-if="activeTab === 'commodities'">
      <div class="flex items-center justify-between mb-3">
        <span class="ds-stat">{{ t('settings.commodities.count', { n: commodityList.length }) }}</span>
        <div class="flex gap-2">
          <div class="flex items-center gap-1">
            <input v-model="commodityTotpCode" type="text" inputmode="numeric" maxlength="6"
              placeholder="TOTP" class="ds-input !w-20 !py-1 !text-[11px] font-mono text-center" title="Código TOTP para todas las acciones">
          </div>
          <button @click="restoreCommodityDefaults" class="ds-btn-secondary" :disabled="commodityTotpCode.length !== 6">
            {{ t('settings.commodities.restoreDefaults') }}
          </button>
          <button @click="openCommodityCreate" class="ds-btn-primary">
            + {{ t('settings.commodities.newCommodity') }}
          </button>
        </div>
      </div>

      <div class="ds-table-section">
        <div class="table-scroll-wrapper flex-1 min-h-0 overflow-y-auto">
        <table class="w-full text-sm" style="min-width: 700px">
          <thead>
            <tr class="bg-slate-800 text-white text-[13px] font-bold uppercase tracking-wider [&>th]:px-4 [&>th]:py-2.5 [&>th]:text-left [&>th]:font-semibold">
              <th>Código</th>
              <th>Etiqueta</th>
              <th>Descripción</th>
              <th>Color</th>
              <th class="text-right">Orden</th>
              <th class="text-center" style="width: 80px">Activo</th>
              <th class="text-right" style="width: 140px">Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in commodityList" :key="c.id"
              class="border-b border-slate-100 transition-colors hover:bg-slate-50/80">
              <td class="font-mono font-semibold text-slate-900">{{ c.code }}</td>
              <td class="text-slate-900">{{ c.label }}</td>
              <td class="text-slate-500 text-[12px] max-w-[200px] truncate">{{ c.description || '—' }}</td>
              <td>
                <div class="flex items-center gap-2">
                  <span class="w-4 h-4 rounded border border-slate-200 inline-block" :style="{ backgroundColor: c.color || '#94a3b8' }"></span>
                  <span class="font-mono text-[12px] text-slate-500">{{ c.color || '—' }}</span>
                </div>
              </td>
              <td class="text-right font-mono text-slate-700">{{ c.sortOrder }}</td>
              <td class="text-center">
                <span class="text-[12px] font-medium px-2 py-0.5 rounded"
                  :class="c.isActive ? 'bg-slate-200 text-slate-900' : 'bg-slate-100 text-slate-400'">
                  {{ c.isActive ? t('common.yes') : t('common.no') }}
                </span>
              </td>
              <td class="text-right">
                <div class="flex gap-1 justify-end">
                  <button @click="startCommodityEdit(c)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.edit') }}</button>
                  <button @click="removeCommodity(c)" class="ds-btn-secondary !px-2 !py-1 !text-[12px]">{{ t('common.delete') }}</button>
                </div>
              </td>
            </tr>
            <tr v-if="commodityList.length === 0">
              <td colspan="7" class="px-4 py-8 text-center text-sm italic text-slate-400">
                {{ t('settings.commodities.empty') }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <!-- Commodity edit modal -->
      <div v-if="editingCommodity" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.commodities.editCommodity') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código</label>
              <input v-model="commodityForm.code" maxlength="50" required class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Etiqueta</label>
              <input v-model="commodityForm.label" maxlength="100" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Descripción</label>
              <input v-model="commodityForm.description" maxlength="500" class="ds-input" placeholder="Descripción opcional">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Color (hex)</label>
              <div class="flex items-center gap-2">
                <input v-model="commodityForm.color" maxlength="20" class="ds-input font-mono" placeholder="#94a3b8">
                <span class="w-8 h-8 rounded border border-slate-200 inline-block flex-shrink-0" :style="{ backgroundColor: commodityForm.color || '#94a3b8' }"></span>
              </div>
            </div>
            <div>
              <label class="ds-label block mb-0.5">Orden</label>
              <input v-model.number="commodityForm.sortOrder" type="number" class="ds-input font-mono">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Activo</label>
              <select v-model="commodityForm.isActive" class="ds-input">
                <option :value="true">Sí</option>
                <option :value="false">No</option>
              </select>
            </div>
            <div class="bg-amber-50 border border-amber-200 rounded-lg p-3">
              <label class="ds-label block mb-0.5 text-amber-800">Código TOTP (Authenticator)</label>
              <input v-model="commodityTotpCode" type="text" inputmode="numeric" maxlength="6"
                placeholder="000000" class="ds-input text-center font-mono tracking-wider">
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveCommodityEdit" :disabled="commodityTotpCode.length !== 6"
                class="ds-btn-primary flex-1 justify-center disabled:opacity-40">{{ t('common.save') }}</button>
              <button @click="editingCommodity = null" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Commodity create modal -->
      <div v-if="showCommodityCreate" class="ds-modal-backdrop">
        <div class="ds-modal-panel max-w-md">
          <div class="ds-modal-header">
            <h2 class="ds-modal-title">{{ t('settings.commodities.newCommodity') }}</h2>
          </div>
          <div class="p-6 space-y-3">
            <div>
              <label class="ds-label block mb-0.5">Código</label>
              <input v-model="commodityCreateForm.code" maxlength="50" required class="ds-input font-mono uppercase">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Etiqueta</label>
              <input v-model="commodityCreateForm.label" maxlength="100" class="ds-input">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Descripción</label>
              <input v-model="commodityCreateForm.description" maxlength="500" class="ds-input" placeholder="Descripción opcional">
            </div>
            <div>
              <label class="ds-label block mb-0.5">Color (hex)</label>
              <div class="flex items-center gap-2">
                <input v-model="commodityCreateForm.color" maxlength="20" class="ds-input font-mono" placeholder="#94a3b8">
                <span class="w-8 h-8 rounded border border-slate-200 inline-block flex-shrink-0" :style="{ backgroundColor: commodityCreateForm.color || '#94a3b8' }"></span>
              </div>
            </div>
            <div>
              <label class="ds-label block mb-0.5">Orden</label>
              <input v-model.number="commodityCreateForm.sortOrder" type="number" class="ds-input font-mono">
            </div>
            <div class="bg-amber-50 border border-amber-200 rounded-lg p-3">
              <label class="ds-label block mb-0.5 text-amber-800">Código TOTP (Authenticator)</label>
              <input v-model="commodityTotpCode" type="text" inputmode="numeric" maxlength="6"
                placeholder="000000" class="ds-input text-center font-mono tracking-wider">
            </div>
            <div class="flex gap-2 pt-2">
              <button @click="saveCommodityCreate" :disabled="commodityTotpCode.length !== 6"
                class="ds-btn-primary flex-1 justify-center disabled:opacity-40">{{ t('common.add') }}</button>
              <button @click="showCommodityCreate = false" class="ds-btn-secondary flex-1 justify-center">Cancelar</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ BACKUPS TAB ============ -->
    <template v-if="activeTab === 'backups'">
      <div class="space-y-4 overflow-y-auto flex-1 pr-1">

        <!-- Stats -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div class="ds-table-section p-3">
            <p class="text-[11px] text-slate-500">{{ t('settings.backups.totalBackups') }}</p>
            <p class="text-xl font-semibold text-slate-900">{{ backupStats.totalBackups ?? '—' }}</p>
          </div>
          <div class="ds-table-section p-3">
            <p class="text-[11px] text-slate-500">{{ t('settings.backups.totalSize') }}</p>
            <p class="text-xl font-semibold text-slate-900">{{ formatBytes(backupStats.totalSizeBytes) }}</p>
          </div>
          <div class="ds-table-section p-3">
            <p class="text-[11px] text-slate-500">{{ t('settings.backups.successRate') }}</p>
            <p class="text-xl font-semibold text-emerald-700">
              {{ backupSuccessRate }}<span v-if="backupSuccessRate !== '—'" class="text-sm">%</span>
            </p>
          </div>
          <div class="ds-table-section p-3">
            <p class="text-[11px] text-slate-500">{{ t('settings.backups.diskFree') }}</p>
            <p class="text-xl font-semibold text-slate-900">{{ formatBytes(backupStats.availableSpaceBytes) }}</p>
          </div>
        </div>

        <!-- Config: carpeta de backups -->
        <div class="ds-table-section">
          <div class="p-4 space-y-4">
            <div>
              <h3 class="text-sm font-semibold text-slate-800 mb-1">{{ t('settings.backups.folderTitle') }}</h3>
              <p class="text-[11px] text-slate-500 leading-relaxed">{{ t('settings.backups.folderHelp') }}</p>
            </div>

            <div>
              <label class="ds-label block mb-1">{{ t('settings.backups.backupDir') }}</label>
              <div class="flex items-center gap-2">
                <input v-model="backupForm.backupDir" :disabled="backupSaving"
                  :placeholder="t('settings.backups.backupDirPlaceholder')"
                  class="ds-input font-mono text-[12px] flex-1">
                <button @click="saveBackupConfig" :disabled="backupSaving" class="ds-btn-primary text-[12px] whitespace-nowrap">
                  {{ backupSaving ? t('common.saving') : t('common.save') }}
                </button>
              </div>
              <p class="text-[11px] text-slate-400 mt-1">
                {{ t('settings.backups.currentDir') }}: <code class="font-mono">{{ backupStats.backupDir || '—' }}</code>
              </p>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-3 gap-3">
              <div>
                <label class="ds-label block mb-1">{{ t('settings.backups.keepDays') }}</label>
                <input v-model.number="backupForm.keepDays" type="number" min="1" max="3650"
                  :disabled="backupSaving" class="ds-input w-full">
              </div>
              <div>
                <label class="ds-label block mb-1">{{ t('settings.backups.compressLevel') }}</label>
                <input v-model.number="backupForm.compressLevel" type="number" min="0" max="9"
                  :disabled="backupSaving" class="ds-input w-full">
              </div>
              <div class="flex items-end pb-1">
                <button @click="triggerManualBackup" :disabled="backupTriggering"
                  class="ds-btn-secondary w-full justify-center text-[12px]">
                  {{ backupTriggering ? t('settings.backups.creating') : t('settings.backups.createNow') }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Rollback info -->
        <div class="bg-slate-50 border border-slate-200 rounded-lg p-3">
          <p class="text-[11px] text-slate-600 leading-relaxed">
            <strong class="text-slate-900">{{ t('settings.backups.rollbackTitle') }}</strong>
            {{ t('settings.backups.rollbackHelp') }}
            <code class="bg-slate-200 px-1 rounded font-mono text-[10px]">./scripts/rollback.sh --pre-deploy</code>
            ·
            <code class="bg-slate-200 px-1 rounded font-mono text-[10px]">--emergency</code>
            ·
            <code class="bg-slate-200 px-1 rounded font-mono text-[10px]">--restore &lt;file&gt;</code>
          </p>
        </div>

        <!-- Restaurar BD -->
        <div class="ds-table-section">
          <div class="px-4 py-2 border-b border-slate-200 flex items-center justify-between">
            <h3 class="text-sm font-semibold text-slate-800">{{ t('settings.backups.restoreTitle') }}</h3>
          </div>
          <div class="p-4 space-y-3">
            <p class="text-[11px] text-slate-500 leading-relaxed">{{ t('settings.backups.restoreHelp') }}</p>

            <div class="flex items-center gap-4">
              <label class="flex items-center gap-1.5 text-[12px] cursor-pointer">
                <input v-model="restoreSource" value="local" type="radio" class="accent-blue-600" :disabled="restoring">
                {{ t('settings.backups.restoreLocal') }}
              </label>
              <label class="flex items-center gap-1.5 text-[12px] cursor-pointer">
                <input v-model="restoreSource" value="url" type="radio" class="accent-blue-600" :disabled="restoring">
                {{ t('settings.backups.restoreUrl') }}
              </label>
            </div>

            <div v-if="restoreSource === 'local'">
              <label class="ds-label block mb-1">{{ t('settings.backups.restoreFilePath') }}</label>
              <input v-model="restoreFilePath" :disabled="restoring"
                :placeholder="t('settings.backups.restoreFilePathPlaceholder')"
                class="ds-input font-mono text-[12px] w-full"
                @keyup.enter="doRestore">
            </div>
            <div v-else>
              <label class="ds-label block mb-1">{{ t('settings.backups.restoreUrlInput') }}</label>
              <input v-model="restoreUrl" :disabled="restoring" type="url" inputmode="url"
                :placeholder="t('settings.backups.restoreUrlPlaceholder')"
                class="ds-input font-mono text-[12px] w-full"
                @keyup.enter="doRestore">
            </div>

            <div class="flex items-center gap-3">
              <button @click="doRestore" :disabled="restoring ||
                  (restoreSource === 'local' ? !restoreFilePath.trim() : !restoreUrl.trim())"
                class="bg-red-600 hover:bg-red-700 text-white text-[12px] whitespace-nowrap rounded-lg px-3 py-1.5 disabled:opacity-50 disabled:cursor-not-allowed">
                {{ restoring ? t('settings.backups.restoring') : t('settings.backups.restoreBtn') }}
              </button>
              <span class="text-[11px] text-slate-400">{{ t('settings.backups.restoreSafety') }}</span>
            </div>

            <div v-if="restoreResult" :class="restoreResult.success
                ? 'bg-emerald-50 border border-emerald-200 text-emerald-800'
                : 'bg-red-50 border border-red-200 text-red-800'"
              class="rounded p-3 text-[11px] whitespace-pre-line leading-relaxed">
              <strong>{{ restoreResult.success ? '✓' : '✗' }} {{ restoreResult.message }}</strong>
              <div v-if="restoreResult.dumpPath" class="font-mono mt-1 text-[10px]">dump: {{ restoreResult.dumpPath }}</div>
            </div>
          </div>
        </div>

        <!-- History -->
        <div class="ds-table-section">
          <div class="px-4 py-2 border-b border-slate-200 flex items-center justify-between">
            <h3 class="text-sm font-semibold text-slate-800">{{ t('settings.backups.history') }}</h3>
            <button @click="loadBackupHistory" class="text-[11px] text-blue-600 hover:underline">
              {{ t('common.refresh') }}
            </button>
          </div>
          <div class="overflow-x-auto max-h-72 overflow-y-auto">
            <table class="w-full text-[11px]">
              <thead class="sticky top-0 bg-slate-100">
                <tr class="text-left text-slate-600">
                  <th class="px-3 py-1.5 font-semibold">{{ t('settings.backups.file') }}</th>
                  <th class="px-3 py-1.5 font-semibold">{{ t('settings.backups.type') }}</th>
                  <th class="px-3 py-1.5 font-semibold">{{ t('settings.backups.size') }}</th>
                  <th class="px-3 py-1.5 font-semibold">{{ t('settings.backups.statusCol') }}</th>
                  <th class="px-3 py-1.5 font-semibold">{{ t('settings.backups.date') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!backupHistory.length">
                  <td colspan="5" class="px-3 py-6 text-center text-slate-400">{{ t('settings.backups.empty') }}</td>
                </tr>
                <tr v-for="h in backupHistory" :key="h.id" class="border-t border-slate-100 hover:bg-slate-50">
                  <td class="px-3 py-1.5 font-mono text-[10px]">{{ h.fileName }}</td>
                  <td class="px-3 py-1.5">
                    <span class="px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 text-[10px] uppercase">{{ h.backupType }}</span>
                  </td>
                  <td class="px-3 py-1.5">{{ formatBytes(h.sizeBytes) }}</td>
                  <td class="px-3 py-1.5">
                    <span :class="h.status === 'SUCCESS'
                      ? 'text-emerald-700 bg-emerald-50'
                      : 'text-red-700 bg-red-50'"
                      class="px-1.5 py-0.5 rounded text-[10px] font-medium">{{ h.status }}</span>
                  </td>
                  <td class="px-3 py-1.5 text-slate-500">{{ formatDateTime(h.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

      </div>
    </template>

    <!-- ============ API / BI TAB ============ -->
    <template v-if="activeTab === 'api'">
      <div class="space-y-4 overflow-y-auto flex-1 pr-1">

        <!-- Header -->
        <div class="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <h3 class="text-sm font-semibold text-blue-900 mb-1">{{ t('settings.apiBi.biConnection') }}</h3>
          <p class="text-xs text-blue-700 leading-relaxed">
            {{ t('settings.apiBi.biConnectionDesc') }}
          </p>
        </div>

        <!-- Detected URL -->
        <div class="ds-table-section">
          <div class="p-4 space-y-4">

            <div>
              <label class="ds-label block mb-1">{{ t('settings.apiBi.serverUrl') }}</label>
              <div class="flex items-center gap-2">
                <input v-model="customUrl" :placeholder="currentOrigin"
                  class="ds-input font-mono text-[12px] flex-1">
                <button @click="customUrl = ''" class="ds-btn-secondary text-[11px] whitespace-nowrap"
                  v-if="customUrl">{{ t('settings.apiBi.reset') }}</button>
              </div>
              <p class="text-[11px] text-slate-400 mt-1">
                {{ t('settings.apiBi.autoDetected') }}: <code class="font-mono">{{ currentOrigin }}</code>
              </p>
            </div>

            <!-- EC2 deployment note -->
            <div class="bg-slate-50 border border-slate-200 rounded p-3">
              <p class="text-[11px] text-slate-700 leading-relaxed">
                <strong class="text-slate-900">{{ t('settings.apiBi.ec2Deploy') }}</strong>
                Abre la app en tu navegador con la URL publica del servidor.
                La URL se detecta automaticamente. nginx proxys las llamadas <code class="bg-slate-200 px-1 rounded font-mono text-[10px]">/api/</code> al gateway internamente.
                Las URLs de abajo ya apuntan al lugar correcto.
              </p>
              <div class="bg-slate-900 text-green-400 rounded p-2 font-mono text-[11px] mt-2 overflow-x-auto">
                <div class="text-slate-500"># {{ t('settings.apiBi.ec2Example') }}</div>
                <div>{{ gatewayUrl }}/api/bi/dashboard?api_key=TOKEN</div>
                <div class="text-slate-500 mt-1"># {{ t('settings.apiBi.ec2PowerBI') }}</div>
              </div>
            </div>

            <!-- API Key -->
            <div>
              <label class="ds-label block mb-1">{{ t('settings.apiBi.serviceName') }}</label>
              <div class="flex items-center gap-2">
                <input :value="biToken || ''" readonly placeholder="Genera el token para conectarte a Power BI"
                  class="ds-input font-mono text-[10px] flex-1 bg-slate-50 select-all">
                <button :disabled="!biToken || biTokenLoading" @click="copyToClipboard(biToken, 'token')"
                  class="ds-btn-secondary text-[12px] whitespace-nowrap disabled:opacity-30">
                  {{ copied && copiedEndpoint === 'token' ? t('common.copied', {text:''}) : t('common.copy') }}
                </button>
                <button @click="loadServiceToken" :disabled="biTokenLoading"
                  class="ds-btn-secondary text-[12px] whitespace-nowrap disabled:opacity-40">{{ biTokenLoading ? 'Generando...' : 'Regenerar' }}</button>
              </div>
              <p v-if="biTokenError" class="text-[11px] text-red-600 mt-1">{{ biTokenError }}</p>
              <p v-else-if="biToken" class="text-[11px] text-slate-400 mt-1">
                Cuenta: <span class="font-mono">bi@rannik.com</span> (BI_USER) — Generado
                <template v-if="biTokenExpiry">· Expira: {{ biTokenExpiry }}</template>
              </p>
              <p v-else class="text-[11px] text-slate-400 mt-1">
                Cuenta: <span class="font-mono">bi@rannik.com</span> (BI_USER) — Pulsa "Regenerar" para emitir un token de servicio.
              </p>
            </div>

            <!-- Test Connection -->
            <div>
              <button @click="testConnection" :disabled="connectionTesting"
                class="ds-btn-primary text-[12px]">
                {{ connectionTesting ? t('common.loading') : t('settings.apiBi.testConnection') }}
              </button>
              <span v-if="connectionStatus" class="ml-3 text-[12px]"
                :class="connectionStatus.ok ? 'text-green-700 font-medium' : 'text-red-600'">
                {{ connectionStatus.msg }}
              </span>
            </div>
          </div>
        </div>

        <!-- Quick start: Power BI -->
        <div class="ds-table-section">
          <div class="p-4">
            <div class="flex items-center gap-2 mb-3">
              <span class="text-amber-500 text-lg">&#9632;</span>
              <label class="ds-label block !mb-0">{{ t('settings.apiBi.powerBI') }}</label>
            </div>
            <div class="space-y-2 text-[12px] text-slate-600 leading-relaxed">
              <div class="flex items-start gap-3">
                <span class="flex-shrink-0 w-5 h-5 rounded-full bg-slate-800 text-white text-[10px] font-bold flex items-center justify-center mt-0.5">1</span>
                <p>{{ t('settings.apiBi.powerBIOpen') }}</p>
              </div>
              <div class="flex items-start gap-3">
                <span class="flex-shrink-0 w-5 h-5 rounded-full bg-slate-800 text-white text-[10px] font-bold flex items-center justify-center mt-0.5">2</span>
                <p>Home &rarr; <strong>Get Data</strong> &rarr; <strong>Web</strong></p>
              </div>
              <div class="flex items-start gap-3">
                <span class="flex-shrink-0 w-5 h-5 rounded-full bg-slate-800 text-white text-[10px] font-bold flex items-center justify-center mt-0.5">3</span>
                <div>
                  <p>Copia esta URL y pegala:</p>
                  <div class="flex items-center gap-2 mt-1 bg-slate-900 rounded px-3 py-2">
                    <code class="text-green-400 font-mono text-[11px] flex-1 overflow-x-auto whitespace-nowrap">{{ gatewayUrl }}/api/bi/dashboard?api_key={{ biToken ? biToken.substring(0, 20) : 'TOKEN_AQUI' }}...</code>
                    <button @click="copyToClipboard(biToken ? `${gatewayUrl}/api/bi/dashboard?api_key=${biToken}` : '', 'pbi-url')"
                      class="text-[10px] text-blue-400 hover:text-blue-300 whitespace-nowrap disabled:opacity-30"
                      :disabled="!biToken">
                      {{ copied && copiedEndpoint === 'pbi-url' ? t('common.copied', {text:''}) : t('settings.apiBi.copyUrl') }}
                    </button>
                  </div>
                </div>
              </div>
              <div class="flex items-start gap-3">
                <span class="flex-shrink-0 w-5 h-5 rounded-full bg-slate-800 text-white text-[10px] font-bold flex items-center justify-center mt-0.5">4</span>
                <p>Power BI carga el JSON &rarr; <strong>Convert to Table</strong> &rarr; Expandir columnas</p>
              </div>
              <div class="flex items-start gap-3">
                <span class="flex-shrink-0 w-5 h-5 rounded-full bg-slate-800 text-white text-[10px] font-bold flex items-center justify-center mt-0.5">5</span>
                <p>Repite con los otros endpoints que necesites como tablas separadas</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Endpoints list -->
        <div class="ds-table-section">
          <div class="p-4">
            <label class="ds-label block mb-2">{{ t('settings.apiBi.endpointsTitle') }}</label>
            <div class="grid grid-cols-1 gap-1.5">
              <div v-for="ep in biEndpoints" :key="ep.path"
                @click="copyToClipboard(biToken ? `${gatewayUrl}${ep.path}?api_key=${biToken}` : '', ep.path)"
                class="flex items-center justify-between bg-slate-50 rounded px-3 py-2 border border-slate-100 hover:border-blue-300 hover:bg-blue-50/30 cursor-pointer transition-all group">
                <div class="min-w-0 flex-1">
                  <code class="text-[11px] font-mono text-slate-900 block truncate">GET {{ ep.path }}</code>
                  <p class="text-[10px] text-slate-500 mt-0.5">{{ ep.desc }}</p>
                </div>
                <span class="text-[10px] text-blue-500 group-hover:text-blue-700 whitespace-nowrap ml-3 flex-shrink-0">
                  {{ copied && copiedEndpoint === ep.path ? t('common.copied', {text:''}) : t('settings.apiBi.copyUrl') }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Advanced guides -->
        <div class="ds-table-section">
          <div class="p-4 space-y-4">
            <label class="ds-label block">{{ t('settings.apiBi.quickStart') }}</label>

            <!-- Power BI M query -->
            <details class="group">
              <summary class="flex items-center gap-2 cursor-pointer text-[13px] font-semibold text-slate-800 hover:text-blue-700 select-none">
                <svg class="w-4 h-4 text-amber-500 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                Power BI — Power Query (M)
              </summary>
              <div class="mt-2 ml-6 space-y-2 text-[12px] text-slate-600 leading-relaxed">
                <p class="font-medium text-slate-800">{{ t('settings.apiBi.pythonDirectDesc') }}</p>
                <div class="bg-slate-900 text-green-400 rounded p-2 font-mono text-[11px] overflow-x-auto">
                  <div>let</div>
                  <div>&nbsp;&nbsp;url = "{{ gatewayUrl }}/api/bi/dashboard?api_key=TOKEN_AQUI",</div>
                  <div>&nbsp;&nbsp;json = Json.Document(Web.Contents(url)),</div>
                  <div>&nbsp;&nbsp;table = Table.FromRecords({json})</div>
                  <div>in table</div>
                </div>
              </div>
            </details>

            <!-- Tableau -->
            <details class="group">
              <summary class="flex items-center gap-2 cursor-pointer text-[13px] font-semibold text-slate-800 hover:text-blue-700 select-none">
                <svg class="w-4 h-4 text-blue-500 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                Tableau
              </summary>
              <div class="mt-2 ml-6 space-y-2 text-[12px] text-slate-600 leading-relaxed">
                <ol class="list-decimal list-inside space-y-1">
                  <li>Abre Tableau &rarr; <strong>Connect</strong> &rarr; <strong>To a Server</strong> &rarr; <strong>Web Data Connector</strong></li>
                  <li>Pega la URL de cualquier endpoint (clic en "Copiar URL" arriba)</li>
                  <li>Tableau carga el JSON como tabla</li>
                </ol>
              </div>
            </details>

            <!-- Metabase -->
            <details class="group">
              <summary class="flex items-center gap-2 cursor-pointer text-[13px] font-semibold text-slate-800 hover:text-blue-700 select-none">
                <svg class="w-4 h-4 text-purple-500 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                Metabase
              </summary>
              <div class="mt-2 ml-6 space-y-2 text-[12px] text-slate-600 leading-relaxed">
                <ol class="list-decimal list-inside space-y-1">
                  <li>Metabase &rarr; Admin &rarr; <strong>Databases</strong> &rarr; <strong>Add database</strong></li>
                  <li>Tipo: <strong>HTTP API</strong> o <strong>REST API / JSON</strong></li>
                  <li>Base URL: <code class="bg-slate-100 px-1 rounded font-mono text-[11px]">{{ gatewayUrl }}</code></li>
                  <li>Headers: <code class="bg-slate-100 px-1 rounded font-mono text-[11px]">Authorization: Bearer TOKEN_AQUI</code></li>
                </ol>
              </div>
            </details>

            <!-- curl / Python -->
            <details class="group">
              <summary class="flex items-center gap-2 cursor-pointer text-[13px] font-semibold text-slate-800 hover:text-blue-700 select-none">
                <svg class="w-4 h-4 text-slate-500 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                curl / Python / pandas
              </summary>
              <div class="mt-2 ml-6 space-y-2 text-[12px] text-slate-600 leading-relaxed">
                <div class="bg-slate-900 text-green-400 rounded p-2 font-mono text-[11px] overflow-x-auto">
                  <div class="text-slate-500"># curl</div>
                  <div>curl "{{ gatewayUrl }}/api/bi/dashboard?api_key=TOKEN_AQUI"</div>
                  <div class="text-slate-500 mt-2"># Python + pandas</div>
                  <div>import pandas as pd</div>
                  <div>df = pd.read_json("{{ gatewayUrl }}/api/bi/weight-report?api_key=TOKEN_AQUI")</div>
                </div>
              </div>
            </details>

          </div>
        </div>

      </div>
    </template>


  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usersApi } from '../api/users'

const { t } = useI18n()
import { sitesApi } from '../api/sites'
import { airlinesApi } from '../api/airlines'
import { uldTypeConfigApi } from '../api/uldTypeConfig'
import { uldTypeCatalogApi } from '../api/uldTypeCatalog'
import { commodityTypesApi } from '../api/commodityTypes'
import { backupsApi } from '../api/backups'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import { extractError } from '../utils/error'
import client from '../api/client'
import { authApi } from '../api/auth'

const toast = useToastStore()
const { confirm } = useConfirm()
const auth = useAuthStore()
const users = ref([])
const allSites = ref([])
const editingUser = ref(null)
const showCreate = ref(false)
const searchQuery = ref('')
const activeTab = ref('users')
const editingSite = ref(null)
const showSiteCreate = ref(false)

const canManageSettings = computed(() => ['ADMIN', 'SUPER_USER'].includes(auth.role))

// ── Backups ──
const backupStats = ref({})
const backupForm = ref({ backupDir: '', keepDays: 30, compressLevel: 6 })
const backupHistory = ref([])
const backupSaving = ref(false)
const backupTriggering = ref(false)

async function openBackupsTab() {
  activeTab.value = 'backups'
  try {
    const [cfg, stats] = await Promise.all([backupsApi.getConfig(), backupsApi.getStats()])
    backupForm.value = {
      backupDir: cfg.data.backupDir || '',
      keepDays: cfg.data.keepDays ?? 30,
      compressLevel: cfg.data.compressLevel ?? 6,
    }
    backupStats.value = stats.data
  } catch (e) {
    toast.error(extractError(e))
  }
  loadBackupHistory()
}

async function loadBackupHistory() {
  try {
    const res = await backupsApi.getHistory(0, 50)
    backupHistory.value = res.data || []
  } catch { /* silencioso */ }
}

async function saveBackupConfig() {
  backupSaving.value = true
  try {
    await backupsApi.updateConfig({
      id: 1,
      backupDir: backupForm.value.backupDir,
      keepDays: backupForm.value.keepDays,
      compressLevel: backupForm.value.compressLevel,
      autoBackupEnabled: true,
      notifyOnFailure: true,
    })
    toast.success(t('settings.backups.saved'))
    const stats = await backupsApi.getStats()
    backupStats.value = stats.data
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    backupSaving.value = false
  }
}

async function triggerManualBackup() {
  backupTriggering.value = true
  try {
    await backupsApi.trigger('MANUAL')
    toast.success(t('settings.backups.created'))
    setTimeout(() => {
      openBackupsTab()
    }, 3000)
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    backupTriggering.value = false
  }
}

// ── Restauración de BD ──
const restoreSource = ref('local')
const restoreFilePath = ref('')
const restoreUrl = ref('')
const restoring = ref(false)
const restoreResult = ref(null)

async function doRestore() {
  const ok = await confirm({
    title: t('settings.backups.restoreConfirmTitle'),
    message: t('settings.backups.restoreConfirmMsg'),
    confirmText: t('settings.backups.restoreConfirmBtn'),
    cancelText: t('common.cancel'),
    danger: true,
  })
  if (!ok) return
  restoring.value = true
  restoreResult.value = null
  try {
    const payload = restoreSource.value === 'url'
      ? { source: 'url', url: restoreUrl.value.trim() }
      : { source: 'local', filePath: restoreFilePath.value.trim() }
    const res = await backupsApi.restore(payload)
    restoreResult.value = res.data
    if (res.data.success) {
      toast.success(t('settings.backups.restoreDone'))
    } else {
      toast.error(t('settings.backups.restoreFailed'))
    }
    setTimeout(loadBackupHistory, 2500)
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    restoring.value = false
  }
}

function formatBytes(bytes) {
  if (bytes == null) return '—'
  if (bytes < 1024) return bytes + ' B'
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = bytes, i = -1
  do { v /= 1024; i++ } while (v >= 1024 && i < units.length - 1)
  return v.toFixed(1) + ' ' + units[i]
}

const backupSuccessRate = computed(() => {
  const s = backupStats.value
  if (!s.totalBackups) return '—'
  return Math.round(((s.successCount || 0) / s.totalBackups) * 100).toString()
})

function formatDateTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })
}

const airlines = ref([])
const editingAirline = ref(null)
const showAirlineCreate = ref(false)
const airlineForm = ref({ id: null, code: '', name: '', iataCode: '', country: '', isActive: true })
const airlineCreateForm = ref({ code: '', name: '', iataCode: '', country: '', isActive: true })

// Catálogo dinámico de tipos ULD (normas IATA) — fallback a la lista legacy si el catálogo no responde
const LEGACY_ULD_TYPES = ['PMC', 'PAH', 'PAG', 'PAJ', 'AAY', 'AAZ', 'AAD', 'PIP', 'BULK', 'AMP', 'AMJ']
const uldCatalog = ref(LEGACY_ULD_TYPES.map(c => ({ code: c, description: '' })))
const showNewUldType = ref(false)
const editingUldType = ref(null)
const newUldTypeForm = ref({ code: '', description: '' })
const typeConfigs = ref([])
const configAirlineId = ref(null)

async function loadUldCatalog() {
  try {
    const res = await uldTypeCatalogApi.getAll(false)
    const items = res.data || []
    if (items.length) {
      uldCatalog.value = items.map(x => ({ id: x.id, code: x.code, description: x.description || '', isActive: x.isActive !== false }))
    }
  } catch {
    // catálogo no disponible: se conserva el fallback legacy
  }
}

const typeOptions = computed(() => {
  const map = new Map(uldCatalog.value.map(x => [x.code, x]))
  for (const cfg of typeConfigs.value) {
    if (cfg.uldType && !map.has(cfg.uldType)) map.set(cfg.uldType, { code: cfg.uldType, description: t('settings.uldConfig.offCatalog') })
  }
  return [...map.values()]
})

function typeOptionFor(code) {
  return typeOptions.value.find(tp => tp.code === code)
}

function openNewUldType() {
  newUldTypeForm.value = { code: '', description: '' }
  editingUldType.value = null
  showNewUldType.value = true
}

function startEditUldType(tp) {
  if (!tp.id) return
  editingUldType.value = tp
  newUldTypeForm.value = { code: tp.code, description: tp.description || '' }
  showNewUldType.value = true
}

function closeUldTypeModal() {
  showNewUldType.value = false
  editingUldType.value = null
}

async function saveNewUldType() {
  const f = newUldTypeForm.value
  if (!f.code.trim()) { toast.warning(t('settings.uldConfig.newTypeRequired')); return }
  try {
    if (editingUldType.value) {
      await uldTypeCatalogApi.update(editingUldType.value.id, {
        code: editingUldType.value.code,
        description: f.description?.trim() || null,
        isActive: editingUldType.value.isActive,
      })
      toast.success(t('settings.uldConfig.catalog.updated', { code: editingUldType.value.code }))
    } else {
      await uldTypeCatalogApi.create({
        code: f.code.trim().toUpperCase(),
        description: f.description?.trim() || null,
        isActive: true,
      })
      toast.success(t('settings.uldConfig.newTypeCreated', { code: f.code.trim().toUpperCase() }))
    }
    closeUldTypeModal()
    await loadUldCatalog()
  } catch (e) {
    toast.error(extractError(e, t('settings.uldConfig.toast.error')))
  }
}

async function toggleUldTypeActive(tp) {
  if (!tp.id) return
  try {
    await uldTypeCatalogApi.update(tp.id, {
      code: tp.code,
      description: tp.description || null,
      isActive: !tp.isActive,
    })
    await loadUldCatalog()
  } catch (e) {
    toast.error(extractError(e, t('settings.uldConfig.toast.error')))
  }
}

async function removeCatalogEntry(tp) {
  if (!tp.id) return
  if (!(await confirm({ message: t('settings.uldConfig.catalog.deleteConfirm', { code: tp.code }), danger: true }))) return
  try {
    await uldTypeCatalogApi.remove(tp.id)
    toast.success(t('settings.uldConfig.catalog.deleted', { code: tp.code }))
    await loadUldCatalog()
  } catch (e) {
    toast.error(extractError(e, t('settings.uldConfig.toast.error')))
  }
}

const commodityList = ref([])
const editingCommodity = ref(null)
const showCommodityCreate = ref(false)
const commodityForm = ref({ code: '', label: '', description: '', color: '#94a3b8', sortOrder: 0, isActive: true })
const commodityCreateForm = ref({ code: '', label: '', description: '', color: '#94a3b8', sortOrder: 0, isActive: true })
const commodityTotpCode = ref('')

/* El token de servicio BI no se hardcodea en el bundle. Se obtiene bajo
   demanda desde el backend (POST /auth/service-token, solo ADMIN/SUPER_USER)
   y se expone en el cliente únicamente para copiarlo a Power BI. */
const biToken = ref(null)
const biTokenLoading = ref(false)
const biTokenError = ref('')
const biTokenExpiry = computed(() => {
  if (!biToken.value) return ''
  const d = decodeJwtExpiry(biToken.value)
  return d ? d.toISOString().slice(0, 10) : ''
})
const currentOrigin = window.location.origin
const copied = ref(false)
const copiedEndpoint = ref('')
const customUrl = ref('')
const connectionStatus = ref(null)
const connectionTesting = ref(false)

function decodeJwtExpiry(token) {
  try {
    const payload = token.split('.')[1]
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    if (json.exp) return new Date(json.exp * 1000)
  } catch { /* noop */ }
  return null
}

async function loadServiceToken() {
  if (!canManageSettings.value) {
    biToken.value = null
    return
  }
  biTokenLoading.value = true
  biTokenError.value = ''
  try {
    const res = await authApi.generateServiceToken('bi@rannik.com')
    biToken.value = res?.data?.token || null
    if (!biToken.value) biTokenError.value = 'El backend no devolvió token de servicio'
  } catch (e) {
    biToken.value = null
    biTokenError.value = extractError(e, 'No se pudo generar el token de servicio')
  } finally {
    biTokenLoading.value = false
  }
}

const biEndpoints = [
  { path: '/api/bi/dashboard', desc: 'Resumen general (KPIs, totales, % completado)' },
  { path: '/api/bi/flights', desc: t('settings.apiBi.biFlightsDesc') },
  { path: '/api/bi/weight-report', desc: t('settings.apiBi.biWeightReportDesc') },
  { path: '/api/bi/summary', desc: t('settings.apiBi.biSummaryDesc') },
  { path: '/api/bi/daily', desc: t('settings.apiBi.biDailyDesc') },
  { path: '/api/bi/top-mawbs', desc: 'Top MAWBs por peso' },
  { path: '/api/bi/by-location', desc: 'Estadísticas por origen/destino' },
  { path: '/api/bi/timeline', desc: 'Timeline de actividad' },
  { path: '/api/bi/flight-performance', desc: t('settings.apiBi.biFlightPerformanceDesc') },
  { path: '/api/flights/list', desc: 'Lista de vuelos (paginado)' },
  { path: '/api/mawbs', desc: 'Lista de MAWBs (paginado)' },
]

const gatewayUrl = computed(() => {
  if (customUrl.value.trim()) return customUrl.value.trim().replace(/\/+$/, '')
  const loc = window.location
  return loc.origin
})

function copyToClipboard(text, label) {
  navigator.clipboard.writeText(text)
  copied.value = true
  copiedEndpoint.value = label || ''
  setTimeout(() => { copied.value = false; copiedEndpoint.value = '' }, 2000)
}

async function testConnection() {
  if (!biToken.value) {
    connectionStatus.value = { ok: false, msg: 'No hay token de servicio. Regénéralo antes de probar la conexión.' }
    return
  }
  connectionTesting.value = true
  connectionStatus.value = null
  try {
    const res = await client.get('/api/bi/dashboard', {
      params: { api_key: biToken.value }
    })
    if (res.status === 200 && res.data) {
      const keys = Object.keys(res.data)
      connectionStatus.value = { ok: true, msg: `Conexión exitosa — ${keys.length} campos en respuesta` }
    } else {
      connectionStatus.value = { ok: false, msg: `Respuesta inesperada: HTTP ${res.status}` }
    }
  } catch (e) {
    connectionStatus.value = { ok: false, msg: extractError(e, t('settings.apiBi.connectionError')) }
  } finally {
    connectionTesting.value = false
  }
}

async function loadAirlines() {
  try {
    const res = await airlinesApi.getAll()
    airlines.value = res.data || []
  } catch (e) {
    toast.error(extractError(e, t('settings.airlines.toast.error')))
  }
}

function openAirlineCreate() {
  airlineCreateForm.value = { code: '', name: '', iataCode: '', country: '', isActive: true }
  showAirlineCreate.value = true
}

async function saveAirlineCreate() {
  const f = airlineCreateForm.value
  if (!f.code || !f.name) { toast.warning(t('settings.airlines.validation')); return }
  try {
    await airlinesApi.create({ ...f, isActive: f.isActive ?? true })
    toast.success(t('settings.airlines.toast.created'))
    showAirlineCreate.value = false
    await loadAirlines()
  } catch (e) {
    toast.error(extractError(e, t('settings.airlines.toast.error')))
  }
}

function startAirlineEdit(a) {
  airlineForm.value = {
    id: a.id, code: a.code, name: a.name,
    iataCode: a.iataCode || '', country: a.country || '',
    isActive: a.isActive !== false,
  }
  editingAirline.value = a
}

async function saveAirlineEdit() {
  const f = airlineForm.value
  if (!f.code || !f.name) { toast.warning(t('settings.airlines.validation')); return }
  try {
    await airlinesApi.update(f.id, { ...f })
    toast.success(t('settings.airlines.toast.updated'))
    editingAirline.value = null
    await loadAirlines()
  } catch (e) {
    toast.error(extractError(e, t('settings.airlines.toast.error')))
  }
}

async function removeAirline(a) {
  if (!(await confirm({ message: t('settings.airlines.deleteConfirm'), danger: true }))) return
  try {
    await airlinesApi.delete(a.id)
    toast.success(t('settings.airlines.toast.deleted'))
    await loadAirlines()
  } catch (e) {
    toast.error(extractError(e, t('settings.airlines.toast.error')))
  }
}

async function loadTypeConfig() {
  if (!configAirlineId.value) return
  try {
    const res = await uldTypeConfigApi.getByAirline(configAirlineId.value)
    typeConfigs.value = (res.data || []).map(c => ({
      ...c,
      defaultTareLbs: c.defaultTareLbs != null ? Number(c.defaultTareLbs) : 0,
      maxGrossLbs: c.maxGrossLbs != null ? Number(c.maxGrossLbs) : null,
    }))
  } catch (e) {
    toast.error(extractError(e, t('settings.uldConfig.toast.error')))
  }
}

function addTypeConfigRow() {
  if (!configAirlineId.value) { toast.warning(t('settings.uldConfig.selectAirline')); return }
  typeConfigs.value.push({
    id: null, airlineId: configAirlineId.value, uldType: 'PMC',
    defaultTareLbs: 0, maxGrossLbs: null, notes: '',
  })
}

function removeTypeConfigRow(idx) {
  typeConfigs.value.splice(idx, 1)
}

async function saveTypeConfig() {
  if (!configAirlineId.value) return
  const rows = typeConfigs.value.map(r => ({
    uldType: r.uldType,
    defaultTareLbs: r.defaultTareLbs || 0,
    maxGrossLbs: r.maxGrossLbs || null,
    notes: r.notes || null,
  }))
  try {
    await uldTypeConfigApi.replaceForAirline(configAirlineId.value, rows)
    toast.success(t('settings.uldConfig.toast.saved'))
    await loadTypeConfig()
  } catch (e) {
    toast.error(extractError(e, t('settings.uldConfig.toast.error')))
  }
}

const roles = ['READ_ONLY', 'WAREHOUSE_ASSISTANT', 'OPERATIONS', 'TRAFFIC', 'LOAD_PLANNER', 'ADMIN', 'SUPER_USER']

const editForm = ref({ email: '', fullName: '', role: 'READ_ONLY', isActive: true, siteIds: [] })
const createForm = ref({ email: '', fullName: '', role: 'READ_ONLY', siteIds: [] })
const siteForm = ref({ code: '', name: '', country: '', isActive: true })
const siteCreateForm = ref({ code: '', name: '', country: '', isActive: true })

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  const q = searchQuery.value.toLowerCase()
  return users.value.filter(u =>
    u.email.toLowerCase().includes(q) || (u.fullName || '').toLowerCase().includes(q)
  )
})

function roleLabel(r) {
  const labels = {
    READ_ONLY: 'Solo Lectura',
    WAREHOUSE_ASSISTANT: 'Warehouse Asst',
    OPERATIONS: 'Operations',
    TRAFFIC: 'Traffic',
    LOAD_PLANNER: 'Load Planner',
    ADMIN: 'Admin',
    SUPER_USER: 'SuperUser',
  }
  return labels[r] || r
}

function userSiteNames(siteIds) {
  if (!siteIds) return []
  return siteIds.map(id => {
    const site = allSites.value.find(s => s.id === id)
    return site ? site.code : id
  })
}

async function loadUsers() {
  try {
    const res = await usersApi.getAll(auth.airlineId)
    users.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

async function loadSites() {
  try {
    const res = await sitesApi.getAll()
    allSites.value = res.data
  } catch (e) { toast.error(extractError(e)) }
}

function startEdit(user) {
  editingUser.value = user
  editForm.value = {
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    isActive: user.isActive,
    airlineId: user.airlineId,
    siteIds: user.siteIds || [],
  }
}

function cancelEdit() {
  editingUser.value = null
}

async function saveEdit() {
  if (!editingUser.value) return
  const editedId = editingUser.value.id
  try {
    const res = await usersApi.update(editedId, editForm.value)
    editingUser.value = null
    if (editedId === auth.userId) {
      auth.fullName = (res.data?.fullName || editForm.value.fullName)
      auth.persist()
    }
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

async function removeUser(user) {
  if (!(await confirm({ message: t('settings.users.deleteConfirm'), danger: true }))) return
  try {
    await usersApi.delete(user.id)
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

async function resetPass(user) {
  if (!(await confirm({ message: t('settings.users.resetPasswordConfirm'), danger: true }))) return
  try {
    await usersApi.resetPassword(user.id)
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

const showMfaSetup = ref(false)
const mfaSetupUser = ref(null)
const mfaSecret = ref('')
const mfaOtpAuthUrl = ref('')
const mfaVerifyCode = ref('')
const showTempPassword = ref(false)
const generatedPassword = ref('')

async function openMfaSetup(user) {
  try {
    const res = await usersApi.mfaSetup(user.id)
    mfaSetupUser.value = user
    mfaSecret.value = res.data.secret
    mfaOtpAuthUrl.value = res.data.otpAuthUrl
    mfaVerifyCode.value = ''
    showMfaSetup.value = true
  } catch (e) { toast.error(extractError(e)) }
}

async function confirmMfaEnable() {
  if (!mfaSetupUser.value || mfaVerifyCode.value.length !== 6) return
  try {
    await usersApi.mfaEnable(mfaSetupUser.value.id, mfaSecret.value, mfaVerifyCode.value)
    showMfaSetup.value = false
    mfaSetupUser.value = null
    toast.success(t('settings.users.toast.mfaEnabled'))
    await loadUsers()
  } catch (e) {
    toast.error(e.response?.data?.error || t('settings.users.toast.invalidCode'))
  }
}

function cancelMfaSetup() {
  showMfaSetup.value = false
  mfaSetupUser.value = null
  mfaSecret.value = ''
  mfaOtpAuthUrl.value = ''
  mfaVerifyCode.value = ''
}

async function disableMfaUser(user) {
  if (!(await confirm({ message: t('settings.users.confirmDisableMfa'), danger: true }))) return
  try {
    await usersApi.mfaDisable(user.id)
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

async function lockMfaUser(user) {
  if (!(await confirm({ message: t('settings.users.confirmLock'), danger: true }))) return
  try {
    await usersApi.mfaLock(user.id)
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

async function unlockMfaUser(user) {
  if (!(await confirm({ message: t('settings.users.confirmUnlock'), danger: true }))) return
  try {
    await usersApi.mfaUnlock(user.id)
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

function openCreate() {
  createForm.value = { email: '', fullName: '', role: 'READ_ONLY', siteIds: [] }
  showCreate.value = true
}

async function saveCreate() {
  try {
    await usersApi.create({ ...createForm.value, airlineId: auth.airlineId })
    showCreate.value = false
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

function openSiteCreate() {
  siteCreateForm.value = { code: '', name: '', country: '', isActive: true }
  showSiteCreate.value = true
}

async function saveSiteCreate() {
  try {
    await sitesApi.create(siteCreateForm.value)
    showSiteCreate.value = false
    await loadSites()
  } catch (e) { toast.error(extractError(e)) }
}

function startSiteEdit(site) {
  editingSite.value = site
  siteForm.value = {
    code: site.code,
    name: site.name,
    country: site.country || '',
    isActive: site.isActive,
  }
}

async function saveSiteEdit() {
  if (!editingSite.value) return
  try {
    await sitesApi.update(editingSite.value.id, siteForm.value)
    editingSite.value = null
    await loadSites()
  } catch (e) { toast.error(extractError(e)) }
}

async function removeSite(site) {
  if (!(await confirm({ message: t('settings.sites.deleteConfirm'), danger: true }))) return
  try {
    await sitesApi.delete(site.id)
    await loadSites()
  } catch (e) { toast.error(extractError(e)) }
}

async function genTempPassword(user) {
  if (!(await confirm({ message: t('settings.users.confirmGenTemp'), danger: true }))) return
  try {
    const res = await usersApi.generateResetLink(user.id)
    generatedPassword.value = res.data.resetLink
    showTempPassword.value = true
    await loadUsers()
  } catch (e) { toast.error(extractError(e)) }
}

async function copyPassword() {
  try {
    await navigator.clipboard.writeText(generatedPassword.value)
    toast.success(t('settings.users.toast.passwordCopied'))
  } catch {
    toast.error(t('settings.users.toast.copyFailed'))
  }
}

async function loadCommodities() {
  try {
    const res = await commodityTypesApi.getAll(false)
    commodityList.value = res.data || []
  } catch (e) { toast.error(extractError(e, t('settings.commodities.toast.error'))) }
}

function openCommodityCreate() {
  commodityCreateForm.value = { code: '', label: '', description: '', color: '#94a3b8', sortOrder: commodityList.value.length, isActive: true }
  commodityTotpCode.value = ''
  showCommodityCreate.value = true
}

function startCommodityEdit(c) {
  commodityForm.value = { code: c.code, label: c.label, description: c.description || '', color: c.color || '#94a3b8', sortOrder: c.sortOrder, isActive: c.isActive }
  commodityTotpCode.value = ''
  editingCommodity.value = c
}

async function saveCommodityEdit() {
  if (!editingCommodity.value || commodityTotpCode.value.length !== 6) return
  try {
    await commodityTypesApi.update(editingCommodity.value.id, commodityForm.value, commodityTotpCode.value)
    toast.success(t('settings.commodities.toast.updated'))
    editingCommodity.value = null
    commodityTotpCode.value = ''
    await loadCommodities()
  } catch (e) { toast.error(extractError(e, t('settings.commodities.toast.error'))) }
}

async function saveCommodityCreate() {
  if (commodityTotpCode.value.length !== 6) return
  const f = commodityCreateForm.value
  if (!f.code) { toast.warning(t('settings.commodities.codeRequired')); return }
  try {
    await commodityTypesApi.create({ ...f, isActive: f.isActive ?? true }, commodityTotpCode.value)
    toast.success(t('settings.commodities.toast.created'))
    showCommodityCreate.value = false
    commodityTotpCode.value = ''
    await loadCommodities()
  } catch (e) { toast.error(extractError(e, t('settings.commodities.toast.error'))) }
}

async function removeCommodity(c) {
  if (commodityTotpCode.value.length !== 6) { toast.warning(t('settings.commodities.totpRequired')); return }
  if (!(await confirm({ message: t('settings.commodities.deleteConfirm'), danger: true }))) return
  try {
    await commodityTypesApi.delete(c.id, commodityTotpCode.value)
    toast.success(t('settings.commodities.toast.deleted'))
    commodityTotpCode.value = ''
    await loadCommodities()
  } catch (e) { toast.error(extractError(e, t('settings.commodities.toast.error'))) }
}

async function restoreCommodityDefaults() {
  if (commodityTotpCode.value.length !== 6) { toast.warning(t('settings.commodities.totpRequired')); return }
  if (!(await confirm({ message: t('settings.commodities.restoreConfirm'), danger: true }))) return
  try {
    const res = await commodityTypesApi.restoreDefaults(commodityTotpCode.value)
    const restored = res.data?.restored || 0
    toast.success(t('settings.commodities.toast.restored', { n: restored }))
    commodityTotpCode.value = ''
    await loadCommodities()
  } catch (e) { toast.error(extractError(e, t('settings.commodities.toast.error'))) }
}

watch(
  () => activeTab.value,
  (tab) => {
    if (tab === 'api') loadServiceToken()
  }
)

onMounted(async () => {
  if (auth.role === 'BI_USER') {
    activeTab.value = 'api'
    return
  }
  await loadUsers()
  if (auth.role === 'SUPER_USER') {
    await loadSites()
  }
  if (canManageSettings.value) {
    await loadAirlines()
    await loadCommodities()
    await loadUldCatalog()
    if (airlines.value.length) {
      configAirlineId.value = airlines.value[0].id
      await loadTypeConfig()
    }
  }
})
</script>

<style scoped>
/* Tabs responsivos: en <=640px, scroll horizontal en vez de wrap/overflow visual.
   En tablet, se permiten wrap (filas) para no forzar scroll. */
@media (max-width: 640px) {
  .settings-tabs {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    flex-wrap: nowrap !important;
    padding-bottom: 4px;
  }
  .settings-tabs > button { flex: 0 0 auto; white-space: nowrap; }
}
</style>
