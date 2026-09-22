package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;

/** Only transaction intent travels over the wire. sourceSlot=-1 means remove, otherwise install/replace. */
public record FieldAttachmentActionRequest(long token, int selectedSlot, long gunId, ItemStack expectedGun,
        NativeAttachment.Slot target, int sourceSlot, ItemStack expectedSource) {
    public FieldAttachmentActionRequest {
        expectedGun=expectedGun.copy();
        expectedSource=expectedSource.copy();
    }
}
