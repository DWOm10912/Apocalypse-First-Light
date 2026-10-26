// Water visual V1 (docs/rendering/water_visual_v1_design.md 3, 5.2, 7): AFL's own water textures, made from a seed.
//
//   node tools/water/make-water-textures.mjs          writes the textures
//   node tools/water/make-water-textures.mjs --check  checks the written ones (size, tiling, unit normals), writes nothing
//
// water_normal_0.png  512 px for 8 m: the long ripples (wind waves in a pond), mostly with the wind (+x)
// water_normal_1.png  512 px for 1.6 m: the fine ripples, every direction
//
// Each is a height field summed from sine waves whose wave numbers are whole cycles across the tile, so it tiles with no
// seam; the amplitude falls as k^-2 (a wind-sea slope spectrum, flat in slope), the phases random. Stored as normals,
// RGB = xyz * 0.5 + 0.5 (z up), scaled to an RMS slope (SLOPE). The tile sizes divide 1024 m, the span the client
// measures texture coordinates over (ClientLiquid), so moving the origin by 1024 m does not shift the pattern.
// block/liquid/water_fall.png: the falling sheet's streaks (see below).
// Not done yet (design 5): water_foam, water_noise; V1.1 flow normal, rain ripples, caustics, colour table.

import { writeFileSync, readFileSync } from 'node:fs';
import { deflateSync, inflateSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const OUT = join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/water');

const LAYERS = [
    // kMin, kMax: whole cycles across the tile; spread: cos^spread around +x (0: all directions)
    { file: 'water_normal_0.png', size: 512, metres: 8, seed: 20261024, kMin: 3, kMax: 40, waves: 220, spread: 4, slope: 0.16 },
    { file: 'water_normal_1.png', size: 512, metres: 1.6, seed: 20261025, kMin: 2, kMax: 24, waves: 160, spread: 0, slope: 0.22 },
];

function rng(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s + 0x6d2b79f5) >>> 0;
        let t = s;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

/** Slope field (dh/dx, dh/dy per tile unit) of the layer's waves on its grid. */
function slopes(L) {
    const r = rng(L.seed);
    const waves = [];
    const seen = new Set();
    while (waves.length < L.waves) {
        const m = Math.round((r() * 2 - 1) * L.kMax), n = Math.round((r() * 2 - 1) * L.kMax);
        const k = Math.hypot(m, n);
        if (k < L.kMin || k > L.kMax || seen.has(m + ',' + n) || seen.has(-m + ',' + -n)) continue;
        // direction spread around +x: waves against the wind are weak
        const c = m / k;
        const dir = L.spread > 0 ? Math.pow(Math.max(0, c) * 0.9 + 0.1, L.spread) : 1;
        if (r() > dir) continue;
        seen.add(m + ',' + n);
        waves.push({ m, n, a: 1 / (k * k), p: r() * Math.PI * 2 });
    }
    const N = L.size, dx = new Float64Array(N * N), dy = new Float64Array(N * N);
    for (const w of waves) {
        const kx = 2 * Math.PI * w.m, ky = 2 * Math.PI * w.n;
        for (let y = 0; y < N; y++) {
            const py = ky * y / N + w.p;
            for (let x = 0; x < N; x++) {
                const c = Math.cos(kx * x / N + py) * w.a;
                dx[y * N + x] += kx * c;
                dy[y * N + x] += ky * c;
            }
        }
    }
    // tile units to metres, scaled to the RMS slope
    let sum = 0;
    for (let i = 0; i < N * N; i++) sum += dx[i] * dx[i] + dy[i] * dy[i];
    const s = L.slope / Math.sqrt(sum / (N * N));
    for (let i = 0; i < N * N; i++) { dx[i] *= s; dy[i] *= s; }
    return { dx, dy };
}

function normals(L) {
    const { dx, dy } = slopes(L);
    const N = L.size, px = Buffer.alloc(N * N * 3);
    for (let i = 0; i < N * N; i++) {
        let x = -dx[i], y = -dy[i], z = 1;
        const l = Math.hypot(x, y, z);
        px[i * 3] = Math.round((x / l * 0.5 + 0.5) * 255);
        px[i * 3 + 1] = Math.round((y / l * 0.5 + 0.5) * 255);
        px[i * 3 + 2] = Math.round((z / l * 0.5 + 0.5) * 255);
    }
    return px;
}

// ---- PNG (8-bit RGB, no filter) -------------------------------------------------------------------------------------

const CRC = new Uint32Array(256).map((_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
});

function crc(buf) {
    let c = 0xffffffff;
    for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
    const len = Buffer.alloc(4), tc = Buffer.concat([Buffer.from(type), data]), c = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    c.writeUInt32BE(crc(tc));
    return Buffer.concat([len, tc, c]);
}

function png(N, rgb) {
    const raw = Buffer.alloc(N * (N * 3 + 1));
    for (let y = 0; y < N; y++) rgb.copy(raw, y * (N * 3 + 1) + 1, y * N * 3, (y + 1) * N * 3);
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(N, 0);
    ihdr.writeUInt32BE(N, 4);
    ihdr[8] = 8;
    ihdr[9] = 2;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
        chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

function readPng(file) {
    const b = readFileSync(file);
    const N = b.readUInt32BE(16);
    const parts = [];
    for (let o = 8; o < b.length;) {
        const len = b.readUInt32BE(o), type = b.toString('ascii', o + 4, o + 8);
        if (type === 'IDAT') parts.push(b.subarray(o + 8, o + 8 + len));
        o += 12 + len;
    }
    const raw = inflateSync(Buffer.concat(parts)), rgb = Buffer.alloc(N * N * 3);
    for (let y = 0; y < N; y++) raw.copy(rgb, y * N * 3, y * (N * 3 + 1) + 1, (y + 1) * (N * 3 + 1));
    return { N, rgb };
}

// ---- main -----------------------------------------------------------------------------------------------------------

const check = process.argv.includes('--check');
let bad = 0;
for (const L of LAYERS) {
    const file = join(OUT, L.file);
    if (!check) {
        writeFileSync(file, png(L.size, normals(L)));
        console.log(`${L.file}: ${L.size} px for ${L.metres} m`);
        continue;
    }
    const { N, rgb } = readPng(file);
    if (N !== L.size) { console.log(`${L.file}: ${N} px, expected ${L.size}`); bad++; continue; }
    // unit normals, and the seam no rougher than the inside (left vs right column, top vs bottom row)
    // (each channel along its own axis: x across the left-right seam, y across the top-bottom one)
    let worstLen = 0, inside = 0, seam = 0;
    const v = (x, y, c) => rgb[(y * N + x) * 3 + c] / 255 * 2 - 1;
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            worstLen = Math.max(worstLen, Math.abs(Math.hypot(v(x, y, 0), v(x, y, 1), v(x, y, 2)) - 1));
            if (x > 0) inside = Math.max(inside, Math.abs(v(x, y, 0) - v(x - 1, y, 0)));
            if (y > 0) inside = Math.max(inside, Math.abs(v(x, y, 1) - v(x, y - 1, 1)));
        }
        seam = Math.max(seam, Math.abs(v(0, y, 0) - v(N - 1, y, 0)));
    }
    for (let x = 0; x < N; x++) seam = Math.max(seam, Math.abs(v(x, 0, 1) - v(x, N - 1, 1)));
    const ok = worstLen < 0.02 && seam <= inside * 1.05;
    if (!ok) bad++;
    console.log(`${L.file}: ${ok ? 'ok' : 'BAD'}, normal length off by ${worstLen.toFixed(4)}, seam step ${seam.toFixed(3)} (inside up to ${inside.toFixed(3)})`);
}

// ---- falling water (docs/gameplay/water_falling_v1_design.md 4) -------------------------------------------------------
//
// textures/block/liquid/water_fall.png: the sheet of water going over an edge, 32 px across, 16 frames of 32 px stacked
// (an animated block texture, .mcmeta: a frame a tick), each frame the streaks 2 px further down, so the pattern runs
// down the sheet. RGBA: near-white water, its alpha the streaks (sums of sines in whole cycles across and down, so it
// tiles both ways and between the last frame and the first): thick lines of water with thinner gaps, where the sheet
// shows through less. The client tints and fades it, whiter and more broken lower down.

const FALL = join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/liquid/water_fall.png');
const FALL_W = 32, FALL_FRAMES = 16;

function pngRgba(w, h, rgba) {
    const raw = Buffer.alloc(h * (w * 4 + 1));
    for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(w, 0);
    ihdr.writeUInt32BE(h, 4);
    ihdr[8] = 8;
    ihdr[9] = 6;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
        chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

function fallStreaks() {
    const r = rng(20261026);
    const across = [], down = [];
    for (let i = 0; i < 9; i++) across.push({ k: 1 + Math.floor(r() * 9), a: 0.4 + r() * 0.6, p: r() * Math.PI * 2 });
    for (let i = 0; i < 4; i++) down.push({ k: 1 + Math.floor(r() * 3), a: 0.3 + r() * 0.4, p: r() * Math.PI * 2 });
    const h = FALL_W * FALL_FRAMES, px = Buffer.alloc(FALL_W * h * 4);
    for (let f = 0; f < FALL_FRAMES; f++) {
        for (let y = 0; y < FALL_W; y++) {
            const v = ((y - f * 2) % FALL_W + FALL_W) % FALL_W / FALL_W;
            for (let x = 0; x < FALL_W; x++) {
                const u = x / FALL_W;
                let s = 0, n = 0;
                for (const w of across) { s += w.a * Math.sin(2 * Math.PI * w.k * u + w.p); n += w.a; }
                // the streaks wander a little down the sheet
                let d = 0;
                for (const w of down) d += w.a * Math.sin(2 * Math.PI * (w.k * v + 2 * u) + w.p);
                const t = Math.max(0, Math.min(1, 0.5 + 0.5 * s / n + 0.18 * d));
                const a = 0.45 + 0.55 * t * t;
                const i = ((f * FALL_W + y) * FALL_W + x) * 4;
                px[i] = 236; px[i + 1] = 244; px[i + 2] = 248; px[i + 3] = Math.round(a * 255);
            }
        }
    }
    return px;
}

if (!check) {
    writeFileSync(FALL, pngRgba(FALL_W, FALL_W * FALL_FRAMES, fallStreaks()));
    writeFileSync(FALL + '.mcmeta', JSON.stringify({ animation: { frametime: 1, interpolate: false } }, null, 2) + '\n');
    console.log(`water_fall.png: ${FALL_W} px, ${FALL_FRAMES} frames`);
} else {
    const b = readFileSync(FALL);
    const ok = b.readUInt32BE(16) === FALL_W && b.readUInt32BE(20) === FALL_W * FALL_FRAMES;
    if (!ok) bad++;
    console.log(`water_fall.png: ${ok ? 'ok' : 'BAD'}, ${b.readUInt32BE(16)} x ${b.readUInt32BE(20)}`);
}
// textures/block/liquid/wet_mask.png: wet ground (docs/gameplay/water_falling_v1_design.md 13), 64 px for 4 m (the
// game's 16 px a metre), tiling. White; its alpha the blotches wet ground dries in (sums of sines in whole cycles across
// the tile, so it tiles), 0.45 to 1: inside a wet patch it mottles a little, at the patch's edge (the client's alpha
// falling off over a metre) it breaks up into blotches instead of a smooth ring.

const WET = join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/block/liquid/wet_mask.png');
const WET_W = 64;

function wetMask() {
    const r = rng(20261025);
    const waves = [];
    for (let i = 0; i < 12; i++) {
        waves.push({ kx: Math.floor(r() * 7) - 3, kz: Math.floor(r() * 7) - 3, a: 0.3 + r() * 0.7, p: r() * Math.PI * 2 });
    }
    const px = Buffer.alloc(WET_W * WET_W * 4);
    for (let z = 0; z < WET_W; z++) {
        for (let x = 0; x < WET_W; x++) {
            let s = 0, n = 0;
            for (const w of waves) {
                if (w.kx === 0 && w.kz === 0) continue;
                s += w.a * Math.sin(2 * Math.PI * (w.kx * x + w.kz * z) / WET_W + w.p);
                n += w.a;
            }
            const t = Math.max(0, Math.min(1, 0.5 + 1.4 * s / n));
            const a = 0.45 + 0.55 * t * t * (3 - 2 * t);
            const i = (z * WET_W + x) * 4;
            px[i] = 255; px[i + 1] = 255; px[i + 2] = 255; px[i + 3] = Math.round(a * 255);
        }
    }
    return px;
}

if (!check) {
    writeFileSync(WET, pngRgba(WET_W, WET_W, wetMask()));
    console.log(`wet_mask.png: ${WET_W} px`);
} else {
    const b = readFileSync(WET);
    const ok = b.readUInt32BE(16) === WET_W && b.readUInt32BE(20) === WET_W;
    if (!ok) bad++;
    console.log(`wet_mask.png: ${ok ? 'ok' : 'BAD'}, ${b.readUInt32BE(16)} x ${b.readUInt32BE(20)}`);
}
if (check && bad) process.exit(1);
