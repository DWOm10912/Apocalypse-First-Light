package com.antaurora.apofirstlight.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** DEV-only shared surface sampler for settlement validation. */
public final class SettlementSurfaceSampler {
    private SettlementSurfaceSampler() {}

    public static void ensureDevChunksReady(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
            }
        }
    }

    public static SurfaceSample sample(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight()) {
            return new SurfaceSample(x, z, y, false, "MIN_BUILD_HEIGHT_SENTINEL");
        }
        return new SurfaceSample(x, z, y, true, "VALID");
    }

    public record SurfaceSample(int x, int z, int y, boolean valid, String reason) {
        public BlockPos ground() { return new BlockPos(x, y - 1, z); }
    }
}
