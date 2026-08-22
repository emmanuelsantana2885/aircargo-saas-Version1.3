<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div class="flex items-end gap-3 flex-1 min-w-0 flex-wrap">
        <div>
          <h1 class="ds-title">{{ t('warehouse.title') }}</h1>
          <p class="ds-subtitle hidden sm:block">{{ t('warehouse.subtitle') }}</p>
        </div>
        <div class="h-8 w-[1px] bg-slate-200 hidden sm:block"></div>
        <div class="flex flex-col gap-0.5 opacity-50 hidden md:flex">
          <span class="ds-label hidden sm:block">{{ t('common.flight') }}</span>
          <select disabled
            class="bg-slate-100 border border-slate-300 rounded px-2 py-1 font-black text-slate-800 uppercase tracking-widest text-[15px] min-w-[120px] cursor-not-allowed">
            <option value="">{{ t('common.all') }}</option>
            <option v-for="f in store.flights" :key="f.id" :value="f.id">
              {{ airlineCodeById(f.airlineId) }}-{{ f.flightNumber }} ({{ f.origin }}→{{ f.destination }})
            </option>
          </select>
        </div>
        <div class="flex flex-col gap-0.5 flex-1 min-w-[140px] max-w-[280px]">
          <span class="ds-label hidden sm:block">{{ t('common.search') }} (* &lt; &gt; =)</span>
          <input v-model="filterTextRaw" type="text" :placeholder="t('common.search')"
            class="ds-input" />
        </div>
        <div class="flex flex-col gap-0.5">
          <span class="ds-label hidden sm:block">{{ t('common.date') }}</span>
          <input v-model="filterDate" type="date"
            class="ds-input" />
        </div>
      </div>
        <div class="flex items-center gap-2 text-[13px] font-mono font-bold text-slate-950 shrink-0">
        <span class="h-1.5 w-1.5 rounded-full bg-slate-500 "></span> {{ filteredMawbs.length }}/{{ store.mawbs.length }} MAWBs
      </div>
    </header>

    <div v-if="overdueMawbs.length > 0 && !overdueMawbsDismissed"
      class="mx-3 mb-2 px-4 py-2.5 bg-amber-50 border border-amber-300 rounded-lg flex items-start gap-3 text-[13px]">
      <span class="text-amber-500 text-[20px] leading-none mt-0.5 shrink-0">&#9888;</span>
      <div class="flex-1 min-w-0">
        <span class="font-bold text-amber-800">{{ overdueMawbs.length }} {{ t('warehouse.overdue.title') }}</span>
        <span class="text-amber-700 ml-1">— {{ t('warehouse.overdue.desc') }}</span>
        <div class="mt-1 flex flex-wrap gap-x-3 gap-y-0.5">
          <span v-for="o in overdueMawbs" :key="o.mawb.id"
            class="font-mono font-bold text-amber-900 cursor-pointer hover:underline"
            @click="toggleExpand(o.mawb)">
            {{ o.mawb.awbNumber || o.mawb.id?.slice(0, 8) }}
            <span class="font-normal text-amber-600 text-[12px]">({{ o.flight.flightNumber || t('warehouse.overdue.noFlight') }})</span>
          </span>
        </div>
      </div>
      <button @click="overdueMawbsDismissed = true" class="text-amber-400 hover:text-amber-600 text-[18px] leading-none shrink-0 mt-0.5" title="Cerrar">&times;</button>
    </div>

    <section class="ds-table-section mb-1.5">
      <div class="overflow-x-auto shrink-0">
      <div class="ds-table-header border-b border-slate-600" style="min-width: 700px">
        <div class="col-span-1 text-left flex items-center gap-1">
          <input type="checkbox" :checked="selectedMawbIds.size === filteredMawbs.length && filteredMawbs.length > 0"
            @change="toggleSelectAll"
            class="accent-slate-700 rounded w-3 h-3 cursor-pointer" />
        </div>
        <div class="col-span-2 text-left relative">
          <span @click="toggleHeaderFilter('mawb')"
            class="cursor-pointer select-none transition-all duration-150"
            :class="columnFilters.mawb ? 'text-slate-300' : 'hover:text-white/80'">
            MAWB <span class="text-[10px]" :class="columnFilters.mawb ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'mawb'"
            class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[200px] max-h-[200px] overflow-y-auto text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('mawb', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!columnFilters.mawb ? 'bg-slate-100' : ''">Todos</div>
            <div v-for="v in uniqueValues.mawb" :key="v" @click="setColumnFilter('mawb', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="columnFilters.mawb === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-2 text-left relative">
          <span @click="toggleHeaderFilter('shipper')"
            class="cursor-pointer select-none transition-all duration-150"
            :class="columnFilters.shipper ? 'text-slate-300' : 'hover:text-white/80'">
            Shipper <span class="text-[10px]" :class="columnFilters.shipper ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'shipper'"
            class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[200px] max-h-[200px] overflow-y-auto text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('shipper', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!columnFilters.shipper ? 'bg-slate-100' : ''">Todos</div>
            <div v-for="v in uniqueValues.shipper" :key="v" @click="setColumnFilter('shipper', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="columnFilters.shipper === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1 text-center">{{ t('common.pieces') }}</div>
        <div class="col-span-2 text-right pr-2">Peso (kg)</div>
        <div class="col-span-1 text-center relative">
          <span @click="toggleHeaderFilter('dest')"
            class="cursor-pointer select-none transition-all duration-150"
            :class="columnFilters.dest ? 'text-slate-300' : 'hover:text-white/80'">
            Dest <span class="text-[10px]" :class="columnFilters.dest ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'dest'"
            class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[200px] overflow-y-auto text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('dest', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!columnFilters.dest ? 'bg-slate-100' : ''">Todos</div>
            <div v-for="v in uniqueValues.dest" :key="v" @click="setColumnFilter('dest', v)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="columnFilters.dest === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
          </div>
        </div>
        <div class="col-span-1 text-center">Docs</div>
        <div class="col-span-2 text-center relative">
          <span @click="toggleHeaderFilter('status')"
            class="cursor-pointer select-none transition-all duration-150 inline-flex items-center gap-1"
            :class="columnFilters.status ? 'text-slate-300' : 'hover:text-white/80'">
            <span v-if="columnFilters.status" class="w-1.5 h-1.5 rounded-full inline-block"
              :class="statusDotClass[columnFilters.status] || 'bg-slate-400'"></span>
            Estado <span class="text-[10px]" :class="columnFilters.status ? 'opacity-100' : 'opacity-40'">&#9660;</span>
          </span>
          <div v-if="headerFilterOpen === 'status'"
            class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[160px] text-[13px] text-slate-950 font-normal normal-case">
            <div @click="setColumnFilter('status', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!columnFilters.status ? 'bg-slate-100' : ''">Todos</div>
            <div v-for="opt in statusOptions" :key="opt.key" @click="setColumnFilter('status', opt.key)"
              class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 flex items-center gap-2" :class="columnFilters.status === opt.key ? 'bg-slate-50 text-slate-700 font-bold' : ''">
              <span class="w-2 h-2 rounded-full" :class="opt.dotClass"></span>
              {{ opt.label }}
            </div>
          </div>
        </div>
      </div>
      </div>
      <div v-if="selectedMawbIds.size > 0" class="flex items-center gap-2 px-5 py-1.5 bg-slate-50 border-b border-slate-200 text-[13px] flex-wrap">
        <span class="font-mono font-bold text-slate-950">{{ selectedMawbIds.size }} seleccionados</span>
        <select v-model="bulkStatusTarget" class="bg-white border border-slate-400 rounded px-2 py-0.5 text-[14px] font-bold font-mono">
          <option value="">Cambiar estado...</option>
          <option v-for="s in statusSteps" :key="s.key" :value="s.key">{{ s.label }}</option>
        </select>
        <button @click="applyBulkStatus" class="ds-btn-primary text-[11px] px-2 py-0.5">
          Aplicar
        </button>
        <button @click="selectedMawbIds.clear()" class="text-slate-950 hover:text-slate-950 text-[14px] font-mono underline ml-auto">{{ t('warehouse.signatures.clear') }}</button>
      </div>

      <div v-if="store.mawbs.length === 0" class="flex-1 flex items-center justify-center">
        <p class="text-[14px] font-mono text-slate-950 uppercase tracking-widest">No hay MAWBs</p>
      </div>
      <div v-else-if="filteredMawbs.length === 0" class="flex-1 flex items-center justify-center">
        <p class="text-[14px] font-mono text-slate-950 uppercase tracking-widest">Ningún MAWB coincide con el filtro</p>
      </div>
      <div v-else class="divide-y divide-slate-200 text-[13px] text-slate-950 overflow-y-auto flex-1 min-h-0 thin-scrollbar">
          <div v-for="m in filteredMawbs" :key="m.id" class="flex flex-col">
          <div class="overflow-x-auto">
          <div class="grid grid-cols-12 items-center py-1.5 px-5 transition-all duration-150 cursor-pointer border-t"
            :class="[expandedId === m.id ? 'row-selected' : '', selectedMawbIds.has(m.id) ? 'bg-slate-50/50' : '', overdueSet.has(m.id) ? 'bg-amber-50/60 border-l-2 !border-l-amber-400' : '']" style="border-color: var(--border); min-width: 700px;"
            @click="toggleExpand(m)">
            <div class="col-span-1 flex items-center justify-center relative z-10">
              <input type="checkbox" :checked="selectedMawbIds.has(m.id)"
                @click.stop @change="toggleSelect(m.id)"
                class="accent-slate-700 rounded w-3 h-3 cursor-pointer" />
            </div>
            <div class="col-span-2 font-mono font-bold text-slate-950 relative z-10 flex items-center gap-1.5">
              <span class="text-[12px] text-slate-950 transition-transform duration-200" :class="{ 'rotate-90': expandedId === m.id }">&#9654;</span>
              {{ m.awbNumber || m.id?.slice(0, 8) || '—' }}
              <span v-if="receiptHawbs[m.id] && receiptHawbs[m.id].length > 1"
                class="text-[13px] font-black text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded leading-none"
                title="Múltiples HAWBs">{{ receiptHawbs[m.id].length }} HAWBs</span>
              <span v-else-if="receiptHawbs[m.id] && receiptHawbs[m.id].length === 1"
                class="text-[13px] font-black text-slate-950 bg-slate-100 px-1.5 py-0.5 rounded leading-none">1 HAWB</span>
            </div>
            <div class="col-span-2 text-slate-950 font-semibold relative z-10 truncate pr-3">{{ m.shipperName || '—' }}</div>
            <div class="col-span-1 text-center font-mono font-bold relative z-10"
              :class="receiptTotals[m.id]?.pieces > 0 ? 'text-slate-700' : 'text-slate-950'">
              {{ receiptTotals[m.id]?.pieces || m.pieces || '—' }}
              <span v-if="receiptTotals[m.id]?.pieces > 0 && receiptTotals[m.id]?.pieces !== m.pieces" class="text-[13px] text-slate-500 block leading-tight">rec: {{ receiptTotals[m.id].pieces }}</span>
            </div>
            <div class="col-span-2 text-right font-mono font-bold relative z-10 pr-2"
              :class="receiptTotals[m.id]?.weightKg > 0 ? 'text-slate-700' : 'text-slate-950'">
              {{ receiptTotals[m.id]?.weightKg ? Number(receiptTotals[m.id].weightKg).toLocaleString() : (m.reportedWeightKg ? Number(m.reportedWeightKg).toLocaleString() : '—') }}
              <span v-if="receiptTotals[m.id]?.weightKg > 0 && receiptTotals[m.id]?.weightKg !== Number(m.reportedWeightKg)" class="text-[13px] text-slate-500 block leading-tight">recibo</span>
            </div>
            <div class="col-span-1 text-center font-mono font-bold text-slate-950 relative z-10">{{ m.destination || '—' }}</div>
            <div class="col-span-1 flex items-center justify-center gap-1 relative z-10">
              <button @click.stop="editOrExpandReceipt(m)" title="Editar/Crear recibo"
                class="text-[16px] px-1 py-0.5 rounded transition font-bold leading-none shadow-sm"
                :class="receiptById[m.id] ? 'bg-amber-500 text-white hover:bg-amber-600' : 'bg-slate-200 text-slate-600 hover:bg-slate-300'">&#9998;</button>
              <template v-if="receiptById[m.id]">
                <button @click.stop="downloadReceiptById(m)" title="Descargar Excel"
                  class="text-[15px] px-1 py-0.5 rounded hover:bg-slate-100 hover:text-slate-700 transition leading-none">&#11015;</button>
                <button @click.stop="downloadHtmlById(m)" title="Descargar HTML evidencias"
                  class="text-[15px] px-1 py-0.5 rounded hover:bg-slate-200 hover:text-slate-950 transition leading-none">&#128196;</button>
                <button @click.stop="downloadPdfById(m)" title="Descargar PDF evidencias"
                  class="text-[15px] px-1 py-0.5 rounded hover:bg-slate-100 hover:text-slate-700 transition leading-none">&#128213;</button>
              </template>
            </div>
            <div class="col-span-2 flex items-center gap-2 relative z-10">
              <div class="flex items-center gap-1">
                <span v-for="s in statusSteps" :key="s.key"
                  @click.stop="changeMawbStatus(m, s.key)"
                  class="h-3 w-3 rounded-full border-2 transition-all duration-150 cursor-pointer hover:scale-150"
                  :class="getStatusDot(m, s)"
                  :title="s.key + ((m.status || 'BOOKED') === s.key ? ' (actual)' : ' → clic')"></span>
              </div>
              <span class="text-[12px] font-mono font-bold uppercase tracking-wider whitespace-nowrap"
                :class="statusLabelClass(m)">{{ statusLabel(m) }}</span>
              <span v-if="overdueSet.has(m.id)"
                class="text-amber-500 text-[16px] leading-none animate-pulse" title="Recibido pero vuelo ya pasó — pendiente de despacho">&#9888;</span>
              <button @click.stop="toggleMawbEvidenceManager(m)"
                class="ml-auto text-[13px] px-1.5 py-0.5 rounded border border-slate-300 text-slate-950 hover:text-slate-950 hover:border-slate-950 transition font-mono"
                title="Gestionar evidencias documentales de esta MAWB">
                &#128193;
              </button>
            </div>
          </div>
          </div>

          <div v-if="expandedId === m.id && receiptForms[m.id]" class="bg-slate-100 border-b border-slate-400">
            <div class="p-2 md:p-3 flex flex-col" style="height: calc(100vh - 240px); min-height: 300px;">
              <!-- Step progress bar -->
              <div class="mb-2 shrink-0">
                <div class="flex items-center justify-between">
                  <div v-for="(step, si) in steps" :key="si" class="flex items-center flex-1">
                    <div @click="localStep = si + 1"
                      class="flex flex-col items-center cursor-pointer group flex-1 min-w-0">
                      <div class="flex items-center w-full">
                        <div class="flex items-center justify-center w-7 h-7 rounded-full text-[14px] font-black font-mono transition-all duration-200 border-2 shrink-0"
                          :class="stepClass(si)">
                          <span v-if="stepDone(si)">&#10003;</span>
                          <span v-else-if="stepError(si)">&#33;</span>
                          <span v-else>{{ si + 1 }}</span>
                        </div>
                        <div v-if="si < steps.length - 1" class="flex-1 h-1 mx-1 rounded transition-all duration-200"
                          :class="stepBarClass(si)"></div>
                      </div>
                      <span class="text-[14px] font-mono font-bold mt-0.5 transition-all duration-200 truncate max-w-full px-1"
                        :class="localStep === si + 1 ? 'text-slate-950' : 'text-slate-500'">
                        {{ step }}
                      </span>
                    </div>
                  </div>
                </div>
                <div class="flex items-center justify-between mt-1">
                  <span v-if="lastDraftSave" class="text-[14px] font-mono text-slate-700 italic">
                    &#9998; Borrador guardado {{ lastDraftSave }}
                  </span>
                </div>
              </div>

              <!-- ═══ Scrollable step content ═══ -->
              <div class="flex-1 min-h-0 overflow-y-auto pr-1 overscroll-contain">
              <div v-if="localStep === 1" class="space-y-2">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-2">
                  <!-- Left column -->
                  <div class="space-y-3">
                    <div>
                      <label class="ds-label">Gateway / CFS Name</label>
                      <input v-model="receiptForms[m.id].gatewayCfs" type="text" placeholder="SDQ"
                        class="ds-input uppercase" />
                    </div>
                    <div>
                      <label class="ds-label">{{ t('warehouse.form.shipperName') }}</label>
                      <div class="flex gap-2 items-center">
                        <input v-model="receiptForms[m.id].shipperName" type="text" placeholder="Shipper"
                          class="ds-input flex-1"
                          @blur="syncMawbName(m, 'shipperName')" />
                        <span class="text-[12px] text-slate-950 font-mono shrink-0">MAWB: {{ m.shipperName || '—' }}</span>
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">Consignee Name</label>
                      <div class="flex gap-2 items-center">
                        <input v-model="receiptForms[m.id].consigneeName" type="text" placeholder="Consignee"
                          class="ds-input flex-1"
                          @blur="syncMawbName(m, 'consigneeName')" />
                        <span class="text-[12px] text-slate-950 font-mono shrink-0">MAWB: {{ m.consigneeName || '—' }}</span>
                      </div>
                    </div>
                    <div>
                      <label class="ds-label">MAWB Number</label>
                      <input :value="m.awbNumber || ''" readonly
                        class="ds-input bg-slate-100 text-slate-950" />
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                      <div>
                        <label class="ds-label">Origin</label>
                        <input v-model="receiptForms[m.id].origin" type="text" maxlength="3" placeholder="SDQ"
                          class="ds-input uppercase" />
                      </div>
                      <div>
                        <label class="ds-label">Destination</label>
                        <input v-model="receiptForms[m.id].destination" type="text" maxlength="3" placeholder="MIA"
                          class="ds-input uppercase" />
                      </div>
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                      <div>
                        <label class="ds-label">AWB Reported Pieces</label>
                        <input v-model.number="receiptForms[m.id].awbReportedPieces" type="number" min="0"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label">{{ t('warehouse.form.mawbWeight') }}</label>
                        <input v-model.number="receiptForms[m.id].mawbWeightGreatest" type="number" step="0.001"
                          class="ds-input" />
                      </div>
                    </div>
                  </div>

                  <!-- Right column: HAWB info + checkboxes -->
                  <div class="space-y-2">
                    <div class="border-2 border-slate-700 rounded-lg bg-white overflow-hidden shadow-sm">
                      <div class="flex items-center justify-between bg-slate-700 px-3 py-1.5 border-b border-slate-800">
                        <span class="text-[12px] font-mono font-bold text-white uppercase tracking-wider">
                          HAWBs
                        </span>
                        <div class="flex items-center gap-2 text-[13px] font-mono">
                          <span class="text-slate-200">HAWBs: <strong class="text-white">{{ (receiptForms[m.id].hawbEntries || []).length }}</strong></span>
                          <button @click="addHawbEntry(m)"
                            class="ml-1 w-5 h-5 flex items-center justify-center rounded bg-white text-slate-700 hover:bg-slate-200 transition text-[14px] font-bold leading-none"
                            title="Agregar HAWB">+</button>
                          <span v-if="receiptHawbs[m.id] && receiptHawbs[m.id].length > 0"
                            class="text-slate-300 ml-1">({{ receiptHawbs[m.id].length }} en DB)</span>
                        </div>
                      </div>
                      <div class="overflow-x-auto p-2">
                        <table class="w-full text-[13px] font-mono border-collapse" style="min-width: 800px">
                          <thead>
                            <tr class="text-slate-800 font-bold uppercase tracking-wider">
                              <th class="px-2 py-1 text-left border-b-2 border-slate-200"># HAWB</th>
                              <th class="px-2 py-1 text-left border-b-2 border-slate-200">Consignee</th>
                              <th class="px-2 py-1 text-center border-b-2 border-slate-200">Pcs</th>
                              <th class="px-2 py-1 text-right border-b-2 border-slate-200">Kg</th>
                              <th class="px-2 py-1 text-center border-b-2 border-slate-200">Dest</th>
                              <th v-if="(receiptForms[m.id].hawbEntries || []).length > 1"
                                class="px-2 py-1 text-center border-b-2 border-slate-200"></th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="(entry, ei) in receiptForms[m.id].hawbEntries" :key="ei"
                              class="hover:bg-slate-50">
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model="entry.hawbNumber" placeholder="HAWB #"
                                  class="w-20 border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px] font-bold text-slate-950" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model="entry.consigneeName" placeholder="Consignee"
                                  class="w-full min-w-[100px] border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px]" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model.number="entry.pieces" type="number" min="0" placeholder="0"
                                  class="w-14 text-center border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px]" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model.number="entry.weightKg" type="number" step="0.1" min="0" placeholder="0"
                                  class="w-20 text-right border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px]" />
                              </td>
                              <td class="px-2 py-1 border-b border-slate-100">
                                <input v-model="entry.destination" maxlength="3" placeholder="MIA"
                                  class="w-14 text-center border border-slate-200 rounded px-2 py-1 outline-none focus:border-slate-700 bg-white text-[13px] uppercase" />
                              </td>
                              <td v-if="(receiptForms[m.id].hawbEntries || []).length > 1"
                                class="px-2 py-1 text-center border-b border-slate-100">
                                <button @click="removeHawbEntry(m.id, ei)"
                                  class="text-slate-400 hover:text-slate-600 transition text-[12px] font-bold">✕</button>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <!-- Checkboxes group -->
                    <div class="border-2 border-slate-700 rounded-lg bg-white p-3 shadow-sm">
                      <div class="text-[12px] font-mono font-bold text-slate-800 uppercase tracking-wider mb-2">Flags / Marcas</div>
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].cashOnly" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Cash Only</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].bookedInAcoms" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Booked in ACOMS</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].docsProvided" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Documents Provided</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].customsCompleted" class="accent-slate-700 rounded w-3 h-3" />
                          <span>Export Customs Completed</span>
                        </label>
                        <label class="text-[12px] font-mono font-bold text-slate-950 flex items-center gap-1.5 cursor-pointer select-none">
                          <input type="checkbox" v-model="receiptForms[m.id].preBuilt" class="accent-slate-700 rounded w-3 h-3" />
                          <span>{{ t('warehouse.checkboxes.preBuilt') }}</span>
                        </label>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- ═══ STEP 2: PIECES ═══ -->
              <div v-if="localStep === 2" class="space-y-2">
                <div class="text-[12px] font-mono font-bold text-slate-950 uppercase tracking-wider flex items-center gap-2">
                  <span>Loose Tender — Dimensiones y Pesos</span>
                  <span class="text-[12px] font-mono font-normal text-slate-950 normal-case tracking-normal">
                    ((L x W x H) x #pcs) / 366 = Kg dimensional
                  </span>
                </div>

                <template v-if="(receiptHawbs[m.id] || []).length <= 1 && (receiptForms[m.id]?.hawbEntries || []).length <= 1">
                  <div class="overflow-x-auto border border-slate-400 rounded">
                    <table class="w-full text-[13px] font-mono border-collapse">
                      <thead>
                        <tr class="bg-slate-700 text-white text-[13px] uppercase tracking-wider">
                          <th class="px-2 py-1 border-r border-slate-600 w-5 text-center">#</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-10 text-center">Pieces</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-center">Length (in)</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-center">Width (in)</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-center">Height (in)</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-14 text-right">Dim Wt</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">Scale LBS</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">Dim LBS</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">S.KGS*</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">Dim KGS</th>
                          <th class="px-2 py-1 border-r border-slate-600 w-16 text-right">CHG KGS</th>
                          <th class="px-2 py-1 w-16 text-right">DOM LBS</th>
                          <th class="px-2 py-1 w-4"></th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(p, pi) in receiptForms[m.id].pieces" :key="pi"
                          class="border-b border-slate-300 hover:bg-slate-50 transition-colors">
                          <td class="px-2 py-1 text-center text-slate-950 border-r border-slate-300">{{ pi + 1 }}</td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.pieces" type="number" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.lengthIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.widthIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.heightIn" type="number" step="0.01" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold text-slate-950">{{ p.dimWeight ? p.dimWeight.toFixed(1) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300">
                            <input v-model.number="p.scaleWeightLbs" type="number" step="0.001" min="0"
                              class="w-full text-center border border-slate-400 rounded px-1.5 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                              @input="calcPiece(m.id, pi)" />
                          </td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold text-slate-950">{{ p.dimWeightLbs ? p.dimWeightLbs.toFixed(1) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold text-slate-950">{{ (p.scaleWeightKg || 0).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right font-semibold">{{ p.dimWeightKg ? p.dimWeightKg.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right text-slate-900">{{ p.chargeableKg ? p.chargeableKg.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 border-r border-slate-300 text-right text-slate-900">{{ p.chargeableLbs ? p.chargeableLbs.toFixed(2) : '—' }}</td>
                          <td class="px-2 py-1 text-center">
                            <button @click="removePiece(m.id, pi)" class="text-slate-400 hover:text-slate-600 transition text-[12px]">✕</button>
                          </td>
                        </tr>
                      </tbody>
                      <tfoot>
                        <tr class="bg-slate-100 text-slate-950 text-[13px]">
                          <td class="px-2 py-1 border-t border-slate-300 text-slate-950 text-center"></td>
                          <td class="px-2 py-1 border-t border-slate-300 text-center">{{ totalPieces(m.id, null) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-slate-950 text-[13px]" colspan="3">TOTAL</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalDimWeight(m.id, null).toFixed(1) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalScaleLbs(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalDimLbs(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalScaleKg(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right">{{ totalDimKg(m.id, null).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right text-slate-950">{{ totalChargeableKg(m.id).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300 text-right text-slate-950">{{ totalChargeableLbs(m.id).toFixed(2) }}</td>
                          <td class="px-2 py-1 border-t border-slate-300"></td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                    <div class="flex justify-between items-center">
                      <button @click="addPiece(m.id)" class="text-[12px] text-slate-950 font-mono uppercase tracking-wider hover:text-slate-950 transition">+ Agregar pieza</button>
                      <div class="flex gap-3 text-[12px] font-mono text-slate-950">
                      <span>Piece Count: <strong class="text-slate-900">{{ totalPieces(m.id, null) }}</strong></span>
                      <span>Actual: <strong class="text-slate-900">{{ totalScaleKg(m.id, null).toFixed(0) }} KGS / {{ totalScaleLbs(m.id, null).toFixed(0) }} LBS</strong></span>
                      <span>Chargeable: <strong class="text-slate-900">{{ totalChargeableKg(m.id).toFixed(0) }} KGS / {{ totalChargeableLbs(m.id).toFixed(0) }} LBS</strong></span>
                    </div>
                  </div>
                  <div class="text-[11px] text-slate-400 font-mono mt-1 text-right">* S.KGS = LBS ÷ 2.20462 (auto)</div>
                </template>

                <template v-else>
                  <div v-for="(h, hi) in hawbsForDisplay(m.id)" :key="h._hawbId || h.id" class="border border-slate-400 rounded overflow-hidden bg-white">
                    <div class="flex items-center justify-between bg-slate-100 px-3 py-1.5 border-b border-slate-400">
                      <span class="text-[12px] font-mono font-bold text-slate-950">
                        HAWB {{ hi + 1 }}: {{ h.hawbNumber || h.hawbNumber || '—' }} &mdash; {{ h.consigneeName || h.consigneeName || '—' }}
                        <span class="text-slate-950 font-normal ml-2">({{ piecesByHawb(m.id, h._hawbId || h.id).length }} pieza(s))</span>
                      </span>
                      <span class="text-[12px] font-mono text-slate-950 font-bold">{{ piecesByHawb(m.id, h._hawbId || h.id).reduce((s, p) => s + (p.pieces || 1), 0) }} pcs</span>
                    </div>
                    <div class="overflow-x-auto">
                      <table class="w-full text-[13px] font-mono border-collapse" style="min-width: 850px">
                        <thead>
                          <tr class="bg-slate-600 text-white text-[13px] uppercase tracking-wider">
                            <th class="px-1 py-0.5 border-r border-slate-500 w-5 text-center">#</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-10 text-center">Pcs</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-center">L</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-center">W</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-center">H</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-12 text-right">DimWt</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-16 text-right">S.LBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-right">DLBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-16 text-right">S.KGS*</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-right">DKGS</th>
                            <th class="px-1 py-0.5 border-r border-slate-500 w-14 text-right">CKGS</th>
                            <th class="px-1 py-0.5 w-16 text-right">CLBS</th>
                            <th class="px-1 py-0.5 w-4"></th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(entry, pi) in piecesByHawbIndexed(m.id, (h._hawbId || h.id))" :key="pi"
                            class="border-b border-slate-300 hover:bg-slate-50">
                            <td class="px-1 py-0.5 text-center text-slate-950 border-r border-slate-300">{{ pi + 1 }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.pieces" type="number" min="0"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.lengthIn" type="number" step="0.01"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.widthIn" type="number" step="0.01"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.heightIn" type="number" step="0.01"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right text-slate-950">{{ entry.piece.dimWeight ? entry.piece.dimWeight.toFixed(1) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300">
                              <input v-model.number="entry.piece.scaleWeightLbs" type="number" step="0.001"
                                class="w-full text-center border border-slate-400 rounded px-1 py-0.5 outline-none focus:border-slate-500 bg-white text-[13px]"
                                @input="calcPiece(m.id, entry.idx)" />
                            </td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right text-slate-950">{{ entry.piece.dimWeightLbs ? entry.piece.dimWeightLbs.toFixed(1) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right text-slate-950">{{ (entry.piece.scaleWeightKg || 0).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right">{{ entry.piece.dimWeightKg ? entry.piece.dimWeightKg.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right">{{ entry.piece.chargeableKg ? entry.piece.chargeableKg.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 border-r border-slate-300 text-right">{{ entry.piece.chargeableLbs ? entry.piece.chargeableLbs.toFixed(2) : '—' }}</td>
                            <td class="px-1 py-0.5 text-center">
                              <button @click="removePiece(m.id, entry.idx)" class="text-slate-400 hover:text-slate-600 text-[12px]">✕</button>
                            </td>
                          </tr>
                        </tbody>
                        <tfoot>
                          <tr class="bg-slate-100 text-slate-950 text-[13px]">
                            <td class="px-1 py-0.5 border-t border-slate-400"></td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-center">{{ hawbTotalPieces(m.id, (h._hawbId || h.id)) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400" colspan="3">TOTAL</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbDimWeight(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbScaleLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbDimLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbScaleKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbDimKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbChargeableKg(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400 text-right">{{ hawbChargeableLbs(m.id, (h._hawbId || h.id)).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t border-slate-400"></td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                    <div class="px-2 py-1 border-t border-slate-300">
                      <button @click="addPiece(m.id, (h._hawbId || h.id))" class="text-[12px] text-slate-950 font-mono uppercase tracking-wider hover:text-slate-950 transition">+ Agregar pieza a este HAWB</button>
                      <span class="text-[11px] text-slate-400 font-mono ml-4">* S.KGS = LBS ÷ 2.20462 (auto)</span>
                    </div>
                  </div>
                  <!-- Resumen General: agrupado por dimensión única LxWxH -->
                  <div class="border-2 border-slate-600 rounded overflow-hidden bg-white mt-2">
                    <div class="flex items-center justify-between bg-slate-600 px-3 py-1.5 border-b border-slate-700">
                      <span class="text-[12px] font-mono font-bold text-white uppercase tracking-wider">Resumen General — Todas las HAWBs</span>
                      <span class="text-[12px] font-mono text-slate-200">{{ totalPieces(m.id, null) }} piezas · {{ groupedSummary(m.id).length }} dim</span>
                    </div>
                    <div class="overflow-x-auto">
                      <table class="w-full text-[13px] font-mono border-collapse" style="min-width: 900px">
                        <thead>
                          <tr class="bg-slate-200 text-slate-800 text-[13px] uppercase tracking-wider">
                            <th class="px-1 py-0.5 border-r border-slate-300 w-5 text-center">#</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-center">L</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-center">W</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-center">H</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-10 text-center">Pcs</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-12 text-right">DimWt</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-16 text-right">S.LBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-right">DLBS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-16 text-right">S.KGS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-right">DKGS</th>
                            <th class="px-1 py-0.5 border-r border-slate-300 w-14 text-right">CKGS</th>
                            <th class="px-1 py-0.5 w-14 text-right">CLBS</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(g, gi) in groupedSummary(m.id)" :key="gi"
                            class="border-b border-slate-200 hover:bg-slate-50">
                            <td class="px-1 py-0.5 text-center text-slate-500 border-r border-slate-200">{{ gi + 1 }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200">{{ g.lengthIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200">{{ g.widthIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200">{{ g.heightIn || '—' }}</td>
                            <td class="px-1 py-0.5 text-center border-r border-slate-200 font-bold">{{ g.totalPieces }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalDimWeight.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalScaleLbs.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalDimLbs.toFixed(1) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalScaleKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalDimKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right border-r border-slate-200">{{ g.totalChargeableKg.toFixed(2) }}</td>
                            <td class="px-1 py-0.5 text-right">{{ g.totalChargeableLbs.toFixed(2) }}</td>
                          </tr>
                        </tbody>
                        <tfoot>
                          <tr class="bg-slate-100 text-slate-950 text-[13px] font-bold">
                            <td class="px-1 py-0.5 border-t-2 border-slate-400" colspan="4">TOTAL</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-center">{{ totalPieces(m.id, null) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalDimWeight(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalScaleLbs(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalDimLbs(m.id, null).toFixed(1) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalScaleKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalDimKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalChargeableKg(m.id, null).toFixed(2) }}</td>
                            <td class="px-1 py-0.5 border-t-2 border-slate-400 text-right">{{ totalChargeableLbs(m.id, null).toFixed(2) }}</td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  </div>
                </template>
              </div>

              <!-- ═══ STEP 3: REMARKS ═══ -->
              <div v-if="localStep === 3" class="space-y-2">
                <label class="ds-label">Remarks / Observaciones</label>
                <textarea v-model="receiptForms[m.id].remarks" rows="3" placeholder="Notas, observaciones, instrucciones especiales..."
                  class="ds-input resize-none"></textarea>
              </div>

              <!-- ═══ STEP 4: EVIDENCE ═══ -->
              <div v-if="localStep === 4" class="space-y-2">
                <p class="text-[12px] font-mono text-slate-950">Adjuntar fotos, documentos u otras evidencias</p>

                <!-- Evidencias del MAWB (desde base de datos) -->
                <div v-if="(receiptForms[m.id].mawbEvidence || []).length > 0">
                  <span class="text-[12px] font-mono font-bold text-slate-950 uppercase tracking-wider mb-1 block">{{ t('warehouse.evidence.mawbEvidence') }}</span>
                  <div class="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-2">
                    <div v-for="(ev, ei) in receiptForms[m.id].mawbEvidence" :key="'mawb-' + ei"
                      class="relative border border-slate-200 rounded bg-slate-50/30 overflow-hidden group cursor-pointer" @click="previewEvidence(ev)">
                      <img v-if="ev.type === 'image' && ev.url" :src="ev.url" class="w-full h-20 object-cover" />
                      <div v-else-if="ev.type === 'text'" class="w-full h-20 flex items-center justify-center bg-slate-50 text-slate-950 text-[12px] font-mono px-2 text-center leading-tight">{{ ev.name }}</div>
                      <div v-else-if="isPdfUrl(ev.url)" class="w-full h-20 flex flex-col items-center justify-center bg-slate-100 text-slate-700 text-[12px] font-mono">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                        <span class="text-[10px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                      </div>
                      <div v-else class="w-full h-20 flex items-center justify-center bg-slate-100 text-slate-950 text-[12px] font-mono">{{ ev.name }}</div>
                      <span class="block text-[12px] font-mono text-slate-950 px-1.5 py-0.5 leading-tight">{{ ev.name }}</span>
                    </div>
                  </div>
                </div>

                <!-- Nuevas evidencias (subidas en este formulario) -->
                <span class="text-[12px] font-mono font-bold text-slate-950 uppercase tracking-wider mb-1 block">Nuevas evidencias (este recibo)</span>
                <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  <div v-for="(ev, ei) in receiptForms[m.id].evidence" :key="'rec-' + ei"
                    class="relative border border-slate-400 rounded bg-white overflow-hidden group cursor-pointer" @click="previewEvidence(ev)">
                    <img v-if="ev.type === 'image'" :src="ev.url" class="w-full h-20 object-cover" />
                    <div v-else-if="isPdfUrl(ev.url)" class="w-full h-20 flex flex-col items-center justify-center bg-slate-100 text-slate-700 text-[12px] font-mono">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                      <span class="text-[11px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                    </div>
                    <div v-else class="w-full h-20 flex items-center justify-center bg-slate-100 text-slate-950 text-[12px] font-mono">{{ ev.name }}</div>
                    <button @click.stop="removeEvidence(m.id, ei)" class="absolute top-0.5 right-0.5 w-3.5 h-3.5 bg-slate-500 text-white rounded-full text-[12px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition">✕</button>
                    <span class="block text-[12px] font-mono text-slate-950 px-1.5 py-0.5 truncate">{{ ev.name }}</span>
                  </div>
                  <div class="border-2 border-dashed border-slate-400 rounded flex flex-col items-center justify-center cursor-pointer hover:border-slate-950 transition group min-h-[80px]"
                    @click="addEvidence(m.id)">
                    <span class="text-[14px] text-slate-300 font-mono group-hover:text-slate-950 transition leading-none">+</span>
                    <span class="text-[12px] font-mono text-slate-950 mt-0.5 uppercase tracking-wider">Subir</span>
                  </div>
                  <div class="border-2 border-dashed border-slate-400 rounded flex flex-col items-center justify-center cursor-pointer hover:border-slate-950 transition group min-h-[80px]"
                    @click="openCamera(m.id)">
                    <IconCamera :size="16" class="text-slate-300 group-hover:text-slate-950 transition" />
                    <span class="text-[12px] font-mono text-slate-950 mt-0.5 uppercase tracking-wider">Cámara</span>
                  </div>
                </div>
                <CameraCapture :show="showCamera" @close="showCamera = false" @captured="onCameraCapture" />
              </div>

              <!-- ═══ STEP 5: SIGNATURES ═══ -->
              <div v-if="localStep === 5" class="space-y-2">
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label class="ds-label">Dock Signature</label>
                    <SignaturePad v-model="receiptForms[m.id].dockSignature" :width="280" :height="60" />
                  </div>
                  <div class="flex flex-col justify-end">
                    <label class="ds-label">Print Name</label>
                        <input v-model="receiptForms[m.id].printName" type="text" placeholder="Nombre"
                          class="ds-input" />
                  </div>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 border-t border-slate-400 pt-2">
                  <div>
                    <label class="ds-label">Delivered By</label>
                    <div class="grid grid-cols-2 gap-1.5 mb-1">
                      <div>
                        <label class="ds-label text-[12px]">Name</label>
                        <input v-model="receiptForms[m.id].deliveredByName" type="text"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label text-[12px]">ID / Cédula</label>
                        <input v-model="receiptForms[m.id].deliveredByIdNum" type="text"
                          class="ds-input" />
                      </div>
                    </div>
                    <SignaturePad v-model="receiptForms[m.id].deliveredBySig" :width="280" :height="50" />
                  </div>
                  <div>
                    <label class="ds-label">Broker Representative</label>
                    <div class="grid grid-cols-2 gap-1.5 mb-1">
                      <div>
                        <label class="ds-label text-[12px]">Name</label>
                        <input v-model="receiptForms[m.id].brokerName" type="text"
                          class="ds-input" />
                      </div>
                      <div>
                        <label class="ds-label text-[12px]">ID / Cédula</label>
                        <input v-model="receiptForms[m.id].brokerIdNum" type="text"
                          class="ds-input" />
                      </div>
                    </div>
                    <SignaturePad v-model="receiptForms[m.id].brokerSig" :width="280" :height="50" />
                  </div>
                </div>
              </div>

              </div> <!-- end scrollable step content -->

              <div class="flex justify-between items-center mt-2 pt-2 border-t border-slate-400 shrink-0">
                <div class="flex items-center gap-2">
                  <button @click="prevStep" :disabled="localStep === 1"
                    class="ds-btn-secondary text-[11px] px-2 py-1 disabled:opacity-30">
                    &#9664; Anterior
                  </button>
                  <button @click="cancelForm"
                    class="ds-btn-secondary text-[11px] px-2 py-1 text-slate-600">
                    &#10005; Cancelar
                  </button>
                  <span v-if="successMsg" class="text-slate-700 text-[14px] font-mono font-bold ">{{ successMsg }}</span>
                </div>
                <div v-if="localStep < 5">
                  <button @click="nextStep"
                    class="ds-btn-primary text-[11px] px-2 py-1">
                    Siguiente &#9654;
                  </button>
                </div>
                <div v-else class="flex items-center gap-2">
                  <button @click="printPreview(m)"
                    class="ds-btn-secondary text-[11px] px-2 py-1 shrink-0"
                    title="Vista previa para impresión">
                    &#128424; Vista Previa
                  </button>
                  <button @click="openConfirmModal(m)" :disabled="submitting"
                    class="ds-btn-primary text-[11px] px-2.5 py-1 shrink-0">
                    <span>{{ submitting ? 'Guardando...' : '&#10003; Confirmar Recibo' }}</span>
                  </button>
                </div>
              </div>
            </div> <!-- end flex-col container -->
          </div>
        </div>
      </div>
    </section>

    <!-- MAWB Evidence Manager Modal -->
    <Teleport to="body">
      <div v-if="mawbEvidenceMgr.show" class="ds-modal-backdrop" @click.self="closeMawbEvidenceMgr">
        <div class="ds-modal-panel max-w-xl p-0" style="max-height: 80vh;">
          <div class="ds-modal-header px-4 py-2.5">
            <span class="ds-modal-title">
              {{ t('warehouse.evidence.title') }} — {{ mawbEvidenceMgr.mawb?.awbNumber || 'MAWB' }}
            </span>
            <button @click="closeMawbEvidenceMgr" class="text-slate-950 hover:text-slate-950 transition text-base">✕</button>
          </div>
          <div class="p-4 overflow-y-auto" style="max-height: calc(80vh - 120px);">
            <div v-if="mawbEvidenceMgr.docs.length === 0" class="text-[14px] font-mono text-slate-950 text-center py-6 uppercase tracking-widest">
              Sin evidencias documentales
            </div>
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 mb-4">
              <div v-for="(doc, di) in mawbEvidenceMgr.docs" :key="di"
                class="relative border border-slate-400 rounded overflow-hidden bg-white group cursor-pointer" @click="previewEvidence(doc)">
                <img v-if="doc.type === 'image' && doc.url" :src="doc.url" class="w-full h-20 object-cover" />
                <div v-else-if="isPdfUrl(doc.url)" class="w-full h-20 flex flex-col items-center justify-center bg-slate-100 text-slate-700 font-mono">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-7 h-7 mb-0.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /><polyline points="10 9 9 9 8 9" /></svg>
                  <span class="text-[12px] leading-tight px-1 text-center truncate max-w-full">PDF</span>
                </div>
                <div v-else class="w-full h-20 flex items-center justify-center bg-slate-100 text-slate-950 text-[13px] font-mono">{{ doc.name }}</div>
                <button @click.stop="removeMawbEvidence(di)" class="absolute top-0.5 right-0.5 w-3.5 h-3.5 bg-slate-500 text-white rounded-full text-[12px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition">✕</button>
                <span class="block text-[13px] font-mono text-slate-950 px-2 py-1 truncate">{{ doc.name }}</span>
              </div>
            </div>
            <div class="flex items-center gap-2 border-t border-slate-300 pt-3">
              <button @click="mawbEvidenceInput.click()"
                class="ds-btn-secondary text-[11px] px-2.5 py-1">
                + Subir archivo
              </button>
              <button @click="openMawbCamera()"
                class="ds-btn-secondary text-[11px] px-2.5 py-1 flex items-center gap-1">
                <IconCamera :size="12" /> Cámara
              </button>
              <button v-if="mawbEvidenceMgr.docs.length > 0" @click="downloadMawbEvidencePdf()"
                class="ml-auto ds-btn-primary text-[11px] px-2.5 py-1">
                &#128196; PDF
              </button>
            </div>
            <input type="file" ref="mawbEvidenceInput" @change="handleMawbEvidenceUpload" accept="image/*,.pdf" class="hidden" />
            <CameraCapture :show="mawbCameraOpen" @close="mawbCameraOpen = false" @captured="onMawbCameraCapture" />
          </div>
          <div class="flex justify-end px-4 py-2.5 border-t border-slate-200 bg-slate-50">
            <button @click="saveMawbEvidence()"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              Guardar cambios
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Confirm Submit Modal -->
    <Teleport to="body">
      <div v-if="showConfirmModal" class="ds-modal-backdrop" @click.self="showConfirmModal = false" style="z-index: 70;">
        <div class="ds-modal-panel max-w-lg p-0" style="max-height: 80vh;">
          <div class="ds-modal-header px-4 py-2.5">
            <span class="ds-modal-title">Confirmar Recibo</span>
            <button @click="showConfirmModal = false" class="text-slate-950 hover:text-slate-950 transition text-base">✕</button>
          </div>
          <div class="overflow-y-auto" style="max-height: calc(80vh - 110px);">
            <template v-if="pendingSubmitMawb">
              <div class="px-4 py-3 space-y-2 text-[13px] font-mono text-slate-950">
                <div class="grid grid-cols-2 gap-x-4 gap-y-1.5">
                  <span class="text-slate-500 uppercase tracking-wider">MAWB</span>
                  <span class="font-bold text-right">{{ pendingSubmitMawb.awbNumber }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">{{ t('warehouse.form.destination') }}</span>
                  <span class="font-bold text-right">{{ receiptForms[pendingSubmitMawb.id]?.destination || pendingSubmitMawb.destination || '—' }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">{{ t('common.pieces') }}</span>
                  <span class="font-bold text-right">{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).reduce((s, p) => s + (p.pieces || 1), 0) }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">Peso (Kg)</span>
                  <span class="font-bold text-right">{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightKg || 0), 0).toFixed(1) }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">{{ t('warehouse.form.shipperName') }}</span>
                  <span class="font-bold text-right truncate">{{ receiptForms[pendingSubmitMawb.id]?.shipperName || pendingSubmitMawb.shipperName || '—' }}</span>
                  <span class="text-slate-500 uppercase tracking-wider">Consignee</span>
                  <span class="font-bold text-right truncate">{{ receiptForms[pendingSubmitMawb.id]?.consigneeName || pendingSubmitMawb.consigneeName || '—' }}</span>
                </div>
                <div class="border-t border-slate-300 pt-2 mt-2">
                  <div class="flex flex-wrap gap-2">
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.cashOnly" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Cash Only</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.bookedInAcoms" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Booked in ACOMS</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.docsProvided" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Docs Provided</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.customsCompleted" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Customs Done</span>
                    <span v-if="receiptForms[pendingSubmitMawb.id]?.preBuilt" class="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded text-[12px] uppercase tracking-wider">Pre-built</span>
                  </div>
                </div>
                <div class="border-t border-slate-300 pt-2 mt-2 flex justify-between text-[12px] text-slate-500">
                  <span>{{ (receiptForms[pendingSubmitMawb.id]?.evidence || []).length }} evidencias</span>
                  <span>{{ (receiptForms[pendingSubmitMawb.id]?.pieces || []).filter(p => p.lengthIn).length }} piezas con dimensiones</span>
                </div>
              </div>
            </template>
          </div>
          <div class="flex items-center justify-end gap-2 px-4 py-2 border-t border-slate-200 bg-slate-50">
            <button @click="showConfirmModal = false"
              class="ds-btn-secondary text-[11px] px-3 py-1.5">
              Cancelar
            </button>
            <button @click="confirmSubmit"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              &#10003; Confirmar
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Booking Correction Modal -->
    <Teleport to="body">
      <div v-if="showBookingCorrectionModal" class="ds-modal-backdrop" @click.self="cancelBookingCorrection" style="z-index: 80; --tw-bg-opacity: 0.7;">
        <div class="ds-modal-panel max-w-md p-0">
          <div class="px-5 py-4 border-b border-amber-300 bg-amber-50">
            <div class="flex items-center gap-2">
              <span class="text-amber-600 text-lg">&#9888;</span>
              <span class="ds-modal-title text-amber-900">Booking Auto-Correccion</span>
            </div>
          </div>
          <div class="px-5 py-4 space-y-3">
            <p class="text-[14px] font-mono text-slate-600">
              Las piezas recibidas superan las reservadas en el Booking. El sistema corregira automaticamente:
            </p>
            <div v-for="(c, idx) in pendingBookingCorrections" :key="idx"
              class="bg-amber-50 border border-amber-200 rounded-md px-3 py-2.5">
              <div class="text-[13px] font-mono text-amber-900" v-html="formatCorrection(c)"></div>
            </div>
            <p class="text-[13px] font-mono text-slate-500 italic">
              Al aceptar, el Booking se actualizara y el recibo se guardara.
            </p>
          </div>
          <div class="flex items-center justify-end gap-2 px-5 py-3 border-t border-amber-200 bg-amber-50/50">
            <button @click="cancelBookingCorrection"
              class="ds-btn-secondary text-[12px] px-3 py-1.5 text-slate-700">
              &#10007; Ajustar
            </button>
            <button @click="confirmBookingCorrection"
              class="flex items-center gap-1.5 text-[12px] px-4 py-1.5 rounded font-mono uppercase tracking-wider font-bold text-white bg-amber-600 hover:bg-amber-500 transition">
              &#10003; Aceptar y Emitir
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Evidence Preview Modal -->
    <Teleport to="body">
      <div v-if="evidencePreview.show" class="ds-modal-backdrop" @click.self="closeEvidencePreview" style="z-index: 60; --tw-bg-opacity: 0.7;">
        <div class="ds-modal-panel max-w-4xl p-0" style="max-height: 90vh;">
          <div class="ds-modal-header px-4 py-2.5">
            <span class="ds-modal-title truncate max-w-[70%]">
              {{ evidencePreview.item?.name || 'Vista previa' }}
            </span>
            <button @click="closeEvidencePreview" class="text-slate-950 hover:text-slate-950 transition text-base">✕</button>
          </div>
          <div class="overflow-auto bg-slate-900 flex items-center justify-center" style="max-height: calc(90vh - 110px); min-height: 200px;">
            <img v-if="evidencePreview.item?.type === 'image' && evidencePreview.item?.url" :src="evidencePreview.item.url"
              class="max-w-full max-h-full object-contain" style="max-height: calc(90vh - 120px);" />
            <embed v-else-if="isPdfUrl(evidencePreview.item?.url)" :src="evidencePreview.item.url"
              type="application/pdf" class="w-full" style="height: calc(90vh - 110px);" />
            <div v-else class="text-white/60 font-mono text-[14px] p-8 text-center">
              {{ evidencePreview.item?.name || 'Sin vista previa disponible' }}
            </div>
          </div>
          <div class="flex items-center justify-end gap-2 px-4 py-2 border-t border-slate-200 bg-slate-50">
            <button @click="closeEvidencePreview"
              class="ds-btn-secondary text-[11px] px-3 py-1.5">
              Cerrar
            </button>
            <button @click="downloadEvidenceItem(evidencePreview.item)"
              class="ds-btn-primary text-[11px] px-3 py-1.5">
              &#128229; Descargar
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
  <EditReceiptModal ref="editModalRef" @saved="onEditReceiptSaved" />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'

const { t } = useI18n()
import SignaturePad from '../components/SignaturePad.vue'
import CameraCapture from '../components/CameraCapture.vue'
import EditReceiptModal from '../components/EditReceiptModal.vue'
import { IconCamera } from '@tabler/icons-vue'
import { hawbsApi } from '../api/hawbs'
import { mawbsApi } from '../api/mawbs'
import { receiptsApi } from '../api/receipts'
import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'

const store = useAppStore()
const toast = useToastStore()
const route = useRoute()

function airlineCodeById(airlineId) {
  const a = store.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

const expandedId = ref(localStorage.getItem('WAREHOUSE_EXPANDED_MAWB') || null)
const overdueMawbsDismissed = ref(false)
const localStep = ref(1)
const submitting = ref(false)
const successMsg = ref('')
const showConfirmModal = ref(false)
const editModalRef = ref(null)
const pendingSubmitMawb = ref(null)
const showBookingCorrectionModal = ref(false)
const pendingBookingCorrections = ref([])
const pendingEmitPayload = ref(null)
const pendingEmitMawb = ref(null)
const pendingEmitHawbs = ref(null)
const formVersion = ref(0)
function bumpFormVersion() { formVersion.value++ }
let formVersionDebounceTimer = null
// Version debounced: coalesce varias pulsaciones de teclado en un solo refresco del bloque
// memoizado (v-memo) del MAWB, para no forzar un re-render pesado por cada digito tecleado
// en dimensiones/cantidad, manteniendo la sensacion de actualizacion "en vivo".
function bumpFormVersionDebounced(delay = 150) {
  if (formVersionDebounceTimer) clearTimeout(formVersionDebounceTimer)
  formVersionDebounceTimer = setTimeout(() => {
    bumpFormVersion()
    formVersionDebounceTimer = null
  }, delay)
}
let successTimer = null
const showCamera = ref(false)
const cameraMawbId = ref(null)

const receiptForms = reactive({})
const receiptHawbs = reactive({})
const generatedReceiptId = ref(null)

// Draft persistence (auto-save)
const DRAFT_PREFIX = 'warehouse_draft_'
const lastDraftSave = ref('')

function saveDraft(mawbId) {
  const f = receiptForms[mawbId]
  if (!f) return
  try {
    const data = {
      gatewayCfs: f.gatewayCfs,
      shipperName: f.shipperName,
      consigneeName: f.consigneeName,
      origin: f.origin,
      destination: f.destination,
      awbReportedPieces: f.awbReportedPieces,
      mawbWeightGreatest: f.mawbWeightGreatest,
      dimFactorKg: f.dimFactorKg,
      dimFactorLbs: f.dimFactorLbs,
      cashOnly: f.cashOnly,
      bookedInAcoms: f.bookedInAcoms,
      docsProvided: f.docsProvided,
      customsCompleted: f.customsCompleted,
      preBuilt: f.preBuilt,
      hawbCount: f.hawbCount,
      hawbEntries: f.hawbEntries.map(e => ({
        hawbNumber: e.hawbNumber, consigneeName: e.consigneeName,
        pieces: e.pieces, weightKg: e.weightKg, destination: e.destination,
        _dbId: e._dbId || null, _hawbId: e._hawbId || null,
      })),
      pieces: f.pieces.map(p => ({
        pieces: p.pieces, hawbId: p.hawbId,
        lengthIn: p.lengthIn, widthIn: p.widthIn, heightIn: p.heightIn,
        scaleWeightLbs: p.scaleWeightLbs,
      })),
      remarks: f.remarks,
      dockSignature: f.dockSignature || '',
      printName: f.printName,
      deliveredByName: f.deliveredByName,
      deliveredByIdNum: f.deliveredByIdNum,
      deliveredBySig: f.deliveredBySig || '',
      brokerName: f.brokerName,
      brokerIdNum: f.brokerIdNum,
      brokerSig: f.brokerSig || '',
    }
    localStorage.setItem(DRAFT_PREFIX + mawbId, JSON.stringify(data))
    lastDraftSave.value = new Date().toLocaleTimeString()
  } catch {}
}

function loadDraft(mawbId) {
  try {
    const raw = localStorage.getItem(DRAFT_PREFIX + mawbId)
    if (!raw) return null
    return JSON.parse(raw)
  } catch { return null }
}

function clearDraft(mawbId) {
  try { localStorage.removeItem(DRAFT_PREFIX + mawbId) } catch {}
}

function applyDraftToForm(mawbId) {
  const draft = loadDraft(mawbId)
  const f = receiptForms[mawbId]
  if (!draft || !f) return
  Object.assign(f, {
    gatewayCfs: draft.gatewayCfs ?? f.gatewayCfs,
    shipperName: draft.shipperName ?? f.shipperName,
    consigneeName: draft.consigneeName ?? f.consigneeName,
    origin: draft.origin ?? f.origin,
    destination: draft.destination ?? f.destination,
    awbReportedPieces: draft.awbReportedPieces ?? f.awbReportedPieces,
    mawbWeightGreatest: draft.mawbWeightGreatest ?? f.mawbWeightGreatest,
    dimFactorKg: draft.dimFactorKg ?? f.dimFactorKg,
    dimFactorLbs: draft.dimFactorLbs ?? f.dimFactorLbs,
    cashOnly: draft.cashOnly ?? f.cashOnly,
    bookedInAcoms: draft.bookedInAcoms ?? f.bookedInAcoms,
    docsProvided: draft.docsProvided ?? f.docsProvided,
    customsCompleted: draft.customsCompleted ?? f.customsCompleted,
    preBuilt: draft.preBuilt ?? f.preBuilt,
    hawbCount: draft.hawbCount ?? f.hawbCount,
    hawbEntries: draft.hawbEntries?.length ? draft.hawbEntries.map(e => ({
      hawbNumber: e.hawbNumber || '', consigneeName: e.consigneeName || '',
      pieces: e.pieces || 0, weightKg: e.weightKg || 0, destination: e.destination || 'MIA',
      _dbId: e._dbId || null, _hawbId: e._hawbId || null,
    })) : f.hawbEntries,
    pieces: draft.pieces?.length ? draft.pieces.map(p => ({
      pieces: p.pieces ?? 1, hawbId: p.hawbId ?? null,
      lengthIn: p.lengthIn ?? null, widthIn: p.widthIn ?? null, heightIn: p.heightIn ?? null,
      scaleWeightLbs: p.scaleWeightLbs ?? null,
      dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0,
    })) : f.pieces,
    remarks: draft.remarks ?? f.remarks,
    dockSignature: draft.dockSignature ?? f.dockSignature,
    printName: draft.printName ?? f.printName,
    deliveredByName: draft.deliveredByName ?? f.deliveredByName,
    deliveredByIdNum: draft.deliveredByIdNum ?? f.deliveredByIdNum,
    deliveredBySig: draft.deliveredBySig ?? f.deliveredBySig,
    brokerName: draft.brokerName ?? f.brokerName,
    brokerIdNum: draft.brokerIdNum ?? f.brokerIdNum,
    brokerSig: draft.brokerSig ?? f.brokerSig,
  })
  lastDraftSave.value = 'borrador'
  f.pieces.forEach((_, pi) => calcPiece(mawbId, pi))
}

// Auto-save watch (debounced on active form)
const activeFormJson = computed(() => {
  const mId = expandedId.value
  if (!mId || !receiptForms[mId]) return null
  const f = receiptForms[mId]
  return JSON.stringify({
    gc: f.gatewayCfs, sn: f.shipperName, cn: f.consigneeName,
    or: f.origin, de: f.destination, arp: f.awbReportedPieces,
    mwg: f.mawbWeightGreatest, dfk: f.dimFactorKg, dfl: f.dimFactorLbs, co: f.cashOnly, bi: f.bookedInAcoms,
    dp: f.docsProvided, cc: f.customsCompleted, pb: f.preBuilt,
    hc: f.hawbCount, he: f.hawbEntries.map(e => ({
      hn: e.hawbNumber, cn: e.consigneeName, p: e.pieces,
      wk: e.weightKg, d: e.destination
    })),
    pcs: f.pieces.map(p => ({
      p: p.pieces, l: p.lengthIn, w: p.widthIn, h: p.heightIn,
      sl: p.scaleWeightLbs
    })),
    r: f.remarks, pn: f.printName, dbn: f.deliveredByName,
    dbi: f.deliveredByIdNum, ds: f.dockSignature?.length || 0,
    dbs: f.deliveredBySig?.length || 0,
    bn: f.brokerName, bii: f.brokerIdNum, bs: f.brokerSig?.length || 0,
  })
})

let draftTimer = null
watch(activeFormJson, (json) => {
  if (!json) return
  if (draftTimer) clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    const mId = expandedId.value
    if (mId) saveDraft(mId)
  }, 2000)
})

const filterTextRaw = ref('')
const filterText = ref('')
const filterDate = ref('')

// Column header filters
const headerFilterOpen = ref(null)
const columnFilters = reactive({ mawb: null, shipper: null, dest: null, status: null })

const uniqueValues = computed(() => {
  const mawbs = store.mawbs
  return {
    mawb: [...new Set(mawbs.map(m => m.awbNumber).filter(Boolean))].sort(),
    shipper: [...new Set(mawbs.map(m => m.shipperName).filter(Boolean))].sort(),
    dest: [...new Set(mawbs.map(m => m.destination).filter(Boolean))].sort(),
  }
})

const statusOptions = [
  { key: 'BOOKED', label: 'Pendientes', dotClass: 'bg-slate-400' },
  { key: 'RECEIVED', label: 'Recibidos', dotClass: 'bg-amber-400' },
  { key: 'MANIFESTED', label: 'Manifestados', dotClass: 'bg-emerald-500' },
  { key: 'DEPARTED', label: 'Despachados', dotClass: 'bg-blue-500' },
]

const statusDotClass = {
  BOOKED: 'bg-slate-400',
  RECEIVED: 'bg-amber-400',
  MANIFESTED: 'bg-emerald-500',
  DEPARTED: 'bg-blue-500',
}

function toggleHeaderFilter(col) {
  headerFilterOpen.value = headerFilterOpen.value === col ? null : col
}

function setColumnFilter(col, val) {
  columnFilters[col] = val
  headerFilterOpen.value = null
}

// Bulk selection
const selectedMawbIds = reactive(new Set())
const bulkStatusTarget = ref('')

function toggleSelectAll(e) {
  if (e.target.checked) {
    filteredMawbs.value.forEach(m => selectedMawbIds.add(m.id))
  } else {
    selectedMawbIds.clear()
  }
}

function toggleSelect(id) {
  if (selectedMawbIds.has(id)) selectedMawbIds.delete(id)
  else selectedMawbIds.add(id)
}

async function applyBulkStatus() {
  const target = bulkStatusTarget.value
  if (!target || selectedMawbIds.size === 0) return
  const ids = [...selectedMawbIds]
  if (!confirm(`¿Cambiar estado de ${ids.length} MAWB(s) a "${statusSteps.find(s => s.key === target)?.label}"?`)) return
  let ok = 0, fail = 0
  for (const id of ids) {
    try {
      await mawbsApi.updateStatus(id, target)
      ok++
    } catch { fail++ }
  }
  selectedMawbIds.clear()
  bulkStatusTarget.value = ''
  if (store.selectedFlightId) await store.loadMawbs(store.selectedFlightId); else await store.loadAllMawbs()
  toast.success(`${ok} actualizado(s)` + (fail ? `, ${fail} error(es)` : ''))
}

let filterDebounce = null
watch(filterTextRaw, (val) => {
  if (filterDebounce) clearTimeout(filterDebounce)
  filterDebounce = setTimeout(() => { filterText.value = val }, 200)
})

const statusPriority = { BOOKED: 0, RECEIVED: 1, MANIFESTED: 2, DEPARTED: 3 }

const filteredMawbs = computed(() => {
  let list = store.mawbs
  if (columnFilters.status) {
    if (columnFilters.status === 'BOOKED') {
      list = list.filter(m => !m.status || m.status === 'BOOKED')
    } else {
      list = list.filter(m => m.status === columnFilters.status)
    }
  }
  if (columnFilters.mawb) {
    list = list.filter(m => m.awbNumber === columnFilters.mawb)
  }
  if (columnFilters.shipper) {
    list = list.filter(m => m.shipperName === columnFilters.shipper)
  }
  if (columnFilters.dest) {
    list = list.filter(m => m.destination === columnFilters.dest)
  }
  if (filterDate.value) {
    const target = filterDate.value
    const mawbsWithReceipt = (store.receipts || [])
      .filter(r => {
        const d = r.receiptDate || r.createdAt
        return d && d.startsWith(target)
      })
      .map(r => r.mawb?.id || r.mawbId)
      .filter(Boolean)
    list = list.filter(m => mawbsWithReceipt.includes(m.id))
  }
  const ft = filterText.value.trim()
  if (ft) list = list.filter(m => applyFilter(m, ft))

  return [...list].sort((a, b) => {
    const pa = statusPriority[a.status || 'BOOKED'] ?? 0
    const pb = statusPriority[b.status || 'BOOKED'] ?? 0
    if (pa !== pb) return pa - pb
    return (b.awbNumber || '').localeCompare(a.awbNumber || '')
  })
})

function applyFilter(m, ft) {
  const numMatch = ft.match(/^(>=?|<=?|=)?(\d+(?:\.\d+)?)$/)
  if (numMatch) {
    const op = numMatch[1] || '='
    const val = parseFloat(numMatch[2])
    // Use received pieces from receiptTotals (what's actually shown in the column)
    const num = receiptTotals.value[m.id]?.pieces || m.pieces || 0
    switch (op) {
      case '>=': return num >= val
      case '>':  return num > val
      case '<=': return num <= val
      case '<':  return num < val
      case '=':  return num === val
      default:   return false
    }
  }
  if (ft.startsWith('*') && ft.endsWith('*')) {
    const mid = ft.slice(1, -1).toLowerCase()
    return matchAnyField(m, v => v.toLowerCase().includes(mid))
  }
  if (ft.startsWith('*')) {
    const suffix = ft.slice(1).toLowerCase()
    return matchAnyField(m, v => v.toLowerCase().endsWith(suffix))
  }
  if (ft.endsWith('*')) {
    const prefix = ft.slice(0, -1).toLowerCase()
    return matchAnyField(m, v => v.toLowerCase().startsWith(prefix))
  }
  const lower = ft.toLowerCase()
  return matchAnyField(m, v => v.toLowerCase().includes(lower))
}

function matchAnyField(m, fn) {
  const fields = [
    m.awbNumber, m.shipperName, m.consigneeName,
    m.destination, m.origin,
  ].filter(Boolean)
  return fields.some(fn)
}

const steps = ['HEADER', 'PIECES', 'REMARKS', 'EVIDENCE', 'SIGNATURES']
const statusSteps = [
  { key: 'BOOKED',     color: 'bg-slate-500 border-slate-600',  label: 'Pendiente',  tone: 'slate' },
  { key: 'RECEIVED',   color: 'bg-amber-500 border-amber-600',  label: 'Recibido',   tone: 'amber' },
  { key: 'MANIFESTED', color: 'bg-emerald-500 border-emerald-600', label: 'Manifestado', tone: 'emerald' },
  { key: 'DEPARTED',   color: 'bg-blue-500 border-blue-600',    label: 'Despachado', tone: 'blue' },
]
const statusOrder = ['BOOKED', 'RECEIVED', 'MANIFESTED', 'DEPARTED']

function getStatusDot(m, s) {
  const cur = m.status || 'BOOKED'
  const idx = statusOrder.indexOf(cur)
  const stepIdx = statusOrder.indexOf(s.key)
  if (idx < 0) return 'bg-slate-200 border-slate-300'
  if (stepIdx < idx) return s.color + ' opacity-60'
  if (stepIdx === idx) return s.color + ' scale-125 ring-2 ring-offset-1 ring-slate-400'
  return 'bg-slate-200 border-slate-300'
}

const statusLabels = {
  BOOKED: 'Pendiente',
  RECEIVED: 'Recibido',
  MANIFESTED: 'Manifestado',
  DEPARTED: 'Despachado',
  ARRIVED: 'Llegado',
  CANCELLED: 'Cancelado',
}

function statusLabel(m) {
  return statusLabels[m.status] || m.status || 'Pendiente'
}

const statusLabelCls = {
  BOOKED: 'text-slate-500',
  RECEIVED: 'text-amber-700',
  MANIFESTED: 'text-emerald-700',
  DEPARTED: 'text-blue-700',
  ARRIVED: 'text-slate-700',
  CANCELLED: 'text-red-600',
}

function statusLabelClass(m) {
  return statusLabelCls[m.status] || 'text-slate-500'
}

function editOrExpandReceipt(m) {
  const rid = receiptById.value[m.id]
  if (rid && editModalRef.value) {
    editModalRef.value.open(rid)
  } else {
    toggleExpand(m)
  }
}

async function onEditReceiptSaved() {
  await store.loadReceipts()
  if (store.selectedFlightId) {
    await store.loadMawbs(store.selectedFlightId)
  } else {
    await store.loadAllMawbs()
  }
  successMsg.value = 'Recibo actualizado correctamente'
  if (successTimer) clearTimeout(successTimer)
  successTimer = setTimeout(() => { successMsg.value = '' }, 4000)
}

function initForm(m) {
  if (!receiptForms[m.id]) {
  const hawbs = hawbsForDisplay(m.id)
    const h0 = hawbs[0]
    const fallbackShipper = h0?.shipperName || m.shipperName || ''
    const fallbackConsignee = h0?.consigneeName || m.consigneeName || (hawbs.length === 1 ? hawbs[0]?.consigneeName : '') || ''
    const hawbEntries = hawbs.length > 0
      ? hawbs.map(h => ({
          hawbNumber: h.hawbNumber || '',
          consigneeName: h.consigneeName || '',
          pieces: h.pieces || 0,
          weightKg: h.weightKg ? Number(h.weightKg) : 0,
          destination: h.destination || m.destination || 'MIA',
          _dbId: h.id,
          _hawbId: h.id,
        }))
      : [{ hawbNumber: '', consigneeName: '', pieces: 0, weightKg: 0, destination: m.destination || 'MIA', _dbId: null, _hawbId: '_hawb_' + Date.now() }]

    receiptForms[m.id] = {
      gatewayCfs: 'SDQ',
      shipperName: m.shipperName || fallbackShipper,
      consigneeName: m.consigneeName || fallbackConsignee,
      origin: m.origin || store.selectedFlight?.origin || 'SDQ',
      destination: m.destination || store.selectedFlight?.destination || 'MIA',
      awbReportedPieces: m.pieces || (hawbs.length > 0 ? hawbs.reduce((s, h) => s + (h.pieces || 0), 0) : 0) || 0,
      mawbWeightGreatest: 0, // auto-calculado desde scaleWeightLbs de las piezas al cargar
      dimFactorKg: 366, // factor dimensional (KG); se sincroniza con dimFactorIntl del backend al cargar un recibo existente
      dimFactorLbs: 194, // factor dimensional (LBS); se sincroniza con dimFactorDom del backend
      cashOnly: false,
      bookedInAcoms: false,
      docsProvided: false,
      customsCompleted: false,
      preBuilt: false,
      hawbCount: Math.max(hawbs.length, 1),
      hawbEntries,
      pieces: hawbEntries.length > 0
        ? hawbEntries.map(e => ({ pieces: 1, hawbId: e._hawbId || null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 }))
        : [{ pieces: 1, hawbId: null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 }],
      remarks: '',
      evidence: [],
      mawbEvidence: [],
      dockSignature: '',
      printName: '',
      deliveredByName: '',
      deliveredByIdNum: '',
      deliveredBySig: '',
      brokerName: '',
      brokerIdNum: '',
      brokerSig: '',
      receivedByName: '',
      receivedBySig: '',
      receiptDate: null,
      startDatetime: null,
      _piecesLoadedFromDb: false,
      pieceCount: 0,
      totalWeightKg: 0,
    }
  }
}

async function loadExistingReceiptData(m) {
  const f = receiptForms[m.id]
  if (!f) return
  const existingReceipts = (store.receipts || []).filter(r => (r.mawb?.id || r.mawbId) === m.id)
  if (existingReceipts.length === 0) return
  existingReceipts.sort((a, b) => {
    const ca = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const cb = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return ca - cb
  })
  // Prefer non-superseded receipts as the active source
  const activeReceipts = existingReceipts.filter(r => !r.superseded)
  const sourceReceipts = activeReceipts.length > 0 ? activeReceipts : existingReceipts
  // Use the last receipt for metadata (field values), but load pieces from ALL
  const sourceReceipt = sourceReceipts[sourceReceipts.length - 1]
  f.gatewayCfs = sourceReceipt.gatewayCfs ?? 'SDQ'
  f.shipperName = sourceReceipt.shipperName ?? f.shipperName
  f.consigneeName = sourceReceipt.consigneeName ?? f.consigneeName
  f.origin = sourceReceipt.origin ?? f.origin
  f.destination = sourceReceipt.destination ?? f.destination
  f.awbReportedPieces = sourceReceipt.awbReportedPieces ?? f.awbReportedPieces
  f.mawbWeightGreatest = sourceReceipt.mawbWeightGreatest ?? f.mawbWeightGreatest
  f.dimFactorKg = sourceReceipt.dimFactorIntl ? Number(sourceReceipt.dimFactorIntl) : f.dimFactorKg
  f.dimFactorLbs = sourceReceipt.dimFactorDom ? Number(sourceReceipt.dimFactorDom) : f.dimFactorLbs
  f.cashOnly = sourceReceipt.cashOnly ?? f.cashOnly
  f.bookedInAcoms = sourceReceipt.bookedInAcoms ?? f.bookedInAcoms
  f.docsProvided = sourceReceipt.docsProvided ?? f.docsProvided
  f.customsCompleted = sourceReceipt.customsCompleted ?? f.customsCompleted
  f.preBuilt = sourceReceipt.preBuilt ?? f.preBuilt
  f.remarks = sourceReceipt.remarks ?? f.remarks
  f.dockSignature = sourceReceipt.dockSignature ?? f.dockSignature
  f.printName = sourceReceipt.printName ?? f.printName
  f.deliveredByName = sourceReceipt.deliveredByName ?? f.deliveredByName
  f.deliveredByIdNum = sourceReceipt.deliveredByIdNum ?? f.deliveredByIdNum
  f.deliveredBySig = sourceReceipt.deliveredBySigUrl ?? f.deliveredBySig
  f.brokerName = sourceReceipt.brokerName ?? f.brokerName
  f.brokerIdNum = sourceReceipt.brokerIdNum ?? f.brokerIdNum
  f.brokerSig = sourceReceipt.brokerSigUrl ?? f.brokerSig
  f.receivedByName = sourceReceipt.receivedByName ?? f.receivedByName
  f.receivedBySig = sourceReceipt.receivedBySigUrl ?? f.receivedBySig
  f.receiptDate = sourceReceipt.receiptDate ?? f.receiptDate
  f.startDatetime = sourceReceipt.startDatetime ?? f.startDatetime
  f.pieceCount = sourceReceipt.pieceCount ?? 0
  f.totalWeightKg = sourceReceipt.actualWeightKg ?? sourceReceipt.chargeableWeightKg ?? 0
  // Load pieces from ALL non-superseded receipts (not just one)
  try {
    const pieceResults = await Promise.all(
      sourceReceipts.map(r => receiptsApi.getPieces(r.id).catch(() => ({ data: [] })))
    )
    const allLoadedPieces = pieceResults.flatMap(res => res.data || [])
    if (allLoadedPieces.length > 0) {
      f.pieces = allLoadedPieces.map((p) => {
        return {
          pieces: p.pieces ?? 1,
          hawbId: p.hawbId ?? null,
          lengthIn: p.lengthIn ?? null,
          widthIn: p.widthIn ?? null,
          heightIn: p.heightIn ?? null,
          scaleWeightLbs: p.scaleWeightLbs ?? null,
          dimWeight: Number(p.dimWeightLbs) || 0,
          dimWeightLbs: Number(p.dimWeightLbs) || 0,
          scaleWeightKg: Number(p.scaleWeightKg) || 0,
          dimWeightKg: Number(p.dimWeightKg) || 0,
          chargeableKg: Number(p.chargeableKg) || 0,
          chargeableLbs: Number(p.chargeableLbs) || 0,
        }
      })
      f._piecesLoadedFromDb = true
      f.mawbWeightGreatest = totalScaleLbs(m.id, null)
    }
  } catch (e) { toast.error(extractError(e)) }
  try {
    const docsRes = await receiptsApi.getSupportingDocsJson(sourceReceipt.id)
    if (docsRes.data && Array.isArray(docsRes.data)) {
      f.evidence = docsRes.data.map(d => ({
        name: d.name || 'documento',
        type: d.type || 'document',
        url: d.url || '',
        file: null,
      }))
    }
  } catch { /* no supporting docs yet */ }
}

function calcPiece(mawbId, pi) {
  const p = receiptForms[mawbId].pieces[pi]
  const l = p.lengthIn || 0
  const w = p.widthIn || 0
  const h = p.heightIn || 0
  const qty = p.pieces || 1
  const vol = l * w * h * qty
  const dimFactorKg = receiptForms[mawbId]?.dimFactorKg || 366
  const dimFactorLbs = receiptForms[mawbId]?.dimFactorLbs || 194
  p.dimWeightKg = vol > 0 ? vol / dimFactorKg : 0
  p.dimWeightLbs = vol > 0 ? vol / dimFactorLbs : 0
  p.dimWeight = p.dimWeightLbs
  p.scaleWeightKg = p.scaleWeightLbs ? p.scaleWeightLbs / 2.20462 : 0
  p.chargeableKg = Math.max(p.dimWeightKg, p.scaleWeightKg)
  p.chargeableLbs = Math.max(p.scaleWeightLbs || 0, p.dimWeightLbs || 0)
  // MAWB weight = suma de pesos de bascula de todas las piezas
  const f = receiptForms[mawbId]
  if (f) f.mawbWeightGreatest = totalScaleLbs(mawbId, null)
  bumpFormVersionDebounced() // refresca (con debounce) el bloque memoizado del MAWB para reflejar totales al tipear
}

function allPieces(mawbId, hawbId) {
  const pieces = receiptForms[mawbId]?.pieces || []
  return hawbId ? pieces.filter(p => p.hawbId === hawbId) : pieces
}

function totalPieces(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.pieces || 1), 0)
}

function totalDimWeight(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.dimWeight || 0), 0)
}

function totalScaleLbs(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.scaleWeightLbs || 0), 0)
}

function totalDimLbs(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.dimWeightLbs || 0), 0)
}

function totalScaleKg(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.scaleWeightKg || 0), 0)
}

function totalDimKg(mawbId, hawbId) {
  return allPieces(mawbId, hawbId).reduce((s, p) => s + (p.dimWeightKg || 0), 0)
}

function totalChargeableKg(mawbId, hawbId) {
  const pieces = allPieces(mawbId, hawbId)
  return pieces.reduce((s, p) => s + (p.chargeableKg || 0), 0)
}

function totalChargeableLbs(mawbId, hawbId) {
  const pieces = allPieces(mawbId, hawbId)
  return pieces.reduce((s, p) => s + (p.chargeableLbs || 0), 0)
}

function hawbsForDisplay(mawbId) {
  return receiptForms[mawbId]?.hawbEntries || []
}

function piecesByHawb(mawbId, hawbId) {
  return allPieces(mawbId, hawbId)
}

// Igual que piecesByHawb, pero conserva el indice real dentro de receiptForms[mawbId].pieces
// para cada elemento. Evita tener que hacer pieces.indexOf(p) repetidamente en el template
// (O(n) por cada input, y fragil si dos piezas fueran el mismo objeto por referencia).
function piecesByHawbIndexed(mawbId, hawbId) {
  if (hawbId == null) return []
  const pieces = receiptForms[mawbId]?.pieces || []
  const result = []
  for (let i = 0; i < pieces.length; i++) {
    if (pieces[i].hawbId === hawbId) {
      result.push({ piece: pieces[i], idx: i })
    }
  }
  return result
}

function groupedSummary(mawbId) {
  const pieces = receiptForms[mawbId]?.pieces || []
  const groups = {}
  pieces.forEach(p => {
    const l = Number(p.lengthIn) || 0
    const w = Number(p.widthIn) || 0
    const h = Number(p.heightIn) || 0
    const key = `${l}x${w}x${h}`
    if (!groups[key]) {
      groups[key] = { lengthIn: l, widthIn: w, heightIn: h, totalPieces: 0, totalDimWeight: 0, totalScaleLbs: 0, totalDimLbs: 0, totalScaleKg: 0, totalDimKg: 0, totalChargeableKg: 0, totalChargeableLbs: 0 }
    }
    const g = groups[key]
    g.totalPieces += Number(p.pieces) || 1
    g.totalDimWeight += Number(p.dimWeight) || 0
    g.totalScaleLbs += Number(p.scaleWeightLbs) || 0
    g.totalDimLbs += Number(p.dimWeightLbs) || 0
    g.totalScaleKg += Number(p.scaleWeightKg) || 0
    g.totalDimKg += Number(p.dimWeightKg) || 0
    g.totalChargeableKg += Number(p.chargeableKg) || 0
    g.totalChargeableLbs += Number(p.chargeableLbs) || 0
  })
  return Object.values(groups)
}

function hawbTotalPieces(mawbId, hawbId) { return totalPieces(mawbId, hawbId) }
function hawbDimWeight(mawbId, hawbId) { return totalDimWeight(mawbId, hawbId) }
function hawbScaleLbs(mawbId, hawbId) { return totalScaleLbs(mawbId, hawbId) }
function hawbDimLbs(mawbId, hawbId) { return totalDimLbs(mawbId, hawbId) }
function hawbScaleKg(mawbId, hawbId) { return totalScaleKg(mawbId, hawbId) }
function hawbDimKg(mawbId, hawbId) { return totalDimKg(mawbId, hawbId) }
function hawbChargeableKg(mawbId, hawbId) { return totalChargeableKg(mawbId, hawbId) }
function hawbChargeableLbs(mawbId, hawbId) { return totalChargeableLbs(mawbId, hawbId) }

function removeHawbEntry(mawbId, idx) {
  const f = receiptForms[mawbId]
  if (!f || f.hawbEntries.length <= 1) return
  const removedHawbId = f.hawbEntries[idx]._hawbId
  f.hawbEntries.splice(idx, 1)
  f.hawbCount = f.hawbEntries.length
  f.pieces = f.pieces.filter(p => p.hawbId !== removedHawbId)
  if (f.pieces.length === 0) {
    f.pieces.push({ pieces: 1, hawbId: f.hawbEntries[0]?._hawbId || null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 })
  }
  bumpFormVersion()
}

function addHawbEntry(m) {
  const f = receiptForms[m.id]
  if (!f) return
  const newHawbId = '_hawb_' + Date.now() + '_' + f.hawbEntries.length
  f.hawbEntries.push({
    hawbNumber: '', consigneeName: '', pieces: 0, weightKg: 0,
    destination: f.destination || m.destination || 'MIA', _dbId: null,
    _hawbId: newHawbId
  })
  f.hawbCount = f.hawbEntries.length
  f.pieces.push({ pieces: 1, hawbId: newHawbId, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 })
  bumpFormVersion()
}

async function syncMawbName(m, field) {
  const val = receiptForms[m.id]?.[field]
  if (val !== undefined && val !== m[field]) {
    try {
      await mawbsApi.update(m.id, { [field]: val })
      m[field] = val
    } catch (e) {
      toast.error(extractError(e))
      console.warn('Error syncing MAWB ' + field + ':', e)
    }
  }
}

async function changeMawbStatus(m, newStatus) {
  const cur = m.status || 'BOOKED'
  if (cur === newStatus) return
  if (!confirm(`¿Cambiar estado de ${m.awbNumber || m.id.slice(0, 8)} de "${statusLabels[cur] ?? cur}" a "${statusLabels[newStatus] ?? newStatus}"?`)) return
  m.status = newStatus
  try {
    await mawbsApi.updateStatus(m.id, newStatus)
    if (store.selectedFlightId) {
      await store.loadMawbs(store.selectedFlightId)
    } else {
      await store.loadAllMawbs()
    }
  } catch (e) {
    toast.error(extractError(e))
    m.status = cur
    alert('Error al actualizar estado: ' + (e.response?.data?.error || e.message))
  }
}

function addPiece(mawbId, hawbId) {
  const f = receiptForms[mawbId]
  const newPiece = { pieces: 1, hawbId: hawbId || null, lengthIn: null, widthIn: null, heightIn: null, scaleWeightLbs: null, dimWeight: 0, dimWeightLbs: 0, scaleWeightKg: 0, dimWeightKg: 0, chargeableKg: 0, chargeableLbs: 0 }
  f.pieces = [...f.pieces, newPiece]
  if (f) f.mawbWeightGreatest = totalScaleLbs(mawbId, null)
  bumpFormVersion()
}

function removePiece(mawbId, idx) {
  const f = receiptForms[mawbId]
  const pieces = f.pieces
  if (pieces.length > 1) f.pieces = pieces.filter((_, i) => i !== idx)
  if (f) f.mawbWeightGreatest = totalScaleLbs(mawbId, null)
  bumpFormVersion()
}

function cancelForm() {
  const mId = expandedId.value
  if (!mId) return
  saveDraft(mId)
  expandedId.value = null
  localStep.value = 1
  generatedReceiptId.value = null
  lastDraftSave.value = ''
}

// Step validation helpers for progress bar
function stepData(mawbId) {
  return receiptForms[mawbId] || null
}

function stepDone(si) {
  const mId = expandedId.value
  const d = stepData(mId)
  if (!d) return false
  if (si === 0) return !!(d.shipperName && d.consigneeName && d.origin && d.destination)
  if (si === 1) return d.pieces.length > 0 && d.pieces.some(p => p.lengthIn || p.widthIn || p.heightIn)
  if (si === 2) return true // remarks optional
  if (si === 3) return true // evidence optional
  if (si === 4) return !!(d.printName || d.dockSignature)
  return false
}

function stepError(_si) {
  return false // could add backend validation errors here
}

function stepClass(si) {
  if (localStep.value === si + 1) return 'bg-slate-950 text-white border-slate-950 scale-110 shadow-lg'
  if (stepDone(si)) return 'bg-slate-500 text-white border-slate-500'
  return 'bg-white text-slate-500 border-slate-300 group-hover:border-slate-500'
}

function stepBarClass(si) {
  if (stepDone(si) || localStep.value > si + 1) return 'bg-slate-500'
  if (localStep.value === si + 1) return 'bg-slate-300'
  return 'bg-slate-200'
}

async function toggleExpand(m) {
  if (expandedId.value === m.id) {
    cancelForm()
  } else {
    expandedId.value = m.id
    localStep.value = 1
    generatedReceiptId.value = null
    initForm(m)
    applyDraftToForm(m.id)
    try {
      const [hawbRes, docsRes] = await Promise.all([
        hawbsApi.getByMawb(m.id),
        mawbsApi.getSupportingDocs(m.id).catch(() => ({ data: [] })),
      ])
      const hawbData = hawbRes.data
      receiptHawbs[m.id] = hawbData
      const f = receiptForms[m.id]
      if (f) {
        // Cargar evidencias del MAWB (solo lectura) en el formulario
        f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')

        await loadExistingReceiptData(m)
        bumpFormVersion()

        if (hawbData.length > 0) {
          const h0 = hawbData[0]
          if (!f.shipperName) f.shipperName = m.shipperName || h0?.shipperName || ''
          if (!f.consigneeName) f.consigneeName = m.consigneeName || (hawbData.length === 1 ? h0?.consigneeName : '') || ''
          // mawbWeightGreatest se auto-calculó desde scaleWeightLbs en loadExistingReceiptData o calcPiece
          if (f.awbReportedPieces == null) f.awbReportedPieces = m.pieces || hawbData.reduce((s, h) => s + (h.pieces || 0), 0) || 0
          const existingIds = new Set(f.hawbEntries.filter(e => e._dbId).map(e => e._dbId))
          for (const h of hawbData) {
            if (!existingIds.has(h.id)) {
              f.hawbEntries.push({
                hawbNumber: h.hawbNumber || '',
                consigneeName: h.consigneeName || '',
                pieces: h.pieces || 0,
                weightKg: h.weightKg ? Number(h.weightKg) : 0,
                destination: h.destination || f.destination || 'MIA',
                _dbId: h.id,
                _hawbId: h.id,
              })
            }
          }
          // Remove phantom default entries (no _dbId) when real DB HAWBs exist
          f.hawbEntries = f.hawbEntries.filter(e => e._dbId != null || f.hawbEntries.filter(e2 => e2._dbId != null).length === 0)
          f.hawbCount = f.hawbEntries.length
          // Build set of valid hawbIds from real entries
          const validHawbIds = new Set(f.hawbEntries.map(e => e._hawbId).filter(Boolean))
          // Remove orphaned pieces (hawbId doesn't match any real entry) that have no dimensions
          f.pieces = f.pieces.filter(p => validHawbIds.has(p.hawbId) || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
          // Re-link any remaining orphaned pieces (with dimensions but stale hawbId) to first real entry
          for (const p of f.pieces) {
            if (!validHawbIds.has(p.hawbId)) {
              p.hawbId = f.hawbEntries[0]?._hawbId || null
            }
          }
        }
      }
    } catch (e) {
      toast.error(extractError(e))
      receiptHawbs[m.id] = []
    }
  }
}

function nextStep() { if (localStep.value < 5) localStep.value++ }
function prevStep() { if (localStep.value > 1) localStep.value-- }

function openCamera(mawbId) {
  cameraMawbId.value = mawbId
  showCamera.value = true
}

function onCameraCapture(dataUrl) {
  const mawbId = cameraMawbId.value
  if (mawbId && dataUrl) {
    receiptForms[mawbId].evidence.push({
      name: 'Foto_' + new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-') + '.jpg',
      type: 'image',
      url: dataUrl,
      file: null,
    })
  }
  cameraMawbId.value = null
}

function addEvidence(mawbId) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*,.pdf'
  input.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0;opacity:0;pointer-events:none'
  const handler = (e) => {
    input.removeEventListener('change', handler)
    input.remove()
    handleEvidenceUpload(mawbId, e)
  }
  input.addEventListener('change', handler)
  document.body.appendChild(input)
  input.click()
}

function isPdfUrl(url) {
  if (!url) return false
  if (url.startsWith('data:application/pdf')) return true
  if (url.toLowerCase().endsWith('.pdf')) return true
  return false
}

function compressImage(dataUrl, maxDim = 720, quality = 0.4) {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => {
      let w = img.width, h = img.height
      if (w > maxDim || h > maxDim) {
        const s = Math.min(maxDim / w, maxDim / h, 1)
        w = Math.round(w * s)
        h = Math.round(h * s)
      }
      const c = document.createElement('canvas')
      c.width = w; c.height = h
      c.getContext('2d').drawImage(img, 0, 0, w, h)
      resolve(c.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => resolve(dataUrl)
    img.src = dataUrl
  })
}

async function handleEvidenceUpload(mawbId, e) {
  const files = e.target.files
  if (!files || !files.length) return
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = async (ev) => {
      let url = ev.target.result
      if (file.type.startsWith('image/')) {
        url = await compressImage(url)
      }
      receiptForms[mawbId].evidence.push({
        name: file.name,
        type: file.type.startsWith('image/') ? 'image' : 'document',
        url,
        file: file,
      })
    }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function removeEvidence(mawbId, idx) {
  receiptForms[mawbId].evidence.splice(idx, 1)
}

async function downloadReceiptById(m) {
  const id = receiptById.value[m.id]
  if (!id) return
  try {
    const res = await receiptsApi.export(id)
    const disposition = res.headers?.['content-disposition'] || ''
    const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";\n]+)"?/)
    const filename = match ? match[1].trim() : `RECIBO_BODEGA_${id.slice(0, 8)}.xlsx`
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('Download error:', e)
  }
}

async function downloadReceiptByIdAuto(receiptId, awbNumber) {
  try {
    const res = await receiptsApi.export(receiptId)
    const filename = `RECIBO_DE_BODEGA_AWB ${awbNumber || receiptId.slice(0, 8)}${receiptVersionTag(receiptId)}.xlsx`
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('Auto-download Excel failed:', e)
  }
}

async function downloadReceiptPdfAuto(receiptId, awbNumber) {
  try {
    const res = await receiptsApi.getFullPdf(receiptId)
    const filename = `RECIBO_DE_BODEGA_AWB ${awbNumber || receiptId.slice(0, 8)}${receiptVersionTag(receiptId)}.pdf`
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.warn('Auto-download PDF failed:', e)
  }
}

async function downloadHtmlById(m) {
  const id = receiptById.value[m.id]
  if (!id) return
  try {
    const res = await receiptsApi.getSupportingDocsHtml(id)
    const blob = new Blob([res.data], { type: 'text/html; charset=UTF-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `EVIDENCIAS_${id.slice(0, 8)}.html`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('Supporting docs HTML error:', e)
  }
}

async function downloadPdfById(m) {
  const id = receiptById.value[m.id]
  if (!id) return
  try {
    const res = await receiptsApi.getSupportingDocsPdf(id)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `EVIDENCIAS_${id.slice(0, 8)}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('Supporting docs PDF error:', e)
  }
}

function openConfirmModal(m) {
  const f = receiptForms[m.id]
  if (!f) return
  f.pieces = f.pieces.filter(p => p.hawbId != null || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
  if (f.pieces.length === 0) {
    alert('Ingresa al menos una pieza')
    return
  }
  pendingSubmitMawb.value = m
  showConfirmModal.value = true
}

async function confirmSubmit() {
  if (!pendingSubmitMawb.value) return
  showConfirmModal.value = false
  const m = pendingSubmitMawb.value
  pendingSubmitMawb.value = null
  await submitReceipt(m)
}

function formatCorrection(c) {
  const m = c.match(/(\d+)\s*→\s*(\d+)/)
  if (m) {
    return c.replace(m[0],
      `<span class="line-through opacity-50">${m[1]} skids</span> <span class="opacity-40 mx-1">&#8594;</span> <span class="font-bold text-amber-700">${m[2]} skids</span>`
    )
  }
  return c
}

function cancelBookingCorrection() {
  showBookingCorrectionModal.value = false
  pendingBookingCorrections.value = []
  pendingEmitPayload.value = null
  pendingEmitMawb.value = null
  pendingEmitHawbs.value = null
  submitting.value = false
}

async function confirmBookingCorrection() {
  showBookingCorrectionModal.value = false
  const payload = pendingEmitPayload.value
  const m = pendingEmitMawb.value
  const hawbs = pendingEmitHawbs.value
  pendingBookingCorrections.value = []
  pendingEmitPayload.value = null
  pendingEmitMawb.value = null
  pendingEmitHawbs.value = null
  try {
    await executeEmit(m, hawbs, payload)
  } catch (e) {
    submitting.value = false
    toast.error(extractError(e))
    const data = e.response?.data
    const msg = data?.error || data?.message || (typeof data === 'string' ? data : null) || e.message
    console.error('Booking correction emit error:', { error: e, responseData: data })
    alert('Error (' + (e.response?.status || '?') + '): ' + msg)
  }
}

async function submitReceipt(m) {
  if (submitting.value) return
  const f = receiptForms[m.id]
  const hawbs = receiptHawbs[m.id] || []
  if (!f) return
  f.pieces = f.pieces.filter(p => p.hawbId != null || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
  if (f.pieces.length === 0) {
    alert('Ingresa al menos una pieza')
    return
  }
  if (receiptById.value[m.id]) {
    if (!confirm('Este MAWB ya tiene un recibo activo. Emitir uno nuevo reemplazara el recibo existente. ¿Continuar?')) {
      return
    }
  }
  submitting.value = true
  try {
    console.warn('[Submit] START', { mawbId: m.id, awbNumber: m.awbNumber, pieceCount: f.pieces.length, hawbCount: hawbs.length })
    try {
      if (f.shipperName && f.shipperName !== m.shipperName) {
        await mawbsApi.update(m.id, { shipperName: f.shipperName })
        m.shipperName = f.shipperName
      }
      if (f.consigneeName && f.consigneeName !== m.consigneeName) {
        await mawbsApi.update(m.id, { consigneeName: f.consigneeName })
        m.consigneeName = f.consigneeName
      }
      for (const h of hawbs) {
        if (h._dirty && h.id) {
          await hawbsApi.update(h.id, { consigneeName: h.consigneeName })
        }
      }
    } catch (nameErr) {
      console.warn('Non-critical: failed to sync MAWB/HAWB names', nameErr)
    }

    for (const entry of f.hawbEntries) {
      if (!entry._dbId && entry.hawbNumber && !hawbs.includes(entry)) {
        hawbs.push(entry)
      }
    }

    for (const h of hawbs) {
      if (!h._dbId && h.hawbNumber) {
        const oldHawbId = h._hawbId
        try {
          const res = await hawbsApi.create({
            mawbId: m.id,
            hawbNumber: h.hawbNumber,
            consigneeName: h.consigneeName || '',
            pieces: h.pieces || 1,
            weightKg: h.weightKg || 0,
            destination: h.destination || f.destination || 'MIA',
          })
          h._dbId = res.data.id
          h._hawbId = res.data.id
          for (const p of f.pieces) {
            if (p.hawbId === oldHawbId) {
              p.hawbId = res.data.id
            }
          }
        } catch (hawbErr) {
          console.warn('Failed to persist HAWB:', h.hawbNumber, hawbErr)
        }
      }
    }

    function buildPayload(pieceList, remarkSuffix) {
      const hawbId = hawbs.length <= 1 ? (hawbs[0]?.id || hawbs[0]?._hawbId || null) : null
      const validHawbId = hawbId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(hawbId) ? hawbId : null
      return {
        mawbId: m.id,
        airlineId: m.airline?.id || m.airlineId || null,
        hawbId: validHawbId,
        gatewayCfs: f.gatewayCfs || 'SDQ',
        shipperName: f.shipperName ?? m.shipperName ?? '',
        consigneeName: f.consigneeName ?? m.consigneeName ?? '',
        origin: f.origin || 'SDQ',
        destination: f.destination || 'MIA',
        awbReportedPieces: f.awbReportedPieces ?? pieceList.reduce((s, p) => s + (p.pieces || 1), 0),
        mawbWeightGreatest: f.mawbWeightGreatest || 0,
        dimFactorIntl: f.dimFactorKg || 366,
        dimFactorDom: f.dimFactorLbs || 194,
        pieceCount: pieceList.reduce((s, p) => s + (p.pieces || 1), 0),
        cashOnly: f.cashOnly || false,
        bookedInAcoms: f.bookedInAcoms || false,
        docsProvided: f.docsProvided || false,
        customsCompleted: f.customsCompleted || false,
        preBuilt: f.preBuilt || false,
        remarks: (f.remarks || '').replace(/ — RECIBO GENERAL(?:\s*— RECIBO GENERAL)*$/, '') + (remarkSuffix ? ' — ' + remarkSuffix : ''),
        dockSignature: f.dockSignature || '',
        printName: f.printName || '',
        deliveredByName: f.deliveredByName || '',
        deliveredByIdNum: f.deliveredByIdNum || '',
        deliveredBySigUrl: f.deliveredBySig || '',
        receivedByName: f.receivedByName || f.printName || '',
        receivedBySigUrl: f.receivedBySig || '',
        brokerName: f.brokerName || '',
        brokerIdNum: f.brokerIdNum || '',
        brokerSigUrl: f.brokerSig || '',
        startDatetime: f.startDatetime || new Date().toISOString(),
        receiptDate: f.receiptDate || new Date().toISOString(),
        pieces: pieceList.map((p, i) => ({
          pieceNumber: i + 1,
          pieces: p.pieces || 1,
          hawbId: (p.hawbId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(p.hawbId)) ? p.hawbId : null,
          lengthIn: p.lengthIn || 0,
          widthIn: p.widthIn || 0,
          heightIn: p.heightIn || 0,
          scaleWeightLbs: p.scaleWeightLbs || 0,
          scaleWeightKg: p.scaleWeightKg || 0,
          dimWeightLbs: p.dimWeightLbs || 0,
          dimWeightKg: p.dimWeightKg || 0,
          chargeableLbs: p.chargeableLbs || 0,
          chargeableKg: p.chargeableKg || 0,
        })),
        supportingDocs: JSON.stringify(f.evidence.map(ev => ({
          name: ev.name,
          type: ev.type,
          url: ev.url,
        }))),
      }
    }

    const payload = hawbs.length <= 1
      ? buildPayload(f.pieces, '')
      : buildPayload(f.pieces, 'RECIBO GENERAL')

    // ─── VALIDATE FIRST (dry-run) ────────────────────────
    const newPieceCount = f.pieces.reduce((s, p) => s + (p.pieces || 1), 0)
    try {
      const valRes = await receiptsApi.validate({
        mawbId: m.id,
        pieces: [{ pieces: newPieceCount }],
      })
      const corrections = valRes.data?.corrections || []
      if (corrections.length > 0) {
        // Show booking correction modal — do NOT emit yet
        pendingBookingCorrections.value = corrections
        pendingEmitPayload.value = payload
        pendingEmitMawb.value = m
        pendingEmitHawbs.value = hawbs
        showBookingCorrectionModal.value = true
        return
      }
    } catch (valErr) {
      console.warn('Validation failed, proceeding with emit:', valErr)
    }

    // ─── NO CORRECTIONS → EMIT DIRECTLY ──────────────────
    await executeEmit(m, hawbs, payload)
  } catch (e) {
    toast.error(extractError(e))
    const data = e.response?.data
    const msg = data?.error || data?.message || (typeof data === 'string' ? data : null) || e.message
    console.error('Receipt submit error:', { error: e, responseData: data })
    alert('Error (' + e.response?.status + '): ' + msg)
  } finally {
    submitting.value = false
  }
}

async function executeEmit(m, hawbs, payload) {
  const f = receiptForms[m.id]
  if (!f) return

  const res = await store.emitReceipt(payload)
  const receiptId = res?.id || null
  if (receiptId) generatedReceiptId.value = receiptId

  await store.loadReceipts()
  await loadExistingReceiptData(m)
  bumpFormVersion()
  localStep.value = 5
  clearDraft(m.id)

  if (store.selectedFlightId) {
    await store.loadMawbs(store.selectedFlightId)
  } else {
    await store.loadAllMawbs()
  }
  const totalKg = (receiptForms[m.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightKg || 0), 0)
  const totalLbs = (receiptForms[m.id]?.pieces || []).reduce((s, p) => s + (p.scaleWeightLbs || 0), 0)
  const chargeKg = (receiptForms[m.id]?.pieces || []).reduce((s, p) => s + Math.max(p.dimWeightKg || 0, p.scaleWeightKg || 0), 0)
  successMsg.value = 'Recibo generado' +
    ` — ${totalPieces(m.id, null)} pzas, ${totalKg.toFixed(1)} KGS / ${totalLbs.toFixed(1)} LBS (facturable: ${chargeKg.toFixed(1)} KGS)`
  if (successTimer) clearTimeout(successTimer)
  successTimer = setTimeout(() => { successMsg.value = '' }, 6000)

  const downloadId = generatedReceiptId.value
  if (downloadId) {
    setTimeout(() => downloadReceiptByIdAuto(downloadId, m.awbNumber), 1500)
    setTimeout(() => downloadReceiptPdfAuto(downloadId, m.awbNumber), 3000)
  }
}

const receiptTotals = computed(() => {
  const totals = {}
  for (const r of store.receipts || []) {
    if (r.superseded) continue
    const mawbId = r.mawb?.id || r.mawbId
    if (!mawbId) continue
    if (!totals[mawbId]) totals[mawbId] = { pieces: 0, weightKg: 0, weightLbs: 0 }
    const pc = r.pieceCount || 0
    totals[mawbId].pieces += pc
    const wk = Number(r.actualWeightKg ?? r.chargeableWeightKg ?? 0)
    totals[mawbId].weightKg += wk
    const wl = Number(r.actualWeightLbs ?? r.chargeableWeightLbs ?? 0)
    totals[mawbId].weightLbs += wl
  }
  return totals
})

const overdueMawbs = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  const flightMap = {}
  for (const f of store.flights) flightMap[f.id] = f
  const result = []
  for (const m of store.mawbs) {
    const hasReceipt = !!receiptById.value[m.id]
    const st = m.status || 'BOOKED'
    if (hasReceipt && st !== 'DEPARTED') {
      const flight = flightMap[m.flightId]
      if (flight && flight.flightDate && flight.flightDate < today) {
        result.push({ mawb: m, flight })
      }
    }
  }
  return result
})

const overdueSet = computed(() => new Set(overdueMawbs.value.map(o => o.mawb.id)))

const receiptById = computed(() => {
  const map = {}
  for (const r of store.receipts || []) {
    if (r.superseded) continue
    const mawbId = r.mawb?.id || r.mawbId
    if (mawbId) map[mawbId] = r.id
  }
  return map
})

function receiptVersionTag(receiptId) {
  const r = (store.receipts || []).find(x => x.id === receiptId)
  const v = r?.correctionNumber ?? 1
  return '-V' + v
}

// ── Evidence Preview ──
const evidencePreview = reactive({ show: false, item: null })

function previewEvidence(item) {
  evidencePreview.item = item
  evidencePreview.show = true
}

function closeEvidencePreview() {
  evidencePreview.show = false
  evidencePreview.item = null
}

function downloadEvidenceItem(item) {
  if (!item || !item.url) return
  const isPdf = isPdfUrl(item.url)
  const mime = isPdf ? 'application/pdf' : (item.type === 'image' ? 'image/png' : 'application/octet-stream')
  const blob = dataUriToBlob(item.url, mime)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = item.name || (isPdf ? 'documento.pdf' : 'imagen.png')
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function dataUriToBlob(dataUri, mimeType) {
  const byteString = atob(dataUri.split(',')[1])
  const ab = new ArrayBuffer(byteString.length)
  const ia = new Uint8Array(ab)
  for (let i = 0; i < byteString.length; i++) ia[i] = byteString.charCodeAt(i)
  return new Blob([ab], { type: mimeType })
}

// ── MAWB Evidence Manager ──
const mawbEvidenceMgr = reactive({ show: false, mawbId: null, mawb: null, docs: [], dirty: false })
const mawbEvidenceInput = ref(null)
const mawbCameraOpen = ref(false)

async function toggleMawbEvidenceManager(m) {
  if (mawbEvidenceMgr.show && mawbEvidenceMgr.mawbId === m.id) {
    closeMawbEvidenceMgr()
    return
  }
  mawbEvidenceMgr.show = true
  mawbEvidenceMgr.mawbId = m.id
  mawbEvidenceMgr.mawb = m
  mawbEvidenceMgr.dirty = false
  try {
    const res = await mawbsApi.getSupportingDocs(m.id)
    mawbEvidenceMgr.docs = res.data || []
  } catch (e) {
    toast.error(extractError(e))
    mawbEvidenceMgr.docs = []
  }
}

function closeMawbEvidenceMgr() {
  if (mawbEvidenceMgr.dirty) {
    if (!confirm('Hay cambios sin guardar. ¿Descartar cambios?')) return
  }
  mawbEvidenceMgr.show = false
  mawbEvidenceMgr.mawbId = null
  mawbEvidenceMgr.mawb = null
  mawbEvidenceMgr.docs = []
  mawbEvidenceMgr.dirty = false
}

function handleMawbEvidenceUpload(e) {
  const files = e.target.files
  if (!files || !files.length) return
  for (const file of files) {
    const reader = new FileReader()
    reader.onload = (ev) => {
      mawbEvidenceMgr.docs.push({
        name: file.name,
        type: file.type.startsWith('image/') ? 'image' : 'document',
        url: ev.target.result,
      })
      mawbEvidenceMgr.dirty = true
    }
    reader.readAsDataURL(file)
  }
  e.target.value = ''
}

function openMawbCamera() {
  mawbCameraOpen.value = true
}

function onMawbCameraCapture(dataUrl) {
  if (dataUrl) {
    mawbEvidenceMgr.docs.push({
      name: 'Foto_' + new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-') + '.jpg',
      type: 'image',
      url: dataUrl,
    })
    mawbEvidenceMgr.dirty = true
  }
}

function removeMawbEvidence(idx) {
  mawbEvidenceMgr.docs.splice(idx, 1)
  mawbEvidenceMgr.dirty = true
}

async function saveMawbEvidence() {
  const id = mawbEvidenceMgr.mawbId
  if (!id) return
  try {
    const docsToSave = mawbEvidenceMgr.docs.map(d => ({ name: d.name, type: d.type, url: d.url }))
    await mawbsApi.updateSupportingDocs(id, docsToSave)
    mawbEvidenceMgr.dirty = false
    alert('Evidencias guardadas correctamente')
  } catch (e) {
    toast.error(extractError(e))
    alert('Error guardando evidencias: ' + (e.response?.data?.error || e.message))
  }
}

async function downloadMawbEvidencePdf() {
  const id = mawbEvidenceMgr.mawbId
  if (!id || mawbEvidenceMgr.docs.length === 0) return
  try {
    const res = await mawbsApi.getSupportingDocsPdf(id)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `EVIDENCIAS_MAWB_${mawbEvidenceMgr.mawb?.awbNumber || id.slice(0, 8)}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(extractError(e))
    console.error('MAWB evidence PDF error:', e)
    alert('Error descargando PDF de evidencias')
  }
}

// ── Print Preview ──
async function printPreview(m) {
  const f = receiptForms[m.id]
  if (!f) return
  const receiptId = generatedReceiptId.value
  if (!receiptId) {
    toast.error('Primero confirma el recibo antes de imprimir')
    return
  }
  try {
    const res = await receiptsApi.getFullPdf(receiptId)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `RECIBO_DE_BODEGA_AWB ${m.awbNumber || receiptId.slice(0, 8)}${receiptVersionTag(receiptId)}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error('Error generando PDF: ' + (e.response?.data?.error || e.message))
    console.error('Print preview PDF error:', e)
  }
}

onMounted(async () => {
  if (!store.airlines.length) await store.loadAirlines()
  if (!store.flights.length) await store.loadFlights()
  await store.loadReceipts()
  await store.loadAllMawbs()
  if (route.query.mawbId && store.mawbs.length) {
    const m = store.mawbs.find(x => x.id === route.query.mawbId)
    if (m) {
      expandedId.value = m.id
      initForm(m)
      try {
        const [hawbRes, docsRes] = await Promise.all([
          hawbsApi.getByMawb(m.id),
          mawbsApi.getSupportingDocs(m.id).catch(() => ({ data: [] })),
        ])
        receiptHawbs[m.id] = hawbRes.data
        const f = receiptForms[m.id]
        if (f) {
          f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')
          await loadExistingReceiptData(m)
          bumpFormVersion()
          const hawbData = hawbRes.data
          if (hawbData.length > 0) {
            const h0 = hawbData[0]
            if (!f.shipperName) f.shipperName = m.shipperName || h0?.shipperName || ''
            if (!f.consigneeName) f.consigneeName = m.consigneeName || (hawbData.length === 1 ? h0?.consigneeName : '') || ''
            if (f.awbReportedPieces == null) f.awbReportedPieces = m.pieces || hawbData.reduce((s, h) => s + (h.pieces || 0), 0) || 0
            const existingIds = new Set(f.hawbEntries.filter(e => e._dbId).map(e => e._dbId))
            for (const h of hawbData) {
              if (!existingIds.has(h.id)) {
                f.hawbEntries.push({
                  hawbNumber: h.hawbNumber || '',
                  consigneeName: h.consigneeName || '',
                  pieces: h.pieces || 0,
                  weightKg: h.weightKg ? Number(h.weightKg) : 0,
                  destination: h.destination || f.destination || 'MIA',
                  _dbId: h.id,
                  _hawbId: h.id,
                })
              }
            }
            f.hawbEntries = f.hawbEntries.filter(e => e._dbId != null || f.hawbEntries.filter(e2 => e2._dbId != null).length === 0)
            f.hawbCount = f.hawbEntries.length
            if (!f._piecesLoadedFromDb) {
              const validHawbIds = new Set(f.hawbEntries.map(e => e._hawbId).filter(Boolean))
              f.pieces = f.pieces.filter(p => validHawbIds.has(p.hawbId) || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
              for (const p of f.pieces) {
                if (!validHawbIds.has(p.hawbId)) {
                  p.hawbId = f.hawbEntries[0]?._hawbId || null
                }
              }
            }
          }
        }
      } catch (e) {
        toast.error(extractError(e))
        receiptHawbs[m.id] = []
      }
    }
  } else if (expandedId.value && store.mawbs.length) {
    const m = store.mawbs.find(x => x.id === expandedId.value)
    if (m) {
      initForm(m)
      try {
        const [hawbRes, docsRes] = await Promise.all([
          hawbsApi.getByMawb(m.id),
          mawbsApi.getSupportingDocs(m.id).catch(() => ({ data: [] })),
        ])
        receiptHawbs[m.id] = hawbRes.data
        const f = receiptForms[m.id]
        if (f) {
          f.mawbEvidence = (docsRes.data || []).filter(d => d.type === 'image' || d.type === 'document')
          await loadExistingReceiptData(m)
          bumpFormVersion()
          const hawbData = hawbRes.data
          if (hawbData.length > 0) {
            const h0 = hawbData[0]
            if (!f.shipperName) f.shipperName = m.shipperName || h0?.shipperName || ''
            if (!f.consigneeName) f.consigneeName = m.consigneeName || (hawbData.length === 1 ? h0?.consigneeName : '') || ''
            if (f.awbReportedPieces == null) f.awbReportedPieces = m.pieces || hawbData.reduce((s, h) => s + (h.pieces || 0), 0) || 0
            const existingIds = new Set(f.hawbEntries.filter(e => e._dbId).map(e => e._dbId))
            for (const h of hawbData) {
              if (!existingIds.has(h.id)) {
                f.hawbEntries.push({
                  hawbNumber: h.hawbNumber || '',
                  consigneeName: h.consigneeName || '',
                  pieces: h.pieces || 0,
                  weightKg: h.weightKg ? Number(h.weightKg) : 0,
                  destination: h.destination || f.destination || 'MIA',
                  _dbId: h.id,
                  _hawbId: h.id,
                })
              }
            }
            f.hawbEntries = f.hawbEntries.filter(e => e._dbId != null || f.hawbEntries.filter(e2 => e2._dbId != null).length === 0)
            f.hawbCount = f.hawbEntries.length
            if (!f._piecesLoadedFromDb) {
              const validHawbIds = new Set(f.hawbEntries.map(e => e._hawbId).filter(Boolean))
              f.pieces = f.pieces.filter(p => validHawbIds.has(p.hawbId) || p.lengthIn || p.widthIn || p.heightIn || p.scaleWeightLbs)
              for (const p of f.pieces) {
                if (!validHawbIds.has(p.hawbId)) {
                  p.hawbId = f.hawbEntries[0]?._hawbId || null
                }
              }
            }
          }
        }
      } catch (e) {
        toast.error(extractError(e))
        receiptHawbs[m.id] = []
      }
    }
  }
})

watch(expandedId, (id) => {
  if (id) localStorage.setItem('WAREHOUSE_EXPANDED_MAWB', id)
  else localStorage.removeItem('WAREHOUSE_EXPANDED_MAWB')
})

watch(() => store.mawbs, (mawbs) => {
  if (expandedId.value) {
    const m = mawbs.find(x => x.id === expandedId.value)
    if (m) {
      initForm(m)
    }
  }
})

function onDocumentClick(e) {
  if (!headerFilterOpen.value) return
  const headerRow = document.querySelector('.bg-slate-950.border-b.border-slate-700')
  if (headerRow && !headerRow.contains(e.target)) {
    headerFilterOpen.value = null
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))
</script>

<style scoped>
.thin-scrollbar::-webkit-scrollbar { width: 4px; }
.thin-scrollbar::-webkit-scrollbar-track { background: transparent; }
.thin-scrollbar::-webkit-scrollbar-thumb { background: #94a3b8; border-radius: 2px; }
.thin-scrollbar::-webkit-scrollbar-thumb:hover { background: #64748b; }
.thin-scrollbar { scrollbar-width: thin; scrollbar-color: #94a3b8 transparent; }
.overscroll-contain { overscroll-behavior: contain; }
</style>
