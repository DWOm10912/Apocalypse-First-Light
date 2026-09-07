package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Load-time recovery only; no permanent ticking block entity or forced chunk loads. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class GunWorkbenchIntegrity {
    private GunWorkbenchIntegrity() {}
    @SubscribeEvent public static void onLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        level.getServer().execute(() -> {
            if (!level.hasChunk(chunk.getPos().x, chunk.getPos().z)) return;
            var block = AflBlocks.GUN_WORKBENCH.get();
            var sections = chunk.getSections();
            for (int i=0;i<sections.length;i++) {
                var section = sections[i];
                if (!section.maybeHas(s -> s.is(block))) continue;
                int bottom = level.getMinBuildHeight() + i * 16;
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                    if (section.getBlockState(x,y,z).is(block))
                        level.scheduleTick(new BlockPos(chunk.getPos().getMinBlockX()+x,bottom+y,chunk.getPos().getMinBlockZ()+z),block,1);
                }
            }
        });
    }
}
