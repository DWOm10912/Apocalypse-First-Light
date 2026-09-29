package com.antaurora.apofirstlight.weapon;

public interface NativeGunItem extends software.bernie.geckolib.animatable.GeoItem {
    String ACTION_CONTROLLER = "action";
    NativeGunDefinition definition();
    /** Field inspection and maintenance are opt-out, independent of attachment slots. */
    default net.minecraft.network.chat.Component inspectionRefusalReason() { return null; }
    /** Null selects the generic no-animation fallback. */
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
    /** Whether this gun's animation asset can play the named clip (magazine reload overrides need it). */
    default boolean supportsClip(String clip) { return false; }
    /** The fitted magazine's reload override for this reload kind, if the gun can play its clip (else the gun's own reload). */
    default NativeAttachmentData.ReloadOverride reloadOverride(net.minecraft.world.item.ItemStack stack, boolean empty) {
        var magazine = NativeAttachments.active(stack, NativeAttachment.Slot.MAGAZINE);
        if (magazine.isEmpty()) return null;
        var override = NativeAttachmentData.get(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(magazine.getItem())).reload(empty);
        return override != null && supportsClip(override.clip()) ? override : null;
    }
    default String reloadClip(net.minecraft.world.item.ItemStack stack, boolean empty) {
        var override = reloadOverride(stack, empty);
        return override != null ? override.clip() : reloadClip(empty);
    }
    default int reloadTicks(net.minecraft.world.item.ItemStack stack, boolean empty) {
        var override = reloadOverride(stack, empty);
        return override != null ? override.ticks() : reloadTicks(empty);
    }
    /** Tick after the reload start at which the rounds are committed. */
    default int magInTick(net.minecraft.world.item.ItemStack stack, boolean empty) {
        var override = reloadOverride(stack, empty);
        return override != null ? override.magInTick() : empty ? definition().emptyMagInTick() : definition().magInTick();
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
