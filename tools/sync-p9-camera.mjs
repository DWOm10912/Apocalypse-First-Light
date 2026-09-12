// Structure-only sync. Never exports or rewrites animation, equip, display or Java.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root = new URL('../', import.meta.url);
const sourcePath = new URL('src/main/blockbench/p9_01.bbmodel', root);
const geoPath = new URL('src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json', root);
const read = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const source = read(sourcePath), geo = read(geoPath);
const before = structuredClone(source), beforeGeo = structuredClone(geo);
const uuid = '9b79c0f2-2b75-4b43-99b4-10e373339260';
// Authoring reference: retain inverse-HIP eye X/Z, lower Y to the rear sight top.
// This is an artist pivot, not a runtime eye/ADS calibration.
const pivot = [-6.51866, 11.59, 28.32887];
let camera = source.groups.find(g => g.name === 'camera');
if (!camera) {
    camera = {name:'camera', uuid, export:true, locked:false, origin:pivot,
        rotation:[0,0,0], children:[], reset:false, shade:true, mirror_uv:false,
        visibility:true, autouv:0, isOpen:true};
    source.groups.push(camera);
    source.outliner.push({uuid, children:[]});
}
if (process.argv.includes('--lower-to-sight')) {
    camera.origin[1] = source.groups.find(g => g.name === 'sight_anchor').origin[1];
}
assert.equal(source.groups.filter(g => g.name === 'camera').length, 1);
assert.equal(camera.export, true);
assert(!camera.scale);
assert.deepEqual(camera.rotation, [0,0,0]);
const entry = source.outliner.find(n => n.uuid === camera.uuid);
assert(entry, 'camera must be a root group');
assert.deepEqual(entry.children, []);
const bones = geo['minecraft:geometry'][0].bones;
const runtime = {name:'camera', pivot:camera.origin.map((n,i) => i === 0 ? -n : n)};
const index = bones.findIndex(b => b.name === 'camera');
if (index < 0) bones.push(runtime); else bones[index] = runtime;
assert(!bones.some(b => b.parent === 'camera'));
const withoutCamera = s => ({...s, groups:s.groups.filter(g => g.name !== 'camera'),
    outliner:s.outliner.filter(n => n.uuid !== camera.uuid)});
assert.deepEqual(withoutCamera(source), withoutCamera(before), 'Existing source data changed');
assert.deepEqual(bones.filter(b => b.name !== 'camera'),
    beforeGeo['minecraft:geometry'][0].bones.filter(b => b.name !== 'camera'));
if (process.argv.includes('--write')) {
    // Mechanical JSON structure insertion; all existing values remain byte-equivalent when serialized.
    if (JSON.stringify(source) !== JSON.stringify(before)) fs.writeFileSync(sourcePath, JSON.stringify(source));
    if (JSON.stringify(geo) !== JSON.stringify(beforeGeo)) fs.writeFileSync(geoPath, JSON.stringify(geo, null, 2)+'\n');
} else {
    assert.deepEqual(read(sourcePath), source);
    assert.deepEqual(read(geoPath), geo);
}
console.log('PASS: root camera, empty geometry, untouched existing source/geo data; '+fileURLToPath(sourcePath));
