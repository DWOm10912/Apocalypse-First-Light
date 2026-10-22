const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');
const R = 'C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/wb_renders/';
const OUT = process.argv[2];
const W = 2560, H = 1320;
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const notes = [
  ['工具槽 ×5', '一对木钉一个槽，玩家自己把工具挂上去才算有；空着的槽只能做不要工具的简单东西'],
  ['小工具条 ×6', '插螺丝刀、凿子这类小工具，也算工具槽'],
  ['前台钳', '铸铁台钳配木钳口，T 形手柄伸出格子前面约 6 cm'],
  ['抽屉 ×2', '工作台自带的储物，做东西时先从这里扣材料'],
  ['工具凹槽', '桌面后面一条凹槽，放零件和小东西'],
  ['下层搁板', '木料、边角料、木箱'],
  ['顶搁板', '钉子罐、铁罐、木盒'],
  ['材料', '框架和搁板：胡桃木 · 桌面：枫木拼板 · 挂板：白栎 · 下层板条：铁杉 · 台钳：深色铸铁'],
  ['尺寸', '蓝框是方块格：2 格长 × 1 格宽 × 2 格高，背面贴墙；桌面高 0.9 m，挂板到 1.8 m'],
];
let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
<style>text{font-family:'Microsoft YaHei','微软雅黑',sans-serif;fill:#e8e6e1}</style>
<rect width="${W}" height="${H}" fill="#2a2c2e"/>
<text x="40" y="58" font-size="40" font-weight="bold">AFL 工作台 V1 · Mesh 概念图</text>
<text x="1640" y="58" font-size="24" fill="#a9b4bd">2 格长 × 1 格宽 × 2 格高 · Blender 概念，不是最终模型</text>
<text x="1640" y="130" font-size="24">正面</text>
<text x="2216" y="130" font-size="24">侧面（左边是正面）</text>`;
let y = 745;
for (const [k, v] of notes) {
  svg += `<text x="1640" y="${y}" font-size="25" font-weight="bold" fill="#7fd0ff">${esc(k)}</text>`;
  // wrap the value at ~30 CJK chars
  const lines = []; let s = v;
  while (s.length > 30) { let n = 30; while ("，；。、）".includes(s[n])) n++; lines.push(s.slice(0, n)); s = s.slice(n); }
  lines.push(s);
  lines.forEach((ln, i) => { svg += `<text x="1800" y="${y + i * 32}" font-size="23">${esc(ln)}</text>`; });
  y += 32 * lines.length + 18;
}
svg += '</svg>';
(async () => {
  const persp = await sharp(R + 'persp.png').resize(1560, 1170).toBuffer();
  const front = await sharp(R + 'front.png').resize(560, 560).toBuffer();
  const side = await sharp(R + 'side.png').resize(330, 471).extract({left: 0, top: 0, width: 330, height: 471}).toBuffer();
  await sharp(Buffer.from(svg)).composite([
    {input: persp, left: 40, top: 100},
    {input: front, left: 1640, top: 150},
    {input: side, left: 2216, top: 150},
  ]).png().toFile(OUT);
  console.log('ok', OUT);
})();
