// VitePress build-time data: the game's own values (see ../lib/gamedata.mjs).
// The dev server reloads it when any of the watched files change.
import { loadAll, REPO } from '../lib/gamedata.mjs'

const abs = (p) => `${REPO.replace(/\\/g, '/')}/${p}`

export default {
  watch: [
    abs('gradle.properties'),
    abs('src/main/resources/data/apocalypse_firstlight/native_guns/*.json'),
    abs('src/main/resources/data/apocalypse_firstlight/item_mass/*.json'),
    abs('src/main/resources/data/apocalypse_firstlight/item_size/*.json'),
    abs('src/main/resources/assets/apocalypse_firstlight/lang/*.json'),
    abs('src/main/resources/data/apocalypse_firstlight/temperature/*.json'),
    abs('src/main/resources/data/apocalypse_firstlight/worldgen/biome/*.json')
  ],
  load() {
    return loadAll()
  }
}
