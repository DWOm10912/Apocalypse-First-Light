import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(root, 'src/main/blockbench/modern_office_desk.bbmodel');
const checkOnly = process.argv.includes('--check');
const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
const clean = value => Math.round(value * 1e8) / 1e8;
const maxY = Math.max(...source.elements.map(element => element.to[1]));

const shiftedPrefixes = [
    'tabletop_',
    'front_',
    'rear_',
    'cross_',
    'modesty_',
    'panel_',
    'cable_',
    'tray_'
];
const shiftedFragments = [
    '_top_support',
    '_top_socket',
    '_upper_corner_bracket',
    '_table_mount',
    '_mount_screw_',
    '_joint_plate_9.95',
    '_joint_screw_9.95'
];
const extendedFragments = ['_upright', '_outer_edge'];

function modeFor(name) {
    if (shiftedPrefixes.some(prefix => name.startsWith(prefix))) return 'shift';
    if (shiftedFragments.some(fragment => name.includes(fragment))) return 'shift';
    if (extendedFragments.some(fragment => name.includes(fragment))) return 'extend';
    return 'unchanged';
}

if (!checkOnly) {
    assert.equal(maxY, 12, 'Desk is not at the original 12-unit height; refusing to apply twice');
    const counts = { shift: 0, extend: 0, unchanged: 0 };
    for (const element of source.elements) {
        const mode = modeFor(element.name);
        counts[mode]++;
        if (mode === 'shift') {
            element.from[1] = clean(element.from[1] + 1.5);
            element.to[1] = clean(element.to[1] + 1.5);
            if (element.origin) element.origin[1] = clean(element.origin[1] + 1.5);
        } else if (mode === 'extend') {
            element.to[1] = clean(element.to[1] + 1.5);
        }
    }
    source.visible_box = [3, 1, 0];
    fs.writeFileSync(sourcePath, `${JSON.stringify(source)}\n`);
    console.log(JSON.stringify({ mode: 'write', counts }, null, 2));
}

const checked = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
assert.equal(checked.elements.length, 145);
assert.equal(Math.min(...checked.elements.map(element => element.from[1])), 0);
assert.equal(Math.max(...checked.elements.map(element => element.to[1])), 13.5);

for (const element of checked.elements) {
    const mode = modeFor(element.name);
    if (mode === 'shift' && element.name.startsWith('tabletop_')) {
        assert(element.from[1] >= 12.4, `Tabletop element was not raised: ${element.name}`);
    }
    if (mode === 'extend') {
        assert(element.to[1] >= 12.1, `Leg element was not extended: ${element.name}`);
    }
}

const lowerFrameNames = checked.elements.filter(element => modeFor(element.name) === 'unchanged')
    .filter(element => element.name.includes('bottom_') || element.name.includes('lower_corner'))
    .map(element => element.name);
assert(lowerFrameNames.length >= 8, 'Expected grounded lower-frame elements to remain unchanged');

console.log(JSON.stringify({
    mode: checkOnly ? 'check' : 'verified',
    cubes: checked.elements.length,
    height: 13.5,
    groundedLowerFrameElements: lowerFrameNames.length
}, null, 2));
