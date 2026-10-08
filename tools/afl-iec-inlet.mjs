// AFL appliance power inlet (Power Outlets V1, 2026-10-08): an IEC 60320 C14 inlet as on North American commercial
// appliances, which take a detachable cord whose appliance end is a C13 connector (tools/build-power-outlets-v1.mjs
// draws that connector; client/PlugCordRenderer puts it into this inlet). Shared by the plug-in appliances' generators
// (beverage cooler, chest freezer, vending machine, water dispenser). 1.5 x real size like the outlets and plugs.
// Built on a surface facing +Z (a block's back in the generators' frame) at z = back: a black housing standing
// INLET.proud out of it, its cavity (the C13 outline: a rectangle with the two lower corners chamfered) open to the back
// with a dark floor, and the three flat pins: earth at the top centre, line and neutral below.
import {extrude, area2} from './cube-slab-mesh-lib.mjs';

const K = 1.5 * 0.016;   // px per real mm (1.5 x, 1 mm = 0.016 px)
export const INLET = {
  housing: [32 * K, 24 * K], corner: 2.5 * K, proud: 0.22,
  cavity: [24.6 * K, 15.6 * K], chamfer: 4.2 * K, floor: 0.01,
  pins: [[0, 3.6 * K], [-7 * K, -3.0 * K], [7 * K, -3.0 * K]], pin: [4.0 * K, 1.5 * K, 0.16],   // earth, line, neutral; w, t, length
};
const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
/** The C13 / C14 outline centred at (cx, cy): w x h with the two lower corners chamfered by c, counter-clockwise. */
export function iecOutline(cx, cy, w, h, c) {
  const x0 = cx - w / 2, x1 = cx + w / 2, y0 = cy - h / 2, y1 = cy + h / 2;
  return [[x0 + c, y0], [x1 - c, y0], [x1, y0 + c], [x1, y1], [x0, y1], [x0, y0 + c]];
}
function rrect(cx, cy, w, h, r, seg = 3) {
  const out = [];
  for (const [ux, uy, a0] of [[cx + w / 2 - r, cy + h / 2 - r, 0], [cx - w / 2 + r, cy + h / 2 - r, 90], [cx - w / 2 + r, cy - h / 2 + r, 180], [cx + w / 2 - r, cy - h / 2 + r, 270]])
    for (let i = 0; i <= seg; i++) { const a = (a0 + 90 * i / seg) * Math.PI / 180; out.push([ux + r * Math.cos(a), uy + r * Math.sin(a)]); }
  return out;
}
const box = (part, a, b) => extrude(part, 'z', {outer: orient([[a[0], a[1]], [b[0], a[1]], [b[0], b[1]], [a[0], b[1]]], true), holes: []}, a[2], b[2], 0);

/** Adds the inlet centred at (cx, cy) on a surface at z = back. parts: {housing, floor, pin} (black plastic, dark, metal). */
export function addIecInlet({housing, floor, pin}, cx, cy, back) {
  const I = INLET, face = back + I.proud;
  extrude(housing, 'z', {outer: orient(rrect(cx, cy, ...I.housing, I.corner), true), holes: [orient(iecOutline(cx, cy, ...I.cavity, I.chamfer), false)]},
    back - 0.02, face, 0);
  extrude(floor, 'z', {outer: orient(iecOutline(cx, cy, ...I.cavity, I.chamfer), true), holes: []}, back - 0.02, back + I.floor, 0);
  for (const [px, py] of I.pins) box(pin, [cx + px - I.pin[0] / 2, cy + py - I.pin[1] / 2, back + I.floor], [cx + px + I.pin[0] / 2, cy + py + I.pin[1] / 2, back + I.floor + I.pin[2]]);
  return face;
}
