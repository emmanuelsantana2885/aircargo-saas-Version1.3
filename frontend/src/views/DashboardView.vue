<template>
  <div class="ds-page">
    <header class="ds-section-header">
      <div>
        <h1 class="ds-title">{{ t('dashboard.title') }}</h1>
        <p class="ds-subtitle">{{ t('dashboard.subtitle') }}</p>
      </div>
      <div class="flex items-center gap-2 text-[12px] font-mono font-bold flex-wrap">
        <span class="ds-stat-chip">
          <span class="h-2 w-2 rounded-full" style="background: var(--accent)"></span> {{ t('dashboard.live') }}
        </span>
        <span class="ds-chip">{{ t('dashboard.flightsCount', { n: filteredFlights.length }) }}</span>
        <button @click="descargarReporte" class="ds-btn-primary">
         <span class="text-[14px] font-semibold leading-none">↓</span> {{ t('dashboard.downloadReport') }}
        </button>
      </div>
    </header>

    <div class="ds-tabs mb-2">
      <button @click="activeTab = 'flights'" class="ds-tab" :class="activeTab === 'flights' ? 'ds-tab-active' : ''">
        {{ t('dashboard.tabs.flights') }}
      </button>
      <button @click="activeTab = 'weight-report'" class="ds-tab" :class="activeTab === 'weight-report' ? 'ds-tab-active' : ''">
        {{ t('dashboard.tabs.weightReport') }}
      </button>
      <button @click="activeTab = 'builder'" class="ds-tab" :class="activeTab === 'builder' ? 'ds-tab-active' : ''">
        {{ t('dashboard.tabs.builder') }}
      </button>
    </div>

    <div v-if="activeTab === 'flights'" class="flex-1 min-h-0 flex flex-col gap-2">
    <FilterBar
      v-model:date-from="dateFrom"
      v-model:date-to="dateTo"
      :show-date-from="true"
      :show-date-to="true"
      :show-search="false"
      container-class="shrink-0"
    />
    <section class="grid grid-cols-1 sm:grid-cols-3 gap-3 shrink-0">
      <div class="ds-card border-l-emerald-500 flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center"><component :is="icons.Scale" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.totalNet') }}</div>
          <div class="ds-card-value text-emerald-700 truncate">{{ totalNetPayload }} <span class="text-[12px] font-semibold text-slate-400">{{ t('common.lbs') }}</span></div>
        </div>
      </div>
      <div class="ds-card border-l-blue-500 flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><component :is="icons.Package" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.totalUlds') }}</div>
          <div class="ds-card-value text-blue-700">{{ totalUldsCount }} <span class="text-[12px] font-semibold text-slate-400">{{ t('dashboard.uldShort') }}</span></div>
        </div>
      </div>
      <div class="ds-card border-l-slate-800 flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-slate-100 text-slate-700 flex items-center justify-center"><component :is="icons.PlaneDeparture" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.totalMawbs') }}</div>
          <div class="ds-card-value text-slate-900">{{ totalMawbsCount }} <span class="text-[12px] font-semibold text-slate-400">{{ t('dashboard.mawbShort') }}</span></div>
        </div>
      </div>
    </section>

    <section class="ds-table-section mb-0">
      <EmptyState v-if="loading" :title="t('dashboard.loadingData')" loading />
      <EmptyState v-else-if="filteredFlights.length === 0" :title="t('dashboard.noFlightsInRange')" :hint="t('dashboard.emptyHint')" :icon="icons.PlaneDeparture" />
      <div v-else ref="tableWrapper" class="overflow-auto flex-1 min-h-0 scrollbar-none">
        <div class="table-scroll-wrapper h-full">
        <table class="w-full border-collapse text-[13px] font-mono flight-table" :style="{ minWidth: tableMinWidth + 'px' }">
          <thead class="sticky top-0 z-20">
            <tr class="bg-slate-100 text-slate-700 text-[13px] font-bold uppercase tracking-wider border-b-2 border-slate-300 font-mono [&>th]:px-2 [&>th]:py-3 [&>th]:whitespace-nowrap">
              <th class="text-center px-2 py-3 whitespace-nowrap w-8 bg-slate-100 text-slate-400">#</th>
              <th class="text-center px-2 py-3 whitespace-nowrap w-8 bg-slate-100">
                <button @click="toggleAllExpanded" class="flex items-center justify-center gap-1 hover:opacity-70 transition"
                  :title="allExpanded ? t('dashboard.collapseAll') : t('dashboard.expandAll')">
                  <span class="text-[14px]">{{ allExpanded ? '▲' : '▼' }}</span>
                </button>
              </th>
              <th class="text-left px-2 py-3 whitespace-nowrap bg-slate-100 relative">
                <span @click="hf.toggleHeaderFilter('flight')" class="cursor-pointer select-none" :class="hf.columnFilters.flight ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.flight') }} <span class="text-[10px]" :class="hf.columnFilters.flight ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'flight'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[160px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('flight', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.flight ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.flight" :key="v" @click="hf.setColumnFilter('flight', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="hf.columnFilters.flight === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-3 whitespace-nowrap bg-slate-100 relative">
                <span @click="hf.toggleHeaderFilter('route')" class="cursor-pointer select-none" :class="hf.columnFilters.route ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.route') }} <span class="text-[10px]" :class="hf.columnFilters.route ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'route'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('route', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.route ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.route" :key="v" @click="hf.setColumnFilter('route', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="hf.columnFilters.route === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-3 whitespace-nowrap bg-slate-100 relative">
                <span @click="hf.toggleHeaderFilter('date')" class="cursor-pointer select-none" :class="hf.columnFilters.date ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.date') }} <span class="text-[10px]" :class="hf.columnFilters.date ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'date'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[160px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('date', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.date ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.date" :key="v" @click="hf.setColumnFilter('date', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="hf.columnFilters.date === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap relative">
                <span @click="hf.toggleHeaderFilter('status')" class="cursor-pointer select-none" :class="hf.columnFilters.status ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.status') }} <span class="text-[10px]" :class="hf.columnFilters.status ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'status'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[140px] text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('status', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.status ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.status" :key="v" @click="hf.setColumnFilter('status', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.status === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-16 relative">
                <span @click="hf.toggleHeaderFilter('ulds')" class="cursor-pointer select-none" :class="hf.columnFilters.ulds ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.ulds') }} <span class="text-[10px]" :class="hf.columnFilters.ulds ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'ulds'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('ulds', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.ulds ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.ulds" :key="v" @click="hf.setColumnFilter('ulds', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.ulds === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-2 py-2.5 whitespace-nowrap w-14 relative">
                <span @click="hf.toggleHeaderFilter('pos')" class="cursor-pointer select-none" :class="hf.columnFilters.pos ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.pos') }} <span class="text-[10px]" :class="hf.columnFilters.pos ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'pos'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('pos', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.pos ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.pos" :key="v" @click="hf.setColumnFilter('pos', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.pos === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 relative">
                <span @click="hf.toggleHeaderFilter('gross')" class="cursor-pointer select-none" :class="hf.columnFilters.gross ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.gross') }} <span class="text-[10px]" :class="hf.columnFilters.gross ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'gross'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('gross', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.gross ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.gross" :key="v" @click="hf.setColumnFilter('gross', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.gross === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 relative">
                <span @click="hf.toggleHeaderFilter('tare')" class="cursor-pointer select-none" :class="hf.columnFilters.tare ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.tare') }} <span class="text-[10px]" :class="hf.columnFilters.tare ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'tare'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('tare', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.tare ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.tare" :key="v" @click="hf.setColumnFilter('tare', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.tare === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 relative">
                <span @click="hf.toggleHeaderFilter('net')" class="cursor-pointer select-none" :class="hf.columnFilters.net ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.net') }} <span class="text-[10px]" :class="hf.columnFilters.net ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'net'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('net', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.net ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.net" :key="v" @click="hf.setColumnFilter('net', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.net === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-2 py-2.5 whitespace-nowrap w-24 text-emerald-600 relative">
                <span @click="hf.toggleHeaderFilter('payload')" class="cursor-pointer select-none" :class="hf.columnFilters.payload ? 'text-indigo-600' : 'hover:text-slate-900'">
                  {{ t('dashboard.table.payload') }} <span class="text-[10px]" :class="hf.columnFilters.payload ? 'opacity-100' : 'opacity-40'">&#9660;</span>
                </span>
                <div v-if="hf.headerFilterOpen === 'payload'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('payload', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.payload ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in flightUniq.payload" :key="v" @click="hf.setColumnFilter('payload', v)"
                    class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.payload === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <!-- Commodity columns - dynamic based on filtered flights -->
              <th v-for="c in visibleCommodities" :key="c.type"
                class="text-right px-2 py-2.5 whitespace-nowrap w-20 text-[12px]"
                :style="{ background: c.color + '20', borderLeft: '1px solid ' + c.color + '40' }"
                :title="c.label">
                <div class="flex items-center justify-end gap-1">
                  <span class="w-1.5 h-1.5 rounded-full" :style="{ background: c.color }"></span>
                  <span class="font-mono">{{ c.short }}</span>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(f, fi) in filteredFlights" :key="f.id">
              <tr class="border-b border-slate-100 transition-colors duration-150 hover:bg-slate-50/80"
                :class="{ 'bg-slate-50/50': isExpanded(f.id) }">
                <td class="text-center px-2 py-2 text-slate-400">{{ fi + 1 }}</td>
                <td class="text-center px-2 py-2">
                  <button @click="toggleExpand(f.id)"
                    class="flex items-center justify-center w-6 h-6 rounded hover:bg-slate-200 transition text-slate-500 hover:text-slate-900"
                    :aria-expanded="isExpanded(f.id)"
                    :title="isExpanded(f.id) ? t('dashboard.collapseDetail') : t('dashboard.expandDetail')">
                    <span class="text-[12px] transition-transform duration-200" :style="{ transform: isExpanded(f.id) ? 'rotate(180deg)' : '' }">▼</span>
                  </button>
                </td>
                <td class="px-2 py-2 font-mono text-slate-950">UPS-{{ f.flightNumber }}</td>
                <td class="text-center px-2 py-2 text-slate-700">{{ f.origin }}→{{ f.destination }}</td>
                <td class="text-center px-2 py-2 text-slate-500">{{ f.flightDate }}</td>
                <td class="text-center px-2 py-2">
                  <span class="inline-flex items-center gap-1">
                    <span :class="getStatusDot(f.status)" class="inline-block w-2 h-2 rounded-full"></span>
                    <span class="px-1.5 py-0.5 rounded text-[12px] font-medium" :style="statusStyle(f.status)">{{ statusLabel(f.status) }}</span>
                  </span>
                </td>
                <td class="text-center px-2 py-2 font-mono text-slate-900">{{ flightUlds(f.id).length }}</td>
                <td class="text-center px-2 py-2 font-mono text-slate-600">{{ flightPositions(f.id) }}<span class="text-slate-300">/</span>{{ f.totalPositions || '—' }}</td>
                <td class="text-right px-2 py-2 font-mono text-slate-950">{{ grossLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-mono text-slate-600">{{ totalTareLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-mono text-slate-900">{{ netLbs(f.id) }}</td>
                <td class="text-right px-2 py-2 font-bold text-emerald-700" style="font-family: 'SF Mono', 'Fira Code', monospace;">{{ payloadLbs(f.id) }}</td>
                <!-- Commodity payload columns -->
                <td v-for="c in visibleCommodities" :key="c.type"
                  class="text-right px-2 py-2 font-mono text-slate-900 tabular-nums"
                  :style="{ background: c.color + '08' }"
                  :title="commodityTooltip(f.id, c.type)">
                  {{ commodityPayload(f.id, c.type) || '—' }}
                </td>
              </tr>

              <!-- Drill-down row -->
              <tr v-show="isExpanded(f.id)" class="bg-slate-50/30 border-t border-slate-200">
                <td :colspan="13 + visibleCommodities.length" class="p-0">
                  <div class="p-3 md:p-4 border-t border-slate-200" style="animation: slideDown 0.2s ease-out;">
                    <FlightDetail :flight="f" :flight-id="f.id" />
                  </div>
                </td>
              </tr>
            </template>

            <!-- Totals row -->
            <tr class="bg-slate-50 border-t-2 border-slate-300 font-bold hover:bg-slate-100 transition-colors">
              <td class="text-center px-2 py-2 text-slate-400">Σ</td>
              <td class="text-center px-2 py-2"></td>
              <td class="px-2 py-2 text-slate-500 bg-slate-50">{{ t('dashboard.table.total') }}</td>
              <td class="text-center px-2 py-2 bg-slate-50"></td>
              <td class="text-center px-2 py-2 bg-slate-50"></td>
              <td class="text-center px-2 py-2"></td>
              <td class="text-center px-2 py-2">{{ totalUldsCount }}</td>
              <td class="text-center px-2 py-2">{{ totalPositionsAll }}<span class="text-slate-300">/</span>{{ totalMaxPositionsAll }}</td>
              <td class="text-right px-2 py-2">{{ totalGrossAll }}</td>
              <td class="text-right px-2 py-2">{{ totalTareAll }}</td>
              <td class="text-right px-2 py-2">{{ totalNetAll }}</td>
              <td class="text-right px-2 py-2 text-emerald-700">{{ totalNetPayload }}</td>
              <td v-for="c in visibleCommodities" :key="c.type"
                class="text-right px-2 py-2 text-slate-900 tabular-nums"
                :style="{ background: c.color + '15' }">
                {{ totalCommodityPayload(c.type) }}
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </section>
    </div>

    <div v-if="activeTab === 'weight-report'" class="flex-1 min-h-0 flex flex-col gap-2">
    <div class="flex items-end gap-2 shrink-0">
      <FilterBar
        v-model:date-from="wrDateFrom"
        v-model:date-to="wrDateTo"
        v-model:commodity="wrCommodity"
        v-model:search-text="wrFlightNumber"
        :show-period-presets="true"
        :show-date-from="true"
        :show-date-to="true"
        :show-commodity="true"
        :show-search="true"
        :show-search-button="true"
        :show-clear="true"
        :loading="wrLoading"
        :search-label="t('common.flight')"
        :search-placeholder="t('dashboard.wr.searchPlaceholder')"
        :search-button-label="t('common.search')"
        @search="loadWeightReport"
        @clear="wrDateFrom = ''; wrDateTo = ''; wrCommodity = ''; wrFlightNumber = ''; loadWeightReport()"
      />
      <button v-if="wrRows.length" @click="exportWeightCSV" class="ds-btn-secondary mb-0.5">
        <span class="text-[14px] font-semibold leading-none">&#8595;</span> {{ t('dashboard.wr.exportCsv') }}
      </button>
    </div>

    <!-- Summary Cards -->
    <div v-if="wrSummary" class="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
      <div class="ds-card flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-slate-100 text-slate-700 flex items-center justify-center"><component :is="icons.LayoutGrid" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.totalRows') }}</div>
          <div class="ds-card-value text-slate-900">{{ wrSummary.totalRows }}</div>
        </div>
      </div>
      <div class="ds-card border-l-blue-500 flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center"><component :is="icons.Package" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.receivedPieces') }}</div>
          <div class="ds-card-value text-blue-700">{{ wrSummary.totalReceivedPieces }}</div>
        </div>
      </div>
      <div class="ds-card border-l-emerald-500 flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center"><component :is="icons.Scale" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.physicalWeightLbs') }}</div>
          <div class="ds-card-value text-emerald-700">{{ formatNum(wrSummary.totalPhysicalWeightLbs) }}</div>
        </div>
      </div>
      <div class="ds-card border-l-amber-500 flex items-center gap-3">
        <span class="shrink-0 w-9 h-9 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center"><component :is="icons.PlaneDeparture" :size="18" :stroke-width="2.2" /></span>
        <div class="min-w-0">
          <div class="ds-card-label">{{ t('dashboard.wr.summary.dispatchedWeightLbs') }}</div>
          <div class="ds-card-value text-amber-700">{{ formatNum(wrSummary.totalDispatchedWeightLbs) }}</div>
        </div>
      </div>
    </div>

    <!-- Per-Commodity Breakdown -->
    <div v-if="wrSummary?.byCommodity && Object.keys(wrSummary.byCommodity).length > 1" class="mb-4">
      <div class="text-[12px] font-bold text-slate-500 uppercase tracking-wider mb-2">{{ t('dashboard.wr.byCommodity') }}</div>
      <div class="flex flex-wrap gap-2">
        <div v-for="(data, code) in wrSummary.byCommodity" :key="code"
          class="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 bg-white text-[12px] shadow-sm">
          <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :style="{ background: commodityColor(code) }"></span>
          <span class="font-bold text-slate-800">{{ code }}</span>
          <span class="text-slate-400">|</span>
          <span class="text-slate-600">{{ t('dashboard.wr.piecesUnit', { n: data.totalReceivedPieces }) }}</span>
          <span class="text-slate-400">|</span>
          <span class="font-semibold text-emerald-700">{{ formatNum(data.totalPhysicalWeightLbs) }} {{ t('common.lbs') }}</span>
          <span class="text-slate-400">→</span>
          <span class="font-semibold text-amber-700">{{ formatNum(data.totalDispatchedWeightLbs) }} {{ t('common.lbs') }}</span>
          <span class="text-slate-300">·</span>
          <span class="w-px h-3 bg-slate-200"></span>
          <span class="text-[11px] text-slate-500">{{ data.totalDispatchedPieces }} {{ t('common.pcs') }}</span>
        </div>
      </div>
    </div>

    <!-- Weight Report Table -->
    <section class="ds-table-section">
      <EmptyState v-if="wrLoading && !wrRows.length" :title="t('common.loading')" loading />
      <EmptyState v-else-if="!wrRows.length" :title="t('dashboard.wr.noData')" :icon="icons.Gauge" />
      <div v-else class="overflow-auto flex-1 min-h-0 scrollbar-none">
        <table class="w-full border-collapse text-[12px] font-mono">
          <thead class="bg-slate-100 text-slate-700 sticky top-0 z-10">
            <tr>
              <th class="text-left px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_awb')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_awb ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.awb') }} <span class="text-[10px]" :class="hf.columnFilters.wr_awb ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_awb'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[160px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_awb', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.wr_awb ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.awb" :key="v" @click="hf.setColumnFilter('wr_awb', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="hf.columnFilters.wr_awb === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-left px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_shipper')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_shipper ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.shipper') }} <span class="text-[10px]" :class="hf.columnFilters.wr_shipper ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_shipper'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_shipper', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.wr_shipper ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.shipper" :key="v" @click="hf.setColumnFilter('wr_shipper', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="hf.columnFilters.wr_shipper === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-left px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_consignee')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_consignee ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.consignee') }} <span class="text-[10px]" :class="hf.columnFilters.wr_consignee ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_consignee'" class="absolute top-full left-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[180px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_consignee', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold" :class="!hf.columnFilters.wr_consignee ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.consignee" :key="v" @click="hf.setColumnFilter('wr_consignee', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 truncate" :class="hf.columnFilters.wr_consignee === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_dest')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_dest ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.dest') }} <span class="text-[10px]" :class="hf.columnFilters.wr_dest ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_dest'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_dest', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_dest ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.dest" :key="v" @click="hf.setColumnFilter('wr_dest', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_dest === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_commodity')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_commodity ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.commodity') }} <span class="text-[10px]" :class="hf.columnFilters.wr_commodity ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_commodity'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[140px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_commodity', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_commodity ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.commodity" :key="v" @click="hf.setColumnFilter('wr_commodity', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_commodity === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_flight')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_flight ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.flight') }} <span class="text-[10px]" :class="hf.columnFilters.wr_flight ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_flight'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_flight', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_flight ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.flight" :key="v" @click="hf.setColumnFilter('wr_flight', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_flight === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-center px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_date')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_date ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.date') }} <span class="text-[10px]" :class="hf.columnFilters.wr_date ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_date'" class="absolute top-full left-1/2 -translate-x-1/2 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[140px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_date', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_date ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.date" :key="v" @click="hf.setColumnFilter('wr_date', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_date === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_pcsRec')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_pcsRec ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.pcsRec') }} <span class="text-[10px]" :class="hf.columnFilters.wr_pcsRec ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_pcsRec'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_pcsRec', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_pcsRec ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.pcsRec" :key="v" @click="hf.setColumnFilter('wr_pcsRec', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_pcsRec === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_physical')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_physical ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.physicalLbs') }} <span class="text-[10px]" :class="hf.columnFilters.wr_physical ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_physical'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_physical', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_physical ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.physical" :key="v" @click="hf.setColumnFilter('wr_physical', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_physical === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_dispatched')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_dispatched ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.dispatchedLbs') }} <span class="text-[10px]" :class="hf.columnFilters.wr_dispatched ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_dispatched'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_dispatched', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_dispatched ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.dispatched" :key="v" @click="hf.setColumnFilter('wr_dispatched', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_dispatched === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
              <th class="text-right px-3 py-2.5 text-[12px] font-bold text-slate-700 border-b border-slate-300 uppercase tracking-wider relative">
                <span @click="hf.toggleHeaderFilter('wr_pcsDisp')" class="cursor-pointer select-none" :class="hf.columnFilters.wr_pcsDisp ? 'text-indigo-600' : 'hover:text-slate-900'">{{ t('dashboard.wr.table.pcsDisp') }} <span class="text-[10px]" :class="hf.columnFilters.wr_pcsDisp ? 'opacity-100' : 'opacity-40'">&#9660;</span></span>
                <div v-if="hf.headerFilterOpen === 'wr_pcsDisp'" class="absolute top-full right-0 mt-1 bg-white border border-slate-300 rounded shadow-lg z-50 min-w-[120px] max-h-[220px] overflow-y-auto text-[13px] text-slate-900 font-normal normal-case font-sans">
                  <div @click="hf.setColumnFilter('wr_pcsDisp', null)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 font-bold text-center" :class="!hf.columnFilters.wr_pcsDisp ? 'bg-slate-100' : ''">{{ t('common.all') }}</div>
                  <div v-for="v in wrUniq.pcsDisp" :key="v" @click="hf.setColumnFilter('wr_pcsDisp', v)" class="px-3 py-1.5 cursor-pointer hover:bg-slate-100 text-center" :class="hf.columnFilters.wr_pcsDisp === v ? 'bg-slate-50 text-slate-700 font-bold' : ''">{{ v }}</div>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in wrVisibleRows" :key="idx"
              :class="idx % 2 === 1 ? 'bg-slate-50/60' : 'bg-white'"
              class="hover:bg-slate-50/80 border-b border-slate-100 transition-colors">
              <td class="px-3 py-2 font-bold text-slate-900">{{ row.awbNumber }}</td>
              <td class="px-3 py-2 text-slate-600">{{ row.shipperName }}</td>
              <td class="px-3 py-2 text-slate-600">{{ row.consigneeName }}</td>
              <td class="text-center px-3 py-2"><span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700">{{ row.destination }}</span></td>
              <td class="text-center px-3 py-2">
                <span v-if="row.commodityType"
                  class="inline-block px-2 py-0.5 rounded-full text-[10px] font-bold"
                  :style="{ background: commodityColor(row.commodityType) + '18', color: commodityColor(row.commodityType) }">
                  {{ row.commodityType }}
                </span>
              </td>
              <td class="text-center px-3 py-2 font-semibold text-slate-800">{{ row.flightNumber }}</td>
              <td class="text-center px-3 py-2 text-slate-500">{{ row.flightDate }}</td>
              <td class="text-right px-3 py-2 tabular-nums">{{ row.receivedPieces }}</td>
              <td class="text-right px-3 py-2 tabular-nums font-semibold text-emerald-700">{{ formatNum(row.physicalWeightLbs) }}</td>
              <td class="text-right px-3 py-2 tabular-nums font-semibold text-amber-700">{{ formatNum(row.dispatchedWeightLbs) }}</td>
              <td class="text-right px-3 py-2 tabular-nums">{{ row.dispatchedPieces }}</td>
            </tr>
            <tr v-if="wrVisibleRows.length" class="bg-slate-100/80 border-t-2 border-slate-300 font-bold hover:bg-slate-100 transition-colors">
              <td class="px-3 py-2.5 text-slate-600" colspan="7">{{ t('dashboard.table.total') }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-slate-900">{{ formatNum(wrTotals.receivedPieces) }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-emerald-800">{{ formatNum(wrTotals.physicalWeightLbs) }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-amber-800">{{ formatNum(wrTotals.dispatchedWeightLbs) }}</td>
              <td class="text-right px-3 py-2.5 tabular-nums text-slate-900">{{ formatNum(wrTotals.dispatchedPieces) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    </div>

  <div v-if="activeTab === 'builder'" class="flex-1 min-h-0 overflow-y-auto pr-1">
        <DashboardBuilderPanel />
      </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { downloadCSV } from '../utils/csv'
import FlightDetail from '../components/FlightDetail.vue'
import { useCommodities } from '../composables/useCommodities'
import { biApi } from '../api/bi'
import FilterBar from '../components/FilterBar.vue'
import DashboardBuilderPanel from '../components/DashboardBuilderPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import { useIcons } from '../composables/useIcons'
import { useHeaderFilters } from '../composables/useHeaderFilters'

const { t } = useI18n()
const icons = useIcons()
const appStore = useAppStore()
const { commodities: dbCommodities, loadCommodities } = useCommodities()
const hf = useHeaderFilters({ containerSelector: '.ds-table-section' })

const dateFrom = ref('')
const dateTo = ref('')
const loading = ref(false)
const expandedFlights = ref(new Set())
const activeTab = ref('flights')

watch(activeTab, (tab) => {
  if (tab === 'weight-report' && !wrRows.value.length && !wrLoading.value) {
    loadWeightReport()
  }
})

// Weight report state
const wrDateFrom = ref('')
const wrDateTo = ref('')
const wrCommodity = ref('')
const wrFlightNumber = ref('')
const wrLoading = ref(false)
const wrRows = ref([])
const wrSummary = ref(null)

const wrVisibleRows = computed(() => {
  const cf = hf.columnFilters
  let list = wrRows.value
  const eq = (k, c) => (c !== null && c !== undefined && list) ? list.filter(r => String(r[k]) === String(c)) : list
  list = eq('awbNumber', cf.wr_awb)
  list = eq('shipperName', cf.wr_shipper)
  list = eq('consigneeName', cf.wr_consignee)
  list = eq('destination', cf.wr_dest)
  list = eq('commodityType', cf.wr_commodity)
  list = eq('flightNumber', cf.wr_flight)
  list = eq('flightDate', cf.wr_date)
  const num = (k, c) => (c !== null && c !== undefined) ? list.filter(r => Number(r[k]) === Number(c)) : list
  list = num('receivedPieces', cf.wr_pcsRec)
  list = num('physicalWeightLbs', cf.wr_physical)
  list = num('dispatchedWeightLbs', cf.wr_dispatched)
  list = num('dispatchedPieces', cf.wr_pcsDisp)
  return list
})

const wrUniq = computed(() => ({
  awb: hf.uniqueValues(wrRows.value, r => r.awbNumber),
  shipper: hf.uniqueValues(wrRows.value, r => r.shipperName),
  consignee: hf.uniqueValues(wrRows.value, r => r.consigneeName),
  dest: hf.uniqueValues(wrRows.value, r => r.destination),
  commodity: hf.uniqueValues(wrRows.value, r => r.commodityType),
  flight: hf.uniqueValues(wrRows.value, r => r.flightNumber),
  date: hf.uniqueValues(wrRows.value, r => r.flightDate),
  pcsRec: hf.uniqueValues(wrRows.value, r => Number(r.receivedPieces)),
  physical: hf.uniqueValues(wrRows.value, r => Number(r.physicalWeightLbs)),
  dispatched: hf.uniqueValues(wrRows.value, r => Number(r.dispatchedWeightLbs)),
  pcsDisp: hf.uniqueValues(wrRows.value, r => Number(r.dispatchedPieces)),
}))

function formatNum(v) {
  if (v == null) return '0'
  const n = Number(v)
  return isNaN(n) ? '0' : n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 1 })
}

const wrTotals = computed(() => {
  const rows = wrVisibleRows.value
  return {
    receivedPieces: rows.reduce((s, r) => s + (Number(r.receivedPieces) || 0), 0),
    physicalWeightLbs: rows.reduce((s, r) => s + (Number(r.physicalWeightLbs) || 0), 0),
    dispatchedWeightLbs: rows.reduce((s, r) => s + (Number(r.dispatchedWeightLbs) || 0), 0),
    dispatchedPieces: rows.reduce((s, r) => s + (Number(r.dispatchedPieces) || 0), 0),
  }
})

function commodityColor(code) {
  const c = dbCommodities.value.find(x => x.code === code)
  return c?.color || '#6b7280'
}

async function loadWeightReport() {
  wrLoading.value = true
  try {
    const params = {}
    if (wrDateFrom.value) params.dateFrom = wrDateFrom.value
    if (wrDateTo.value) params.dateTo = wrDateTo.value
    if (wrCommodity.value) params.commodityType = wrCommodity.value
    if (wrFlightNumber.value) params.awbNumber = wrFlightNumber.value
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

function exportWeightCSV() {
  if (!wrRows.value.length) return
  const headers = [
    t('dashboard.wr.csvHeaders.awb'),
    t('dashboard.wr.csvHeaders.shipper'),
    t('dashboard.wr.csvHeaders.consignee'),
    t('dashboard.wr.csvHeaders.dest'),
    t('dashboard.wr.csvHeaders.commodity'),
    t('dashboard.wr.csvHeaders.flight'),
    t('dashboard.wr.csvHeaders.date'),
    t('dashboard.wr.csvHeaders.pcsRec'),
    t('dashboard.wr.csvHeaders.physicalLbs'),
    t('dashboard.wr.csvHeaders.dispatchedLbs'),
    t('dashboard.wr.csvHeaders.pcsDisp'),
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
  a.download = `${t('dashboard.wr.csvFilename')}-${new Date().toISOString().slice(0,10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const filteredFlights = computed(() => {
  let list = appStore.flights
  if (dateFrom.value) {
    list = list.filter(f => f.flightDate >= dateFrom.value)
  }
  if (dateTo.value) {
    list = list.filter(f => f.flightDate <= dateTo.value)
  }
  const cf = hf.columnFilters
  if (cf.flight) list = list.filter(f => `UPS-${f.flightNumber}` === cf.flight)
  if (cf.route) list = list.filter(f => `${f.origin}→${f.destination}` === cf.route)
  if (cf.date) list = list.filter(f => f.flightDate === cf.date)
  if (cf.status) list = list.filter(f => statusLabel(f.status) === cf.status)
  if (cf.ulds !== null && cf.ulds !== undefined) list = list.filter(f => flightUlds(f.id).length === Number(cf.ulds))
  if (cf.pos !== null && cf.pos !== undefined) list = list.filter(f => flightPositions(f.id) === Number(cf.pos))
  if (cf.gross !== null && cf.gross !== undefined) list = list.filter(f => grossLbs(f.id) === Number(cf.gross))
  if (cf.tare !== null && cf.tare !== undefined) list = list.filter(f => totalTareLbs(f.id) === Number(cf.tare))
  if (cf.net !== null && cf.net !== undefined) list = list.filter(f => netLbs(f.id) === Number(cf.net))
  if (cf.payload !== null && cf.payload !== undefined) list = list.filter(f => payloadLbs(f.id) === Number(cf.payload))
  return list
})

const flightUniq = computed(() => {
  const rows = appStore.flights
  return {
    flight: hf.uniqueValues(rows, f => `UPS-${f.flightNumber}`),
    route: hf.uniqueValues(rows, f => `${f.origin}→${f.destination}`),
    date: hf.uniqueValues(rows, f => f.flightDate),
    status: hf.uniqueValues(rows, f => statusLabel(f.status)),
    ulds: hf.uniqueValues(rows, f => flightUlds(f.id).length),
    pos: hf.uniqueValues(rows, f => flightPositions(f.id)),
    gross: hf.uniqueValues(rows, f => grossLbs(f.id)),
    tare: hf.uniqueValues(rows, f => totalTareLbs(f.id)),
    net: hf.uniqueValues(rows, f => netLbs(f.id)),
    payload: hf.uniqueValues(rows, f => payloadLbs(f.id)),
  }
})

const allUlDs = computed(() => appStore.ulds)
const allMawbs = computed(() => appStore.mawbs)

function flightUlds(flightId) {
  return allUlDs.value.filter(u => u.flightId === flightId)
}

function flightMawbs(flightId) {
  return allMawbs.value.filter(m => m.flightId === flightId)
}

function flightPositions(flightId) {
  const ulds = flightUlds(flightId)
  return new Set(ulds.map(u => u.position).filter(Boolean)).size
}

function grossLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds.reduce((s, u) => s + (Number(u.grossWeightLbs) || 0), 0)
}

function isBellyPosition(position) {
  if (!position) return false
  const p = position.toString().trim().toUpperCase()
  return p === '31' || p === '34' || p === 'AB' || p === 'A' || p === 'B' || p === 'LOOSE' || p === 'BULK' || p.includes('BELLY')
}

function totalTareLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds.reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
}

function bellyTareLbs(flightId) {
  const ulds = flightUlds(flightId)
  return ulds
    .filter(u => isBellyPosition(u.position))
    .reduce((s, u) => s + (Number(u.tareLbs) || 0), 0)
}

function netLbs(flightId) {
  return grossLbs(flightId) - totalTareLbs(flightId)
}

function payloadLbs(flightId) {
  return grossLbs(flightId) - bellyTareLbs(flightId)
}

// ── Commodity definitions & ordering (dynamic from DB) ──────────────────────────
const COMMODITY_ORDER = computed(() => dbCommodities.value.map(c => c.code))

const COMMODITY_MAP = computed(() => {
  const map = {}
  for (const c of dbCommodities.value) {
    const shortLen = Math.min(c.code.length, 4)
    map[c.code] = { label: c.label, short: c.code.slice(0, shortLen), color: c.color || '#94a3b8' }
  }
  return map
})

// ULD IDs per flight (cached for fast lookup during commodity calculations)
const _uldIdCache = new Map()
function flightUldIdSet(flightId) {
  if (!_uldIdCache.has(flightId)) {
    _uldIdCache.set(flightId, new Set(appStore.ulds.filter(u => u.flightId === flightId).map(u => u.id)))
  }
  return _uldIdCache.get(flightId)
}

// Invalidate cache when ulds change. Dimos: la caché de un vuelo depende de la
// asignación (id, flightId) de cada ULD. Rastrear solo `.length` (como antes)
// dejaba la caché stale cuando un ULD existente se reasigna a otro vuelo (la
// cantidad no cambia). Usamos una firma de todas las asignaciones, así una
// reasignación invalida el vuelo correcto bajo demanda.
const uldFlightSignature = computed(() =>
  appStore.ulds.map(u => u.id + ':' + (u.flightId || '')).join('|')
)
watch(uldFlightSignature, () => _uldIdCache.clear())

// Dispatched weight per MAWB within a specific flight:
// only counts ULD-AWB links whose ULD belongs to that flight.
// Formula: (physicalWeight / totalPieces) * dispatchedPieces
function mawbDispatchedWeightLbs(mawb, flightId) {
  const receivedKg = Number(mawb.reportedWeightKg || mawb.chargeableWeightKg || 0)
  const receivedPcs = Number(mawb.pieces || 0)
  if (!receivedKg || !receivedPcs) return 0
  const uldIds = flightUldIdSet(flightId)
  const links = appStore.uldAwbs?.filter?.(l => l.mawbLabel === mawb.awbNumber && uldIds.has(l.uldId)) || []
  const dispatchedPcs = links.reduce((s, l) => s + (Number(l.pieces) || 0), 0)
  if (!dispatchedPcs) return 0
  return (receivedKg * 2.20462 / receivedPcs) * dispatchedPcs
}

// Commodity payload per flight (sum of per-MAWB dispatched weights)
function commodityPayload(flightId, commodityType) {
  const mawbs = flightMawbs(flightId)
  const totalLbs = mawbs
    .filter(m => (m.commodityType || 'DRY_CARGO') === commodityType)
    .reduce((s, m) => s + mawbDispatchedWeightLbs(m, flightId), 0)
  return totalLbs > 0 ? Math.round(totalLbs) : null
}

function commodityTooltip(flightId, commodityType) {
  const mawbs = flightMawbs(flightId)
  const items = mawbs.filter(m => (m.commodityType || 'DRY_CARGO') === commodityType)
  if (!items.length) return t('dashboard.tooltip.commodityZero', { label: COMMODITY_MAP.value[commodityType]?.label || commodityType })
  const totalLbs = items.reduce((s, m) => s + mawbDispatchedWeightLbs(m, flightId), 0)
  const uldIds = flightUldIdSet(flightId)
  const totalPcs = items.reduce((s, m) => {
    const links = appStore.uldAwbs?.filter?.(l => l.mawbLabel === m.awbNumber && uldIds.has(l.uldId)) || []
    return s + links.reduce((ps, l) => ps + (Number(l.pieces) || 0), 0)
  }, 0)
  const mawbCount = items.length
  return t('dashboard.tooltip.commodity', { label: COMMODITY_MAP.value[commodityType]?.label || commodityType, lbs: Math.round(totalLbs), pcs: totalPcs, n: mawbCount })
}

// Visible commodities = those with dispatched payload > 0 in ANY filtered flight
const visibleCommodities = computed(() => {
  const activeTypes = new Set()
  filteredFlights.value.forEach(f => {
    flightMawbs(f.id).forEach(m => {
      const type = m.commodityType || 'DRY_CARGO'
      if (mawbDispatchedWeightLbs(m, f.id) > 0) activeTypes.add(type)
    })
  })
  return COMMODITY_ORDER.value
    .filter(t => activeTypes.has(t))
    .map(t => ({ type: t, ...COMMODITY_MAP.value[t] }))
})

// Table min-width for horizontal scroll
const tableMinWidth = computed(() => {
  const isMobile = typeof window !== 'undefined' && window.innerWidth <= 640
  const base = isMobile ? 720 : 940 // fixed columns (menos en móvil: sin sticky)
  const commodityCols = visibleCommodities.value.length * 80 // 80px per commodity col
  return base + commodityCols
})

// Totals
const totalNetPayload = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + payloadLbs(f.id), 0)
})

const totalUldsCount = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightUlds(f.id).length, 0)
})

const totalMawbsCount = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightMawbs(f.id).length, 0)
})

const totalPositionsAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + flightPositions(f.id), 0)
})

const totalMaxPositionsAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + (f.totalPositions || 0), 0)
})

const totalGrossAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + grossLbs(f.id), 0)
})

const totalTareAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + totalTareLbs(f.id), 0)
})

const totalNetAll = computed(() => {
  return filteredFlights.value.reduce((s, f) => s + netLbs(f.id), 0)
})

function totalCommodityPayload(commodityType) {
  const total = filteredFlights.value.reduce((s, f) => s + (commodityPayload(f.id, commodityType) || 0), 0)
  return total > 0 ? total : '—'
}

// Expand logic
const allExpanded = computed(() => {
  return filteredFlights.value.length > 0 && filteredFlights.value.every(f => expandedFlights.value.has(f.id))
})

function toggleExpand(flightId) {
  if (expandedFlights.value.has(flightId)) {
    expandedFlights.value.delete(flightId)
  } else {
    expandedFlights.value.add(flightId)
  }
}

function toggleAllExpanded() {
  if (allExpanded.value) {
    expandedFlights.value.clear()
  } else {
    filteredFlights.value.forEach(f => expandedFlights.value.add(f.id))
  }
}

function isExpanded(flightId) {
  return expandedFlights.value.has(flightId)
}

function descargarReporte() {
  // Build headers: fixed + commodity columns
  const fixedHeaders = [
    t('dashboard.csvHeaders.flightNumber'),
    t('dashboard.csvHeaders.route'),
    t('dashboard.csvHeaders.date'),
    t('dashboard.csvHeaders.status'),
    t('dashboard.csvHeaders.uldCount'),
    t('dashboard.csvHeaders.positions'),
    t('dashboard.csvHeaders.grossLbs'),
    t('dashboard.csvHeaders.tareLbs'),
    t('dashboard.csvHeaders.netLbs'),
    t('dashboard.csvHeaders.payloadLbs'),
  ]
  const commodityHeaders = visibleCommodities.value.map(c => c.short)
  const headers = [...fixedHeaders, ...commodityHeaders]

  const rows = filteredFlights.value.map(f => {
    const fixed = [
      `UPS-${f.flightNumber}`,
      `${f.origin}→${f.destination}`,
      f.flightDate || '',
      statusLabel(f.status),
      flightUlds(f.id).length,
      flightPositions(f.id),
      grossLbs(f.id),
      totalTareLbs(f.id),
      netLbs(f.id),
      payloadLbs(f.id),
    ]
    const commodityVals = visibleCommodities.value.map(c => commodityPayload(f.id, c.type) || '')
    return [...fixed, ...commodityVals]
  })
  downloadCSV(headers, rows, `${t('dashboard.csvFilename')}-${new Date().toISOString().slice(0, 10)}.csv`)
}

function getStatusDot(status) {
  if (status === 'SCHEDULED') return 'bg-slate-300'
  if (status === 'BOARDING') return 'bg-slate-400'
  if (status === 'DEPARTED') return 'bg-slate-600'
  if (status === 'ARRIVED') return 'bg-slate-800'
  if (status === 'CANCELLED') return 'bg-slate-200'
  if (status === 'DELAYED') return 'bg-slate-400'
  return 'bg-slate-200'
}

function statusStyle(status) {
  const map = {
    SCHEDULED: { background: '#e2e8f0', color: '#475569' },
    BOARDING: { background: '#e2e8f0', color: '#475569' },
    DEPARTED: { background: '#94a3b8', color: '#fff' },
    ARRIVED: { background: '#1e293b', color: '#fff' },
    CANCELLED: { background: '#f1f5f9', color: '#94a3b8' },
    DELAYED: { background: '#fef08a', color: '#854d0e' },
  }
  return map[status] || { background: '#e2e8f0', color: '#475569' }
}

function statusLabel(status) {
  const map = {
    SCHEDULED: 'SCH',
    BOARDING: 'BRD',
    DEPARTED: 'DPT',
    ARRIVED: 'ARR',
    CANCELLED: 'CNL',
    DELAYED: 'DLY',
  }
  return map[status] || status?.slice(0, 3) || '—'
}

onMounted(async () => {
  loading.value = true
  await loadCommodities()
  await appStore.loadFlights()
  if (appStore.flights.length) {
    await Promise.all([
      appStore.loadUlds(),
      appStore.loadAllMawbs(),
      appStore.loadUldAwbs(),

    ])
  }
  loading.value = false
})
</script>

<style scoped>
/* La tabla de vuelos ya no usa columnas sticky horizontales: fluye con el ancho
   natural y solo la cabecera permanece fija (sticky top) al hacer scroll vertical. */

.ds-card {
  @apply bg-white border border-slate-200 rounded-xl px-3.5 py-3
         shadow-sm shadow-slate-900/5 border-l-4 border-l-slate-800
         transition hover:shadow-md;
}
.ds-card-label {
  @apply text-[10px] font-mono font-semibold text-slate-500 uppercase tracking-wide mb-0.5;
}
.ds-card-value {
  @apply text-[18px] font-mono font-bold text-slate-900 tracking-tight leading-tight;
}
.ds-btn-secondary {
  @apply px-3 py-1.5 rounded-lg text-[12px] font-semibold font-mono border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition;
}
</style>