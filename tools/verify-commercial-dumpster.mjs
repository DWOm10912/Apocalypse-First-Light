import assert from 'node:assert/strict';
import fs from 'node:fs';
import { spawnSync } from 'node:child_process';

const exporter = spawnSync(process.execPath, ['tools/export-commercial-dumpster-runtime.mjs', '--check'], {
    cwd: process.cwd(),
    encoding: 'utf8'
});
assert.equal(exporter.status, 0, `${exporter.stdout}\n${exporter.stderr}`);

const read = path => JSON.parse(fs.readFileSync(path, 'utf8'));
const source = read('src/main/blockbench/commercial_dumpster.bbmodel');
const master = read('src/main/resources/assets/apocalypse_firstlight/models/block/commercial_dumpster/master.json');
const secondary = read('src/main/resources/assets/apocalypse_firstlight/models/block/commercial_dumpster/secondary.json');
const item = read('src/main/resources/assets/apocalypse_firstlight/models/item/commercial_dumpster.json');
const blockstate = read('src/main/resources/assets/apocalypse_firstlight/blockstates/commercial_dumpster.json');
const loot = read('src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/commercial_dumpster.json');
const pickaxe = read('src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json');
const iron = read('src/main/resources/data/minecraft/tags/blocks/needs_iron_tool.json');
const diamond = read('src/main/resources/data/minecraft/tags/blocks/needs_diamond_tool.json');
const en = read('src/main/resources/assets/apocalypse_firstlight/lang/en_us.json');
const zh = read('src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json');
const blockCode = fs.readFileSync(
    'src/main/java/com/antaurora/apofirstlight/block/CommercialDumpsterBlock.java', 'utf8');

assert.equal(source.elements.length, 260);
assert.equal(item.elements.length, 260, 'BlockItem must render the complete asset');
assert(master.elements.length > 0 && secondary.elements.length > 0);
for (const model of [master, secondary, item]) {
    assert.equal(model.ambientocclusion, false);
    assert.equal(model.textures['0'], 'apocalypse_firstlight:block/commercial_dumpster');
    for (const element of model.elements) {
        assert([...element.from, ...element.to].every(value => value >= -16 && value <= 32),
            `Out-of-bounds model element: ${element.name}`);
    }
}

for (const hand of ['firstperson_righthand', 'firstperson_lefthand']) {
    assert.deepEqual(source.display[hand].scale, [0.3, 0.3, 0.3]);
    assert.deepEqual(item.display[hand], source.display[hand]);
}
for (const hand of ['thirdperson_righthand', 'thirdperson_lefthand']) {
    assert.deepEqual(source.display[hand].scale, [0.25, 0.25, 0.25]);
    assert.deepEqual(item.display[hand], source.display[hand]);
}
assert.deepEqual(source.display.gui.scale, [0.4, 0.4, 0.4]);
assert.deepEqual(item.display.gui, source.display.gui);

assert.equal(Object.keys(blockstate.variants).length, 8);
for (const facing of ['north', 'east', 'south', 'west']) {
    for (const part of ['master', 'secondary']) {
        assert(blockstate.variants[`facing=${facing},part=${part}`]);
    }
}

const lootConditions = loot.pools[0].conditions;
assert(lootConditions.some(condition => condition.condition === 'minecraft:block_state_property'
    && condition.properties?.part === 'master'));
assert.equal(loot.pools[0].entries[0].name, 'apocalypse_firstlight:commercial_dumpster');
assert(pickaxe.values.includes('apocalypse_firstlight:commercial_dumpster'));
assert(iron.values.includes('apocalypse_firstlight:commercial_dumpster'));
assert(!diamond.values.includes('apocalypse_firstlight:commercial_dumpster'));
assert.equal(en['block.apocalypse_firstlight.commercial_dumpster'], 'Commercial Dumpster');
assert.equal(zh['block.apocalypse_firstlight.commercial_dumpster'], '商业垃圾箱');

for (const required of [
    'Part.MASTER', 'Part.SECONDARY', 'facing.getClockWise()',
    'playerWillDestroy', 'Part.SECONDARY', 'getDrops', 'Part.MASTER',
    'PushReaction.BLOCK', 'getCollisionShape', 'horizontalRotations',
    'canPlaceStructure', 'isUnobstructed', 'getWorldBorder', 'MUTATIONS'
]) assert(blockCode.includes(required), `Missing multiblock invariant: ${required}`);
assert(!blockCode.includes('EntityBlock'), 'V1 must not create a BlockEntity');

console.log(JSON.stringify({
    resourceSync: 'PASS',
    sourceCubes: source.elements.length,
    masterElements: master.elements.length,
    secondaryElements: secondary.elements.length,
    completeBlockItem: item.elements.length,
    variants: Object.keys(blockstate.variants).length,
    display: { firstPerson: 0.3, thirdPerson: 0.25, gui: 0.4 },
    mining: 'pickaxe / iron+',
    duplicateDropGuard: 'master loot ownership + secondary forwarding + mutation guard',
    runtime: 'NOT_TESTED'
}, null, 2));
