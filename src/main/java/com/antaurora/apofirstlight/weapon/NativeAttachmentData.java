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
 * NativeMuzzleMount, NativeSightMount). reload_overrides lets a magazine replace the gun's tactical and / or empty reload
 * (animation clip, duration, magazine-in commit tick), e.g. a drum that cannot be knocked out by the next magazine.
 */
public record NativeAttachmentData(Double noiseMultiplier, ResourceLocation ratedAmmo, String mountInterface,
                                   ReloadOverride tacticalReload, ReloadOverride emptyReload) {
    public static final NativeAttachmentData NONE=new NativeAttachmentData(null,null,null,null,null);
    private static final Map<ResourceLocation,NativeAttachmentData> CACHE=new ConcurrentHashMap<>();

    /** clip in the gun's own animation asset; ticks = whole reload; magInTick = when the rounds are committed (<= ticks). */
    public record ReloadOverride(String clip, int ticks, int magInTick) {
        public ReloadOverride {
            if(clip==null||!clip.matches("reload_[a-z0-9_]+"))throw new IllegalArgumentException("reload override clip must be reload_<name>: "+clip);
            if(ticks<=0||magInTick<0||magInTick>ticks)throw new IllegalArgumentException("reload override ticks / mag_in_tick invalid");
        }
    }

    public double noiseMultiplier(double fallback){return noiseMultiplier!=null?noiseMultiplier:fallback;}
    public ReloadOverride reload(boolean empty){return empty?emptyReload:tacticalReload;}

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
        ReloadOverride tactical=null,empty=null;
        if(o.has("reload_overrides")){
            var r=o.getAsJsonObject("reload_overrides");
            for(String key:r.keySet())if(!key.equals("tactical")&&!key.equals("empty"))
                throw new IllegalArgumentException(id+": reload_overrides."+key+" (expected tactical / empty)");
            tactical=reloadOverride(id,r,"tactical");empty=reloadOverride(id,r,"empty");
        }
        return new NativeAttachmentData(noise,ammo,mount,tactical,empty);
    }
    /** {clip, seconds, mag_in_tick?}; without mag_in_tick the rounds are committed at the end of the override. */
    private static ReloadOverride reloadOverride(ResourceLocation id,JsonObject r,String key){
        if(!r.has(key))return null;
        var o=r.getAsJsonObject(key);
        double seconds=o.get("seconds").getAsDouble();
        if(!Double.isFinite(seconds)||seconds<=0||seconds>60)throw new IllegalArgumentException(id+": reload_overrides."+key+".seconds outside (0,60]");
        int ticks=(int)Math.ceil(seconds*20);
        try{return new ReloadOverride(o.get("clip").getAsString(),ticks,o.has("mag_in_tick")?o.get("mag_in_tick").getAsInt():ticks);}
        catch(IllegalArgumentException e){throw new IllegalArgumentException(id+": reload_overrides."+key+": "+e.getMessage());}
    }
}
