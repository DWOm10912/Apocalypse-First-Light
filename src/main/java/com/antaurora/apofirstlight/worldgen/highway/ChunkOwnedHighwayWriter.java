package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Commits only blocks whose owning chunk is the currently decorated chunk. */
public final class ChunkOwnedHighwayWriter implements HighwayBlockWriter {
    private final WorldGenLevel level;
    private final ChunkPos owner;
    private final Map<BlockPos, BlockState> attempted = new HashMap<>();
    private int changedBlocks;
    private int clearedBlocks;
    private int asphaltSurfaceBlocks;
    private int duplicateAttempts;
    private int illegalWrites;
    private long blockWriteNanos;

    public ChunkOwnedHighwayWriter(WorldGenLevel level, ChunkPos owner) {
        this.level = level;
        this.owner = owner;
    }

    @Override
    public boolean set(BlockPos pos, BlockState state) {
        if (!owns(pos)) return false;
        if (!level.ensureCanWrite(pos)) {
            illegalWrites++;
            return false;
        }
        if (level.getBlockEntity(pos) != null) return false;
        BlockPos immutable = pos.immutable();
        BlockState previousAttempt = attempted.put(immutable, state);
        if (previousAttempt != null && previousAttempt.equals(state)) duplicateAttempts++;
        BlockState current = level.getBlockState(pos);
        if (current.equals(state)) return false;
        if (state.isAir() && !current.isAir()) clearedBlocks++;
        long writeStarted = System.nanoTime();
        boolean written = level.setBlock(pos, state, 2);
        blockWriteNanos += System.nanoTime() - writeStarted;
        if (!written) return false;
        changedBlocks++;
        if (state.is(HighwayPalette.ASPHALT.getBlock())) asphaltSurfaceBlocks++;
        return true;
    }

    @Override
    public boolean owns(BlockPos pos) {
        return (pos.getX() >> 4) == owner.x && (pos.getZ() >> 4) == owner.z;
    }

    @Override
    public boolean mayAffectHorizontal(int centerX, int centerZ, int radius) {
        return centerX + radius >= owner.getMinBlockX() && centerX - radius <= owner.getMaxBlockX()
                && centerZ + radius >= owner.getMinBlockZ() && centerZ - radius <= owner.getMaxBlockZ();
    }

    public int changedBlocks() { return changedBlocks; }
    public int clearedBlocks() { return clearedBlocks; }
    public int asphaltSurfaceBlocks() { return asphaltSurfaceBlocks; }
    public int duplicateAttempts() { return duplicateAttempts; }
    public int illegalWrites() { return illegalWrites; }
    public long blockWriteNanos() { return blockWriteNanos; }
}
