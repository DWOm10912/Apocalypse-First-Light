package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/**
 * Hotspot point is in the hotspot anchor bone's pivot frame, model units: hotspot_offset [x,y,z],
 * or the older straight-down hotspot_y (x = z = 0).
 * <p>
 * {@code standard}: the gun's own magazine as an item (docs/native_guns/magazine_system_v1.md). A gun with one keeps its
 * rounds in magazines: the fitted magazine is stored in the MAGAZINE slot (none: no magazine in the gun), a reload swaps in
 * a loaded one from the rig or the pockets, and magazines are loaded round by round. It is accepted like the others.
 * Without it the gun keeps the old rule (the magazine slot only changes the capacity, a reload takes loose rounds).
 */
public record NativeMagazineMount(Set<ResourceLocation> accepts,String hotspotAnchor,float hotspotX,float hotspotY,float hotspotZ,
                                  ResourceLocation standard) {
    public NativeMagazineMount{accepts=Set.copyOf(accepts);}
    public NativeMagazineMount(Set<ResourceLocation> accepts,String hotspotAnchor,float hotspotX,float hotspotY,float hotspotZ){
        this(accepts,hotspotAnchor,hotspotX,hotspotY,hotspotZ,null);
    }
    /** Whether the gun loads from magazine items (it has a standard magazine). */
    public boolean magazineItems(){return standard!=null;}
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
        ResourceLocation standard=null;
        if(data.has("standard")){
            standard=new ResourceLocation(data.get("standard").getAsString());
            if(validate&&!(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(standard) instanceof NativeMagazineItem))
                throw new IllegalArgumentException("Not a native magazine: "+standard);
            ids.add(standard);
        }
        String anchor=data.has("hotspot_anchor")?data.get("hotspot_anchor").getAsString():"magazine";
        if(data.has("hotspot_offset")){
            if(data.has("hotspot_y"))throw new IllegalArgumentException("magazine_slot: use hotspot_offset or hotspot_y, not both");
            var a=data.getAsJsonArray("hotspot_offset");if(a.size()!=3)throw new IllegalArgumentException("hotspot_offset needs xyz");
            float[] v=new float[3];
            for(int i=0;i<3;i++){v[i]=a.get(i).getAsFloat();if(!Float.isFinite(v[i])||Math.abs(v[i])>128)throw new IllegalArgumentException("hotspot_offset invalid");}
            return new NativeMagazineMount(ids,anchor,v[0],v[1],v[2],standard);
        }
        return new NativeMagazineMount(ids,anchor,0,data.has("hotspot_y")?data.get("hotspot_y").getAsFloat():-6.2f,0,standard);
    }
}
