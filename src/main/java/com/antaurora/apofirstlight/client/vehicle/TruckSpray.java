package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ColdMistParticle;
import com.antaurora.apofirstlight.client.rain.RainRenderer;
import com.antaurora.apofirstlight.registry.AflParticles;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the T15's tyres throw (docs/vehicles/first_pickup_v0_design.md 8.18; user 2026-10-18, for the dev log): per wheel
 * each client tick, from the tyre's tread speed (its spin times the radius) and how much it slips against the ground.
 * <ul>
 *   <li>Loose ground (dirt, gravel, sand, grass, snow; TruckSounds#surface): chips of the block under the tyre thrown
 *   back and up against the tread's turn, more when it spins or slides; dry, a soft dust in the ground's colour (less on
 *   grass); wet, no dust (mud).</li>
 *   <li>Hard ground wet (rain on the truck, eased like the wet tyre sound): water drops off the tread and a fine mist,
 *   growing with speed, in AFL's rain look ({@link #water}; user 2026-10-18: vanilla's pixel splashes did not fit). Drawn
 *   by AFL's rain (RainRenderer.drawsSpray: no shader pack); with a shader pack that draws the rain (Aureveil, AFL_RAIN)
 *   the pack draws it from the wheel data in its trucks texture ({@link #packWheel}); any other pack: none.</li>
 *   <li>Hard ground dry: tyre smoke only when a tyre slips hard (a skid or a burnout).</li>
 * </ul>
 * The soft puffs of dust and smoke are {@link ColdMistParticle}s tinted and set per use; chips are vanilla's block
 * particles. Rates are per wheel per tick at the Particles: All setting (Decreased 0.4, Minimal none).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class TruckSpray {
    /** Trucks this close to the camera throw (blocks). */
    private static final double RANGE = 48;
    /** The tread speed (m/s) at which rolling throws its most, and the slip (m/s) at which slipping does. */
    private static final double ROLL_FULL = 13, SLIP_FULL = 5;
    /** Per wheel per tick at full: block chips, dust puffs; on wet hard ground mist puffs and drops; dry hard ground smoke. */
    private static final double CHIPS = 0.9, DUST = 0.7, MIST = 1.2, DROPS = 9, SMOKE = 1.2;
    /** Slip (m/s) where a dry hard surface starts to smoke. */
    private static final double SMOKE_SLIP = 4;
    /** Wet ease per tick (as TruckSounds' wet tyre sound). */
    private static final float WET_EASE = 0.02F;
    /** Ground colour of grass for its dust (the dirt under it, not the green). */
    private static final int GRASS_DUST = 0x86684A;

    /** Where on the back of the tyre the water leaves it (deg up from the ground), and the arch's limit on its rise (m/s). */
    private static final double FROM_ANGLE = 15, TO_ANGLE = 95, UP_MAX = 3.5;

    private static final Map<PickupTruckEntity, float[]> WET = new WeakHashMap<>();
    /** Per truck, per wheel this tick: water spray 0..1, its direction along forward (+1 / -1), tread speed (m/s). */
    private static final Map<PickupTruckEntity, float[]> PACK = new WeakHashMap<>();

    private TruckSpray() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        if (level == null || camera == null || mc.isPaused()) return;
        double density = switch (mc.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 0.4;
            case MINIMAL -> 0;
        };
        // Minimal: nothing of AFL's own, but the wheel data a shader pack draws from stays up to date
        for (Entity e : level.entitiesForRendering())
            if (e instanceof PickupTruckEntity truck && truck.distanceToSqr(camera) < RANGE * RANGE) spray(truck, level, density);
    }

    private static void spray(PickupTruckEntity truck, ClientLevel level, double density) {
        RandomSource random = level.random;
        double vx = (truck.getX() - truck.xo) * 20, vz = (truck.getZ() - truck.zo) * 20;
        Vec3 fwd = truck.frameDirection(1, 0, 0, 1.0F), right = truck.frameDirection(0, 1, 0, 1.0F);
        double along = vx * fwd.x + vz * fwd.z, lateral = Math.abs(vx * right.x + vz * right.z);
        float[] wet = WET.computeIfAbsent(truck, t -> new float[1]);
        float[] pack = PACK.get(truck);
        if (pack != null) java.util.Arrays.fill(pack, 0);
        BlockPos roof = BlockPos.containing(truck.getX(), truck.getY() + 2.5, truck.getZ());
        float raining = level.isRainingAt(roof) ? level.getRainLevel(1.0F) : 0;
        wet[0] += Mth.clamp(raining - wet[0], -WET_EASE, WET_EASE);
        Vec3 carry = new Vec3(vx / 20, 0, vz / 20);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            double[] w = TruckSounds.WHEELS[i];
            Vec3 under = truck.point(w[0], w[1], -0.05);
            p.set(Mth.floor(under.x), Mth.floor(under.y), Mth.floor(under.z));
            BlockState s = level.getBlockState(p);
            if (s.getCollisionShape(level, p).isEmpty()) s = level.getBlockState(p.move(0, -1, 0));
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            double tread = (truck.wheelSpin(i, 1.0F) - truck.wheelSpin(i, 0.0F)) * 20 * VehicleSpec.T15.radius;
            double slip = Math.sqrt((tread - along) * (tread - along) + lateral * lateral);
            double roll = Mth.clamp((Math.abs(tread) - 1) / ROLL_FULL, 0, 1), spin = Mth.clamp(slip / SLIP_FULL, 0, 1.5);
            if (roll <= 0 && spin <= 0.2) continue;
            // thrown against the tread's turn (a tyre standing still and sliding: against the slide)
            double dir = Math.abs(tread) > 0.3 ? -Math.signum(tread) : -Math.signum(along);
            if (dir == 0) dir = -1;
            Vec3 back = new Vec3(fwd.x * dir, 0, fwd.z * dir).normalize();
            Vec3 from = truck.point(w[0] + dir * 0.32, w[1] + Math.signum(w[1]) * 0.04, 0.12);
            double throwSpeed = Math.min(0.42, 0.08 + 0.014 * Math.min(Math.abs(tread), 25) + 0.04 * spin);
            int kind = TruckSounds.surface(s);
            if (kind != TruckSounds.ASPHALT) {
                boolean snow = s.is(BlockTags.SNOW), grass = kind == TruckSounds.GRASS;
                double amount = roll * (grass ? 0.35 : 1) + spin;
                for (int n = count(random, amount * CHIPS * density * (wet[0] > 0.3 ? 1.3 : 1)); n > 0; n--)
                    chip(level, random, s, from, back, right, carry, throwSpeed);
                if (wet[0] < 0.3 || snow) {
                    int col = grass ? GRASS_DUST : snow ? 0xF4F8FC : s.getMapColor(level, p).col;
                    for (int n = count(random, amount * DUST * density * (grass ? 0.4 : 1)); n > 0; n--)
                        puff(random, from, back, carry, throwSpeed * 0.5, col, 0.25F, 40, 70, 0.18F, 0.28F, 3.2F, 0.16F, -0.0002, 0.93, 0.003);
                }
            } else if (wet[0] > 0.05) {
                double amount = wet[0] * Mth.clamp((Math.abs(tread) - 2) / 18, 0, 1) + wet[0] * spin * 0.5;
                PACK.computeIfAbsent(truck, t -> new float[3 * 4])[i * 3] = (float) Math.min(1, amount);
                PACK.get(truck)[i * 3 + 1] = (float) dir;
                PACK.get(truck)[i * 3 + 2] = (float) Math.abs(tread);
                if (RainRenderer.drawsSpray()) water(truck, level, random, i, dir, Math.abs(tread), amount, density, vx, vz);
                continue;
            } else if (slip > SMOKE_SLIP) {
                double amount = Mth.clamp((slip - SMOKE_SLIP) / 8, 0, 1);
                for (int n = count(random, amount * SMOKE * density); n > 0; n--)
                    puff(random, from, back, carry, 0.04, 0xD8D8DA, 0, 50, 80, 0.2F, 0.3F, 3.5F, 0.2F, -0.0004, 0.94, 0.003);
            }
        }
    }

    /**
     * Water off a wet tyre, in AFL's rain look (client/rain/RainSplashes): drops leave the tread on the back of the tyre,
     * between FROM_ANGLE and TO_ANGLE up from the ground, along the tread there (back and up), with a share of its speed
     * (the rest stays on the tyre), plus the truck's own motion; the wheel arch stops the steepest (UP_MAX). Small drops,
     * strong drag: they hang behind a truck that drives on. And puffs of fine mist off the back of the tyre, dragged along a
     * little by the truck's wake, spreading. tread: m/s, dir: +1 / -1 along the frame's forward the water goes.
     */
    private static void water(PickupTruckEntity truck, ClientLevel level, RandomSource random, int wheel, double dir, double tread,
                              double amount, double density, double vx, double vz) {
        double[] w = TruckSounds.WHEELS[wheel];
        double side = Math.signum(w[1]), radius = VehicleSpec.T15.radius;
        Vec3 fwd = truck.frameDirection(1, 0, 0, 1.0F), right = truck.frameDirection(0, 1, 0, 1.0F), up = truck.frameDirection(0, 0, 1, 1.0F);
        Vec3 back = fwd.scale(dir), hub = truck.point(w[0], w[1], radius);
        double road = truck.point(w[0], w[1], 0).y - 0.02;
        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, BlockPos.containing(hub.x, hub.y + 1, hub.z));
        Vec3 carry = new Vec3(vx, 0, vz);   // m/s
        for (int n = count(random, amount * DROPS * density); n > 0; n--) {
            double a = Math.toRadians(FROM_ANGLE + (TO_ANGLE - FROM_ANGLE) * random.nextDouble());
            // the point a up the back of the tyre from the ground, and the tread's direction there (relative to the truck)
            Vec3 at = hub.add(back.scale(radius * Math.sin(a))).add(up.scale(-radius * Math.cos(a)))
                    .add(right.scale(side * (0.08 + 0.06 * random.nextDouble())));
            Vec3 along = back.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 v = along.scale(tread * (0.3 + 0.35 * random.nextDouble())).add(carry)
                    .add(right.scale(side * tread * (0.03 + 0.1 * random.nextDouble())));
            double vy = Math.min(v.y, UP_MAX);
            float size = 0.004F + random.nextFloat() * 0.006F;
            RainRenderer.sprayDrop(at.x, at.y, at.z, v.x / 20, vy / 20, v.z / 20, size, 0.9F + 0.06F * random.nextFloat(), road,
                    10 + random.nextInt(10), light);
        }
        for (int n = count(random, amount * MIST * density); n > 0; n--) {
            Vec3 at = hub.add(back.scale(radius + 0.1 + 0.25 * random.nextDouble())).add(up.scale(-radius * 0.5 + 0.3 * random.nextDouble()))
                    .add(right.scale(side * (0.05 + 0.15 * random.nextDouble())));
            Vec3 v = carry.scale(0.5 + 0.2 * random.nextDouble()).add(back.scale(0.3 + 0.6 * random.nextDouble()))
                    .add(right.scale(side * 0.4 * random.nextDouble())).add(0, 0.15 + 0.25 * random.nextDouble(), 0);
            RainRenderer.sprayMist(at.x, at.y, at.z, v.x / 20, v.y / 20, v.z / 20, 0.14F + 0.1F * random.nextFloat(),
                    (float) (0.13 * (0.4 + 0.6 * Math.min(1, amount))), random.nextFloat() * Mth.TWO_PI, road + 0.05, 28 + random.nextInt(22), light);
        }
    }

    /**
     * A shader pack's wheel data (client/rain/RainShaderPack, the trucks texture; docs/rendering/rain_v1.md 6 ③): for wheel
     * 0-3 (FL, FR, RL, RR) this frame, into out[0..7]: the tyre's contact point relative to the camera (x, y, z) and its
     * water spray 0..1; the direction the water goes (world unit x, y, z) and the tread speed (m/s). All 0 when it throws
     * no water.
     */
    public static void packWheel(PickupTruckEntity truck, int wheel, float partialTick, Vec3 cam, float[] out) {
        java.util.Arrays.fill(out, 0);
        float[] p = PACK.get(truck);
        if (p == null || p[wheel * 3] <= 0) return;
        double[] w = TruckSounds.WHEELS[wheel];
        Vec3 c = truck.framePoint(w[0], w[1], 0, partialTick), d = truck.frameDirection(p[wheel * 3 + 1], 0, 0, partialTick);
        out[0] = (float) (c.x - cam.x);
        out[1] = (float) (c.y - cam.y);
        out[2] = (float) (c.z - cam.z);
        out[3] = p[wheel * 3];
        out[4] = (float) d.x;
        out[5] = (float) d.y;
        out[6] = (float) d.z;
        out[7] = p[wheel * 3 + 2];
    }

    /** How wet the road under the truck is, 0..1 (eased; 0 for a truck not seen yet). */
    public static float wet(PickupTruckEntity truck) {
        float[] w = WET.get(truck);
        return w == null ? 0 : w[0];
    }

    /** A chip of the ground block, thrown back and up off the tyre. */
    private static void chip(ClientLevel level, RandomSource random, BlockState s, Vec3 from, Vec3 back, Vec3 right, Vec3 carry, double speed) {
        Vec3 v = back.scale(speed * (0.5 + 0.7 * random.nextDouble()))
                .add(right.scale((random.nextDouble() - 0.5) * 0.12))
                .add(0, 0.10 + 0.14 * random.nextDouble() + speed * 0.3, 0)
                .add(carry.scale(0.3));
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, s),
                from.x + (random.nextDouble() - 0.5) * 0.2, from.y, from.z + (random.nextDouble() - 0.5) * 0.2, 0, 0, 0);
        // the block particle scrambles the velocity it is given (Particle's constructor); set it after
        if (particle != null) particle.setParticleSpeed(v.x, v.y, v.z);
    }

    /** A soft puff (dust, mist, smoke): colour 0xRRGGBB lightened toward grey by {@code pale}, ColdMistParticle settings. */
    private static void puff(RandomSource random, Vec3 from, Vec3 back, Vec3 carry, double speed, int rgb, float pale, int life0, int life1,
                             float size0, float size1, float growth, float alpha, double gravity, double drag, double swirl) {
        Vec3 v = back.scale(speed * (0.5 + random.nextDouble())).add(0, 0.01 + 0.02 * random.nextDouble(), 0).add(carry.scale(0.4));
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(AflParticles.COLD_MIST.get(),
                from.x + (random.nextDouble() - 0.5) * 0.3, from.y, from.z + (random.nextDouble() - 0.5) * 0.3, v.x, v.y, v.z);
        if (!(particle instanceof ColdMistParticle mist)) return;
        mist.configure(life0 + random.nextInt(life1 - life0 + 1), size0 + random.nextFloat() * (size1 - size0), growth, alpha, gravity, drag, swirl);
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F, tint = 0.94F + 0.06F * random.nextFloat();
        mist.setColor(Mth.lerp(pale, r, 0.8F) * tint, Mth.lerp(pale, g, 0.8F) * tint, Mth.lerp(pale, b, 0.8F) * tint);
    }

    private static int count(RandomSource random, double perTick) {
        int whole = (int) perTick;
        return whole + (random.nextDouble() < perTick - whole ? 1 : 0);
    }
}
