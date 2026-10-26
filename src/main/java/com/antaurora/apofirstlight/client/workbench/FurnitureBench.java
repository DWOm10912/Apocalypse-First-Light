package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.cabin.FurnitureWork;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The furniture work's layout on the bench top (docs/gameplay/furniture_bench_work_v1.md 2, 3.6), shared by the anchors
 * (WorkbenchWork) and the drawing (client/FurnitureWorkRenderer). Blocks in the bench's north frame: the player at z < 0
 * facing +z, +x the player's left, the top at y 0.9. The numbers of the crosscut, the stands, the can and its things are
 * tools/author-furniture-actions.mjs CUT, ASM, STAND_*, PRESS_*, CAN_STAND and WIPE (its --report checks the reach with
 * them); the pieces, their nail slots and hardware places are here.
 */
public final class FurnitureBench {
    // ---- the crosscut (CUT): the cut plane, the board, the stance, the off-cut pile
    public static final double CUT_X = 0.0, CUT_Z0 = 0.06, CUT_Z1 = 0.26, CUT_TOP = 0.925, CUT_THICK = 0.025, CUT_PIECE = 0.28,
            CUT_STAND_X = 0.36, CUT_STAND_Z = -0.18, CUT_YAW = -15, CUT_HOLD_X = 0.13, CUT_HOLD_Y = 0.955, PILE_X = 0.24, PILE_Z = 0.37;
    /** The crosscut's beats (s): the slide, the strokes (the kerf from the front edge to the back), through, the carry. */
    public static final double SLIDE_FROM = 0.1, SLIDE_TO = 0.6, SAW_FROM = 0.9, SAW_SPAN = 4.8, THROUGH = 5.7, CARRY_FROM = 6.1, CARRY_TO = 6.4;
    /** The board's length before a cut: the pieces still to cut, and 30 cm over. */
    public static final double BOARD_OVER = 0.30;

    // ---- the assembly (ASM): the stands, the can, the boxes, the lid's, stick's and rag's rests
    public static final double STAND_Z = -0.08, STAND_RIGHT = 0.18, STAND_BACK = 0.36, CAN_STAND = 0.62, WIPE_STAND = 1.10,
            WIPE_A = 0.80, WIPE_B = 1.32, WIPE_UP = 0.015, PRESS_LEFT = 0.25, PRESS_BACK = 0.10, PRESS_REACH = 0.36;
    public static final double[] CAN = {0.60, 0.9, 0.30}, BOX = {1.76, 0.9, 0.14}, LID = {0.52, 0.905, 0.13}, STICK = {0.45, 0.905, 0.30},
            RAG = {0.66, 0.9, 0.12};
    /** The stirring stick leaning in the can on the rim's left (its foot at the stick rest). */
    public static final double[] STICK_LEAN = {0.55, 1, 0.1};
    /** The wipe's strokes end at these seconds (each paints the next band). */
    public static final double[] STROKE_ENDS = {1.4, 2.3, 3.2};
    public static final int STROKES = 3, BANDS = 8;

    /** One mesh unit as the props draw it: 0.508 (real size) times the player model's 0.9375. */
    public static final double K = 0.508 * 0.9375;
    public static final double NAIL_LEN = 0.1 * K, SCREW_LEN = 0.063 * K, CAN_H = 0.238 * K;
    /** The knobs over the wood (their face screw sits on top): the iron one 0.104 mesh units with its 0.049 stud sunk in, the wooden 0.081. */
    public static final double IRON_KNOB_STUD = 0.049 * K, IRON_KNOB_H = 0.104 * K - IRON_KNOB_STUD, WOODEN_KNOB_H = 0.081 * K;

    private FurnitureBench() {}

    /** A board of a piece: its corners, its grain along x (else z). */
    public record Board(double x0, double y0, double z0, double x1, double y1, double z1, boolean alongX) {}

    /** A piece (3.6): its boards, its top (the nails' and the hardware's surface), its depth (the stain's bands), its nail slots (x, z). */
    public record Piece(String id, List<Board> boards, double top, double z0, double z1, double[][] nails) {
        public double[] nail(int i) {
            double[] n = nails[Math.floorMod(i, nails.length)];
            return new double[]{n[0], top, n[1]};
        }
    }

    private static Board x(double x0, double y0, double z0, double x1, double y1, double z1) { return new Board(x0, y0, z0, x1, y1, z1, true); }

    private static Board z(double x0, double y0, double z0, double x1, double y1, double z1) { return new Board(x0, y0, z0, x1, y1, z1, false); }

    /**
     * The pieces: a panel (three boards on two cleats, the nails through the boards into the cleats); a frame (a door's or
     * lid's: two rails and two stiles on a back of two boards, the nails through the members; the first ten slots keep clear
     * of the storage chest's hardware, the last four of the wardrobe's); an open box (a drawer upside down, its bottom nailed
     * onto the sides' edges); a rack (the hanger board, the ledger strip along its back and two brackets).
     */
    public static final Map<String, Piece> PIECES = Map.of(
            "panel", new Piece("panel", List.of(
                    z(0.83, 0.90, 0.06, 0.87, 0.92, 0.46), z(1.53, 0.90, 0.06, 1.57, 0.92, 0.46),
                    x(0.75, 0.92, 0.06, 1.65, 0.94, 0.19), x(0.75, 0.92, 0.195, 1.65, 0.94, 0.325), x(0.75, 0.92, 0.33, 1.65, 0.94, 0.46)),
                    0.94, 0.06, 0.46, new double[][]{{0.85, 0.125}, {1.55, 0.125}, {0.85, 0.26}, {1.55, 0.26}, {0.85, 0.395}, {1.55, 0.395}}),
            "frame", new Piece("frame", List.of(
                    x(0.75, 0.90, 0.05, 1.65, 0.912, 0.25), x(0.75, 0.90, 0.25, 1.65, 0.912, 0.45),
                    x(0.75, 0.912, 0.05, 1.65, 0.936, 0.11), x(0.75, 0.912, 0.39, 1.65, 0.936, 0.45),
                    z(0.75, 0.912, 0.11, 0.81, 0.936, 0.39), z(1.59, 0.912, 0.11, 1.65, 0.936, 0.39)),
                    0.936, 0.05, 0.45, new double[][]{{0.78, 0.08}, {1.62, 0.08}, {0.78, 0.42}, {1.62, 0.42}, {0.78, 0.18}, {1.62, 0.18},
                    {0.78, 0.32}, {1.62, 0.32}, {1.2, 0.42}, {1.125, 0.08}, {1.275, 0.08}, {0.98, 0.42}, {1.42, 0.42}, {0.975, 0.08}}),
            "box", new Piece("box", List.of(
                    x(0.95, 0.90, 0.05, 1.45, 1.035, 0.065), x(0.95, 0.90, 0.385, 1.45, 1.035, 0.40),
                    z(0.95, 0.90, 0.065, 0.965, 1.035, 0.385), z(1.435, 0.90, 0.065, 1.45, 1.035, 0.385),
                    x(0.95, 1.035, 0.05, 1.45, 1.05, 0.40)),
                    1.05, 0.05, 0.40, new double[][]{{1.03, 0.0575}, {1.37, 0.0575}, {1.03, 0.3925}, {1.37, 0.3925},
                    {0.9575, 0.15}, {1.4425, 0.15}, {0.9575, 0.30}, {1.4425, 0.30}}),
            "rack", new Piece("rack", List.of(
                    x(0.70, 0.90, 0.06, 1.70, 0.922, 0.24), x(0.70, 0.922, 0.17, 1.70, 0.944, 0.23),
                    z(0.80, 0.922, 0.08, 0.84, 0.944, 0.17), z(1.56, 0.922, 0.08, 1.60, 0.944, 0.17)),
                    0.944, 0.06, 0.24, new double[][]{{0.85, 0.20}, {1.55, 0.20}, {1.2, 0.20}, {1.025, 0.20}, {1.375, 0.20}}));

    @Nullable
    public static Piece piece(String id) {
        return PIECES.get(id);
    }

    // ---------------------------------------------------------------- hardware

    public enum Kind { HINGE, KNOB, WOODEN_KNOB, ESCUTCHEON }

    /**
     * A piece of hardware in place: its kind, its mesh origin (the action's anchor hw), its mesh x and y axes in the bench
     * (WorkbenchRenderer turns it so; the actions' mounted turns are the same), its screws' heads when flush (x, y, z).
     */
    public record Hardware(Kind kind, double[] base, double[] ex, double[] ey, double[][] screws) {}

    /** The hardware of step {@code index} of the job (a fit step), its {@code rep}-th piece. */
    @Nullable
    public static Hardware hardware(FurnitureWork.Job job, int index, int rep) {
        FurnitureWork.Step s = job.steps().get(index);
        Piece p = piece(pieceAt(job, index));
        if (p == null) return null;
        int n = Math.max(1, s.times());
        double top = p.top(), front = p.z0() + 0.03, back = p.z1() - 0.03;
        boolean wooden = job.knob() != null && job.knob().getPath().equals("wooden_knob");
        switch (s.step()) {
            case FurnitureWork.FIT_HINGE -> {
                // on the back rail, the pin along x, the leaves across it; three screws a leaf
                double hx = spread(n, rep, new double[]{0.95, 1.45}, new double[]{0.88, 1.08, 1.32, 1.52});
                double[][] screws = new double[6][];
                for (int i = 0; i < 6; i++) screws[i] = new double[]{hx + (i % 3 - 1) * 0.022, top + 0.002, back + (i < 3 ? -0.018 : 0.018)};
                return new Hardware(Kind.HINGE, new double[]{hx - 0.038, top + 0.001, back}, new double[]{0, 0, 1}, new double[]{1, 0, 0}, screws);
            }
            case FurnitureWork.FIT_KNOB -> {
                Kind kind = wooden ? Kind.WOODEN_KNOB : Kind.KNOB;
                if (p.id().equals("box")) {
                    // the drawer lies upside down: the knob on its front, out toward the body; the screw through the bottom into the front
                    return new Hardware(kind, new double[]{1.2, 0.9675, 0.05 + (wooden ? 0 : IRON_KNOB_STUD)}, new double[]{1, 0, 0}, new double[]{0, 0, -1},
                            new double[][]{{1.2, top, 0.0575}});
                }
                // upright on the front rail, held by a face screw on its top
                double kx = spread(n, rep, new double[]{1.0, 1.4}, new double[]{0.9, 1.05, 1.35, 1.5});
                double h = wooden ? WOODEN_KNOB_H : IRON_KNOB_H;
                return new Hardware(kind, new double[]{kx, top - (wooden ? 0 : IRON_KNOB_STUD), front}, new double[]{1, 0, 0}, new double[]{0, 1, 0},
                        new double[][]{{kx, top + h, front}});
            }
            case FurnitureWork.FIT_ESCUTCHEON -> {
                // flat on the front rail over the lock hole, its foot toward the body; a screw at each end
                double ex = n == 1 ? 1.2 : 1.0 + 0.4 * rep / Math.max(1, n - 1);
                double foot = front - 0.0445 * K;
                return new Hardware(Kind.ESCUTCHEON, new double[]{ex, top + 0.0005, foot}, new double[]{-1, 0, 0}, new double[]{0, 0, 1},
                        new double[][]{{ex, top + 0.0015, foot + 0.012 * K}, {ex, top + 0.0015, foot + 0.077 * K}});
            }
            default -> {
                return null;
            }
        }
    }

    /** The place of the rep-th of n along a rail: the tables for 2 and 4, else spread over 0.88..1.52 (one: the middle). */
    private static double spread(int n, int rep, double[] two, double[] four) {
        if (n == 2) return two[Math.min(rep, 1)];
        if (n == 4) return four[Math.min(rep, 3)];
        return n <= 1 ? 1.2 : 0.88 + 0.64 * Math.min(rep, n - 1) / (n - 1);
    }

    /** The piece on the bench at step {@code index}: the last named up to it, or the board. */
    public static String pieceAt(FurnitureWork.Job job, int index) {
        String p = FurnitureWork.BOARD;
        for (int i = 0; i <= Math.min(index, job.steps().size() - 1); i++) if (job.steps().get(i).piece() != null) p = job.steps().get(i).piece();
        return p;
    }

    /** The nails before step {@code index} (the slots run on across the nail steps). */
    public static int nailsBefore(FurnitureWork.Job job, int index) {
        int n = 0;
        for (int i = 0; i < index; i++) if (FurnitureWork.kind(job.steps().get(i).step()) == FurnitureWork.Kind.NAIL) n += job.steps().get(i).times();
        return n;
    }

    /** The stand's x for work at x: a little right of the body's middle, along the bench. */
    public static double standX(double x) {
        return Math.max(0.6, Math.min(1.62, x + STAND_RIGHT));
    }

    /** The stand's z for work at z: at the front, stepped back for the near ones. */
    public static double standZ(double z) {
        return Math.min(STAND_Z, z - STAND_BACK);
    }

    /** The can's mouth. */
    public static double[] mouth() {
        return new double[]{CAN[0], CAN[1] + CAN_H, CAN[2]};
    }

    /** The kerf's z on the board by the crosscut's second: from the front edge to the back over the strokes. */
    public static double kerfZ(double t) {
        return CUT_Z0 + (CUT_Z1 - CUT_Z0) * clamp((t - SAW_FROM) / SAW_SPAN);
    }

    /** The board's slide (0..1) and the off-cut's carry (0..1) by the crosscut's second. */
    public static double slide(double t) {
        return clamp((t - SLIDE_FROM) / (SLIDE_TO - SLIDE_FROM));
    }

    public static double carry(double t) {
        return clamp((t - CARRY_FROM) / (CARRY_TO - CARRY_FROM));
    }

    /** The off-cut's top middle on its way to the pile ({@code stack} off-cuts already on it), as the hand's anchor carry less 2 cm. */
    public static double[] carried(double t, int stack) {
        double c = carry(t);
        double[] from = {CUT_X - CUT_PIECE / 2, CUT_TOP, (CUT_Z0 + CUT_Z1) / 2}, to = {PILE_X, 0.9 + CUT_THICK * (stack + 1), PILE_Z};
        return new double[]{from[0] + (to[0] - from[0]) * c, from[1] + (to[1] - from[1]) * c + 0.08 * Math.sin(Math.PI * c), from[2] + (to[2] - from[2]) * c};
    }

    public static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
