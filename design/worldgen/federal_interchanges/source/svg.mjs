// Small SVG helpers for the interchange naming sheets (copied from federal_junctions).
export const FONT = `font-family="Microsoft YaHei, Noto Sans SC, sans-serif"`;
export const MONO = `font-family="Consolas, IBM Plex Mono, monospace"`;
/** Road-sign lettering: Bahnschrift (DIN-like, ships with Windows) narrowed, standing in for the in-game sign font. */
export const SIGN = `font-family="Bahnschrift, Arial Narrow, sans-serif" style="font-variation-settings: 'wdth' 78, 'wght' 620"`;
export const f = n => (Math.round(n * 100) / 100).toString();
export const pts = a => a.map(p => f(p[0]) + ',' + f(p[1])).join(' ');
export const poly = (a, attrs) => `<polygon points="${pts(a)}" ${attrs}/>`;
export const polyline = (a, attrs) => `<polyline points="${pts(a)}" fill="none" ${attrs}/>`;
export const line = (a, b, attrs) => `<line x1="${f(a[0])}" y1="${f(a[1])}" x2="${f(b[0])}" y2="${f(b[1])}" ${attrs}/>`;
export const text = (x, y, s, attrs = '') => `<text x="${f(x)}" y="${f(y)}" ${FONT} ${attrs}>${s}</text>`;
export const mono = (x, y, s, attrs = '') => `<text x="${f(x)}" y="${f(y)}" ${MONO} ${attrs}>${s}</text>`;
export const sign = (x, y, s, attrs = '') => `<text x="${f(x)}" y="${f(y)}" ${SIGN} ${attrs}>${s}</text>`;

export function sheet(w, h, title, code, body, note) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">
<rect width="${w}" height="${h}" fill="#fbfbf8"/>
<rect x="14" y="14" width="${w - 28}" height="${h - 28}" fill="none" stroke="#23282b" stroke-width="2"/>
<g>${body}</g>
<rect x="14" y="${h - 58}" width="${w - 28}" height="44" fill="#f1f2ef" stroke="#23282b" stroke-width="2"/>
${mono(30, h - 30, code, 'font-size="17" font-weight="700" fill="#8e3a32"')}
${text(150, h - 30, title, 'font-size="18" font-weight="700" fill="#23282b"')}
${text(w - 30, h - 30, note, 'font-size="13" fill="#6b7376" text-anchor="end"')}
</svg>`;
}

/** A block of text lines; each line [string, attrs?]. Returns the svg and the y after the last line. */
export function lines(x, y, rows, lh = 22, base = 'font-size="15" fill="#23282b"') {
  let s = '';
  for (const r of rows) {
    const [str, attrs, gap] = Array.isArray(r) ? r : [r];
    if (str) s += text(x, y, str, attrs || base);
    y += gap ?? lh;
  }
  return { svg: s, y };
}

export function star(cx, cy, r, attrs) {
  const p = [];
  for (let i = 0; i < 10; i++) {
    const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r * 0.4 : r;
    p.push([cx + rr * Math.cos(a), cy + rr * Math.sin(a)]);
  }
  return poly(p, attrs);
}
