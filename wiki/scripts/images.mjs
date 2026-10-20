// Wiki images (V2). Every entry of images.json is built from its source in the repo with sharp, then:
//   internal -> public/img/<key>.<fmt>, bundled only into the Access-protected site
//   public   -> uploaded to the shared R2 bucket (antaurora-assets) under afl/wiki/<category>/ and served from
//               https://img.staticgrove.net/; the URL is recorded in images.lock.json and pages use it.
// A public image that is not uploaded yet (new or changed source) is staged in public/img so local previews work;
// the strict check (deploy) fails until it is uploaded. Staged copies live in public/img/staged/ (git-ignored).
//
// Usage (from wiki/):
//   node scripts/images.mjs                    build: internal outputs + staging for public images not uploaded yet
//   node scripts/images.mjs upload             list the public images to upload (dry run)
//   node scripts/images.mjs upload --yes       upload them (Main checkout on master only), update images.lock.json
//   node scripts/images.mjs --check            no writes; errors on bad entries / missing outputs, warns on uploads due
//   node scripts/images.mjs --check --strict   same, but an image waiting for upload is an error (used by deploy)
//   node scripts/images.mjs --check --remote   also HEAD every public URL
//
// The uploader is Page Studio's (server/cloud/store.ts): credentials from ~/.page-studio/r2.json or PAGE_STUDIO_R2_*,
// conditional create only (never overwrites or deletes), content-addressed names, immutable cache headers.
// Set PAGE_STUDIO_DIR if Page Studio is not next to this repo.
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { requireMain } from './lib/main-guard.mjs'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO = path.resolve(WIKI, '..')
const OUT = path.join(WIKI, 'public/img')
const STAGED = path.join(OUT, 'staged') // git-ignored: public images waiting for upload
const LOCK = path.join(WIKI, 'images.lock.json')
export const PUBLIC_BASE = 'https://img.staticgrove.net/'
export const PREFIX = 'afl/wiki/'
export const CATEGORIES = ['guns', 'vehicles', 'items', 'industry', 'survival', 'world', 'companies', 'ui']
const TYPES = { webp: 'image/webp', png: 'image/png', jpg: 'image/jpeg' }

const args = process.argv.slice(2)
const mode = args.includes('--check') ? 'check' : args[0] === 'upload' ? 'upload' : 'build'
const strict = args.includes('--strict')
const remote = args.includes('--remote')

const sha256 = (b) => crypto.createHash('sha256').update(b).digest('hex')
const readJson = (p, fallback) => (fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : fallback)
const list = readJson(path.join(WIKI, 'images.json'), {})
const lock = readJson(LOCK, {})
const errors = []
const warnings = []
const due = [] // public images to upload: { key, spec, file, sig }

const entries = Object.entries(list).filter(([k]) => !k.startsWith('_'))
const fmtOf = (spec) => spec.format ?? 'webp'
// What the output depends on: the source bytes and the processing settings.
function sourceSig(spec) {
  const src = path.join(REPO, spec.src)
  if (!fs.existsSync(src)) return null
  const settings = JSON.stringify({ crop: spec.crop ?? null, width: spec.width ?? 1600, format: fmtOf(spec) })
  return sha256(Buffer.concat([fs.readFileSync(src), Buffer.from(settings)]))
}

async function encode(spec) {
  const sharp = (await import('sharp')).default
  let img = sharp(path.join(REPO, spec.src))
  if (spec.crop) {
    const m = await img.metadata()
    const [l, t, w, h] = spec.crop
    img = img.extract({ left: Math.round(l * m.width), top: Math.round(t * m.height), width: Math.round(w * m.width), height: Math.round(h * m.height) })
  }
  img = img.resize({ width: spec.width ?? 1600, withoutEnlargement: true })
  img = fmtOf(spec) === 'png' ? img.png() : img.webp({ quality: 82 })
  return img.toBuffer({ resolveWithObject: true })
}

if (mode !== 'check') fs.mkdirSync(STAGED, { recursive: true })

for (const [key, spec] of entries) {
  const fmt = fmtOf(spec)
  const local = path.join(OUT, `${key}.${fmt}`)
  const staged = path.join(STAGED, `${key}.${fmt}`)
  if (!/^[a-z0-9_]+$/.test(key)) { errors.push(`${key}: key must be lowercase letters, digits and _`); continue }
  if (!CATEGORIES.includes(spec.category)) { errors.push(`${key}: category must be one of ${CATEGORIES.join(', ')}`); continue }
  if (spec.visibility !== 'public' && spec.visibility !== 'internal') { errors.push(`${key}: visibility must be "public" or "internal"`); continue }
  if (!TYPES[fmt]) { errors.push(`${key}: unsupported format ${fmt}`); continue }
  const sig = sourceSig(spec)
  if (!sig) { errors.push(`${key}: missing source ${spec.src}`); continue }

  if (spec.visibility === 'internal') {
    // An internal image must never have a public copy.
    if (lock[key]) errors.push(`${key}: marked internal but images.lock.json has a public R2 copy (${lock[key].url}); the R2 object stays public until it is removed in Cloudflare`)
    if (mode === 'check') { if (!fs.existsSync(local)) errors.push(`${key}: missing public/img/${key}.${fmt} (run npm run images)`); continue }
    const { data } = await encode(spec)
    fs.writeFileSync(local, data)
    fs.rmSync(staged, { force: true }) // left over from when it was public
    console.log(`internal  ${key}.${fmt}  ${(data.length / 1024).toFixed(0)} KB`)
    continue
  }

  const uploaded = lock[key] && lock[key].srcSig === sig && lock[key].category === spec.category
  if (uploaded) {
    if (mode !== 'check') for (const p of [local, staged]) fs.rmSync(p, { force: true }) // not served from the site any more
    continue
  }
  const msg = `${key}: public image ${lock[key] ? 'changed' : 'new'}, not uploaded yet (npm run images:upload on Main)`
  ;(strict ? errors : warnings).push(msg)
  if (mode === 'check') { if (!strict && !fs.existsSync(staged)) errors.push(`${key}: no staged public/img/staged/${key}.${fmt} (run npm run images)`); continue }
  const { data, info } = await encode(spec)
  fs.rmSync(local, { force: true }) // a public image is never bundled under its plain name
  fs.writeFileSync(staged, data) // staged for local preview until uploaded
  due.push({ key, spec, fmt, data, width: info.width, height: info.height, sig })
  console.log(`staged    ${key}.${fmt}  ${(data.length / 1024).toFixed(0)} KB  (public, waiting for upload)`)
}

// Lock entries for images that are gone from images.json.
for (const key of Object.keys(lock)) if (!list[key]) warnings.push(`${key}: in images.lock.json but not in images.json (the R2 object is kept; remove the lock entry if it is no longer used)`)

if (mode === 'check' && remote) {
  for (const [key, e] of Object.entries(lock)) {
    if (list[key]?.visibility !== 'public') continue
    try {
      const r = await fetch(e.url, { method: 'HEAD', signal: AbortSignal.timeout(20000) })
      if (r.status !== 200) errors.push(`${key}: ${e.url} answered HTTP ${r.status}`)
      else if (+r.headers.get('content-length') !== e.bytes && r.headers.get('content-length')) errors.push(`${key}: ${e.url} size ${r.headers.get('content-length')} ≠ ${e.bytes}`)
    } catch (err) {
      errors.push(`${key}: ${e.url} unreachable (${err.message})`)
    }
  }
}

if (mode === 'upload' && !errors.length && due.length) {
  console.log(`\n${due.length} public image(s) for ${PUBLIC_BASE}${PREFIX}:`)
  for (const d of due) console.log(`  ${d.spec.category}/${d.key}  ${d.width}×${d.height}  ${(d.data.length / 1024).toFixed(0)} KB  from ${d.spec.src}`)
  if (!args.includes('--yes')) {
    console.log('\nDry run. These become publicly reachable once uploaded; check none is confidential, then run with --yes.')
  } else {
    requireMain(REPO)
    const studio = process.env.PAGE_STUDIO_DIR ?? path.resolve(REPO, '..', 'Antaurora Page Studio')
    const storeModule = path.join(studio, 'server/cloud/store.ts')
    if (!fs.existsSync(storeModule)) throw new Error(`Page Studio uploader not found at ${storeModule} (set PAGE_STUDIO_DIR)`)
    const { readR2Settings, R2Store } = await import(pathToFileURL(storeModule).href)
    const r2 = readR2Settings()
    if (!r2.settings) throw new Error(`R2: ${r2.problem}`)
    if (r2.settings.bucket !== 'antaurora-assets') throw new Error(`R2 credentials point at bucket ${r2.settings.bucket}, expected antaurora-assets`)
    const store = new R2Store(r2.settings)
    for (const d of due) {
      const objectKey = `${PREFIX}${d.spec.category}/${d.key}.${d.width}w.${sha256(d.data).slice(0, 14)}.${d.fmt}`
      const result = await store.putIfAbsent(objectKey, new Uint8Array(d.data), TYPES[d.fmt])
      if (result === 'different') { errors.push(`${d.key}: ${objectKey} already exists with other bytes; left alone`); continue }
      lock[d.key] = {
        category: d.spec.category, url: PUBLIC_BASE + objectKey, key: objectKey, sha256: sha256(d.data),
        bytes: d.data.length, width: d.width, height: d.height, srcSig: d.sig, uploaded: new Date().toISOString()
      }
      fs.rmSync(path.join(STAGED, `${d.key}.${d.fmt}`), { force: true })
      console.log(`  ${result.padEnd(7)} ${objectKey}`)
      // drop this image's "not uploaded yet" warning from the first pass
      for (let i = warnings.length - 1; i >= 0; i--) if (warnings[i].startsWith(`${d.key}: public image`)) warnings.splice(i, 1)
    }
    const sorted = Object.fromEntries(Object.keys(lock).sort().map((k) => [k, lock[k]]))
    fs.writeFileSync(LOCK, JSON.stringify(sorted, null, 2) + '\n')
  }
}

for (const w of warnings) console.log(`  warn  ${w}`)
for (const e of errors) console.log(`  ERROR ${e}`)
if (errors.length) process.exitCode = 1
