// Portable diesel generator V1 (docs/machines/portable_diesel_generator_v1.md): the control panel's printed face, drawn with a
// browser canvas (concept round 1, user 2026-10-09: "都按你的建议来"). North American 60 Hz panel, English legends; its outlets
// are the game's one socket, the NEMA 5-15R of the wall outlet (the concept's 5-20R, L14-30R twist-lock and 12 V DC posts are
// gone: user, same day, "我们没有那么多插头吧，目前游戏里面的不就一种通用插头？"). Only what
// is printed: the plate, the voltmeter's dial face, the hour meter's black window, the legends, the receptacle faces (the
// raised nylon receptacle bodies take their front faces from here) and the corner screws. The voltmeter's bezel, glass and
// needle, the hour meter's drums, the oil lamp, the breaker buttons and the DC / ground posts are mesh parts
// (tools/build-portable-diesel-generator-v1.mjs), placed by PANEL below.
// Below the panel: the hour meter drums' digit cells (white on black for whole hours, black on white for tenths) and plain
// cells (col 10: black, plate grey; col 11: the nylon cream) for drum ends, plate edges and the receptacle bodies' sides.
//   node tools/draw-portable-diesel-generator-panel-v1.mjs   -> src/main/blockbench/textures/portable_diesel_generator_panel_v1.png
// Headless Chrome draws the page and the canvas comes back through --dump-dom (exact canvas pixels, no screenshot).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const OUT = path.join(ROOT, 'src/main/blockbench/textures/portable_diesel_generator_panel_v1.png');

/**
 * Panel units (the plate 500 x 400 units = 0.30 x 0.24 m, x right, y down as printed). The voltmeter sweeps 270 degrees
 * from lower left (0 V) clockwise to lower right (150 V). duplex: the two NEMA 5-15R receptacle bodies (rects), each with
 * two sockets (centres).
 */
export const PANEL = {
  w: 500, h: 400, metres: 0.30,
  volt: {c: [78, 96], r: 48, max: 150, band: [110, 130]},
  sweep: [0.75 * Math.PI, 2.25 * Math.PI],
  hours: {bezel: [154, 50, 278, 90], window: [160, 56, 272, 84], drums: {x0: 163, w: 20, gap: 2.0, n: 5, face: 20}, label: [216, 102]},
  lamp: {c: [318, 70], lens: 8, bezel: 12, label: 'OIL'},
  breakers: [{c: [372, 70], amps: 20}, {c: [414, 70], amps: 20}, {c: [458, 70], amps: 30}], breaker: {cap: 7.5, ring: 11}, breakersTitle: [415, 40],
  duplex: [{rect: [177, 214, 223, 306], sockets: [[200, 238], [200, 282]]}, {rect: [277, 214, 323, 306], sockets: [[300, 238], [300, 282]]}],
  duplexLabel: [[250, 330, '120V AC  20A'], [250, 345, 'GFCI']],
  ground: {c: [452, 256]},
  rating: [250, 382, '5.0 kW RATED  \u00b7  60 Hz  \u00b7  DIESEL ONLY'],
  screws: [[14, 14], [486, 14], [14, 386], [486, 386]],
};
export const SCALE = 2;
export const STRIP = {y: PANEL.h * SCALE, cell: 64, cols: 12, rows: 2};   // row 0 white on black, row 1 black on white; col 10 / 11 plain
export const CANVAS = [PANEL.w * SCALE, PANEL.h * SCALE + STRIP.cell * STRIP.rows];
export const COLOURS = {plate: '#303235', face: '#0e0f10', print: '#e9e4d8', dim: '#b2ab9f', green: '#3f9a4b', nylon: '#e4dfd2', nylonShade: '#d2ccbe', slot: '#141414', paper: '#e6e1d4', ink: '#141516'};

// Runs in the browser (serialised into the page): draws PANEL at SCALE onto canvas c.
function drawPanel(c, P, S, ST, K) {
  const g = c.getContext('2d'), u = S;
  g.clearRect(0, 0, c.width, c.height);
  const grd = g.createLinearGradient(0, 0, 0, P.h * u); grd.addColorStop(0, '#35373a'); grd.addColorStop(1, '#2b2d30');
  g.fillStyle = grd; g.fillRect(0, 0, P.w * u, P.h * u);
  g.strokeStyle = '#1d1e20'; g.lineWidth = 4 * u; g.strokeRect(2 * u, 2 * u, (P.w - 4) * u, (P.h - 4) * u);
  for (const [x, y] of P.screws) {
    g.fillStyle = '#8d9093'; g.beginPath(); g.arc(x * u, y * u, 5.2 * u, 0, 7); g.fill();
    g.strokeStyle = '#4e5154'; g.lineWidth = 1.3 * u; g.beginPath(); g.moveTo((x - 3.4) * u, (y - 1.1) * u); g.lineTo((x + 3.4) * u, (y + 1.1) * u); g.stroke();
  }
  g.textAlign = 'center'; g.textBaseline = 'middle';
  const text = (t, x, y, size, col = K.print) => { g.fillStyle = col; g.font = 'bold ' + size * u + 'px Arial'; g.fillText(t, x * u, y * u); };
  // voltmeter face: black disc (under the bezel's lip too), green band, ticks every 25 V, minor every 5
  { const V = P.volt, cx = V.c[0] * u, cy = V.c[1] * u, r = V.r * u, [a0, a1] = P.sweep, at = t => a0 + (a1 - a0) * t;
    g.fillStyle = K.face; g.beginPath(); g.arc(cx, cy, r + 1.5 * u, 0, 7); g.fill();
    g.strokeStyle = K.green; g.lineWidth = 4.5 * u; g.beginPath(); g.arc(cx, cy, r - 7 * u, at(V.band[0] / V.max), at(V.band[1] / V.max)); g.stroke();
    g.strokeStyle = K.print; g.fillStyle = K.print;
    for (let i = 0; i <= 6; i++) { const a = at(i / 6); g.lineWidth = 2.2 * u;
      g.beginPath(); g.moveTo(cx + Math.cos(a) * (r - 3 * u), cy + Math.sin(a) * (r - 3 * u)); g.lineTo(cx + Math.cos(a) * (r - 12 * u), cy + Math.sin(a) * (r - 12 * u)); g.stroke();
      g.font = 'bold ' + 9 * u + 'px Arial'; g.fillText(String(i * 25), cx + Math.cos(a) * (r - 21 * u), cy + Math.sin(a) * (r - 21 * u));
      if (i < 6) for (let k = 1; k < 5; k++) { const b = at((i + k / 5) / 6); g.lineWidth = 1 * u;
        g.beginPath(); g.moveTo(cx + Math.cos(b) * (r - 3 * u), cy + Math.sin(b) * (r - 3 * u)); g.lineTo(cx + Math.cos(b) * (r - 8 * u), cy + Math.sin(b) * (r - 8 * u)); g.stroke(); } }
    text('AC VOLTS', V.c[0], V.c[1] + V.r + 16, 8, K.dim); }   // under the dial on the plate: inside it the figures crowd it
  // hour meter window (the drums turn behind it) and legend
  { const H = P.hours; g.fillStyle = K.face; g.fillRect(H.window[0] * u, H.window[1] * u, (H.window[2] - H.window[0]) * u, (H.window[3] - H.window[1]) * u);
    text('HOURS', H.label[0], H.label[1], 8, K.dim); }
  // oil lamp: a dark ring printed under its bezel
  g.fillStyle = '#141517'; g.beginPath(); g.arc(P.lamp.c[0] * u, P.lamp.c[1] * u, P.lamp.bezel * u, 0, 7); g.fill();
  text(P.lamp.label, P.lamp.c[0], P.lamp.c[1] + 21, 8, K.dim);
  // breakers: dark collars under the push buttons, ratings under them
  text('BREAKERS', P.breakersTitle[0], P.breakersTitle[1], 7.5, K.dim);
  for (const B of P.breakers) { g.fillStyle = '#18191b'; g.beginPath(); g.arc(B.c[0] * u, B.c[1] * u, P.breaker.ring * u, 0, 7); g.fill(); text(B.amps + 'A', B.c[0], B.c[1] + 21, 8); }
  // NEMA 5-20R duplex faces: nylon body, two receptacle faces (T-shaped neutral slot, round-bottom ground hole below)
  for (const D of P.duplex) {
    const [x0, y0, x1, y1] = D.rect; g.fillStyle = K.nylon; g.beginPath(); g.roundRect((x0) * u, (y0) * u, (x1 - x0) * u, (y1 - y0) * u, 5 * u); g.fill();
    for (const [sx, sy] of D.sockets) {
      g.fillStyle = K.nylonShade; g.beginPath(); g.ellipse(sx * u, sy * u, 19 * u, 18 * u, 0, 0, 7); g.fill();
      g.fillStyle = K.slot;
      g.fillRect((sx + 6) * u, (sy - 8) * u, 3 * u, 10 * u);                                  // hot (the narrow blade)
      g.fillRect((sx - 9.5) * u, (sy - 9) * u, 3.6 * u, 12 * u);                              // neutral (the wide blade)
      g.beginPath(); g.moveTo((sx - 3) * u, (sy + 4) * u); g.lineTo((sx - 3) * u, (sy + 9) * u); g.arc(sx * u, (sy + 9) * u, 3 * u, Math.PI, 0, true); g.lineTo((sx + 3) * u, (sy + 4) * u); g.closePath(); g.fill();
    }
    g.fillStyle = '#9c978d'; g.beginPath(); g.arc((x0 + x1) / 2 * u, (y0 + y1) / 2 * u, 2.2 * u, 0, 7); g.fill();   // the face screw
  }
  for (const [x, y, t] of P.duplexLabel) text(t, x, y, t === 'GFCI' ? 7.5 : 8.5, t === 'GFCI' ? K.dim : K.print);
  // the ground terminal: a dark collar under the post, the earth symbol
  { const [x, y] = P.ground.c; g.fillStyle = '#18191b'; g.beginPath(); g.arc(x * u, y * u, 12 * u, 0, 7); g.fill();
    g.strokeStyle = K.print; g.lineWidth = 1.6 * u; g.beginPath();
    g.moveTo(x * u, (y + 17) * u); g.lineTo(x * u, (y + 24) * u); g.moveTo((x - 7) * u, (y + 24) * u); g.lineTo((x + 7) * u, (y + 24) * u);
    g.moveTo((x - 4.5) * u, (y + 27) * u); g.lineTo((x + 4.5) * u, (y + 27) * u); g.moveTo((x - 2) * u, (y + 30) * u); g.lineTo((x + 2) * u, (y + 30) * u); g.stroke(); }
  text(P.rating[2], P.rating[0], P.rating[1], 8, K.dim);
  // digit cells for the hour meter drums; col 10 plain (black / plate grey), col 11 plain nylon
  for (let row = 0; row < ST.rows; row++) for (let col = 0; col < ST.cols; col++) {
    const x = col * ST.cell, y = ST.y + row * ST.cell, paper = row === 1;
    g.fillStyle = col === 11 ? K.nylon : col === 10 ? (row === 0 ? K.face : K.plate) : paper ? K.paper : K.face; g.fillRect(x, y, ST.cell, ST.cell);
    if (col >= 10) continue;
    g.fillStyle = paper ? K.ink : K.print; g.font = 'bold ' + ST.cell * 0.74 + 'px Consolas'; g.fillText(String(col), x + ST.cell / 2, y + ST.cell * 0.54);
  }
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'afl-ppanel-')), page = path.join(dir, 'panel.html');
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
