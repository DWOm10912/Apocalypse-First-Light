package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Fire smoke and sparks on this client (2026-10-05, docs/gameplay/fuel_fire_v1.md "火焰效果 V2"): one place every fire
 * spawns them from (burning fuel, bullet holes, fire blocks, burning things, fuel blasts), instead of vanilla's pixel smoke.
 * <ul>
 *   <li>Smoke: puffs from the Blender smoke sheet (textures/effect/fire_smoke, tools/build-fire-fx-v1.mjs), tinted to their
 *   shade (oil smoke near black, wood smoke grey), lit by the world where they are. A puff fades in, swells to
 *   {@link #GROWTH} times its size, rises on its own heat (slowing; stopped under a ceiling), drifts with a slow common
 *   wind, thins out and greys as it rises, fades over the second half of its life. Turned to the camera, only a little
 *   rolled (the puffs are lit from above).</li>
 *   <li>Sparks: small soft glows (textures/effect/fire_glow), full bright and added, white-yellow cooling to red as they
 *   flutter up (a blast's fly out and fall).</li>
 *   <li>A fuel blast ({@link #blast}): a burst of black smoke and sparks, then a column of smoke for a few seconds.</li>
 *   <li>Sparks off steel ({@link #metalSparks}, 2026-10-05: in place of vanilla's lava pops): hot streaks
 *   (textures/effect/spark, emissive) drawn along their flight, thrown out of the struck face, falling and bouncing,
 *   white cooling to orange, gone in under a second; a short flash where the bullet struck.</li>
 * </ul>
 * V2.3 (2026-10-05): drawn here, in the fuel stains' world pass (ClientFuelStains), not as particles: Sundial drew no
 * translucent particles at all, and the number is capped ({@link #MAX_PUFFS}, {@link #MAX_SPARKS}; spawns over the cap are
 * dropped): with every stain and fire block smoking on its own, a large fire had stacked a thousand big translucent
 * puffs and cost a lot of frame time (user). Thinner at the decreased / minimal particle settings.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FireFx {
    /** Smoke shades (grey level at birth; it greys towards 0.5 as it thins): oil fires black, wood and flesh grey. */
    public static final float GASOLINE_SMOKE = 0.13F, DIESEL_SMOKE = 0.07F, WOOD_SMOKE = 0.38F, BODY_SMOKE = 0.3F;
    static final int MAX_PUFFS = 300, MAX_SPARKS = 400;
    private static final float GROWTH = 2.3F;
    private static final double RANGE = 96.0;

    private static final class Puff {
        double x, y, z, ox, oy, oz, vx, vy, vz;
        float size, shade, peak, roll, oldRoll, spin, alpha, grey;
        int life, age, sprite;
    }

    private static final class Spark {
        double x, y, z, ox, oy, oz, vx, vy, vz;
        float size, gravity;
        int life, age;
    }

    /** A spark struck off steel: a hot streak along its flight, falling, bouncing off what it meets. */
    private static final class Streak {
        double x, y, z, ox, oy, oz, vx, vy, vz;
        float width;
        int life, age;
    }

    /** The flash where a bullet struck sparks. */
    private record Flash(Vec3 at, float size, long born) {}

    private record Plume(Vec3 at, float power, long end) {}

    static final int MAX_STREAKS = 200;
    private static final int FLASH_TICKS = 3;
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Plume> PLUMES = new ArrayList<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private FireFx() {
    }

    static boolean isEmpty() {
        return PUFFS.isEmpty() && SPARKS.isEmpty() && STREAKS.isEmpty() && FLASHES.isEmpty();
    }

    static int puffCount() {
        return PUFFS.size();
    }

    static int sparkCount() {
        return SPARKS.size() + STREAKS.size();
    }

    /** False now and then at reduced particle settings (decreased: half, minimal: one in five). */
    private static boolean allowed(RandomSource random) {
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        return status == ParticleStatus.ALL || status == ParticleStatus.DECREASED && random.nextBoolean() || random.nextInt(5) == 0;
    }

    /**
     * A smoke puff at x, y, z: {@code size} its starting half-size (blocks; it swells 2.3 times), {@code shade} its grey,
     * {@code alpha} at its thickest, {@code life} ticks, {@code rise} its first upward speed (blocks a tick).
     */
    public static void smoke(ClientLevel level, double x, double y, double z, float size, float shade, float alpha, int life, double rise) {
        if (PUFFS.size() >= MAX_PUFFS || !allowed(level.random)) return;
        RandomSource r = level.random;
        puff(x, y, z, (r.nextDouble() - 0.5) * 0.01, rise, (r.nextDouble() - 0.5) * 0.01, size * (0.85F + 0.3F * r.nextFloat()), shade, alpha,
                life + r.nextInt(Math.max(1, life / 3)), r);
    }

    private static void puff(double x, double y, double z, double vx, double vy, double vz, float size, float shade, float alpha, int life, RandomSource r) {
        Puff p = new Puff();
        p.x = p.ox = x;
        p.y = p.oy = y;
        p.z = p.oz = z;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.size = size;
        p.shade = p.grey = shade;
        p.peak = alpha;
        p.life = life;
        p.roll = p.oldRoll = (r.nextFloat() - 0.5F) * 0.7F;
        p.spin = (r.nextFloat() - 0.5F) * 0.008F;
        p.sprite = r.nextInt(8);
        PUFFS.add(p);
    }

    /** A spark at x, y, z flying at vx, vy, vz (blocks a tick); {@code gravity} negative rises. */
    public static void ember(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float gravity, int life) {
        if (SPARKS.size() >= MAX_SPARKS || !allowed(level.random)) return;
        RandomSource r = level.random;
        Spark s = new Spark();
        s.x = s.ox = x;
        s.y = s.oy = y;
        s.z = s.oz = z;
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.size = 0.018F + 0.014F * r.nextFloat();
        s.gravity = gravity;
        s.life = life + r.nextInt(Math.max(1, life / 2));
        SPARKS.add(s);
    }

    /** A rising spark from a flame at x, y, z. */
    public static void ember(ClientLevel level, double x, double y, double z) {
        RandomSource r = level.random;
        ember(level, x, y, z, (r.nextDouble() - 0.5) * 0.04, 0.05 + r.nextDouble() * 0.08, (r.nextDouble() - 0.5) * 0.04, -0.006F, 20);
    }

    /**
     * Sparks struck off steel by a bullet at {@code at} on a face with normal {@code n} (client/BulletHoles#impact, when the
     * server says it sparked): 6 to 12 streaks thrown out of the face, a flash where it struck.
     */
    public static void metalSparks(ClientLevel level, Vec3 at, Vec3 n) {
        RandomSource r = level.random;
        int count = 6 + r.nextInt(7);
        for (int i = 0; i < count && STREAKS.size() < MAX_STREAKS; i++) {
            Vec3 d = n.scale(0.5 + 0.5 * r.nextDouble()).add(r.nextGaussian() * 0.55, r.nextGaussian() * 0.55 + 0.25, r.nextGaussian() * 0.55)
                    .normalize().scale(0.2 + r.nextDouble() * 0.35);
            Streak s = new Streak();
            s.x = s.ox = at.x;
            s.y = s.oy = at.y;
            s.z = s.oz = at.z;
            s.vx = d.x;
            s.vy = d.y;
            s.vz = d.z;
            s.width = 0.008F + 0.008F * r.nextFloat();
            s.life = 5 + r.nextInt(10);
            STREAKS.add(s);
        }
        FLASHES.add(new Flash(at, 0.1F + 0.05F * r.nextFloat(), level.getGameTime()));
    }

    /** A fuel container went up (AflNetwork.FuelBlastS2CPacket): a burst of black smoke and sparks, then a column of smoke. */
    public static void blast(Vec3 at, float power, boolean diesel) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = level.random;
        float shade = diesel ? DIESEL_SMOKE : GASOLINE_SMOKE;
        int puffs = Math.min(10 + Math.round(power * 5), MAX_PUFFS - PUFFS.size());
        for (int i = 0; i < puffs; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * 0.7, r.nextGaussian()).normalize();
            Vec3 p = at.add(d.scale(power * 0.45 * Math.cbrt(r.nextDouble())));
            puff(p.x, p.y, p.z, d.x * 0.06, 0.04 + d.y * 0.06, d.z * 0.06, 0.5F + power * 0.12F * (0.8F + 0.4F * r.nextFloat()), shade, 0.8F,
                    110 + r.nextInt(60), r);
        }
        int sparks = 20 + Math.round(power * 10);
        for (int i = 0; i < sparks; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) + 0.3, r.nextGaussian()).normalize().scale(0.2 + r.nextDouble() * 0.35);
            ember(level, at.x, at.y + 0.5, at.z, d.x, d.y, d.z, 0.04F, 30);
        }
        PLUMES.add(new Plume(at, power, level.getGameTime() + 120 + Math.round(power * 10)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            PUFFS.clear();
            SPARKS.clear();
            STREAKS.clear();
            FLASHES.clear();
            PLUMES.clear();
            trackedLevel = level;
        }
        if (level == null || minecraft.isPaused()) return;
        RandomSource r = level.random;
        double wind = level.getGameTime() * 0.0002, windX = Math.cos(wind) * 0.0004, windZ = Math.sin(wind) * 0.0003;
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (Puff p : PUFFS) {
            p.ox = p.x;
            p.oy = p.y;
            p.oz = p.z;
            p.oldRoll = p.roll;
            p.age++;
            p.vx = p.vx * 0.96 + windX + (r.nextDouble() - 0.5) * 0.002;
            p.vz = p.vz * 0.96 + windZ + (r.nextDouble() - 0.5) * 0.002;
            p.vy = p.vy * 0.975 + 0.0012;   // buoyant: settles at about 0.05 a tick
            if (p.vy > 0 && !level.getBlockState(above.set(p.x, p.y + p.vy + 0.2, p.z)).getCollisionShape(level, above).isEmpty()) {
                p.vy = 0;   // under a ceiling: it spreads instead
                p.vx += (r.nextDouble() - 0.5) * 0.01;
                p.vz += (r.nextDouble() - 0.5) * 0.01;
            }
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.roll += p.spin;
            float t = (float) p.age / p.life, out = Mth.clamp((t - 0.35F) / 0.65F, 0.0F, 1.0F);
            p.alpha = p.peak * Math.min(1.0F, t / 0.12F) * (1.0F - out * out * (3.0F - 2.0F * out));
            p.grey = p.shade + (0.5F - p.shade) * t * 0.6F;
        }
        PUFFS.removeIf(p -> p.age >= p.life);
        for (Spark s : SPARKS) {
            s.ox = s.x;
            s.oy = s.y;
            s.oz = s.z;
            s.age++;
            s.vx = s.vx * 0.96 + (r.nextDouble() - 0.5) * 0.012;
            s.vz = s.vz * 0.96 + (r.nextDouble() - 0.5) * 0.012;
            s.vy = (s.vy - 0.04 * s.gravity) * 0.96;
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
        }
        SPARKS.removeIf(s -> s.age >= s.life);
        for (Streak s : STREAKS) {   // falling, dragged, bouncing off blocks
            s.ox = s.x;
            s.oy = s.y;
            s.oz = s.z;
            s.age++;
            s.vx *= 0.92;
            s.vz *= 0.92;
            s.vy = s.vy * 0.92 - 0.03;
            if (!level.getBlockState(above.set(s.x + s.vx, s.y + s.vy, s.z + s.vz)).getCollisionShape(level, above).isEmpty()) {
                s.vy = Math.abs(s.vy) * 0.35;
                s.vx *= 0.6;
                s.vz *= 0.6;
                continue;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
        }
        STREAKS.removeIf(s -> s.age >= s.life);
        long tick = level.getGameTime();
        FLASHES.removeIf(f -> tick - f.born >= FLASH_TICKS || tick < f.born);
        if (!PLUMES.isEmpty()) {
            long now = level.getGameTime();
            PLUMES.removeIf(p -> now >= p.end);
            for (Plume p : PLUMES) {   // the column: a puff a tick out of the crater, rising fast at first
                smoke(level, p.at.x + (r.nextDouble() - 0.5) * p.power * 0.4, p.at.y + 0.3, p.at.z + (r.nextDouble() - 0.5) * p.power * 0.4,
                        0.45F + p.power * 0.1F, GASOLINE_SMOKE, 0.7F, 140, 0.12 + r.nextDouble() * 0.06);
            }
        }
    }

    /** The smoke (LiquidRenderTypes.SMOKE: sorted back to front when drawn), relative to the camera. */
    static void renderSmoke(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Camera camera, float partialTick) {
        if (PUFFS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Quaternionf view = camera.rotation();
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.SMOKE);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        Quaternionf turn = new Quaternionf();
        Vector3f corner = new Vector3f();
        for (Puff p : PUFFS) {
            if (p.alpha <= 0.01F) continue;
            double x = Mth.lerp(partialTick, p.ox, p.x) - cam.x, y = Mth.lerp(partialTick, p.oy, p.y) - cam.y, z = Mth.lerp(partialTick, p.oz, p.z) - cam.z;
            float t = Mth.clamp((p.age + partialTick) / p.life, 0.0F, 1.0F), size = p.size * (1.0F + (GROWTH - 1.0F) * (1.0F - (1.0F - t) * (1.0F - t)));
            if (x * x + y * y + z * z > RANGE * RANGE || x * look.x() + y * look.y() + z * look.z() < -size) continue;   // far, or behind the camera
            int light = LevelRenderer.getLightColor(level, at.set(x + cam.x, y + cam.y, z + cam.z));
            int grey = Math.round(p.grey * 255), alpha = Math.round(p.alpha * 255);
            float u0 = (p.sprite % 4) / 4.0F, u1 = u0 + 0.25F, v0 = (p.sprite / 4) / 2.0F, v1 = v0 + 0.5F;
            turn.set(view).rotateZ(Mth.lerp(partialTick, p.oldRoll, p.roll));
            corner(out, matrix, normals, turn, corner, -1, -1, size, x, y, z, u1, v1, grey, alpha, light);
            corner(out, matrix, normals, turn, corner, -1, 1, size, x, y, z, u1, v0, grey, alpha, light);
            corner(out, matrix, normals, turn, corner, 1, 1, size, x, y, z, u0, v0, grey, alpha, light);
            corner(out, matrix, normals, turn, corner, 1, -1, size, x, y, z, u0, v1, grey, alpha, light);
        }
    }

    /** The sparks (the GLOW batch: added, full bright), relative to the camera. */
    static void renderSparks(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (SPARKS.isEmpty() && FLASHES.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Quaternionf view = camera.rotation();
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.GLOW);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        Vector3f corner = new Vector3f();
        for (Spark s : SPARKS) {
            double x = Mth.lerp(partialTick, s.ox, s.x) - cam.x, y = Mth.lerp(partialTick, s.oy, s.y) - cam.y, z = Mth.lerp(partialTick, s.oz, s.z) - cam.z;
            if (x * x + y * y + z * z > RANGE * RANGE) continue;
            float t = Mth.clamp((s.age + partialTick) / s.life, 0.0F, 1.0F), size = s.size * (1.0F - 0.5F * t) * 2.5F;   // the glow sprite is mostly falloff
            int alpha = Math.round((1.0F - t * t) * 255), green = Math.round((0.9F - 0.5F * t) * 255), blue = Math.round((0.6F - 0.5F * t) * 255);
            sparkCorner(out, matrix, normals, view, corner, -1, -1, size, x, y, z, 0.0F, 1.0F, green, blue, alpha);
            sparkCorner(out, matrix, normals, view, corner, -1, 1, size, x, y, z, 0.0F, 0.0F, green, blue, alpha);
            sparkCorner(out, matrix, normals, view, corner, 1, 1, size, x, y, z, 1.0F, 0.0F, green, blue, alpha);
            sparkCorner(out, matrix, normals, view, corner, 1, -1, size, x, y, z, 1.0F, 1.0F, green, blue, alpha);
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Flash f : FLASHES) {   // where sparks were struck: a bright flash for a few ticks
            float age = (level.getGameTime() - f.born + partialTick) / FLASH_TICKS;
            if (age >= 1.0F) continue;
            float size = f.size * (1.0F - 0.5F * age);
            int alpha = Math.round((1.0F - age) * 255);
            double x = f.at.x - cam.x, y = f.at.y - cam.y, z = f.at.z - cam.z;
            sparkCorner(out, matrix, normals, view, corner, -1, -1, size, x, y, z, 0.0F, 1.0F, 225, 160, alpha);
            sparkCorner(out, matrix, normals, view, corner, -1, 1, size, x, y, z, 0.0F, 0.0F, 225, 160, alpha);
            sparkCorner(out, matrix, normals, view, corner, 1, 1, size, x, y, z, 1.0F, 0.0F, 225, 160, alpha);
            sparkCorner(out, matrix, normals, view, corner, 1, -1, size, x, y, z, 1.0F, 1.0F, 225, 160, alpha);
        }
    }

    /** The streaks of sparks off steel (LiquidRenderTypes.SPARK), each a quad from its head back along its flight, facing the camera. */
    static void renderStreaks(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (STREAKS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.SPARK);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (Streak s : STREAKS) {
            Vec3 head = new Vec3(Mth.lerp(partialTick, s.ox, s.x) - cam.x, Mth.lerp(partialTick, s.oy, s.y) - cam.y, Mth.lerp(partialTick, s.oz, s.z) - cam.z);
            Vec3 v = new Vec3(s.vx, s.vy, s.vz);
            double speed = v.length();
            if (speed < 1e-4 || head.lengthSqr() > RANGE * RANGE) continue;
            Vec3 tail = head.subtract(v.scale(Math.max(1.4, 0.03 / speed)));
            Vec3 side = head.subtract(tail).cross(head);
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(s.width);
            float t = Mth.clamp((s.age + partialTick) / s.life, 0.0F, 1.0F);
            int green = Math.round((0.9F - 0.4F * t) * 255), blue = Math.round((0.8F - 0.65F * t) * 255), alpha = Math.round((1.0F - t * t) * 255);
            streakVertex(out, matrix, normals, head.add(side), 1.0F, 0.0F, green, blue, alpha);
            streakVertex(out, matrix, normals, head.subtract(side), 0.0F, 0.0F, green, blue, alpha);
            streakVertex(out, matrix, normals, tail.subtract(side), 0.0F, 1.0F, green, blue, alpha);
            streakVertex(out, matrix, normals, tail.add(side), 1.0F, 1.0F, green, blue, alpha);
        }
    }

    private static void streakVertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 p, float u, float v, int green, int blue, int alpha) {
        out.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(255, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }

    private static void corner(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Quaternionf turn, Vector3f v, float cx, float cy, float size,
                               double x, double y, double z, float u, float uv, int grey, int alpha, int light) {
        v.set(cx, cy, 0.0F).rotate(turn).mul(size);
        out.vertex(matrix, (float) (x + v.x()), (float) (y + v.y()), (float) (z + v.z())).color(grey, grey, Math.round(grey * 0.98F), alpha).uv(u, uv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normals, 0, 1, 0).endVertex();
    }

    private static void sparkCorner(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Quaternionf view, Vector3f v, float cx, float cy, float size,
                                    double x, double y, double z, float u, float uv, int green, int blue, int alpha) {
        v.set(cx, cy, 0.0F).rotate(view).mul(size);
        out.vertex(matrix, (float) (x + v.x()), (float) (y + v.y()), (float) (z + v.z())).color(255, green, blue, alpha).uv(u, uv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }
}
