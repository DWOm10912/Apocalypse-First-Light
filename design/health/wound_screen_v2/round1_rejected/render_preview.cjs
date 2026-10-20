// Offline preview of the wound screen effects V2 (scratch sting / infection throb / suppuration fever) on a game screenshot.
// node render.cjs <base.png> <outdir>   -> scratch.gif, infection.gif, suppuration.gif
const {execFileSync, spawnSync} = require('child_process');
const fs = require('fs'), path = require('path');
const [base, outDir] = process.argv.slice(2);
const W = 720, H = 405, FPS = 20, CELL = 3;              // one effect pixel = W / 240 (the frost frame's grid)
const raw = execFileSync('ffmpeg', ['-v', 'error', '-i', base, '-vf', `crop=1956:1100:300:0,scale=${W}:${H}:flags=area`, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], {maxBuffer: 1 << 26});
const src = new Float32Array(W * H * 3);
for (let i = 0; i < src.length; i++) src[i] = raw[i] / 255;

const GX = Math.ceil(W / CELL), GY = Math.ceil(H / CELL);
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
// per cell: the edge distance (0 centre .. 1+ corners, the screen's aspect kept round) with a ragged pixel jitter
const edge = new Float32Array(GX * GY);
for (let y = 0; y < GY; y++) for (let x = 0; x < GX; x++) {
    const u = ((x + 0.5) / GX) * 2 - 1, v = ((y + 0.5) / GY) * 2 - 1;
    const r = Math.pow(Math.pow(Math.abs(u), 2.6) + Math.pow(Math.abs(v) * 0.92, 2.6), 1 / 2.6);   // a rounded rectangle
    edge[y * GX + x] = r + (hash(x >> 2, y >> 2, 7) - 0.5) * 0.035;
}
const clamp = (a, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, a));
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const step4 = a => Math.round(clamp(a) * 4) / 4;           // pixel-art: four alpha steps
// a beat: fast rise, slower fall
const beat = (t, at, len) => { const d = t - at; return d < 0 || d > len ? 0 : d < 0.06 ? d / 0.06 : Math.exp(-(d - 0.06) / (len * 0.35)); };
const pulse = (t, period, second) => { const ph = ((t % period) + period) % period; return Math.max(beat(ph, 0, period * 0.7), second * beat(ph, 0.16, period * 0.6)); };
// a window envelope over [a, b] with ease in / out
const env = (t, a, b, ease) => smooth(a, a + ease, t) * (1 - smooth(b - ease, b, t));

function sample(out, ang, dx, dy, zoom) {
    const c = Math.cos(ang), s = Math.sin(ang), cx = W / 2, cy = H / 2;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        const px = x - cx - dx, py = y - cy - dy;
        let sx = (c * px + s * py) / zoom + cx, sy = (-s * px + c * py) / zoom + cy;
        sx = clamp(sx, 0, W - 1.001); sy = clamp(sy, 0, H - 1.001);
        const x0 = sx | 0, y0 = sy | 0, fx = sx - x0, fy = sy - y0, o = (y * W + x) * 3;
        for (let k = 0; k < 3; k++) {
            const a = src[(y0 * W + x0) * 3 + k], b = src[(y0 * W + x0 + 1) * 3 + k], cc = src[((y0 + 1) * W + x0) * 3 + k], d = src[((y0 + 1) * W + x0 + 1) * 3 + k];
            out[o + k] = (a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + d * fx) * fy;
        }
    }
}
function grade(img, desat, wash) {   // desaturate toward luma, then wash toward a pale grey (fever: colours go flat)
    for (let i = 0; i < img.length; i += 3) {
        const l = 0.2126 * img[i] + 0.7152 * img[i + 1] + 0.0722 * img[i + 2];
        for (let k = 0; k < 3; k++) { let v = img[i + k] + (l - img[i + k]) * desat; v = v + (0.78 - v) * wash; img[i + k] = v; }
    }
}
// lay a colour over cells: alpha(cellX, cellY, edgeValue) -> 0..1 (stepped)
function overlay(img, col, alphaOf) {
    for (let gy = 0; gy < GY; gy++) for (let gx = 0; gx < GX; gx++) {
        const a = step4(alphaOf(gx, gy, edge[gy * GX + gx]));
        if (a <= 0) continue;
        for (let y = gy * CELL; y < Math.min(H, gy * CELL + CELL); y++) for (let x = gx * CELL; x < Math.min(W, gx * CELL + CELL); x++) {
            const o = (y * W + x) * 3;
            for (let k = 0; k < 3; k++) img[o + k] += (col[k] - img[o + k]) * a;
        }
    }
}

const EFFECTS = {
    // 抓伤: nothing between stings; a sting = a pale flash in from the edge and a small flinch of the view (0.4 s)
    scratch: {len: 6, frame(t, img) {
        const s = Math.max(beat(t, 1.0, 0.45), 0.8 * beat(t, 3.9, 0.45));
        sample(img, -0.004 * s, 3 * s, 2 * s, 1 + 0.008 * s);
        overlay(img, [0.96, 0.9, 0.84], (gx, gy, e) => s * 0.42 * smooth(0.86, 1.06, e));
    }},
    // 局部感染: the edges darken with each heartbeat (lub-dub, 84 bpm), colours a little flat; low and steady
    infection: {len: 6, frame(t, img) {
        sample(img, 0, 0, 0, 1);
        grade(img, 0.12, 0.03);
        const p = pulse(t, 60 / 84, 0.6);
        overlay(img, [0.16, 0.11, 0.09], (gx, gy, e) => (0.30 + 0.30 * p) * smooth(0.70 - 0.05 * p, 1.05, e));
    }},
    // 化脓: faster heavier pulse, colours flat and washed; the eyelids sag (2.4-4.4 s); a dizzy spell (5.6-9 s): the view
    // greys in from the edges and sways
    suppuration: {len: 10, frame(t, img) {
        const lid = env(t, 2.4, 4.4, 0.8), dizzy = env(t, 5.6, 9.0, 1.0);
        const sway = dizzy * Math.sin((t - 5.6) * 1.9);
        sample(img, 0.022 * sway, 6 * sway, 2 * dizzy * Math.sin((t - 5.6) * 3.1), 1 + 0.01 * dizzy);
        grade(img, 0.30 + 0.15 * dizzy, 0.10 + 0.06 * dizzy);
        const p = pulse(t, 60 / 104, 0.7);
        overlay(img, [0.13, 0.10, 0.09], (gx, gy, e) => (0.42 + 0.32 * p + 0.3 * dizzy) * smooth(0.62 - 0.05 * p - 0.28 * dizzy, 1.0 - 0.15 * dizzy, e));
        // eyelids: top band down to 24 %, bottom up to 10 %, a ragged pixel lid line
        overlay(img, [0.10, 0.08, 0.07], (gx, gy) => {
            const v = (gy + 0.5) / GY, j = (hash(gx >> 2, 11, 5) - 0.5) * 0.012, bow = 0.11 * Math.pow(((gx + 0.5) / GX) * 2 - 1, 2);
            const top = lid * (0.19 - bow) + j, bottom = 1 - lid * (0.08 - bow * 0.6) - j;   // lid lines arch: lowest / highest in the middle
            return lid * Math.max(0.92 * smooth(top + 0.07, top - 0.01, v), 0.85 * smooth(bottom - 0.05, bottom + 0.01, v));
        });
    }},
};

for (const [name, fx] of Object.entries(EFFECTS)) {
    const frames = Math.round(fx.len * FPS), buf = Buffer.alloc(W * H * 3 * frames), img = new Float32Array(W * H * 3);
    for (let f = 0; f < frames; f++) {
        fx.frame(f / FPS, img);
        const o = f * W * H * 3;
        for (let i = 0; i < img.length; i++) buf[o + i] = Math.round(clamp(img[i]) * 255);
    }
    const out = path.join(outDir, name + '.gif');
    const r = spawnSync('ffmpeg', ['-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', `${W}x${H}`, '-r', String(FPS), '-i', '-',
        '-vf', 'split[a][b];[a]palettegen=max_colors=192:stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4', '-loop', '0', out], {input: buf, maxBuffer: 1 << 26});
    if (r.status) throw new Error(String(r.stderr));
    console.log(name, frames, 'frames', (fs.statSync(out).size / 1e6).toFixed(1), 'MB');
}
