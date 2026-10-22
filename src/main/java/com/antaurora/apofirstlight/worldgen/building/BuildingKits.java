package com.antaurora.apofirstlight.worldgen.building;

import java.util.Map;

/**
 * The kit of each building kind (TownBuildings.KINDS; Town buildings V1, docs/worldgen/town_buildings_v1.md). V1: every
 * kind is a WhiteBoxKit in its kind's facade material; a kind's own kit (the minimal traditional house, the two-part
 * commercial block, ...) replaces its entry, and the world lock's town_buildings version goes up with it. 2 (2026-10-21):
 * house_small is Town House A (MinimalTraditionalKit). 3 (2026-10-21): its stoops on the concrete stoop steps (six risers).
 * 4 (2026-10-21): house_small is Town House A or B (SmallHouseKit: CapeCodKit on half the sites 13 wide and over).
 * 5 (2026-10-21): the stoops' landing is smooth_concrete (stoop_concrete renamed, no blowholes).
 */
public final class BuildingKits {
    private BuildingKits() {}

    /** The kits' version (the world lock's town_buildings system, with TownBuildings.VERSION). */
    public static final int VERSION = 5;

    private static final Map<String, BuildingKit> KITS = Map.of(
            "house_small", new SmallHouseKit(),
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
