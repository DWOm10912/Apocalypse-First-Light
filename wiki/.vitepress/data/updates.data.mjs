// VitePress build-time data: the main line's recent commits for the update log (see ../lib/gamedata.mjs).
import { loadUpdates } from '../lib/gamedata.mjs'

export default {
  load() {
    return loadUpdates(80)
  }
}
