// Copies the planned roads and nodes near a town out of an offline road plan (SystemPlans output) into
// data/r1_roads.json for the sheets. Usage: node extract_roads.mjs <plan.json> <town id> <radius m>
import fs from 'fs';

const [plan, town, radius] = process.argv.slice(2);
const j = JSON.parse(fs.readFileSync(plan, 'utf8'));
const t = j.nodes.find(n => n.id === town);
const R = +radius, near = p => Math.hypot(p[0] - t.x, p[1] - t.z) < R;
const routes = j.routes.filter(r => r.control.some(near)).map(r => ({ id: r.id, class: r.class, corridor: r.corridor, control: r.control }));
const nodes = j.nodes.filter(n => Math.hypot(n.x - t.x, n.z - t.z) < R);
fs.writeFileSync(new URL('./data/r1_roads.json', import.meta.url), JSON.stringify({
  source: plan.replace(/\\/g, '/') + ' (offline SystemPlans, seed -295378578869149513 = r1)', town, centre: [t.x, t.z], routes, nodes,
}));
console.log('routes', routes.map(r => r.id).join(', '));
console.log('nodes', nodes.length);
