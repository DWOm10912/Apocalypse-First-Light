package com.antaurora.apofirstlight.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * First-person body V1 (docs/rendering/first_person_body_v1.md) and the player skeleton V1 prototype: the client's own
 * settings, config/apocalypse_firstlight-client.toml.
 * Only a ForgeConfigSpec, so loading this class on a dedicated server is harmless.
 */
public final class FirstPersonBodyConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_BODY;
    public static final ForgeConfigSpec.BooleanValue SKELETON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("first_person");
        SHOW_BODY = builder.comment("Draw your own torso and legs (and what you wear on them) when you look down in first person.")
                .define("show_body", true);
        builder.pop();
        builder.push("skeleton");
        SKELETON = builder.comment("Player skeleton V1 prototype (docs/dev/player_skeleton_v1_design.md): every player is drawn by the AFL skeleton",
                        "(bending elbows and knees, one body for first and third person). On by default since 2026-10-16 (guns: known issues, see",
                        "docs/dev/player_actions_v1_plan.md 13); off: the vanilla body and the old first-person view models.")
                .define("enabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    public static boolean showBody() {
        return !SPEC.isLoaded() || SHOW_BODY.get();
    }

    public static boolean skeleton() {
        return SPEC.isLoaded() && SKELETON.get();
    }

    private FirstPersonBodyConfig() {
    }
}
