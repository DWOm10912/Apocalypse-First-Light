// P9-01 28-round Extended Magazine V3 (item p9_01_extended_magazine; docs/native_guns/p9_01_extended_magazine_v1.md V3):
// the V3 standard magazine (the gun's own part_mag_base / part_mag_body / part_mag_follower, as build-p9-01-magazine.mjs
// cuts them) lengthened, so the extended magazine is the same black polymer magazine as the gun's, only longer.
//   node tools/build-p9-01-extended-magazine.mjs          -> writes the editable source, geo and mesh
//   node tools/build-p9-01-extended-magazine.mjs --check  -> verifies all three are up to date with the gun mesh
// Replaces the 2026-09-27 V2 build (grip-extension sleeve, tan inserts, own 512 atlas; in git history before 2026-10-20).
// Frame: as the standard magazine, bone_pivot_local_blocks with the asset origin at the gun's magazine pivot
// [0, 3.5992, 1.647] and the 22 deg grip tilt baked in; the item renderer takes the tilt back out (itemTilt 22). The work
// below is done "straightened" (+22 deg about x, magazine axis = +y, in px) and turned back.
//   - body: one straight prism from the floorplate (y' -4.165) to the feed lips (y' 0.107), no rings between; every body
//     vertex below CUT moves down EXTEND, the long side faces stretch (their colour is even along the length).
//   - floorplate: moved down EXTEND, unchanged, plus a small wedge-shaped finger lip at its front (-z is the muzzle side), the one
//     mark that tells it from the 17-round magazine beside the length. Its UVs sit on the plate's own bottom texels.
//   - follower: the gun draws its own follower bone in the fitted magazine (the extended replaces only the magazine
//     bones, not their children), so this copy sits where the gun's loaded follower is (static_idle offset
//     [0, -0.4172, 0.1686] px) and SINK lower: always under the gun's own, the only one in the reload / dropped copies and
//     the item.
// Length: 17 rounds 4.71 px (y' -4.519 .. 0.191); a 9 mm double-stack magazine grows about 6 mm a round, +11 rounds
// ~ +66 mm on a ~128 mm magazine -> EXTEND 2.5 px, 7.21 px, x1.53.
// UVs stay on the gun's 2048 atlas: the item draws with p9_01_v3_native (NativeMagazineItem textureAsset).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {convert} from './export-afl-mesh.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const NAME = 'p9_01_extended_magazine';
const SOURCE = path.join(assets, 'meshes/p9_01_v3_native.aflmesh.json');
const OUT = {
  src: path.join(root, `src/main/blockbench/${NAME}_mesh.bbmodel`),
  geo: path.join(assets, `geo/${NAME}.geo.json`),
  mesh: path.join(assets, `meshes/${NAME}.aflmesh.json`),
};
const TILT = 22, EXTEND = 2.5, CUT = -2.0, SINK = 0.06;
const LOADED = [0, -0.4172, 0.1686];        // follower static_idle offset, px, asset frame
// straightened px, before EXTEND: root (inside the plate, z0) x +-x, y y0..y1; tip (z1) x +-xTip, y y0..yTip (a wedge)
const LIP = {x: 0.30, xTip: 0.24, y0: -4.50, y1: -4.34, yTip: -4.43, z0: -0.95, z1: -1.22};
const TEX = 2048;

const c = Math.cos(TILT * Math.PI / 180), s = Math.sin(TILT * Math.PI / 180);
const straight = v => [v[0] * 16, (v[1] * c - v[2] * s) * 16, (v[1] * s + v[2] * c) * 16];
const tilted = q => [q[0] / 16, (q[1] * c + q[2] * s) / 16, (-q[1] * s + q[2] * c) / 16];
const r10 = x => +x.toFixed(10) || 0;

const source = JSON.parse(fs.readFileSync(SOURCE, 'utf8'));
const take = name => {
  const p = source.parts.find(q => q.name === name);
  if (!p) throw new Error(`${name} missing from ${SOURCE}`);
  return p;
};
const moved = (p, fn) => p.vertices.map(v => { const q = fn(straight(v)); return [...tilted(q).map(r10), v[3], v[4]]; });

const body = take('part_mag_body'), base = take('part_mag_base'), follower = take('part_mag_follower');
for (const v of body.vertices) { const y = straight(v)[1]; if (Math.abs(y - CUT) < 1) throw new Error(`body vertex at y' ${y} near the cut`); }
const parts = [
  {name: 'part_mag_body', vertices: moved(body, q => q[1] < CUT ? [q[0], q[1] - EXTEND, q[2]] : q), faces: body.faces},
  {name: 'part_mag_base', vertices: moved(base, q => [q[0], q[1] - EXTEND, q[2]]), faces: base.faces},
];
const sink = [0, -SINK * c, SINK * s];   // further down the tilted axis, px
parts.push({name: 'part_mag_follower', faces: follower.faces,
  vertices: follower.vertices.map(v => [...[0, 1, 2].map(i => r10(v[i] + (LOADED[i] + sink[i]) / 16)), v[3], v[4]])});

// finger lip: a box on the plate front, UVs on the plate's largest bottom face
const bottom = base.faces.map(f => f.map(i => base.vertices[i]))
  .filter(q => q.every(v => Math.abs(straight(v)[1] + 4.519) < 0.003))
  .map(q => ({q, area: Math.abs((q[1][3] - q[0][3]) * (q[2][4] - q[0][4]) - (q[2][3] - q[0][3]) * (q[1][4] - q[0][4]))}))
  .sort((a, b) => b.area - a.area)[0];
if (!bottom) throw new Error('no floorplate bottom face');
const uc = [0, 1].map(k => bottom.q.reduce((t, v) => t + v[3 + k], 0) / bottom.q.length);
const du = 1.5 / TEX, lipUv = [[uc[0] - du, uc[1] + du], [uc[0] + du, uc[1] + du], [uc[0] + du, uc[1] - du], [uc[0] - du, uc[1] - du]];
{
  // k 0 = tip (front), 1 = root; i 0 / 1 = -x / +x; j 0 / 1 = bottom / top
  const P = (i, j, k) => [(i ? 1 : -1) * (k ? LIP.x : LIP.xTip), (j ? (k ? LIP.y1 : LIP.yTip) : LIP.y0) - EXTEND, k ? LIP.z0 : LIP.z1];
  // corners in order around each face; turned so cross(v1 - v0, v2 - v0) points outward (ccw from outside, as the gun mesh)
  const quads = [
    [P(0, 0, 0), P(1, 0, 0), P(1, 1, 0), P(0, 1, 0)],   // front (-z)
    [P(0, 1, 0), P(1, 1, 0), P(1, 1, 1), P(0, 1, 1)],   // top (+y)
    [P(0, 0, 1), P(1, 0, 1), P(1, 0, 0), P(0, 0, 0)],   // bottom (-y)
    [P(0, 0, 1), P(0, 0, 0), P(0, 1, 0), P(0, 1, 1)],   // -x
    [P(1, 0, 0), P(1, 0, 1), P(1, 1, 1), P(1, 1, 0)],   // +x
  ];
  const out = {front: [0, 0, -1], top: [0, 1, 0], bottom: [0, -1, 0], nx: [-1, 0, 0], px: [1, 0, 0]};
  const vertices = [], faces = [];
  quads.forEach((q0, n) => {
    let q = q0;
    const facing = qq => { const a = qq[1].map((x, i) => x - qq[0][i]), b = qq[2].map((x, i) => x - qq[0][i]), w = Object.values(out)[n];
      return (a[1] * b[2] - a[2] * b[1]) * w[0] + (a[2] * b[0] - a[0] * b[2]) * w[1] + (a[0] * b[1] - a[1] * b[0]) * w[2]; };
    if (facing(q) < 0) q = [...q].reverse();
    const e1 = q[1].map((x, i) => x - q[0][i]), e2 = q[2].map((x, i) => x - q[0][i]);
    const nrm = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
    const want = Object.values(out)[n];
    if (nrm[0] * want[0] + nrm[1] * want[1] + nrm[2] * want[2] <= 0) throw new Error(`lip face ${n} winds inward`);
    faces.push(q.map((p, k) => { vertices.push([...tilted(p).map(r10), lipUv[k][0], lipUv[k][1]]); return vertices.length - 1; }));
  });
  parts.push({name: 'part_mag_lip', vertices, faces});
}
for (const p of parts) p.bone = 'magazine_root';

const num = v => String(v);
function meshText() {
  const lines = ['{', `  "format_version": ${source.format_version},`, `  "coordinate_space": "${source.coordinate_space}",`,
    `  "uv_origin": "${source.uv_origin}",`, `  "winding": "${source.winding}",`, `  "texture_size": [${source.texture_size.join(',')}],`];
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
const geo = {format_version: '1.12.0', 'minecraft:geometry': [{
  description: {identifier: `geometry.${NAME}`, texture_width: TEX, texture_height: TEX,
    visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, -0.25, 0]},
  bones: [{name: 'magazine_root', pivot: [0, 0, 0]}]}]};

// editable source (Blockbench Free Model, px in the asset frame, the gun's atlas by relative path): the icon render and
// anyone opening the magazine in Blockbench see this; the runtime mesh above is written straight from the parts
const uuid = (k) => { let h = 2166136261; for (const ch of NAME + ':' + k) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
  const x = (h.toString(16).padStart(8, '0') + k.length.toString(16).padStart(4, '0')).repeat(3);
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-4${x.slice(13, 16)}-8${x.slice(17, 20)}-${x.slice(20, 32)}`; };
const key = i => i.toString(36).padStart(4, '0');
const elements = parts.map(p => {
  const vertices = {}, faces = {};
  p.vertices.forEach((v, i) => { vertices[key(i)] = v.slice(0, 3).map(x => r10(x * 16)); });
  p.faces.forEach((f, j) => { faces['f' + key(j)] = {uv: Object.fromEntries(f.map(i => [key(i), [r10(p.vertices[i][3] * TEX), r10(p.vertices[i][4] * TEX)]])),
    vertices: f.map(key), texture: 0}; });
  return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
    render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('el:' + p.name)};
});
const group = {name: 'magazine_root', uuid: uuid('group'), export: true, locked: false, scope: 0, selected: false, visibility: true,
  origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true};
const bbmodel = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: `${NAME}_mesh`, model_identifier: '',
  visible_box: [1, 1, 0], variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
  resolution: {width: TEX, height: TEX}, elements, groups: [group],
  outliner: [{uuid: group.uuid, isOpen: true, children: elements.map(e => e.uuid)}],
  textures: [{name: 'p9_01_v3_native.png', relative_path: 'textures/p9_01_v3_native.png', folder: '', namespace: '', id: '0', group: '',
    scope: 0, width: TEX, height: TEX, uv_width: TEX, uv_height: TEX, particle: false, use_as_default: false, layers_enabled: false,
    sync_to_project: '', file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color',
    fps: 7, frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: false, saved: true,
    uuid: uuid('texture')}],
  animations: []};

// the editable source must say the same as the runtime mesh: run it through the bbmodel exporter and compare
const back = convert(bbmodel, geo, {}, `${NAME}_mesh.bbmodel`, 2);
let worst = 0;
for (const p of parts) {
  const q = back.parts.find(x => x.name === p.name);
  if (!q) throw new Error(`source/runtime mismatch: ${p.name} missing`);
  // the exporter welds / splits corners its own way: compare every face corner (position + uv) of one with the other's
  const corners = m => m.faces.flatMap(f => f.map(i => m.vertices[i].slice(0, 5)));
  const near = (a, list) => Math.min(...list.map(b => Math.max(...a.map((x, k) => Math.abs(x - b[k])))));
  const A = corners(p), B = corners(q);
  // (it may also split a quad it finds bent into two triangles: compare both ways, not the counts)
  for (const a of A) worst = Math.max(worst, near(a, B));
  for (const b of B) worst = Math.max(worst, near(b, A));
}
if (worst > 2e-5) throw new Error(`editable source differs from the runtime mesh by ${worst}`);

const outputs = [[OUT.src, JSON.stringify(bbmodel)], [OUT.geo, JSON.stringify(geo, null, 2) + '\n'], [OUT.mesh, meshText()]];
if (process.argv.includes('--check')) {
  let bad = 0;
  for (const [file, text] of outputs) {
    const now = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : null;
    if (now !== text) { console.log(`STALE ${path.relative(root, file)}`); bad++; }
  }
  if (bad) process.exit(1);
  console.log(`${NAME} up to date`);
} else {
  for (const [file, text] of outputs) fs.writeFileSync(file, text);
  console.log(`wrote ${outputs.map(o => path.relative(root, o[0])).join(', ')}`);
}

// straightened bounds (the item renderer's +22 deg) -> the item lift (bounds centre, px), and the length vs 17 rounds
const all = parts.flatMap(p => p.vertices.map(straight));
const ext = i => [Math.min(...all.map(q => q[i])), Math.max(...all.map(q => q[i]))];
const [y0, y1] = ext(1);
console.log(JSON.stringify({straightenedPx: {x: ext(0).map(v => +v.toFixed(3)), y: [+y0.toFixed(3), +y1.toFixed(3)], z: ext(2).map(v => +v.toFixed(3))},
  lengthPx: +(y1 - y0).toFixed(3), vsStandard: +((y1 - y0) / 4.71).toFixed(3), itemLiftPx: +(-(y0 + y1) / 2).toFixed(3),
  sourceRoundTrip: worst, parts: parts.map(p => `${p.name}:${p.faces.reduce((t, f) => t + f.length - 2, 0)}`)}));
