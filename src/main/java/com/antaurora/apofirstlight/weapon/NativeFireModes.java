package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;

/** Read without mutation on the client; server persists only actual changes or stale saved values. */
public final class NativeFireModes {
    public static final String TAG="AflGunFireMode";
    private NativeFireModes() {}
    public static NativeFireMode current(ItemStack stack, NativeGunDefinition definition) {
        if(stack.hasTag() && stack.getTag().contains(TAG)) {
            try {
                var mode=NativeFireMode.parse(stack.getTag().getString(TAG));
                if(definition.fire().modes().contains(mode))return mode;
            }catch(IllegalArgumentException ignored) { }
        }
        return definition.fire().defaultMode();
    }
    public static void sanitize(ItemStack stack, NativeGunDefinition definition) {
        if(stack.hasTag() && stack.getTag().contains(TAG)) {
            var mode=current(stack,definition);
            if(!stack.getTag().getString(TAG).equals(mode.key()))stack.getTag().putString(TAG,mode.key());
        }
    }
    public static boolean cycle(ItemStack stack, NativeGunDefinition definition) {
        var modes=definition.fire().modes();
        if(modes.size()<2)return false;
        var next=modes.get((modes.indexOf(current(stack,definition))+1)%modes.size());
        stack.getOrCreateTag().putString(TAG,next.key());
        return true;
    }
}
