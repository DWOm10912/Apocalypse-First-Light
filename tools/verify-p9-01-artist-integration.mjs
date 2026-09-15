import assert from 'node:assert/strict';
import { readFileSync, existsSync, readdirSync } from 'node:fs';

const base = new URL('../src/main/resources/assets/apocalypse_firstlight/', import.meta.url);
const read = path => readFileSync(new URL(path, base), 'utf8');
const geo = JSON.parse(read('geo/p9_01.geo.json'))['minecraft:geometry'][0];
const animation = JSON.parse(read('animations/p9_01.animation.json')).animations;
const sounds = JSON.parse(read('sounds.json'));
const bones = new Map(geo.bones.map(bone => [bone.name, bone]));
const clips = ['static_idle', 'empty_idle', 'shoot', 'reload_tactical', 'reload_empty',
    'draw', 'put_away', 'inspect', 'inspect_empty'];
for (const clip of clips) assert.ok(animation[clip], `missing P9 clip ${clip}`);
for (const bone of ['root', 'slide', 'magazine', 'reload_magazine', 'muzzle_anchor',
    'sight_anchor', 'ejection_anchor', 'camera', 'righthand_pos', 'lefthand_pos'])
    assert.ok(bones.has(bone), `missing P9 bone ${bone}`);
for (const bone of ['righthand_pos', 'lefthand_pos', 'camera'])
    assert.equal(bones.get(bone).cubes?.length ?? 0, 0, `visible proxy ${bone}`);
for (const [anchor, parent] of [['right_hand_anchor', 'righthand_pos'],
    ['left_hand_anchor', 'lefthand_pos']]) {
    assert.equal(bones.get(anchor)?.parent, parent, `${anchor} must inherit artist hand`);
    assert.equal(geo.bones.filter(b => b.name === anchor).length, 1, `${anchor} duplicate`);
    assert.ok(!bones.get(anchor).cubes?.length, `${anchor} must be cube-free`);
    for (const clip of clips)
        assert.deepEqual(animation[clip].bones[anchor]?.scale?.vector, [1, 0.625, 1],
            `${clip} ${anchor} stretch compensation`);
}
for (const bone of ['right_hand_motion', 'left_hand_motion']) {
    assert.ok(!bones.has(bone), `${bone} obsolete runtime locator`);
    for (const clip of clips) assert.ok(!animation[clip].bones[bone], `${clip} obsolete retarget`);
}
for (const [clipName, clip] of Object.entries(animation))
    for (const hand of ['righthand_1', 'lefthand_1'])
        assert.deepEqual(clip.bones?.[hand]?.scale?.vector, [1, 1.6, 1],
            `${clipName} ${hand} author proxy scale changed`);
assert.equal(animation.reload_tactical.animation_length, 2.38);
assert.equal(animation.reload_empty.animation_length, 3.12);
assert.equal(animation.shoot.animation_length, 0.6);
assert.equal(animation.empty_idle.loop, 'hold_on_last_frame');
assert.equal(animation.empty_idle.animation_length, 0.04167);
for (const channel of ['rotation', 'position'])
    assert.deepEqual(animation.empty_idle.bones.slide[channel].vector,
        animation.inspect_empty.bones.slide[channel]['0.0'].vector,
        `empty idle slide ${channel} must match inspect_empty frame zero`);
for (const bone of ['righthand', 'lefthand', 'righthand_1', 'lefthand_1',
    'magazine', 'lower', 'empty_old_magazine'])
    assert.deepEqual(animation.shoot.bones[bone], animation.static_idle.bones[bone],
        `shoot must keep static_idle ${bone} pose`);
for (const channel of ['rotation', 'position'])
    assert.deepEqual(animation.shoot.bones.root[channel]['0.0'].vector,
        animation.static_idle.bones.root[channel].vector,
        `shoot root ${channel} must start at static_idle`);
assert.notDeepEqual(animation.shoot.bones.root.rotation['0.05'].vector,
    animation.shoot.bones.root.rotation['0.0'].vector, 'shoot recoil lost');
const handLayer = readFileSync(new URL('../src/main/java/com/antaurora/apofirstlight/weapon/client/P901HandLayer.java',
    import.meta.url), 'utf8');
assert.match(handLayer, /NativePlayerArmRenderer\.render\(locator, right, buffers, light, overlay\)/,
    'P9 must use the exact same player-arm dimensions as BR51');
const display = JSON.parse(read('models/item/p9_01_in_hand.json')).display;
assert.deepEqual(display.firstperson_righthand.translation, [3.24148, -7.4945, -14.19624],
    'P9 right Hip must move the complete rig down and away');
assert.deepEqual(display.firstperson_lefthand.translation, [2.83248, -7.4945, -14.22484],
    'P9 left Hip must receive the same depth and height shift');
const adsProfile = readFileSync(new URL('../src/main/java/com/antaurora/apofirstlight/weapon/client/NativeAdsProfile.java',
    import.meta.url), 'utf8');
assert.match(adsProfile, /3\.24148F,-7\.4945F,-14\.19624F/,
    'P9 ADS inverse must use the new Hip Display');
assert.doesNotMatch(handLayer, /righthand_pos|lefthand_pos|locator\.scale\(/,
    'P9 must not bind artist proxy or use hand-only Java scale hack');
const expectedMarkers = {
    reload_tactical: { '0.36': 'apocalypse_firstlight:p9_01_magazine_out',
        '0.79': 'apocalypse_firstlight:p9_01_magazine_in' },
    reload_empty: { '0.26': 'apocalypse_firstlight:p9_01_magazine_out',
        '0.76': 'apocalypse_firstlight:p9_01_magazine_in',
        '1.64': 'apocalypse_firstlight:p9_01_slide_action' },
    inspect: { '0.0': 'apocalypse_firstlight:p9_01_inspect' },
    inspect_empty: { '0.0': 'apocalypse_firstlight:p9_01_inspect' }
};
for (const [clip, markers] of Object.entries(expectedMarkers))
    for (const [time, marker] of Object.entries(markers))
        assert.equal(animation[clip].sound_effects[time]?.effect, marker, `${clip} marker ${time}`);
// Gameplay fire routing is server attachment-aware, never a P9 shoot cue.
const actions = readFileSync(new URL('../src/main/java/com/antaurora/apofirstlight/weapon/P901Actions.java',
    import.meta.url), 'utf8');
assert.match(actions, /NativeGunNoise\.resolve\(player\.getMainHandItem\(\),definition\)\.fireSound\(item\)/);
assert.match(actions, /if \(reload && item\.animationAsset\(\) != null\) state\.cues\.addAll/);
const soundRoot = new URL('sounds/', base);
for (const [id, entry] of Object.entries(sounds)) {
    if (!id.startsWith('p9_01_') && !id.startsWith('br51_01_')) continue;
    for (const candidate of entry.sounds) {
        const name = typeof candidate === 'string' ? candidate : candidate.name;
        assert.ok(name.startsWith('apocalypse_firstlight:'), `${id} invalid namespace`);
        const path = name.slice(name.indexOf(':') + 1);
        if (id !== 'p9_01_dry_fire')
            assert.ok(path.startsWith('weapons/'), `${id} outside normalized weapons directory`);
        assert.ok(existsSync(new URL(`${path}.ogg`, soundRoot)), `${id} missing ${path}.ogg`);
    }
}
assert.equal(existsSync(new URL('sounds/br51_01/', base)), false, 'legacy BR51 folder remains');
assert.deepEqual(readdirSync(new URL('sounds/weapons/p9_01/', base)).sort(), [
    'p9_01_fire.ogg', 'p9_01_inspect.ogg', 'p9_01_magazine_in.ogg',
    'p9_01_magazine_out.ogg', 'p9_01_slide_action.ogg', 'p9_01_suppressed.ogg'
].sort());
const trackNames = new Set(Object.values(animation).flatMap(clip => Object.keys(clip.bones ?? {})));
const orphans = [...trackNames].filter(name => !bones.has(name)).sort();
console.log(`PASS P9 artist integration: ${clips.length} clips, ${bones.size} bones, sound files resolved`);
console.log(`KNOWN_ORPHAN_TRACKS=${orphans.join(',') || 'NONE'}`);
