<template>
  <div class="ds-builder">
    <!-- Toolbar -->
    <div class="flex flex-wrap items-center gap-2 mb-3 shrink-0">
      <input
        v-model="cfg.name"
        type="text"
        :placeholder="t('db.namePlaceholder')"
        class="ds-input w-64 max-w-full"
        @keyup.enter="runEval"
      />
      <select v-model="cfg.dimension" class="ds-input w-44 max-w-full" :title="t('db.dimension')">
        <option value="">{{ t('db.noGrouping') }}</option>
        <option v-for="f in groupFields" :key="'d-' + f.key" :value="f.key">
          {{ f.label }}
        </option>
      </select>
      <select v-model="cfg.baseSource" class="ds-input w-44 max-w-full" :title="t('db.baseSource')">
        <option value="uld-awb">{{ t('db.baseUldAwb') }}</option>
        <option value="mawb">{{ t('db.baseMawb') }}</option>
        <option value="booking">{{ t('db.baseBooking') }}</option>
        <option value="receipt">{{ t('db.baseReceipt') }}</option>
        <option value="flight">{{ t('db.baseFlight') }}</option>
        <option value="uld">{{ t('db.baseUld') }}</option>
      </select>
      <button
        @click="toggleMode"
        class="ds-btn-secondary"
        :title="mode === 'pivot' ? t('db.flatMode') : t('db.pivotMode')"
      >
        {{ mode === 'pivot' ? t('db.flatMode') : t('db.pivotMode') }}
      </button>
      <select v-model="topN" class="ds-input w-24 max-w-full" :title="t('db.topN')">
        <option :value="null">{{ t('db.allRows') }}</option>
        <option :value="5">Top 5</option>
        <option :value="10">Top 10</option>
        <option :value="20">Top 20</option>
        <option :value="50">Top 50</option>
      </select>
      <button @click="runEval" class="ds-btn-primary" :disabled="busy">
        {{ busy ? t('db.busy') : t('db.run') }}
      </button>
      <span class="ds-divider"></span>
      <button v-if="canManage" @click="saveReport" class="ds-btn-secondary" :disabled="busy">
        {{ t('db.save') }}
      </button>
      <select
        v-if="canManage"
        v-model="loadKey"
        @change="loadReport"
        class="ds-input w-52 max-w-full"
        :title="t('db.load')"
      >
        <option value="">{{ t('db.load') }}</option>
        <option v-for="r in savedReports" :key="r.id" :value="r.id">{{ r.name }}</option>
      </select>
      <button
        v-if="canManage && currentReportId"
        @click="deleteReport"
        class="ds-btn-danger"
        :title="t('db.delete')"
      >
        &#128465;
      </button>
    </div>

    <!-- Error -->
    <div v-if="error" class="mb-3 text-[12px] font-mono text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2 flex items-center gap-2">
      <span class="w-2 h-2 rounded-full bg-red-500 flex-shrink-0"></span>
      {{ error }}
    </div>

    <!-- Pivot builder -->
    <div v-if="mode === 'pivot'" class="ds-card mb-3">
      <div class="ds-card-label">{{ t('db.pivotCfg') }}</div>
      <div class="mt-2 grid grid-cols-1 lg:grid-cols-3 gap-3">
        <div>
          <div class="text-[11px] font-bold text-slate-500 mb-1">{{ t('db.pivotRows') }}</div>
          <div class="space-y-1">
            <div v-for="(rf, i) in pivotRows" :key="'pr-' + i" class="flex items-center gap-1">
              <FieldPicker
                v-model="pivotRows[i]"
                :fields-by-source="fieldsBySource"
                :label-of="labelOf"
                :placeholder="t('db.pivotPickRow')"
                class="w-full"
              />
              <button v-if="pivotRows.length > 1" class="ds-btn-danger px-1 text-[10px]" @click="pivotRows.splice(i, 1)">&#10005;</button>
            </div>
          </div>
          <button class="ds-btn-secondary mt-1" @click="pivotRows.push('')">{{ t('db.addRow') }}</button>
        </div>

        <div>
          <div class="text-[11px] font-bold text-slate-500 mb-1">{{ t('db.pivotCols') }}</div>
          <FieldPicker
            v-model="pivotColumn"
            :fields-by-source="fieldsBySource"
            :label-of="labelOf"
            :placeholder="t('db.pivotNoCol')"
          />
          <button
            v-if="pivotColumn"
            class="ds-btn-secondary mt-1"
            @click="pivotColumn = ''"
          >{{ t('db.pivotClear') }}</button>
          <p class="text-[11px] text-slate-400 mt-1">{{ t('db.pivotColHint') }}</p>
        </div>

        <div>
          <div class="text-[11px] font-bold text-slate-500 mb-1">{{ t('db.pivotVals') }}</div>
          <div class="space-y-1">
            <div v-for="(v, i) in pivotVals" :key="'pv-' + i" class="flex items-center gap-1 flex-wrap">
              <FieldPicker
                v-model="pivotVals[i].field"
                :fields-by-source="fieldsBySource"
                :label-of="labelOf"
                :placeholder="t('db.pivotPickField')"
                only-numeric
                class="min-w-[160px]"
              />
              <select v-model="pivotVals[i].agg" class="ds-input w-24 px-1 py-0.5">
                <option value="SUM">SUM</option>
                <option value="AVG">AVG</option>
                <option value="MAX">MAX</option>
                <option value="MIN">MIN</option>
                <option value="COUNT">COUNT</option>
              </select>
              <button class="ds-btn-danger px-1 text-[10px]" @click="pivotVals.splice(i, 1)">&#10005;</button>
            </div>
          </div>
          <button class="ds-btn-secondary mt-1" @click="pivotVals.push({ field: '', agg: 'SUM' })">{{ t('db.addVal') }}</button>
          <p class="text-[11px] text-slate-400 mt-1">{{ t('db.pivotValHint') }}</p>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-3">
      <!-- Fields by source table: compact popup dropdown -->
      <div class="ds-card">
        <div class="ds-card-label flex items-center gap-2">
          {{ t('db.fields') }}
          <span class="text-[10px] font-mono text-slate-400">{{ t('db.pickOpen') }}</span>
          <span class="text-[10px] font-mono text-slate-400 ml-auto">{{ cfg.fieldSources.length }} {{ t('db.pickSelected') }}</span>
        </div>
        <FieldPicker
          class="mt-1"
          v-model="cfg.fieldSources"
          multi
          :fields-by-source="fieldsBySource"
          :label-of="labelOf"
          :placeholder="t('db.pickChooseFields')"
        />
        <p class="text-[11px] text-slate-400 mt-2 leading-snug">{{ t('db.fieldsHint') }}</p>
      </div>

      <div class="space-y-4">
        <!-- Scenario -->
        <div class="ds-card">
          <div class="ds-card-label">{{ t('db.scenario') }}</div>
          <div class="flex flex-wrap gap-2 mt-1">
            <div v-for="(v, k) in cfg.scenario" :key="'s-' + k" class="flex items-center gap-1 text-[12px] font-mono">
              <span class="text-slate-500">{{ k }}</span>
              <input v-model="cfg.scenario[k]" type="number" step="any" class="ds-input w-24 px-1 py-0.5" />
            </div>
          </div>
          <div class="flex gap-2 mt-2">
            <input v-model="newVarName" class="ds-input w-32 px-1 py-0.5" :placeholder="t('db.varPlaceholder')" />
            <button @click="addVar" class="ds-btn-secondary">{{ t('db.add') }}</button>
          </div>
          <p class="text-[11px] text-slate-400 mt-2 leading-snug">{{ t('db.scenarioHint') }}</p>
        </div>

        <!-- Filters (WHERE) -->
        <div class="ds-card">
          <div class="ds-card-label">{{ t('db.filters') }}</div>
          <div class="mt-1 space-y-1">
            <div v-for="(fi, i) in cfg.filters" :key="'fi-' + i" class="flex items-center gap-1 flex-wrap text-[12px]">
              <FieldPicker
                v-model="fi.field"
                :fields-by-source="fieldsBySource"
                :label-of="labelOf"
                :placeholder="t('db.filterField')"
                class="min-w-[160px]"
              />
              <select v-model="fi.op" class="ds-input w-28 px-1 py-0.5">
                <option v-for="op in ops" :key="op" :value="op">{{ t('db.ops.' + op) }}</option>
              </select>
              <select v-if="isBoolField(fi.field) && fi.op && fi.op !== 'isNull' && fi.op !== 'notNull'" v-model="fi.value"
                class="ds-input w-20 px-1 py-0.5">
                <option :value="true">{{ t('common.yes') }}</option>
                <option :value="false">{{ t('common.no') }}</option>
              </select>
              <input
                v-else-if="fi.op && fi.op !== 'isNull' && fi.op !== 'notNull'"
                v-model="fi.value"
                :type="isNumField(fi.field) ? 'number' : 'text'"
                step="any"
                class="ds-input w-32 px-1 py-0.5"
                :placeholder="t('db.filterValue')"
              />
              <span v-else class="text-[11px] text-slate-400">{{ t('db.filterNoValue') }}</span>
              <button @click="cfg.filters.splice(i, 1)" class="ds-btn-danger" title="&#10005;">&#10005;</button>
            </div>
          </div>
          <div class="mt-2">
            <button @click="addFilter" class="ds-btn-secondary">{{ t('db.addFilter') }}</button>
            <span class="text-[11px] text-slate-400 ml-2">{{ t('db.filtersHint') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Formulas -->
    <div class="ds-card mb-3">
      <div class="ds-card-label">{{ t('db.formulas') }}</div>
      <div class="mt-1 space-y-1">
        <div v-for="(cf, i) in cfg.formulas" :key="'cf-' + i" class="flex items-center gap-1 flex-wrap">
          <input v-model="cf.column" class="ds-input w-36 max-w-full px-1 py-0.5" :placeholder="t('db.colName')" />
          <input v-model="cf.expression" class="ds-input flex-1 min-w-[180px] px-1 py-0.5 font-mono" :placeholder="t('db.exprPlaceholder')" />
          <select v-model="cf.aggregate" class="ds-input w-24 px-1 py-0.5">
            <option value="">{{ t('db.aggRow') }}</option>
            <option value="SUM">SUM</option>
            <option value="AVG">AVG</option>
            <option value="MAX">MAX</option>
            <option value="MIN">MIN</option>
            <option value="COUNT">COUNT</option>
          </select>
          <button @click="cfg.formulas.splice(i, 1)" class="ds-btn-danger">&#10005;</button>
        </div>
      </div>
      <div class="mt-2">
        <button @click="addFormula" class="ds-btn-secondary">{{ t('db.addFormula') }}</button>
        <span class="text-[11px] text-slate-400 ml-2">{{ t('db.functionsHint') }}</span>
      </div>
    </div>

    <!-- Results -->
    <template v-if="result || pivotResult">
      <!-- Pivot matrix -->
      <div v-if="mode === 'pivot' && pivotResult" class="ds-card mb-3 border-t-4 border-t-indigo-500">
        <div class="ds-card-label flex items-center gap-2">
          {{ t('db.pivotResult') }}
          <span class="ds-chip">{{ pivotResult.rows.length }} {{ t('db.rows') }}</span>
        </div>
        <div class="overflow-auto max-h-[460px] mt-2 rounded-lg border border-slate-200">
          <table class="w-full border-collapse text-[12px] font-mono">
            <thead class="sticky top-0 z-10">
              <tr class="bg-slate-800 text-white">
                <th v-for="rf in pivotResult.rowFields" :key="'ph-' + rf" class="px-2.5 py-2 text-left whitespace-nowrap uppercase text-[11px] tracking-wider">
                  {{ labelOf(rf) }}
                </th>
                <th v-if="!pivotResult.rowFields.length" class="px-2.5 py-2 text-left">{{ t('db.pivotAll') }}</th>
                <th v-for="group in pivotResult.colGroups" :key="'pg-' + group" :colspan="pivotResult.measures.length"
                  class="px-2.5 py-2 text-center whitespace-nowrap border-l border-slate-600 uppercase text-[11px] tracking-wider">
                  {{ group === '__total' ? t('db.pivotTotal') : group }}
                </th>
              </tr>
              <tr class="bg-slate-700 text-white">
                <th v-for="rf in pivotResult.rowFields" :key="'pms-' + rf" class="px-2 py-1.5"></th>
                <th v-if="!pivotResult.rowFields.length" class="px-2 py-1.5"></th>
                <template v-for="group in pivotResult.colGroups" :key="'pgm-' + group">
                  <th v-for="m in pivotResult.measures" :key="group + m" class="px-2.5 py-1.5 text-left whitespace-nowrap border-l border-slate-600 text-[11px]">
                    {{ m }}
                  </th>
                </template>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in pivotResult.rows" :key="'prr-' + i"
                :class="i % 2 === 1 ? 'bg-slate-50/60' : 'bg-white'"
                class="border-b border-slate-100 hover:bg-blue-50/40 transition-colors">
                <td v-for="(k, ki) in row.key" :key="'rk-' + i + ki" class="px-2.5 py-1.5 font-bold whitespace-nowrap text-slate-800">
                  {{ k === '__single' ? '' : k }}
                </td>
                <td v-if="!pivotResult.rowFields.length"></td>
                <td v-for="(cell, ci) in row.cells" :key="'rc-' + i + ci"
                  class="px-2.5 py-1.5 text-right whitespace-nowrap border-l border-slate-100 text-slate-900 tabular-nums">
                  {{ fmtPivot(cell) }}
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="bg-slate-100 font-bold">
                <td :colspan="Math.max(1, pivotResult.rowFields.length)" class="px-2.5 py-2 text-right uppercase text-[11px] text-slate-600">
                  {{ t('db.pivotTotal') }}
                </td>
                <td v-for="(cell, ci) in pivotResult.totals" :key="'pt-' + ci"
                  class="px-2.5 py-2 text-right whitespace-nowrap border-l border-slate-200 text-slate-900 tabular-nums">
                  {{ fmtPivot(cell) }}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <!-- Flat table -->
      <div v-if="mode !== 'pivot' && result" class="ds-card mb-3 border-t-4 border-t-emerald-500">
        <div class="ds-card-label flex items-center gap-2">
          {{ t('db.result') }}
          <span class="ds-chip">{{ result.rows.length }} {{ t('db.rows') }}</span>
        </div>
        <div class="overflow-auto max-h-[420px] mt-2 rounded-lg border border-slate-200">
          <table class="w-full border-collapse text-[12px] font-mono">
            <thead class="sticky top-0 z-10">
              <tr class="bg-slate-800 text-white">
                <th v-for="c in result.columns" :key="'h-' + c" class="px-2.5 py-2 text-left whitespace-nowrap uppercase text-[11px] tracking-wider">
                  {{ columnLabel(c) }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in result.rows" :key="'r-' + i"
                :class="i % 2 === 1 ? 'bg-slate-50/60' : 'bg-white'"
                class="border-b border-slate-100 hover:bg-blue-50/40 transition-colors">
                <td v-for="c in result.columns" :key="c + i" class="px-2.5 py-1.5 whitespace-nowrap text-slate-900">
                  {{ formatCell(row, c) }}
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr v-for="(tot, ti) in result.totals" :key="'t-' + ti" class="bg-slate-100 font-bold border-t-2 border-slate-300">
                <td v-for="c in result.columns" :key="c + 'tf' + ti" class="px-2.5 py-2 whitespace-nowrap text-slate-900">
                  {{ formatCell(tot, c) }}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <!-- Chart -->
      <div class="ds-card border-t-4 border-t-sky-500" v-if="pointsData.length" ref="chartCard">
        <div class="ds-card-label flex items-center gap-2">
          {{ t('db.chart') }}
          <span class="ds-chip">{{ pointsData.length }} {{ t('db.points') }}</span>
          <div class="ml-auto flex items-center gap-1.5">
            <button class="ds-btn-secondary px-2 py-1 text-[10px]" @click="showLabels = !showLabels"
              :title="t('db.chartLabels')">
              {{ showLabels ? t('db.pickOn') : t('db.pickOff') }}
            </button>
            <button class="ds-btn-secondary px-2 py-1 text-[10px]" @click="exportPng" :title="t('db.chartPng')">
              PNG
            </button>
            <button class="ds-btn-secondary px-2 py-1 text-[10px]" @click="exportCsv" :title="t('db.chartCsv')">
              CSV
            </button>
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2 mt-1 text-[12px]">
          <label class="flex items-center gap-1">
            <span class="text-slate-500">{{ t('db.chartType') }}</span>
            <select v-model="chartType" class="ds-input w-24 px-1 py-0.5">
              <option value="bar">{{ t('db.chartBar') }}</option>
              <option value="line">{{ t('db.chartLine') }}</option>
              <option value="area">{{ t('db.chartArea') }}</option>
              <option value="pie">{{ t('db.chartPie') }}</option>
            </select>
          </label>
          <label class="flex items-center gap-1">
            <span class="text-slate-500">{{ t('db.chartX') }}</span>
            <select v-model="chartX" class="ds-input w-36 px-1 py-0.5">
              <option
                v-for="c in xCandidates"
                :key="'x-' + c"
                :value="c"
              >{{ columnLabel(c) }}</option>
            </select>
          </label>
          <label class="flex items-center gap-1">
            <span class="text-slate-500">{{ t('db.chartY') }}</span>
            <select v-model="chartY" class="ds-input w-40 px-1 py-0.5">
              <option value="">{{ t('db.chartAutoY') }}</option>
              <option
                v-for="c in numericColumns"
                :key="'y-' + c"
                :value="c"
              >{{ columnLabel(c) }}</option>
            </select>
          </label>
        </div>
        <p class="text-[11px] text-slate-400 mt-1">{{ t('db.chartHint') }}</p>
        <div class="overflow-x-auto mt-2">
          <svg
            v-if="chartType !== 'pie'"
            :viewBox="`0 0 ${chartCanvasW} ${chartH}`"
            :width="chartCanvasW"
            class="max-h-72"
            preserveAspectRatio="xMidYMid meet"
            ref="chartSvg"
          >
            <!-- y grid -->
            <line v-for="(g, gi) in gridTicks" :key="'gy-' + gi" :y1="yAt(g.v)" :y2="yAt(g.v)"
              :x1="chartPad" :x2="chartCanvasW - chartPad" stroke="#e2e8f0" stroke-width="1" stroke-dasharray="3,3" />
            <text v-for="(g, gi) in gridTicks" :key="'gy-' + gi" :x="chartPad - 6" :y="yAt(g.v) + 3"
              text-anchor="end" class="chart-text" font-size="9" fill="#94a3b8">{{ g.label }}</text>

            <!-- area / line path -->
            <path v-if="chartType === 'area' || chartType === 'line'" :d="seriesPath"
              :fill="chartType === 'area' ? 'url(#chartAreaGrad)' : 'none'" stroke="#0ea5e9"
              stroke-width="2.5" fill-rule="evenodd" stroke-linejoin="round" stroke-linecap="round" />
            <defs>
              <linearGradient id="chartAreaGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#0ea5e9" stop-opacity="0.35" />
                <stop offset="100%" stop-color="#0ea5e9" stop-opacity="0.02" />
              </linearGradient>
            </defs>
            <circle v-for="(pt, pi) in pointsData" :key="'pt-' + pi" :cx="pt.x" :cy="pt.y" r="3.5"
              fill="#fff" stroke="#0ea5e9" stroke-width="2">
              <title>{{ pt.label }}</title>
            </circle>

            <!-- bars -->
            <rect v-for="(b, bi) in bars" :key="'bar-' + bi" :x="b.x" :y="b.y" :width="b.w" :height="b.h"
              :fill="b.fill" :rx="b.w / 2 > 4 ? 4 : b.w / 2" opacity="0.92">
              <title>{{ b.label }}</title>
            </rect>
            <g v-if="showLabels">
              <text v-for="(b, bi) in bars" :key="'bx-' + bi" :x="b.x + b.w / 2" :y="b.y - 5"
                text-anchor="middle" class="chart-text" font-size="9.5" font-weight="600" fill="#0f172a">{{ b.text }}</text>
            </g>

            <!-- x labels -->
            <g v-for="(pt, pi) in pointsData" :key="'xl-' + pi"
              :transform="`translate(${pt.x},${chartH - chartPad + 14}) rotate(-30)`">
              <text :x="0" :y="0" text-anchor="end" class="chart-text" font-size="9" fill="#64748b"
                style="transform-origin:center">{{ pt.shortLabel }}</text>
            </g>
          </svg>

          <!-- pie -->
          <svg v-else :viewBox="`0 0 ${chartW} ${chartH}`" :width="chartW" class="max-h-72"
            preserveAspectRatio="xMidYMid meet" ref="chartSvg">
            <g :transform="`translate(${chartW / 2},${chartH / 2})`">
              <path v-for="(s, si) in pieSlices" :key="'pie-' + si" :d="slicePath(s)"
                :fill="s.fill" stroke="#fff" stroke-width="2">
                <title>{{ s.label }}: {{ s.value }}</title>
              </path>
            </g>
            <g v-for="(s, si) in pieSlices" :key="'pl-' + si">
              <rect :x="chartPad - 6" :y="chartH - 18 * pieSlices.length + si * 16" width="11" height="11" rx="2" :fill="s.fill" />
              <text :x="chartPad + 10" :y="chartH - 18 * pieSlices.length + si * 16 + 9.5"
                class="chart-text" font-size="9.5" fill="#334155">{{ s.label }} — <tspan font-weight="600">{{ s.pct }}%</tspan></text>
            </g>
          </svg>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { dashboardBuilderApi } from '../api/dashboardReports'
import FieldPicker from './FieldPicker.vue'

const { t } = useI18n()
const auth = useAuthStore()

const fields = ref([])
const busy = ref(false)
const error = ref('')
const result = ref(null)
const pivotResult = ref(null)
const mode = ref('flat')
const pivotRows = ref(['MawbStatus'])
const pivotColumn = ref('')
const pivotVals = ref([{ field: 'MawbPieces', agg: 'SUM' }])
const topN = ref(null)
const loadKey = ref('')
const savedReports = ref([])
const currentReportId = ref(null)
const newVarName = ref('')

const cfg = ref({
  name: '',
  dimension: '',
  fieldSources: ['AwbNumber', 'MawbPieces', 'FlightNumber', 'AirlineCode'],
  formulas: [],
  scenario: {},
  filters: [],
  chartConfig: { type: 'bar', filters: [] },
  baseSource: 'mawb',
})

const canManage = computed(() =>
  ['OPERATIONS', 'TRAFFIC', 'ADMIN', 'SUPER_USER'].includes(auth.role)
)

const ops = ['eq', 'ne', 'contains', 'gt', 'gte', 'lt', 'lte', 'isNull', 'notNull']

const fieldsBySource = computed(() => {
  const order = ['uld', 'flight', 'airline', 'mawb', 'booking', 'receipt', 'scenario']
  const groups = []
  const bySource = new Map()
  for (const f of fields.value) {
    const s = f.source || 'scenario'
    if (!bySource.has(s)) bySource.set(s, [])
    bySource.get(s).push(f)
  }
  for (const s of order) {
    if (bySource.has(s)) groups.push({ source: s, fields: bySource.get(s) })
  }
  for (const [s, fs] of bySource.entries()) {
    if (!order.includes(s)) groups.push({ source: s, fields: fs })
  }
  return groups
})

const groupFields = computed(() => fields.value.filter((f) => f.type === 'string' || f.type === 'boolean'))

function fieldDef(key) {
  return fields.value.find((f) => f.key === key)
}
function isNumField(key) {
  return fieldDef(key)?.type === 'number'
}
function isBoolField(key) {
  return fieldDef(key)?.type === 'boolean'
}

function addFormula() {
  cfg.value.formulas.push({ column: '', expression: '', aggregate: '' })
}
function addVar() {
  const k = newVarName.value.trim().replace(/[^A-Za-z0-9_]/g, '')
  if (k && cfg.value.scenario[k] === undefined) cfg.value.scenario[k] = 0
  newVarName.value = ''
}
function addFilter() {
  cfg.value.filters.push({ field: '', op: 'eq', value: undefined })
}

async function runEval() {
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'pivot') {
      const rows = pivotRows.value.map((r) => r && r.trim()).filter(Boolean)
      const values = pivotVals.value
        .filter((v) => v.field && v.field.trim())
        .map((v) => ({ field: v.field, agg: v.agg || 'SUM' }))
      const chart = Object.assign({}, cfg.value.chartConfig || {}, {
        filters: (cfg.value.filters || []).filter((f) => f.field),
      })
      const payload = {
        baseSource: cfg.value.baseSource || 'mawb',
        rows,
        values,
        column: pivotColumn.value || null,
        chartConfig: chart,
      }
      const res = await dashboardBuilderApi.pivot(payload)
      pivotResult.value = res.data
      result.value = null
    } else {
      const payload = JSON.parse(JSON.stringify(cfg.value))
      const chart = Object.assign({}, payload.chartConfig || {}, { filters: (payload.filters || []).filter((f) => f.field) })
      if (topN.value) chart.topN = topN.value
      chart.type = chartType.value
      chart.x = chartX.value
      chart.y = chartY.value
      payload.chartConfig = chart
      delete payload.filters
      const res = await dashboardBuilderApi.evaluate(payload)
      result.value = res.data
      pivotResult.value = null
    }
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || String(e)
  } finally {
    busy.value = false
  }
  scrollToChart()
}

// Bring the chart into view after running a query so the user sees it immediately
function scrollToChart() {
  requestAnimationFrame(() => {
    const el = chartCard.value
    if (!el) return
    const scroller = el.closest('.overflow-y-auto') || document.querySelector('main.overflow-auto')
    if (scroller) scroller.scrollTo({ top: scroller.scrollHeight, behavior: 'smooth' })
  })
}

async function saveReport() {
  if (!cfg.value.name) { error.value = t('db.nameRequired'); return }
  const payload = JSON.parse(JSON.stringify(cfg.value))
  const chart = Object.assign({}, payload.chartConfig || {}, { filters: payload.filters || [] })
  chart.type = chartType.value
  chart.x = chartX.value
  chart.y = chartY.value
  chart.pivot = {
    rows: pivotRows.value.map((r) => r && r.trim()).filter(Boolean),
    column: pivotColumn.value || null,
    values: pivotVals.value.filter((v) => v.field && v.field.trim()).map((v) => ({ field: v.field, agg: v.agg || 'SUM' })),
  }
  chart.mode = mode.value
  payload.chartConfig = chart
  delete payload.filters
  try {
    const res = currentReportId.value
      ? await dashboardBuilderApi.updateReport(currentReportId.value, payload)
      : await dashboardBuilderApi.createReport({ userId: auth.userId, ...payload })
    currentReportId.value = res.data.id
    await loadSavedReports()
    error.value = ''
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || String(e)
  }
}

async function loadReport() {
  if (!loadKey.value) return
  try {
    const res = await dashboardBuilderApi.getReport(loadKey.value)
    const r = res.data
    currentReportId.value = r.id
    const chart = r.chartConfig || {}
    cfg.value = {
      name: r.name || '',
      dimension: r.dimension || '',
      fieldSources: r.fieldSources || ['AwbNumber', 'MawbPieces', 'FlightNumber', 'AirlineCode'],
      formulas: r.formulas || [],
      scenario: r.scenario || {},
      filters: chart.filters || [],
      chartConfig: { type: 'bar', ...chart },
      baseSource: r.baseSource || 'mawb',
    }
    chartType.value = ['bar', 'line', 'area', 'pie'].includes(chart.type) ? chart.type : 'bar'
    chartX.value = chart.x || ''
    chartY.value = chart.y || ''
    const pv = chart.pivot || {}
    if (Array.isArray(pv.rows) && pv.rows.length) pivotRows.value = pv.rows
    pivotColumn.value = pv.column || ''
    if (Array.isArray(pv.values) && pv.values.length) pivotVals.value = pv.values
    if (chart.mode === 'pivot') mode.value = 'pivot'
    await runEval()
  } catch (e) { error.value = e?.response?.data?.message || e.message || String(e) }
}

async function deleteReport() {
  if (!currentReportId.value) return
  try {
    await dashboardBuilderApi.deleteReport(currentReportId.value, null)
    currentReportId.value = null
    loadKey.value = ''
    await loadSavedReports()
  } catch (e) { error.value = e?.response?.data?.message || e.message || String(e) }
}

async function loadSavedReports() {
  try {
    const res = await dashboardBuilderApi.listReports(auth.userId)
    savedReports.value = res.data || []
  } catch {
    savedReports.value = savedReports.value || []
  }
}

function columnLabel(c) {
  const f = fieldDef(c)
  if (f) return f.label
  if (c === '__label') return ''
  if (c === 'GrossLbs' || c === 'NetLbs' || c === 'TareLbs' || c === 'TareKg' || c === 'GrossKg' || c === 'NetKg') return c
  return c
}

function formatCell(row, col) {
  if (col === '__label') return row[col] ?? ''
  const v = row[col]
  if (v === null || v === undefined) return ''
  if (typeof v === 'boolean') return v ? t('common.yes') : t('common.no')
  if (typeof v === 'number') {
    return Number.isInteger(v) ? String(v) : v.toFixed(2)
  }
  return v
}

function labelOf(key) {
  const f = fieldDef(key)
  return f ? f.label : (key === '__summaryRow' ? t('db.pivotTotal') : key)
}
function fmtPivot(v) {
  if (v === null || v === undefined) return ''
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : v.toFixed(2)
  return v
}
function toggleMode() {
  mode.value = mode.value === 'pivot' ? 'flat' : 'pivot'
  if (mode.value === 'pivot' && !pivotResult.value) runEval()
  else if (mode.value === 'flat') pivotResult.value = null
}

// Chart geometry
const chartW = 600
const chartH = 240
const chartPad = 36
const showLabels = ref(true)
const chartSvg = ref(null)
const chartCard = ref(null)

const chartType = ref('bar')
const chartX = ref('')
const chartY = ref('')

// Unified chart source: works for both flat (result) and pivot (pivotResult)
// so the graph is visible in every mode.
const chartColumns = computed(() => {
  if (result.value?.columns?.length) return result.value.columns
  const pr = pivotResult.value
  if (!pr) return []
  const cols = pr.rowFields.slice(0, 1).concat(pr.measures.length ? [pr.measures[0]] : [])
  return cols
})
const chartRows = computed(() => {
  if (result.value?.rows?.length) return result.value.rows
  const pr = pivotResult.value
  if (!pr?.rows?.length) return []
  const rf = pr.rowFields[0]
  const mIdx = 0 // use first measure
  return pr.rows.map((row) => {
    const label = rf ? ((row.key && row.key[0]) ?? '__single') : '__single'
    const obj = { __label: label }
    if (rf) obj[rf] = label === '__single' ? '' : label
    // Prefer the grand-total column group (all rows aggregated), else the first group
    let value = null
    const gi = pr.colGroups.indexOf('__total') >= 0 ? pr.colGroups.indexOf('__total') : 0
    const idx = gi * (pr.measures?.length || 1) + mIdx
    if (row.cells && row.cells.length) value = row.cells[idx] ?? null
    if (pr.measures[mIdx]) obj[pr.measures[mIdx]] = typeof value === 'number' ? value : null
    return obj
  })
})

const numericColumns = computed(() => {
  const cols = chartColumns.value
  return cols.filter((c) => c.slice(0, 2) !== '__' && chartRows.value.some((r) => typeof r[c] === 'number'))
})
const xCandidates = computed(() => {
  const cols = chartColumns.value
  return cols.filter((c) => c.slice(0, 2) !== '__' && !numericColumns.value.includes(c))
})

const chartDataCol = computed(() => {
  if (!chartRows.value.length) return null
  const y = chartY.value && numericColumns.value.includes(chartY.value) ? chartY.value : numericColumns.value[numericColumns.value.length - 1] || ''
  const x = chartX.value || xCandidates.value[0] || chartColumns.value[0] || ''
  return { x, y }
})

function short(s, n = 12) {
  const t = String(s ?? '')
  return t.length > n ? t.slice(0, n - 1) + '…' : t
}
const pointsData = computed(() => {
  const d = chartDataCol.value
  if (!d) return []
  const n = chartRows.value.length || 1
  const step = (chartCanvasW.value - 2 * chartPad) / Math.max(n, 1)
  return chartRows.value.map((r, i) => {
    const label = String(r[d.x] ?? '')
    const value = typeof r[d.y] === 'number' ? r[d.y] : 0
    return { label, shortLabel: short(label), value, x: chartPad + i * step + step / 2 }
  })
})

// Dynamic canvas width: expand horizontally when there are many points so bars
// keep a comfortable width and the user can scroll (better distribution).
const chartCanvasW = computed(() => {
  const n = chartRows.value.length || 0
  const minW = chartW
  const perPoint = 64
  return chartType.value === 'pie' ? chartW : Math.max(minW, chartPad * 2 + n * perPoint)
})

const maxVal = computed(() => {
  const m = Math.max(...pointsData.value.map((s) => s.value), 0)
  return m === 0 ? 1 : m
})
// "Nice" ticks: 4 gradaciones redondas
const niceMax = computed(() => {
  const raw = maxVal.value
  const mag = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1))))
  const n = raw / mag
  const step = n > 5 ? 2 : n > 2 ? 1 : 0.5
  return (step * mag * 4).toFixed(raw * mag >= 1 ? 0 : 2) * 1
})
const gridTicks = computed(() => Array.from({ length: 5 }, (_, i) => {
  const v = (niceMax.value / 4) * i
  const label = v >= 1000 ? (v / 1000).toFixed(v % 1000 === 0 ? 0 : 1) + 'k'
    : Number.isInteger(v) ? String(v) : v.toFixed(2)
  return { v, label }
}))
const yAt = (v) => chartH - chartPad - (v / niceMax.value) * (chartH - 2 * chartPad)

const bars = computed(() => {
  const n = pointsData.value.length || 1
  const bw = Math.min(48, (chartCanvasW.value - 2 * chartPad) / n - 2)
  return pointsData.value.map((s, i) => {
    const h = Math.max(2, (s.value / maxVal.value) * (chartH - 2 * chartPad))
    const x = chartPad + i * ((chartCanvasW.value - 2 * chartPad) / n) + 1
    const y = chartH - chartPad - h
    const valText = s.value >= 1000
      ? (s.value / 1000).toFixed(s.value % 1000 === 0 ? 0 : 1) + 'k'
      : Math.round(s.value * 10) / 10
    return { x, y, w: bw, h, fill: PALETTE[i % PALETTE.length], text: String(valText), label: `${s.label}: ${s.value}` }
  })
})

const seriesPoints = computed(() => {
  const n = pointsData.value.length || 1
  const step = (chartCanvasW.value - 2 * chartPad) / (Math.max(n, 1))
  return pointsData.value.map((s, i) => {
    const h = (s.value / maxVal.value) * (chartH - 2 * chartPad)
    return { x: chartPad + i * step + step / 2, y: chartH - chartPad - Math.max(0, h), label: `${s.label}: ${s.value}` }
  })
})
const seriesPath = computed(() => {
  const apt = seriesPoints.value
  if (!apt.length) return ''
  return apt.map((p, i) => (i === 0 ? `M ${p.x} ${p.y}` : ` L ${p.x} ${p.y}`)).join('')
})

const PALETTE = ['#0ea5e9', '#8b5cf6', '#f59e0b', '#10b981', '#ef4444', '#ec4899', '#14b8a6', '#f97316', '#6366f1', '#a3e635']
function arc(startAngle, endAngle, r) {
  const sx = Math.cos(startAngle) * r, sy = Math.sin(startAngle) * r
  const ex = Math.cos(endAngle) * r, ey = Math.sin(endAngle) * r
  const large = endAngle - startAngle > Math.PI ? 1 : 0
  return `M ${sx} ${sy} A ${r} ${r} 0 ${large} 1 ${ex} ${ey} L 0 0 Z`
}
const pieTotal = computed(() => pointsData.value.reduce((a, s) => a + Math.max(0, s.value), 0))
const pieSlices = computed(() => {
  if (!pieTotal.value) return []
  const r = 68
  let angle = -Math.PI / 2
  return pointsData.value.map((s, i) => {
    const frac = Math.max(0, s.value) / pieTotal.value
    const start = angle
    const end = angle + frac * 2 * Math.PI
    angle = end
    const pct = Math.round(frac * 1000) / 10
    return { path: arc(start, end, r), fill: PALETTE[i % PALETTE.length], label: short(s.label, 20), pct, value: s.value }
  })
})
const slicePath = (s) => s.path

function exportPng() {
  const svg = chartSvg.value
  if (!svg) return
  const clone = svg.cloneNode(true)
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  const vb = clone.getAttribute('viewBox') || ''
  const vbParts = vb.split(/[\s,]+/).map(Number)
  const w = vbParts[2] ? vbParts[2] : chartW
  const h = vbParts[3] ? vbParts[3] : chartH
  // Inject fonts so the exported image keeps the current font
  clone.setAttribute('width', w)
  clone.setAttribute('height', h)
  const style = document.createElement('style')
  style.textContent = '.chart-text{font-family:' + getComputedStyle(document.body).fontFamily + ';}'
  clone.appendChild(style)
  const xml = new XMLSerializer().serializeToString(clone)
  const blob = new Blob([xml], { type: 'image/svg+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const img = new Image()
  img.onload = () => {
    const canvas = document.createElement('canvas')
    canvas.width = w * 2
    canvas.height = h * 2
    const ctx = canvas.getContext('2d')
    ctx.scale(2, 2)
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, w, h)
    ctx.drawImage(img, 0, 0, w, h)
    URL.revokeObjectURL(url)
    const link = document.createElement('a')
    link.download = (cfg.value.name || 'reporte') + '_grafico.png'
    link.href = canvas.toDataURL('image/png')
    link.click()
  }
  img.src = url
}

function downloadBlob(content, mime, filename) {
  const blob = new Blob([content], { type: mime })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link)
}

function exportCsv() {
  const rows = result.value?.rows || []
  const cols = result.value?.columns || []
  if (!cols.length) return
  const escape = (v) => {
    const s = v === null || v === undefined ? '' : String(v)
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
  }
  const lines = []
  lines.push(cols.map((c) => escape(columnLabel(c))).join(','))
  for (const r of rows) {
    lines.push(cols.map((c) => escape(formatCell(r, c))).join(','))
  }
  downloadBlob('\uFEFF' + lines.join('\n'), 'text/csv;charset=utf-8', (cfg.value.name || 'reporte') + '.csv')
}

onMounted(() => {
  dashboardBuilderApi.getFields().then((res) => {
    fields.value = res.data || []
  }).catch(() => { fields.value = [] })
  loadSavedReports()
})
</script>

<style scoped>
.ds-builder { display: flex; flex-direction: column; height: 100%; }
</style>