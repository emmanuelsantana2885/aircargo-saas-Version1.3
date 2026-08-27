import { ref, watchEffect } from 'vue'

const STORAGE_KEY = 'aircargo_icon_lib'
const VALID = ['tabler', 'lucide']

export const iconLib = ref(
  VALID.includes(localStorage.getItem(STORAGE_KEY)) ? localStorage.getItem(STORAGE_KEY) : 'tabler'
)

watchEffect(() => {
  localStorage.setItem(STORAGE_KEY, iconLib.value)
})

export function toggleIconLib() {
  iconLib.value = iconLib.value === 'tabler' ? 'lucide' : 'tabler'
}
