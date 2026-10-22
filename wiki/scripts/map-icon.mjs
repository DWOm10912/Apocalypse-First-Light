// The Prairie road map's raw icon render, drawn offline: Blockbench leaves the sheet out when the pencil is in the same
// model (the sheet alone renders), so this projects the game model (models/block/map/prairie_road_map_floor.json, the
// item's display.gui rotation [62, 0, 0]) orthographically from its own texture: the sheet's top face, foreshortened by
// sin 62°, and the pencil's top face turned 22.5° about y. The sheet's 0.05 px edge and the pencil's 0.14 px height are
// below a pixel at icon size and left out.
// Usage (from wiki/): node scripts/map-icon.mjs, then node scripts/icons.mjs
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

const WIKI = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO = path.resolve(WIKI, '..')
const TEX = path.join(REPO, 'src/main/resources/assets/apocalypse_firstlight/textures/block/map/prairie_road_map.png')
const OUT = path.join(REPO, 'design/wiki/icons/raw/prairie_road_map.png')

const N = 1024, T = 240 / 16                         // canvas; texels per model px
const k = Math.sin(62 * Math.PI / 180)               // the top face's depth on screen
const S = N * 0.9 / 9.6                               // screen px per model px (the sheet is 9.6 px wide)
const sheet = { x0: 3.2, z0: 4.4, w: 9.6, d: 7.2 }    // element 1, up face uv [0, 0, 9.6, 7.2]
const pencil = { cx: 10.1, cz: 9.97, len: 3, wid: 0.14, angle: 22.5 }   // element 2, up face uv [0, 13.3333, 3, 13.4733]

const sheetImg = await sharp(TEX).extract({ left: 0, top: 0, width: Math.round(sheet.w * T), height: Math.round(sheet.d * T) })
  .resize(Math.round(sheet.w * S), Math.round(sheet.d * S * k), { kernel: 'nearest', fit: 'fill' }).png().toBuffer()
const pencilStrip = await sharp(TEX).extract({ left: 0, top: 200, width: 45, height: 2 })
  .resize(Math.round(pencil.len * S), Math.max(2, Math.round(pencil.wid * S)), { kernel: 'nearest', fit: 'fill' }).png().toBuffer()
// on screen the pencil's 22.5° turn is flattened along the depth axis like the sheet
const screenAngle = Math.atan2(Math.sin(pencil.angle * Math.PI / 180) * k, Math.cos(pencil.angle * Math.PI / 180)) * 180 / Math.PI
const pencilImg = await sharp(pencilStrip).rotate(screenAngle, { background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer({ resolveWithObject: true })

const left = Math.round((N - sheet.w * S) / 2), top = Math.round((N - sheet.d * S * k) / 2)
const px = left + (pencil.cx - sheet.x0) * S, pz = top + (pencil.cz - sheet.z0) * S * k
await sharp({ create: { width: N, height: N, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite([
    { input: sheetImg, left, top },
    { input: pencilImg.data, left: Math.round(px - pencilImg.info.width / 2), top: Math.round(pz - pencilImg.info.height / 2) },
  ])
  .png().toFile(OUT)
console.log('prairie_road_map ->', path.relative(REPO, OUT))
