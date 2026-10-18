// Checks that the wiki still matches the game before a build: every <V> value resolves, every gun has
// prose in both languages and complete data, every page exists in both languages, and the images are built.
// Usage: node scripts/check-wiki.mjs        (npm run check; npm run build runs it first)
// Exit code 1 on any error. Warnings (screenshots still to take) don't fail.
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { loadGuns, javaValues, loadVersion, loadBiomes } from '../.vitepress/lib/gamedata.mjs'
import { collectRefs, resolveRefs } from '../.vitepress/lib/values.mjs'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const errors = []
const warnings = []

function pages(dir, base = dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (['node_modules', '.vitepress', 'public', 'content', 'scripts'].includes(e.name)) continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) pages(p, base, out)
    else if (e.name.endsWith('.md')) out.push(path.relative(base, p).replace(/\\/g, '/'))
  }
  return out
}

// 1. Game data loads (JSON parse errors surface here).
let guns = []
try {
  loadVersion()
  guns = loadGuns()
  if (!guns.length) errors.push('no guns found in native_guns')
  if (!loadBiomes().length) errors.push('no biomes found in worldgen/biome')
} catch (e) {
  errors.push(`game data failed to load: ${e.message}`)
}

// 2. Every <V d=... /> and <V j=... /> resolves to a value.
const { values, missing } = resolveRefs(collectRefs())
for (const m of missing) errors.push(`value not found: ${m.ref} (in ${[].concat(m.files).join(", ")})`)

// 3. Gun data is complete enough for the gun pages.
const profiles = javaValues().accuracyProfiles
for (const g of guns) {
  for (const loc of ['zh', 'en']) {
    if (!fs.existsSync(path.join(WIKI, 'content/guns', `${g.id}.${loc}.md`))) errors.push(`gun ${g.id}: missing content/guns/${g.id}.${loc}.md`)
  }
  if (!g.name.zh || g.name.zh === g.id || !g.name.en || g.name.en === g.id) errors.push(`gun ${g.id}: no lang name`)
  if (!profiles[g.accuracyProfile]) errors.push(`gun ${g.id}: accuracy profile "${g.accuracyProfile}" not found in NativeAccuracyProfile.java`)
  for (const a of g.attachments) {
    if (a.slot === 'magazine' && a.rounds == null) errors.push(`gun ${g.id}: magazine ${a.id} has no round count (AflItems.java)`)
    if (a.slot === 'muzzle' && a.noiseMultiplier == null) errors.push(`gun ${g.id}: muzzle ${a.id} has no noise multiplier`)
  }
  if (g.massKg == null) warnings.push(`gun ${g.id}: no mass in item_mass`)
}

// 4. Every page exists in both languages.
const zh = pages(WIKI).filter((p) => !p.startsWith('en/'))
const en = pages(path.join(WIKI, 'en'))
for (const p of zh) if (!en.includes(p)) errors.push(`page ${p} has no English version (en/${p})`)
for (const p of en) if (!zh.includes(p)) errors.push(`page en/${p} has no Chinese version`)

// 5. Images: built outputs exist; Shot keys not in images.json are screenshots still to take.
try {
  execFileSync(process.execPath, [path.join(WIKI, 'scripts/sync-images.mjs'), '--check'], { stdio: 'pipe' })
} catch (e) {
  errors.push(`images: ${String(e.stdout).trim() || 'sync-images --check failed'}`)
}
const images = JSON.parse(fs.readFileSync(path.join(WIKI, 'images.json'), 'utf8'))
const shotKeys = new Set()
for (const p of [...zh, ...en.map((p) => `en/${p}`)]) {
  const text = fs.readFileSync(path.join(WIKI, p), 'utf8')
  for (const m of text.matchAll(/<Shot\s+k="([^"]+)"/g)) shotKeys.add(m[1])
}
for (const k of shotKeys) if (!images[k]) warnings.push(`screenshot to take: "${k}" (Shot placeholder shown)`)

console.log(`wiki check: ${guns.length} guns, ${Object.keys(values).length} values, ${zh.length} pages × 2 languages`)
for (const w of warnings) console.log(`  warn  ${w}`)
for (const e of errors) console.log(`  ERROR ${e}`)
if (errors.length) {
  console.log(`${errors.length} error(s)`)
  process.exitCode = 1
} else {
  console.log('ok')
}
