package com.antaurora.apofirstlight.cooking;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.liquid.world.LiquidWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The water in a carried pail (Cooking V1 K1a, docs/gameplay/cooking_v1_plan.md 7): its surface sloshes as the hand
 * moves, and what rises over the rim spills, into the AFL water engine as parcels (the server's, {@link #tick}); each
 * client runs the same model for the surface it draws (client/cooking/PailClient).
 * <p>
 * The surface is one tilting plane, the pail's first sloshing mode: its gradient (tx, tz, m/m, world x / z) is a damped
 * oscillator at the mode's own frequency (ω² = g k tanh(k d), k = 1.841 / r: about 11.4 rad/s, 1.8 Hz, in this pail),
 * driven toward -a / g by the hand's horizontal acceleration a. The hand is the player's side (the main arm) at hip
 * height, turning with the body; the arm takes up most of a jolt ({@link #ARM}). Walking rocks the hand once a step, near
 * the mode's own frequency, so a full pail builds up to the rim in a few strides (user 2026-10-27 "过八成就会洒": past
 * 10 of the 12 L walking spills; sneaking carries it full); landing from a jump knocks it.
 * <p>
 * Where the plane stands over the rim it flows out as over a weir (q = 0.42 √(2g) e^1.5 per metre of lip), on the side the
 * crest is; pouring ({@link WaterPailItem}) holds the model still. Offline (the same model, 2026-10-27): walking, a 12 L
 * pail loses 1.4 L in 5 s and settles near 10.2 L; sprinting near 9.9 L; 10 L walked 20 s loses 0.1 L; sneaking keeps 12 L.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PailSlosh {
    /** The pail (m): outer radius at the foot and the rim, the rim's height, the wall (the generator's PAIL, in cm). */
    public static final double RB = 0.132, RT = 0.142, H = 0.23, WALL = 0.002;
    static final double G = 9.81;
    /** The share of the hand's acceleration the water feels (the arm and the bail's swing take the rest), and its cap (m/s²). */
    static final double ARM = 0.25, A_MAX = 3.0;
    /** Damping ratio of the mode (a smooth enamel wall: little); more while it spills over the rim. */
    static final double ZETA = 0.1, ZETA_SPILL = 0.15;
    /** The walking rock: its amplitude per m/s of speed (m/s² along the way), the step rate (Hz) at a speed v: 1.43 + 0.085 v. */
    static final double STEP_ACCEL = 0.17, STEP_F0 = 1.43, STEP_FV = 0.085;
    /** The knock of a landing, rad/s per m/s of the fall. */
    static final double LANDING = 0.15;
    /** The hand: out from the body's middle (m, to the main arm's side), and its height over the feet (the grip). */
    static final double HAND_SIDE = 0.36, HAND_UP = 0.72;
    /** The rim under the grip (m): the bail stood up (grip centre 34.8 cm, the rim 23 cm). */
    public static final double GRIP_OVER_RIM = 0.118;
    /** Spilled water goes into the engine in parcels of at least this much (mL), or when it stops spilling. */
    static final long PARCEL_ML = 120;
    private static final int SUBSTEPS = 5;

    private PailSlosh() {
    }

    /** The pail's inside radius (m) at a height y over its foot. */
    public static double radius(double y) {
        return RB + (RT - RB) * y / H - WALL;
    }

    /** The water's height (m, over the pail's foot) when it holds ml, standing level. */
    public static double level(double ml) {
        double v = ml / 1e6, y = WALL;
        // up the frustum in 2 mm slices: cheap and exact enough
        for (int i = 0; i < 140 && v > 0; i++) {
            double r = radius(y + 0.001), slice = Math.PI * r * r * 0.002;
            if (slice >= v) return y + 0.002 * v / slice;
            v -= slice;
            y += 0.002;
        }
        return Math.min(y, H);
    }

    /** One pail's water surface: its gradient, the gradient's rate, the hand it hangs from. */
    public static final class Sim {
        public double tx, tz;
        double vx, vz;
        /** The hand's last two positions (world), the walk's phase (rad), whether it was airborne and how fast it fell. */
        private Vec3 p1, p2;
        private double phase, fall;
        private boolean air;
        /** Spilled and not yet sent (whole mL), the part of a mL not yet taken off the stack, the last crest (world, m) and the hand's velocity then. */
        double pending, frac;
        Vec3 crest = Vec3.ZERO, handVelocity = Vec3.ZERO;
        double crestX, crestZ;

        /** The surface's tilt (rad) toward its crest. */
        public double tilt() {
            return Math.atan(Math.hypot(tx, tz));
        }

        /**
         * One tick (1/20 s) of the hand at {@code hand} (world), on the ground or not, falling at vy (m/s, world), the pail
         * holding ml. Returns mL that went over the rim.
         */
        public double step(Vec3 hand, boolean onGround, double vy, double ml) {
            if (p1 == null || hand.distanceToSqr(p1) > 4) {   // first tick, or a teleport: start level
                p1 = p2 = hand;
                tx = tz = vx = vz = 0;
                air = !onGround;
                return 0;
            }
            double dt = 0.05;
            Vec3 v = hand.subtract(p1).scale(1 / dt), a = hand.subtract(p1.scale(2)).add(p2).scale(1 / (dt * dt));
            p2 = p1;
            p1 = hand;
            handVelocity = v;
            double ax = a.x * ARM, az = a.z * ARM, al = Math.hypot(ax, az);
            if (al > A_MAX) {
                ax *= A_MAX / al;
                az *= A_MAX / al;
            }
            // the walk: the hand rocks along the way once a step
            double speed = Math.hypot(v.x, v.z);
            if (onGround && speed > 0.3) {
                double f = STEP_F0 + STEP_FV * speed, rock = STEP_ACCEL * Math.min(speed, 7) * Math.sin(phase);
                phase = (phase + 2 * Math.PI * f * dt) % (2 * Math.PI);
                ax += rock * v.x / speed;
                az += rock * v.z / speed;
            }
            // a landing knocks the water along the way (or to the side the pail hangs)
            if (air && onGround && fall > 1.5) {
                double dx = speed > 0.3 ? v.x / speed : 0, dz = speed > 0.3 ? v.z / speed : 1;
                vx += LANDING * fall * dx;
                vz += LANDING * fall * dz;
            }
            air = !onGround;
            fall = onGround ? 0 : Math.max(fall, -vy);
            double h = level(ml), r = radius(h), depth = Math.max(0.01, h - WALL), k = 1.841 / r;
            double w = Math.sqrt(G * k * Math.tanh(k * depth));
            double spilled = 0, sub = dt / SUBSTEPS;
            for (int i = 0; i < SUBSTEPS; i++) {
                double th = Math.hypot(tx, tz), e = h + th * r - H, zeta = ZETA;
                if (e > 0 && th > 1e-6) {
                    // over the rim on the crest side: a weir along the lip the plane stands over
                    double u = Math.min(2 * r, e / th), lip = 2 * Math.sqrt(Math.max(0, u * (2 * r - u)));
                    double q = 0.42 * Math.sqrt(2 * G) * Math.pow(e, 1.5) * lip;   // m³/s
                    double out = Math.min(ml - spilled, q * sub * 1e6);
                    spilled += out;
                    crestX = tx / th;
                    crestZ = tz / th;
                    zeta = ZETA_SPILL;
                }
                // semi-implicit: the rate first, then the gradient
                vx += (-w * w * (tx + ax / G) - 2 * zeta * w * vx) * sub;
                vz += (-w * w * (tz + az / G) - 2 * zeta * w * vz) * sub;
                tx += vx * sub;
                tz += vz * sub;
            }
            // a surface steeper than this has broken anyway
            double th = Math.hypot(tx, tz);
            if (th > 1.2) {
                tx *= 1.2 / th;
                tz *= 1.2 / th;
            }
            return spilled;
        }
    }

    /** Where the hand of a player carrying a pail is (world): the main arm's side at the grip's height, turning with the body. */
    public static Vec3 hand(Player player, float partial) {
        double yaw = Math.toRadians(Mth.lerp(partial, player.yBodyRotO, player.yBodyRot));
        double side = player.getMainArm() == HumanoidArm.RIGHT ? HAND_SIDE : -HAND_SIDE;
        // the body's right, from its yaw (as FuelCanItem#spout)
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Vec3 at = player.getPosition(partial);
        return new Vec3(at.x + rx * side, at.y + HAND_UP, at.z + rz * side);
    }

    private static final Map<UUID, Sim> SERVER = new HashMap<>();

    /** The server's model: spills go into the engine; the client's surface is its own (client/cooking/PailClient). */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        long ml = stack.getItem() instanceof WaterPailItem ? WaterPailItem.water(stack) : 0;
        if (ml <= 0 || !player.isAlive() || player.isSpectator() || player.isUsingItem()) {
            SERVER.remove(player.getUUID());
            return;
        }
        Sim sim = SERVER.computeIfAbsent(player.getUUID(), k -> new Sim());
        Vec3 hand = hand(player, 1);
        double out = sim.step(hand, player.onGround(), player.getDeltaMovement().y * 20, ml);
        if (out > 0) {
            sim.frac += out;
            long whole = Math.min(ml, (long) Math.floor(sim.frac));
            sim.frac -= whole;
            sim.pending += whole;
            if (whole > 0) WaterPailItem.setWater(stack, ml - whole);
            double r = radius(H);
            sim.crest = new Vec3(hand.x + sim.crestX * (r + 0.01), hand.y - GRIP_OVER_RIM, hand.z + sim.crestZ * (r + 0.01));
        }
        if (sim.pending >= PARCEL_ML || (sim.pending >= 1 && out <= 0)) {
            launch(player.serverLevel(), sim.crest, sim.handVelocity.add(sim.crestX * 0.6, 0.1, sim.crestZ * 0.6), Math.round(sim.pending), player.getId());
            sim.pending = 0;
        }
    }

    /** Water thrown from a pail into the engine (world position and velocity); lost when the engine has no column there. */
    static void launch(ServerLevel level, Vec3 at, Vec3 velocity, long ml, int source) {
        if (ml <= 0) return;
        LiquidEngine engine = LiquidWorld.engine(level);
        if (engine != null) engine.launch(at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, ml, source);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SERVER.remove(event.getEntity().getUUID());
    }
}
