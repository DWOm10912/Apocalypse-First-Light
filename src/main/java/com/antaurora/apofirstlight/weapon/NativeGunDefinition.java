package com.antaurora.apofirstlight.weapon;

import net.minecraft.resources.ResourceLocation;

/** Small immutable gameplay/HUD metadata, independent of the visual rig. */
public record NativeGunDefinition(ResourceLocation id, ResourceLocation ammoType, int magazineCapacity,
                                  ResourceLocation hudIcon, int reloadDurationTicks, int magInTick,
                                  int fireIntervalTicks, double baseDamage, double headshotMultiplier,
                                  double falloffStart, double effectiveRange, double maxRange,
                                  double minimumDamageMultiplier, double spreadDegrees, double noiseRadius,
                                  NativeRecoilProfile recoil, NativeTrailProfile trail) {
    public NativeGunDefinition {
        java.util.Objects.requireNonNull(recoil, "recoil");
        java.util.Objects.requireNonNull(trail, "trail");
        if (magazineCapacity <= 0 || magInTick <= 0 || magInTick > reloadDurationTicks || fireIntervalTicks <= 0)
            throw new IllegalArgumentException("Invalid native gun timing/capacity");
    }

    public static final NativeGunDefinition SERVICE_PISTOL = new NativeGunDefinition(
            id("service_pistol"), id("9mm_round"), 17,
            id("textures/gui/gun/service_pistol_hud.png"), 26, 19, 3,
            7, 3, 24, 48, 64, .65, 1.2, 64,
            new NativeRecoilProfile(.80, 1.10, -.12, .12, 4.5, 1.0,
                    .05, .18, 5, .04, .75, .75, .10), NativeTrailProfile.SUBTLE_PISTOL);

    private static ResourceLocation id(String path) {
        return new ResourceLocation("apocalypse_firstlight", path);
    }
}
