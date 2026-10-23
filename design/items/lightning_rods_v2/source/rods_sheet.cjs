// Lightning rods V2 concept sheets: node rods_sheet.cjs <renders dir> <out dir>
// Renders come from rods_build.py + rods_render.py (Blender, transparent PNG).
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');
const path = require('path');
const [R, OUT] = process.argv.slice(2);
const W = 2600, H = 1440, BG = '#2a2c2e';
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');

function wrap(s, n) {
  const lines = [];
  while (s.length > n) {
    let k = n;
    while (k < s.length && '，；、）：'.includes(s[k])) k++;
    lines.push(s.slice(0, k)); s = s.slice(k);
  }
  lines.push(s);
  return lines;
}

async function sheet(file, title, sub, images, notes, notesX, notesY) {
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
<style>text{font-family:'Microsoft YaHei','微软雅黑',sans-serif;fill:#e8e6e1}</style>
<rect width="${W}" height="${H}" fill="${BG}"/>
<text x="40" y="62" font-size="42" font-weight="bold">${esc(title)}</text>
<text x="40" y="104" font-size="24" fill="#a9b4bd">${esc(sub)}</text>`;
  for (const im of images) svg += `<text x="${im.x}" y="${im.y - 12}" font-size="22" fill="#a9b4bd">${esc(im.label)}</text>`;
  let y = notesY;
  for (const [k, v] of notes) {
    svg += `<text x="${notesX}" y="${y}" font-size="25" font-weight="bold" fill="#7fd0ff">${esc(k)}</text>`;
    y += 36;
    for (const line of wrap(v, 40)) { svg += `<text x="${notesX}" y="${y}" font-size="23">${esc(line)}</text>`; y += 33; }
    y += 16;
  }
  svg += '</svg>';
  const layers = [];
  for (const im of images) {
    const buf = await sharp(path.join(R, im.src)).resize({width: im.w, height: im.h, fit: 'inside'}).toBuffer();
    layers.push({input: buf, left: im.x, top: im.y});
  }
  await sharp(Buffer.from(svg)).composite(layers).png().toFile(path.join(OUT, file));
  console.log('wrote', file, 'notes end y', y);
}

(async () => {
  await sheet('rods_v2_A_copper_air_terminal.png', '铜接闪杆 V2 · 概念图',
    '现代建筑屋顶 · 按 NFPA 780 二类 · Blender 概念，不是最终模型 · 蓝框是方块格（1 m）',
    [
      {src: 'A_hero.png', x: 30, y: 150, w: 980, h: 1274, label: '整体'},
      {src: 'A_front.png', x: 1020, y: 150, w: 520, h: 953, label: '正面'},
      {src: 'A_base.png', x: 1580, y: 940, w: 600, h: 480, label: '底座和压线夹'},
    ],
    [
      ['杆', '5/8 英寸（15.9 mm）实心铜杆，长 36 英寸（914 mm），圆钝头。二类（建筑高于 23 m）铜杆至少 1/2 英寸，5/8 英寸是市面上常见最粗的一档'],
      ['三脚撑架', '超过 24 英寸的接闪杆厂家都配撑架：半高处一个铜夹箍，三根 19×3.5 mm 铜扁条，脚上用螺钉固定，撑开直径约 0.6 m'],
      ['底座', '铸青铜平底座 115 mm 见方，4 颗不锈钢螺栓；六角螺纹座，杆拧进去后锁紧'],
      ['引下线', '1/2 英寸铜绞线从底座侧面的压线夹出来，贴着屋面走，隔一段用铜线卡固定'],
      ['和 V1 比', '高 660 → 976 mm，多了撑架、压线夹和线卡，底座更大更厚。杆本身仍按真实粗细，远看主要靠撑架和底座读出来'],
      ['占格', '一格以内，撑脚和引下线都不出格'],
    ], 1580, 170);
  await sheet('rods_v2_B_barn_lightning_rod.png', '谷仓避雷针 V2 · 概念图',
    '美国老谷仓、农舍屋脊 · 约 1900–1920 年 · Blender 概念，不是最终模型 · 蓝框是方块格（1 m）',
    [
      {src: 'B_hero.png', x: 30, y: 150, w: 980, h: 1274, label: '整体'},
      {src: 'B_front.png', x: 1000, y: 150, w: 560, h: 1027, label: '正面'},
      {src: 'B_top.png', x: 1575, y: 150, w: 470, h: 376, label: '箭头和尖顶'},
      {src: 'B_stand.png', x: 2075, y: 150, w: 470, h: 376, label: '三脚座和绝缘子'},
    ],
    [
      ['总高', '1.80 m，占两格（实物整套有 72 英寸、约 1.83 m 的）'],
      ['支座', '铸铁三脚座：铸铁套筒高 0.31 m，三条弯腿，扁脚掌钉在屋面上'],
      ['杆', '铜包铁扭绞方杆，3/4 英寸（19 mm）'],
      ['玻璃球', '4.5 英寸（114 mm）钴蓝玻璃球，上下铜套'],
      ['风向箭头', '铜皮箭头，长约 0.7 m，尾羽压筋；固定不转'],
      ['尖顶', '铜球上一根长尖，周围三根爪形尖'],
      ['引下线', '铜绞线从套筒侧面的接线耳出来，经过一个玻璃绝缘子，再沿屋面走'],
      ['和 V1 比', '高 1.45 → 1.80 m，杆 12 → 19 mm，加了三脚座、风向箭头、爪形尖和玻璃绝缘子'],
    ], 1580, 580);
})();
