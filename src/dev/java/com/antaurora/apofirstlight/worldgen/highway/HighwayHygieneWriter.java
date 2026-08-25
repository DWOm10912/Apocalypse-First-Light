package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Narrow worldgen-only writer for final natural-feature cleanup. */
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

    /**
     * Single predicate for every Final Hygiene read and write.
     *
     * The FEATURES WorldGenRegion may expose a small writable halo around its
     * center chunk.  That permission is useful for some vanilla features, but
     * it is not ownership for this chunk-owned highway pass: allowing it here
     * makes a highway feature invoked for one chunk clear vegetation in a
     * neighbouring chunk and triggers WorldGenRegion's far-chunk diagnostic.
     */
    public boolean canAccess(BlockPos pos) {
        return owns(pos) && level.ensureCanWrite(pos);
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

    private boolean owns(BlockPos pos) {
        return (pos.getX() >> 4) == sourceChunk.x && (pos.getZ() >> 4) == sourceChunk.z;
    }
}
