// VitePress build-time data: backgrounds, body stats, traits, growth and status badges (see ../lib/survivor.mjs).
import { loadSurvivor } from '../lib/survivor.mjs'
import { REPO } from '../lib/gamedata.mjs'

const abs = (p) => `${REPO.replace(/\\/g, '/')}/${p}`
const D = 'src/main/resources/data/apocalypse_firstlight'

export default {
  watch: [
    abs(`${D}/survivor_backgrounds/*.json`),
    abs(`${D}/survivor_body/*.json`),
    abs(`${D}/survivor_traits/*.json`),
    abs(`${D}/survivor_growth/*.json`),
    abs(`${D}/thirst/*.json`),
    abs('src/main/resources/assets/apocalypse_firstlight/lang/*.json')
  ],
  load() {
    return loadSurvivor()
  }
}
