// Crease smoothing as Vextris 0.1.1-alpha.2 documents it (docs/format.md, format 3 crease_angle), for previews and
// comparisons only (design use; the game uses Vextris' own loader): corners welded by exact position; an edge is soft when
// exactly two faces share it, in opposite directions, with their geometric normals (first three corners) less than the
// crease angle apart; each corner averages the faces reachable around its vertex across soft edges.
// weighting 'angle' = Vextris (corner angle); 'area' = face area (the comparison: big flat panels keep their normal).
// Returns one normal per face corner: out[f][k] = [nx, ny, nz].
(function (root) {
  const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  const len = a => Math.sqrt(dot(a, a));
  const unit = a => { const l = len(a); return l > 0 ? [a[0] / l, a[1] / l, a[2] / l] : [0, 0, 0]; };
  function creaseNormals(vertices, faces, creaseDeg, weighting = 'angle') {
    const weld = new Map(), wid = vertices.map(v => { const k = v[0] + ',' + v[1] + ',' + v[2]; if (!weld.has(k)) weld.set(k, weld.size); return weld.get(k); });
    const P = f => f.map(i => vertices[i]);
    const fn = [], area = [];
    faces.forEach(f => {
      const p = P(f); fn.push(unit(cross(sub(p[1], p[0]), sub(p[2], p[0]))));
      let a = 0; for (let k = 1; k + 1 < p.length; k++) a += 0.5 * len(cross(sub(p[k], p[0]), sub(p[k + 1], p[0]))); area.push(a);
    });
    const cos = Math.cos(creaseDeg * Math.PI / 180), edges = new Map();
    const ek = (a, b) => a < b ? a + ':' + b : b + ':' + a;
    faces.forEach((f, fi) => f.forEach((_, k) => {
      const a = wid[f[k]], b = wid[f[(k + 1) % f.length]], key = ek(a, b);
      if (!edges.has(key)) edges.set(key, []); edges.get(key).push({fi, a, b});
    }));
    const soft = key => {
      const e = edges.get(key);
      if (!e || e.length !== 2 || e[0].a !== e[1].b || e[0].b !== e[1].a) return null;
      // under the crease angle: the angle between the normals is strictly less than the crease
      return dot(fn[e[0].fi], fn[e[1].fi]) > cos ? e : null;
    };
    const cornerAngle = (f, k) => {
      const p = P(f), n = p.length, a = sub(p[(k + n - 1) % n], p[k]), b = sub(p[(k + 1) % n], p[k]);
      return Math.acos(Math.max(-1, Math.min(1, dot(a, b) / (len(a) * len(b) || 1))));
    };
    return faces.map((f, fi) => f.map((_, k) => {
      const v = wid[f[k]], seen = new Set([fi]), stack = [fi], sum = [0, 0, 0];
      while (stack.length) {
        const g = stack.pop(), face = faces[g], kk = face.findIndex(i => wid[i] === v), n = face.length;
        const w = weighting === 'area' ? area[g] : cornerAngle(face, kk);
        for (let c = 0; c < 3; c++) sum[c] += w * fn[g][c];
        for (const other of [wid[face[(kk + n - 1) % n]], wid[face[(kk + 1) % n]]]) {
          const e = soft(ek(v, other)); if (!e) continue;
          const h = e[0].fi === g ? e[1].fi : e[0].fi; if (!seen.has(h)) { seen.add(h); stack.push(h); }
        }
      }
      const s = unit(sum); return len(s) > 0 ? s : fn[fi];
    }));
  }
  root.creaseNormals = creaseNormals;
})(typeof module !== 'undefined' ? module.exports : window);
