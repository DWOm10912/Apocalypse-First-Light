// Wound screen effects V2, round 2 (user 2026-10-20: no sting visuals, no edge flashing): infection = an occasional blur
// spell; suppuration (high fever) = steady washed colours + light sensitivity (bright areas bloom), more and stronger blur
// spells with a slow dizzy sway, and a chills burst (the view shivers). Offline preview on a game screenshot.
// node render2.cjs <base.png> <outdir>
const {execFileSync, spawnSync} = require('child_process');
const fs = require('fs'), path = require('path');
const [base, outDir] = process.argv.slice(2);
const W = 720, H = 405, FPS = 20;
const raw = execFileSync('ffmpeg', ['-v', 'error', '-i', base, '-vf', `crop=1956:1100:300:0,scale=${W}:${H}:flags=area`, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], {maxBuffer: 1 << 26});
const src = new Float32Array(W * H * 3);
for (let i = 0; i < src.length; i++) src[i] = raw[i] / 255;
const clamp = (a, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, a));
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const env = (t, a, b, inT, outT) => smooth(a, a + inT, t) * (1 - smooth(b - outT, b, t));

function sample(out, ang, dx, dy, zoom) {
    const c = Math.cos(ang), s = Math.sin(ang), cx = W / 2, cy = H / 2;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        const px = x - cx - dx, py = y - cy - dy;
        const sx = clamp((c * px + s * py) / zoom + cx, 0, W - 1.001), sy = clamp((-s * px + c * py) / zoom + cy, 0, H - 1.001);
        const x0 = sx | 0, y0 = sy | 0, fx = sx - x0, fy = sy - y0, o = (y * W + x) * 3;
        for (let k = 0; k < 3; k++) {
            const a = src[(y0 * W + x0) * 3 + k], b = src[(y0 * W + x0 + 1) * 3 + k], cc = src[((y0 + 1) * W + x0) * 3 + k], d = src[((y0 + 1) * W + x0 + 1) * 3 + k];
            out[o + k] = (a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + d * fx) * fy;
        }
    }
}
// box blur (three passes ~ gaussian) of a float rgb image, radius r px (fractional: blends r and r+1)
const tmp = new Float32Array(W * H * 3);
function box(img, r, horiz) {
    const n = horiz ? W : H, m = horiz ? H : W, line = new Float32Array(n * 3);
    for (let j = 0; j < m; j++) {
        for (let i = 0; i < n; i++) { const o = horiz ? (j * W + i) * 3 : (i * W + j) * 3; line[i * 3] = img[o]; line[i * 3 + 1] = img[o + 1]; line[i * 3 + 2] = img[o + 2]; }
        for (let k = 0; k < 3; k++) {
            let sum = 0;
            for (let i = -r; i <= r; i++) sum += line[clamp(i, 0, n - 1) * 3 + k];
            for (let i = 0; i < n; i++) {
                const o = horiz ? (j * W + i) * 3 : (i * W + j) * 3;
                img[o + k] = sum / (2 * r + 1);
                sum += line[Math.min(i + r + 1, n - 1) * 3 + k] - line[Math.max(i - r, 0) * 3 + k];
            }
        }
    }
}
function blur(img, radius) {
    if (radius < 0.3) return;
    const r = Math.floor(radius), f = radius - r;
    tmp.set(img);
    for (let p = 0; p < 3; p++) { box(img, Math.max(1, r), true); box(img, Math.max(1, r), false); }
    if (r < 1) for (let i = 0; i < img.length; i++) img[i] = tmp[i] + (img[i] - tmp[i]) * radius;
    else if (f > 0.01) { const b = new Float32Array(tmp); for (let p = 0; p < 3; p++) { box(b, r + 1, true); box(b, r + 1, false); } for (let i = 0; i < img.length; i++) img[i] += (b[i] - img[i]) * f; }
}
function grade(img, desat, wash, warm) {
    for (let i = 0; i < img.length; i += 3) {
        const l = 0.2126 * img[i] + 0.7152 * img[i + 1] + 0.0722 * img[i + 2];
        for (let k = 0; k < 3; k++) { let v = img[i + k] + (l - img[i + k]) * desat; v += (0.8 - v) * wash; img[i + k] = v; }
        img[i] += warm; img[i + 2] -= warm;
    }
}
// light sensitivity: what is bright spreads into a soft haze around it (no flicker)
const bright = new Float32Array(W * H * 3);
function photophobia(img, amount, radius) {
    for (let i = 0; i < img.length; i += 3) {
        const l = 0.2126 * img[i] + 0.7152 * img[i + 1] + 0.0722 * img[i + 2], w = smooth(0.55, 0.9, l);
        for (let k = 0; k < 3; k++) bright[i + k] = img[i + k] * w;
    }
    blur(bright, radius);
    for (let i = 0; i < img.length; i++) img[i] = img[i] + bright[i] * amount * (1 - img[i] * 0.5);
}

const EFFECTS = {
    // 局部感染: a blur spell (in 1.2 s, hold, out 1.5 s), the focus drifting a little while it lasts; clear otherwise
    infection: {len: 8, frame(t, img) {
        const spell = env(t, 1.5, 6.0, 1.2, 1.5);
        sample(img, 0, 0, 0, 1);
        blur(img, spell * (2.1 + 0.5 * Math.sin(t * 2.3)));
    }},
    // 化脓 (high fever): colours flat and a little warm, bright areas bloom (both steady); a stronger blur spell with the
    // view swaying slowly (dizzy); a chills burst near the end (the view shivers for 1.2 s)
    suppuration: {len: 10, frame(t, img) {
        const spell = env(t, 1.0, 6.2, 1.0, 1.6), chills = env(t, 7.2, 8.6, 0.15, 0.3);
        const sway = spell * Math.sin((t - 1.0) * 1.3);
        const shiver = chills * (Math.sin(t * 61) * 0.6 + Math.sin(t * 43 + 1.3) * 0.4);
        sample(img, 0.016 * sway + 0.004 * shiver, 5 * sway + 1.6 * shiver, 1.5 * spell * Math.sin((t - 1.0) * 2.1) + 1.2 * chills * Math.sin(t * 53), 1 + 0.006 * spell);
        grade(img, 0.25, 0.06, 0.015);
        photophobia(img, 0.55, 10);
        blur(img, 0.8 + spell * (3.4 + 0.8 * Math.sin(t * 1.9)));
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
        '-vf', 'split[a][b];[a]palettegen=max_colors=224:stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a', '-loop', '0', out], {input: buf, maxBuffer: 1 << 26});
    if (r.status) throw new Error(String(r.stderr));
    console.log(name, frames, 'frames', (fs.statSync(out).size / 1e6).toFixed(1), 'MB');
}
