package com.antaurora.apofirstlight.liquid.ground;

/** Copy of C's GroundMaterial (GroundView V0, 6.2): the ground's material for infiltration and runoff. */
public record GroundMaterialV0(String id, Kind kind, double repose, double excavation) {
    public enum Kind { NONE, SOIL, SAND, GRAVEL, CLAY, MUD, ROCK, RUBBLE, SNOW }

    public static final GroundMaterialV0 NONE = new GroundMaterialV0("none", Kind.NONE, 0, 0);
}
