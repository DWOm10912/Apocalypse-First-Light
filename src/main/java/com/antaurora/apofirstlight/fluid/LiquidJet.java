package com.antaurora.apofirstlight.fluid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A jet of liquid as a chain of parcels (2026-10-05; first user: the fuel nozzle, docs/models/fuel_dispenser_v1.md "滋油";
 * meant for any jet or spurt: a water dispenser's tap, blood from a hit). The source emits parcels in a row, each with its
 * own velocity, and every parcel flies its own parabola under gravity until it meets a block or a liquid's surface (or ages
 * out). Drawn as a tube through consecutive parcels, the jet therefore shoots out when it starts, its tail falls away when
 * it stops, and it bends and lags when the source swings, like a real stream. Parcel ids run in emission order; a gap
 * ({@link #gap}) breaks the chain. Units: blocks, seconds; {@link #tick} is one game tick (two {@link #STEP} sub-steps).
 */
public final class LiquidJet {
    /** Earth gravity, blocks (metres) a second squared. */
    public static final double EARTH_GRAVITY = 9.8;
    /** Sub-step (seconds); a game tick is two. */
    public static final double STEP = 0.025;

    public static final class Parcel {
        public final long id;
        /** Position now and one tick ago (the renderer interpolates), velocity (blocks a second), age (seconds). */
        public Vec3 pos, prev, vel;
        public double age;

        private Parcel(long id, Vec3 pos, Vec3 vel) {
            this.id = id;
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
        }
    }

    /** A parcel meeting a block: where and on which face. */
    public interface Landing {
        void land(Parcel parcel, BlockHitResult hit);
    }

    private final Deque<Parcel> parcels = new ArrayDeque<>();
    private final double gravity, maxAge;
    private long nextId;

    public LiquidJet(double gravity, double maxAge) {
        this.gravity = gravity;
        this.maxAge = maxAge;
    }

    /** A parcel leaving {@code pos} with {@code velocity}, already {@code lead} seconds into its flight (emitted mid-tick). */
    public void emit(Vec3 pos, Vec3 velocity, double lead) {
        Parcel parcel = new Parcel(nextId++, pos.add(velocity.scale(lead)).add(0, -0.5 * gravity * lead * lead, 0), velocity.add(0, -gravity * lead, 0));
        parcel.age = lead;
        parcel.prev = parcel.pos;
        parcels.addLast(parcel);
    }

    /** The source stopped: the next parcel starts a new chain. */
    public void gap() {
        nextId++;
    }

    /** The id the next parcel will get (the chain's head follows the newest parcel only while this is newest + 1). */
    public long nextId() {
        return nextId;
    }

    /** One game tick: every parcel flies two sub-steps; one that meets a block lands there and is gone. */
    public void tick(Level level, @org.jetbrains.annotations.Nullable Entity source, Landing landing) {
        for (Iterator<Parcel> it = parcels.iterator(); it.hasNext(); ) {
            Parcel p = it.next();
            p.prev = p.pos;
            boolean landed = false;
            for (int s = 0; s < 2 && !landed; s++) {
                Vec3 next = p.pos.add(p.vel.scale(STEP));
                BlockHitResult hit = level.clip(new ClipContext(p.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, source));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    p.pos = hit.getLocation();
                    landing.land(p, hit);
                    landed = true;
                } else {
                    p.pos = next;
                    p.vel = p.vel.add(0, -gravity * STEP, 0);
                }
            }
            p.age += 2 * STEP;
            if (landed || p.age > maxAge || p.pos.y < level.getMinBuildHeight()) it.remove();
        }
    }

    /** Oldest first. */
    public Deque<Parcel> parcels() {
        return parcels;
    }

    public boolean isEmpty() {
        return parcels.isEmpty();
    }
}
