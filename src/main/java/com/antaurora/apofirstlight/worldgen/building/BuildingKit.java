package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;

/**
 * Draws one kind of building into its site (Town buildings V1, docs/worldgen/town_buildings_v1.md). Pure in its
 * inputs: the same site always gives the same cells (the site's seed for every choice), whatever chunk asks.
 */
public interface BuildingKit {
    /** The canvas height over the floor the building needs, blocks (its roof included). */
    int height(TownBuildings.Site site);

    /** Draws the building, facing north (BuildingCanvas turns it). */
    void build(TownBuildings.Site site, BuildingCanvas canvas);
}
