// Trims the transparent margin off raw Blockbench renders and centres each on the same canvas,
// so every gun image has identical framing. Usage: node scripts/frame-renders.mjs
//   in:  design/wiki/gun_renders/raw/<id>.png   (Blockbench offscreen captures, transparent background)
//   out: design/wiki/gun_renders/<id>.png       (1600x1000, model fills at most 84% x 76%)
import fs from 'node:fs'
import path from 'node:path'
import sharp from 'sharp'
import { fileURLToPath } from 'node:url'

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const RAW = path.join(REPO, 'design/wiki/gun_renders/raw')
const OUT = path.join(REPO, 'design/wiki/gun_renders')
const W = 1600, H = 1000, FILL_W = 0.84, FILL_H = 0.76

for (const f of fs.readdirSync(RAW).filter((f) => f.endsWith('.png'))) {
  const trimmed = await sharp(path.join(RAW, f)).trim({ threshold: 1 }).png().toBuffer({ resolveWithObject: true })
  const { width, height } = trimmed.info
  const scale = Math.min((W * FILL_W) / width, (H * FILL_H) / height)
  const w = Math.round(width * scale), h = Math.round(height * scale)
  const model = await sharp(trimmed.data).resize(w, h).png().toBuffer()
  await sharp({ create: { width: W, height: H, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: model, left: Math.round((W - w) / 2), top: Math.round((H - h) / 2) }])
    .png()
    .toFile(path.join(OUT, f))
  console.log(`${f}  ${width}x${height} -> ${w}x${h}`)
}
