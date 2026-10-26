package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.ground.GroundMaterialV0;

/**
 * What the ground does with a thin film of water (docs/gameplay/water_falling_v1_design.md 12, a first part of package 3):
 * <ul>
 *   <li>Depression storage: the first millimetres on a floor fill its small hollows (soil crumbs, grass, a rough stone face)
 *   and do not run off. Typical values (ASCE / WEF drainage manuals): pavement and rock 1.3-2.5 mm, lawns 2.5-5 mm.</li>
 *   <li>That film soaks in at the floor's infiltration rate, saturated hydraulic conductivity from Rawls, Brakensiek and
 *   Miller (1983): sand 118 mm/h, loamy sand 30, sandy loam 11, loam 3.4, clay 0.3; topsoil under grass is held at
 *   10 mm/h (roots and worm holes), gravel and rubble drain freely. Rock and anything that is not natural ground: none.</li>
 *   <li>It also evaporates, about 5 mm a day in summer (0.2 mm a game hour).</li>
 * </ul>
 * Only the film soaks in and dries: water standing deeper than the storage (a pond, a full pit) keeps its water until
 * package 3 brings infiltration under standing water and saturation. 1 mm/h on 1 m2 is 1 mL a tick (LiquidUnits).
 */
public final class LiquidSoak {
    /** Evaporation from a film, mm a game hour. */
    public static final double EVAPORATION_MM_H = 0.2;
    /**
     * Horton's decay of the infiltration capacity once the ground is soaking (per game hour; Horton 1940, typical
     * 2-5 /h): f = fc + (f0 - fc) e^(-kt), the same as f = f0 - k F with F the water taken in above fc since it started.
     */
    public static final double HORTON_K = 4;

    /**
     * The infiltration capacity of dry ground, mm a game hour (Horton's f0: Akan 1993 / ASCE tables, dry loam with grass
     * 75, dry sand 125-250, clay 25). Never below {@link #infiltrationMmH} (fc, where it settles when soaked).
     */
    public static double initialInfiltrationMmH(GroundMaterialV0.Kind kind) {
        return Math.max(infiltrationMmH(kind), switch (kind) {
            case SOIL -> 75;
            case SAND -> 250;
            case GRAVEL -> 400;
            case RUBBLE -> 150;
            case CLAY -> 25;
            case MUD, SNOW, ROCK, NONE -> 0;
        });
    }

    /**
     * How long ground stays dark after its last water soaked in or dried (game hours): only how it looks, the water is
     * gone. Soil and clay hold their surface moisture for hours, sand and gravel drain and pale fast, rock and paving are
     * wet only while water is on them.
     */
    public static double darkHours(GroundMaterialV0.Kind kind) {
        return switch (kind) {
            case SOIL -> 2;
            case CLAY, MUD -> 3;
            case RUBBLE -> 1;
            case SAND, GRAVEL -> 0.5;
            case SNOW -> 0;
            case ROCK, NONE -> 0.25;
        };
    }

    private LiquidSoak() {
    }

    /** Depression storage of a floor, mm. */
    public static double storageMm(GroundMaterialV0.Kind kind) {
        return switch (kind) {
            case SOIL -> 4;
            case SAND, GRAVEL, RUBBLE, CLAY -> 3;
            case MUD, SNOW -> 2;
            case ROCK, NONE -> 1.5;
        };
    }

    /** Infiltration of a floor under a film, mm a game hour. */
    public static double infiltrationMmH(GroundMaterialV0.Kind kind) {
        return switch (kind) {
            case SOIL -> 10;
            case SAND -> 118;
            case GRAVEL -> 200;
            case RUBBLE -> 50;
            case CLAY -> 0.3;
            case MUD -> 0.5;
            case SNOW, ROCK, NONE -> 0;
        };
    }
}
