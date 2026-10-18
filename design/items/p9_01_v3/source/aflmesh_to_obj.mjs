// The current P9 runtime mesh as an OBJ (one object per part) in Blender axes, metres, for a shape reference.
// Model space = bone pivot (px / 16) + vertex (blocks). Gecko: +y up, the muzzle at -z. Blender: +z up, the muzzle at +y... we
// keep it simple: Blender x = model x, Blender y = -model z (muzzle towards +y), Blender z = model y.
import fs from 'node:fs';

const RES = 'D:/Minecraft Modding/AFL-survivor-v0/src/main/resources/assets/apocalypse_firstlight/';
const OUT = process.argv[2];
const mesh = JSON.parse(fs.readFileSync(RES + 'meshes/p9_01_v2_native.aflmesh.json', 'utf8'));
const geo = JSON.parse(fs.readFileSync(RES + 'geo/p9_01_v2_native.geo.json', 'utf8'))['minecraft:geometry'][0];
const pivot = Object.fromEntries(geo.bones.map(b => [b.name, b.pivot.map(v => v / 16)]));
const SKIP = new Set(['empty_old_mag', 'reload_magazine']);

// overall length along z (blocks), to scale to the real 208 mm
let zmin = Infinity, zmax = -Infinity, ymin = Infinity, ymax = -Infinity;
for (const p of mesh.parts) {
  if (SKIP.has(p.bone)) continue;
  const pv = pivot[p.bone];
  for (const v of p.vertices) {
    const z = pv[2] + v[2], y = pv[1] + v[1];
    zmin = Math.min(zmin, z); zmax = Math.max(zmax, z); ymin = Math.min(ymin, y); ymax = Math.max(ymax, y);
  }
}
const scale = 0.208 / (zmax - zmin);
console.log('length blocks', (zmax - zmin).toFixed(4), 'height blocks', (ymax - ymin).toFixed(4), 'scale m/block', scale.toFixed(4),
  'height mm', ((ymax - ymin) * scale * 1000).toFixed(1));
let out = '# P9-01 v2 native runtime mesh, metres, scaled to 208 mm length\n', base = 1;
for (const p of mesh.parts) {
  if (SKIP.has(p.bone)) continue;
  const pv = pivot[p.bone];
  out += 'o ' + p.name + '\n';
  for (const v of p.vertices) {
    const x = (pv[0] + v[0]) * scale, y = (pv[1] + v[1] - ymin) * scale, z = (pv[2] + v[2]) * scale;
    out += `v ${x.toFixed(6)} ${(-z).toFixed(6)} ${y.toFixed(6)}\n`;
  }
  for (const f of p.faces) out += 'f ' + f.map(i => i + base).join(' ') + '\n';
  base += p.vertices.length;
}
fs.writeFileSync(OUT, out);
console.log('parts', mesh.parts.filter(p => !SKIP.has(p.bone)).length, 'written', OUT);
