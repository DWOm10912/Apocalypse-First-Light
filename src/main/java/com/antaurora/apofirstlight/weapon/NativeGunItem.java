package com.antaurora.apofirstlight.weapon;

public interface NativeGunItem extends software.bernie.geckolib.animatable.GeoItem {
    String ACTION_CONTROLLER = "action";
    NativeGunDefinition definition();
    /** Null preserves the established pistol timeline and controller. */
    default String animationAsset() { return null; }
    /** Optional presentation capability; no substitute animation for unsupported guns. */
    default String inspectClip() { return null; }
    /** Select an inspect variant from the authoritative held stack, if supported. */
    default String inspectClip(net.minecraft.world.item.ItemStack stack) { return inspectClip(); }
    default String fireClip(boolean last) { return last ? "fire_last_round" : "fire"; }
    default String reloadClip(boolean empty) { return empty ? "reload_empty" : "reload"; }
    default int reloadTicks(boolean empty) {
        return empty ? definition().emptyReloadTicks() : definition().reloadDurationTicks();
    }
    default net.minecraft.sounds.SoundEvent fireSound() {
        return java.util.Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(definition().fireSound()),
                "Unregistered native fire sound " + definition().fireSound());
    }
    default net.minecraft.sounds.SoundEvent dryFireSound() {
        return java.util.Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(definition().dryFireSound()),
                "Unregistered native dry-fire sound " + definition().dryFireSound());
    }
}
