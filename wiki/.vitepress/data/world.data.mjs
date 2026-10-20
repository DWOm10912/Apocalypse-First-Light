// VitePress build-time data: the Federation's towns (see ../lib/world.mjs).
import { loadTowns } from '../lib/world.mjs'
import { REPO } from '../lib/gamedata.mjs'

const abs = (p) => `${REPO.replace(/\\/g, '/')}/${p}`
const J = 'src/main/java/com/antaurora/apofirstlight/worldgen'

export default {
  watch: [abs(`${J}/network/road/RoadNames.java`), abs(`${J}/federal/FederalTemplate.java`), abs('docs/worldgen/federal_towns_v1.md')],
  load() {
    return { towns: loadTowns() }
  }
}
