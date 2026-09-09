const KEY = 'aircargo_accent'

export const ACCENTS = {
  blue: { label: 'Azul', accent: '#3e7bfa', strong: '#2b5fd9', violet: '#8b5cf6', cyan: '#06b6d4' },
  indigo: { label: 'Índigo', accent: '#6366f1', strong: '#4f46e5', violet: '#a78bfa', cyan: '#22d3ee' },
  violet: { label: 'Violeta', accent: '#8b5cf6', strong: '#7c3aed', violet: '#e879f9', cyan: '#22d3ee' },
  emerald: { label: 'Esmeralda', accent: '#10b981', strong: '#059669', violet: '#8b5cf6', cyan: '#06b6d4' },
  teal: { label: 'Teal', accent: '#14b8a6', strong: '#0d9488', violet: '#a78bfa', cyan: '#22d3ee' },
  rose: { label: 'Rosa', accent: '#f43f5e', strong: '#e11d48', violet: '#f59e0b', cyan: '#06b6d4' },
  amber: { label: 'Ámbar', accent: '#f59e0b', strong: '#d97706', violet: '#8b5cf6', cyan: '#06b6d4' },
  slate: { label: 'Gris', accent: '#64748b', strong: '#475569', violet: '#8b5cf6', cyan: '#0891b2' },
}

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  const full = h.length === 3 ? h.split('').map(c => c + c).join('') : h
  const n = parseInt(full, 16)
  return `${(n >> 16) & 255},${(n >> 8) & 255},${n & 255}`
}

function rgba(hex, alpha) {
  return `rgba(${hexToRgb(hex)},${alpha})`
}

const OVERRIDE_PROPS = ['--accent', '--accent-strong', '--accent-soft', '--accent-soft-strong']

export function applyAccent(name) {
  const el = document.documentElement
  OVERRIDE_PROPS.forEach(p => el.style.removeProperty(p))
  el.style.removeProperty('--accent-violet')
  el.style.removeProperty('--accent-cyan')
  if (!name) return
  const a = ACCENTS[name]
  if (!a) return
  el.style.setProperty('--accent', a.accent)
  el.style.setProperty('--accent-strong', a.strong)
  el.style.setProperty('--accent-soft', rgba(a.accent, 0.1))
  el.style.setProperty('--accent-soft-strong', rgba(a.accent, 0.18))
  el.style.setProperty('--accent-violet', a.violet)
  el.style.setProperty('--accent-cyan', a.cyan)
}

export function getAccent() {
  const name = localStorage.getItem(KEY)
  return name && ACCENTS[name] ? name : null
}

export function setAccent(name) {
  applyAccent(name)
  if (name) localStorage.setItem(KEY, name)
  else localStorage.removeItem(KEY)
}

export function initAccent() {
  applyAccent(getAccent())
}