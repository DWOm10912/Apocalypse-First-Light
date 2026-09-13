import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = path.resolve(import.meta.dirname, '..');
const asset = p => path.join(root, 'src/main/resources/assets/apocalypse_firstlight', p);
const source = JSON.parse(fs.readFileSync(path.join(root, 'src/main/blockbench/commercial_wall_mounted_sink.bbmodel'), 'utf8'));
const key = 'block.apocalypse_firstlight.commercial_wall_mounted_sink';
const sink = 'apocalypse_firstlight:commercial_wall_mounted_sink';
function insist(value, message) { if (!value) throw Error(message); }
function lang(name) {
  const text = fs.readFileSync(asset(`lang/${name}.json`), 'utf8');
  const parsed = JSON.parse(text);
  const keys = [...text.matchAll(/^  "([^"]+)":/gm)].map(match => match[1]);
  insist(keys.length === Object.keys(parsed).length, `${name} duplicate keys`);
  return {keys, parsed};
}
const en = lang('en_us'), zh = lang('zh_cn');
insist(en.keys.join('\n') === zh.keys.join('\n'), 'Localization key/order mismatch');
insist(en.parsed[key] === 'Commercial Wall-Mounted Sink', 'English name');
insist(zh.parsed[key] === '商业壁挂式洗手台', 'Chinese name');
insist(en.keys[en.keys.indexOf(key)-1] === 'block.apocalypse_firstlight.commercial_flushometer_toilet', 'Wrong localization section');

insist(source.elements.length === 250, 'Source cube count changed');
insist(source.elements.every(c => c.to.every((v, i) => v > c.from[i])), 'Zero-thickness cube');
insist(source.elements.every(c => !/water|funnel/i.test(c.name)), 'Unexpected water geometry');
const rimTop = Math.max(...source.elements.filter(c => c.name.startsWith('continuous_rim_tile_')).map(c => c.to[1]));
insist(rimTop >= 14.9 && rimTop <= 15.1, `Rim was not raised five units: ${rimTop}`);
const png = fs.readFileSync(asset('textures/block/commercial_wall_mounted_sink.png'));
const editablePng = fs.readFileSync(path.join(root, 'src/main/blockbench/textures/commercial_wall_mounted_sink.png'));
const embeddedPng = Buffer.from(source.textures[0].source.split(',')[1], 'base64');
for (const other of [editablePng, embeddedPng]) insist(png.equals(other), 'Texture mismatch');
const obj = fs.readFileSync(asset('models/block/commercial_wall_mounted_sink.obj'), 'utf8');
const mtl = fs.readFileSync(asset('models/block/commercial_wall_mounted_sink.mtl'), 'utf8');
insist((obj.match(/^o /gm)||[]).length === source.elements.length, 'OBJ object count');
insist(obj.includes('mtllib commercial_wall_mounted_sink.mtl'), 'OBJ material binding');
insist(mtl.includes('map_Kd apocalypse_firstlight:block/commercial_wall_mounted_sink'), 'MTL texture binding');
insist((obj.match(/^f /gm)||[]).length > 0, 'OBJ has no faces');
const vertices = obj.split('\n').filter(line => line.startsWith('v ')).map(line => line.split(' ').slice(1).map(Number));
const bounds = [0,1,2].map(i => [Math.min(...vertices.map(v => v[i])), Math.max(...vertices.map(v => v[i]))]);
insist(bounds.every(([a,b],i) => a >= 0 && b <= (i === 1 ? 2 : 1)) && bounds[1][1] > 1,
  `OBJ does not fit two-cell occupancy: ${JSON.stringify(bounds)}`);
const states = JSON.parse(fs.readFileSync(asset('blockstates/commercial_wall_mounted_sink.json'), 'utf8')).variants;
insist(Object.keys(states).length === 8, 'Not four facings in both halves');
for (const [direction, rotation] of Object.entries({north:0,east:90,south:180,west:270})) {
  const state = states[`facing=${direction},half=lower`];
  insist(state?.model === 'apocalypse_firstlight:block/commercial_wall_mounted_sink' && (state.y||0) === rotation, `Bad ${direction} model rotation`);
  insist(states[`facing=${direction},half=upper`]?.model === 'apocalypse_firstlight:block/commercial_wall_mounted_sink_upper',
    `Bad ${direction} upper placeholder`);
}
insist(!Object.hasOwn(JSON.parse(fs.readFileSync(asset('models/block/commercial_wall_mounted_sink_upper.json'), 'utf8')), 'elements'),
  'Upper model should not duplicate sink geometry');
const loot = JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/commercial_wall_mounted_sink.json'), 'utf8'));
insist(loot.pools.length === 1 && loot.pools[0].entries.length === 1 && loot.pools[0].entries[0].name === sink, 'Wrong self drop');
for (const tag of ['mineable/pickaxe','needs_iron_tool']) {
  const values = JSON.parse(fs.readFileSync(path.join(root, `src/main/resources/data/minecraft/tags/blocks/${tag}.json`), 'utf8')).values;
  insist(values.filter(v => v === sink).length === 1, `${tag} missing or duplicate`);
}
const digest = crypto.createHash('sha256').update(png).digest('hex');
console.log(JSON.stringify({status:'PASS',localizationKeyParity:true,localizationOrderParity:true,
  localizationDuplicateKeys:0,localizationIndex:en.keys.indexOf(key),cubes:source.elements.length,rimTop,
  objFaces:(obj.match(/^f /gm)||[]).length,bounds,textureSha256:digest}));
