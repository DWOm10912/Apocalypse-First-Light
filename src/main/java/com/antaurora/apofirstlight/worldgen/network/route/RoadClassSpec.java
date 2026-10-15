package com.antaurora.apofirstlight.worldgen.network.route;

/**
 * The public road classes of the federal national network (C3, 2026-10-14; docs/worldgen/federal_highways_c3.md, 2).
 * Each class has its own design rules; nothing is "an interstate by default". Pure data, metres, no Minecraft.
 * <p>
 * Not here: the private gravel driveway (Main, the forest house) and town streets (the future town planner). They
 * share the road surface base later, with their own parameters.
 * <ul>
 *   <li>grades: {@code grade} is the design limit everywhere; {@code gradeMountain} is allowed only where the route
 *   climbs through mountain or plateau country (AASHTO's terrain classes: level / rolling vs mountainous). Steeper than
 *   that is never a road of this class: the planner detours, or reports a tunnel or a viaduct;</li>
 *   <li>radii: {@code radius} on the whole route; tighter bends are relaxed out by the planner;</li>
 *   <li>{@code edge}: lateral distance from the centreline to the outer paved edge; {@code formation}: to the edge of
 *   the engineered road body (paved shoulders, unpaved shoulder rounding); {@code reserve}: the corridor half width that
 *   forests, lots, houses and structures keep clear of (hard within the formation and its slopes, soft beyond);</li>
 *   <li>{@code maxCut} / {@code maxFill}: the deepest cut and the highest fill the class builds as earthworks; deeper is a
 *   tunnel, higher is a viaduct (both reported in the plan);</li>
 *   <li>{@code clearance}: deck soffit above the water surface at a bridge.</li>
 * </ul>
 */
public enum RoadClassSpec {
    /** R4 rural four-lane freeway (the accepted R4 sample section, 44 m): 110 km/h. */
    INTERSTATE("interstate", 110, 0.03, 0.05, 600, 22.0, 22.5, 60, 18, 14, 6.0, 40, 0.7),
    /** Two-lane state highway: 2 x 3.65 m lanes, 2.4 m paved shoulders; 90 km/h. */
    STATE("state", 90, 0.05, 0.07, 250, 6.05, 7.0, 30, 12, 9, 5.0, 25, 0.5),
    /** Two-lane rural county road: 2 x 3.3 m lanes, 0.6 m paved edge, 1 m gravel shoulder; 70 km/h. */
    COUNTY("county", 70, 0.07, 0.10, 100, 3.9, 4.9, 15, 7, 6, 3.5, 15, 0.3);

    public final String id;
    public final int speedKmh;
    public final double grade, gradeMountain, radius, edge, formation, reserve, maxCut, maxFill, clearance, sigma, raise;

    RoadClassSpec(String id, int speedKmh, double grade, double gradeMountain, double radius, double edge, double formation,
                  double reserve, double maxCut, double maxFill, double clearance, double sigma, double raise) {
        this.id = id;
        this.speedKmh = speedKmh;
        this.grade = grade;
        this.gradeMountain = gradeMountain;
        this.radius = radius;
        this.edge = edge;
        this.formation = formation;
        this.reserve = reserve;
        this.maxCut = maxCut;
        this.maxFill = maxFill;
        this.clearance = clearance;
        this.sigma = sigma;
        this.raise = raise;
    }

    /** Bridge deck depth under the pavement (deck slab and beams), m. */
    public double deckDepth() { return this == INTERSTATE ? 1.8 : this == STATE ? 1.4 : 1.0; }

    /** First vertical-curve smoothing of the profile (Gaussian sigma), m; sharper curves are widened locally to kMin. */
    public double vSigma() { return this == INTERSTATE ? 40 : this == STATE ? 25 : 15; }

    /**
     * The smallest crest / sag K the design should reach, m per % of grade change: vertical acceleration v^2 / (100 K)
     * at the design speed stays about 0.3-0.5 m/s2 (ride comfort; the planner reports it, C3.1).
     */
    public double kMin() { return this == INTERSTATE ? 30 : this == STATE ? 15 : 8; }
}
