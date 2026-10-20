// Federation towns for the wiki: ids, English names, state, rank and site from the worldgen code
// (worldgen/network/road/RoadNames.TOWNS, worldgen/federal/FederalTemplate.CITY_SITES); the Chinese names come from
// the table in docs/worldgen/federal_towns_v1.md section 1 (the code keeps English only, signs are English in every
// language). check-wiki.mjs fails if a pattern stops matching or a town has no Chinese name.
import fs from 'node:fs'
import path from 'node:path'
import { REPO, JAVA } from './gamedata.mjs'

const RANKS = {
  1: { zh: '城市', en: 'City' },
  2: { zh: '镇', en: 'Town' },
  3: { zh: '村', en: 'Village' }
}

export function loadTowns() {
  const names = fs.readFileSync(path.join(JAVA, 'worldgen/network/road/RoadNames.java'), 'utf8')
  const sites = fs.readFileSync(path.join(JAVA, 'worldgen/federal/FederalTemplate.java'), 'utf8')
  const doc = fs.readFileSync(path.join(REPO, 'docs/worldgen/federal_towns_v1.md'), 'utf8')

  const en = Object.fromEntries([...names.matchAll(/Map\.entry\("(\w+)",\s*"([^"]+)"\)/g)].map((m) => [m[1], m[2]]))
  const zh = {}
  for (const m of doc.matchAll(/^\| ([A-Z][^|]*?) \| ([^|]+?) \| (\w+) \|/gm)) zh[m[3]] = m[2].replace(/（.*?）/, '').trim()

  const towns = []
  for (const m of sites.matchAll(/new CitySite\("(\w+)",\s*(S\d),\s*CityRole\.(\w+),\s*(\d),\s*(-?[\d.]+),\s*(-?[\d.]+)\)/g)) {
    const [, id, state, role, rank, x, z] = m
    towns.push({ id, name: en[id] ?? null, zh: zh[id] ?? null, state, role: role.toLowerCase(), rank: +rank, rankName: RANKS[rank] ?? { zh: rank, en: rank }, x: +x, z: +z })
  }
  if (!towns.length) throw new Error('FederalTemplate.java: no CitySite found (wiki/.vitepress/lib/world.mjs)')
  for (const t of towns) {
    if (!t.name) throw new Error(`town ${t.id}: no English name in RoadNames.TOWNS`)
    if (!t.zh) throw new Error(`town ${t.id}: no Chinese name in docs/worldgen/federal_towns_v1.md section 1`)
  }
  towns.sort((a, b) => a.state.localeCompare(b.state) || a.rank - b.rank || a.name.localeCompare(b.name))
  return towns
}
