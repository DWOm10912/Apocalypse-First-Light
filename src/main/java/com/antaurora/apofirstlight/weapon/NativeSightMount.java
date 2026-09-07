package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Gun-local mount offset and optical axis, in the editable source's model units. */
public record NativeSightMount(String anchor, float x, float y, float z,
                               float aimX, float aimY, float aimZ, Set<ResourceLocation> accepts) {
    public NativeSightMount { accepts=Set.copyOf(accepts); }
    public static NativeSightMount parse(JsonObject gun, boolean validate) {
        if(!gun.has("sight_slot"))return null;
        var o=gun.getAsJsonObject("sight_slot");
        String anchor=o.get("anchor").getAsString();
        if(anchor.isBlank())throw new IllegalArgumentException("Empty sight anchor");
        var ids=new java.util.HashSet<ResourceLocation>();
        for(var e:o.getAsJsonArray("accepts")) {
            var id=new ResourceLocation(e.getAsString());
            if(validate && !(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id) instanceof NativeSightItem))
                throw new IllegalArgumentException("Not a native SIGHT item: "+id);
            ids.add(id);
        }
        float[] v=new float[6];int j=0;
        for(String key:java.util.List.of("mount_offset","ads_center")) {
            var a=o.getAsJsonArray(key);if(a.size()!=3)throw new IllegalArgumentException(key+" needs xyz");
            for(var e:a){float f=e.getAsFloat();if(!Float.isFinite(f)||Math.abs(f)>128)throw new IllegalArgumentException(key+" invalid");v[j++]=f;}
        }
        return new NativeSightMount(anchor,v[0],v[1],v[2],v[3],v[4],v[5],ids);
    }
}
