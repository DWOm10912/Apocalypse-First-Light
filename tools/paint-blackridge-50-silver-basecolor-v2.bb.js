(async (P) => {
// Silver Base Color V2: per-facet value hierarchy (flat normals) + narrow longitudinal highlight, no fake reflection.
// Derived from Chrome V3 (polished): V5 logic + dark horizon reflection band. Blackiron V5: one gun-wide height gradient for all metal (no per-part base split), deeper cool gunmetal.
// Derived from V3: same palette/zoning as V2, but shaded from auto-smoothed normals and whole-form gradients,
// so facets blend; only long sharp silhouette edges get a highlight; serrations stay soft.
const fs = require('fs'), path = require('path');
const W = 1024, H = 1024;
const texDir = path.join(path.dirname(Project.save_path), 'textures');
const img1 = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = 'data:image/png;base64,' + fs.readFileSync(path.join(texDir, '' + P.refName)).toString('base64'); });
const c1 = document.createElement('canvas'); c1.width = W; c1.height = H; const g1 = c1.getContext('2d'); g1.drawImage(img1, 0, 0, W, H);
const D1 = g1.getImageData(0, 0, W, H).data;
const v1lum = (u, v) => { u = Math.max(0, Math.min(W - 1, Math.round(u))); v = Math.max(0, Math.min(H - 1, Math.round(v))); const i = (v * W + u) * 4; return 0.2126 * D1[i] + 0.7152 * D1[i + 1] + 0.0722 * D1[i + 2]; };
const C = P.colors;
const CTRLS = new Set(['safety', 'slide_stop', 'hammer', 'magazine_release', 'extractor_visual', 'trigger']);
const hash = (a, b) => { let h = (a * 374761393 + b * 668265263) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const acc = new Float32Array(W * H * 3); const filled = new Uint8Array(W * H);
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const SMOOTH_COS = Math.cos(P.smoothAngle * Math.PI / 180), EDGE_ANG = P.edgeAngle * Math.PI / 180;
// Continuous facing response from the (smoothed) normal: bottom -> side -> top without steps.
// Facet levels from the flat face normal: bottom / lower chamfer / side / upper chamfer / top.
const facingFlat = ny => { const T = [[-1, P.fBottom], [-0.55, P.fLower], [-0.2, 1], [0.2, 1], [0.55, P.fUpper], [1, P.fTop]];
  for (let i = 1; i < T.length; i++) if (ny <= T[i][0]) { const [a, va] = T[i - 1], [b, vb] = T[i]; return va + (vb - va) * (ny - a) / (b - a); } return P.fTop; };
const facing = ny => ny >= 0 ? 1 + (P.top - 1) * smooth(0.05, 0.95, ny) : 1 - (1 - P.bottom) * smooth(0.05, 0.95, -ny);
const stats = { faces: 0, internal: 0, keyEdges: 0 };
for (const m of Mesh.all) {
  if (m.export === false) continue;
  const Pt = k => { const p = m.vertices[k]; return [p[0] + m.origin[0], p[1] + m.origin[1], p[2] + m.origin[2]]; };
  let ymin = 1e9, ymax = -1e9, zmin = 1e9, zmax = -1e9;
  for (const k in m.vertices) { const p = Pt(k); ymin = Math.min(ymin, p[1]); ymax = Math.max(ymax, p[1]); zmin = Math.min(zmin, p[2]); zmax = Math.max(zmax, p[2]); }
  const F = Object.entries(m.faces).map(([fk, f]) => {
    const vs = f.getSortedVertices(); const pts = vs.map(Pt); let n = [0, 0, 0];
    for (let i = 1; i + 1 < pts.length; i++) { const c = cross(sub(pts[i], pts[0]), sub(pts[i + 1], pts[0])); n = [n[0] + c[0], n[1] + c[1], n[2] + c[2]]; }
    const area = Math.hypot(...n) / 2;
    const cen = pts.reduce((a, p) => [a[0] + p[0] / pts.length, a[1] + p[1] / pts.length, a[2] + p[2] / pts.length], [0, 0, 0]);
    return { fk, f, vs, pts, n: norm(n), area, cen, uv: vs.map(k => f.uv[k]) };
  });
  const byVert = {}; F.forEach((fc, i) => fc.vs.forEach(k => (byVert[k] = byVert[k] || []).push(i)));
  // auto-smooth corner normals: average neighbours within the smoothing angle, area weighted
  for (const fc of F) fc.sn = fc.vs.map(k => { let s = [0, 0, 0]; for (const j of byVert[k]) { const o = F[j]; if (dot(o.n, fc.n) >= SMOOTH_COS) s = [s[0] + o.n[0] * o.area, s[1] + o.n[1] * o.area, s[2] + o.n[2] * o.area]; } return norm(s); });
  const edgeMap = {};
  F.forEach((F_, i) => { for (let j = 0; j < F_.vs.length; j++) { const a = F_.vs[j], b = F_.vs[(j + 1) % F_.vs.length]; const key = a < b ? a + '|' + b : b + '|' + a; (edgeMap[key] = edgeMap[key] || []).push(i); } });
  const phase = P.gy ? 1.3 : hash(m.name.length, m.name.charCodeAt(0)) * 6.28;   // one drift phase for the whole gun
  for (let fi = 0; fi < F.length; fi++) {
    const fc = F[fi]; stats.faces++;
    const cu = fc.uv.reduce((a, u) => a + u[0], 0) / fc.uv.length, cv = fc.uv.reduce((a, u) => a + u[1], 0) / fc.uv.length;
    let kind;
    if (m.name === 'grip') kind = 'grip'; else if (m.name === 'follower') kind = 'foll'; else if (CTRLS.has(m.name)) kind = 'ctrl';
    else if (m.name === 'front_sight' || m.name === 'rear_sight') kind = 'sight'; else if (/magazine/.test(m.name)) kind = 'mag'; else kind = 'main';
    if ((kind === 'main' || kind === 'mag') && v1lum(cu, cv) <= P.intLum) { kind = 'int'; stats.internal++; }
    else if (m.name === 'barrel' && P.boreAxis) {
      const rmax = Math.max(...fc.pts.map(p => Math.hypot(p[0] - P.boreAxis[0], p[1] - P.boreAxis[1])));
      if (rmax < P.boreR) { kind = 'int'; stats.internal++; }
    }
    const n = fc.n; const edges = []; let concN = 0;
    for (let j = 0; j < fc.vs.length; j++) {
      const a = fc.vs[j], b = fc.vs[(j + 1) % fc.vs.length]; const key = a < b ? a + '|' + b : b + '|' + a; const nb = (edgeMap[key] || []).filter(x => x !== fi);
      let type = 'none', k = 0;
      if (nb.length) { const o = F[nb[0]]; const ang = Math.acos(Math.max(-1, Math.min(1, dot(n, o.n))));
        const conv = dot(sub(o.cen, fc.cen), n) < 0; const len = Math.hypot(...sub(Pt(a), Pt(b)));
        if (!conv && ang > 0.5) { type = 'ccv'; concN++; }
        // key edges only: sharp, long silhouette / form breaks
        else if (conv && ang > EDGE_ANG && len > P.edgeMinLen) { type = 'cvx'; k = Math.max(0, Math.min(1, P.edgeBase + Math.max(n[1], o.n[1]))); stats.keyEdges++; } }
      edges.push({ u0: fc.uv[j], u1: fc.uv[(j + 1) % fc.uv.length], type, k });
    }
    const recessFloor = concN >= Math.max(2, fc.vs.length - 1);
    let base;
    switch (kind) { case 'grip': base = C.GRIP; break; case 'foll': base = C.FOLL; break; case 'ctrl': base = C.CTRL; break; case 'sight': base = C.SIGHT; break;
      case 'int': base = C.INTERNAL; break; case 'mag': base = (fc.cen[1] < ymin + P.baseplateH) ? C.BASEP : C.MAG; break; default: base = C.MAIN; }
    let covered = 0;
    const shadeAt = (X, Y, Z, sn, px, py, x, y) => {
      const metal = kind === 'main' || kind === 'ctrl' || kind === 'sight';
      // metal parts share the gun-wide height range, so slide and frame continue the same gradient
      const h = metal && P.gy ? (Y - P.gy[0]) / (P.gy[1] - P.gy[0]) : (Y - ymin) / Math.max(1e-6, ymax - ymin);
      const along = metal && P.gz ? (Z - P.gz[0]) / (P.gz[1] - P.gz[0]) : (Z - zmin) / Math.max(1e-6, zmax - zmin);
      if (kind === 'int') return 0.8 + 0.3 * h;
      let v = P.flatFacing && kind !== 'grip' ? facingFlat(n[1]) : facing(sn[1]);
      const side = 1 - smooth(0.35, 0.8, Math.abs(sn[1]));                               // how much this texel faces sideways
      // one continuous form gradient for side walls: dark lower edge -> mid grey -> soft long band in the upper half
      const grad = P.sideLo + (P.sideHi - P.sideLo) * smooth(0, 1, h);
      const band = kind === 'grip' ? 0 : P.band * Math.exp(-Math.pow((h - P.bandH) / P.bandW, 2)) * (0.85 + 0.15 * Math.sin(along * 3.1 + phase));
      v *= 1 + side * (grad * (1 + band) - 1);
      // Polished chrome: a dark "horizon" reflection band under the highlight gives the mirror contrast.
      if (P.dark && kind !== 'grip' && kind !== 'int') {
        const dk = P.dark * Math.exp(-Math.pow((h - P.darkH) / P.darkW, 2)) * (0.9 + 0.1 * Math.sin(along * 2.3 + 0.8));
        v *= 1 - side * dk;
        // top faces mirror the sky: lift them a touch more than satin metal
        if (P.skyLift) v *= 1 + P.skyLift * smooth(0.4, 0.95, sn[1]);
      }
      if (Math.abs(sn[2]) > 0.85) v *= P.endFace;
      // narrow machined highlight running the length of the slide / frame side walls (world height, continuous along Z)
      if (P.hlAmp && (kind === 'main' || kind === 'mag') && Math.abs(n[1]) < 0.35) for (const [hy, hw, ha] of P.highlights) { const d = (Y - hy) / hw; v *= 1 + ha * P.hlAmp * Math.exp(-d * d); }
      if (kind !== 'grip') v *= 1 + P.drift * Math.sin(Z * 0.6 + phase) + P.drift * 0.4 * Math.sin(Z * 1.7 + 1.7);
      if (kind === 'grip') {
        const cell = (((x >> 1) + ((y >> 1) & 1)) & 1) ? 1 : -1;
        v *= 1 + P.stipple * 0.5 * cell + 0.03 * (hash(x >> 1, y >> 1) - 0.5) + 0.04 * Math.sin(Z * 1.3 + Y * 0.8);
      }
      if (recessFloor) v *= P.recess;
      let hi = 1, ao = 1;
      for (const e of edges) {
        if (e.type === 'none') continue;
        const ex = e.u1[0] - e.u0[0], ey = e.u1[1] - e.u0[1]; const L2 = ex * ex + ey * ey; if (L2 < 1e-9) continue;
        let t = ((px - e.u0[0]) * ex + (py - e.u0[1]) * ey) / L2; t = Math.max(0, Math.min(1, t)); const d = Math.hypot(px - (e.u0[0] + t * ex), py - (e.u0[1] + t * ey));
        if (e.type === 'cvx' && kind !== 'grip') hi = Math.max(hi, 1 + P.edgeHi * e.k * (1 - smooth(0.5, P.edgeW, d)));   // soft falloff, no hard line
        else if (e.type === 'ccv') ao = Math.min(ao, P.aoCore + (1 - P.aoCore) * smooth(0.5, P.aoW, d));
      }
      return Math.max(v * hi * ao, facing(sn[1]) * P.floorMin);
    };
    for (let i = 1; i + 1 < fc.uv.length; i++) {
      const A = fc.uv[0], B = fc.uv[i], Cc = fc.uv[i + 1]; const PA = fc.pts[0], PB = fc.pts[i], PC = fc.pts[i + 1];
      const NA = fc.sn[0], NB = fc.sn[i], NC = fc.sn[i + 1];
      const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], Cc[0]))), x1 = Math.min(W - 1, Math.ceil(Math.max(A[0], B[0], Cc[0])));
      const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], Cc[1]))), y1 = Math.min(H - 1, Math.ceil(Math.max(A[1], B[1], Cc[1])));
      const den = (B[1] - Cc[1]) * (A[0] - Cc[0]) + (Cc[0] - B[0]) * (A[1] - Cc[1]); if (Math.abs(den) < 1e-9) continue;
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const px = x + 0.5, py = y + 0.5;
        const wa = ((B[1] - Cc[1]) * (px - Cc[0]) + (Cc[0] - B[0]) * (py - Cc[1])) / den, wb = ((Cc[1] - A[1]) * (px - Cc[0]) + (A[0] - Cc[0]) * (py - Cc[1])) / den, wc = 1 - wa - wb;
        if (wa < -0.02 || wb < -0.02 || wc < -0.02) continue;
        const X = wa * PA[0] + wb * PB[0] + wc * PC[0], Y = wa * PA[1] + wb * PB[1] + wc * PC[1], Z = wa * PA[2] + wb * PB[2] + wc * PC[2];
        const sn = norm([wa * NA[0] + wb * NB[0] + wc * NC[0], wa * NA[1] + wb * NB[1] + wc * NC[1], wa * NA[2] + wb * NB[2] + wc * NC[2]]);
        const v = shadeAt(X, Y, Z, sn, px, py, x, y);
        const idx = y * W + x; acc[idx * 3] = base[0] * v; acc[idx * 3 + 1] = base[1] * v; acc[idx * 3 + 2] = base[2] * v + 1.5; filled[idx] = 1; covered++;
      }
    }
    if (covered === 0) {
      const v = kind === 'int' ? 0.9 : facing(fc.sn.reduce((a, s) => a + s[1], 0) / fc.sn.length);
      for (const [u, w] of [[cu, cv], ...fc.uv]) {
        const x = Math.max(0, Math.min(W - 1, Math.floor(u))), y = Math.max(0, Math.min(H - 1, Math.floor(w))); const idx = y * W + x;
        if (filled[idx] === 1) continue;
        acc[idx * 3] = base[0] * v; acc[idx * 3 + 1] = base[1] * v; acc[idx * 3 + 2] = base[2] * v + 1.5; filled[idx] = 3;
      }
    }
  }
}
for (let i = 0; i < W * H; i++) if (filled[i] === 3) filled[i] = 1;
for (let it = 0; it < 4; it++) {
  const add = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x; if (filled[i]) continue; let r = 0, g = 0, b = 0, c = 0;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const X = x + dx, Y = y + dy; if (X < 0 || Y < 0 || X >= W || Y >= H) continue; const j = Y * W + X; if (filled[j] === 1) { r += acc[j * 3]; g += acc[j * 3 + 1]; b += acc[j * 3 + 2]; c++; } }
    if (c) add.push([i, r / c, g / c, b / c]);
  }
  for (const [i, r, g, b] of add) { acc[i * 3] = r; acc[i * 3 + 1] = g; acc[i * 3 + 2] = b; filled[i] = 1; }
}
const out = document.createElement('canvas'); out.width = W; out.height = H; const go = out.getContext('2d'); const id = go.createImageData(W, H); const L = [];
for (let i = 0; i < W * H; i++) {
  const o = i * 4; if (!filled[i]) { id.data[o] = 18; id.data[o + 1] = 18; id.data[o + 2] = 20; id.data[o + 3] = 255; continue; }
  const r = Math.max(0, Math.min(255, acc[i * 3])), g = Math.max(0, Math.min(255, acc[i * 3 + 1])), b = Math.max(0, Math.min(255, acc[i * 3 + 2]));
  id.data[o] = r; id.data[o + 1] = g; id.data[o + 2] = b; id.data[o + 3] = 255; if (i % 7 === 0) L.push(0.2126 * r + 0.7152 * g + 0.0722 * b);
}
go.putImageData(id, 0, 0);
const file = path.join(texDir, P.outName);
const dataURL = out.toDataURL('image/png');
fs.writeFileSync(file, Buffer.from(dataURL.split(',')[1], 'base64'));
L.sort((a, b) => a - b); const q = x => Math.round(L[Math.floor(x * (L.length - 1))]);
return { file, dataURL, stats, lum: { p5: q(.05), p25: q(.25), p50: q(.5), p75: q(.75), p95: q(.95), p99: q(.99) } };
})
