<template>
  <div class="flex items-center gap-1 flex-wrap justify-center min-w-[80px]">
    <template v-for="item in visible" :key="item.type">
      <span
        class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono font-medium uppercase tracking-tight border"
        :style="chipStyle(item.type)"
        :title="`${item.label}: ${item.weight} lbs (${item.pieces} pcs)`">
        <span class="w-1.5 h-1.5 rounded-full" :style="{ background: chipColor(item.type) }"></span>
        <span>{{ item.shortLabel }}</span>
        <span class="text-[10px] text-slate-500 font-normal">{{ item.weight }} lbs</span>
      </span>
    </template>
    <span v-if="hidden > 0" class="px-1.5 py-0.5 text-[11px] font-mono text-slate-400" title="{{ hidden }} más">
      +{{ hidden }}
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '../stores/app'
import { useCommodities } from '../composables/useCommodities'

const props = defineProps({
  flightId: { type: String, required: true },
  maxVisible: { type: Number, default: 3 },
})

const appStore = useAppStore()
const { commodities: dbCommodities } = useCommodities()

const COMMODITY_MAP = computed(() => {
  const map = {}
  for (const c of dbCommodities.value) {
    const shortLen = Math.min(c.code.length, 4)
    map[c.code] = { label: c.label, short: c.code.slice(0, shortLen), color: c.color || '#94a3b8' }
  }
  return map
})

const fallback = { label: 'DRY CARGO', short: 'DRY', color: '#64748b' }

function mawbsForFlight() {
  return appStore.mawbs.filter(m => m.flightId === props.flightId)
}

const aggregated = computed(() => {
  const map = {}
  const mawbs = mawbsForFlight()

  mawbs.forEach(m => {
    const type = m.commodityType || 'DRY_CARGO'
    const info = COMMODITY_MAP.value[type] || fallback
    const weight = Number(m.chargeableWeightKg || m.reportedWeightKg || 0) * 2.20462 // kg → lbs
    const pieces = Number(m.pieces || 0)

    if (!map[type]) {
      map[type] = { type, label: info.label, shortLabel: info.short, color: info.color, weight: 0, pieces: 0 }
    }
    map[type].weight += weight
    map[type].pieces += pieces
  })

  return Object.values(map)
    .filter(c => c.weight > 0 || c.pieces > 0)
    .sort((a, b) => b.weight - a.weight)
})

const visible = computed(() => aggregated.value.slice(0, props.maxVisible))
const hidden = computed(() => Math.max(0, aggregated.value.length - props.maxVisible))

function chipStyle(type) {
  const info = COMMODITY_MAP.value[type] || fallback
  return {
    background: info.color + '15',
    borderColor: info.color + '40',
    color: info.color,
  }
}

function chipColor(type) {
  const info = COMMODITY_MAP.value[type] || fallback
  return info.color
}
</script>

<style scoped>
/* chips are inline-styled */
</style>