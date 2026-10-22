// Field Survival Guide V1 concept sheets: stacks out/sheets.json (from fsg_sheets.py) into PNGs next to this script.
//   node design/items/field_survival_guide_v1/fsg_sheets.cjs
const fs = require('fs');
const path = require('path');
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');

const OUT = path.join(__dirname, 'out');
const src = f => (path.isAbsolute(f) ? f : path.join(OUT, f));

async function layer(l) {
  if (l.svg) return {input: await sharp(src(l.svg), {limitInputPixels: false}).png().toBuffer(), left: l.x, top: l.y};
  if (l.shadow) {
    const pad = 40;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${l.w + 2 * pad}" height="${l.h + 2 * pad}">` +
      `<rect x="${pad}" y="${pad + 8}" width="${l.w}" height="${l.h}" fill="#000" fill-opacity="0.55"/></svg>`;
    return {input: await sharp(Buffer.from(svg)).blur(14).png().toBuffer(), left: l.x - pad, top: l.y - pad};
  }
  let s = sharp(src(l.img), {limitInputPixels: false}).resize(l.w, l.h, {fit: 'cover'});
  if (l.blur) s = s.blur(l.blur);
  if (l.dim) s = s.modulate({brightness: l.dim});
  return {input: await s.png().toBuffer(), left: l.x, top: l.y};
}

(async () => {
  const sheets = JSON.parse(fs.readFileSync(path.join(OUT, 'sheets.json'), 'utf8'));
  for (const [name, sh] of Object.entries(sheets)) {
    const layers = [];
    for (const l of sh.layers) layers.push(await layer(l));
    await sharp({create: {width: sh.w, height: sh.h, channels: 4, background: sh.bg || '#000000'}})
      .composite(layers).png().toFile(path.join(__dirname, sh.out));
    console.log(name, sh.out);
  }
})();
