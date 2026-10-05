// Fluid Tank V2 (docs/models/fluid_tank_v2.md): the vertical fluid tank remade (2026-10-05). A square tank, dark steel
// corner posts, tempered glass on all four sides, a steel port deck on top and underneath (one AFL fluid port per cell,
// tools/afl-fluid-port.mjs sizes). Tanks that form a complete cuboid (FluidTankStructures) join into one: the cells'
// joined sides drop their glass and posts and the glass of the outer faces runs on to the cell boundary, so a joined
// tank reads as one glass box with posts on its outer corners and port decks over its top and under its bottom (user:
// joints as clear as possible). Each cell picks its pieces from its six joined flags (FluidTankBlock NORTH .. DOWN):
//   glass_<face>            a face not joined: the pane between the corner posts and the decks
//   glass_<face>_<e>        ... its strip toward a joined neighbour e (side or up / down), and the corner patch when two are
//   post_<corner>           both sides at a corner not joined: a round post between the decks;
//   post_<corner>_up / _down   ... carried on to the cell boundary when joined up / down
//   deck_top / deck_bottom  not joined up / down: the port deck plate, bore, throat and studs
// Pure Mesh + LabPBR atlas exported as Forge OBJ block models (static, chunk-baked); glass pieces are translucent.
//   node tools/build-fluid-tank-v2.mjs                -> writes source, OBJ / MTL / models, blockstate, atlas
//   node tools/build-fluid-tank-v2.mjs --check        -> verifies every output is up to date
//   node tools/build-fluid-tank-v2.mjs --preview DIR  -> writes only OBJ + maps into DIR (offline review)
// Frame (px): the cell's centre is the origin, north = -Z, east = +X. OBJ in block units: px / 16 + 0.5.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Part, extrude, add, sub, dot, cross, norm, newell, area2, unwrap, paint, png, readPng, zFightLevels} from './cube-slab-mesh-lib.mjs';
import {FLUID_PORT} from './afl-fluid-port.mjs';

// --heat-resistant: the Heat-Resistant Fluid Tank (2026-10-05, docs/models/heat_resistant_fluid_set_v1.md): the same tank in
// quartz glass, a refractory ceramic lining ring round every deck bore, an orange high-temperature band under the top deck
const HEAT = process.argv.includes('--heat-resistant');
const ID = HEAT ? 'heat_resistant_fluid_tank' : 'fluid_tank';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function assert(c, m) { if (!c) throw new Error(m); }
const D2R = Math.PI / 180;

const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const shape = (L, holes = []) => ({outer: orient(L, true), holes: holes.map(h => orient(h, false))});
const hex = (cu, cv, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * D2R; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });
const zx = L => L.map(([x, z]) => [z, x]);
const planY = (part, L, y0, y1, c = 0, holes = []) => extrude(part, 'y', shape(zx(L), holes.map(zx)), y0, y1, c);   // plan (x, z)
// a glass pane: only its two broad faces (edges always meet a post, a deck or the next cell's pane), as Fluid Pipe V2
function pane(part, a, b, k) {
  const lo = a.map((v, i) => Math.min(v, b[i])), hi = a.map((v, i) => Math.max(v, b[i])), [i, j] = [0, 1, 2].filter(x => x !== k);
  for (const [side, n] of [[lo[k], -1], [hi[k], 1]]) {
    const at = (u, v) => { const q = [0, 0, 0]; q[k] = side; q[i] = u; q[j] = v; return part.vtx(q); }, nn = [0, 0, 0]; nn[k] = n;
    part.face([at(lo[i], lo[j]), at(hi[i], lo[j]), at(hi[i], hi[j]), at(lo[i], hi[j])], nn, 'side');
  }
}
function cylY(part, cx, cz, r, y0, y1, seg, caps = [true, true]) {
  const ring = y => Array.from({length: seg}, (_, i) => { const a = 2 * Math.PI * (i + 0.5) / seg; return part.vtx([cx + r * Math.cos(a), y, cz + r * Math.sin(a)]); });
  const r0 = ring(y0), r1 = ring(y1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = 2 * Math.PI * (i + 1) / seg; part.face([r0[i], r0[j], r1[j], r1[i]], [Math.cos(m), 0, Math.sin(m)]); }
  [[r0, y0, -1], [r1, y1, 1]].forEach(([R, y, s], k) => { if (!caps[k]) return; const c = part.vtx([cx, y, cz]); for (let i = 0; i < seg; i++) part.face([c, R[i], R[(i + 1) % seg]], [0, s, 0], 'cap'); });
}

// ---------------- materials (as Fluid Pipe V2: near-black painted steel, the cooler's glass) ----------------
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
function vn(x, y, z) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), s = t => t * t * (3 - 2 * t), l = (a, b, t) => a + (b - a) * t;
  const sx = s(x - xi), sy = s(y - yi), sz = s(z - zi), h = (a, b, c) => hash(a, b, c * 7919 + 101);
  return l(l(l(h(xi, yi, zi), h(xi + 1, yi, zi), sx), l(h(xi, yi + 1, zi), h(xi + 1, yi + 1, zi), sx), sy),
    l(l(h(xi, yi, zi + 1), h(xi + 1, yi, zi + 1), sx), l(h(xi, yi + 1, zi + 1), h(xi + 1, yi + 1, zi + 1), sx), sy), sz);
}
const tone = (c, k) => c.map(v => v * k), mott = (p, s, k) => k * (vn(p[0] * s, p[1] * s, p[2] * s) - 0.5);
export const MATS = {
  post:   {c: p => tone([46, 49, 54], 1 + mott(p, 0.35, 0.04)), hl: 6, sm: 108, se: 128, f0: 24},
  deck:   {c: p => tone([62, 66, 72], 1 + mott(p, 0.3, 0.04)), hl: 10, sm: 120, se: 144, f0: 26},   // the AFL fluid port's plate steel
  throat: {c: [24, 25, 27], hl: 0, sm: 60, se: 60, f0: 20},
  stud:   {c: [124, 128, 133], hl: 7, sm: 124, se: 140, f0: 30},
  glass:  {c: [150, 186, 204], hl: 0, sm: 235, se: 235, f0: 10},   // alpha GLASS_ALPHA
  ...(HEAT ? {
    glass:   {c: [205, 226, 232], hl: 0, sm: 240, se: 240, f0: 10},                                      // fused quartz
    ceramic: {c: [194, 183, 160], hl: 6, sm: 46, se: 70, f0: 20},                                        // refractory lining
    band:    {c: p => tone([196, 108, 32], 1 + mott(p, 0.35, 0.04)), hl: 8, sm: 112, se: 136, f0: 24},   // high-temperature band
  } : {}),
};
const GLASS_ALPHA = HEAT ? 40 : 56;
export const LINING = 4.9;   // heat-resistant: the ceramic ring round each bore, out to this

// ---------------- dimensions (px) ----------------
export const TANK = {glass: [5.9, 6.1], post: [6.3, 1.55], deck: 7.4, throat: 0.5};
// FluidTankRenderGeometry: the fluid fills x / z inside the glass (+-5.8 on a side not joined) and y between the decks
export const FLUID = {side: 5.8, y: [-TANK.deck, TANK.deck]};
const FACES = {north: {axis: 2, s: -1, along: ['west', 'east']}, south: {axis: 2, s: 1, along: ['west', 'east']},
  west: {axis: 0, s: -1, along: ['north', 'south']}, east: {axis: 0, s: 1, along: ['north', 'south']}};
const SIGN = {west: -1, east: 1, north: -1, south: 1};
const CORNERS = {ne: ['north', 'east'], nw: ['north', 'west'], se: ['south', 'east'], sw: ['south', 'west']};

function glassPiece(P, face, ext) {   // ext: [] centre, or the joined neighbours this strip / patch reaches toward
  const F = FACES[face], [g0, g1] = TANK.glass, part = P(`glass_${face}${ext.length ? '_' + ext.join('_') : ''}`, 'glass');
  const ranges = {u: [-TANK.post[0], TANK.post[0]], y: [-TANK.deck, TANK.deck]};
  for (const e of ext) {
    if (e === 'up') ranges.y = [TANK.deck, 8]; else if (e === 'down') ranges.y = [-8, -TANK.deck];
    else ranges.u = SIGN[e] < 0 ? [-8, -TANK.post[0]] : [TANK.post[0], 8];
  }
  const w = F.s < 0 ? [-g1, -g0] : [g0, g1], a = [0, ranges.y[0], 0], b = [0, ranges.y[1], 0], u = F.axis === 2 ? 0 : 2;
  a[F.axis] = w[0]; b[F.axis] = w[1]; a[u] = ranges.u[0]; b[u] = ranges.u[1];
  pane(part, a, b, F.axis);
  return part;
}
function postPiece(P, corner, span) {   // span: 'mid' (between the decks), 'up' / 'down' (on to the cell boundary)
  const [ns, ew] = CORNERS[corner], x = SIGN[ew] * TANK.post[0], z = SIGN[ns] * TANK.post[0];
  const [y0, y1] = span === 'mid' ? [-TANK.deck, TANK.deck] : span === 'up' ? [TANK.deck, 8] : [-8, -TANK.deck];
  cylY(P(`post_${corner}${span === 'mid' ? '' : '_' + span}`, 'post'), x, z, TANK.post[1], y0, y1, 16, [span !== 'up', span !== 'down']);
}
function deckPiece(P, top) {   // the port deck: the whole cell's plate with the AFL bore, its throat and the four studs
  const s = top ? 1 : -1, name = top ? 'deck_top' : 'deck_bottom', b = FLUID_PORT.bore;
  const [p0, p1] = top ? [TANK.deck, 8] : [-8, -TANK.deck];
  const lb = HEAT ? LINING : b;
  planY(P(name + '_plate', 'deck'), rect(-8, -8, 8, 8), p0, p1, 0, [rect(-lb, -lb, lb, lb)]);
  if (HEAT) planY(P(name + '_lining', 'ceramic'), rect(-lb, -lb, lb, lb), p0, p1, 0, [rect(-b, -b, b, b)]);
  const [t0, t1] = top ? [TANK.deck - TANK.throat, TANK.deck + 0.02] : [-TANK.deck - 0.02, -TANK.deck + TANK.throat];
  planY(P(name + '_throat', 'throat'), rect(-b, -b, b, b), t0, t1, 0);
  const studs = P(name + '_studs', 'stud');
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) planY(studs, hex(sx * FLUID_PORT.bolt, sz * FLUID_PORT.bolt, FLUID_PORT.stud), top ? 8 : -8 - FLUID_PORT.studOut, top ? 8 + FLUID_PORT.studOut : -8, 0);
}

// pieces: model name -> {build, when (blockstate conditions), glass}
export const PIECES = {};
for (const face of Object.keys(FACES)) {
  const [a1, a2] = FACES[face].along;
  const variants = [[], [a1], [a2], ['up'], ['down'], [a1, 'up'], [a2, 'up'], [a1, 'down'], [a2, 'down']];
  for (const ext of variants) {
    const name = `glass_${face}${ext.length ? '_' + ext.join('_') : ''}`;
    PIECES[name] = {build: P => glassPiece(P, face, ext), when: {[face]: 'false', ...Object.fromEntries(ext.map(e => [e, 'true']))}, glass: true};
  }
}
for (const [corner, [ns, ew]] of Object.entries(CORNERS)) {
  PIECES[`post_${corner}`] = {build: P => postPiece(P, corner, 'mid'), when: {[ns]: 'false', [ew]: 'false'}};
  PIECES[`post_${corner}_up`] = {build: P => postPiece(P, corner, 'up'), when: {[ns]: 'false', [ew]: 'false', up: 'true'}};
  PIECES[`post_${corner}_down`] = {build: P => postPiece(P, corner, 'down'), when: {[ns]: 'false', [ew]: 'false', down: 'true'}};
}
PIECES.deck_top = {build: P => deckPiece(P, true), when: {up: 'false'}};
if (HEAT) for (const face of Object.keys(FACES)) PIECES['band_' + face] = {build: P => {
  const F = FACES[face], ew = F.axis === 0 ? 0.05 : 0, a = [0, 6.5 + ew, 0], b2 = [0, 7.3 - ew, 0], u = F.axis === 2 ? 0 : 2;   // east / west a hair thinner: no coplanar faces where two bands cross at a corner
  a[F.axis] = F.s * 6.12; b2[F.axis] = F.s * 6.42; a[u] = -8; b2[u] = 8;
  planY(P('band_' + face, 'band'), F.axis === 2 ? rect(a[0], Math.min(a[2], b2[2]), b2[0], Math.max(a[2], b2[2])) : rect(Math.min(a[0], b2[0]), a[2], Math.max(a[0], b2[0]), b2[2]), a[1], b2[1], 0);
}, when: {[face]: 'false', up: 'false'}};
PIECES.deck_bottom = {build: P => deckPiece(P, false), when: {down: 'false'}};
const ITEM = ['glass_north', 'glass_south', 'glass_west', 'glass_east', 'post_ne', 'post_nw', 'post_se', 'post_sw', 'deck_top', 'deck_bottom',
  ...(HEAT ? ['band_north', 'band_south', 'band_west', 'band_east'] : [])];

// ---------------- bake: UV, LabPBR maps (glass alpha) ----------------
const DUMMY = 'data:image/png;base64,' + png(Buffer.from([0, 0, 0, 255]), 1, 1).toString('base64');
function bake() {
  const PARTS = [];
  for (const [piece, def] of Object.entries(PIECES)) def.build((name, mat) => { const p = new Part(`${piece}__${name}`, piece, mat); PARTS.push(p); return p; });
  const live = PARTS.filter(p => p.f.length);
  for (const p of live) {   // Forge draws a quad as 0-1-2 / 2-3-0: split anything that is not a triangle or a strictly convex quad
    const out = [];
    for (const f of p.f) {
      const P3 = f.ids.map(i => p.v[i]), n = newell(P3);
      const convexQuad = f.ids.length === 4 && [0, 1, 2, 3].every(k => dot(cross(sub(P3[(k + 1) % 4], P3[k]), sub(P3[(k + 2) % 4], P3[(k + 1) % 4])), n) > 1e-12);
      if (f.ids.length === 3 || convexQuad) { out.push(f); continue; }
      assert(f.ids.length === 4, `${f.ids.length}-gon in ${p.name}`);
      const k = [0, 1, 2, 3].find(k => dot(cross(sub(P3[k], P3[(k + 3) % 4]), sub(P3[(k + 1) % 4], P3[k])), n) <= 1e-12) ?? 0;
      out.push({...f, ids: [f.ids[k], f.ids[(k + 1) % 4], f.ids[(k + 2) % 4]]}, {...f, ids: [f.ids[(k + 2) % 4], f.ids[(k + 3) % 4], f.ids[k]]});
    }
    p.f = out;
  }
  const atlas = 1024, UV = unwrap(live, {atlas, pad: 2, startS: 16, stepS: 0.25});
  for (const p of live) assert(p.f.every(f => UV.faceUV.get(p)?.has(f)), `unmapped face in ${p.name}`);
  const painted = paint({PARTS: live, islands: UV.islands, S: UV.S, uvOf: UV.uvOf, atlas, pad: 2, MATS, ZONED: new Set(), groupInfo: new Map(),
    sourceGroups: () => [], refTexture: {source: DUMMY}, refUvWidth: 1, background: {c: [46, 49, 54, 255], s: [108, 24, 0, 255], n: [128, 128, 255, 255]}});
  const maps = painted.PNG.map((buf, i) => {
    if (i !== 0) return buf;
    const img = readPng(buf), px = Buffer.from(img.px);
    for (const is of UV.islands.filter(is => is.part.mat === 'glass'))
      for (let y = is.py - 2; y < is.py + is.H + 2; y++) for (let x = is.px - 2; x < is.px + is.W + 2; x++) px[(y * img.w + x) * img.bpp + 3] = GLASS_ALPHA;
    return png(px, img.w, img.h);
  });
  // a full single cell and a joined pair's pieces must not overlap
  const coplanar = [Object.keys(PIECES).filter(k => !k.includes('_up') && !k.includes('_down') || k.startsWith('deck'))]
    .flatMap(g => zFightLevels(live.filter(p => g.includes(p.bone) && ITEM.includes(p.bone)), new Map()).unresolved);
  return {id: ID, PARTS: live, atlas, UV, maps, coplanar};
}

const uuidOf = (ns, s) => { const h = createHash('sha256').update(`${HEAT ? 'afl-heat-resistant-fluid-tank' : 'afl-fluid-tank-v2'}:${ns}:${s}`).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
const r12 = v => +v.toFixed(12) || 0;
function sourceOf(b) {   // editable Free Model source (frame as the header, px): one group per piece, the single tank shown
  const uuid = s => uuidOf(b.id, s), name = b.id + (HEAT ? '_v1' : '_v2'), bones = [...new Set(b.PARTS.map(p => p.bone))];
  const elements = b.PARTS.map(p => {
    const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {}, uvs = b.UV.faceUV.get(p);
    p.v.forEach((q, i) => { vertices[key(i)] = q.map(r12); });
    p.f.forEach((f, fi) => { const uv = uvs.get(f); faces['f' + key(fi)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), uv[j].map(r12)])), vertices: f.ids.map(key), texture: 0}; });
    return {name: p.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: ITEM.includes(p.bone), locked: false,
      render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid('mesh:' + p.name)};
  });
  return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name, model_identifier: '', visible_box: [1, 1, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: b.atlas, height: b.atlas},
    elements, groups: bones.map(bone => ({name: bone, uuid: uuid('group:' + bone), export: true, locked: false, scope: 0,
      selected: false, visibility: ITEM.includes(bone), _static: {properties: {}, temp_data: {}}, origin: [0, 0, 0], rotation: [0, 0, 0],
      color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false})),
    outliner: bones.map(bone => ({uuid: uuid('group:' + bone), isOpen: true, children: elements.filter((e, i) => b.PARTS[i].bone === bone).map(e => e.uuid)})),
    textures: [{name: name + '.png', relative_path: `textures/${name}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: b.atlas, height: b.atlas, uv_width: b.atlas, uv_height: b.atlas, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + b.maps[0].toString('base64')}],
    animations: []};
}

// ---------------- OBJ / MTL / models ----------------
const f6 = v => (+v.toFixed(6)).toString();
const SMOOTH = Math.cos(36 * D2R);   // the 16-sided posts shade round
function cornerNormals(V, F) {
  const fn = F.map(f => newell(f.ids.map(i => V[i]))), byV = new Map();
  F.forEach((f, k) => f.ids.forEach(i => (byV.get(i) || byV.set(i, []).get(i)).push(k)));
  return F.map((f, k) => { const n0 = norm(fn[k]); return f.ids.map(i => norm(byV.get(i).reduce((a, j) => dot(norm(fn[j]), n0) >= SMOOTH ? add(a, fn[j]) : a, [0, 0, 0]))); });
}
const toCell = q => q.map(v => v / 16 + 0.5);
function objOf(b, title, file, bones) {
  const out = [`# AFL ${title}, generated by tools/build-fluid-tank-v2.mjs`, `mtllib ${file.split('/').pop()}.mtl`];
  let vBase = 1, tBase = 1, nBase = 1;
  for (const p of b.PARTS) {
    if (!bones.includes(p.bone)) continue;
    const V = p.v.map(toCell), F = p.f;
    out.push(`o ${p.name}`, `usemtl ${b.id}`);
    for (const q of V) out.push(`v ${f6(q[0])} ${f6(q[1])} ${f6(q[2])}`);
    const uvs = b.UV.faceUV.get(p), vt = [], vn = [], fl = [], cn = cornerNormals(V, F), nIndex = new Map();
    F.forEach((f, k) => {
      const uv = uvs.get(f);
      fl.push('f ' + f.ids.map((id, j) => {
        const n = cn[k][j], key = n.map(f6).join(' ');
        if (!nIndex.has(key)) { nIndex.set(key, nBase + vn.length); vn.push('vn ' + key); }
        vt.push(`vt ${f6(uv[j][0] / b.atlas)} ${f6(1 - uv[j][1] / b.atlas)}`); return `${vBase + id}/${tBase + vt.length - 1}/${nIndex.get(key)}`;
      }).join(' '));
    });
    out.push(...vt, ...vn, ...fl);
    vBase += p.v.length; tBase += vt.length; nBase += vn.length;
  }
  return out.join('\n') + '\n';
}
const mtlOf = (b, title) => `# AFL ${title}\nnewmtl ${b.id}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/${b.id}\n`;
const objModel = (file, glass) => ({loader: 'forge:obj', model: `apocalypse_firstlight:models/block/${file}.obj`, automatic_culling: false,
  flip_v: true, shade_quads: true, ambientocclusion: false, render_type: glass ? 'minecraft:translucent' : 'minecraft:solid',
  textures: {particle: `apocalypse_firstlight:block/${ID}`}});

// ---------------- write ----------------
const B = bake();
const bbDir = path.join(ROOT, 'src/main/blockbench'), assets = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const json = v => JSON.stringify(v, null, 2) + '\n';
const outputs = [], objs = [];
const T = HEAT ? 'Heat-Resistant Fluid Tank' : 'Fluid Tank V2';
outputs.push([path.join(bbDir, `${ID}${HEAT ? '_v1' : '_v2'}.bbmodel`), JSON.stringify(sourceOf(B))],
  ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bbDir, `textures/${ID}${HEAT ? '_v1' : '_v2'}${k}.png`), B.maps[i]], [path.join(assets, `textures/block/${ID}${k}.png`), B.maps[i]]]));
for (const [name, def] of Object.entries(PIECES)) {
  const file = `${ID}/${name}`, obj = objOf(B, `${T} ${name}`, file, [name]);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, `${T} ${name}`)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, def.glass))]);
  objs.push([file, obj]);
}
{ // the item's shell (FluidTankItemRenderer draws the default state; this model is its particle / preview only)
  const file = `${ID}/item`, obj = objOf(B, `${T} single tank`, file, ITEM);
  outputs.push([path.join(assets, `models/block/${file}.obj`), obj], [path.join(assets, `models/block/${file}.mtl`), mtlOf(B, `${T} single tank`)],
    [path.join(assets, `models/block/${file}.json`), json(objModel(file, true))]);
  objs.push([file, obj]);
}
// FluidTankBlock: north / east / south / west / up / down = joined to that neighbour in one structure
outputs.push([path.join(assets, `blockstates/${ID}.json`), json({multipart: Object.entries(PIECES).map(([name, def]) =>
  ({when: def.when, apply: {model: `apocalypse_firstlight:block/${ID}/${name}`}}))})]);

const tris = bones => B.PARTS.filter(p => bones.includes(p.bone)).reduce((s, p) => s + p.f.reduce((t, f) => t + f.ids.length - 2, 0), 0);
export const stats = {pieces: Object.keys(PIECES).length, singleTankTriangles: tris(ITEM), atlas: {size: B.atlas, texelsPerPx: B.UV.S, islands: B.UV.islands.length, coplanar: B.coplanar.length}};
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  console.log(JSON.stringify(stats));
  if (B.coplanar.length) console.log('COPLANAR', JSON.stringify([...new Set(B.coplanar.map(c => c.a + ' | ' + c.b))].slice(0, 12)));
  const pi = process.argv.indexOf('--preview');
  if (pi > 0) {
    const dir = process.argv[pi + 1]; fs.mkdirSync(dir, {recursive: true});
    for (const [file, obj] of objs) fs.writeFileSync(path.join(dir, file.replace('/', '_') + '.obj'), obj);
    ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${B.id}${k}.png`), B.maps[i]));
    console.log('preview written to ' + dir);
  } else if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(ROOT, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file] of outputs) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log('wrote ' + outputs.length + ' files');
  }
}
