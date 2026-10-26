package com.antaurora.apofirstlight.liquid;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.antaurora.apofirstlight.liquid.Liquid.Behaviour.*;

/**
 * The liquid definitions (docs/gameplay/water_v1_design.md 13.3). V1: water only (user, 2026-10-24); its evaporation and
 * infiltration come with package 3.
 */
public final class Liquids {
    /** Water at 20 deg C: 998 kg/m3, 1.0 mPa s. */
    public static final Liquid WATER = new Liquid("minecraft:water", 998, 1.0e-3, 15, Liquid.FlowModel.SHALLOW_PIPES, "water",
            EnumSet.of(EVAPORATION, INFILTRATION, ENTITY_EFFECTS, CONTAINERS));

    private static final Map<String, Liquid> BY_ID = new LinkedHashMap<>();

    static {
        BY_ID.put(WATER.id(), WATER);
    }

    private Liquids() {}

    /** The liquid saved under {@code id}, or null when there is none (a save from a later version). */
    public static Liquid byId(String id) {
        return BY_ID.get(id);
    }
}
