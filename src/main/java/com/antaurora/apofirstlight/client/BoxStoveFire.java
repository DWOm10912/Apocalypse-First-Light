package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.cabin.BoxStove;
import com.antaurora.apofirstlight.cabin.BoxStoveBlockEntity;
import com.antaurora.apofirstlight.cabin.CabinBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Box Stove V1 (docs/models/box_stove_v1.md), client side: the fire of a lit box stove, drawn in the fire world pass
 * (ClientFuelStains, after the block entities; the flames FuelFlames#flame, the glows textures/effect/fire_glow), and its
 * smoke and sound from the block entity's client tick.
 * <ul>
 * <li>Door open: one to three flames over the splits (as many as logs' worth are left), a glow on the ash bed and a
 * faint one on the floor in front of the door; now and then a spark spits out onto the hearth.</li>
 * <li>Door shut: only the six holes of the spin draft glow.</li>
 * <li>Smoke: soft wood-smoke puffs (FireFx, not vanilla pixel smoke) from the chimney's top: up the stove pipes (through
 * a roof they pass), out of the gap under the rain cap round its brim; a bare stove smokes from its pipe stub. Thicker
 * for a while after lighting.</li>
 * <li>Sound: not here: the fire loop (and the cook top's boiling / frying) are client BlockLoopSoundController's.</li>
 * </ul>
 * The lit stoves come from their client ticks (a stove not ticked for a few ticks is dropped: unloaded or gone).
 */
public final class BoxStoveFire {
    private static final double RANGE = 48.0, SMOKE_RANGE = 96.0;
    /** In the north frame (tools/build-box-stove-v1.mjs FLAMES): x, base y, z, width, height. */
    private static final double[][] FLAMES = {{0.30, 0.22, 0.47, 0.24, 0.17}, {0.42, 0.21, 0.45, 0.22, 0.15}, {0.20, 0.21, 0.47, 0.18, 0.13}};
    /** The spin draft (tools/build-box-stove-v1.mjs DRAFT): its face, middle, the holes' ring and radius. */
    private static final double DRAFT_X = -0.0243, DRAFT_Y = 0.345, DRAFT_Z = 0.46, DRAFT_RING = 0.026;
    private static final int KINDLING_TICKS = 300;
    /** Just past the rain cap's brim (tools/build-cabin-exterior-v1.mjs pipePieces: R 0.16): smoke there rises clear of it. */
    private static final double CAP_BRIM = 0.175;
    /** Lit stoves: position -> the game time of their last client tick. */
    private static final Map<Long, Long> LIVE = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private BoxStoveFire() {}

    static boolean isEmpty() { return LIVE.isEmpty(); }

    /** The block entity's client tick: follow the fire, smoke, sparks. */
    public static void tick(BoxStoveBlockEntity stove) {
        if (!(stove.getLevel() instanceof ClientLevel level)) return;
        follow(level);
        BlockState state = stove.getBlockState();
        stove.clientBurn(state);
        BlockPos pos = stove.getBlockPos();
        long now = level.getGameTime();
        if (!state.getValue(BoxStove.Stove.LIT)) {
            LIVE.remove(pos.asLong());
            stove.clientLitAt = Long.MIN_VALUE;
            return;
        }
        LIVE.put(pos.asLong(), now);
        if (stove.clientLitAt == Long.MIN_VALUE) stove.clientLitAt = now;
        var player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().isPaused()) return;
        RandomSource r = level.random;
        Direction facing = state.getValue(BoxStove.Stove.FACING);
        boolean open = state.getValue(BoxStove.Stove.OPEN);
        // the pipes may change: look again every two seconds (first: clientChimneyAt starts at Long.MIN_VALUE, and now minus
        // that overflows negative, so the null test, not the difference, finds the first time; without it no smoke came)
        if (stove.clientChimney == null || now - stove.clientChimneyAt >= 40) {
            chimney(level, pos, facing, stove);
            stove.clientChimneyAt = now;
        }
        Vec3 top = stove.clientChimney;
        boolean kindling = now - stove.clientLitAt < KINDLING_TICKS;
        if (top != null && top.distanceToSqr(player.position()) < SMOKE_RANGE * SMOKE_RANGE && now - stove.clientPuff >= (kindling ? 2 : 3)) {
            stove.clientPuff = now;
            double x = top.x + (r.nextDouble() - 0.5) * 0.06, z = top.z + (r.nextDouble() - 0.5) * 0.06;
            if (stove.clientChimneyCap) {   // out of the gap under the cap, just past its brim (R 0.16), all round
                double a = r.nextDouble() * Math.PI * 2, d = CAP_BRIM + 0.02 * r.nextDouble();
                x = top.x + Math.cos(a) * d;
                z = top.z + Math.sin(a) * d;
            }
            FireFx.smoke(level, x, top.y, z, 0.1F, FireFx.WOOD_SMOKE, kindling ? 0.5F : 0.32F, 110, 0.045);
        }
        if (pos.distToCenterSqr(player.position()) > RANGE * RANGE) return;
        // the fire's sound is its loop now (client BlockLoopSoundController, heard 4 blocks; was a random campfire crackle)
        if (open && r.nextFloat() < 0.02F) {   // a spark spits out onto the hearth
            Vec3 at = BoxStove.world(facing, pos, -0.01, 0.3, 0.4 + 0.12 * r.nextDouble());
            Vec3 v = BoxStove.world(facing, BlockPos.ZERO, -0.04 - 0.03 * r.nextDouble(), 0.03, 0.5 + (r.nextDouble() - 0.5) * 0.04)
                    .subtract(BoxStove.world(facing, BlockPos.ZERO, 0, 0, 0.5));
            FireFx.ember(level, at.x, at.y, at.z, v.x, v.y, v.z, 0.004F, 18);
        }
    }

    /**
     * Where the smoke leaves (into the stove's client fields): up through the stove pipes over the stove, and a roof cell
     * a pipe passes through (not a ridge: it takes no pipe), as StovePipe#cabinFlags joins them. The last pipe has its
     * rain cap: the smoke leaves from the gap under it (0.55..0.62), round the brim. A roof cell's pipe is open on top;
     * without pipes, the stub's open top.
     */
    static void chimney(ClientLevel level, BlockPos stove, Direction facing, BoxStoveBlockEntity out) {
        BlockPos.MutableBlockPos p = stove.mutable().move(Direction.UP);
        BlockPos last = null;
        boolean roof = false;
        for (int n = 0; n < 32; n++) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CabinBlocks.StovePipe) { last = p.immutable(); roof = false; }
            else if (last != null && !roof && s.getBlock() instanceof CabinBlocks.Roof && s.getValue(CabinBlocks.Roof.SHAPE) != CabinBlocks.RoofShape.RIDGE) {
                last = p.immutable();
                roof = true;
            } else break;
            p.move(Direction.UP);
        }
        out.clientChimneyCap = last != null && !roof;
        out.clientChimney = last == null ? BoxStove.world(facing, stove, 0.5, 1.02, 0.5)
                : new Vec3(last.getX() + 0.5, last.getY() + (roof ? 1.02 : 0.585), last.getZ() + 0.5);
    }

    /** A new level: forget the old one's stoves. */
    private static void follow(@Nullable ClientLevel level) {
        if (level == trackedLevel) return;
        LIVE.clear();
        trackedLevel = level;
    }

    /** The flames of the open stoves (the FLAME batch, relative to the camera). */
    static void renderFlames(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, double now) {
        follow(level);
        if (LIVE.isEmpty()) return;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        VertexConsumer out = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Iterator<Map.Entry<Long, Long>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            if (now - e.getValue() > 5) { it.remove(); continue; }   // not ticked: unloaded or gone
            p.set(e.getKey());
            if (p.distToCenterSqr(camera) > RANGE * RANGE) continue;
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof BoxStove.Stove) || !state.getValue(BoxStove.Stove.LIT) || !state.getValue(BoxStove.Stove.OPEN)) continue;
            if (out == null) out = buffers.getBuffer(LiquidRenderTypes.FLAME);
            Direction facing = state.getValue(BoxStove.Stove.FACING);
            int load = Math.max(1, state.getValue(BoxStove.Stove.LOAD));
            float size = load == 1 ? 0.75F : load == 2 ? 0.9F : 1.0F;
            long h = e.getKey() * 0x9E3779B97F4A7C15L;
            for (int k = 0; k < load; k++) {
                double[] f = FLAMES[k];
                long hk = h ^ (k * 0xC2B2AE3D27D4EB4FL);
                double r1 = ((hk >>> 11) & 1023) / 1023.0, r2 = ((hk >>> 23) & 1023) / 1023.0;
                FuelFlames.flame(out, matrix, normals, camera, camera, BoxStove.world(facing, p, f[0], f[1], f[2]), r1 * Math.PI, f[3] * size, f[4] * size,
                        now * 1.1 + r2 * 48, 255, 222, 178, 228);
            }
        }
    }

    /** The glows: the ash bed and the floor before an open door, the draft holes of a shut one (the GLOW batch). */
    static void renderGlows(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, double now) {
        if (LIVE.isEmpty()) return;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        VertexConsumer out = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (long key : LIVE.keySet()) {
            p.set(key);
            if (p.distToCenterSqr(camera) > RANGE * RANGE) continue;
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof BoxStove.Stove) || !state.getValue(BoxStove.Stove.LIT)) continue;
            if (out == null) out = buffers.getBuffer(LiquidRenderTypes.GLOW);
            Direction facing = state.getValue(BoxStove.Stove.FACING);
            double flicker = 0.85 + 0.1 * Math.sin(now * 0.55 + (key * 0x9E3779B97F4A7C15L >>> 52)) + 0.05 * Math.sin(now * 1.7);
            if (state.getValue(BoxStove.Stove.OPEN)) {
                FuelFlames.glow(out, matrix, normals, camera, BoxStove.world(facing, p, 0.33, 0.193, 0.46), 0.22, 255, 120, 45, (int) Math.round(120 * flicker));
                FuelFlames.glow(out, matrix, normals, camera, BoxStove.world(facing, p, -0.3, 0.004, 0.46), 0.42, 255, 110, 40, (int) Math.round(36 * flicker));
                continue;
            }
            Vec3 u = BoxStove.world(facing, BlockPos.ZERO, 0, 0, 1).subtract(BoxStove.world(facing, BlockPos.ZERO, 0, 0, 0));
            for (int i = 0; i < 6; i++) {
                double a = i / 6.0 * Math.PI * 2;
                Vec3 c = BoxStove.world(facing, p, DRAFT_X, DRAFT_Y + Math.sin(a) * DRAFT_RING, DRAFT_Z + Math.cos(a) * DRAFT_RING);
                upright(out, matrix, normals, camera, c, u, 0.011, 255, 125, 45, (int) Math.round(170 * flicker));
            }
        }
    }

    /** A glow spot standing upright at c, its width along u (horizontal), half-size s. */
    private static void upright(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 origin, Vec3 c, Vec3 u, double s, int red, int green, int blue, int alpha) {
        float x = (float) (c.x - origin.x), y = (float) (c.y - origin.y), z = (float) (c.z - origin.z);
        float ux = (float) (u.x * s), uz = (float) (u.z * s), h = (float) s;
        vertex(out, matrix, normals, x - ux, y - h, z - uz, 0, 1, red, green, blue, alpha);
        vertex(out, matrix, normals, x + ux, y - h, z + uz, 1, 1, red, green, blue, alpha);
        vertex(out, matrix, normals, x + ux, y + h, z + uz, 1, 0, red, green, blue, alpha);
        vertex(out, matrix, normals, x - ux, y + h, z - uz, 0, 0, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha) {
        out.vertex(matrix, x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normals, 0, 1, 0).endVertex();
    }
}
