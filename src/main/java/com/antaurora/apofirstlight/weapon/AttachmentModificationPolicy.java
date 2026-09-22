package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared permission boundary, including the existing C.A.T. refusal. */
public final class AttachmentModificationPolicy {
    public static boolean allowed(ItemStack stack) {
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        return stack.getCount()==1 && stack.getItem() instanceof NativeGunItem
                && !(stack.getItem() instanceof CatNativeGunItem)
                && id!=null && "apocalypse_firstlight".equals(id.getNamespace());
    }
    public static boolean hasSlots(ItemStack stack) {
        if(!allowed(stack))return false;
        for(var slot:NativeAttachment.Slot.values())if(NativeAttachments.supportsSlot(stack,slot))return true;
        return false;
    }
    private AttachmentModificationPolicy() {}
}
