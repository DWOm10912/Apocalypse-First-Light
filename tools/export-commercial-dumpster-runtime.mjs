import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assetRoot = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const sourcePath = path.join(root, 'src/main/blockbench/commercial_dumpster.bbmodel');
const texturePath = path.join(assetRoot, 'textures/block/commercial_dumpster.png');
const sourceBytes = fs.readFileSync(sourcePath);
const source = JSON.parse(sourceBytes);
const checkOnly = process.argv.includes('--check');
const applyDisplay = process.argv.includes('--apply-display');

const DISPLAY = {
    thirdperson_righthand: { scale: [0.25, 0.25, 0.25] },
    thirdperson_lefthand: { scale: [0.25, 0.25, 0.25] },
    firstperson_righthand: { scale: [0.3, 0.3, 0.3] },
    firstperson_lefthand: { scale: [0.3, 0.3, 0.3] },
    gui: { rotation: [24, 140, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
    ground: { translation: [0, 2, 0], scale: [0.3, 0.3, 0.3] },
    fixed: { rotation: [0, 180, 0], translation: [0, -3, 0], scale: [0.3, 0.3, 0.3] }
};

if (applyDisplay) {
    source.display = structuredClone(DISPLAY);
    fs.writeFileSync(sourcePath, `${JSON.stringify(source)}\n`);
} else {
    assert.deepEqual(source.display, DISPLAY, 'Blockbench display transforms are missing or stale; run with --apply-display');
}

assert.equal(source.meta?.model_format, 'java_block');
assert.equal(source.elements?.length, 260, 'Approved geometry cube count changed');
assert.equal(source.groups?.length, 31, 'Approved group count changed');
assert.equal(source.textures?.length, 1);
assert.deepEqual(source.resolution, { width: 128, height: 128 });
for (const side of ['left', 'right']) {
    const lid = source.groups.find(group => group.name === `${side}_lid_assembly`);
    assert(lid, `Missing ${side} lid assembly`);
    assert.deepEqual(lid.rotation, [0, 0, 0], `${side} lid must export closed`);
    assert.equal(lid.origin[1], 21);
    assert.equal(lid.origin[2], 15);
}

const embeddedTexture = source.textures[0];
assert(embeddedTexture.source?.startsWith('data:image/png;base64,'), 'Embedded texture is missing');
assert(fs.readFileSync(texturePath).equals(Buffer.from(embeddedTexture.source.split(',')[1], 'base64')),
    'Saved texture differs from the approved Blockbench source');

const clean = value => Math.round(value * 1e8) / 1e8;
const near = (left, right) => Math.abs(left - right) < 1e-7;
const volume = element => element.to.reduce((result, value, axis) => result * (value - element.from[axis]), 1);
const faceAxes = {
    north: [2, 0, 0, -1, 1, -1],
    south: [2, 1, 0, 1, 1, -1],
    west: [0, 0, 2, 1, 1, -1],
    east: [0, 1, 2, -1, 1, -1],
    up: [1, 1, 0, 1, 2, 1],
    down: [1, 0, 0, 1, 2, -1]
};

function runtimeElement(element) {
    assert.equal(element.type, 'cube', `Unsupported element type: ${element.name}`);
    const rotation = element.rotation ?? [0, 0, 0];
    const axes = rotation.flatMap((value, axis) => value ? [axis] : []);
    assert(axes.length <= 1, `Multiple rotation axes are unsupported: ${element.name}`);
    if (axes.length) assert([-45, -22.5, 22.5, 45].includes(rotation[axes[0]]), `Unsupported angle: ${element.name}`);
    const faces = {};
    for (const [direction, face] of Object.entries(element.faces)) {
        if (face.texture === null) continue;
        assert(face.texture === 0 || face.texture === embeddedTexture.uuid,
            `Unexpected texture binding: ${element.name}/${direction}`);
        faces[direction] = {
            uv: face.uv.map((value, index) => clean(value * 16
                    / (index % 2 ? source.resolution.height : source.resolution.width))),
            texture: '#0',
            ...(face.rotation ? { rotation: face.rotation } : {})
        };
    }
    return {
        name: element.name,
        from: element.from.map(clean),
        to: element.to.map(clean),
        ...(axes.length ? {
            rotation: {
                origin: element.origin.map(clean),
                axis: 'xyz'[axes[0]],
                angle: rotation[axes[0]],
                rescale: Boolean(element.rescale)
            }
        } : {}),
        shade: element.shade !== false,
        faces
    };
}

function clipUv(faceName, face, whole, low, high) {
    const [normalAxis, positive, uAxis, uSign, vAxis, vSign] = faceAxes[faceName];
    if (!near(positive ? high[normalAxis] : low[normalAxis],
            positive ? whole.to[normalAxis] : whole.from[normalAxis])) return null;
    const interval = (axis, sign) => {
        const size = whole.to[axis] - whole.from[axis];
        if (near(size, 0)) return [0, 1];
        return sign > 0
            ? [(low[axis] - whole.from[axis]) / size, (high[axis] - whole.from[axis]) / size]
            : [(whole.to[axis] - high[axis]) / size, (whole.to[axis] - low[axis]) / size];
    };
    const [u0, u1] = interval(uAxis, uSign);
    const [v0, v1] = interval(vAxis, vSign);
    const [minU, minV, maxU, maxV] = face.uv;
    return {
        ...face,
        uv: [
            minU + (maxU - minU) * u0,
            minV + (maxV - minV) * v0,
            minU + (maxU - minU) * u1,
            minV + (maxV - minV) * v1
        ].map(clean)
    };
}

const halves = { master: [], secondary: [] };
for (const sourceElement of source.elements.filter(element => element.export !== false)) {
    const whole = runtimeElement(sourceElement);
    let totalVolume = 0;
    let fragmentCount = 0;
    for (const [part, minimumX, maximumX, localOffset] of [
        ['master', -16, 16, 0],
        ['secondary', 16, 48, 16]
    ]) {
        const low = [Math.max(whole.from[0], minimumX), whole.from[1], whole.from[2]];
        const high = [Math.min(whole.to[0], maximumX), whole.to[1], whole.to[2]];
        if (high[0] - low[0] <= 1e-8) continue;
        const fragment = structuredClone(whole);
        fragment.from = low.map((value, axis) => clean(value - (axis === 0 ? localOffset : 0)));
        fragment.to = high.map((value, axis) => clean(value - (axis === 0 ? localOffset : 0)));
        fragment.faces = {};
        for (const [faceName, face] of Object.entries(whole.faces)) {
            const clipped = clipUv(faceName, face, whole, low, high);
            if (clipped) fragment.faces[faceName] = clipped;
        }
        if (fragment.rotation) fragment.rotation.origin[0] = clean(fragment.rotation.origin[0] - localOffset);
        halves[part].push(fragment);
        fragmentCount++;
        totalVolume += (high[0] - low[0]) * (whole.to[1] - whole.from[1]) * (whole.to[2] - whole.from[2]);
    }
    assert(near(totalVolume, volume(whole)), `Geometry was lost while splitting ${whole.name}`);
    if (fragmentCount > 1 && whole.rotation) {
        assert.equal(whole.rotation.axis, 'x', `Unsafe rotated split across X=16: ${whole.name}`);
    }
}

const textures = {
    '0': 'apocalypse_firstlight:block/commercial_dumpster',
    particle: 'apocalypse_firstlight:block/commercial_dumpster'
};
const blockModel = elements => ({
    credit: 'Apocalypse: First Light — Commercial Dumpster',
    parent: 'minecraft:block/block',
    ambientocclusion: false,
    texture_size: [128, 128],
    textures,
    elements
});
const centeredItemElements = source.elements.filter(element => element.export !== false).map(runtimeElement).map(element => {
    const centered = structuredClone(element);
    centered.from[0] = clean(centered.from[0] - 8);
    centered.to[0] = clean(centered.to[0] - 8);
    if (centered.rotation) centered.rotation.origin[0] = clean(centered.rotation.origin[0] - 8);
    return centered;
});
const itemModel = {
    ...blockModel(centeredItemElements),
    gui_light: 'side',
    display: structuredClone(source.display)
};
const variants = {};
for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
    for (const part of ['master', 'secondary']) {
        variants[`facing=${facing},part=${part}`] = {
            model: `apocalypse_firstlight:block/commercial_dumpster/${part}`,
            ...(y ? { y } : {})
        };
    }
}

const outputs = new Map([
    [path.join(assetRoot, 'models/block/commercial_dumpster/master.json'), blockModel(halves.master)],
    [path.join(assetRoot, 'models/block/commercial_dumpster/secondary.json'), blockModel(halves.secondary)],
    [path.join(assetRoot, 'models/item/commercial_dumpster.json'), itemModel],
    [path.join(assetRoot, 'blockstates/commercial_dumpster.json'), { variants }]
]);
for (const [outputPath, value] of outputs) {
    const encoded = Buffer.from(`${JSON.stringify(value, null, 2)}\n`);
    if (checkOnly) {
        assert(fs.existsSync(outputPath) && fs.readFileSync(outputPath).equals(encoded),
            `Generated resource is missing or stale: ${path.relative(root, outputPath)}`);
    } else {
        fs.mkdirSync(path.dirname(outputPath), { recursive: true });
        fs.writeFileSync(outputPath, encoded);
    }
}

for (const element of [...halves.master, ...halves.secondary, ...centeredItemElements]) {
    assert([...element.from, ...element.to].every(value => value >= -16 && value <= 32),
        `Runtime coordinate outside Java model bounds: ${element.name}`);
}
assert.deepEqual(itemModel.display.firstperson_righthand.scale, [0.3, 0.3, 0.3]);
assert.deepEqual(itemModel.display.thirdperson_righthand.scale, [0.25, 0.25, 0.25]);
assert.deepEqual(itemModel.display.gui.scale, [0.4, 0.4, 0.4]);

console.log(JSON.stringify({
    sourceCubes: source.elements.length,
    masterElements: halves.master.length,
    secondaryElements: halves.secondary.length,
    itemElements: centeredItemElements.length,
    sourceDisplaySynchronized: true,
    firstPersonScale: 0.3,
    thirdPersonScale: 0.25,
    guiScale: 0.4,
    mode: checkOnly ? 'check' : 'write'
}, null, 2));
