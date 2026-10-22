// Folding knife V1 concept sheets (2026-10-22): one 2560x1320 PNG per version from fk_build.py's renders.
// node fk_sheet.cjs <out dir>
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');
const R = 'C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/fk_renders/';
const OUT = process.argv[2];
const W = 2560, H = 1320;
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
// side views: ortho 0.235 m over 2000 px, drawn at 1560 px wide
const SIDE_W = 1560, PX_PER_CM = SIDE_W / 23.5;

const sheets = {
  A: {
    title: '折刀 V1 · A 版：传统背锁折刀',
    notes: [
      ['定位', '老式农场折刀，抽屉、工具箱、旧皮卡的手套箱里常能翻到'],
      ['刀刃', '斜切尖（clip point），缎面不锈钢，平磨；刀面有开刀用的指甲槽'],
      ['刀柄', '胡桃木贴片，前后黄铜枕，黄铜铆钉，钢背簧'],
      ['锁', '背锁：打开时背簧卡住刀根；按下刀背中间的锁片才能收刀'],
      ['尺寸', '收起 12.6 cm，打开 21.1 cm，露刃约 8.5 cm，估重 140 g'],
      ['开刀', '要两只手：一手拿柄，一手用指甲勾槽拉开；动作慢一点，有“咔”的落锁声'],
      ['近战', '打开锁住后能当近战武器，放近战栏（3 键）；收起时不能攻击'],
    ],
  },
  B: {
    title: '折刀 V1 · B 版：现代衬锁折刀',
    notes: [
      ['定位', '户外店和杂货店卖的日用折刀，常别在裤兜、背包或车门储物格里'],
      ['刀刃', '落尖（drop point），石洗不锈钢，高平磨；刀根有拇指钉'],
      ['刀柄', '橄榄绿 G10 贴片，不锈钢衬板，黑色背垫，刀背防滑齿，口袋夹'],
      ['锁', '衬锁：内侧衬板弹进来顶住刀根；用拇指把衬板推开才能收刀'],
      ['尺寸', '收起 11.6 cm，打开 19.2 cm，露刃约 7.5 cm，估重 100 g'],
      ['开刀', '一只手：拇指推刀根上的拇指钉甩开；比 A 版快'],
      ['近战', '打开锁住后能当近战武器，放近战栏（3 键）；收起时不能攻击'],
    ],
  },
};

async function sheet(tag) {
  const s = sheets[tag];
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
<style>text{font-family:'Microsoft YaHei','微软雅黑',sans-serif;fill:#e8e6e1}</style>
<rect width="${W}" height="${H}" fill="#2a2c2e"/>
<text x="40" y="58" font-size="40" font-weight="bold">${esc(s.title)}</text>
<text x="1640" y="58" font-size="24" fill="#a9b4bd">Blender 概念，不是最终模型 · 刀面不刻品牌</text>
<text x="40" y="130" font-size="24">打开（已锁住）</text>
<text x="1640" y="130" font-size="24">收起（背面）</text>
<text x="40" y="900" font-size="24">侧面（打开）</text>`;
  // 5 cm scale bar under the side view
  const bx = 60, by = 1270, bl = 5 * PX_PER_CM;
  svg += `<rect x="${bx}" y="${by}" width="${bl}" height="6" fill="#7fd0ff"/>`;
  for (let i = 0; i <= 5; i++) svg += `<rect x="${bx + i * PX_PER_CM - 1}" y="${by - 8}" width="2" height="14" fill="#7fd0ff"/>`;
  svg += `<text x="${bx + bl + 14}" y="${by + 9}" font-size="22" fill="#7fd0ff">5 cm</text>`;
  let y = 690;
  for (const [k, v] of s.notes) {
    svg += `<text x="1640" y="${y}" font-size="25" font-weight="bold" fill="#7fd0ff">${esc(k)}</text>`;
    // wrap by width: CJK 1, Latin 0.55; Latin words and closing punctuation never split from what they follow
    const tokens = v.match(/[A-Za-z0-9.()\- ]+|[^A-Za-z0-9.()\- ][”，；。、）：]*/g);
    const lines = ['']; let w = 0;
    for (const tok of tokens) {
      const tw = [...tok].reduce((a, c) => a + (c.charCodeAt(0) < 128 ? 0.55 : 1), 0);
      if (w + tw > 29 && lines[lines.length - 1]) { lines.push(''); w = 0; }
      lines[lines.length - 1] += lines[lines.length - 1] ? tok : tok.trimStart(); w += tw;
    }
    lines.forEach((ln, i) => { svg += `<text x="1760" y="${y + i * 32}" font-size="23">${esc(ln)}</text>`; });
    y += 32 * lines.length + 16;
  }
  svg += '</svg>';
  const open = await sharp(R + tag + '_open.png').resize(1560, 867).toBuffer();
  const closed = await sharp(R + tag + '_closed.png').resize(720, 540).toBuffer();
  const sideH = Math.round(560 * SIDE_W / 2000);
  const sideOpen = await sharp(R + tag + '_side_open.png').resize(SIDE_W, sideH).toBuffer();
  await sharp(Buffer.from(svg)).composite([
    {input: open, left: 40, top: 60},
    {input: closed, left: 1640, top: 130},
    {input: sideOpen, left: 40, top: 1250 - sideH},
  ]).png().toFile(OUT + '/folding_knife_' + tag.toLowerCase() + '_concept.png');
}
(async () => {
  for (const tag of ['A', 'B']) await sheet(tag);
  console.log('ok');
})();
