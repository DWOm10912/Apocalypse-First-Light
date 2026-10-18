// Values quoted in page text: <V d="stamina/stamina_v1#costs.sprint" /> reads a datapack JSON,
// <V j="DieselGeneratorBlockEntity.RATED" /> reads a numeric Java constant.
// Only references that appear in the wiki's markdown are resolved and shipped.
import { collectRefs, resolveRefs } from '../lib/values.mjs'
import { REPO } from '../lib/gamedata.mjs'

const abs = (p) => `${REPO.replace(/\\/g, '/')}/${p}`

export default {
  watch: [
    abs('wiki/**/*.md'),
    abs('src/main/resources/data/apocalypse_firstlight/**/*.json'),
    abs('src/main/java/com/antaurora/apofirstlight/**/*.java')
  ],
  load() {
    return resolveRefs(collectRefs())
  }
}
