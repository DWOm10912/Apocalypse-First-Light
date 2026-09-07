package com.antaurora.apofirstlight.weapon;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Atomic validated snapshots; server and client copies remain separate in integrated play. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class NativeGunData {
    private static final Gson GSON=new Gson();
    private static volatile Map<ResourceLocation,NativeGunDefinition> server=Map.of(), client=Map.of();
    private static volatile Map<ResourceLocation,JsonElement> wire=Map.of();
    private static final Map<ResourceLocation,NativeGunDefinition> defaults=new java.util.concurrent.ConcurrentHashMap<>();
    public static NativeGunDefinition get(ResourceLocation id) {
        var map=EffectiveSide.get().isServer()?server:client;
        var found=map.get(id);return found!=null?found:packaged(id);
    }
    public static NativeGunDefinition packaged(ResourceLocation id) {
        var cached=defaults.get(id);if(cached!=null)return cached;
        try(var in=NativeGunData.class.getResourceAsStream("/data/"+id.getNamespace()+"/native_guns/"+id.getPath()+".json")) {
            if(in==null)throw new IllegalArgumentException("Missing packaged definition "+id);
            var value=parse(id,JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(),false);
            defaults.putIfAbsent(id,value);return value;
        }catch(java.io.IOException e){throw new IllegalStateException(id.toString(),e);}
    }
    private static double num(JsonObject o,String key,double min,double max) {
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key+": required number");
        double v=o.get(key).getAsDouble();
        if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(key+": outside ["+min+","+max+"]");
        return v;
    }
    private static int integer(JsonObject o,String key) {
        double v=num(o,key,1,Integer.MAX_VALUE);
        if(v!=Math.rint(v))throw new IllegalArgumentException(key+": expected integer");return (int)v;
    }
    private static ResourceLocation item(JsonObject o,String key,boolean validate) {
        var id=ResourceLocation.tryParse(o.get(key).getAsString());
        if(id==null||validate&&!ForgeRegistries.ITEMS.containsKey(id))throw new IllegalArgumentException(key+": unknown item "+id);
        return id;
    }
    public static NativeGunDefinition parse(ResourceLocation id,JsonObject o,boolean validate) {
        try {
            var f=o.getAsJsonObject("fire");var d=o.getAsJsonObject("damage");
            var a=o.getAsJsonObject("accuracy");var reload=o.getAsJsonObject("reload");var noise=o.getAsJsonObject("noise");var ads=o.getAsJsonObject("ads");
            if(!"semi".equals(f.get("mode").getAsString()))throw new IllegalArgumentException("fire.mode: only semi supported");
            double start=num(d,"falloff_start",0,Double.MAX_VALUE);
            double range=num(d,"max_range",start,Double.MAX_VALUE);
            int tactical=(int)Math.ceil(num(reload,"tactical_seconds",0,100000)*20);
            int empty=(int)Math.ceil(num(reload,"empty_seconds",0,100000)*20);
            var recoil=o.getAsJsonObject("recoil");
            for(String key:List.of("verticalMin","verticalMax","horizontalMin","horizontalMax","maxVertical","maxHorizontal",
                    "recoveryDelay","cameraRecoveryTime","modelPitch","modelBack","modelYaw","modelRoll","modelRecoveryTime",
                    "horizontalContinueChance","horizontalRecoveryTime","horizontalLeftMin","horizontalRightMin"))
                num(recoil,key,-Double.MAX_VALUE,Double.MAX_VALUE);
            var rp=GSON.fromJson(recoil,NativeRecoilProfile.class);
            var ap=switch(a.get("profile").getAsString()) {
                case "default"->NativeAccuracyProfile.DEFAULT; case "battle_rifle"->NativeAccuracyProfile.BATTLE_RIFLE;
                default->throw new IllegalArgumentException("accuracy.profile: unknown preset");
            };
            if(!noise.getAsJsonPrimitive("tinnitus").isBoolean())throw new IllegalArgumentException("noise.tinnitus: expected boolean");
            boolean rifle=id.getPath().equals("br51_01"); // Presentation metadata, not balance.
            return new NativeGunDefinition(id,item(o,"ammo",validate),integer(o,"magazine_capacity"),
                    new ResourceLocation(id.getNamespace(),"textures/gui/gun/"+id.getPath()+"_hud.png"),rifle?60:36,rifle?12:22,
                    tactical,rifle?tactical:Math.min(19,tactical),integer(f,"interval_ticks"),num(d,"base",0,Double.MAX_VALUE),
                    start,num(d,"effective_range",start,Double.MAX_VALUE),range,num(d,"min_damage_multiplier",0,1),
                    num(a,"base_spread_degrees",0,45),num(noise,"radius",0,Double.MAX_VALUE),rp,NativeTrailProfile.SUBTLE_PISTOL,ap,
                    noise.get("tinnitus").getAsBoolean(),empty,(float)(num(ads,"time_seconds",0,100000)*20),
                    (float)num(ads,"fov_multiplier",Float.MIN_NORMAL,Float.MAX_VALUE),item(o,"casing",validate),NativeSightMount.parse(o,validate));
        }catch(RuntimeException e){throw new IllegalArgumentException(id+": "+e.getMessage(),e);}
    }
    @SubscribeEvent public static void register(AddReloadListenerEvent e) {
        e.addListener(new SimpleJsonResourceReloadListener(GSON,"native_guns") {
            @Override protected void apply(Map<ResourceLocation,JsonElement> input,ResourceManager manager,ProfilerFiller profiler) {
                // Syntactically invalid JSON is omitted by the base listener; retain last-good too.
                var next=new HashMap<ResourceLocation,NativeGunDefinition>(server);
                var raw=new HashMap<ResourceLocation,JsonElement>(wire);
                input.forEach((id,json)->{
                    try{next.put(id,parse(id,json.getAsJsonObject(),true));raw.put(id,json.deepCopy());}
                    catch(RuntimeException ex){
                        com.mojang.logging.LogUtils.getLogger().error("Native gun JSON rejected {}",ex.getMessage());
                        if(server.containsKey(id)){next.put(id,server.get(id));raw.put(id,wire.get(id));}
                    }
                });
                server=Map.copyOf(next);wire=Map.copyOf(raw);
                P901Actions.clearForDataReload();
            }
        });
    }
    public static String snapshot(){return GSON.toJson(wire.entrySet().stream().collect(
            java.util.stream.Collectors.toMap(e->e.getKey().toString(),Map.Entry::getValue)));}
    public static void receive(String json) {
        var next=new HashMap<ResourceLocation,NativeGunDefinition>();
        JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e->{
            var id=new ResourceLocation(e.getKey());next.put(id,parse(id,e.getValue().getAsJsonObject(),true));
        }); client=Map.copyOf(next);
    }
    public static void clearClient(){client=Map.of();}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
        server=Map.of();wire=Map.of();
    }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent e) {
        if(e.getPlayer()!=null)com.antaurora.apofirstlight.network.AflNetwork.sendGunData(e.getPlayer(),snapshot());
        else for(var p:e.getPlayerList().getPlayers())com.antaurora.apofirstlight.network.AflNetwork.sendGunData(p,snapshot());
    }
}
