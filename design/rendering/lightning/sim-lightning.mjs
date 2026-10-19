// Realistic lightning concept (2026-10-18): the channel generator the game would use, rendered offline as stills and a
// short animation of whole flashes, so the user can judge the shape and the timing before any game code.
//
//   node design/rendering/lightning/sim-lightning.mjs            stills + animation (needs ffmpeg on PATH)
//
// What it follows (sources in docs/rendering/lightning_v1.md):
// - the channel: straight pieces tens of metres long (about N(20 m, 10 m)), each bent ~16 deg (sd 11.5) off the leader's
//   heading, which itself wanders; finer wiggles inside each piece. The stepped leader forks as it comes down, most often
//   high up, so branches leave the channel downward, thinner and dimmer; most die out in the air, now and then one reaches
//   the ground too (1.7 ground contacts a flash on average);
// - the flash: the first return stroke lights the channel and every branch at once; then 1-4 more strokes 30-120 ms apart
//   come down the same channel without the branches (the dart leaders follow the conditioned path, unbranched), each
//   weaker; now and then one keeps glowing for ~100 ms (continuing current); between strokes the channel goes dark;
// - the scene: a night overcast, the cloud base lit from below round the channel, rain streaks, a tree line.
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const OUT = path.dirname(fileURLToPath(import.meta.url));
const W = 1280, H = 720;

// ---- random ----
function rng(seed) {
  let s = (seed * 2654435761) >>> 0 || 1;
  return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}
const gauss = r => Math.sqrt(-2 * Math.log(1 - r())) * Math.cos(2 * Math.PI * r());

// ---- vectors ----
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const len = a => Math.hypot(a[0], a[1], a[2]);
const norm = a => mul(a, 1 / len(a));
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const DEG = Math.PI / 180;
/** Two unit vectors square to d. */
function basis(d) {
  const t = norm(cross(d, Math.abs(d[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0]));
  return [t, cross(d, t)];
}
/** d turned by angle off itself, toward a random side. */
function bend(d, angle, r) {
  const [t, b] = basis(d), az = 2 * Math.PI * r();
  return norm(add(mul(d, Math.cos(angle)), mul(add(mul(t, Math.cos(az)), mul(b, Math.sin(az))), Math.sin(angle))));
}

/**
 * One flash's channels in metres (y up, the ground at 0, the cloud base at `top`): segments
 * {a, b, w (relative width), k (relative brightness), main (on the stroke channel), y (for the leader's reveal)}.
 */
function channel(seed, top, aim) {
  const r = rng(seed), segs = [];
  let groundBranches = r() < 0.4 ? 1 : 0;   // a branch that reaches the ground as well
  function leader(p, heading, o) {
    const {main, width, bright, budget, depth, target} = o;
    let travelled = 0;
    while (travelled < budget && p[1] > 0.01) {
      const step = 17 * Math.exp(0.5 * gauss(r));
      // the heading wanders over hundreds of metres, held downward; the main one drifts toward its ground point
      heading = bend(heading, Math.abs(gauss(r)) * 9 * DEG, r);
      if (target) heading = norm(add(heading, mul(norm(sub(target, p)), 0.06)));
      if (heading[1] > -0.35) heading = norm([heading[0], -0.35, heading[2]]);
      // each piece bent off it: |N(16, 11.5)| deg
      let d = bend(heading, Math.abs(16 + 11.5 * gauss(r)) * DEG, r);
      if (d[1] > 0.1) d = norm([d[0], 0.1, d[2]]);
      let q = add(p, mul(d, step));
      if (q[1] < 0) q = add(p, mul(d, p[1] / -d[1]));
      // finer wiggle inside the piece (3 sub-pieces)
      const [t, b] = basis(d);
      let prev = p;
      for (let k = 1; k <= 3; k++) {
        let m = add(p, mul(sub(q, p), k / 3));
        if (k < 3) m = add(m, add(mul(t, gauss(r) * 0.07 * step), mul(b, gauss(r) * 0.07 * step)));
        const taper = main ? 1 : Math.max(0.2, 1 - (travelled + step * k / 3) / budget);
        segs.push({a: prev, b: m, w: width * (0.4 + 0.6 * taper), k: bright * (main ? 1 : 0.35 + 0.65 * taper), main, y: m[1]});
        prev = m;
      }
      travelled += step;
      p = q;
      // forks: the stepped leader branches as it comes down, mostly in its upper part
      const high = p[1] / top;
      const chance = main ? 0.03 + 0.11 * high * high : 0.05 / depth;
      if (depth < 3 && p[1] > 40 && r() < chance) {
        const toGround = main && groundBranches > 0 && high < 0.6 && r() < 0.5;
        if (toGround) groundBranches--;
        const off = bend(d, (20 + 35 * r()) * DEG, r);
        off[1] = Math.min(off[1], -0.2);
        leader(p, norm(off), {
          main: false, width: (main ? 0.5 : 0.7) * width, bright: (main ? 0.5 : 0.7) * bright, depth: depth + 1,
          budget: toGround ? p[1] * 3 : Math.min(p[1] * 1.2, top * (0.05 + 0.3 * r() * r()) / depth ** 0.5),
          target: toGround ? [p[0] + (r() - 0.5) * p[1], 0, p[2] + (r() - 0.5) * p[1]] : null});
      }
    }
  }
  const ground = [aim[0] + (r() - 0.5) * 300, 0, aim[2] + (r() - 0.5) * 300];
  const start = [ground[0] + (r() - 0.5) * 0.5 * top, top, ground[2] + (r() - 0.5) * 0.3 * top];
  leader(start, norm(sub(ground, start)), {main: true, width: 1, bright: 1, budget: top * 4, depth: 0, target: ground});
  return {segs, start};
}

/** The flash's strokes: {t (s), k (brightness), branches (lit too), hold (continuing glow, s)}. */
function strokes(seed) {
  const r = rng(seed * 7 + 3), out = [{t: 0, k: 1, branches: true, hold: 0.02 + 0.03 * r()}];
  const n = 1 + Math.floor(r() * 4);   // 2-5 strokes in all
  let t = 0;
  for (let i = 0; i < n; i++) {
    t += 0.035 + 0.085 * r();
    out.push({t, k: 0.4 + 0.4 * r(), branches: false, hold: r() < 0.25 ? 0.08 + 0.1 * r() : 0.008 + 0.015 * r()});
  }
  return out;
}

/** Brightness of the main channel, of the branches, and of the sky t s after the flash began. */
function light(list, t) {
  let main = 0, branch = 0;
  for (const s of list) {
    const dt = t - s.t;
    if (dt < -0.002) continue;
    // the stroke itself (one frame at 60 fps), then its glow: the hold, then a ~12 ms fade
    const v = dt < 0.017 ? 1 : dt < s.hold ? 0.5 : 0.5 * Math.exp(-(dt - Math.max(s.hold, 0.017)) / 0.012);
    main = Math.max(main, s.k * v);
    if (s.branches) branch = Math.max(branch, s.k * (dt < 0.017 ? 1 : 0.35 * Math.exp(-(dt - 0.017) / 0.01)));
  }
  return {main, branch, sky: Math.max(main, branch * 0.7)};
}

// ---- camera ----
/** At eye height 1.7 m at the origin looking along -z, pitched up; returns [x px, y px, depth m] (depth <= 0: behind). */
function camera(fovDeg, pitchDeg) {
  const f = (H / 2) / Math.tan(fovDeg * DEG / 2), c = Math.cos(pitchDeg * DEG), s = Math.sin(pitchDeg * DEG);
  const project = p => {
    const y = p[1] - 1.7, z = -p[2];
    const yc = y * c - z * s, zc = y * s + z * c;
    return [W / 2 + f * p[0] / zc, H / 2 - f * yc / zc, zc];
  };
  /** The view ray through pixel (x, y), world space. */
  const ray = (x, y) => {
    const cx = (x - W / 2) / f, cy = -(y - H / 2) / f;
    return [cx, cy * c + s, -(-cy * s + c)];
  };
  return {f, project, ray, horizon: H / 2 + f * Math.tan(pitchDeg * DEG)};
}

// ---- noise ----
function valueNoise(seed) {
  const r = rng(seed), P = new Float32Array(256 * 256);
  for (let i = 0; i < P.length; i++) P[i] = r();
  const at = (x, y) => P[(y & 255) * 256 + (x & 255)];
  const sm = t => t * t * (3 - 2 * t);
  const n = (x, y) => {
    const xi = Math.floor(x), yi = Math.floor(y), fx = sm(x - xi), fy = sm(y - yi);
    return (at(xi, yi) * (1 - fx) + at(xi + 1, yi) * fx) * (1 - fy) + (at(xi, yi + 1) * (1 - fx) + at(xi + 1, yi + 1) * fx) * fy;
  };
  return (x, y) => { let v = 0, a = 0.5; for (let o = 0; o < 5; o++) { v += a * n(x, y); x *= 2.03; y *= 2.03; a *= 0.5; } return v; };
}

/** Box blur (three passes ~ a gaussian of sigma), in place. */
function blur(src, sigma) {
  const rad = Math.max(1, Math.round(sigma * 0.87)), tmp = new Float32Array(src.length), inv = 1 / (2 * rad + 1);
  for (let pass = 0; pass < 3; pass++) {
    for (let y = 0; y < H; y++) {
      const o = y * W;
      let acc = 0;
      for (let x = -rad; x <= rad; x++) acc += src[o + Math.min(W - 1, Math.max(0, x))];
      for (let x = 0; x < W; x++) {
        tmp[o + x] = acc * inv;
        acc += src[o + Math.min(W - 1, x + rad + 1)] - src[o + Math.max(0, x - rad)];
      }
    }
    for (let x = 0; x < W; x++) {
      let acc = 0;
      for (let y = -rad; y <= rad; y++) acc += tmp[Math.min(H - 1, Math.max(0, y)) * W + x];
      for (let y = 0; y < H; y++) {
        src[y * W + x] = acc * inv;
        acc += tmp[Math.min(H - 1, y + rad + 1) * W + x] - tmp[Math.max(0, y - rad) * W + x];
      }
    }
  }
}

/** The channel's core into a luminance buffer: anti-aliased lines, 1.2 m wide at full width (never under a pixel). */
function drawChannel(buf, segs, project, L, reveal, scale) {
  for (const s of segs) {
    const k = (s.main ? L.main : L.branch) * s.k;
    if (k <= 0.002 || s.y < reveal) continue;
    const a = project(s.a), b = project(s.b);
    if (a[2] <= 1 || b[2] <= 1) continue;
    const px = scale * s.w * 1.2 * 623 / ((a[2] + b[2]) / 2), w = Math.max(1, px), kk = k * Math.min(1, px) * 5;
    const n = Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) * 2) + 1, rr = w / 2 + 1;
    for (let i = 0; i <= n; i++) {
      const x = a[0] + (b[0] - a[0]) * i / n, y = a[1] + (b[1] - a[1]) * i / n;
      for (let py = Math.floor(y - rr); py <= Math.ceil(y + rr); py++) {
        if (py < 0 || py >= H) continue;
        for (let qx = Math.floor(x - rr); qx <= Math.ceil(x + rr); qx++) {
          if (qx < 0 || qx >= W) continue;
          const c = Math.min(1, Math.max(0, w / 2 + 0.5 - Math.hypot(qx - x, py - y)));
          if (c > 0) buf[py * W + qx] = Math.max(buf[py * W + qx], kk * c);
        }
      }
    }
  }
}

function makeScene(seed, dist, side) {
  const top = 1500;   // the cloud base (m)
  const cam = camera(62, 17);
  const aim = [side * dist * 0.35, 0, -dist];
  const {segs, start} = channel(seed, top, aim);
  // the sky: where each pixel's ray meets the cloud base, fbm there (the deck's texture in perspective)
  const noise = valueNoise(seed * 13 + 1), cloud = new Float32Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const d = cam.ray(x, y);
    if (d[1] <= 0.002) { cloud[y * W + x] = -1; continue; }
    const t = (top - 1.7) / d[1];
    const far = Math.min(1, t / 18000);   // far away the deck's texture averages out (no moire at the horizon)
    cloud[y * W + x] = 0.5 + (noise(d[0] * t / 700 + 50, d[2] * t / 700 + 50) - 0.5) * (1 - far) ** 2;
  }
  // the tree line: two layers of spruce silhouettes (px above the horizon per column)
  const r = rng(seed * 5 + 9), trees = new Float32Array(W);
  for (const [count, hMin, hMax] of [[260, 4, 11], [70, 10, 26]]) {
    for (let i = 0; i < count; i++) {
      const x0 = r() * W, h = hMin + (hMax - hMin) * r(), w = h * (0.22 + 0.1 * r());
      for (let x = Math.floor(x0 - w); x <= Math.ceil(x0 + w); x++) {
        if (x < 0 || x >= W) continue;
        const u = Math.abs(x - x0) / w, tier = 1 - 0.25 * ((h * (1 - u) * 0.35) % 1);
        trees[x] = Math.max(trees[x], h * (1 - u) * (u < 0.08 ? 1 : tier));
      }
    }
  }
  const tp = cam.project(start);
  return {seed, top, segs, list: strokes(seed), cam, cloud, trees, topPx: tp};
}

/** The scene and the flash on it at t s, as RGB bytes. */
function frame(sc, t, opts = {}) {
  const {segs, list, cam, cloud, trees, topPx} = sc, hy = cam.horizon;
  const L = light(list, t), core = new Float32Array(W * H);
  const leaderT = opts.leader ?? -1;
  if (leaderT > 0 && t < 0 && t > -leaderT) {
    // the stepped leader: faint, coming down from the cloud over ~30 ms (the camera barely catches it)
    drawChannel(core, segs, cam.project, {main: 0.06, branch: 0.06}, sc.top * (-t / leaderT), 0.5);
  } else drawChannel(core, segs, cam.project, L, -1, 1);
  const h1 = Float32Array.from(core), h2 = Float32Array.from(core), h3 = Float32Array.from(core);
  blur(h1, 2.5);
  blur(h2, 14);
  blur(h3, 70);
  const sky = L.sky, r = rng(sc.seed * 31 + Math.round(t * 1000) + 7);
  const lum = new Float32Array(W * H * 3);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, cl = cloud[i];
    let R, G, B;
    if (cl >= 0) {
      // night overcast: almost black, a touch lighter low down; in the flash the deck shows its texture, brightest
      // round the channel and above its top (the cloud the flash comes out of)
      const low = Math.max(0, Math.min(1, 1 - (hy - y) / hy));
      const base = 0.006 + 0.01 * low;
      const dx = (x - topPx[0]) / 330, dy = (y - topPx[1]) / 200, blob = Math.exp(-(dx * dx + dy * dy)) * L.main;
      const dens = Math.max(0, cl - 0.25) * 1.8;
      const lit = sky * (0.015 + 0.05 * dens) + (blob * 0.8 + h3[i] * 6) * (0.2 + dens);
      R = base + lit * 0.78; G = base + lit * 0.8; B = base * 1.4 + lit * 1.0;
    } else {
      // wet ground in the dark; a dim wash in the flash and a soft reflection of the channel
      const g = 0.0015 + sky * 0.004, my = Math.round(2 * hy - y);
      const m = my >= 0 && my < H ? (h2[my * W + x] * 0.12 + h3[my * W + x] * 1.5) * Math.max(0, 1 - (y - hy) / 140) : 0;
      R = g + m * 0.8; G = g + m * 0.85; B = g * 1.2 + m;
    }
    // the channel: white core, a blue-violet glow round it (scattered by the rain)
    const c = core[i];
    R += c + h1[i] * 0.8 + h2[i] * 1.2 + h3[i] * 2;
    G += c + h1[i] * 0.85 + h2[i] * 1.25 + h3[i] * 2.1;
    B += c * 1.05 + h1[i] * 1.1 + h2[i] * 1.7 + h3[i] * 3;
    lum[i * 3] = R; lum[i * 3 + 1] = G; lum[i * 3 + 2] = B;
  }
  // rain: short slanted streaks, only seen in the flash
  if (sky > 0.03) {
    for (let n = 0; n < 1800; n++) {
      const x0 = r() * W, y0 = r() * H, l = 8 + 22 * r(), v = sky * (0.006 + 0.02 * r()) * (y0 > hy ? 0.4 : 1);
      for (let k = 0; k < l; k++) {
        const x = Math.round(x0 + k * 0.12), y = Math.round(y0 + k);
        if (x >= 0 && x < W && y >= 0 && y < H) { const i = (y * W + x) * 3; lum[i] += v; lum[i + 1] += v; lum[i + 2] += v * 1.1; }
      }
    }
  }
  // the tree line, black against the lit sky
  for (let x = 0; x < W; x++) {
    for (let y = Math.floor(hy - trees[x]); y <= Math.ceil(hy) + 1; y++) {
      if (y < 0 || y >= H) continue;
      const i = (y * W + x) * 3, g = 0.002 + sky * 0.004;
      lum[i] = g; lum[i + 1] = g; lum[i + 2] = g * 1.3;
    }
  }
  const out = Buffer.alloc(W * H * 3);
  for (let i = 0; i < lum.length; i++) out[i] = Math.round(255 * Math.pow(1 - Math.exp(-lum[i] * 2.2), 1 / 2.2));
  return out;
}

// ---- output ----
function ffmpeg(args, input) {
  return new Promise((ok, fail) => {
    const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', ...args], {cwd: OUT, stdio: ['pipe', 'inherit', 'inherit']});
    ff.on('exit', c => c === 0 ? ok() : fail(new Error('ffmpeg ' + c)));
    ff.stdin.end(input);
  });
}
/** An RGB buffer to PNG, with captions (Chinese text through text files, drawn by ffmpeg). */
async function png(file, rgb, w, h, labels = []) {
  const filters = labels.map((l, n) => {
    fs.writeFileSync(path.join(OUT, `_label${n}.txt`), l.text);
    return `drawtext=fontfile='C\\:/Windows/Fonts/msyh.ttc':textfile=_label${n}.txt:x=${l.x}:y=${l.y}:fontsize=${l.size ?? 22}:fontcolor=white@0.85:box=1:boxcolor=black@0.45:boxborderw=6`;
  });
  await ffmpeg(['-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', `${w}x${h}`, '-i', '-', ...(filters.length ? ['-vf', filters.join(',')] : []), file], rgb);
  labels.forEach((_, n) => fs.rmSync(path.join(OUT, `_label${n}.txt`)));
}
/** Frames at half size in a grid. */
function sheet(frames, cols) {
  const w = W / 2, h = H / 2, rows = Math.ceil(frames.length / cols), out = Buffer.alloc(cols * w * rows * h * 3, 0);
  frames.forEach((f, n) => {
    const ox = (n % cols) * w, oy = Math.floor(n / cols) * h;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) for (let c = 0; c < 3; c++) {
      const a = ((2 * y) * W + 2 * x) * 3 + c, b = a + W * 3;
      out[((oy + y) * cols * w + ox + x) * 3 + c] = (f[a] + f[a + 3] + f[b] + f[b + 3]) >> 2;
    }
  });
  return {buf: out, w: cols * w, h: rows * h};
}

// 1. six flashes at their first stroke: the shape
const shapeScenes = [[11, 1500, -0.2], [23, 1900, 0.3], [37, 1400, 0], [58, 2300, -0.4], [71, 1700, 0.25], [94, 1300, -0.1]];
const s1 = sheet(shapeScenes.map(([s, d, x]) => frame(makeScene(s, d, x), 0.005)), 3);
await png('concept_1_shapes.png', s1.buf, s1.w, s1.h, shapeScenes.map(([s, d], n) => ({x: (n % 3) * 640 + 10, y: Math.floor(n / 3) * 360 + 10, size: 18, text: `${(d / 1000).toFixed(1)} km`})));
// 2. one flash at full size
const hero = makeScene(37, 1400, 0);
await png('concept_2_first_stroke.png', frame(hero, 0.005), W, H, [{x: 16, y: 16, text: '第一次回击：主通道 + 全部分叉（约 1.4 km）'}]);
// 3. the same flash through its strokes
const later = hero.list[1] ?? hero.list[0], last = hero.list[hero.list.length - 1];
const times = [[-0.012, '先导（很暗，约 30 ms）'], [0.005, '第一次回击（带分叉）'], [0.035, '余辉，分叉先灭'],
  [later.t - 0.006, '两次回击之间：通道变暗、天空变黑'], [later.t + 0.005, '后续回击：同一条通道，不带分叉'], [last.t + 0.03, '最后一次的余辉']];
const s3 = sheet(times.map(([t]) => frame(hero, t, {leader: 0.03})), 3);
await png('concept_3_timeline.png', s3.buf, s3.w, s3.h, times.map(([t, text], n) => ({x: (n % 3) * 640 + 10, y: Math.floor(n / 3) * 360 + 10, size: 18, text: `${n + 1}. ${text}  t = ${Math.round(t * 1000)} ms`})));
fs.writeFileSync(path.join(OUT, 'concept_3_timeline.txt'), 'strokes: ' + hero.list.map(s => `${Math.round(s.t * 1000)} ms x${s.k.toFixed(2)}${s.branches ? ' (branches)' : ''} hold ${Math.round(s.hold * 1000)} ms`).join('; ') + '\n');

// 4. animation: five flashes, 60 fps, a dark pause before each
const FPS = 60, clips = [[37, 1400, 0], [58, 2300, -0.4], [94, 1300, -0.1], [23, 1900, 0.3], [71, 1700, 0.25]];
const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', `${W}x${H}`, '-r', String(FPS), '-i', '-',
  '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '16', 'lightning_sim.mp4'], {cwd: OUT, stdio: ['pipe', 'inherit', 'inherit']});
const done = new Promise((ok, fail) => ff.on('exit', c => c === 0 ? ok() : fail(new Error('ffmpeg ' + c))));
for (const [seed, dist, side] of clips) {
  const sc = makeScene(seed, dist, side);
  for (let f = -Math.round(0.7 * FPS); f < Math.round(0.8 * FPS); f++) {
    const buf = frame(sc, f / FPS, {leader: 0.03});
    if (!ff.stdin.write(buf)) await new Promise(ok => ff.stdin.once('drain', ok));
  }
}
ff.stdin.end();
await done;
console.log('wrote', fs.readdirSync(OUT).filter(f => /\.(png|mp4|txt)$/.test(f)).join(', '));

// 5. the first flash again, four times slower (240 steps a second played at 60)
const slow = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', `${W}x${H}`, '-r', '60', '-i', '-',
  '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '16', 'lightning_sim_slow4x.mp4'], {cwd: OUT, stdio: ['pipe', 'inherit', 'inherit']});
const slowDone = new Promise((ok, fail) => slow.on('exit', c => c === 0 ? ok() : fail(new Error('ffmpeg ' + c))));
for (let f = -24; f < 120; f++) {
  const buf = frame(hero, f / 240, {leader: 0.03});
  if (!slow.stdin.write(buf)) await new Promise(ok => slow.stdin.once('drain', ok));
}
slow.stdin.end();
await slowDone;
console.log('wrote lightning_sim_slow4x.mp4');
