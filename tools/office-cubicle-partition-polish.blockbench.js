(() => {
  if (Project.name !== 'office_cubicle_partition' || Cube.all.length !== 42 || Texture.all.length !== 1) {
    throw new Error('Expected the live office cubicle partition project');
  }
  Undo.initEdit({textures: Texture.all});
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;
  const swatch = (index, base, light, dark) => {
    const x = (index % 4) * 32;
    const y = Math.floor(index / 4) * 32;
    ctx.fillStyle = base;
    ctx.fillRect(x, y, 32, 32);
    ctx.fillStyle = light;
    ctx.fillRect(x + 1, y + 1, 30, 2);
    ctx.fillRect(x + 2, y + 3, 1, 26);
    ctx.fillStyle = dark;
    ctx.fillRect(x + 1, y + 29, 30, 2);
    ctx.fillRect(x + 29, y + 3, 2, 26);
  };
  swatch(0, '#30353a', '#42494e', '#202428');
  swatch(1, '#3a4045', '#50585e', '#292e32');
  swatch(2, '#252a2e', '#353b40', '#171b1e');
  swatch(3, '#4a5156', '#626a70', '#343a3e');
  swatch(4, '#aeb7be', '#bac2c8', '#9ca5ac');
  swatch(5, '#b5bdc3', '#c0c7cc', '#a3abb1');
  swatch(6, '#a7b0b7', '#b4bcc2', '#969fa6');
  swatch(7, '#bbc2c7', '#c5cbd0', '#aab1b6');
  swatch(8, '#989fa5', '#a7aeb4', '#858d93');
  swatch(9, '#1d2124', '#2b3034', '#111416');
  swatch(10, '#555d62', '#697177', '#3d4449');
  swatch(11, '#8f989f', '#a0a8ae', '#7d858c');
  swatch(12, '#353b40', '#495056', '#24292d');
  swatch(13, '#adb5bb', '#b9c0c5', '#9aa2a8');
  swatch(14, '#2a2f33', '#3d4348', '#1b1f22');
  swatch(15, '#c0c6ca', '#c9ced2', '#afb5b9');
  const fabric = (index, base, softLight, softDark) => {
    const x = (index % 4) * 32;
    const y = Math.floor(index / 4) * 32;
    ctx.fillStyle = base;
    ctx.fillRect(x, y, 32, 32);
    ctx.fillStyle = softLight;
    ctx.fillRect(x + 3, y + 4, 15, 10);
    ctx.fillRect(x + 18, y + 18, 11, 8);
    ctx.fillStyle = softDark;
    ctx.fillRect(x + 4, y + 21, 12, 7);
  };
  fabric(4, '#adb6bd', '#b0b9c0', '#aab3ba');
  fabric(5, '#afb8be', '#b2bbc1', '#acb5bb');
  fabric(6, '#abb4bb', '#aeb7be', '#a8b1b8');
  fabric(7, '#b1b9bf', '#b4bcc2', '#aeb6bc');
  Texture.all[0].fromDataURL(canvas.toDataURL());
  Texture.all[0].updateSource();
  Undo.finishEdit('Smooth office partition fabric');
  Canvas.updateAll();
  return {project: Project.name, cubes: Cube.all.length, texture: [Project.texture_width, Project.texture_height]};
})()
