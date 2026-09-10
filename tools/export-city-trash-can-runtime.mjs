import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(root, 'src/main/blockbench/city_trash_can.bbmodel');
const texturePath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/textures/block/city_trash_can.png');
const outputPath = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/models/block/metal_trash_can.json');
const before = fs.readFileSync(sourcePath);
const source = JSON.parse(before);

if (source.meta?.model_format !== 'java_block') throw new Error('Expected a Java block Blockbench source');
if (source.elements?.length !== 204) throw new Error('Expected the approved 204-cube source');
if (source.textures?.length !== 1) throw new Error('Expected one texture atlas');
const texture = source.textures[0];
if (!texture.source?.startsWith('data:image/png;base64,')) throw new Error('Missing embedded texture');
if (!fs.readFileSync(texturePath).equals(Buffer.from(texture.source.split(',')[1], 'base64'))) {
    throw new Error('Saved PNG differs from the embedded Blockbench texture');
}

const elements = source.elements.filter(element => element.export !== false).map(element => {
    if (element.type !== 'cube') throw new Error(`Unsupported element type: ${element.name}`);
    const rotation = element.rotation ?? [0, 0, 0];
    const axes = rotation.map((value, index) => value ? index : -1).filter(index => index >= 0);
    if (axes.length > 1 || axes.some(index => ![-45, -22.5, 22.5, 45].includes(rotation[index]))) {
        throw new Error(`Unsupported Java rotation: ${element.name}`);
    }
    if ([...element.from, ...element.to].some(value => !Number.isFinite(value) || value < -16 || value > 32)) {
        throw new Error(`Element outside supported bounds: ${element.name}`);
    }
    const faces = {};
    for (const [direction, face] of Object.entries(element.faces)) {
        if (face.texture === null) continue;
        if (face.texture !== 0) throw new Error(`Unexpected texture binding: ${element.name}/${direction}`);
        faces[direction] = {
            uv: face.uv.map((value, index) => value * 16 / (index % 2 ? source.resolution.height : source.resolution.width)),
            texture: '#0',
            ...(face.rotation ? { rotation: face.rotation } : {}),
            ...(face.cullface ? { cullface: face.cullface } : {})
        };
    }
    return {
        name: element.name,
        from: element.from,
        to: element.to,
        ...(axes.length ? {
            rotation: {
                origin: element.origin,
                axis: 'xyz'[axes[0]],
                angle: rotation[axes[0]],
                rescale: Boolean(element.rescale)
            }
        } : {}),
        shade: element.shade !== false,
        faces
    };
});

const runtime = {
    credit: 'Apocalypse: First Light — City metal trash can',
    parent: 'minecraft:block/block',
    ambientocclusion: false,
    texture_size: [source.resolution.width, source.resolution.height],
    textures: {
        '0': 'apocalypse_firstlight:block/city_trash_can',
        particle: 'apocalypse_firstlight:block/city_trash_can'
    },
    elements
};
const encoded = Buffer.from(`${JSON.stringify(runtime, null, 2)}\n`);

if (process.argv.includes('--check')) {
    if (!fs.readFileSync(outputPath).equals(encoded)) throw new Error('Runtime model is stale');
} else {
    if (fs.existsSync(outputPath)) throw new Error('Refusing to overwrite the existing runtime model');
    fs.writeFileSync(outputPath, encoded, { flag: 'wx' });
}
if (!before.equals(fs.readFileSync(sourcePath))) throw new Error('Blockbench source changed during export');
console.log(`PASS: source unchanged; ${elements.length} runtime elements; texture binding verified.`);
