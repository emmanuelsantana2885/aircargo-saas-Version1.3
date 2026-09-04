const KEY = 'aircargo_density'
const VALID = ['comfortable', 'compact']

export function getDensity() {
  const d = localStorage.getItem(KEY)
  return VALID.includes(d) ? d : 'comfortable'
}

export function applyDensity(density) {
  document.documentElement.setAttribute('data-density', density)
}

export function setDensity(density) {
  const next = VALID.includes(density) ? density : 'comfortable'
  localStorage.setItem(KEY, next)
  applyDensity(next)
  return next
}

export function initDensity() {
  applyDensity(getDensity())
}
