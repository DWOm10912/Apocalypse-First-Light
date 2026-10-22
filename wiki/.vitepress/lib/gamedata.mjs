// Reads the mod's own data files so every number on the wiki is the number the game loads.
// Sources: gradle.properties, datapack JSON under src/main/resources/data/apocalypse_firstlight,
// lang files, and git (commit of the data + per-gun value history).
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

export const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
export const NS = 'apocalypse_firstlight'
export const DATA = path.join(REPO, 'src/main/resources/data', NS)
export const ASSETS = path.join(REPO, 'src/main/resources/assets', NS)
export const GUN_DIR = path.join(DATA, 'native_guns')

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'))

function git(args) {
  try {
    return execFileSync('git', args, { cwd: REPO, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 << 20 }).trim()
  } catch {
    return ''
  }
}

function parseProps(text) {
  const out = {}
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^\s*([\w.]+)\s*=\s*(.*?)\s*$/)
    if (m) out[m[1]] = m[2]
  }
  return out
}

export function loadVersion() {
  const props = parseProps(fs.readFileSync(path.join(REPO, 'gradle.properties'), 'utf8'))
  const commit = git(['rev-parse', '--short=8', 'HEAD'])
  const commitDate = git(['log', '-1', '--format=%cs'])
  // data files with uncommitted edits: the wiki then shows values newer than the last commit
  const dirtyData = git(['status', '--porcelain', '--', 'src/main/resources/data']).split('\n').filter(Boolean).length
  // wiki pages with uncommitted edits (deploys allow this; the game files must be committed)
  const dirtyWiki = git(['status', '--porcelain', '--', 'wiki']).split('\n').filter(Boolean).length
  return {
    mod: props.mod_version,
    minecraft: props.minecraft_version,
    forge: props.forge_version,
    commit,
    commitDate,
    branch: git(['rev-parse', '--abbrev-ref', 'HEAD']),
    dirtyData,
    dirtyWiki,
    built: new Date().toISOString().slice(0, 10),
    // when this build was made (= synced with the main line when it is deployed), local time with its offset
    synced: localStamp(new Date())
  }
}

function localStamp(d) {
  const pad = (n) => String(n).padStart(2, '0')
  const off = -d.getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())} UTC${sign}${Math.floor(Math.abs(off) / 60)}${off % 60 ? ':' + pad(Math.abs(off) % 60) : ''}`
}

// Main-line history for the update log: first-parent commits of HEAD, newest first, with what kind of thing changed.
export function loadUpdates(limit = 80) {
  const log = git(['log', '--first-parent', `-${limit}`, '--format=%h%x1f%cs%x1f%s'])
  if (!log) return []
  return log.split('\n').map((line) => {
    const [hash, date, subject] = line.split('\x1f')
    const files = git(['show', '--first-parent', '-m', '--name-only', '--format=', hash]).split('\n').filter(Boolean)
    const kinds = []
    if (files.some((f) => f.startsWith('wiki/'))) kinds.push('wiki')
    if (files.some((f) => f.startsWith('src/main/resources/data/'))) kinds.push('data')
    if (files.some((f) => f.startsWith('src/main/java/'))) kinds.push('code')
    if (files.some((f) => f.startsWith('docs/'))) kinds.push('docs')
    return { hash, date, subject, kinds, merge: /^合并|^Merge/.test(subject) }
  })
}

let langCache
export function lang() {
  if (!langCache) {
    langCache = {
      zh: readJson(path.join(ASSETS, 'lang/zh_cn.json')),
      en: readJson(path.join(ASSETS, 'lang/en_us.json'))
    }
  }
  return langCache
}

export function itemName(id) {
  const [ns, p] = id.includes(':') ? id.split(':') : [NS, id]
  const key = `item.${ns}.${p}`
  const block = `block.${ns}.${p}`
  const L = lang()
  return {
    zh: L.zh[key] ?? L.zh[block] ?? p,
    en: L.en[key] ?? L.en[block] ?? p
  }
}

function massTable() {
  const out = {}
  for (const f of fs.readdirSync(path.join(DATA, 'item_mass'))) {
    const j = readJson(path.join(DATA, 'item_mass', f))
    Object.assign(out, j.items, j.native_guns)
  }
  return out
}

function sizeTable() {
  const j = readJson(path.join(DATA, 'item_size/core_v1.json'))
  return { classes: j.weapon_classes ?? {}, items: j.items ?? {} }
}

const short = (id) => (id.includes(':') ? id.split(':')[1] : id)

// Values that live in Java rather than data files. Read from the source so they stay in step;
// check-wiki.mjs fails if a pattern stops matching.
export const JAVA = path.join(REPO, 'src/main/java/com/antaurora/apofirstlight')
export function javaValues() {
  const read = (p) => fs.readFileSync(path.join(JAVA, p), 'utf8')
  const sup = read('weapon/NativeSuppressorItem.java').match(/DEFAULT_NOISE_MULTIPLIER\s*=\s*([\d.]+)/)
  const magItem = read('weapon/NativeMagazineItem.java').match(/NativeMagazineItem\(\)\{this\("(\w+)",(\d+)/)
  const items = read('registry/AflItems.java')
  const mags = {}
  for (const m of items.matchAll(/register\("(\w+)",\s*(?:\(\)\s*->\s*new\s+[\w.]*NativeMagazineItem\((?:"(\w+)",(\d+))?|[\w.]*NativeMagazineItem::new)/g)) {
    if (m[2]) mags[m[1]] = { gun: m[2], rounds: +m[3] }
    else if (magItem) mags[m[1]] = { gun: magItem[1], rounds: +magItem[2] }
  }
  const profiles = {}
  for (const m of read('weapon/NativeAccuracyProfile.java').matchAll(/(\w+)\s*=\s*new NativeAccuracyProfile\(([^)]+)\)/g)) {
    const [crouchStill, crouchMoving, walking, sprinting, airborne] = m[2].split(',').map(Number)
    profiles[m[1].toLowerCase()] = { crouchStill, crouchMoving, walking, sprinting, airborne }
  }
  return { suppressorNoise: sup ? +sup[1] : null, magazines: mags, accuracyProfiles: profiles }
}

function attachmentData(id) {
  const p = path.join(DATA, 'native_attachments', `${id}.json`)
  return fs.existsSync(p) ? readJson(p) : {}
}

function attachmentsOf(raw, java) {
  const out = []
  for (const [slot, key] of [['sight', 'sight_slot'], ['muzzle', 'muzzle_slot'], ['magazine', 'magazine_slot']]) {
    for (const full of raw[key]?.accepts ?? []) {
      const id = short(full)
      const data = attachmentData(id)
      const a = { slot, id, name: itemName(full) }
      if (slot === 'muzzle') a.noiseMultiplier = data.noise_multiplier ?? java.suppressorNoise
      if (slot === 'magazine') a.rounds = java.magazines[id]?.rounds ?? null
      if (data.reload_overrides?.empty?.seconds) a.emptyReload = data.reload_overrides.empty.seconds
      out.push(a)
    }
  }
  return out
}

function modeStats(raw, mode) {
  const o = raw.fire?.mode_overrides?.[mode] ?? {}
  const interval = o.interval_ticks ?? raw.fire.interval_ticks
  return {
    mode,
    intervalTicks: interval,
    rpm: Math.round(1200 / interval),
    bloom: o.bloom ?? raw.fire?.bloom ?? null
  }
}

export function loadGuns() {
  const mass = massTable()
  const size = sizeTable()
  const java = javaValues()
  const guns = []
  for (const f of fs.readdirSync(GUN_DIR).filter((f) => f.endsWith('.json')).sort()) {
    const id = f.replace(/\.json$/, '')
    const raw = readJson(path.join(GUN_DIR, f))
    const modes = raw.fire.modes ?? [raw.fire.mode ?? 'semi']
    const m = mass[`${NS}:${id}`] ?? {}
    const ammoMass = mass[raw.ammo]?.unit_mass_kg ?? null
    const d = raw.damage
    guns.push({
      id,
      name: itemName(`${NS}:${id}`),
      weaponClass: raw.weapon_class,
      actionType: raw.action_type ?? null,
      ammo: { id: short(raw.ammo), name: itemName(raw.ammo), massKg: ammoMass },
      capacity: raw.magazine_capacity,
      modes: modes.map((mode) => modeStats(raw, mode)),
      defaultMode: raw.fire.default_mode ?? modes[0],
      pellets: raw.fire.pellets_per_shot ?? 1,
      damage: {
        base: d.base,
        headshot: d.headshot_multiplier ?? null,
        falloffStart: d.falloff_start,
        effectiveRange: d.effective_range,
        maxRange: d.max_range,
        minMultiplier: d.min_damage_multiplier
      },
      spread: { hip: raw.accuracy?.base_spread_degrees ?? null, ads: raw.accuracy?.ads_spread_degrees ?? null },
      accuracyProfile: raw.accuracy?.profile ?? 'default',
      reload: { tactical: raw.reload?.tactical_seconds ?? null, empty: raw.reload?.empty_seconds ?? null },
      noise: { radius: raw.noise?.radius ?? null, earDb: raw.noise?.ear_db ?? null },
      ads: { time: raw.ads?.time_seconds ?? null, fov: raw.ads?.fov_multiplier ?? null },
      massKg: m.receiver_mass_kg != null ? +(m.receiver_mass_kg + (m.default_magazine_empty_mass_kg ?? 0)).toFixed(3) : (m.unit_mass_kg ?? null),
      massEstimated: !!m.estimated,
      grid: size.items[`${NS}:${id}`] ?? size.classes[raw.weapon_class] ?? null,
      attachments: attachmentsOf(raw, java),
      source: `src/main/resources/data/${NS}/native_guns/${f}`
    })
  }
  return guns
}

// ---- value history from git -------------------------------------------------

// Fields players care about; anything else that changed is counted as a handling tweak.
export const TRACKED = {
  magazine_capacity: { zh: '弹匣容量', en: 'Magazine', unit: { zh: '发', en: 'rds' } },
  'fire.interval_ticks': { zh: '射击间隔', en: 'Fire interval', unit: { zh: 'tick', en: 'ticks' } },
  'fire.mode_overrides.auto.interval_ticks': { zh: '全自动射击间隔', en: 'Auto fire interval', unit: { zh: 'tick', en: 'ticks' } },
  'fire.pellets_per_shot': { zh: '每发弹丸', en: 'Pellets per shot', unit: { zh: '', en: '' } },
  'damage.base': { zh: '伤害', en: 'Damage', unit: { zh: '', en: '' } },
  'damage.headshot_multiplier': { zh: '爆头倍率', en: 'Headshot multiplier', unit: { zh: '×', en: '×' } },
  'damage.falloff_start': { zh: '衰减起点', en: 'Falloff start', unit: { zh: '格', en: 'blocks' } },
  'damage.effective_range': { zh: '有效射程', en: 'Effective range', unit: { zh: '格', en: 'blocks' } },
  'damage.max_range': { zh: '最大射程', en: 'Max range', unit: { zh: '格', en: 'blocks' } },
  'damage.min_damage_multiplier': { zh: '最低伤害倍率', en: 'Min damage multiplier', unit: { zh: '×', en: '×' } },
  'accuracy.base_spread_degrees': { zh: '腰射散布', en: 'Hip spread', unit: { zh: '°', en: '°' } },
  'accuracy.ads_spread_degrees': { zh: '瞄准散布', en: 'ADS spread', unit: { zh: '°', en: '°' } },
  'reload.tactical_seconds': { zh: '战术换弹', en: 'Tactical reload', unit: { zh: '秒', en: 's' } },
  'reload.empty_seconds': { zh: '空仓换弹', en: 'Empty reload', unit: { zh: '秒', en: 's' } },
  'noise.radius': { zh: '枪声半径', en: 'Noise radius', unit: { zh: '格', en: 'blocks' } },
  'ads.time_seconds': { zh: '开镜时间', en: 'ADS time', unit: { zh: '秒', en: 's' } }
}

function flatten(obj, prefix = '', out = {}) {
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
    out[prefix] = JSON.stringify(obj)
    return out
  }
  for (const [k, v] of Object.entries(obj)) flatten(v, prefix ? `${prefix}.${k}` : k, out)
  return out
}

const versionAt = new Map()
function modVersionAt(hash) {
  if (!versionAt.has(hash)) {
    versionAt.set(hash, parseProps(git(['show', `${hash}:gradle.properties`])).mod_version ?? '')
  }
  return versionAt.get(hash)
}

// One entry per commit that changed a tracked value (oldest values compared to the commit before).
export function gunHistory(id) {
  const rel = `src/main/resources/data/${NS}/native_guns/${id}.json`
  const log = git(['log', '--follow', '--format=%h|%cs', '--', rel])
  if (!log) return []
  const commits = log.split('\n').map((l) => { const [hash, date] = l.split('|'); return { hash, date } }).reverse()
  const entries = []
  let prev = null
  let prevPath = rel
  for (const c of commits) {
    // --follow may cross renames; read the file at whatever path it had in that commit
    const names = git(['show', '--name-only', '--format=', c.hash, '--', `src/main/resources/data/${NS}`])
      .split('\n').filter((n) => n.endsWith(`/${id}.json`) || n === rel)
    const p = names[0] ?? prevPath
    const text = git(['show', `${c.hash}:${p}`])
    prevPath = p
    if (!text) continue
    let flat
    try { flat = flatten(JSON.parse(text)) } catch { continue }
    if (!prev) {
      entries.push({ ...c, version: modVersionAt(c.hash), added: true, changes: [], other: 0 })
    } else {
      const changes = []
      let other = 0
      for (const key of new Set([...Object.keys(prev), ...Object.keys(flat)])) {
        if (prev[key] === flat[key]) continue
        if (TRACKED[key]) changes.push({ key, label: TRACKED[key], from: prev[key] && JSON.parse(prev[key]), to: flat[key] && JSON.parse(flat[key]) })
        else other++
      }
      if (changes.length || other) entries.push({ ...c, version: modVersionAt(c.hash), added: false, changes, other })
    }
    prev = flat
  }
  return entries.reverse()
}

// Every AFL biome with its names and the temperature system's mean / daily swing / water temperature.
export function loadBiomes() {
  const temps = readJson(path.join(DATA, 'temperature/temperature_v1.json')).biomes
  const L = lang()
  return fs.readdirSync(path.join(DATA, 'worldgen/biome')).filter((f) => f.endsWith('.json')).map((f) => {
    const id = f.slice(0, -5), key = `biome.${NS}.${id}`
    const t = temps[`${NS}:${id}`] ?? {}
    return { id, zh: L.zh[key] ?? id, en: L.en[key] ?? id, mean: t.mean ?? null, amp: t.amp ?? null, water: t.water ?? null }
  }).sort((a, b) => (b.mean ?? -99) - (a.mean ?? -99))
}

export function loadAll() {
  const guns = loadGuns()
  for (const g of guns) g.history = gunHistory(g.id)
  return { version: loadVersion(), guns, accuracyProfiles: javaValues().accuracyProfiles, biomes: loadBiomes() }
}
