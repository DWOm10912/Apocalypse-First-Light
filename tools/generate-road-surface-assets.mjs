// Static road surface assets. Run only when intentionally regenerating the V1-B visual placeholders.
// Java RoadSurfaceBlock/RoadCurbBlock own the matching, permanent collision contract.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/apocalypse_firstlight');
const data = path.join(root, 'src/main/resources/data/apocalypse_firstlight');
const ns = 'apocalypse_firstlight:';
function json(file, value) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}
function box(x0, y0, z0, x1, y1, z1) {
    const faces = {};
    for (const direction of ['down', 'up', 'north', 'south', 'west', 'east']) faces[direction] = { texture: '#all' };
    // Only the cell boundaries can be culled against neighboring opaque geometry.
    if (y0 === 0) faces.down.cullface = 'down';
    if (y1 === 16) faces.up.cullface = 'up';
    if (z0 === 0) faces.north.cullface = 'north';
    if (z1 === 16) faces.south.cullface = 'south';
    if (x0 === 0) faces.west.cullface = 'west';
    if (x1 === 16) faces.east.cullface = 'east';
    return { from: [x0, y0, z0], to: [x1, y1, z1], faces };
}
function model(texture, elements) {
    return { parent: 'minecraft:block/block', textures: { all: texture, particle: texture }, elements };
}
function itemAndLoot(id, parent, properties) {
    json(path.join(assets, `models/item/${id}.json`), { parent: `${ns}block/${parent}` });
    json(path.join(data, `loot_tables/blocks/${id}.json`), {
        type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: `${ns}${id}`,
            functions: [{ function: 'minecraft:copy_state', block: `${ns}${id}`, properties }] }],
        conditions: [{ condition: 'minecraft:survives_explosion' }] }]
    });
}
const surfaces = [
    ['road_asphalt_surface', `${ns}block/asphalt`, 13],
    ['road_sidewalk_surface', `${ns}block/reinforced_concrete`, 16],
    ['road_utility_surface', 'minecraft:block/dirt', 16]
];
for (const [id, texture, defaultLayer] of surfaces) {
    const variants = {};
    for (let layers = 1; layers <= 16; layers++) {
        const name = `road_surfaces/${id}_${layers}`;
        json(path.join(assets, `models/block/${name}.json`), model(texture, [box(0, 0, 0, 16, layers, 16)]));
        variants[`layers=${layers}`] = { model: `${ns}block/${name}` };
    }
    json(path.join(assets, `blockstates/${id}.json`), { variants });
    itemAndLoot(id, `road_surfaces/${id}_${defaultLayer}`, ['layers']);
}
const curbVariants = {};
for (const shape of ['straight', 'inner', 'outer', 'driveway']) {
    for (let layers = 1; layers <= 16; layers++) {
        const base = Math.max(0, layers - 3), elements = [];
        const lower = (...coords) => { if (base > 0) elements.push(box(coords[0], 0, coords[1], coords[2], base, coords[3])); };
        if (shape === 'driveway') elements.push(box(0, 0, 0, 16, layers, 16));
        else if (shape === 'straight') {
            lower(0, 0, 16, 12);
            elements.push(box(0, 0, 12, 16, layers, 16));
        } else if (shape === 'inner') {
            lower(0, 0, 12, 12);
            elements.push(box(0, 0, 12, 16, layers, 16), box(12, 0, 0, 16, layers, 12));
        } else {
            lower(0, 0, 16, 12); lower(0, 12, 12, 16);
            elements.push(box(12, 0, 12, 16, layers, 16));
        }
        const name = `road_surfaces/road_curb_${shape}_${layers}`;
        json(path.join(assets, `models/block/${name}.json`), model(`${ns}block/reinforced_concrete`, elements));
        for (const [index, facing] of ['north', 'east', 'south', 'west'].entries()) {
            curbVariants[`facing=${facing},layers=${layers},shape=${shape}`] = {
                model: `${ns}block/${name}`, y: index * 90, uvlock: true
            };
        }
    }
}
json(path.join(assets, 'blockstates/road_curb.json'), { variants: curbVariants });
itemAndLoot('road_curb', 'road_surfaces/road_curb_straight_16', ['layers', 'facing', 'shape']);
console.log('Generated 112 static block models, 4 blockstates, 4 item models and 4 loot tables; existing textures reused.');
