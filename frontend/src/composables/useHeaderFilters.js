import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'

export function useHeaderFilters(options = {}) {
  const headerFilterOpen = ref(null)
  const columnFilters = reactive({})

  function ensureColumn(col) {
    if (!(col in columnFilters)) columnFilters[col] = null
  }

  function toggleHeaderFilter(col) {
    ensureColumn(col)
    headerFilterOpen.value = headerFilterOpen.value === col ? null : col
  }

  function setColumnFilter(col, val) {
    ensureColumn(col)
    columnFilters[col] = val
    headerFilterOpen.value = null
  }

  function clearFilters() {
    Object.keys(columnFilters).forEach(k => { columnFilters[k] = null })
  }

  const activeFilterCount = computed(() =>
    Object.values(columnFilters).filter(v => v !== null && v !== undefined && v !== '').length
  )

  function uniqueValues(rows, keyOf) {
    const set = new Set()
    for (const r of rows) {
      const v = keyOf(r)
      if (v !== null && v !== undefined && v !== '') set.add(v)
    }
    return [...set].sort((a, b) => String(a).localeCompare(String(b), undefined, { numeric: true }))
  }

  let containerSel = options.containerSelector || '.ds-table-header'

  function onClickOutside(e) {
    if (!headerFilterOpen.value) return
    const holder = e.target && typeof e.target.closest === 'function'
      ? e.target.closest(containerSel)
      : null
    if (!holder) headerFilterOpen.value = null
  }

  if (options.bindDocument !== false) {
    onMounted(() => document.addEventListener('click', onClickOutside))
    onUnmounted(() => document.removeEventListener('click', onClickOutside))
  }

  return {
    headerFilterOpen,
    columnFilters,
    activeFilterCount,
    toggleHeaderFilter,
    setColumnFilter,
    clearFilters,
    uniqueValues,
  }
}
