// Writes the tunnel concept sheets (SVG) into the out dir given as argv[2].
import fs from 'fs';
import path from 'path';
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
const only = process.argv[3];
const jobs = {
  t1_sections: async () => (await import('./section.mjs')).sectionSheet(),
  t2_interior: async () => (await import('./interior.mjs')).interiorSheet(),
  t3_portal: async () => (await import('./portal.mjs')).portalSheet(),
  t4_elevation_plan: async () => (await import('./elevplan.mjs')).elevPlanSheet(),
  t5_layout_meshes: async () => (await import('./layout.mjs')).layoutSheet(),
};
for (const [name, fn] of Object.entries(jobs)) {
  if (only && only !== name) continue;
  fs.writeFileSync(path.join(out, name + '.svg'), await fn());
  console.log('wrote', name);
}
