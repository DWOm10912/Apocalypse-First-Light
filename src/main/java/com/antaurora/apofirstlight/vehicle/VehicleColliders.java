package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Vehicle collision while parked (docs/vehicles/first_pickup_v0_design.md section 8.2): an entity's box is square and
 * axis-aligned, so a vehicle gives its own world boxes (rotated local boxes cut into thin strips, {@link #strips}) and
 * mixin/EntityVehicleCollisionMixin adds the ones near a moving entity to the entity collision list Entity#collide works
 * with; vanilla walking, jumping and stepping up then treat them like blocks. Vehicles register here while they are in a
 * level (both sides; the client moves its own player).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class VehicleColliders {
    /** A vehicle with collision boxes. */
    public interface Collider {
        /** All of its boxes (world). */
        List<AABB> collisionBoxes();

        /** A box around all of them (world). */
        AABB collisionBounds();

        /** Whether this entity goes through it (the vehicle itself, its parts, who rides it). */
        boolean passesThrough(Entity entity);
    }

    /** Strip width (blocks) a box turned off the axes is cut into: its sides become steps this wide. */
    public static final double STRIP = 0.125;
    private static final Map<LevelAccessor, List<Collider>> LEVELS = new ConcurrentHashMap<>();

    private VehicleColliders() {
    }

    public static void add(Level level, Collider collider) {
        LEVELS.computeIfAbsent(level, l -> new CopyOnWriteArrayList<>()).add(collider);
    }

    public static void remove(Level level, Collider collider) {
        List<Collider> list = LEVELS.get(level);
        if (list != null) list.remove(collider);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        LEVELS.remove(event.getLevel());
    }

    /** The entity collision list of a move (Entity#collide) with the vehicle boxes in this query added. */
    public static List<VoxelShape> withVehicles(Entity entity, AABB query, List<VoxelShape> list) {
        List<Collider> colliders = LEVELS.get(entity.level());
        if (colliders == null || colliders.isEmpty()) return list;
        // stepping up looks up to the step height above the move
        AABB q = query.inflate(0.05).expandTowards(0, Math.max(0.6, entity.getStepHeight()) + 0.05, 0);
        List<VoxelShape> out = null;
        for (Collider c : colliders) {
            if (!c.collisionBounds().intersects(q) || c.passesThrough(entity)) continue;
            for (AABB box : c.collisionBoxes()) {
                if (!box.intersects(q)) continue;
                if (out == null) out = new ArrayList<>(list);
                out.add(Shapes.create(box));
            }
        }
        return out == null ? list : out;
    }

    /** Whether something stands on a vehicle (the server's floating check: no blocks under it does not mean flying). */
    public static boolean standsOn(Entity entity) {
        List<Collider> colliders = LEVELS.get(entity.level());
        if (colliders == null || colliders.isEmpty()) return false;
        AABB q = entity.getBoundingBox().inflate(0.0625).expandTowards(0, -0.55, 0);
        for (Collider c : colliders) {
            if (!c.collisionBounds().intersects(q) || c.passesThrough(entity)) continue;
            for (AABB box : c.collisionBoxes()) if (box.intersects(q)) return true;
        }
        return false;
    }

    /** Whether a world box overlaps any vehicle box (dismount spots); `ignore` is let through as for its own moves. */
    public static boolean blocked(Level level, AABB box, Entity ignore) {
        List<Collider> colliders = LEVELS.get(level);
        if (colliders == null) return false;
        for (Collider c : colliders) {
            if (!c.collisionBounds().intersects(box) || c.passesThrough(ignore)) continue;
            for (AABB b : c.collisionBoxes()) if (b.intersects(box)) return true;
        }
        return false;
    }

    /**
     * A box in a vehicle's frame turned to the world: the rectangle forward f0..f1, right r0..r1 (metres) at (x, z) with
     * the heading yaw (degrees; forward = (-sin, cos), right = (-cos, -sin)), heights y0..y1 (world). Square to the axes
     * (within 0.06 degrees) it is one box; otherwise strips {@link #STRIP} wide along world x, each as wide in z as the
     * rectangle reaches inside it (never smaller than the real shape).
     */
    public static void strips(double x, double z, float yaw, double f0, double f1, double r0, double r1, double y0, double y1, List<AABB> out) {
        double a = Math.toRadians(yaw), s = Math.sin(a), c = Math.cos(a);
        double[] px = new double[4], pz = new double[4];
        double[][] corner = {{f0, r0}, {f1, r0}, {f1, r1}, {f0, r1}};   // in order around the rectangle
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double f = corner[i][0], r = corner[i][1];
            px[i] = x - s * f - c * r;
            pz[i] = z + c * f - s * r;
            minX = Math.min(minX, px[i]);
            maxX = Math.max(maxX, px[i]);
            minZ = Math.min(minZ, pz[i]);
            maxZ = Math.max(maxZ, pz[i]);
        }
        if (Math.abs(s * c) < 1e-3) {
            out.add(new AABB(minX, y0, minZ, maxX, y1, maxZ));
            return;
        }
        int n = Math.max(1, (int) Math.ceil((maxX - minX) / STRIP - 1e-6));
        double w = (maxX - minX) / n;
        for (int k = 0; k < n; k++) {
            double xa = minX + k * w, xb = k == n - 1 ? maxX : xa + w;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                if (px[i] >= xa && px[i] <= xb) {
                    lo = Math.min(lo, pz[i]);
                    hi = Math.max(hi, pz[i]);
                }
                int j = (i + 1) % 4;
                double dx = px[j] - px[i];
                if (Math.abs(dx) < 1e-9) continue;
                for (double xe : new double[]{xa, xb}) {
                    double t = (xe - px[i]) / dx;
                    if (t < 0 || t > 1) continue;
                    double ze = pz[i] + t * (pz[j] - pz[i]);
                    lo = Math.min(lo, ze);
                    hi = Math.max(hi, ze);
                }
            }
            if (hi > lo) out.add(new AABB(xa, y0, lo, xb, y1, hi));
        }
    }
}
