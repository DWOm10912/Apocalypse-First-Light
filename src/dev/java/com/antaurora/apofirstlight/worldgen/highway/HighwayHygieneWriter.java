package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Narrow worldgen-only writer for final natural-feature cleanup.  Unlike the
 * construction writer it may use the FEATURES region's vanilla radius-one
 * write permission, but it never receives arbitrary construction positions.
 */
public final class HighwayHygieneWriter {
    private final WorldGenLevel level;
    private final ChunkPos sourceChunk;
    private int writes;
    private int crossChunkWrites;
    private int illegalWrites;

    public HighwayHygieneWriter(WorldGenLevel level, ChunkPos sourceChunk) {
        this.level = level;
        this.sourceChunk = sourceChunk;
    }

    /** Single WorldGenRegion-safe predicate for every Final Hygiene read and write. */
    public boolean canAccess(BlockPos pos) {
        return level.ensureCanWrite(pos);
    }

    public boolean canWrite(BlockPos pos) {
        return canAccess(pos);
    }

    public boolean clear(BlockPos pos) {
        if (!canWrite(pos)) {
            illegalWrites++;
            return false;
        }
        if (level.getBlockEntity(pos) != null || level.getBlockState(pos).isAir()) return false;
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)) return false;
        writes++;
        if ((pos.getX() >> 4) != sourceChunk.x || (pos.getZ() >> 4) != sourceChunk.z) {
            crossChunkWrites++;
        }
        return true;
    }

    public int writes() { return writes; }
    public int crossChunkWrites() { return crossChunkWrites; }
    public int illegalWrites() { return illegalWrites; }
}
