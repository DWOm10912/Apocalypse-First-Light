package com.antaurora.apofirstlight.worldgen.network.town;

/**
 * The town street sections (docs/worldgen/north_american_roads_and_lots_spec_v1.md 2; Town Framework V1): the asphalt
 * width the class is named for, and on each side from the asphalt edge outward the curb band, the utility / planting
 * strip and the sidewalk; the right of way is all of it. Metres (blocks).
 * <pre>
 *   R12 residential   12 + 2 x (1 + 2 + 2) = 22
 *   C14 commercial    14 + 2 x (1 + 2 + 3) = 26   (Main St, the exit's cross street)
 *   I12 industrial    12 + 2 x (1 + 3 + 2) = 24   (rows of industry only; V1 lays it as R12: #layout)
 * </pre>
 * Heights (the spec's G / S): the sidewalk top and the lots' pads at G, the asphalt edge S = G - CURB_HEIGHT; the asphalt
 * crowned CROWN on its centre line over its edges.
 */
public final class TownStreets {
    private TownStreets() {}

    /** The curb's height over the asphalt edge (3/16 block), the crown of the asphalt's centre over its edges, m. */
    public static final double CURB_HEIGHT = 3.0 / 16, CROWN = 0.12;

    public record Section(String name, double asphalt, double curb, double utility, double sidewalk) {
        public double row() { return asphalt + 2 * (curb + utility + sidewalk); }

        public double halfAsphalt() { return asphalt / 2; }

        public double halfRow() { return row() / 2; }
    }

    public static final Section R12 = new Section("R12", 12, 1, 2, 2);
    public static final Section C14 = new Section("C14", 14, 1, 2, 3);
    public static final Section I12 = new Section("I12", 12, 1, 3, 2);

    /** The section a street is built with (V1: I12 on R12's right of way, the grid's blocks are spaced by R12). */
    public static Section layout(String cls) { return cls.equals("C14") ? C14 : R12; }

    public static Section of(String cls) {
        return switch (cls) {
            case "C14" -> C14;
            case "I12" -> I12;
            default -> R12;
        };
    }
}
