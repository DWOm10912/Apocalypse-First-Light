package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact reversible edit ledger for the optional debug clear command. */
public final class HighwayEditSession {
    private final ServerLevel level;
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();

    public HighwayEditSession(ServerLevel level) { this.level = level; }

    public boolean set(BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null) return false;
        BlockState current = level.getBlockState(pos);
        if (current.equals(state)) return false;
        originals.putIfAbsent(pos.immutable(), current);
        level.setBlock(pos, state, 3);
        return true;
    }

    public int changedBlocks() { return originals.size(); }

    public int restore() {
        int restored = 0;
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
            restored++;
        }
        originals.clear();
        return restored;
    }

    public boolean isEmpty() { return originals.isEmpty(); }
}
