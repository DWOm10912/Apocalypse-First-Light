// Wood Building Blocks V1 (docs/models/wood_building_blocks_v1.md, 2026-10-12): the planks, rounded timber and beams of
// the six AFL tree species, at AFL's man-made resolution (240 x 240 texels per block, LabPBR _s / _n, no per-texel
// noise). Colours and ring profiles are the trees' own (tools/mesh-trees/felling-art.mjs, species-art.mjs: sapwood,
// heartwood, latewood, rays, checks, resin), so a plank, a timber and the log they come from read as one wood.
// AFL style (2026-10-12, like the facade bricks and the commercial wood door): clean and quiet, not a photo of wood. The
// layout carries the face (board seams and staggered butt joints, the same reading as vanilla planks), the grain stays
// calm (straight, low contrast, no cathedral flames, knots or blotches), boards differ by small tone steps like bricks;
// a species reads by its colour, its ring spacing and its end grain.
//   <species>_planks        four boards (25 cm) across the block, staggered butt joints, straight grain along them
//                           drifting slowly out of the board (rift-sawn), one tone step per board; grooved seams
//   <species>_timber_side   the rounded timber's sides and the beam's: planed, straight grain along the length (v), the
//                           species' lumber colour. No check: the four sides share the texture, a crack would repeat
//   <species>_timber_end    the timber's ends (and the middle half the beam's): rings round the pith in the middle of the
//                           block (what tells the timber from the barked log at a glance), sapwood toward the corners
//                           where the log's reached, radial drying checks; oaks' rays, white pine's resin
// Ring spacing is the log end's (felling-art profile, px of 32 per block) x 3.6: about 7..10 texels, so the latewood bands
// are 2..4 texels, soft-edged, colour only (no relief: grazing light would turn them into hard lines). Relief only where
// the wood really has it: the plank seams and butt joints (bevelled grooves), the checks.
// Also writes the block / item models, blockstates and loot tables (one self drop each). The tags (axe, wood break
// noise), item mass and lang are edited by hand; --check audits them.
//   node tools/build-wood-blocks-v1.mjs                 -> writes textures, models, blockstates, loot tables
//   node tools/build-wood-blocks-v1.mjs --check         -> verifies every output is up to date and the hand-edited data
//   node tools/build-wood-blocks-v1.mjs --preview DIR   -> writes the texture sets and a contact sheet into DIR
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {png} from './cube-slab-mesh-lib.mjs';
import {N, hash, clamp, sstep, mul, mix, vn, wrapD, render} from './mesh-trees/hd-art.mjs';
import {OAK, PINE, OAK_WOOD, PINE_WOOD} from './mesh-trees/felling-art.mjs';
import {RED_OAK, HICKORY, RED_MAPLE, HEMLOCK, RED_OAK_WOOD, HICKORY_WOOD, RED_MAPLE_WOOD, HEMLOCK_WOOD} from './mesh-trees/species-art.mjs';

/** The species in Species order, with the trees' palettes and wood profiles. */
// lumber: the sawn wood's sapwood share (0 heartwood .. 1 sapwood: oaks sell heartwood, maple its pale sapwood, hickory
// both) and the spread between boards
export const SPECIES = [
  {id: 'white_oak', p: OAK, w: OAK_WOOD, seed: 1100, contrast: 0.85, lumber: 0.15, spread: 0.12},
  {id: 'white_pine', p: PINE, w: PINE_WOOD, seed: 1200, contrast: 0.75, lumber: 0.55, spread: 0.2},
  {id: 'red_oak', p: RED_OAK, w: RED_OAK_WOOD, seed: 1300, contrast: 0.9, lumber: 0.1, spread: 0.1},
  {id: 'shagbark_hickory', p: HICKORY, w: HICKORY_WOOD, seed: 1400, contrast: 0.8, lumber: 0.35, spread: 0.3},
  {id: 'red_maple', p: RED_MAPLE, w: RED_MAPLE_WOOD, seed: 1500, contrast: 0.6, lumber: 0.85, spread: 0.12},
  {id: 'eastern_hemlock', p: HEMLOCK, w: HEMLOCK_WOOD, seed: 1600, contrast: 0.8, lumber: 0.5, spread: 0.15},
];
/** WoodBlocks.TIMBER_RADIUS in texels: the timber's rounded corners (the end texture is only drawn inside them). */
const TIMBER_R = 1.5 / 16 * N;
/** The log a timber is hewn from: its outer radius (texels from the pith, just beyond the block's corners). */
const LOG_R = 175;

/** Planed-face colours of a species (a planed face is a little lighter than end grain, which drinks the light). */
function faceColours(sp) {
  const {p} = sp;
  return {heart: mix(p.heart, p.cutLight, 0.12), late: mix(p.late, p.heart, 0.25), sap: mix(p.sap, p.cutLight, 0.15),
    sapLate: mix(p.sapLate, p.sap, 0.2), check: p.check, ray: p.ray};
}
const spacing = sp => sp.w.spacing * 3.6;
/** Heartwood radius (texels from the pith) of the log a timber or board comes from: the tree's own share. */
const heartR = sp => LOG_R * sp.w.sapR / sp.w.innerR;

/**
 * End-grain wood at ring phase `phase` (rings from the pith, wobble included) and radius r (texels, for the sapwood):
 * soft latewood bands, sapwood beyond the heart radius (blended over 16 texels: no hard heart outline).
 */
function ringWood(sp, phase, r, contrast) {
  const S = spacing(sp), f = ((phase % 1) + 1) % 1, share = sp.w.lateShare;
  // soft band: earlywood -> latewood over 1.2 texels each side
  const e = 1.2 / S, late = sstep(0, e, f) * (1 - sstep(share - e, share + e, f));
  const sapT = sstep(heartR(sp) - 8, heartR(sp) + 8, r);
  return mix(mix(sp.p.heart, sp.p.late, late * contrast), mix(sp.p.sap, sp.p.sapLate, late * contrast * 0.7), sapT);
}

/**
 * Calm straight grain on a planed face, t across it, s along it (texels): the latewood share 0..1 of lines running
 * along s, drifting `slope` rings across the face per tile (rift-sawn: the lines leave the board slowly), swaying a few
 * texels over half a block; ring widths vary by about a third (good and bad years), so the lines are not a barcode.
 * x, y: noise coordinates, the first along the grain.
 */
function straightGrain(sp, t, s, slope, seed, x, y) {
  const S = spacing(sp), share = sp.w.lateShare, e = 1.2 / S;
  const phase = (t + 4 * vn(x, y, seed, 120, 40)) / S + slope * s / N + 0.3 * vn(x, y, seed + 7, 120, 24);
  const f = ((phase % 1) + 1) % 1;
  return sstep(0, e, f) * (1 - sstep(share - e, share + e, f));
}

/** Planed lumber of sapwood share m: {wood, late} colours. */
function lumber(sp, m) {
  const fc = faceColours(sp);
  return {wood: mix(fc.heart, fc.sap, m), late: mix(fc.late, fc.sapLate, m)};
}

// ---- planks: four boards (one per 60 texels), each one board from its butt joint round the tile back to it ----
function planks(sp) {
  const B = N / 4, G = 1.6, BEV = 3.2, s0 = sp.seed, contrast = sp.contrast * 0.55;
  // staggered joints (a quarter to half a block apart row to row, as vanilla planks), one tone step per board
  const boards = [36, 156, 96, 216].map((j, k) => ({
    joint: j + 20 * (hash(k, 5, s0) - 0.5), slope: (hash(k, 3, s0) < 0.5 ? -1 : 1) * (0.4 + 0.8 * hash(k, 4, s0)),
    tone: 0.955 + 0.09 * hash(k, 6, s0), ...lumber(sp, clamp(sp.lumber + sp.spread * 2 * (hash(k, 7, s0) - 0.5), 0, 1))}));
  return (x, y) => {
    const row = Math.floor(y / B), k = ((row % 4) + 4) % 4, b = boards[k], ty = y - row * B;
    // along the board from its joint: the grain breaks at the joint, joins up at the tile's edge
    const xb = ((x - b.joint) % N + N) % N;
    const late = straightGrain(sp, ty + 37 * k, xb, b.slope, s0 + 20 + k, x, y);
    let c = mix(b.wood, b.late, late * contrast);
    c = mul(c, b.tone * (1 + 0.025 * (xb / N - 0.5)) * (1 + 0.012 * vn(x, y, s0 + 40 + k, 120, 30)));
    // seams between boards (top and bottom of each board) and the board's butt joint
    const d = Math.min(Math.min(ty, B - ty), wrapD(x - b.joint));
    let h = 0, ao = 1, s = 108, p = 30;
    if (d < G) { c = mul(c, 0.55); h = -1.4; ao = 0.72; s = 55; p = 45; }
    else if (d < G + BEV) { const q = 1 - (d - G) / BEV; h = -1.0 * q * q; c = mul(c, 1 - 0.12 * q * q); ao = 1 - 0.12 * q; }
    return {c, s, p, h, ao};
  };
}

// ---- timber side: planed, straight grain along v in the species' lumber colour ----
function timberSide(sp) {
  const s0 = sp.seed + 50, {wood, late: lateC} = lumber(sp, sp.lumber), contrast = sp.contrast * 0.6;
  return (x, y) => {
    const late = straightGrain(sp, x, y, 1, s0 + 1, y, x);
    const c = mul(mix(wood, lateC, late * contrast), 1 + 0.015 * vn(x, y, s0 + 3, 40, 120));
    return {c, s: 112, p: 30, h: 0, ao: 1};
  };
}

// ---- timber end: rings round the pith at the block's middle ----
function timberEnd(sp) {
  const s0 = sp.seed + 80, {p, w} = sp;
  const ph1 = 6.283 * hash(3, 1, s0), ph2 = 6.283 * hash(3, 2, s0);
  const checks = Array.from({length: w.checks}, (_, k) => ({a: 6.283 * hash(4, k, s0), len: 60 + 40 * hash(5, k, s0), bend: (hash(6, k, s0) - 0.5) * 0.006}));
  const rays = w.rays ? Array.from({length: 22}, (_, k) => ({a: (k + 0.7 * hash(7, k, s0)) * 6.283 / 22, from: 14 + 30 * hash(8, k, s0), len: 20 + 40 * hash(9, k, s0)})) : [];
  const resin = [];
  for (let k = 0; resin.length < (w.resin ? 5 : 0) && k < 200; k++) {
    const a = 6.283 * hash(10, k, s0), d = heartR(sp) + 8 + (LOG_R - heartR(sp) - 16) * hash(11, k, s0);
    const x = N / 2 + Math.cos(a) * d, y = N / 2 + Math.sin(a) * d;
    if (x > TIMBER_R && x < N - TIMBER_R && y > TIMBER_R && y < N - TIMBER_R) resin.push([x, y]);
  }
  return (x, y) => {
    const dx = x - N / 2, dy = y - N / 2, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    // the rings' wander grows from nothing at the pith (no star in the middle)
    const rw = d + sstep(0, 40, d) * (2.6 * Math.sin(3 * a + ph1) + 1.4 * Math.sin(5 * a + ph2) + 0.8 * vn(x, y, s0 + 1, 24));
    let c = ringWood(sp, rw / spacing(sp), rw, 0.65);
    if (d < 5) c = mix(p.pith, c, sstep(3, 5, d));
    // the saw cut is never quite flat: a soft light-to-dark across the end
    c = mul(c, 1.025 - 0.05 * ((x + y) / (2 * N)) + 0.015 * vn(x, y, s0 + 2, 60));
    let h = 0, ao = 1, s = 72, por = 50;
    for (const ray of rays) {
      const off = Math.abs(Math.atan2(Math.sin(a - ray.a), Math.cos(a - ray.a))) * d;
      if (d > ray.from && d < ray.from + ray.len && off < 1.4) c = mix(c, p.ray, 0.35 * (1 - off / 1.4));
    }
    for (const ch of checks) {
      const aa = ch.a + ch.bend * d, off = Math.abs(Math.atan2(Math.sin(a - aa), Math.cos(a - aa))) * d;
      const half = 1.6 * (1 - d / ch.len);
      if (d > 6 && d < ch.len && off < half + 0.8) { const q = clamp(1 - Math.max(0, off - half) / 0.8, 0, 1); c = mix(c, p.check, 0.85 * q); h = -1.5 * q; ao = 1 - 0.3 * q; s = 50; }
    }
    for (const [rx, ry] of resin) {
      const q = Math.hypot(x - rx, y - ry);
      if (q < 3.2) { const k = sstep(3.2, 2.0, q); c = mix(c, q < 1.6 ? p.resinHi : p.resin, k); s = Math.max(s, 72 + 80 * k); por = 50 - 40 * k; }
    }
    return {c, s, p: por, h, ao};
  };
}

/** Every texture set: [name, {col, maps}]. */
export function textures() {
  const out = [];
  for (const sp of SPECIES) {
    out.push([`${sp.id}_planks`, render(planks(sp))]);
    out.push([`${sp.id}_timber_side`, render(timberSide(sp))]);
    out.push([`${sp.id}_timber_end`, render(timberEnd(sp))]);
  }
  return out;
}

// ---- assets and data ----
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight');
const DATA = path.join(ROOT, 'src/main/resources/data');
const json = v => JSON.stringify(v, null, 2) + '\n';
const NS = 'apocalypse_firstlight:block/wood/';
export const KINDS = ['planks', 'rounded_timber', 'beam'];
export const IDS = SPECIES.flatMap(sp => KINDS.map(k => `${sp.id}_${k}`));

/** The beam along Y, 8 x 8 / 16: sides from the middle of the side texture, ends from the middle of the end texture. */
function beamModel(id) {
  const side = {texture: '#side', uv: [4, 0, 12, 16]};
  return {parent: 'minecraft:block/block', textures: {particle: NS + id + '_timber_side', side: NS + id + '_timber_side', end: NS + id + '_timber_end'},
    elements: [{from: [4, 0, 4], to: [12, 16, 12], faces: {
      down: {texture: '#end', uv: [4, 4, 12, 12], cullface: 'down'}, up: {texture: '#end', uv: [4, 4, 12, 12], cullface: 'up'},
      north: side, south: side, west: side, east: side}}]};
}

export function outputs() {
  const out = [];
  for (const [name, t] of textures()) ['', '_s', '_n'].forEach((k, i) => out.push([path.join(ASSETS, 'textures/block/wood', name + k + '.png'), t.maps[i]]));
  const axes = model => ({variants: {'axis=y': {model}, 'axis=z': {model, x: 90}, 'axis=x': {model, x: 90, y: 90}}});
  for (const sp of SPECIES) {
    const id = sp.id, m = k => `apocalypse_firstlight:block/wood/${id}_${k}`;
    out.push([path.join(ASSETS, 'models/block/wood', id + '_planks.json'), json({parent: 'minecraft:block/cube_all', textures: {all: NS + id + '_planks'}})]);
    // the rounded timber's geometry is TreeLogModel's (WoodModels); the JSON holds the particle and the item transforms
    out.push([path.join(ASSETS, 'models/block/wood', id + '_rounded_timber.json'), json({parent: 'minecraft:block/block', textures: {particle: NS + id + '_timber_side'}})]);
    out.push([path.join(ASSETS, 'models/block/wood', id + '_beam.json'), json(beamModel(id))]);
    out.push([path.join(ASSETS, 'blockstates', id + '_planks.json'), json({variants: {'': {model: m('planks')}}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '_rounded_timber.json'), json({variants: {'': {model: m('rounded_timber')}}})]);
    out.push([path.join(ASSETS, 'blockstates', id + '_beam.json'), json(axes(m('beam')))]);
    for (const k of KINDS) {
      out.push([path.join(ASSETS, 'models/item', `${id}_${k}.json`), json({parent: m(k)})]);
      out.push([path.join(DATA, 'apocalypse_firstlight/loot_tables/blocks', `${id}_${k}.json`), json({type: 'minecraft:block', pools: [{rolls: 1, bonus_rolls: 0,
        entries: [{type: 'minecraft:item', name: `apocalypse_firstlight:${id}_${k}`}], conditions: [{condition: 'minecraft:survives_explosion'}]}],
        random_sequence: `apocalypse_firstlight:blocks/${id}_${k}`})]);
    }
  }
  return out;
}

/** The hand-edited data: the axe tag, the wood break-noise tag, no tier tag, no planks / logs tags, item mass, both lang files. */
function audit() {
  const fails = [];
  const read = p => JSON.parse(fs.readFileSync(path.join(ROOT, p), 'utf8'));
  const axe = read('src/main/resources/data/minecraft/tags/blocks/mineable/axe.json').values;
  // not in #minecraft:planks / logs, so the wood break noise needs them by name
  const noise = read('src/main/resources/data/apocalypse_firstlight/tags/blocks/noise_wood_blocks.json').values;
  const tiers = ['needs_stone_tool', 'needs_iron_tool', 'needs_diamond_tool'].flatMap(t => { const f = path.join(DATA, 'minecraft/tags/blocks', t + '.json'); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')).values : []; });
  const others = ['minecraft/tags/blocks/planks.json', 'minecraft/tags/items/planks.json', 'minecraft/tags/blocks/logs.json', 'minecraft/tags/items/logs.json']
    .flatMap(t => { const f = path.join(DATA, t); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')).values : []; });
  const mass = read('src/main/resources/data/apocalypse_firstlight/item_mass/afl_content_v1.json');
  const en = read('src/main/resources/assets/apocalypse_firstlight/lang/en_us.json'), zh = read('src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json');
  const massOf = id => { const e = mass.items?.[id] ?? mass[id]; return e && typeof e === 'object' ? e.unit_mass_kg : e; };
  // the timber's corner radius: the end texture's outline (TIMBER_R), the model (WoodBlocks) and FellCheck's prism test
  const src = p => fs.readFileSync(path.join(ROOT, p), 'utf8');
  if (!src('src/main/java/com/antaurora/apofirstlight/tree/WoodBlocks.java').includes('TIMBER_RADIUS = 1.5F / 16;')) fails.push('WoodBlocks.TIMBER_RADIUS is not 1.5F / 16 (TIMBER_R here)');
  if (!src('tools/mesh-trees/FellCheck.java').includes('TIMBER_RADIUS = 1.5F / 16;')) fails.push('FellCheck.TIMBER_RADIUS is not 1.5F / 16 (TIMBER_R here)');
  for (const id of IDS) {
    const full = 'apocalypse_firstlight:' + id;
    if (!axe.includes(full)) fails.push(full + ' not in minecraft:mineable/axe');
    if (!noise.includes(full)) fails.push(full + ' not in apocalypse_firstlight:noise_wood_blocks');
    if (tiers.includes(full)) fails.push(full + ' has a tool tier tag (wood: none)');
    if (others.includes(full)) fails.push(full + ' is in a vanilla planks / logs tag (would enable vanilla recipes)');
    if (!(massOf(full) > 0)) fails.push(full + ' has no item mass');
    for (const [name, lang] of [['en_us', en], ['zh_cn', zh]]) if (!lang['block.apocalypse_firstlight.' + id]) fails.push(full + ' has no ' + name + ' name');
  }
  return fails;
}

/** Contact sheet: per species a row of planks, timber side, timber end (the end masked to the rounded outline). */
function sheet(tex) {
  const W = 3 * N + 4 * 8, Hh = SPECIES.length * (N + 8) + 8, img = Buffer.alloc(W * Hh * 4, 40);
  for (let i = 0; i < W * Hh; i++) img[i * 4 + 3] = 255;
  SPECIES.forEach((sp, row) => ['planks', 'timber_side', 'timber_end'].forEach((k, col) => {
    const t = tex.find(([n]) => n === `${sp.id}_${k}`)[1].col;
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      if (k === 'timber_end') {
        const qx = Math.max(Math.abs(x + 0.5 - N / 2) - (N / 2 - TIMBER_R), 0), qy = Math.max(Math.abs(y + 0.5 - N / 2) - (N / 2 - TIMBER_R), 0);
        if (Math.hypot(qx, qy) > TIMBER_R) continue;
      }
      const o = ((row * (N + 8) + 8 + y) * W + col * (N + 8) + 8 + x) * 4, s = (y * N + x) * 4;
      img[o] = t[s]; img[o + 1] = t[s + 1]; img[o + 2] = t[s + 2];
    }
  }));
  return png(img, W, Hh);
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const at = process.argv.indexOf('--preview');
  if (at >= 0) {
    const dir = path.resolve(process.argv[at + 1]);
    fs.mkdirSync(dir, {recursive: true});
    const tex = textures();
    for (const [name, t] of tex) ['', '_s', '_n'].forEach((k, i) => fs.writeFileSync(path.join(dir, `${name}${k}.png`), t.maps[i]));
    fs.writeFileSync(path.join(dir, 'sheet.png'), sheet(tex));
    console.log('wrote ' + tex.length * 3 + ' textures and sheet.png to ' + dir);
  } else if (process.argv.includes('--check')) {
    const stale = [];
    for (const [file, data] of outputs()) { const cur = fs.existsSync(file) ? fs.readFileSync(file) : null; if (!cur || !cur.equals(Buffer.isBuffer(data) ? data : Buffer.from(data))) stale.push('stale ' + path.relative(ROOT, file)); }
    const fails = [...stale, ...audit()];
    for (const f of fails.slice(0, 30)) console.log('FAIL ' + f);
    console.log(fails.length ? `CHECK FAILED: ${fails.length}` : `CHECK OK (${IDS.length} blocks)`);
    process.exit(fails.length ? 1 : 0);
  } else {
    const list = outputs();
    for (const [file] of list) fs.mkdirSync(path.dirname(file), {recursive: true});
    for (const [file, data] of list) fs.writeFileSync(file, data);
    console.log('wrote ' + list.length + ' files');
  }
}
