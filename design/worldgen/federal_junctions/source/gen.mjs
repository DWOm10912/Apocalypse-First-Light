// Writes the junction concept sheets (SVG) into the out dir given as argv[2].
import fs from 'fs';
import path from 'path';
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const only = process.argv[3];
const jobs = {
  j1_t_junction: async () => (await import('./tplan.mjs')).tSheet(),
  j2_town_corner: async () => (await import('./corner.mjs')).cornerSheet(),
};
for (const [name, fn] of Object.entries(jobs)) {
  if (only && only !== name) continue;
  fs.writeFileSync(path.join(out, name + '.svg'), await fn());
  console.log('wrote', name);
}
