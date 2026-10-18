import { gunPaths } from '../../../.vitepress/lib/gun-routes.mjs'

export default {
  watch: ['../../../content/guns/*.md', '../../../../src/main/resources/data/apocalypse_firstlight/native_guns/*.json'],
  paths: () => gunPaths('en')
}
