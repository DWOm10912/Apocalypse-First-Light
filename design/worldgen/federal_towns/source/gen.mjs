// Writes the town framework concept sheets (SVG) into the out dir given as argv[2] (optionally only one by name).
import fs from 'fs';
import path from 'path';
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const only = process.argv[3];
const jobs = {
  t1_tiers: async () => (await import('./t1_tiers.mjs')).tiersSheet(),
  t2_linden: async () => (await import('./t2_linden.mjs')).lindenSheet(),
  t3_lots: async () => (await import('./t3_lots.mjs')).lotsSheet(),
  t4_names: async () => (await import('./t4_names.mjs')).namesSheet(),
};
for (const [name, fn] of Object.entries(jobs)) {
  if (only && only !== name) continue;
  fs.writeFileSync(path.join(out, name + '.svg'), await fn());
  console.log('wrote', name);
}
