const KEY = 'aircargo_font'
const VALID = ['consolas', 'nerd', 'sans', 'cascadia', 'bodoni', 'combo']
const DEFAULT = 'combo'

export function getFont() {
  const f = localStorage.getItem(KEY)
  return VALID.includes(f) ? f : DEFAULT
}

export function applyFont(font) {
  document.documentElement.setAttribute('data-font', font)
}

export function setFont(font) {
  const next = VALID.includes(font) ? font : DEFAULT
  localStorage.setItem(KEY, next)
  applyFont(next)
  return next
}

export function initFont() {
  applyFont(getFont())
}