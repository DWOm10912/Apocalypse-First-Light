// AFL Workbench crafting UI V1 concept sheets (2026-10-21): version A (icon grid, after Abiotic Factor's bench), version B
// (text list, after Enshrouded's crafting menu; user pick 2026-10-21, no separate knowledge tab). Layout only: the items,
// numbers and times are placeholders. Colours from docs/ui/afl_overlay_ui_style_v1.md 3.2.
//   node design/ui/afl_workbench_ui_v1/wb_ui_sheet.cjs
const fs = require('fs');
const path = require('path');
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');

const ROOT = path.resolve(__dirname, '../../..');
const MAIN = 'D:/Minecraft Modding/Apocalypse First Light';
const OUT = __dirname;
const TEX = ROOT + '/src/main/resources/assets/apocalypse_firstlight/textures';
const W = 2400, H = 1080, MW = 1920;   // the mock screen, then the notes column

const C = {
  text: '#E8E4DC', sub: '#A8A49C', dis: '#6C6A66', amber: '#E0A84A', bad: '#D0785A', ok: '#9CC08A',
  base: 'rgba(16,16,16,0.62)', hover: 'rgba(30,33,36,0.72)', line: 'rgba(232,228,220,0.28)', lineHi: 'rgba(232,228,220,0.55)',
};
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

async function uri(file, size, opts = {}) {
  let img = sharp(file);
  if (opts.extract) img = img.extract(opts.extract);
  if (size) img = img.resize(size, size, {fit: 'contain', background: {r: 0, g: 0, b: 0, alpha: 0}, kernel: opts.nearest ? 'nearest' : 'lanczos3'});
  const buf = await img.png().toBuffer();
  return 'data:image/png;base64,' + buf.toString('base64');
}

// ------------------------------------------------------------------------------------------------ drawing helpers
class Svg {
  constructor() { this.s = []; }
  add(x) { this.s.push(x); return this; }
  rect(x, y, w, h, o = {}) {
    return this.add(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.r ?? 8}" fill="${o.fill ?? C.base}" stroke="${o.stroke ?? C.line}" stroke-width="${o.sw ?? 1.5}"${o.dash ? ` stroke-dasharray="${o.dash}"` : ''}/>`);
  }
  panel(x, y, w, h, r = 10) {
    this.add(`<rect x="${x - 4}" y="${y - 2}" width="${w + 8}" height="${h + 10}" rx="${r + 4}" fill="rgba(0,0,0,0.22)" filter="url(#soft)"/>`);
    return this.rect(x, y, w, h, {r});
  }
  text(x, y, s, size = 22, o = {}) {
    return this.add(`<text x="${x}" y="${y}" font-size="${size}" fill="${o.fill ?? C.text}"${o.bold ? ' font-weight="bold"' : ''}${o.anchor ? ` text-anchor="${o.anchor}"` : ''}${o.op ? ` opacity="${o.op}"` : ''}>${esc(s)}</text>`);
  }
  img(x, y, w, h, href, op = 1) { return this.add(`<image x="${x}" y="${y}" width="${w}" height="${h}" href="${href}" opacity="${op}" preserveAspectRatio="xMidYMid meet"/>`); }
  line(x1, y1, x2, y2, stroke = C.line, sw = 1.5) { return this.add(`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="${sw}"/>`); }
  key(x, y, label, size = 18) {   // a keycap: solid light rounded block, dark text
    const w = Math.max(30, [...label].reduce((a, ch) => a + (ch.charCodeAt(0) > 255 ? size * 1.02 : ch === ' ' ? size * 0.3 : size * 0.62), 0) + 16);
    this.rect(x, y - size - 4, w, size + 12, {r: 5, fill: 'rgba(232,228,220,0.92)', stroke: 'none'});
    this.text(x + w / 2, y + 2, label, size, {fill: '#16181A', bold: true, anchor: 'middle'});
    return w;
  }
  // a slot with an icon (or a placeholder tile), its state drawn on the frame
  slot(x, y, s, icon, o = {}) {
    this.rect(x, y, s, s, {r: 6, fill: o.sel ? C.hover : C.base, stroke: o.sel ? C.lineHi : C.line});
    if (icon) this.img(x + s * 0.1, y + s * 0.1, s * 0.8, s * 0.8, icon, o.dim ? 0.35 : 1);
    if (o.sel) this.add(`<rect x="${x}" y="${y + s - 4}" width="${s}" height="4" rx="2" fill="${C.amber}"/>`);
    if (o.missing) this.add(`<circle cx="${x + s - 10}" cy="${y + 10}" r="5" fill="${C.bad}"/>`);
    if (o.isNew) this.add(`<circle cx="${x + 10}" cy="${y + 10}" r="5" fill="${C.amber}"/>`);
    return this;
  }
  bar(x, y, w, h, f, col = C.amber) {
    this.rect(x, y, w, h, {r: h / 2, fill: 'rgba(232,228,220,0.12)', stroke: 'none'});
    return this.add(`<rect x="${x}" y="${y}" width="${Math.max(h, w * f)}" height="${h}" rx="${h / 2}" fill="${col}"/>`);
  }
  wrap(x, y, s, size, width, o = {}) {   // naive CJK wrap by character count
    const per = Math.floor(width / (size * 1.0));
    const lines = [];
    let rest = s;
    while (rest.length > per) { let n = per; while ('，。；、）”：'.includes(rest[n])) n++; lines.push(rest.slice(0, n)); rest = rest.slice(n); }
    if (rest) lines.push(rest);
    lines.forEach((l, i) => this.text(x, y + i * size * 1.5, l, size, o));
    return y + lines.length * size * 1.5;
  }
  out() { return this.s.join('\n'); }
}

function frame(bg, title, subtitle) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
<defs><filter id="soft" x="-5%" y="-5%" width="110%" height="120%"><feGaussianBlur stdDeviation="6"/></filter></defs>
<style>text{font-family:'Microsoft YaHei','微软雅黑',sans-serif}</style>
<rect width="${W}" height="${H}" fill="#202224"/>
<image x="0" y="0" width="${MW}" height="${H}" href="${bg}" preserveAspectRatio="xMidYMid slice"/>
<rect x="0" y="0" width="${MW}" height="${H}" fill="rgba(8,9,10,0.45)"/>
<rect x="${MW}" y="0" width="${W - MW}" height="${H}" fill="#26282a"/>
<text x="${MW + 36}" y="64" font-size="34" font-weight="bold" fill="${C.text}">${esc(title)}</text>
`;
}

function notes(svg, list, y0 = 150, subtitle = '') {
  let y = y0;
  if (subtitle) y = svg.wrap(MW + 36, 104, subtitle, 19, W - MW - 70, {fill: C.sub}) + 30;
  for (const [k, v] of list) {
    svg.text(MW + 36, y, k, 22, {fill: '#7fd0ff', bold: true});
    y = svg.wrap(MW + 36, y + 32, v, 19, W - MW - 70, {fill: C.text}) + 18;
  }
}

// shared header: title, tabs, close
function header(svg, tabs, active, o = {}) {
  svg.text(90, 84, '工作台', 34, {bold: true});
  svg.text(210, 84, '胡桃木工作台 · 溪谷老宅', 20, {fill: C.sub});
  const cx = MW / 2;
  let x = cx - tabs.length * 75;
  svg.key(x - 54, 84, 'Q');
  tabs.forEach((t, i) => {
    const tx = x + i * 150 + 75;
    svg.text(tx, 84, t, 24, {anchor: 'middle', fill: i === active ? C.text : C.sub, bold: i === active});
    if (i === active) svg.add(`<rect x="${tx - 34}" y="98" width="68" height="4" rx="2" fill="${C.amber}"/>`);
  });
  svg.key(x + tabs.length * 150 + 20, 84, 'E');
  svg.text(MW - 90, 84, '关闭', 20, {fill: C.sub, anchor: 'end'});
  svg.key(MW - 90 - 50 - 62, 84, 'Esc');
  if (o.line !== false) svg.line(70, 120, MW - 70, 120, C.line, 1);
}

// --------------------------------------------------------------------------------------------------- the sheets
async function main() {
  const bg = await sharp(MAIN + '/wiki/public/img/hero_forest.webp').resize(MW, H, {fit: 'cover'}).blur(14).modulate({brightness: 0.75})
    .png().toBuffer().then(b => 'data:image/png;base64,' + b.toString('base64'));
  const I = {
    hammer: await uri(TEX + '/item/claw_hammer.png', 256),
    axe: await uri(TEX + '/item/felling_axe.png', 256),
    crowbar: await uri(TEX + '/item/crowbar.png', 256),
    splint: await uri(MAIN + '/wiki/public/img/icons/splint.webp', 256),
    bandage: await uri(MAIN + '/wiki/public/img/icons/bandage.webp', 256),
    alcohol: await uri(MAIN + '/wiki/public/img/icons/medical_alcohol.webp', 256),
    muffs: await uri(MAIN + '/wiki/public/img/icons/simple_hearing_protection.webp', 256),
    lighter: await uri(MAIN + '/wiki/public/img/icons/flip_lighter.webp', 256),
  };
  const wood = {};
  for (const k of ['shagbark_hickory', 'eastern_hemlock', 'white_oak', 'red_maple', 'white_pine', 'red_oak'])
    wood[k] = await uri(TEX + `/block/wood/${k}_planks.png`, 96);
  const end = await uri(TEX + '/block/wood/white_oak_timber_end.png', 96);

  // ============================================================ version A: icon grid (Abiotic Factor's bench)
  {
    const svg = new Svg();
    svg.add(frame(bg, 'A 版 · 图标网格', '参考《非生物因素》工作台：左边分类竖条，中间图标格，右边成品详情和制作'));
    header(svg, ['背包', '制作'], 1);
    // category rail
    svg.panel(70, 140, 110, 880);
    const cats = [['全部', null, 21], ['木工', wood.white_oak, 4], ['家具', wood.red_oak, 3], ['工具', I.hammer, 4], ['建造', wood.eastern_hemlock, 2],
      ['生活用品', I.splint, 4], ['修补', I.bandage, 2], ['拆解', I.crowbar, 2]];
    cats.forEach(([n, ic, k], i) => {
      const y = 158 + i * 106, sel = n === '工具';
      if (sel) { svg.rect(80, y - 6, 90, 98, {r: 8, fill: C.hover, stroke: C.lineHi}); svg.add(`<rect x="72" y="${y + 8}" width="4" height="70" rx="2" fill="${C.amber}"/>`); }
      if (ic) svg.img(101, y, 48, 48, ic, k ? 1 : 0.3);
      else svg.text(125, y + 36, '▦', 34, {anchor: 'middle', fill: C.sub});
      svg.text(125, y + 74, n, 17, {anchor: 'middle', fill: k ? (sel ? C.text : C.sub) : C.dis});
      if (k) svg.text(160, y + 14, String(k), 14, {anchor: 'end', fill: C.dis});
    });
    // recipe grid
    svg.panel(200, 140, 440, 880);
    svg.rect(218, 158, 404, 44, {r: 6, fill: 'rgba(0,0,0,0.25)'});
    svg.text(236, 188, '搜索……', 19, {fill: C.dis});
    svg.rect(220, 222, 18, 18, {r: 4, fill: C.amber, stroke: 'none'}); svg.text(229, 237, '✓', 15, {anchor: 'middle', fill: '#16181A', bold: true});
    svg.text(248, 238, '只显示现在能做的', 17, {fill: C.sub});
    svg.text(220, 290, '工具', 26, {bold: true}); svg.text(282, 290, '会做 4 个', 18, {fill: C.sub});
    const grid = [[I.hammer, {sel: true}], [I.axe, {}], [wood.shagbark_hickory, {missing: true}], [end, {isNew: true}]];
    grid.forEach(([ic, o], i) => svg.slot(220 + (i % 5) * 82, 312 + Math.floor(i / 5) * 82, 74, ic, o));
    svg.rect(220, 412, 400, 90, {r: 8, fill: 'none', stroke: C.line, dash: '6 6'});
    svg.text(420, 450, '这一类还有没学会的东西', 18, {anchor: 'middle', fill: C.sub});
    svg.text(420, 478, '书籍和图纸能教会更多配方', 16, {anchor: 'middle', fill: C.dis});
    svg.text(220, 556, '图例', 18, {fill: C.sub});
    svg.add(`<circle cx="228" cy="590" r="5" fill="${C.bad}"/>`); svg.text(244, 596, '缺材料或缺工具（还能点开看）', 16, {fill: C.sub});
    svg.add(`<circle cx="228" cy="622" r="5" fill="${C.amber}"/>`); svg.text(244, 628, '刚学会，没看过', 16, {fill: C.sub});
    svg.text(220, 680, '鼠标移到格子上：名字 + 缺什么', 16, {fill: C.dis});

    // detail
    svg.panel(660, 140, 720, 880);
    svg.add(`<radialGradient id="g1"><stop offset="0" stop-color="rgba(232,228,220,0.10)"/><stop offset="1" stop-color="rgba(232,228,220,0)"/></radialGradient><ellipse cx="1020" cy="330" rx="300" ry="170" fill="url(#g1)"/>`);
    svg.add(`<g transform="rotate(-8 1020 330)">`); svg.img(860, 170, 320, 320, I.hammer); svg.add('</g>');
    svg.text(690, 196, '羊角锤', 38, {bold: true});
    svg.text(690, 230, '工具 · 组装', 18, {fill: C.sub});
    svg.text(1350, 196, '拖动可以转着看', 15, {fill: C.dis, anchor: 'end'});
    svg.wrap(690, 520, '16 盎司弯爪锤。旧锤头还能用，换一根山核桃木柄、打进木楔就行。', 19, 640, {fill: C.text});
    svg.text(690, 578, '重量 0.68 kg   ·   背包 1 × 2   ·   用时 30 秒   ·   来自：林区生活经验', 17, {fill: C.sub});
    svg.line(680, 600, 1360, 600);
    svg.text(690, 636, '材料', 22, {bold: true}); svg.text(1350, 636, '有 / 要', 18, {fill: C.sub, anchor: 'end'});
    const mats = [[I.hammer, '旧锤头', '工作台抽屉', '1 / 1'], [wood.shagbark_hickory, '山核桃木锤柄', '背包', '2 / 1'], [end, '木楔', '附近木箱', '6 / 1']];
    mats.forEach(([ic, n, src, k], i) => {
      const y = 652 + i * 66;
      svg.slot(690, y, 56, ic);
      svg.text(762, y + 26, n, 20); svg.text(762, y + 50, src, 15, {fill: C.sub});
      svg.text(1350, y + 36, k, 20, {anchor: 'end', fill: C.ok});
    });
    svg.text(690, 878, '工具（挂在工作台上的）', 22, {bold: true});
    const pegs = [['手锯', true, true], ['羊角锤', true, false], ['', false, false], ['', false, false], ['伐木斧', true, false]];
    pegs.forEach(([n, has, need], i) => {
      const x = 690 + i * 104;
      svg.rect(x, 896, 92, 92, {r: 8, fill: need ? C.hover : C.base, stroke: need ? C.lineHi : C.line, dash: has ? null : '5 5'});
      if (n === '羊角锤') svg.img(x + 14, 902, 64, 64, I.hammer, 0.4);
      if (n === '伐木斧') svg.img(x + 14, 902, 64, 64, I.axe, 0.4);
      if (n === '手锯') { svg.text(x + 46, 946, '🪚', 34, {anchor: 'middle'}); }
      svg.text(x + 46, 982, n || '空', 15, {anchor: 'middle', fill: need ? C.text : C.dis});
      if (need) svg.text(x + 82, 916, '✓', 18, {anchor: 'end', fill: C.ok, bold: true});
      svg.text(x + 10, 914, String(i + 1), 13, {fill: C.dis});
    });
    svg.text(1222, 932, '台钳 ✓', 18, {fill: C.text}); svg.text(1222, 958, '工作台自带', 15, {fill: C.sub});

    // make + sources
    svg.panel(1400, 140, 450, 420);
    svg.text(1424, 190, '制作', 24, {bold: true});
    svg.rect(1424, 214, 54, 54, {r: 8}); svg.text(1451, 250, '−', 30, {anchor: 'middle'});
    svg.rect(1488, 214, 120, 54, {r: 8, fill: 'rgba(0,0,0,0.25)'}); svg.text(1548, 252, '1', 28, {anchor: 'middle', bold: true});
    svg.rect(1618, 214, 54, 54, {r: 8}); svg.text(1645, 250, '+', 30, {anchor: 'middle'});
    svg.rect(1684, 214, 142, 54, {r: 8}); svg.text(1755, 248, '最多 1', 20, {anchor: 'middle', fill: C.sub});
    svg.rect(1424, 290, 402, 76, {r: 10, fill: 'rgba(224,168,74,0.18)', stroke: C.amber, sw: 2});
    svg.text(1625, 340, '正在制作  12 / 30 秒', 26, {anchor: 'middle', bold: true});
    svg.bar(1424, 380, 402, 10, 0.4);
    svg.wrap(1424, 430, '人要站在台前。走开或关掉界面就停下，做完一件才扣这一件的材料。', 17, 400, {fill: C.sub});
    svg.text(1424, 530, '再按一次「制作」取消', 16, {fill: C.dis});

    svg.panel(1400, 580, 450, 440);
    svg.text(1424, 628, '材料从哪里拿', 22, {bold: true});
    const srcs = [['1', '工作台抽屉', '左右各 9 格 · 用了 11 格'], ['2', '身上', '背包、胸挂、口袋'], ['3', '附近的箱子', '4 格内 · 2 个木箱、1 个工具箱']];
    srcs.forEach(([n, a, b], i) => {
      const y = 660 + i * 84;
      svg.rect(1424, y, 402, 70, {r: 8});
      svg.text(1446, y + 44, n, 26, {fill: C.amber, bold: true});
      svg.text(1484, y + 30, a, 20); svg.text(1484, y + 56, b, 15, {fill: C.sub});
    });
    svg.rect(1424, 924, 22, 22, {r: 4, fill: C.amber, stroke: 'none'}); svg.text(1435, 941, '✓', 16, {anchor: 'middle', fill: '#16181A', bold: true});
    svg.text(1458, 942, '用附近箱子里的材料', 18);
    svg.text(1424, 990, '按顺序扣：先抽屉，再身上，最后箱子', 15, {fill: C.dis});

    svg.add(`<text x="70" y="1062" font-size="16" fill="${C.dis}">概念图 · 物品、数字、用时都是示意 · 游戏里文字用原版字体</text>`);
    notes(svg, [
      ['左边竖条', '分类：木工、家具、工具、建造、生活用品、修补、拆解。数字是这一类会做几个，没有会做的就灰掉。'],
      ['中间图标格', '只显示会做的配方，缺材料的右上角红点，刚学会的左上角琥珀点。没学会的不显示，只在格子下面说一句"还有没学会的"。'],
      ['成品详情', '3D 模型能拖着转，下面是说明、重量、背包大小、用时、这个配方是从哪学会的。'],
      ['材料', '有多少 / 要多少，每行写从哪里拿：工作台抽屉、身上、附近箱子。'],
      ['工具', '五个格子就是工作台上的五对木钉，要用的那把亮起来打勾，没挂的显示空。台钳是自带的。'],
      ['制作', '数量、制作按钮兼进度条。人要守着，走开就停。'],
      ['和非生物因素的不同', '没有技能等级和工作台升级按钮；附近箱子直接能用，不用装附件。'],
    ], 150, '参考《非生物因素》工作台：左边分类竖条，中间图标格，右边成品详情和制作');
    svg.add('</svg>');
    await sharp(Buffer.from(svg.out())).png().toFile(OUT + '/workbench_ui_a.png');
  }

  // ============================================================ version B: text list (Enshrouded's crafting menu)
  {
    const svg = new Svg();
    svg.add(frame(bg, 'B 版 · 文字清单', '参考《灵英神殿》制作菜单：左边按分类分组的清单，中间大图和说明，右边材料'));
    header(svg, ['背包', '制作'], 1);
    // list
    svg.panel(70, 140, 500, 880);
    svg.rect(90, 158, 460, 44, {r: 6, fill: 'rgba(0,0,0,0.25)'}); svg.text(108, 188, '搜索…', 19, {fill: C.dis});
    let y = 240;
    const groups = [
      ['木工', 4, false, []], ['家具', 3, false, []], ['工具', 4, false, []], ['建造', 2, false, []],
      ['生活用品', 4, true, [[I.splint, '夹板', 'missing', true], [I.bandage, '绷带', 'ok'], [I.muffs, '简易护耳罩', 'ok'], [wood.white_pine, '引火柴', 'ok']]],
      ['修补', 2, false, []], ['拆解', 2, false, []],
    ];
    for (const [g, k, open, items] of groups) {
      svg.text(92, y, open ? '▾' : '▸', 18, {fill: C.sub});
      svg.text(116, y, g, 21, {bold: true, fill: k ? C.text : C.dis});
      svg.text(548, y, k ? `已学会 ${k} 个` : '尚未学会', 16, {anchor: 'end', fill: k ? C.sub : C.dis});
      y += 16; svg.line(92, y, 548, y, C.line, 1); y += 14;
      if (open) for (const [ic, n, st, isNew] of items) {
        const sel = n === '夹板';
        if (sel) { svg.rect(86, y - 4, 468, 50, {r: 6, fill: C.hover, stroke: C.lineHi}); svg.add(`<rect x="86" y="${y + 6}" width="4" height="30" rx="2" fill="${C.amber}"/>`); }
        svg.img(104, y, 40, 40, ic);
        svg.text(156, y + 28, n, 20, {fill: st === 'ok' ? C.text : C.text});
        if (isNew) svg.text(156 + n.length * 21 + 8, y + 26, '新', 14, {fill: C.amber, bold: true});
        svg.text(540, y + 28, st === 'ok' ? '可制作' : '材料不足', 16, {anchor: 'end', fill: st === 'ok' ? C.ok : C.bad});
        y += 54;
      }
      y += 26;
    }
    svg.text(92, 990, '未学会的配方不显示', 16, {fill: C.dis});

    // detail
    svg.add(`<radialGradient id="g2"><stop offset="0" stop-color="rgba(232,228,220,0.10)"/><stop offset="1" stop-color="rgba(232,228,220,0)"/></radialGradient><ellipse cx="960" cy="400" rx="330" ry="220" fill="url(#g2)"/>`);
    svg.img(760, 210, 400, 400, I.splint);
    svg.text(610, 196, '夹板', 46, {bold: true});
    svg.text(612, 234, '生活用品', 19, {fill: C.sub});
    svg.text(612, 266, '新学会，来源：动手摸索', 16, {fill: C.amber});
    svg.line(610, 650, 1310, 650);
    const props = [['重量', '0.30 kg'], ['尺寸', '1 × 2 格'], ['制作时间', '10 秒'], ['已持有', '0']];
    props.forEach(([a, b], i) => { svg.text(612 + i * 175, 690, a, 17, {fill: C.sub}); svg.text(612 + i * 175, 722, b, 22, {bold: true}); });
    svg.wrap(612, 780, '固定骨折的手臂或腿，帮助愈合', 20, 690, {fill: C.text});

    // right: materials
    svg.panel(1350, 140, 500, 600);
    svg.text(1374, 190, '材料', 24, {bold: true}); svg.text(1826, 190, '有 / 要', 18, {fill: C.sub, anchor: 'end'});
    const mats = [[wood.white_pine, '直树枝', '5 / 2', true], [I.bandage, '布条', '1 / 2', false]];
    mats.forEach(([ic, n, k, ok], i) => {
      const yy = 212 + i * 80;
      svg.slot(1374, yy, 62, ic);
      svg.text(1452, yy + 40, n, 21);
      svg.text(1826, yy + 40, k, 22, {anchor: 'end', fill: ok ? C.ok : C.bad, bold: !ok});
    });
    svg.text(1374, 420, '缺少：布条 ×1', 18, {fill: C.bad});
    svg.line(1366, 486, 1834, 486);
    svg.text(1374, 526, '工具', 22, {bold: true});
    svg.text(1374, 562, '无', 18, {fill: C.sub});
    svg.line(1366, 590, 1834, 590);
    svg.text(1374, 630, '数量', 20, {fill: C.sub});
    svg.rect(1460, 600, 48, 48, {r: 8}); svg.text(1484, 633, '−', 26, {anchor: 'middle'});
    svg.text(1560, 634, '1', 26, {anchor: 'middle', bold: true});
    svg.rect(1612, 600, 48, 48, {r: 8}); svg.text(1636, 633, '+', 26, {anchor: 'middle'});
    svg.text(1826, 634, '可制作 0', 18, {anchor: 'end', fill: C.dis});

    // pinned HUD preview
    svg.panel(1350, 770, 500, 170);
    svg.text(1374, 812, '加入待做后，屏幕右上角显示：', 17, {fill: C.sub});
    svg.rect(1374, 830, 452, 92, {r: 8, fill: 'rgba(16,16,16,0.5)'});
    svg.img(1384, 840, 40, 40, I.splint); svg.text(1436, 868, '待做：夹板', 19, {bold: true});
    svg.text(1436, 904, '布条 1 / 2', 17, {fill: C.bad}); svg.text(1580, 904, '直树枝 5 / 2', 17, {fill: C.ok});

    // bottom prompt bar
    svg.add(`<rect x="600" y="960" width="720" height="60" rx="10" fill="rgba(16,16,16,0.55)" stroke="${C.line}"/>`);
    let kx = 626;
    kx += svg.key(kx, 998, '左键') + 12; svg.text(kx, 998, '按住制作', 20, {fill: C.dis}); kx += 110;
    kx += svg.key(kx, 998, 'F') + 12; svg.text(kx, 998, '加入待做', 20); kx += 110;
    kx += svg.key(kx, 998, '右键') + 12; svg.text(kx, 998, '查看可用材料', 20, {fill: C.sub});
    svg.add(`<circle cx="1286" cy="990" r="16" fill="none" stroke="rgba(232,228,220,0.2)" stroke-width="4"/>`);

    svg.add(`<text x="70" y="1062" font-size="16" fill="${C.dis}">概念图 · 物品、数字、用时都是示意 · 游戏里文字用原版字体</text>`);
    notes(svg, [
      ['顶上标签', '背包 / 制作，Q、E 切换。在工作台前打开时直接在"制作"。没有单独的知识页。'],
      ['左边清单', '按分类折叠，每类写已学会几个。每行写可制作 / 材料不足。读完书籍、图纸或动手摸索学会的配方直接加进对应分类，标"新"。未学会的不显示。'],
      ['中间', '大图（3D，可转）、名字、来源（生活经验、动手摸索或书名）、重量、尺寸、制作时间、已持有数量，下面是物品说明（和背包里的说明同一条）。'],
      ['右边材料', '只写有 / 要和缺少什么。材料从哪里扣、去哪里找，写进 wiki，界面上不说。不需要工具时写"无"。'],
      ['加入待做', '按 F 把这个配方加入待做，屏幕右上角显示还缺的材料，出门搜刮时看得到。'],
      ['制作', '按住左键制作，进度在圆圈上，松开就停。人要在工作台前。'],
      ['和灵英神殿的不同', '没有等级和稀有度，没有 NPC 工匠，不显示未学会的配方。'],
    ], 150, '参考《灵英神殿》制作菜单：左边按分类分组的清单，中间大图和说明，右边材料');
    svg.add('</svg>');
    await sharp(Buffer.from(svg.out())).png().toFile(OUT + '/workbench_ui_b.png');
  }

  console.log('written', ['workbench_ui_a.png', 'workbench_ui_b.png'].join(', '));
}
main().catch(e => { console.error(e); process.exit(1); });
