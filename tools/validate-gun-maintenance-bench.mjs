import fs from 'node:fs';
import assert from 'node:assert/strict';

const base = 'src/main/resources/assets/apocalypse_firstlight/';
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const source = read('src/main/blockbench/gun_maintenance_bench.bbmodel');
const model = read(base + 'models/block/gun_maintenance_bench.json');
const geoPath = base + 'geo/gun_maintenance_bench.geo.json';
const geo = read(geoPath);

// Blockbench 5 emits new Bedrock display metadata. AFL's optional GeckoLib
// representation only needs the classic face-UV geometry; Java keeps Display.
if (process.argv.includes('--normalize-geo')) {
  geo.format_version = '1.12.0';
  delete geo['minecraft:geometry'][0].item_display_transforms;
  fs.writeFileSync(geoPath, JSON.stringify(geo, null, 2) + '\n');
}
assert.equal(geo.format_version, '1.12.0');
const geometry = geo['minecraft:geometry'][0];
assert(!geometry.item_display_transforms);
assert(source.elements.length > 0 && source.elements.length < 1000);
assert.equal(model.elements.length, source.elements.length);
assert.equal(geometry.bones.flatMap(b => b.cubes ?? []).length, source.elements.length);
assert.equal(source.textures.length, 1);
assert.equal(source.resolution.width, 256);
assert.equal(source.resolution.height, 256);
const png = fs.readFileSync(base + 'textures/block/gun_maintenance_bench.png');
assert.equal(png.readUInt32BE(16), 256);
assert.equal(png.readUInt32BE(20), 256);
assert(png.equals(Buffer.from(source.textures[0].source.split(',')[1], 'base64')));
assert.equal(model.textures['0'], 'apocalypse_firstlight:block/gun_maintenance_bench');
const faceNames = ['north', 'south', 'east', 'west', 'up', 'down'];
const uuid = source.textures[0].uuid;
for (const cube of source.elements) {
  assert.equal(cube.type, 'cube');
  assert(cube.from.every((v, i) => Number.isFinite(v) && v < cube.to[i]));
  for (const face of faceNames) {
    const binding = cube.faces[face].texture;
    assert.equal(typeof binding === 'number' ? source.textures[binding]?.uuid : binding, uuid);
    assert(cube.faces[face].uv.every(v => Number.isFinite(v) && v >= 0 && v <= 256));
  }
}
for (const cube of model.elements) {
  assert(cube.from.concat(cube.to).every(v => v >= -16 && v <= 32));
  if (cube.rotation) assert([-45, -22.5, 0, 22.5, 45].includes(cube.rotation.angle));
  for (const face of Object.values(cube.faces)) {
    assert.equal(face.texture, '#0');
    assert(face.uv.every(v => Number.isFinite(v) && v >= 0 && v <= 16));
    assert(!face.cullface, 'Do not cull extended-station faces against the anchor block');
  }
}
const min = [0, 1, 2].map(i => Math.min(...source.elements.map(c => c.from[i])));
const max = [0, 1, 2].map(i => Math.max(...source.elements.map(c => c.to[i])));
assert.deepEqual(min, [-8, 0, 0.05]);
assert.deepEqual(max, [24, 32, 16]);
const axisAligned = source.elements.filter(c => !(c.rotation ?? []).some(v => Math.abs(v) > 1e-6));
for (let i = 0; i < axisAligned.length; i++) for (let j = i + 1; j < axisAligned.length; j++) {
  const a = axisAligned[i], b = axisAligned[j];
  const d = [0, 1, 2].map(k => Math.min(a.to[k], b.to[k]) - Math.max(a.from[k], b.from[k]));
  assert(!d.every(v => v > 1e-5), `${a.name} overlaps ${b.name}`);
  for (let k = 0; k < 3; k++) if (d.every((v, t) => t === k || v > 1e-5)) {
    for (const side of ['from', 'to'])
      assert(Math.abs(a[side][k] - b[side][k]) >= 1e-5, `${a.name}/${b.name}: duplicate coplanar face`);
  }
}
const subtract = (a, b) => {
  const lo = a.from.map((v, i) => Math.max(v, b.from[i]));
  const hi = a.to.map((v, i) => Math.min(v, b.to[i]));
  if (lo.some((v, i) => hi[i] - v <= 1e-6)) return [a];
  const f = [...a.from], t = [...a.to], out = [];
  for (let i = 0; i < 3; i++) {
    if (lo[i] - f[i] > 1e-6) { const q = [...t]; q[i] = lo[i]; out.push({from: [...f], to: q}); f[i] = lo[i]; }
    if (t[i] - hi[i] > 1e-6) { const q = [...f]; q[i] = hi[i]; out.push({from: q, to: [...t]}); t[i] = hi[i]; }
  }
  return out;
};
const groups = new Map(source.groups.map(g => [g.uuid, g]));
const frameIds = new Set();
function walk(nodes, inFrame = false) {
  for (const n of nodes) {
    if (typeof n === 'string') { if (inFrame) frameIds.add(n); }
    else walk(n.children, inFrame || groups.get(n.uuid)?.name === 'frame');
  }
}
walk(source.outliner);
const upper = axisAligned.filter(c => frameIds.has(c.uuid) && c.to[1] > 16.5)
  .map(c => ({from: [c.from[0], Math.max(16.5, c.from[1]), c.from[2]], to: [...c.to]}));
assert(upper.length > 0);
for (const a of upper) {
  let remainder = [{from: [16 - a.to[0], a.from[1], a.from[2]], to: [16 - a.from[0], a.to[1], a.to[2]]}];
  for (const b of upper) remainder = remainder.flatMap(p => subtract(p, b));
  assert.equal(remainder.length, 0, 'Upper frame/side-panel volume must be mirror symmetric: ' + JSON.stringify({a, remainder}));
}
const bones = new Set(geometry.bones.map(b => b.name));
assert.equal(bones.size, geometry.bones.length);
for (const bone of geometry.bones) if (bone.parent) assert(bones.has(bone.parent));
// Include the four rotated tool cubes in a planar polygon-overlap check.
const dot = (a, b) => a.reduce((v, x, i) => v + x * b[i], 0);
const diff = (a, b) => a.map((v, i) => v - b[i]);
function rotate(v, angles) {
  v = [...v];
  for (let axis = 0; axis < 3; axis++) {
    const a = (angles?.[axis] ?? 0) * Math.PI / 180;
    const p = (axis + 1) % 3, q = (axis + 2) % 3;
    const x = v[p], y = v[q];
    v[p] = x * Math.cos(a) - y * Math.sin(a);
    v[q] = x * Math.sin(a) + y * Math.cos(a);
  }
  return v;
}
const buckets = new Map();
for (const cube of source.elements) for (let k = 0; k < 3; k++) for (const sign of [-1, 1]) {
  const axes = [0, 1, 2].filter(i => i !== k);
  const n0 = [0, 0, 0], u0 = [0, 0, 0], v0 = [0, 0, 0];
  n0[k] = sign; u0[axes[0]] = 1; v0[axes[1]] = 1;
  const normal = rotate(n0, cube.rotation), u = rotate(u0, cube.rotation), v = rotate(v0, cube.rotation);
  const points = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([a, b]) => {
    const p = [...cube.from]; p[k] = sign > 0 ? cube.to[k] : cube.from[k];
    p[axes[0]] = a ? cube.to[axes[0]] : cube.from[axes[0]];
    p[axes[1]] = b ? cube.to[axes[1]] : cube.from[axes[1]];
    const pivot = cube.origin ?? [0, 0, 0];
    return rotate(diff(p, pivot), cube.rotation).map((x, i) => x + pivot[i]);
  });
  const key = [...normal, dot(normal, points[0])].map(x => Math.round(x * 1e5)).join(',');
  if (!buckets.has(key)) buckets.set(key, []);
  buckets.get(key).push({name: cube.name, points, u, v});
}
const cross2 = (a, b, p) => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
function clip(subject, boundary) {
  for (let i = 0; i < boundary.length && subject.length; i++) {
    const a = boundary[i], b = boundary[(i + 1) % boundary.length], next = [];
    for (let j = 0; j < subject.length; j++) {
      const p = subject[j], q = subject[(j + 1) % subject.length];
      const dp = cross2(a, b, p), dq = cross2(a, b, q);
      if (dp >= -1e-8) next.push(p);
      if ((dp > 1e-8 && dq < -1e-8) || (dp < -1e-8 && dq > 1e-8)) {
        const t = dp / (dp - dq); next.push(p.map((x, k) => x + (q[k] - x) * t));
      }
    }
    subject = next;
  }
  return subject;
}
for (const faces of buckets.values()) for (let i = 0; i < faces.length; i++) for (let j = i + 1; j < faces.length; j++) {
  const a = faces[i], b = faces[j];
  const project = p => [dot(diff(p, a.points[0]), a.u), dot(diff(p, a.points[0]), a.v)];
  const polygon = clip(b.points.map(project), a.points.map(project));
  const area = Math.abs(polygon.reduce((s, p, k) => {
    const q = polygon[(k + 1) % polygon.length]; return s + p[0] * q[1] - p[1] * q[0];
  }, 0)) / 2;
  assert(area < 1e-6, `Coplanar surface area ${area}: ${a.name}/${b.name}`);
}
console.log(JSON.stringify({asset: 'gun_maintenance_bench', cubes: source.elements.length,
  atlas: '256x256', bounds: {min, max}, upperSideSymmetry: 'PASS',
  textureBindings: 'PASS', axisAlignedOverlapPairs: 0, duplicateAxisAlignedFaces: 0,
  coplanarFacesIncludingRotatedTools: 0,
  javaModel: 'PASS', geo: 'PASS',
  inGameValidation: 'Asset-only check; see gun_maintenance_bench_block_v1.md for runtime verification'}, null, 2));
