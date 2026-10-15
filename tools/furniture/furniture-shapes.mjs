// Mesh Shape profiles of the wood furniture's storage (docs/rendering/mesh_shape_runtime_v1.md; cabin/FurnitureOpening):
// the regions the player aims at to open / close the doors, drawer or lid and to search / view the open interior, written
// by tools/build-wood-furniture-v1.mjs (the counters) and -v2.mjs (wardrobe, nightstand, vintage freezer) from the same
// pieces they draw. The pieces' frame (blocks, front at z 0, north) goes to the profile's: px from the cell's bottom centre.

/** The axis-aligned bounds of a piece (or of several): [[x0, y0, z0], [x1, y1, z1]] in blocks. */
export function bounds(...pieces) {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  for (const pc of pieces) for (const p of pc.polys) for (const v of p.v) for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], v[k]); hi[k] = Math.max(hi[k], v[k]); }
  return [lo, hi];
}

const r3 = v => Math.round(v * 1000) / 1000 + 0;
/** A point in blocks to px; dx moves it across cells (a second cell's frame: -1). */
export const px = ([x, y, z], dx = 0) => [(x + dx) * 16 - 8, y * 16, z * 16 - 8].map(r3);
/** A box [[x0, y0, z0], [x1, y1, z1]] in blocks to px, grown by g blocks on every side. */
export const pxBox = ([a, b], dx = 0, g = 0) => [...px([a[0] - g, a[1] - g, a[2] - g], dx), ...px([b[0] + g, b[1] + g, b[2] + g], dx)];

/**
 * One profile state: physical and selection boxes, the regions {name: [boxes, anchor point]} (blocks; dx for the cell's
 * frame). The selection must cover every region (the regions only count on or in front of it).
 */
export function state({physical, selection = physical, regions}, dx = 0) {
  const anchors = {}, interaction = {};
  for (const [name, [boxes, anchor]] of Object.entries(regions)) {
    interaction[name] = {boxes: boxes.map(b => pxBox(b, dx)), anchor: name};
    anchors[name] = px(anchor, dx);
  }
  return {physical: physical.map(b => pxBox(b, dx)), selection: selection.map(b => pxBox(b, dx)), interaction, anchors};
}

export const profile = (states, cellsY = 1) => JSON.stringify({format_version: 1, units: 'px', ...(cellsY > 1 ? {cells_y: cellsY} : {}), states}, null, 2) + '\n';
