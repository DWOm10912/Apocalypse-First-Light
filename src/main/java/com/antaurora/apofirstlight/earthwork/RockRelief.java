package com.antaurora.apofirstlight.earthwork;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import static com.antaurora.apofirstlight.earthwork.CarveChunk.N;

/**
 * U6 natural rock faces (docs/worldgen/underground_u6_rock_faces_design.md; user 2026-10-24: cave walls continuous and
 * irregular, collision following them). A natural rock block with air beside or under it has its open faces worn back
 * into it, in the rock carving's 1/8 m sub-cells: the same gone sub-cells a pick leaves (RockCarve), so the carving's
 * surface net draws it (RockCarveMesh), its boxes collide and are aimed at, and a strike cuts on from there. Nothing is
 * stored: the relief is a pure function of the block's position, its rock's look and which sides are open, the same on
 * both sides; a strike stores the result as a carving.
 * <ul>
 *   <li>Faces worn: the walls (the four sides) up to {@link #WALL} sub-cells back, the roof (open below) up to
 *   {@link #ROOF}, every top (a cave's floor, and since V3 a step's, a ledge's or a cliff's top under the open sky: rock
 *   is no longer Terrain Smooth's, user 2026-10-24 "all Mesh or all cubes") up to {@link #FLOOR}, shallow steps of a
 *   sub-cell that are walked over. Where a top or a wall meets another open face (a ledge's rim, an outcrop's corner)
 *   the edge is rounded off ({@link #bevel}). Where the opposite side is open too (a thin wall, a pillar, a slab of
 *   roof) each side goes back at most {@link #THIN} ({@link #THIN_EDGE} with the rounding), so the block never wears
 *   through.</li>
 *   <li>How far, by a depth field over the face in world space (continuous across blocks, so a wall reads as one surface),
 *   by the rock's look: granite (and vanilla stone, gneiss, schist) jointed into flat facets at angles; limestone (and
 *   dolomite, marl) dissolved into broad rounded hollows; shale (and rock salt) in thin beds standing out and set back in
 *   horizontal bands; sandstone in thick beds, rounded off.</li>
 *   <li>Open: air, and water (terrain rework R3c, docs/worldgen/terrain_rework_v1_design.md 14.4: rock under a cave's
 *   pool, a lake's or a river's rocky bed): above and below, and on the four walls where the water is deep there (the
 *   block over the water is water or solid). At the water's surface a wall worn back would show a dry slot under the
 *   water from above, so it stays flat (#open). Vanilla water draws
 *   no face toward the rock (a full cube to it), so the worn face shows through the water. Anything placed against a face
 *   (a torch, a ladder, a block) closes that side, so the face behind it is the flat block face it was placed on; lava
 *   leaves its bed as it was.</li>
 *   <li>Not here: rock the earthwork manages (an edited column's skin and its pick floor), and blocks a neighbour not
 *   loaded may border (counted as rock).</li>
 * </ul>
 */
public final class RockRelief {
    private RockRelief() {}

    /**
     * deepest a wall, a roof and a floor are worn back, sub-cells; each side where both opposite sides are open; the same
     * with a rounded edge added; any face with its rounded edge
     */
    static final double WALL = 3.6, ROOF = 3.0, FLOOR = 2.0, THIN = 2.4, THIN_EDGE = 3.4, EDGE_MAX = 6.0;
    /** Direction ordinals: down, up, north (-z), south (+z), west (-x), east (+x) */
    static final int DOWN = 0, UP = 1;
    public static final int GRANITE = 0, SANDSTONE = 1, LIMESTONE = 2, SHALE = 3;

    /**
     * Natural loose ground (Terrain Smooth's ground blocks other than rock). Worn like rock where it was a soil wall from U6
     * V3 to dig R1; no longer (user 2026-10-27: "怎么还是有岩石形状的泥土", and the worn-back soil left see-through gaps
     * beside the slopes and cubes that counted on it being whole): a soil wall is a plain cube, a straight cut.
     */
    public static boolean soil(Block b) {
        return com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth.ground(b) && !Earthwork.rock(b);
    }

    private record Entry(int open, int look, long[] bits) {}

    private static final long[] WHOLE = new long[0];
    private static final ThreadLocal<Long2ObjectOpenHashMap<Entry>> CACHE = ThreadLocal.withInitial(Long2ObjectOpenHashMap::new);

    /**
     * The relief's gone sub-cells of the block at pos (shared: read only), null when it is no worn natural rock. level:
     * the side's (null: the client's earthwork copies); getter: reads the neighbours (a level reads only loaded chunks).
     */
    public static long[] of(Level level, BlockGetter getter, BlockPos pos, Block block) {
        if (!Earthwork.rock(block)) return null;   // loose ground is never worn (dig R1): its walls are straight cut faces
        RockTypes.Rock rock = RockTypes.of(block);
        if (rock == null) return null;
        int look = look(rock.look());
        int open = openSides(getter, pos);
        if (open == 0 || managed(level, pos)) return null;
        Long2ObjectOpenHashMap<Entry> cache = CACHE.get();
        long key = pos.asLong();
        Entry e = cache.get(key);
        if (e == null || e.open != open || e.look != look) {
            if (cache.size() > 16384) cache.clear();
            long[] b = bits(pos.getX(), pos.getY(), pos.getZ(), open, look);
            cache.put(key, e = new Entry(open, look, b == null ? WHOLE : b));
        }
        return e.bits == WHOLE ? null : e.bits;
    }

    /** The open sides of the block at pos (bits by Direction ordinal, #open): a neighbour not loaded is closed. */
    static int openSides(BlockGetter getter, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int air = 0, water = 0;
        for (int d = 0; d < 6; d++) {
            p.set(pos.getX() + (d == 4 ? -1 : d == 5 ? 1 : 0), pos.getY() + (d == 0 ? -1 : d == 1 ? 1 : 0), pos.getZ() + (d == 2 ? -1 : d == 3 ? 1 : 0));
            BlockState s = stateNow(getter, p);
            if (s == null) continue;
            if (s.isAir()) air |= 1 << d;
            else if (water(s)) {
                if (d <= UP) water |= 1 << d;
                else {   // a wall: only where the water goes on above (not its surface)
                    p.move(0, 1, 0);
                    BlockState over = stateNow(getter, p);
                    if (over != null && (water(over) || over.isSolidRender(getter, p))) water |= 1 << d;
                }
            }
        }
        return open(air, water);
    }

    private static boolean water(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && s.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * The sides worn (R3c): those open to air and those toward deep water, water a block toward which (still or flowing)
     * lies above or below, or beside with water or a solid block over it (a wall at the water's surface stays flat).
     */
    public static int open(int air, int water) {
        return air | water;
    }

    private static BlockState stateNow(BlockGetter getter, BlockPos p) {
        if (getter instanceof Level l) {
            if (l.isOutsideBuildHeight(p)) return null;
            LevelChunk c = l.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            return c == null ? null : c.getBlockState(p);
        }
        return getter.getBlockState(p);
    }

    /** Rock an edited column's earthwork draws and shapes itself (its skin, its pick floor and the lid under it; RockMining#minable). */
    private static boolean managed(Level level, BlockPos pos) {
        EarthChunk e = Earthwork.get(level, pos.getX() >> 4, pos.getZ() >> 4);
        if (e == null) return false;
        int col = EarthGrid.column(pos.getX(), pos.getZ());
        return e.edited(col) && pos.getY() >= e.top0(col) - Earthwork.ROCK_DEPTH - 1;
    }

    static int look(String look) {
        return switch (look) {
            case "sandstone" -> SANDSTONE;
            case "limestone" -> LIMESTONE;
            case "shale" -> SHALE;
            default -> GRANITE;
        };
    }

    /** The gone sub-cells of a block at (x, y, z) with these open sides and look, null when none (CarveChunk layout). */
    public static long[] bits(int x, int y, int z, int open, int look) {
        long[] gone = null;
        for (int d = 0; d < 6; d++) {
            if ((open & 1 << d) == 0) continue;
            double max = d == DOWN ? ROOF : d == UP ? FLOOR : WALL;
            boolean thin = (open & 1 << (d ^ 1)) != 0;
            if (thin) max = Math.min(max, THIN);
            for (int u = 0; u < N; u++) for (int v = 0; v < N; v++) {
                double a = (u + 0.5) / N, b = (v + 0.5) / N;
                // the face point (world, m) under sub-cell column (u, v); u, v: x, z on a roof or floor, the face's in-plane axes on a wall
                double depth = switch (d) {
                    case DOWN -> depth(look, x + a, y, z + b) * 0.85;
                    case UP -> floor(look, x + a, y + 1, z + b);
                    case 2 -> depth(look, x + a, y + b, z);
                    case 3 -> depth(look, x + a, y + b, z + 1);
                    case 4 -> depth(look, x, y + b, z + a);
                    default -> depth(look, x + 1, y + b, z + a);
                };
                depth = Math.min(thin ? THIN_EDGE : EDGE_MAX, Math.min(max, depth) + edge(d, open, u, v, x, y, z, look));
                for (int t = 0; t + 0.5 < depth; t++) {
                    int i, j, k;
                    switch (d) {
                        case DOWN -> { i = u; j = t; k = v; }
                        case UP -> { i = u; j = N - 1 - t; k = v; }
                        case 2 -> { i = u; j = v; k = t; }
                        case 3 -> { i = u; j = v; k = N - 1 - t; }
                        case 4 -> { i = t; j = v; k = u; }
                        default -> { i = N - 1 - t; j = v; k = u; }
                    }
                    if (gone == null) gone = new long[N];
                    gone[j] |= 1L << (i | k << 3);
                }
            }
        }
        return gone;
    }

    /**
     * How much further face d is set back at sub-cell column (u, v) by the rounding of its edges with the other open faces
     * across it: a top's with its open sides (a ledge's rim), a wall's with the top and with the open walls beside it (an
     * outcrop's corner). Roofs keep their edges. Sub-cells.
     */
    static double edge(int d, int open, int u, int v, int x, int y, int z, int look) {
        if (d == DOWN) return 0;
        double near = N - u - 0.5, far = u + 0.5, best = 0, a = far / N, b = (v + 0.5) / N;
        // the radius by the face point (world): the faces meeting at an edge read nearly the same point, a ledge's rim varies along it
        double wx = d == 4 ? x : d == 5 ? x + 1 : x + a, wy = d == UP ? y + 1 : y + b, wz = d == 2 ? z : d == 3 ? z + 1 : d == UP ? z + b : z + a;
        double r = 2.4 + 0.8 * value(wx / 0.9, wy / 0.9, wz / 0.9, 61);
        if (d == UP) {   // u: x, v: z
            if ((open & 1 << 4) != 0) best = Math.max(best, bevel(r, far));
            if ((open & 1 << 5) != 0) best = Math.max(best, bevel(r, near));
            if ((open & 1 << 2) != 0) best = Math.max(best, bevel(r, v + 0.5));
            if ((open & 1 << 3) != 0) best = Math.max(best, bevel(r, N - v - 0.5));
            return best;
        }
        // a wall: v is up; u runs along x on a north or south face, along z on a west or east one
        if ((open & 1 << UP) != 0) best = bevel(r, N - v - 0.5);
        int lo = d <= 3 ? 4 : 2, hi = d <= 3 ? 5 : 3;
        if ((open & 1 << lo) != 0) best = Math.max(best, bevel(r, far));
        if ((open & 1 << hi) != 0) best = Math.max(best, bevel(r, near));
        return best;
    }

    /** A rounded edge of radius r: how far the face is set back at distance t (sub-cells) from the other face's plane. */
    static double bevel(double r, double t) {
        return t >= r ? 0 : r - Math.sqrt(r * r - (r - t) * (r - t));
    }

    /** How far the face is worn back at world point (x, y, z), sub-cells (may be below 0: not at all). */
    static double depth(int look, double x, double y, double z) {
        return switch (look) {
            case LIMESTONE -> 4.4 * smooth(0.3, 0.75, fbm(x / 1.7, y / 1.7, z / 1.7, 11, 3)) + 0.9 * (value(x / 0.5, y / 0.5, z / 0.5, 12) - 0.5) - 0.2;
            case SHALE -> {
                double band = value1(y * 2.4 + 1.2 * fbm(x / 3.5, y / 3.5, z / 3.5, 21, 2), 22);
                yield 3.6 * band * band + 0.7 * (value(x / 0.6, y / 0.6, z / 0.6, 23) - 0.5);
            }
            case SANDSTONE -> {
                double band = value1(y * 1.2 + 0.9 * fbm(x / 3, y / 3, z / 3, 31, 2), 32);
                yield 2.4 * band + 2.0 * smooth(0.35, 0.8, fbm(x / 2.2, y / 2.2, z / 2.2, 33, 3)) - 0.5;
            }
            default -> facets(x / 1.1, y / 1.1, z / 1.1);
        };
    }

    /**
     * How far a top (a cave's floor, a ledge or an outcrop under the sky) is worn down at (x, y, z), sub-cells, shallow (it is walked on): limestone in a flowstone
     * undulation; shale, sandstone and granite in terraces a sub-cell apart (bedding planes, and the slabs jointed granite
     * breaks into), broken off in irregular edges. The walls' fields do not serve: their bands follow height, a floor is
     * all one height, and granite's joint planes meet in steps of two sub-cells (25 cm) that a walker bumps over.
     */
    static double floor(int look, double x, double y, double z) {
        if (look == LIMESTONE) return 2.4 * smooth(0.35, 0.8, fbm(x / 1.4, y / 1.4, z / 1.4, 51, 3)) + 0.5 * (value(x / 0.45, y / 0.45, z / 0.45, 52) - 0.5);
        double scale = look == SHALE ? 1.3 : look == SANDSTONE ? 1.9 : 1.6;
        return Math.floor(2.6 * fbm(x / scale, y / scale, z / scale, 53 + look, 3)) + 0.6;
    }

    /** Jointed rock: the plane of the nearest of a jittered lattice of points (cells of 1.1 m), each its own depth and tilt. */
    static double facets(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double best = Double.MAX_VALUE, px = 0, py = 0, pz = 0;
        int bx = 0, by = 0, bz = 0;
        for (int cz = zi - 1; cz <= zi + 1; cz++) for (int cy = yi - 1; cy <= yi + 1; cy++) for (int cx = xi - 1; cx <= xi + 1; cx++) {
            double fx = cx + hash(cx, cy, cz, 41), fy = cy + hash(cx, cy, cz, 42), fz = cz + hash(cx, cy, cz, 43);
            double d2 = (fx - x) * (fx - x) + (fy - y) * (fy - y) + (fz - z) * (fz - z);
            if (d2 < best) {
                best = d2;
                px = fx;
                py = fy;
                pz = fz;
                bx = cx;
                by = cy;
                bz = cz;
            }
        }
        double r = 3.8 * hash(bx, by, bz, 44) - 0.4;
        return r + 3.0 * ((hash(bx, by, bz, 45) - 0.5) * (x - px) + (hash(bx, by, bz, 46) - 0.5) * (y - py) + (hash(bx, by, bz, 47) - 0.5) * (z - pz));
    }

    // ------------------------------------------------------------------------------------------------- noise

    static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** 0..1 from a lattice point and a seed. */
    static double hash(long x, long y, long z, long seed) {
        long h = seed * 0x632BE59BD9B4E019L ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise, 0..1. */
    static double value(double x, double y, double z, long seed) {
        long xi = (long) Math.floor(x), yi = (long) Math.floor(y), zi = (long) Math.floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        double u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy), w = fz * fz * (3 - 2 * fz);
        double c00 = lerp(u, hash(xi, yi, zi, seed), hash(xi + 1, yi, zi, seed)), c10 = lerp(u, hash(xi, yi + 1, zi, seed), hash(xi + 1, yi + 1, zi, seed));
        double c01 = lerp(u, hash(xi, yi, zi + 1, seed), hash(xi + 1, yi, zi + 1, seed)), c11 = lerp(u, hash(xi, yi + 1, zi + 1, seed), hash(xi + 1, yi + 1, zi + 1, seed));
        return lerp(w, lerp(v, c00, c10), lerp(v, c01, c11));
    }

    static double value1(double t, long seed) {
        long ti = (long) Math.floor(t);
        double f = t - ti, u = f * f * (3 - 2 * f);
        return lerp(u, hash(ti, 0, 0, seed), hash(ti + 1, 0, 0, seed));
    }

    static double fbm(double x, double y, double z, long seed, int octaves) {
        double s = 0, amp = 0.5, n = 0, f = 1;
        for (int o = 0; o < octaves; o++) {
            s += amp * value(x * f, y * f, z * f, seed + o * 101L);
            n += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return s / n;
    }

    private static double lerp(double t, double a, double b) { return a + (b - a) * t; }
}
