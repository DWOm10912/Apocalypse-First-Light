package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Burning fuel stains on this client (2026-10-05, docs/gameplay/fuel_fire_v1.md): flames, smoke and crackle.
 * <ul>
 *   <li>flames: three crossed upright planes each, fixed in the world ({@link #flame}; 2026-10-05 V2.1, they had been
 *   billboards turned to the camera), from the flame sheet (textures/effect/fuel_flame: 48 frames of a
 *   Blender fire simulation, tools/build-fuel-flame-v1.mjs), drawn additively and full bright (LiquidRenderTypes.FLAME).
 *   A floor stain carries one flame plus one more per 0.16 block of size, spread over it; a wall or ceiling stain one.
 *   Each flame has its own size and place in the loop (seeded by the stain), so a burning pool does not flicker in step;
 *   they shoot up over the first {@link #GROW_TICKS} after catching and shrink with the stain as its fuel burns.
 *   Gasoline burns tall and bright, diesel lower and redder.</li>
 *   <li>smoke and sparks: soft black smoke puffs (FireFx; diesel blacker and thicker) and rising sparks (2026-10-05:
 *   vanilla's pixel smoke looked cheap next to these flames, user);</li>
 *   <li>sound: vanilla fire crackle now and then (the catching whoosh is the server's).</li>
 * </ul>
 */
final class FuelFlames {
    private static final int FRAMES = 48, COLS = 8, ROWS = 6, GROW_TICKS = 12;
    private static final double RANGE = 64.0, SMOKE_RANGE = 48.0;

    private FuelFlames() {
    }

    static void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera, double now) {
        VertexConsumer out = null;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) {
            if (!s.burning() || s.pos.distanceToSqr(camera) > RANGE * RANGE) continue;
            if (out == null) out = buffers.getBuffer(LiquidRenderTypes.FLAME);
            float grow = (float) Math.min(1.0, Math.max(0.15, (now - s.ignite) / GROW_TICKS));
            int count = s.floor() ? 1 + (int) (s.size / 0.16F) : 1;
            if (s.pos.distanceToSqr(camera) > 32 * 32) count = Math.min(count, 2);   // fewer flames far away
            long h = s.id * 0x9E3779B97F4A7C15L;
            Vec3 n = Vec3.atLowerCornerOf(s.face.getNormal());
            for (int k = 0; k < count; k++) {
                long hk = h ^ (k * 0xC2B2AE3D27D4EB4FL);
                double r1 = ((hk >>> 11) & 1023) / 1023.0, r2 = ((hk >>> 23) & 1023) / 1023.0, r3 = ((hk >>> 37) & 1023) / 1023.0;
                Vec3 base;
                if (s.floor()) {
                    double angle = r1 * 2 * Math.PI, dist = k == 0 ? 0 : s.size * (0.18 + 0.2 * r2);
                    base = s.pos.add(Math.cos(angle) * dist, -0.02, Math.sin(angle) * dist);
                } else base = s.pos.add(n.scale(0.06)).add(0, s.face == net.minecraft.core.Direction.DOWN ? -0.25 * s.size : -0.05, 0);
                double width = Math.max(0.14, s.size * (s.floor() ? 0.95 : 0.8) * (0.75 + 0.5 * r3) / Math.sqrt(count)) * grow;
                double height = width * 1.375 * (s.diesel ? 0.85 : 1.15);
                flame(out, matrix, normals, camera, camera, base, r1 * Math.PI, width, height, now * 1.2 + r2 * FRAMES,
                        255, s.diesel ? 200 : 255, s.diesel ? 160 : 255, s.diesel ? 210 : 235);
            }
        }
    }

    /** The glow of burning fuel on the floor under it (the GLOW batch): seen from above too, where the flames thin out. */
    static void renderGlows(PoseStack pose, MultiBufferSource buffers, Vec3 camera, double now) {
        VertexConsumer out = null;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) {
            if (!s.burning() || !s.floor() || s.pos.distanceToSqr(camera) > RANGE * RANGE) continue;
            if (out == null) out = buffers.getBuffer(LiquidRenderTypes.GLOW);
            float grow = (float) Math.min(1.0, Math.max(0.15, (now - s.ignite) / GROW_TICKS));
            double flicker = 0.85 + 0.15 * Math.sin(now * 0.7 + s.id * 1.7);
            glow(out, matrix, normals, camera, s.pos.add(0, 0.01, 0), (s.size * 0.6 + 0.12) * grow, 255, s.diesel ? 90 : 110, s.diesel ? 25 : 30,
                    (int) Math.round(80 * flicker));
        }
    }

    /** One flame of burning fuel ({@code diesel}: lower, redder), relative to the camera. */
    static void flame(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 camera, Vec3 base, double yaw, double width, double height,
                      double phase, boolean diesel) {
        flame(out, matrix, normals, camera, camera, base, yaw, width, height, phase, 255, diesel ? 200 : 255, diesel ? 160 : 255, diesel ? 210 : 235);
    }

    /**
     * One flame (2026-10-05 V2.1: fixed in the world, no longer turning to the camera, user): three upright planes 60
     * degrees apart round the vertical through {@code base}, turned by {@code yaw}, each its own frame of the loop
     * ({@code phase} + 16 k), so going round it shows its depth. A plane fades as it turns edge-on to {@code eye} (weight
     * cos^2 of its normal to the line of sight, the three summing to one; less from high above), so no plane is ever seen
     * as a bright line. Tint and alpha given; vertices relative to {@code origin} (the camera in a world pass, an entity's
     * render origin inside its renderer). Also the flames of bullet holes, fire blocks and burning things.
     */
    static void flame(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 origin, Vec3 eye, Vec3 base, double yaw, double width, double height,
                      double phase, int red, int green, int blue, int alpha) {
        double dx = base.x - eye.x, dy = base.y + height * 0.5 - eye.y, dz = base.z - eye.z, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) return;
        dx /= len;
        dz /= len;
        double c0 = facing(yaw, dx, dz), c1 = facing(yaw + Math.PI / 3, dx, dz), c2 = facing(yaw + 2 * Math.PI / 3, dx, dz);
        double norm = 1.0 / Math.max(c0 + c1 + c2, 0.6);
        float bx = (float) (base.x - origin.x), by = (float) (base.y - origin.y), bz = (float) (base.z - origin.z), top = (float) height;
        for (int k = 0; k < 3; k++) {
            int a = (int) Math.round(alpha * (k == 0 ? c0 : k == 1 ? c1 : c2) * norm);
            if (a < 2) continue;
            double angle = yaw + k * Math.PI / 3;
            float hx = (float) (Math.cos(angle) * width / 2), hz = (float) (Math.sin(angle) * width / 2);
            int frame = (int) Math.floorMod((long) (phase + k * 16), FRAMES);
            float u0 = (frame % COLS) / (float) COLS, u1 = u0 + 1.0F / COLS, v0 = (frame / COLS) / (float) ROWS, v1 = v0 + 1.0F / ROWS;
            vertex(out, matrix, normals, bx - hx, by, bz - hz, u0, v1, red, green, blue, a);
            vertex(out, matrix, normals, bx + hx, by, bz + hz, u1, v1, red, green, blue, a);
            vertex(out, matrix, normals, bx + hx, by + top, bz + hz, u1, v0, red, green, blue, a);
            vertex(out, matrix, normals, bx - hx, by + top, bz - hz, u0, v0, red, green, blue, a);
        }
    }

    /** cos^2 between the normal of the upright plane along {@code angle} and the line of sight (dx, dz: its horizontal part). */
    private static double facing(double angle, double dx, double dz) {
        double c = -Math.sin(angle) * dx + Math.cos(angle) * dz;
        return c * c;
    }

    /** A soft round glow lying on the floor at {@code centre} (textures/effect/fire_glow, the GLOW batch). */
    static void glow(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 origin, Vec3 centre, double radius, int red, int green, int blue, int alpha) {
        float x = (float) (centre.x - origin.x), y = (float) (centre.y - origin.y), z = (float) (centre.z - origin.z), r = (float) radius;
        vertex(out, matrix, normals, x - r, y, z - r, 0.0F, 0.0F, red, green, blue, alpha);
        vertex(out, matrix, normals, x - r, y, z + r, 0.0F, 1.0F, red, green, blue, alpha);
        vertex(out, matrix, normals, x + r, y, z + r, 1.0F, 1.0F, red, green, blue, alpha);
        vertex(out, matrix, normals, x + r, y, z - r, 1.0F, 0.0F, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha) {
        out.vertex(matrix, x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }

    /** Smoke and crackle, a client tick. */
    static void tick(ClientLevel level, Vec3 viewer, long now) {
        boolean crackled = false;
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) {
            if (!s.burning() || s.pos.distanceToSqr(viewer) > SMOKE_RANGE * SMOKE_RANGE) continue;
            float spread = 0.5F + s.size * 2;
            if (level.random.nextFloat() < (s.diesel ? 0.14F : 0.09F) * spread) {   // black oil smoke from the flame tips
                double lift = Math.max(0.25, s.size * 1.3) * (s.diesel ? 0.85 : 1.15);
                FireFx.smoke(level, s.pos.x + (level.random.nextDouble() - 0.5) * s.size, s.pos.y + lift, s.pos.z + (level.random.nextDouble() - 0.5) * s.size,
                        0.22F + s.size * 0.5F, s.diesel ? FireFx.DIESEL_SMOKE : FireFx.GASOLINE_SMOKE, s.diesel ? 0.75F : 0.65F,
                        s.diesel ? 110 : 80, s.diesel ? 0.045 : 0.065);
            }
            if (level.random.nextFloat() < 0.07F * spread) {   // sparks off the flames
                FireFx.ember(level, s.pos.x + (level.random.nextDouble() - 0.5) * s.size, s.pos.y + 0.1 + level.random.nextDouble() * s.size,
                        s.pos.z + (level.random.nextDouble() - 0.5) * s.size);
            }
            if (!crackled && level.random.nextFloat() < 0.02F) {
                level.playLocalSound(s.pos.x, s.pos.y, s.pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                        0.5F + s.size, 0.85F + level.random.nextFloat() * 0.3F, false);
                crackled = true;
            }
        }
    }
}
