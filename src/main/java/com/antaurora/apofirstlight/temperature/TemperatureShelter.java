package com.antaurora.apofirstlight.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How sheltered a spot is, for Temperature V1: the radiation shielding sampler's rays (radiation/RadiationShielding),
 * up to 8 blocks from the head, without the 5 downward ones (open ground stops those too). "Any block that stops
 * movement is a wall"; leaves, glass panes and iron bars count half. roof: share of the 5 upward rays (straight up and
 * the 4 upper diagonals) stopped; walls: share of the 4 horizontal rays stopped; cover S = their 9-ray average.
 * Unloaded blocks count as open.
 */
public final class TemperatureShelter {
    public record Sample(double roof, double walls) {
        public static final Sample OPEN = new Sample(0, 0);
        /** S, 0 open ground .. 1 closed room / cave. */
        public double cover() { return (5 * roof + 4 * walls) / 9; }
    }
    public static final int MAX_DISTANCE = 8;
    private static final int[][] UP = {{0, 1, 0}, {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1}};
    private static final int[][] SIDE = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private TemperatureShelter() {}

    public static Sample sample(ServerLevel level, BlockPos head) {
        double roof = 0, walls = 0;
        for (int[] d : UP) roof += ray(level, head, d);
        for (int[] d : SIDE) walls += ray(level, head, d);
        return new Sample(roof / UP.length, walls / SIDE.length);
    }

    /** The largest wall value along the ray; stops at the first full wall. */
    private static double ray(ServerLevel level, BlockPos head, int[] d) {
        double ox = head.getX() + 0.5, oy = head.getY() + 0.5, oz = head.getZ() + 0.5, blocked = 0;
        for (int step = 1; step <= MAX_DISTANCE && blocked < 1; step++) {
            BlockPos pos = BlockPos.containing(ox + d[0] * step, oy + d[1] * step, oz + d[2] * step);
            if (!level.isLoaded(pos)) break;
            blocked = Math.max(blocked, wall(level, pos, level.getBlockState(pos)));
        }
        return blocked;
    }

    /** 1 for a block that stops movement, 0.5 for leaves and thin glass / bars, 0 for air, plants and fluids. */
    static double wall(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return 0;
        if (state.is(BlockTags.LEAVES)) return 0.5;
        if (state.getCollisionShape(level, pos).isEmpty()) return 0;
        return state.getBlock() instanceof IronBarsBlock ? 0.5 : 1;
    }
}
