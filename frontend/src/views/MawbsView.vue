<template>
  <div class="ds-page">

    <!-- Tabs -->
    <div class="flex items-center gap-1 border-b border-slate-200 pb-0 shrink-0">
      <button @click="activeTab = 'matriz'"
        class="px-4 py-2 text-[13px] font-mono font-black uppercase tracking-widest transition-all relative"
          :class="activeTab === 'matriz' ? 'text-slate-950' : 'text-slate-400 hover:text-slate-600'">
        {{ t('mawbs.tabs.matrix') }}
        <div v-if="activeTab === 'matriz'" class="absolute bottom-0 left-0 right-0 h-0.5 bg-slate-950"></div>
      </button>
      <button @click="activeTab = 'estados'"
        class="px-4 py-2 text-[13px] font-mono font-black uppercase tracking-widest transition-all relative"
          :class="activeTab === 'estados' ? 'text-slate-950' : 'text-slate-400 hover:text-slate-600'">
        {{ t('mawbs.tabs.states') }}
        <div v-if="activeTab === 'estados'" class="absolute bottom-0 left-0 right-0 h-0.5 bg-slate-950"></div>
      </button>
      <button @click="activeTab = 'lbs-vuelo'"
        class="px-4 py-2 text-[13px] font-mono font-black uppercase tracking-widest transition-all relative"
          :class="activeTab === 'lbs-vuelo' ? 'text-slate-950' : 'text-slate-400 hover:text-slate-600'">
        {{ t('mawbs.tabs.lbsPerFlight') }}
        <div v-if="activeTab === 'lbs-vuelo'" class="absolute bottom-0 left-0 right-0 h-0.5 bg-slate-950"></div>
      </button>
    </div>

    <header v-if="activeTab === 'matriz'" class="flex flex-wrap justify-between items-end gap-2 border-b border-slate-200 pb-3 shrink-0">
      <div class="flex items-end gap-3">
        <div>
          <h1 class="ds-title">{{ t('mawbs.title') }}</h1>
          <p class="ds-subtitle">{{ t('mawbs.subtitle') }}</p>
        </div>
        <div class="flex items-end gap-2">
          <button @click="showFilter = !showFilter"
            class="px-2 py-1 rounded border transition-all"
            :class="showFilter || filterText ? 'bg-slate-950 text-white border-slate-950' : 'bg-white text-slate-400 border-slate-200 hover:border-slate-400'"
            :title="t('mawbs.dynamicFilter')">
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
            </svg>
          </button>
          <div class="flex items-center gap-1">
            <select v-model="commodityFilter"
              class="bg-white border border-slate-300 rounded px-2 py-1.5 text-[12px] font-mono text-slate-950 outline-none focus:border-slate-500">
              <option value="">{{ t('mawbs.commodityAll') }}</option>
              <option v-for="c in dbCommodities" :key="c.code" :value="c.code">{{ c.label }}</option>
            </select>
          </div>
          <div v-if="showFilter" class="flex items-center gap-1">
      <input v-model="filterText" :placeholder="t('mawbs.filterPlaceholder')"
        class="w-full bg-white border border-slate-300 rounded px-3 py-1.5 text-[13px] font-mono text-slate-950 outline-none focus:border-slate-500 transition-colors" />
            <span class="text-[14px] font-mono text-slate-950 min-w-[30px]">{{ filterTypeLabel }}</span>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-[12px] font-mono text-slate-950 whitespace-nowrap">{{ t('mawbs.rowsInfo', { rows: filteredRows.length, total: matrixRows.length, cols: flightColumns.length }) }}</span>
        <button @click="showLabels = true" class="ds-btn-secondary" :title="t('mawbs.labelsTooltip')">
          <span class="text-[14px] font-semibold leading-none">&#9642;</span> {{ t('mawbs.labelsButton') }}
        </button>
        <button @click="exportCSV" class="ds-btn-secondary">
          <span class="text-[14px] font-semibold leading-none">↓</span> {{ t('mawbs.exportCsv') }}
        </button>
        <div class="flex items-center gap-2 border-l border-slate-200 pl-2">
          <div class="relative">
            <button @click="showPeriodMenu = !showPeriodMenu"
              class="p-1.5 rounded hover:bg-slate-100 transition-colors text-[14px] font-mono flex items-center gap-1" :title="t('mawbs.timelinePeriod')">
              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>
              <span class="font-bold">{{ periodLabel }}</span>
            </button>
            <div v-if="showPeriodMenu" class="absolute top-full left-0 mt-1 bg-white border border-slate-400 rounded-lg shadow-xl z-50 py-1 min-w-[150px]">
              <button v-for="p in periodOptions" :key="p.value" @click="setTimelinePeriod(p.value)"
                class="w-full text-left px-3 py-2 text-[14px] font-mono hover:bg-slate-100 transition-colors"
                :class="timelinePeriod === p.value ? 'bg-slate-200 font-bold text-slate-950' : 'text-slate-950'">
                {{ t('mawbs.periods.' + p.value) }}
              </button>
            </div>
          </div>
          <div class="flex items-center overflow-x-auto scrollbar-none" style="max-width:480px">
            <div v-if="timelineSegments.length" class="relative w-full py-1 select-none" style="min-width:280px">
              <!-- Barra de fondo -->
              <div class="relative h-6 flex items-center mx-1">
                <div class="absolute left-0 right-0 h-1.5 bg-slate-100 rounded-full"></div>
                <!-- Rango activo -->
                <div v-if="rangeStartSeg && rangeEndSeg"
                  class="absolute h-1.5 rounded-full bg-slate-950/20"
                  :style="rangeBarStyle"></div>
                <!-- Segmentos -->
                <div v-for="seg in timelineSegments" :key="seg.value"
                  class="flex-1 flex flex-col items-center relative z-10 cursor-pointer group"
                  @click="toggleRangeSegment(seg.value)"
                  :title="t('mawbs.tooltip.segmentCols', { label: seg.label, count: seg.count })">
                  <span class="text-[13px] font-mono mb-0.5 leading-none transition-colors font-bold"
                    :class="isInRange(seg.value) ? 'text-slate-950' : 'text-slate-400 group-hover:text-slate-600'">
                    {{ seg.short }}
                  </span>
                  <div class="w-full h-1.5 flex items-center justify-center">
                    <div class="h-1 w-full rounded-sm transition-all duration-150"
                      :class="isInRange(seg.value)
                        ? 'bg-slate-950'
                        : 'bg-transparent group-hover:bg-slate-200'"></div>
                  </div>
                </div>
              </div>
              <!-- Handle izquierdo + derecho -->
              <div v-if="rangeStartSeg && timelineSegments.length > 1"
                class="absolute top-0 left-0 right-0 h-6 pointer-events-none">
                <div class="relative w-full h-full">
                  <div class="absolute top-0 -translate-x-1/2 pointer-events-auto cursor-col-resize group"
                    :style="{ left: rangeLeftPct + '%' }"
                    @pointerdown.prevent="startRangeDrag('left', $event)">
                    <div class="w-3 h-5 bg-slate-950 rounded-sm shadow-sm flex items-center justify-center">
                      <div class="w-0.5 h-3 bg-white/60 rounded-full"></div>
                    </div>
                  </div>
                  <div class="absolute top-0 translate-x-1/2 pointer-events-auto cursor-col-resize group"
                    :style="{ left: rangeRightPct + '%' }"
                    @pointerdown.prevent="startRangeDrag('right', $event)">
                    <div class="w-3 h-5 bg-slate-950 rounded-sm shadow-sm flex items-center justify-center">
                      <div class="w-0.5 h-3 bg-white/60 rounded-full"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <span v-else class="text-[13px] text-slate-300 font-mono px-2 whitespace-nowrap">{{ t('mawbs.noDates') }}</span>
          </div>
          <button v-if="rangeStartSeg" @click="clearTimeline"
            class="text-slate-400 hover:text-slate-950 text-[12px] px-1 font-bold">✕</button>
        </div>
        <select v-model="localFlightId" @change="onFlightChange"
          class="bg-slate-100 border border-slate-300 rounded px-2 py-1 font-black text-slate-950 focus:outline-none uppercase tracking-widest text-[12px] cursor-pointer min-w-[140px]">
          <option value="">{{ t('mawbs.allFlights') }}</option>
          <option v-for="flight in store.flights" :key="flight.id" :value="flight.id">
            {{ airlineCodeById(flight.airlineId) }}-{{ flight.flightNumber }} ({{ flight.origin }}→{{ flight.destination }})
          </option>
        </select>
        <button @click="toggleHidePast"
          class="flex items-center gap-1 px-2 py-1 rounded text-[12px] font-mono font-bold transition border"
          :class="hidePastDates ? 'bg-slate-100 border-slate-400 text-slate-800' : 'bg-white border-slate-300 text-slate-500 hover:bg-slate-100'"
          :title="t('mawbs.hidePast')">
          <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
          {{ hidePastDates ? t('mawbs.future') : t('mawbs.past') }}
        </button>
      </div>
    </header>

    <!-- Estados Header -->
    <header v-if="activeTab === 'estados'" class="flex flex-wrap justify-between items-end gap-2 border-b border-slate-200 pb-3 shrink-0">
      <div class="flex items-end gap-3">
        <div>
          <h1 class="text-[15px] font-black tracking-tight text-slate-950 uppercase font-mono">{{ t('mawbs.states.title') }}</h1>
          <p class="text-[13px] font-mono text-slate-400 mt-0.5 uppercase tracking-widest font-bold">{{ t('mawbs.states.subtitle') }}</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <div class="flex items-center gap-1">
          <button v-for="s in statusOptions" :key="s.value"
            @click="toggleStatusFilter(s.value)"
            class="px-2.5 py-1 rounded text-[12px] font-mono font-bold transition-all border"
            :class="estadosStatusFilter.includes(s.value)
              ? 'bg-slate-950 text-white border-slate-950'
              : 'bg-white text-slate-500 border-slate-300 hover:border-slate-500'">
            {{ s.label }} ({{ s.count }})
          </button>
        </div>
        <span class="text-[12px] font-mono text-slate-400">{{ t('mawbs.states.counter', { n: estadosFilteredRows.length }) }}</span>
      </div>
    </header>

    <!-- Data-driven status strip (NASA mission control style) -->
    <div v-if="activeTab === 'matriz'" class="flex items-center gap-3 my-2 shrink-0 px-1">
      <div v-for="(s, si) in dataStatus" :key="si" class="flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full" :class="s.dotClass" :style="s.dotStyle"></span>
        <span class="text-[12px] font-mono font-bold text-slate-950">{{ s.label }}</span>
        <span class="text-[12px] font-mono text-slate-400">|</span>
        <span class="text-[12px] font-mono font-black text-slate-950 tabular-nums">{{ s.value }}</span>
      </div>
    </div>

    <div v-if="activeTab === 'matriz'" class="flex-1 min-h-0 flex gap-2 mb-1.5">
    <section ref="tableSectionRef" class="ds-table-section">
      <div v-if="loadingMatrix" class="flex-1 flex items-center justify-center">
        <span class="text-[14px] font-mono text-slate-950">{{ t('mawbs.buildingMatrix') }}</span>
      </div>
      <div v-else-if="!filteredRows.length" class="flex-1 flex items-center justify-center">
        <p class="text-[14px] font-mono text-slate-950 uppercase tracking-widest">{{ t('mawbs.noResults') }}</p>
      </div>
      <template v-else>
        <div ref="scrollContainer" class="overflow-auto scrollbar-none flex-1" @scroll="onScroll">
          <table class="w-full border-collapse text-[13px] font-mono">
            <thead class="sticky top-0 z-20">
              <tr class="bg-slate-700 text-white text-[13px] font-bold uppercase tracking-wider shadow-sm">
                <th :style="[{ left: stickyOffsets[0] + 'px', zIndex: 30 }, colStyle(0)]"
                  class="sticky bg-slate-700 text-left px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap relative">{{ t('mawbs.columns.mawb') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(0, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th :style="[{ left: stickyOffsets[1] + 'px', zIndex: 30 }, colStyle(1)]"
                  class="sticky bg-slate-700 text-left px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap relative">{{ t('mawbs.columns.shipperConsignee') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(1, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th :style="[{ left: stickyOffsets[2] + 'px', zIndex: 30 }, colStyle(2)]"
                  class="sticky bg-slate-700 text-right px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap relative">{{ t('mawbs.columns.pcsReserved') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(2, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th :style="[{ left: stickyOffsets[3] + 'px', zIndex: 30 }, colStyle(3)]"
                  class="sticky bg-slate-700 text-right px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap relative">{{ t('mawbs.columns.pcsReceived') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(3, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th :style="[{ left: stickyOffsets[4] + 'px', zIndex: 30 }, colStyle(4)]"
                  class="sticky bg-slate-700 text-right px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 relative">{{ t('mawbs.columns.kg') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(4, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th :style="[{ left: stickyOffsets[5] + 'px', zIndex: 30 }, colStyle(5)]"
                  class="sticky bg-slate-700 text-right px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 relative">{{ t('mawbs.columns.lbs') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(5, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th :style="[{ left: stickyOffsets[6] + 'px', zIndex: 30 }, colStyle(6)]"
                  class="sticky bg-slate-700 text-right px-2 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap relative">{{ t('mawbs.columns.pcsDispatched') }}
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown="startColResize(7, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
                <th v-for="(f, fi) in flightColumns" :key="f.id"
                  :style="colStyle(7 + fi)"
                  class="px-2 py-2.5 text-center font-black border-x border-slate-500/40 cursor-pointer transition-colors relative"
                  :class="[highlightFlightId === f.id ? 'bg-slate-500 text-slate-950' : 'hover:bg-slate-800/70']"
                  @mouseenter="hoverFlightCol = f.id" @mouseleave="hoverFlightCol = null"
                  @click="scrollToFlight(f.id)">
                  <div class="text-[13px] leading-tight">{{ airlineCodeById(f.airlineId) }}-{{ f.flightNumber }}</div>
                  <div class="text-[13px] font-bold opacity-90 tracking-wide">{{ formatDate(f.flightDate) }}</div>
                  <div v-if="flightTotals[f.id]" class="text-[13px] font-normal opacity-60 mt-0.5">{{ flightTotals[f.id] }} {{ t('common.pcs') }}</div>
                  <div class="absolute right-0 top-0 bottom-0 w-2 cursor-col-resize group z-40" @pointerdown.stop="startColResize(8 + fi, $event)">
                    <div class="w-0.5 h-full mx-auto bg-transparent group-hover:bg-white/40 transition-colors"></div>
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in filteredRows" :key="row.mawbId"
                class="border-b border-slate-100 transition-colors"
                :class="[rowBgClass(row), hoverFlightCol ? 'group' : '']">
                <td :style="[{ left: stickyOffsets[0] + 'px', zIndex: 10 }, colStyle(0)]"
                  class="sticky px-2 py-2.5 border-r border-slate-300 cursor-pointer transition-colors duration-150 whitespace-nowrap"
                  :class="mawbStatusClass(row)"
                  :title="statusTitle(row)"
                  @click="openInfoPanel(row)">
                  <div class="flex flex-col leading-tight">
<span class="underline decoration-dotted underline-offset-2 truncate text-[13px]">{{ row.awbNumber || '—' }}</span>
                     <span class="text-[13px] font-normal opacity-70 mt-0.5">
                      <template v-if="row.status === 'BOOKED'">{{ t('mawbs.rowStatus.BOOKED') }}</template>
                      <template v-else-if="row.status === 'RECEIVED'">{{ t('mawbs.rowStatus.RECEIVED') }}</template>
                      <template v-else-if="row.status === 'MANIFESTED'">{{ t('mawbs.rowStatus.MANIFESTED') }}</template>
                      <template v-else-if="row.status === 'DEPARTED'">{{ t('mawbs.rowStatus.DEPARTED') }}</template>
                      <template v-else>{{ row.status || '' }}</template>
                    </span>
                  </div>
                </td>
                <td :style="[{ left: stickyOffsets[1] + 'px', zIndex: 10 }, colStyle(1)]"
                  class="sticky bg-white px-2 py-2.5 text-slate-800 border-r border-slate-300 whitespace-nowrap"
                  :title="(row.shipperName || '?') + ' / ' + (row.consigneeName || '?')">
                  <div class="flex flex-col leading-tight">
<span class="truncate text-[13px]">{{ row.shipperName || '—' }}</span>
                     <span class="truncate text-[13px] text-slate-950">/ {{ row.consigneeName || '—' }}</span>
                  </div>
                </td>
                <td :style="[{ left: stickyOffsets[2] + 'px', zIndex: 10 }, colStyle(2)]"
                  class="sticky bg-white px-2 py-2.5 text-right border-r border-slate-300 whitespace-nowrap"
                   :class="row.pieceDiff !== 0 ? 'text-slate-600 bg-slate-50' : 'text-slate-950'">
                  {{ row.reservedPieces || '—' }}
<span v-if="row.pieceDiff > 0" class="text-[13px] text-slate-500 ml-0.5" :title="t('mawbs.tooltip.exceedsReserved')">&#9650;</span>
                    <span v-else-if="row.pieceDiff < 0" class="text-[13px] text-slate-500 ml-0.5" :title="t('mawbs.tooltip.belowReserved')">&#9660;</span>
                </td>
                <td :style="[{ left: stickyOffsets[3] + 'px', zIndex: 10 }, colStyle(3)]"
                  class="sticky bg-white px-2 py-2.5 text-right border-r border-slate-300 whitespace-nowrap"
                   :class="row.pieceDiff !== 0 ? 'text-slate-600 bg-slate-50' : 'text-slate-950'">
                  {{ row.receivedPieces || '—' }}
                   <span v-if="row.receivedPieces > 0 && row.pieceDiff !== 0" class="text-[13px] text-slate-400 ml-0.5">&#9888;</span>
                </td>
                <td :style="[{ left: stickyOffsets[4] + 'px', zIndex: 10 }, colStyle(4)]"
                  class="sticky bg-white px-2 py-2.5 text-right text-slate-950 border-r border-slate-300">{{ row.totalWeightKg ? Number(row.totalWeightKg).toLocaleString() : '—' }}</td>
                <td :style="[{ left: stickyOffsets[5] + 'px', zIndex: 10 }, colStyle(5)]"
                  class="sticky bg-white px-2 py-2.5 text-right text-amber-700 border-r border-slate-300 font-semibold">{{ row.physicalWeightLbs != null ? Number(row.physicalWeightLbs).toLocaleString('en-US', { maximumFractionDigits: 1 }) : '—' }}</td>
                <td :style="[{ left: stickyOffsets[6] + 'px', zIndex: 10 }, colStyle(6)]"
                  class="sticky bg-white px-2 py-2.5 text-right border-r border-slate-300"
                   :class="row.hasDispatchedExcess ? 'text-slate-600' : 'text-slate-950'">
                  {{ row.pcsDispatched || '—' }}
                   <span v-if="row.hasDispatchedExcess" class="text-[13px] text-slate-500 ml-0.5" :title="t('mawbs.tooltip.exceedsReceived')">&#9888;</span>
                </td>
                <td v-for="(f, fi) in flightColumns" :key="f.id"
                  :style="colStyle(7 + fi)"
                  class="px-2 py-2.5 text-center border-x border-slate-200 transition-all duration-200"
                  :class="cellClasses(row, f)"
                  :title="uldTooltip(row, f)">
                  <span v-if="getPieces(row, f)" class="relative text-[13px]">
                    <span class="flex flex-col items-center leading-tight">
                      <span>{{ getPieces(row, f) }}</span>
                      <span class="text-[13px] font-normal opacity-60">{{ row.uldCountByFlight[f.id] || 0 }} ULDs</span>
                    </span>
                    <svg width="16" height="16" viewBox="0 0 14 14" class="inline-block shrink-0 ml-0.5">
                      <circle cx="7" cy="7" r="5.5" fill="none" stroke="#e2e8f0" stroke-width="1.5" />
                      <circle cx="7" cy="7" r="5.5" fill="none" stroke="#475569" stroke-width="1.5"
                        :stroke-dasharray="arcCircum"
                        :stroke-dashoffset="arcOffset(row, f)"
                        transform="rotate(-90 7 7)" />
                    </svg>
                  </span>
                  <span v-else class="text-slate-200">&middot;</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="bg-slate-100 border-t border-slate-300 px-4 py-1.5 text-[14px] text-slate-950 font-mono flex justify-between items-center shrink-0">
          <span>{{ t('mawbs.footer.summary', { reserved: totalReserved, received: totalReceived, dispatched: totalDispatched, tracked: totalTracked }) }}</span>
          <span class="flex items-center gap-3">
            <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-sm bg-slate-950"></span> {{ t('mawbs.footer.withPieces') }}</span>
            <span>{{ t('mawbs.footer.cells', { cells: activeCells, flights: flightColumns.length, mawbs: filteredRows.length }) }}</span>
          </span>
        </div>
        <div class="h-4 cursor-row-resize flex items-center justify-center hover:bg-slate-200 transition-colors shrink-0 group border-t border-slate-300"
          @mousedown.prevent="startResize">
          <div class="w-10 h-0.5 rounded-full bg-slate-400 group-hover:bg-slate-500 group-hover:h-1 transition-all duration-150"></div>
        </div>
      </template>
    </section>

    <!-- MAWB Info Sidebar -->
    <transition name="slide">
      <aside v-if="infoPanel.show" class="hidden md:flex w-72 shrink-0 border border-slate-300 rounded bg-white flex-col overflow-hidden">
        <div class="flex items-center justify-between px-3 py-2 border-b border-slate-400 bg-slate-100 shrink-0">
          <span class="text-[12px] font-mono font-black uppercase tracking-widest text-slate-950">{{ t('mawbs.infoPanel.title') }}</span>
          <button @click="closeInfoPanel" class="text-slate-500 hover:text-slate-950 transition text-sm">✕</button>
        </div>
        <div class="overflow-y-auto flex-1 p-3 space-y-2.5 text-[12px] font-mono text-slate-950">
          <template v-if="infoPanel.row">
            <div class="grid grid-cols-2 gap-x-3 gap-y-1.5">
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.columns.mawb') }}</span>
              <span class="font-bold text-right text-slate-800">{{ infoPanel.row.awbNumber }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.status') }}</span>
              <span class="text-right"><span class="inline-block w-2 h-2 rounded-full mr-1" :class="mawbStatusClassRaw(infoPanel.row.mawbStatus)"></span>{{ infoPanel.row.mawbStatus || 'BOOKED' }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.shipper') }}</span>
              <span class="font-bold text-right truncate" :title="infoPanel.row.shipperName">{{ infoPanel.row.shipperName || '—' }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.consignee') }}</span>
              <span class="font-bold text-right truncate" :title="infoPanel.row.consigneeName">{{ infoPanel.row.consigneeName || '—' }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.destination') }}</span>
              <span class="font-bold text-right">{{ infoPanel.row.destination || '—' }}</span>
            </div>

            <div class="border-t border-slate-200 pt-2">
              <div class="text-slate-500 uppercase tracking-wider mb-1">{{ t('mawbs.infoPanel.distribution') }}</div>
              <div v-for="(cell, fid) in infoPanel.row.cells" :key="fid" class="flex justify-between items-center py-0.5 border-b border-slate-100 last:border-0">
                <span class="text-slate-600 truncate">{{ infoPanel.flightLabel(fid) }}</span>
                <span class="font-bold tabular-nums">{{ cell }} {{ t('mawbs.infoPanel.pcs') }}</span>
              </div>
              <div class="flex justify-between items-center py-0.5 mt-1 border-t border-slate-300 pt-1 font-black">
                <span>{{ t('mawbs.infoPanel.total') }}</span>
                <span class="tabular-nums">{{ Object.values(infoPanel.row.cells).reduce((a, b) => a + b, 0) }} {{ t('mawbs.infoPanel.pcs') }}</span>
              </div>
            </div>

            <div class="border-t border-slate-200 pt-2 grid grid-cols-2 gap-x-3 gap-y-1.5">
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.reservedPieces') }}</span>
              <span class="font-bold text-right">{{ infoPanel.row.reservedPieces }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.receivedPieces') }}</span>
              <span class="font-bold text-right">{{ infoPanel.row.receivedPieces }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.dispatchedPieces') }}</span>
              <span class="font-bold text-right">{{ infoPanel.row.pcsDispatched }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.weightKg') }}</span>
              <span class="font-bold text-right">{{ infoPanel.row.totalWeightKg ? Number(infoPanel.row.totalWeightKg).toLocaleString() : '—' }}</span>
              <span class="text-slate-500 uppercase tracking-wider">{{ t('mawbs.infoPanel.physicalWeightLbs') }}</span>
              <span class="font-bold text-right text-amber-700">{{ infoPanel.row.physicalWeightLbs != null ? Number(infoPanel.row.physicalWeightLbs).toLocaleString('en-US', { maximumFractionDigits: 1 }) : '—' }}</span>
            </div>

            <div class="border-t border-slate-200 pt-2 space-y-1">
              <div v-if="receiptForMawb(infoPanel.row)" class="grid grid-cols-2 gap-1">
                <button @click="downloadReceiptXlsx(infoPanel.row)"
                  class="text-[12px] px-2 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider text-slate-950 hover:bg-slate-100 transition"
                  :title="t('mawbs.infoPanel.xlsxTooltip')">
                  {{ t('mawbs.infoPanel.downloadXlsx') }}
                </button>
                <button @click="downloadReceiptPdf(infoPanel.row)"
                  class="text-[12px] px-2 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider text-slate-950 hover:bg-slate-100 transition"
                  :title="t('mawbs.infoPanel.pdfTooltip')">
                  {{ t('mawbs.infoPanel.downloadPdf') }}
                </button>
                <button @click="downloadEvidenceHtml(infoPanel.row)"
                  class="text-[12px] px-2 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider text-slate-950 hover:bg-slate-100 transition"
                  :title="t('mawbs.infoPanel.evidenceTooltip')">
                  {{ t('mawbs.infoPanel.downloadEvidence') }}
                </button>
                <button @click="downloadEvidencePdf(infoPanel.row)"
                  class="text-[12px] px-2 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider text-slate-950 hover:bg-slate-100 transition"
                  :title="t('mawbs.infoPanel.evidencePdfTooltip')">
                  {{ t('mawbs.infoPanel.downloadEvidencePdf') }}
                </button>
              </div>
              <button @click="goToReceipt(infoPanel.row)"
                class="w-full text-[12px] px-2 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider text-slate-950 hover:bg-slate-100 transition">
                {{ t('mawbs.infoPanel.viewInReceipts') }}
              </button>
              <button @click="copyToClipboard(infoPanel.row.awbNumber)"
                class="w-full text-[12px] px-2 py-1.5 rounded border border-slate-300 font-mono uppercase tracking-wider text-slate-950 hover:bg-slate-100 transition">
                {{ t('mawbs.infoPanel.copyMawb') }}
              </button>
            </div>
          </template>
          <div v-else class="text-slate-400 text-center py-8">{{ t('mawbs.infoPanel.selectPrompt') }}</div>
        </div>
      </aside>
    </transition>
    </div>

    <teleport to="body">
      <transition name="fade">
        <div v-if="showMiniMap" class="fixed bottom-6 right-6 z-50 bg-white border border-slate-300 rounded p-3 w-56 h-44 overflow-hidden">
          <div class="text-[10px] font-mono font-bold text-slate-950 uppercase tracking-wider mb-1">{{ t('mawbs.minimap.title') }}</div>
          <div class="grid" :style="miniMapGridStyle">
            <div v-for="(cell, mi) in miniMapCells" :key="mi"
              class="rounded-[1px]"
              :class="cell.active ? 'bg-slate-950' : 'bg-slate-100'"
              :title="cell.label"></div>
          </div>
          <div class="absolute inset-0 border-2 border-slate-500/30 rounded-lg pointer-events-none" :style="miniMapViewportStyle"></div>
        </div>
      </transition>
    </teleport>

    <button v-if="activeTab === 'matriz'" @click="showMiniMap = !showMiniMap"
      class="fixed bottom-4 left-4 z-50 w-8 h-8 rounded-full bg-slate-800 text-white text-[12px] font-mono shadow-lg hover:bg-slate-950 transition flex items-center justify-center"
      :title="t('mawbs.minimap.openTooltip')">
      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /></svg>
    </button>

    <LabelPrintModal v-if="showLabels" type="CARGO" :items="labelItems" @close="showLabels = false" />

    <!-- Estados View -->
    <template v-if="activeTab === 'estados'">
      <div class="flex-1 min-h-0 flex gap-2 mb-1.5 mt-2">
        <section class="ds-table-section">
          <div class="overflow-auto flex-1">
            <table class="w-full border-collapse text-[13px] font-mono" style="min-width: 1100px">
              <thead class="sticky top-0 z-20">
                <tr class="bg-slate-700 text-white text-[13px] font-bold uppercase tracking-wider shadow-sm">
                  <th class="text-left px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.mawb') }}</th>
                  <th class="text-left px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.shipperConsignee') }}</th>
                  <th class="text-right px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.pcsReserved') }}</th>
                  <th class="text-right px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.pcsReceived') }}</th>
                  <th class="text-right px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.kg') }}</th>
                  <th class="text-right px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.lbs') }}</th>
                  <th class="text-right px-3 py-2.5 font-black uppercase tracking-wider border-r border-slate-500 whitespace-nowrap">{{ t('mawbs.columns.pcsDispatched') }}</th>
                  <th class="text-center px-3 py-2.5 font-black uppercase tracking-wider whitespace-nowrap">{{ t('mawbs.columns.status') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in estadosFilteredRows" :key="row.mawbId"
                  class="border-b border-slate-100 hover:bg-slate-50 transition-colors">
                  <td class="px-3 py-2.5 border-r border-slate-300 whitespace-nowrap cursor-pointer"
                    :class="mawbStatusClass(row)"
                    @click="openInfoPanel(row)">
                    <span class="underline decoration-dotted underline-offset-2 text-[13px]">{{ row.awbNumber || '—' }}</span>
                  </td>
                  <td class="px-3 py-2.5 text-slate-800 border-r border-slate-300 whitespace-nowrap">
                    <div class="flex flex-col leading-tight">
                      <span class="text-[13px]">{{ row.shipperName || '—' }}</span>
                      <span class="text-[13px] text-slate-500">/ {{ row.consigneeName || '—' }}</span>
                    </div>
                  </td>
                  <td class="px-3 py-2.5 text-right border-r border-slate-300 text-slate-950 tabular-nums">{{ row.reservedPieces || '—' }}</td>
                  <td class="px-3 py-2.5 text-right border-r border-slate-300 tabular-nums"
                    :class="row.pieceDiff !== 0 ? 'text-slate-600 bg-slate-50' : 'text-slate-950'">
                    {{ row.receivedPieces || '—' }}
                    <span v-if="row.receivedPieces > 0 && row.pieceDiff !== 0" class="text-[13px] text-slate-400 ml-0.5">&#9888;</span>
                  </td>
                  <td class="px-3 py-2.5 text-right text-slate-950 border-r border-slate-300 tabular-nums">{{ row.totalWeightKg ? Number(row.totalWeightKg).toLocaleString() : '—' }}</td>
                  <td class="px-3 py-2.5 text-right text-amber-700 border-r border-slate-300 tabular-nums font-semibold">{{ row.physicalWeightLbs != null ? Number(row.physicalWeightLbs).toLocaleString('en-US', { maximumFractionDigits: 1 }) : '—' }}</td>
                  <td class="px-3 py-2.5 text-right border-r border-slate-300 tabular-nums"
                    :class="row.hasDispatchedExcess ? 'text-slate-600' : 'text-slate-950'">
                    {{ row.pcsDispatched || '—' }}
                    <span v-if="row.hasDispatchedExcess" class="text-[13px] text-slate-500 ml-0.5">&#9888;</span>
                  </td>
                  <td class="px-3 py-2.5 text-center whitespace-nowrap">
                    <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[12px] font-bold"
                      :class="estadosBadgeClass(row.status)">
                      <span class="w-1.5 h-1.5 rounded-full" :class="mawbStatusDotClass(row.status)"></span>
                      {{ estadosLabel(row.status) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="bg-slate-100 border-t border-slate-300 px-4 py-1.5 text-[14px] text-slate-950 font-mono flex justify-between items-center shrink-0">
            <span>{{ t('mawbs.states.footerSummary', { n: estadosFilteredRows.length, pieces: estadosTotalPieces }) }}</span>
            <span class="flex items-center gap-3">
              <span v-for="(s, si) in statusOptions" :key="si" class="flex items-center gap-1">
                <span class="w-2 h-2 rounded-full" :class="s.dotClass"></span>
                {{ s.label }}: {{ s.count }}
              </span>
            </span>
          </div>
        </section>
      </div>
    </template>

    <!-- ============ LBS POR VUELO TAB ============ -->
    <template v-if="activeTab === 'lbs-vuelo'">
      <div class="flex items-end gap-2 shrink-0 border-b border-slate-200 pb-3 mb-3">
        <FilterBar
          v-model:date-from="wrDateFrom"
          v-model:date-to="wrDateTo"
          v-model:commodity="wrCommodity"
          v-model:mawb-number="wrSearchText"
          v-model:shipper-name="wrShipperFilter"
          v-model:consignee-name="wrConsigneeFilter"
          v-model:destination="wrDestFilter"
          :show-period-presets="true"
          :show-date-from="true"
          :show-date-to="true"
          :show-commodity="true"
          :show-mawb="true"
          :show-shipper="true"
          :show-consignee="true"
          :show-destination="true"
          :show-search-button="true"
          :show-clear="true"
          :loading="wrLoading"
          :search-button-label="t('mawbs.lbsTab.search')"
          @search="loadWeightReport"
          @clear="clearWeightReportFilters"
        />
        <button v-if="wrRows.length" @click="exportWrCsv" class="ds-btn-secondary mb-0.5">
          <span class="text-[14px] font-semibold leading-none">&#8595;</span> {{ t('mawbs.lbsTab.csv') }}
        </button>
      </div>

      <!-- Summary Cards -->
      <div v-if="wrSummary" class="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
        <div class="ds-card">
          <div class="ds-card-label">{{ t('mawbs.lbsTab.summary.totalRows') }}</div>
          <div class="ds-card-value">{{ wrSummary.totalRows }}</div>
        </div>
        <div class="ds-card">
          <div class="ds-card-label">{{ t('mawbs.lbsTab.summary.receivedPieces') }}</div>
          <div class="ds-card-value">{{ wrSummary.totalReceivedPieces }}</div>
        </div>
        <div class="ds-card">
          <div class="ds-card-label">{{ t('mawbs.lbsTab.summary.physicalWeightLbs') }}</div>
          <div class="ds-card-value text-emerald-700">{{ fmtNum(wrSummary.totalPhysicalWeightLbs) }}</div>
        </div>
        <div class="ds-card">
          <div class="ds-card-label">{{ t('mawbs.lbsTab.summary.dispatchedWeightLbs') }}</div>
          <div class="ds-card-value text-amber-700">{{ fmtNum(wrSummary.totalDispatchedWeightLbs) }}</div>
        </div>
      </div>

      <!-- Per-Commodity Breakdown -->
      <div v-if="wrSummary?.byCommodity && Object.keys(wrSummary.byCommodity).length > 1" class="mb-4">
        <div class="text-[12px] font-bold text-slate-500 uppercase tracking-wider mb-2">{{ t('mawbs.lbsTab.byCommodity') }}</div>
        <div class="flex flex-wrap gap-2">
          <div v-for="(data, code) in wrSummary.byCommodity" :key="code"
            class="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 bg-white text-[12px]">
            <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :style="{ background: commodityColor(code) }"></span>
            <span class="font-bold text-slate-800">{{ code }}</span>
            <span class="text-slate-400">|</span>
            <span class="text-slate-600">{{ t('mawbs.lbsTab.piecesUnit', { n: data.totalReceivedPieces }) }}</span>
            <span class="text-slate-400">|</span>
            <span class="font-semibold text-emerald-700">{{ fmtNum(data.totalPhysicalWeightLbs) }} lbs</span>
            <span class="text-slate-400">&#8594;</span>
            <span class="font-semibold text-amber-700">{{ t('mawbs.lbsTab.dispatchedLbs', { n: fmtNum(data.totalDispatchedWeightLbs) }) }}</span>
          </div>
        </div>
      </div>

      <!-- Data Table -->
      <section class="ds-table-section flex-1 min-h-0">
        <div class="overflow-auto flex-1 min-h-0 scrollbar-none" style="max-height:60vh">
          <table class="w-full border-collapse text-[12px] font-mono">
            <thead class="bg-slate-100 sticky top-0 z-10">
              <tr>
                <th class="text-left px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.awb') }}</th>
                <th class="text-left px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.shipper') }}</th>
                <th class="text-left px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.consignee') }}</th>
                <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.dest') }}</th>
                <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.commodity') }}</th>
                <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.flight') }}</th>
                <th class="text-center px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.date') }}</th>
                <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.pcsRec') }}</th>
                <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.lbsPhysical') }}</th>
                <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.lbsDisp') }}</th>
                <th class="text-right px-2 py-2 text-[11px] font-bold text-slate-600 border-b border-slate-200">{{ t('mawbs.lbsTab.table.pcsDisp') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in wrRows" :key="idx"
                class="hover:bg-blue-50/30 border-b border-slate-100">
                <td class="px-2 py-1.5 font-bold text-slate-900">{{ row.awbNumber }}</td>
                <td class="px-2 py-1.5 text-slate-600">{{ row.shipperName }}</td>
                <td class="px-2 py-1.5 text-slate-600">{{ row.consigneeName }}</td>
                <td class="text-center px-2 py-1.5">{{ row.destination }}</td>
                <td class="text-center px-2 py-1.5">
                  <span v-if="row.commodityType"
                    class="inline-block px-1.5 py-0.5 rounded text-[10px] font-bold"
                    :style="{ background: commodityColor(row.commodityType) + '18', color: commodityColor(row.commodityType) }">
                    {{ row.commodityType }}
                  </span>
                </td>
                <td class="text-center px-2 py-1.5 font-semibold">{{ row.flightNumber }}</td>
                <td class="text-center px-2 py-1.5">{{ row.flightDate }}</td>
                <td class="text-right px-2 py-1.5 tabular-nums">{{ row.receivedPieces }}</td>
                <td class="text-right px-2 py-1.5 tabular-nums font-semibold text-emerald-700">{{ fmtNum(row.physicalWeightLbs) }}</td>
                <td class="text-right px-2 py-1.5 tabular-nums font-semibold text-amber-700">{{ fmtNum(row.dispatchedWeightLbs) }}</td>
                <td class="text-right px-2 py-1.5 tabular-nums">{{ row.dispatchedPieces }}</td>
              </tr>
              <tr v-if="!wrRows.length && !wrLoading">
                <td colspan="11" class="text-center py-8 text-slate-400">{{ t('mawbs.lbsTab.noData') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, shallowRef, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import api from '../api/client'
import { receiptsApi } from '../api/receipts'
import { biApi } from '../api/bi'
import { downloadCSV } from '../utils/csv'
import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'
import LabelPrintModal from '../components/labels/LabelPrintModal.vue'
import FilterBar from '../components/FilterBar.vue'
import { useCommodities } from '../composables/useCommodities'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const store = useAppStore()
const toast = useToastStore()
const { t } = useI18n()
const { commodities: dbCommodities, loadCommodities } = useCommodities()

const showLabels = ref(false)
const labelItems = computed(() =>
  filteredRows.value.map(r => r.mawbId).filter(Boolean)
)

function airlineCodeById(airlineId) {
  const a = store.airlines.find(x => x.id === airlineId)
  return a?.code || 'AIR'
}

const activeTab = ref('matriz')
const localFlightId = ref(store.selectedFlightId || '')
const loadingMatrix = ref(false)
const highlightFlightId = ref(null)

// Weight report (lbs por vuelo) state
const wrDateFrom = ref('')
const wrDateTo = ref('')
const wrCommodity = ref('')
const wrSearchText = ref('')
const wrShipperFilter = ref('')
const wrConsigneeFilter = ref('')
const wrDestFilter = ref('')
const wrLoading = ref(false)
const wrRows = ref([])
const wrSummary = ref(null)

function fmtNum(v) {
  if (v == null) return '0'
  const n = Number(v)
  return isNaN(n) ? '0' : n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 1 })
}

function commodityColor(code) {
  const c = dbCommodities.value.find(x => x.code === code)
  return c?.color || '#6b7280'
}

function clearWeightReportFilters() {
  wrDateFrom.value = ''
  wrDateTo.value = ''
  wrCommodity.value = ''
  wrSearchText.value = ''
  wrShipperFilter.value = ''
  wrConsigneeFilter.value = ''
  wrDestFilter.value = ''
  loadWeightReport()
}

async function loadWeightReport() {
  wrLoading.value = true
  try {
    const params = {}
    if (wrDateFrom.value) params.dateFrom = wrDateFrom.value
    if (wrDateTo.value) params.dateTo = wrDateTo.value
    if (wrCommodity.value) params.commodityType = wrCommodity.value
    if (wrSearchText.value) params.awbNumber = wrSearchText.value
    if (wrShipperFilter.value) params.shipperName = wrShipperFilter.value
    if (wrConsigneeFilter.value) params.consigneeName = wrConsigneeFilter.value
    if (wrDestFilter.value) params.destination = wrDestFilter.value
    const [rowsRes, sumRes] = await Promise.all([
      biApi.getWeightReport(params),
      biApi.getWeightSummary(params),
    ])
    wrRows.value = rowsRes.data
    wrSummary.value = sumRes.data
  } catch (e) {
    console.error('Weight report error:', e)
    wrRows.value = []
    wrSummary.value = null
  } finally {
    wrLoading.value = false
  }
}

function exportWrCsv() {
  if (!wrRows.value.length) return
  const headers = [
    t('mawbs.lbsTab.csvHeaders.awb'),
    t('mawbs.lbsTab.csvHeaders.shipper'),
    t('mawbs.lbsTab.csvHeaders.consignee'),
    t('mawbs.lbsTab.csvHeaders.dest'),
    t('mawbs.lbsTab.csvHeaders.commodity'),
    t('mawbs.lbsTab.csvHeaders.flight'),
    t('mawbs.lbsTab.csvHeaders.date'),
    t('mawbs.lbsTab.csvHeaders.pcsRec'),
    t('mawbs.lbsTab.csvHeaders.lbsPhysical'),
    t('mawbs.lbsTab.csvHeaders.lbsDisp'),
    t('mawbs.lbsTab.csvHeaders.pcsDisp'),
  ]
  const csvRows = [headers.join(',')]
  for (const r of wrRows.value) {
    csvRows.push([
      r.awbNumber, r.shipperName, r.consigneeName, r.destination,
      r.commodityType, r.flightNumber, r.flightDate,
      r.receivedPieces, r.physicalWeightLbs, r.dispatchedWeightLbs, r.dispatchedPieces
    ].map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))
  }
  const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${t('mawbs.lbsTab.csvFilename')}-${new Date().toISOString().slice(0,10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
const showFilter = ref(false)
const filterText = ref('')
const commodityFilter = ref('')
const hoverFlightCol = ref(null)
const showMiniMap = ref(false)
const infoPanel = reactive({ show: false, row: null })
function openInfoPanel(row) {
  infoPanel.row = row
  infoPanel.show = true
}
function closeInfoPanel() {
  infoPanel.show = false
  infoPanel.row = null
}
function flightLabel(flightId) {
  const f = store.flights.find(f => f.id === flightId)
  return f ? `${airlineCodeById(f.airlineId)}-${f.flightNumber} ${formatDate(f.flightDate)}` : flightId.slice(0, 8)
}
function copyToClipboard(text) {
  navigator.clipboard?.writeText(text)
  toast.success(t('common.copied', { text }))
}
infoPanel.flightLabel = flightLabel
const scrollContainer = ref(null)
const tableSectionRef = ref(null)
const scrollPos = ref({ top: 0, left: 0 })
const timelinePeriod = ref('month')
const rangeStartSeg = ref('')
const rangeEndSeg = ref('')
const showPeriodMenu = ref(false)
const hidePastDates = ref(false)
function toggleHidePast() {
  hidePastDates.value = !hidePastDates.value
  buildMatrix()
}
const periodOptions = [
  { value: 'year' },
  { value: 'quarter' },
  { value: 'month' },
  { value: 'week' },
  { value: 'day' },
]

const periodLabel = computed(() => {
  const p = periodOptions.find(x => x.value === timelinePeriod.value)
  return p ? t('mawbs.periods.' + p.value) : timelinePeriod.value
})

const matrixRows = shallowRef([])
const flightColumns = shallowRef([])
const stickyOffsets = ref([0, 180, 380, 470, 560, 650, 740])

// Column resize state
const defaultColWidths = [180, 200, 90, 90, 90, 90, 80]
const colWidths = reactive({})
let resizeColIndex = null
let resizeStartX = 0
let resizeStartWidth = 0

function startColResize(colIdx, e) {
  resizeColIndex = colIdx
  resizeStartX = e.clientX
  resizeStartWidth = colWidths[colIdx] || (colIdx < defaultColWidths.length ? defaultColWidths[colIdx] : 100)
  document.addEventListener('pointermove', onColResize)
  document.addEventListener('pointerup', stopColResize)
  e.preventDefault()
}

function onColResize(e) {
  if (resizeColIndex === null) return
  const diff = e.clientX - resizeStartX
  let newWidth = Math.max(50, resizeStartWidth + diff)
  colWidths[resizeColIndex] = newWidth
  // Update sticky offsets for sticky columns (0-7)
  if (resizeColIndex <= 7) {
    const offsets = [0]
    for (let i = 0; i < 7; i++) {
      offsets.push(offsets[i] + (colWidths[i] || defaultColWidths[i]))
    }
    stickyOffsets.value = offsets
  }
}

function stopColResize() {
  resizeColIndex = null
  document.removeEventListener('pointermove', onColResize)
  document.removeEventListener('pointerup', stopColResize)
}

function colStyle(colIdx) {
  const w = colWidths[colIdx]
  if (w) return { width: w + 'px', minWidth: w + 'px' }
  if (colIdx < defaultColWidths.length) return { width: defaultColWidths[colIdx] + 'px', minWidth: defaultColWidths[colIdx] + 'px' }
  return { width: '100px', minWidth: '100px' }
}

const filterTypeLabel = computed(() => {
  const f = filterText.value
  if (!f) return ''
  if (f.includes('+')) return t('mawbs.filterTypes.multi')
  if (f.startsWith('=')) return t('mawbs.filterTypes.exact')
  if (f.startsWith('>')) return t('mawbs.filterTypes.greater')
  if (f.startsWith('<')) return t('mawbs.filterTypes.less')
  if (f.startsWith('*') || f.endsWith('*')) return t('mawbs.filterTypes.contains')
  if (/^\d+$/.test(f)) return t('mawbs.filterTypes.num')
  return t('mawbs.filterTypes.text')
})

const filteredRows = computed(() => {
  let rows = matrixRows.value
  if (commodityFilter.value) {
    rows = rows.filter(r => r.commodityType === commodityFilter.value)
  }
  const q = filterText.value.trim()
  if (!q) return rows

  // Multi-shipper mode: split by +, OR match across shipper/consignee/awb
  if (q.includes('+')) {
    let searchStr = q
    if (searchStr.startsWith('*') && searchStr.endsWith('*'))
      searchStr = searchStr.slice(1, -1)
    const segments = searchStr.split('+').map(s => s.trim().toLowerCase()).filter(Boolean)
    if (!segments.length) return rows
    return rows.filter(row => {
      const shipper = (row.shipperName || '').toLowerCase()
      const consignee = (row.consigneeName || '').toLowerCase()
      const awb = (row.awbNumber || '').toLowerCase()
      return segments.some(seg =>
        shipper.includes(seg) || consignee.includes(seg) || awb.includes(seg)
      )
    })
  }

  let op = 'contains'
  let val = q

  if (q.startsWith('=')) { op = 'exact'; val = q.slice(1) }
  else if (q.startsWith('>')) { op = 'gt'; val = q.slice(1) }
  else if (q.startsWith('<')) { op = 'lt'; val = q.slice(1) }
  else if (q.startsWith('*') && q.endsWith('*')) { op = 'contains'; val = q.slice(1, -1) }
  else if (q.startsWith('*')) { op = 'ends'; val = q.slice(1) }
  else if (q.endsWith('*')) { op = 'starts'; val = q.slice(0, -1) }

  const isNumeric = /^-?\d+(\.\d+)?$/.test(val)

  return rows.filter(row => {
    if (op === 'gt' && isNumeric) {
      const n = parseFloat(val)
      return row.totalPieces > n || (row.totalWeightKg && parseFloat(row.totalWeightKg) > n)
    }
    if (op === 'lt' && isNumeric) {
      const n = parseFloat(val)
      return row.totalPieces < n || (row.totalWeightKg && parseFloat(row.totalWeightKg) < n)
    }
    if (op === 'exact') {
      const lower = val.toLowerCase()
      return (row.awbNumber && row.awbNumber.toLowerCase() === lower) ||
             (row.shipperName && row.shipperName.toLowerCase() === lower) ||
              (row.consigneeName && row.consigneeName.toLowerCase() === lower) ||
             (row.totalPieces && row.totalPieces.toString() === val)
    }
    if (op === 'starts') {
      const lower = val.toLowerCase()
      return (row.awbNumber && row.awbNumber.toLowerCase().startsWith(lower)) ||
             (row.shipperName && row.shipperName.toLowerCase().startsWith(lower)) ||
              (row.consigneeName && row.consigneeName.toLowerCase().startsWith(lower))
    }
    if (op === 'ends') {
      const lower = val.toLowerCase()
      return (row.awbNumber && row.awbNumber.toLowerCase().endsWith(lower)) ||
             (row.shipperName && row.shipperName.toLowerCase().endsWith(lower)) ||
              (row.consigneeName && row.consigneeName.toLowerCase().endsWith(lower))
    }
    if (isNumeric) {
      const n = parseFloat(val)
      return row.totalPieces === n || (row.totalPieces && row.totalPieces.toString().includes(val)) ||
             (row.awbNumber && row.awbNumber.includes(val))
    }
    const lower = val.toLowerCase()
    return (row.awbNumber && row.awbNumber.toLowerCase().includes(lower)) ||
           (row.shipperName && row.shipperName.toLowerCase().includes(lower)) ||
           (row.consigneeName && row.consigneeName.toLowerCase().includes(lower))
  })
})

const flightTotals = computed(() => {
  const totals = {}
  for (const row of filteredRows.value) {
    for (const f of flightColumns.value) {
      const pcs = row.cells[f.id] || 0
      totals[f.id] = (totals[f.id] || 0) + pcs
    }
  }
  return totals
})

const miniMapCells = computed(() => {
  const cells = []
  const rows = filteredRows.value
  const cols = flightColumns.value
  for (let ri = 0; ri < Math.min(rows.length, 30); ri++) {
    const row = rows[ri]
    for (let ci = 0; ci < Math.min(cols.length, 30); ci++) {
      const col = cols[ci]
      const pcs = row.cells[col.id] || 0
      cells.push({ active: pcs > 0, label: `${row.awbNumber} / ${airlineCodeById(col.airlineId)}-${col.flightNumber}: ${pcs} ${t('common.pcs')}` })
    }
  }
  return cells
})

const miniMapGridStyle = computed(() => {
  const cols = Math.min(flightColumns.value.length, 30)
  return { gridTemplateColumns: `repeat(${cols}, 1fr)`, gap: '1px' }
})

const miniMapViewportStyle = computed(() => {
  const el = scrollContainer.value
  if (!el) return {}
  const sh = el.scrollHeight || 1
  const sw = el.scrollWidth || 1
  const vh = el.clientHeight
  const vw = el.clientWidth
  void scrollPos.value
  const topPct = (el.scrollTop / sh) * 100
  const leftPct = (el.scrollLeft / sw) * 100
  const hPct = (vh / sh) * 100
  const wPct = (vw / sw) * 100
  return { top: `${topPct}%`, left: `${leftPct}%`, width: `${wPct}%`, height: `${hPct}%` }
})

function onScroll() {
  if (!showMiniMap.value) return
  scrollPos.value = { top: scrollContainer.value?.scrollTop || 0, left: scrollContainer.value?.scrollLeft || 0 }
}

async function buildMatrix() {
  loadingMatrix.value = true
  try {
    const params = {}
    if (store.selectedFlight?.airlineId) params.airlineId = store.selectedFlight.airlineId
    const [uldRes, linkRes] = await Promise.all([
      api.get('/ulds', { params }),
      api.get('/uld-awbs'),
    ])
    const ulds = uldRes.data
    const links = linkRes.data

    const uldFlightMap = {}
    const uldNumberMap = {}
    for (const u of ulds) {
      uldFlightMap[u.id] = u.flightId
      uldNumberMap[u.id] = u.uldNumber || u.id.slice(0, 8)
    }

    const receivedByMawb = {}
    for (const r of store.receipts || []) {
      const mawbId = r.mawbId
      if (!mawbId) continue
      const pc = r.pieceCount || 0
      // SUM de todos los recibos: cada recibo contiene un conjunto unico de piezas
      receivedByMawb[mawbId] = (receivedByMawb[mawbId] || 0) + pc
    }

    const bookingByMawb = {}
    for (const b of store.bookings) {
      if (b.mawbId) {
        if (!bookingByMawb[b.mawbId]) bookingByMawb[b.mawbId] = []
        bookingByMawb[b.mawbId].push(b)
      }
    }

    const mawbPcsByFlight = new Map()
    const mawbUldsByFlight = new Map()
    for (const link of links) {
      const mawbId = link.mawbId
      const flightId = uldFlightMap[link.uldId]
      const pcs = link.pieces || 0
      if (!mawbId || !flightId) continue
      if (!mawbPcsByFlight.has(mawbId)) mawbPcsByFlight.set(mawbId, new Map())
      const fm = mawbPcsByFlight.get(mawbId)
      fm.set(flightId, (fm.get(flightId) || 0) + pcs)
      if (!mawbUldsByFlight.has(mawbId)) mawbUldsByFlight.set(mawbId, new Map())
      const um = mawbUldsByFlight.get(mawbId)
      if (!um.has(flightId)) um.set(flightId, [])
      um.get(flightId).push({ uldNumber: uldNumberMap[link.uldId] || 'ULD?', pieces: pcs })
    }

    const flightIds = new Set()
    for (const fm of mawbPcsByFlight.values())
      for (const fid of fm.keys()) flightIds.add(fid)

    let filteredMawbs = store.mawbs
    if (localFlightId.value) {
      filteredMawbs = store.mawbs.filter(m => m.flight?.id === localFlightId.value || m.flightId === localFlightId.value)
      const extraFids = new Set()
      for (const m of filteredMawbs) {
        const fm = mawbPcsByFlight.get(m.id)
        if (fm) for (const fid of fm.keys()) extraFids.add(fid)
      }
      flightIds.clear()
      flightIds.add(localFlightId.value)
      for (const fid of extraFids) flightIds.add(fid)
    }

    let flights = store.flights.filter(f => flightIds.has(f.id))
    if (rangeStartSeg.value || rangeEndSeg.value) {
      const keys = timelineSegments.value.filter(s => isInRange(s.value)).map(s => s.value)
      const dates = []
      for (const k of keys) {
        const dr = getPeriodDateRange(timelinePeriod.value, k)
        if (dr.start) dates.push(dr)
      }
      if (dates.length) {
        const globalStart = dates.reduce((a, d) => d.start < a ? d.start : a, dates[0].start)
        const globalEnd = dates.reduce((a, d) => d.end > a ? d.end : a, dates[0].end)
        flights = flights.filter(f => {
          if (!f.flightDate) return false
          return f.flightDate >= globalStart && f.flightDate <= globalEnd
        })
      }
    }
    if (hidePastDates.value) {
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const todayStr = today.toISOString().split('T')[0]
      flights = flights.filter(f => f.flightDate >= todayStr)
    }
    flights.sort((a, b) => (a.flightDate || '').localeCompare(b.flightDate || '') || (a.flightNumber || '').localeCompare(b.flightNumber || ''))

    const rows = filteredMawbs.map(m => {
      const fm = mawbPcsByFlight.get(m.id) || new Map()
      const um = mawbUldsByFlight.get(m.id) || new Map()
      const cells = {}
      const uldCountByFlight = {}
      const breakdown = {}
      let pcsDispatched = 0
      for (const f of flights) {
        const pcs = fm.get(f.id) || 0
        cells[f.id] = pcs
        pcsDispatched += pcs
        uldCountByFlight[f.id] = (um.get(f.id) || []).length
        breakdown[f.id] = um.get(f.id) || []
      }
      const receivedPieces = receivedByMawb[m.id] || 0
      const bookingList = bookingByMawb[m.id]
      const reservedPieces = bookingList && bookingList.length > 0
        ? Math.max(...bookingList.map(b => b.skids || 0))
        : (m.pieces || 0)
      return {
        mawbId: m.id,
        awbNumber: m.awbNumber,
        shipperName: m.shipperName,
        consigneeName: m.consigneeName,
        destination: m.destination,
        commodityType: m.commodityType || null,
        totalPieces: receivedPieces || pcsDispatched || m.pieces || 0,
        totalWeightKg: m.reportedWeightKg || m.chargeableWeightKg || null,
        physicalWeightLbs: m.reportedWeightKg ? (Number(m.reportedWeightKg) * 2.20462) : (m.chargeableWeightKg ? (Number(m.chargeableWeightKg) * 2.20462) : null),
        status: m.status,
        reservedPieces,
        receivedPieces,
        pieceDiff: receivedPieces - reservedPieces,
        hasDispatchedExcess: receivedPieces > 0 && pcsDispatched > receivedPieces,
        pcsDispatched,
        cells,
        uldCountByFlight,
        breakdownByFlight: breakdown,
      }
    })

    matrixRows.value = rows
    flightColumns.value = flights
    await nextTick()
    computeStickyOffsets()
  } catch (e) {
    toast.error(extractError(e))
    console.error('Matrix error:', e)
  } finally {
    loadingMatrix.value = false
  }
}

function computeStickyOffsets() {
  const el = scrollContainer.value
  if (!el) return
  const ths = el.querySelectorAll('thead tr th')
  if (!ths.length) return
  const offsets = []
  let cum = 0
  for (let i = 0; i < ths.length && i <= 7; i++) {
    offsets.push(cum)
    cum += ths[i].offsetWidth
  }
  while (offsets.length <= 7) offsets.push(offsets[offsets.length - 1] || 0)
  stickyOffsets.value = offsets
}

// ── Estados tab ──
const statusOptions = computed(() => {
  const counts = { BOOKED: 0, RECEIVED: 0, MANIFESTED: 0, DEPARTED: 0 }
  for (const r of matrixRows.value) {
    const s = r.status || 'BOOKED'
    if (counts[s] !== undefined) counts[s]++
  }
  const labels = { BOOKED: t('mawbs.rowStatus.BOOKED'), RECEIVED: t('mawbs.rowStatus.RECEIVED'), MANIFESTED: t('mawbs.rowStatus.MANIFESTED'), DEPARTED: t('mawbs.rowStatus.DEPARTED') }
  const dots = { BOOKED: 'bg-slate-400', RECEIVED: 'bg-slate-500', MANIFESTED: 'bg-slate-500', DEPARTED: 'bg-slate-700' }
  return Object.entries(counts).map(([value, count]) => ({
    value, label: labels[value] || value, count, dotClass: dots[value] || 'bg-slate-400'
  }))
})

const estadosStatusFilter = ref(['BOOKED', 'RECEIVED', 'MANIFESTED', 'DEPARTED'])

function toggleStatusFilter(value) {
  const idx = estadosStatusFilter.value.indexOf(value)
  if (idx >= 0) {
    if (estadosStatusFilter.value.length > 1) estadosStatusFilter.value.splice(idx, 1)
  } else {
    estadosStatusFilter.value.push(value)
  }
}

const estadosFilteredRows = computed(() =>
  matrixRows.value.filter(r => estadosStatusFilter.value.includes(r.status || 'BOOKED'))
)

const estadosTotalPieces = computed(() =>
  estadosFilteredRows.value.reduce((s, r) => s + (r.totalPieces || 0), 0)
)

function estadosBadgeClass(status) {
  const base = 'bg-slate-50 text-slate-700 border border-slate-300'
  if (!status || status === 'BOOKED') return 'bg-slate-100 text-slate-600 border-slate-300'
  if (status === 'RECEIVED') return 'bg-slate-100 text-slate-700 border-slate-400'
  if (status === 'MANIFESTED') return 'bg-slate-200 text-slate-700 border-slate-500'
  if (status === 'DEPARTED') return 'bg-slate-700 text-white border-slate-800'
  return base
}

function mawbStatusDotClass(status) {
  if (!status || status === 'BOOKED') return 'bg-slate-400'
  if (status === 'RECEIVED') return 'bg-slate-500'
  if (status === 'MANIFESTED') return 'bg-slate-500'
  if (status === 'DEPARTED') return 'bg-slate-700'
  return 'bg-slate-400'
}

function estadosLabel(status) {
  if (!status || status === 'BOOKED') return t('mawbs.rowStatus.BOOKED')
  if (status === 'RECEIVED') return t('mawbs.rowStatus.RECEIVED')
  if (status === 'MANIFESTED') return t('mawbs.rowStatus.MANIFESTED')
  if (status === 'DEPARTED') return t('mawbs.rowStatus.DEPARTED')
  return status || '—'
}

function exportCSV() {
  const headers = [t('mawbs.columns.mawb'), t('common.shipper'), t('common.pieces'), t('common.kg'), t('common.status'), t('common.flight')]
  const rows = filteredRows.value.map(row => {
    const flights = flightColumns.value
        .filter(f => (row.cells[f.id] || 0) > 0)
        .map(f => `${airlineCodeById(f.airlineId)}-${f.flightNumber}`)
        .join('; ')
    return [
      row.awbNumber || '',
      row.shipperName || '',
      row.totalPieces || '',
      row.totalWeightKg || '',
      row.status || '',
      flights,
    ]
  })
  downloadCSV(headers, rows, `mawbs-${new Date().toISOString().slice(0, 10)}.csv`)
}

function getPeriodKey(dateStr, period) {
  const d = new Date(dateStr)
  switch (period) {
    case 'year': return String(d.getFullYear())
    case 'quarter': return `${d.getFullYear()}-Q${Math.floor(d.getMonth() / 3) + 1}`
    case 'month': {
      const ms = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
      return `${d.getFullYear()}-${ms[d.getMonth()]}`
    }
    case 'week': {
      const start = new Date(d.getTime())
      start.setHours(0,0,0,0)
      start.setDate(start.getDate() + 3 - ((start.getDay() + 6) % 7))
      const week = Math.round(((start.getTime() - new Date(start.getFullYear(),0,4).getTime()) / 86400000 + (new Date(start.getFullYear(),0,4).getDay() + 6) % 7) / 7)
      return `${d.getFullYear()}-W${String(week).padStart(2,'0')}`
    }
    case 'day': return dateStr
    default: return dateStr
  }
}

function getPeriodDateRange(period, key) {
  if (period === 'year') {
    return { start: `${key}-01-01`, end: `${key}-12-31` }
  }
  if (period === 'quarter') {
    const [, y, q] = key.match(/(\d+)-Q(\d)/)
    const sm = (parseInt(q) - 1) * 3 + 1
    const em = parseInt(q) * 3
    const lastDay = new Date(parseInt(y), em, 0).getDate()
    return { start: `${y}-${String(sm).padStart(2,'0')}-01`, end: `${y}-${String(em).padStart(2,'0')}-${String(lastDay).padStart(2,'0')}` }
  }
  if (period === 'month') {
    const [, y, m] = key.match(/(\d+)-(\w+)/)
    const ms = { Jan:'01',Feb:'02',Mar:'03',Apr:'04',May:'05',Jun:'06',Jul:'07',Aug:'08',Sep:'09',Oct:'10',Nov:'11',Dec:'12' }
    const mi = ms[m] || '01'
    const lastDay = new Date(parseInt(y), parseInt(mi), 0).getDate()
    return { start: `${y}-${mi}-01`, end: `${y}-${mi}-${String(lastDay).padStart(2,'0')}` }
  }
  if (period === 'week') {
    const [, y, w] = key.match(/(\d+)-W(\d+)/)
    const jan4 = new Date(parseInt(y), 0, 4)
    const start = new Date(jan4.getTime())
    start.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7) + (parseInt(w) - 1) * 7)
    const end = new Date(start.getTime())
    end.setDate(start.getDate() + 6)
    const fmt = d => d.toISOString().slice(0, 10)
    return { start: fmt(start), end: fmt(end) }
  }
  if (period === 'day') {
    return { start: key, end: key }
  }
  return { start: '', end: '' }
}

const timelineSegments = computed(() => {
  const flights = flightColumns.value
  if (!flights.length) return []
  const counts = new Map()
  for (const f of flights) {
    if (!f.flightDate) continue
    const k = getPeriodKey(f.flightDate, timelinePeriod.value)
    counts.set(k, (counts.get(k) || 0) + 1)
  }
  const sorted = [...counts.keys()].sort()
  return sorted.map(k => {
    let short = k
    if (timelinePeriod.value === 'quarter') short = k.replace(/^\d+-Q/, 'Q')
    else if (timelinePeriod.value === 'month') short = k.replace(/^\d+-/, '')
    else if (timelinePeriod.value === 'week') short = k.replace(/^\d+-W/, 'W')
    return { value: k, short, label: k, count: counts.get(k) || 0 }
  })
})

const segIndex = computed(() => {
  const m = {}
  timelineSegments.value.forEach((s, i) => { m[s.value] = i })
  return m
})

function isInRange(value) {
  if (!rangeStartSeg.value && !rangeEndSeg.value) return true
  const idx = segIndex.value[value]
  if (idx === undefined) return false
  const si = rangeStartSeg.value ? (segIndex.value[rangeStartSeg.value] ?? 0) : 0
  const ei = rangeEndSeg.value ? (segIndex.value[rangeEndSeg.value] ?? timelineSegments.value.length - 1) : timelineSegments.value.length - 1
  return idx >= Math.min(si, ei) && idx <= Math.max(si, ei)
}

const rangeLeftPct = computed(() => {
  if (!timelineSegments.value.length) return 0
  const start = rangeStartSeg.value ? (segIndex.value[rangeStartSeg.value] ?? 0) : 0
  return (start / timelineSegments.value.length) * 100
})

const rangeRightPct = computed(() => {
  if (!timelineSegments.value.length) return 100
  const end = rangeEndSeg.value ? (segIndex.value[rangeEndSeg.value] ?? timelineSegments.value.length - 1) : timelineSegments.value.length - 1
  return ((end + 1) / timelineSegments.value.length) * 100
})

const rangeBarStyle = computed(() => {
  return { left: rangeLeftPct.value + '%', width: (rangeRightPct.value - rangeLeftPct.value) + '%' }
})

let rangeDragging = null

function setTimelinePeriod(period) {
  timelinePeriod.value = period
  rangeStartSeg.value = ''
  rangeEndSeg.value = ''
  showPeriodMenu.value = false
  buildMatrix()
}

function toggleRangeSegment(value) {
  const segs = timelineSegments.value
  if (!segs.length) return
  if (!rangeStartSeg.value || (rangeStartSeg.value && rangeEndSeg.value)) {
    rangeStartSeg.value = value
    rangeEndSeg.value = ''
  } else {
    const si = segIndex.value[rangeStartSeg.value]
    const ei = segIndex.value[value]
    if (ei < si) {
      rangeEndSeg.value = rangeStartSeg.value
      rangeStartSeg.value = value
    } else {
      rangeEndSeg.value = value
    }
  }
  buildMatrix()
}

function startRangeDrag(side, e) {
  rangeDragging = side
  const onMove = (ev) => {
    if (!rangeDragging || !timelineSegments.value.length) return
    const bar = e.currentTarget?.parentElement?.parentElement
    const container = bar?.querySelector?.('.h-6') || bar?.closest?.('[style*="min-width"]')
    if (!container) return
    const rect = container.getBoundingClientRect()
    const pct = Math.max(0, Math.min(100, ((ev.clientX - rect.left) / rect.width) * 100))
    const idx = Math.round((pct / 100) * (timelineSegments.value.length - 1))
    const seg = timelineSegments.value[Math.max(0, Math.min(idx, timelineSegments.value.length - 1))]
    if (!seg) return
    if (rangeDragging === 'left') {
      rangeStartSeg.value = seg.value
    } else {
      rangeEndSeg.value = seg.value
    }
  }
  const onUp = () => {
    rangeDragging = null
    document.removeEventListener('pointermove', onMove)
    document.removeEventListener('pointerup', onUp)
    buildMatrix()
  }
  document.addEventListener('pointermove', onMove)
  document.addEventListener('pointerup', onUp)
}

function clearTimeline() {
  rangeStartSeg.value = ''
  rangeEndSeg.value = ''
  buildMatrix()
}

function getPieces(row, flight) { return row.cells[flight.id] || 0 }

function cellClasses(row, flight) {
  const pcs = getPieces(row, flight)
  if (!pcs) return ''
  const base = 'relative'
  const colGlow = hoverFlightCol.value === flight.id ? 'ring-1 ring-inset ring-slate-300' : ''
  return `${base} bg-white text-slate-950 ${colGlow}`
}

const arcCircum = 2 * Math.PI * 5.5

function arcOffset(row, flight) {
  const pcs = getPieces(row, flight)
  const total = row.totalPieces || 1
  const pct = Math.min(pcs / total, 1)
  return arcCircum * (1 - pct)
}

function rowBgClass(_row) {
  return 'hover:bg-slate-50'
}

function mawbStatusClassRaw(status) {
  if (!status || status === 'BOOKED') return 'bg-slate-500'
  if (status === 'RECEIVED') return 'bg-slate-600'
  if (status === 'MANIFESTED') return 'bg-slate-500'
  if (status === 'DEPARTED') return 'bg-slate-700'
  return 'bg-slate-400'
}

function mawbStatusClass(row) {
  const base = 'bg-white px-2 py-2.5 border-r border-slate-300 truncate max-w-[180px] cursor-pointer transition-colors duration-150'
  if (row.hasDispatchedExcess) return `${base} text-slate-700 bg-slate-50/80 hover:bg-slate-100 border-l-4 border-l-slate-500`
  const s = row.status
  if (!s || s === 'BOOKED') return `${base} text-slate-600 bg-slate-50 hover:bg-slate-100 border-l-4 border-l-slate-400`
  if (s === 'RECEIVED') return `${base} text-slate-700 bg-slate-50/60 hover:bg-slate-100 border-l-4 border-l-slate-500`
  if (s === 'MANIFESTED') return `${base} text-slate-600 bg-slate-50/60 hover:bg-slate-100 border-l-4 border-l-slate-400`
  if (s === 'DEPARTED') return `${base} text-slate-800 bg-slate-50/60 hover:bg-slate-100 border-l-4 border-l-slate-600`
  return `${base} hover:bg-slate-50`
}

function statusTitle(row) {
  if (row.hasDispatchedExcess) return t('mawbs.tooltip.dispatchExcess')
  const s = row.status
  if (!s || s === 'BOOKED') return t('mawbs.tooltip.goReceiptBooked')
  if (s === 'RECEIVED') return t('mawbs.tooltip.goReceiptReceived')
  if (s === 'MANIFESTED') return t('mawbs.tooltip.goReceiptManifested')
  if (s === 'DEPARTED') return t('mawbs.tooltip.goReceiptDeparted')
  return t('mawbs.tooltip.goReceipt')
}

function uldTooltip(row, flight) {
  const pcs = getPieces(row, flight)
  const uldCount = row.uldCountByFlight?.[flight.id] || 0
  if (!pcs) return ''
  const pcsLabel = `UP${pcs !== 1 ? 'S' : ''}`
  const uldLabel = `ULD${uldCount !== 1 ? 'S' : ''}`
  return t('mawbs.tooltip.distributed', { pcs, pcsLabel, ulds: uldCount, uldLabel })
}

function formatDate(d) {
  if (!d) return '—'
  const parts = d.split('-')
  return parts.length === 3 ? parts[1] + '/' + parts[2] : d
}

function goToReceipt(row) {
  closeInfoPanel()
  router.push({ name: 'receipts', query: { mawbId: row.mawbId } })
}

function receiptForMawb(row) {
  if (!row || !store.receipts) return null
  return store.receipts.find(r => r.mawbId === row.mawbId) || null
}

function downloadReceiptXlsx(row) {
  const rec = receiptForMawb(row)
  if (!rec) { toast.warning(t('mawbs.toast.noReceipt')); return }
  const vTag = '-V' + (rec.correctionNumber ?? 1)
  receiptsApi.export(rec.id).then(res => {
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = 'RECIBO_DE_BODEGA_AWB ' + (row.awbNumber || '—') + vTag + '.xlsx'
    a.click(); URL.revokeObjectURL(url)
  }).catch(e => toast.error(t('mawbs.toast.excelError', { error: extractError(e) })))
}

function downloadReceiptPdf(row) {
  const rec = receiptForMawb(row)
  if (!rec) { toast.warning(t('mawbs.toast.noReceipt')); return }
  const vTag = '-V' + (rec.correctionNumber ?? 1)
  receiptsApi.getFullPdf(rec.id).then(res => {
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = 'RECIBO_DE_BODEGA_AWB ' + (row.awbNumber || '—') + vTag + '.pdf'
    a.click(); URL.revokeObjectURL(url)
  }).catch(e => toast.error(t('mawbs.toast.pdfError', { error: extractError(e) })))
}

function downloadEvidenceHtml(row) {
  const rec = receiptForMawb(row)
  if (!rec) { toast.warning(t('mawbs.toast.noReceipt')); return }
  receiptsApi.getSupportingDocsHtml(rec.id).then(res => {
    const blob = new Blob([res.data], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = 'EVIDENCIAS_' + (row.awbNumber || '—') + '.html'
    a.click(); URL.revokeObjectURL(url)
  }).catch(e => toast.error(t('mawbs.toast.evidenceError', { error: extractError(e) })))
}

function downloadEvidencePdf(row) {
  const rec = receiptForMawb(row)
  if (!rec) { toast.warning(t('mawbs.toast.noReceipt')); return }
  receiptsApi.getSupportingDocsPdf(rec.id).then(res => {
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = 'EVIDENCIAS_' + (row.awbNumber || '—') + '.pdf'
    a.click(); URL.revokeObjectURL(url)
  }).catch(e => toast.error(t('mawbs.toast.evidencePdfError', { error: extractError(e) })))
}

function startResize(e) {
  const section = tableSectionRef.value
  if (!section) return
  const startY = e.clientY
  const startHeight = section.offsetHeight
  const parent = section.parentElement
  const maxH = parent ? parent.clientHeight - 20 : 800
  const onMove = (ev) => {
    const delta = ev.clientY - startY
    const newH = Math.min(maxH, Math.max(180, startHeight + delta))
    section.style.flex = 'none'
    section.style.height = newH + 'px'
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function scrollToFlight(flightId) {
  const el = scrollContainer.value
  if (!el) return
  const th = el.querySelector('thead tr th')
  if (!th) return
  const headerCells = Array.from(el.querySelectorAll('thead tr th'))
  const idx = headerCells.findIndex(c => {
    const text = c.textContent
    return text && text.includes(flightId.slice(0, 8))
  })
    if (idx >= 6) {
    const prevCells = headerCells.slice(6, idx)
    let offset = stickyOffsets.value[7] || stickyOffsets.value[6] || 550
    for (const c of prevCells) offset += c.offsetWidth || 84
    el.scrollTo({ left: offset - 20, behavior: 'smooth' })
  }
}

const dataStatus = computed(() => {
  const rows = filteredRows.value
  const totalReserved = rows.reduce((s, r) => s + (r.reservedPieces || 0), 0)
  const totalReceived = rows.reduce((s, r) => s + (r.receivedPieces || 0), 0)
  const totalDisp = rows.reduce((s, r) => s + (r.pcsDispatched || 0), 0)
  const tracked = rows.reduce((s, r) => s + Object.values(r.cells).reduce((a, b) => a + b, 0), 0)
  const departed = rows.filter(r => r.status === 'DEPARTED').length
  const overDisp = rows.filter(r => r.hasDispatchedExcess).length
  const diffCount = rows.filter(r => r.pieceDiff !== 0).length
  const flightsActive = store.flights.length
  const mawbsTotal = rows.length
  return [
    { label: t('mawbs.dataStatus.mawbs'), value: `${mawbsTotal}`, dotClass: 'bg-slate-950', dotStyle: {} },
    { label: t('mawbs.dataStatus.flights'), value: `${flightsActive}`, dotClass: 'bg-slate-500', dotStyle: {} },
    { label: t('mawbs.dataStatus.pzs'), value: `${tracked}`, dotClass: 'bg-slate-400', dotStyle: {} },
    { label: t('mawbs.dataStatus.res'), value: `${totalReserved}`, dotClass: 'bg-slate-400', dotStyle: {} },
    { label: t('mawbs.dataStatus.rec'), value: `${totalReceived}`, dotClass: 'bg-slate-500', dotStyle: {} },
    { label: t('mawbs.dataStatus.desp'), value: `${totalDisp}`, dotClass: overDisp ? 'bg-slate-400' : 'bg-slate-500', dotStyle: {} },
    { label: t('mawbs.dataStatus.departed'), value: `${departed}`, dotClass: 'bg-slate-400', dotStyle: {} },
    { label: t('mawbs.dataStatus.diff'), value: `${diffCount}`, dotClass: diffCount ? 'bg-slate-500' : 'bg-slate-300', dotStyle: {} },
  ]
})

const totalTracked = computed(() =>
  filteredRows.value.reduce((s, r) => s + Object.values(r.cells).reduce((a, b) => a + b, 0), 0)
)
const totalReserved = computed(() =>
  filteredRows.value.reduce((s, r) => s + (r.reservedPieces || 0), 0)
)
const totalReceived = computed(() =>
  filteredRows.value.reduce((s, r) => s + (r.receivedPieces || 0), 0)
)
const totalDispatched = computed(() =>
  filteredRows.value.reduce((s, r) => s + (r.pcsDispatched || 0), 0)
)
const activeCells = computed(() => {
  let c = 0
  for (const r of filteredRows.value)
    for (const v of Object.values(r.cells)) if (v > 0) c++
  return c
})

async function onFlightChange() {
  if (localFlightId.value) {
    store.selectedFlightId = localFlightId.value
    await store.loadMawbs(localFlightId.value)
  } else {
    store.selectedFlightId = null
    await store.loadAllMawbs()
  }
  await store.loadBookings(localFlightId.value || undefined)
  highlightFlightId.value = localFlightId.value || null
  await buildMatrix()
}

onMounted(async () => {
  await loadCommodities()
  if (!store.airlines.length) await store.loadAirlines()
  if (!store.flights.length) await store.loadFlights()
  await store.loadReceipts()
  await store.loadBookings()
  if (store.selectedFlightId) {
    localFlightId.value = store.selectedFlightId
    await store.loadMawbs(store.selectedFlightId)
  } else {
    await store.loadAllMawbs()
  }
  highlightFlightId.value = localFlightId.value || null
  await buildMatrix()
})
</script>

<style scoped>
.lp-scroll-x::-webkit-scrollbar { height: 8px; display: block; }
.lp-scroll-x::-webkit-scrollbar-track { background: #e2e8f0; border-radius: 4px; }
.lp-scroll-x::-webkit-scrollbar-thumb { background: #94a3b8; border-radius: 4px; }
.lp-scroll-x::-webkit-scrollbar-thumb:hover { background: #64748b; }
.lp-scroll-x { scrollbar-width: auto; scrollbar-color: #94a3b8 #e2e8f0; }
</style>
