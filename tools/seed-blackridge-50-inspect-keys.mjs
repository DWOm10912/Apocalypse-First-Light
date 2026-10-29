// Writes the Blackridge .50 V2's own inspect keys (docs/native_guns/blackridge_50_remake_v1.md 12.2; user 2026-10-28:
// "给 Blackridge 单独做一套检视") into src/main/blockbench/blackridge_50_v2_native.bbmodel as Blockbench bezier keys, from
// tools/author-blackridge-50-v2-native-animations.mjs inspectKeys (the key poses INSPECT_POSES on the beats INSPECT_T):
// handling rotation / position and camera rotation of inspect, inspect_empty, inspect_no_mag, inspect_empty_no_mag.
// Flat handles (each move eases out of its hold and stops dead on the next, no overshoot): the leaving handle 0.4 of the
// segment, the arriving one 0.2. A pair of equal keys is a hold.
//   node tools/seed-blackridge-50-inspect-keys.mjs [--force]
// Overwrites only its own keys and round 1's (the P9's copied keys, kept in BACKUP); keys edited in Blockbench since are
// refused unless --force.
// Then: node tools/author-blackridge-50-v2-native-animations.mjs --write-source --write-runtime
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {nativePath} from './build-blackridge-50-v2-native.mjs';
import {inspectKeys, INSPECT_T, INSPECT_NO_MAG_T} from './author-blackridge-50-v2-native-animations.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BACKUP = path.join(root, 'design/items/blackridge_50/anim_inspect/inspect_p9_copy_r1.animations.json');
const force = process.argv.includes('--force');
const OURS = 'b5015eed';
const uuid = s => { const h = createHash('sha256').update('blackridge-50-inspect-seed:' + s).digest('hex'); return `${OURS}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };   // OURS marks this script's keys
const r4 = v => +v.toFixed(4) || 0;
const CLIPS = {inspect: [false, false], inspect_empty: [true, false], inspect_no_mag: [false, true], inspect_empty_no_mag: [true, true]};

const backed = new Set(JSON.parse(fs.readFileSync(BACKUP, 'utf8')).animations
    .flatMap(a => Object.values(a.animators).flatMap(an => an.keyframes.map(k => k.uuid))));
const br = JSON.parse(fs.readFileSync(nativePath, 'utf8'));
const groups = new Map(br.groups.map(g => [g.name, g]));
const report = {};
for (const [name, [empty, noMag]] of Object.entries(CLIPS)) {
    const length = (noMag ? INSPECT_NO_MAG_T : INSPECT_T).end, keys = inspectKeys(empty, noMag);
    const anim = br.animations.find(a => a.name === name);
    if (!anim) throw new Error('no ' + name + ' in the source (run the author with --write-source first)');
    const channels = [];
    for (const [bone, chs] of Object.entries(keys)) {
        const g = groups.get(bone); if (!g) throw new Error('rig has no ' + bone);
        const animator = anim.animators[g.uuid] ??= {name: bone, type: 'bone', keyframes: []};
        for (const [ch, list] of Object.entries(chs)) {
            const keyId = t => uuid(`${name}:${bone}:${ch}:${r4(t)}`);
            const foreign = animator.keyframes.filter(k => k.channel === ch && k.interpolation === 'bezier' && !backed.has(k.uuid)
                && !k.uuid.startsWith(OURS));
            if (foreign.length && !force) throw new Error(`${name} ${bone}.${ch}: ${foreign.length} keys edited since (--force to overwrite)`);
            const kfs = list.map(([t, v], i) => {
                const before = i ? t - list[i - 1][0] : 0, after = i < list.length - 1 ? list[i + 1][0] - t : 0;
                return {channel: ch, data_points: [{x: String(r4(v[0])), y: String(r4(v[1])), z: String(r4(v[2]))}], uuid: keyId(t),
                    time: r4(t), color: -1, interpolation: 'bezier', bezier_linked: false,
                    bezier_left_time: Array(3).fill(r4(-0.2 * before)), bezier_left_value: [0, 0, 0],
                    bezier_right_time: Array(3).fill(r4(0.4 * after)), bezier_right_value: [0, 0, 0]};
            });
            animator.keyframes = animator.keyframes.filter(k => k.channel !== ch).concat(kfs);
            channels.push(`${bone}.${ch}:${kfs.length}`);
        }
    }
    // drop the lefthand / mag_out hand keys of round 1 (the support hand is solved now); the author rewrites those bones
    for (const an of Object.values(anim.animators)) if (['lefthand', 'mag_out', 'lefthand_pos'].includes(an.name))
        an.keyframes = an.keyframes.filter(k => k.interpolation !== 'bezier' || !backed.has(k.uuid));
    anim.length = length;
    report[name] = {length, channels: channels.join(' ')};
}
fs.writeFileSync(nativePath, JSON.stringify(br));
console.log(JSON.stringify(report, null, 1), '\nwrote', path.relative(root, nativePath));
