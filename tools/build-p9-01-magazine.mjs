// P9-01 standard 17-round magazine as an item (p9_01_magazine; docs/native_guns/magazine_system_v1.md 6): the gun's own
// magazine cut out of the v3 game mesh, so the item, the magazine in the gun and the reload's magazines are one shape.
//   node tools/build-p9-01-magazine.mjs          -> writes geo/p9_01_magazine.geo.json and meshes/p9_01_magazine.aflmesh.json
//   node tools/build-p9-01-magazine.mjs --check  -> verifies both are up to date with the gun mesh
// Parts: part_mag_base, part_mag_body (bone magazine) and part_mag_follower (bone follower, same pivot), unchanged
// (bone_pivot_local_blocks: the asset origin is the gun's magazine pivot [0, 3.5992, 1.647], the 22 deg grip tilt baked in,
// as the extended magazine). One bone magazine_root. UVs stay on the gun's 2048 atlas: the item draws with the gun's
// texture (NativeMagazineItem textureAsset p9_01_v3_native), no texture of its own.
// Also prints the straightened bounds (the item renderer's +22 deg about x) for the item lift and the prop points.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const NAME = 'p9_01_magazine';
const SOURCE = path.join(assets, 'meshes/p9_01_v3_native.aflmesh.json');
const OUT = {geo: path.join(assets, `geo/${NAME}.geo.json`), mesh: path.join(assets, `meshes/${NAME}.aflmesh.json`)};
const PARTS = ['part_mag_base', 'part_mag_body', 'part_mag_follower'];
const TILT = 22;

const source = JSON.parse(fs.readFileSync(SOURCE, 'utf8'));
const parts = PARTS.map(name => {
  const p = source.parts.find(q => q.name === name);
  if (!p) throw new Error(`${name} missing from ${SOURCE}`);
  if (p.bone !== 'magazine' && p.bone !== 'follower') throw new Error(`${name} is on bone ${p.bone}`);
  return {name: p.name, bone: 'magazine_root', vertices: p.vertices, faces: p.faces};
});

const num = v => Number.isInteger(v) ? String(v) : String(v);
function meshText() {
  const lines = ['{', `  "format_version": ${source.format_version},`, `  "coordinate_space": "${source.coordinate_space}",`,
    `  "uv_origin": "${source.uv_origin}",`, `  "winding": "${source.winding}",`,
    `  "texture_size": [${source.texture_size.join(',')}],`];
  if (source.crease_angle !== undefined) lines.push(`  "crease_angle": ${source.crease_angle},`);
  if (source.crease_weighting !== undefined) lines.push(`  "crease_weighting": "${source.crease_weighting}",`);
  lines.push('  "parts": [');
  parts.forEach((p, i) => {
    lines.push('    {', `      "name": "${p.name}",`, `      "bone": "${p.bone}",`, '      "vertices": [');
    p.vertices.forEach((v, j) => lines.push(`        [${v.map(num).join(',')}]${j < p.vertices.length - 1 ? ',' : ''}`));
    lines.push('      ],', '      "faces": [');
    p.faces.forEach((f, j) => lines.push(`        [${f.join(',')}]${j < p.faces.length - 1 ? ',' : ''}`));
    lines.push('      ]', `    }${i < parts.length - 1 ? ',' : ''}`);
  });
  lines.push('  ]', '}', '');
  return lines.join('\n');
}

function geoText() {
  return JSON.stringify({
    format_version: '1.12.0',
    'minecraft:geometry': [{
      description: {identifier: `geometry.${NAME}`, texture_width: source.texture_size[0], texture_height: source.texture_size[1],
        visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]},
      bones: [{name: 'magazine_root', pivot: [0, 0, 0]}],
    }],
  }, null, 2) + '\n';
}

const outputs = [[OUT.geo, geoText()], [OUT.mesh, meshText()]];
if (process.argv.includes('--check')) {
  let bad = 0;
  for (const [file, text] of outputs) {
    const now = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : null;
    if (now !== text) { console.log(`STALE ${path.relative(root, file)}`); bad++; }
  }
  if (bad) process.exit(1);
  console.log('p9_01_magazine up to date');
} else {
  for (const [file, text] of outputs) fs.writeFileSync(file, text);
  console.log(`wrote ${outputs.map(o => path.relative(root, o[0])).join(', ')}`);
}

// bounds: as stored (blocks, magazine pivot origin) and straightened (+TILT about x, the item renderer's tilt)
const c = Math.cos(TILT * Math.PI / 180), s = Math.sin(TILT * Math.PI / 180);
const box = () => ({min: [Infinity, Infinity, Infinity], max: [-Infinity, -Infinity, -Infinity]});
const raw = box(), straight = box();
for (const p of parts) for (const v of p.vertices) {
  const st = [v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c];
  for (let i = 0; i < 3; i++) {
    raw.min[i] = Math.min(raw.min[i], v[i]); raw.max[i] = Math.max(raw.max[i], v[i]);
    straight.min[i] = Math.min(straight.min[i], st[i]); straight.max[i] = Math.max(straight.max[i], st[i]);
  }
}
const f = b => `[${b.min.map(x => x.toFixed(5))}] .. [${b.max.map(x => x.toFixed(5))}]`;
console.log(`raw ${f(raw)}`);
console.log(`straightened ${f(straight)}`);
