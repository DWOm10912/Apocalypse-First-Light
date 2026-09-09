package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record NativeMagazineMount(Set<ResourceLocation> accepts,String hotspotAnchor,float hotspotY) {
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
        var data=gun.getAsJsonObject("magazine_slot");
        return new NativeMagazineMount(ids,
                data.has("hotspot_anchor")?data.get("hotspot_anchor").getAsString():"magazine",
                data.has("hotspot_y")?data.get("hotspot_y").getAsFloat():-6.2f);
    }
}
