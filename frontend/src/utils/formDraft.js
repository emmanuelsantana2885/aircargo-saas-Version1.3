/**
 * Snapshot genérico de formularios para preservar trabajo no guardado
 * (logout por inactividad, refresh accidental, etc.).
 *
 * Captura todos los input/textarea/select visibles con una firma
 * estructural y los restaura despachando los eventos que actualizan
 * los v-model de Vue.
 */

const SELECTOR =
  'input:not([type=hidden]):not([type=file]):not([type=submit]):not([type=button]), textarea, select'

function sig(el) {
  return `${el.tagName}:${el.type || ''}:${el.id || ''}:${el.name || ''}`
}

function val(el) {
  if (el.type === 'checkbox') return el.checked ? '1' : '0'
  return el.value ?? ''
}

function apply(el, value) {
  if (el.type === 'checkbox') {
    el.checked = value === '1'
    el.dispatchEvent(new Event('change', { bubbles: true }))
    return
  }
  el.value = value
  el.dispatchEvent(new Event('input', { bubbles: true }))
  el.dispatchEvent(new Event('change', { bubbles: true }))
}

export function captureForms(root = document) {
  const els = Array.from(root.querySelectorAll(SELECTOR)).filter(
    el => !el.disabled && !el.readOnly
  )
  return els.map((el, i) => ({ i, s: sig(el), v: val(el) }))
}

/** Restaura solo los campos cuya firma coincide. @return cantidad restaurada */
export function restoreForms(snapshot, root = document) {
  const els = Array.from(root.querySelectorAll(SELECTOR)).filter(
    el => !el.disabled && !el.readOnly
  )
  let restored = 0
  for (const e of snapshot) {
    const el = els[e.i]
    if (el && sig(el) === e.s) {
      apply(el, e.v)
      restored++
    }
  }
  return restored
}

const DRAFT_KEY = 'aircargo_draft'
const RETURN_KEY = 'aircargo_return_to'

export function saveDraft(payload) {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ ...payload, ts: Date.now() }))
  } catch {}
}

export function loadDraft() {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function clearDraft() {
  sessionStorage.removeItem(DRAFT_KEY)
}

export function setReturnTo(path) {
  try {
    sessionStorage.setItem(RETURN_KEY, path)
  } catch {}
}

export function popReturnTo() {
  try {
    const p = sessionStorage.getItem(RETURN_KEY)
    sessionStorage.removeItem(RETURN_KEY)
    return p
  } catch {
    return null
  }
}
