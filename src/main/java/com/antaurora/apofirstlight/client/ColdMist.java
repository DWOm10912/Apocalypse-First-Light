package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.block.MeshSourceFrame;
import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.registry.AflParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cold mist ({@link ColdMistParticle}) of a powered chest freezer, from the master's client block-entity tick (the
 * thermal generator's pattern): only within 32 blocks of the player, none at the Minimal particle setting, half at
 * Decreased. Positions are in the Pure Mesh source frame (px, {@link MeshSourceFrame}); the lids' open fractions come
 * from the mesh animation state, so the mist follows what the player sees.
 * <ul>
 * <li>Always: a faint layer low in the well (seen through the glass with the lids closed).</li>
 * <li>Over an open half: wisps at the opening and mist welling over the rim (front, back, outer end) and sinking down
 * the outside.</li>
 * <li>While a lid slides open: a burst rising from the strip it uncovers that tick.</li>
 * </ul>
 * The beverage cooler has none: it only chills (user, 2026-10-01).
 */
public final class ColdMist {
    private static final double RANGE = 32.0;
    /** Last tick's open fraction of each lid, per freezer (the burst follows the change). */
    private static final Map<BlockEntity, double[]> LAST_OPEN = new WeakHashMap<>();

    // tools/build-chest-freezer-v2.mjs, source px: the well inside the liner, the lids (closed x ranges, travel), the rim
    private static final double WELL_X0 = -6.0, WELL_X1 = 22.0, WELL_Z = 6.0, SEAM = 8.0;
    private static final double MASTER_LID_X1 = 22.25, SLAVE_LID_X0 = -6.25, LID_TRAVEL = 14.15, HALF_WIDTH = 14.0;
    private static final double RIM_TOP = 15.8;
    // per tick at the Particles: All setting; open-half rates are for a fully open half
    private static final double LAYER_RATE = .12, WISP_RATE = .2, SPILL_FRONT_RATE = .15, SPILL_BACK_RATE = .05,
            SPILL_END_RATE = .03, LID_BURST = 16;

    /** Particle settings per use: life (ticks), starting half-size and growth, peak alpha, gravity, drag, swirl. */
    private enum Kind {
        LAYER(90, 140, .20F, .28F, 1.3F, .09F, .00015, .95, .0008),
        WISP(40, 70, .16F, .24F, 1.7F, .12F, .0003, .95, .0012),
        SPILL(40, 55, .12F, .18F, 2.0F, .13F, .0012, .96, .0008),
        BURST(30, 50, .20F, .28F, 1.8F, .15F, .0005, .93, .0012);

        final int life0, life1;
        final float size0, size1, growth, alpha;
        final double gravity, drag, swirl;

        Kind(int life0, int life1, float size0, float size1, float growth, float alpha, double gravity, double drag,
             double swirl) {
            this.life0 = life0;
            this.life1 = life1;
            this.size0 = size0;
            this.size1 = size1;
            this.growth = growth;
            this.alpha = alpha;
            this.gravity = gravity;
            this.drag = drag;
            this.swirl = swirl;
        }
    }

    private ColdMist() {
    }

    /** Client tick of the freezer's master (ChestFreezerBlock#getTicker). */
    public static void freezerTick(Level level, BlockPos master, BlockState state, ChestFreezerBlockEntity freezer) {
        double scale = scale(level, master);
        if (scale <= 0 || !freezer.powered()) {
            LAST_OPEN.remove(freezer);
            return;
        }
        Direction facing = state.getValue(ChestFreezerBlock.FACING);
        RandomSource random = level.random;
        double time = level.getGameTime();
        double[] open = {freezer.meshAnimation().sample("left_open", time), freezer.meshAnimation().sample("right_open", time)};
        double[] last = LAST_OPEN.computeIfAbsent(freezer, ignored -> open.clone());
        for (int i = count(random, LAYER_RATE * scale); i-- > 0; )
            spawn(level, master, facing, Kind.LAYER, between(random, WELL_X0 + .5, WELL_X1 - .5), between(random, 4.0, 5.6),
                    between(random, -WELL_Z + 1.2, WELL_Z - 1.2), drift(random), 0, drift(random));
        // each half opens from its outer end toward the seam: the master's lid slides to -x, the slave's to +x
        double masterEdge = MASTER_LID_X1 - LID_TRAVEL * open[0], slaveEdge = SLAVE_LID_X0 + LID_TRAVEL * open[1];
        openHalf(level, master, facing, random, scale, Math.max(masterEdge, SEAM), WELL_X1, 1);
        openHalf(level, master, facing, random, scale, WELL_X0, Math.min(slaveEdge, SEAM), -1);
        lidBurst(level, master, facing, random, (open[0] - last[0]) * LID_BURST * scale,
                Math.max(masterEdge, SEAM), Math.min(MASTER_LID_X1 - LID_TRAVEL * last[0], WELL_X1));
        lidBurst(level, master, facing, random, (open[1] - last[1]) * LID_BURST * scale,
                Math.max(SLAVE_LID_X0 + LID_TRAVEL * last[1], WELL_X0), Math.min(slaveEdge, SEAM));
        last[0] = open[0];
        last[1] = open[1];
    }

    /** The uncovered part of one half, x0..x1; outward: +1 when its outer end is the master's (+x), -1 the slave's. */
    private static void openHalf(Level level, BlockPos master, Direction facing, RandomSource random, double scale,
                                 double x0, double x1, int outward) {
        double width = x1 - x0;
        if (width < .5) return;
        double share = Math.min(1, width / HALF_WIDTH) * scale;
        for (int i = count(random, WISP_RATE * share); i-- > 0; )
            spawn(level, master, facing, Kind.WISP, between(random, x0 + .5, x1 - .5), between(random, 12.0, 13.8),
                    between(random, -WELL_Z + 1.2, WELL_Z - 1.2), drift(random), between(random, .002, .004), drift(random));
        // welling over the rim: born just above the rim's inner edge, it settles on the rim, slides off its outer edge and
        // runs down the outside, fading before the floor (source -z is the front)
        for (int i = count(random, SPILL_FRONT_RATE * share); i-- > 0; )
            spawn(level, master, facing, Kind.SPILL, between(random, x0 + .5, x1 - .5), between(random, RIM_TOP + .2, RIM_TOP + .5),
                    between(random, -WELL_Z - 1.4, -WELL_Z - .2), drift(random), 0, -between(random, .009, .013));
        for (int i = count(random, SPILL_BACK_RATE * share); i-- > 0; )
            spawn(level, master, facing, Kind.SPILL, between(random, x0 + .5, x1 - .5), between(random, RIM_TOP + .2, RIM_TOP + .5),
                    between(random, WELL_Z + .2, WELL_Z + 1.4), drift(random), 0, between(random, .009, .013));
        double end = outward > 0 ? WELL_X1 : WELL_X0;
        for (int i = count(random, SPILL_END_RATE * scale); i-- > 0; )
            spawn(level, master, facing, Kind.SPILL, end + outward * between(random, .2, 1.4), between(random, RIM_TOP + .2, RIM_TOP + .5),
                    between(random, -WELL_Z + 1.5, WELL_Z - 1.5), outward * between(random, .009, .013), 0, drift(random));
    }

    /** A lid sliding open: puffs rising from the strip x0..x1 it uncovered this tick, a third of them toward the front. */
    private static void lidBurst(Level level, BlockPos master, Direction facing, RandomSource random, double amount,
                                 double x0, double x1) {
        if (amount <= 0 || x1 - x0 < .05) return;
        for (int i = count(random, amount); i-- > 0; )
            spawn(level, master, facing, Kind.BURST, between(random, x0, x1), between(random, 13.0, 14.6),
                    between(random, -WELL_Z + 1.0, WELL_Z - 1.0), drift(random), between(random, .006, .012),
                    random.nextInt(3) == 0 ? -between(random, .004, .008) : drift(random));
    }

    /** Spawn rate factor: 0 out of range or at Minimal particles. */
    private static double scale(Level level, BlockPos master) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!level.isClientSide || minecraft.isPaused() || minecraft.player == null
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(master)) > RANGE * RANGE) return 0;
        return switch (minecraft.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> .5;
            case MINIMAL -> 0;
        };
    }

    /** Source px position, blocks/tick velocity (source axes: +x the viewer's left, -z the front). */
    private static void spawn(Level level, BlockPos master, Direction facing, Kind kind, double x, double y, double z,
                              double vx, double vy, double vz) {
        Vec3 at = MeshSourceFrame.toWorld(master, facing, x, y, z);
        Vec3 velocity = MeshSourceFrame.toWorldDirection(facing, vx, vy, vz);
        var particle = Minecraft.getInstance().particleEngine.createParticle(AflParticles.COLD_MIST.get(),
                at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
        if (!(particle instanceof ColdMistParticle mist)) return;
        RandomSource random = level.random;
        mist.configure(kind.life0 + random.nextInt(kind.life1 - kind.life0 + 1),
                kind.size0 + random.nextFloat() * (kind.size1 - kind.size0), kind.growth, kind.alpha,
                kind.gravity, kind.drag, kind.swirl);
    }

    private static int count(RandomSource random, double perTick) {
        int whole = (int) perTick;
        return whole + (random.nextDouble() < perTick - whole ? 1 : 0);
    }

    private static double between(RandomSource random, double from, double to) {
        return from + random.nextDouble() * (to - from);
    }

    private static double drift(RandomSource random) {
        return (random.nextDouble() - .5) * .004;
    }
}
