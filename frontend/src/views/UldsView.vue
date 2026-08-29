<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div class="flex items-center gap-4">
        <div>
          <h1 class="ds-title">{{ t('ulds.title') }}</h1>
          <p class="ds-subtitle">{{ t('ulds.subtitle') }}</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <span v-if="pendingReceiptCount > 0"
          class="text-[14px] font-mono font-bold text-slate-600 bg-slate-50 border border-slate-200 px-2 py-1 rounded">
          &#9888; {{ t('ulds.pendingReceipts', { n: pendingReceiptCount }) }}
        </span>
        <FilterBar
          v-model:status="statusFilter"
          v-model:search-text="searchText"
          v-model:destination="destFilter"
          :show-status="true"
          :show-search="true"
          :show-destination="true"
          :status-options="uldStatusOptions"
          :search-placeholder="t('ulds.searchPlaceholder')"
          container-class="!gap-1"
        />
        <button @click="createNewBlankUld"
          class="ds-btn-primary">
          <span class="text-[12px] font-sans">&#65291;</span> {{ t('ulds.createUld') }}
        </button>
      </div>
    </header>

    <section class="ds-table-section mb-1.5">
      <div v-if="appStore.loading && !localUlds.length" class="flex-1 flex items-center justify-center">
        <span class="text-[14px] font-mono text-slate-400 animate-pulse">{{ t('ulds.loading') }}</span>
      </div>

      <div v-else-if="filteredUlDs.length === 0" class="flex-1 flex items-center justify-center">
        <p class="text-[14px] font-mono text-slate-400 uppercase tracking-widest">{{ t('ulds.emptyCreate') }}</p>
      </div>

      <template v-else>
        <!-- Single row per ULD -->
        <div class="flex flex-col gap-px p-2 overflow-y-auto max-h-[120px] shrink-0 scrollbar-none">
          <div v-for="uld in filteredUlDs" :key="uld.uid"
            @click="toggleUldExpansion(uld.uid)"
            class="flex items-center gap-3 rounded border cursor-pointer transition-all px-3 py-2 select-none"
            :class="[expandedUldId === uld.uid
              ? 'border-slate-950 ring-1 ring-slate-950 row-selected'
              : 'border-slate-200 hover:border-slate-400 bg-white hover:shadow-sm',
              uld._isFirstDated ? 'border-t-2 border-t-slate-950 mt-1' : '']"
            :style="uldStatusBorderStyle(uld.status)">

            <span class="text-[13px] font-black text-slate-950 font-mono truncate min-w-[100px] leading-tight flex items-center gap-1.5">
              {{ uld.uldNumber || t('ulds.newUld') }}
              <span v-if="uldAgeInDays(uld.createdAt) !== null"
                class="text-[10px] font-bold px-1 py-px rounded leading-none"
                :class="uldAgeBadgeClass(uldAgeInDays(uld.createdAt))">
                {{ uldAgeInDays(uld.createdAt) }}d
              </span>
            </span>
            <span class="text-[10px] font-black px-1 py-px rounded uppercase whitespace-nowrap leading-none shrink-0"
              :class="statusBadgeClass(uld.status)">{{ t('ulds.status.' + uld.status) }}</span>

            <span class="text-[13px] font-mono text-slate-400 font-semibold truncate leading-tight shrink-0 min-w-[80px]">
              {{ flightLabel(uld) }}
            </span>

            <span class="text-[13px] font-mono text-slate-500 truncate leading-tight shrink-0 min-w-[60px]">
              {{ uld.route ? uld.route.replace(' -> ', '&#8594;') : '---' }}
            </span>

            <span class="text-[13px] font-mono font-bold text-slate-950 leading-tight shrink-0 min-w-[70px]">
              {{ Number(uld.grossWeightLbs || 0).toLocaleString() }} lb
            </span>

            <span class="text-[13px] font-mono text-slate-500 leading-tight shrink-0 min-w-[60px]">
              {{ t('ulds.mawbCount', (uld.mawbs || []).length) }}
            </span>

            <div class="flex items-center gap-1 ml-auto min-w-[80px]">
              <div class="flex-1 h-[2px] bg-slate-100 rounded-full overflow-hidden">
                <div class="h-full rounded-full transition-all duration-300"
                  :class="uld.volumePct >= 90 ? 'bg-slate-600' : 'bg-slate-950'"
                  :style="{ width: uld.volumePct + '%' }"></div>
              </div>
              <span class="text-[13px] font-mono font-bold text-slate-400 leading-none">{{ uld.volumePct }}%</span>
            </div>
          </div>
        </div>

        <!-- Expanded form area -->
        <div class="border-t border-slate-200 flex-1 overflow-y-auto scrollbar-none bg-slate-50">
          <div v-for="uld in filteredUlDs" :key="'f-'+uld.uid">
            <div v-show="expandedUldId === uld.uid" class="p-4">
              <div class="bg-white border border-slate-300 rounded shadow-sm max-w-5xl mx-auto p-3 md:p-6 font-mono text-sm relative">
                  <div class="flex justify-between items-center border-b border-slate-300 pb-3 mb-5">
                    <div class="flex items-center gap-2">
                      <span class="text-[13px] font-black text-slate-950 uppercase tracking-wider">{{ t('ulds.palletSheetHeader') }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-[13px] font-bold text-slate-400 uppercase">Volumen:</span>
                    <input v-model.number="uld.volumePct" type="number" min="0" max="100" inputmode="decimal" class="w-20 text-center bg-slate-100 border border-slate-300 rounded font-bold text-slate-950 focus:outline-none text-[14px] pct-input" />
                    <span class="text-[14px] font-bold text-slate-950">%</span>
                  </div>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-6">
                  <div>
                    <label class="ds-label block mb-1 flex items-center gap-1.5">
                      {{ t('ulds.form.uldCode') }} *
                      <span v-if="creationStep === 2" class="text-[10px] bg-emerald-100 text-emerald-700 px-1.5 py-px rounded font-black">{{ t('ulds.scanOrType') }}</span>
                    </label>
                    <input v-model="uld.uldNumber" type="text" placeholder="PMC-XXXXX"
                      class="ds-input uppercase transition-all duration-300"
                      :class="creationStep === 2 ? 'ring-2 ring-emerald-400 bg-emerald-50 border-emerald-400' : ''" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.configType') }}</label>
                    <select v-model="uld.uldType" class="ds-input">
                      <option v-for="ut in uldTypes" :key="ut" :value="ut">{{ ut }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.position') }}</label>
                    <input v-model="uld.position" type="text" placeholder="1L" class="ds-input uppercase" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.sealNumber') }}</label>
                    <input v-model="uld.sealNumber" type="text" placeholder="SC-XXXXXXXX" class="ds-input font-bold" />
                  </div>
                </div>

                <!-- SCAN PANEL -->
                <ScanPanel
                  :active="scanMode"
                  :uld-id="uld.backendId || ''"
                  :uld-number="uld.uldNumber || t('ulds.newShort')"
                  @piece-added="onScanPieceAdded"
                  @piece-removed="onScanPieceRemoved"
                  @exit-scan="scanMode = false"
                  @uld-number-scanned="onUldNumberScanned"
                />

                <!-- MAWB TABLE -->
                <div class="border border-slate-200 rounded overflow-hidden mb-6">
                  <div class="overflow-x-auto">
                  <div class="table-scroll-wrapper">
                  <div class="ds-table-header grid grid-cols-13 py-3 px-5 items-center gap-2" style="min-width: 750px">
                    <div class="col-span-3">MAWB</div>
                    <div class="col-span-1">{{ t('ulds.table.status') }}</div>
                    <div class="col-span-2">{{ t('ulds.table.description') }}</div>
                    <div class="col-span-1 text-right">{{ t('ulds.table.pcsAssigned') }}</div>
                    <div class="col-span-1 text-right">{{ t('ulds.table.pcsReceived') }}</div>
                    <div class="col-span-1 text-center">%</div>
                    <div class="col-span-1 text-right">{{ t('ulds.table.dest') }}</div>
                    <div class="col-span-2 text-center">{{ t('ulds.table.receipt') }}</div>
                    <div class="col-span-1"></div>
                  </div>
                  <div class="divide-y divide-slate-100 max-h-[240px] overflow-y-auto scrollbar-none">
                    <div v-for="(mawb, mIdx) in uld.mawbs" :key="mawb._rowId" class="ds-table-row grid grid-cols-13 gap-2 text-sm">
                      <div class="col-span-3 relative">
                        <input v-model="mawb.awbNumber" @input="onMawbInput(uld, mIdx)" @focus="onMawbInput(uld, mIdx)" @blur="onMawbBlur(uld, mIdx)"
                          :placeholder="t('ulds.writeMawb')"
                          class="w-full border-b border-slate-200 focus:outline-none focus:border-slate-950 py-1 bg-transparent font-bold tracking-tight text-slate-950 text-[13px]" />
                        <div v-if="mawb._showSuggestions && mawb._suggestions.length"
                          class="absolute top-full left-0 right-0 z-50 bg-white border border-slate-300 rounded shadow-lg max-h-[160px] overflow-y-auto">
                          <div v-for="s in mawb._suggestions" :key="s.id"
                            @mousedown.prevent="selectMawbSuggestion(uld, mIdx, s)"
                            class="px-2 py-1.5 text-[13px] font-mono cursor-pointer hover:bg-slate-50 border-b border-slate-100 last:border-0"
                            :class="s.availablePieces > 0 ? 'text-slate-950' : 'text-slate-300'">
                            <span class="w-2 h-2 rounded-full inline-block mr-1" :class="mawbStatusDotClass(s.awbNumber)"></span>
                            <span class="font-bold">{{ s.awbNumber }}</span>
                            <span class="text-slate-400 ml-1">— {{ s.shipperName || s.consigneeName || '' }}</span>
                            <span class="text-slate-400 text-sm ml-1">[{{ s.commodityType }}]</span>
                            <span v-if="s.availablePieces > 0" class="text-slate-600 ml-1">{{ t('ulds.availablePiecesShort', { n: s.availablePieces }) }}</span>
                            <span v-else class="text-slate-400 ml-1">{{ t('ulds.noPieces') }}</span>
                          </div>
                        </div>
                        <div v-if="mawb.awbNumber && !mawb._isSpecial && !mawbInBookings(mawb.awbNumber)"
                          class="mt-0.5 text-[10px] text-amber-600 font-mono flex items-center gap-0.5">
                          &#9888; {{ t('ulds.notInBookings') }}
                        </div>
                      </div>
                      <div class="col-span-1 flex items-center">
                        <div v-if="mawb.awbNumber && !mawb._isSpecial" class="flex items-center gap-1">
                          <span class="w-2 h-2 rounded-full shrink-0" :class="mawbStatusDotClass(mawb.awbNumber)"></span>
                          <span class="text-[10px] font-mono font-bold uppercase leading-none" :class="mawbStatusTextClass(mawb.awbNumber)">{{ mawbStatusLabel(mawb.awbNumber) }}</span>
                        </div>
                        <span v-else class="text-[10px] text-slate-300">—</span>
                      </div>
                      <div class="col-span-2">
                        <input v-model="mawb.commodityType" type="text" :placeholder="mawb.commodityHint || t('ulds.dryCargoHint')"
                          class="w-full border-b border-slate-200 focus:outline-none focus:border-slate-950 py-1 bg-transparent font-medium text-slate-950 text-[13px]" />
                      </div>
                      <div class="col-span-1 flex items-center gap-1">
                        <input v-model.number="mawb.pieces" type="number" min="0"
                          class="w-full border-b border-slate-200 focus:border-slate-950 py-1 text-right bg-transparent font-bold text-[13px]" />
                      </div>
                      <div class="col-span-1 text-right font-mono text-[13px] flex items-center justify-end gap-1"
                        :class="mawb.receivedPieces != null ? 'text-slate-600' : 'text-slate-400'">
                        <template v-if="mawb.receivedPieces != null">{{ mawb.receivedPieces }}</template>
                        <span v-else>&mdash;</span>
                      </div>
                      <div class="col-span-1 text-center flex items-center justify-center gap-1">
                        <input v-model.number="mawb.piecesPct" type="number" min="0" max="100" inputmode="decimal"
                          class="w-14 border-b border-slate-200 focus:outline-none focus:border-slate-950 py-1 text-center bg-transparent font-bold text-slate-600 text-[13px] pct-input" />
                        <span class="text-[13px] text-slate-400">%</span>
                      </div>
                      <div class="col-span-1 text-right">
                        <input v-model="mawb.destination" type="text" maxlength="3"
                          class="w-full border-b border-slate-200 focus:outline-none focus:border-slate-950 py-1 text-right bg-transparent uppercase font-bold text-slate-950 text-[13px]" />
                      </div>
                      <div class="col-span-2 flex justify-center items-center gap-1 text-[13px] font-mono">
                        <span v-if="mawb.hasReceipt"
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-bold text-slate-600 bg-slate-100 border border-slate-200">
                          {{ t('ulds.receivedBadge') }}
                        </span>
                        <span v-else
                          class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-bold text-slate-500 bg-slate-100 border border-slate-200 cursor-help"
                          :title="t('ulds.noReceiptTooltip')">
                          &#9888; {{ t('ulds.pendingShort') }}
                        </span>
                      </div>
                      <div class="col-span-1 text-center">
                        <button @click="removeMawbRow(uld, mIdx)" class="text-slate-400 hover:text-slate-600 text-sm">&#10005;</button>
                      </div>
                    </div>
                  </div>
                  </div>
                  </div>
                  <div class="p-2 bg-slate-50 border-t border-slate-100 flex justify-between items-center text-[13px] text-slate-500 flex-wrap gap-1">
                    <button @click="addMawbRow(uld)"
                      class="py-1.5 px-3 border border-dashed border-slate-300 rounded text-center hover:text-slate-950 transition-colors font-bold text-[13px] uppercase">
                      + MAWB
                    </button>
                    <div class="flex items-center gap-2">
                      <span class="font-mono">{{ t('ulds.pcsProgress', { assigned: totalUldPieces(uld), received: totalUldReceivedPieces(uld) }) }}</span>
                    </div>
                  </div>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-6">
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.tare.label') }} <span class="text-slate-950">(lbs)</span></label>
                    <div class="relative">
                      <input v-model.number="uld.tareLbs" type="number" step="0.1"
                        class="ds-input" />
                    </div>
                    <div v-if="suggestedTareLbs" class="mt-1 text-[11px] text-slate-400 font-mono">
                      {{ t('ulds.tare.suggested', { tare: suggestedTareLbs }) }}
                      <button @click="uld.tareLbs = suggestedTareLbs"
                        class="text-slate-500 hover:text-slate-700 underline ml-1">{{ t('ulds.tare.use') }}</button>
                    </div>
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.grossLbs') }}</label>
                    <input v-model.number="uld.grossWeightLbs" type="number" class="ds-input" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.status') }}</label>
                      <select v-model="uld.status" class="ds-input font-bold">
                        <option value="OPEN">OPEN ({{ t('ulds.status.OPEN') }})</option>
                        <option value="BUILT">BUILT ({{ t('ulds.status.BUILT') }})</option>
                        <option value="SEALED">SEALED ({{ t('ulds.status.SEALED') }})</option>
                        <option value="LOADED">LOADED ({{ t('ulds.status.LOADED') }})</option>
                        <option value="LEFT_BEHIND">LEFT BEHIND ({{ t('ulds.status.LEFT_BEHIND') }})</option>
                      </select>
                  </div>
                  <div class="bg-slate-50 flex flex-col justify-center rounded px-3 py-2 border border-slate-200">
                    <span class="text-sm font-black text-slate-600 uppercase tracking-wider">{{ t('ulds.netWeight') }}</span>
                    <span class="text-sm font-black text-slate-800">{{ ((uld.grossWeightLbs || 0) - (uld.tareLbs || 0)).toLocaleString() }} lbs</span>
                  </div>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-4 gap-4 border-t border-slate-200 pt-5">
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.destination') }}</label>
                    <input v-model="uld.destination" type="text" :placeholder="t('ulds.form.destinationPlaceholder')" class="ds-input uppercase" />
                  </div>
<div>
                      <label class="ds-label block mb-1">{{ t('ulds.form.builtBy') }}</label>
                      <input v-model="uld.builtBy" type="text" :placeholder="t('ulds.form.builtByPlaceholder')" class="ds-input font-bold" />
                    </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.confirmedWith') }}</label>
                    <input v-model="uld.confirmedWith" type="text" :placeholder="t('ulds.form.confirmedWithPlaceholder')" class="ds-input" />
                  </div>
                  <div>
                    <label class="ds-label block mb-1">{{ t('ulds.form.notes') }}</label>
                    <input v-model="uld.notes" type="text" :placeholder="t('ulds.form.notesPlaceholder')" class="ds-input" />
                  </div>
                </div>


                <!-- Step guide for new ULD creation -->
                <div v-if="!uld.backendId && creationStep > 0" class="flex items-center gap-3 mb-4 px-3 py-2 rounded-lg"
                  :class="creationStep === 1 ? 'bg-amber-50 ring-2 ring-amber-300' : creationStep === 2 ? 'bg-emerald-50 ring-2 ring-emerald-300' : 'bg-slate-50'">
                  <div class="flex items-center gap-1.5 text-[11px] font-black uppercase tracking-wider"
                    :class="creationStep >= 1 ? 'text-amber-700' : 'text-slate-300'">
                    <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black"
                      :class="creationStep > 1 ? 'bg-emerald-500 text-white' : creationStep === 1 ? 'bg-amber-400 text-white' : 'bg-slate-200'">1</span>
                    {{ t('ulds.form.flight') }}
                    <span v-if="creationStep > 1" class="text-emerald-600 ml-1">✓</span>
                  </div>
                  <span class="text-slate-300 text-[10px]">▸</span>
                  <div class="flex items-center gap-1.5 text-[11px] font-black uppercase tracking-wider"
                    :class="creationStep >= 2 ? 'text-emerald-700' : 'text-slate-300'">
                    <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black"
                      :class="creationStep > 2 ? 'bg-emerald-500 text-white' : creationStep === 2 ? 'bg-emerald-400 text-white' : 'bg-slate-200'">2</span>
                    {{ t('ulds.steps.scanUld') }}
                    <span v-if="creationStep > 2" class="text-emerald-600 ml-1">✓</span>
                  </div>
                  <span class="text-slate-300 text-[10px]">▸</span>
                  <div class="flex items-center gap-1.5 text-[11px] font-black uppercase tracking-wider"
                    :class="creationStep >= 3 ? 'text-slate-900' : 'text-slate-300'">
                    <span class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black"
                      :class="creationStep >= 3 ? 'bg-slate-900 text-white' : 'bg-slate-200'">3</span>
                    {{ t('ulds.steps.registerPieces') }}
                  </div>
                </div>

                <div class="border-t border-slate-200 pt-5 flex flex-wrap justify-end gap-2 bg-slate-50/50 -mx-2 md:-mx-6 -mb-6 p-3 md:p-6 rounded-b">
                  <div class="flex items-center gap-4 mr-auto">
                    <div class="flex flex-col">
                      <span class="text-[12px] font-black uppercase tracking-widest flex items-center gap-1.5"
                        :class="creationStep === 1 ? 'text-amber-700' : 'text-slate-400'">
                        {{ t('ulds.form.flight') }}
                        <span v-if="creationStep === 1" class="text-[10px] bg-amber-200 text-amber-800 px-1.5 py-px rounded">{{ t('ulds.step', { n: 1 }) }}</span>
                      </span>
                      <select v-model="uld.saveFlightId"
                        class="ds-input uppercase text-[12px] min-w-[160px] transition-all duration-300"
                        :class="creationStep === 1 ? 'ring-2 ring-amber-400 bg-amber-50 border-amber-400' : ''">
                        <option value="" disabled>{{ t('common.selectFlight') }}</option>
                        <option v-for="f in appStore.flights" :key="f.id" :value="f.id">
                          {{ airlineCodeById(f.airlineId) }}-{{ f.flightNumber }} ({{ f.origin }}&#8594;{{ f.destination }}) {{ f.flightDate }}
                        </option>
                      </select>
                    </div>
                    <div class="flex flex-col">
                      <span class="text-[12px] font-black text-slate-400 uppercase tracking-widest">{{ t('ulds.created') }}</span>
                      <span class="text-[14px] font-bold text-slate-950">{{ uld.createdAt ? formatDate(uld.createdAt) : '—' }}</span>
                    </div>
                  </div>
                  <div class="flex items-center gap-2">
                    <button @click="toggleScanMode(uld)"
                      class="font-mono font-black uppercase text-[12px] tracking-widest px-4 py-2.5 rounded shadow-md transition-all flex items-center gap-2"
                      :class="creationStep === 2 ? 'bg-emerald-600 hover:bg-emerald-700 text-white ring-2 ring-emerald-300 animate-pulse' : scanMode ? 'bg-emerald-600 hover:bg-emerald-700 text-white ring-2 ring-emerald-300' : 'bg-blue-600 hover:bg-blue-700 text-white'">
                      <template v-if="creationStep === 2">{{ t('ulds.scanNow') }}</template>
                      <template v-else-if="scanMode">{{ t('ulds.scanning') }}</template>
                      <template v-else>📷 {{ t('ulds.scanMode') }}</template>
                    </button>
                    <button @click="deleteUld(uld)"
                      class="ds-btn-secondary text-slate-400 hover:text-slate-700"
                      :title="uld.backendId ? t('ulds.actions.delete') : t('ulds.actions.discard')">
                      &#10005;
                    </button>
                    <button v-if="uld.backendId" @click="dismountUld(uld)"
                      class="ds-btn-secondary">
                      {{ t('ulds.actions.demount') }}
                    </button>
                    <button v-if="uld.backendId" @click="printPalletLabel(uld)"
                      class="ds-btn-secondary" :title="t('ulds.printPalletTooltip')">
                      &#9642; {{ t('ulds.actions.palletLabel') }}
                    </button>
                    <button @click="saveUld(uld)"
                      class="ds-btn-primary">
                      {{ uld.backendId ? t('ulds.actions.update') : t('ulds.sendToLoadPlanning') }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="!expandedUldId" class="flex items-center justify-center h-full">
            <p class="text-[13px] font-mono text-slate-300 uppercase tracking-widest">{{ t('ulds.selectToEdit') }}</p>
          </div>
        </div>
      </template>
    </section>

    <LabelPrintModal v-if="showLabels" type="PALLET" :items="labelIds" @close="showLabels = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useUldsStore } from '../stores/ulds'
import { useAppStore } from '../stores/app'
import { uldsApi } from '../api/ulds'
import { uldAwbsApi } from '../api/uldAwbs'
import { uldTypeConfigApi } from '../api/uldTypeConfig'
import { uldTypeCatalogApi } from '../api/uldTypeCatalog'

import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'
import { useI18n } from 'vue-i18n'
import ScanPanel from '../components/ScanPanel.vue'
import LabelPrintModal from '../components/labels/LabelPrintModal.vue'
import { useCommodities } from '../composables/useCommodities'
import { useConfirm } from '../composables/useConfirm'
import FilterBar from '../components/FilterBar.vue'

const uldsStore = useUldsStore()
const appStore = useAppStore()
const toast = useToastStore()
const { t, te } = useI18n()
const { confirm } = useConfirm()
const { commodities: dbCommodities, loadCommodities } = useCommodities()

const showLabels = ref(false)
const labelIds = ref([])
function printPalletLabel(uld) {
  if (!uld.backendId) { toast.warning(t('ulds.toast.saveBeforePrint')); return }
  labelIds.value = [uld.backendId]
  showLabels.value = true
}

function airlineCodeById(airlineId) {
  const a = appStore.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

// Tipos ULD desde el catálogo dinámico (normas IATA); fallback a lista legacy
const LEGACY_ULD_TYPES = ['PMC','PAH','PAG','PAJ','AAY','AAZ','AAD','PIP','BULK','AMP','AMJ']
const uldTypes = ref([...LEGACY_ULD_TYPES])

async function loadUldCatalog() {
  try {
    const res = await uldTypeCatalogApi.getAll(true)
    const codes = (res.data || []).map(x => x.code)
    if (codes.length) uldTypes.value = codes
  } catch {
    // se conserva el fallback legacy
  }
}

const specialItems = [
  { id: 'spc-sdq-sdf', awbNumber: 'SDQ/SDF', shipperName: 'Ruta Doméstica SDQ→SDF', consigneeName: 'Ruta Doméstica SDQ→SDF', commodityType: 'SDQ_SDF', pieces: 0, destination: 'SDF', isSpecial: true },
  { id: 'spc-sdq-mia', awbNumber: 'SDQ/MIA', shipperName: 'Ruta Doméstica SDQ→MIA', consigneeName: 'Ruta Doméstica SDQ→MIA', commodityType: 'SDQ_MIA', pieces: 0, destination: 'MIA', isSpecial: true },
  { id: 'spc-wwef', awbNumber: 'WWEF', shipperName: 'Worldwide Express Freight', consigneeName: 'WWEF', commodityType: 'WWEF', pieces: 0, destination: 'MIA', isSpecial: true },
  { id: 'spc-fcc', awbNumber: 'FCC', shipperName: 'Full Container Load', consigneeName: 'FCC Equipment', commodityType: 'FCC', pieces: 0, destination: '', isSpecial: true },
  { id: 'spc-empty-uld', awbNumber: 'EMPTY ULD', shipperName: 'Empty ULD', consigneeName: 'Empty ULD Equipment', commodityType: 'EMPTY_ULD', pieces: 0, destination: '', isSpecial: true },
  { id: 'spc-empty-bags', awbNumber: 'EMPTY BAGS', shipperName: 'Empty Bags', consigneeName: 'Empty Bags Equipment', commodityType: 'EMPTY_BAGS', pieces: 0, destination: '', isSpecial: true },
  { id: 'spc-nets', awbNumber: 'NETS', shipperName: 'Cargo Nets', consigneeName: 'Cargo Nets Equipment', commodityType: 'NETS', pieces: 0, destination: '', isSpecial: true },
]

const VALID_COMMODITIES = computed(() => {
  const set = new Set(['DRY_CARGO'])
  for (const c of dbCommodities.value) set.add(c.code)
  return set
})

function normalizeCommodity(val) {
  const v = String(val || '').trim().toUpperCase()
  return VALID_COMMODITIES.value.has(v) ? v : 'GENERAL'
}

const expandedUldId = ref(null)
const statusFilter = ref('')
const searchText = ref('')
const destFilter = ref('')

const uldStatusOptions = computed(() => [
  { value: 'OPEN', label: t('ulds.status.OPEN'), dotClass: 'bg-slate-400' },
  { value: 'BUILT', label: t('ulds.status.BUILT'), dotClass: 'bg-blue-500' },
  { value: 'SEALED', label: t('ulds.status.SEALED'), dotClass: 'bg-amber-500' },
  { value: 'LOADED', label: t('ulds.status.LOADED'), dotClass: 'bg-emerald-600' },
  { value: 'OFFLOADED', label: t('ulds.status.OFFLOADED'), dotClass: 'bg-red-500' },
])

const TARE_MAP = {
  AAY: 460, AAD: 540, AAZ: 500, AMP: 600, AMJ: 610,
  PMC: 270, PAG: 250, PAH: 300, PIP: 250,
}

// ULD type config from backend (per airline), editable by ADMIN/SUPER_USER
const typeConfigs = ref([])

function currentAirlineId() {
  return appStore.selectedFlight?.airlineId || appStore.airlines[0]?.id || null
}

async function loadTypeConfig() {
  const airlineId = currentAirlineId()
  if (!airlineId) { typeConfigs.value = []; return }
  try {
    const res = await uldTypeConfigApi.getByAirline(airlineId)
    typeConfigs.value = res.data || []
  } catch {
    typeConfigs.value = []
  }
}

function defaultTareFor(uldType) {
  const type = (uldType || '').toUpperCase()
  const cfg = typeConfigs.value.find(c => String(c.uldType).toUpperCase() === type)
  if (cfg && cfg.defaultTareLbs != null && Number(cfg.defaultTareLbs) > 0) return Number(cfg.defaultTareLbs)
  return TARE_MAP[type] ?? 0
}

const suggestedTareLbs = computed(() => {
  const expanded = localUlds.value.find(u => u.uid === expandedUldId.value)
  if (!expanded || !expanded.uldType) return null
  return defaultTareFor(expanded.uldType) || TARE_MAP[expanded.uldType.toUpperCase()] || null
})

const filteredUlDs = computed(() => {
  let list = localUlds.value
  if (statusFilter.value) {
    list = list.filter(u => u.status === statusFilter.value)
  }
  if (destFilter.value) {
    const d = destFilter.value.toUpperCase()
    list = list.filter(u => {
      const mawbs = u.mawbs || []
      return mawbs.some(m => (m.destination || '').toUpperCase() === d) || (u.route || '').toUpperCase().includes(d)
    })
  }
  const q = searchText.value.trim().toLowerCase()
  if (q) {
    list = list.filter(u => {
      const haystack = [
        u.uldNumber, u.status, u.route,
        ...(u.mawbs || []).map(m => m.awbNumber),
      ].filter(Boolean).join(' ').toLowerCase()
      return haystack.includes(q)
    })
  }
  return list
})

function formatDate(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleDateString('es-DO', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// Local ULD list derived from backend + new unsaved
const localUlds = ref([])

// Scan mode state
const scanMode = ref(false)
const scanUldUid = ref(null)

// Guided creation steps: 0=idle, 1=select flight, 2=scan ULD, 3=done
const creationStep = ref(0)

function toggleScanMode(uld) {
  scanMode.value = !scanMode.value
  if (scanMode.value && uld) {
    scanUldUid.value = uld.uid
  } else {
    scanUldUid.value = null
  }
}

// Auto-advance: after flight selected → step 2 → activate scan mode
watch(expandedUldId, () => { creationStep.value = 0 })

watch(() => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  return uld?.saveFlightId
}, (flightId) => {
  if (flightId && creationStep.value === 1) {
    creationStep.value = 2
    const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
    if (uld && !uld.backendId) {
      scanUldUid.value = uld.uid
      scanMode.value = true
    }
  }
})

// After ULD number is set → step 3 done
watch(() => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  return uld?.uldNumber
}, (num) => {
  if (num && creationStep.value === 2) {
    creationStep.value = 3
  }
})

// Auto-apply suggested tare when ULD type changes on an unsaved ULD
watch(() => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  return uld?.uldType
}, (type, oldType) => {
  const uld = localUlds.value.find(u => u.uid === expandedUldId.value)
  if (!uld || uld.backendId) return
  const prevAuto = oldType ? defaultTareFor(oldType) : null
  const isAutoValue = uld.tareLbs == null || uld.tareLbs === 0 || (prevAuto != null && uld.tareLbs === prevAuto)
  if (isAutoValue) {
    uld.tareLbs = defaultTareFor(type)
  }
})

// Reload ULD type config when the selected flight/airline changes
watch(() => appStore.selectedFlight?.airlineId, () => loadTypeConfig())

function onScanPieceAdded(result) {
  const uid = scanUldUid.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return

  let mawbRow = uld.mawbs.find(m => m.awbNumber === result.awbNumber)
  if (!mawbRow) {
    const mawbData = appStore.mawbs.find(m => m.awbNumber === result.awbNumber)
    mawbRow = {
      _rowId: Math.random().toString(36).slice(2),
      awbNumber: result.awbNumber,
      commodityType: normalizeCommodity(mawbData?.commodityType),
      commodityHint: mawbData?.commodityType || '',
      pieces: 0,
      piecesPct: 0,
      destination: mawbData?.destination || 'MIA',
      mawbId: mawbData?.id || null,
      hasReceipt: false,
      receivedPieces: 0,
      reservedPieces: 0,
      availablePieces: 0,
      _showSuggestions: false,
      _suggestions: [],
    }
    uld.mawbs.push(mawbRow)
  }

  mawbRow.pieces = result.totalOnUld
}

function onScanPieceRemoved(data) {
  const uid = scanUldUid.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return
  const mawbRow = uld.mawbs.find(m => m.awbNumber === data.awbNumber)
  if (mawbRow && mawbRow.pieces > 0) {
    mawbRow.pieces--
  }
}

async function onUldNumberScanned(code) {
  const uid = scanUldUid.value
  if (!uid) return
  const uld = localUlds.value.find(u => u.uid === uid)
  if (!uld) return
  const upper = code.toUpperCase()
  uld.uldNumber = upper
  const prefix = upper.slice(0, 3)
  if (uldTypes.value.includes(prefix)) {
    uld.uldType = prefix
  }

  // Auto-save ULD so backendId is available for MAWB piece registration
  const flightId = uld.saveFlightId || appStore.selectedFlight?.id
  if (!uld.backendId && flightId) {
    try {
      const flight = appStore.flights.find(f => f.id === flightId)
      uld.airlineId = uld.airlineId || flight?.airlineId || appStore.selectedFlight?.airlineId
      uld.flightId = flightId
      const result = await uldsStore.dispatchUld(uld, flightId)
      uld.backendId = result?.id
      await appStore.loadUlds()
    } catch (e) {
      console.warn('[ULD Scan] Auto-save failed:', e)
    }
  }
}

// MAWB availability computation
const availableMawbs = computed(() => {
  const mawbsWithAvailability = (appStore.mawbs || []).map(m => {
    const receipt = (appStore.receipts || []).find(r => (r.mawb && r.mawb.id === m.id) || r.mawbId === m.id)
    const reserved = m.pieces || 0
    const assignedInUlDs = localUlds.value.flatMap(u =>
      (u.mawbs || []).filter(mw => mw.awbNumber === m.awbNumber)
    ).reduce((s, mw) => s + (mw.pieces || 0), 0)
    const receiptPieces = receipt ? (receipt.pieceCount || receipt.receivedPieces || 0) : 0
    const available = Math.max(0, (reserved > 0 ? reserved : receiptPieces) - assignedInUlDs)
    return { ...m, availablePieces: available }
  })
  return [...specialItems, ...mawbsWithAvailability]
})

function onMawbInput(uld, mIdx) {
  const mawb = uld.mawbs[mIdx]
  const q = (mawb.awbNumber || '').toUpperCase().trim()
  if (!q) {
    mawb._showSuggestions = false
    mawb._suggestions = []
    return
  }
  mawb._suggestions = availableMawbs.value.filter(m => {
    const label = m.awbNumber || ''
    return label.toUpperCase().includes(q)
  }).slice(0, 15)
  mawb._showSuggestions = mawb._suggestions.length > 0
}

function onMawbBlur(uld, mIdx) {
  setTimeout(() => {
    if (uld.mawbs && uld.mawbs[mIdx]) {
      uld.mawbs[mIdx]._showSuggestions = false
    }
  }, 200)
}

function selectMawbSuggestion(uld, mIdx, selected) {
  uld.mawbs[mIdx].awbNumber = selected.awbNumber
  uld.mawbs[mIdx]._showSuggestions = false
  uld.mawbs[mIdx]._isSpecial = !!selected.isSpecial
  onMawbSelect(uld, mIdx)
}

const pendingReceiptCount = computed(() => {
  const allMawbs = localUlds.value.flatMap(u => u.mawbs || [])
  const uniqueAwbs = [...new Set(allMawbs.filter(m => m.awbNumber).map(m => m.awbNumber))]
  return uniqueAwbs.filter(awb => !mawbHasReceipt(awb)).length
})

function mawbReceiptInfo(awbNumber) {
  const m = appStore.mawbs.find(x => x.awbNumber === awbNumber)
  const receipt = (appStore.receipts || []).find(r => (r.mawb && r.mawb.id === m?.id) || r.mawbId === m?.id)
  const reservedPieces = m ? (m.pieces || 0) : 0
  const assignedInUlDs = localUlds.value.flatMap(u =>
    (u.mawbs || []).filter(mw => mw.awbNumber === awbNumber)
  ).reduce((s, mw) => s + (mw.pieces || 0), 0)
  const availablePieces = Math.max(0, (reservedPieces > 0 ? reservedPieces : (receipt ? (receipt.pieceCount || receipt.receivedPieces || 0) : 0)) - assignedInUlDs)
  return {
    hasReceipt: !!receipt,
    receivedPieces: receipt ? (receipt.pieceCount || receipt.receivedPieces || 0) : 0,
    reservedPieces: reservedPieces,
    availablePieces: availablePieces,
    mawbId: m?.id || null,
  }
}

function mawbHasReceipt(awbNumber) {
  return mawbReceiptInfo(awbNumber).hasReceipt
}

function mawbInBookings(awbNumber) {
  if (!awbNumber) return false
  return (appStore.bookings || []).some(b => b.awbNumber === awbNumber)
}

function mawbStatusForAwb(awbNumber) {
  if (!awbNumber) return null
  const m = (appStore.mawbs || []).find(x => x.awbNumber === awbNumber)
  return m?.status || null
}

function mawbStatusLabel(awbNumber) {
  const s = mawbStatusForAwb(awbNumber)
  if (!s) return '—'
  const key = 'ulds.mawbStatus.' + s
  return te(key) ? t(key) : s
}

function mawbStatusDotClass(awbNumber) {
  const s = mawbStatusForAwb(awbNumber)
  if (s === 'RECEIVED') return 'bg-amber-400'
  if (s === 'MANIFESTED') return 'bg-emerald-500'
  if (s === 'DEPARTED' || s === 'ARRIVED') return 'bg-blue-500'
  if (s === 'BOOKED') return 'bg-slate-400'
  return 'bg-slate-200'
}

function mawbStatusTextClass(awbNumber) {
  const s = mawbStatusForAwb(awbNumber)
  if (s === 'RECEIVED') return 'text-amber-600'
  if (s === 'MANIFESTED') return 'text-emerald-600'
  if (s === 'DEPARTED' || s === 'ARRIVED') return 'text-blue-600'
  if (s === 'BOOKED') return 'text-slate-500'
  return 'text-slate-400'
}

function totalUldPieces(uld) {
  return (uld.mawbs || []).reduce((s, m) => s + (m.pieces || 0), 0)
}

function totalUldReceivedPieces(uld) {
  return (uld.mawbs || []).reduce((s, m) => s + ((m.receivedPieces != null ? m.receivedPieces : m.pieces) || 0), 0)
}

function rebuildLocalList() {
  const backend = (appStore.ulds || []).map(u => {
    const flight = appStore.flights.find(f => f.id === u.flightId)
    return {
      uid: u.id,
      backendId: u.id,
      uldNumber: u.uldNumber,
      flightId: u.flightId,
      flightLabel: flight?.flightNumber || 'FLOTANTE',
      route: flight ? (flight.origin + ' -> ' + flight.destination) : t('ulds.noFlight'),
      uldType: u.uldType,
      config: u.config,
      position: u.position,
      sealNumber: u.sealNumber,
      tareLbs: u.tareLbs || u.tareWeight || 0,
      grossWeightLbs: u.grossWeightLbs || u.grossWeight || 0,
      status: u.status || 'OPEN',
      builtBy: u.builtBy || '',
      notes: u.notes || '',
      destination: u.destination || '',
      confirmedWith: u.confirmedWith || '',
      saveFlightId: u.flightId,
      awbs: (u.awbs || []),
      volumePct: 0,
      createdAt: u.createdAt,
      mawbs: (u.awbs || []).map(m => {
        const info = mawbReceiptInfo(m.mawbLabel || '')
        return {
          _rowId: m.id || Math.random().toString(36).slice(2),
          awbNumber: m.mawbLabel || '',
          _showSuggestions: false,
          _suggestions: [],
          _isSpecial: false,
          commodityType: m.description || 'DRY_CARGO',
          commodityHint: m.description || '',
          pieces: m.pieces || 0,
          piecesPct: m.piecesPct || 0,
          destination: m.destination || '-',
          mawbId: m.mawbId || null,
          ...info,
        }
      }),
    }
  })
  // Calculate volumePct as sum of piecesPct across all MAWBs, capped at 100
  backend.forEach(uld => {
    const total = (uld.mawbs || []).reduce((s, m) => s + (m.piecesPct || 0), 0)
    uld.volumePct = Math.min(total, 100)
  })
  // Merge with existing unsaved local ULDS
  const unsaved = localUlds.value.filter(u => !u.backendId)
  localUlds.value = [...unsaved, ...backend]
}

function createNewBlankUld() {
  localUlds.value.unshift({
    uid: 'new-' + Date.now(),
    backendId: null,
    uldNumber: '',
    flightId: null,
    saveFlightId: null,
    flightLabel: '',
    route: 'SDQ -> MIA',
    uldType: 'PMC',
    config: '',
    position: '',
    sealNumber: '',
    tareLbs: defaultTareFor('PMC'),
    grossWeightLbs: 0,
    status: 'OPEN',
    volumePct: 0,
    builtBy: '',
    notes: '',
    destination: '',
    confirmedWith: '',
    mawbs: [],
  })
  expandedUldId.value = localUlds.value[0].uid
  creationStep.value = 1
  scanMode.value = false
  scanUldUid.value = null
}

async function dismountUld(uld) {
  if (!(await confirm({ message: t('ulds.confirmDemountFull', { number: uld.uldNumber || '' }) }))) return
  try {
    // 1. Delete all ULD-AWB links
    const existing = await uldAwbsApi.getByUld(uld.backendId)
    for (const link of (existing.data || [])) {
      await uldAwbsApi.delete(link.id)
    }
    // 2. Reset the ULD
    await uldsApi.update(uld.backendId, {
      airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
      uldNumber: uld.uldNumber,
      position: null,
      sealNumber: null,
      tareLbs: 0,
      grossWeightLbs: 0,
      status: 'OPEN',
      notes: null,
    })
    uld.tareLbs = 0
    // 3. Remove flight assignment
    await uldsApi.assignFlight(uld.backendId, null)
    // 4. Reload
    await appStore.loadUlds()
    rebuildLocalList()
    expandedUldId.value = null
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function deleteUld(uld) {
  if (!uld.backendId) {
    // Local ULD not saved — just discard from list
    localUlds.value = localUlds.value.filter(u => u.uid !== uld.uid)
    if (expandedUldId.value === uld.uid) expandedUldId.value = null
    return
  }
  const hasCargo = (uld.mawbs || []).length > 0
  if (hasCargo) {
    if (!(await confirm({ message: t('ulds.confirmDeleteWithCargo', { number: uld.uldNumber || '', count: uld.mawbs.length }), danger: true }))) return
    // Dismount first
    try {
      const existing = await uldAwbsApi.getByUld(uld.backendId)
      for (const link of (existing.data || [])) {
        await uldAwbsApi.delete(link.id)
      }
      await uldsApi.update(uld.backendId, {
        airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
        uldNumber: uld.uldNumber,
        position: '',
        sealNumber: '',
        tareLbs: 0,
        grossWeightLbs: 0,
        status: 'OPEN',
        notes: '',
      })
      uld.tareLbs = 0
      await uldsApi.assignFlight(uld.backendId, null)
    } catch (e) {
      toast.error(extractError(e))
      return
    }
  } else {
    if (!(await confirm({ message: t('ulds.confirmDelete', { number: uld.uldNumber || '' }), danger: true }))) return
  }
  try {
    await uldsApi.delete(uld.backendId)
    localUlds.value = localUlds.value.filter(u => u.uid !== uld.uid)
    if (expandedUldId.value === uld.uid) expandedUldId.value = null
    await appStore.loadUlds()
    rebuildLocalList()
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function saveUld(uld) {
  if (!uld.uldNumber) {
    toast.warning(t('ulds.uldNumberRequired'))
    return
  }
  const flightId = uld.saveFlightId

  if (!flightId) {
    if (!uld.backendId) {
      toast.warning(t('ulds.selectFlightRequired'))
      return
    }
    // Floating ULD without flight change — just update fields
    try {
      // notes: solo lo que escriba el operador (sin autollenado)
      await uldsApi.update(uld.backendId, {
        airlineId: uld.airlineId || appStore.selectedFlight?.airlineId || null,
        uldNumber: uld.uldNumber,
        uldType: uld.uldType,
        config: uld.config || null,
        position: uld.position ?? null,
        sealNumber: uld.sealNumber ?? null,
        tareLbs: uld.tareLbs || 0,
        grossWeightLbs: uld.grossWeightLbs || 0,
        status: uld.status || 'OPEN',
        notes: uld.notes ?? null,
        destination: uld.destination ?? null,
        builtBy: uld.builtBy ?? null,
      })
      // Recreate ULD-AWB links
      if (uld.backendId) {
        const existing = await uldAwbsApi.getByUld(uld.backendId)
        for (const link of (existing.data || [])) {
          await uldAwbsApi.delete(link.id)
        }
      }
      for (const m of (uld.mawbs || [])) {
        if (m.awbNumber && uld.backendId) {
          const matchingMawb = appStore.mawbs.find(x => x.awbNumber === m.awbNumber)
          await uldAwbsApi.create({
            uldId: uld.backendId,
            mawbId: matchingMawb?.id || null,
            mawbLabel: m.awbNumber,
            description: normalizeCommodity(m.commodityType),
            destination: m.destination || 'MIA',
            pieces: m.pieces || 0,
            piecesPct: m.piecesPct || 0,
          })
        }
      }
      expandedUldId.value = null
      await appStore.loadUlds()
      rebuildLocalList()
    } catch (e) {
      toast.error(extractError(e))
    }
    return
  }


  try {
    uld.flightId = flightId
    // notes: solo lo que escriba el operador (sin autollenado)
    const result = await uldsStore.dispatchUld(uld, flightId)
    uld.backendId = result?.id || uld.backendId
    // Delete existing ULD-AWB links before recreating
    if (uld.backendId) {
      const existing = await uldAwbsApi.getByUld(uld.backendId)
      for (const link of (existing.data || [])) {
        await uldAwbsApi.delete(link.id)
      }
    }
    // Create ULD-AWB links for each MAWB
    for (const m of (uld.mawbs || [])) {
      if (m.awbNumber && result?.id) {
        const matchingMawb = appStore.mawbs.find(x => x.awbNumber === m.awbNumber)
        await uldAwbsApi.create({
          uldId: result.id,
          mawbId: matchingMawb?.id || null,
          mawbLabel: m.awbNumber,
          description: normalizeCommodity(m.commodityType),
          destination: m.destination || 'MIA',
          pieces: m.pieces || 0,
          piecesPct: m.piecesPct || 0,
        })
      }
    }
    expandedUldId.value = null
    await appStore.loadUlds()
    rebuildLocalList()
    await appStore.loadAllMawbs()
  } catch (e) {
    toast.error(extractError(e))
  }
}

function toggleUldExpansion(uid) {
  expandedUldId.value = expandedUldId.value === uid ? null : uid
}

function addMawbRow(uld) {
  uld.mawbs.push({
    _rowId: Math.random().toString(36).slice(2),
    awbNumber: '',
    commodityType: 'DRY_CARGO',
    commodityHint: '',
    pieces: 0,
    piecesPct: 0,
    destination: 'MIA',
    mawbId: null,
    hasReceipt: false,
    receivedPieces: 0,
    reservedPieces: 0,
    availablePieces: 0,
    _isSpecial: false,
    _showSuggestions: false,
    _suggestions: [],
  })
}

function removeMawbRow(uld, index) {
  uld.mawbs.splice(index, 1)
}

function onMawbSelect(uld, mIdx) {
  const selected = availableMawbs.value.find(m => m.awbNumber === uld.mawbs[mIdx].awbNumber)
  if (selected) {
    uld.mawbs[mIdx].commodityType = normalizeCommodity(selected.commodityType)
    uld.mawbs[mIdx].commodityHint = selected.commodityType || ''
    uld.mawbs[mIdx].destination = selected.destination || 'MIA'
    uld.mawbs[mIdx].mawbId = selected.isSpecial ? null : selected.id
    if (!selected.isSpecial) {
      const info = mawbReceiptInfo(selected.awbNumber)
      uld.mawbs[mIdx].hasReceipt = info.hasReceipt
      uld.mawbs[mIdx].receivedPieces = info.receivedPieces
      uld.mawbs[mIdx].reservedPieces = info.reservedPieces
      // Auto-fill pieces from reserved/received
      if (info.receivedPieces > 0) {
        uld.mawbs[mIdx].pieces = info.receivedPieces
      } else if (info.reservedPieces > 0) {
        uld.mawbs[mIdx].pieces = info.reservedPieces
      }
    }
  }
}

function statusBadgeClass(status) {
  switch (status) {
    case 'OPEN': return 'bg-slate-100 text-slate-600 border border-slate-200'
    case 'BUILT': return 'bg-blue-50 text-blue-700 border border-blue-100'
    case 'SEALED': return 'bg-amber-50 text-amber-700 border border-amber-100'
    case 'LOADED': return 'bg-emerald-50 text-emerald-700 border border-emerald-100'
    case 'LEFT_BEHIND': return 'bg-rose-50 text-rose-700 border border-rose-100'
    default: return 'bg-slate-100 text-slate-950 border border-slate-200'
  }
}

function flightLabel(uld) {
  if (!uld.flightLabel) return '---'
  if (uld.flightLabel === 'FLOTANTE') return t('ulds.floating')
  if (uld.flightLabel === 'TBD') return 'TBD'
  const num = parseInt(uld.flightLabel, 10)
  if (!isNaN(num)) return airlineCodeById(uld.airlineId) + '-' + uld.flightLabel
  return uld.flightLabel
}

function uldAgeInDays(createdAt) {
  if (!createdAt) return null
  const created = new Date(createdAt)
  const now = new Date()
  return Math.floor((now - created) / (1000 * 60 * 60 * 24))
}

function uldAgeBadgeClass(days) {
  if (days === null) return ''
  if (days < 7) return 'bg-slate-100 text-slate-600 border border-slate-200'
  if (days <= 30) return 'bg-slate-100 text-slate-600 border border-slate-200'
  return 'bg-slate-100 text-slate-600 border border-slate-200'
}

function uldStatusBorderStyle(status) {
  const map = {
    SEALED: 'border-l-2 border-l-amber-400',
    LOADED: 'border-l-2 border-l-emerald-500',
    LEFT_BEHIND: 'border-l-2 border-l-rose-500',
    BUILT: 'border-l-2 border-l-blue-400',
  }
  return map[status] || ''
}

onMounted(async () => {
  if (!appStore.airlines.length) await appStore.loadAirlines()
  await Promise.all([
    appStore.loadFlights(),
    appStore.loadAllMawbs(),
    appStore.loadReceipts(),
    appStore.loadUlds(),
    loadCommodities(),
  ])
  loadTypeConfig()
  loadUldCatalog()
  rebuildLocalList()
})

watch(() => appStore.ulds, () => rebuildLocalList(), { deep: true })
</script>

<style scoped>
input[type="number"]::-webkit-inner-spin-button,
input[type="number"]::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
input[type="number"] { -moz-appearance: textfield; appearance: textfield; }
.row-selected { background: #f8fafc; }
</style>