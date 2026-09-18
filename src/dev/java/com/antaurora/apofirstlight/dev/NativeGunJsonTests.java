package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.AflItems;
import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
public class NativeGunJsonTests {
    @GameTest(template="network_empty",batch="native_framework_generalization")
    public static void classDrivenPresentation(GameTestHelper h) throws Exception {
        var pistol=NativeGunData.parse(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight","renamed_pistol_fixture"),read("p9_01"),false);
        var rifle=NativeGunData.parse(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight","renamed_rifle_fixture"),read("br51_01"),false);
        h.assertTrue(pistol.weaponClass()==WeaponClass.PISTOL && pistol.hudWidth()==36 && pistol.hudHeight()==22
                && pistol.magInTick()==19 && pistol.adsCalibration().ay()==5.80F
                && pistol.adsCalibration().rootPitch()==0F && pistol.adsCalibration().adsPitch()==0F
                && pistol.adsCalibration().adsYaw()==0F && pistol.adsCalibration().adsRoll()==0F,
                "Pistol presentation and sight-axis calibration are data driven and frozen");
        h.assertTrue(rifle.weaponClass()==WeaponClass.RIFLE && rifle.hudWidth()==60 && rifle.hudHeight()==12
                && rifle.magInTick()==52 && rifle.adsCalibration().ay()==13.6875F
                && rifle.adsCalibration().adsPitch()==0F && rifle.adsCalibration().adsYaw()==0F
                && rifle.adsCalibration().adsRoll()==0F,"Rifle presentation and ADS behavior are frozen");
        h.assertTrue(pistol.trail().equals(NativeTrailProfile.SUBTLE_PISTOL)
                && rifle.trail().equals(NativeTrailProfile.SUBTLE_RIFLE),"Per-gun trail presets resolve");
        var invalid=read("p9_01");invalid.addProperty("weapon_class","not_a_weapon_class");
        try {
            NativeGunData.parse(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight","invalid_class_fixture"),invalid,false);
            h.fail("Invalid weapon_class was accepted");
        } catch (IllegalArgumentException expected) {
            h.assertTrue(expected.getMessage().contains("weapon_class: unknown value"),"Invalid class has a clear error");
        }
        h.succeed();
    }

    @GameTest(template="network_empty",batch="zz_native_json_reload",timeoutTicks=1200)
    public static void actualDatapackReload(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();
        var root=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("afl_native_json_test");
        Files.createDirectories(root.resolve("data/apocalypse_firstlight/native_guns"));
        Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Native JSON DEV test\"}}");
        var dir=root.resolve("data/apocalypse_firstlight/native_guns");
        JsonObject rifle=read("br51_01"),pistol=read("p9_01");
        rifle.getAsJsonObject("damage").addProperty("base",10);
        rifle.getAsJsonObject("noise").addProperty("radius",64);
        pistol.getAsJsonObject("fire").addProperty("interval_ticks",5);
        Files.writeString(dir.resolve("br51_01.json"),rifle.toString());
        Files.writeString(dir.resolve("p9_01.json"),pistol.toString());
        var original=new ArrayList<>(server.getPackRepository().getSelectedIds());
        server.getPackRepository().reload();
        for(String id:server.getPackRepository().getAvailableIds())
            if(!id.equals("file/afl_native_json_test")&&!original.contains(id))original.add(id);
        com.mojang.logging.LogUtils.getLogger().info("Native JSON test reload packs {}",original);
        var packs=new ArrayList<>(original);packs.add("file/afl_native_json_test");
        server.reloadResources(packs).thenRunAsync(()->{
            var r=((NativeGunItem)AflItems.BR51_01.get()).definition();
            var p=((NativeGunItem)AflItems.P9_01.get()).definition();
            h.assertTrue(NativeGunShot.damageAt(r,0)==10 && r.noiseRadius()==64 && p.fireIntervalTicks()==5,"Live item getters after actual resource reload");
            h.assertTrue(NativeGunData.snapshot().contains("\"base\":10"),"Client sync snapshot updated");
            rifle.addProperty("magazine_capacity",-1);
            try{Files.writeString(dir.resolve("br51_01.json"),rifle.toString());}catch(Exception e){throw new RuntimeException(e);}
        },server).thenComposeAsync(v->server.reloadResources(packs),server).thenRunAsync(()->{
            h.assertTrue(((NativeGunItem)AflItems.BR51_01.get()).definition().baseDamage()==10,"Invalid override retains whole last-good definition");
        },server).handleAsync((v,error)->{
            // Always remove the test pack from selection and restore production definitions.
            return server.reloadResources(original).thenRunAsync(()->{
                try {
                    // Existing suite injects this template only at ServerStarting; reload clears its cache.
                    PowerNetworkGameTests.createTemplate(new net.minecraftforge.event.server.ServerStartingEvent(server));
                    IndustrialWasteGameTests.createTestTemplate(new net.minecraftforge.event.server.ServerStartingEvent(server));
                    Files.deleteIfExists(dir.resolve("br51_01.json"));Files.deleteIfExists(dir.resolve("p9_01.json"));
                    Files.deleteIfExists(root.resolve("pack.mcmeta"));
                }catch(Exception e){throw new RuntimeException(e);}
                if(error!=null){h.fail("JSON reload test: "+error);return;}
                h.assertTrue(((NativeGunItem)AflItems.BR51_01.get()).definition().baseDamage()==18
                        && ((NativeGunItem)AflItems.BR51_01.get()).definition().noiseRadius()==112
                        && ((NativeGunItem)AflItems.P9_01.get()).definition().fireIntervalTicks()==3,"Production restored");
                h.succeed();
            },server);
        },server).thenCompose(x->x).exceptionally(error->{server.execute(()->h.fail(error.toString()));return null;});
    }
    private static JsonObject read(String id) throws Exception {
        try(var in=NativeGunJsonTests.class.getResourceAsStream("/data/apocalypse_firstlight/native_guns/"+id+".json")){
            return JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
