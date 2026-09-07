package com.antaurora.apofirstlight.weapon;

import net.minecraft.resources.ResourceLocation;

/** Small immutable gameplay/HUD metadata, independent of the visual rig. */
public record NativeGunDefinition(ResourceLocation id, ResourceLocation ammoType, int magazineCapacity,
                                  ResourceLocation hudIcon, int reloadDurationTicks, int magInTick,
                                  int fireIntervalTicks, double baseDamage, double headshotMultiplier,
                                  double falloffStart, double effectiveRange, double maxRange,
                                  double minimumDamageMultiplier, double spreadDegrees, double noiseRadius) {
    public NativeGunDefinition {
        if (magazineCapacity <= 0 || magInTick <= 0 || magInTick > reloadDurationTicks || fireIntervalTicks <= 0)
            throw new IllegalArgumentException("Invalid native gun timing/capacity");
    }

    public static final NativeGunDefinition SERVICE_PISTOL = new NativeGunDefinition(
            id("service_pistol"), id("9mm_round"), 17,
            id("textures/gui/gun/service_pistol_hud.png"), 26, 19, 3,
            7, 3, 24, 48, 64, .65, 1.2, 64);

    private static ResourceLocation id(String path) {
        return new ResourceLocation("apocalypse_firstlight", path);
    }
}
