package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;

import java.util.SplittableRandom;

/**
 * The house_small kind's kit (2026-10-21): Town House A ({@link MinimalTraditionalKit}) or B ({@link CapeCodKit}), one
 * or the other by the site's seed (its own stream, so the house's own choices stay as they were), half and half where B
 * fits; a site too narrow for B (12 wide) is always A. height and build ask the same choice.
 */
public final class SmallHouseKit implements BuildingKit {
    /** Salts the site's seed for the choice. */
    private static final long SALT = 0x5A4C_C0D_B0A7L;

    private final MinimalTraditionalKit a = new MinimalTraditionalKit();
    private final CapeCodKit b = new CapeCodKit();

    private BuildingKit kit(TownBuildings.Site site) {
        return CapeCodKit.fits(site) && new SplittableRandom(site.seed() ^ SALT).nextBoolean() ? b : a;
    }

    @Override
    public int height(TownBuildings.Site site) { return kit(site).height(site); }

    @Override
    public void build(TownBuildings.Site site, BuildingCanvas canvas) { kit(site).build(site, canvas); }
}
