// Log mount (block/LogMount, docs/models/cabin_exterior_v1.md "壁挂件垫块", 2026-10-13): a wall fixture on a round log wall
// stands LOG_DEPTH out of the wall face, on the flat pad the wall draws behind it (the logs bulge 7 cm out of their cell;
// outlets and the lampholder's pull chain sank into them). The OBJ fixtures' log_mount=true states use a second block model:
// the same OBJ moved by a Forge root transform ("transform", applied in model space before the blockstate rotation:
// forge ExtendedBlockModelDeserializer reads it, ObjModel composes the state's rotation after it; block units).
export const LOG_DEPTH = 0.08;

/**
 * The `_log` twin of an OBJ block model JSON: moved out along the model's front. Models built facing north (front -Z, the
 * wall at +Z) move along -Z; lens-up models, turned onto a wall by x 90 / 270, along +Y (the lens side ends up away from
 * the wall).
 */
export const logTwin = (model, lensUp = false) => ({...model, transform: {translation: lensUp ? [0, LOG_DEPTH, 0] : [0, 0, -LOG_DEPTH]}});
