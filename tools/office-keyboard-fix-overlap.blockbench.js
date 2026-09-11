(() => {
  if (Project.name !== 'office_keyboard' || Cube.all.length !== 34) throw new Error('Wrong keyboard project');
  const row = Cube.all.find(cube => cube.name === 'numpad_row_04');
  if (!row) throw new Error('Missing numpad_row_04');
  Undo.initEdit({elements: [row]});
  row.to[0] = 12.92;
  Undo.finishEdit('Separate numpad Enter from row 04');
  Canvas.updateAll();
  return {name: row.name, from: [...row.from], to: [...row.to]};
})()
