<template>
  <div class="ds-page">

    <header class="ds-section-header">
      <div class="flex flex-wrap items-center gap-3 md:gap-4">
        <div>
          <h1 class="ds-title">{{ t('bookings.title') }}</h1>
          <p class="ds-subtitle">{{ t('bookings.subtitle') }}</p>
        </div>
        <div class="ds-divider"></div>
        <div class="flex flex-col gap-0.5">
          <span class="text-[13px] font-black text-slate-950 uppercase tracking-widest">{{ t('flights.table.flight') }}</span>
          <select v-model="localFlightId" @change="onFlightChange"
            class="ds-input font-black uppercase tracking-widest cursor-pointer"
            :class="store.selectedFlight ? 'min-w-[280px]' : 'min-w-[160px]'">
            <option value="" disabled>{{ t('bookings.selectFlight') }}</option>
            <option v-for="flight in flightList" :key="flight.id" :value="flight.id">
              {{ airlineCodeById(flight.airlineId) }}-{{ flight.flightNumber }} ({{ flight.origin }}→{{ flight.destination }}) — {{ flight.flightDate }}
            </option>
          </select>
        </div>
        <div v-if="store.selectedFlight" class="flex gap-3 text-[13px] font-mono font-bold text-slate-700">
          <span>{{ store.selectedFlight.aircraftReg || '—' }}</span>
          <span>{{ store.selectedFlight.flightDate }}</span>
        </div>
        <FilterBar
          v-model:status="statusFilter"
          v-model:mawb-number="bkMawbFilter"
          v-model:shipper-name="bkShipperFilter"
          v-model:consignee-name="bkConsigneeFilter"
          v-model:destination="bkDestFilter"
          :show-status="true"
          :show-mawb="true"
          :show-shipper="true"
          :show-consignee="true"
          :show-destination="true"
          :status-options="statusOptions"
          :show-count="true"
          :filtered-count="visibleBookings.length"
          :total-count="store.bookings.length"
          container-class="!gap-1"
        />
      </div>
      <div class="flex items-center gap-2">
        <button @click="triggerImport" class="ds-btn-secondary">
          <span class="text-[14px] font-semibold leading-none">↑</span> {{ t('bookings.importXlsx') }}
        </button>
        <button @click="exportCSV" class="ds-btn-secondary">
          <span class="text-[14px] font-semibold leading-none">↓</span> {{ t('bookings.exportCsv') }}
        </button>
        <button @click="openCreate" class="ds-btn-primary">
          <span class="text-[14px] font-semibold leading-none">+</span> {{ t('bookings.newBooking') }}
        </button>
        <input type="file" ref="fileInput" @change="handleFileImport" accept=".xlsx,.xls" class="hidden" />
      </div>
    </header>

    <section class="ds-table-section">
      <div class="table-scroll-wrapper flex-1 min-h-0">
      <div class="ds-table-header" style="min-width: 860px">
        <div class="col-span-2 text-left relative">
          <span @click="toggleHeaderFilter('awb')" class="cursor-pointer select-none"
            :class="columnFilters.awb ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.booking') }} <span class="text-[10px]" :class="columnFilters.awb ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'awb'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('awb', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!columnFilters.awb ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in uniqueValues(flightBookings, b => b.awbNumber).slice(0, 200)" :key="v" @click="setColumnFilter('awb', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="columnFilters.awb === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-2 text-left relative">
          <span @click="toggleHeaderFilter('client')" class="cursor-pointer select-none"
            :class="columnFilters.client ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.clientName') }} <span class="text-[10px]" :class="columnFilters.client ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'client'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('client', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!columnFilters.client ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in uniqueValues(flightBookings, b => b.clientName).slice(0, 200)" :key="v" @click="setColumnFilter('client', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="columnFilters.client === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-2 text-left relative">
          <span @click="toggleHeaderFilter('shipper')" class="cursor-pointer select-none"
            :class="columnFilters.shipper ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.shipper') }} <span class="text-slate-300 font-normal">{{ t('bookings.table.receipt') }}</span> <span class="text-[10px]" :class="columnFilters.shipper ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'shipper'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('shipper', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!columnFilters.shipper ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in uniqueValues(flightBookings, b => b.shipperName).slice(0, 200)" :key="v" @click="setColumnFilter('shipper', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="columnFilters.shipper === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1 text-center relative">
          <span @click="toggleHeaderFilter('pieces')" class="cursor-pointer select-none"
            :class="columnFilters.pieces ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.pieces') }} <span class="text-[10px]" :class="columnFilters.pieces ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'pieces'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[160px] text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('pieces', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!columnFilters.pieces ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in uniqueValues(flightBookings, b => Number(b.skids || b.units || 0)).slice(0, 100)" :key="v" @click="setColumnFilter('pieces', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="columnFilters.pieces === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1 text-center relative">
          <span @click="toggleHeaderFilter('eaType')" class="cursor-pointer select-none"
            :class="columnFilters.eaType ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.unitType') }} <span class="text-[10px]" :class="columnFilters.eaType ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'eaType'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[140px] text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('eaType', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!columnFilters.eaType ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in ['SKID', 'BOX']" :key="v" @click="setColumnFilter('eaType', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="columnFilters.eaType === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1 text-right pr-2 relative">
          <span @click="toggleHeaderFilter('status')" class="cursor-pointer select-none inline-flex items-center"
            :class="columnFilters.status ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.weightKg') }} <span class="text-[10px]" :class="columnFilters.status ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'status'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[140px] text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('weight', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!columnFilters.weight ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in uniqueValues(flightBookings, b => Math.round(Number(b.reservedKg || 0))).slice(0, 100)" :key="v" @click="setColumnFilter('weight', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="columnFilters.weight === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1 text-center bg-slate-800 py-0.5 rounded border border-slate-600 text-white font-black tracking-wide relative">
          <span @click="toggleHeaderFilter('mawbStatus')" class="cursor-pointer select-none"
            :class="columnFilters.mawbStatus ? 'text-slate-300' : 'hover:text-white/80'">
            {{ t('bookings.table.mawbStatus') }} <span class="text-[10px]" :class="columnFilters.mawbStatus ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'mawbStatus'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[160px] text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('mawbStatus', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!columnFilters.mawbStatus ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
            <div v-for="v in ['BOOKED', 'RECEIVED', 'MANIFESTED', 'DEPARTED']" :key="v" @click="setColumnFilter('mawbStatus', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="columnFilters.mawbStatus === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1"></div>
      </div>

      <EmptyState v-if="store.loading && !store.bookings.length" :title="t('common.loading')" loading />

      <EmptyState v-else-if="deduplicatedBookings.length === 0"
        :title="store.selectedFlightId ? t('bookings.empty') : t('bookings.emptyCreate')"
        :hint="t('bookings.emptyHint')"
        :icon="icons.ClipboardList">
        <button v-if="store.selectedFlightId" @click="openCreate" class="ds-btn-primary mt-1">
          <component :is="icons.Plus" :size="14" :stroke-width="2.5" /> {{ t('bookings.createFirst') }}
        </button>
      </EmptyState>

      <div v-else class="divide-y divide-slate-100 text-[13px] text-slate-950 overflow-y-auto flex-1 min-h-0 scrollbar-none">
        <div v-for="b in deduplicatedBookings" :key="b.id"
          class="ds-table-row group">

          <div class="col-span-2 font-mono font-black text-slate-950 relative z-10 text-[13px] flex items-center gap-2">
            <button v-if="b.mawbId" @click="goToReceipt(b)"
              class="underline decoration-dotted underline-offset-2 hover:text-blue-700 transition-colors text-left"
              :title="t('bookings.openReceipt')">
              {{ b.awbNumber || b.id?.slice(0, 8) || 'N/A' }}
            </button>
            <span v-else>{{ b.awbNumber || b.id?.slice(0, 8) || 'N/A' }}</span>
            <span v-if="b._dupCount > 1" class="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-slate-100 text-slate-700 text-[12px] font-bold" :title="t('bookings.dupGroupedTooltip')">{{ b._dupCount }}x</span>
          </div>

          <div class="col-span-2 text-slate-950 font-semibold relative z-10 truncate pr-3">
            {{ b.clientName || '—' }}
          </div>

          <div class="col-span-2 text-slate-900 font-bold relative z-10 truncate pr-2 font-mono text-[13px] flex flex-col leading-tight">
            <span>{{ bookingReceipt(b)?.shipperName || b.shipperName || '—' }}</span>
            <span v-if="bookingReceipt(b)" class="text-[13px] text-slate-600 font-semibold">&#10003; {{ t('bookings.status.RECEIVED') }}</span>
          </div>

          <div class="col-span-1 text-center font-mono font-bold text-slate-900 relative z-10">
            <span v-if="bookingReceipt(b)">{{ bookingReceipt(b).pieceCount || '—' }}</span>
            <span v-else>{{ b.skids || b.units || '—' }}</span>
          </div>

          <div class="col-span-1 text-center font-mono font-bold text-slate-900 relative z-10 flex items-center justify-center gap-1">
            <template v-for="p in unitParts(b)" :key="p.type">
              <span class="inline-block px-1.5 py-0.5 rounded text-[11px] font-mono font-bold whitespace-nowrap"
                :title="p.type === 'SKID' ? t('bookings.skidPallet') : t('bookings.boxUnit')"
                :class="p.type === 'SKID' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-800'">
                {{ p.count }}{{ p.type === 'SKID' ? 'S' : 'B' }}
              </span>
            </template>
            <span v-if="!unitParts(b).length" class="text-slate-300">—</span>
          </div>

          <div class="col-span-1 text-right font-mono font-bold text-slate-950 relative z-10 pr-2">
            <template v-if="bookingReceipt(b)">
              {{ Number(bookingReceipt(b).chargeableWeightKg || bookingReceipt(b).actualWeightKg || 0).toLocaleString() }}<span class="text-[13px] text-slate-950 font-normal font-mono">k</span>
            </template>
            <template v-else>
              {{ b.reservedKg ? Number(b.reservedKg).toLocaleString() : '—' }}<span class="text-[13px] text-slate-950 font-normal font-mono">k</span>
            </template>
          </div>

          <div class="col-span-1 flex items-center justify-center gap-1.5 relative z-10">
            <div class="flex items-center gap-2 text-[17px] font-mono" :title="'MAWB: ' + getMawbStatus(b)">
              <span class="inline-block w-2.5 h-2.5" :class="getMawbStatusClass(b)"></span>
              <span class="px-1.5 py-0.5 rounded text-[12px] font-medium" :style="getMawbBadgeStyle(b)">{{ getMawbStatus(b) }}</span>
              <span v-if="getMawbStatus(b) !== '—'" class="text-slate-300 text-[13px]">·</span>
            </div>
          </div>
          <div class="col-span-1 flex justify-end relative z-10 ds-row-actions">
            <button @click.stop="openEdit(b)"
              class="text-slate-400 hover:text-blue-600 transition-colors p-1"
              :title="t('common.edit')">
              <component :is="icons.Pencil" :size="15" :stroke-width="1.5" />
            </button>
            <button @click.stop="removeBooking(b)"
              class="text-slate-400 hover:text-slate-600 transition-colors p-1"
              :title="t('common.delete')">
              <component :is="icons.Trash" :size="15" :stroke-width="1.5" />
            </button>
          </div>
        </div>
      </div>
      </div>
    </section>
    <div v-if="showModal" class="ds-modal-backdrop" @click.self="closeModal">
      <div class="ds-modal-panel">
        <div class="ds-modal-header">
          <h2 class="ds-modal-title">{{ editingBooking ? t('bookings.editBooking') : t('bookings.newBooking') }}</h2>
          <button @click="closeModal" class="text-slate-400 hover:text-slate-950 transition"><component :is="icons.X" :size="18" :stroke-width="2" /></button>
        </div>
          <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="ds-label">{{ t('bookings.form.client') }} *</label>
              <input v-model="form.clientName" type="text" :placeholder="t('bookings.form.clientPlaceholder')" class="ds-input" />
            </div>
            <div>
              <label class="ds-label">{{ t('bookings.form.contact') }} *</label>
              <input v-model="form.contactName" type="text" :placeholder="t('bookings.form.contactPlaceholder')" class="ds-input" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="ds-label">{{ t('common.shipper') }}</label>
              <input v-model="form.shipperName" type="text" :placeholder="t('bookings.form.shipperName')" class="ds-input" />
            </div>
            <div>
              <label class="ds-label">{{ t('bookings.form.consigneeLabel') }}</label>
              <input v-model="form.cnee" type="text" :placeholder="t('bookings.form.consigneeName')"
                class="ds-input" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="ds-label">{{ t('bookings.form.mawbNumber') }}</label>
              <input v-model="form.awbNumber" type="text" placeholder="UPS-XXX-XXXX"
                class="w-full text-[14px] font-mono px-4 py-2.5 rounded border border-slate-400 outline-none focus:border-slate-950 transition uppercase" />
            </div>
            <div>
              <label class="ds-label">{{ t('bookings.form.reservedKg') }} *</label>
              <input v-model.number="form.reservedKg" type="number" step="0.001"
                class="ds-input" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="ds-label">{{ t('common.destination') }}</label>
              <input v-model="form.destination" type="text" maxlength="3" placeholder="MIA"
                class="ds-input uppercase" />
            </div>
            <div class="grid grid-cols-3 gap-2">
              <div>
                <label class="ds-label">{{ t('bookings.form.skids') }}</label>
                <input v-model.number="form.skids" type="number" min="0"
                  class="ds-input" />
              </div>
              <div>
                <label class="ds-label">{{ t('bookings.form.units') }}</label>
                <input v-model.number="form.units" type="number" min="0"
                  class="ds-input" />
              </div>
              <div>
                <label class="ds-label">{{ t('bookings.form.eaType') }}</label>
                <select v-model="form.eaType" class="ds-input">
                  <option value="SKID">SKID</option>
                  <option value="BOX">BOX</option>
                </select>
              </div>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="ds-label">{{ t('bookings.form.commodityType') }}</label>
              <select v-model="form.commodityType"
                class="ds-input">
                <option v-for="c in commodityTypes" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
            <div>
              <label class="ds-label">{{ t('bookings.form.priority') }}</label>
              <input v-model.number="form.priority" type="number" min="0" max="10"
                class="ds-input" />
            </div>
          </div>
          <div>
            <label class="ds-label">{{ t('bookings.form.notes') }}</label>
            <textarea v-model="form.notes" rows="2" :placeholder="t('bookings.form.notesPlaceholder')"
              class="ds-input resize-none"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-6 pt-4 border-t border-slate-200">
          <button @click="closeModal"
            class="ds-btn-secondary">
            {{ t('common.cancel') }}
          </button>
          <button @click="saveBooking" :disabled="saving"
            class="ds-btn-primary">
            <span>{{ saving ? t('common.saving') : (editingBooking ? t('bookings.saveChanges') : t('bookings.create')) }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- IMPORT PREVIEW MODAL -->
    <div v-if="showImportModal" class="ds-modal-backdrop" @click.self="closeImportModal">
      <div class="bg-white rounded-xl border border-slate-200 shadow-2xl w-full max-w-4xl max-h-[80vh] flex flex-col">
        <div class="flex justify-between items-center px-6 py-4 border-b border-slate-200 shrink-0">
          <div>
            <h2 class="ds-modal-title">{{ t('bookings.import.previewTitle') }}</h2>
            <p class="text-[13px] font-mono text-slate-950 mt-0.5">{{ t('bookings.import.rowsFound', { n: parsedRows.length }) }}</p>
          </div>
          <button @click="closeImportModal" class="text-slate-950 hover:text-slate-950"><component :is="icons.X" :size="16" :stroke-width="2" /></button>
        </div>

        <div class="overflow-auto flex-1 min-h-0">
          <table class="w-full text-[13px] font-mono" style="min-width: 1100px">
            <thead class="bg-slate-100 sticky top-0 z-10">
              <tr class="text-[13px] font-black text-slate-950 uppercase tracking-wider">
                <th class="text-left px-5 py-3 border-b border-slate-400">#</th>
                <th class="text-left px-5 py-3 border-b border-slate-400">{{ t('bookings.form.client') }}</th>
                <th class="text-left px-5 py-3 border-b border-slate-400">{{ t('bookings.form.contact') }}</th>
                <th class="text-left px-5 py-3 border-b border-slate-400">{{ t('common.shipper') }}</th>
                <th class="text-left px-5 py-3 border-b border-slate-400">{{ t('bookings.import.cnee') }}</th>
                <th class="text-center px-4 py-3 border-b border-slate-400">{{ t('bookings.table.awb') }}</th>
                <th class="text-center px-4 py-3 border-b border-slate-400">{{ t('bookings.table.skids') }}</th>
                <th class="text-center px-4 py-3 border-b border-slate-400">{{ t('bookings.table.boxes') }}</th>
                <th class="text-center px-4 py-3 border-b border-slate-400">{{ t('bookings.table.unitType') }}</th>
                <th class="text-right px-4 py-3 border-b border-slate-400">{{ t('common.kg') }}</th>
                <th class="text-center px-4 py-3 border-b border-slate-400">{{ t('common.dest') }}</th>
                <th class="text-center px-4 py-3 border-b border-slate-400">{{ t('bookings.import.commodityShort') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-300">
              <tr v-for="(row, idx) in parsedRows" :key="idx" class="hover:bg-slate-100 transition-colors">
                <td class="px-5 py-3 text-slate-950">{{ idx + 1 }}</td>
                <td class="px-5 py-3 font-semibold text-slate-950">{{ row.clientName }}</td>
                <td class="px-5 py-3 text-slate-950">{{ row.contactName }}</td>
                <td class="px-5 py-3 text-slate-950 truncate max-w-[120px]">{{ row.shipperName }}</td>
                <td class="px-5 py-3 text-slate-950 truncate max-w-[120px]">{{ row.cnee }}</td>
                <td class="px-4 py-3 text-center text-slate-950 font-mono">{{ row.awbNumber || '—' }}</td>
                <td class="px-4 py-3 text-center font-bold text-slate-900">{{ row.skids || '—' }}</td>
                <td class="px-4 py-3 text-center font-bold text-slate-900">{{ row.units || '—' }}</td>
                <td class="px-4 py-3 text-center">
                  <span class="inline-block px-1.5 py-0.5 rounded text-[12px] font-semibold"
                    :class="row.eaType === 'SKID' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-800'">{{ row.eaType }}</span>
                </td>
                <td class="px-4 py-3 text-right font-bold text-slate-900">{{ row.reservedKg.toLocaleString() }}</td>
                <td class="px-4 py-3 text-center font-bold text-slate-950">{{ row.destination }}</td>
                <td class="px-4 py-3 text-center"><span class="inline-block text-[13px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-950 font-semibold">{{ row.commodityType }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex justify-between items-center px-6 py-4 border-t border-slate-200 bg-slate-100 rounded-b-xl shrink-0">
          <span class="text-[13px] font-mono text-slate-950">{{ t('bookings.import.willCreate', { n: parsedRows.length }) }}</span>
          <div class="flex gap-2">
            <button @click="closeImportModal"
              class="ds-btn-secondary">
              {{ t('common.cancel') }}
            </button>
            <button @click="confirmImport" :disabled="importing"
              class="ds-btn-primary">
              <span>{{ importing ? t('bookings.import.importing') : t('bookings.import.importN', { n: parsedRows.length }) }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useIcons } from '../composables/useIcons'
import * as XLSX from 'xlsx'

const icons = useIcons()
import { downloadCSV } from '../utils/csv'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import { extractError } from '../utils/error'
import { useCommodities } from '../composables/useCommodities'
import FilterBar from '../components/FilterBar.vue'
import EmptyState from '../components/EmptyState.vue'
import { useHeaderFilters } from '../composables/useHeaderFilters'

const store = useAppStore()
const router = useRouter()
const { t } = useI18n()
const hf = useHeaderFilters({ containerSelector: '.ds-table-header' })
const { headerFilterOpen, columnFilters, toggleHeaderFilter, setColumnFilter, uniqueValues } = hf
const { commodities: dbCommodities, loadCommodities } = useCommodities()
const toast = useToastStore()
const { confirm } = useConfirm()

const showModal = ref(false)
const saving = ref(false)
const editingBooking = ref(null)

const fileInput = ref(null)
const showImportModal = ref(false)
const parsedRows = ref([])
const importing = ref(false)

const flightList = computed(() => store.flights)
const localFlightId = ref(store.selectedFlightId)

const statusFilter = ref('')
const bkMawbFilter = ref('')
const bkShipperFilter = ref('')
const bkConsigneeFilter = ref('')
const bkDestFilter = ref('')

const statusOptions = [
  { value: 'BOOKED', label: 'Booked', dotClass: 'bg-slate-400' },
  { value: 'RECEIVED', label: 'Received', dotClass: 'bg-amber-400' },
  { value: 'MANIFESTED', label: 'Manifested', dotClass: 'bg-emerald-500' },
  { value: 'DEPARTED', label: 'Departed', dotClass: 'bg-blue-500' },
]

function onFlightChange() {
  if (localFlightId.value) {
    store.selectFlight(localFlightId.value)
  }
}



const COM_MAP = {
  'H/V': 'HIGH_VALUES',
  'GEN': 'GENERAL',
  'PAC': 'SMALL_PACKAGES',
  'WWEF': 'WWEF',
  'CIG': 'CIGARETTES',
  'PLAN': 'LIVE_PLANTS',
  'WAL': 'GENERAL',
  'PROD': 'GENERAL',
}

function parseCommodity(abbr) {
  const key = (abbr || '').trim().toUpperCase()
  return COM_MAP[key] || 'GENERAL'
}

function normalizeAwb(raw) {
  let s = String(raw || '').replace(/[\s\-_\/]/g, '')
  if (/^\d{11}$/.test(s)) {
    s = s.slice(0, 3) + '-' + s.slice(3)
  }
  return s
}

function parseBookingsFromXLSX(data) {
  const rows = []
  for (let i = 2; i < data.length; i++) {
    const r = data[i]
    const clientName = String(r[0] || '').trim()
    if (!clientName || clientName === clientName.toUpperCase()) continue
    const contactName = String(r[1] || '').trim()
    const awbRaw = r[2]
    const awbNumber = normalizeAwb(awbRaw)
    const skids = parseInt(r[3]) || 0
    const units = parseInt(r[4]) || 0
    const reservedKg = parseFloat(r[5]) || 0
    if (skids === 0 && units === 0 && reservedKg === 0) continue
    rows.push({
      clientName,
      contactName,
      shipperName: String(r[19] || '').trim() || contactName,
      cnee: String(r[18] || '').trim(),
      awbNumber,
      skids,
      units,
      eaType: skids > 0 ? 'SKID' : (units > 0 ? 'BOX' : 'SKID'),
      reservedKg,
      destination: String(r[9] || '').trim().toUpperCase() || 'MIA',
      commodityType: parseCommodity(r[11]),
      priority: parseInt(r[10]) || 0,
      notes: String(r[20] || '').trim(),
    })
  }
  return rows
}

const commodityTypes = computed(() => dbCommodities.value.map(c => c.code))

const flightBookings = computed(() =>
  store.selectedFlightId
    ? store.bookings.filter(b => b.flightId === store.selectedFlightId)
    : store.bookings
)

const visibleBookings = computed(() => {
  let list = flightBookings.value
  if (columnFilters.awb) {
    const q = columnFilters.awb
    list = list.filter(b => {
      const m = bookingMawb(b)
      return (b.awbNumber === q) || (m?.awbNumber === q)
    })
  }
  if (columnFilters.client) {
    const q = columnFilters.client
    list = list.filter(b => (b.clientName || '').toLowerCase() === String(q).toLowerCase())
  }
  if (columnFilters.shipper) {
    const q = columnFilters.shipper
    list = list.filter(b => {
      const m = bookingMawb(b)
      return (b.shipperName || m?.shipperName || '') === q
    })
  }
  if (columnFilters.pieces !== null && columnFilters.pieces !== undefined) {
    const q = Number(columnFilters.pieces)
    list = list.filter(b => Number(b.skids || b.units || 0) === q)
  }
  if (columnFilters.eaType) {
    list = list.filter(b => {
      const parts = unitParts(b)
      return columnFilters.eaType === 'SKID'
        ? parts.some(p => p.type === 'SKID')
        : parts.some(p => p.type === 'BOX')
    })
  }
  if (columnFilters.weight !== null && columnFilters.weight !== undefined) {
    const q = Number(columnFilters.weight)
    list = list.filter(b => Math.round(Number(b.reservedKg || 0)) === q)
  }
  if (columnFilters.mawbStatus) {
    const q = columnFilters.mawbStatus
    list = list.filter(b => {
      const m = bookingMawb(b)
      const s = q === 'BOOKED' ? (!m?.status || m.status === 'BOOKED') : m?.status === q
      return s
    })
  }
  if (statusFilter.value) {
    list = list.filter(b => {
      const m = bookingMawb(b)
      const s = m?.status || 'BOOKED'
      return s === statusFilter.value
    })
  }
  if (bkMawbFilter.value) {
    const q = bkMawbFilter.value.trim().toLowerCase()
    list = list.filter(b => {
      const m = bookingMawb(b)
      return (b.awbNumber || '').toLowerCase().includes(q) || (m?.awbNumber || '').toLowerCase().includes(q)
    })
  }
  if (bkShipperFilter.value) {
    const q = bkShipperFilter.value.trim().toLowerCase()
    list = list.filter(b => {
      const m = bookingMawb(b)
      return (b.shipperName || '').toLowerCase().includes(q) || (m?.shipperName || '').toLowerCase().includes(q)
    })
  }
  if (bkConsigneeFilter.value) {
    const q = bkConsigneeFilter.value.trim().toLowerCase()
    list = list.filter(b => (b.clientName || '').toLowerCase().includes(q) || (b.contactName || '').toLowerCase().includes(q))
  }
  if (bkDestFilter.value) {
    const d = bkDestFilter.value.toUpperCase()
    list = list.filter(b => (b.destination || '').toUpperCase() === d)
  }
  return list
})

const deduplicatedBookings = computed(() => {
  const groups = {}
  for (const b of visibleBookings.value) {
    const key = b.mawbId || b.awbNumber || b.id
    if (!groups[key]) {
      groups[key] = { booking: b, count: 1 }
    } else {
      groups[key].count++
      if ((Number(b.skids) || 0) > (Number(groups[key].booking.skids) || 0)) {
        groups[key].booking = b
      }
    }
  }
  return Object.values(groups).map(g => ({ ...g.booking, _dupCount: g.count }))
})

function flightNumber(flightId) {
  if (!flightId) return '—'
  const f = store.flights.find(f => f.id === flightId)
  return f ? `${airlineCodeById(f.airlineId)}-${f.flightNumber}` : flightId.slice(0, 8)
}

function airlineCodeById(airlineId) {
  const a = store.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

function unitParts(b) {
  const parts = []
  const skids = Number(b.skids) || 0
  const units = Number(b.units) || 0
  if (skids > 0) parts.push({ type: 'SKID', count: skids })
  if (units > 0) parts.push({ type: 'BOX', count: units })
  return parts
}

const form = ref({
  clientName: '',
  contactName: '',
  shipperName: '',
  cnee: '',
  awbNumber: '',
  skids: 1,
  units: 0,
  eaType: 'SKID',
  reservedKg: null,
  destination: 'MIA',
  commodityType: 'GENERAL',
  priority: 0,
  notes: '',
})

function bookingMawb(b) {
  if (!b.mawbId) return null
  return store.mawbs.find(m => m.id === b.mawbId || m.flightId === b.flightId && m.awbNumber === b.awbNumber) || null
}

function goToReceipt(b) {
  const m = bookingMawb(b)
  const mawbId = m?.id || b.mawbId
  router.push({ name: 'receipts', query: { mawbId } })
}

function bookingReceipt(b) {
  const m = bookingMawb(b)
  if (!m) return null
  const all = (store.receipts || []).filter(r => r.mawb?.id === m.id || r.mawbId === m.id)
  if (all.length === 0) return null
  // Prefer the general receipt (no hawbId) which contains all pieces
  return all.find(r => !r.hawbId) || all[all.length - 1]
}

function getMawbStatus(b) {
  const m = bookingMawb(b)
  if (!m) return '—'
  return m.status || 'BOOKED'
}

function getMawbStatusClass(b) {
  const s = getMawbStatus(b)
  if (s === 'RECEIVED') return 'bg-amber-400'
  if (s === 'MANIFESTED') return 'bg-emerald-500'
  if (s === 'DEPARTED' || s === 'ARRIVED') return 'bg-blue-500'
  if (s === 'BOOKED' || s === '—') return 'bg-slate-400'
  return 'bg-slate-300'
}

function getMawbBadgeStyle(b) {
  const s = getMawbStatus(b)
  if (s === 'RECEIVED') return { background: '#fef3c7', color: '#92400e' }
  if (s === 'MANIFESTED') return { background: '#d1fae5', color: '#065f46' }
  if (s === 'DEPARTED' || s === 'ARRIVED') return { background: '#dbeafe', color: '#1e40af' }
  return { background: 'var(--bg, #f1f5f9)', color: 'var(--text, #475569)' }
}

function exportCSV() {
  const headers = ['AWB', 'Client', 'Shipper', 'Destination', 'Skids', 'Kg', 'Status', 'Flight']
  const rows = deduplicatedBookings.value.map(b => [
    b.awbNumber || '',
    b.clientName || '',
    b.shipperName || '',
    b.destination || '',
    b.skids || '',
    b.reservedKg || '',
    getMawbStatus(b),
    flightNumber(b.flightId),
  ])
  downloadCSV(headers, rows, `bookings-${new Date().toISOString().slice(0, 10)}.csv`)
}

function openCreate() {
  editingBooking.value = null
  const flightNum = store.selectedFlight?.flightNumber || 'XXX'
  form.value = {
    clientName: '', contactName: '', shipperName: '', cnee: '',
    awbNumber: `${airlineCodeById(store.selectedFlight?.airlineId)}-${flightNum}-${Date.now().toString(36).toUpperCase()}`,
    skids: 1, units: 0, eaType: 'SKID',
    reservedKg: null, destination: 'MIA', commodityType: 'GENERAL', priority: 0, notes: ''
  }
  showModal.value = true
}

function openEdit(b) {
  editingBooking.value = b
  form.value = {
    clientName: b.clientName || '',
    contactName: b.contactName || '',
    shipperName: b.shipperName || '',
    cnee: b.cnee || '',
    awbNumber: b.awbNumber || '',
    skids: b.skids || 0,
    units: b.units || 0,
    eaType: b.eaType || 'SKID',
    reservedKg: b.reservedKg || null,
    destination: b.destination || '',
    commodityType: b.commodityType || 'GENERAL',
    priority: b.priority || 0,
    notes: b.notes || '',
  }
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  editingBooking.value = null
}

function triggerImport() {
  fileInput.value?.click()
}

function handleFileImport(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    try {
      const data = new Uint8Array(ev.target.result)
      const wb = XLSX.read(data, { type: 'array' })
      const ws = wb.Sheets[wb.SheetNames[0]]
      const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })
      parsedRows.value = parseBookingsFromXLSX(rows)
      showImportModal.value = true
    } catch (err) {
      toast.error(t('bookings.toast.fileReadError', { error: extractError(err) }))
    }
  }
  reader.readAsArrayBuffer(file)
  e.target.value = ''
}

function closeImportModal() {
  showImportModal.value = false
  parsedRows.value = []
}

async function confirmImport() {
  if (!store.selectedFlightId) {
    toast.warning(t('bookings.selectFlightFirst'))
    return
  }
  if (parsedRows.value.length === 0) return
  importing.value = true
  let idx = -1
  let success = 0
  let errors = 0
  for (const row of parsedRows.value) {
    idx++
    try {
      const dto = {
        airlineId: store.selectedFlight?.airlineId,
        flightId: store.selectedFlightId,
        awbNumber: row.awbNumber,
        clientName: row.clientName,
        contactName: row.contactName,
        shipperName: row.shipperName || row.clientName,
        cnee: row.cnee,
        reservedKg: row.reservedKg,
        skids: row.skids || 1,
        units: row.units || 0,
        eaType: row.eaType || 'SKID',
        destination: row.destination,
        commodityType: row.commodityType,
        priority: row.priority,
        notes: row.notes,
      }
      const booking = await store.createBooking(dto)
      if (booking?.id) {
        const awbNumber = row.awbNumber || `406-${(Date.now() + idx).toString().slice(-8).padStart(8, '0')}`
        const mawb = await store.createMawb({
          airlineId: store.selectedFlight?.airlineId,
          flightId: store.selectedFlightId,
          awbNumber: awbNumber,
          shipperName: row.shipperName || row.clientName,
          consigneeName: row.cnee || row.clientName,
          origin: store.selectedFlight?.origin || 'SDQ',
          destination: row.destination || store.selectedFlight?.destination || 'MIA',
          pieces: row.skids || row.units || 1,
          reportedWeightKg: row.reservedKg,
          chargeableWeightKg: row.reservedKg,
          commodityType: row.commodityType,
          status: 'BOOKED',
        })
        const mawbData = mawb.mawb || mawb
        if (mawb.weightWarning) {
          console.warn('⚠', mawb.weightWarning)
        }
        if (mawbData?.id) {
          await store.updateBooking(booking.id, { ...dto, mawbId: mawbData.id })
        }
      }
      success++
    } catch (e) {
      toast.error(extractError(e))
      const apiMsg = e.response?.data?.error || e.response?.data?.message || ''
      console.warn('Error importing row:', row.clientName, e.message, apiMsg)
      errors++
    }
  }
  await Promise.all([
    store.loadBookings(store.selectedFlightId),
    store.loadMawbs(store.selectedFlightId),
  ])
  importing.value = false
  closeImportModal()
  toast.success(`Importación completada: ${success} exitosos, ${errors} errores`)
}

async function saveBooking() {
  if (!form.value.clientName || !form.value.contactName || !form.value.reservedKg) {
    toast.warning('Cliente, Contacto y Peso Reservado son obligatorios')
    return
  }
  if (!store.selectedFlightId) {
    toast.warning(t('bookings.selectFlightFirst'))
    return
  }
  try {
    saving.value = true
    const dto = {
      airlineId: store.selectedFlight?.airlineId,
      flightId: store.selectedFlightId,
      awbNumber: form.value.awbNumber,
      clientName: form.value.clientName,
      contactName: form.value.contactName,
      shipperName: form.value.shipperName || form.value.clientName,
      cnee: form.value.cnee,
      reservedKg: form.value.reservedKg || 0,
      skids: form.value.skids || 1,
      units: form.value.units || 0,
      eaType: form.value.eaType || 'SKID',
      destination: form.value.destination,
      commodityType: form.value.commodityType,
      priority: form.value.priority,
      notes: form.value.notes,
    }
    if (editingBooking.value) {
      await store.updateBooking(editingBooking.value.id, dto)
      await Promise.all([
        store.loadBookings(store.selectedFlightId),
        store.loadMawbs(store.selectedFlightId),
      ])
    } else {
      const booking = await store.createBooking(dto)
      if (booking?.id) {
        const awbNumber = form.value.awbNumber || `406-${Date.now().toString().slice(-8).padStart(8, '0')}`
        try {
          const mawb = await store.createMawb({
            airlineId: store.selectedFlight?.airlineId,
            flightId: store.selectedFlightId,
            awbNumber: awbNumber,
            shipperName: form.value.shipperName || form.value.clientName,
            consigneeName: form.value.cnee || form.value.clientName,
            origin: store.selectedFlight?.origin || 'SDQ',
            destination: form.value.destination || store.selectedFlight?.destination || 'MIA',
            pieces: form.value.skids || form.value.units || 1,
            reportedWeightKg: form.value.reservedKg || 0,
            chargeableWeightKg: form.value.reservedKg || 0,
            commodityType: form.value.commodityType || 'GENERAL',
            status: 'BOOKED',
          })
          const mawbData = mawb.mawb || mawb
          if (mawb.weightWarning) {
            console.warn('⚠', mawb.weightWarning)
          }
          if (mawbData?.id) {
            await store.updateBooking(booking.id, { ...dto, mawbId: mawbData.id })
          }
        } catch (e2) {
          toast.error(extractError(e2))
          const apiMsg = e2.response?.data?.error || e2.response?.data?.message || ''
          console.warn('MAWB creation non-critical:', e2.message, apiMsg)
        }
      }
      await Promise.all([
        store.loadBookings(store.selectedFlightId),
        store.loadMawbs(store.selectedFlightId),
      ])
    }
    closeModal()
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    saving.value = false
  }
}

async function removeBooking(b) {
  const keys = b._dupCount > 1 ? store.bookings.filter(x => (x.mawbId === b.mawbId) || (!b.mawbId && x.awbNumber === b.awbNumber)).map(x => x.clientName).filter(Boolean) : []
  const msg = keys.length > 1
    ? `¿Eliminar ${keys.length} bookings agrupados (${keys.join(', ')})?`
    : `¿Eliminar booking de ${b.clientName || '—'} (${b.awbNumber || b.id?.slice(0, 8) || 'N/A'})?`
  if (!(await confirm({ message: msg }))) return
  try {
    if (b._dupCount > 1) {
      const group = store.bookings.filter(x => (x.mawbId === b.mawbId) || (!b.mawbId && x.awbNumber === b.awbNumber))
      await Promise.all(group.map(x => store.deleteBooking(x.id).catch((e) => { toast.error(extractError(e)) })))
    } else {
      await store.deleteBooking(b.id)
    }
  } catch (e) {
    toast.error('Error al eliminar: ' + extractError(e))
  }
}

onMounted(async () => {
  if (!store.airlines.length) {
    await store.loadAirlines()
  }
  if (!store.flights.length) {
    await store.loadFlights()
  }
  if (store.selectedFlightId) {
    localFlightId.value = store.selectedFlightId
  }
  store.loadBookings()
  store.loadAllMawbs()
  loadCommodities()
})

watch(() => store.selectedFlightId, (id) => {
  localFlightId.value = id
})
</script>

