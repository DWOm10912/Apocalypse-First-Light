package com.antaurora.apofirstlight.dev;

import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class TerrainDiagnosticIO {
    static final String VERSION="terrain_phase0_1";
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private TerrainDiagnosticIO() {}
    static Path write(String filename,JsonObject root) throws java.io.IOException {
        Path dir=FMLPaths.GAMEDIR.get().resolve("afl_debug/terrain");Files.createDirectories(dir);
        Path target=dir.resolve(filename+".json"),tmp=dir.resolve(filename+".json.tmp");
        Files.writeString(tmp,GSON.toJson(root),StandardCharsets.UTF_8);
        try{Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}return target;
    }
    static JsonObject identity(ServerLevel level,int x,int z) {
        JsonObject j=new JsonObject();j.addProperty("schema_version",1);j.addProperty("diagnostic_version",VERSION);
        j.addProperty("terrain_version","CURRENT_RUNTIME_FINGERPRINT;NOT_A_WORLD_VERSION_LOCK");
        j.addProperty("worldgen_profile","NOT_INSTALLED;EXISTING_WORLDGEN_PROFILE_IS_A_VALUE_CONTRACT_ONLY");
        j.addProperty("world_seed",level.getSeed());j.addProperty("dimension",level.dimension().location().toString());
        j.addProperty("generated_at_game_tick",level.getGameTime());j.add("sample_center",GSON.toJsonTree(Map.of("x",x,"z",z)));
        j.addProperty("min_y",level.getMinBuildHeight());j.addProperty("exclusive_max_y",level.getMaxBuildHeight());
        j.addProperty("sea_level",level.getSeaLevel());return j;
    }
    /** Selected active resources + code bytes; an evidence fingerprint, never a generated-world compatibility lock. */
    static JsonObject fingerprint(ServerLevel level) {
        JsonObject j=new JsonObject();j.addProperty("scope","SELECTED_AFL_WORLDGEN_RESOURCES_AND_CLASSES;NOT_COMPLETE_MODPACK");
        JsonObject hashes=new JsonObject();int bytes=0,count=0;
        try {
            var resources=new TreeMap<>(level.getServer().getResourceManager().listResources("worldgen",r->
                    r.getNamespace().equals("apocalypse_firstlight")&&r.getPath().endsWith(".json")));
            for(var entry:resources.entrySet()) {
                if(count++>=256||bytes>=4_194_304){j.addProperty("truncated",true);break;}
                try(var in=entry.getValue().open()) {byte[] b=in.readNBytes(262145);bytes+=b.length;
                    hashes.addProperty(entry.getKey().toString(),b.length>262144?"RESOURCE_TOO_LARGE":sha(b));}
            }
            for(String name:List.of("geography.MacroGeography","geography.MacroTerrainDensity","geography.LandTerrainRelief",
                    "geography.LandTerrainBias","geography.InlandElevationBias","roads.construction.RoadConstructionPlanner")) {
                String path="/com/antaurora/apofirstlight/worldgen/"+name.replace('.','/')+".class";
                try(var in=TerrainDiagnosticIO.class.getResourceAsStream(path)) {
                    hashes.addProperty(path,in==null?"NOT_AVAILABLE":sha(in.readNBytes(262144)));
                }
            }
        }catch(Exception e){j.addProperty("error",e.toString());}
        j.add("sha256",hashes);return j;
    }
    private static String sha(byte[] b)throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
