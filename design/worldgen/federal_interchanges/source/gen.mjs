// Naming draft sheets for C3.3b group 2 package B (route numbers, town names, shields). Concept only.
// node gen.mjs <outdir>; then sh render.sh <outdir>. Road geometry: the ic1 test world (seed -295378578869149513),
// read once from the C2 client's road network cache and kept as ic1_routes.json beside this script.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { f, pts, poly, polyline, line, text, mono, sign, sheet, lines } from './svg.mjs';
import { shield, cardinal, advanceSign, entranceSign, shieldWidth, NAVY, RED, GOLD, GREEN, INK } from './shields.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(process.argv[2] || path.join(HERE, '..', 'sheets'));
fs.mkdirSync(OUT, { recursive: true });
const NOTE = 'AFL C3.3b 第二组 B 包 · 命名草案 · 2026-10-17';

// ------------------------------------------------------------------ data
const CACHE = path.join(HERE, '../../../../build/federal-c2-client/afl_cache/road_network/afl_road_network_c3_v1_18151365494840402103_05c318c57fca5c3a.json');
const LOCAL = path.join(HERE, 'ic1_routes.json');
if (!fs.existsSync(LOCAL)) {
  const d = JSON.parse(fs.readFileSync(CACHE, 'utf8'));
  const routes = d.routes.map(r => ({ id: r.id, length: Math.round(r.length),
    control: r.control.filter((_, i, a) => i % 3 === 0 || i === a.length - 1).map(p => [Math.round(p[0]), Math.round(p[1])]) }));
  const nodes = d.nodes.filter(n => n.kind === 'CITY' || (n.kind === 'INTERCHANGE' && !n.route)).map(n => ({ id: n.id, x: Math.round(n.x), z: Math.round(n.z) }));
  fs.writeFileSync(LOCAL, JSON.stringify({ seed: '-295378578869149513', routes, nodes }));
}
const NET = JSON.parse(fs.readFileSync(LOCAL, 'utf8'));
const NODE = Object.fromEntries(NET.nodes.map(n => [n.id, n]));

const STATES = {
  S1: { zh: 'S1 东部森林河谷', tint: '#e2eed9', accent: '英式殖民老州' },
  S2: { zh: 'S2 农业平原', tint: '#f3ecd0', accent: '中西部农业州' },
  S3: { zh: 'S3 寒冷山地', tint: '#dde5ee', accent: '山区矿业' },
  S4: { zh: 'S4 工业湖区', tint: '#e7e3f0', accent: '五大湖工业' },
  S5: { zh: 'S5 南部湿地海岸', tint: '#d9ece8', accent: '法语湾区' },
  S6: { zh: 'S6 西部荒漠高原', tint: '#f3e1cf', accent: '西南西班牙语' },
};

const TOWNS = [
  ['river_city', 'S1', '城市 1 级', 'Bellhaven', '贝尔黑文', '河口大城；-haven 是港湾，东部老城常见的构词'],
  ['valley_town', 'S1', '镇 2 级', 'Linden', '林登', '椴树；开局河谷边的镇，两条高速在这里交会'],
  ['hill_village', 'S1', '村', 'Laurel Ridge', '月桂岭', '山月桂 + 山岭，东部丘陵的叫法'],
  ['county_seat', 'S2', '县城 2 级', 'Sutton', '萨顿', '平原县城多用开拓者的姓'],
  ['farm_town_north', 'S2', '农镇', 'Elmwood', '榆林镇', '平原上的防风榆树林'],
  ['farm_town_south', 'S2', '农镇', 'Redfield', '红田镇', '南边的红土麦田'],
  ['mining_town', 'S3', '矿镇', 'Cobalt', '科博尔特', '山区矿镇常拿矿种命名'],
  ['pass_village', 'S3', '村', 'Tamarack', '落叶松垭', '美洲落叶松；垭口上的村'],
  ['industrial_city', 'S4', '工业城 1 级', 'Kessler', '凯斯勒', '钢铁公司创办人的姓，典型的公司城'],
  ['port_town', 'S4', '港镇', 'Gull Harbor', '海鸥港', '北湾的湖港'],
  ['mill_town', 'S4', '村', 'Birch Mills', '桦木磨坊', '湖区锯木厂起家'],
  ['port_city', 'S5', '港城 2 级', 'Port Marais', '马雷港', '法语 marais = 沼泽，湾区法裔口音'],
  ['bayou_village', 'S5', '村', 'Bayou Noir', '黑水湾', '黑水河汊（bayou）'],
  ['shore_town', 'S5', '镇', 'Pelican Point', '鹈鹕角', '海岸沙嘴上的镇'],
  ['desert_town', 'S6', '镇 2 级', 'Mesa Roja', '梅萨罗哈', '西班牙语"红色台地"'],
  ['canyon_outpost', 'S6', '前哨', 'Fort Ocotillo', '奥科蒂约堡', '沙漠植物 ocotillo；Fort = 前哨'],
].map(([id, state, role, en, zh, why]) => ({ id, state, role, en, zh, why }));
const EN = Object.fromEntries(TOWNS.map(t => [t.id, t.en]));
const ZH = Object.fromEntries(TOWNS.map(t => [t.id, t.zh]));

// expressways (federal) and routes; label = [leg id, fraction along it]
const LINES = [
  { tier: 'fx', num: '40', ids: ['interstate_ew'], via: ['desert_town', 'county_seat', 'valley_town', 'river_city'], miles: 11.5, labels: [['interstate_ew:desert_town-county_seat', 0.25], ['interstate_ew:valley_town-river_city', 0.62]] },
  { tier: 'fx', num: '65', ids: ['interstate_ns'], via: ['port_city', 'valley_town', 'industrial_city'], miles: 6.9, labels: [['interstate_ns:valley_town-port_city', 0.45], ['interstate_ns:industrial_city-valley_town', 0.3]] },
  { tier: 'rt', num: '10', ids: ['sh_lakes'], via: ['mill_town', 'industrial_city', 'port_town'], labels: [['sh_lakes:mill_town-industrial_city', 0.5], ['sh_lakes:industrial_city-port_town', 0.72]] },
  { tier: 'rt', num: '30', ids: ['sh_mountain'], via: ['mining_town', 'pass_village', 'farm_town_north'], labels: [['sh_mountain:mining_town-pass_village', 0.55]] },
  { tier: 'rt', num: '31', ids: ['sh_hills'], via: ['valley_town', 'hill_village', 'port_town'], labels: [['sh_hills:valley_town-hill_village', 0.5]] },
  { tier: 'rt', num: '61', ids: ['sh_farm_north', 'sh_farm_belt'], via: ['mill_town', 'farm_town_north', 'county_seat', 'farm_town_south'], labels: [['sh_farm_north:farm_town_north-mill_town', 0.62], ['sh_farm_belt:county_seat-farm_town_south', 0.5]] },
  { tier: 'rt', num: '80', ids: ['sh_gulf'], via: ['farm_town_south', 'bayou_village', 'port_city', 'shore_town', 'river_city'], labels: [['sh_gulf:farm_town_south-bayou_village', 0.5], ['sh_gulf:bayou_village-port_city', 0.55]] },
  { tier: 'rt', num: '81', ids: ['sh_range_front', 'sh_canyon'], via: ['pass_village', 'desert_town', 'canyon_outpost'], labels: [['sh_canyon:desert_town-canyon_outpost', 0.45]] },
];

// the four diamonds built in ic1; mile = milepost from the west / south end (planned lengths, so approximate)
const EXITS = [
  { fx: '40', exit: '4', rt: '61', x: -2932, z: 1696, mile: 3.8, where: 'RT 61 南段' },
  { fx: '40', exit: '5', rt: '61', x: -1158, z: -244, mile: 5.4, where: 'RT 61 北段' },
  { fx: '65', exit: '1', rt: '80', x: 6320, z: 4079, mile: 0.9, where: 'RT 80' },
  { fx: '65', exit: '6', rt: '10', x: 768, z: -3628, mile: 5.5, where: 'RT 10（你看过的那座）' },
];

// ------------------------------------------------------------------ sheet 1: numbering on the ic1 map
function coast() {
  const P = 2.6, HX = 10000, HZ = 8000;
  const COAST = [[8, 0.085, 1700], [97, 0.13, 1300], [-68, 0.08, 1000], [-138, 0.035, 900], [172, 0.05, 550], [38, 0.07, -650], [-28, 0.06, -450]];
  const out = [];
  for (let i = 0; i < 720; i++) {
    const th = -Math.PI + i * Math.PI / 360, c = Math.cos(th), s = Math.sin(th);
    let r = 1 / Math.pow(Math.pow(Math.abs(c) / HX, P) + Math.pow(Math.abs(s) / HZ, P), 1 / P);
    for (const [deg, w, depth] of COAST) {
      let d = th - deg * Math.PI / 180;
      while (d > Math.PI) d -= 2 * Math.PI;
      while (d < -Math.PI) d += 2 * Math.PI;
      r -= depth * Math.exp(-(d / w) * (d / w));
    }
    out.push([r * c, r * s]);
  }
  return out;
}

const STATE_POLY = (() => {
  const B = 14000;
  return {
    S3: [[-B, -B], [-4700, -B], [-4700, -1500], [-B, -1500]],
    S6: [[-B, -1500], [-4700, -1500], [-4700, B], [-B, B]],
    S4: [[-4700, -B], [B, -B], [B, -4600], [3000, -4600], [3000, -3300], [-4700, -3300]],
    S2: [[-4700, -3300], [-900, -3300], [-900, B], [-4700, B]],
    S1: [[-900, -3300], [3000, -3300], [3000, -4600], [B, -4600], [B, 1700], [7360, 1808], [4664, 2071], [2000, 2150], [-900, 1900]],
    S5: [[-900, 1900], [2000, 2150], [4664, 2071], [7360, 1808], [B, 1700], [B, B], [-900, B]],
  };
})();
const STATE_LABEL = { S1: [5600, -1500], S2: [-3500, 3300], S3: [-8300, -2600], S4: [-1700, -4300], S5: [3600, 3300], S6: [-8600, 1300] };

const TOWN_LABEL = { // [anchor, dx, dy]
  river_city: ['middle', 0, -34], valley_town: ['end', -6, 30], hill_village: ['start', 12, -4], county_seat: ['start', 12, 26],
  farm_town_north: ['end', -12, -8], farm_town_south: ['end', -12, -6], mining_town: ['start', 12, -14], pass_village: ['end', -12, -10],
  industrial_city: ['start', 14, -6], port_town: ['end', -12, -12], mill_town: ['end', -12, 22], port_city: ['start', 12, 22],
  bayou_village: ['middle', 0, 26], shore_town: ['start', 14, 4], desert_town: ['end', -12, -6], canyon_outpost: ['end', -12, -4],
};
const EXIT_LABEL = { 4: ['end', -14, 24], 5: ['start', 14, 22], 1: ['start', 16, 6], 6: ['end', -14, 5] };

function along(control, t) {
  let L = 0; const seg = [];
  for (let i = 1; i < control.length; i++) { const d = Math.hypot(control[i][0] - control[i - 1][0], control[i][1] - control[i - 1][1]); seg.push(d); L += d; }
  let want = t * L;
  for (let i = 0; i < seg.length; i++) {
    if (want <= seg[i]) { const u = want / seg[i]; return [control[i][0] + u * (control[i + 1][0] - control[i][0]), control[i][1] + u * (control[i + 1][1] - control[i][1])]; }
    want -= seg[i];
  }
  return control[control.length - 1];
}

/** Neutral map tag (the shield design is open): navy F-nn, white RT nn. */
function tag(x, y, tier, num) {
  const label = tier === 'fx' ? 'F-' + num : 'RT ' + num;
  const w = 10 + label.length * 8.6;
  return tier === 'fx'
    ? `<rect x="${f(x - w / 2)}" y="${f(y - 11)}" width="${f(w)}" height="22" rx="4" fill="${NAVY}" stroke="#fff" stroke-width="1.5"/>` + sign(x, y + 6, label, 'font-size="16" fill="#fff" text-anchor="middle"')
    : `<rect x="${f(x - w / 2)}" y="${f(y - 10)}" width="${f(w)}" height="20" rx="4" fill="#fff" stroke="${INK}" stroke-width="1.5"/>` + sign(x, y + 5.5, label, `font-size="15" fill="${INK}" text-anchor="middle"`);
}

function sheetNumbering() {
  const S = 0.056, X0 = 40, Y0 = 70;
  const P = ([x, z]) => [X0 + (x + 10600) * S, Y0 + (z + 8500) * S];
  const land = coast().map(P);
  let b = `<defs><clipPath id="land">${poly(land, '')}</clipPath></defs>`;
  b += text(40, 52, 'ic1 测试世界（种子 −295378578869149513）的国道网 · 州界为示意', 'font-size="20" font-weight="700" fill="#23282b"');
  b += `<rect x="${X0}" y="${Y0}" width="${f(21200 * S)}" height="${f(17000 * S)}" fill="#dfe9ee"/>`;
  b += poly(land, 'fill="#f2f3ee"');
  for (const [k, pl] of Object.entries(STATE_POLY)) b += poly(pl.map(P), `fill="${STATES[k].tint}" clip-path="url(#land)"`);
  for (const [k, pl] of Object.entries(STATE_POLY)) b += poly(pl.map(P), 'fill="none" stroke="#8d9595" stroke-width="1.2" stroke-dasharray="7 5" clip-path="url(#land)"');
  b += poly(land, 'fill="none" stroke="#6f8590" stroke-width="1.6"');
  for (const [k, p] of Object.entries(STATE_LABEL)) b += text(...P(p), STATES[k].zh, 'font-size="17" font-weight="700" fill="#7d8687" text-anchor="middle" opacity="0.9"');
  const byId = Object.fromEntries(NET.routes.map(r => [r.id, r]));
  // routes first, expressways on top
  for (const tier of ['rt', 'fx']) for (const ln of LINES.filter(l => l.tier === tier)) {
    for (const r of NET.routes.filter(r => ln.ids.includes(r.id.split(':')[0]))) {
      const p = r.control.map(P);
      b += tier === 'fx' ? polyline(p, 'stroke="#fff" stroke-width="7.5" stroke-linejoin="round"') + polyline(p, `stroke="${NAVY}" stroke-width="4.2" stroke-linejoin="round"`)
        : polyline(p, 'stroke="#fff" stroke-width="5" stroke-linejoin="round"') + polyline(p, 'stroke="#6a5a4c" stroke-width="2.4" stroke-linejoin="round"');
    }
  }
  for (const t of TOWNS) {
    const n = NODE[t.id]; const [x, y] = P([n.x, n.z]);
    const [a, dx, dy] = TOWN_LABEL[t.id];
    b += `<circle cx="${f(x)}" cy="${f(y)}" r="6" fill="#fff" stroke="#23282b" stroke-width="2.2"/>`;
    b += text(x + dx, y + dy, t.en, `font-size="15.5" font-weight="700" fill="#23282b" text-anchor="${a}"`);
    b += text(x + dx, y + dy + 15, t.zh, `font-size="12" fill="#5d6669" text-anchor="${a}"`);
  }
  for (const ln of LINES) for (const [leg, t] of ln.labels) b += tag(...P(along(byId[leg].control, t)), ln.tier, ln.num);
  for (const e of EXITS) {
    const [x, y] = P([e.x, e.z]);
    b += `<circle cx="${f(x)}" cy="${f(y)}" r="9" fill="${RED}" stroke="#fff" stroke-width="2"/>`;
    const [a, dx, dy] = EXIT_LABEL[e.exit];
    b += mono(x + dx, y + dy, `EXIT ${e.exit}`, `font-size="14" font-weight="700" fill="${RED}" text-anchor="${a}" stroke="#fbfbf8" stroke-width="3" paint-order="stroke"`);
  }
  // legend
  const ly = Y0 + 17000 * S - 92;
  b += `<rect x="${X0 + 12}" y="${ly}" width="330" height="80" rx="4" fill="#fff" stroke="#9aa3a6" opacity="0.94"/>`;
  b += polyline([[X0 + 26, ly + 20], [X0 + 70, ly + 20]], `stroke="${NAVY}" stroke-width="4.2"`) + text(X0 + 80, ly + 25, '联邦高速（现在的州际）', 'font-size="13.5" fill="#23282b"');
  b += polyline([[X0 + 26, ly + 42], [X0 + 70, ly + 42]], 'stroke="#6a5a4c" stroke-width="2.4"') + text(X0 + 80, ly + 47, '联邦干线（现在的州道）', 'font-size="13.5" fill="#23282b"');
  b += `<circle cx="${X0 + 48}" cy="${ly + 64}" r="7" fill="${RED}" stroke="#fff" stroke-width="2"/>` + text(X0 + 80, ly + 69, 'ic1 里建成的菱形立交和出口号', 'font-size="13.5" fill="#23282b"');

  // rules panel
  const RX = X0 + 21200 * S + 34; let y = 52;
  const H1 = 'font-size="19" font-weight="700" fill="#23282b"', H2 = 'font-size="16" font-weight="700" fill="#8e3a32"';
  const T = 'font-size="14.5" fill="#23282b"', G = 'font-size="13" fill="#5d6669"';
  let r = lines(RX, y, [
    ['编号规则（草案，按我的默认）', H1, 34],
    ['1　两级路网', H2, 26],
    ['· 联邦高速 FEDERAL：现在的州际（双向四车道、全立交），写 F-40', T],
    ['· 联邦干线 ROUTE：现在的州道（R2，连城镇），写 RT 10；', T],
    ['　号码跨州不变，地位像美国的 US Route', T],
    ['· 代码和文档里"州际 / 州道"的叫法不改，只是牌面名字', G],
    ['· 真正的州内道路、县道（C3.3c 起）到时再定州号和州名', G, 34],
    ['2　号码怎么排', H2, 26],
    ['· 东西向用偶数，南北向用奇数', T],
    ['· 高速：东西向从南往北变大，南北向从西往东变大，主干尾数 0 / 5', T],
    ['· 干线：方向反过来（东西向从北往南、南北向从东往西变大），', T],
    ['　主干尾数 0 / 1；同一地区两级的号码不会撞', T],
    ['· 一条连续的线一个号：sh_farm_north + sh_farm_belt = RT 61，', T],
    ['　sh_range_front + sh_canyon = RT 81', T, 34],
    ['3　出口编号（已定：英里、里程桩）', H2, 26],
    ['· 从西端 / 南端起算的英里数，四舍五入', T],
    ['· 同一个整英里里有两座立交时，按里程增加方向加 A / B', T, 34],
  ]);
  b += r.svg; y = r.y;
  // number table: number, then the towns it runs through and the current corridor id under them
  b += text(RX, y, '号', H2) + text(RX + 72, y, '经过（西→东 / 南→北）和现在的线', H2); y += 8;
  b += line([RX, y], [RX + 600, y], 'stroke="#9aa3a6" stroke-width="1"'); y += 22;
  for (const ln of LINES) {
    b += mono(RX, y + 6, (ln.tier === 'fx' ? 'F-' : 'RT ') + ln.num, `font-size="15" font-weight="700" fill="${ln.tier === 'fx' ? NAVY : '#5a4636'}"`);
    b += text(RX + 72, y, ln.via.map(v => EN[v]).join(' – '), 'font-size="13.5" fill="#23282b"');
    b += mono(RX + 72, y + 16, ln.ids.join(' + ') + (ln.miles ? `   约 ${ln.miles} 英里` : ''), 'font-size="11.5" fill="#6b7376"');
    y += 38;
  }
  y += 14;
  b += text(RX, y, 'ic1 里 4 座立交的出口号（估算）', H2); y += 26;
  for (const e of EXITS) {
    b += mono(RX, y, `F-${e.fx}  EXIT ${e.exit}`, `font-size="14.5" font-weight="700" fill="${NAVY}"`);
    b += text(RX + 160, y, `${e.where}，${e.mile} 英里　(${e.x} ${e.z})`, 'font-size="13.5" fill="#23282b"');
    y += 24;
  }
  r = lines(RX, y + 6, [
    ['· 里程按规划长度估算；B 包按建成的路算，EXIT 6 在 5.5 英里附近，可能变成 5', G],
    ['· F-40 在 EXIT 4 和 5 之间同时是 RT 61（并线，牌上两个盾牌都挂）', G],
  ], 20);
  b += r.svg;
  return sheet(1900, 1120, '编号规则和 ic1 路网', 'N1', b, NOTE);
}

// ------------------------------------------------------------------ sheet 2: town names
function sheetTowns() {
  let b = text(40, 52, '16 个城镇的名字（草案）：牌上用英文名，游戏界面中文用中文名', 'font-size="20" font-weight="700" fill="#23282b"');
  b += text(40, 80, '按各州的开发史分口音：东部英式老州、平原中西部、山区矿镇、五大湖工业、南部法语湾区、西南西班牙语；构词照北美常见的 -haven、-field、Fort、Bayou、Mesa，不用真实大城市的名字。', 'font-size="14.5" fill="#5d6669"');
  const X = [40, 250, 470, 600, 900, 1060];
  let y = 122;
  const H = 'font-size="15" font-weight="700" fill="#8e3a32"';
  b += text(X[0], y, '州', H) + text(X[1], y, '内部 id', H) + text(X[2], y, '类型', H) + text(X[3], y, '牌面效果（英文）', H) + text(X[4], y, '中文名', H) + text(X[5], y, '由来', H);
  y += 10; b += line([40, y], [1860, y], 'stroke="#23282b" stroke-width="1.5"');
  let prev = null;
  const RH = 54;
  for (const t of TOWNS) {
    const top = y;
    if (t.state !== prev) {
      const n = TOWNS.filter(o => o.state === t.state).length;
      b += `<rect x="40" y="${top}" width="1820" height="${n * RH}" fill="${STATES[t.state].tint}" opacity="0.55"/>`;
      b += text(X[0] + 6, top + 30, STATES[t.state].zh, 'font-size="15" font-weight="700" fill="#23282b"');
      b += text(X[0] + 6, top + 50, STATES[t.state].accent, 'font-size="12.5" fill="#5d6669"');
      if (prev) b += line([40, top], [1860, top], 'stroke="#9aa3a6" stroke-width="1"');
      prev = t.state;
    }
    const cy = top + RH / 2;
    b += mono(X[1], cy + 5, t.id, 'font-size="13.5" fill="#5d6669"');
    b += text(X[2], cy + 5, t.role, 'font-size="14" fill="#23282b"');
    const w = 30 + t.en.length * 15.5;
    b += `<rect x="${X[3]}" y="${cy - 19}" width="${f(w)}" height="38" rx="5" fill="${GREEN}"/><rect x="${X[3] + 3}" y="${cy - 16}" width="${f(w - 6)}" height="32" rx="3.5" fill="none" stroke="#fff" stroke-width="1.6"/>`;
    b += sign(X[3] + w / 2, cy + 9, t.en, 'font-size="26" fill="#fff" text-anchor="middle"');
    b += text(X[4], cy + 6, t.zh, 'font-size="17" font-weight="700" fill="#23282b"');
    b += text(X[5], cy + 5, t.why, 'font-size="14" fill="#23282b"');
    y += RH;
  }
  b += line([40, y], [1860, y], 'stroke="#23282b" stroke-width="1.5"');
  const r = lines(40, y + 34, [
    ['· 州名这次没有定：现在用不到（干线号码跨州不变），等州界欢迎牌、州内道路时再定。', 'font-size="14" fill="#5d6669"'],
    ['· 计划：B 包的牌面文字按一张名字表生成，定了以后再改名只改这张表。', 'font-size="14" fill="#5d6669"'],
  ]);
  b += r.svg;
  return sheet(1900, 1150, '城镇名', 'N2', b, NOTE);
}

// ------------------------------------------------------------------ sheet 3: shields
const FAMILIES = [
  { k: 'A', name: 'A　联邦盾徽', desc: ['尖底盾形，蓝底红顶写 FEDERAL；', '从之前设计文档的占位稿发展来，最像传统北美路盾。', '干线：同形白底黑字。'] },
  { k: 'B', name: 'B　三星拱牌', desc: ['圆拱顶，上部蓝底三颗星、红线，下部白底黑字；', '颜色最少，远处最好认，联邦味道靠三颗星。', '干线：同形白底黑字，没有星。'] },
  { k: 'C', name: 'C　六角铭牌', desc: ['尖角六边形，深蓝底金顶写 FEDERAL；', '形状和美国的路盾都不一样，最有自己的识别度。', '干线：同形白底黑字。'] },
];

function sheetShields() {
  let b = text(40, 52, '路线盾牌三套（挑一套；每套有高速和干线两级）', 'font-size="20" font-weight="700" fill="#23282b"');
  const CW = 606;
  FAMILIES.forEach((fam, i) => {
    const x0 = 34 + i * CW, cx = x0 + CW / 2;
    if (i) b += line([x0, 76], [x0, 1050], 'stroke="#c9cfd1" stroke-width="1.2"');
    b += text(x0 + 24, 104, fam.name, 'font-size="22" font-weight="700" fill="#23282b"');
    const r = lines(x0 + 24, 132, fam.desc.map(d => [d, 'font-size="14" fill="#5d6669"']), 21);
    b += r.svg;
    // big shields: expressway, route
    const H = 150;
    b += shield(fam.k, 'fx', '65', x0 + 130, 214, H) + text(x0 + 130, 214 + H + 28, '联邦高速 F-65', 'font-size="14" fill="#23282b" text-anchor="middle"');
    b += shield(fam.k, 'rt', '10', x0 + 300, 214 + 12, H - 24) + text(x0 + 300, 214 + H + 28, '联邦干线 RT 10', 'font-size="14" fill="#23282b" text-anchor="middle"');
    // reassurance assembly on a post
    const ax = x0 + 480, ah = 96;
    b += `<rect x="${ax - 4}" y="236" width="8" height="132" fill="#8b9194"/>`;
    b += cardinal(ax, 204, shieldWidth(fam.k, ah) * 1.02, 'NORTH') + shield(fam.k, 'fx', '65', ax, 204 + shieldWidth(fam.k, ah) * 0.38, ah);
    b += text(ax, 214 + H + 28, '路边确认牌', 'font-size="14" fill="#23282b" text-anchor="middle"');
    // guide signs in context
    b += text(x0 + 24, 440, '指路牌里的样子（F-65 北行，出口前 1 英里）', 'font-size="15" font-weight="700" fill="#8e3a32"');
    b += advanceSign(fam.k, x0 + 50, 458, 104, { exit: '6', route: '10', dir: 'EAST', city: 'Gull Harbor', dist: '1 MILE' });
    b += text(x0 + 24, 830, '州道上的入口指示（RT 10 上，入口匝道前）', 'font-size="15" font-weight="700" fill="#8e3a32"');
    b += entranceSign(fam.k, x0 + 110, 848, 72, { fx: '65', dir: 'SOUTH', city: 'Linden' });
  });
  return sheet(1900, 1120, '路线盾牌三套', 'N3', b, NOTE + ' · 字体是 Windows 的 Bahnschrift，只是示意；游戏里用路牌自己的字形');
}

fs.writeFileSync(path.join(OUT, 'n1_numbering.svg'), sheetNumbering());
fs.writeFileSync(path.join(OUT, 'n2_town_names.svg'), sheetTowns());
fs.writeFileSync(path.join(OUT, 'n3_shields.svg'), sheetShields());
console.log('wrote 3 sheets to', OUT);
