package com.antaurora.apofirstlight.worldgen.building;

import java.util.Map;

/**
 * The kit of each building kind (TownBuildings.KINDS; Town buildings V1, docs/worldgen/town_buildings_v1.md). V1: every
 * kind is a WhiteBoxKit in its kind's facade material; a kind's own kit (the minimal traditional house, the two-part
 * commercial block, ...) replaces its entry, and the world lock's town_buildings version goes up with it.
 */
public final class BuildingKits {
    private BuildingKits() {}

    /** The kits' version (the world lock's town_buildings system, with TownBuildings.VERSION). */
    public static final int VERSION = 1;

    private static final Map<String, BuildingKit> KITS = Map.of(
            "house_small", new WhiteBoxKit("grey_board_and_batten"),
            "house_large", new WhiteBoxKit("grey_board_and_batten"),
            "mobile_home", new WhiteBoxKit("metal_wall_panel"),
            "shop", new WhiteBoxKit("face_brick_warm_gray"),
            "highway_store", new WhiteBoxKit("face_brick_warm_gray"),
            "civic_hall", new WhiteBoxKit("face_brick_charcoal"),
            "warehouse", new WhiteBoxKit("metal_wall_panel"),
            "school", new WhiteBoxKit("face_brick_warm_gray"),
            "landmark", new WhiteBoxKit("face_brick_charcoal"));

    /** The kit of a kind, or null. */
    public static BuildingKit of(String kind) { return KITS.get(kind); }
}
