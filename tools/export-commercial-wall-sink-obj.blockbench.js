// Export the approved dry Blockbench source as Forge's static OBJ block model.
(function () {
  const fs = require('fs');
  const path = require('path');
  const root = 'D:/Minecraft Modding/Apocalypse First Light';
  const source = path.join(root, 'src/main/blockbench/commercial_wall_mounted_sink.bbmodel');
  const target = path.join(root, 'src/main/resources/assets/apocalypse_firstlight/models/block');
  const saved = JSON.parse(fs.readFileSync(source, 'utf8'));
  if (saved.elements.length !== 250 || saved.elements.some(e => /water|funnel/i.test(e.name)))
    throw Error('Inspect the saved dry sink source before export');
  const original = Project;
  try {
    Codecs.project.load(saved, {path: source, no_file: true});
    if (Cube.all.length !== saved.elements.length) throw Error('Missing loaded cubes');
    const uuid = Texture.all[0]?.uuid;
    if (uuid !== saved.textures[0].uuid) throw Error('Texture UUID mismatch');
    let obj = Codecs.obj.compile({mtl_name: 'commercial_wall_mounted_sink.mtl'});
    const objects = (obj.match(/^o /gm) || []).length;
    if (objects !== 250) throw Error(`Expected 250 OBJ objects, found ${objects}`);
    // Blockbench OBJ output is in block units, centered at X/Z=0.
    obj = obj.replace(/^v ([^ ]+) ([^ ]+) ([^\r\n]+)/gm, (_, x, y, z) =>
      `v ${Number(x) + .5} ${y} ${Number(z) + .5}`);
    const vertices = obj.split('\n').filter(line => line.startsWith('v '));
    const bounds = [0, 1, 2].map(i => [Math.min(...vertices.map(line => Number(line.split(' ')[i + 1]))),
      Math.max(...vertices.map(line => Number(line.split(' ')[i + 1])))]);
    if (bounds.some(([min, max], i) => !Number.isFinite(min) || min < -1e-6 || max > (i === 1 ? 2 : 1) + 1e-6))
      throw Error(`OBJ exceeds two-block vertical occupancy: ${JSON.stringify(bounds)}`);
    const material = `newmtl m_${uuid}\nKd 1 1 1\nmap_Kd apocalypse_firstlight:block/commercial_wall_mounted_sink\n`;
    fs.mkdirSync(target, {recursive: true});
    fs.writeFileSync(path.join(target, 'commercial_wall_mounted_sink.obj'), obj);
    fs.writeFileSync(path.join(target, 'commercial_wall_mounted_sink.mtl'), material);
    return JSON.stringify({objects, faces:(obj.match(/^f /gm)||[]).length, vertices:vertices.length,
      textureUuid:uuid, bounds});
  } finally {
    original?.select();
  }
})()
