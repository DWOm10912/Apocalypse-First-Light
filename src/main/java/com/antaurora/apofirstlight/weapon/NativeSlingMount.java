package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

/**
 * The rear sling swivel (gun JSON {@code sling_slot}): where the sling hotspot sits on the Z page and the maintenance
 * bench, {@code hotspot_offset} in model pixels from the anchor bone's pivot. accepts stays the compatibility authority.
 * {@code strap.front / strap.rear} (docs/native_guns/attachments/sling_physics_v1.md 3): the swivels' bottom bars the
 * strap's snap hooks clip onto, a bone and model pixels from its pivot; null when the gun has no strap drawn.
 */
public record NativeSlingMount(String anchor, float x, float y, float z, Set<ResourceLocation> accepts, StrapEnd front, StrapEnd rear) {
    public NativeSlingMount {accepts=Set.copyOf(accepts);}
    /** One strap end: the bone the swivel moves with and the bar's centre in model pixels from that bone's pivot. */
    public record StrapEnd(String bone, float x, float y, float z) {
        static StrapEnd parse(JsonObject o){
            var bone=o.get("bone").getAsString();var p=o.getAsJsonArray("point");
            if(bone.isBlank()||p==null||p.size()!=3)throw new IllegalArgumentException("sling strap end needs a bone and a 3-value point");
            float x=p.get(0).getAsFloat(),y=p.get(1).getAsFloat(),z=p.get(2).getAsFloat();
            if(!Float.isFinite(x+y+z))throw new IllegalArgumentException("Invalid sling strap point");
            return new StrapEnd(bone,x,y,z);
        }
    }
    public static NativeSlingMount parse(JsonObject gun,boolean validate){
        if(!gun.has("sling_slot"))return null;
        var o=gun.getAsJsonObject("sling_slot");var name=o.get("anchor").getAsString();
        if(name.isBlank())throw new IllegalArgumentException("Empty sling anchor");
        var offset=o.has("hotspot_offset")?o.getAsJsonArray("hotspot_offset"):null;
        if(offset!=null&&offset.size()!=3)throw new IllegalArgumentException("sling hotspot_offset needs 3 values");
        float x=offset==null?0:offset.get(0).getAsFloat(),y=offset==null?0:offset.get(1).getAsFloat(),z=offset==null?0:offset.get(2).getAsFloat();
        if(!Float.isFinite(x+y+z))throw new IllegalArgumentException("Invalid sling hotspot_offset");
        var ids=new java.util.HashSet<ResourceLocation>();
        for(var value:o.getAsJsonArray("accepts")){
            var id=new ResourceLocation(value.getAsString());
            if(validate && (!(ForgeRegistries.ITEMS.getValue(id) instanceof NativeAttachment a)||a.slot()!=NativeAttachment.Slot.SLING))
                throw new IllegalArgumentException("Not a native SLING item: "+id);
            ids.add(id);
        }
        StrapEnd front=null,rear=null;
        if(o.has("strap")){var strap=o.getAsJsonObject("strap");front=StrapEnd.parse(strap.getAsJsonObject("front"));rear=StrapEnd.parse(strap.getAsJsonObject("rear"));}
        return new NativeSlingMount(name,x,y,z,ids,front,rear);
    }
}
