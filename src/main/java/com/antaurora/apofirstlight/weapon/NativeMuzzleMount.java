package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

/** accepts stays the runtime compatibility authority; mount_interface + the gun's ammo only validate it. */
public record NativeMuzzleMount(String anchor, Set<ResourceLocation> accepts, String mountInterface) {
    public NativeMuzzleMount {accepts=Set.copyOf(accepts);}
    public static NativeMuzzleMount parse(JsonObject gun,boolean validate){
        if(!gun.has("muzzle_slot"))return null;
        var o=gun.getAsJsonObject("muzzle_slot");var name=o.get("anchor").getAsString();
        if(name.isBlank())throw new IllegalArgumentException("Empty muzzle anchor");
        String mount=o.has("mount_interface")?o.get("mount_interface").getAsString():null;
        if(mount!=null&&mount.isBlank())throw new IllegalArgumentException("Empty muzzle mount_interface");
        var ammo=gun.has("ammo")?ResourceLocation.tryParse(gun.get("ammo").getAsString()):null;
        var ids=new java.util.HashSet<ResourceLocation>();
        for(var value:o.getAsJsonArray("accepts")){
            var id=new ResourceLocation(value.getAsString());
            if(validate && (!(ForgeRegistries.ITEMS.getValue(id) instanceof NativeAttachment a)||a.slot()!=NativeAttachment.Slot.MUZZLE))
                throw new IllegalArgumentException("Not a native MUZZLE item: "+id);
            // Declared device data guards against an obvious calibre / interface mistake in an accepts list.
            var data=NativeAttachmentData.get(id);
            if(data.ratedAmmo()!=null&&!data.ratedAmmo().equals(ammo))
                throw new IllegalArgumentException(id+" is rated for "+data.ratedAmmo()+", gun fires "+ammo);
            if(data.mountInterface()!=null&&!data.mountInterface().equals(mount))
                throw new IllegalArgumentException(id+" needs mount_interface "+data.mountInterface()+", slot declares "+mount);
            ids.add(id);
        }
        return new NativeMuzzleMount(name,ids,mount);
    }
}
