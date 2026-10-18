// Converts the images listed in images.json into public/img/<key>.webp (or .png).
// Usage: node scripts/sync-images.mjs [--check]
//   --check  only reports missing outputs or sources, writes nothing
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO = path.resolve(WIKI, '..')
const OUT = path.join(WIKI, 'public/img')
const check = process.argv.includes('--check')
const list = JSON.parse(fs.readFileSync(path.join(WIKI, 'images.json'), 'utf8'))

let problems = 0
const sharp = check ? null : (await import('sharp')).default
fs.mkdirSync(OUT, { recursive: true })
for (const [key, spec] of Object.entries(list)) {
  if (key.startsWith('_')) continue
  const src = path.join(REPO, spec.src)
  const fmt = spec.format ?? 'webp'
  const out = path.join(OUT, `${key}.${fmt}`)
  if (check) {
    if (!fs.existsSync(out)) { console.log(`missing output: public/img/${key}.${fmt} (run npm run images)`); problems++ }
    continue
  }
  if (!fs.existsSync(src)) { console.log(`missing source: ${spec.src}`); problems++; continue }
  let img = sharp(src)
  if (spec.crop) {
    const m = await img.metadata()
    const [l, t, w, h] = spec.crop
    img = img.extract({ left: Math.round(l * m.width), top: Math.round(t * m.height), width: Math.round(w * m.width), height: Math.round(h * m.height) })
  }
  img = img.resize({ width: spec.width ?? 1600, withoutEnlargement: true })
  img = fmt === 'png' ? img.png() : img.webp({ quality: 82 })
  await img.toFile(out)
  console.log(`${key}.${fmt}  ${(fs.statSync(out).size / 1024).toFixed(0)} KB`)
}
if (problems) process.exitCode = 1
