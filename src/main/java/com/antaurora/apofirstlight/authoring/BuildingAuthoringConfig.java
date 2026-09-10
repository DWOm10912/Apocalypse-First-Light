package com.antaurora.apofirstlight.authoring;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BuildingAuthoringConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    static {
        var b=new ForgeConfigSpec.Builder();
        ENABLED=b.comment("Development asset authoring only. Requires creative or operator permission 2. No world generation.")
                .define("buildingAuthoringEnabled",false);
        SPEC=b.build();
    }
    private BuildingAuthoringConfig() {}
}
