package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.bunker.*;
import com.antaurora.apofirstlight.registry.AflBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Fresh normal-world integration, not flat GameTest terrain. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class StartupRegressionProbe {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if(!Boolean.getBoolean("afl.startupRegressionProbe"))return;
        var server=event.getServer();var level=server.overworld();
        boolean passed=true;
        int[][] points={{0,0},{64,0},{-64,0},{0,64},{0,-64},{160,0},{300,0}};
        for(var p:points){
            level.getChunk(p[0]>>4,p[1]>>4);
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p[0],p[1])-1;
            var expected=StartupPlainsEnclave.zoneAt(p[0],p[1],level.getSeed())==StartupPlainsEnclave.Zone.WOODLAND_BUFFER
                    ? AflBiomes.IRRADIATED_WOODLAND : Biomes.PLAINS;
            var actual=level.getBiome(new BlockPos(p[0],y,p[1]));
            boolean ok=actual.is(expected);passed &=ok;
            ApocalypseFirstLight.LOGGER.info("[AFL STARTUP SELFTEST] seed={} pos=({}, {}) expected={} actual={} result={}",level.getSeed(),p[0],p[1],expected.location(),actual.unwrapKey(),ok?"PASS":"FAIL");
        }
        var source=level.getChunkSource().getGenerator().getBiomeSource();var sampler=level.getChunkSource().randomState().sampler();
        var futures=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
        for(int i=0;i<32;i++) futures.add(java.util.concurrent.CompletableFuture.supplyAsync(()->source.getNoiseBiome(0,20,0,sampler).is(Biomes.PLAINS)));
        for(var f:futures)passed &=f.join();
        var data=level.getDataStorage().computeIfAbsent(BunkerSavedData::load,BunkerSavedData::new,BunkerSavedData.ID);
        boolean bunker=data.isGenerated();passed &=bunker;
        if(bunker){
            var origin=data.getOrigin();BunkerPlacementManager.ensureGenerated(level);
            passed &=origin.equals(data.getOrigin());
            passed &=BunkerPlayerSpawnEvents.findSafePosition(level,BunkerPlayerSpawnEvents.preferredSpawn(level,data))!=null;
            var saved=data.save(new net.minecraft.nbt.CompoundTag());
            passed &=BunkerSavedData.load(saved).getOrigin().equals(origin)&&BunkerSavedData.load(saved).isGenerated();
        }
        ApocalypseFirstLight.LOGGER.info("[AFL STARTUP SELFTEST] FINAL seed={} ecologyAndConcurrencyAndBunker={} bunker={} origin={}",level.getSeed(),passed?"PASS":"FAIL",bunker,data.getOrigin());
        server.halt(false);
    }
}
