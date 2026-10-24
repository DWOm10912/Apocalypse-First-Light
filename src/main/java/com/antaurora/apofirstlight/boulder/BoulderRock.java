package com.antaurora.apofirstlight.boulder;

import net.minecraft.util.StringRepresentable;

/**
 * Rock R3 (docs/gameplay/rock_r1_stones_boulders_design.md 11.5): what rock a boulder or a loose stone is, for its look only
 * (the strikes, the drops and the mass stay R1's granite). Its sprites are the granite ones with granite replaced by
 * {@link #id} (textures/block/boulder: limestone, limestone_lichen, limestone_fresh, limestone_worn ...; tools/boulders
 * ROCKS). Order is the block state's and UndergroundPlan.ROCK_LOOKS'.
 */
public enum BoulderRock implements StringRepresentable {
    GRANITE("granite"), LIMESTONE("limestone"), SANDSTONE("sandstone"), SHALE("shale");

    public final String id;

    BoulderRock(String id) { this.id = id; }

    /** A granite sprite name (BoulderData / LooseStoneData SPRITES) as this rock's. */
    public String sprite(String granite) {
        return granite.startsWith("granite") ? id + granite.substring("granite".length()) : granite;
    }

    /** The rock of an id (UndergroundPlan.ROCK_LOOKS), granite if unknown. */
    public static BoulderRock of(String id) {
        for (BoulderRock r : values()) if (r.id.equals(id)) return r;
        return GRANITE;
    }

    @Override
    public String getSerializedName() { return id; }
}
