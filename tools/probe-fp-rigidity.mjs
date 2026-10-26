// First-person arm rigidity probe (docs/dev/fp_arm_rigidity_v1.md). Read-only: measures how "soft" a native gun's FP
// clips are on the runtime GeckoLib JSON (60 Hz linear keys), in model units of the gun's geo.
//   node tools/probe-fp-rigidity.mjs [gun ...]          default: p9_01_v3_native br51_01_v3_native
// Columns: holdPct / longestHold = share of the clip and longest stretch with the gun still (< 6 deg/s, grip < 1 unit/s);
// reversals = direction changes of the handling rotation (> 0.3 deg); lhSwim = s the support hand slides on the gun
// within 1.5 units of its grip; rhSlip = how far the grip leaves the firing hand; shoulder vs hand = travel of the rigid
// arm box's proximal end vs its hand end; wrist = wrist angle the arm would need with its root held at the idle shoulder;
// shotBack = how far the gun's pitch swings back past rest after its peak (% of the peak); butt = stock travel (rifles).
// GeckoLib frame: pivot x, position x, rotation x and y negated; local = T(pivot) T(pos) Rz Ry Rx T(-pivot).
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.join(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'), 'src/main/resources/assets/apocalypse_firstlight');
const guns = process.argv.length > 2 ? process.argv.slice(2) : ['p9_01_v3_native', 'br51_01_v3_native'];
// forearm length = 12 px x 0.78 presentation / the gun's FP display scale (tools/build-*-native.mjs ARM_LEN)
const ARM_LEN = {p9_01_v3_native: 12 * 0.78 / 0.53, br51_01_v3_native: 12 * 0.78 / 0.45};
const D = Math.PI / 180;
const BUTT = {br51_01_v3_native: [0.44, 9.28, 26.75]};   // rear of the buttplate (aflmesh gun_body, geo x negated)
const mm = (a, b) => a.map(r => [0, 1, 2].map(j => r[0] * b[0][j] + r[1] * b[1][j] + r[2] * b[2][j]));
const mv = (m, v) => m.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const T3 = m => [0, 1, 2].map(i => [0, 1, 2].map(j => m[j][i]));
const add = (a, b) => a.map((v, i) => v + b[i]), sub = (a, b) => a.map((v, i) => v - b[i]);
function Rzyx([x, y, z]) {
    const [cx, sx, cy, sy, cz, sz] = [Math.cos(x * D), Math.sin(x * D), Math.cos(y * D), Math.sin(y * D), Math.cos(z * D), Math.sin(z * D)];
    return [[cy * cz, sx * sy * cz - cx * sz, cx * sy * cz + sx * sz], [cy * sz, sx * sy * sz + cx * cz, cx * sy * sz - sx * cz], [-sy, sx * cy, cx * cy]];
}
const angle = m => Math.acos(Math.max(-1, Math.min(1, (m[0][0] + m[1][1] + m[2][2] - 1) / 2))) / D;
function sampler(ch) {
    if (!ch) return () => [0, 0, 0];
    if (Array.isArray(ch)) return () => ch;
    const keys = Object.entries(ch).map(([t, v]) => [+t, Array.isArray(v) ? v : (v.post ?? v.vector ?? v.pre)]).sort((a, b) => a[0] - b[0]);
    return t => {
        if (t <= keys[0][0]) return keys[0][1];
        if (t >= keys.at(-1)[0]) return keys.at(-1)[1];
        let i = keys.findIndex(k => k[0] > t) - 1; const [ta, a] = keys[i], [tb, b] = keys[i + 1];
        const u = (t - ta) / (tb - ta); return a.map((v, k) => v + (b[k] - v) * u);
    };
}
function probe(gun) {
    const geo = JSON.parse(fs.readFileSync(`${root}/geo/${gun}.geo.json`, 'utf8'))['minecraft:geometry'][0].bones;
    const anim = JSON.parse(fs.readFileSync(`${root}/animations/${gun}.animation.json`, 'utf8')).animations;
    const bones = new Map(geo.map(b => [b.name, b]));
    const out = [];
    for (const [full, a] of Object.entries(anim)) {
        const name = full.replace(/^.*\./, ''), len = a.animation_length;
        const ch = {}; for (const [b, v] of Object.entries(a.bones || {})) ch[b] = {r: sampler(v.rotation), p: sampler(v.position)};
        const frame = (name, t) => {   // world {R, o} of a bone
            const b = bones.get(name); const parent = b.parent ? frame(b.parent, t) : {R: [[1, 0, 0], [0, 1, 0], [0, 0, 1]], o: [0, 0, 0]};
            const piv = [-b.pivot[0], b.pivot[1], b.pivot[2]], bind = b.rotation ? [-b.rotation[0], -b.rotation[1], b.rotation[2]] : [0, 0, 0];
            const r = ch[name] ? ch[name].r(t) : [0, 0, 0], p = ch[name] ? ch[name].p(t) : [0, 0, 0];
            const R = Rzyx(add(bind, [-r[0], -r[1], r[2]])), o = add(sub(add(piv, [-p[0], p[1], p[2]]), mv(R, piv)), [0, 0, 0]);
            return {R: mm(parent.R, R), o: add(parent.o, mv(parent.R, o))};
        };
        const local = (child, par, t) => { const c = frame(child, t), p = frame(par, t), Pt = T3(p.R); return {R: mm(Pt, c.R), o: mv(Pt, sub(c.o, p.o))}; };
        const dt = 1 / 60, N = Math.round(len / dt);
        const grip = bones.get('right_hand_anchor').pivot, gp = [-grip[0], grip[1], grip[2]];
        let hold = 0, longest = 0, run = 0, peakW = 0, slowL = 0, maxRSlip = 0, onGunSwim = 0, camPeak = 0;
        const wx = [], ends = {r: [], l: [], rh: [], lh: []}, shotX = [];
        const L0 = local('left_hand_anchor', 'handling', 0), R0 = local('right_hand_anchor', 'handling', 0);
        for (let i = 0; i < N; i++) {
            const t = i * dt, a0 = frame('handling', t), a1 = frame('handling', t + dt);
            const w = angle(mm(a1.R, T3(a0.R))) / dt;
            const v = Math.hypot(...sub(add(a1.o, mv(a1.R, gp)), add(a0.o, mv(a0.R, gp)))) / dt;
            peakW = Math.max(peakW, w);
            if (w < 6 && v < 1) { hold += dt; run += dt; longest = Math.max(longest, run); } else run = 0;
            wx.push(ch.handling ? ch.handling.r(t) : [0, 0, 0]);
            const l0 = local('left_hand_anchor', 'handling', t), l1 = local('left_hand_anchor', 'handling', t + dt);
            const lv = Math.hypot(...sub(l1.o, l0.o)) / dt, near = Math.hypot(...sub(l0.o, L0.o));
            if (near < 1.5 && lv > 0.3) onGunSwim += dt;            // near the grip but sliding on it
            if (lv > 0.3 && lv < 6) slowL += dt;                     // slow creeping of the support hand relative to the gun
            const r = local('right_hand_anchor', 'handling', t);
            maxRSlip = Math.max(maxRSlip, Math.hypot(...sub(r.o, R0.o)));
            for (const [k, an] of [['r', 'right_hand_anchor'], ['l', 'left_hand_anchor']]) {
                const f = frame(an, t), piv = bones.get(an).pivot, o = add(f.o, mv(f.R, [-piv[0], piv[1], piv[2]]));
                ends[k + 'h'].push(o); ends[k].push(add(o, mv(f.R, [0, -ARM_LEN[gun], 0])));
            }
            if (BUTT[gun]) { const f = frame('handling', t); (ends.butt ??= []).push(add(f.o, mv(f.R, BUTT[gun]))); }
            shotX.push(ch.handling ? ch.handling.r(t)[0] : 0);
            const c = ch.camera ? ch.camera.r(t) : [0, 0, 0]; camPeak = Math.max(camPeak, Math.hypot(...c));
        }
        // reversals: local extrema of each handling rotation axis with prominence > 0.3 deg
        let rev = 0;
        for (let k = 0; k < 3; k++) {
            const s = wx.map(v => v[k]); let dir = 0, ext = s[0];
            for (const x of s) { if (dir >= 0 && x < ext - 0.3) { if (dir > 0) rev++; dir = -1; ext = x; } else if (dir <= 0 && x > ext + 0.3) { if (dir < 0) rev++; dir = 1; ext = x; } else if ((dir >= 0 && x > ext) || (dir <= 0 && x < ext)) ext = x; }
        }
        // wrist needed if the arm root stayed at the idle shoulder end: angle between the hand's forearm axis (-Y) and hand->shoulder
        const wrist = {r: 0, l: 0}, IDLE = probe.idle?.[gun];
        if (IDLE) for (const k of ['r', 'l']) for (let i = 0; i < ends[k].length; i++) {
            const h = ends[k + 'h'][i], arm = sub(ends[k][i], h), want = sub(IDLE[k], h);
            const c = arm.reduce((a, v, j) => a + v * want[j], 0) / Math.hypot(...arm) / Math.hypot(...want);
            wrist[k] = Math.max(wrist[k], Math.acos(Math.max(-1, Math.min(1, c))) / D);
        }
        if (name === 'static_idle') (probe.idle ??= {})[gun] = {r: ends.r[0], l: ends.l[0]};
        const span = a => Math.hypot(...[0, 1, 2].map(k => Math.max(...a.map(v => v[k])) - Math.min(...a.map(v => v[k]))));
        const peak = Math.max(...shotX.map(Math.abs)), iP = shotX.findIndex(x => Math.abs(x) === peak), after = shotX.slice(iP);
        const back = peak ? Math.round(100 * Math.max(0, ...after.map(x => -Math.sign(shotX[iP]) * x)) / peak) : 0;
        const settle = after.findIndex((x, i) => after.slice(i).every(y => Math.abs(y) < 0.1));
        out.push({clip: name, shoulderR: +span(ends.r).toFixed(1), handR: +span(ends.rh).toFixed(1), shoulderL: +span(ends.l).toFixed(1), handL: +span(ends.lh).toFixed(1),
            shotBack: back, butt: ends.butt ? +span(ends.butt).toFixed(1) : '-', wristR: Math.round(wrist.r), wristL: Math.round(wrist.l), settleS: settle < 0 ? -1 : +((iP + settle) / 60).toFixed(2), len, holdPct: Math.round(100 * hold / len), longestHold: +longest.toFixed(2), peakW: Math.round(peakW),
            reversals: rev, lhSwim: +onGunSwim.toFixed(2), lhCreep: +slowL.toFixed(2), rhSlip: +maxRSlip.toFixed(2), camDeg: +camPeak.toFixed(1)});
    }
    return out;
}
const COLS = ['clip', 'len', 'holdPct', 'longestHold', 'reversals', 'shotBack', 'settleS', 'lhSwim', 'rhSlip', 'shoulderR', 'handR', 'shoulderL', 'handL', 'wristR', 'wristL', 'butt', 'camDeg'];
for (const g of guns) {
    console.log('== ' + g); console.log(COLS.join('	'));
    for (const r of probe(g)) console.log(COLS.map(c => r[c]).join('	'));
}
