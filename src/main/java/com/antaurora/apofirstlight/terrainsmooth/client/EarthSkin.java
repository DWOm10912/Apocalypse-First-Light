package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.antaurora.apofirstlight.earthwork.EarthGrid.*;

/**
 * Chunk mesh of an edited column's skin cell (Earthwork A1, docs/worldgen/earthwork_a1.md 4): the part of the column's
 * skin inside the cell (EarthGrid#pieces), each triangle one quad (its last point twice) in the unculled list of
 * SmoothSurfaceModel. A triangle whose three vertices are still at their first heights keeps the column's first top
 * texture (grass, sand...), any other is bare ground (EarthGrid#bare: grass and podzol to dirt). Textures are projected
 * from above in the orientation of the first top's variant, tint and tilt shading baked into the vertex colour (the cell's
 * own block may be other ground than the first top; loose earth, A2: its material's ground, BulkMaterials), lit flat with the light of the open cells around it baked in (the
 * skin lies inside the cell, whose own light is dark), as Terrain Smooth's bands above a cell. Rock R2: bare ground in a
 * rock cell, or lying on the floor of a cell over rock (a pit dug down to its hard floor), is that rock's top; loose earth
 * whose material has a sprite of its own (rubble) is drawn with it.
 */
final class EarthSkin {
    private EarthSkin() {}

    private record Layer(TextureAtlasSprite sprite, float[][] corners, int tint) {
        /** uv at (fx, fz) from the corner uvs nw, sw, se, ne (TopTexture#uv) */
        float[] uv(float fx, float fz) {
            float[] out = new float[2];
            for (int k = 0; k < 2; k++)
                out[k] = corners[0][k] * (1 - fx) * (1 - fz) + corners[1][k] * (1 - fx) * fz + corners[2][k] * fx * fz + corners[3][k] * fx * (1 - fz);
            return out;
        }
    }

    /** The quads of the skin cell at pos (chunk-builder threads), or null when it is not one: then the block is vanilla. */
    static List<BakedQuad> quads(BlockAndTintGetter region, BlockPos pos, BlockState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Earthwork.CLIENT.isEmpty() || !TerrainSmooth.CLIENT.enabled() || !TerrainSmooth.applies(level)) return null;
        if (!Earthwork.ground(state.getBlock())) return null;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int[] h = new int[SIDE * SIDE];
        boolean[] undisturbed = new boolean[SIDE * SIDE];
        if (!Earthwork.heights(level, x, z, h, undisturbed)) return null;
        if (kind(min(h), max(h), y) != SURFACE) return null;
        EarthChunk e = Earthwork.get(level, x >> 4, z >> 4);
        if (e == null) return null;
        int col = column(x, z);
        BlockState first = e.topState(col);
        if (first == null) return null;
        BlockPos topPos = new BlockPos(x, e.top0(col), z);
        Layer top = layer(first, topPos, region), bare = layer(Earthwork.bare(first), topPos, region);
        BlockState below = region.getBlockState(pos.below());
        boolean rockCell = Earthwork.rock(state.getBlock());
        Layer rock = rockCell ? layer(state, pos, region) : Earthwork.rock(below.getBlock()) ? layer(below, pos.below(), region) : null;
        int light = light(region, pos, EarthGrid.top(max(h)));
        List<BakedQuad> out = new ArrayList<>();
        Map<Integer, Layer> loose = new HashMap<>(4);
        for (EarthGrid.Piece p : pieces(h, undisturbed, y)) {
            List<double[]> pts = p.points();
            // loose earth (A2, docs/worldgen/earthwork_a2.md 3.3) at the piece's nearest vertex: its material's ground
            int m = p.disturbed() ? looseMaterial(level, x, z, pts) : -1;
            float shade = 1;
            Layer l;
            if (m >= 0) {
                BlockState ground = e.matBlock(m);
                l = loose.computeIfAbsent(m, k -> e.matTexture(k) != null ? sprite(e.matTexture(k)) : ground == null ? bare : layer(ground, topPos, region));
                shade = e.matShade(m);
            } else if (p.disturbed() && rock != null && (rockCell || onFloor(pts))) l = rock;
            else l = p.disturbed() || top == null ? bare : top;
            if (l == null) continue;
            double[] n = normal(pts);
            boolean side = n[1] < SIDE_PROJECTION;
            for (int k = 1; k + 1 < pts.size(); k++) {
                float[][] q = {f(pts.get(0)), f(pts.get(k)), f(pts.get(k + 1)), f(pts.get(k + 1))};
                float[][] uv = new float[4][];
                for (int i = 0; i < 4; i++) uv[i] = side ? sideUv(q[i], n) : l.uv(q[i][0], q[i][2]);
                out.add(SmoothSurfaceModel.quad(q, uv, l.sprite, -1, Direction.UP, shaded(tinted(TopTexture.topColor(q), l.tint), shade), light));
            }
        }
        walls(region, level, x, y, z, h, light, out);
        TerrainSmoothClient.markSloped(state);
        return List.copyOf(out);
    }

    /**
     * Rock R2 round 2 (user 2026-10-22, sky through a pit's sides): the face of an unedited neighbour's buried block beside
     * this cell, from the skin's edge on that side up to the cell's top, facing in, with the neighbour's side texture. This
     * cell's block is a full cube in the data, so the neighbour's own face is culled although the skin there lies lower (the
     * foot of a natural step, dug or filled). A neighbour's top cell is left to Terrain Smooth's band, which meets the
     * vertices the two columns share.
     */
    private static void walls(BlockAndTintGetter region, ClientLevel level, int x, int y, int z, int[] h, int light, List<BakedQuad> out) {
        var mc = Minecraft.getInstance();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int s = 0; s < 4; s++) {
            Direction d = SmoothSurfaceModel.SIDES[s];
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (Earthwork.editedTop(level, nx, nz) != Integer.MIN_VALUE) continue;   // edited too: the vertices are shared
            if (!region.getBlockState(n.set(nx, y + 1, nz)).isSolidRender(region, n)) continue;
            BlockState nb = region.getBlockState(n.set(nx, y, nz));
            if (!nb.isSolidRender(region, n)) continue;
            Direction face = d.getOpposite();
            List<BakedQuad> layers = mc.getBlockRenderer().getBlockModel(nb).getQuads(nb, face, RandomSource.create(nb.getSeed(n)), ModelData.EMPTY, null);
            if (layers.isEmpty()) continue;
            BakedQuad o = layers.get(0);
            int color = tinted(-1, o.isTinted() ? mc.getBlockColors().getColor(nb, region, n, o.getTintIndex()) : -1);
            float[] a = SmoothSurfaceModel.SIDE_A[s], b = SmoothSurfaceModel.SIDE_B[s];
            for (int k = 0; k < PER_BLOCK; k++) {
                float u0 = (float) k / PER_BLOCK, u1 = (float) (k + 1) / PER_BLOCK;
                float lo0 = edge(h, a, b, u0, y), lo1 = edge(h, a, b, u1, y);
                if (lo0 >= 1 && lo1 >= 1) continue;
                float x0 = a[0] + (b[0] - a[0]) * u0, z0 = a[1] + (b[1] - a[1]) * u0, x1 = a[0] + (b[0] - a[0]) * u1, z1 = a[1] + (b[1] - a[1]) * u1;
                // Terrain Smooth's outward side a-low, b-low, b-high, a-high reversed: it faces into this cell
                float[][] q = {{x0, 1, z0}, {x1, 1, z1}, {x1, lo1, z1}, {x0, lo0, z0}};
                float[][] uv = {{1 - u0, 0}, {1 - u1, 0}, {1 - u1, 1 - lo1}, {1 - u0, 1 - lo0}};
                out.add(SmoothSurfaceModel.quad(q, uv, o.getSprite(), -1, face, color, light));
            }
        }
    }

    /** The skin's height (0..1 in cell y) at u along the side from a to b (a vertex of the side's edge). */
    private static float edge(int[] h, float[] a, float[] b, float u, int y) {
        int i = Math.round((a[0] + (b[0] - a[0]) * u) * PER_BLOCK), j = Math.round((a[1] + (b[1] - a[1]) * u) * PER_BLOCK);
        return Math.max(0, Math.min(1, (float) h[at(i, j)] / Q - y));
    }

    private static float[] f(double[] p) { return new float[]{(float) p[0], (float) p[1], (float) p[2]}; }

    /** Whether a piece lies on its cell's floor (the top of the block under it). */
    private static boolean onFloor(List<double[]> pts) {
        for (double[] q : pts) if (q[1] > 1e-3) return false;
        return true;
    }

    /** A block-atlas sprite as a layer, untinted, plainly oriented (a material's own texture). */
    private static Layer sprite(net.minecraft.resources.ResourceLocation id) {
        TextureAtlasSprite s = Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).getSprite(id);
        return new Layer(s, TopTexture.uv(TopTexture.PLAIN), -1);
    }

    /**
     * The material of the loose earth at the vertex nearest a piece's middle (BulkMaterials index), -1 where none lies:
     * the vertex may be in the next chunk (the client's copies).
     */
    private static int looseMaterial(ClientLevel level, int x, int z, List<double[]> pts) {
        double cx = 0, cz = 0;
        for (double[] q : pts) { cx += q[0]; cz += q[2]; }
        int i = Math.round((float) (cx / pts.size() * PER_BLOCK)), j = Math.round((float) (cz / pts.size() * PER_BLOCK));
        int vx = x * PER_BLOCK + Math.max(0, Math.min(PER_BLOCK, i)), vz = z * PER_BLOCK + Math.max(0, Math.min(PER_BLOCK, j));
        EarthChunk c = Earthwork.get(level, vx >> 6, vz >> 6);
        if (c == null) return -1;
        int k = EarthGrid.vertex(vx & 63, vz & 63);
        return c.loose(k) > 0 ? c.mat(k) : -1;
    }

    /** A vertex colour (ABGR) times shade (loose earth: backfill a little darker). */
    private static int shaded(int abgr, float shade) {
        if (shade == 1) return abgr;
        int r = Math.round((abgr & 255) * shade), g = Math.round((abgr >> 8 & 255) * shade), b = Math.round((abgr >> 16 & 255) * shade);
        return abgr & 0xFF000000 | Math.min(255, b) << 16 | Math.min(255, g) << 8 | Math.min(255, r);
    }

    /**
     * cos 45 degrees: a piece steeper than this takes its texture from the side, as a block's side face (A1.1), so a pit's
     * wall does not streak; projected from above it would stretch by 1 / cos, from the side by 1 / sin, equal at 45.
     */
    private static final double SIDE_PROJECTION = Math.sqrt(0.5);

    /** Unit normal of a planar piece (Newell), y up for the vanilla UP order. */
    private static double[] normal(List<double[]> pts) {
        double nx = 0, ny = 0, nz = 0;
        for (int k = 0; k < pts.size(); k++) {
            double[] a = pts.get(k), b = pts.get((k + 1) % pts.size());
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1e-12 ? new double[]{0, 1, 0} : new double[]{nx / len, ny / len, nz / len};
    }

    /** uv of point p (cell-relative) on a steep piece facing n: the vanilla side face along the nearer of x and z. */
    private static float[] sideUv(float[] p, double[] n) {
        float u = Math.abs(n[0]) >= Math.abs(n[2]) ? (n[0] > 0 ? 1 - p[2] : p[2]) : (n[2] < 0 ? 1 - p[0] : p[0]);
        return new float[]{u, 1 - p[1]};
    }

    /** A block's top layer as drawn at pos: the first UP quad of its vanilla model (the variant vanilla picks there). */
    private static Layer layer(BlockState s, BlockPos pos, BlockAndTintGetter region) {
        var mc = Minecraft.getInstance();
        List<BakedQuad> up = mc.getBlockRenderer().getBlockModel(s).getQuads(s, Direction.UP, RandomSource.create(s.getSeed(pos)), ModelData.EMPTY, null);
        if (up.isEmpty()) return null;
        BakedQuad q = up.get(0);
        int tint = q.isTinted() ? mc.getBlockColors().getColor(s, region, pos, q.getTintIndex()) : -1;
        return new Layer(q.getSprite(), TopTexture.uv(TopTexture.orientation(up)), tint);
    }

    /** The tilt shade (grey, or white with a shader pack) times the tint (0xRRGGBB, -1 none), as a vertex colour (ABGR). */
    private static int tinted(int shade, int tint) {
        if (tint == -1) return shade;
        float f = (shade & 255) / 255F;
        int r = Math.round((tint >> 16 & 255) * f), g = Math.round((tint >> 8 & 255) * f), b = Math.round((tint & 255) * f);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    /** Brightest of the open cells around the skin: above the column's top, above the cell and beside it. */
    private static int light(BlockAndTintGetter region, BlockPos pos, int top) {
        int sky = 0, block = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos[] around = {p.set(pos.getX(), top + 1, pos.getZ()).immutable(), pos.above(), pos.north(), pos.south(), pos.west(), pos.east()};
        for (int k = 0; k < around.length; k++) {
            BlockPos n = around[k];
            if (k > 0 && region.getBlockState(n).isSolidRender(region, n)) continue;
            int l = LevelRenderer.getLightColor(region, n);
            sky = Math.max(sky, LightTexture.sky(l));
            block = Math.max(block, LightTexture.block(l));
        }
        return LightTexture.pack(block, sky);
    }
}
