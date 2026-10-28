package com.antaurora.apofirstlight.terrainsmooth.client;

import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
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
        walls(region, level, x, y, z, h, light, state, out);
        TerrainSmoothClient.markSloped(state);
        return List.copyOf(out);
    }

    /**
     * The sides of this cell toward unedited neighbours (an edited neighbour shares the vertices: its skin goes on). This
     * cell's block is a full cube in the data, so the neighbour's face toward it is culled, and the skin itself has no
     * sides. Along each side, between the skin's edge and the neighbour's ground there (EarthGrid#sideGaps):
     * <ul>
     *   <li>the neighbour higher: its face, facing in, with its side texture (Rock R2 round 2, user 2026-10-22, sky through
     *   a pit's sides: the foot of a natural step, dug or filled; round 4: also the neighbour's top cell, up to its drawn
     *   top, or up to its band where that band is not culled);</li>
     *   <li>the skin higher: this cell's own side, facing out (round 4, 2026-10-23: the rim of a two-cell step, and the
     *   corner of a step whose shared vertex went to the column edited first, SmoothRules#notFree).</li>
     * </ul>
     */
    private static void walls(BlockAndTintGetter region, ClientLevel level, int x, int y, int z, int[] h, int light, BlockState own, List<BakedQuad> out) {
        var mc = Minecraft.getInstance();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos g = new BlockPos.MutableBlockPos();
        for (int s = 0; s < 4; s++) {
            Direction d = SmoothSurfaceModel.SIDES[s];
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (Earthwork.editedTop(level, nx, nz) != Integer.MIN_VALUE) continue;   // edited too: the vertices are shared
            Ground ground = ground(region, n, g, nx, y, nz, d.getOpposite());
            float[] a = SmoothSurfaceModel.SIDE_A[s], b = SmoothSurfaceModel.SIDE_B[s];
            Face in = null, outward = null;
            BlockPos cell = new BlockPos(x, y, z);
            for (int k = 0; k < PER_BLOCK; k++) {
                float u0 = (float) k / PER_BLOCK, u1 = (float) (k + 1) / PER_BLOCK;
                float[] p0 = along(a, b, u0), p1 = along(a, b, u1);
                for (float[] piece : sideGaps(edge(h, p0, y), edge(h, p1, y), ground.at(p0, d), ground.at(p1, d))) {
                    float v0 = u0 + (u1 - u0) * piece[0], v1 = u0 + (u1 - u0) * piece[1];
                    float[] q0 = along(a, b, v0), q1 = along(a, b, v1);
                    float b0 = piece[2], b1 = piece[3], t0 = piece[4], t1 = piece[5];
                    if (piece[6] == 1) {
                        if (in == null && (in = face(mc, region, ground.block, ground.blockPos, d.getOpposite(), light)) == null) break;
                        // Terrain Smooth's outward side a-low, b-low, b-high, a-high reversed: it faces into this cell
                        float[][] q = {{q0[0], t0, q0[1]}, {q1[0], t1, q1[1]}, {q1[0], b1, q1[1]}, {q0[0], b0, q0[1]}};
                        float[][] uv = {{1 - v0, 1 - t0}, {1 - v1, 1 - t1}, {1 - v1, 1 - b1}, {1 - v0, 1 - b0}};
                        out.add(SmoothSurfaceModel.quad(q, uv, in.sprite, -1, d.getOpposite(), in.color, in.light));
                    } else {
                        if (outward == null && (outward = face(mc, region, own, cell, d, ground.open ? LevelRenderer.getLightColor(region, n.set(nx, y, nz)) : light)) == null) break;
                        float[][] q = {{q0[0], b0, q0[1]}, {q1[0], b1, q1[1]}, {q1[0], t1, q1[1]}, {q0[0], t0, q0[1]}};
                        float[][] uv = {{v0, 1 - b0}, {v1, 1 - b1}, {v1, 1 - t1}, {v0, 1 - t0}};
                        out.add(SmoothSurfaceModel.quad(q, uv, outward.sprite, -1, d, outward.color, outward.light));
                    }
                }
            }
        }
    }

    /** One side face's sprite, colour and light. */
    private record Face(TextureAtlasSprite sprite, int color, int light) {}

    /** The face of block s at pos toward side, null when its model has none there. */
    private static Face face(Minecraft mc, BlockAndTintGetter region, BlockState s, BlockPos pos, Direction side, int light) {
        List<BakedQuad> layers = mc.getBlockRenderer().getBlockModel(s).getQuads(s, side, RandomSource.create(s.getSeed(pos)), ModelData.EMPTY, null);
        if (layers.isEmpty()) return null;
        BakedQuad o = layers.get(0);
        return new Face(o.getSprite(), tinted(-1, o.isTinted() ? mc.getBlockColors().getColor(s, region, pos, o.getTintIndex()) : -1), light);
    }

    /**
     * An unedited neighbour's ground beside a cell at level y, as it is drawn there: its top (cell heights relative to y;
     * above 1 for a buried block), the block whose side shows, whether its cell at y is open. Where Terrain Smooth draws
     * that block's band toward the cell unculled (one reaching above its own cell), the band's bottom stands for the top:
     * the band closes the rest, and two faces would fight.
     */
    private record Ground(BlockState block, BlockPos blockPos, boolean open, float base, float[] corners, float[] band, Direction toward) {
        /** The ground's height at p, a point of the cell's side toward d (cell coordinates). */
        float at(float[] p, Direction d) {
            if (corners == null && band == null) return base;
            float fx = p[0] - d.getStepX(), fz = p[1] - d.getStepZ();   // the same point in the neighbour's cell
            if (band != null) {
                int s = sideIndex(toward);
                float[] a = SmoothSurfaceModel.SIDE_A[s], b = SmoothSurfaceModel.SIDE_B[s];
                float w = (fx - a[0]) * (b[0] - a[0]) + (fz - a[1]) * (b[1] - a[1]);
                return base + band[0] + (band[1] - band[0]) * w;
            }
            return base + (float) SmoothRules.surface(corners, fx, fz);
        }
    }

    private static int sideIndex(Direction d) {
        for (int s = 0; s < 4; s++) if (SmoothSurfaceModel.SIDES[s] == d) return s;
        throw new IllegalArgumentException(d.toString());
    }

    /**
     * The neighbour's ground (#Ground) at column (nx, nz) beside cell y; toward: the neighbour's side facing the cell. Its
     * cell at y solid: buried under another solid (2: above the cell), else its top there (Terrain Smooth's slope, or the
     * block's top); open: the top of a slope of the block under it rising into y, else the cell's floor or below (-1).
     */
    private static Ground ground(BlockAndTintGetter region, BlockPos.MutableBlockPos n, BlockPos.MutableBlockPos g, int nx, int y, int nz, Direction toward) {
        BlockState here = region.getBlockState(n.set(nx, y, nz));
        if (here.isSolidRender(region, n)) {
            BlockPos at = n.immutable();
            if (region.getBlockState(g.set(nx, y + 1, nz)).isSolidRender(region, g)) return new Ground(here, at, false, 2, null, null, toward);
            return sloped(region, here, at, 0, false, toward);
        }
        BlockState under = region.getBlockState(g.set(nx, y - 1, nz));
        if (under.isSolidRender(region, g)) return sloped(region, under, g.immutable(), -1, true, toward);
        return new Ground(here, n.immutable(), true, -1, null, null, toward);
    }

    /** A solid block's drawn top as ground (base: its floor relative to the cell), its band toward the cell if unculled. */
    private static Ground sloped(BlockAndTintGetter region, BlockState s, BlockPos pos, float base, boolean open, Direction toward) {
        long geo = TerrainSmoothClient.renderGeometry(region, pos, s);
        if (geo < 0) return new Ground(s, pos, open, base + 1, null, null, toward);
        float[] c = TerrainSmoothClient.renderTop(region, pos, geo);   // R2b: the smooth top (its band corners as before)
        int side = sideIndex(toward);
        float la = (geo >>> (16 + 8 * side) & 15) / 4F, lb = (geo >>> (20 + 8 * side) & 15) / 4F;
        float ua = c[SmoothSurfaceModel.SIDE_CORNERS[side][0]], ub = c[SmoothSurfaceModel.SIDE_CORNERS[side][1]];
        la = Math.min(la, ua);
        lb = Math.min(lb, ub);
        boolean band = ua > la + 1e-4F || ub > lb + 1e-4F, above = ua > 1 + 1e-4F || ub > 1 + 1e-4F;
        return new Ground(s, pos, open, base, c, band && above ? new float[]{la, lb} : null, toward);
    }

    /** The point u along the side from a to b (cell x, z). */
    private static float[] along(float[] a, float[] b, float u) {
        return new float[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u};
    }

    /** The skin's height (cell y, unclamped) at a vertex p of the cell's edge. */
    private static float edge(int[] h, float[] p, int y) {
        int i = Math.round(p[0] * PER_BLOCK), j = Math.round(p[1] * PER_BLOCK);
        return (float) h[at(i, j)] / Q - y;
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
