// Dynamic routes for gun pages: one page per native_guns/*.json, prose from content/guns/<id>.<loc>.md.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadGuns } from './gamedata.mjs'

const CONTENT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../content/guns')

export function gunPaths(loc) {
  return loadGuns().map((g) => {
    const file = path.join(CONTENT, `${g.id}.${loc}.md`)
    return {
      params: { gun: g.id, title: g.name[loc] },
      content: fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : ''
    }
  })
}
