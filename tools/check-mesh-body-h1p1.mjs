// Offline check of the H1-P1 survivor body export (design/characters/survivor_base_h1p1, docs/dev/mesh_survivor_base_body_h1p1.md):
// rebuilds GeckoLib's bone walk from geo + animation JSON for every clip (linear keys; pivot and position x negated,
// rotation x and y negated, local = T(pivot + pos) Rz Ry Rx T(-pivot)) and compares it with Blender's posed bones and
// skinned vertices (design/characters/survivor_base_h1p1/export/survivor_base_h1p1_ref.json). Also checks the skin
// weights, the first-person arm mask, that the P9-01 carried by hand_r keeps its sight line through the eye in fp_ads and
// at the end of fp_raise, that the clips meet without a jump (raise / fire start and end on hip or ADS), and fp_view.
//   node tools/check-mesh-body-h1p1.mjs [assets root] [reference json]
import fs from 'node:fs';
const A = (process.argv[2] ?? 'src/main/resources/assets/apocalypse_firstlight') + '/';
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const geo = read(A + 'geo/dev/survivor_base_h1p1.geo.json')['minecraft:geometry'][0];
const anims = read(A + 'animations/dev/survivor_base_h1p1.animation.json').animations;
const skin = read(A + 'skins/dev/survivor_base_h1p1.skin.json');
const ref = read(process.argv[3] ?? 'design/characters/survivor_base_h1p1/export/survivor_base_h1p1_ref.json');
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
function walker(anim) {
    const ch = Object.fromEntries(geo.bones.map(b => [b.name, {r: sampler(anim.bones[b.name]?.rotation), p: sampler(anim.bones[b.name]?.position)}]));
    return function frame(name, t, memo) {                // deformation {R, o}, render px
        if (memo[name]) return memo[name];
        const b = bones.get(name);
        const parent = b.parent ? frame(b.parent, t, memo) : {R: [[1, 0, 0], [0, 1, 0], [0, 0, 1]], o: [0, 0, 0]};
        if (b.rotation) problems.push('bind rotation on ' + name);
        const piv = [-b.pivot[0], b.pivot[1], b.pivot[2]], r = ch[name].r(t), p = ch[name].p(t);
        const R = Rzyx([-r[0], -r[1], r[2]]), o = sub(add(piv, [-p[0], p[1], p[2]]), mv(R, piv));
        return memo[name] = {R: mm(parent.R, R), o: add(parent.o, mv(parent.R, o))};
    };
}
const inv4 = m => {
    const R = [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i])), t = [m[0][3], m[1][3], m[2][3]];
    const o = mv(R, t).map(v => -v);
    return [[...R[0], o[0]], [...R[1], o[1]], [...R[2], o[2]], [0, 0, 0, 1]];
};
const mul4 = (a, b) => a.map(r => [0, 1, 2, 3].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j] + r[3] * b[3][j]));

// 1 + 2. bones and skin vs Blender, per clip and reference frame (render px = metres x 16)
let rotErr = 0, posErr = 0, skinErr = 0, samples = 0;
const FK = {};
for (const [clip, r] of Object.entries(ref.clips)) {
    const anim = anims[clip];
    if (!anim) { problems.push('clip missing from animation file: ' + clip); continue; }
    const frame = walker(anim);
    FK[clip] = {};
    for (const f of r.frames) {
        const t = (f - r.frames[0]) / ref.fps, memo = {};
        const fk = FK[clip][f] = {};
        for (const n of ref.bones) {
            const g = fk[n] = frame(n, t, memo), d = mul4(r.bone_world_render_px[f][n], inv4(ref.bone_rest_render_px[n]));
            for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) rotErr = Math.max(rotErr, Math.abs(g.R[i][j] - d[i][j]));
            posErr = Math.max(posErr, ...[0, 1, 2].map(i => Math.abs(g.o[i] - d[i][3])));
        }
        const posed = r.skin_posed[String(f)];
        ref.skin_sample.forEach((vi, k) => {
            const v = skin.vertices[vi].slice(0, 3).map(c => c * 16);
            let out = [0, 0, 0];
            for (const [bi, w] of skin.weights[vi]) {
                const g = fk[skin.bones[bi]];
                out = add(out, add(mv(g.R, v), g.o).map(c => c * w));
            }
            skinErr = Math.max(skinErr, Math.hypot(...sub(out, posed[k].map(c => c * 16))));
            samples++;
        });
    }
}
const rotDeg = Math.asin(Math.min(1, rotErr)) / D;

// 3. weights and the first-person mask (triangles whose three vertices are >= 0.5 arm / hand weight)
let wBad = 0;
for (const w of skin.weights) {
    const s = w.reduce((a, x) => a + x[1], 0);
    if (w.length > 4 || Math.abs(s - 1) > 2e-3) wBad++;
}
const armBone = skin.bones.map(n => skin.fp_bones.includes(n.split('_')[0]) || n.startsWith('upper_arm') || n.startsWith('forearm'));
const armW = skin.weights.map(w => w.reduce((a, [bi, x]) => a + (armBone[bi] ? x : 0), 0));
const fpTris = skin.triangles.filter(t => t.every(i => armW[i] >= 0.5)).length;
if (skin.bones.some(b => !bones.has(b))) problems.push('skin bone missing from geo');
if (skin.triangles.some(t => t.some(i => i >= skin.vertices.length))) problems.push('skin triangle index out of range');

// 4. P9-01 on hand_r: rear notch and front dot (gun px -> mesh blocks (x, z, -y) / 16) through the eye in fp_ads and
// at the end of fp_raise
const K = skin.props.p9_01.matrix;                                 // column-major, mesh blocks -> body model blocks
const Kv = v => [0, 1, 2].map(i => K[i] * v[0] + K[4 + i] * v[1] + K[8 + i] * v[2] + K[12 + i]);
const gunPx = ([x, y, z]) => [x / 16, z / 16, -y / 16];
const atBone = (g, v) => add(mv(g.R, v.map(c => c * 16)), g.o).map(c => c / 16);   // blocks in, blocks out
function sightLine(fk) {
    const eye = atBone(fk.head, skin.eye);
    const rear = atBone(fk.hand_r, Kv(gunPx([0, -3.33, 6.04]))), front = atBone(fk.hand_r, Kv(gunPx([0, 6.0, 6.13])));
    const a = sub(rear, eye), b = sub(front, eye);
    const off = v => Math.hypot(v[0], v[1]) / v[2];                // angle off the +z (forward) axis, radians
    return {rear_forward_m: +a[2].toFixed(3), rear_off_deg: +(Math.atan(off(a)) / D).toFixed(2), front_off_deg: +(Math.atan(off(b)) / D).toFixed(2)};
}
const sight = {};
for (const [clip, at] of [['fp_ads', 0], ['fp_raise', -1]]) {
    const r = ref.clips[clip];
    if (!r) { problems.push('reference missing clip ' + clip); continue; }
    const s = sight[clip] = sightLine(FK[clip][r.frames.at(at)]);
    if (s.rear_off_deg > 0.5 || s.front_off_deg > 0.5) problems.push(clip + ' sight line misses the eye');
}

// 5. seams: the game switches clips without blending, so each clip must start (or end) on the pose it leaves from
const SEAMS = [['fp_raise', 0, 'fp_hip', 0], ['fp_raise', -1, 'fp_ads', 0], ['fp_lower', 0, 'fp_ads', 0], ['fp_lower', -1, 'fp_hip', 0],
               ['fp_lower', 1, 'fp_raise', 1], ['fp_fire', 0, 'fp_ads', 0], ['fp_fire', -1, 'fp_ads', 0],
               ['fp_fire_hip', 0, 'fp_hip', 0], ['fp_fire_hip', -1, 'fp_hip', 0]];
// H1-P2 action clips (reloads) start from hip and hand back to it; the _ads ones from and to ADS
for (const c of Object.keys(ref.clips)) if (c.startsWith('fp_reload')) {
    const end = c.endsWith('_ads') ? 'fp_ads' : 'fp_hip';
    SEAMS.push([c, 0, end, 0], [c, -1, end, 0]);
}
// the draws hand over to hip, put_away leaves from it; all three meet at the gun below the screen (put_away's end is
// where the draws start)
for (const [a, fa, b, fb] of [['fp_draw', -1, 'fp_hip', 0], ['fp_first_draw', -1, 'fp_hip', 0], ['fp_put_away', 0, 'fp_hip', 0],
                              ['fp_put_away', -1, 'fp_draw', 0], ['fp_first_draw', 0, 'fp_draw', 0]])
    if (ref.clips[a]) SEAMS.push([a, fa, b, fb]);
// the inspects leave from hip and hand back to it; the unloads leave from it (they end with the gun below the screen,
// where the held item turns into the magazine)
for (const c of Object.keys(ref.clips)) {
    if (c.startsWith('fp_inspect')) SEAMS.push([c, 0, 'fp_hip', 0], [c, -1, 'fp_hip', 0]);
    if (c.startsWith('fp_unload')) SEAMS.push([c, 0, 'fp_hip', 0], [c, -1, 'fp_hip', 0]);   // stowed, back at the hip (2026-10-24)
}
let seamPx = 0;
for (const [a, fa, b, fb] of SEAMS) {
    if (!ref.clips[a] || !ref.clips[b]) { problems.push('reference missing clip for seam ' + a + ' / ' + b); continue; }
    const A1 = FK[a][ref.clips[a].frames.at(fa)], B1 = FK[b][ref.clips[b].frames.at(fb)];
    let worst = 0, at = '';
    for (const n of ref.bones) {                                  // where each bone carries the origin and a point 1 block out
        const pa = add(mv(A1[n].R, [16, 16, 16]), A1[n].o), pb = add(mv(B1[n].R, [16, 16, 16]), B1[n].o);
        const d = Math.max(Math.hypot(...sub(pa, pb)), Math.hypot(...sub(A1[n].o, B1[n].o)));
        if (d > worst) [worst, at] = [d, n];
    }
    seamPx = Math.max(seamPx, worst);
    if (worst > 0.05) problems.push('clip seam ' + a + '[' + fa + '] / ' + b + '[' + fb + '] jumps ' + worst.toFixed(3) + ' px at ' + at);
}

// 6. first-person view points per end pose (m behind the eye, vertical fov)
const view = skin.fp_view;
if (!view || !['hip', 'ads'].every(k => Array.isArray(view[k]) && view[k].length === 2 && view[k][1] > 20 && view[k][1] < 90))
    problems.push('skin fp_view missing or out of range');

const ok = rotDeg < 0.2 && posErr < 0.03 && skinErr < 0.05 && wBad === 0 && problems.length === 0;
console.log(JSON.stringify({
    bones: geo.bones.length, clips: Object.fromEntries(Object.entries(anims).map(([k, a]) => [k, {length_s: a.animation_length, bones: Object.keys(a.bones).length}])),
    fk_vs_blender: {rotation_deg: +rotDeg.toFixed(4), position_px: +posErr.toFixed(5)},
    skin_vs_blender_px: +skinErr.toFixed(5), skin_samples: samples, skin_weight_problems: wBad,
    skinned: {vertices: skin.vertices.length, triangles: skin.triangles.length, first_person_triangles: fpTris},
    sight, clip_seam_px: +seamPx.toFixed(4), fp_view: view, problems, result: ok ? 'PASS' : 'FAIL'}, null, 1));
process.exitCode = ok ? 0 : 1;
