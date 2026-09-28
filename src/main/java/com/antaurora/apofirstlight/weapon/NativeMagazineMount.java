package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Hotspot point is in the hotspot anchor bone's pivot frame, model units: hotspot_offset [x,y,z],
 * or the older straight-down hotspot_y (x = z = 0). */
public record NativeMagazineMount(Set<ResourceLocation> accepts,String hotspotAnchor,float hotspotX,float hotspotY,float hotspotZ) {
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
        String anchor=data.has("hotspot_anchor")?data.get("hotspot_anchor").getAsString():"magazine";
        if(data.has("hotspot_offset")){
            if(data.has("hotspot_y"))throw new IllegalArgumentException("magazine_slot: use hotspot_offset or hotspot_y, not both");
            var a=data.getAsJsonArray("hotspot_offset");if(a.size()!=3)throw new IllegalArgumentException("hotspot_offset needs xyz");
            float[] v=new float[3];
            for(int i=0;i<3;i++){v[i]=a.get(i).getAsFloat();if(!Float.isFinite(v[i])||Math.abs(v[i])>128)throw new IllegalArgumentException("hotspot_offset invalid");}
            return new NativeMagazineMount(ids,anchor,v[0],v[1],v[2]);
        }
        return new NativeMagazineMount(ids,anchor,0,data.has("hotspot_y")?data.get("hotspot_y").getAsFloat():-6.2f,0);
    }
}
