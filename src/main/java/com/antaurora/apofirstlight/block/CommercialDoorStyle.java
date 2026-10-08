package com.antaurora.apofirstlight.block;

import net.minecraft.util.StringRepresentable;

/**
 * Commercial Wood Door looks (docs/models/steel_frame_doors_v1.md): one block, the look is appearance only (the same
 * shapes, interaction and drop). PLAIN: lever, closer, kick plate. RESTROOM: plus the occupancy indicator and the door
 * plaque. VISION: a 4" x 25" lite on the latch side instead of the solid slab.
 */
public enum CommercialDoorStyle implements StringRepresentable {
    PLAIN("plain"),
    RESTROOM("restroom"),
    VISION("vision");

    private final String name;

    CommercialDoorStyle(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }

    public CommercialDoorStyle next() { return values()[(ordinal() + 1) % values().length]; }
}
