package com.antaurora.apofirstlight.meshhit;

/**
 * A block whose model and shape reach down into the cell under it (docs/rendering/mesh_hit_runtime_v1.md, 2026-10-14): the
 * emergency radio sinks onto the top of the table, counter or desk under it, its whole body in that cell. A ray that comes
 * into the cell under it from the side never passes its own cell, so MeshHitClip also tries such a block above each cell
 * it passes, and the nearer hit wins (the hit keeps the block's own position).
 */
public interface MeshHitReachesDown {
}
