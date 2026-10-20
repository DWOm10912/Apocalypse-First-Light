// Turns a rendered frame run into a seamless flipbook loop (fire flames, docs/gameplay/fuel_fire_v1.md "火焰贴图 V3"):
// loop frame i is render frame start + i; the first `fade` loop frames are crossfaded with the frames that follow the loop
// (start + n + i), so the last loop frame runs straight into the first. Frames are read as f%03d.png, written as l00.png ...
//   node tools/blender-loop-crossfade.mjs <inDir> <outDir> <start> <n> <fade>
// e.g. node tools/blender-loop-crossfade.mjs render/ tools/blender/fuel_flame_v2_loop 60 48 12   (needs frames 60..119)
// A plain colour blend of two flames turns a bright yellow over black into a dim olive, which a real flame never is (a
// weaker flame is cooler, so redder). So the crossfade blends brightness (luminance) and colours it back from the render
// itself: every brightness level gets the mean colour of the unblended frames' pixels at that level.
import fs from 'node:fs';
import path from 'node:path';
import {png, readPng} from './cube-slab-mesh-lib.mjs';

const [inDir, outDir, startArg, nArg, fadeArg] = process.argv.slice(2);
if (!fadeArg) throw new Error('usage: node tools/blender-loop-crossfade.mjs <inDir> <outDir> <start> <n> <fade>');
const start = +startArg, n = +nArg, fade = +fadeArg, BINS = 1024;
const read = f => readPng(fs.readFileSync(path.join(inDir, `f${String(f).padStart(3, '0')}.png`)));
const lum = (px, k) => 0.2126 * px[k] + 0.7152 * px[k + 1] + 0.0722 * px[k + 2];   // 0..255

// brightness -> mean colour, from the frames that are used as they are
const sum = new Float64Array(BINS * 3), cnt = new Float64Array(BINS);
for (let i = fade; i < n; i++) {
  const a = read(start + i);
  for (let p = 0; p < a.w * a.h; p++) {
    const k = p * a.bpp, bin = Math.min(BINS - 1, Math.floor(lum(a.px, k) / 256 * BINS));
    sum[bin * 3] += a.px[k]; sum[bin * 3 + 1] += a.px[k + 1]; sum[bin * 3 + 2] += a.px[k + 2]; cnt[bin]++;
  }
}
const lut = new Float64Array(BINS * 3);
for (let b = 0, last = [0, 0, 0]; b < BINS; b++) {   // empty bins take the nearest darker one
  if (cnt[b] > 0) last = [sum[b * 3] / cnt[b], sum[b * 3 + 1] / cnt[b], sum[b * 3 + 2] / cnt[b]];
  lut[b * 3] = last[0]; lut[b * 3 + 1] = last[1]; lut[b * 3 + 2] = last[2];
}

fs.mkdirSync(outDir, {recursive: true});
for (let i = 0; i < n; i++) {
  const a = read(start + i), out = Buffer.alloc(a.w * a.h * 4);
  const b = i < fade ? read(start + n + i) : null, w = b ? (i + 0.5) / fade : 1;   // weight of the loop's own frame
  for (let p = 0; p < a.w * a.h; p++) {
    const k = p * a.bpp, o = p * 4;
    if (!b) { out[o] = a.px[k]; out[o + 1] = a.px[k + 1]; out[o + 2] = a.px[k + 2]; }
    else {
      const l = w * lum(a.px, k) + (1 - w) * lum(b.px, p * b.bpp), bin = Math.min(BINS - 1, Math.floor(l / 256 * BINS));
      for (let c = 0; c < 3; c++) out[o + c] = Math.round(lut[bin * 3 + c]);
    }
    out[o + 3] = 255;
  }
  fs.writeFileSync(path.join(outDir, `l${String(i).padStart(2, '0')}.png`), png(out, a.w, a.h));
}
console.log(`wrote ${n} loop frames to ${outDir} (${fade} crossfaded through the render's brightness -> colour)`);
