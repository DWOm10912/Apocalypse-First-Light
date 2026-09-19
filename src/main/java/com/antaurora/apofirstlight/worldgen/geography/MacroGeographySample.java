package com.antaurora.apofirstlight.worldgen.geography;

/** Signed coastDistance is a radial/ellipse field distance in blocks, not a surveyed shoreline distance. */
public record MacroGeographySample(SurfaceClass surfaceClass, NationId nationId, int regionId,
                                   int landmassId, LandmassRole landmassRole, int waterbodyId,
                                   WaterClass waterClass, double coastDistance, double surfaceHeight) {
    public enum SurfaceClass { LAND, COAST, INLAND_WATER, OPEN_OCEAN }
    public enum NationId { NONE, MAIN_NATION }
    public enum LandmassRole { NONE, MAINLAND, STRATEGIC_ISLAND, MILITARY_ISLAND, INDUSTRIAL_ISLAND, MINOR_ISLAND }
    public enum WaterClass { NONE, INLAND_SEA, BAY, STRAIT, COASTAL_WATER, OPEN_OCEAN }

    public boolean isLand() { return landmassId >= 0; }
    public boolean isWater() { return !isLand(); }
}
