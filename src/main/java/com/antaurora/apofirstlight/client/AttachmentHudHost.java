package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.NativeAttachment;
import net.minecraft.world.item.ItemStack;

/** UI target/projection boundary. Both entry points use the same HUD and candidate page. */
public interface AttachmentHudHost {
    ItemStack gun();
    long revision();
    MaintenanceHotspots.Point project(NativeAttachment.Slot slot);
    void submit(ItemStack expected,long revision,NativeAttachment.Slot slot,int source,ItemStack stack);
    default void action(MaintenanceActionState action){}
}
