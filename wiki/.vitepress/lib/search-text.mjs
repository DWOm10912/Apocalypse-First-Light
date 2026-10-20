// Local search only indexes the markdown, not what the data components render. Before a page is indexed, each data
// component tag is swapped for plain text with the names it shows (traits, backgrounds, statuses, growth items,
// towns), so searching "运动员" or "Bellhaven" finds the right page and section. The page itself is unchanged.
import { loadSurvivor } from './survivor.mjs'
import { loadTowns } from './world.mjs'

let cache = null
const data = () => (cache ??= { sv: loadSurvivor(), towns: loadTowns() })

function lines(tag, attrs, loc) {
  const { sv, towns } = data()
  const n = (x) => x?.[loc] ?? ''
  switch (tag) {
    case 'TraitTable':
      return sv.traits.map((t) => `${n(t.name)}：${t.effects.map((e) => n(e.name)).concat(t.growthStart.map((g) => n(g.name)), t.growthCap.map((g) => n(g.name))).join('、')}`)
    case 'BackgroundTable':
      return sv.backgrounds.map((b) => `${n(b.name)}：${n(b.desc)} ${b.traitNames.map(n).join('、')}`)
    case 'StatusTable':
      return sv.statuses.filter((s) => s.kind === attrs.kind).flatMap((s) => s.rows.map((r) => `${n(r.name)}：${n(r.effect)}`))
    case 'LevelTable': {
      const s = [...sv.body, ...sv.growth].find((x) => x.id === attrs.id)
      return s ? [`${n(s.name)}：${n(s.how)} ${s.levels.map((l) => n(l.name)).join('、')} ${s.perks?.map((p) => n(p.name)).join('、') ?? ''}`] : []
    }
    case 'GunLevelTable':
      return [loc === 'zh' ? '瞄准晃动 后坐力回复 换弹档' : 'aiming sway recoil recovery reload tier']
    case 'TownTable':
      return towns.map((t) => (loc === 'zh' ? `${t.name} ${t.zh} ${t.state}` : `${t.name} ${t.state}`))
    default:
      return null
  }
}

/** The page's markdown with data component tags replaced by searchable text. */
export function searchSource(src, relativePath) {
  const loc = relativePath.startsWith('en/') ? 'en' : 'zh'
  return src.replace(/<(TraitTable|BackgroundTable|StatusTable|LevelTable|GunLevelTable|TownTable)\b([^>]*)\/>/g, (all, tag, rest) => {
    const attrs = Object.fromEntries([...rest.matchAll(/(\w+)="([^"]*)"/g)].map((m) => [m[1], m[2]]))
    const out = lines(tag, attrs, loc)
    return out ? '\n\n' + out.map((l) => `- ${l}`).join('\n') + '\n\n' : all
  })
}
