// Resolves the <V d="..."/> and <V j="..."/> references used in wiki pages.
import fs from 'node:fs'
import path from 'node:path'
import { REPO, DATA } from './gamedata.mjs'

const WIKI = path.join(REPO, 'wiki')
const JAVA = path.join(REPO, 'src/main/java/com/antaurora/apofirstlight')

function walk(dir, ext, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'node_modules' || e.name.startsWith('.')) continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, ext, out)
    else if (p.endsWith(ext)) out.push(p)
  }
  return out
}

// Every d="..." / j="..." on a <V> tag in the wiki's markdown, with the files using it.
export function collectRefs() {
  const refs = { d: new Map(), j: new Map() }
  for (const file of walk(WIKI, '.md')) {
    const text = fs.readFileSync(file, 'utf8')
    for (const m of text.matchAll(/<V\b[^>]*?\b([dj])="([^"]+)"/g)) {
      const map = refs[m[1]]
      if (!map.has(m[2])) map.set(m[2], [])
      map.get(m[2]).push(path.relative(WIKI, file).replace(/\\/g, '/'))
    }
  }
  return refs
}

const jsonCache = new Map()
function dataValue(ref) {
  const [file, keyPath] = ref.split('#')
  const p = path.join(DATA, `${file}.json`)
  if (!jsonCache.has(p)) jsonCache.set(p, fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : null)
  let v = jsonCache.get(p)
  if (v == null) return undefined
  for (const k of (keyPath ?? '').split('.').filter(Boolean)) {
    if (v == null || typeof v !== 'object') return undefined
    v = v[k]
  }
  return typeof v === 'number' || typeof v === 'string' ? v : undefined
}

let javaIndex
// ClassName.CONST -> number, for `static final <numeric> NAME = <number literal or simple expression>`.
function javaConstants() {
  if (javaIndex) return javaIndex
  javaIndex = new Map()
  for (const file of walk(JAVA, '.java')) {
    const cls = path.basename(file, '.java')
    const text = fs.readFileSync(file, 'utf8')
    // also handles "static final int A = 1, B = A / 8;" (split on top-level commas)
    for (const m of text.matchAll(/static\s+final\s+(?:int|long|float|double)\s+([A-Z][A-Z0-9_]*\s*=[^;]+);/g)) {
      let depth = 0, cur = ''
      const parts = []
      for (const ch of m[1]) {
        if (ch === '(') depth++
        if (ch === ')') depth--
        if (ch === ',' && depth === 0) { parts.push(cur); cur = '' } else cur += ch
      }
      parts.push(cur)
      for (const part of parts) {
        const d = part.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.+?)\s*$/s)
        if (d) javaIndex.set(`${cls}.${d[1]}`, d[2])
      }
    }
  }
  return javaIndex
}

function javaValue(ref) {
  const idx = javaConstants()
  const seen = new Set()
  const evalExpr = (cls, expr) => {
    // numbers like 30_000, 0.25F, 1200L, plus + - * / ( ) and other constants of the same class
    const cleaned = expr
      .replace(/(\d)_(?=\d)/g, '$1')
      .replace(/(\d+(?:\.\d+)?)[fFdDlL]\b/g, '$1')
      .replace(/\b([A-Z][A-Z0-9_]*)\b/g, (name) => {
        const key = `${cls}.${name}`
        if (!idx.has(key) || seen.has(key)) throw new Error(name)
        seen.add(key)
        return `(${evalExpr(cls, idx.get(key))})`
      })
    if (!/^[\d\s.+\-*/()]+$/.test(cleaned)) throw new Error(expr)
    return Function(`return (${cleaned})`)()
  }
  const raw = idx.get(ref)
  if (raw == null) return undefined
  try {
    const v = evalExpr(ref.split('.')[0], raw)
    return Number.isFinite(v) ? v : undefined
  } catch {
    return undefined
  }
}

export function resolveRefs(refs) {
  const values = {}
  const missing = []
  for (const [ref, files] of refs.d) {
    const v = dataValue(ref)
    if (v === undefined) missing.push({ ref: `d:${ref}`, files })
    else values[`d:${ref}`] = v
  }
  for (const [ref, files] of refs.j) {
    const v = javaValue(ref)
    if (v === undefined) missing.push({ ref: `j:${ref}`, files })
    else values[`j:${ref}`] = v
  }
  return { values, missing }
}
