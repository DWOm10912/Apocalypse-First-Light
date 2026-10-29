// One-time seed of the Blackridge .50 V2 hand keys (docs/native_guns/blackridge_50_remake_v1.md 12): copies the P9 V3's
// hand-keyed channels (handling, lefthand, camera, lefthand_pos' pre-rotation; the inspect's mag_out too) of each round-1
// clip into src/main/blockbench/blackridge_50_v2_native.bbmodel, re-timed onto the Blackridge beats by a piecewise-linear
// map through the clip's beats (the bezier handles scaled with their segment). Both guns hold the grip on the same hand
// point, so the P9's poses carry over; they are a start, edited in Blockbench afterwards.
//   node tools/seed-blackridge-50-v2-hand-keys.mjs [clip ...] [--force]     (no clip names: all of WARP)
// Refuses a clip that already has hand keys in the Blackridge source (so the edits are never overwritten) unless --force.
// Round 1 (2026-10-28): the hip reloads and the inspects (replaced by the Blackridge's own, tools/seed-blackridge-50-inspect-keys.mjs);
// round 2: the ADS reloads; round 3: the 16 tier reloads (each warped through its own beats, P9 tierTiming -> Blackridge tierTiming).
// Then: node tools/author-blackridge-50-v2-native-animations.mjs --write-source --write-runtime
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {nativePath} from './build-blackridge-50-v2-native.mjs';
import {nativePath as p9Path} from './build-p9-01-v3-native.mjs';
import {tierTiming as p9Timing} from './author-p9-01-v3-native-animations.mjs';
import {TIER_CLIPS, tierTiming as brTiming} from './author-blackridge-50-v2-native-animations.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const force = process.argv.includes('--force');
const only = process.argv.slice(2).filter(a => !a.startsWith('--'));
const uuid = s => { const h = createHash('sha256').update('blackridge-50-v2-seed:' + s).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`; };

// [P9 beats, Blackridge beats] (s): 0, release, drop, enter, align, seat, (stop), regrip, end; the no-magazine clips from
// enter on (every beat NO_MAG_CUT = 0.1 s earlier, both guns); tools/author-*-native-animations.mjs reloadKeyed
const WARP = {
    reload_tactical: [[0, 0.22, 0.24, 0.42, 0.62, 0.86, 1.18, 1.7], [0, 0.3, 0.33, 0.64, 0.92, 1.25, 1.72, 2.45]],
    reload_empty: [[0, 0.22, 0.24, 0.46, 0.66, 0.9, 1.0, 1.34, 2.0], [0, 0.3, 0.33, 0.66, 0.96, 1.3, 1.44, 1.88, 2.7]],
    reload_tactical_no_mag: [[0, 0.32, 0.52, 0.76, 1.18, 1.6], [0, 0.54, 0.82, 1.15, 1.72, 2.35]],
    reload_empty_no_mag: [[0, 0.36, 0.56, 0.8, 0.9, 1.34, 1.9], [0, 0.56, 0.86, 1.2, 1.34, 1.88, 2.6]],
    inspect: null, inspect_empty: null, inspect_no_mag: null, inspect_empty_no_mag: null,   // same beats as the P9's
    // ADS (docs/dev/p9_reload_inspect_v1.md 2.7): the hip beats, the palm back onto the grip 0.1 s later (both guns); the
    // no-magazine ADS clips have the no-magazine hip beats (that 0.1 s and NO_MAG_CUT cancel)
    reload_tactical_ads: [[0, 0.22, 0.24, 0.42, 0.62, 0.86, 1.28, 1.7], [0, 0.3, 0.33, 0.64, 0.92, 1.25, 1.82, 2.45]],
    reload_empty_ads: [[0, 0.22, 0.24, 0.46, 0.66, 0.9, 1.0, 1.44, 2.0], [0, 0.3, 0.33, 0.66, 0.96, 1.3, 1.44, 1.98, 2.7]],
    reload_tactical_no_mag_ads: [[0, 0.32, 0.52, 0.76, 1.18, 1.6], [0, 0.54, 0.82, 1.15, 1.72, 2.35]],
    reload_empty_no_mag_ads: [[0, 0.36, 0.56, 0.8, 0.9, 1.34, 1.9], [0, 0.56, 0.86, 1.2, 1.34, 1.88, 2.6]],
};
// round 3: a tier clip's beats on both guns, paired by name (the Blackridge's are the P9's slowed, the fumble's own moves not)
for (const c of TIER_CLIPS) {
    const o = {ads: !!c.ads, noMag: !!c.noMag}, a = p9Timing(c.empty, c.tierName, !!c.fumble, o), b = brTiming(c.empty, c.tierName, !!c.fumble, o);
    const pairs = [[0, 0], ...Object.keys(a).filter(k => b[k] != null).map(k => [a[k], b[k]])].sort((x, y) => x[0] - y[0])
        .filter((p, i, all) => i === 0 || p[0] > all[i - 1][0] + 1e-9);
    pairs.forEach((p, i) => { if (i && !(p[1] > pairs[i - 1][1])) throw new Error(c.name + ': beats not in the same order'); });
    if (pairs.at(-1)[0] !== a.end || pairs.at(-1)[1] !== b.end) throw new Error(c.name + ': the ends are not paired');
    WARP[c.name] = [pairs.map(p => p[0]), pairs.map(p => p[1])];
}
const KEYED = ['handling', 'lefthand', 'camera', 'lefthand_pos', 'mag_out'];

const p9 = JSON.parse(fs.readFileSync(p9Path, 'utf8'));
const br = JSON.parse(fs.readFileSync(nativePath, 'utf8'));
const brGroups = new Map(br.groups.map(g => [g.name, g]));
br.animations ??= [];
const r5 = v => +v.toFixed(5);
const report = {};
for (const [name, warp] of Object.entries(WARP)) {
    if (only.length && !only.includes(name)) continue;
    const src = p9.animations.find(a => a.name === name);
    if (!src) throw new Error('P9 has no ' + name);
    const have = br.animations.find(a => a.name === name);
    if (have && !force && Object.values(have.animators ?? {}).some(a => a.keyframes?.some(k => k.interpolation === 'bezier')))
        throw new Error(`${name} already has hand keys in the Blackridge source (--force to overwrite)`);
    const [from, to] = warp ?? [[0, src.length], [0, src.length]];
    if (Math.abs(from.at(-1) - src.length) > 1e-6) throw new Error(`${name}: P9 length ${src.length}, beats end ${from.at(-1)}`);
    const seg = t => { let i = 0; while (i < from.length - 2 && t > from[i + 1]) i++; return i; };
    const slope = i => (to[i + 1] - to[i]) / (from[i + 1] - from[i]);
    const map = t => { const i = seg(t); return to[i] + (t - from[i]) * slope(i); };
    const animators = {};
    for (const a of Object.values(src.animators ?? {})) {
        if (!KEYED.includes(a.name)) continue;
        const g = brGroups.get(a.name); if (!g) throw new Error('Blackridge rig has no ' + a.name);
        const keyframes = a.keyframes.map(k => {
            const out = {...k, time: r5(map(k.time)), uuid: uuid(`${name}:${a.name}:${k.channel}:${k.time}`)};
            if (k.interpolation === 'bezier') {   // a handle stays inside its own segment's scale
                const before = seg(Math.max(0, k.time - 1e-6)), after = seg(Math.min(src.length, k.time + 1e-6));
                out.bezier_left_time = k.bezier_left_time.map(v => r5(v * slope(before)));
                out.bezier_right_time = k.bezier_right_time.map(v => r5(v * slope(after)));
            }
            return out;
        });
        animators[g.uuid] = {...a, keyframes};
    }
    const anim = {uuid: uuid(name), name, loop: src.loop, override: false, length: to.at(-1), snapping: 60, selected: false,
        anim_time_update: '', blend_weight: '', start_delay: '', loop_delay: '', animators};
    br.animations = br.animations.filter(x => x.name !== name).concat(anim);
    report[name] = {length: anim.length, channels: Object.values(animators).map(a => `${a.name}:${a.keyframes.length}`).join(' ')};
}
fs.writeFileSync(nativePath, JSON.stringify(br));
console.log(JSON.stringify(report, null, 1), '\nwrote', path.relative(root, nativePath));
