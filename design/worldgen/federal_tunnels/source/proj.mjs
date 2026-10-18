// A tiny perspective projector + painter for the concept views.
import { f, pts } from './svg.mjs';

export function camera({ pos, yaw = 0, pitch = 0, fov = 72, w, h, ox = 0, oy = 0 }) {
  const cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
  const F = (w / 2) / Math.tan(fov * Math.PI / 360);
  const toCam = p => {
    const dx = p[0] - pos[0], dy = p[1] - pos[1], dz = p[2] - pos[2];
    const X = dx * cy - dz * sy, Z1 = dx * sy + dz * cy;
    const Y = dy * cp - Z1 * sp, Z = dy * sp + Z1 * cp;
    return [X, Y, Z];
  };
  const proj = p => { const c = toCam(p); return [ox + w / 2 + F * c[0] / c[2], oy + h / 2 - F * c[1] / c[2], c[2]]; };
  return { toCam, proj, F, w, h, ox, oy };
}

export function hex(c) { const n = parseInt(c.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
export function rgb(a) { return '#' + a.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join(''); }
export function mix(a, b, t) { const A = hex(a), B = hex(b); return rgb(A.map((v, i) => v + (B[i] - v) * t)); }
export function scale(a, k) { return rgb(hex(a).map(v => v * k)); }

/** Painter list: add polygons (3D) with fill; draw back to front within each layer. */
export class Scene {
  constructor(cam) { this.cam = cam; this.items = []; }
  add(p3, fill, { layer = 0, bias = 0, stroke = null, sw = 1, opacity = 1, filter = null, minZ = 0.4 } = {}) {
    const c = p3.map(p => this.cam.proj(p));
    if (c.some(q => q[2] < minZ)) return;
    const key = c.reduce((a, q) => a + q[2], 0) / c.length - bias;
    this.items.push({ layer, key, svg: `<polygon points="${pts(c)}" fill="${fill}"${stroke ? ` stroke="${stroke}" stroke-width="${sw}"` : ' stroke="' + fill + '" stroke-width="0.6"'}${opacity < 1 ? ` opacity="${opacity}"` : ''}${filter ? ` filter="url(#${filter})"` : ''}/>` });
  }
  line(p3, color, width, { layer = 0, bias = 0.02, opacity = 1 } = {}) {
    const c = p3.map(p => this.cam.proj(p));
    if (c.some(q => q[2] < 0.4)) return;
    const key = c.reduce((a, q) => a + q[2], 0) / c.length - bias;
    const wpx = Math.max(0.5, width * this.cam.F / key);
    this.items.push({ layer, key, svg: `<polyline points="${pts(c)}" fill="none" stroke="${color}" stroke-width="${f(Math.min(wpx, 40))}" stroke-linecap="round"${opacity < 1 ? ` opacity="${opacity}"` : ''}/>` });
  }
  raw(svg, key, layer = 0) { this.items.push({ layer, key, svg }); }
  render() {
    return this.items.sort((a, b) => a.layer - b.layer || b.key - a.key).map(i => i.svg).join('\n');
  }
}
