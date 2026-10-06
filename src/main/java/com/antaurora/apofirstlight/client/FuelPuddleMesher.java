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
 * stain there changes, and every {@link #FADE_REBUILD} ticks while one is drying. The scorches (client/Scorches) use the
 * same mesh.
 */
final class FuelPuddleMesher {
    private static final int GRID = 16;
    /** The field at and over which a corner is deep inside (full alpha; between 1 and this: the rim's alpha). */
    private static final float DEEP = 1.6F;
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

    static int cellCount() {
        return CELLS.size();
    }

    /** The vertices of every cached pool (FireStats). */
    static int vertexCount() {
        int count = 0;
        for (Cell cell : CELLS.values()) for (Pool pool : cell.pools) count += pool.quads.length / 6;
        return count;
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
                List<double[]> balls = new ArrayList<>();
                for (FuelStainIndex.Stain s : mine) blob(balls, air, s.pos, s.size * (0.35 + 0.65 * fade(s, now)), s.id);
                float[] quads = mesh(air, balls, RIM);
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
    static boolean fullFloor(Level level, BlockPos air) {
        BlockPos below = air.below();
        BlockState under = level.getBlockState(below), here = level.getBlockState(air);
        return here.getCollisionShape(level, air).isEmpty() && level.getFluidState(air).isEmpty()
                && under.isFaceSturdy(level, below, Direction.UP) && under.getCollisionShape(level, below).max(Direction.Axis.Y) >= 0.999;
    }

    /** A blob of {@code scale} at {@code pos} (seeded by {@code seed}): a main ball and three lobes, as x, z (cell relative), radius. */
    static void blob(List<double[]> balls, BlockPos air, Vec3 pos, double scale, long seed) {
        double cx = pos.x - air.getX(), cz = pos.z - air.getZ();
        long h = seed * 0x9E3779B97F4A7C15L;
        balls.add(new double[]{cx, cz, scale * 0.36});
        for (int k = 0; k < 3; k++) {
            double angle = ((h >>> (12 * k + 3)) & 255) / 256.0 * 2 * Math.PI, r = scale * (0.13 + ((h >>> (12 * k + 11)) & 15) / 16.0 * 0.09);
            balls.add(new double[]{cx + Math.cos(angle) * scale * 0.3, cz + Math.sin(angle) * scale * 0.3, r});
        }
    }

    /**
     * The part of a cell where the blobs field is over 1 (marching squares on the 1/16 grid), as quads: x, y, z (cell
     * relative), u, v (0..16 sprite px of a texture that tiles every 2 blocks, world aligned), alpha factor ({@code rim} at
     * the edge and just inside it, 1 deep inside).
     * <p>
     * Few vertices for the same picture (2026-10-05: every square had been two quads, 2048 vertices for a covered cell and
     * four times that under a scorch with its ember layers; a burning fuel station cost a lot of frame time, user):
     * squares wholly inside with one alpha at all four corners are merged into rectangles, each drawn as a fan round its
     * middle through every grid point on its edge, so whatever lies next to it has its corners there too (no T-junctions,
     * no pinholes along the seams); every other square is its polygon (convex: its points lie on the square's edge, in
     * order) as a fan, two triangles to a quad. Texture and alpha are linear across a merged rectangle, so nothing changes.
     */
    static float[] mesh(BlockPos air, List<double[]> balls, float rim) {
        float[][] f = new float[GRID + 1][GRID + 1];
        for (int i = 0; i <= GRID; i++) for (int j = 0; j <= GRID; j++) {
            double x = (double) i / GRID, z = (double) j / GRID, sum = 0;
            for (double[] b : balls) {
                double dx = x - b[0], dz = z - b[1], q = b[2] * b[2] / (dx * dx + dz * dz + 1e-5);
                sum += q * q;
            }
            f[i][j] = (float) sum;
        }
        Quads out = new Quads(Math.floorMod(air.getX(), 2) * GRID, Math.floorMod(air.getZ(), 2) * GRID);
        // squares wholly inside, by the alpha of their corners: 2 all deep (1), 1 all rim; 0 the rest
        int[][] kind = new int[GRID][GRID];
        boolean[][] done = new boolean[GRID][GRID];
        for (int i = 0; i < GRID; i++) for (int j = 0; j < GRID; j++) {
            float a = f[i][j], b = f[i + 1][j], c = f[i + 1][j + 1], d = f[i][j + 1];
            kind[i][j] = a >= DEEP && b >= DEEP && c >= DEEP && d >= DEEP ? 2
                    : Math.min(Math.min(a, b), Math.min(c, d)) >= 1.0F && Math.max(Math.max(a, b), Math.max(c, d)) < DEEP ? 1 : 0;
        }
        for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) {
            int k = kind[i][j];
            if (k == 0 || done[i][j]) continue;
            int w = 1, h = 1;
            while (i + w < GRID && kind[i + w][j] == k && !done[i + w][j]) w++;
            grow:
            while (j + h < GRID) {
                for (int x = i; x < i + w; x++) if (kind[x][j + h] != k || done[x][j + h]) break grow;
                h++;
            }
            for (int x = i; x < i + w; x++) for (int z = j; z < j + h; z++) done[x][z] = true;
            out.rectangle(i, j, w, h, k == 2 ? 1.0 : rim);
        }
        double[] px = new double[8], pz = new double[8], pa = new double[8];   // a square's polygon: grid x, z, alpha factor
        int[][] corner = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < GRID; i++) for (int j = 0; j < GRID; j++) {
            if (done[i][j]) continue;
            int n = 0;
            for (int k = 0; k < 4; k++) {
                int ai = i + corner[k][0], aj = j + corner[k][1], bi = i + corner[(k + 1) % 4][0], bj = j + corner[(k + 1) % 4][1];
                float fa = f[ai][aj], fb = f[bi][bj];
                if (fa >= 1.0F) {
                    px[n] = ai;
                    pz[n] = aj;
                    pa[n++] = fa >= DEEP ? 1.0 : rim;
                }
                if ((fa >= 1.0F) != (fb >= 1.0F)) {
                    double t = (1.0 - fa) / (fb - fa);
                    px[n] = ai + (bi - ai) * t;
                    pz[n] = aj + (bj - aj) * t;
                    pa[n++] = rim;
                }
            }
            for (int k = 1; k + 1 < n; k += 2) {   // two triangles of the fan to a quad (a last one alone: its corner doubled)
                int d = Math.min(k + 2, n - 1);
                out.corner(px[0], pz[0], pa[0]);
                out.corner(px[k], pz[k], pa[k]);
                out.corner(px[k + 1], pz[k + 1], pa[k + 1]);
                out.corner(px[d], pz[d], pa[d]);
            }
        }
        return out.array();
    }

    /** A growing quad list in the cell mesh layout; corners given on the grid (0..GRID). */
    private static final class Quads {
        private final int ox, oz;
        private float[] data = new float[6 * 256];
        private int size;

        Quads(int ox, int oz) {
            this.ox = ox;
            this.oz = oz;
        }

        void corner(double gx, double gz, double alpha) {
            if (size + 6 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[size++] = (float) (gx / GRID);
            data[size++] = 0.0F;
            data[size++] = (float) (gz / GRID);
            data[size++] = (float) ((ox + gx) / 2.0);
            data[size++] = (float) ((oz + gz) / 2.0);
            data[size++] = (float) alpha;
        }

        /**
         * The squares (i, j) to (i + w, j + h), one alpha: one quad for a single square, else a fan round the middle
         * through the 2 (w + h) grid points round its edge, two to a quad.
         */
        void rectangle(int i, int j, int w, int h, double alpha) {
            if (w == 1 && h == 1) {
                corner(i, j, alpha);
                corner(i + 1, j, alpha);
                corner(i + 1, j + 1, alpha);
                corner(i, j + 1, alpha);
                return;
            }
            int n = 2 * (w + h);
            int[] ex = new int[n], ez = new int[n];
            for (int k = 0; k < n; k++) {   // round the edge from (i, j), the way a square's corners go
                int e = k;
                if (e < w) { ex[k] = i + e; ez[k] = j; continue; }
                e -= w;
                if (e < h) { ex[k] = i + w; ez[k] = j + e; continue; }
                e -= h;
                if (e < w) { ex[k] = i + w - e; ez[k] = j + h; continue; }
                e -= w;
                ex[k] = i;
                ez[k] = j + h - e;
            }
            double cx = i + w / 2.0, cz = j + h / 2.0;
            for (int k = 0; k < n; k += 2) {
                int b = (k + 2) % n;
                corner(cx, cz, alpha);
                corner(ex[k], ez[k], alpha);
                corner(ex[k + 1], ez[k + 1], alpha);
                corner(ex[b], ez[b], alpha);
            }
        }

        float[] array() {
            return java.util.Arrays.copyOf(data, size);
        }
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
