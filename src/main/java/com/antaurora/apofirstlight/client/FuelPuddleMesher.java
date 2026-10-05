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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fuel pools on floors (2026-10-05, ClientFuelStains): instead of one decal per stain (overlapping decals flickered and
 * never joined; user, 2026-10-05), the floor stains of a block's top face add up to one wetness field per fuel, sampled
 * every 1/16 block over the cell (each stain a sharp metaball blob with a few seeded lobes, shrinking as it dries), and the
 * part above the threshold is drawn (marching squares, edges interpolated): neighbouring stains run together into one pool,
 * nothing overlaps, and a pool stops at the cell's edge (the field is only drawn on cells with a full top face under open
 * air; elsewhere ClientFuelStains falls back to decals). The pool's rim is a little denser than its middle. A tileable film
 * texture (textures/block/liquid_film) in the fuel's tint, world aligned. Meshes are cached per cell and rebuilt when a
 * stain there changes, and every {@link #FADE_REBUILD} ticks while one is drying.
 */
final class FuelPuddleMesher {
    private static final int GRID = 16;
    private static final float RIM = 1.25F;
    static final int FADE_REBUILD = 40;
    private static final ResourceLocation FILM = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/liquid_film");

    /** One fuel's pool in a cell: quads as x, y, z (cell relative), u, v (0..16 sprite px), alpha factor; the stains in it. */
    private record Pool(boolean diesel, float[] quads, List<FuelStainIndex.Stain> stains) {}

    /** A cell's pools (null: the cell has no full floor; its stains are drawn as decals). */
    private record Cell(BlockPos air, double floorY, List<Pool> pools) {}

    private static final Map<Long, Cell> CELLS = new HashMap<>();
    private static final Set<Long> DIRTY = new HashSet<>();
    private static final Set<Long> FALLBACK = new HashSet<>();

    private FuelPuddleMesher() {
    }

    static void clear() {
        CELLS.clear();
        DIRTY.clear();
        FALLBACK.clear();
    }

    /** A floor stain changed: its cell and the eight round it need rebuilding (its blob can reach into them). */
    static void touched(FuelStainIndex.Stain stain) {
        if (!stain.floor()) return;
        BlockPos cell = BlockPos.of(stain.cell());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) DIRTY.add(cell.offset(dx, 0, dz).asLong());
    }

    /** True if this floor stain's cell is drawn as a pool (else it is a decal). */
    static boolean pooled(FuelStainIndex.Stain stain) {
        return !FALLBACK.contains(stain.cell());
    }

    static void rebuild(Level level, double now) {
        if (DIRTY.isEmpty()) return;
        for (long key : DIRTY) {
            CELLS.remove(key);
            FALLBACK.remove(key);
            BlockPos air = BlockPos.of(key);
            List<FuelStainIndex.Stain> near = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.inCell(air.offset(dx, 0, dz).asLong())) if (s.floor()) near.add(s);
            }
            if (near.isEmpty()) continue;
            if (!fullFloor(level, air)) {
                FALLBACK.add(key);
                continue;
            }
            double floorY = air.getY();
            List<Pool> pools = new ArrayList<>();
            for (boolean diesel : new boolean[]{false, true}) {
                List<FuelStainIndex.Stain> mine = new ArrayList<>();
                for (FuelStainIndex.Stain s : near) if (s.diesel == diesel && Math.abs(s.pos.y - floorY) < 0.05) mine.add(s);
                if (mine.isEmpty()) continue;
                float[] quads = mesh(air, mine, now);
                if (quads.length > 0) pools.add(new Pool(diesel, quads, mine));
            }
            if (!pools.isEmpty()) CELLS.put(key, new Cell(air, floorY, pools));
        }
        DIRTY.clear();
    }

    /** Marks the cells of drying stains for a rebuild (their blobs shrink as they dry). */
    static void refreshDrying(double now) {
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) if (s.floor() && fade(s, now) < 1.0F) touched(s);
    }

    /** 1 while fresh, then down to 0 over the last 60% of its life (as ClientFuelStains fades decals). */
    static float fade(FuelStainIndex.Stain s, double now) {
        float life = s.life(), age = (float) (now - s.wet);
        return age < 0.4F * life ? 1.0F : Math.max(0.0F, 1.0F - (age - 0.4F * life) / (0.6F * life));
    }

    /** A full block top under open air: the pool can cover the whole cell and stop at its edges. */
    private static boolean fullFloor(Level level, BlockPos air) {
        BlockPos below = air.below();
        BlockState under = level.getBlockState(below), here = level.getBlockState(air);
        return here.getCollisionShape(level, air).isEmpty() && level.getFluidState(air).isEmpty()
                && under.isFaceSturdy(level, below, Direction.UP) && under.getCollisionShape(level, below).max(Direction.Axis.Y) >= 0.999;
    }

    private static float[] mesh(BlockPos air, List<FuelStainIndex.Stain> stains, double now) {
        // the blobs: per stain a main ball and three seeded lobes (centre x, z relative to the cell, radius)
        List<double[]> balls = new ArrayList<>();
        for (FuelStainIndex.Stain s : stains) {
            double scale = s.size * (0.35 + 0.65 * fade(s, now)), cx = s.pos.x - air.getX(), cz = s.pos.z - air.getZ();
            long h = s.id * 0x9E3779B97F4A7C15L;
            balls.add(new double[]{cx, cz, scale * 0.36});
            for (int k = 0; k < 3; k++) {
                double angle = ((h >>> (12 * k + 3)) & 255) / 256.0 * 2 * Math.PI, r = scale * (0.13 + ((h >>> (12 * k + 11)) & 15) / 16.0 * 0.09);
                balls.add(new double[]{cx + Math.cos(angle) * scale * 0.3, cz + Math.sin(angle) * scale * 0.3, r});
            }
        }
        float[][] f = new float[GRID + 1][GRID + 1];
        for (int i = 0; i <= GRID; i++) for (int j = 0; j <= GRID; j++) {
            double x = (double) i / GRID, z = (double) j / GRID, sum = 0;
            for (double[] b : balls) {
                double dx = x - b[0], dz = z - b[1], q = b[2] * b[2] / (dx * dx + dz * dz + 1e-5);
                sum += q * q;
            }
            f[i][j] = (float) sum;
        }
        List<Float> out = new ArrayList<>();
        int ox = Math.floorMod(air.getX(), 2) * GRID, oz = Math.floorMod(air.getZ(), 2) * GRID;
        double[][] corner = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < GRID; i++) for (int j = 0; j < GRID; j++) {
            List<double[]> poly = new ArrayList<>();   // x, z (cell 0..1), alpha factor
            for (int k = 0; k < 4; k++) {
                int ai = i + (int) corner[k][0], aj = j + (int) corner[k][1], bi = i + (int) corner[(k + 1) % 4][0], bj = j + (int) corner[(k + 1) % 4][1];
                float fa = f[ai][aj], fb = f[bi][bj];
                if (fa >= 1.0F) poly.add(new double[]{(double) ai / GRID, (double) aj / GRID, fa >= 1.6F ? 1.0 : RIM});
                if ((fa >= 1.0F) != (fb >= 1.0F)) {
                    double t = (1.0 - fa) / (fb - fa);
                    poly.add(new double[]{(ai + (bi - ai) * t) / GRID, (aj + (bj - aj) * t) / GRID, RIM});
                }
            }
            for (int k = 1; k + 1 < poly.size(); k++) {   // a fan of quads (the last corner doubled)
                double[][] q = {poly.get(0), poly.get(k), poly.get(k + 1), poly.get(k + 1)};
                for (double[] p : q) {
                    out.add((float) p[0]);
                    out.add(0.0F);
                    out.add((float) p[1]);
                    out.add((float) ((ox + p[0] * GRID) / 2.0));
                    out.add((float) ((oz + p[1] * GRID) / 2.0));
                    out.add((float) p[2]);
                }
            }
        }
        float[] quads = new float[out.size()];
        for (int k = 0; k < quads.length; k++) quads[k] = out.get(k);
        return quads;
    }

    /** Every cached pool within range, its alpha the freshest of its stains' fades; vertices relative to the camera. */
    static void render(PoseStack pose, MultiBufferSource buffers, Level level, Vec3 camera, double range, double now, int gasoline, int diesel, int alpha) {
        if (CELLS.isEmpty()) return;
        TextureAtlasSprite film = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FILM);
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.DECAL);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (Cell cell : CELLS.values()) {
            double dx = cell.air.getX() + 0.5 - camera.x, dy = cell.floorY - camera.y, dz = cell.air.getZ() + 0.5 - camera.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > range) continue;
            double lift = Math.max(0.003, distance * 0.00012);   // off the floor, more far away (depth precision)
            int light = LevelRenderer.getLightColor(level, cell.air);
            float x0 = (float) (cell.air.getX() - camera.x), y0 = (float) (cell.floorY + lift - camera.y), z0 = (float) (cell.air.getZ() - camera.z);
            for (Pool pool : cell.pools) {
                float fade = 0.0F;
                for (FuelStainIndex.Stain s : pool.stains) fade = Math.max(fade, fade(s, now));
                if (fade <= 0.0F) continue;
                int rgb = pool.diesel ? diesel : gasoline, red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
                float[] q = pool.quads;
                for (int k = 0; k < q.length; k += 6) {
                    int a = Math.min(255, Math.round(alpha * fade * q[k + 5]));
                    out.vertex(matrix, x0 + q[k], y0 + q[k + 1], z0 + q[k + 2]).color(red, green, blue, a)
                            .uv(film.getU(q[k + 3]), film.getV(q[k + 4])).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(normals, 0, 1, 0).endVertex();
                }
            }
        }
    }
}
