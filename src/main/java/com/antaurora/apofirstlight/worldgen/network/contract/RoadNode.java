package com.antaurora.apofirstlight.worldgen.network.contract;

/**
 * Where a public road meets a private road or a lot (contract section 4): both surfaces meet here at the same height and
 * heading. The private side is built up to the node, the public side starts at it; the transition between the two
 * surfaces is C's. Only the structure's owner creates a node; C never moves it.
 *
 * @param id      stable, e.g. {@code forest_home_b/driveway}
 * @param x       the hand-over point (metres)
 * @param y       the finished surface height there
 * @param z       the hand-over point (metres)
 * @param heading degrees (contract section 3: 0 = +X east, 90 = +Z south), from the private side toward the public road
 * @param width   metres at the hand-over
 * @param grade   the grade along the heading at the hand-over (rise / run; C's junction follows it)
 */
public record RoadNode(String id, double x, double y, double z, double heading, PrivateSpec privateSpec, PublicClass publicClass,
                       double width, double grade, String owner, State state, int version) {
    public static final int VERSION = 1;

    public enum PrivateSpec { PRIVATE_GRAVEL, LOT_APRON }

    /** The public road to join; an interstate never joins a lot directly. */
    public enum PublicClass { COUNTY, STATE, INTERSTATE }

    /** PLANNED: plan only; DEAD_END: no public road yet, the private side ends here; CONNECTED. */
    public enum State { PLANNED, DEAD_END, CONNECTED }
}
