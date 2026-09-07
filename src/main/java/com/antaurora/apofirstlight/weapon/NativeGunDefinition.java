package com.antaurora.apofirstlight.weapon;

import net.minecraft.resources.ResourceLocation;

/** Small immutable gameplay/HUD metadata, independent of the visual rig. */
public record NativeGunDefinition(ResourceLocation id, ResourceLocation ammoType, int magazineCapacity,
                                  ResourceLocation hudIcon, int hudWidth, int hudHeight, int reloadDurationTicks, int magInTick,
                                  int fireIntervalTicks, double baseDamage, double headshotMultiplier,
                                  double falloffStart, double effectiveRange, double maxRange,
                                  double minimumDamageMultiplier, double spreadDegrees, double noiseRadius,
                                  NativeRecoilProfile recoil, NativeTrailProfile trail, NativeAccuracyProfile accuracy) {
    public NativeGunDefinition {
        java.util.Objects.requireNonNull(recoil, "recoil");
        java.util.Objects.requireNonNull(trail, "trail");
        java.util.Objects.requireNonNull(accuracy, "accuracy");
        if (!Double.isFinite(spreadDegrees) || spreadDegrees <= 0 || spreadDegrees > 45)
            throw new IllegalArgumentException("Invalid spread half-angle");
        if (magazineCapacity <= 0 || magInTick <= 0 || magInTick > reloadDurationTicks || fireIntervalTicks <= 0)
            throw new IllegalArgumentException("Invalid native gun timing/capacity");
    }

    public static final NativeGunDefinition P9_01 = new NativeGunDefinition(
            id("p9_01"), id("9mm_round"), 17,
            id("textures/gui/gun/p9_01_hud.png"), 36, 22, 26, 19, 3,
            7, 1.5, 24, 48, 64, .65, 1.2, 64,
            new NativeRecoilProfile(.80, 1.10, -.12, .18, 4.5, 1.0,
                    .05, .18, 5, .04, .75, .75, .10, .70, .16), NativeTrailProfile.SUBTLE_PISTOL, NativeAccuracyProfile.DEFAULT);

    // Temporary shared 9mm/FX/balance defaults; no new rifle ammunition system.
    public static final NativeGunDefinition BR51_01 = new NativeGunDefinition(
            id("br51_01"), P9_01.ammoType(), 20, id("textures/gui/gun/br51_01_hud.png"), 60, 12,
            NativeGunAnimations.ticks("br51_01", "reload_tactical"), NativeGunAnimations.ticks("br51_01", "reload_tactical"), 4,
            18, P9_01.headshotMultiplier(), P9_01.falloffStart(),
            P9_01.effectiveRange(), P9_01.maxRange(), P9_01.minimumDamageMultiplier(),
            .30, P9_01.noiseRadius(), P9_01.recoil(), P9_01.trail(), NativeAccuracyProfile.BATTLE_RIFLE);

    private static ResourceLocation id(String path) {
        return new ResourceLocation("apocalypse_firstlight", path);
    }
}
