// Cabin Exterior V1 (docs/models/cabin_exterior_v1.md, 2026-10-12): the 240-texel LabPBR textures of the forest start
// home's exterior pack (tools/build-cabin-exterior-v1.mjs). AFL style: clean and quiet, not a photo; the layout and the
// mesh carry the form, colour steps stay small, relief only where the material has it (log faces, chinking, board
// grooves, corrugation, stone joints), no per-texel noise and no fine coherent lines in the normal maps (>= 5 texels).
// 240 texels = 1 m (4.17 mm per texel). Every texture tiles both ways unless it is a single-piece end (log end grain).
// Colours: the concept render's materials (design/buildings/forest_start_home_v1, scheme B): aged, oiled white oak logs
// in honey brown (user 2026-10-12: white oak, not the pale white pine), cream chinking, old green standing-seam steel,
// galvanised and rusted corrugated steel, dark brown and weathered grey board-and-batten, mortared fieldstone.
// M1.1 (user 2026-10-12): this 240 px set stays as it was. A 32 px pixel set for the logs, fieldstone and firewood and a
// denoised 240 set were tried and rejected: they blurred the forms the house relies on. New: the porch deck boards; the
// corrugation moved into the mesh (build script), the sheet textures keep the zinc / rust and shade the valleys in step.
// M1.2: the fieldstone is roughly squared rubble (the user's pick of two concepts), the porch posts' stone footing.
// 2026-10-13 (user: the cabin's white oak was far off the white oak planks and logs): the oak is the species' own wood
// now, from the trees' palette like the Wood Building Blocks; the logs keep a small aged-and-oiled warming over it.
// 2026-10-20 (user: "白栎门/窗，铁皮屋面的木纹也没有改"): the planed oak (trim, door boards, ceiling boards under the roofs,
// porch deck) samples the white oak timber photo (wood/white_oak_timber_side, tools/build-wood-photo-v1.mjs), the same
// wood as the planks; rerun these textures after that script.
// 2026-10-20 (user: "老宅原木墙、柴火、柴垛、板条墙都换成新的木纹"): the log walls, round logs and log ends, the board
// siding and the firewood stack sample the photo wood too (tools/wood-photo/sample.mjs): the layouts, chinking, grooves,
// knots, checks and relief stay; only the wood's colour and grain come from the photos.
import {png} from '../cube-slab-mesh-lib.mjs';
import {texture, base, lum} from '../wood-photo/sample.mjs';
import {N, hash, clamp, sstep, mul, mix, vn, wrapD, cells} from '../mesh-trees/hd-art.mjs';
import {OAK} from '../mesh-trees/felling-art.mjs';

const SS = 3, MM = 1000 / N;

/**
 * Rasterises f(x, y) -> {c, s (smoothness 0..255), f0 (LabPBR G, default 10: dielectric), p (porosity 0..64), h (mm),
 * ao (0..1), a (alpha 0..255, default 255), e (emission 0..1; 2026-10-14, the emergency radio's lit dial: LabPBR _s alpha
 * 0..254, left at 255 = none when no sample gives one)}, supersampled SS x SS, slopes wrapping. Like hd-art render, with F0
 * and alpha.
 * height false: _n alpha 255 (no parallax; for curved meshes and cut-outs).
 */
export function renderX(f, {height = true} = {}) {
  const col = Buffer.alloc(N * N * 4), spec = Buffer.alloc(N * N * 4), nrm = Buffer.alloc(N * N * 4), H = new Float64Array(N * N);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let c = [0, 0, 0], s = 0, f0 = 0, p = 0, h = 0, ao = 0, a = 0, e = 0, lit = false;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const r = f(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
      c = c.map((v, i) => v + r.c[i]); s += r.s; f0 += r.f0 ?? 10; p += r.p ?? 0; h += r.h ?? 0; ao += r.ao ?? 1; a += r.a ?? 255;
      if (r.e != null) { e += r.e; lit = true; }
    }
    const k = SS * SS, o = (y * N + x) * 4;
    c.forEach((v, i) => { col[o + i] = clamp(Math.round(v / k), 0, 255); }); col[o + 3] = clamp(Math.round(a / k), 0, 255);
    spec[o] = clamp(Math.round(s / k), 0, 255); spec[o + 1] = clamp(Math.round(f0 / k), 0, 255); spec[o + 2] = clamp(Math.round(p / k), 0, 64);
    spec[o + 3] = lit ? clamp(Math.round(254 * e / k), 0, 254) : 255;
    H[y * N + x] = h / k; nrm[o + 2] = clamp(Math.round(255 * ao / k), 0, 255);
  }
  const at = (i, j) => H[((j + N) % N) * N + ((i + N) % N)];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const hx = (at(x + 1, y) - at(x - 1, y)) / (2 * MM), hy = (at(x, y + 1) - at(x, y - 1)) / (2 * MM);
    let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
    const o = (y * N + x) * 4;
    nrm[o] = Math.round((nx * 0.5 + 0.5) * 255); nrm[o + 1] = Math.round((ny * 0.5 + 0.5) * 255);
    nrm[o + 3] = height ? clamp(Math.round(255 * (1 + H[y * N + x] / 250)), 0, 255) : 255;
  }
  return [png(col, N, N), png(spec, N, N), png(nrm, N, N)];
}

// ------------------------------------------------------------------------------------------------- wood

/**
 * White oak lumber (trim, posts, rails, doors, ceiling boards, the porch deck, stairs and slabs): since 2026-10-20 the
 * white oak timber photo, the planks' own wood, so the cabin's oak and the planks, timber and beams read as one wood.
 * Until 2026-10-13 the cabin had its own honey brown from the concept render (L 47..54, redder than the planks); the user
 * saw the difference in game. OAK_FACE (the trees' palette) now only feeds the logs' painted light bands.
 */
const OAK_PHOTO = texture('wood/white_oak_timber_side');
/** The planed white oak photo at (along the grain, across it) in texels (240 = 1 m; the photo's grain runs along v). */
const oak = (along, across) => OAK_PHOTO(across, along);
/** The log end (white oak end-grain photo out to the wood's edge, radius 0.47 of the sprite, no bark). */
const LOG_END = base('white_oak_log_end_240');
/** Firewood: red oak bark (240 px per metre, ridges along v), its split face (the planed photo, grain along v), its end. */
const FW_BARK = base('firewood_bark_240'), FW_SPLIT = texture('wood/red_oak_timber_side'), FW_END = base('firewood_end_240');
/** Siding: rough-sawn pine (grain along v), stained: the stain colour times the photo's relative brightness. */
const PINE = texture('wood/white_pine_timber_side');
const OAK_FACE = mix(mix(OAK.heart, OAK.cutLight, 0.12), mix(OAK.sap, OAK.cutLight, 0.15), 0.15);
/** Aged and oiled (the log walls: decades of sun and linseed): the same wood a little darker and warmer, not a new colour. */
const aged = c => [c[0] * 0.88, c[1] * 0.84, c[2] * 0.8];
/** The logs' painted parts over the photo (light bands, knots, checks): aged white oak from the trees' palette. */
export const LOG = {light: aged(mix(OAK_FACE, OAK.sap, 0.25)),
  knot: aged(mix(OAK.late, OAK.pith, 0.6)), check: aged(mix(OAK.check, OAK.pith, 0.4))};
const CHINK = [192, 184, 164];

/**
 * The log wall's face (the mesh's bulges and chinking are planar-mapped: u along the wall, v down it): three logs per
 * block, chinking centred on v 0, 1/3, 2/3 (CHINK_HALF wide each side, in step with the mesh). Each log its own tone,
 * long drawknife facets (soft tone bands along it), straight grain, a few small knots and one soft drying check.
 */
export const CHINK_HALF = 0.0215;
export function logWall() {
  const half = CHINK_HALF * N, B = N / 3, seed = 2100;
  const logs = [0, 1, 2].map(k => ({tone: 0.95 + 0.1 * hash(k, 1, seed), knots: Array.from({length: 2}, (_, j) => ({
    x: N * hash(k, 10 + j, seed), y: 0.3 + 0.4 * hash(k, 20 + j, seed), r: 5 + 4 * hash(k, 30 + j, seed)})),
    check: {x: N * hash(k, 40, seed), len: 70 + 60 * hash(k, 41, seed), y: 0.35 + 0.3 * hash(k, 42, seed)}}));
  return (x, y) => {
    const band = Math.floor(y / B), k = ((band % 3) + 3) % 3, ty = y - band * B, d = Math.min(ty, B - ty);
    if (d < half) {   // chinking: cream mortar, rough, a soft wash and a slight concave
      const t = d / half;
      return {c: mul(CHINK, 0.97 + 0.04 * vn(x, y, seed + 5, 40, 20) - 0.06 * (1 - t)), s: 40, p: 50, h: -1.2 * (1 - t * t), ao: 0.86 + 0.14 * t};
    }
    const L = logs[k], q = (ty - half) / (B - 2 * half);              // 0..1 across the log's face
    let c = aged(oak(x + 89 * k, q * (B - 2 * half) + 61 * k));
    // drawknife facets: three or four soft bands along the log
    const facet = vn(x * 0.25, q * N, seed + 20 + k, 60, 60);
    c = mul(c, L.tone * (1 + 0.035 * facet) * (1 + 0.02 * vn(x, y, seed + 30 + k, 120, 40)));
    c = mix(c, LOG.light, 0.12 * sstep(0.3, 0.9, vn(x, y, seed + 40 + k, 60, 20)));
    let h = 0.4 * facet;
    for (const kn of L.knots) {
      const dk = Math.hypot(wrapD(x - kn.x) * 0.7, (q - kn.y) * (B - 2 * half));
      if (dk < kn.r) { const t = 1 - dk / kn.r; c = mix(c, LOG.knot, 0.55 * sstep(0, 0.6, t)); h -= 0.4 * t; }
    }
    const ck = L.check, along = wrapD(x - ck.x), across = Math.abs(q - ck.y) * (B - 2 * half);
    if (along < ck.len / 2 && across < 2.5) {
      const t = (1 - across / 2.5) * sstep(0, 0.3, 1 - along / (ck.len / 2));
      c = mix(c, LOG.check, 0.6 * t);
    }
    // the edges of the face (where the log turns into the chinking) a touch darker: dirt and oil
    const edge = sstep(0, 0.12, Math.min(q, 1 - q));
    return {c: mul(c, 0.9 + 0.1 * edge), s: 92, p: 22, h, ao: 0.9 + 0.1 * edge};
  };
}

/** The round log's skin (corner log ends, v round the log): the same wood without chinking. */
export function logRound() {
  const seed = 2200;
  return (x, y) => {
    let c = aged(oak(x + 113, y + 37));
    const facet = vn(x * 0.25, y, seed + 20, 60, 40);
    c = mul(c, (1 + 0.035 * facet) * (1 + 0.02 * vn(x, y, seed + 30, 120, 40)));
    return {c, s: 92, p: 22, h: 0.4 * facet, ao: 1};
  };
}

/**
 * Log end grain, one piece, out to radius 0.47 of the sprite (the mesh maps a log's end disc there): the white oak
 * end-grain photo (its own rings, pith and checks) aged like the logs, weathered grey toward the rim, a darker edge.
 * Outside the disc: never drawn.
 */
export function logEnd() {
  return (x, y) => {
    const r = Math.hypot(x - N / 2, y - N / 2) / (N * 0.47);
    let c = mul(aged(LOG_END(x, y)), 0.97);                             // end grain drinks a little light
    c = mix(c, [150, 140, 124], 0.25 * sstep(0.6, 1.0, r));             // weathered grey toward the rim
    if (r > 0.94) c = mix(c, [92, 76, 58], sstep(0.94, 1.0, r));
    return {c, s: 60, p: 40, h: 0, ao: 1};
  };
}

/** Planed trim (casings, fascias, barge boards, linings, porch posts, rails): grain along u (vertical: along v). */
export function trim(vertical) {
  const seed = vertical ? 2410 : 2400;
  return (X, Y) => {
    const [x, y] = vertical ? [Y, X] : [X, Y];
    const c = mul(oak(x + (vertical ? 131 : 0), y + (vertical ? 97 : 0)), 1 + 0.02 * vn(x, y, seed + 3, 120, 30));
    return {c, s: 118, p: 20, h: 0, ao: 1};
  };
}

/** Vertical V-groove boards (doors): six boards per block, grain along v, a 6-texel bevelled groove at each seam. */
export function doorBoards() {
  const seed = 2500, W = N / 6, tones = Array.from({length: 6}, (_, k) => 0.95 + 0.1 * hash(k, 1, seed));
  return (x, y) => {
    const k = Math.floor(x / W) % 6, tx = x - k * W, d = Math.min(tx, W - tx);
    let c = mul(oak(y + 67 * k, tx + 53 * k), 0.96 * tones[k] * (1 + 0.02 * vn(x, y, seed + 9, 30, 120)));
    let h = 0, ao = 1;
    if (d < 3) { const t = 1 - d / 3; h = -1.6 * t; c = mul(c, 1 - 0.25 * t); ao = 1 - 0.25 * t; }
    return {c, s: 110, p: 22, h, ao};
  };
}

/** Ceiling boards under the roofs: tongue-and-groove, ten 10 cm boards across v, grain along u, a little paler (no sun). */
export function roofBoards() {
  const seed = 2600, W = N / 10, tones = Array.from({length: 10}, (_, k) => 0.95 + 0.1 * hash(k, 1, seed));
  return (x, y) => {
    const k = Math.floor(y / W) % 10, ty = y - k * W, d = Math.min(ty, W - ty);
    let c = mul(oak(x + 61 * k, ty + 29 * k), 1.03 * tones[k] * (1 + 0.02 * vn(x, y, seed + 9, 120, 24)));
    let h = 0, ao = 1;
    if (d < 2.5) { const t = 1 - d / 2.5; h = -1.2 * t; c = mul(c, 1 - 0.3 * t); ao = 1 - 0.3 * t; }
    return {c, s: 70, p: 30, h, ao};
  };
}

/**
 * Porch deck boards (M1.1, white_oak_porch_deck): eight 12.5 cm boards across v, grain along u, oiled and a little
 * weathered, its own tone per board, a 3-texel gap between the boards and one staggered butt joint per board; the gaps
 * shallow and soft in the normal map (a floor under grazing light).
 */
export function deckBoards() {
  const seed = 2650, W = N / 8, tones = Array.from({length: 8}, (_, k) => 0.94 + 0.12 * hash(k, 1, seed));
  const butts = Array.from({length: 8}, (_, k) => N * hash(k, 2, seed));
  return (x, y) => {
    const k = Math.floor(y / W) % 8, ty = y - k * W, d = Math.min(Math.min(ty, W - ty), wrapD(x - butts[k]));
    let c = mul(oak(x + 71 * k, ty + 43 * k), tones[k] * (1 + 0.02 * vn(x, y, seed + 9, 120, 24)));
    c = mix(c, [150, 140, 124], 0.1 * sstep(0.3, 1, vn(x, y, seed + 13, 60, 30)));   // weathered, a touch grey
    if (d < 1.5) return {c: [70, 54, 40], s: 30, p: 40, h: -1.5, ao: 0.6};
    let h = 0, ao = 1;
    if (d < 3) { const t = 1 - (d - 1.5) / 1.5; c = mul(c, 1 - 0.2 * t); h = -1.5 * t; ao = 1 - 0.2 * t; }
    return {c, s: 80, p: 28, h, ao};
  };
}

/**
 * Board-and-batten siding boards (the battens are mesh): rough-sawn vertical boards 25 cm wide with their joints at
 * u 1/8 + k/4 (under the battens), stained. brown: the addition; grey: weathered, for the outhouse and sheds.
 */
export function boardSiding(grey) {
  const seed = grey ? 2710 : 2700, W = N / 4;
  const stain = grey ? [114, 110, 96] : [98, 75, 57];
  const tones = Array.from({length: 4}, (_, k) => 0.94 + 0.12 * hash(k, 1, seed));
  return (x, y) => {
    const xs = ((x - W / 2) % N + N) % N, k = Math.floor(xs / W), tx = xs - k * W, d = Math.min(tx, W - tx);
    // the stain over the photo pine: its colour times the wood's relative brightness (knots and grain darker)
    const rel = Math.max(0.55, Math.min(1.35, lum(PINE(tx + 71 * k + (grey ? 40 : 0), y + 97 * k)) / PINE.mean));
    let c = mul(stain, (1 + 0.8 * (rel - 1)) * tones[k] * (1 + 0.03 * vn(x, y, seed + 9, 30, 120)));
    if (grey) c = mix(c, [138, 134, 124], 0.18 * sstep(0.2, 0.9, vn(x, y, seed + 13, 40, 120)));   // silvered streaks
    // rough-sawn: soft cross bands of the saw (wide, low)
    c = mul(c, 1 + 0.025 * Math.sin(y / N * Math.PI * 2 * 7 + 3 * vn(x, y, seed + 17, 60)));
    let h = 0, ao = 1;
    if (d < 3) { const t = 1 - d / 3; h = -2 * t; c = mul(c, 1 - 0.35 * t); ao = 1 - 0.3 * t; }
    return {c, s: 45, p: 40, h, ao};
  };
}

// ------------------------------------------------------------------------------------------------- metal

/** Old green standing-seam steel (the seams are mesh ribs): satin paint, chalked a little, faint streaks down the slope. */
export function roofGreen() {
  const seed = 2800;
  return (x, y) => {
    let c = mul([78, 98, 74], 1 + 0.03 * vn(x, y, seed, 24, 120) + 0.02 * vn(x, y, seed + 1, 120, 60));
    c = mix(c, [104, 118, 100], 0.12 * sstep(0.3, 1, vn(x, y, seed + 2, 60, 120)));   // chalking
    return {c, s: 150, f0: 10, p: 0, h: 0.5 * vn(x, y, seed + 3, 120), ao: 1};
  };
}

/**
 * Corrugated sheet (M1.1): the waves are mesh (build script: CORRUGATION waves a block across u, 12.5 cm pitch, a crest on
 * each block edge, the ribs running down v). The *_sheet textures darken the valleys in step with the mesh (vanilla bakes
 * only a little shade from the normals) and keep the valley / crown weathering; no painted relief. The plain textures are
 * the same metal for the flat parts (ridge cap, rake trim, flashing, the pipe's plate). w: 1 on a crest, -1 in a valley.
 */
export const CORRUGATION = 8;
const wave = x => Math.cos(x / N * Math.PI * 2 * CORRUGATION);
const VALLEY_SHADE = 0.1;
/**
 * Aged galvanised steel: dull zinc, a soft spangle mottle. Mid grey, rough and dielectric (F0 10): the M1 zinc
 * ([160, 165, 165], smoothness 105, F0 45) glared white under Sundial in game (2026-10-12, M1 and again in M1.1).
 */
/**
 * Galvanised steel is a metal (Aureveil's request 2026-10-25, docs/afl-request-metal-pbr.md in its repo: the roof read as
 * matte as the boards): LabPBR F0 230 (iron, the nearest preset to zinc), the colour is the reflectance, so it is a light
 * zinc grey (sRGB ~175; was 135 as a dielectric). Weathered, not new: smoothness 120-150, mottled by the spangle.
 */
function zinc(x, y, seed) { return mul([172, 177, 178], 1 + 0.04 * vn(x, y, seed, 20) + 0.03 * vn(x, y, seed + 1, 120, 60)); }
function zincSmooth(x, y, seed) { return 135 + 10 * vn(x, y, seed + 5, 12) + 5 * vn(x, y, seed + 6, 60, 30); }
export function roofGalvanized() { return (x, y) => ({c: zinc(x, y, 2900), s: zincSmooth(x, y, 2900), f0: 230, p: 0, h: 0, ao: 1}); }
/** The corrugated galvanised sheet: valleys a shade darker, faint white rust in them (rougher there; still the metal). */
export function roofGalvanizedSheet() {
  const seed = 2900;
  return (x, y) => {
    const w = wave(x);
    let c = mul(zinc(x, y, seed), 1 - VALLEY_SHADE * (1 - w) / 2);
    const white = 0.2 * sstep(0.2, 1, -w) * sstep(0.2, 1, vn(x, y, seed + 2, 40, 120));
    c = mix(c, [190, 192, 187], white);
    return {c, s: zincSmooth(x, y, seed) - 200 * white, f0: 230, p: 0, h: 0, ao: 0.92 + 0.08 * (w + 1) / 2};
  };
}
/** Rusted steel: brown rust with orange-brown patches. */
function rust(x, y, seed) {
  const c = mul([112, 76, 54], 1 + 0.07 * vn(x, y, seed, 40) + 0.05 * vn(x, y, seed + 1, 120, 60));
  return mix(c, [132, 88, 56], 0.3 * sstep(0, 1, vn(x, y, seed + 2, 60)));
}
export function roofRust() { return (x, y) => ({c: rust(x, y, 3000), s: 50, f0: 10, p: 20, h: 0, ao: 1}); }
/** The corrugated rusty sheet: valleys a shade darker with darker runs, a few patches of grey zinc left on the crowns. */
export function roofRustSheet() {
  const seed = 3000;
  return (x, y) => {
    const w = wave(x);
    let c = mul(rust(x, y, seed), 1 - VALLEY_SHADE * (1 - w) / 2);
    c = mix(c, [76, 54, 42], 0.35 * sstep(0.3, 1, -w) * sstep(0, 0.8, vn(x, y, seed + 3, 30, 120)));   // runs in the valleys
    c = mix(c, [128, 126, 120], 0.5 * sstep(0.55, 0.85, vn(x, y, seed + 4, 60)) * sstep(0, 1, w));    // zinc left on crowns
    return {c, s: 50, f0: 10, p: 20, h: 0, ao: 0.92 + 0.08 * (w + 1) / 2};
  };
}

/** Black wrought iron (hinges, latches, handles). */
export function iron() { return (x, y) => ({c: mul([40, 38, 36], 1 + 0.06 * vn(x, y, 3100, 40)), s: 80, f0: 230, p: 0, h: 0, ao: 1}); }
/** Dark sash paint (window sashes, the front door's glazing frame). */
export function sash() { return (x, y) => ({c: mul([46, 41, 36], 1 + 0.03 * vn(x, y, 3200, 60)), s: 130, f0: 10, p: 0, h: 0, ao: 1}); }
/** Stove pipe: black stove paint, matte, faint brown heat marks. */
export function stovePipe() {
  return (x, y) => ({c: mix(mul([38, 37, 36], 1 + 0.05 * vn(x, y, 3300, 40)), [70, 52, 40], 0.25 * sstep(0.5, 1, vn(x, y, 3301, 30, 60))), s: 70, f0: 10, p: 0, h: 0, ao: 1});
}
/** Green steel T-post paint (the garden fence posts). */
export function tPost() { return (x, y) => ({c: mul([54, 82, 60], 1 + 0.04 * vn(x, y, 3400, 40)), s: 110, f0: 10, p: 0, h: 0, ao: 1}); }

/** Window glass: a faint green-grey, mostly see-through (alpha 64). */
export function glass() { return (x, y) => ({c: mul([196, 212, 208], 1 + 0.01 * vn(x, y, 3500, 120)), s: 245, f0: 10, p: 0, h: 0, ao: 1, a: 64}); }

/**
 * Woven wire field fence, one block wide (u) and high (v), cut-out: galvanised line wires closer toward the ground
 * (heights below), vertical stays every 12.5 cm, a knot where they cross; 3 texels wide (thinner disappears).
 */
const LINE_WIRES = [0.04, 0.11, 0.19, 0.28, 0.38, 0.49, 0.61, 0.74, 0.87, 0.97];
export function wovenWire() {
  return (x, y) => {
    const v = 1 - y / N;
    let d = 1e9;
    for (const h of LINE_WIRES) d = Math.min(d, Math.abs(v - h) * N);
    const stay = wrapD(x - N / 16, N / 8) < 1.5 && v > 0.03 && v < 0.98;
    const knot = stay && d < 3.5;
    const on = d < 1.5 || stay || knot;
    // galvanised wire: the metal, as the roof (F0 230, a light zinc grey)
    return {c: mul([174, 179, 176], 1 + 0.05 * vn(x, y, 3600, 20)), s: 125, f0: 230, p: 0, h: 0, ao: 1, a: on ? 255 : 0};
  };
}

// ------------------------------------------------------------------------------------------------- stone, firewood

/**
 * Fieldstone, M1.2 (user 2026-10-12, concept B picked from design/buildings/forest_start_home_v1/m1_source/plinth_concepts):
 * roughly squared rubble, the bearing plinth of an old mountain house (the granite foundation of the Grogan House, HABS
 * GA-53). M1 read as bathroom tiles; the M1.1 power-diagram stones read as river-cobble cladding ("too round, too smooth,
 * a landscape wall"). Now blocky split stones, 20-60 cm tall and 35-70 cm long, in broken courses (bands of two nominal
 * courses, a column one stone through both, two or three high), joints a little slanted, a few corners knocked off at a
 * random angle; each face a split face of three low facets (no pillow), the arris a narrow straight bevel, sharp in places
 * and spalled wider in others; earthy mortar (1.7-3 cm) close to the stone's value, recessed flat. Low-saturation grey
 * and grey-brown, small steps between stones. One seamless texture FS_COLS x FS_ROWS blocks (3 m x 2 m), cut into tiles
 * fieldstone_0 .. 5 (row-major) that MacroTileModel picks by world position, so a plinth repeats every 3 m.
 */
export const FS_COLS = 3, FS_ROWS = 2;
const FW = FS_COLS * N, FH = FS_ROWS * N, FS_SEED = 4200;
/** Periodic value noise over the whole fieldstone texture, cells cx x cy texels (dividing 720 and 480), -1..1; lin: no smoothstep (angular). */
function vnF(x, y, seed, cx, cy = cx, lin = false) {
  const nx = Math.round(FW / cx), ny = Math.round(FH / cy), xi = Math.floor(x / cx), yi = Math.floor(y / cy);
  const h = (i, j) => hash(((i % nx) + nx) % nx, ((j % ny) + ny) % ny, seed);
  const sm = lin ? t => t : t => t * t * (3 - 2 * t), tx = sm(x / cx - xi), ty = sm(y / cy - yi);
  const a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * tx, b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * tx;
  return 2 * (a + (b - a) * ty) - 1;
}
const fsRng = seed => { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; };
/** Random lengths in [a, b] summing exactly to total (the last one dropped if it overshoots by more than half). */
function lengths(rnd, a, b, total) {
  const L = []; let s = 0;
  while (s < total) { const l = a + (b - a) * rnd(); L.push(l); s += l; }
  if (L.length > 1 && s - total > L[L.length - 1] / 2) s -= L.pop();
  return L.map(l => l * total / s);
}
/** A periodic piecewise-linear wobble along x (straight segments, so the bed joints stay angular): knots [a, b] apart, +-amp. */
function wobble(rnd, a, b, amp) {
  const xs = [0]; for (const l of lengths(rnd, a, b, FW)) xs.push(xs[xs.length - 1] + l);
  const ys = xs.map(() => (2 * rnd() - 1) * amp); ys[ys.length - 1] = ys[0];
  return x => { x = ((x % FW) + FW) % FW; let i = 0; while (xs[i + 1] < x) i++; const t = (x - xs[i]) / (xs[i + 1] - xs[i]); return ys[i] + (ys[i + 1] - ys[i]) * t; };
}
const FS = {band: [96, 150], cols: [85, 165], split: [0.25, 0.6, 0.15], subdiv: 0.25, knot: [60, 140], wobble: 5, slant: 0.14, splitSlope: 0.05,
  round: 1.5, chip: [0.5, 0.9], chipScale: [16, 40], joint: [2.0, 1.6], arris: 3.5, mix: 0.4, clip: [0.4, 8, 18], facet: [3, 4], vary: 0.08,
  // grey, warm grey, grey-brown, light grey, dark (share %), then a further 40 % toward neutral grey
  pal: [[[118, 116, 110], 35], [[120, 113, 102], 25], [[105, 100, 93], 20], [[129, 126, 119], 10], [[95, 92, 87], 10]],
  mortar: [97, 92, 83], mortarH: -11, mortarAo: 0.65};
/**
 * The stone layout: horizontal bands (bed joints of straight wobbling segments), each cut into columns by slanted head
 * joints, each column 1..3 stones high (split lines with a small slope), a stone row sometimes cut once more.
 */
function rubbleLayout() {
  const P = FS, rnd = fsRng(FS_SEED), hs = lengths(rnd, P.band[0], P.band[1], FH), Y = [0];
  for (const h of hs) Y.push(Y[Y.length - 1] + h);
  const nb = hs.length, wob = Array.from({length: nb}, () => wobble(rnd, P.knot[0], P.knot[1], P.wobble));
  const bands = [];
  for (let b = 0; b < nb; b++) {
    const ws = lengths(rnd, P.cols[0], P.cols[1], FW), X = [rnd() * FW];
    for (const w of ws) X.push(X[X.length - 1] + w);
    const S = ws.map(() => (2 * rnd() - 1) * P.slant); S.push(S[0]);
    const cols = ws.map(w => {
      const r = rnd(), n = r < P.split[0] ? 1 : r < P.split[0] + P.split[1] ? 2 : 3;
      const ts = n === 1 ? [] : n === 2 ? [0.36 + 0.28 * rnd()] : [0.26 + 0.12 * rnd(), 0.6 + 0.14 * rnd()];
      const rows = [];
      for (let j = 0; j < n; j++) rows.push(rnd() < P.subdiv && w > P.cols[0] * 1.15 ? {m: 0.32 + 0.36 * rnd(), s: (2 * rnd() - 1) * P.slant} : null);
      return {ts, ss: ts.map(() => (2 * rnd() - 1) * P.splitSlope), rows};
    });
    bands.push({yc: (Y[b] + Y[b + 1]) / 2, X, S, cols});
  }
  return {nb, bnd: (b, x) => Y[b] + wob[b % nb](x), bands};
}
/** The stone under (x, y): its id, its distances to its top, bottom, left and right edges (texels), its position (-1..1), size. */
function rubbleAt(L, x, y) {
  x = ((x % FW) + FW) % FW; y = ((y % FH) + FH) % FH;
  if (y < L.bnd(0, x)) y += FH; else if (y >= FH + L.bnd(0, x)) y -= FH;   // the band across the wrap
  let b = 0;
  while (b < L.nb - 1 && y >= L.bnd(b + 1, x)) b++;
  const top = L.bnd(b, x), bot = b + 1 === L.nb ? FH + L.bnd(0, x) : L.bnd(b + 1, x), B = L.bands[b];
  const jx = k => B.X[k] + B.S[k] * (y - B.yc);
  let xx = x;
  while (xx < jx(0)) xx += FW;
  while (xx >= jx(0) + FW) xx -= FW;
  let k = 0;
  while (xx >= jx(k + 1)) k++;
  const C = B.cols[k], x0 = jx(k), x1 = jx(k + 1), xc = (x0 + x1) / 2, sy = i => top + C.ts[i] * (bot - top) + C.ss[i] * (xx - xc);
  let t0 = top, t1 = bot, j = 0;
  for (let i = 0; i < C.ts.length; i++) { const s = sy(i); if (y >= s) { t0 = s; j = i + 1; } }
  if (j < C.ts.length) t1 = sy(j);
  let l = x0, r = x1, side = 0;
  const R = C.rows[j];
  if (R) { const xm = x0 + R.m * (x1 - x0) + R.s * (y - (t0 + t1) / 2); if (xx < xm) r = xm; else { l = xm; side = 1; } }
  const cs = s => 1 / Math.sqrt(1 + s * s);
  return {id: ((b * 64 + k) * 4 + j) * 2 + side, d: [y - t0, t1 - y, (xx - l) * cs(B.S[k]), (r - xx) * cs(B.S[k + 1])],
    u: (2 * xx - l - r) / Math.max(r - l, 1), v: (2 * y - t0 - t1) / Math.max(t1 - t0, 1), w: r - l, h: t1 - t0};
}
export function fieldstoneTexture() {
  const P = FS, L = rubbleLayout(), seed = FS_SEED, memo = new Map();
  const stone = id => {
    if (!memo.has(id)) {
      const r = i => hash(id, i, seed + 7);
      let w = r(0) * 100, k = 0;
      while (w > P.pal[k][1]) { w -= P.pal[k][1]; k++; }
      const base = P.pal[k][0], g = (base[0] + base[1] + base[2]) / 3;
      // corners knocked off (TL, TR, BL, BR: size, slope weights; null = square), the split face's three facets
      const clip = [5, 6, 7, 8].map(i => r(i) < P.clip[0] ? [P.clip[1] + (P.clip[2] - P.clip[1]) * r(i + 10), 0.5 + r(i + 50), 0.5 + r(i + 60)] : null);
      const facets = [0, 1, 2].map(i => { const a = r(20 + i) * Math.PI * 2; return [Math.cos(a), Math.sin(a), P.facet[0] + P.facet[1] * r(30 + i), 0.6 * r(40 + i)]; });
      memo.set(id, {c: mul(mix(base, [g, g, g], P.mix), 1 - P.vary / 2 + P.vary * r(1)), set: -1.5 + 2.5 * r(2), k: id % 3, clip, facets});
    }
    return memo.get(id);
  };
  return (x, y) => {
    const q = rubbleAt(L, x, y), st = stone(q.id), [dt, db, dl, dr] = q.d, all = [...q.d], kr = P.round;
    const cmax = 0.35 * Math.min(q.w, q.h);   // a small stone keeps its corners (else it reads as a pebble)
    [[dt, dl], [dt, dr], [db, dl], [db, dr]].forEach(([a, b], i) => { const c = st.clip[i]; if (c) all.push((a * c[1] + b * c[2] - Math.min(c[0], cmax) * (c[1] + c[2]) / 2) / Math.hypot(c[1], c[2])); });
    const d = all.sort((a, b) => a - b), hk = Math.max(kr - Math.abs(d[0] - d[1]), 0) / kr;
    let e = Math.min(d[0], d[1]) - hk * hk * kr / 4;
    e += P.chip[0] * vnF(x, y, seed + 1, P.chipScale[0], P.chipScale[0], true) + P.chip[1] * vnF(x, y, seed + 2, P.chipScale[1], P.chipScale[1], true);   // a few straight chips, not a torn edge
    const g = P.joint[0] + P.joint[1] * sstep(-0.5, 0.8, vnF(x, y, seed + 3, 48));   // half the joint
    const big = 1 + 0.03 * vnF(x, y, seed + 4, 240, 160);   // a metre-scale drift so no two blocks match
    if (e < g) {   // mortar: flat and recessed, no lip that would outline the stone
      const into = g - e;
      return {c: mul(P.mortar, big * (1 + 0.04 * vnF(x, y, seed + 5, 24)) * (0.88 + 0.12 * sstep(0, 4, into))), s: 26, f0: 10, p: 50, h: P.mortarH + 0.6 * vnF(x, y, seed + 9, 20), ao: P.mortarAo};
    }
    const ar = P.arris * (0.55 + 0.9 * sstep(-0.7, 0.7, vnF(x, y, seed + 8, 30)));   // the arris sharp in places, spalled wider in others
    const prof = Math.min((e - g) / ar, 1);   // a narrow straight bevel: a crisp split arris, no rounded shoulder
    let f = 9;   // the split face: the lowest of three tilted planes (flat facets meeting in low ridges)
    for (const [cx, cy, s, o] of st.facets) f = Math.min(f, 2.5 + o - s * (cx * q.u + cy * q.v));
    const h = clamp(-14 + 12 * prof + (f + st.set + 0.5 * vnF(x, y, seed + 6 + st.k, 36)) * prof, -16, 4);
    let c = mul(st.c, big * (1 + 0.045 * vnF(x, y, seed + 10 + st.k, 28) + 0.02 * vnF(x, y, seed + 20, 10)));
    c = mix(c, [112, 108, 100], 0.15 * sstep(0.2, 0.9, vnF(x, y, seed + 40, 48)));   // weathered film
    c = mul(c, 0.9 + 0.1 * prof);
    return {c, s: 52 + 8 * vnF(x, y, seed + 50, 40), f0: 10, p: 20, h, ao: 0.66 + 0.34 * prof};
  };
}
/** The fieldstone texture's tiles: [[colour, _s, _n] PNGs] for fieldstone_0 .. 5 (row-major, row 0 at the top). */
export function fieldstoneTiles() {
  const f = fieldstoneTexture(), W = FW, H = FH;
  const col = new Float64Array(W * H * 3), sp = new Float64Array(W * H * 3), Hm = new Float64Array(W * H), AO = new Float64Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let c = [0, 0, 0], s = 0, f0 = 0, p = 0, h = 0, ao = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const r = f(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS);
      c = c.map((v, i) => v + r.c[i]); s += r.s; f0 += r.f0; p += r.p; h += r.h; ao += r.ao;
    }
    const k = SS * SS, o = y * W + x;
    for (let i = 0; i < 3; i++) col[o * 3 + i] = c[i] / k;
    sp[o * 3] = s / k; sp[o * 3 + 1] = f0 / k; sp[o * 3 + 2] = p / k; Hm[o] = h / k; AO[o] = ao / k;
  }
  const at = (i, j) => Hm[((j + H) % H) * W + ((i + W) % W)], tiles = [];
  for (let ty = 0; ty < FS_ROWS; ty++) for (let tx = 0; tx < FS_COLS; tx++) {
    const c = Buffer.alloc(N * N * 4), s = Buffer.alloc(N * N * 4), n = Buffer.alloc(N * N * 4);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const X = tx * N + x, Y = ty * N + y, o = Y * W + X, d = (y * N + x) * 4;
      for (let i = 0; i < 3; i++) c[d + i] = clamp(Math.round(col[o * 3 + i]), 0, 255);
      c[d + 3] = 255;
      s[d] = clamp(Math.round(sp[o * 3]), 0, 255); s[d + 1] = clamp(Math.round(sp[o * 3 + 1]), 0, 255); s[d + 2] = clamp(Math.round(sp[o * 3 + 2]), 0, 64); s[d + 3] = 255;
      const hx = (at(X + 1, Y) - at(X - 1, Y)) / (2 * MM), hy = (at(X, Y + 1) - at(X, Y - 1)) / (2 * MM);
      let nx = -hx, ny = -hy; const l = Math.hypot(nx, ny, 1); nx /= l; ny /= l;
      n[d] = Math.round((nx * 0.5 + 0.5) * 255); n[d + 1] = Math.round((ny * 0.5 + 0.5) * 255);
      n[d + 2] = clamp(Math.round(255 * AO[o]), 0, 255); n[d + 3] = clamp(Math.round(255 * (1 + Hm[o] / 250)), 0, 255);
    }
    tiles.push([png(c, N, N), png(s, N, N), png(n, N, N)]);
  }
  return tiles;
}
/**
 * M1.2: the squared stone block a porch post stands on (one stone, 34 cm, 22 cm high; its faces take this by position, the
 * block's foot at the bottom of the texture): grey granite as the plinth, mottled, a little earth on its lowest part.
 */
export function stoneFooting() {
  const base = mix([118, 116, 110], [116, 116, 116], 0.3);
  return (x, y) => {
    let c = mul(base, 1 + 0.05 * vn(x, y, 4401, 30) + 0.02 * vn(x, y, 4402, 8));
    c = mix(c, [112, 108, 100], 0.15 * sstep(0.2, 0.9, vn(x, y, 4403, 48)));
    c = mix(c, [100, 94, 84], 0.4 * sstep(N * 0.85, N, y));
    return {c, s: 56, f0: 10, p: 20, h: 0.8 * vn(x, y, 4404, 40), ao: 1};
  };
}

/** A firewood stack's end: split rounds (wedges and halves) with bark on their rounded side, dark gaps between them. */
export function firewoodEnd() {
  const seed = 3800;
  return (x, y) => {
    const cl = cells(x, y, 8, 8, seed, 0.85), e = cl.edge;
    if (e < 1.8) return {c: [58, 46, 36], s: 20, f0: 10, p: 30, h: -14, ao: 0.5};
    // the red oak end photo round a pith off to one side of the piece (a split wedge's point), turned per piece, at 1.8x
    // (its rings 2-3 texels apart: finer ones vanish); a bark rim on most pieces' edges; weathered a little grey
    const pa = 6.283 * hash(cl.i, cl.j, seed + 1), px = cl.cx + Math.cos(pa) * 16, py = cl.cy + Math.sin(pa) * 16;
    let dx = x - px, dy = y - py;
    dx -= Math.round(dx / N) * N; dy -= Math.round(dy / N) * N;
    const tw = 6.283 * hash(cl.i, cl.j, seed + 4), cs = Math.cos(tw), sn = Math.sin(tw), K = 1.8;
    let c = FW_END(N / 2 + K * (dx * cs - dy * sn), N / 2 + K * (dx * sn + dy * cs));
    c = mul(c, 0.86 + 0.2 * hash(cl.i, cl.j, seed + 2));
    c = mix(c, [150, 140, 124], 0.15);                                                                          // weathered
    c = mix(c, [102, 88, 72], 0.75 * (1 - sstep(1.8, 4.5, e)) * (hash(cl.i, cl.j, seed + 3) < 0.6 ? 1 : 0.25));   // bark rim
    return {c, s: 40, f0: 10, p: 40, h: -3 * (1 - sstep(1.8, 6, e)), ao: 0.8 + 0.2 * sstep(1.8, 8, e)};
  };
}
/** A firewood stack's long sides: rows of split pieces, bark (grey-brown) or split face (light) per piece. */
export function firewoodSide() {
  const seed = 3900, ROWS = 6, Hr = N / ROWS;
  return (x, y) => {
    const row = Math.floor(y / Hr), ty = y - row * Hr, d = Math.min(ty, Hr - ty);
    const seg = Math.floor(((x + 37 * row) % N) / 60), split = hash(row, seg, seed) < 0.45;
    // pieces lie along u: the bark photo's ridges and the split face's grain run along v, so they are turned
    const off = 53 * row + 31 * seg;
    // a split face is rough, not planed: the photo's grain at 2.2x contrast, a little weathered grey
    let c = FW_BARK(y + off, x + 2 * off);
    if (split) {
      const w = FW_SPLIT(ty + off, x + off), k = 0.84 * (1 + 1.2 * (lum(w) / FW_SPLIT.mean - 1));
      c = mix(mul(w, k), [150, 140, 124], 0.12);
    }
    c = mul(c, 0.92 + 0.12 * hash(row, seg, seed + 3));
    let h = 0, ao = 1;
    if (d < 4) { const t = 1 - d / 4; c = mix(c, [42, 34, 28], 0.8 * t); h = -10 * t; ao = 1 - 0.5 * t; }
    return {c, s: split ? 40 : 25, f0: 10, p: 40, h, ao};
  };
}

/** Every texture: name -> [maker, height?]. Paths: textures/block/cabin/<name>.png (+ _s, _n). */
export const TEXTURES = {
  white_oak_log_wall: [logWall, false], white_oak_log_round: [logRound, false], white_oak_log_end: [logEnd, false],
  oak_trim: [() => trim(false), false], oak_trim_v: [() => trim(true), false], oak_door_boards: [doorBoards, true],
  oak_roof_boards: [roofBoards, true], oak_deck_boards: [deckBoards, false], stone_footing: [stoneFooting, true],
  brown_board_siding: [() => boardSiding(false), true], grey_board_siding: [() => boardSiding(true), true],
  roof_green: [roofGreen, false], roof_galvanized: [roofGalvanized, false], roof_galvanized_sheet: [roofGalvanizedSheet, false],
  roof_rust: [roofRust, false], roof_rust_sheet: [roofRustSheet, false],
  cabin_iron: [iron, false], cabin_sash: [sash, false], stove_pipe: [stovePipe, false], fence_t_post: [tPost, false],
  cabin_glass: [glass, false], woven_wire: [wovenWire, false],
  firewood_end: [firewoodEnd, true], firewood_side: [firewoodSide, true],
};
