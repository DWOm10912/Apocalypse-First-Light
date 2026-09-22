package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.item.ItemStack;

/** Model-local attachment hit location. No camera or view-specific transform. */
public record AttachmentHotspotDefinition(NativeAttachment.Slot slot,String preferred,String fallback,
                                          float x,float y,float z) {
    public static AttachmentHotspotDefinition forSlot(ItemStack stack,NativeAttachment.Slot slot){
        if(!(stack.getItem() instanceof NativeGunItem gun)||!NativeAttachments.supportsSlot(stack,slot))return null;
        var d=gun.definition();
        return switch(slot){
            case SIGHT -> new AttachmentHotspotDefinition(slot,"maintenance_sight_anchor",d.sightMount().anchor(),
                    d.sightMount().x()/16f,d.sightMount().y()/16f,d.sightMount().z()/16f);
            case MUZZLE -> new AttachmentHotspotDefinition(slot,"maintenance_muzzle_anchor",d.muzzleMount().anchor(),0,0,0);
            case MAGAZINE -> new AttachmentHotspotDefinition(slot,null,d.magazineMount().hotspotAnchor(),0,d.magazineMount().hotspotY()/16f,0);
        };
    }
}
