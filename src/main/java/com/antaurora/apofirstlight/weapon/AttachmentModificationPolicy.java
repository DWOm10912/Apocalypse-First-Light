package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;

/** Default-allow native gun inspection; attachment transactions still validate slot compatibility. */
public final class AttachmentModificationPolicy {
    public static boolean allowed(ItemStack stack) {
        return stack.getCount()==1 && stack.getItem() instanceof NativeGunItem gun
                && gun.inspectionRefusalReason()==null;
    }
    private AttachmentModificationPolicy() {}
}
