package com.antaurora.apofirstlight.weapon;

/** Class defaults plus optional per-definition presentation overrides. */
public record NativeGunPresentation(int hudWidth, int hudHeight, int magInTick, NativeTrailProfile trail) {
    public static NativeGunPresentation defaults(WeaponClass weaponClass, int tacticalTicks) {
        return switch (weaponClass) {
            case PISTOL -> new NativeGunPresentation(36, 22, Math.min(19, tacticalTicks), NativeTrailProfile.SUBTLE_PISTOL);
            case SMG -> new NativeGunPresentation(48, 18, Math.min(19, tacticalTicks), NativeTrailProfile.SUBTLE_PISTOL);
            case RIFLE, PRECISION_RIFLE, MACHINE_GUN ->
                    new NativeGunPresentation(60, 12, tacticalTicks, NativeTrailProfile.SUBTLE_RIFLE);
            case SHOTGUN, SPECIAL -> new NativeGunPresentation(60, 14, tacticalTicks, NativeTrailProfile.SUBTLE_RIFLE);
        };
    }
}
