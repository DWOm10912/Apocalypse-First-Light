package com.antaurora.apofirstlight.weapon.client;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.antaurora.apofirstlight.weapon.NativeGunItem;

/** Presentation-only resource profile, loaded once per entry; users can calibrate with a resource pack. */
public record FieldAttachmentViewProfile(float x,float y,float z,float centerX,float centerY,float centerZ,
                                         float pitch,float yaw,float roll,
                                         float scale,float enterTicks,float exitTicks) {
    public static final FieldAttachmentViewProfile DEFAULT=new FieldAttachmentViewProfile(0,0,-1.15f,0,0,0,0,90,0,.8f,7,6);
    public static FieldAttachmentViewProfile load(ItemStack stack){
        if(!(stack.getItem() instanceof NativeGunItem gun))return DEFAULT;
        var id=gun.definition().id();
        var resource=Minecraft.getInstance().getResourceManager().getResource(
                new ResourceLocation(id.getNamespace(),"field_attachment/"+id.getPath()+".json"));
        if(resource.isEmpty())return DEFAULT;
        try(var reader=resource.get().openAsReader()){
            var j=JsonParser.parseReader(reader).getAsJsonObject();
            var center=j.getAsJsonArray("center");
            var p=new FieldAttachmentViewProfile(j.get("x").getAsFloat(),j.get("y").getAsFloat(),j.get("z").getAsFloat(),
                    center.get(0).getAsFloat(),center.get(1).getAsFloat(),center.get(2).getAsFloat(),
                    j.get("pitch").getAsFloat(),j.get("yaw").getAsFloat(),j.get("roll").getAsFloat(),
                    j.get("scale").getAsFloat(),j.get("enter_ticks").getAsFloat(),j.get("exit_ticks").getAsFloat());
            if(!Float.isFinite(p.x+p.y+p.z+p.centerX+p.centerY+p.centerZ+p.pitch+p.yaw+p.roll+p.scale+p.enterTicks+p.exitTicks)
                    ||p.scale<=0||p.enterTicks<1||p.exitTicks<1)return DEFAULT;
            return p;
        }catch(Exception e){
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.warn("Invalid field attachment profile {}",id,e);
            return DEFAULT;
        }
    }
}
