package com.antaurora.apofirstlight.weapon;

public interface NativeGunItem extends software.bernie.geckolib.animatable.GeoItem {
    NativeGunDefinition definition();
    /** Null preserves the established pistol timeline and controller. */
    default String animationAsset() { return null; }
    /** Optional presentation capability; no substitute animation for unsupported guns. */
    default String inspectClip() { return null; }
    default String fireClip(boolean last) { return last ? "fire_last_round" : "fire"; }
    default String reloadClip(boolean empty) { return empty ? "reload_empty" : "reload"; }
    default int reloadTicks(boolean empty) {
        return empty ? definition().emptyReloadTicks() : definition().reloadDurationTicks();
    }
    default net.minecraft.sounds.SoundEvent fireSound() {
        return com.antaurora.apofirstlight.registry.AflSounds.P9_01_FIRE.get();
    }
}
