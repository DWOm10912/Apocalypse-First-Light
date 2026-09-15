import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const actions = read('src/main/java/com/antaurora/apofirstlight/weapon/P901Actions.java');
const policy = read('src/main/java/com/antaurora/apofirstlight/weapon/NativeShotAnimationPolicy.java');
const p9Item = read('src/main/java/com/antaurora/apofirstlight/weapon/P901AnimationController.java');
const configured = read('src/main/java/com/antaurora/apofirstlight/weapon/ConfiguredNativeGunItem.java');

assert.match(actions, /if \(!reload\) \{[\s\S]*?item\.stopTriggeredAnim\(player, state\.id, P901Item\.CONTROLLER, state\.clip\);[\s\S]*?\}\s*item\.triggerAnim\(player, state\.id, P901Item\.CONTROLLER, state\.clip\);/,
    'accepted shot must stop before re-triggering the same clip');
assert.match(actions, /state\.lastShot = lastShot;/);
assert.match(actions, /state\.start \+ NativeShotAnimationPolicy\.LAST_SHOT_HANDOFF_TICKS/);
assert.match(policy, /LAST_SHOT_HANDOFF_TICKS = 1/);
assert.match(policy, /ammoBefore == 1 && ammoAfter == 0/);
assert.match(p9Item, /thenPlayAndHold\("empty_idle"\)/);
assert.match(configured, /\? "static_bolt_caught" : profile\.idle\(\)/);

const animation = name => JSON.parse(read(
    `src/main/resources/assets/apocalypse_firstlight/animations/${name}.animation.json`)).animations;
const p9 = animation('p9_01');
const br51 = animation('br51_01');
assert.equal(p9.shoot.bones.slide.position['0.05'].vector[2], 1.625,
    'P9 must reach slide recoil by the one-tick handoff');
assert.equal(p9.empty_idle.bones.slide.position.vector[2], 1.905,
    'P9 empty baseline must hold the slide rearward');
assert.equal(br51.shoot.bones.bolt.position['0.0167'][2], 3,
    'BR51 must reach bolt recoil before the one-tick handoff');
assert.equal(br51.static_bolt_caught.bones.bolt.position[2], 3,
    'BR51 empty baseline must hold the bolt rearward');

console.log('PASS shared Native Gun shot retrigger and one-tick last-shot lock policy');
