// Survivor data for the wiki: backgrounds, body stats, traits, growth and the HUD's status badges.
// Everything is read from the game's datapack JSON and lang files; the only hand-written parts are the labels
// for experience actions (keys in the data) and which statuses are good or bad (StatusBadgeHud.Kind in Java).
import fs from 'node:fs'
import path from 'node:path'
import { DATA, NS, JAVA, lang } from './gamedata.mjs'

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'))
const dir = (d) => fs.readdirSync(path.join(DATA, d)).filter((f) => f.endsWith('.json')).map((f) => ({ id: f.slice(0, -5), ...readJson(path.join(DATA, d, f)) }))
const S = `survivor.${NS}.`

function name(key, fallback) {
  const L = lang()
  return { zh: L.zh[key] ?? fallback, en: L.en[key] ?? fallback }
}
const opt = (key) => {
  const L = lang()
  return L.zh[key] || L.en[key] ? { zh: L.zh[key] ?? '', en: L.en[key] ?? '' } : null
}

// Attribute ids -> display names from the lang file (attribute.name.<ns>.<path>); vanilla max health has no AFL key.
export function attributeName(id) {
  const [ns, p] = id.split(':')
  if (id === 'minecraft:generic.max_health') return { zh: '生命上限', en: 'Max health' }
  return name(`attribute.name.${ns}.${p}`, p)
}

// Units of the attributes that add a physical amount (the rest are plain points or multipliers).
const UNITS = {
  'apocalypse_firstlight:carry_capacity': 'kg',
  'apocalypse_firstlight:cold_threshold': '°C',
  'apocalypse_firstlight:heat_threshold': '°C'
}

// Labels for the experience actions named in survivor_body / survivor_growth "training.xp".
export const XP_ACTIONS = {
  loaded_walk_10m: { zh: '背着重物走 10 米', en: 'Walk 10 m loaded' },
  chop: { zh: '砍一下树', en: 'Chop' },
  dig: { zh: '挖一下石头', en: 'Dig' },
  recoil_pull: { zh: '拉一次启动绳', en: 'Pull a recoil starter' },
  sprint_10m: { zh: '疾跑 10 米', en: 'Sprint 10 m' },
  swim_10m: { zh: '游泳 10 米', en: 'Swim 10 m' },
  recover: { zh: '累了以后缓过来', en: 'Recover from being winded' },
  hit: { zh: '打中目标', en: 'Hit a target' },
  kill: { zh: '打死目标', en: 'Kill a target' },
  reload: { zh: '换弹', en: 'Reload' },
  exposed_minute: { zh: '冷 / 热着待一分钟', en: 'A minute out in the cold / heat' }
}

function training(t = {}) {
  const toNext = Object.entries(t.to_next ?? {}).map(([lv, xp]) => ({ level: +lv, xp })).sort((a, b) => a.level - b.level)
  const xp = Object.entries(t.xp ?? {}).map(([k, v]) => ({ id: k, xp: v, label: XP_ACTIONS[k] ?? { zh: k, en: k } }))
  return { toNext, total: toNext.reduce((a, b) => a + b.xp, 0), xp }
}

function levels(l = {}) {
  return Object.entries(l).map(([attr, v]) => ({ attr, name: attributeName(attr), unit: UNITS[attr] ?? '', op: v.op, values: v.values }))
}

export function loadSurvivor() {
  const backgrounds = dir('survivor_backgrounds').sort((a, b) => a.order - b.order).map((b) => ({
    id: b.id, order: b.order, budget: b.budget, traits: b.traits ?? [], successor: !!b.successor_pool,
    name: name(`${S}background.${b.id}`, b.id), desc: opt(`${S}background.${b.id}.desc`)
  }))
  const body = dir('survivor_body').sort((a, b) => a.order - b.order).map((b) => ({
    id: b.id, default: b.default, startMin: b.start_min, startMax: b.start_max, max: b.max,
    name: name(`${S}body.${b.id}`, b.id), how: opt(`${S}growth_page.how.${b.id}`),
    levels: levels(b.levels), training: training(b.training)
  }))
  const growth = dir('survivor_growth').sort((a, b) => a.order - b.order).map((g) => ({
    id: g.id, group: g.group, groupName: name(`${S}growth_page.group.${g.group}`, g.group), default: g.default, max: g.max,
    name: name(`${S}growth.${g.id}`, g.id), how: opt(`${S}growth_page.how.${g.id}`),
    levels: levels(g.levels), training: training(g.training),
    perks: Object.entries(g.perks ?? {}).map(([lv, id]) => ({ level: +lv, id, name: name(`${S}perk.${id}`, id), desc: opt(`${S}perk.${id}.desc`) }))
  }))
  const growthNames = Object.fromEntries([...growth, ...body].map((g) => [g.id, g.name]))
  const traits = dir('survivor_traits').map((t) => ({
    id: t.id, group: t.group, groupName: name(`${S}trait_group.${t.group}`, t.group), cost: t.cost,
    name: name(`${S}trait.${t.id}`, t.id),
    effects: (t.effects ?? []).map((e) => ({ attr: e.attribute, name: attributeName(e.attribute), unit: UNITS[e.attribute] ?? '', op: e.op, value: e.value })),
    bodyBonus: Object.entries(t.body_bonus ?? {}).map(([id, v]) => ({ id, name: growthNames[id] ?? { zh: id, en: id }, value: v })),
    growthStart: Object.entries(t.growth_start ?? {}).map(([id, v]) => ({ id, name: growthNames[id] ?? { zh: id, en: id }, value: v })),
    growthCap: Object.entries(t.growth_cap ?? {}).map(([id, v]) => ({ id, name: growthNames[id] ?? { zh: id, en: id }, value: v })),
    exclusive: t.exclusive ?? [],
    backgroundOnly: !!t.background_only
  }))
  const groupOrder = ['body', 'metabolism', 'climate', 'noise', 'hands', 'growth']
  traits.sort((a, b) => groupOrder.indexOf(a.group) - groupOrder.indexOf(b.group) || b.cost - a.cost || a.id.localeCompare(b.id))
  const traitNames = Object.fromEntries(traits.map((t) => [t.id, t.name]))
  for (const t of traits) t.exclusiveNames = t.exclusive.map((id) => traitNames[id] ?? { zh: id, en: id })
  for (const b of backgrounds) b.traitNames = b.traits.map((id) => traitNames[id] ?? { zh: id, en: id })
  return { backgrounds, body, growth, traits, statuses: loadStatuses(), gunLevels: gunLevelCurves() }
}

// Gun levels change aiming sway and recoil recovery by linear formulas in survivor/SurvivorGrowth.java
// (adsSway / recoilRecovery); read the two coefficients so the page shows the code's numbers.
function gunLevelCurves() {
  const src = fs.readFileSync(path.join(JAVA, 'survivor/SurvivorGrowth.java'), 'utf8')
  const curve = (fn) => {
    const m = src.match(new RegExp(`float ${fn}\\(int level\\)\\s*\\{\\s*return\\s+([\\d.]+)F\\s*([+-])\\s*([\\d.]+)F\\s*\\*`))
    if (!m) throw new Error(`SurvivorGrowth.${fn}: formula not found (wiki/.vitepress/lib/survivor.mjs)`)
    const base = +m[1], step = (m[2] === '-' ? -1 : 1) * +m[3]
    return Array.from({ length: 11 }, (_, lv) => +(base + step * lv).toFixed(3))
  }
  return { adsSway: curve('adsSway'), recoilRecovery: curve('recoilRecovery') }
}

// ---- HUD status badges ------------------------------------------------------------------------------------------
// Which statuses exist and whether they help or hurt comes from client/StatusBadgeHud.java (Kind GOOD / BAD / COLD /
// DANGER); names and effect lines come from the lang file (hud.<ns>.status.<id>[.<stage>][.effect]).
const STATUSES = [
  { id: 'cold', stages: 3, kind: 'bad', danger: 3, source: 'temperature' },
  { id: 'hot', stages: 3, kind: 'bad', danger: 3, source: 'temperature' },
  { id: 'wet', stages: 2, kind: 'bad', sharedEffect: true, source: 'temperature' },
  { id: 'thirst', stages: 3, kind: 'bad', danger: 2, source: 'thirst' },
  { id: 'hunger', stages: 2, kind: 'bad', danger: 2, source: 'food' },
  { id: 'load', tiers: ['heavy', 'hauling', 'extreme'], kind: 'bad', danger: 3, source: 'weight' },
  { id: 'radiation', stages: 4, kind: 'bad', danger: 4, roman: true, source: 'radiation' },
  { id: 'gastro', kind: 'bad', timed: true, source: 'effect' },
  { id: 'gasoline', kind: 'bad', timed: true, source: 'effect' },
  { id: 'diesel', kind: 'bad', timed: true, source: 'effect' },
  { id: 'fed', kind: 'good', source: 'food' },
  { id: 'warming', kind: 'good', source: 'temperature' }
]

function loadStatuses() {
  const L = lang()
  const H = `hud.${NS}.status.`
  const pick = (k) => ({ zh: L.zh[H + k], en: L.en[H + k] })
  const out = []
  for (const s of STATUSES) {
    const rows = []
    if (s.tiers) {
      // the slowdown grows with the load ratio and sprinting stops above a ratio, not at a tier: shown as a range
      // (HUD text is "N% slower" with the live N; written out here because N is a curve, see weight/EncumbranceState)
      const LOAD = { zh: '走路变慢，越重越慢；负重超过一定比例后不能疾跑', en: 'Slower walking, more so the heavier the load; no sprinting past a set load ratio' }
      s.tiers.forEach((tier, i) => rows.push({ stage: i + 1, name: pick(`load.${tier}`), effect: { ...LOAD }, danger: i + 1 === s.danger }))
    } else if (s.stages) {
      for (let n = 1; n <= s.stages; n++) {
        const nm = s.roman ? pick(s.id) : pick(`${s.id}.${n}`)
        rows.push({ stage: n, name: nm, roman: s.roman ? ['I', 'II', 'III', 'IV'][n - 1] : null, effect: pick(s.sharedEffect ? `${s.id}.effect` : `${s.id}.${n}.effect`), danger: s.danger != null && n >= s.danger })
      }
    } else {
      rows.push({ stage: 1, name: pick(s.id), effect: pick(`${s.id}.effect`), danger: false })
    }
    if (s.id === 'thirst') {
      const t = readJson(path.join(DATA, 'thirst/thirst_v1.json'))
      rows[0].args = [Math.round(t.stamina_regen_thirsty * 100)]
      rows[1].args = [Math.round(t.stamina_regen_dehydrated * 100)]
      rows[2].args = [t.damage_interval_seconds]
    }
    for (const r of rows) for (const loc of ['zh', 'en']) {
      let i = 0
      r.effect[loc] = r.effect[loc]?.replace(/%(\d\$)?s/g, () => String((r.args ?? [])[i++] ?? '…')).replace(/%%/g, '%')
      if (s.roman) r.name[loc] = r.name[loc]?.replace(/%s/, r.roman)
    }
    for (const r of rows) if (!r.name.zh || !r.name.en || !r.effect.zh || !r.effect.en) throw new Error(`status ${s.id} stage ${r.stage}: lang key missing`)
    out.push({ id: s.id, kind: s.kind, timed: !!s.timed, source: s.source, rows })
  }
  return out
}
