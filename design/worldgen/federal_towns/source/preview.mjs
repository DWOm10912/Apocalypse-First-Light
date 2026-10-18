// Working preview (not a sheet): Linden's terrain, roads and rings, to read the site. node preview.mjs <out.svg>
import fs from 'fs';
import { loadGrid, shade, contours } from './raster.mjs';

const d = loadGrid('valley_town');
const roads = JSON.parse(fs.readFileSync(new URL('./data/r1_roads.json', import.meta.url), 'utf8'));
const [cx, cz] = roads.centre, half = 1700, px = 0.5;
const x0 = cx - half, z0 = cz - half, x1 = cx + half, z1 = cz + half;
const P = (x, z) => [(x - x0) * px, (z - z0) * px];
const r = shade(d, x0, z0, x1, z1, px);
let s = `<svg xmlns="http://www.w3.org/2000/svg" width="${r.w}" height="${r.h}"><image href="${r.uri}" width="${r.w}" height="${r.h}"/>`;
s += `<path d="${contours(d, x0, z0, x1, z1, 10, 20, P)}" stroke="#7d6a4d" stroke-width="0.6" fill="none" opacity="0.6"/>`;
for (const rt of roads.routes) {
  const p = rt.control.map(q => P(q[0], q[1]).map(v => v.toFixed(1)).join(',')).join(' ');
  s += `<polyline points="${p}" fill="none" stroke="${rt.class === 'interstate' ? '#333' : '#777'}" stroke-width="${rt.class === 'interstate' ? 3 : 2}"/>`;
}
for (const rr of [250, 450, 700]) s += `<circle cx="${P(cx, cz)[0]}" cy="${P(cx, cz)[1]}" r="${rr * px}" fill="none" stroke="#c0702c" stroke-dasharray="4 3"/>`;
for (const n of roads.nodes) { const [a, b] = P(n.x, n.z); s += `<circle cx="${a}" cy="${b}" r="3" fill="red"/><text x="${a + 4}" y="${b - 4}" font-size="9">${n.id.slice(0, 40)}</text>`; }
// height labels every 400 m
for (let x = x0 + 200; x < x1; x += 400) for (let z = z0 + 200; z < z1; z += 400) { const [a, b] = P(x, z); s += `<text x="${a}" y="${b}" font-size="10" fill="#222">${d.at(x, z).toFixed(0)}</text>`; }
s += '</svg>';
fs.writeFileSync(process.argv[2], s);
