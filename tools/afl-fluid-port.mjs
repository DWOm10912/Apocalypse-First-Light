// AFL fluid port (device side), shared by every Pure Mesh generator whose block takes a fluid pipe: the counterpart of
// Fluid Pipe V2's port flange (tools/build-fluid-pipe-v2.mjs: a square flange +-5.4 px from 7.2 px to the boundary, bore
// +-4.0, four nuts at (+-4.65, +-4.65)). Here: a square steel port plate (+-5.5 px) whose mating face lies on the block
// boundary, the bore (+-4.0) open into a dark throat, and four stud bolts at the pipe's bolt positions standing out of the
// face (inside the pipe's flange when one is fitted). Standard: docs/models/fluid_pipe_v2.md ("流体接口规格").
// Faces +Z (as tools/afl-power-port.mjs); the caller places it at a cell's face centre and turns it.
//   Devices whose casing stops short of the boundary: back = the casing's top / back; the plate stands out as a boss.
import {extrude, area2} from './cube-slab-mesh-lib.mjs';

export const FLUID_PORT = {half: 5.5, bore: 4.0, depth: 0.6, throat: 1.6, bolt: 4.65, stud: 0.42, studOut: 0.32};

const orient = (L, ccw) => (area2(L) > 0) === ccw ? L : L.slice().reverse();
const rect = (u0, v0, u1, v1) => [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
const hex = (cx, cy, r) => Array.from({length: 6}, (_, i) => { const a = (30 + 60 * i) * Math.PI / 180; return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; });

/**
 * plate: the port plate (steel); throat: the dark bore behind it; studs: the four stud bolts. (cx, cy): the port centre in
 * the face plane (x, y); back: where the plate starts (default FLUID_PORT.depth inside the boundary); z1: the boundary.
 */
export function addFluidPort({plate, throat, studs}, cx, cy, back = -FLUID_PORT.depth, z1 = 0) {
  const P = FLUID_PORT, h = P.half, b = P.bore;
  extrude(plate, 'z', {outer: orient(rect(cx - h, cy - h, cx + h, cy + h), true), holes: [orient(rect(cx - b, cy - b, cx + b, cy + b), false)]}, back, z1, 0.08);
  // the throat: a dark box behind the bore, open toward the face (its face toward the boundary is the bore's bottom)
  extrude(throat, 'z', {outer: orient(rect(cx - b, cy - b, cx + b, cy + b), true), holes: []}, z1 - P.throat, back + 0.05, 0);
  for (const sx of [-1, 1]) for (const sy of [-1, 1])
    extrude(studs, 'z', {outer: orient(hex(cx + sx * P.bolt, cy + sy * P.bolt, P.stud), true), holes: []}, z1, z1 + P.studOut, 0);
}
