import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourceRoot = path.join(root, 'src/main/blockbench');
const assetRoot = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const checkOnly = process.argv.includes('--check');
const clean = value => Math.round(value * 1e8) / 1e8;
const near = (left, right) => Math.abs(left - right) < 1e-7;

const faceAxes = {
    north: [2, 0, 0, -1, 1, -1],
    south: [2, 1, 0, 1, 1, -1],
    west: [0, 0, 2, 1, 1, -1],
    east: [0, 1, 2, -1, 1, -1],
    up: [1, 1, 0, 1, 2, 1],
    down: [1, 0, 0, 1, 2, -1]
};

function readSource(id, expectedCubes, expectedResolution = 128) {
    const source = JSON.parse(fs.readFileSync(path.join(sourceRoot, `${id}.bbmodel`), 'utf8'));
    assert.equal(source.meta?.model_format, 'java_block', `${id}: expected java_block source`);
    assert.equal(source.elements?.length, expectedCubes, `${id}: approved cube count changed`);
    assert.equal(source.textures?.length, 1, `${id}: expected one embedded texture`);
    assert.deepEqual(source.resolution, { width: expectedResolution, height: expectedResolution },
        `${id}: expected ${expectedResolution}x${expectedResolution} texture`);
    const embedded = source.textures[0];
    for (const element of source.elements) {
        for (const [faceName, face] of Object.entries(element.faces ?? {})) {
            assert(face.texture === null || face.texture === 0 || face.texture === embedded.uuid,
                `${id}: unexpected texture binding on ${element.name}/${faceName}`);
        }
    }
    return source;
}

function transformedElement(source, sourceElement, {
    namePrefix = '', texture = '#0', xOffset = 0, yOffset = 0, zOffset = 0,
    mirrorX = false, rotateY180 = false
} = {}) {
    const element = runtimeElement(source, sourceElement, { yOffset });
    if (mirrorX || rotateY180) {
        assert(!element.rotation, `${source.name}: transformed element cannot retain vanilla rotation: ${element.name}`);
    }

    let [fromX, fromZ, toX, toZ] = [element.from[0], element.from[2], element.to[0], element.to[2]];
    if (mirrorX || rotateY180) [fromX, toX] = [16 - toX, 16 - fromX];
    if (rotateY180) [fromZ, toZ] = [16 - toZ, 16 - fromZ];
    element.from[0] = clean(fromX + xOffset);
    element.to[0] = clean(toX + xOffset);
    element.from[2] = clean(fromZ + zOffset);
    element.to[2] = clean(toZ + zOffset);

    const remapDirection = direction => {
        if (rotateY180) return ({ north: 'south', south: 'north', east: 'west', west: 'east' })[direction] ?? direction;
        if (mirrorX) return ({ east: 'west', west: 'east' })[direction] ?? direction;
        return direction;
    };
    element.faces = Object.fromEntries(Object.entries(element.faces).map(([direction, face]) => [
        remapDirection(direction),
        { ...face, texture }
    ]));
    element.name = `${namePrefix}${element.name}`;
    return element;
}

function runtimeElement(source, element, { stripRotation = false, yOffset = 0 } = {}) {
    assert.equal(element.type, 'cube', `Unsupported element type: ${element.name}`);
    const faces = {};
    for (const [direction, face] of Object.entries(element.faces ?? {})) {
        if (face.texture === null) continue;
        faces[direction] = {
            uv: face.uv.map((value, index) => clean(value * 16
                / (index % 2 ? source.resolution.height : source.resolution.width))),
            texture: '#0',
            ...(face.rotation ? { rotation: face.rotation } : {})
        };
    }
    const rotation = element.rotation ?? [0, 0, 0];
    const axes = rotation.flatMap((value, axis) => value ? [axis] : []);
    if (!stripRotation) {
        assert(axes.length <= 1, `${source.name}: unsupported multi-axis rotation on ${element.name}`);
        if (axes.length) assert([-45, -22.5, 22.5, 45].includes(rotation[axes[0]]),
            `${source.name}: unsupported vanilla element angle ${rotation[axes[0]]} on ${element.name}`);
    }
    return {
        name: element.name,
        from: element.from.map((value, axis) => clean(value + (axis === 1 ? yOffset : 0))),
        to: element.to.map((value, axis) => clean(value + (axis === 1 ? yOffset : 0))),
        ...(!stripRotation && axes.length ? {
            rotation: {
                origin: element.origin.map((value, axis) => clean(value + (axis === 1 ? yOffset : 0))),
                axis: 'xyz'[axes[0]],
                angle: rotation[axes[0]],
                rescale: Boolean(element.rescale)
            }
        } : {}),
        shade: element.shade !== false,
        faces
    };
}

function textures(id) {
    return {
        '0': `apocalypse_firstlight:block/${id}`,
        particle: `apocalypse_firstlight:block/${id}`
    };
}

function blockModel(id, elements, extra = {}) {
    return {
        credit: `Apocalypse: First Light — ${id}`,
        parent: 'minecraft:block/block',
        ambientocclusion: false,
        texture_size: [128, 128],
        textures: textures(id),
        ...extra,
        elements
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

function splitDesk(source) {
    const parts = { left: [], center: [], right: [] };
    const ranges = [
        ['left', -16, 0, -16],
        ['center', 0, 16, 0],
        ['right', 16, 32, 16]
    ];
    for (const sourceElement of source.elements.filter(element => element.export !== false)) {
        const whole = runtimeElement(source, sourceElement);
        assert(!whole.rotation, `Desk element unexpectedly rotated: ${whole.name}`);
        let sourceVolume = 1;
        for (let axis = 0; axis < 3; axis++) sourceVolume *= whole.to[axis] - whole.from[axis];
        let splitVolume = 0;
        for (const [part, minX, maxX, offsetX] of ranges) {
            const low = [Math.max(whole.from[0], minX), whole.from[1], whole.from[2]];
            const high = [Math.min(whole.to[0], maxX), whole.to[1], whole.to[2]];
            if (high[0] - low[0] <= 1e-8) continue;
            const fragment = structuredClone(whole);
            fragment.from = low.map((value, axis) => clean(value - (axis === 0 ? offsetX : 0)));
            fragment.to = high.map((value, axis) => clean(value - (axis === 0 ? offsetX : 0)));
            fragment.faces = {};
            for (const [faceName, face] of Object.entries(whole.faces)) {
                const clipped = clipUv(faceName, face, whole, low, high);
                if (clipped) fragment.faces[faceName] = clipped;
            }
            parts[part].push(fragment);
            splitVolume += (high[0] - low[0]) * (whole.to[1] - whole.from[1]) * (whole.to[2] - whole.from[2]);
        }
        assert(near(sourceVolume, splitVolume), `Desk geometry lost while splitting ${whole.name}`);
    }
    return parts;
}

function facingVariants(modelFor, extraProperties = [{}]) {
    const variants = {};
    for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
        for (const properties of extraProperties) {
            const suffix = Object.entries(properties).map(([key, value]) => `,${key}=${value}`).join('');
            variants[`facing=${facing}${suffix}`] = {
                model: modelFor(properties),
                ...(y ? { y } : {})
            };
        }
    }
    return { variants };
}

const desk = readSource('modern_office_desk', 145);
const chair = readSource('modern_office_chair', 202);
const monitor = readSource('modern_lcd_monitor', 110);
const keyboard = readSource('office_keyboard', 34, 64);
const mouse = readSource('office_mouse', 13, 32);
const deskParts = splitDesk(desk);

const outputs = new Map();
for (const part of ['left', 'center', 'right']) {
    outputs.set(path.join(assetRoot, `models/block/modern_office_desk/${part}.json`),
        blockModel('modern_office_desk', deskParts[part]));
}
const deskVariants = {};
for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
    for (const part of ['left', 'center', 'right']) {
        deskVariants[`facing=${facing},part=${part}`] = {
            model: `apocalypse_firstlight:block/modern_office_desk/${part}`,
            ...(y ? { y } : {})
        };
    }
}
outputs.set(path.join(assetRoot, 'blockstates/modern_office_desk.json'), { variants: deskVariants });
outputs.set(path.join(assetRoot, 'models/item/modern_office_desk.json'), {
    ...blockModel('modern_office_desk', desk.elements.filter(element => element.export !== false)
        .map(element => runtimeElement(desk, element))),
    gui_light: 'side',
    display: {
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.2, 0.2, 0.2] },
        thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.2, 0.2, 0.2] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
        gui: { rotation: [30, 135, 0], translation: [0, -1.5, 0], scale: [0.28, 0.28, 0.28] },
        ground: { translation: [0, 2, 0], scale: [0.22, 0.22, 0.22] },
        fixed: { rotation: [0, 180, 0], translation: [0, -2, 0], scale: [0.25, 0.25, 0.25] }
    }
});

const chairByAngle = new Map();
for (const element of chair.elements.filter(element => element.export !== false)) {
    const rotation = element.rotation ?? [0, 0, 0];
    assert(near(rotation[0], 0) && near(rotation[2], 0), `Chair has unsupported X/Z rotation: ${element.name}`);
    const angle = clean(rotation[1] || 0);
    assert([0, 72, 144, 216, 288].includes(angle), `Chair has unexpected Y rotation: ${element.name}`);
    if (!chairByAngle.has(angle)) chairByAngle.set(angle, []);
    chairByAngle.get(angle).push(runtimeElement(chair, element, { stripRotation: true }));
}
const chairChildren = {};
for (const angle of [...chairByAngle.keys()].sort((a, b) => a - b)) {
    chairChildren[`rotation_${angle}`] = blockModel('modern_office_chair', chairByAngle.get(angle), angle ? {
        transform: { rotation: [0, angle, 0], origin: [0.5, 0, 0.5] }
    } : {});
}
const chairModel = {
    credit: 'Apocalypse: First Light — modern_office_chair',
    loader: 'forge:composite',
    ambientocclusion: false,
    textures: textures('modern_office_chair'),
    children: chairChildren
};
outputs.set(path.join(assetRoot, 'models/block/modern_office_chair.json'), chairModel);
outputs.set(path.join(assetRoot, 'blockstates/modern_office_chair.json'),
    facingVariants(() => 'apocalypse_firstlight:block/modern_office_chair'));
outputs.set(path.join(assetRoot, 'models/item/modern_office_chair.json'), {
    parent: 'apocalypse_firstlight:block/modern_office_chair',
    gui_light: 'side',
    display: {
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.5, 0.5, 0.5] },
        thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.5, 0.5, 0.5] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 1, 0], scale: [0.55, 0.55, 0.55] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 1, 0], scale: [0.55, 0.55, 0.55] },
        gui: { rotation: [25, 135, 0], translation: [0, -1, 0], scale: [0.62, 0.62, 0.62] },
        ground: { translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
        fixed: { rotation: [0, 180, 0], translation: [0, -1, 0], scale: [0.55, 0.55, 0.55] }
    }
});

const monitorElements = monitor.elements.filter(element => element.export !== false)
    .map(element => runtimeElement(monitor, element));
const loweredMonitorElements = monitor.elements.filter(element => element.export !== false)
    .map(element => runtimeElement(monitor, element, { yOffset: -2.5 }));
outputs.set(path.join(assetRoot, 'models/block/modern_lcd_monitor.json'),
    blockModel('modern_lcd_monitor', monitorElements));
outputs.set(path.join(assetRoot, 'models/block/modern_lcd_monitor_lowered.json'),
    blockModel('modern_lcd_monitor', loweredMonitorElements));
outputs.set(path.join(assetRoot, 'blockstates/modern_lcd_monitor.json'), facingVariants(
    properties => `apocalypse_firstlight:block/modern_lcd_monitor${properties.lowered === 'true' ? '_lowered' : ''}`,
    [{ lowered: 'false' }, { lowered: 'true' }]
));
outputs.set(path.join(assetRoot, 'models/item/modern_lcd_monitor.json'), {
    parent: 'apocalypse_firstlight:block/modern_lcd_monitor',
    gui_light: 'side',
    display: {
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2, 0], scale: [0.65, 0.65, 0.65] },
        thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2, 0], scale: [0.65, 0.65, 0.65] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2, 0], scale: [0.7, 0.7, 0.7] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2, 0], scale: [0.7, 0.7, 0.7] },
        gui: { rotation: [25, 135, 0], translation: [0, 0, 0], scale: [0.72, 0.72, 0.72] },
        ground: { translation: [0, 2, 0], scale: [0.65, 0.65, 0.65] },
        fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.7, 0.7, 0.7] }
    }
});

const stationElements = [
    ...monitor.elements.filter(element => element.export !== false).map(element =>
        transformedElement(monitor, element, {
            namePrefix: 'monitor_', texture: '#monitor', zOffset: 3.2
        })),
    ...keyboard.elements.filter(element => element.export !== false).map(element =>
        transformedElement(keyboard, element, {
            namePrefix: 'keyboard_', texture: '#keyboard', xOffset: 1.2, zOffset: -1.9, mirrorX: true
        })),
    ...mouse.elements.filter(element => element.export !== false).map(element =>
        transformedElement(mouse, element, {
            namePrefix: 'mouse_', texture: '#mouse', xOffset: -6.7, zOffset: -2, rotateY180: true
        }))
];
const loweredStationElements = stationElements.map(element => ({
    ...structuredClone(element),
    from: element.from.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0))),
    to: element.to.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0))),
    ...(element.rotation ? {
        rotation: {
            ...element.rotation,
            origin: element.rotation.origin.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0)))
        }
    } : {})
}));
const stationTextures = {
    monitor: 'apocalypse_firstlight:block/modern_lcd_monitor',
    keyboard: 'apocalypse_firstlight:block/office_keyboard',
    mouse: 'apocalypse_firstlight:block/office_mouse',
    particle: 'apocalypse_firstlight:block/modern_lcd_monitor'
};
outputs.set(path.join(assetRoot, 'models/block/office_computer_station.json'),
    blockModel('office_computer_station', stationElements, { textures: stationTextures }));
outputs.set(path.join(assetRoot, 'models/block/office_computer_station_lowered.json'),
    blockModel('office_computer_station', loweredStationElements, { textures: stationTextures }));
outputs.set(path.join(assetRoot, 'blockstates/office_computer_station.json'), facingVariants(
    properties => `apocalypse_firstlight:block/office_computer_station${properties.lowered === 'true' ? '_lowered' : ''}`,
    [{ lowered: 'false' }, { lowered: 'true' }]
));
outputs.set(path.join(assetRoot, 'models/item/office_computer_station.json'), {
    parent: 'apocalypse_firstlight:block/office_computer_station',
    gui_light: 'side',
    display: {
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2, 0], scale: [0.65, 0.65, 0.65] },
        thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2, 0], scale: [0.65, 0.65, 0.65] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2, 0], scale: [0.7, 0.7, 0.7] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2, 0], scale: [0.7, 0.7, 0.7] },
        gui: { rotation: [25, 135, 0], translation: [0, 0, 0], scale: [0.72, 0.72, 0.72] },
        ground: { translation: [0, 2, 0], scale: [0.65, 0.65, 0.65] },
        fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.7, 0.7, 0.7] }
    }
});

const keyboardElements = keyboard.elements.filter(element => element.export !== false).map(element =>
    transformedElement(keyboard, element, {
        namePrefix: 'keyboard_', texture: '#keyboard', mirrorX: true
    }));
const loweredKeyboardElements = keyboardElements.map(element => ({
    ...structuredClone(element),
    from: element.from.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0))),
    to: element.to.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0)))
}));
const keyboardTextures = {
    keyboard: 'apocalypse_firstlight:block/office_keyboard',
    particle: 'apocalypse_firstlight:block/office_keyboard'
};
outputs.set(path.join(assetRoot, 'models/block/office_keyboard.json'),
    blockModel('office_keyboard', keyboardElements, { textures: keyboardTextures, texture_size: [64, 64] }));
outputs.set(path.join(assetRoot, 'models/block/office_keyboard_lowered.json'),
    blockModel('office_keyboard', loweredKeyboardElements, { textures: keyboardTextures, texture_size: [64, 64] }));
outputs.set(path.join(assetRoot, 'blockstates/office_keyboard.json'), facingVariants(
    properties => `apocalypse_firstlight:block/office_keyboard${properties.lowered === 'true' ? '_lowered' : ''}`,
    [{ lowered: 'false' }, { lowered: 'true' }]
));
outputs.set(path.join(assetRoot, 'models/item/office_keyboard.json'), {
    ...blockModel('office_keyboard', keyboardElements, { textures: keyboardTextures, texture_size: [64, 64] }),
    gui_light: 'side',
    display: {
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.8, 0.8, 0.8] },
        thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.8, 0.8, 0.8] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: [0.9, 0.9, 0.9] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2.5, 0], scale: [0.9, 0.9, 0.9] },
        gui: { rotation: [30, 135, 0], translation: [0, 3.5, 0], scale: [0.95, 0.95, 0.95] },
        ground: { translation: [0, 2, 0], scale: [0.8, 0.8, 0.8] },
        fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] }
    }
});

const mouseElements = mouse.elements.filter(element => element.export !== false).map(element =>
    transformedElement(mouse, element, {
        namePrefix: 'mouse_', texture: '#mouse', rotateY180: true
    }));
const loweredMouseElements = mouseElements.map(element => ({
    ...structuredClone(element),
    from: element.from.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0))),
    to: element.to.map((value, axis) => clean(value + (axis === 1 ? -2.5 : 0)))
}));
const mouseTextures = {
    mouse: 'apocalypse_firstlight:block/office_mouse',
    particle: 'apocalypse_firstlight:block/office_mouse'
};
outputs.set(path.join(assetRoot, 'models/block/office_mouse.json'),
    blockModel('office_mouse', mouseElements, { textures: mouseTextures, texture_size: [32, 32] }));
outputs.set(path.join(assetRoot, 'models/block/office_mouse_lowered.json'),
    blockModel('office_mouse', loweredMouseElements, { textures: mouseTextures, texture_size: [32, 32] }));
outputs.set(path.join(assetRoot, 'blockstates/office_mouse.json'), facingVariants(
    properties => `apocalypse_firstlight:block/office_mouse${properties.lowered === 'true' ? '_lowered' : ''}`,
    [{ lowered: 'false' }, { lowered: 'true' }]
));
outputs.set(path.join(assetRoot, 'models/item/office_mouse.json'), {
    ...blockModel('office_mouse', mouseElements, { textures: mouseTextures, texture_size: [32, 32] }),
    gui_light: 'side',
    display: {
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [1.4, 1.4, 1.4] },
        thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [1.4, 1.4, 1.4] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: [1.6, 1.6, 1.6] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2.5, 0], scale: [1.6, 1.6, 1.6] },
        gui: { rotation: [25, 135, 0], translation: [0, 3.5, 0], scale: [1.8, 1.8, 1.8] },
        ground: { translation: [0, 2, 0], scale: [1.3, 1.3, 1.3] },
        fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [1.5, 1.5, 1.5] }
    }
});

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

assert.deepEqual([...chairByAngle.keys()].sort((a, b) => a - b), [0, 72, 144, 216, 288]);
assert.equal(deskParts.left.length + deskParts.center.length + deskParts.right.length >= desk.elements.length, true);
assert.equal(loweredMonitorElements.every((element, index) =>
    near(element.from[1], monitorElements[index].from[1] - 2.5)
    && near(element.to[1], monitorElements[index].to[1] - 2.5)), true);
const keyboardMain = stationElements.find(element => element.name === 'keyboard_main_key_recess');
const keyboardNumpad = stationElements.find(element => element.name === 'keyboard_numpad_recess');
assert(keyboardNumpad.to[0] < keyboardMain.from[0], 'Station keyboard numpad must be on the NORTH user\'s right');
const stationMouseFront = stationElements.find(element => element.name === 'mouse_front_top_deck');
const stationMouseRear = stationElements.find(element => element.name === 'mouse_rear_hump');
assert(stationMouseFront.from[2] > stationMouseRear.from[2], 'Station mouse nose must point toward the monitor');
assert(Math.max(...stationElements.filter(element => element.name.startsWith('mouse_')).map(element => element.to[0])) <= 2.35,
    'Station mouse must remain on the NORTH user\'s right');
assert(Math.min(...stationElements.filter(element => element.name.startsWith('keyboard_')).map(element => element.from[0])) >= 2.9,
    'Station keyboard must leave room for the embedded mouse');
const mouseFront = mouseElements.find(element => element.name === 'mouse_front_top_deck');
const mouseRear = mouseElements.find(element => element.name === 'mouse_rear_hump');
assert(mouseFront.from[2] > mouseRear.from[2], 'Mouse nose must point away from the NORTH user and toward the monitor');
assert(loweredStationElements.every((element, index) =>
    near(element.from[1], stationElements[index].from[1] - 2.5)
    && near(element.to[1], stationElements[index].to[1] - 2.5)), true);
assert(loweredMouseElements.every((element, index) =>
    near(element.from[1], mouseElements[index].from[1] - 2.5)
    && near(element.to[1], mouseElements[index].to[1] - 2.5)), true);

console.log(JSON.stringify({
    mode: checkOnly ? 'check' : 'write',
    desk: { sourceCubes: desk.elements.length, runtimeParts: Object.fromEntries(Object.entries(deskParts).map(([key, value]) => [key, value.length])) },
    chair: { sourceCubes: chair.elements.length, forgeRotationChildren: Object.fromEntries([...chairByAngle].map(([key, value]) => [key, value.length])) },
    monitor: { sourceCubes: monitor.elements.length, loweredVariantOffset: -2.5 },
    officeComputerStation: { monitorCubes: monitor.elements.length, keyboardCubes: keyboard.elements.length, mouseCubes: mouse.elements.length, loweredVariantOffset: -2.5 },
    officeKeyboard: { sourceCubes: keyboard.elements.length, loweredVariantOffset: -2.5 },
    officeMouse: { sourceCubes: mouse.elements.length, centeredStandaloneModel: true, loweredVariantOffset: -2.5 },
    outputs: outputs.size
}, null, 2));
