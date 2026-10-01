// Energy Battery V1 item mesh: a small cylindrical cell, drawn as Pure Mesh in the world views (hand, ground, item frame)
// and as a 16x16 2D icon in the inventory, by the same bake as the Material Meshes (tools/build-material-meshes-v1.mjs).
//   node tools/build-energy-battery-v1.mjs                 -> writes source, atlas, geo, AFL mesh sidecar, icon and item model
//   node tools/build-energy-battery-v1.mjs --check         -> verifies every output is up to date
//   node tools/build-energy-battery-v1.mjs --preview DIR   -> writes the outputs into DIR only (offline review)
// Frame (px): cell axis along X, positive terminal toward +X; bake centres it on X / Z and rests it on y = 0.
// Look: dark shrink-wrapped can (the film laps over both rims), an amber band marking the positive end, nickel-plated
// end discs and a raised positive nub. No printed text, plain surfaces.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Part, extrude, area2} from './cube-slab-mesh-lib.mjs';
import {bake} from './build-material-meshes-v1.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const circle = (cu, cv, r, n, phase = Math.PI / n) => Array.from({length: n}, (_, i) => [cu + r * Math.cos(phase + 2 * Math.PI * i / n), cv + r * Math.sin(phase + 2 * Math.PI * i / n)]);
const disc = (r, seg = 14) => ({outer: orient(circle(0, R, r, seg), true), holes: []});   // (z, y) section, axis at y = R

export const R = 2.3, X0 = -5.4, X1 = 3.4, BAND = [1.6, 2.8], NUB = {r: 0.85, h: 0.55};

function energyBattery() {
  const can = new Part('can', 'energy_battery', 'wrap');
  extrude(can, 'x', disc(R), X0, X1, 0.3);
  for (const f of can.f) if (f.tag === 'cap') f.mat = 'metal';            // end discs; the bevels stay wrapped film
  const band = new Part('band', 'energy_battery', 'band');
  extrude(band, 'x', disc(R + 0.03), BAND[0], BAND[1], 0);
  const nub = new Part('nub', 'energy_battery', 'metal');
  extrude(nub, 'x', disc(NUB.r, 12), X1 - 0.05, X1 + NUB.h, 0.12);
  return {PARTS: [can, band, nub], MATS: {
    wrap:  {c: [42, 46, 52], hl: 10, sm: 118, se: 140, f0: 20},     // glossy PVC shrink wrap
    band:  {c: [184, 136, 46], hl: 8, sm: 118, se: 140, f0: 20},    // amber band, positive end
    metal: {c: [156, 158, 162], hl: 12, sm: 132, se: 150, f0: 255}, // nickel-plated steel terminals
  }, icon: {view: [30, 225, 0]}};
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const {outputs, stats} = bake('energy_battery', energyBattery());
  console.log(JSON.stringify(stats));
  const pi = process.argv.indexOf('--preview'), check = process.argv.includes('--check');
  let stale = 0;
  for (const [file, data] of outputs) {
    const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
    if (pi > 0) { const out = path.join(process.argv[pi + 1], path.basename(file)); fs.mkdirSync(path.dirname(out), {recursive: true}); fs.writeFileSync(out, buf); }
    else if (check) { if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) { console.log('stale ' + path.relative(ROOT, file)); stale++; } }
    else { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, buf); }
  }
  if (check) { if (stale) process.exit(1); console.log('CHECK OK'); }
  else console.log(pi > 0 ? 'preview written to ' + process.argv[pi + 1] : 'wrote ' + outputs.map(([f]) => path.relative(ROOT, f)).join(', '));
}
