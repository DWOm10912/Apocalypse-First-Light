package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional packaged attachment data: data/&lt;ns&gt;/native_attachments/&lt;path&gt;.json (no file = code defaults).
 * noise_multiplier tunes a muzzle device; rated_ammo / mount_interface describe what it fits (a sight declares only
 * mount_interface). Gun JSON accepts lists stay the runtime authority; these two fields only validate them (see
 * NativeMuzzleMount, NativeSightMount).
 */
public record NativeAttachmentData(Double noiseMultiplier, ResourceLocation ratedAmmo, String mountInterface) {
    public static final NativeAttachmentData NONE=new NativeAttachmentData(null,null,null);
    private static final Map<ResourceLocation,NativeAttachmentData> CACHE=new ConcurrentHashMap<>();

    public double noiseMultiplier(double fallback){return noiseMultiplier!=null?noiseMultiplier:fallback;}

    public static NativeAttachmentData get(ResourceLocation id){
        return id==null?NONE:CACHE.computeIfAbsent(id,NativeAttachmentData::load);
    }
    private static NativeAttachmentData load(ResourceLocation id){
        try(var in=NativeAttachmentData.class.getResourceAsStream("/data/"+id.getNamespace()+"/native_attachments/"+id.getPath()+".json")){
            if(in==null)return NONE;
            return parse(id,JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(java.io.IOException e){throw new IllegalStateException(id.toString(),e);}
    }
    static NativeAttachmentData parse(ResourceLocation id,JsonObject o){
        Double noise=null;ResourceLocation ammo=null;String mount=null;
        if(o.has("noise_multiplier")){
            double v=o.get("noise_multiplier").getAsDouble();
            if(!Double.isFinite(v)||v<=0||v>1)throw new IllegalArgumentException(id+": noise_multiplier outside (0,1]");
            noise=v;
        }
        if(o.has("rated_ammo")){
            ammo=ResourceLocation.tryParse(o.get("rated_ammo").getAsString());
            if(ammo==null)throw new IllegalArgumentException(id+": invalid rated_ammo");
        }
        if(o.has("mount_interface")){
            mount=o.get("mount_interface").getAsString();
            if(mount.isBlank())throw new IllegalArgumentException(id+": empty mount_interface");
        }
        return new NativeAttachmentData(noise,ammo,mount);
    }
}
