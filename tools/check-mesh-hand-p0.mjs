// Offline check of the H1-P0 mesh hand export (design/items/mesh_hand_p0, docs/dev/mesh_hand_blender_geckolib_p0.md):
// rebuilds GeckoLib's bone walk from geo + animation JSON (linear keys; pivot and position x negated, rotation x and y
// negated, local = T(pivot + pos) Rz Ry Rx T(-pivot)) and compares it with Blender's own posed bones and skinned
// vertices (design/items/mesh_hand_p0/export/blender_ref.json). Also validates the Vextris sidecar limits and the skin.
//   node tools/check-mesh-hand-p0.mjs [assets root] [reference json]
// Key reduction keeps every bone within 0.05 deg of the bake, so a three-bone finger may drift up to ~0.15 deg.
import fs from 'node:fs';
const A = (process.argv[2] ?? 'src/main/resources/assets/apocalypse_firstlight') + '/';
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const geo = read(A + 'geo/dev/mesh_hand_p0.geo.json')['minecraft:geometry'][0];
const anim = Object.values(read(A + 'animations/dev/mesh_hand_p0.animation.json').animations)[0];
const mesh = read(A + 'meshes/dev/mesh_hand_p0.aflmesh.json');
const skin = read(A + 'skins/dev/mesh_hand_p0.skin.json');
const ref = read(process.argv[3] ?? 'design/items/mesh_hand_p0/export/blender_ref.json');
const D = Math.PI / 180;
const problems = [];

const mm = (a, b) => a.map(r => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const mv = (m, v) => m.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]);
function Rzyx([x, y, z]) {
    const [cx, sx, cy, sy, cz, sz] = [Math.cos(x * D), Math.sin(x * D), Math.cos(y * D), Math.sin(y * D), Math.cos(z * D), Math.sin(z * D)];
    return [[cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz], [cy * sz, sx * sy * sz + cx * cz, cx * sy * sz - sx * cz], [-sy, sx * cy, cx * cy]];
}
function sampler(ch) {
    if (!ch) return () => [0, 0, 0];
    const keys = Object.entries(ch).map(([t, v]) => [+t, v]).sort((a, b) => a[0] - b[0]);
    return t => {
        if (t <= keys[0][0]) return keys[0][1];
        if (t >= keys.at(-1)[0]) return keys.at(-1)[1];
        const i = keys.findIndex(k => k[0] > t) - 1, [ta, a] = keys[i], [tb, b] = keys[i + 1], u = (t - ta) / (tb - ta);
        return a.map((v, k) => v + (b[k] - v) * u);
    };
}
const bones = new Map(geo.bones.map(b => [b.name, b]));
const ch = Object.fromEntries(geo.bones.map(b => [b.name, {r: sampler(anim.bones[b.name]?.rotation), p: sampler(anim.bones[b.name]?.position)}]));
function frame(name, t, memo) {                       // world {R, o}, render px
    if (memo[name]) return memo[name];
    const b = bones.get(name);
    const parent = b.parent ? frame(b.parent, t, memo) : {R: [[1, 0, 0], [0, 1, 0], [0, 0, 1]], o: [0, 0, 0]};
    if (b.rotation) problems.push('bind rotation on ' + name);
    const piv = [-b.pivot[0], b.pivot[1], b.pivot[2]], r = ch[name].r(t), p = ch[name].p(t);
    const R = Rzyx([-r[0], -r[1], r[2]]), o = sub(add(piv, [-p[0], p[1], p[2]]), mv(R, piv));
    return memo[name] = {R: mm(parent.R, R), o: add(parent.o, mv(parent.R, o))};
}

// 1. bones vs Blender: D_ref = W(t) W_rest^-1
const inv4 = m => {   // rigid inverse of a 4x4 given as rows
    const R = [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i])), t = [m[0][3], m[1][3], m[2][3]];
    const o = mv(R, t).map(v => -v);
    return [[...R[0], o[0]], [...R[1], o[1]], [...R[2], o[2]], [0, 0, 0, 1]];
};
const mul4 = (a, b) => a.map(r => [0, 1, 2, 3].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j] + r[3] * b[3][j]));
let rotErr = 0, posErr = 0;
const FK = {};
for (const f of ref.frames) {
    const t = f / ref.fps, memo = {};
    FK[f] = {};
    for (const n of ref.bones) {
        const g = frame(n, t, memo), d = mul4(ref.bone_world_render_px[f][n], inv4(ref.bone_rest_render_px[n]));
        FK[f][n] = g;
        for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) rotErr = Math.max(rotErr, Math.abs(g.R[i][j] - d[i][j]));
        posErr = Math.max(posErr, ...[0, 1, 2].map(i => Math.abs(g.o[i] - d[i][3])));
    }
}
const rotDeg = Math.asin(Math.min(1, rotErr)) / D;

// 2. B: linear blend skinning with the FK bones vs Blender's skinned vertices
let skinErr = 0, wBad = 0;
for (const w of skin.weights) {
    const s = w.reduce((a, x) => a + x[1], 0);
    if (w.length > 4 || Math.abs(s - 1) > 2e-3) wBad++;
}
for (const f of ref.frames) {
    const posed = ref.skin.posed[String(f)];
    ref.skin.sample.forEach((vi, k) => {
        const v = skin.vertices[vi].slice(0, 3).map(c => c * 16);
        let out = [0, 0, 0];
        for (const [bi, w] of skin.weights[vi]) {
            const g = FK[f][skin.bones[bi]];
            out = add(out, add(mv(g.R, v), g.o).map(c => c * w));
        }
        skinErr = Math.max(skinErr, Math.hypot(...sub(out, posed[k].map(c => c * 16))));
    });
}

// 2b. A: pivot-local rigid pieces carried by the FK bones vs Blender's posed pieces
let rigidErr = 0, rigidSamples = 0;
for (const p of mesh.parts) {
    const r = ref.rigid?.[p.name];
    if (!r) { problems.push('no Blender reference for part ' + p.name); continue; }
    const b = bones.get(p.bone), piv = [-b.pivot[0], b.pivot[1], b.pivot[2]];
    for (const f of ref.frames) r.slots.forEach((si, k) => {
        const g = FK[f][p.bone], v = add(p.vertices[si].slice(0, 3).map(c => c * 16), piv);
        rigidErr = Math.max(rigidErr, Math.hypot(...sub(add(mv(g.R, v), g.o), r.posed[String(f)][k].map(c => c * 16))));
        rigidSamples++;
    });
}

// 3. Vextris sidecar limits and pairing
const tris = mesh.parts.reduce((a, p) => a + p.faces.reduce((b, f) => b + f.length - 2, 0), 0);
const verts = mesh.parts.reduce((a, p) => a + p.vertices.length, 0);
if (mesh.parts.length > 128) problems.push('more than 128 parts');
if (verts > 65536) problems.push('more than 65536 vertices');
if (tris > 16384) problems.push('more than 16384 triangles');
if (mesh.texture_size[0] !== geo.description.texture_width || mesh.texture_size[1] !== geo.description.texture_height) problems.push('texture size differs from geo');
for (const p of mesh.parts) {
    if (!bones.has(p.bone)) problems.push('part on unknown bone ' + p.bone);
    for (const v of p.vertices) if (v[3] < 0 || v[3] > 1 || v[4] < 0 || v[4] > 1) { problems.push('uv out of range in ' + p.name); break; }
    for (const f of p.faces) if (f.length < 3 || f.length > 4 || f.some(i => i >= p.vertices.length)) { problems.push('bad face in ' + p.name); break; }
}
if (skin.bones.some(b => !bones.has(b))) problems.push('skin bone missing from geo');
if (skin.triangles.some(t => t.some(i => i >= skin.vertices.length))) problems.push('skin triangle index out of range');

const ok = rotDeg < 0.2 && posErr < 0.02 && skinErr < 0.03 && rigidErr < 0.03 && wBad === 0 && problems.length === 0;
console.log(JSON.stringify({
    bones: geo.bones.length, animated: Object.keys(anim.bones).length, length_s: anim.animation_length,
    keys: Object.values(anim.bones).reduce((a, b) => a + Object.values(b).reduce((c, d) => c + Object.keys(d).length, 0), 0),
    fk_vs_blender: {rotation_deg: +rotDeg.toFixed(4), position_px: +posErr.toFixed(5)},
    skin_vs_blender_px: +skinErr.toFixed(5), skin_samples: ref.skin.sample.length * ref.frames.length, skin_weight_problems: wBad,
    rigid_vs_blender_px: +rigidErr.toFixed(5), rigid_samples: rigidSamples,
    rigid: {parts: mesh.parts.length, vertices: verts, triangles: tris},
    skinned: {vertices: skin.vertices.length, triangles: skin.triangles.length},
    problems, result: ok ? 'PASS' : 'FAIL'}, null, 1));
process.exitCode = ok ? 0 : 1;
