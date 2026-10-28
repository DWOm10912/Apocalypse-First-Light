// Offline third-person picture of a player action (untextured, depth-tested boxes and the gun mesh). NOT Minecraft visual
// acceptance: the rig core's own solve (tools/blockbench/afl_rig.js solveFrame, as tools/author-*-actions.mjs check them),
// the skin as one box per bone segment plus the head, a gun prop drawn from its aflmesh (its static bones; the magazines
// out of the gun drawn where their gun_part props are, the gun's own magazine hidden while one shows).
//   node tools/preview-tp-action.mjs <action id> <t1,t2,...> <out.png> [--pitch <deg>] [--crouch] [--rifle] [--cams back,front,right,left]
//   action id: e.g. gun/br51_01_reload_tactical (player_actions/<id>.json); rows = times, columns = cameras
//   --rifle: the body as with a rifle in the hands in game (the chest 30 degrees right of the view, its rifle share of the pitch)
// Colours: grey = gun, amber = magazines, red = right arm, blue = left arm, teal = torso, navy = legs, skin = head.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const core = require('./blockbench/afl_rig.js');
const {v3, tf} = core;
const A = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const rig = core.loadRig(read(path.join(A, 'rigs/afl_humanoid_v1.json')));
const poser = new core.Poser(rig, core.loadClips(rig, read(path.join(A, 'animations/player/locomotion.animation.json'))));
const defCache = new Map();
const defOf = id => { if (!defCache.has(id)) { const f = path.join(A, 'player_props', id + '.json'); defCache.set(id, fs.existsSync(f) ? read(f) : null); } return defCache.get(id); };
const meshCache = new Map();
/** A gun's aflmesh as per-bone polygons in its GeckoLib frame (blocks): vertex + its bone's rest pivot. */
function gunPolys(gun) {
    if (meshCache.has(gun)) return meshCache.get(gun);
    const asset = fs.existsSync(path.join(A, `meshes/${gun}_v3_native.aflmesh.json`)) ? `${gun}_v3_native` : gun;
    const mesh = read(path.join(A, `meshes/${asset}.aflmesh.json`));
    const bones = new Map(read(path.join(A, `geo/${asset}.geo.json`))['minecraft:geometry'][0].bones.map(b => [b.name, b]));
    const out = [];
    for (const p of mesh.parts) {
        const piv = (bones.get(p.bone)?.pivot ?? [0, 0, 0]).map(n => n / 16);
        const V = p.vertices.map(v => [v[0] + piv[0], v[1] + piv[1], v[2] + piv[2]]);
        for (const f of p.faces) out.push({bone: p.bone, pts: f.map(i => V[i])});
    }
    meshCache.set(gun, out);
    return out;
}
const MAG_BONES = new Set(['mag_out', 'reload_magazine', 'mag_out_round_1', 'mag_out_round_2', 'empty_old_mag', 'empty_old_mag_round_1', 'empty_old_mag_round_2']);

// camera: posed space is the rig's (px, y down from the neck, the body faces -z); eye at `from` looking at `at`
function camera(from, at) {
    const f = v3.norm(v3.sub(at, from)), up = [0, -1, 0], r = v3.norm(v3.cross(f, up)), u = v3.cross(r, f);
    return p => { const d = v3.sub(p, from); return [v3.dot(d, r), v3.dot(d, u), -v3.dot(d, f)]; };
}
const CAMS = {back: [-18, -6, 34], front: [16, -2, -36], right: [-38, 0, -2], left: [38, 0, -2]};

function frame(json, t, opts, W, H, pixels, zbuf, ox, oy, stride, cam) {
    const action = core.actionFromJson(json, id => defOf(id), () => null);
    const res = core.solveFrame(poser, {action, seconds: t, time: t, pitch: opts.pitch, crouch: opts.crouch, ...(opts.rifle ? {rifle: true, chestYaw: 30} : {})});
    const center = res.pose.point(rig.chest, rig.pivot[rig.chest]);
    const view = camera(v3.add(center, CAMS[cam]), v3.add(center, [0, 3, -3]));
    const f = H / 2 / Math.tan(32 * Math.PI / 180);
    const tri = (P, color) => {
        const Q = P.map(view);
        if (Q.some(q => q[2] >= -1)) return;
        const e1 = v3.sub(Q[1], Q[0]), e2 = v3.sub(Q[2], Q[0]), n = v3.cross(e1, e2), ln = Math.hypot(...n) || 1;
        const light = 0.45 + 0.35 * Math.abs(n[1] / ln) + 0.2 * Math.abs(n[2] / ln), c = color.map(v => Math.min(255, Math.round(v * light)));
        const pr = Q.map(v => [W / 2 + v[0] / -v[2] * f, H / 2 - v[1] / -v[2] * f, -v[2]]);
        for (let j = 1; j < pr.length - 1; j++) {
            const [a, b, cc] = [pr[0], pr[j], pr[j + 1]], den = (b[1] - cc[1]) * (a[0] - cc[0]) + (cc[0] - b[0]) * (a[1] - cc[1]);
            if (Math.abs(den) < 1e-9) continue;
            for (let y = Math.max(0, Math.floor(Math.min(a[1], b[1], cc[1]))); y < Math.min(H, Math.ceil(Math.max(a[1], b[1], cc[1]))); y++)
                for (let x = Math.max(0, Math.floor(Math.min(a[0], b[0], cc[0]))); x < Math.min(W, Math.ceil(Math.max(a[0], b[0], cc[0]))); x++) {
                    const u = ((b[1] - cc[1]) * (x + .5 - cc[0]) + (cc[0] - b[0]) * (y + .5 - cc[1])) / den, v = ((cc[1] - a[1]) * (x + .5 - cc[0]) + (a[0] - cc[0]) * (y + .5 - cc[1])) / den, l = 1 - u - v;
                    if (Math.min(u, v, l) < 0) continue;
                    const z = 1 / (u / a[2] + v / b[2] + l / cc[2]), i = (oy + y) * stride + ox + x;
                    if (z >= zbuf[i]) continue; zbuf[i] = z; pixels.set(c, i * 3);
                }
        }
    };
    const box = (bone, lo, hi, color) => {   // lo, hi: the rig file's model px (y up from the feet); the pose's rest space is y down from 24
        const v = Array.from({length: 8}, (_, i) => { const p = [0, 1, 2].map(j => (i & (1 << j) ? hi[j] : lo[j])); return res.pose.point(bone, [p[0], 24 - p[1], p[2]]); });
        for (const q of [[0, 2, 3, 1], [4, 5, 7, 6], [0, 1, 5, 4], [2, 6, 7, 3], [0, 4, 6, 2], [1, 3, 7, 5]]) tri(q.map(i => v[i]), color);
    };
    const COL = {body: [80, 150, 150], right_arm: [205, 120, 95], left_arm: [95, 135, 205], right_leg: [70, 70, 140], left_leg: [70, 70, 140]};
    for (const part of rig.json.skin) {
        const top = part.from[1] + part.size[1];
        part.bones.forEach((bone, i) => {
            const hi = i === 0 ? top : top - part.cuts[i - 1], lo = i === part.bones.length - 1 ? part.from[1] : top - part.cuts[i];
            box(rig.index[bone] ?? rig.find(bone), [part.from[0], lo, part.from[2]], [part.from[0] + part.size[0], hi, part.from[2] + part.size[2]], COL[part.part]);
        });
    }
    box(rig.head, [-4, 24, -4], [4, 32, 4], [225, 190, 160]);
    // props: the gun from its mesh; its magazines out of it as their gun parts
    const tracks = json.props ?? (json.prop ? [json.prop] : []);
    const shownParts = new Set();
    tracks.forEach((p, i) => { const d = defOf(p.id); if (d?.gun_part && res.props[i] && !res.hidden[i]) shownParts.add(i); });
    tracks.forEach((p, i) => {
        const d = defOf(p.id), placed = res.props[i] ?? (i === 0 ? res.prop : null);
        if (!d || !placed || res.hidden[i]) return;
        const k = 16 * d.scale, toWorld = q => tf.apply(placed, [q[0] * k, -q[1] * k, -q[2] * k]);
        if (d.gun) {
            for (const poly of gunPolys(d.gun)) {
                if (MAG_BONES.has(poly.bone) || (shownParts.size && /^magazine/.test(poly.bone))) continue;
                tri(poly.pts.map(toWorld), [120, 124, 130]);
            }
        } else if (d.gun_part) {
            const want = d.gun_part.bone === 'mag_out' ? /^(reload_magazine|mag_out_round)/ : /^empty_old_mag/;
            for (const poly of gunPolys(d.gun_part.gun)) if (want.test(poly.bone)) tri(poly.pts.map(toWorld), [205, 150, 60]);
        }
    });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const a = process.argv.slice(2), opt = (n, d) => { const i = a.indexOf(n); return i < 0 ? d : a[i + 1]; };
    const [id, times, out] = a;
    const json = read(path.join(A, 'player_actions', id + '.json'));
    const opts = {pitch: +opt('--pitch', 0), crouch: a.includes('--crouch'), rifle: a.includes('--rifle')};
    const cams = opt('--cams', 'back,front,right').split(','), ts = times.split(',').map(Number);
    const W = +(process.env.TP_W || 320), H = +(process.env.TP_H || 320), SW = W * cams.length, SH = H * ts.length;
    const pixels = Buffer.alloc(SW * SH * 3, 210), zbuf = new Float64Array(SW * SH).fill(Infinity);
    ts.forEach((t, r) => cams.forEach((c, ci) => frame(json, t, opts, W, H, pixels, zbuf, ci * W, r * H, SW, c)));
    for (let y = 0; y < SH; y++) for (let x = 0; x < SW; x++) if (x % W === 0 || y % H === 0) pixels.set([255, 255, 255], (y * SW + x) * 3);
    const ppm = out.replace(/\.png$/, '.ppm');
    fs.writeFileSync(ppm, Buffer.concat([Buffer.from(`P6\n${SW} ${SH}\n255\n`), pixels]));
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', ppm, out]); fs.unlinkSync(ppm);
    console.log('wrote', out);
}
