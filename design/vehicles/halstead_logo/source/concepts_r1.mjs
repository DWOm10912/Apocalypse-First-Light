// Halstead Motor Company badge concepts, round 1 (2026-10-21): three directions on one sheet, each as a chrome badge
// and in one colour, plus where it sits on the T15 (grille centre plate, tailgate). Run from the repo root:
//   node design/vehicles/halstead_logo/source/concepts_r1.mjs
import { createRequire } from 'module'
import path from 'path'
import { fileURLToPath } from 'url'
const require = createRequire(import.meta.url)
const sharp = require('../../../../wiki/node_modules/sharp')
const here = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.join(here, '..', 'halstead_logo_concepts_r1.png')

const W = 2400, H = 1500
const FONT = "font-family='Bahnschrift, Arial' font-weight='700'"

// chrome: a horizon line across a bright sky band and a dark ground band, the usual stamped-chrome look
const defs = `
<linearGradient id='chrome' x1='0' y1='0' x2='0' y2='1'>
  <stop offset='0' stop-color='#f4f6f8'/><stop offset='0.42' stop-color='#c9ced4'/><stop offset='0.5' stop-color='#5c636b'/>
  <stop offset='0.62' stop-color='#9aa1a8'/><stop offset='1' stop-color='#e6e9ec'/>
</linearGradient>
<linearGradient id='chromeEdge' x1='0' y1='0' x2='0' y2='1'>
  <stop offset='0' stop-color='#8a9097'/><stop offset='1' stop-color='#2c3035'/>
</linearGradient>
<linearGradient id='paint' x1='0' y1='0' x2='0' y2='1'>
  <stop offset='0' stop-color='#7d2a24'/><stop offset='1' stop-color='#5a1d19'/>
</linearGradient>
<filter id='drop' x='-10%' y='-10%' width='120%' height='140%'><feDropShadow dx='0' dy='4' stdDeviation='4' flood-color='#000' flood-opacity='0.45'/></filter>`

// ---- A: the HALSTEAD wordmark, wide stamped capitals (grille plate and tailgate)
const word = (x, y, size, fill, extra = '') =>
  `<text x='${x}' y='${y}' ${FONT} font-size='${size}' text-anchor='middle' fill='${fill}' letter-spacing='${size * 0.18}'
    transform='translate(${x} ${y}) scale(1.28 1) translate(${-x} ${-y})' ${extra}>HALSTEAD</text>`

// ---- B: three tapered bars stacked into a conifer on a short trunk, inside a rounded keystone (echoes the grille's three bars)
function treeBadge(cx, cy, s, fill, stroke) {
  const k = (x, y) => `${cx + x * s},${cy + y * s}`
  const keystone = `M${k(-0.62, -0.78)} L${k(0.62, -0.78)} Q${k(0.70, -0.78)} ${k(0.69, -0.70)} L${k(0.52, 0.66)} Q${k(0.50, 0.78)} ${k(0.40, 0.78)} L${k(-0.40, 0.78)} Q${k(-0.50, 0.78)} ${k(-0.52, 0.66)} L${k(-0.69, -0.70)} Q${k(-0.70, -0.78)} ${k(-0.62, -0.78)} Z`
  const bars = [[-0.50, 0.16], [-0.20, 0.30], [0.10, 0.44]].map(([y, hw]) =>
    `M${k(-hw, y + 0.17)} L${k(hw, y + 0.17)} L${k(hw * 0.35, y)} L${k(-hw * 0.35, y)} Z`).join(' ')
  const trunk = `M${k(-0.06, 0.29)} L${k(0.06, 0.29)} L${k(0.06, 0.52)} L${k(-0.06, 0.52)} Z`
  return `<path d='${keystone}' fill='none' stroke='${stroke}' stroke-width='${0.075 * s}' stroke-linejoin='round'/>
    <path d='${bars} ${trunk}' fill='${fill}'/>`
}

// ---- C: an upright H whose crossbar runs out through a ring, like a road to the horizon
function ringH(cx, cy, s, fill) {
  const k = (x, y) => `${cx + x * s},${cy + y * s}`
  const ring = `<circle cx='${cx}' cy='${cy}' r='${0.72 * s}' fill='none' stroke='${fill}' stroke-width='${0.09 * s}'/>`
  const stems = [-1, 1].map(d => `M${k(d * 0.26 - 0.075, -0.50)} L${k(d * 0.26 + 0.075, -0.50)} L${k(d * 0.26 + 0.075, 0.50)} L${k(d * 0.26 - 0.075, 0.50)} Z`).join(' ')
  const bar = `M${k(-0.98, -0.045)} L${k(0.98, -0.045)} L${k(0.92, 0.055)} L${k(-0.92, 0.055)} Z`
  return `${ring}<path d='${stems} ${bar}' fill='${fill}'/>`
}

function panel(x, y, w, h, title, sub) {
  return `<rect x='${x}' y='${y}' width='${w}' height='${h}' rx='18' fill='#1d2024'/>
    <text x='${x + 34}' y='${y + 62}' font-family='Segoe UI, Arial' font-weight='700' font-size='40' fill='#eef0f2'>${title}</text>
    <text x='${x + 34}' y='${y + 104}' font-family='Segoe UI, Arial' font-size='26' fill='#9aa3ad'>${sub}</text>`
}

// a sketch of the T15 grille: chrome surround, dark grid, three chrome bars, the centre plate carrying the badge
function grille(x, y, w, h, badge) {
  const bars = [0.28, 0.52, 0.76].map(t => `<rect x='${x + w * 0.03}' y='${y + h * t - h * 0.04}' width='${w * 0.94}' height='${h * 0.08}' rx='${h * 0.04}' fill='url(#chrome)'/>`).join('')
  return `<rect x='${x}' y='${y}' width='${w}' height='${h}' rx='10' fill='url(#chrome)'/>
    <rect x='${x + w * 0.02}' y='${y + h * 0.06}' width='${w * 0.96}' height='${h * 0.88}' rx='6' fill='#15171a'/>${bars}${badge}`
}

function tailgate(x, y, w, h, letters) {
  return `<rect x='${x}' y='${y}' width='${w}' height='${h}' rx='8' fill='url(#paint)'/>
    <rect x='${x}' y='${y}' width='${w}' height='${h * 0.08}' fill='#00000033'/>${letters}`
}

const cols = [40, 820, 1600], pw = 760
let body = `<rect width='${W}' height='${H}' fill='#2a2e33'/>
  <text x='40' y='70' font-family='Segoe UI, Arial' font-weight='700' font-size='46' fill='#ffffff'>Halstead Motor Company · 车标概念 第 1 轮</text>
  <text x='40' y='112' font-family='Segoe UI, Arial' font-size='26' fill='#aab3bd'>镀铬车标 · 单色 · 装在 T15 上的样子（格栅中间的铭牌、尾门）。只是方向，选定后再细化</text>`

// A
body += panel(cols[0], 150, pw, 1310, 'A · HALSTEAD 字标', '加宽的冲压大写字；格栅铭牌 + 尾门压字')
body += `<g filter='url(#drop)'>${word(cols[0] + pw / 2, 420, 92, 'url(#chrome)')}</g>`
body += word(cols[0] + pw / 2, 560, 92, '#e9ecef')
body += grille(cols[0] + 60, 640, pw - 120, 300,
  `<rect x='${cols[0] + pw / 2 - 170}' y='${640 + 300 * 0.52 - 30}' width='340' height='60' rx='10' fill='#15171a'/>
   <g filter='url(#drop)'>${word(cols[0] + pw / 2, 640 + 300 * 0.52 + 16, 40, 'url(#chrome)')}</g>`)
body += tailgate(cols[0] + 60, 1020, pw - 120, 360, `<g>${word(cols[0] + pw / 2, 1222, 60, '#9a4038') + word(cols[0] + pw / 2, 1218, 60, '#4a1612')}</g>`)

// B
const bx = cols[1] + pw / 2
body += panel(cols[1], 150, pw, 1310, 'B · 三杠松树盾', '三根渐宽横杠叠成针叶树，呼应格栅的三根镀铬横杠')
body += `<g filter='url(#drop)'>${treeBadge(bx - 150, 420, 150, 'url(#chrome)', 'url(#chrome)')}</g>`
body += treeBadge(bx + 170, 420, 130, '#e9ecef', '#e9ecef')
body += grille(cols[1] + 60, 640, pw - 120, 300,
  `<rect x='${bx - 60}' y='${640 + 34}' width='120' height='232' rx='14' fill='#15171a'/>
   <g filter='url(#drop)'>${treeBadge(bx, 640 + 150, 88, 'url(#chrome)', 'url(#chrome)')}</g>`)
body += tailgate(cols[1] + 60, 1020, pw - 120, 360, `${word(bx, 1222, 60, '#9a4038') + word(bx, 1218, 60, '#4a1612')}`)
body += `<text x='${bx}' y='1410' font-family='Segoe UI, Arial' font-size='24' fill='#9aa3ad' text-anchor='middle'>尾门仍用 A 的字标</text>`

// C
const cx = cols[2] + pw / 2
body += panel(cols[2], 150, pw, 1310, 'C · 圆环 H', '直立的 H，横杠穿出圆环，像通到地平线的公路')
body += `<g filter='url(#drop)'>${ringH(cx - 160, 420, 150, 'url(#chrome)')}</g>`
body += ringH(cx + 180, 420, 120, '#e9ecef')
body += grille(cols[2] + 60, 640, pw - 120, 300,
  `<circle cx='${cx}' cy='${640 + 150}' r='96' fill='#15171a'/>
   <g filter='url(#drop)'>${ringH(cx, 640 + 150, 110, 'url(#chrome)')}</g>`)
body += tailgate(cols[2] + 60, 1020, pw - 120, 360, `${word(cx, 1222, 60, '#9a4038') + word(cx, 1218, 60, '#4a1612')}`)
body += `<text x='${cx}' y='1410' font-family='Segoe UI, Arial' font-size='24' fill='#9aa3ad' text-anchor='middle'>尾门仍用 A 的字标</text>`

const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='${W}' height='${H}'><defs>${defs}</defs>${body}</svg>`
await sharp(Buffer.from(svg)).png().toFile(OUT)
console.log('wrote ' + OUT)
