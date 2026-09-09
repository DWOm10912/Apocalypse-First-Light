package com.antaurora.apofirstlight.weapon;

import net.minecraft.resources.ResourceLocation;

/** Small immutable gameplay/HUD metadata, independent of the visual rig. */
public record NativeGunDefinition(ResourceLocation id, ResourceLocation ammoType, int magazineCapacity,
                                  ResourceLocation hudIcon, int hudWidth, int hudHeight, int reloadDurationTicks, int magInTick,
                                  int fireIntervalTicks, double baseDamage,
                                  double falloffStart, double effectiveRange, double maxRange,
                                  double minimumDamageMultiplier, double spreadDegrees, double noiseRadius,
                                  NativeRecoilProfile recoil, NativeTrailProfile trail, NativeAccuracyProfile accuracy,
                                  boolean gunshotTinnitus, int emptyReloadTicks, float adsTicks, float adsFov, ResourceLocation casing,
                                  NativeSightMount sightMount, NativeMuzzleMount muzzleMount, ResourceLocation suppressedFireSound, NativeMagazineMount magazineMount) {
    public NativeGunDefinition {
        java.util.Objects.requireNonNull(recoil, "recoil");
        java.util.Objects.requireNonNull(trail, "trail");
        java.util.Objects.requireNonNull(accuracy, "accuracy");
        if (!Double.isFinite(spreadDegrees) || spreadDegrees < 0 || spreadDegrees > 45)
            throw new IllegalArgumentException("Invalid spread half-angle");
        if (magazineCapacity <= 0 || magInTick < 0 || fireIntervalTicks <= 0)
            throw new IllegalArgumentException("Invalid native gun timing/capacity");
    }
    public double headshotMultiplier() { return NativeHeadshots.MULTIPLIER.get(); }

    // Packaged JSON defaults retained for compatibility with DEV tests; items resolve live by ID.
    public static final NativeGunDefinition P9_01 = NativeGunData.packaged(new ResourceLocation("apocalypse_firstlight", "p9_01"));
    public static final NativeGunDefinition BR51_01 = NativeGunData.packaged(new ResourceLocation("apocalypse_firstlight", "br51_01"));
}
