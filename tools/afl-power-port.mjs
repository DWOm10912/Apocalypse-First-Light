// AFL power port (machine side), shared by every Pure Mesh generator whose block takes a power cable: a 6 x 6 px steel
// plate whose mating face lies on the block boundary, a round socket (r 1.95, a dark cup set into the plate) and a contact
// pin. The cable's plug flange (r 1.9, tools/build-power-cable-v2.mjs PLUG) ends on the same boundary and covers the
// socket. Standard: docs/models/power_cable_v2.md ("电源接口规格"). Faces +Z (a block's back in the generators' frame);
// the caller places it at a cell's face centre.
//   Machines whose casing stops short of the boundary: back = the casing back; the plate stands out as a boss.
//   Appliances whose back wall already lies on the boundary: cut portHole() in that wall and set the plate into it
//   (back left at its default, PORT.depth inside the boundary), so no face of the wall and the plate overlap.
import {AX, extrude, mul, area2} from './cube-slab-mesh-lib.mjs';

export const PORT = {half: 3.0, socket: 1.95, gap: 0.05, depth: 0.6, chamfer: 0.12, pinR: 0.45, segments: 16};

const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const circle = (cu, cv, r, seg) => Array.from({length: seg}, (_, i) => { const a = Math.PI / seg + 2 * Math.PI * i / seg; return [cu + r * Math.cos(a), cv + r * Math.sin(a)]; });

/** Cylinder along an axis (the generators' common helper): side quads and two fan caps. */
export function cyl(part, ax, cu, cv, r, a0, a1, seg) {
  const A = AX[ax], ang = i => Math.PI / seg + 2 * Math.PI * i / seg;
  const rg = a => Array.from({length: seg}, (_, i) => part.vtx(A.to3(cu + r * Math.cos(ang(i)), cv + r * Math.sin(ang(i)), a)));
  const r0 = rg(a0), r1 = rg(a1);
  for (let i = 0; i < seg; i++) { const j = (i + 1) % seg, m = (ang(i) + ang(j)) / 2;
    part.face([r0[i], r0[j], r1[j], r1[i]], A.to3(Math.cos(m), Math.sin(m), 0), 'side'); }
  for (const [ring_, a, s] of [[r0, a0, -1], [r1, a1, 1]]) { const c = part.vtx(A.to3(cu, cv, a));
    for (let i = 0; i < seg; i++) part.face([c, ring_[i], ring_[(i + 1) % seg]], mul(A.n, s), 'cap'); }
}

/** The wall opening for a port set into a back wall that lies on the boundary: [x0, y0, x1, y1]. */
export const portHole = (cx, cy) => [cx - PORT.half - PORT.gap, cy - PORT.half - PORT.gap, cx + PORT.half + PORT.gap, cy + PORT.half + PORT.gap];

/**
 * Adds one port centred at (cx, cy) on a surface at z = back: the plate from 0.02 inside it to the mating face z1 (the
 * block boundary, 8 in a cell-centred frame), the socket cup 0.24 deep from the plate's base, the pin above it.
 * parts: {plate, socket, pin} (materials: plate steel, socket dark, pin dark steel).
 */
export function addPowerPort({plate, socket, pin}, cx, cy, back = 8 - PORT.depth, z1 = 8) {
  extrude(plate, 'z', {outer: orient(rect(cx - PORT.half, cy - PORT.half, cx + PORT.half, cy + PORT.half), true),
    holes: [orient(circle(cx, cy, PORT.socket, PORT.segments), false)]}, back - 0.02, z1, PORT.chamfer);
  cyl(socket, 'z', cx, cy, PORT.socket - 0.05, back - 0.02, back + 0.22, PORT.segments);
  cyl(pin, 'z', cx, cy, PORT.pinR, back + 0.22, back + 0.42, 8);
}
