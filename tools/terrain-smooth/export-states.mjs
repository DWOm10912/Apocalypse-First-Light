// Terrain Smooth: export the full block states (name + properties) of an area from a save, for the seam audit
// (tools/terrain-smooth/SeamAudit.java, area mode; docs/worldgen/terrain_smooth_v1.md X). Reads the Anvil region files
// (1.20.1) directly and never writes the save; the game must have saved the area (quit to the title screen first).
//   node tools/terrain-smooth/export-states.mjs <world dir> <centre x> <centre z> <radius> <minY> <maxY> <out file>
// Output: header int32 BE: minX, minZ, w, d, minY, h, paletteCount; then the palette strings (uint16 length + utf8,
// "name[key=value,...]" with sorted keys); then a uint16 BE palette index per block, order ((z * w + x) * h + y);
// 0xFFFF = chunk missing or not fully generated.
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

const globalPal = new Map();
const palList = [];
function gid(s) {
  const props = s.Properties || {};
  const keys = Object.keys(props).sort();
  const name = s.Name + (keys.length ? '[' + keys.map(k => k + '=' + props[k]).join(',') + ']' : '');
  let i = globalPal.get(name);
  if (i === undefined) { i = palList.length; palList.push(name); globalPal.set(name, i); }
  return i;
}

const regions = new Map();
function regionFile(dir, rx, rz) {
  const key = rx + ',' + rz;
  if (!regions.has(key)) {
    const file = path.join(dir, 'region', 'r.' + rx + '.' + rz + '.mca');
    regions.set(key, fs.existsSync(file) ? fs.readFileSync(file) : null);
  }
  return regions.get(key);
}

const chunks = new Map();
// returns Map sectionY -> Uint16Array(4096) of global ids, or null
function chunk(dir, cx, cz) {
  const key = cx + ',' + cz;
  if (chunks.has(key)) return chunks.get(key);
  const f = regionFile(dir, cx >> 5, cz >> 5);
  let out = null;
  if (f) {
    const idx = 4 * ((cx & 31) + (cz & 31) * 32), loc = f.readUInt32BE(idx), off = (loc >>> 8) * 4096;
    if (off) {
      const len = f.readUInt32BE(off), type = f[off + 4], data = f.subarray(off + 5, off + 4 + len);
      const c = nbt(type === 2 ? zlib.inflateSync(data) : type === 1 ? zlib.gunzipSync(data) : data);
      if (c.Status === 'minecraft:full' || c.Status === 'full') {
        out = new Map();
        for (const sec of c.sections) {
          if (!sec.block_states) continue;
          const pal = sec.block_states.palette.map(gid), data = sec.block_states.data;
          const arr = new Uint16Array(4096);
          if (!data) arr.fill(pal[0]);
          else {
            const bits = Math.max(4, Math.ceil(Math.log2(pal.length))), per = Math.floor(64 / bits), mask = (1n << BigInt(bits)) - 1n;
            for (let i = 0; i < 4096; i++) {
              const word = BigInt.asUintN(64, data[Math.floor(i / per)]);
              arr[i] = pal[Number((word >> BigInt((i % per) * bits)) & mask)];
            }
          }
          out.set(sec.Y, arr);
        }
      }
    }
  }
  chunks.set(key, out);
  return out;
}

const [dir, sx, sz, sr, sMinY, sMaxY, outFile] = process.argv.slice(2);
const cx0 = Number(sx), cz0 = Number(sz), r = Number(sr);
const minX = cx0 - r, minZ = cz0 - r, w = 2 * r + 1, d = 2 * r + 1, minY = Number(sMinY), h = Number(sMaxY) - minY + 1;
const air = gid({Name: 'minecraft:air'});
const blocks = new Uint16Array(w * d * h);
for (let z = 0; z < d; z++) for (let x = 0; x < w; x++) {
  const wx = minX + x, wz = minZ + z;
  const c = chunk(dir, wx >> 4, wz >> 4);
  for (let y = 0; y < h; y++) {
    const wy = minY + y, i = (z * w + x) * h + y;
    if (!c) { blocks[i] = 0xFFFF; continue; }
    const sec = c.get(wy >> 4);
    blocks[i] = sec ? sec[((wy & 15) * 16 + (wz & 15)) * 16 + (wx & 15)] : air;
  }
}
const head = Buffer.alloc(28);
[minX, minZ, w, d, minY, h, palList.length].forEach((v, i) => head.writeInt32BE(v, i * 4));
const parts = [head];
for (const s of palList) { const b = Buffer.from(s, 'utf8'); const l = Buffer.alloc(2); l.writeUInt16BE(b.length); parts.push(l, b); }
const body = Buffer.alloc(blocks.length * 2);
for (let i = 0; i < blocks.length; i++) body.writeUInt16BE(blocks[i], i * 2);
parts.push(body);
fs.writeFileSync(outFile, Buffer.concat(parts));
console.log('wrote ' + outFile + ' x ' + minX + '..' + (minX + w - 1) + ' z ' + minZ + '..' + (minZ + d - 1) + ' y ' + minY + '..' + (minY + h - 1) + ' palette ' + palList.length);
