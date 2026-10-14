package com.antaurora.apofirstlight.wiring;

import net.minecraft.core.Direction;

/**
 * One node of Building Wiring V1 (docs/gameplay/building_wiring_v1_design.md 3.1): one circuit's wire in one cell, packed in
 * a long of 50 bits.
 * <pre>
 *   bits  0-3   circuit: 1-4 the panel's breakers, 5 FEEDER (inlet box or meter box to the panel)
 *   bits  4-5   unused (0; in saves before 2026-10-13 v2 a free colour, see WiringChunk#load)
 *   bits  6-11  links to the same circuit's node in the six neighbours (Direction 3D data order: down, up, north, south, west, east)
 *   bits 12-17  taps into the device in that neighbour (its junction box; a tap is not a node and costs no wire)
 *   bits 18-41  each tap's group, 4 bits per direction: 0 always on, 1-8 light groups, 15 photocell
 *   bits 42-49  slope links (2026-10-13): to the same circuit's node one cell across and one up or down, along a roof slope or
 *               a flight of stairs (BuildingWiring#slope); bit 2 h + (up ? 1 : 0), h the horizontal direction's 2D value
 * </pre>
 * A node value is never 0 (its circuit is 1-5), so 0 means "no node". Saved nodes from before the slope links simply have
 * none.
 * <p>The colour is the circuit (user 2026-10-13: "4种颜色，4路不就够了吗"): circuit 1 red, 2 blue, 3 green, 4 yellow, like
 * Terraria's four wires; the feeder is black (a service cable). It only tells wires apart; joining and power follow the
 * circuit and the links.
 */
public final class WiringNode {
    /** The panel's breakers (circuits 1-4); the feeder after them; CIRCUITS the highest circuit number. */
    public static final int BREAKERS = 4, FEEDER = 5, CIRCUITS = 5;
    public static final int RED = 0, BLUE = 1, GREEN = 2, YELLOW = 3, BLACK = 4;
    public static final int ALWAYS = 0, PHOTOCELL = 15;
    private static final int LINKS = 6, TAPS = 12, GROUPS = 18, SLOPES = 42;

    private WiringNode() {}

    public static long of(int circuit) {
        if (circuit < 1 || circuit > CIRCUITS) throw new IllegalArgumentException("circuit " + circuit);
        return circuit;
    }

    public static int circuit(long node) {
        return (int) (node & 15);
    }

    /** The node's colour: its circuit's ({@link #colourOf}). */
    public static int colour(long node) {
        return colourOf(circuit(node));
    }

    public static boolean linked(long node, Direction d) {
        return (node >> LINKS + d.get3DDataValue() & 1) != 0;
    }

    public static long link(long node, Direction d, boolean on) {
        long bit = 1L << LINKS + d.get3DDataValue();
        return on ? node | bit : node & ~bit;
    }

    /** The six link bits (bit i: Direction.from3DDataValue(i)). */
    public static int links(long node) {
        return (int) (node >> LINKS & 63);
    }

    public static boolean tapped(long node, Direction d) {
        return (node >> TAPS + d.get3DDataValue() & 1) != 0;
    }

    /** The six tap bits (bit i: Direction.from3DDataValue(i)). */
    public static int taps(long node) {
        return (int) (node >> TAPS & 63);
    }

    /** A tap toward d in the given group (it replaces an earlier tap there). */
    public static long tap(long node, Direction d, int group) {
        int i = d.get3DDataValue(), shift = GROUPS + 4 * i;
        return (node | 1L << TAPS + i) & ~(15L << shift) | (long) (group & 15) << shift;
    }

    public static long untap(long node, Direction d) {
        int i = d.get3DDataValue();
        return node & ~(1L << TAPS + i) & ~(15L << GROUPS + 4 * i);
    }

    public static int group(long node, Direction d) {
        return (int) (node >> GROUPS + 4 * d.get3DDataValue() & 15);
    }

    /** The slope link bit for horizontal direction h and up / down (0..7). */
    public static int slopeBit(Direction h, boolean up) {
        return h.get2DDataValue() * 2 + (up ? 1 : 0);
    }

    /** Linked to the node one toward h and one up (or down). */
    public static boolean slopeLinked(long node, Direction h, boolean up) {
        return (node >> SLOPES + slopeBit(h, up) & 1) != 0;
    }

    public static long slopeLink(long node, Direction h, boolean up, boolean on) {
        long bit = 1L << SLOPES + slopeBit(h, up);
        return on ? node | bit : node & ~bit;
    }

    /** The eight slope link bits (bit slopeBit(h, up)). */
    public static int slopes(long node) {
        return (int) (node >> SLOPES & 255);
    }

    /** How many wires leave the node: straight links and slope links (not taps). */
    public static int linkCount(long node) {
        return Integer.bitCount(links(node)) + Integer.bitCount(slopes(node));
    }

    /** A circuit's colour: 1 red, 2 blue, 3 green, 4 yellow; the feeder black. */
    public static int colourOf(int circuit) {
        return circuit == FEEDER ? BLACK : (circuit - 1) & 3;
    }

    /**
     * Two nodes of one circuit in one cell made one (saves before v2, WiringChunk#load): every link and slope link of
     * both; a's taps with their groups, then b's taps where a has none.
     */
    public static long merge(long a, long b) {
        long out = a | b & (63L << LINKS | 255L << SLOPES);
        for (Direction d : Direction.values()) if (tapped(b, d) && !tapped(out, d)) out = tap(out, d, group(b, d));
        return out;
    }

    /** The colour's lang key suffix. */
    public static String colourName(int colour) {
        return switch (colour) {
            case RED -> "red";
            case BLUE -> "blue";
            case GREEN -> "green";
            case YELLOW -> "yellow";
            default -> "black";
        };
    }
}
