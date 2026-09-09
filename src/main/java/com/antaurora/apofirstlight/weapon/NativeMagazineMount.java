package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record NativeMagazineMount(Set<ResourceLocation> accepts) {
    public NativeMagazineMount{accepts=Set.copyOf(accepts);}
    public static NativeMagazineMount parse(JsonObject gun,boolean validate){
        if(!gun.has("magazine_slot"))return null;
        var ids=new java.util.HashSet<ResourceLocation>();
        for(var value:gun.getAsJsonObject("magazine_slot").getAsJsonArray("accepts")){
            var id=new ResourceLocation(value.getAsString());
            if(validate&&!(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id) instanceof NativeMagazineItem))
                throw new IllegalArgumentException("Not a native magazine: "+id);
            ids.add(id);
        }
        return new NativeMagazineMount(ids);
    }
}
