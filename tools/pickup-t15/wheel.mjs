// T15 wheel (mesh pickup_t15_wheel, bone "wheel" at the hub centre; drawn at each corner and as the spare). Built for the
// right side (outboard toward +z); the renderer turns it half a turn for the left side.
// Reference (design doc 2.4, drawing 410463): pressed steel wheel, six lug nuts on 6 x 139.7, six trapezoid vent holes;
// tyre 225/75 R15 (rim 15 in, bead radius 0.19 m, outer radius 0.36 m, section width 0.225 m).
import {FZ, slab, lathe, fillet, circle} from '../vehicle-mesh-kit.mjs';
import {R, P} from './common.mjs';

const Wp = mat => P('wheel', 'wheel', mat);
export const RIM_R = 0.1905, LUG_PCD = 0.0699;

export function buildWheel() {
  // tyre: bead, bulging sidewall, rounded shoulders, flat tread (tread pattern lives in the textures)
  lathe(Wp('tire'), [0, 0, 0], [0, 0, 1], [
    [-0.093, RIM_R + 0.004], [-0.104, 0.215], [-0.112, 0.255], [-0.111, 0.30], [-0.106, 0.333], [-0.097, 0.350], [-0.084, R - 0.003], [-0.06, R],
    [0.06, R], [0.084, R - 0.003], [0.097, 0.350], [0.106, 0.333], [0.111, 0.30], [0.112, 0.255], [0.104, 0.215], [0.093, RIM_R + 0.004],
    [0.07, RIM_R + 0.002], [-0.07, RIM_R + 0.002]], 48);
  // rim barrel with its flanges (the outer flange shows; the barrel is seen through the vents)
  lathe(Wp('wheel'), [0, 0, 0], [0, 0, 1], [
    [-0.098, RIM_R + 0.012], [-0.090, RIM_R + 0.016], [-0.084, RIM_R + 0.004], [-0.03, RIM_R - 0.016], [0.05, RIM_R - 0.016], [0.084, RIM_R + 0.004],
    [0.090, RIM_R + 0.016], [0.098, RIM_R + 0.012], [0.094, RIM_R], [0.07, RIM_R - 0.022], [-0.07, RIM_R - 0.022], [-0.094, RIM_R]], 40);
  // centre disc: dished ring from the barrel to the flat mounting face, then the face with the six vents and the hub hole
  lathe(Wp('wheel'), [0, 0, 0], [0, 0, 1], [[-0.01, 0.1685], [0.035, 0.150], [0.047, 0.131], [0.040, 0.131], [0.028, 0.148], [-0.017, 0.1685]], 40);
  const vents = [];
  for (let k = 0; k < 6; k++) {
    const a = (k + 0.5) * Math.PI / 3, w0 = 0.024, w1 = 0.036, r0 = 0.098, r1 = 0.124, c = Math.cos(a), sn = Math.sin(a);
    const pt = (r, w) => [c * r - sn * w, sn * r + c * w];
    vents.push(fillet([pt(r0, -w0), pt(r1, -w1), pt(r1, w1), pt(r0, w0)].reverse(), 0.006, 2));
  }
  slab(Wp('wheel'), FZ, circle(0.132, 36), [...vents, circle(0.054, 18)], 0.04, 0.047, {r0: 0, r1: 0.002});
  // hub (dark), lug nuts, small chrome centre cap
  lathe(Wp('under'), [0, 0, 0], [0, 0, 1], [[-0.02, 0], [-0.02, 0.07], [0.04, 0.07], [0.04, 0.054], [0.06, 0.05], [0.06, 0]], 18);
  for (let k = 0; k < 6; k++) { const a = k * Math.PI / 3;
    lathe(Wp('steel'), [LUG_PCD * Math.cos(a), LUG_PCD * Math.sin(a), 0.047], [0, 0, 1], [[0, 0], [0, 0.0125], [0.012, 0.0125], [0.018, 0.0105], [0.022, 0.006], [0.023, 0]], 6, {phase: 0}); }
  lathe(Wp('chrome'), [0, 0, 0.058], [0, 0, 1], [[0, 0], [0, 0.044], [0.008, 0.043], [0.018, 0.034], [0.022, 0.02], [0.023, 0]], 20);
}
