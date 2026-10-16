// Halstead Tamarack T15 pickup, body V1 (2026-10-15; docs/vehicles/first_pickup_v0_design.md sections 2.2 and 2.4):
// rebuilt to the real reference structure (Toyota Hilux LN106 parts-catalogue drawings) with rounded hard-surface
// geometry (tools/vehicle-mesh-kit.mjs), split into five Pure Meshes so each stays inside the Vextris limits and the
// renderer can skip what is not seen:
//   pickup_t15          outside: body, hood, doors, tailgate, glass, lamps, bumpers          (always)
//   pickup_t15_wheel    one wheel at its hub, drawn at the four corners and as the spare      (always)
//   pickup_t15_cab      interior: dash, gauges, column, wheel, pedals, levers, seat, trims    (near)
//   pickup_t15_engine   engine bay: engine and its ancillaries, aprons, firewall              (hood open, near)
//   pickup_t15_chassis  frame, axles, springs, driveline, exhaust, tank, radiator             (near)
// Three 2048 atlases (outside + wheel, cab, engine + chassis), each Base Color + LabPBR _s / _n with ray-traced ambient
// occlusion, and ten wear overlays (pickup_t15_wear_1..10: texel alpha 255 where the paint is worn through at that wear
// tenth; the renderer binds the one for round(wear x 10), because the vanilla cutout tests texture alpha only).
// Shading normals: Vextris format 3 crease smoothing, off for now (CREASE = false: format 2, flat normals). With it on, each
// sidecar carries crease_angle (SMOOTHING.crease_deg) and a per-part override for the materials in SMOOTHING.materials, and
// Vextris averages the faces across soft edges at load. Its 0.1.1-alpha.2 average is weighted by corner angle, which bends
// the big flat panels next to bevels (tailgate ribs smeared, bed sides shaded like a bulge; design/vehicles/
// first_pickup_v0/sheets/v3n_close_*.png); face-area weighting is requested from Vextris (user 2026-10-15,
// docs/dev/vextris_0_1_1_integration.md section 4). No normals are written here: shared mesh capabilities belong to Vextris.
//   node tools/build-pickup-t15.mjs           writes every output
//   node tools/build-pickup-t15.mjs --check   verifies every output is up to date (writes nothing)
//   node tools/build-pickup-t15.mjs --fast    no occlusion bake (quick geometry iterations; not for delivery)
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {unfoldUV, bake, Bvh, hemisphere, png, newell, norm, cross, sub, add, mul, dot} from './vehicle-mesh-kit.mjs';
import {MATS, MESHES, ATLASES, PIVOTS, PAINTED, SPARE, HINGES, ANCHORS, SMOOTHING, allParts, FX, RX, R, TR, YB, ROOF, AY, AR} from './pickup-t15/common.mjs';
import {buildExterior} from './pickup-t15/exterior.mjs';
import {buildWheel} from './pickup-t15/wheel.mjs';
import {SHADE} from './pickup-t15/shade.mjs';
const CREASE = false;   // Vextris format 3 crease_angle (see the header)
let buildCab = () => {}, buildEngine = () => {}, buildChassis = () => {};
try { ({buildCab} = await import('./pickup-t15/cab.mjs')); } catch (e) { if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e; }
try { ({buildEngine} = await import('./pickup-t15/engine.mjs')); } catch (e) { if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e; }
try { ({buildChassis} = await import('./pickup-t15/chassis.mjs')); } catch (e) { if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e; }

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const BB = path.join(ROOT, 'src/main/blockbench/vehicles');
const TEX = path.join(ASSETS, 'textures/entity/vehicle');
const K = 16, PAD = 4, WEAR = 1024, LEVELS = 10;
const CHECK = process.argv.includes('--check'), FAST = process.argv.includes('--fast');
const r12 = v => +v.toFixed(12) || 0, r6 = v => +v.toFixed(6) || 0;
const assert = (c, m) => { if (!c) throw new Error('pickup: ' + m); };
const t0 = Date.now(), log = m => console.error(`[${((Date.now() - t0) / 1000).toFixed(1)} s] ${m}`);

// ---------------------------------------------------------------- geometry
buildExterior(); buildWheel(); buildCab(); buildEngine(); buildChassis();
const ALL = allParts();
// concept metres -> runtime source px (front toward -Z, right toward +X; a rotation, not a mirror)
const toRuntime = ([x, y, z]) => [z * K, y * K, -x * K];
const toConcept = ([X, Y, Z]) => [-Z / K, Y / K, X / K];
const toConceptDir = ([X, Y, Z]) => [-Z, Y, X];
for (const p of ALL) p.v = p.v.map(toRuntime);
// drop degenerate faces (zero area after rounding, repeated corners)
let dropped = 0;
for (const p of ALL) p.f = p.f.filter(f => { const P = f.ids.map(i => p.v[i]);
  const ok = Math.hypot(...newell(P)) > 2e-6 && new Set(P.map(q => q.map(v => v.toFixed(7)).join())).size === P.length; if (!ok) dropped++; return ok; });
const pivotPx = b => toRuntime(PIVOTS[b]);
log(`geometry: ${ALL.length} parts, ${ALL.reduce((s, p) => s + p.f.length, 0)} faces (${dropped} degenerate dropped)`);

// ---------------------------------------------------------------- UV per atlas
const DENSITY = {dial: 4, needle: 2, liner: 0.5, under: 0.45, rubber: 0.8, glass: 0.25, mirror: 0.4, felt: 0.4, steel: 0.7, engine: 0.75, cover: 0.75, alu: 0.75,
  iron: 0.6, radiator: 0.6, cream: 0.7, washer: 0.7, redcap: 0.6, yellow: 0.6, lead: 0.6, belting: 0.6, mat: 0.7, headliner: 0.6, belt: 0.8};
const UV = {};
for (const [a, spec] of Object.entries(ATLASES)) {
  const parts = ALL.filter(p => MESHES[p.mesh].atlas === a);
  if (!parts.length) continue;
  UV[a] = unfoldUV(parts, {atlas: spec.size, pad: PAD, startS: 30, stepS: 0.25, maxDihedral: 47, density: (p, m) => DENSITY[m] ?? 1});
  log(`uv ${a}: ${UV[a].islands.length} islands, ${UV[a].S.toFixed(2)} texels per px`);
}

// ---------------------------------------------------------------- occlusion scene (rest pose, wheels at the corners)
const WHEELS = [['fl', FX, 1], ['fr', FX, -1], ['rl', RX, 1], ['rr', RX, -1]].map(([n, x, left]) => ({n, x, left}));
/** Runtime placement of a wheel-mesh point: hub at (+-TR, R, -x); the left wheels turned half a turn about Y. */
const wheelAt = (w, [X, Y, Z]) => { const c = toRuntime([w.x, R, (w.left > 0 ? -1 : 1) * TR]); return w.left > 0 ? [c[0] - X, c[1] + Y, c[2] - Z] : [c[0] + X, c[1] + Y, c[2] + Z]; };
/** The spare: centre at SPARE, turned -90 deg about Z (the outboard face, +X, points down). */
const spareAt = ([X, Y, Z]) => { const c = toRuntime([SPARE.x, SPARE.y, 0]); return [c[0] + Y, c[1] - X, c[2] + Z]; };
function occluders() {
  const T = [];
  for (const p of ALL) { if (MATS[p.mat].alpha !== undefined) continue;
    const xf = p.mesh === 'wheel' ? [...WHEELS.map(w => q => wheelAt(w, q)), spareAt] : [q => q];
    for (const tf of xf) for (const f of p.f) { const P = f.ids.map(i => tf(p.v[i])); for (let q = 1; q + 1 < P.length; q++) T.push([P[0], P[q], P[q + 1]]); } }
  return T;
}
const BVH = FAST ? null : new Bvh(occluders());
if (BVH) log(`occlusion bvh: ${BVH.n} triangles`);

// ---------------------------------------------------------------- bake
const OUT = [];   // [file, data]
const DUMMY_BG = {c: [40, 40, 40, 255], s: [60, 10, 0, 255], n: [128, 128, 255, 255]};
const ATLAS_PNG = {};
for (const [a, spec] of Object.entries(ATLASES)) {
  if (!UV[a]) continue;
  const parts = ALL.filter(p => MESHES[p.mesh].atlas === a);
  // the wheel bakes in the front-right wheel's place
  const wheelParts = parts.filter(p => p.mesh === 'wheel');
  const saved = wheelParts.map(p => p.v);
  for (const p of wheelParts) p.v = p.v.map(q => wheelAt(WHEELS[1], q));
  const mats = Object.fromEntries(Object.entries(MATS).map(([k, m]) => [k, {...m, shade: SHADE[k]}]));
  const res = bake({parts, uv: UV[a], atlas: spec.size, pad: PAD, mats, toModel: toConcept, toModelDir: toConceptDir, bvh: BVH, aoDirs: hemisphere(28), aoMax: 0.45 * K, aoStride: 2, bump: 0.6, background: DUMMY_BG});
  wheelParts.forEach((p, i) => { p.v = saved[i]; });
  ATLAS_PNG[a] = res.PNG;
  ['', '_s', '_n'].forEach((k, i) => { OUT.push([path.join(TEX, spec.file + k + '.png'), res.PNG[i]]); OUT.push([path.join(BB, 'textures', spec.file + k + '.png'), res.PNG[i]]); });
  log(`bake ${a}: coverage ${(res.coverage * 100).toFixed(1)} %`);
}

// ---------------------------------------------------------------- wear overlays (outside painted parts, ext + front)
const fract = v => v - Math.floor(v), h3 = (a, b, c) => fract(Math.sin(a * 127.1 + b * 311.7 + c * 74.7) * 43758.5453);
function vnoise(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const fx = s(x - xi), fy = s(y - yi), fz = s(z - zi), v = (i, j, k) => h3(xi + i, yi + j, zi + k);
  return l(l(l(v(0, 0, 0), v(1, 0, 0), fx), l(v(0, 1, 0), v(1, 1, 0), fx), fy), l(l(v(0, 0, 1), v(1, 0, 1), fx), l(v(0, 1, 1), v(1, 1, 1), fx), fy), fz);
}
const fbm = (x, y, z) => 0.6 * vnoise(x, y, z) + 0.3 * vnoise(x * 2.03 + 5.1, y * 2.03, z * 2.03) + 0.1 * vnoise(x * 4.1, y * 4.1 + 2.7, z * 4.1);
const clamp01 = v => Math.max(0, Math.min(1, v)), smooth = (a, b, v) => { const t = clamp01((v - a) / (b - a)); return t * t * (3 - 2 * t); };
/** Wear at a painted point (concept metres): onset (the wear value at which it shows), rust or primer. */
function wearAt([x, y, z], n) {
  const low = clamp01((y - YB) / (ROOF - YB));
  const dArch = Math.min(Math.abs(Math.hypot(x - FX, y - AY) - AR), Math.abs(Math.hypot(x - RX, y - AY) - AR));
  const big = fbm(x * 2.2 + 11, y * 2.2, z * 2.2), mid = fbm(x * 7 + 3, y * 7 + 1, z * 7 + 9);
  let rust = 0.55 + 0.9 * low + 0.6 * (big - 0.5) + 0.25 * (mid - 0.5);
  if (y < 1.0) rust -= 0.42 * Math.exp(-dArch / 0.07);              // around the wheel arches
  rust -= 0.30 * Math.exp(-(y - YB) / 0.06);                         // sills and lower edges
  if (x < -2.15 || x > 2.2) rust -= 0.12 * (1 - low);                // bed rear corners, front corners
  let primer = 0.82 + 0.35 * (fbm(x * 3.1 + 40, y * 3.1, z * 3.1 + 7) - 0.5) + 0.15 * (mid - 0.5);
  if (n[1] > 0.7) primer -= 0.10;                                    // sun-baked tops (hood, roof, rails)
  return rust <= primer ? {onset: rust, rust: true} : {onset: primer, rust: false};
}
let wearTexels = 0;
{
  const onset = new Float32Array(WEAR * WEAR).fill(9), col = new Uint8Array(WEAR * WEAR * 3), isRust = new Uint8Array(WEAR * WEAR);
  const sc = WEAR / ATLASES.ext.size, uv = UV.ext, done = new Uint8Array(WEAR * WEAR);
  for (const pass of [0, 1]) for (const p of ALL) {
    if (MESHES[p.mesh].atlas !== 'ext' || p.mesh === 'wheel' || !PAINTED.has(p.mat)) continue;
    const fu = uv.faceUV.get(p);
    for (const f of p.f) {
      const A = fu.get(f).map(([u, v]) => [u * sc, v * sc]), P3 = f.ids.map(i => toConcept(p.v[i])), n = norm(toConceptDir(newell(f.ids.map(i => p.v[i]))));
      for (let q = 1; q + 1 < A.length; q++) {
        const T = [A[0], A[q], A[q + 1]], den = (T[1][1] - T[2][1]) * (T[0][0] - T[2][0]) + (T[2][0] - T[1][0]) * (T[0][1] - T[2][1]);
        if (Math.abs(den) < 1e-9) continue;
        const tol = pass ? -1.0 / Math.max(1, Math.sqrt(Math.abs(den))) : 0;
        const x0 = Math.max(0, Math.floor(Math.min(...T.map(a => a[0])) - 1)), x1 = Math.min(WEAR - 1, Math.ceil(Math.max(...T.map(a => a[0])) + 1));
        const y0 = Math.max(0, Math.floor(Math.min(...T.map(a => a[1])) - 1)), y1 = Math.min(WEAR - 1, Math.ceil(Math.max(...T.map(a => a[1])) + 1));
        for (let yy = y0; yy <= y1; yy++) for (let xx = x0; xx <= x1; xx++) {
          const X = xx + 0.5, Y = yy + 0.5, k = yy * WEAR + xx;
          const l1 = ((T[1][1] - T[2][1]) * (X - T[2][0]) + (T[2][0] - T[1][0]) * (Y - T[2][1])) / den;
          const l2 = ((T[2][1] - T[0][1]) * (X - T[2][0]) + (T[0][0] - T[2][0]) * (Y - T[2][1])) / den, l3 = 1 - l1 - l2;
          if (l1 < tol || l2 < tol || l3 < tol || (pass && done[k])) continue;
          done[k] = 1;
          const pos = add(add(mul(P3[0], l1), mul(P3[q], l2)), mul(P3[q + 1], l3)), w = wearAt(pos, n);
          onset[k] = w.onset; isRust[k] = w.rust ? 1 : 0;
          let c;
          if (w.rust) { const t = smooth(0.1, 0.75, w.onset), g = fbm(pos[0] * 23, pos[1] * 23, pos[2] * 23); c = [52 + 30 * t + 14 * g, 32 + 17 * t + 6 * g, 21 + 8 * t]; }
          else { const t = fbm(pos[0] * 9, pos[1] * 9, pos[2] * 9); c = [112 + 10 * t, 112 + 10 * t, 108 + 10 * t]; }
          col.set(c.map(Math.round), k * 3);
        }
      }
    }
  }
  for (let L = 1; L <= LEVELS; L++) {
    const base = Buffer.alloc(WEAR * WEAR * 4), spec = Buffer.alloc(WEAR * WEAR * 4), nm = Buffer.alloc(WEAR * WEAR * 4);
    for (let k = 0; k < WEAR * WEAR; k++) {
      const on = onset[k] <= L / LEVELS;
      base.set([col[k * 3], col[k * 3 + 1], col[k * 3 + 2], on ? 255 : 0], k * 4);
      spec.set(isRust[k] ? [26, 18, 0, 255] : [62, 14, 0, 255], k * 4);
      nm.set([128, 128, isRust[k] ? 214 : 240, 255], k * 4);
      if (on && L === LEVELS) wearTexels++;
    }
    const files = [png(base, WEAR, WEAR), png(spec, WEAR, WEAR), png(nm, WEAR, WEAR)];
    ['', '_s', '_n'].forEach((k, i) => { OUT.push([path.join(TEX, `pickup_t15_wear_${L}${k}.png`), files[i]]); });
  }
  log('wear overlays');
}

// ---------------------------------------------------------------- per mesh: Free Model source, geo, sidecar
const SUMMARY = {};
for (const [key, m] of Object.entries(MESHES)) {
  const parts = ALL.filter(p => p.mesh === key);
  if (!parts.length) continue;
  const uv = UV[m.atlas], spec = ATLASES[m.atlas], ID = m.id;
  const uuid = s => { const h = createHash('sha256').update('afl-pickup-t15-v1:' + ID + ':' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const groups = [], outliner = [], elements = [], LAYERS = {};
  for (const bone of m.bones) {
    const node = {uuid: uuid('group:' + bone), isOpen: true, children: []};
    groups.push({name: bone, uuid: node.uuid, export: true, locked: false, scope: 0, selected: false, visibility: true, _static: {properties: {}, temp_data: {}},
      origin: pivotPx(bone).map(r12), rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false});
    outliner.push(node);
    for (const p of parts.filter(q => q.bone === bone)) {
      const k36 = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, fu = uv.faceUV.get(p);
      p.v.forEach((q, i) => { vertices[k36(i)] = q.map(r12); });
      p.f.forEach((f, fi) => { const u = fu.get(f); faces['f' + k36(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [k36(vid), u[j].map(r12)])), vertices: f.ids.map(k36), texture: 0}; });
      const id = uuid('mesh:' + p.name);
      elements.push({name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
        render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
      node.children.push(id);
      if (MATS[p.mat].alpha !== undefined) LAYERS[p.name] = 'translucent';
    }
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [6, 3, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: spec.size, height: spec.size},
    elements, groups, outliner,
    textures: [{name: spec.file + '.png', relative_path: `textures/${spec.file}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: spec.size, height: spec.size, uv_width: spec.size, uv_height: spec.size, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + ATLAS_PNG[m.atlas][0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: spec.size, texture_height: spec.size,
    visible_bounds_width: 6, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
    bones: m.bones.map(b => { const o = pivotPx(b); return {name: b, pivot: [-o[0] || 0, o[1], o[2]].map(r12)}; })}]};
  const flat = convert(source, geo, {}, ID + '.bbmodel', 2, null, LAYERS);
  const matOf = new Map(parts.map(p => [p.name, p.mat]));
  const sidecar = !CREASE ? flat : {...flat, format_version: 3, crease_angle: SMOOTHING.crease_deg,
    parts: flat.parts.map(q => SMOOTHING.materials[matOf.get(q.name)] === undefined ? q : {...q, crease_angle: SMOOTHING.materials[matOf.get(q.name)]})};
  const triEq = sidecar.parts.reduce((s, q) => s + q.faces.reduce((t, f) => t + f.length - 2, 0), 0), verts = sidecar.parts.reduce((s, q) => s + q.vertices.length, 0);
  assert(sidecar.parts.length <= 128 && triEq <= 16384 && verts <= 65536, `${ID} over the Vextris limits: ${sidecar.parts.length} parts, ${triEq} triangles, ${verts} vertices`);
  SUMMARY[ID] = {parts: sidecar.parts.length, triangles: triEq, vertices: verts};
  OUT.push([path.join(BB, ID + '.bbmodel'), JSON.stringify(source)]);
  OUT.push([path.join(ASSETS, `geo/${ID}.geo.json`), JSON.stringify(geo, null, 2) + '\n']);
  OUT.push([path.join(ASSETS, `meshes/${ID}.aflmesh.json`), serializeCompact(sidecar)]);
}

// ---------------------------------------------------------------- rig and smoothing source (for later Vextris v3 export and the vehicle code)
{
  const blk = q => toRuntime(q).map(v => r6(v / K)), dir = q => toRuntime(q).map(v => r6(v / K));
  const rig = {format: 'afl-vehicle-rig-v1', vehicle: 'pickup_t15', frame: 'runtime blocks: front -Z, right +X, up +Y; entity origin on the ground at the body centre',
    meshes: Object.fromEntries(Object.entries(MESHES).map(([k, m]) => [m.id, {atlas: ATLASES[m.atlas].file, bones: m.bones, parts: ALL.filter(p => p.mesh === k).map(p => p.name)}])),
    bones: Object.fromEntries(Object.keys(PIVOTS).map(b => [b, {parent: b === 'body' || b === 'wheel' ? null : 'body', pivot: blk(PIVOTS[b]),
      ...(HINGES[b] ? {axis: dir(HINGES[b].axis), open_deg: HINGES[b].open, note: HINGES[b].note} : {})}])),
    wheels: {mesh: 'pickup_t15_wheel', outboard: '+X in the wheel mesh; left wheels turned 180 deg about Y',
      hubs: Object.fromEntries(WHEELS.map(w => [w.n, {hub: blk([w.x, R, (w.left > 0 ? -1 : 1) * TR]), turned: w.left > 0, steers: w.x > 0}])),
      spare: {centre: blk([SPARE.x, SPARE.y, 0]), turn: 'Z -90 deg (outboard face down)'}},
    anchors: Object.fromEntries(Object.entries(ANCHORS).map(([n, [b, q]]) => [n, {bone: b, pos: blk(q)}])),
    smoothing: SMOOTHING};
  OUT.push([path.join(BB, 'pickup_t15.rig.json'), JSON.stringify(rig, null, 1) + String.fromCharCode(10)]);
}

// ---------------------------------------------------------------- checks
// hood: +X rotation by 58 deg about its pivot lifts the front edge; runtime orientation: driver side is -X
{
  const pv = pivotPx('hood').map(v => v / K), front = toRuntime([2.27, 1.0, 0]).map(v => v / K), a = 58 * Math.PI / 180;
  const y = front[1] - pv[1], z = front[2] - pv[2];
  assert(y * Math.cos(a) - z * Math.sin(a) + pv[1] > 1.8, 'hood rotation sign');
  assert(pivotPx('steering')[0] < 0, 'runtime orientation (steering wheel on the -X side)');
}

// ---------------------------------------------------------------- outputs
let stale = 0;
for (const [file, data] of OUT) {
  const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
  if (CHECK) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { stale++; console.log('stale: ' + path.relative(ROOT, file)); } }
  else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
}
console.log(JSON.stringify({meshes: SUMMARY, texelsPerPx: Object.fromEntries(Object.entries(UV).map(([a, u]) => [a, +u.S.toFixed(2)])), wearTexels, fast: FAST,
  pivots: Object.fromEntries(Object.entries(PIVOTS).map(([b, v]) => [b, toRuntime(v).map(q => r6(q / K))]))}, null, 1));
if (CHECK) { console.log(stale ? `${stale} stale output(s)` : 'all outputs up to date'); process.exit(stale ? 1 : 0); }
