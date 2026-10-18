// Small SVG helpers for the junction concept sheets (copied from federal_tunnels).
export const FONT = `font-family="Microsoft YaHei, Noto Sans SC, sans-serif"`;
export const MONO = `font-family="Consolas, IBM Plex Mono, monospace"`;
export const f = n => (Math.round(n * 100) / 100).toString();
export const pts = a => a.map(p => f(p[0]) + ',' + f(p[1])).join(' ');
export const poly = (a, attrs) => `<polygon points="${pts(a)}" ${attrs}/>`;
export const line = (a, b, attrs) => `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" ${attrs}/>`;
export const text = (x, y, s, attrs = '') => `<text x="${f(x)}" y="${f(y)}" ${FONT} ${attrs}>${s}</text>`;
export const mono = (x, y, s, attrs = '') => `<text x="${f(x)}" y="${f(y)}" ${MONO} ${attrs}>${s}</text>`;

export function sheet(w, h, title, code, body, note = '') {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">
<rect width="${w}" height="${h}" fill="#fbfbf8"/>
<rect x="14" y="14" width="${w - 28}" height="${h - 28}" fill="none" stroke="#23282b" stroke-width="2"/>
<g>${body}</g>
<rect x="14" y="${h - 58}" width="${w - 28}" height="44" fill="#f1f2ef" stroke="#23282b" stroke-width="2"/>
${mono(30, h - 30, code, 'font-size="17" font-weight="700" fill="#8e3a32"')}
${text(150, h - 30, title, 'font-size="18" font-weight="700" fill="#23282b"')}
${text(w - 30, h - 30, note || 'AFL C3.3b 路口 · 州道平交概念 · 2026-10-16 · 尺寸单位 m', 'font-size="13" fill="#6b7376" text-anchor="end"')}
</svg>`;
}

/** Horizontal dimension: from x0 to x1 at y, with extension lines down to y0. */
export function dimH(x0, x1, y, label, y0 = null, size = 13) {
  let s = line([x0, y], [x1, y], 'stroke="#2c6174" stroke-width="1"');
  for (const x of [x0, x1]) {
    s += line([x - 4, y + 4], [x + 4, y - 4], 'stroke="#2c6174" stroke-width="1.4"');
    if (y0 !== null) s += line([x, y0], [x, y + (y0 > y ? -6 : 6)], 'stroke="#2c6174" stroke-width="0.7" stroke-dasharray="3 3"');
  }
  s += mono((x0 + x1) / 2, y - 5, label, `font-size="${size}" fill="#2c6174" text-anchor="middle"`);
  return s;
}
/** Vertical dimension at x from y0 to y1 (screen), label rotated. */
export function dimV(x, y0, y1, label, size = 13) {
  let s = line([x, y0], [x, y1], 'stroke="#2c6174" stroke-width="1"');
  for (const y of [y0, y1]) s += line([x - 4, y + 4], [x + 4, y - 4], 'stroke="#2c6174" stroke-width="1.4"');
  const ym = (y0 + y1) / 2;
  s += `<text x="${f(x - 6)}" y="${f(ym)}" ${MONO} font-size="${size}" fill="#2c6174" text-anchor="middle" transform="rotate(-90 ${f(x - 6)} ${f(ym)})">${label}</text>`;
  return s;
}
/** Leader: dot at p, elbow to q, label at q. */
export function leader(p, q, label, anchor = 'start', size = 13.5) {
  const dx = anchor === 'start' ? 6 : -6;
  return `<circle cx="${f(p[0])}" cy="${f(p[1])}" r="2.6" fill="#8e3a32"/>` +
    `<polyline points="${pts([p, q, [q[0] + (anchor === 'start' ? 14 : -14), q[1]]])}" fill="none" stroke="#8e3a32" stroke-width="1"/>` +
    text(q[0] + (anchor === 'start' ? 14 : -14) + dx, q[1] + 4.5, label, `font-size="${size}" fill="#23282b" text-anchor="${anchor}"`);
}
/** Numbered callout bubble. */
export function bubble(x, y, n, r = 11) {
  return `<circle cx="${f(x)}" cy="${f(y)}" r="${r}" fill="#8e3a32" stroke="#fff" stroke-width="1.6"/>` +
    mono(x, y + 4.5, String(n), `font-size="${r + 2}" font-weight="700" fill="#fff" text-anchor="middle"`);
}
