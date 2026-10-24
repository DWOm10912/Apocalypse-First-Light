package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.earthwork.CarveChunk;
import com.antaurora.apofirstlight.earthwork.CarveNet;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.earthwork.RockCarve;
import com.antaurora.apofirstlight.earthwork.RockRelief;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.antaurora.apofirstlight.earthwork.CarveChunk.N;

/**
 * Chunk mesh of a carved or worn rock block (U3a-3 round 2, docs/worldgen/underground_u3_rocks_ores_design.md 13.3; U6:
 * every natural rock face, earthwork.RockRelief, docs/worldgen/underground_u6_rock_faces_design.md): the surface net the block owns (earthwork.CarveNet: geometry and ownership) in the unculled list of SmoothSurfaceModel; the block's
 * vanilla faces are not drawn. A neighbour's sub-cells: its carving where carved, else all solid for a full opaque block
 * and all empty for anything else.
 * <ul>
 *   <li>Texture: the solid end's block's own vanilla face toward the quad's main axis (its variant at that position),
 *   mapped by position, so untouched and worn parts keep the rock's look; a face the pick made (its empty sub-cell cut, not worn) takes
 *   the rock's freshly broken sprite (textures/block/rock/cut/, lighter and duller: tools/rock/build-cut-textures.mjs),
 *   where there is one.</li>
 *   <li>Light: flat, the light of the empty end's cell baked in, or, inside the carving, the brightest open cell around
 *   it (the carving is inside an opaque block, whose own light is dark), as the earthwork skin.</li>
 * </ul>
 */
final class RockCarveMesh {
    private RockCarveMesh() {}

    private static final int L = CarveNet.L, LO = CarveNet.LO;

    private static final class Field {
        final boolean[] solid = new boolean[L * L * L];
        /**
         * per neighbour block (CarveNet: 27, 13 the block itself): carved or worn, drawn the vanilla way as a full cube, a
         * pick's carving stored, its face relief (U6), state, light
         */
        final boolean[] carved = new boolean[27], cube = new boolean[27], stored = new boolean[27];
        final long[][] relief = new long[27][];
        final BlockState[] states = new BlockState[27];
        final int[] light = new int[27];
        final float[][] vert = new float[CarveNet.VERTS][];
    }

    private static final ThreadLocal<Field> FIELD = ThreadLocal.withInitial(Field::new);

    /** The quads of the carved or worn rock block at pos (chunk-builder threads), or null when it is not one. */
    static List<BakedQuad> quads(BlockAndTintGetter region, BlockPos pos, BlockState state) {
        if (!Earthwork.rock(state.getBlock()) && !RockRelief.soil(state.getBlock())) return null;
        long t0 = System.nanoTime();
        // the client level: the earthwork's copies (an edited column's rock is the earthwork's) and the pick's carvings
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (RockCarve.occupancy(level, region, pos, state.getBlock()) == null) {
            checkNanos.add(System.nanoTime() - t0);
            return null;
        }
        Field f = FIELD.get();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int n = dx + 1 + 3 * (dy + 1) + 9 * (dz + 1);
            p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            BlockState s = region.getBlockState(p);
            boolean rock = Earthwork.rock(s.getBlock());
            long[] carving = rock ? RockCarve.bits(level, p) : null;
            f.relief[n] = rock || RockRelief.soil(s.getBlock()) ? RockRelief.of(level, region, p, s.getBlock()) : null;
            f.stored[n] = carving != null;
            long[] b = carving != null ? carving : f.relief[n];
            f.states[n] = s;
            f.carved[n] = b != null;
            f.cube[n] = b == null && s.isSolidRender(region, p);
            f.light[n] = -1;
            int i0 = LO + dx * N, j0 = LO + dy * N, k0 = LO + dz * N;
            for (int k = Math.max(0, k0); k < Math.min(L, k0 + N); k++)
                for (int j = Math.max(0, j0); j < Math.min(L, j0 + N); j++)
                    for (int i = Math.max(0, i0); i < Math.min(L, i0 + N); i++)
                        f.solid[CarveNet.sample(i, j, k)] = b != null ? !CarveChunk.gone(b, i - i0, j - j0, k - k0) : f.cube[n];
        }
        List<BakedQuad> out = new ArrayList<>();
        Faces faces = new Faces(pos);
        CarveNet.build(f.solid, f.carved, f.cube, f.vert, (pts, axis, lowSolid, solidN, emptyN, emptySample) ->
                quad(f, faces, out, pts, axis, lowSolid, solidN, emptyN, emptySample, pos, region));
        TerrainSmoothClient.markSloped(state);
        meshed.increment();
        quadCount.add(out.size());
        long now = System.nanoTime();
        meshNanos.add(now - t0);
        if (now - lastLog > 10_000_000_000L) {   // U6 prototype: what worn rock costs the chunk builder, every 10 s while it meshes
            lastLog = now;
            long n = meshed.sumThenReset(), q = quadCount.sumThenReset(), mn = meshNanos.sumThenReset(), cn = checkNanos.sumThenReset();
            LOGGER.info("[U6 rock faces] 10 s: {} worn or carved blocks meshed, {} quads ({} a block), {} ms meshing ({} ms a block), {} ms on rock found whole",
                    n, q, n == 0 ? 0 : q / n, mn / 1_000_000, n == 0 ? 0 : String.format(java.util.Locale.ROOT, "%.3f", mn / 1e6 / n), cn / 1_000_000);
        }
        return List.copyOf(out);
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile long lastLog = System.nanoTime();

    /** chunk-builder counters (worker threads): worn or carved blocks meshed, their quads and time; the time spent on rock found whole */
    static final java.util.concurrent.atomic.LongAdder meshed = new java.util.concurrent.atomic.LongAdder(), quadCount = new java.util.concurrent.atomic.LongAdder(),
            meshNanos = new java.util.concurrent.atomic.LongAdder(), checkNanos = new java.util.concurrent.atomic.LongAdder();

    private static final Direction[] AXIS_POS = {Direction.EAST, Direction.UP, Direction.SOUTH}, AXIS_NEG = {Direction.WEST, Direction.DOWN, Direction.NORTH};

    /** One quad of the net, textured from its solid end's block and lit from its empty end. */
    private static void quad(Field f, Faces faces, List<BakedQuad> out, float[][] pts, int axis, boolean lowSolid, int solidN, int emptyN,
                             int emptySample, BlockPos pos, BlockAndTintGetter region) {
        // the face's main axis by its geometric normal (a rounded cut leans), else the edge's
        float[] n = normal(pts);
        int main = Math.abs(n[0]) >= Math.abs(n[1]) && Math.abs(n[0]) >= Math.abs(n[2]) ? 0 : Math.abs(n[1]) >= Math.abs(n[2]) ? 1 : 2;
        if (n[0] == 0 && n[1] == 0 && n[2] == 0) {
            main = axis;
            n[axis] = lowSolid ? 1 : -1;
        }
        Direction dir = n[main] >= 0 ? AXIS_POS[main] : AXIS_NEG[main];
        int sdx = solidN % 3 - 1, sdy = solidN / 3 % 3 - 1, sdz = solidN / 9 - 1;
        // fresh: the empty sub-cell was cut by a pick (stored carving, not worn by the relief); a worn face keeps the rock's own look
        boolean fresh = false;
        if (f.stored[emptyN]) {
            long[] r = f.relief[emptyN];
            int li = emptySample % L - LO - (emptyN % 3 - 1) * N, lj = emptySample / L % L - LO - (emptyN / 3 % 3 - 1) * N,
                    lk = emptySample / (L * L) - LO - (emptyN / 9 - 1) * N;
            fresh = r == null || !CarveChunk.gone(r, li, lj, lk);
        }
        Face face = faces.face(f.states[solidN], sdx, sdy, sdz, dir, fresh);
        if (face == null) return;
        float[][] uv = new float[4][];
        for (int q = 0; q < 4; q++) uv[q] = face.uv(pts[q][0] - sdx, pts[q][1] - sdy, pts[q][2] - sdz);
        out.add(SmoothSurfaceModel.quad(pts, uv, face.sprite, face.tint, dir, -1, light(f, emptyN, pos, region)));
    }

    private static float[] normal(float[][] p) {
        float[] n = new float[3];
        for (int[] tri : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
            float ux = p[tri[1]][0] - p[tri[0]][0], uy = p[tri[1]][1] - p[tri[0]][1], uz = p[tri[1]][2] - p[tri[0]][2];
            float vx = p[tri[2]][0] - p[tri[0]][0], vy = p[tri[2]][1] - p[tri[0]][1], vz = p[tri[2]][2] - p[tri[0]][2];
            n[0] += uy * vz - uz * vy;
            n[1] += uz * vx - ux * vz;
            n[2] += ux * vy - uy * vx;
        }
        return n;
    }

    /** The light a quad whose empty end lies in neighbour n is lit with (packed): that open cell's, or inside a carving the brightest open cell round it. */
    private static int light(Field f, int n, BlockPos pos, BlockAndTintGetter region) {
        if (f.light[n] >= 0) return f.light[n];
        BlockPos at = pos.offset(n % 3 - 1, n / 3 % 3 - 1, n / 9 - 1);
        int l;
        if (!f.carved[n]) {
            l = LevelRenderer.getLightColor(region, at);
        } else {
            int sky = 0, block = 0;
            for (Direction d : Direction.values()) {
                BlockPos o = at.relative(d);
                BlockState s = region.getBlockState(o);
                if (s.isSolidRender(region, o)) continue;
                int c = LevelRenderer.getLightColor(region, o);
                sky = Math.max(sky, LightTexture.sky(c));
                block = Math.max(block, LightTexture.block(c));
            }
            l = LightTexture.pack(block, sky);
        }
        return f.light[n] = l;
    }

    // ------------------------------------------------------------------------------------------------- textures

    /** A vanilla face's sprite and its corner uvs (sprite units 0..1) by position on the face. */
    private record Face(TextureAtlasSprite sprite, int tint, int axis, float[][] uvs) {
        /** uv at block-local point (x, y, z) projected on the face: bilinear in the face's two in-plane axes */
        float[] uv(float x, float y, float z) {
            float a, b;
            if (axis == 0) { a = z; b = y; } else if (axis == 1) { a = x; b = z; } else { a = x; b = y; }
            a = Math.max(0, Math.min(1, a));
            b = Math.max(0, Math.min(1, b));
            float[] o = new float[2];
            for (int c = 0; c < 2; c++)
                o[c] = uvs[0][c] * (1 - a) * (1 - b) + uvs[1][c] * a * (1 - b) + uvs[2][c] * (1 - a) * b + uvs[3][c] * a * b;
            return o;
        }
    }

    /** A mesh's faces, by neighbour block, direction and fresh. */
    private static final class Faces {
        final BlockPos pos;
        final Map<Integer, Face> cache = new java.util.HashMap<>();

        Faces(BlockPos pos) {
            this.pos = pos;
        }

        Face face(BlockState state, int dx, int dy, int dz, Direction dir, boolean fresh) {
            int key = ((dx + 1) + 3 * (dy + 1) + 9 * (dz + 1)) * 16 + dir.ordinal() * 2 + (fresh ? 1 : 0);
            return cache.computeIfAbsent(key, k -> build(state, pos.offset(dx, dy, dz), dir, fresh));
        }

        private Face build(BlockState state, BlockPos at, Direction dir, boolean fresh) {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            if (model instanceof SmoothSurfaceModel s) model = s.original();
            RandomSource random = RandomSource.create(state.getSeed(at));
            List<BakedQuad> qs = model.getQuads(state, dir, random, ModelData.EMPTY, RenderType.solid());
            int axis = dir.getAxis().ordinal();
            if (qs.isEmpty()) {
                TextureAtlasSprite sp = model.getParticleIcon(ModelData.EMPTY);
                return new Face(fresh ? cut(sp) : sp, -1, axis, new float[][]{{0, 1}, {1, 1}, {0, 0}, {1, 0}});
            }
            BakedQuad q = qs.get(0);
            TextureAtlasSprite sp = q.getSprite();
            int[] v = q.getVertices();
            int stride = v.length / 4;
            float[][] uvs = new float[4][];
            for (int c = 0; c < 4; c++) {
                float x = Float.intBitsToFloat(v[c * stride]), y = Float.intBitsToFloat(v[c * stride + 1]), z = Float.intBitsToFloat(v[c * stride + 2]);
                float a, b;
                if (axis == 0) { a = z; b = y; } else if (axis == 1) { a = x; b = z; } else { a = x; b = y; }
                int corner = (a > 0.5F ? 1 : 0) + (b > 0.5F ? 2 : 0);
                float u = Float.intBitsToFloat(v[c * stride + 4]), w = Float.intBitsToFloat(v[c * stride + 5]);
                uvs[corner] = new float[]{(u - sp.getU0()) / (sp.getU1() - sp.getU0()), (w - sp.getV0()) / (sp.getV1() - sp.getV0())};
            }
            for (int c = 0; c < 4; c++) if (uvs[c] == null) return new Face(fresh ? cut(sp) : sp, q.getTintIndex(), axis, new float[][]{{0, 1}, {1, 1}, {0, 0}, {1, 0}});
            return new Face(fresh ? cut(sp) : sp, q.getTintIndex(), axis, uvs);
        }
    }

    private static volatile TextureAtlas cutAtlas;
    private static final Map<ResourceLocation, TextureAtlasSprite> CUT = new ConcurrentHashMap<>();

    /** The freshly broken sprite of a rock sprite (textures/block/rock/cut/<its name>), or the sprite itself where there is none. */
    static TextureAtlasSprite cut(TextureAtlasSprite natural) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        if (atlas != cutAtlas) {
            CUT.clear();
            cutAtlas = atlas;
        }
        ResourceLocation name = natural.contents().name();
        return CUT.computeIfAbsent(name, k -> {
            String path = k.getPath();
            ResourceLocation id = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block/rock/cut/" + path.substring(path.lastIndexOf('/') + 1));
            TextureAtlasSprite s = atlas.getSprite(id);
            return s.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? natural : s;
        });
    }

    // ------------------------------------------------------------------------------------------------- re-meshing

    /** RockCarve#clientChanged (main thread): a block changed; worn faces up to two blocks out read it (their open sides, their neighbours' nets). */
    static void around(BlockPos pos) {
        var mc = Minecraft.getInstance();
        if (mc.levelRenderer == null || !mc.isSameThread()) return;
        mc.levelRenderer.setBlocksDirty(pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
    }

    /** RockCarve.ClientHooks: re-mesh every block whose carving changed, and its neighbours (they draw toward it). */
    static void changed(int cx, int cz, CarveChunk before, CarveChunk after) {
        var mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;
        java.util.Set<Integer> keys = new java.util.HashSet<>();
        if (before != null) for (var en : before.entries()) {
            long[] a = after == null ? null : after.get(CarveChunk.keyX(en.getIntKey()), CarveChunk.keyY(en.getIntKey()), CarveChunk.keyZ(en.getIntKey()));
            if (a == null || !java.util.Arrays.equals(a, en.getValue())) keys.add(en.getIntKey());
        }
        if (after != null) for (var en : after.entries()) {
            long[] b = before == null ? null : before.get(CarveChunk.keyX(en.getIntKey()), CarveChunk.keyY(en.getIntKey()), CarveChunk.keyZ(en.getIntKey()));
            if (b == null || !java.util.Arrays.equals(b, en.getValue())) keys.add(en.getIntKey());
        }
        for (int k : keys) {
            int x = cx * 16 + CarveChunk.keyX(k), y = CarveChunk.keyY(k), z = cz * 16 + CarveChunk.keyZ(k);
            mc.levelRenderer.setBlocksDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
        }
    }
}
