const KEY = 'aircargo_font'
const VALID = ['consolas', 'nerd', 'sans']

export function getFont() {
  const f = localStorage.getItem(KEY)
  return VALID.includes(f) ? f : 'consolas'
}

export function applyFont(font) {
  document.documentElement.setAttribute('data-font', font)
}

export function setFont(font) {
  const next = VALID.includes(font) ? font : 'consolas'
  localStorage.setItem(KEY, next)
  applyFont(next)
  return next
}

export function initFont() {
  applyFont(getFont())
}