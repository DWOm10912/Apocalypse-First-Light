package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

public record NativeMuzzleMount(String anchor, Set<ResourceLocation> accepts) {
    public NativeMuzzleMount {accepts=Set.copyOf(accepts);}
    public static NativeMuzzleMount parse(JsonObject gun,boolean validate){
        if(!gun.has("muzzle_slot"))return null;
        var o=gun.getAsJsonObject("muzzle_slot");var name=o.get("anchor").getAsString();
        if(name.isBlank())throw new IllegalArgumentException("Empty muzzle anchor");
        var ids=new java.util.HashSet<ResourceLocation>();
        for(var value:o.getAsJsonArray("accepts")){
            var id=new ResourceLocation(value.getAsString());
            if(validate && (!(ForgeRegistries.ITEMS.getValue(id) instanceof NativeAttachment a)||a.slot()!=NativeAttachment.Slot.MUZZLE))
                throw new IllegalArgumentException("Not a native MUZZLE item: "+id);
            ids.add(id);
        }
        return new NativeMuzzleMount(name,ids);
    }
}
