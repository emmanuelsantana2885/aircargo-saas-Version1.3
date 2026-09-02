<script setup>
// ResponsiveTable.vue — rendereza una tabla normal en escritorio y tarjetas
// apiladas en móvil (<=640px) sin duplicar la lógica de datos.
// Ambas representaciones se montan y CSS elige cuál mostrar por breakpoint,
// así no depende de listeners de resize.
const props = defineProps({
  columns: { type: Array, required: true }, // [{ key, label, slot?|value(via row), width?, hideSm?, align? }]
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  emptyText: { type: String, default: '' },
  // Si se pasa, columna índice sticky a la izquierda en la tabla
  indexColumn: { type: Boolean, default: false },
})

function cellValue(row, col) {
  if (col.value != null) return col.value(row)
  const v = row[col.key]
  return v == null ? '' : v
}
</script>

<template>
  <div>
    <!-- Desktop / tablet: tabla normal -->
    <div class="responsive-table-desktop">
      <div class="table-scroll-wrapper overflow-x-auto">
        <table class="min-w-full w-full border-collapse">
          <thead>
            <tr class="bg-slate-100 text-[11px] uppercase tracking-wide text-slate-500">
              <th v-if="props.indexColumn" class="sticky left-0 z-10 bg-slate-100 px-2 py-2 border-b border-slate-200">#</th>
              <th
                v-for="(col, i) in props.columns"
                :key="col.key || i"
                :data-col="col.hideSm ? 'hide-sm' : undefined"
                :class="[col.align === 'right' ? 'text-right' : 'text-left', col.width ? col.width : '']"
                class="px-2 py-2 border-b border-slate-200 font-semibold whitespace-nowrap"
              >{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, ri) in props.rows"
              :key="row[props.rowKey] ?? ri"
              class="border-b border-slate-100 hover:bg-slate-50"
            >
              <td v-if="props.indexColumn" class="sticky left-0 z-10 bg-white px-2 py-2 text-[11px] text-slate-400">{{ ri + 1 }}</td>
              <td
                v-for="(col, i) in props.columns"
                :key="col.key || i"
                :data-col="col.hideSm ? 'hide-sm' : undefined"
                :class="[col.align === 'right' ? 'text-right' : 'text-left']"
                class="px-2 py-2 text-[11px] text-slate-700 whitespace-nowrap"
              >
                <slot :name="`cell-${col.key}`" :row="row" :value="cellValue(row, col)">
                  {{ cellValue(row, col) }}
                </slot>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Móvil: tarjetas -->
    <div class="responsive-table-cards space-y-2">
      <div
        v-for="(row, ri) in props.rows"
        :key="row[props.rowKey] ?? ri"
        class="border border-slate-200 rounded-lg p-3 bg-white shadow-sm"
      >
        <div v-if="props.indexColumn" class="text-[10px] text-slate-400 mb-1">#{{ ri + 1 }}</div>
        <div
          v-for="(col, i) in props.columns"
          :key="col.key || i"
          class="flex justify-between items-baseline gap-3 py-1 border-b border-slate-50 last:border-0"
        >
          <span class="text-[11px] text-slate-500 shrink-0">{{ col.label }}</span>
          <span class="text-[12px] font-medium text-slate-800 text-right break-words min-w-0">
            <slot :name="`cell-${col.key}`" :row="row" :value="cellValue(row, col)">
              {{ cellValue(row, col) }}
            </slot>
          </span>
        </div>
      </div>
      <div v-if="props.rows.length === 0 && props.emptyText" class="text-center text-[12px] text-slate-400 py-4">
        {{ props.emptyText }}
      </div>
    </div>
  </div>
</template>

<style scoped>
@media (max-width: 640px) {
  .responsive-table-desktop { display: none !important; }
}
@media (min-width: 641px) {
  .responsive-table-cards { display: none !important; }
}
</style>
