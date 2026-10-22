// Furniture Mesh V1 (docs/models/wood_furniture_v2.md "动画 Mesh", 2026-10-15): the wood furniture with moving parts (V1's
// kitchen and sink counters, V2's wardrobe, nightstand, storage chest and vintage chest freezer) exported from their
// cabin-kit geometry (tools/cabin/cabin-geo.mjs pieces) to the AFL Animated Block Mesh Runtime, the same path as the
// beverage cooler and the commercial chest freezer (tools/build-beverage-cooler-v2.mjs, build-chest-freezer-v2.mjs):
// a Free Model source, geo bones, the aflmesh sidecar (tools/export-afl-mesh.mjs), a block mesh profile with the 'open'
// channel, an item model fitted to the mesh, and the block model (static_mesh: resting parts in the chunk).
// Moving parts only (spec.part, 2026-10-15: the box stove's loading door, the emergency radio's crank): blocks whose
// resting pieces are cabin pieces drawn by the chunk with their neighbours (the stove's heat shield) keep them, and only the
// moving part is an animated mesh, drawn by the block entity renderer (the block model is no static_mesh, so the renderer
// draws every part); no item or block model is written, the block keeps its cabin ones.
// The look is kept: every polygon keeps the material texture and the planar projection the cabin kit gives it (1 block =
// 1 tile, split along tile lines), only now its tile is a cell of the asset's own atlas, the materials' 240 px colour,
// LabPBR specular and normal maps copied in with an 8 px wrapped gutter (no mip bleeding: the textures tile).
// Frame: cabin pieces are in blocks, the master cell 0..1, drawn facing north (front toward -z); source px = (x - 0.5,
// y, z - 0.5) * 16, the runtime origin the master cell's bottom centre. Rotations are right-handed, as cabin-geo's.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {convert, serializeCompact} from '../export-afl-mesh.mjs';
import {readPng, png} from '../cube-slab-mesh-lib.mjs';
import {baked} from '../cabin/cabin-geo.mjs';

const CELL = 256, GUT = 8, TILE = CELL - 2 * GUT;   // a material tile (240 px) and its gutter in the atlas
const r6 = v => +v.toFixed(6) || 0, r3 = v => +v.toFixed(3) || 0, r12 = v => +v.toFixed(12) || 0;
const toPx = ([x, y, z]) => [(x - 0.5) * 16, y * 16, (z - 0.5) * 16];

/**
 * One material's colour, specular and normal maps as RGBA buffers (TILE x TILE). An id may end in *r,g,b: its colour map
 * multiplied by that (the AFL workbench's maple top aged to amber, 2026-10-21); its specular and normal maps unchanged.
 */
function material(assets, id) {
  const [base, tint] = id.split('*'), k3 = tint ? tint.split(',').map(Number) : null;
  const [ns, p] = base.split(':');
  if (ns !== 'apocalypse_firstlight') throw new Error('furniture mesh: texture of another namespace ' + id);
  return ['', '_s', '_n'].map(k => {
    const file = path.join(assets, 'textures', p + k + '.png');
    if (!fs.existsSync(file)) throw new Error('furniture mesh: missing ' + path.relative(assets, file));
    const {w, h, bpp, px} = readPng(fs.readFileSync(file));
    if (w !== TILE || h !== TILE) throw new Error(`furniture mesh: ${id}${k} is ${w}x${h}, not ${TILE}`);
    let out = px;
    if (bpp !== 4) {
      out = Buffer.alloc(w * h * 4);
      for (let i = 0; i < w * h; i++) { out[i * 4] = px[i * 3]; out[i * 4 + 1] = px[i * 3 + 1]; out[i * 4 + 2] = px[i * 3 + 2]; out[i * 4 + 3] = 255; }
    }
    if (k3 && k === '') {
      out = Buffer.from(out);
      for (let i = 0; i < w * h; i++) for (let c = 0; c < 3; c++) out[i * 4 + c] = Math.min(255, Math.round(out[i * 4 + c] * k3[c]));
    }
    return out;
  });
}

/** Drops repeated and collinear corners (clipping leaves them; the converter rejects zero-area triangles). */
function cleaned(v, uv) {
  let pts = v.map((p, i) => ({p, t: uv[i]}));
  for (let changed = true; changed && pts.length > 3;) {
    changed = false;
    for (let i = 0; i < pts.length && pts.length > 3; i++) {
      const a = pts[(i + pts.length - 1) % pts.length].p, b = pts[i].p, c = pts[(i + 1) % pts.length].p;
      const e = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], f = [c[0] - b[0], c[1] - b[1], c[2] - b[2]];
      const x = [e[1] * f[2] - e[2] * f[1], e[2] * f[0] - e[0] * f[2], e[0] * f[1] - e[1] * f[0]];
      if (Math.hypot(...e) < 1e-6 || Math.hypot(...x) < 1e-9) { pts.splice(i, 1); changed = true; i--; }
    }
  }
  return pts;
}

/** A transform of the profile's (rotation degrees about the part's pivot, or translation) applied to a point at t. */
function moved(q, pivot, tr, t) {
  let [x, y, z] = [q[0] - pivot[0], q[1] - pivot[1], q[2] - pivot[2]];
  const rot = tr.rotation ?? [0, 0, 0], mv = tr.translation ?? [0, 0, 0];
  const turn = (a, b, deg) => { const r = deg * t * Math.PI / 180, c = Math.cos(r), s = Math.sin(r); return [a * c - b * s, a * s + b * c]; };
  // the renderer turns z, then y, then x (AflAnimatedBlockMeshRenderer#rotate): applied to the point innermost first
  if (rot[0]) [y, z] = turn(y, z, rot[0]);
  if (rot[1]) [z, x] = turn(z, x, rot[1]);
  if (rot[2]) [x, y] = turn(x, y, rot[2]);
  return [x + pivot[0] + mv[0] * t, y + pivot[1] + mv[1] * t, z + pivot[2] + mv[2] * t];
}

/**
 * One furniture asset. spec: {id, dir ('furniture'), texPrefix, particle, bones: [{name, pivot ([x, y, z] blocks, cabin
 * frame), pieces: [Piece], hidden (neverRender: not in the item icon)}], open: {ticks, easing, transforms: {bone:
 * {rotation: [x, y, z] degrees} | {translation: [x, y, z] blocks}}}}. Instead of open, animations: the profile's channels
 * as written (any name, wrap etc.). part: moving parts only (see above). lifts: {name: dy blocks}, one profile
 * <id>_<name>.json each with that origin (a block standing lower on a surface) instead of <id>.json.
 * Returns {outputs: [[file, data]], stats}.
 */
export function furnitureMesh(root, spec) {
  const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight'), bb = path.join(root, 'src/main/blockbench', spec.dir);
  const ID = spec.id, tex = t => t.includes(':') ? t : spec.texPrefix + t;
  // the polygons per bone, baked (tile-split, explicit 0..1 UVs in their material)
  const bones = spec.bones.map(b => ({...b, polys: b.pieces.flatMap(pc => baked(pc).polys)}));
  const mats = [...new Set(bones.flatMap(b => b.polys.map(p => tex(p.t))))].sort();
  const cols = Math.ceil(Math.sqrt(mats.length)), rows = Math.ceil(mats.length / cols), W = cols * CELL, H = rows * CELL;
  const cellOf = new Map(mats.map((m, i) => [m, [(i % cols) * CELL + GUT, Math.floor(i / cols) * CELL + GUT]]));
  // the atlas: each material in its cell, wrapped into the gutter
  const MAPS = [0, 1, 2].map(() => Buffer.alloc(W * H * 4));
  for (const m of mats) {
    const src = material(assets, m), [cx, cy] = cellOf.get(m);
    for (let k = 0; k < 3; k++) for (let y = -GUT; y < TILE + GUT; y++) for (let x = -GUT; x < TILE + GUT; x++) {
      const sx = (x + TILE) % TILE, sy = (y + TILE) % TILE, s = (sy * TILE + sx) * 4, d = ((cy + y) * W + cx + x) * 4;
      src[k].copy(MAPS[k], d, s, s + 4);
    }
  }
  const PNGS = MAPS.map(px => png(px, W, H));

  // source (Free Model): a group per bone at its pivot, one mesh element per bone and material
  const uuid = s => { const h = createHash('sha256').update('afl-furniture-mesh-v1:' + ID + ':' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const groups = [], outliner = [], elements = [];
  for (const b of bones) {
    const origin = toPx(b.pivot).map(r12), node = {uuid: uuid('group:' + b.name), isOpen: true, children: []};
    groups.push({name: b.name, uuid: node.uuid, export: true, locked: false, scope: 0, selected: false, visibility: true,
      _static: {properties: {}, temp_data: {}}, origin, rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true,
      mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false});
    outliner.push(node);
    for (const m of mats) {
      const polys = b.polys.filter(p => tex(p.t) === m);
      if (!polys.length) continue;
      const [cx, cy] = cellOf.get(m), key = i => i.toString(36).padStart(5, '0'), vertices = {}, faces = {};
      let nv = 0, nf = 0;
      for (const p of polys) {
        const pts = cleaned(p.v, p.uv);
        if (pts.length < 3) continue;
        const ids = pts.map(({p: q}) => { const k = key(nv++); vertices[k] = toPx(q).map(r12); return k; });
        faces['f' + key(nf++)] = {uv: Object.fromEntries(pts.map(({t}, j) => [ids[j], [r12(cx + t[0] * TILE), r12(cy + t[1] * TILE)]])), vertices: ids, texture: 0};
      }
      const name = `${b.name}_${m.slice(m.lastIndexOf('/') + 1).replace(/\*.*/, '_tinted')}`, id = uuid('mesh:' + name);
      elements.push({name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
        render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: id});
      node.children.push(id);
    }
  }
  const source = {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: ID, model_identifier: '', visible_box: [2, 2, 0],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: W, height: H},
    elements, groups, outliner,
    textures: [{name: ID + '.png', relative_path: `textures/${ID}.png`, folder: '', namespace: '', id: '0', group: '', scope: 0,
      width: W, height: H, uv_width: W, uv_height: H, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
      file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
      source: 'data:image/png;base64,' + PNGS[0].toString('base64')}],
    animations: []};

  // runtime: geo bones (the exporter's X sign), sidecar, profile
  const geo = {format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: 'geometry.' + ID, texture_width: W, texture_height: H,
    visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0]},
    bones: bones.map(b => { const o = toPx(b.pivot); return {name: b.name, pivot: [-o[0] || 0, o[1], o[2]].map(r12), ...(b.hidden ? {neverRender: true} : {})}; })}]};
  const sidecar = convert(source, geo, {}, ID + '.bbmodel', 2, null, {});
  const meshText = serializeCompact(sidecar);
  const profilePivot = b => [b.pivot[0] - 0.5, b.pivot[1], b.pivot[2] - 0.5].map(r6);
  const animations = spec.animations ?? {open: {duration_ticks: spec.open.ticks, easing: spec.open.easing ?? 'ease_in_out', transforms: spec.open.transforms}};
  const transformOf = name => Object.values(animations).map(a => a.transforms[name]).find(Boolean);
  // bounds (cell frame, as the profile's): every pose the parts take, closed and open (eighths of a wrapping turn), 0.25 px out
  const pts = [], steps = Object.values(animations).some(a => a.wrap) ? [1, 2, 3, 4, 5, 6, 7].map(k => k / 8) : [0.5, 1];
  for (const b of bones) {
    const tr = transformOf(b.name);
    for (const p of b.polys) for (const q of p.v) { pts.push(q); if (tr) for (const t of steps) pts.push(moved(q, b.pivot, tr, t)); }
  }
  const m = 0.25 / 16, lo = [0, 1, 2].map(k => Math.min(...pts.map(q => q[k])) - m), hi = [0, 1, 2].map(k => Math.max(...pts.map(q => q[k])) + m);
  const profile = {format_version: 1, geometry: `apocalypse_firstlight:geo/${ID}.geo.json`,
    texture: `apocalypse_firstlight:textures/block/${spec.dir}/${ID}.png`, origin: [0, 0, 0], scale: [1, 1, 1], facing: 'horizontal',
    bounds: [...lo, ...hi].map(r6), parts: Object.fromEntries(bones.map(b => [b.name, {pivot: profilePivot(b)}])), animations};
  const profiles = spec.lifts
    ? Object.entries(spec.lifts).map(([name, dy]) => [`${ID}_${name}`, {...profile, origin: [0, r6(dy), 0],
      bounds: profile.bounds.map((v, i) => i % 3 === 1 ? r6(v + dy) : v)}])
    : [[ID, profile]];

  // item model: builtin/entity, every view centred on the closed mesh (as the chest freezer's)
  const itemDisplay = () => {
    const rot = ([ax, ay]) => q => { const a = ay * Math.PI / 180, b = ax * Math.PI / 180, x = q[0] * Math.cos(a) + q[2] * Math.sin(a), z = -q[0] * Math.sin(a) + q[2] * Math.cos(a);
      return [x, q[1] * Math.cos(b) - z * Math.sin(b), q[1] * Math.sin(b) + z * Math.cos(b)]; };
    const all = bones.filter(b => !b.hidden).flatMap(b => b.polys.flatMap(p => p.v)).map(toPx).map(q => [q[0], q[1] - 8, q[2]]), S3 = v => [v, v, v];
    const view = (r, fit, extra = [0, 0, 0]) => { const R = all.map(rot(r)), xs = R.map(q => q[0]), ys = R.map(q => q[1]), zs = R.map(q => q[2]);
      const s = typeof fit === 'number' && fit > 1 ? r3(fit / Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys))) : fit;
      const c = [(Math.max(...xs) + Math.min(...xs)) / 2, (Math.max(...ys) + Math.min(...ys)) / 2, (Math.max(...zs) + Math.min(...zs)) / 2];
      return {rotation: [r[0], r[1], 0], translation: [r3(-s * c[0] + extra[0]), r3(-s * c[1] + extra[1]), r3(-s * c[2] + extra[2])], scale: S3(s)}; };
    const size = Math.max(...[0, 1, 2].map(k => hi[k] - lo[k]));   // blocks: the bigger the piece, the smaller in hand
    const k = Math.min(1, 1.2 / size);
    return {gui: view([25, 225], 15), ground: view([0, 0], r3(0.25 * k), [0, 3, 0]), fixed: view([0, 0], r3(0.45 * k)),
      thirdperson_righthand: view([75, 225], r3(0.3 * k), [0, 2.5, 0]), firstperson_righthand: view([0, 225], r3(0.36 * k), [0, 1, 0])};
  };
  const itemModel = () => ({parent: 'builtin/entity', gui_light: 'side', textures: {particle: spec.particle}, display: itemDisplay()});
  const blockModel = {loader: 'apocalypse_firstlight:static_mesh', textures: {particle: spec.particle}};

  const j = v => JSON.stringify(v, null, 2) + '\n';
  const outputs = [[path.join(bb, ID + '.bbmodel'), JSON.stringify(source)], [path.join(assets, `geo/${ID}.geo.json`), j(geo)],
    [path.join(assets, `meshes/${ID}.aflmesh.json`), meshText],
    ...profiles.map(([name, p]) => [path.join(assets, `block_mesh_profiles/${name}.json`), j(p)]),
    ...(spec.part ? [] : [[path.join(assets, `models/item/${ID}.json`), j(itemModel())], [path.join(assets, `models/block/${spec.dir}/${ID}.json`), j(blockModel)]]),
    ...['', '_s', '_n'].flatMap((k, i) => [[path.join(bb, `textures/${ID}${k}.png`), PNGS[i]], [path.join(assets, `textures/block/${spec.dir}/${ID}${k}.png`), PNGS[i]]])];
  const faces = sidecar.parts.flatMap(p => p.faces);
  return {outputs, stats: {id: ID, atlas: [W, H], materials: mats.length, parts: sidecar.parts.length, faces: faces.length,
    triangles: faces.reduce((s, q) => s + q.length - 2, 0), bounds: profile.bounds}};
}
