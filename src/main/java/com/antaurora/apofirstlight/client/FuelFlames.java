package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Burning fuel stains on this client (2026-10-05, docs/gameplay/fuel_fire_v1.md): flames, smoke and crackle.
 * <ul>
 *   <li>flames: upright billboards turned to the camera, from the flame sheet (textures/effect/fuel_flame: 48 frames of a
 *   Blender fire simulation, tools/build-fuel-flame-v1.mjs), drawn additively and full bright (LiquidRenderTypes.FLAME).
 *   A floor stain carries one flame plus one more per 0.16 block of size, spread over it; a wall or ceiling stain one.
 *   Each flame has its own size and place in the loop (seeded by the stain), so a burning pool does not flicker in step;
 *   they shoot up over the first {@link #GROW_TICKS} after catching and shrink with the stain as its fuel burns.
 *   Gasoline burns tall and bright, diesel lower and redder.</li>
 *   <li>smoke: vanilla smoke puffs rising from the flames, thick campfire smoke for diesel;</li>
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
                flame(out, matrix, normals, camera, base, width, height, now * 1.2 + r2 * FRAMES, s.diesel);
            }
        }
    }

    /**
     * One flame: an upright billboard from {@code base}, turned about the vertical to the camera, at {@code phase} in the
     * loop (frames). Also the flame at a burning bullet hole (ClientFuelLeaks).
     */
    static void flame(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 camera, Vec3 base, double width, double height,
                      double phase, boolean diesel) {
        int frame = (int) Math.floorMod((long) phase, FRAMES);
        float u0 = (frame % COLS) / (float) COLS, u1 = u0 + 1.0F / COLS, v0 = (frame / COLS) / (float) ROWS, v1 = v0 + 1.0F / ROWS;
        double tx = camera.x - base.x, tz = camera.z - base.z, len = Math.sqrt(tx * tx + tz * tz);
        double rx = len > 1e-4 ? -tz / len : 1, rz = len > 1e-4 ? tx / len : 0;
        int red = 255, green = diesel ? 200 : 255, blue = diesel ? 160 : 255, alpha = diesel ? 210 : 235;
        float bx = (float) (base.x - camera.x), by = (float) (base.y - camera.y), bz = (float) (base.z - camera.z);
        float hx = (float) (rx * width / 2), hz = (float) (rz * width / 2), top = (float) height;
        vertex(out, matrix, normals, bx - hx, by, bz - hz, u0, v1, red, green, blue, alpha);
        vertex(out, matrix, normals, bx + hx, by, bz + hz, u1, v1, red, green, blue, alpha);
        vertex(out, matrix, normals, bx + hx, by + top, bz + hz, u1, v0, red, green, blue, alpha);
        vertex(out, matrix, normals, bx - hx, by + top, bz - hz, u0, v0, red, green, blue, alpha);
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
            float chance = (s.diesel ? 0.12F : 0.05F) * (0.5F + s.size * 2);
            if (level.random.nextFloat() < chance) {
                double lift = Math.max(0.2, s.size * 1.2);
                level.addAlwaysVisibleParticle(s.diesel ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE, true,
                        s.pos.x + (level.random.nextDouble() - 0.5) * s.size, s.pos.y + lift, s.pos.z + (level.random.nextDouble() - 0.5) * s.size,
                        0, s.diesel ? 0.04 : 0.06, 0);
            }
            if (!crackled && level.random.nextFloat() < 0.02F) {
                level.playLocalSound(s.pos.x, s.pos.y, s.pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                        0.5F + s.size, 0.85F + level.random.nextFloat() * 0.3F, false);
                crackled = true;
            }
        }
    }
}
