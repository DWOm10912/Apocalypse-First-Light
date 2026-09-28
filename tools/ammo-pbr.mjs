// Light LabPBR for AFL lathe ammo: one zone table shared by every calibre's high-detail atlas (rounds, casings) and its
// ejected-casing FX asset, so a flying casing, the same casing as an item and the magazine top round read as the same
// materials under shader packs. Used by tools/build-9x19mm-ammo.mjs and tools/build-50ae-ammo.mjs.
// _s: R perceptual smoothness, G F0 (255 = metal, the Base Color is F0; 10 = dielectric, F0 ~0.04), B 0, A 255 (no emission).
// _n: flat tangent normal (128, 128) and B = AO. Nothing high-frequency: rounds and casings are small (shimmer).

// zone -> [smoothness, F0, AO]
export const AMMO_PBR = {
  // cartridge brass (metal, medium smoothness: turned edges a little higher, recesses and grooves lower)
  head: [110, 255, 255], chamfer: [130, 255, 255], rim: [125, 255, 255], rimtop: [105, 255, 235], groove: [100, 255, 215],
  bevel: [110, 255, 255], body: [115, 255, 255], crimp: [115, 255, 255], mouth: [125, 255, 255], lip: [125, 255, 255],
  lipTop: [135, 255, 255], pocket: [70, 255, 170], inner: [70, 255, 190], innerDeep: [55, 255, 150],
  // powder soot, flash hole and the FX casing's shallow mouth dish (dielectric)
  floor: [45, 10, 120], flash: [30, 10, 90], dish: [50, 10, 150],
  // nickel primer (metal)
  primer: [140, 255, 255], primerEdge: [130, 255, 240], primerSide: [110, 255, 200], dimple: [120, 255, 235],
  // copper / gilding-metal bullet jacket (metal)
  seated: [110, 255, 200], bearing: [125, 255, 255], ogive: [135, 255, 255], tip: [130, 255, 255],
  jacket: [125, 255, 255], jacketEdge: [140, 255, 255],
  // exposed soft-point lead: dielectric with a slight sheen (as a metal its dark Base Color would turn near black)
  lead: [80, 10, 255], meplat: [85, 10, 255],
};

/** LabPBR texel values for a paint zone; ao overrides the table's AO (e.g. a radial gradient). */
export function ammoPbr(zone, ao = null) {
  const v = AMMO_PBR[zone];
  if (!v) throw new Error('no ammo PBR for zone ' + zone);
  return {s: [v[0], v[1], 0, 255], n: [128, 128, ao ?? v[2], 255]};
}
