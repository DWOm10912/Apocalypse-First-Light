package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Wall fixtures on a round log wall (2026-10-13, forest start home; docs/models/cabin_exterior_v1.md "壁挂件垫块"): the
 * logs bulge 7 cm out of their cell, so a fixture hung at the cell face sank into them (outlets, the lampholder's pull
 * chain). A fixture whose wall is one (block tag {@link #ROUND_LOG_WALLS}) carries {@link #LOG} = true and stands
 * {@link #DEPTH} out of the face, on a flat oak pad the wall draws behind it (cabin.CabinBlocks.LogWall reads the
 * fixture's {@link Padded#logPad} and adds the piece log_wall/pad_&lt;kind&gt;; tools/build-cabin-exterior-v1.mjs PADS). The
 * pad's face lies 1 cm in front of the log crests. Any face of a log wall counts (a fixture on a log's cut end stands on
 * a pad as well). OBJ fixtures draw a second model moved by a Forge root transform (tools/afl-log-mount.mjs), the mesh
 * fixtures move their whole mesh (blockmesh.AflAnimatedMeshHost#meshMountDepth). Shapes, socket and cord points move
 * with them. The pad itself has no block and no drop: it comes and goes with the fixture.
 */
public final class LogMount {
    public static final BooleanProperty LOG = BooleanProperty.create("log_mount");
    public static final TagKey<Block> ROUND_LOG_WALLS = BlockTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "round_log_walls"));
    /** How far a fixture on logs stands out of the cell face: the 7 cm log bulge and 1 cm of pad in front of it (blocks; px). */
    public static final double DEPTH = 0.08, DEPTH_PX = DEPTH * 16;

    /** The pad kinds the wall can draw (tools/build-cabin-exterior-v1.mjs PADS, in this order from 1). */
    public static final int PAD_OUTLET = 1, PAD_LAMPHOLDER = 2, PAD_EMERGENCY = 3, PAD_WALL_PACK = 4, PAD_PANEL_LIGHT = 5,
            PAD_LINEAR = 6, PAD_PANELBOARD = 7, PAD_LOAD_CENTER = 8, PAD_METER = 9, PAD_INLET = 10;

    /**
     * The pads by kind from 1: px in the fixture's canonical frame (facing north, the wall at z 16), x0, y0, x1, y1, from
     * the wall face DEPTH out; a margin of about 2 cm (0.3 to 0.4 px) round the fixture's back (the meter and the inlet: one
     * backboard for the whole set, the inlet's cord hook included). Mirrored in tools/build-cabin-exterior-v1.mjs PADS
     * (--check compares).
     */
    static final double[][] PADS = {
            {6.8, 3.9, 9.2, 7.3},        // outlet (plate 7.16..8.84 x 4.23..6.97)
            {6.75, 5.47, 9.25, 7.97},    // porcelain lampholder on a wall (base r 0.83 at y 6.72)
            {4.0, 5.6, 12.0, 10.3},      // emergency light
            {4.8, 7.0, 11.2, 11.7},      // wall pack
            {2.9, 2.9, 13.1, 13.1},      // square panel light (industrial_utility_light) on a wall
            {0.1, 7.0, 15.9, 9.0},       // linear light on a wall (housing 7.36..8.64): a rail, butt joints between cells
            {3.2, 2.1, 12.8, 13.9},      // commercial panelboard
            {5.05, 4.5, 10.95, 12.3},    // load center
            {1.2, 1.1, 13.9, 11.7},      // service meter box: meter, disconnect, inlet
            {6.3, 3.8, 9.7, 10.2}};      // generator inlet box and its cord hook

    /** A wall fixture that stands on a pad on logs: the pad kind for this state (0: none, e.g. hung from a ceiling). */
    public interface Padded {
        int logPad(BlockState state);
    }

    private LogMount() {}

    /** The pad of a kind as a canonical shape (px), part of the fixture's outline: aiming at the pad picks the fixture. */
    public static VoxelShape pad(int kind) {
        double[] p = PADS[kind - 1];
        return Block.box(p[0], p[1], 16 - DEPTH_PX, p[2], p[3], 16);
    }

    /** A canonical shape moved out by DEPTH onto its pad. */
    public static VoxelShape onPad(VoxelShape north, int kind) {
        return Shapes.or(out(north), pad(kind));
    }

    /** {@link HorizontalShapeUtils#rotations} of {@link #onPad}. */
    public static Map<Direction, VoxelShape> rotationsOnPad(VoxelShape north, int kind) {
        return HorizontalShapeUtils.rotations(onPad(north, kind));
    }

    /** The fixture facing {@code facing} (away from its wall) at pos hangs on a round log wall. */
    public static boolean onLogs(BlockGetter level, BlockPos pos, Direction facing) {
        return facing.getAxis().isHorizontal() && level.getBlockState(pos.relative(facing.getOpposite())).is(ROUND_LOG_WALLS);
    }

    /**
     * The pad the log wall at wallPos draws toward d (0: none): the block there is a {@link Padded} fixture facing d (its
     * "facing" property, whichever instance) that stands on the logs. Read while the wall's chunk section is meshed.
     */
    public static int padToward(BlockGetter level, BlockPos wallPos, Direction d) {
        BlockState o = level.getBlockState(wallPos.relative(d));
        if (!(o.getBlock() instanceof Padded padded) || !mounted(o)) return 0;
        for (var e : o.getValues().entrySet())
            if (e.getKey().getName().equals("facing") && e.getValue() == d) return padded.logPad(o) & 15;
        return 0;
    }

    /** The placed state with {@link #LOG} from its wall. */
    public static BlockState placed(BlockState state, BlockGetter level, BlockPos pos, Direction facing) {
        return state.setValue(LOG, onLogs(level, pos, facing));
    }

    /** updateShape: the wall behind changed (a log wall put in or taken out under a fixture that survives it). */
    public static BlockState updated(BlockState state, Direction direction, BlockState neighbor, Direction facing) {
        if (direction != facing.getOpposite() || !facing.getAxis().isHorizontal()) return state;
        boolean log = neighbor.is(ROUND_LOG_WALLS);
        return state.getValue(LOG) == log ? state : state.setValue(LOG, log);
    }

    public static boolean mounted(BlockState state) {
        return state.hasProperty(LOG) && state.getValue(LOG);
    }

    /** How far this state's fixture stands out of its wall's face (blocks). */
    public static double depth(BlockState state) {
        return mounted(state) ? DEPTH : 0;
    }

    /** A canonical (facing north, wall at z 16) shape moved out by DEPTH. */
    public static VoxelShape out(VoxelShape north) {
        return north.move(0, 0, -DEPTH);
    }

    /**
     * A canonical box (px) between a part that stays put (a pull box at a face centre, where the cable meets it) and the
     * fixture moved out: its wall end comes forward with the fixture.
     */
    public static VoxelShape cutBack(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1 - DEPTH_PX);
    }
}
