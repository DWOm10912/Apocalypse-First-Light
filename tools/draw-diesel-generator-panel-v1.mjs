// Diesel standby generator V1 (docs/machines/diesel_standby_generator_v1.md): the instrument panel's printed face, drawn
// with a browser canvas (the user approved the analogue panel of concept v3 on 2026-10-09; English labels, as on real NA
// equipment). Only what is printed: the plate, the four dial faces (scales, red bands, titles), the hour meter's black
// window, the lamp / key / button legends and the corner screws. Bezels, gauge glasses, needles, lamps, the key switch,
// the buttons and the hour meter's drums are mesh parts (tools/build-diesel-generator-v1.mjs), placed by PANEL below.
// Below the panel: the hour meter drums' digit cells (white on black for whole hours, black on white for tenths) and two
// plain cells (black, plate grey) for the drums' ends and the plate's edges.
//   node tools/draw-diesel-generator-panel-v1.mjs   -> src/main/blockbench/textures/diesel_generator_panel_v1.png
// Headless Chrome draws the page and the canvas comes back through --dump-dom (exact canvas pixels, no screenshot).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const OUT = path.join(ROOT, 'src/main/blockbench/textures/diesel_generator_panel_v1.png');

/**
 * Panel units (the plate 500 x 400 units = 0.46 x 0.368 m, x right, y down as printed). gauges: centre, dial radius, the
 * 270 degree sweep from lower left (value 0) clockwise to lower right (value 1).
 */
export const PANEL = {
  w: 500, h: 400, metres: 0.46,
  gauges: [
    {id: 'load', c: [100, 108], r: 72, title: 'LOAD', unit: '%', ticks: ['0', '25', '50', '75', '100', '125'], red: [0.8, 1]},
    {id: 'fuel', c: [260, 108], r: 72, title: 'FUEL', unit: '', ticks: ['E', '', '\u00bd', '', 'F'], red: [0, 0.125]},
    {id: 'temp', c: [100, 272], r: 58, title: 'COOLANT', unit: '\u00b0C', ticks: ['40', '60', '80', '100', '120'], red: [0.75, 1]},
    {id: 'oil', c: [260, 272], r: 58, title: 'OIL PRESS', unit: 'bar', ticks: ['0', '2', '4', '6', '8', '10'], red: [0, 0.08]},
  ],
  sweep: [0.75 * Math.PI, 2.25 * Math.PI],   // canvas angles (y down): value 0, value 1
  // the hour meter: the chrome bezel, its window (a hole in the plate: the drums show through), the five drums
  hours: {bezel: [348, 56, 480, 94], window: [356, 64, 472, 86], drums: {x0: 359, w: 20, gap: 2.5, n: 5, face: 20}, label: [414, 106]},
  lamps: [{id: 'run', c: [372, 150], label: 'RUN'}, {id: 'low', c: [414, 150], label: 'LOW FUEL'}, {id: 'fault', c: [456, 150], label: 'FAULT'}],
  lamp: {lens: 8.5, bezel: 13},
  key: {c: [414, 238], lock: 19, bezel: 26, positions: {OFF: -45, RUN: 0, START: 40}},
  buttons: [{id: 'start', c: [382, 324], label: 'START'}, {id: 'stop', c: [446, 324], label: 'STOP'}],
  button: {cap: 12.5, bezel: 17},
  screws: [[14, 14], [486, 14], [14, 386], [486, 386]],
};
/** Canvas px per panel unit, and the digit cells under the panel (canvas px). */
export const SCALE = 2;
export const STRIP = {y: PANEL.h * SCALE, cell: 64, cols: 11, rows: 2};   // row 0 white on black, row 1 black on white; col 10 plain
export const CANVAS = [PANEL.w * SCALE, PANEL.h * SCALE + STRIP.cell * STRIP.rows];
export const COLOURS = {plate: '#2f3134', face: '#0e0f10', print: '#e9e4d8', red: '#c23a2c', dim: '#a8a296', paper: '#e6e1d4', ink: '#141516'};

// Runs in the browser (serialised into the page): draws PANEL at SCALE onto canvas c.
function drawPanel(c, P, S, ST, K) {
  const g = c.getContext('2d'), u = S;
  g.clearRect(0, 0, c.width, c.height);
  // plate: satin dark grey, a faint top-to-bottom falloff, a darker rolled edge
  const grd = g.createLinearGradient(0, 0, 0, P.h * u); grd.addColorStop(0, '#34363a'); grd.addColorStop(1, '#2b2d30');
  g.fillStyle = grd; g.fillRect(0, 0, P.w * u, P.h * u);
  g.strokeStyle = '#1e1f21'; g.lineWidth = 4 * u; g.strokeRect(2 * u, 2 * u, (P.w - 4) * u, (P.h - 4) * u);
  for (const [x, y] of P.screws) {
    g.fillStyle = '#8d9093'; g.beginPath(); g.arc(x * u, y * u, 5.5 * u, 0, 7); g.fill();
    g.strokeStyle = '#4e5154'; g.lineWidth = 1.4 * u; g.beginPath(); g.moveTo((x - 3.6) * u, (y - 1.2) * u); g.lineTo((x + 3.6) * u, (y + 1.2) * u); g.stroke();
  }
  g.textAlign = 'center'; g.textBaseline = 'middle';
  const [a0, a1] = P.sweep, at = t => a0 + (a1 - a0) * t;
  for (const G of P.gauges) {
    const cx = G.c[0] * u, cy = G.c[1] * u, r = G.r * u;
    g.fillStyle = K.face; g.beginPath(); g.arc(cx, cy, r + 1.5 * u, 0, 7); g.fill();   // under the bezel's lip too
    g.strokeStyle = K.red; g.lineWidth = 5 * u; g.beginPath(); g.arc(cx, cy, r - 8 * u, at(G.red[0]), at(G.red[1])); g.stroke();
    g.strokeStyle = K.print; g.fillStyle = K.print;
    const n = G.ticks.length - 1, big = G.r > 65;
    for (let i = 0; i <= n; i++) {
      const a = at(i / n);
      g.lineWidth = 2.4 * u; g.beginPath(); g.moveTo(cx + Math.cos(a) * (r - 4 * u), cy + Math.sin(a) * (r - 4 * u));
      g.lineTo(cx + Math.cos(a) * (r - 14 * u), cy + Math.sin(a) * (r - 14 * u)); g.stroke();
      if (G.ticks[i] !== '') { g.font = 'bold ' + (big ? 11 : 9.5) * u + 'px Arial';
        g.fillText(G.ticks[i], cx + Math.cos(a) * (r - (big ? 25 : 23) * u), cy + Math.sin(a) * (r - (big ? 25 : 23) * u)); }
      if (i < n) for (let k = 1; k < 5; k++) { const b = at((i + k / 5) / n); g.lineWidth = 1.1 * u;
        g.beginPath(); g.moveTo(cx + Math.cos(b) * (r - 4 * u), cy + Math.sin(b) * (r - 4 * u)); g.lineTo(cx + Math.cos(b) * (r - 9 * u), cy + Math.sin(b) * (r - 9 * u)); g.stroke(); }
    }
    let fs = big ? 10.5 : 8; g.font = 'bold ' + fs * u + 'px Arial';
    while (g.measureText(G.title).width > r * 0.8 && fs > 6.5) { fs -= 0.5; g.font = 'bold ' + fs * u + 'px Arial'; }
    // title and unit inside the figures' ring: the big dials low in the sweep's gap, the small ones just under the hub
    g.fillText(G.title, cx, cy + r * (big ? 0.5 : 0.25));
    if (G.unit) { g.fillStyle = K.dim; g.font = (big ? 9 : 8) * u + 'px Arial'; g.fillText(G.unit, cx, cy + r * (big ? 0.72 : 0.62)); }
  }
  // the hour meter: its window black (the drums turn behind it), the legend under the bezel
  const H = P.hours; g.fillStyle = K.face; g.fillRect(H.window[0] * u, H.window[1] * u, (H.window[2] - H.window[0]) * u, (H.window[3] - H.window[1]) * u);
  g.fillStyle = K.dim; g.font = 'bold ' + 8.5 * u + 'px Arial'; g.fillText('HOURS', H.label[0] * u, H.label[1] * u);
  // lamps: a dark ring printed under each bezel, the legend under it
  for (const L of P.lamps) { g.fillStyle = '#141517'; g.beginPath(); g.arc(L.c[0] * u, L.c[1] * u, P.lamp.bezel * u, 0, 7); g.fill();
    g.fillStyle = K.dim; g.font = 'bold ' + 7.5 * u + 'px Arial'; g.fillText(L.label, L.c[0] * u, (L.c[1] + 23) * u); }
  // key switch: position marks and legends round the bezel (angles from straight up, clockwise as seen)
  const kx = P.key.c[0] * u, ky = P.key.c[1] * u;
  for (const [name, deg] of Object.entries(P.key.positions)) {
    const a = (deg - 90) * Math.PI / 180, r0 = (P.key.bezel + 2) * u, r1 = (P.key.bezel + 6) * u;
    g.strokeStyle = K.print; g.lineWidth = 2 * u; g.beginPath(); g.moveTo(kx + Math.cos(a) * r0, ky + Math.sin(a) * r0); g.lineTo(kx + Math.cos(a) * r1, ky + Math.sin(a) * r1); g.stroke();
    g.fillStyle = K.print; g.font = 'bold ' + 8 * u + 'px Arial'; const rt = (P.key.bezel + 15) * u;
    g.fillText(name, kx + Math.cos(a) * rt * (name === 'RUN' ? 1 : 1.12), ky + Math.sin(a) * rt);
  }
  for (const B of P.buttons) { g.fillStyle = K.dim; g.font = 'bold ' + 8 * u + 'px Arial'; g.fillText(B.label, B.c[0] * u, (B.c[1] + 27) * u); }
  // digit cells for the hour meter drums
  for (let row = 0; row < ST.rows; row++) for (let col = 0; col < ST.cols; col++) {
    const x = col * ST.cell, y = ST.y + row * ST.cell, paper = row === 1;
    g.fillStyle = col === 10 ? (row === 0 ? K.face : K.plate) : paper ? K.paper : K.face; g.fillRect(x, y, ST.cell, ST.cell);
    if (col === 10) continue;
    g.fillStyle = paper ? K.ink : K.print; g.font = 'bold ' + ST.cell * 0.74 + 'px Consolas'; g.fillText(String(col), x + ST.cell / 2, y + ST.cell * 0.54);
  }
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-panel-')), page = path.join(dir, 'panel.html');
  const html = '<!doctype html><html><body><canvas id="c" width="' + CANVAS[0] + '" height="' + CANVAS[1] + '"></canvas><pre id="out"></pre><script>\n'
    + drawPanel.toString() + '\nconst c = document.getElementById("c");\n'
    + 'drawPanel(c, ' + JSON.stringify(PANEL) + ', ' + SCALE + ', ' + JSON.stringify(STRIP) + ', ' + JSON.stringify(COLOURS) + ');\n'
    + 'document.getElementById("out").textContent = c.toDataURL("image/png");\n</script></body></html>';
  fs.writeFileSync(page, html);
  const chrome = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
  const dom = execFileSync(chrome, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--virtual-time-budget=8000', '--dump-dom', 'file:///' + page.replace(/\\/g, '/')],
    {encoding: 'utf8', maxBuffer: 64 << 20});
  const m = dom.match(/data:image\/png;base64,([A-Za-z0-9+/=]+)/);
  if (!m) throw new Error('no canvas data in the dumped page');
  fs.mkdirSync(path.dirname(OUT), {recursive: true});
  fs.writeFileSync(OUT, Buffer.from(m[1], 'base64'));
  fs.rmSync(dir, {recursive: true, force: true});
  console.log('wrote ' + path.relative(ROOT, OUT) + ' (' + CANVAS.join(' x ') + ')');
}
