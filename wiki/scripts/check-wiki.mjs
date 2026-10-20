// Checks that the wiki still matches the game before a build: every <V> value resolves, every gun has
// prose in both languages and complete data, every page exists in both languages, the images are built and every item icon exists.
// Usage: node scripts/check-wiki.mjs            (npm run check; npm run build runs it first)
//        node scripts/check-wiki.mjs --strict   (deploy: public images must be uploaded, R2 URLs must answer)
// Exit code 1 on any error. Warnings (screenshots still to take) don't fail.
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { loadGuns, javaValues, loadVersion, loadBiomes } from '../.vitepress/lib/gamedata.mjs'
import { collectRefs, resolveRefs } from '../.vitepress/lib/values.mjs'
import { loadSurvivor } from '../.vitepress/lib/survivor.mjs'
import { loadTowns } from '../.vitepress/lib/world.mjs'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const strict = process.argv.includes('--strict')
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

// 1b. Survivor data (backgrounds, body, growth, traits, statuses, gun level curves) loads and every entry has a lang name;
// every status stage has its icon; every <LevelTable id="..."> names an entry that exists.
let survivorIds = null
try {
  const sv = loadSurvivor()
  for (const e of [...sv.backgrounds, ...sv.body, ...sv.growth, ...sv.traits]) {
    if (!e.name.zh || e.name.zh === e.id || !e.name.en || e.name.en === e.id) errors.push(`survivor ${e.id}: no lang name`)
  }
  for (const e of sv.growth) for (const pk of e.perks) if (pk.name.zh === pk.id) errors.push(`survivor perk ${pk.id}: no lang name`)
  // the HUD status icons on the statuses page, drawn by `gradlew wikiStatusIcons` (test client/StatusBadgeIcons)
  for (const s of sv.statuses) for (const r of s.rows)
    if (!fs.existsSync(path.join(WIKI, 'public/img/status', `${s.id}_${r.stage}.png`))) errors.push(`status ${s.id} stage ${r.stage}: public/img/status/${s.id}_${r.stage}.png not drawn (gradlew wikiStatusIcons)`)
  survivorIds = new Set([...sv.body, ...sv.growth].map((e) => e.id))
} catch (e) {
  errors.push(`survivor data failed to load: ${e.message}`)
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

// 1c. Federation towns (worldgen code + Chinese names from docs/worldgen/federal_towns_v1.md).
try {
  if (loadTowns().length !== 16) warnings.push('towns: expected 16 Federation towns')
} catch (e) {
  errors.push(`towns failed to load: ${e.message}`)
}

if (survivorIds) {
  for (const p of pages(WIKI)) {
    for (const m of fs.readFileSync(path.join(WIKI, p), 'utf8').matchAll(/<LevelTable id="([^"]+)"/g)) {
      if (!survivorIds.has(m[1])) errors.push(`${p}: <LevelTable id="${m[1]}"> is not a survivor body or growth entry`)
    }
  }
}

// 4. Every page exists in both languages.
const zh = pages(WIKI).filter((p) => !p.startsWith('en/'))
const en = pages(path.join(WIKI, 'en'))
for (const p of zh) if (!en.includes(p)) errors.push(`page ${p} has no English version (en/${p})`)
for (const p of en) if (!zh.includes(p)) errors.push(`page en/${p} has no Chinese version`)

// 5. Images: built outputs exist; Shot keys not in images.json are screenshots still to take.
try {
  const out = execFileSync(process.execPath, [path.join(WIKI, 'scripts/images.mjs'), '--check', ...(strict ? ['--strict', '--remote'] : [])], { stdio: 'pipe', encoding: 'utf8' })
  for (const l of out.split('\n')) if (l.trim().startsWith('warn')) warnings.push(`images: ${l.trim().slice(4).trim()}`)
} catch (e) {
  errors.push(`images:\n${String(e.stdout).trim() || 'images.mjs --check failed'}`)
}
const images = JSON.parse(fs.readFileSync(path.join(WIKI, 'images.json'), 'utf8'))
const shotKeys = new Set()
for (const p of [...zh, ...en.map((p) => `en/${p}`)]) {
  const text = fs.readFileSync(path.join(WIKI, p), 'utf8')
  for (const m of text.matchAll(/<Shot\s+k="([^"]+)"/g)) shotKeys.add(m[1])
}
for (const k of shotKeys) if (!images[k]) warnings.push(`screenshot to take: "${k}" (Shot placeholder shown)`)

// 5b. Item pictures: every <ItemIcon>/<ModelShot> id has a built icon; renders older than the game model warn.
try {
  const out = execFileSync(process.execPath, [path.join(WIKI, 'scripts/icons.mjs'), '--check', ...(strict ? ['--strict'] : [])], { stdio: 'pipe', encoding: 'utf8' })
  for (const l of out.split('\n')) if (l.trim().startsWith('warn')) warnings.push(`icons: ${l.trim().slice(4).trim()}`)
} catch (e) {
  errors.push(`icons:\n${String(e.stdout).trim() || 'icons.mjs --check failed'}`)
}

// 6. Status tags use the four steps (+ creative); page status in front matter, if given, is one of them.
const STEPS = ['verified', 'integrated', 'test', 'planned']
for (const p of [...zh, ...en.map((p) => `en/${p}`)]) {
  const text = fs.readFileSync(path.join(WIKI, p), 'utf8')
  for (const m of text.matchAll(/<StatusTag\b([^>]*)\/>/g)) {
    const s = m[1].match(/\bs="([^"]*)"/)?.[1]
    if (![...STEPS, 'creative'].includes(s)) errors.push(`${p}: <StatusTag s="${s}"> is not one of ${STEPS.join(', ')}, creative`)
    const d = m[1].match(/\bd="([^"]*)"/)?.[1]
    if (s === 'verified' && !/^\d{4}-\d{2}-\d{2}$/.test(d ?? '') && !p.includes('about/')) errors.push(`${p}: verified tag needs d="YYYY-MM-DD" (date of the in-game check)`)
    if (d && s !== 'verified') errors.push(`${p}: only a verified tag carries a date`)
  }
  const fm = text.match(/^---\r?\n([\s\S]*?)\r?\n---/)?.[1] ?? ''
  const st = fm.match(/^status:\s*(\S+)/m)?.[1]
  if (st && !STEPS.includes(st) && st !== 'mixed') errors.push(`${p}: front matter status "${st}" is not one of ${STEPS.join(', ')}, mixed`)
}

console.log(`wiki check: ${guns.length} guns, ${Object.keys(values).length} values, ${zh.length} pages × 2 languages`)
for (const w of warnings) console.log(`  warn  ${w}`)
for (const e of errors) console.log(`  ERROR ${e}`)
if (errors.length) {
  console.log(`${errors.length} error(s)`)
  process.exitCode = 1
} else {
  console.log('ok')
}
