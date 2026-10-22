// Item and block pictures for the wiki: one 3D render per item, made in Blockbench from the game's own models.
//
// Every <ItemIcon id="..." /> and <ModelShot id="..." /> on a page (and every id in icons.json) gets a render.
// How an id is rendered is worked out from the item model, the same way the game resolves it:
//   bbmodel  items drawn by a renderer (builtin/entity, separate_transforms, composite): the Blockbench source
//            src/main/blockbench/<id>.bbmodel, or the file named in icons.json
//   java     element models (cubes, machines, slabs ...): the model flattened with its vanilla parent
//   obj      forge:obj models: the OBJ file and the texture its .mtl names
//   extrude  flat item sprites with no 3D model: the sprite extruded one pixel deep, as the game draws it in the hand
// The view angle is the game's own inventory angle (display.gui rotation) when the model sets one, else a default.
// icons.json can override the source, the view angle (rot, Minecraft item display rotation in degrees),
// which groups/elements to hide, and mirror the finished picture left to right (mirror: true).
//
// Usage (from wiki/):
//   node scripts/icons.mjs jobs      write design/wiki/icons/jobs.json for the ids whose render is missing or stale
//   (render the jobs in Blockbench with tools/blockbench/wiki_icons.js, which writes design/wiki/icons/raw/<id>.png
//    and records each source signature in raw/manifest.json)
//   node scripts/icons.mjs           build public/img/icons/<id>.webp (128 px) from the raw renders
//   node scripts/icons.mjs --check   no writes; warns on ids with no icon and on stale renders (--strict: no icon = error)
// Workspaces other than Main may add <ItemIcon>/<ModelShot> for new items; Main renders them before deploying.
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO = path.resolve(WIKI, '..')
const NS = 'apocalypse_firstlight'
const ASSETS = path.join(REPO, 'src/main/resources/assets', NS)
const BB = path.join(REPO, 'src/main/blockbench')
const DESIGN = path.join(REPO, 'design/wiki/icons')
const RAW = path.join(DESIGN, 'raw')
const OUT = path.join(WIKI, 'public/img/icons')
export const ICON_SIZE = 128

const BLOCK_ROT = [30, 225, 0] // vanilla block/block gui rotation
const ITEM_ROT = [25, -32, 0] // 3/4 view for held items and devices

const rel = (p) => path.relative(REPO, p).split(path.sep).join('/')
const readJson = (p, fallback) => (fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : fallback)
const sha = (parts) => crypto.createHash('sha256').update(parts.join('\n')).digest('hex').slice(0, 16)

// Vanilla parents that carry geometry (the rest only carry display transforms).
const FACES = ['down', 'up', 'north', 'south', 'west', 'east']
const cubeFaces = (tex) => Object.fromEntries(FACES.map((d) => [d, { uv: [0, 0, 16, 16], texture: '#' + tex(d), cullface: d }]))
const VANILLA = {
  'block/cube': { elements: [{ from: [0, 0, 0], to: [16, 16, 16], faces: cubeFaces((d) => d) }] },
  'block/cube_all': { textures: { down: '#all', up: '#all', north: '#all', south: '#all', west: '#all', east: '#all', particle: '#all' }, parent: 'block/cube' },
  'block/cube_column': { textures: { down: '#end', up: '#end', north: '#side', south: '#side', west: '#side', east: '#side', particle: '#side' }, parent: 'block/cube' },
  'block/cube_column_horizontal': { parent: 'block/cube_column' },
  'block/slab': { elements: [{ from: [0, 0, 0], to: [16, 8, 16], faces: { down: { uv: [0, 0, 16, 16], texture: '#bottom' }, up: { uv: [0, 0, 16, 16], texture: '#top' }, north: { uv: [0, 8, 16, 16], texture: '#side' }, south: { uv: [0, 8, 16, 16], texture: '#side' }, west: { uv: [0, 8, 16, 16], texture: '#side' }, east: { uv: [0, 8, 16, 16], texture: '#side' } } }] },
  'block/stairs': { elements: [
    { from: [0, 0, 0], to: [16, 8, 16], faces: { down: { uv: [0, 0, 16, 16], texture: '#bottom' }, up: { uv: [0, 0, 16, 16], texture: '#top' }, north: { uv: [0, 8, 16, 16], texture: '#side' }, south: { uv: [0, 8, 16, 16], texture: '#side' }, west: { uv: [0, 8, 16, 16], texture: '#side' }, east: { uv: [0, 8, 16, 16], texture: '#side' } } },
    { from: [8, 8, 0], to: [16, 16, 16], faces: { up: { uv: [8, 0, 16, 16], texture: '#top' }, north: { uv: [0, 0, 8, 8], texture: '#side' }, south: { uv: [8, 0, 16, 8], texture: '#side' }, west: { uv: [0, 0, 16, 8], texture: '#side' }, east: { uv: [0, 0, 16, 8], texture: '#side' } } }
  ] },
  'block/block': {},
  'item/generated': { generated: true },
  'item/handheld': { parent: 'item/generated' },
  'builtin/entity': { builtin: true }
}

function splitRef(ref) {
  const [ns, p] = ref.includes(':') ? ref.split(':') : ['minecraft', ref]
  return { ns, p }
}
function modelJson(ref) {
  const { ns, p } = splitRef(ref)
  if (ns === 'minecraft') {
    const v = VANILLA[p]
    if (!v) throw new Error(`vanilla parent ${ref} not supported`)
    return v
  }
  const f = path.join(ASSETS, 'models', p + '.json')
  if (!fs.existsSync(f)) throw new Error(`missing model ${rel(f)}`)
  return { ...readJson(f), __file: f }
}
export const textureFile = (ref) => {
  const { ns, p } = splitRef(ref)
  return ns === NS ? path.join(ASSETS, 'textures', p + '.png') : null
}

// The game's own inventory angle: the first display.gui rotation along the item's AFL model chain
// (devices are modelled facing north and turned to face the viewer here). null when the game sets none.
function guiRotation(item) {
  let m = readJson(item, null)
  for (let depth = 0; m && depth < 12; depth++) {
    if (m.display?.gui?.rotation) return m.display.gui.rotation
    if (!m.parent) return null
    const { ns, p } = splitRef(m.parent)
    if (ns !== NS) return null
    m = readJson(path.join(ASSETS, 'models', p + '.json'), null)
  }
  return null
}

// Resolve an item to a render job. Returns { kind, sources: [files], ... } or throws.
export function resolve(id, override = {}) {
  const sources = []
  const bbFile = override.bbmodel ? path.join(REPO, override.bbmodel) : path.join(BB, id + '.bbmodel')
  const item = path.join(ASSETS, 'models/item', id + '.json')
  const gui = fs.existsSync(item) ? guiRotation(item) : null
  if (override.bbmodel) {
    if (!fs.existsSync(bbFile)) throw new Error(`${id}: bbmodel ${override.bbmodel} not found`)
    return { kind: 'bbmodel', file: bbFile, sources: [bbFile], rot: override.rot ?? gui ?? ITEM_ROT }
  }
  if (!fs.existsSync(item)) throw new Error(`${id}: no item model models/item/${id}.json`)
  let m = { ...readJson(item), __file: item }
  sources.push(item)
  // separate_transforms: the 3D look is the base model; its gui perspective is only the inventory sprite.
  if (m.loader === 'forge:separate_transforms') m = { ...m.base, __file: item }
  const textures = {}
  let elements = null
  let obj = null
  let builtin = false
  let generated = false
  let composite = false
  for (let depth = 0; m && depth < 12; depth++) {
    if (m.__file && !sources.includes(m.__file)) sources.push(m.__file)
    for (const [k, v] of Object.entries(m.textures ?? {})) if (!(k in textures)) textures[k] = v
    if (!elements && m.elements) elements = m.elements
    if (m.loader === 'forge:obj') { obj = m.model; break }
    if (m.loader === 'forge:composite') { composite = true; break }
    if (m.loader) throw new Error(`${id}: loader ${m.loader} not supported, name a bbmodel in icons.json`)
    if (m.builtin) { builtin = true; break }
    if (m.generated) { generated = true; break }
    if (elements && !m.parent) break
    if (!m.parent) break
    m = modelJson(m.parent)
  }
  // resolve #refs
  const deref = (v) => { let n = 0; while (typeof v === 'string' && v.startsWith('#') && n++ < 8) v = textures[v.slice(1)]; return v }
  if (builtin || composite) {
    if (!fs.existsSync(bbFile)) throw new Error(`${id}: drawn by a renderer; add src/main/blockbench/${id}.bbmodel or a bbmodel override in icons.json`)
    sources.push(bbFile)
    return { kind: 'bbmodel', file: bbFile, sources, rot: override.rot ?? gui ?? ITEM_ROT }
  }
  if (obj) {
    const { p } = splitRef(obj)
    const objFile = path.join(ASSETS, p)
    const mtlFile = objFile.replace(/\.obj$/, '.mtl')
    sources.push(objFile)
    const tex = {}
    if (fs.existsSync(mtlFile)) {
      sources.push(mtlFile)
      let cur = null
      for (const line of fs.readFileSync(mtlFile, 'utf8').split(/\r?\n/)) {
        const [k, ...rest] = line.trim().split(/\s+/)
        if (k === 'newmtl') cur = rest.join(' ')
        if (k === 'map_Kd' && cur) tex[cur] = textureFile(deref(rest.join(' ')))
      }
    }
    if (!Object.keys(tex).length && textures.particle) tex['*'] = textureFile(deref(textures.particle))
    Object.values(tex).forEach((f) => f && sources.push(f))
    const flipV = readJson(m.__file ?? '', {}).flip_v ?? m.flip_v ?? false
    return { kind: 'obj', file: objFile, textures: tex, flipV: !!flipV, sources, rot: override.rot ?? gui ?? ITEM_ROT }
  }
  if (generated) {
    const layer = textureFile(deref(textures.layer0))
    if (!layer || !fs.existsSync(layer)) throw new Error(`${id}: sprite ${textures.layer0} not found`)
    sources.push(layer)
    if (fs.existsSync(bbFile)) { sources.push(bbFile); return { kind: 'bbmodel', file: bbFile, sources, rot: override.rot ?? gui ?? ITEM_ROT } }
    return { kind: 'extrude', texture: layer, sources, rot: override.rot ?? [20, -30, 0] }
  }
  if (elements) {
    const used = {}
    const els = elements.map((e) => ({ ...e, faces: Object.fromEntries(Object.entries(e.faces ?? {}).map(([d, f]) => {
      const t = deref(f.texture)
      const key = 'tex' + (used[t] ?? (used[t] = Object.keys(used).length))
      return [d, { ...f, texture: '#' + key }]
    })) }))
    const tex = {}
    for (const [t, i] of Object.entries(used)) {
      const f = textureFile(t)
      if (!f || !fs.existsSync(f)) throw new Error(`${id}: texture ${t} not found`)
      sources.push(f)
      tex['tex' + i] = t.includes(':') ? t : NS + ':' + t
    }
    return { kind: 'java', model: { textures: tex, elements: els }, sources, rot: override.rot ?? gui ?? BLOCK_ROT }
  }
  throw new Error(`${id}: no geometry found`)
}

// Ids used on the pages, plus icons.json. shots: ids shown as a large picture (<ModelShot>).
export function usedIds() {
  const ids = new Set(Object.keys(readJson(path.join(WIKI, 'icons.json'), {})).filter((k) => !k.startsWith('_')))
  const shots = new Set()
  const walk = (d) => {
    for (const f of fs.readdirSync(d, { withFileTypes: true })) {
      if (['node_modules', '.vitepress', 'public', 'scripts'].includes(f.name)) continue
      const p = path.join(d, f.name)
      if (f.isDirectory()) walk(p)
      else if (f.name.endsWith('.md')) for (const m of fs.readFileSync(p, 'utf8').matchAll(/<(ItemIcon|ModelShot)\s+id="([a-z0-9_]+)"/g)) {
        ids.add(m[2])
        if (m[1] === 'ModelShot') shots.add(m[2])
      }
    }
  }
  walk(WIKI)
  return { ids: [...ids].sort(), shots: [...shots].sort() }
}

export function jobFor(id) {
  const overrides = readJson(path.join(WIKI, 'icons.json'), {})
  const o = overrides[id] ?? {}
  const job = resolve(id, o)
  const extra = JSON.stringify({ rot: job.rot, hide: o.hide ?? null, kind: job.kind })
  job.sig = sha([...job.sources.map((f) => sha([fs.readFileSync(f).toString('base64')])), extra])
  job.hide = o.hide ?? null
  job.id = id
  job.out = path.join(RAW, id + '.png')
  job.sources = job.sources.map(rel)
  return job
}

async function main() {
  const args = process.argv.slice(2)
  const mode = args[0] === 'jobs' ? 'jobs' : args.includes('--check') ? 'check' : 'build'
  const all = args.includes('--all')
  const strict = args.includes('--strict')
  const manifest = readJson(path.join(RAW, 'manifest.json'), {})
  const errors = []
  const warnings = []
  const { ids, shots } = usedIds()
  const images = readJson(path.join(WIKI, 'images.json'), {})
  const jobs = []
  for (const id of ids) {
    let job
    try { job = jobFor(id) } catch (e) { errors.push(e.message); continue }
    const fresh = fs.existsSync(job.out) && manifest[id] === job.sig
    if (!fresh || all) jobs.push(job)
    if (!fresh) warnings.push(`${id}: render ${fs.existsSync(job.out) ? 'is stale' : 'missing'} (node scripts/icons.mjs jobs, then render in Blockbench)`)
    if (mode === 'check' && !fs.existsSync(path.join(OUT, id + '.webp'))) (strict ? errors : warnings).push(`${id}: public/img/icons/${id}.webp not built (node scripts/icons.mjs)`)
  }
  for (const id of shots) {
    const spec = images['model_' + id]
    if (!spec) errors.push(`${id}: <ModelShot> needs an images.json entry "model_${id}" with src design/wiki/icons/shots/${id}.png`)
    else if (spec.src !== `design/wiki/icons/shots/${id}.png`) errors.push(`model_${id}: src must be design/wiki/icons/shots/${id}.png`)
  }
  if (mode === 'jobs') {
    fs.mkdirSync(DESIGN, { recursive: true })
    fs.writeFileSync(path.join(DESIGN, 'jobs.json'), JSON.stringify({ assets: ASSETS.split(path.sep).join('/'), jobs: jobs.map((j) => ({ ...j, file: j.file?.split(path.sep).join('/'), texture: j.texture?.split(path.sep).join('/'), textures: j.textures && Object.fromEntries(Object.entries(j.textures).map(([k, v]) => [k, v?.split(path.sep).join('/')])), out: j.out.split(path.sep).join('/') })) }, null, 1))
    console.log(`${jobs.length} job(s) -> ${rel(path.join(DESIGN, 'jobs.json'))}`)
  }
  if (mode === 'build') {
    const sharp = (await import('sharp')).default
    const overrides = readJson(path.join(WIKI, 'icons.json'), {})
    fs.mkdirSync(OUT, { recursive: true })
    let n = 0
    for (const id of ids) {
      const raw = path.join(RAW, id + '.png')
      if (!fs.existsSync(raw)) continue
      // mirror: the render left-right flipped (a mesh whose game rendering mirrors x, so printed text reads backwards
      // in Blockbench, e.g. the Field Survival Guide's cover)
      const trimmed = await sharp(raw).flop(!!overrides[id]?.mirror).trim({ threshold: 1 }).png().toBuffer({ resolveWithObject: true })
      const inner = Math.round(ICON_SIZE * 0.92)
      const model = await sharp(trimmed.data).resize(inner, inner, { fit: 'inside', kernel: 'lanczos3' }).png().toBuffer({ resolveWithObject: true })
      await sharp({ create: { width: ICON_SIZE, height: ICON_SIZE, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
        .composite([{ input: model.data, left: Math.round((ICON_SIZE - model.info.width) / 2), top: Math.round((ICON_SIZE - model.info.height) / 2) }])
        .webp({ quality: 90, alphaQuality: 100 })
        .toFile(path.join(OUT, id + '.webp'))
      n++
    }
    console.log(`${n} icon(s) -> ${path.relative(WIKI, OUT)}`)
    // Large pictures: same canvas for every model, the model fills at most 84% x 80%.
    const SHOTS = path.join(DESIGN, 'shots')
    fs.mkdirSync(SHOTS, { recursive: true })
    const W = 1600, H = 1200
    for (const id of shots) {
      const raw = path.join(RAW, id + '.png')
      if (!fs.existsSync(raw)) continue
      const trimmed = await sharp(raw).flop(!!overrides[id]?.mirror).trim({ threshold: 1 }).png().toBuffer({ resolveWithObject: true })
      const model = await sharp(trimmed.data).resize(Math.round(W * 0.84), Math.round(H * 0.8), { fit: 'inside', withoutEnlargement: true, kernel: 'lanczos3' }).png().toBuffer({ resolveWithObject: true })
      await sharp({ create: { width: W, height: H, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
        .composite([{ input: model.data, left: Math.round((W - model.info.width) / 2), top: Math.round((H - model.info.height) / 2) }])
        .png()
        .toFile(path.join(SHOTS, id + '.png'))
    }
    console.log(`${shots.length} shot(s) -> ${rel(SHOTS)} (then node scripts/images.mjs)`)
  }
  if (mode !== 'jobs') for (const w of warnings) console.log('  warn  ' + w)
  for (const e of errors) console.log('  error ' + e)
  if (errors.length) process.exit(1)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main()
