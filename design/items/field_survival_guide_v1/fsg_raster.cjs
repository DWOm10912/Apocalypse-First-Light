// Field Survival Guide V1 concept: out/*.svg -> out/*.png (text is already outlines, no fonts needed here).
//   node design/items/field_survival_guide_v1/fsg_raster.cjs
const fs = require('fs');
const path = require('path');
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');

const OUT = path.join(__dirname, 'out');
(async () => {
  for (const f of fs.readdirSync(OUT).filter(f => f.endsWith('.svg'))) {
    await sharp(path.join(OUT, f), {limitInputPixels: false}).png().toFile(path.join(OUT, f.replace(/\.svg$/, '.png')));
    console.log(f);
  }
})();
