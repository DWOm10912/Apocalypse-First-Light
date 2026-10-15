// Terrain smoothing prototype: export the blocks of a test area from a save for the offline walking check
// (tools/terrain-smooth/PhysicsCheck.java, real-world mode). Reads the Anvil region files (1.20.1) directly; the game
// must have saved the area (quit to the title screen first).
//   node tools/terrain-smooth/export-area.mjs <world dir> <minX> <minZ> <size> <minY> <maxY> <out file>
// Each block becomes one byte: the TerrainSmooth#cell kind (bits 0-2: 0 air, 1 cover, 2 leaf, 3 fluid, 4 natural, 5 other,
// 6 unknown, 7 tree), for natural ground the block (bits 3-6: 1 grass block, 2 dirt, 3 coarse dirt, 4 sand, 5 gravel, 6 stone,
// 7 podzol, 8 mud, 9 clay, 10 snow block, 11 red sand, 12 any of the seven terracottas),
// and a second byte the water surface height above the block's floor in 1/16 (0 = no water), as vanilla's fluid
// pushing reads it. The area grows by 2 columns on every side (the shapes read their neighbours).
// Header (big-endian int32): minX, minZ, width, depth, minY, height.
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';

function nbt(buf) {
  let o = 0;
  const u8 = () => buf[o++], i16 = () => { const v = buf.readInt16BE(o); o += 2; return v; }, i32 = () => { const v = buf.readInt32BE(o); o += 4; return v; };
  const str = () => { const n = buf.readUInt16BE(o); o += 2; const s = buf.toString('utf8', o, o + n); o += n; return s; };
  function payload(t) {
    switch (t) {
      case 1: return buf.readInt8(o++);
      case 2: return i16();
      case 3: return i32();
      case 4: { const v = buf.readBigInt64BE(o); o += 8; return v; }
      case 5: { const v = buf.readFloatBE(o); o += 4; return v; }
      case 6: { const v = buf.readDoubleBE(o); o += 8; return v; }
      case 7: { const n = i32(); const v = buf.subarray(o, o + n); o += n; return v; }
      case 8: return str();
      case 9: { const et = u8(), n = i32(), a = []; for (let i = 0; i < n; i++) a.push(payload(et)); return a; }
      case 10: { const c = {}; for (;;) { const tt = u8(); if (tt === 0) return c; const name = str(); c[name] = payload(tt); } }
      case 11: { const n = i32(), a = []; for (let i = 0; i < n; i++) a.push(i32()); return a; }
      case 12: { const n = i32(), a = []; for (let i = 0; i < n; i++) { a.push(buf.readBigInt64BE(o)); o += 8; } return a; }
      default: throw Error('tag ' + t);
    }
  }
  const t = u8(); str(); return payload(t);
}

const chunks = new Map();
function chunk(dir, cx, cz) {
  const key = cx + ',' + cz;
  if (chunks.has(key)) return chunks.get(key);
  const file = path.join(dir, 'region', `r.${cx >> 5}.${cz >> 5}.mca`);
  let c = null;
  if (fs.existsSync(file)) {
    const f = fs.readFileSync(file), idx = 4 * ((cx & 31) + (cz & 31) * 32), loc = f.readUInt32BE(idx), off = (loc >> 8) * 4096;
    if (off) {
      const len = f.readUInt32BE(off), type = f[off + 4], data = f.subarray(off + 5, off + 4 + len);
      c = nbt(type === 2 ? zlib.inflateSync(data) : type === 1 ? zlib.gunzipSync(data) : data);
      if (c.Status !== 'minecraft:full' && c.Status !== 'full') c = null;
    }
  }
  chunks.set(key, c);
  return c;
}

function state(dir, x, y, z) {
  const c = chunk(dir, x >> 4, z >> 4);
  if (!c) return null;
  const sec = c.sections.find(s => s.Y === (y >> 4));
  if (!sec || !sec.block_states) return {Name: 'minecraft:air'};
  const pal = sec.block_states.palette, data = sec.block_states.data;
  if (!data) return pal[0];
  const bits = Math.max(4, Math.ceil(Math.log2(pal.length))), per = Math.floor(64 / bits), i = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15);
  const word = data[Math.floor(i / per)];
  return pal[Number((BigInt.asUintN(64, word) >> BigInt((i % per) * bits)) & ((1n << BigInt(bits)) - 1n))];
}

const NATURAL = {'grass_block': 1, 'dirt': 2, 'coarse_dirt': 3, 'sand': 4, 'gravel': 5, 'stone': 6, 'podzol': 7, 'mud': 8, 'clay': 9,
  'snow_block': 10, 'red_sand': 11, 'terracotta': 12, 'white_terracotta': 12, 'orange_terracotta': 12, 'yellow_terracotta': 12,
  'brown_terracotta': 12, 'red_terracotta': 12, 'light_gray_terracotta': 12};   // TerrainSmooth.GROUND (the terracottas walk alike)
const ALWAYS_WET = new Set(['water', 'bubble_column', 'seagrass', 'tall_seagrass', 'kelp', 'kelp_plant']);
// vanilla BushBlock subclasses (and snow layers): SmoothWorld#cell counts them as cover
const COVER = new Set(['grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet',
  'red_tulip', 'orange_tulip', 'white_tulip', 'pink_tulip', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'wither_rose', 'torchflower',
  'sunflower', 'lilac', 'rose_bush', 'peony', 'pitcher_plant', 'sweet_berry_bush', 'brown_mushroom', 'red_mushroom', 'lily_pad', 'snow',
  'wheat', 'carrots', 'potatoes', 'beetroots', 'melon_stem', 'pumpkin_stem', 'attached_melon_stem', 'attached_pumpkin_stem', 'pink_petals',
  'azalea', 'flowering_azalea', 'small_dripleaf', 'mangrove_propagule', 'torchflower_crop', 'pitcher_crop']);

function classify(s) {
  if (!s) return [6, 0];
  const name = s.Name.replace(/^minecraft:/, ''), props = s.Properties || {};
  if (name === 'air' || name === 'cave_air' || name === 'void_air') return [0, 0];
  if (name === 'water') {
    const level = Number(props.level || 0);
    return [3, level >= 8 ? 16 : Math.round(16 * (8 - level) / 9)];
  }
  if (name === 'lava') return [3, 0];
  if (ALWAYS_WET.has(name) || props.waterlogged === 'true') return [3, Math.round(16 * 8 / 9)];
  if (COVER.has(name) || name.endsWith('_sapling')) return [1, 0];
  if (name.endsWith('_leaves')) return [2, 0];
  if (name in NATURAL) return [4 | (NATURAL[name] << 3), 0];
  // TerrainSmooth#cell TREE: logs, wood, stems, hyphae (BlockTags.LOGS), vines, huge mushrooms, cocoa, bee nests
  if (/_(log|wood|stem|hyphae)$/.test(name) || name === 'vine' || /_mushroom_block$/.test(name) || name === 'mushroom_stem' || name === 'cocoa' || name === 'bee_nest') return [7, 0];
  return [5, 0];
}

const [dir, sMinX, sMinZ, sSize, sMinY, sMaxY, out] = process.argv.slice(2);
if (!out) { console.error('usage: export-area.mjs <world dir> <minX> <minZ> <size> <minY> <maxY> <out file>'); process.exit(2); }
const minX = Number(sMinX) - 2, minZ = Number(sMinZ) - 2, w = Number(sSize) + 4, d = Number(sSize) + 4, minY = Number(sMinY), h = Number(sMaxY) - minY + 1;
const head = Buffer.alloc(24);
[minX, minZ, w, d, minY, h].forEach((v, i) => head.writeInt32BE(v, i * 4));
const kinds = Buffer.alloc(w * d * h), water = Buffer.alloc(w * d * h);
const count = {};
for (let z = 0; z < d; z++) for (let x = 0; x < w; x++) for (let y = 0; y < h; y++) {
  const s = state(dir, minX + x, minY + y, minZ + z);
  // a fluid above makes the water full height (FlowingFluid#getHeight)
  const [k, wet] = classify(s);
  const i = (z * w + x) * h + y;
  kinds[i] = k;
  water[i] = wet;
  const key = k & 7;
  count[key] = (count[key] || 0) + 1;
}
for (let z = 0; z < d; z++) for (let x = 0; x < w; x++) for (let y = 0; y < h - 1; y++) {
  const i = (z * w + x) * h + y;
  if (water[i] && water[i + 1]) water[i] = 16;
}
fs.writeFileSync(out, Buffer.concat([head, kinds, water]));
console.log(`wrote ${out}: x ${minX}..${minX + w - 1}, z ${minZ}..${minZ + d - 1}, y ${minY}..${minY + h - 1}; kinds`, count);
