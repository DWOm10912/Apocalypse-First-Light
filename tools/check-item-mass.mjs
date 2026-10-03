// Item mass audit (docs/gameplay/weight_system_v1.md). Read-only; writes nothing.
//
// Checks what weight/ItemMassData would do with data/*/item_mass/*.json, before the game loads it:
// - every AFL item (registry/AflItems.java) has an explicit `items` entry, or a `native_guns` entry for guns;
// - each file loads the way the runtime loads it: one bad entry (unknown item id, non-whole grams, negative, duplicate
//   of an earlier file's rule, a second policy, a bad penalty curve or carry factor) rejects the WHOLE file, so these
//   are errors here;
// - with the vanilla/Forge jars from the Gradle cache: tag_defaults that tie (same top priority, different mass) on a
//   vanilla item, which the runtime turns into the 250 g fallback; tags that do not exist; vanilla fallback count.
//
// Usage: node tools/check-item-mass.mjs [--list] [--vanilla-fallback] [--item <id>]... [--client-jar <jar>] [--forge-jar <jar>]
//   --list              AFL items with their mass (and carry factor), heaviest first
//   --vanilla-fallback  vanilla items no rule covers (they weigh the fallback)
//   --item <id>         what an item resolves to (explicit rule, winning tag, or fallback); repeatable
// Exit code 1 when anything would be missing, rejected or tied.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const MOD = 'apocalypse_firstlight';
const args = process.argv.slice(2);
const flag = name => args.includes(name);
const option = name => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null; };
const errors = [], warnings = [];

// ---- AFL registry: every ITEMS.register("id", ...) in AflItems.java; guns name their definition id.
const itemsJava = fs.readFileSync(path.join(ROOT, 'src/main/java/com/antaurora/apofirstlight/registry/AflItems.java'), 'utf8');
const afl = new Map(); // id -> gun definition id | null
for (const part of itemsJava.split('ITEMS.register(').slice(1)) {
    const id = part.match(/^"([a-z0-9_]+)"/)?.[1];
    if (!id) continue;
    const gun = /NativeGunItem\(/.test(part) ? part.match(/ResourceLocation\("([a-z0-9_.-]+)",\s*"([a-z0-9_/.-]+)"\)/) : null;
    afl.set(`${MOD}:${id}`, gun ? `${gun[1]}:${gun[2]}` : null);
}

// ---- Vanilla / Forge jars (optional): item ids and item tags.
function zipEntries(file) {
    const data = fs.readFileSync(file), entries = new Map();
    let end = data.length - 22;
    while (end >= 0 && data.readUInt32LE(end) !== 0x06054b50) end--;
    let at = data.readUInt32LE(end + 16);
    for (let n = data.readUInt16LE(end + 10); n > 0; n--) {
        const method = data.readUInt16LE(at + 10), size = data.readUInt32LE(at + 20), nameLength = data.readUInt16LE(at + 28);
        const local = data.readUInt32LE(at + 42), name = data.toString('utf8', at + 46, at + 46 + nameLength);
        entries.set(name, () => {
            const start = local + 30 + data.readUInt16LE(local + 26) + data.readUInt16LE(local + 28), body = data.subarray(start, start + size);
            return (method === 8 ? zlib.inflateRawSync(body) : body).toString('utf8');
        });
        at += 46 + nameLength + data.readUInt16LE(at + 30) + data.readUInt16LE(at + 32);
    }
    return entries;
}
const props = Object.fromEntries(fs.readFileSync(path.join(ROOT, 'gradle.properties'), 'utf8').split(/\r?\n/)
    .map(line => line.match(/^\s*([\w.]+)\s*=\s*(.*?)\s*$/)).filter(Boolean).map(m => [m[1], m[2]]));
const cache = path.join(os.homedir(), '.gradle/caches/forge_gradle');
const mc = props.minecraft_version, forge = props.forge_version;
const clientJar = option('--client-jar') ?? path.join(cache, `minecraft_repo/versions/${mc}/client-extra.jar`);
const forgeJar = option('--forge-jar') ?? path.join(cache, `maven_downloader/net/minecraftforge/forge/${mc}-${forge}/forge-${mc}-${forge}-universal.jar`);
const haveJars = fs.existsSync(clientJar) && fs.existsSync(forgeJar);
if (!haveJars) warnings.push(`vanilla checks skipped: jars not found (${clientJar}, ${forgeJar}); minecraft:* ids are NOT verified`);

const vanilla = new Set();      // minecraft item ids
const tagFiles = new Map();     // tag id -> [{replace, values}] in load order
function addTagFile(tag, text) {
    const json = JSON.parse(text);
    if (!tagFiles.has(tag)) tagFiles.set(tag, []);
    tagFiles.get(tag).push({replace: !!json.replace, values: json.values ?? []});
}
const TAG_PATH = /^data\/([a-z0-9_.-]+)\/tags\/items\/(.+)\.json$/;
if (haveJars) {
    const client = zipEntries(clientJar);
    const lang = JSON.parse(client.get('assets/minecraft/lang/en_us.json')());
    const keys = Object.keys(lang);
    for (const name of client.keys()) {
        const m = name.match(/^assets\/minecraft\/models\/item\/([a-z0-9_]+)\.json$/);
        if (!m) continue;
        // item models also hold variants (compass_00, bow_pulling_0); an item has a name too
        const id = m[1];
        if (lang[`item.minecraft.${id}`] || lang[`block.minecraft.${id}`] || keys.some(k => k.startsWith(`item.minecraft.${id}.`))) vanilla.add(`minecraft:${id}`);
    }
    for (const [jar, entries] of [[clientJar, client], [forgeJar, zipEntries(forgeJar)]])
        for (const name of entries.keys()) { const m = name.match(TAG_PATH); if (m) addTagFile(`${m[1]}:${m[2]}`, entries.get(name)()); }
}
const dataRoot = path.join(ROOT, 'src/main/resources/data');
function walk(dir) { return fs.existsSync(dir) ? fs.readdirSync(dir, {withFileTypes: true}).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]) : []; }
for (const file of walk(dataRoot)) {
    const m = path.relative(path.join(ROOT, 'src/main/resources'), file).replaceAll('\\', '/').match(TAG_PATH);
    if (m) addTagFile(`${m[1]}:${m[2]}`, fs.readFileSync(file, 'utf8'));
}
const resolved = new Map();
function tagItems(tag, stack = []) {
    if (resolved.has(tag)) return resolved.get(tag);
    const out = new Set();
    if (stack.includes(tag)) return out;
    for (const file of tagFiles.get(tag) ?? []) {
        if (file.replace) out.clear();
        for (const value of file.values) {
            const id = typeof value === 'string' ? value : value.id;
            if (id.startsWith('#')) tagItems(id.slice(1), [...stack, tag]).forEach(x => out.add(x)); else out.add(id);
        }
    }
    resolved.set(tag, out);
    return out;
}

// ---- item_mass files, in the runtime's order (ResourceLocation: path, then namespace).
const known = id => afl.has(id) || (haveJars ? vanilla.has(id) : id.startsWith('minecraft:'));
const isId = id => /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id);
function grams(value, where) {
    if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`${where}: must be numeric kg`);
    const g = Math.round(value * 1000);
    if (Math.abs(value * 1000 - g) > 1e-6) throw new Error(`${where}: ${value} kg is not whole grams`);
    if (g < 0) throw new Error(`${where}: must be nonnegative`);
    return g;
}
const files = walk(dataRoot).map(file => {
    const m = path.relative(dataRoot, file).replaceAll('\\', '/').match(/^([a-z0-9_.-]+)\/item_mass\/(.+)\.json$/);
    return m && {file, ns: m[1], path: m[2]};
}).filter(Boolean).sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : a.ns < b.ns ? -1 : a.ns > b.ns ? 1 : 0);
const items = new Map(), guns = new Map(), tags = [], carry = []; // id -> {grams, file}
const finite = (o, key) => { const v = o?.[key]; if (typeof v !== 'number' || !Number.isFinite(v)) throw new Error(`${key} must be a finite number`); return v; };
function penalties(p) { // ItemMassData#penalties
    const armor = finite(p, 'armor_load_factor');
    if (!Array.isArray(p.curve) || !p.curve.length) throw new Error('penalties.curve must be a non-empty list');
    let last = -Infinity;
    for (const c of p.curve) {
        const ratio = finite(c, 'load_ratio'), speed = finite(c, 'speed'), jump = finite(c, 'jump');
        if (ratio < 0 || ratio <= last || speed <= 0 || speed > 1 || jump <= 0 || jump > 1) throw new Error(`bad penalty curve point ${JSON.stringify(c)}`);
        last = ratio;
    }
    const block = finite(p, 'sprint_block_ratio'), resume = finite(p, 'sprint_resume_ratio');
    if (armor <= 0 || resume <= 0 || resume > block) throw new Error('bad penalties');
}
let policyFile = null;
for (const f of files) {
    const name = `${f.ns}:${f.path}`;
    try {
        const json = JSON.parse(fs.readFileSync(f.file, 'utf8'));
        if (json.format_version !== 1) throw new Error('format_version must be 1');
        const nextItems = new Map(), nextGuns = new Map(), nextTags = [];
        for (const [id, rule] of Object.entries(json.items ?? {})) {
            if (!isId(id) || !known(id)) throw new Error(`unknown item ${id}`);
            if (items.has(id) || nextItems.has(id)) throw new Error(`duplicate rule ${id} (also in ${items.get(id)?.file ?? name})`);
            nextItems.set(id, {grams: grams(rule.unit_mass_kg, id), file: name});
        }
        for (const [id, rule] of Object.entries(json.native_guns ?? {})) {
            if (guns.has(id) || nextGuns.has(id)) throw new Error(`duplicate gun rule ${id}`);
            nextGuns.set(id, {grams: grams(rule.receiver_mass_kg, id) + grams(rule.default_magazine_empty_mass_kg, id), file: name});
        }
        for (const rule of json.tag_defaults ?? []) {
            if (!isId(rule.tag) || !Number.isInteger(rule.priority)) throw new Error(`bad tag rule ${JSON.stringify(rule)}`);
            nextTags.push({tag: rule.tag, priority: rule.priority, grams: grams(rule.unit_mass_kg, rule.tag), file: name});
        }
        const nextCarry = [];
        for (const rule of json.carry_factors ?? []) {
            if (!isId(rule.tag) || !(finite(rule, 'factor') > 0)) throw new Error(`bad carry factor ${JSON.stringify(rule)}`);
            nextCarry.push({tag: rule.tag, factor: rule.factor, file: name});
        }
        if (json.policy) {
            if (policyFile) throw new Error(`second policy (first in ${policyFile})`);
            if (grams(json.policy.fallback_unit_mass_kg, 'fallback') <= 0 || grams(json.policy.comfort_capacity_kg, 'comfort') <= 0)
                throw new Error('fallback and comfort must be positive');
            const onset = finite(json.policy, 'severity_onset_ratio'), severe = finite(json.policy, 'severe_ratio');
            if (onset < 0 || severe <= onset) throw new Error('invalid policy ratios');
            if (json.policy.penalties) penalties(json.policy.penalties);
        }
        nextItems.forEach((v, k) => items.set(k, v)); nextGuns.forEach((v, k) => guns.set(k, v)); tags.push(...nextTags); carry.push(...nextCarry);
        if (json.policy) policyFile = name;
    } catch (e) {
        errors.push(`${name}: the runtime would reject this whole file: ${e.message}`);
    }
}

for (const rule of carry) if (!tagFiles.has(rule.tag)) warnings.push(`carry tag ${rule.tag} (${rule.file}) does not exist; the factor never applies`);
const carryOf = id => Math.max(1, ...carry.filter(rule => tagItems(rule.tag).has(id)).map(rule => rule.factor));

// ---- AFL coverage
const missing = [...afl].filter(([id, gun]) => gun ? !guns.has(gun) : !items.has(id)).map(([id]) => id);
missing.forEach(id => errors.push(`no explicit mass: ${id}`));

// ---- vanilla tag resolution (same as ItemMassData#publish: explicit > highest priority tag > fallback)
let tagCovered = 0;
const fallback = [];
if (haveJars) {
    for (const rule of tags) if (!tagFiles.has(rule.tag)) warnings.push(`tag ${rule.tag} (${rule.file}) does not exist; the rule never applies`);
    for (const id of [...vanilla].sort()) {
        if (items.has(id)) continue;
        const matches = tags.filter(rule => tagItems(rule.tag).has(id));
        if (!matches.length) { fallback.push(id); continue; }
        const top = Math.max(...matches.map(r => r.priority)), winners = matches.filter(r => r.priority === top);
        if (new Set(winners.map(r => r.grams)).size > 1)
            errors.push(`tag tie on ${id} at priority ${top}: ${winners.map(r => `${r.tag}=${r.grams} g`).join(', ')} (runtime: fallback)`);
        else tagCovered++;
    }
}

if (flag('--list'))
    [...afl].map(([id, gun]) => [id, gun ? guns.get(gun)?.grams : items.get(id)?.grams])
        .sort((a, b) => (b[1] ?? -1) - (a[1] ?? -1))
        .forEach(([id, g]) => console.log(`${g === undefined ? 'MISSING' : (g / 1000).toString().padStart(7) + ' kg'}  ${id}${carryOf(id) !== 1 ? `  (carry x${carryOf(id)})` : ''}`));
if (flag('--vanilla-fallback')) fallback.forEach(id => console.log(`fallback  ${id}`));
args.forEach((a, i) => {
    if (a !== '--item') return;
    const id = args[i + 1].includes(':') ? args[i + 1] : 'minecraft:' + args[i + 1];
    const gun = afl.get(id);
    if (gun) return console.log(`item  ${id} -> ${guns.get(gun)?.grams ?? 'MISSING'} g receiver+magazine (native_guns)`);
    if (items.has(id)) return console.log(`item  ${id} -> ${items.get(id).grams} g (items, ${items.get(id).file})`);
    const matches = tags.filter(rule => tagItems(rule.tag).has(id));
    if (!matches.length) return console.log(`item  ${id} -> fallback${haveJars ? '' : ' (vanilla tags not loaded)'}`);
    const top = Math.max(...matches.map(r => r.priority)), winners = matches.filter(r => r.priority === top);
    console.log(`item  ${id} -> ${winners[0].grams} g (tag ${winners.map(r => r.tag).join(' / ')} at priority ${top})${new Set(winners.map(r => r.grams)).size > 1 ? ' TIE -> fallback' : ''}`);
});
warnings.forEach(w => console.log(`WARN  ${w}`));
errors.forEach(e => console.log(`ERROR ${e}`));
console.log(`item_mass files=${files.length} | AFL items=${afl.size} explicit=${afl.size - missing.length} missing=${missing.length}`
    + (haveJars ? ` | vanilla items=${vanilla.size} explicit=${[...vanilla].filter(id => items.has(id)).length} tag=${tagCovered} fallback=${fallback.length}` : '')
    + ` | ${errors.length ? 'FAIL' : 'PASS'}`);
process.exit(errors.length ? 1 : 0);
