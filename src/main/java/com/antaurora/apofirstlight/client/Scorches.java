package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Burnt ground (2026-10-05, docs/gameplay/fuel_fire_v1.md "火焰效果 V2"; after a reference video the user liked): where fuel
 * burnt, the ground stays scorched in the world's own pixel style and glows with embers that cool.
 * <ul>
 *   <li>A scorch for every burning stain this client saw burn out (ClientFuelStains#apply): where it lay, as large as it
 *   burnt (the largest size it had while burning), its embers cooling from then on. Nothing while fuel still lies there.</li>
 *   <li>Floors with a full top: the scorches round a cell add up to one field like the fuel pools (FuelPuddleMesher; the
 *   stain's size, lobes of their own) and are drawn as one mesh: the charcoal tile (textures/block/scorch_char, 16 px
 *   a block and world aligned, like the blocks' own pixels) with a soft edge, and over it three layers of single ember
 *   pixels (scorch_embers_0..2) added on full bright, flickering and dimming as it cools, the denser layers first (fewer
 *   embers). Embers glow {@link #GASOLINE_EMBERS} / {@link #DIESEL_EMBERS} ticks after the fire.</li>
 *   <li>Walls, ceilings and floors without a full top: a near-black soot splat (LiquidDecals), no embers.</li>
 *   <li>Each lasts {@link #LIFE} ticks, fading over the last {@link #FADE}; at most {@link #MAX}, the oldest going first.
 *   This client's own, not saved.</li>
 * </ul>
 */
public final class Scorches {
    static final int LIFE = 12000, FADE = 2400, MAX = 512, GASOLINE_EMBERS = 600, DIESEL_EMBERS = 1200;
    private static final float SCALE = 1.0F, RIM = 0.45F;
    private static final int CHAR_ALPHA = 225, SOOT = 0x161311, SOOT_ALPHA = 190;
    private static final long SALT = 0x5C0A7C4L;
    private static final ResourceLocation CHAR = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/scorch_char");
    private static final ResourceLocation[] EMBERS = {
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/scorch_embers_0"),
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/scorch_embers_1"),
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/scorch_embers_2")};

    private static final class Scorch {
        final long id;
        final Vec3 pos;
        final Direction face;
        final boolean diesel;
        final long born;
        final float u0, u1, v0, v1;
        /** The block it is burnt into, and what that block was. */
        final BlockPos support;
        final net.minecraft.world.level.block.state.BlockState supportState;
        float size;
        long cool = Long.MAX_VALUE;

        Scorch(FuelStainIndex.Stain s, float size, long now) {
            id = s.id;
            pos = s.pos;
            face = s.face;
            diesel = s.diesel;
            born = now;
            cool = now;
            this.size = size;
            u0 = s.u0;
            u1 = s.u1;
            v0 = s.v0;
            v1 = s.v1;
            support = BlockPos.containing(s.pos.subtract(Vec3.atLowerCornerOf(s.face.getNormal()).scale(0.01)));
            Level level = Minecraft.getInstance().level;
            supportState = level == null ? null : level.getBlockState(support);
        }

        boolean floor() {
            return face == Direction.UP;
        }

        long cell() {
            return FuelStainIndex.cellOf(pos, face);
        }

        float fade(double now) {
            double age = now - born;
            return age < LIFE - FADE ? 1.0F : (float) Math.max(0.0, (LIFE - age) / FADE);
        }

        float heat(double now) {
            return cool == Long.MAX_VALUE ? 1.0F : (float) Math.max(0.0, 1.0 - (now - cool) / (diesel ? DIESEL_EMBERS : GASOLINE_EMBERS));
        }
    }

    private record Cell(BlockPos air, float[] quads, List<Scorch> scorches) {}

    private static final Map<Long, Scorch> BY_ID = new LinkedHashMap<>();
    /** The largest size each burning stain has had (its scorch is that large). */
    private static final Map<Long, Float> PEAK = new HashMap<>();
    private static final Map<Long, Cell> CELLS = new HashMap<>();
    private static final Set<Long> DIRTY = new HashSet<>(), FALLBACK = new HashSet<>();

    private Scorches() {
    }

    static boolean isEmpty() {
        return BY_ID.isEmpty();
    }

    static void clear() {
        BY_ID.clear();
        PEAK.clear();
        CELLS.clear();
        DIRTY.clear();
        FALLBACK.clear();
    }

    /** A stain is burning (synced): how large it burnt. */
    static void burning(FuelStainIndex.Stain s) {
        PEAK.merge(s.id, s.size, Math::max);
    }

    /**
     * A burning stain is gone (burnt out): the ground it covered is scorched now, as large as it burnt, its embers starting
     * to cool (2026-10-05 V2.1: a scorch from the moment of catching, and 1.3 times as large, showed as black round fuel
     * still lying there, user).
     */
    static void burntOut(FuelStainIndex.Stain s, long now) {
        Float peak = PEAK.remove(s.id);
        Scorch scorch = new Scorch(s, Math.max(s.size, peak == null ? 0.0F : peak), now);
        Scorch old = BY_ID.put(s.id, scorch);
        if (old != null) touched(old);
        touched(scorch);
        for (Iterator<Scorch> it = BY_ID.values().iterator(); BY_ID.size() > MAX && it.hasNext(); ) {
            Scorch oldest = it.next();
            it.remove();
            touched(oldest);
        }
    }

    private static void touched(Scorch scorch) {
        if (!scorch.floor()) return;
        BlockPos cell = BlockPos.of(scorch.cell());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) DIRTY.add(cell.offset(dx, 0, dz).asLong());
    }

    /** Once a second: the old ones go, and any whose block changed (a missed change). */
    static void tick(long now) {
        if (now % 20 != 0 || BY_ID.isEmpty()) return;
        Level level = Minecraft.getInstance().level;
        for (Iterator<Scorch> it = BY_ID.values().iterator(); it.hasNext(); ) {
            Scorch s = it.next();
            if (now - s.born > LIFE || level != null && level.isLoaded(s.support) && level.getBlockState(s.support) != s.supportState) {
                it.remove();
                touched(s);
            }
        }
    }

    /**
     * A block changed on this client (LevelRendererFireTrackMixin): scorches burnt into it go at once (2026-10-05: dug-out
     * ground left its scorch hanging in the air, user), and the char meshes of the cell over it are rebuilt (a scorch
     * reaching over from a neighbour stops at a hole).
     */
    public static void blockChanged(BlockPos pos, net.minecraft.world.level.block.state.BlockState now) {
        if (BY_ID.isEmpty()) return;
        for (Iterator<Scorch> it = BY_ID.values().iterator(); it.hasNext(); ) {
            Scorch s = it.next();
            if (s.support.equals(pos) && now != s.supportState) {
                it.remove();
                touched(s);
            }
        }
        long above = pos.above().asLong();
        if (CELLS.containsKey(above) || FALLBACK.contains(above)) DIRTY.add(above);
    }

    private static void rebuild(Level level) {
        if (DIRTY.isEmpty()) return;
        for (long key : DIRTY) {
            CELLS.remove(key);
            FALLBACK.remove(key);
            BlockPos air = BlockPos.of(key);
            List<Scorch> near = new ArrayList<>();
            for (Scorch s : BY_ID.values()) {
                if (s.floor() && Math.abs(s.pos.y - air.getY()) < 0.05 && Math.abs(s.pos.x - air.getX() - 0.5) < 1.6 && Math.abs(s.pos.z - air.getZ() - 0.5) < 1.6) near.add(s);
            }
            if (near.isEmpty()) continue;
            if (!FuelPuddleMesher.fullFloor(level, air)) {
                FALLBACK.add(key);
                continue;
            }
            List<double[]> balls = new ArrayList<>();
            for (Scorch s : near) FuelPuddleMesher.blob(balls, air, s.pos, s.size * SCALE, s.id ^ SALT);
            float[] quads = FuelPuddleMesher.mesh(air, balls, RIM);
            if (quads.length > 0) CELLS.put(key, new Cell(air, quads, near));
        }
        DIRTY.clear();
    }

    private static TextureAtlasSprite sprite(ResourceLocation location) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(location);
    }

    /** The char and the soot (the stains' DECAL batch): before the fuel stains, so fuel still there lies over it. */
    static void renderChar(PoseStack pose, MultiBufferSource buffers, Level level, Vec3 camera, double range, double now) {
        if (BY_ID.isEmpty()) return;
        rebuild(level);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        if (!CELLS.isEmpty()) {
            TextureAtlasSprite charcoal = sprite(CHAR);
            VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.DECAL);
            for (Cell cell : CELLS.values()) {
                double distance = Math.sqrt(cell.air.distToCenterSqr(camera));
                if (distance > range) continue;
                float fade = 0.0F;
                for (Scorch s : cell.scorches) fade = Math.max(fade, s.fade(now));
                if (fade <= 0.0F) continue;
                double lift = Math.max(0.002, distance * 0.0001);   // under the fuel pools (0.003 and up)
                int light = LevelRenderer.getLightColor(level, cell.air);
                float x0 = (float) (cell.air.getX() - camera.x), y0 = (float) (cell.air.getY() + lift - camera.y), z0 = (float) (cell.air.getZ() - camera.z);
                float[] q = cell.quads;
                for (int k = 0; k < q.length; k += 6) {
                    out.vertex(matrix, x0 + q[k], y0 + q[k + 1], z0 + q[k + 2]).color(255, 255, 255, Math.round(CHAR_ALPHA * fade * q[k + 5]))
                            .uv(charcoal.getU(q[k + 3]), charcoal.getV(q[k + 4])).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(normals, 0, 1, 0).endVertex();
                }
            }
        }
        for (Scorch s : BY_ID.values()) {   // soot where no char mesh goes
            if (s.floor() && !FALLBACK.contains(s.cell()) || s.pos.distanceToSqr(camera) > range * range) continue;
            LiquidDecals.draw(pose, buffers, level, camera, s.pos, s.face, FuelStainIndex.axisU(s.face), FuelStainIndex.axisV(s.face), s.id ^ SALT,
                    s.size * 1.2F, s.u0, s.u1, s.v0, s.v1, 0.0F, 0.0F, SOOT, Math.round(SOOT_ALPHA * s.fade(now)));
        }
    }

    /** The embers (LiquidRenderTypes.EMBER): after the stains, added on full bright. */
    static void renderEmbers(PoseStack pose, MultiBufferSource buffers, Vec3 camera, double range, double now) {
        if (CELLS.isEmpty()) return;
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        VertexConsumer out = null;
        TextureAtlasSprite[] layers = null;
        for (Cell cell : CELLS.values()) {
            float heat = 0.0F;
            for (Scorch s : cell.scorches) heat = Math.max(heat, s.heat(now) * s.fade(now));
            if (heat <= 0.0F) continue;
            double distance = Math.sqrt(cell.air.distToCenterSqr(camera));
            if (distance > range) continue;
            if (out == null) {
                out = buffers.getBuffer(LiquidRenderTypes.EMBER);
                layers = new TextureAtlasSprite[]{sprite(EMBERS[0]), sprite(EMBERS[1]), sprite(EMBERS[2])};
            }
            double lift = Math.max(0.0025, distance * 0.00011);
            float x0 = (float) (cell.air.getX() - camera.x), y0 = (float) (cell.air.getY() + lift - camera.y), z0 = (float) (cell.air.getZ() - camera.z);
            long h = cell.air.asLong() * 0x9E3779B97F4A7C15L;
            float[] q = cell.quads;
            for (int layer = 0; layer < 3; layer++) {
                float strength = Mth.clamp((heat - layer * 0.25F) / 0.5F, 0.0F, 1.0F);
                if (strength <= 0.0F) continue;
                float flicker = 0.7F + 0.3F * (float) Math.sin(now * (0.15 + 0.05 * layer) + ((h >>> (8 * layer)) & 255) / 40.0);
                TextureAtlasSprite sprite = layers[layer];
                for (int k = 0; k < q.length; k += 6) {
                    out.vertex(matrix, x0 + q[k], y0 + q[k + 1], z0 + q[k + 2]).color(255, 255, 255, Math.round(255 * strength * flicker * Math.min(1.0F, q[k + 5])))
                            .uv(sprite.getU(q[k + 3]), sprite.getV(q[k + 4])).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                            .normal(normals, 0, 1, 0).endVertex();
                }
            }
        }
    }
}
