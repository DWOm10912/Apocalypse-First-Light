// Shared builder for lathe-profiled visible ammo: a live round and a spent casing as pure Mesh, one Base Color atlas.
// Used by tools/build-9x19mm-ammo.mjs and tools/build-50ae-ammo.mjs. A calibre config supplies the profiles, the paint
// function and the output paths; this module builds the lathe meshes, packs the UV regions, rasterises the Base Color
// and writes the Free Model sources, geo files and AFL mesh sidecars (with --check it verifies them instead).
//
// Profiles: part.runs = [{region, map, pts}], pts = [r, y, zone-of-the-segment-ending-here]. A run is a lathe 'strip'
// (cylindrical unwrap: angle -> u, arc length -> v) or a 'disc' (planar top-down projection). The traversal direction
// fixes the outward side: normal = (dy, -dr) in (r, y). Zone changes run along texel rows in the strips, so every band
// edge is axis aligned (no stair-stepping).
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import {createHash} from 'node:crypto';
import {convert, serialize} from './export-afl-mesh.mjs';

export const sm = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
export const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t), sc = (c, k) => c.map(v => v * k);
export const hash = (a, b) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };

const sub = (a, b) => a.map((v, i) => v - b[i]), dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const arcLen = pts => pts.slice(1).reduce((s, p, i) => s + Math.hypot(p[0] - pts[i][0], p[1] - pts[i][1]), 0);

/**
 * cfg: {root, N, atlas, background, uuidSeed, models: {round|casing: {bone, parts}}, paint(model, zone, pos),
 *       sourceName(model), geoId(model), texture: {name, relativePath}, out: {round: {src, geo, mesh}, casing: {...},
 *       srcTexture, texture}}
 */
export function runLatheAmmo(cfg) {
  const {N, root} = cfg, ATLAS = cfg.atlas, PAD = 4, ang = k => 2 * Math.PI * k / N;
  const regions = new Map();   // name -> {map, w, h} in units (scaled later)
  for (const m of Object.values(cfg.models)) for (const part of m.parts) for (const run of part.runs) {
    const rMax = Math.max(...run.pts.map(p => p[0]));
    const need = run.map === 'strip' ? {w: 2 * Math.PI * rMax, h: arcLen(run.pts)} : {w: 2 * rMax, h: 2 * rMax};
    const g = regions.get(run.region);
    regions.set(run.region, {map: run.map, w: Math.max(g?.w || 0, need.w), h: Math.max(g?.h || 0, need.h)});
  }
  // shelf-pack regions into the atlas at the largest uniform texel density that fits
  function pack(s) {
    let x = PAD, y = PAD, rowH = 0;
    for (const [, g] of [...regions].sort((a, b) => b[1].h - a[1].h || a[0].localeCompare(b[0]))) {
      const W = Math.ceil(g.w * s), H = Math.ceil(g.h * s);
      if (x + W + PAD > ATLAS) { x = PAD; y += rowH + PAD; rowH = 0; }
      if (y + H + PAD > ATLAS) return false;
      Object.assign(g, {px: x, py: y, W, H}); x += W + PAD; rowH = Math.max(rowH, H);
    }
    return true;
  }
  let S = 300; while (!pack(S)) S -= 1;

  function buildPart(part) {
    const verts = [], keyOf = new Map(), faces = [];
    const vid = p => { const k = p.map(v => v.toFixed(6)).join(','); if (!keyOf.has(k)) { keyOf.set(k, verts.length); verts.push(p); } return keyOf.get(k); };
    const ring = (r, y) => r < 1e-9 ? [vid([0, y, 0])] : Array.from({length: N}, (_, k) => vid([r * Math.cos(ang(k)), y, r * Math.sin(ang(k))]));
    for (const run of part.runs) {
      const g = regions.get(run.region); let v0 = 0;
      const rings = run.pts.map(([r, y]) => ring(r, y));
      for (let i = 0; i + 1 < run.pts.length; i++) {
        const [ra, ya] = run.pts[i], [rb, yb, zone] = run.pts[i + 1], dr = rb - ra, dy = yb - ya, seg = Math.hypot(dr, dy);
        const A = rings[i], B = rings[i + 1];
        for (let k = 0; k < N; k++) {
          const k1 = k + 1, a = A.length === 1 ? null : [A[k], A[k1 % N]], b = B.length === 1 ? null : [B[k], B[k1 % N]];
          const ids = a && b ? [a[0], a[1], b[1], b[0]] : a ? [a[0], a[1], B[0]] : [A[0], b[1], b[0]];
          // UV per face corner: strips unwrap angle -> u, arc length -> v (seam face uses u = full width); discs project X/Z
          const uvOf = (id, onA, kk) => {
            if (run.map === 'disc') { const p = verts[id]; return [g.px + g.W / 2 + p[0] * S, g.py + g.H / 2 + p[2] * S]; }
            const pole = (onA ? A : B).length === 1;
            return [g.px + (pole ? k + 0.5 : kk) / N * g.W, g.py + (v0 + (onA ? 0 : seg)) * S];
          };
          const uvs = a && b ? [uvOf(a[0], true, k), uvOf(a[1], true, k1), uvOf(b[1], false, k1), uvOf(b[0], false, k)]
            : a ? [uvOf(a[0], true, k), uvOf(a[1], true, k1), uvOf(B[0], false, k)] : [uvOf(A[0], true, k), uvOf(b[1], false, k1), uvOf(b[0], false, k)];
          // outward side from the traversal direction: (dy, -dr) in (r, y), evaluated at the face's mid angle
          const am = ang(k + 0.5), want = [dy * Math.cos(am), -dr, dy * Math.sin(am)];
          const P = ids.map(id => verts[id]), n = cross(sub(P[1], P[0]), sub(P[2], P[0]));
          const f = {ids, uvs, zone, region: run.region};
          if (dot(n, want) < 0) { f.ids = ids.slice().reverse(); f.uvs = uvs.slice().reverse(); }
          faces.push(f);
        }
        v0 += seg;
      }
    }
    return {name: part.name, verts, faces};
  }
  const BUILT = Object.fromEntries(Object.entries(cfg.models).map(([k, m]) => [k, {bone: m.bone, parts: m.parts.map(buildPart)}]));

  // ---------------- Base Color: region padding, then every face rasterised with half-pixel overdraw ----------------
  const img = Buffer.alloc(ATLAS * ATLAS * 4);
  for (let i = 0; i < ATLAS * ATLAS; i++) img.set([...cfg.background, 255], i * 4);
  const put = (x, y, c) => { if (x >= 0 && y >= 0 && x < ATLAS && y < ATLAS) img.set([...c.map(v => Math.max(0, Math.min(255, Math.round(v)))), 255], (y * ATLAS + x) * 4); };
  for (const [model, m] of Object.entries(BUILT)) for (const part of m.parts) {
    for (const f of part.faces) {   // 2 px padding in the colour of the region's first face
      const g = regions.get(f.region); if (g.painted) continue; g.painted = true;
      const c = cfg.paint(model, f.zone, part.verts[f.ids[0]]);
      for (let y = g.py - 2; y < g.py + g.H + 2; y++) for (let x = g.px - 2; x < g.px + g.W + 2; x++) put(x, y, c);
    }
    for (const f of part.faces) for (let t = 1; t + 1 < f.ids.length; t++) {
      const T = [0, t, t + 1], A = T.map(i => f.uvs[i]), P3 = T.map(i => part.verts[f.ids[i]]);
      const den = (A[1][1] - A[2][1]) * (A[0][0] - A[2][0]) + (A[2][0] - A[1][0]) * (A[0][1] - A[2][1]);
      if (Math.abs(den) < 1e-9) continue;   // zero-area UV (a vertical primer wall seen top-down)
      const tol = -0.75 / Math.max(1, Math.sqrt(Math.abs(den)));
      const x0 = Math.floor(Math.min(...A.map(a => a[0])) - 1), x1 = Math.ceil(Math.max(...A.map(a => a[0])) + 1);
      const y0 = Math.floor(Math.min(...A.map(a => a[1])) - 1), y1 = Math.ceil(Math.max(...A.map(a => a[1])) + 1);
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const X = x + 0.5, Y = y + 0.5;
        const l1 = ((A[1][1] - A[2][1]) * (X - A[2][0]) + (A[2][0] - A[1][0]) * (Y - A[2][1])) / den;
        const l2 = ((A[2][1] - A[0][1]) * (X - A[2][0]) + (A[0][0] - A[2][0]) * (Y - A[2][1])) / den, l3 = 1 - l1 - l2;
        if (l1 < tol || l2 < tol || l3 < tol) continue;
        put(x, y, cfg.paint(model, f.zone, [0, 1, 2].map(k => P3[0][k] * l1 + P3[1][k] * l2 + P3[2][k] * l3)));
      }
    }
  }

  // ---------------- PNG, Blockbench sources, geo, sidecars ----------------
  const crcTable = Array.from({length: 256}, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc32 = buf => { let c = 0xFFFFFFFF; for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const encodePng = (rgba, w, h) => {
    const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); };
    const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
    const raw = Buffer.alloc(h * (w * 4 + 1)); for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, {level: 9})), chunk('IEND', Buffer.alloc(0))]);
  };
  const png = encodePng(img, ATLAS, ATLAS);
  const uuid = s => { const h = createHash('sha256').update(cfg.uuidSeed + ':' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };
  const bbmodel = model => {
    const m = BUILT[model], gid = uuid(model + ':group');
    const elements = m.parts.map(part => {
      const key = i => i.toString(36).padStart(4, '0'), vertices = {}, faces = {};
      part.verts.forEach((p, i) => { vertices[key(i)] = p.map(v => +v.toFixed(6)); });
      part.faces.forEach((f, i) => { faces['f' + key(i)] = {uv: Object.fromEntries(f.ids.map((id, j) => [key(id), f.uvs[j].map(v => +v.toFixed(4))])), vertices: f.ids.map(key), texture: 0}; });
      return {name: part.name, color: 0, origin: [0, 0, 0], rotation: [0, 0, 0], shading: 'flat', export: true, visibility: true, locked: false,
        render_order: 'default', scope: 0, allow_mirror_modeling: true, vertices, faces, type: 'mesh', uuid: uuid(model + ':' + part.name)};
    });
    return {meta: {format_version: '5.0', model_format: 'free', box_uv: false}, name: cfg.sourceName(model), model_identifier: '', visible_box: [1, 1, 0],
      variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, resolution: {width: ATLAS, height: ATLAS},
      elements, groups: [{name: m.bone, uuid: gid, export: true, locked: false, scope: 0, selected: false, visibility: true, _static: {properties: {}, temp_data: {}},
        origin: [0, 0, 0], rotation: [0, 0, 0], color: 0, children: [], reset: false, shade: true, mirror_uv: false, autouv: 0, isOpen: true, primary_selected: false}],
      outliner: [{uuid: gid, isOpen: true, children: elements.map(e => e.uuid)}],
      textures: [{name: cfg.texture.name, relative_path: cfg.texture.relativePath, folder: '', namespace: '', id: '0', group: '', scope: 0,
        width: ATLAS, height: ATLAS, uv_width: ATLAS, uv_height: ATLAS, particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '',
        file_format: 'png', render_mode: 'default', render_sides: 'auto', wrap_mode: 'limited', pbr_channel: 'color', fps: 7, frame_time: 1,
        frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: true, uuid: uuid('texture'),
        source: 'data:image/png;base64,' + png.toString('base64')}],
      animations: []};
  };
  const geo = (model, bone) => ({format_version: '1.12.0', 'minecraft:geometry': [{description: {identifier: cfg.geoId(model),
    texture_width: ATLAS, texture_height: ATLAS, visible_bounds_width: 2, visible_bounds_height: 2, visible_bounds_offset: [0, 0.5, 0]}, bones: [{name: bone, pivot: [0, 0, 0]}]}]});

  const outputs = [];
  for (const model of ['round', 'casing']) {
    const o = cfg.out[model], src = bbmodel(model), g = geo(model, BUILT[model].bone), sidecar = convert(src, g, {}, path.basename(o.src));
    outputs.push([o.src, JSON.stringify(src)], [o.geo, JSON.stringify(g, null, 2) + '\n'], [o.mesh, serialize(sidecar)]);
    const tri = sidecar.parts.reduce((s, p) => s + p.triangles.length, 0);
    const ys = sidecar.parts.flatMap(p => p.vertices.map(v => v[1] * 16));
    console.log(JSON.stringify({model, parts: sidecar.parts.map(p => `${p.name}:${p.triangles.length}`), triangles: tri, height: +(Math.max(...ys) - Math.min(...ys)).toFixed(5)}));
  }
  outputs.push([cfg.out.srcTexture, png], [cfg.out.texture, png]);
  if (process.argv.includes('--check')) {
    for (const [file, data] of outputs) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) throw new Error('stale ' + path.relative(root, file)); }
    console.log('CHECK OK');
  } else {
    for (const [file, data] of outputs) fs.writeFileSync(file, data);
    console.log(JSON.stringify({texelPerUnit: S, regions: Object.fromEntries([...regions].map(([k, g]) => [k, [g.px, g.py, g.W, g.H]])), wrote: outputs.map(([f]) => path.relative(root, f))}));
  }
}
