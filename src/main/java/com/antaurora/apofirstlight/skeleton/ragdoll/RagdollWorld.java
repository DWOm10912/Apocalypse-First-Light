package com.antaurora.apofirstlight.skeleton.ragdoll;

import java.util.List;

/**
 * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 2): what the ragdoll asks of the world, the solid boxes near the body. The
 * game's side reads the blocks' collision shapes (Terrain Smooth slopes included); the offline check gives its own
 * scenes. Read only; never loads or generates a chunk.
 */
public interface RagdollWorld {
    /**
     * Adds every solid box touching the given world box to {@code out}, each as {minX, minY, minZ, maxX, maxY, maxZ}
     * (world blocks). Unloaded columns add nothing.
     */
    void boxes(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, List<double[]> out);
}
