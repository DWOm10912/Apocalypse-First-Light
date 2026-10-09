package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Curbs V1 (docs/models/curbs_v1.md): the curb of one ground cell, worked out from its neighbours, so the block state only
 * keeps what a builder chooses (CurbBlock: axis, level, paint). Shared by the block (shape) and the client model (pieces).
 * <ul>
 * <li>An edge carries a curb where the state says so (CurbBlock NORTH ... WEST: set when a road surface was beside it, kept when
 * it goes) or a road surface ({@link #ROAD}: asphalt, concrete pavement) is beside it now (cells written without the flags).</li>
 * <li>A straight cell (one edge) uses its own level: full 15 cm, lowered 2.5 cm, flush (none; a sidewalk cell gets the yellow
 * detectable warning). A full cell next to a lower one along the run slopes down to it over its metre (the transition), so a
 * builder only marks the low cells.</li>
 * <li>A cell with two or more edges is a corner: always full, the outer corners rounded.</li>
 * <li>An inner corner (both side neighbours are curbs whose edges turn round this cell's corner) puts a 15 cm post there.</li>
 * <li>An end cap closes the run where the next cell is lower or does not carry it on.</li>
 * </ul>
 * Everything is packed into an int key (bits: 0-3 edges N E S W, 4-6 profile, 7-10 posts NE SE SW NW, 11-18 caps per edge
 * ccw / cw end, 19 warning). The reads stay within one block of the cell plus the cells along the run, so the vanilla
 * dirty marking (one block round a change) re-meshes every cell whose key can change.
 */
public final class CurbGeometry {
    public static final TagKey<Block> ROAD = TagKey.create(Registries.BLOCK, new ResourceLocation(ApocalypseFirstLight.MOD_ID, "curb_road_surface"));
    /** Curb width and height, lowered height (block units). */
    public static final double W = 0.15, H = 0.15, L = 0.025;
    public static final int NONE = 0, FULL = 1, LOWERED = 2, LOW_CCW = 3, LOW_CW = 4, ZERO_CCW = 5, ZERO_CW = 6;
    /** Edge i of the key; the corner i lies between H4[i] and its clockwise neighbour (NE, SE, SW, NW). */
    public static final Direction[] H4 = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** A full straight curb on the north edge, both ends closed: the item. */
    public static final int ITEM_KEY = 1 | FULL << 4 | 1 << 11 | 1 << 12;
    private static final Map<Integer, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    private CurbGeometry() {}

    /** A curb piece (tools/build-curbs-v1.mjs, models/block/curb/NAME), turned 90 * turn degrees as BlockModelRotation y. */
    public record Piece(String name, int turn) {}
    private static final String[] STRIP = {null, "strip_full", "strip_lowered", "strip_low_ccw", "strip_low_cw", "strip_zero_ccw", "strip_zero_cw"};
    private static final Map<Integer, List<Piece>> PIECES = new ConcurrentHashMap<>();

    /** The pieces drawing a key: edge i is the north piece turned i quarter turns, corner i the north-east piece turned the same. */
    public static List<Piece> pieces(int key) {
        return PIECES.computeIfAbsent(key, CurbGeometry::assemble);
    }

    private static List<Piece> assemble(int key) {
        List<Piece> out = new ArrayList<>();
        int edges = edges(key), count = Integer.bitCount(edges), profile = profile(key);
        for (int i = 0; i < 4; i++) {
            if ((edges & 1 << i) == 0) continue;
            if (count == 1) {
                if (STRIP[profile] != null) out.add(new Piece(STRIP[profile], i));
                if (warning(key)) out.add(new Piece("warning", i));
            } else {
                out.add(new Piece("mid", i));
                if ((edges & 1 << (i + 3) % 4) == 0) out.add(new Piece("square_ccw", i));
                if ((edges & 1 << (i + 1) % 4) == 0) out.add(new Piece("square_cw", i));
            }
            boolean lowCcw = count == 1 && endHeight(profile, false) < H, lowCw = count == 1 && endHeight(profile, true) < H;
            if (cap(key, i, false)) out.add(new Piece(lowCcw ? "cap_ccw_low" : "cap_ccw_full", i));
            if (cap(key, i, true)) out.add(new Piece(lowCw ? "cap_cw_low" : "cap_cw_full", i));
        }
        for (int i = 0; i < 4; i++) {
            if ((edges & 1 << i) != 0 && (edges & 1 << (i + 1) % 4) != 0) out.add(new Piece("outer", i));
            else if (post(key, i)) out.add(new Piece("post", i));
        }
        return List.copyOf(out);
    }

    public static int edges(int key) { return key & 15; }
    public static int profile(int key) { return key >> 4 & 7; }
    public static boolean post(int key, int corner) { return (key >> 7 + corner & 1) != 0; }
    public static boolean cap(int key, int edge, boolean cw) { return (key >> 11 + 2 * edge + (cw ? 1 : 0) & 1) != 0; }
    public static boolean warning(int key) { return (key >> 19 & 1) != 0; }

    public static int compute(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CurbBlock curb)) return 0;
        int edges = edges(level, pos), count = Integer.bitCount(edges), key = edges, profile = FULL;
        for (int i = 0; i < 4; i++) {
            Direction a = H4[i], b = a.getClockWise();
            if ((edges & bit(a)) == 0 && (edges & bit(b)) == 0 && turns(level, pos.relative(a), b) && turns(level, pos.relative(b), a)) key |= 1 << 7 + i;
        }
        if (count == 1) {
            Direction d = H4[Integer.numberOfTrailingZeros(edges)];
            CurbBlock.Level own = state.getValue(CurbBlock.LEVEL);
            profile = profile(level, pos, d, own);
            key |= profile << 4;
            if (own == CurbBlock.Level.FLUSH && curb.back() == CurbBlock.Back.SIDEWALK) key |= 1 << 19;
        }
        for (int i = 0; i < 4; i++) {
            if ((edges & 1 << i) == 0) continue;
            Direction d = H4[i];
            for (boolean cw : new boolean[]{false, true}) {
                Direction end = cw ? d.getClockWise() : d.getCounterClockWise();
                if (count > 1 && (edges & bit(end)) != 0) continue;   // the rounded outer corner closes it
                double h = count == 1 ? endHeight(profile, cw) : H;
                if (h > 0 && h > neighbourEnd(level, pos.relative(end), d, end.getOpposite()) + 1e-6) key |= 1 << 11 + 2 * i + (cw ? 1 : 0);
            }
        }
        return key;
    }

    /** Height of a straight cell's curb at its counter-clockwise (west for a north edge) or clockwise end. */
    public static double endHeight(int profile, boolean cw) {
        return switch (profile) {
            case FULL -> H;
            case LOWERED -> L;
            case LOW_CCW -> cw ? H : L;
            case LOW_CW -> cw ? L : H;
            case ZERO_CCW -> cw ? H : 0;
            case ZERO_CW -> cw ? 0 : H;
            default -> 0;
        };
    }

    /** The key bit of the edge facing d. */
    private static int bit(Direction d) {
        for (int i = 0; i < 4; i++) if (H4[i] == d) return 1 << i;
        return 0;
    }

    static boolean isRoad(BlockGetter level, BlockPos p) { return level.getBlockState(p).is(ROAD); }
    static boolean isCurb(BlockGetter level, BlockPos p) { return level.getBlockState(p).getBlock() instanceof CurbBlock; }

    /** The curbed edges of a cell: those its state keeps, and any road surface beside it now. */
    static int edges(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        boolean curb = s.getBlock() instanceof CurbBlock;
        int e = 0;
        for (int i = 0; i < 4; i++) if (curb && s.getValue(CurbBlock.edge(H4[i])) || isRoad(level, pos.relative(H4[i]))) e |= 1 << i;
        return e;
    }

    /** n is a curb cell with an edge facing d. */
    private static boolean turns(BlockGetter level, BlockPos n, Direction d) {
        return isCurb(level, n) && (edges(level, n) & bit(d)) != 0;
    }

    /** A curb cell's level as its run sees it: its own level when straight, full when it is a corner. */
    private static CurbBlock.Level effective(BlockGetter level, BlockPos p, BlockState s) {
        return Integer.bitCount(edges(level, p)) == 1 ? s.getValue(CurbBlock.LEVEL) : CurbBlock.Level.FULL;
    }

    /** The level of the neighbour that carries the edge d on, or null where the run stops. */
    private static CurbBlock.Level along(BlockGetter level, BlockPos n, Direction d) {
        BlockState s = level.getBlockState(n);
        if (!(s.getBlock() instanceof CurbBlock) || (edges(level, n) & bit(d)) == 0) return null;
        return effective(level, n, s);
    }

    private static int profile(BlockGetter level, BlockPos pos, Direction d, CurbBlock.Level own) {
        if (own == CurbBlock.Level.FLUSH) return NONE;
        if (own == CurbBlock.Level.LOWERED) return LOWERED;
        CurbBlock.Level ccw = along(level, pos.relative(d.getCounterClockWise()), d), cw = along(level, pos.relative(d.getClockWise()), d);
        boolean lowCcw = ccw != null && ccw != CurbBlock.Level.FULL, lowCw = cw != null && cw != CurbBlock.Level.FULL;
        if (lowCcw == lowCw) return FULL;   // a full cell between two low ones stays full (both ends capped)
        return lowCcw ? (ccw == CurbBlock.Level.FLUSH ? ZERO_CCW : LOW_CCW) : (cw == CurbBlock.Level.FLUSH ? ZERO_CW : LOW_CW);
    }

    /**
     * The run's height in the neighbour n (back = the way from n to this cell) at the end it shares with this cell: 0 where
     * it stops, full where n holds the post of an inner corner there.
     */
    private static double neighbourEnd(BlockGetter level, BlockPos n, Direction d, Direction back) {
        BlockState s = level.getBlockState(n);
        if (!(s.getBlock() instanceof CurbBlock)) return 0;
        int e = edges(level, n);
        if ((e & bit(d)) == 0) return (e & bit(back)) == 0 && turns(level, n.relative(d), back) ? H : 0;
        CurbBlock.Level lv = effective(level, n, s);
        return lv == CurbBlock.Level.FLUSH ? 0 : lv == CurbBlock.Level.LOWERED ? L : H;
    }

    /** Collision and outline: the ground cube, the curb along each edge (transitions in four steps) and the posts. */
    public static VoxelShape shape(int key) {
        return SHAPES.computeIfAbsent(key & 0x7FF, CurbGeometry::build);
    }

    private static VoxelShape build(int key) {
        VoxelShape shape = Shapes.block();
        int edges = edges(key), count = Integer.bitCount(edges), profile = profile(key);
        for (int i = 0; i < 4; i++) {
            if ((edges & 1 << i) == 0) continue;
            if (count == 1) {
                double h0 = endHeight(profile, false), h1 = endHeight(profile, true);
                for (int s = 0; s < 4; s++) {
                    double h = Math.max(h0 + (h1 - h0) * s / 4.0, h0 + (h1 - h0) * (s + 1) / 4.0);
                    if (h > 0) shape = Shapes.joinUnoptimized(shape, box(i, s / 4.0, 0, (s + 1) / 4.0, W, h), BooleanOp.OR);
                }
            } else shape = Shapes.joinUnoptimized(shape, box(i, 0, 0, 1, W, H), BooleanOp.OR);
        }
        for (int i = 0; i < 4; i++) if (post(key, i)) shape = Shapes.joinUnoptimized(shape, box(i, 1 - W, 0, 1, W, H), BooleanOp.OR);
        return shape.optimize();
    }

    /** A box drawn for the north edge (x across, z 0 at the road), turned to edge i, from the top of the cube up to 1 + h. */
    private static VoxelShape box(int i, double x0, double z0, double x1, double z1, double h) {
        double[] a = turn(i, x0, z0), b = turn(i, x1, z1);
        return Shapes.box(Math.min(a[0], b[0]), 1, Math.min(a[1], b[1]), Math.max(a[0], b[0]), 1 + h, Math.max(a[1], b[1]));
    }

    /** The model rotation y = 90 * i (north to east): (x, z) to (1 - z, x). */
    private static double[] turn(int i, double x, double z) {
        for (int k = 0; k < i; k++) { double t = x; x = 1 - z; z = t; }
        return new double[]{x, z};
    }
}
