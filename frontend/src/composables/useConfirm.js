import { ref } from 'vue'

const visible = ref(false)
const title = ref('')
const message = ref('')
const confirmText = ref('Aceptar')
const cancelText = ref('Cancelar')
const danger = ref(false)
let resolvePromise = null

export function useConfirm() {
  function confirm(opts) {
    title.value = opts.title || ''
    message.value = opts.message || ''
    confirmText.value = opts.confirmText || 'Aceptar'
    cancelText.value = opts.cancelText || 'Cancelar'
    danger.value = opts.danger || false
    visible.value = true
    return new Promise(resolve => { resolvePromise = resolve })
  }

  function onConfirm() { visible.value = false; resolvePromise?.(true) }
  function onCancel() { visible.value = false; resolvePromise?.(false) }

  return { visible, title, message, confirmText, cancelText, danger, confirm, onConfirm, onCancel }
}
