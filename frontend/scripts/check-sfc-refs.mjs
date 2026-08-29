#!/usr/bin/env node
/**
 * check-sfc-refs.mjs
 *
 * Guard estático anti-runtime-errors para SFC Vue (script setup).
 * Detecta la clase de bug "identificador usado pero nunca definido":
 *   - referencias a refs/funciones en <template> que no existen en <script setup>
 *   - llamadas a funciones inexistentes dentro del propio <script setup>
 *     (caso real: loadConfirmedWithOptions() eliminada pero seguía llamada en watch/onMounted)
 *
 * Sin dependencias (Node >= 14). Uso: node scripts/check-sfc-refs.mjs
 * Exit 1 si hay referencias no resueltas (para integrarlo en lint/CI).
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'

const ROOT = join(import.meta.dirname, '..')
const SRC_DIR = join(ROOT, 'src')

const JS_KEYWORDS = new Set([
  'as', 'async', 'await', 'break', 'case', 'catch', 'class', 'const', 'continue',
  'debugger', 'default', 'delete', 'do', 'else', 'enum', 'export', 'extends', 'false',
  'finally', 'for', 'from', 'function', 'get', 'if', 'import', 'in', 'instanceof',
  'let', 'new', 'null', 'of', 'return', 'set', 'static', 'super', 'switch', 'this',
  'throw', 'true', 'try', 'typeof', 'undefined', 'var', 'void', 'while', 'with', 'yield',
])

const GLOBALS = new Set([
  // literales numéricos / valores
  'NaN', 'Infinity',
  // tipos / constructores
  'Number', 'String', 'Boolean', 'Object', 'Array', 'Function', 'RegExp', 'Date',
  'Map', 'Set', 'WeakMap', 'WeakSet', 'Promise', 'Symbol', 'BigInt', 'Error',
  'TypeError', 'RangeError', 'ReferenceError', 'SyntaxError', 'URIError',
  'ArrayBuffer', 'SharedArrayBuffer', 'DataView', 'Uint8Array', 'Uint16Array',
  'Uint32Array', 'Int8Array', 'Int16Array', 'Int32Array', 'Float32Array',
  'Float64Array', 'BigInt64Array', 'BigUint64Array',
  // funciones globales
  'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'decodeURI', 'decodeURIComponent',
  'encodeURI', 'encodeURIComponent', 'escape', 'unescape', 'eval', 'globalThis',
  'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'queueMicrotask',
  'structuredClone', 'atob', 'btoa', 'requestAnimationFrame', 'cancelAnimationFrame',
  'fetch', 'import', 'Intl', 'JSON', 'Math', 'Reflect', 'Proxy',
  // browser / DOM / APIs
  'window', 'document', 'navigator', 'location', 'history', 'screen', 'localStorage',
  'sessionStorage', 'crypto', 'performance', 'console', 'URL', 'URLSearchParams',
  'Blob', 'File', 'FileReader', 'FormData', 'Headers', 'Request', 'Response',
  'AbortController', 'AbortSignal', 'Event', 'CustomEvent', 'KeyboardEvent',
  'MouseEvent', 'PointerEvent', 'InputEvent', 'FocusEvent', 'ClipboardEvent',
  'SubmitEvent', 'TextEncoder', 'TextDecoder', 'Notification', 'Audio', 'Image',
  'WebSocket', 'EventSource', 'Worker', 'IntersectionObserver', 'ResizeObserver',
  'MutationObserver', 'DOMParser', 'XMLHttpRequest', 'DOMException', 'Node',
  'Element', 'HTMLElement', 'HTMLInputElement', 'SVGElement', 'getComputedStyle',
  'matchMedia', 'requestIdleCallback', 'cancelIdleCallback', 'getSelection',
  'CSS', 'import.meta', 'BarcodeDetector',
])

// Macros de compilación de Vue (no requieren import)
const VUE_MACROS = new Set([
  'defineProps', 'defineEmits', 'defineExpose', 'defineModel', 'defineSlots',
  'defineOptions', 'defineComponent', 'defineAsyncComponent', 'withDefaults',
])

/**
 * Reemplaza string literals por espacios (conserva offsets).
 * No soporta template literals con ${...} dentro (se descartan a propósito:
 * es un guard conservador, no un parser completo).
 */
function stripStrings(code) {
  let out = ''
  let i = 0
  let quote = null
  while (i < code.length) {
    const c = code[i]
    if (quote === null) {
      if (c === "'" || c === '"' || c === '`') {
        quote = c
        out += ' '
      } else {
        out += c
      }
      i++
      continue
    }
    if (c === '\\') {
      out += '  '
      i += 2
      continue
    }
    if (c === quote) {
      quote = null
      out += ' '
    } else {
      out += c === '\n' ? '\n' : ' '
    }
    i++
  }
  return out
}

/** Quita literales regex (/.../flags) conservando offsets. Heurística: un `/` tras operador/inicio es regex. */
function stripRegexLiterals(code) {
  let out = ''
  let i = 0
  const prevSignificant = (j) => {
    while (j >= 0 && /\s/.test(code[j])) j--
    return j >= 0 ? code[j] : ''
  }
  while (i < code.length) {
    const c = code[i]
    if (c === '/' && code[i + 1] !== '/' && code[i + 1] !== '*') {
      const p = prevSignificant(i - 1)
      const startsRegex = p === '' || '([{=,:;!&|?+-*%^~<>'.includes(p)
      if (startsRegex) {
        out += ' '
        i++
        let inClass = false
        while (i < code.length) {
          if (code[i] === '\\') { out += '  '; i += 2; continue }
          if (code[i] === '[') { inClass = true; out += ' '; i++; continue }
          if (code[i] === ']' && inClass) { inClass = false; out += ' '; i++; continue }
          if (code[i] === '\n') { out += '\n'; i++; continue }
          if (code[i] === '/' && !inClass) { out += ' '; i++; break }
          out += ' ';
          i++
        }
        while (i < code.length && /[A-Za-z]/.test(code[i])) { out += ' '; i++ }
        continue
      }
    }
    out += c
    i++
  }
  return out
}

/** Quita comentarios de línea (//) y de bloque (/* \*\/) conservando offsets. Respeta strings. */
function stripComments(code) {
  let out = ''
  let i = 0
  let quote = null
  while (i < code.length) {
    const c = code[i]
    if (quote === null) {
      if (c === "'" || c === '"' || c === '`') {
        quote = c
        out += c
        i++
        continue
      }
      if (c === '/' && code[i + 1] === '/') {
        while (i < code.length && code[i] !== '\n') { out += ' '; i++ }
        continue
      }
      if (c === '/' && code[i + 1] === '*') {
        out += '  '
        i += 2
        while (i < code.length) {
          if (code[i] === '*' && code[i + 1] === '/') { out += '  '; i += 2; break }
          out += code[i] === '\n' ? '\n' : ' '
          i++
        }
        continue
      }
      out += c
      i++
      continue
    }
    // dentro de string: solo importa el cierre, respeta escapes
    if (c === '\\') {
      out += c + (code[i + 1] ?? '')
      i += 2
      continue
    }
    if (c === quote) {
      quote = null
      out += c
    } else {
      out += c
    }
    i++
  }
  return out
}

function isPropertyPosition(code, idx) {
  let j = idx - 1
  while (j >= 0 && /\s/.test(code[j])) j--
  return j >= 0 && code[j] === '.'
}

function isObjectKey(code, idx) {
  let j = idx - 1
  while (j >= 0 && /\s/.test(code[j])) j--
  if (j >= 0 && (code[j] === '{' || code[j] === ',')) {
    let k = idx
    while (k < code.length && /[A-Za-z0-9_$]/.test(code[k])) k++
    while (k < code.length && /\s/.test(code[k])) k++
    return k < code.length && code[k] === ':'
  }
  return false
}

/** Todos los identificadores candidatos de un texto (tras limpiar strings). No position props ni object keys. */
function candidates(code) {
  const out = []
  const re = /[A-Za-z_$][A-Za-z0-9_$]*/g
  let m
  while ((m = re.exec(code))) {
    const token = m[0]
    if (isPropertyPosition(code, m.index)) continue
    if (isObjectKey(code, m.index)) continue
    out.push({ name: token, index: m.index })
  }
  return out
}

/** Nombres definidos a partir del código de <script setup>. */
function extractDefinitions(script) {
  const defs = new Set()

  // imports — parseo manual del specifier (robusto ante default/named/namespace/mixto)
  const importRe = /import\s+([^;\n]+?)\s*from\s+[^;\n]+|import\s+['"][^'"]+['"]/g
  let m
  while ((m = importRe.exec(script))) {
    const spec = (m[1] || '').trim()
    if (!spec) continue // side-effect import ('./x.css')
    let parts = [spec]
    if (spec.startsWith('{')) {
      parts = spec.slice(1, -1).split(',').map((p) => p.trim())
    } else if (spec.startsWith('*')) {
      const ns = spec.match(/\*\s+as\s+([A-Za-z_$][A-Za-z0-9_$]*)/)
      if (ns) defs.add(ns[1])
      continue
    } else {
      parts = [spec]
    }
    for (const p of parts) {
      const name = p.split(/\s+as\s+/).pop().trim()
      if (!name || !/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) continue
      defs.add(name)
    }
  }

  // declaraciones simples y destructuring, SIN saltar líneas ni comas del cuerpo:
  // const a = .. , const {b} = .. , let c .. , let d, e = ..
  const declRe = /(?:^|[\n;}])\s*(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*|\{[^}]*\}|\[[^\]]*\])\s*/g
  while ((m = declRe.exec(script))) {
    for (const name of destructureNames(m[1])) defs.add(name)
  }
  // declaradores múltiples en una misma sentencia: let pcs = 0, gross = 0, tare = 0
  const multiDeclRe = /,\s*([A-Za-z_$][A-Za-z0-9_$]*|\{[^}]*\}|\[[^\]]*\])\s*=(?!=)/g
  while ((m = multiDeclRe.exec(script))) {
    for (const name of destructureNames(m[1])) defs.add(name)
  }
  // declaraciones tras for/while/catch/function( y operadores
  const declRe2 = /\b(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*|\{[^}]*\}|\[[^\]]*\])\s*(?==|[;,]\s*$|[;,]\s*(?:const|let|var|undefined|`))/g
  while ((m = declRe2.exec(script))) {
    for (const name of destructureNames(m[1])) defs.add(name)
  }

  // destructuring sin declarador: ({ a } = x) /  [a] = x
  const destrRe = /\b\s*(\{[^}]*\}\s*=\s*[^;]|\[[^\]]*\]\s*=\s*[^;])\b/g
  while ((m = destrRe.exec(script))) {
    const head = m[1].split('=')[0].trim()
    for (const name of destructureNames(head)) defs.add(name)
  }

  // props de defineProps({...}): claves de objeto de nivel superior + params de callback
  const propsRe = /defineProps\s*\(([\s\S]*?)\)[\s;]*/g
  let pm
  while ((pm = propsRe.exec(script))) {
    if (pm[1].trim().startsWith('[')) {
      // forma array: defineProps(['a','b','c'])
      for (const q of pm[1].matchAll(/'([^']+)'|"([^"]+)"/g)) {
        if (q[1]) defs.add(q[1])
      }
      continue
    }
    for (const k of topLevelObjectKeys(pm[1])) defs.add(k)
  }

  // params de arrow functions: (a, b) => ... y destructuring ({a},{b}) => ...
  const paramsRe = /\(([^()]*)\)\s*=>/g
  while ((m = paramsRe.exec(script))) {
    for (const p of splitParams(m[1])) defs.add(p)
  }
  // params de function declarations: function foo(a, b) {
  const declParamsRe = /\bfunction\s+[A-Za-z_$][\w$]*\s*\(([^()]*)\)/g
  while ((m = declParamsRe.exec(script))) {
    for (const p of splitParams(m[1])) defs.add(p)
  }
  // function declarations (nombre)
  const fnRe = /\bfunction\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(/g
  while ((m = fnRe.exec(script))) defs.add(m[1])
  // arrows de un solo parámetro (identificador simple): x => ...
  const oneParamRe = /\b([A-Za-z_$][A-Za-z0-9_$]*)\s*=>/g
  while ((m = oneParamRe.exec(script))) {
    let j = m.index - 1
    while (j >= 0 && /\s/.test(script[j])) j--
    const prev = script[j] ?? ''
    if (!/[a-zA-Z0-9_$.]/.test(prev)) defs.add(m[1])
  }
  // catch (e) / for (const x = 0; ...) / for (const x of ...) / for (const x in ...) / for (let i = 0)
  const catchRe = /\bcatch\s*\(([^)]+)\)/g
  while ((m = catchRe.exec(script))) {
    const p = m[1].trim()
    if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(p)) defs.add(p)
  }
  const forRe = /\bfor\s*\((?:(?:const|let|var)\s+)?([A-Za-z_$][A-Za-z0-9_$]*|\{[^}]*\}|\[[^\]]*\])\s*(?:in|of|=)/g
  while ((m = forRe.exec(script))) {
    for (const name of destructureNames(m[1])) defs.add(name)
  }

  return defs
}

/** Nombres de una parte destructural: "{a, b}" / "[a, b]" / "a" -> Set de nombres. */
function destructureNames(part) {
  const names = []
  const inner = part.trim()
  const body = inner.startsWith('{') || inner.startsWith('[') ? inner.slice(1, -1) : inner
  for (const seg of body.split(',')) {
    const raw = seg.trim()
    if (!raw) continue
    let name = raw
    if (raw.startsWith('...')) {
      name = raw.slice(3)
    } else {
      const colon = name.split(':')
      name = colon.length > 1 ? colon[colon.length - 1] : name
    }
    name = name.split(/[=:]/)[0].trim().trim().replace(/\s+as\s+.*$/, '')
    if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) names.push(name)
  }
  return names
}

/** Params de una lista "(a, b = 1, {c}, [d], e: f)" -> nombres definidos. */
function splitParams(list) {
  // dividir por top-level (respeta {..} [..] anidados): (a, {b}, [c, d]) => ...
  const names = []
  let parts = [list]
  const nested = /[{[]/.test(list)
  if (!nested) parts = list.split(',')
  for (const p of nested ? splitTopLevelCommas(list) : parts) {
    const raw = p.trim()
    if (!raw) continue
    if (raw.startsWith('{') || raw.startsWith('[')) {
      for (const n of destructureNames(raw)) if (n && !names.includes(n)) names.push(n)
    } else {
      const name = raw.split(/[=:]/)[0].trim().replace(/^\.{3}/, '')
      if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name) && !names.includes(name)) names.push(name)
    }
  }
  return names
}

/** Divide por comas de nivel superior (no dentro de {..} [..] (..)) conservando "@" de decorators TS.*/
function splitTopLevelCommas(list) {
  const out = []
  let depth = 0, cur = '', quote = null
  for (let i = 0; i < list.length; i++) {
    const c = list[i]
    if (quote) {
      cur += c
      if (c === quote) quote = null
      continue
    }
    if (c === "'" || c === '"' || c === '`') { quote = c; cur += c; continue }
    if (c === '{' || c === '[' || c === '(') depth++
    if (c === '}' || c === ']' || c === ')') depth--
    if (c === ',' && depth === 0) { out.push(cur); cur = ''; continue }
    cur += c
  }
  if (cur.trim()) out.push(cur)
  return out
}

/** Claves de nivel superior de un objeto literal (defineProps / defineEmits).
 *  El body viene como "{\n  prop: {...}," y las claves están a depth===1
 *  (tras el brace de apertura). Ignora quotes y claves anidadas.
 */
function topLevelObjectKeys(body) {
  const keys = []
  let depth = 0
  let i = 0
  // saltar el brace de apertura (el body empieza con "{")
  while (i < body.length) {
    const c = body[i]
    if (c === '{') { depth++; i++; break }
    if (!/\s/.test(c)) break // no empieza con {: devolver vacío
    i++
  }
  if (depth === 0) return keys
  while (i < body.length) {
    const c = body[i]
    if (c === "'" || c === '"' || c === '`') {
      const q = c
      i++
      while (i < body.length && body[i] !== q) { if (body[i] === '\\') i++; i++ }
      i++
      continue
    }
    if (c === '{') depth++
    else if (c === '}') depth--
    else if (depth === 1 && /[A-Za-z_$]/.test(c)) {
      let start = i
      while (i < body.length && /[A-Za-z0-9_$]/.test(body[i])) i++
      const key = body.slice(start, i)
      let j = i
      while (j < body.length && /\s/.test(body[j])) j++
      if (body[j] === ':' && !keys.includes(key)) keys.push(key)
    }
    i++
  }
  return keys
}

/** Nombres permitidos dentro de <template> (v-for, v-slot, $refs..., macros). */
function templateScope(template) {
  const scope = new Set()
  const forRe = /\bv-for\s*=\s*["']([^"']*)["']/g
  let m
  while ((m = forRe.exec(template))) {
    const expr = m[1]
    const inIdx = expr.search(/\sin\s|\sof\s/)
    if (inIdx === -1) continue
    const left = expr.slice(0, inIdx).trim()
    // "(uld, index)" o "uld" o "(m, idx)"
    for (const name of left.replace(/^\(|\)$/g, '').split(',').map(s => s.trim().split('=')[0].trim())) {
      if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) scope.add(name)
    }
  }
  const slotRe = /\bv-slot:[^\s=]*\s*=\s*["']\{?([^"']*)\}?["']|\#default\s*=\s*["']\{?([^"']*)\}?["']/g
  while ((m = slotRe.exec(template))) {
    const content = m[1] || m[2] || ''
    for (const name of content.split(',').map(s => s.trim().split('=')[0].trim())) {
      if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) scope.add(name)
    }
  }
  return scope
}

/** Params de arrow functions dentro de una expresión de plantilla: (e) => ... o e => ... */
function arrowParams(expr) {
  const locals = new Set()
  const multiRe = /\(([^()]*)\)\s*=>/g
  let m
  while ((m = multiRe.exec(expr))) {
    for (const p of m[1].split(',')) {
      const name = p.trim().split(/[=:]/)[0].trim().replace(/^\.{3}/, '')
      if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) locals.add(name)
    }
  }
  const oneRe = /\b([A-Za-z_$][A-Za-z0-9_$]*)\s*=>/g
  while ((m = oneRe.exec(expr))) {
    if (!/[a-zA-Z0-9_$.]/.test(expr[m.index - 1] ?? '')) locals.add(m[1])
  }
  return locals
}

function lineOf(code, globalStart, idx) {
  const upTo = code.slice(0, idx)
  const lines = upTo.split('\n').length
  return lines + globalStart // approx: +0 (block offset added by caller)
}

function analyseFile(file) {
  const src = readFileSync(file, 'utf8')
  const issues = []
  const globalBases = new Set([...GLOBALS, ...VUE_MACROS, ...JS_KEYWORDS])

  const tplMatch = src.match(/<template[\s\S]*?>([\s\S]*?)<\/template>/i)
  const setupMatches = [...src.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)]
  const setupBlock = setupMatches.find(([, attrs]) => attrs.includes('setup'))
  if (!setupBlock) return issues // no setup, no guard

  const setupCode = setupBlock[2]
  const setupStart = src.indexOf(setupCode)
  const defined = extractDefinitions(setupCode)

  // --- 1) referencias dentro del propio <script setup> ---
  const cleanSetup = stripStrings(stripComments(stripRegexLiterals(setupCode)))
  const setupStartLine = src.slice(0, setupStart).split('\n').length
  const setupCands = candidates(cleanSetup)
  for (const cand of setupCands) {
    if (!defined.has(cand.name) && !globalBases.has(cand.name) && !cand.name.startsWith('$')) {
      const line = lineOf(cleanSetup, setupStartLine, cand.index)
      issues.push({ file, line, kind: 'setup', symbol: cand.name })
    }
  }

  // --- 2) referencias del <template> ---
  if (tplMatch) {
    const template = tplMatch[1]
    const templateStart = src.indexOf(template) + tplMatch[0].indexOf(template) + 1
    const allowed = new Set([...defined, ...templateScope(template), ...globalBases])

    // mustache {{ ... }}
    const muRe = /{{([\s\S]*?)}}/g
    let mm
    while ((mm = muRe.exec(template))) {
      const clean = stripStrings(stripComments(mm[1]))
      const exprLocals = arrowParams(clean)
      for (const cand of candidates(clean)) {
        if (!allowed.has(cand.name) && !exprLocals.has(cand.name) && !cand.name.startsWith('$')) {
          const line = src.slice(0, templateStart + mm.index).split('\n').length
          issues.push({ file, line, kind: 'template', symbol: cand.name })
        }
      }
    }

    // atributos reactivos: :prop=".." @evt=".." v-*=".."
    // El nombre del binding va pegado al prefijo (sin espacios); así v-else/v-if
    // sin valor NO capturan el siguiente atributo estático class="..." .
    const attrRe = /(?:@[^\s=]*|:[^\s=]*|v-[^\s=]*)\s*=\s*"([^"]*)"/g
    while ((mm = attrRe.exec(template))) {
      const clean = stripStrings(stripComments(mm[1]))
      const exprLocals = arrowParams(clean)
      for (const cand of candidates(clean)) {
        if (!allowed.has(cand.name) && !exprLocals.has(cand.name) && !cand.name.startsWith('$')) {
          const line = src.slice(0, templateStart + mm.index).split('\n').length
          issues.push({ file, line, kind: 'template', symbol: cand.name })
        }
      }
    }
  }

  return issues
}

function walk(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) {
      walk(p, files)
    } else if (extname(p) === '.vue') {
      files.push(p)
    }
  }
  return files
}

const files = walk(SRC_DIR)
let totalIssues = 0
for (const f of files) {
  const issues = analyseFile(f)
  for (const i of issues) {
    totalIssues++
    console.log(`${i.file}:${i.line}  [${i.kind}]  símbolo no definido: ${i.symbol}`)
  }
}

if (totalIssues === 0) {
  console.log(`\ncheck-sfc-refs ✓ ${files.length} SFC analizados, 0 referencias no resueltas.`)
} else {
  console.log(`\ncheck-sfc-refs ✗ ${totalIssues} referencia(s) no resuelta(s) en ${files.length} SFC.`)
  process.exit(1)
}