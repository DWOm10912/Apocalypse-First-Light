// Terrain stats round each town node (data/<id>.json from TownSurvey): height range, water share and the share of
// buildable cells (dry, slope under 8 %) within a few radii. Usage: node stats.mjs
import fs from 'fs';

const dir = new URL('./data/', import.meta.url);
const files = fs.readdirSync(dir).filter(f => f.endsWith('.json') && !f.startsWith('r1_'));
const RADII = [250, 450, 700];
for (const f of files) {
  const d = JSON.parse(fs.readFileSync(new URL(f, dir), 'utf8'));
  const n = d.n, c = d.cell, H = (i, j) => d.h[j * n + i] / 10, W = (i, j) => d.w[j * n + i];
  const mid = (n - 1) / 2;
  const row = [d.id.padEnd(16)];
  for (const r of RADII) {
    let lo = Infinity, hi = -Infinity, wet = 0, flat = 0, all = 0;
    for (let j = 1; j < n - 1; j++)
      for (let i = 1; i < n - 1; i++) {
        if (Math.hypot(i - mid, j - mid) * c > r) continue;
        all++;
        const h = H(i, j);
        lo = Math.min(lo, h); hi = Math.max(hi, h);
        if (W(i, j) !== 0) { wet++; continue; }
        const gx = (H(i + 1, j) - H(i - 1, j)) / (2 * c), gz = (H(i, j + 1) - H(i, j - 1)) / (2 * c);
        if (Math.hypot(gx, gz) < 0.08) flat++;
      }
    row.push(`r${r}: h ${lo.toFixed(0)}-${hi.toFixed(0)} wet ${(100 * wet / all).toFixed(0)}% flat ${(100 * flat / all).toFixed(0)}%`);
  }
  console.log(row.join(' | '));
}
