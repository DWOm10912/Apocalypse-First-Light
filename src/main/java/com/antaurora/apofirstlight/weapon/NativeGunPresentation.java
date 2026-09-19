package com.antaurora.apofirstlight.weapon;

/** Class defaults plus optional per-definition presentation overrides. */
public record NativeGunPresentation(int hudWidth, int hudHeight, int magInTick, int emptyMagInTick,
                                    NativeTrailProfile trail, NativeHitEffect hitEffect) {
    public NativeGunPresentation(int width,int height,int magIn,int emptyMagIn,NativeTrailProfile trail) {
        this(width,height,magIn,emptyMagIn,trail,NativeHitEffect.NONE);
    }
    public static NativeGunPresentation defaults(WeaponClass weaponClass, int tacticalTicks, int emptyTicks) {
        return switch (weaponClass) {
            case PISTOL -> new NativeGunPresentation(36, 22, Math.min(19, tacticalTicks), emptyTicks, NativeTrailProfile.SUBTLE_PISTOL);
            case SMG -> new NativeGunPresentation(48, 18, Math.min(19, tacticalTicks), emptyTicks, NativeTrailProfile.SUBTLE_PISTOL);
            case RIFLE, PRECISION_RIFLE, MACHINE_GUN ->
                    new NativeGunPresentation(60, 12, tacticalTicks, emptyTicks, NativeTrailProfile.SUBTLE_RIFLE);
            case SHOTGUN, SPECIAL -> new NativeGunPresentation(60, 14, tacticalTicks, emptyTicks, NativeTrailProfile.SUBTLE_RIFLE);
        };
    }
}
