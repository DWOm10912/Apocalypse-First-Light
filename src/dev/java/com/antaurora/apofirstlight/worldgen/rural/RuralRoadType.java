package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Surface profiles; no world reads or random-stream consumption. */
public enum RuralRoadType {
    MAIN(5, 1, 1, 30), SIDE(3, 0, 1, 20), FARM_TRACK(3, 0, 0, 10);

    public final int width, shoulder, transition, priority;
    RuralRoadType(int width, int shoulder, int transition, int priority) {
        this.width = width; this.shoulder = shoulder; this.transition = transition; this.priority = priority;
    }
    public int radius() { return width / 2 + shoulder + transition; }
    public int layer(int distance) {
        return distance <= width / 2 ? 2 : distance <= width / 2 + shoulder ? 1 : 0;
    }
    public BlockState material(int distance, long seed, int x, int z) {
        long hash = seed ^ x * 341873128712L ^ z * 132897987541L;
        hash ^= hash >>> 33;
        int variation = Math.floorMod(hash, 5);
        if (layer(distance) == 0) return (variation == 0 ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState();
        if (this == MAIN && layer(distance) == 2) return AflBlocks.ASPHALT.get().defaultBlockState();
        if (this == FARM_TRACK) return (variation < 2 ? Blocks.GRAVEL
                : variation < 4 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
        return Blocks.GRAVEL.defaultBlockState();
    }
}
