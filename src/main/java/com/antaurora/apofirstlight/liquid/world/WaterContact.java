package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.liquid.WaterQuery;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;

/**
 * Bodies in AFL's water (docs/gameplay/water_v1_design.md 17, package 4), on both sides from each side's WaterQuery, after
 * vanilla's own fluid pass over the box (EntityAflWaterMixin):
 * <ul>
 *   <li>deep enough to float ({@link #swimFrom}: 45% of the body's height, 0.1 m at least): the water counts as vanilla
 *   water at its depth, so swimming, buoyancy, drowning, splashes, fall damage and fire go as in vanilla water (an item
 *   floats over 0.1 m, as on a water block);</li>
 *   <li>shallower: wading, the body's horizontal speed eased by the depth (a quarter off at the swimming depth);</li>
 *   <li>where the water runs: pushed with it, by how much of the body is in it; a floating body drifts at about the
 *   water's speed (a player at 80%, an item at it), one wading is only leaned on.</li>
 * </ul>
 * Boats and their riders are left to vanilla (they do not float on AFL's water yet).
 */
public final class WaterContact {
    /** Wading at the swimming depth takes this off the horizontal speed each tick. */
    static final double WADE = 0.3;
    /** How fast a floating body takes up the water's speed (of the difference, a tick, the body all in). */
    static final double DRAG = 0.05;
    /** Floating, what makes up for the body's own friction in water: living 0.8 a tick, items 0.99. */
    static final double CARRY_LIVING = 0.2, CARRY_OTHER = 0.01;
    /** Wading, the push of the current (of its speed, a tick, the body all in). */
    static final double LEAN = 0.25;
    /** Slower water pushes nothing (m/s). */
    static final double STILL = 0.05;

    private static final WaterQuery.Immersion SERVER = new WaterQuery.Immersion(), CLIENT = new WaterQuery.Immersion();

    private WaterContact() {}

    /** From this depth (m) a body of this height floats. */
    public static double swimFrom(double height) {
        return Math.max(0.1, 0.45 * height);
    }

    /** After vanilla's fluid pass: heights is the entity's fluid heights (Forge), filled with the water it floats in. */
    public static void touch(Entity e, Object2DoubleMap<FluidType> heights) {
        if (e instanceof Boat || e.getVehicle() instanceof Boat || e.isSpectator()) return;
        AABB box = e.getBoundingBox().deflate(0.001);
        WaterQuery.Immersion im = e.level().isClientSide() ? CLIENT : SERVER;
        if (!WaterQuery.immersion(e.level(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, im)) return;
        double size = Math.max(0.05, box.getYsize()), h = im.height, from = swimFrom(size), f = Math.min(1, h / size);
        FluidType water = ForgeMod.WATER_TYPE.get();
        boolean swim = h >= from;
        if (swim) {
            if (heights.getDouble(water) < h) heights.put(water, h);
        } else if (e instanceof LivingEntity && !e.isPassenger() && !(e instanceof Player p && p.getAbilities().flying)) {
            double m = 1 - WADE * h / from;
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * m, v.y, v.z * m);
            if (h >= 0.25 * size) e.clearFire();
        }
        if (Math.hypot(im.vx, im.vz) < STILL || !e.isPushedByFluid(water) || e.isPassenger()) return;
        if (e instanceof Player p && p.getAbilities().flying) return;
        double ux = im.vx / 20, uz = im.vz / 20;   // blocks a tick
        Vec3 v = e.getDeltaMovement();
        double dx, dz;
        if (swim) {
            double carry = e instanceof LivingEntity ? CARRY_LIVING : CARRY_OTHER;
            dx = f * (carry * ux + DRAG * (ux - v.x));
            dz = f * (carry * uz + DRAG * (uz - v.z));
        } else {
            dx = f * LEAN * ux;
            dz = f * LEAN * uz;
        }
        e.setDeltaMovement(v.x + dx, v.y, v.z + dz);
    }

    /** Are the eyes under AFL water (as vanilla asks, a ninth of a block under the eye)? */
    public static boolean eyesUnder(Entity e) {
        if (e.getVehicle() instanceof Boat || e.isSpectator()) return false;
        return !Double.isNaN(WaterQuery.surfaceAt(e.level(), e.getX(), e.getEyeY() - 0.11111111F, e.getZ()));
    }
}
