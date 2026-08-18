package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflBlockRenderTypes {
    private AflBlockRenderTypes() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(AflBlocks.STEEL_GRATE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AflBlocks.INDUSTRIAL_UTILITY_LIGHT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AflBlocks.POPLAR_DOOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AflBlocks.POPLAR_TRAPDOOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AflBlocks.POPLAR_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(AflBlocks.POPLAR_SAPLING.get(), RenderType.cutout());
        });
    }
}
