// Field Survival Guide V1 game assets: out/game/*.svg (fsg_game_assets.py) -> the mod's textures.
//   node design/items/field_survival_guide_v1/fsg_game_assets.cjs
// The reader's pages and covers are 0.64 of the design size (736 x 1152 for a 1150 x 1800 page; drawn bilinear, about
// 860 px tall on a 1080p screen); the model's sources go to src/main/blockbench/textures for the mesh bake.
const path = require('path');
const sharp = require('D:/Minecraft Modding/Apocalypse First Light/wiki/node_modules/sharp');

const ROOT = path.resolve(__dirname, '../../..');
const IN = path.join(__dirname, 'out', 'game');
const GUI = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/textures/gui/book/field_survival_guide');
const BB = path.join(ROOT, 'src/main/blockbench/textures');
const jobs = [
  ['paper', GUI, 'paper.png', 736, 1152],
  ['fig_bow_drill', GUI, 'fig_bow_drill.png', 920, 560],
  ['cover_zh_cn', GUI, 'cover_zh_cn.png', 736, 1152],
  ['cover_en_us', GUI, 'cover_en_us.png', 736, 1152],
  ['model_cover', BB, 'field_survival_guide_cover_src.png', 1180, 900],
  ['model_edges', BB, 'field_survival_guide_edges_src.png', 512, 128],
];
(async () => {
  require('fs').mkdirSync(GUI, {recursive: true});
  for (const [name, dir, file, w, h] of jobs) {
    await sharp(path.join(IN, name + '.svg'), {limitInputPixels: false, density: 72}).resize(w, h, {fit: 'fill'})
      .png({compressionLevel: 9}).toFile(path.join(dir, file));
    console.log(file, w, h);
  }
})();
