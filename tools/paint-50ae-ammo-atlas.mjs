// .50 AE Ammo Material V1: Base Color atlas shared by 50ae_round (UV space 512) and 50ae_casing (UV space 16).
// Paints in UV space from the interpolated 3D position of every texel; geometry and UVs are read, never written.
//   node tools/paint-50ae-ammo-atlas.mjs <out.rgba>   -> raw 512x512 RGBA (convert with ffmpeg)
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const N = 512;
const img = new Float32Array(N * N * 3), cov = new Uint8Array(N * N);

// ---------- palette (linear-ish sRGB 0..255, Base Color only, no painted speculars) ----------
const C = {
    brass: [178, 140, 74], brassHead: [166, 130, 70], brassEdge: [196, 160, 94], brassShadow: [118, 90, 50],
    copper: [168, 94, 60], copperTip: [186, 110, 72], copperSkive: [118, 62, 42],
    cavityTop: [96, 54, 38], cavityDeep: [40, 30, 28], lead: [58, 58, 62], leadCore: [36, 36, 40],
    primer: [160, 157, 150], primerRing: [132, 129, 122], pocket: [72, 58, 36],
    // spent casing
    soot: [70, 60, 50], sootDeep: [40, 34, 30], strike: [86, 84, 80], primerSpent: [140, 136, 128],
};
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const mul = (a, k) => a.map(v => v * k);
const clamp01 = x => Math.max(0, Math.min(1, x));
const smooth = (a, b, x) => { const t = clamp01((x - a) / (b - a)); return t * t * (3 - 2 * t); };
// Low-frequency material drift, deterministic, no pixel noise.
const drift = (ang, y) => 1 + 0.03 * Math.sin(ang + y * 2.3 + 0.7) + 0.02 * Math.sin(3 * ang - y * 5.1 + 1.9);

// ---------- zones ----------
function zone(kind, c, n) {
    const r = Math.hypot(c[0], c[2]), nr = r > 1e-6 ? (n[0] * c[0] + n[2] * c[2]) / r : 0, y = c[1], up = n[1];
    if (up < -0.9) return r > 0.15 ? 'head' : 'primer';
    if (y < 0.05 && nr < -0.5) return 'pocket';
    if (y < 0.05 && Math.abs(up) < 0.9 && nr < 0.5) return 'strike';            // casing firing-pin dimple
    if (y < 0.07 && nr > 0.5) return 'rim';
    if (y < 0.13) return 'groove';
    if (kind === 'round') {
        if (up > 0.9 && y < 1.8) return 'mouthRing';
        if (up > 0.9 && r > 0.1) return 'meplat';
        if (up > 0.9) return 'cavityFloor';
        if (nr < -0.5) return 'cavityWall';
        if (y > 1.78) return 'jacket';
        return 'body';
    }
    if (up > 0.9 && y > 1.7) return 'lip';
    if (up > 0.9) return 'innerFloor';
    if (nr < -0.5) return 'innerWall';
    return 'body';
}

// ---------- colour rules ----------
function shade(kind, z, p, len) {
    const y = p[1], r = Math.hypot(p[0], p[2]), ang = Math.atan2(p[2], p[0]), d = drift(ang, y);
    const spent = kind === 'casing';
    switch (z) {
        case 'body': {
            // value hierarchy along the case: slightly darker at the head, brightest around 2/3 height
            let c = mul(C.brass, d * (0.93 + 0.1 * smooth(0.1, 1.2, y) - 0.04 * smooth(1.35, 1.76, y)));
            if (!spent && y > 1.7) c = mix(c, C.brassEdge, 0.35 * smooth(1.7, 1.76, y));         // case mouth edge catch
            if (spent) {
                c = mul(c, 0.96);
                c = mix(c, [150, 116, 96], 0.07 * smooth(1.15, 1.45, y) * (1 - smooth(1.55, 1.7, y)));   // faint heat tint band
                c = mix(c, C.soot, 0.42 * smooth(1.5, 1.774, y) * (0.85 + 0.15 * Math.sin(ang * 2 + 0.4)));  // fired mouth
            }
            return c;
        }
        case 'rim': return mul(mix(C.brassHead, C.brassEdge, y < 0.02 ? 0.25 : 0.45), d);
        case 'groove': return mul(C.brassShadow, d);                                                // extractor groove AO
        case 'head': {
            let c = mul(C.brassHead, d * (0.97 + 0.03 * Math.sin(r * 90)));                           // faint lathe rings
            c = mix(c, C.brassEdge, 0.3 * smooth(0.3, 0.37, r));
            c = mix(c, C.brassShadow, 0.35 * (1 - smooth(0.12, 0.15, r)));                             // primer pocket shadow
            return c;
        }
        case 'pocket': return C.pocket;
        case 'primer': {
            const base = spent ? C.primerSpent : C.primer;
            let c = mix(C.primerRing, base, smooth(0.09, 0.07, r) === 0 ? 1 : smooth(0.1, 0.075, r));
            if (spent) c = mix(c, C.strike, 0.55 * (1 - smooth(0.022, 0.05, r)));                       // strike halo
            return mul(c, 0.98 + 0.02 * Math.sin(ang));
        }
        case 'strike': return mix(C.strike, [70, 68, 66], smooth(0.02, 0, r));
        case 'mouthRing': return mix(C.brassShadow, C.brassEdge, 0.35);                                // crimp / case mouth step
        case 'jacket': {
            let c = mul(mix(C.copper, C.copperTip, smooth(1.84, 2.2, y) * 0.6), d);
            // JHP skives: six shallow cuts running down from the meplat
            const k = Math.abs(((ang / (Math.PI / 3)) % 1 + 1) % 1 - 0.5) * (Math.PI / 3) * r;         // arc distance to a skive line
            const s = (1 - smooth(0.006, 0.02, k)) * smooth(2.02, 2.12, y);
            return mix(c, C.copperSkive, 0.7 * s);
        }
        case 'meplat': return mix(C.copperTip, C.brassEdge, 0.12 * smooth(0.12, 0.16, r));
        case 'cavityWall': return mix(C.cavityDeep, C.cavityTop, smooth(2.09, 2.19, y));
        case 'cavityFloor': return mix(C.leadCore, C.lead, smooth(0.0, 0.06, r));                        // lead core, not flat black
        case 'lip': return mix(mul(C.brassEdge, 0.86), C.soot, 0.45);                                   // fired mouth lip
        case 'innerWall': return mix(C.sootDeep, C.soot, smooth(1.4, 1.76, y));
        case 'innerFloor': return C.sootDeep;
    }
    return [255, 0, 255];
}

// ---------- raster ----------
function paint(kind, file, scale) {
    const s = JSON.parse(fs.readFileSync(path.join(root, 'src/main/blockbench', file), 'utf8'));
    const counts = {};
    for (const e of s.elements) {
        const P = id => e.vertices[id].map((x, i) => x + e.origin[i]);
        for (const f of Object.values(e.faces)) {
            const ps = f.vertices.map(P), uv = f.vertices.map(v => f.uv[v].map(u => u * scale));
            const c = [0, 1, 2].map(i => ps.reduce((a, q) => a + q[i], 0) / ps.length);
            const a = ps[0], b = ps[1], d = ps[2], u = b.map((x, i) => x - a[i]), v = d.map((x, i) => x - a[i]);
            let n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
            const L = Math.hypot(...n) || 1; n = n.map(x => x / L);
            const z = zone(kind, c, n); counts[z] = (counts[z] || 0) + 1;
            for (let i = 1; i + 1 < ps.length; i++) tri(kind, z, [uv[0], uv[i], uv[i + 1]], [ps[0], ps[i], ps[i + 1]]);
        }
    }
    return counts;
}
function tri(kind, z, T, Q) {
    const [a, b, c] = T, e = (p, q, x, y) => (q[0] - p[0]) * (y - p[1]) - (q[1] - p[1]) * (x - p[0]);
    const A = e(a, b, c[0], c[1]); if (Math.abs(A) < 1e-9) return;
    const x0 = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0]) - 1)), x1 = Math.min(N - 1, Math.ceil(Math.max(a[0], b[0], c[0]) + 1));
    const y0 = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1]) - 1)), y1 = Math.min(N - 1, Math.ceil(Math.max(a[1], b[1], c[1]) + 1));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const px = x + 0.5, py = y + 0.5;
        let w0 = e(b, c, px, py) / A, w1 = e(c, a, px, py) / A, w2 = e(a, b, px, py) / A;
        if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
        const p = [0, 1, 2].map(i => w0 * Q[0][i] + w1 * Q[1][i] + w2 * Q[2][i]);
        const col = shade(kind, z, p), k = y * N + x;
        img.set(col, k * 3); cov[k] = 1;
    }
}

const stats = {round: paint('round', '50ae_round.bbmodel', 1), casing: paint('casing', '50ae_casing.bbmodel', 32)};
// Sub-pixel sliver faces (collapsed caps) may miss every texel centre: stamp their centroid colour.
// Then dilate islands 6 px so mip/filter bleed never reaches the background.
for (let pass = 0; pass < 6; pass++) {
    const add = [];
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
        const k = y * N + x; if (cov[k]) continue;
        let s = [0, 0, 0], n = 0;
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
            const X = x + dx, Y = y + dy; if (X < 0 || Y < 0 || X >= N || Y >= N) continue;
            const q = Y * N + X; if (cov[q] === 1) { s = s.map((v, i) => v + img[q * 3 + i]); n++; }
        }
        if (n) add.push([k, s.map(v => v / n)]);
    }
    for (const [k, c] of add) { img.set(c, k * 3); cov[k] = 2; }
    for (let k = 0; k < N * N; k++) if (cov[k] === 2) cov[k] = 1;
}
const out = Buffer.alloc(N * N * 4);
for (let k = 0; k < N * N; k++) {
    const c = cov[k] ? [0, 1, 2].map(i => img[k * 3 + i]) : C.brassShadow;   // opaque neutral background
    for (let i = 0; i < 3; i++) out[k * 4 + i] = Math.max(0, Math.min(255, Math.round(c[i])));
    out[k * 4 + 3] = 255;
}
if (process.argv[2]) fs.writeFileSync(process.argv[2], out);
console.log(JSON.stringify(stats));
