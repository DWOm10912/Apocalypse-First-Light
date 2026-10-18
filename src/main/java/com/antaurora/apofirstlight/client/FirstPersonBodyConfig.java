package com.antaurora.apofirstlight.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The client's own settings, config/apocalypse_firstlight-client.toml: first-person body V1
 * (docs/rendering/first_person_body_v1.md), the player skeleton V1 prototype, and the vehicle dashboard HUD's style and
 * speed unit (docs/vehicles/first_pickup_v0_design.md 8.12; changed in game on the AFL settings screen, Mods > Config).
 * Only a ForgeConfigSpec, so loading this class on a dedicated server is harmless.
 */
public final class FirstPersonBodyConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_BODY;
    public static final ForgeConfigSpec.BooleanValue SKELETON;
    /** Vehicle dashboard HUD: RING (a round tachometer) or BAND (a speed band). */
    public enum DashStyle { RING, BAND }
    /** The speed the dashboard shows. */
    public enum SpeedUnit { MPH, KMH }
    public static final ForgeConfigSpec.EnumValue<DashStyle> DASH_STYLE;
    public static final ForgeConfigSpec.EnumValue<SpeedUnit> SPEED_UNIT;
    /** The driving key hints at the top right (toggled in game with the hints key, VehicleDriving.HINTS). */
    public static final ForgeConfigSpec.BooleanValue KEY_HINTS;

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
        builder.push("vehicle_hud");
        DASH_STYLE = builder.comment("The driving dashboard at the bottom right: RING (a round segmented tachometer, the speed inside) or BAND",
                        "(a big speed number under a rising tachometer band).")
                .defineEnum("style", DashStyle.RING);
        SPEED_UNIT = builder.comment("The speed on the dashboard: MPH or KMH.").defineEnum("speed_unit", SpeedUnit.MPH);
        KEY_HINTS = builder.comment("Show the driving keys at the top right (Caps Lock by default toggles it while driving).").define("key_hints", true);
        builder.pop();
        SPEC = builder.build();
    }

    public static boolean showBody() {
        return !SPEC.isLoaded() || SHOW_BODY.get();
    }

    public static boolean skeleton() {
        return SPEC.isLoaded() && SKELETON.get();
    }

    public static DashStyle dashStyle() {
        return SPEC.isLoaded() ? DASH_STYLE.get() : DashStyle.RING;
    }

    public static SpeedUnit speedUnit() {
        return SPEC.isLoaded() ? SPEED_UNIT.get() : SpeedUnit.MPH;
    }

    public static boolean keyHints() {
        return !SPEC.isLoaded() || KEY_HINTS.get();
    }

    /** Flips the driving key hints and saves the choice. */
    public static void toggleKeyHints() {
        if (!SPEC.isLoaded()) return;
        KEY_HINTS.set(!KEY_HINTS.get());
        KEY_HINTS.save();
    }

    private FirstPersonBodyConfig() {
    }
}
