// AFL Material Meshes V1: five Material System V1 items as Pure Mesh, drawn by AflStaticMeshItemRenderer in every view
// (inventory, hand, ground, item frame). One bone, one 256 LabPBR atlas (Base Color / _s / _n) per item.
//   node tools/build-material-meshes-v1.mjs            -> writes sources, atlases, geo, AFL mesh sidecars and item models
//   node tools/build-material-meshes-v1.mjs --check    -> verifies every output is up to date
// Frame (px): mesh centred on X / Z, resting on y = 0; the item renderer's vertical offset (0.5 - height / 32) centres it.
// Items are authored at icon size (longest side 9-13 px), not real-world size, so they read like other held items.
// Materials follow the AFL rules: plain surfaces, no painted marks, no noise; metals are metal only where they really are
// bare metal, and stay low-smoothness so they do not glare under shaders.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {convert, serializeCompact} from './export-afl-mesh.mjs';
import {Part, extrude, area2, unwrap, paint, png, zFightLevels, add, sub, mul, dot, cross, norm, newell} from './cube-slab-mesh-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BB = path.join(ROOT, 'src/main/blockbench'), ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const ATLAS = 256, PAD = 2;
function assert(c, m) { if (!c) throw new Error(m); }
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const circle = (cu, cv, r, n, phase = Math.PI / n) => Array.from({length: n}, (_, i) => [cu + r * Math.cos(phase + 2 * Math.PI * i / n), cv + r * Math.sin(phase + 2 * Math.PI * i / n)]);
function roundRect(u0, v0, u1, v1, r, seg = 3) {
  const out = [];
  for (const [cu, cv, a0] of [[u1 - r, v0 + r, -90], [u1 - r, v1 - r, 0], [u0 + r, v1 - r, 90], [u0 + r, v0 + r, 180]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); }
  return out;
}
const rotY = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c]; };
const rotX = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c]; };
const move = d => p => add(p, d);
const transform = (part, ...fns) => { part.v = part.v.map(p => fns.reduce((q, f) => f(q), p)); };

// ---------------------------------------------------------------- items

/** Steel billet: continuous-cast square billet, rounded corners, dark mill scale; torch-cut ends in bare steel. */
function steelBillet() {
  const bar = new Part('billet', 'steel_billet', 'scale');
  extrude(bar, 'x', {outer: orient(roundRect(-1.8, 0, 1.8, 3.6, 0.55), true), holes: []}, -6.5, 6.5, 0.2);
  for (const f of bar.f) if (f.tag === 'cap' || f.tag === 'bevel') f.mat = 'cut';
  return {PARTS: [bar], MATS: {
    scale: {c: [60, 63, 69], hl: 10, sm: 72, se: 100, f0: 30},        // iron-oxide mill scale: dielectric, matte
    cut:   {c: [146, 144, 140], hl: 10, sm: 128, se: 150, f0: 230},   // LabPBR iron
  }};
}

/** Lead brick: 1 : 2 : 4 interlocking shielding brick, male chevron at one end and the matching notch at the other. */
function leadBrick() {
  const brick = new Part('brick', 'lead_brick', 'lead');
  // plan outline in (z, x): notch end at +x, point at -x
  const plan = [[-3, -3], [-3, 6], [0, 3], [3, 6], [3, -3], [0, -6]];
  extrude(brick, 'y', {outer: orient(plan, true), holes: []}, 0, 3, 0.25);
  return {PARTS: [brick], MATS: {
    lead: {c: [112, 115, 121], hl: 6, sm: 58, se: 86, f0: 235},       // LabPBR lead, neutral grey (not the old purple)
  }};
}

/** Electrolytic nickel: one sheared cathode square, thick and flat. */
function electrolyticNickel() {
  const square = new Part('square', 'electrolytic_nickel', 'nickel');
  extrude(square, 'y', {outer: orient(roundRect(-3, -3, 3, 3, 0.3, 2), true), holes: []}, 0, 1.8, 0.2);
  return {PARTS: [square], MATS: {
    nickel: {c: [160, 161, 158], hl: 14, sm: 104, se: 132, f0: 255},  // nodular cathode: metal, matte
  }};
}

/** Cemented carbide blank: as-sintered round rod blank with two coolant holes, chamfered ends. */
function cementedCarbideBlank() {
  const rod = new Part('rod', 'cemented_carbide_blank', 'carbide');
  const holes = [-0.75, 0.75].map(u => orient(circle(u, 1.8, 0.3, 8), false));
  extrude(rod, 'x', {outer: orient(circle(0, 1.8, 1.8, 20), true), holes}, -6, 6, 0.25);
  return {PARTS: [rod], MATS: {
    carbide: {c: [104, 106, 112], hl: 10, sm: 84, se: 118, f0: 255},  // WC-Co: dark metal, as-sintered matte
  }};
}

/**
 * Tungsten filament: a five-turn coil on two support leads, one continuous wire swept as a 6-sided tube. Thick enough
 * (0.84 px) to stay visible in the inventory. The tube gets its own UV: one strip along the wire (uniform material).
 */
function tungstenFilament() {
  const R = 1.35, yc = 5.8, x0 = -3.4, x1 = 3.4, turns = 5, perTurn = 12, lx = 4.2, r = 0.42, sides = 6;
  const path = [[-lx, 0, 0], [-lx, 2.0, 0], [-lx, 4.0, 0]];
  const coil0 = path.length;
  for (let i = 0; i <= turns * perTurn; i++) {
    const t = Math.PI + 2 * Math.PI * i / perTurn, k = i / (turns * perTurn);
    path.push([x0 + (x1 - x0) * k, yc + R * Math.cos(t), R * Math.sin(t)]);
  }
  const coil1 = path.length - 1;
  path.push([lx, 4.0, 0], [lx, 2.0, 0], [lx, 0, 0]);
  const isLead = path.map((_, i) => i < coil0 || i > coil1);
  const wire = new Part('wire', 'tungsten_filament', 'tungsten');
  const T = path.map((p, i) => norm(sub(path[Math.min(i + 1, path.length - 1)], path[Math.max(i - 1, 0)])));
  let n = norm(cross(T[0], [0, 0, 1]));
  const rings = [], arc = [0];
  for (let i = 0; i < path.length; i++) {
    if (i) { n = norm(sub(n, mul(T[i], dot(n, T[i])))); arc.push(arc[i - 1] + Math.hypot(...sub(path[i], path[i - 1]))); }
    const b = cross(T[i], n);
    rings.push(Array.from({length: sides + 1}, (_, j) => { const a = 2 * Math.PI * j / sides; return wire.vtx(add(path[i], add(mul(n, r * Math.cos(a)), mul(b, r * Math.sin(a))))); }));
  }
  const tex = new Map(), total = arc[arc.length - 1], H = 14;
  const U = s => PAD + 0.5 + (s / total) * (ATLAS - 2 * PAD - 1), V = j => PAD + 0.5 + (j / sides) * (H - 1);
  rings.forEach((ring, i) => ring.forEach((id, j) => tex.set(id, [U(arc[i]), V(j)])));
  for (let i = 0; i + 1 < path.length; i++) for (let j = 0; j < sides; j++) {
    const ids = [rings[i][j], rings[i][j + 1], rings[i + 1][j + 1], rings[i + 1][j]];
    const mid = mul(ids.reduce((s, id) => add(s, wire.v[id]), [0, 0, 0]), 0.25), axis = mul(add(path[i], path[i + 1]), 0.5);
    // triangles, not quads: along the tight coil a ring-to-ring quad can twist enough to self-intersect
    for (const tri of [[0, 1, 2], [0, 2, 3]]) wire.face(tri.map(k => ids[k]), sub(mid, axis), 'side', isLead[i] && isLead[i + 1] ? 'moly' : 'tungsten');
  }
  for (const [i, sgn] of [[0, -1], [path.length - 1, 1]]) {
    const c = wire.vtx(path[i]); tex.set(c, [U(arc[i]), V(sides / 2)]);
    for (let j = 0; j < sides; j++) wire.face([c, rings[i][j], rings[i][j + 1]], mul(T[i], sgn), 'cap', 'moly');
  }
  // one island: the whole wire strip
  const island = {part: wire, faces: wire.f.map(f => ({f, n: norm(newell(f.ids.map(id => wire.v[id])))})), px: PAD, py: PAD, W: ATLAS - 2 * PAD, H, tex};
  const faceUV = new Map([[wire, new Map(wire.f.map(f => [f, f.ids.map(id => tex.get(id))]))]]);
  return {PARTS: [wire], MATS: {
    tungsten: {c: [178, 179, 184], hl: 6, sm: 150, se: 170, f0: 255},  // drawn tungsten wire
    moly:     {c: [150, 150, 147], hl: 6, sm: 128, se: 150, f0: 255},  // molybdenum support leads
  }, uv: {islands: [island], S: 8, uvOf: (is, id) => is.tex.get(id), faceUV}};
}

// ---------------------------------------------------------------- bake

const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
const r12 = v => +v.toFixed(12) || 0, r3 = v => +v.toFixed(3) || 0, r6 = v => +v.toFixed(6) || 0;
const R3 = ([ax, ay, az]) => {   // Minecraft ItemTransform rotation: rotationXYZ, applied to a vector as Rx * Ry * Rz
  const rx = rotX(ax), ry = rotY(ay), rz = deg => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return p => [p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]]; };
  const z = rz(az); return p => rx(ry(z(p)));
};

// one inventory view for every material: vanilla block-item angle, no roll, so the slots line up
const GUI = [30, 225, 0];

function bake(id, {PARTS, MATS, uv, gui = GUI}) {
  // centre on X / Z, rest on y = 0
  const all = PARTS.flatMap(p => p.v), lo = [0, 1, 2].map(k => Math.min(...all.map(q => q[k]))), hi = [0, 1, 2].map(k => Math.max(...all.map(q => q[k])));
  const shift = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2];
  for (const p of PARTS) transform(p, move(shift));
  const size = sub(hi, lo);

  const UV = uv ?? unwrap(PARTS, {atlas: ATLAS, pad: PAD, startS: 40, stepS: 0.25});
  for (const p of PARTS) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), 'unmapped face in ' + id + '/' + p.name);
  const main = MATS[PARTS[0].f[0].mat];
  const painted = paint({PARTS, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas: ATLAS, pad: PAD, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [...main.c, 255], s: [main.sm, main.f0, 0, 255], n: [128, 128, 255, 255]}});

  const uuid = s => { const h = createHash('sha256').update(`afl-material-mesh-${id}:` + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const elements = PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const u = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((vid, j) => [key(vid), u[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  const atlasName = id + '_mesh';
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: id, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
    elements, groups: [{name: id, uuid: uuid('group'), export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}],
    outliner: [{uuid: uuid('group'), isOpen: true, children: elements.map(e => e.uuid)}],
    textures: [{name: atlasName + '.png', relative_path: `textures/${atlasName}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + painted.PNG[0].toString('base64')}],
    animations: []};
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + id, texture_width: ATLAS, texture_height: ATLAS,
    visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]}, bones: [{name: id, pivot: [0, 0, 0]}]}]};
  const sidecar = convert(source, geo, {}, id + '.bbmodel', 2);

  // display: GUI fitted to 14 of 16 slot pixels from the projected mesh; the other views share one size rule
  // (longest side normalised to 12 px) so every material is held at the same apparent size
  const centred = PARTS.flatMap(p => p.v).map(q => [q[0], q[1] - size[1] / 2, q[2]]), rot = R3(gui);
  const pr = centred.map(rot), px = pr.map(q => q[0]), py = pr.map(q => q[1]);
  const w = Math.max(...px) - Math.min(...px), h = Math.max(...py) - Math.min(...py), s = r3(14 / Math.max(w, h));
  const cx = (Math.max(...px) + Math.min(...px)) / 2, cy = (Math.max(...py) + Math.min(...py)) / 2, k = 12 / Math.max(...size);
  const S3 = v => [r3(v), r3(v), r3(v)];
  const display = {
    thirdperson_righthand: {rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: S3(0.42 * k)},
    firstperson_righthand: {rotation: [0, 45, 0], translation: [0, 1, 0], scale: S3(0.5 * k)},
    ground: {rotation: [0, 0, 0], translation: [0, 3, 0], scale: S3(0.4 * k)},
    gui: {rotation: gui, translation: [r3(-s * cx), r3(-s * cy), 0], scale: S3(s)},
    fixed: {rotation: [0, 0, 0], translation: [0, 0, 0], scale: S3(0.7 * k)},
  };
  const itemModel = {parent: 'builtin/entity', textures: {particle: `apocalypse_firstlight:item/${atlasName}`}, display};
  const offset = r6(0.5 - size[1] / 32);

  const outputs = [
    [path.join(BB, id + '.bbmodel'), JSON.stringify(source)],
    [path.join(ASSETS, `geo/${id}.geo.json`), JSON.stringify(geo, null, 2) + '\n'],
    [path.join(ASSETS, `meshes/${id}.aflmesh.json`), serializeCompact(sidecar)],
    [path.join(ASSETS, `models/item/${id}.json`), JSON.stringify(itemModel, null, 2) + '\n'],
    ...['', '_s', '_n'].flatMap((x, i) => [[path.join(BB, `textures/${atlasName}${x}.png`), painted.PNG[i]], [path.join(ASSETS, `textures/item/${atlasName}${x}.png`), painted.PNG[i]]]),
  ];
  const zf = zFightLevels(PARTS, new Map());
  const stats = {id, triangles: sidecar.parts.flatMap(p => p.faces).reduce((t, q) => t + q.length - 2, 0), size: size.map(r3), texelsPerPx: r3(UV.S),
    verticalOffset: offset, gui: display.gui, coplanar: zf.unresolved.length};
  return {outputs, stats};
}

export const ITEMS = {
  steel_billet: steelBillet,
  lead_brick: leadBrick,
  electrolytic_nickel: electrolyticNickel,
  tungsten_filament: tungstenFilament,
  cemented_carbide_blank: cementedCarbideBlank,
};

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const check = process.argv.includes('--check');
  let stale = 0;
  for (const [id, build] of Object.entries(ITEMS)) {
    const {outputs, stats} = bake(id, build());
    console.log(JSON.stringify(stats));
    for (const [file, data] of outputs) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
      else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
    }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); }
}
