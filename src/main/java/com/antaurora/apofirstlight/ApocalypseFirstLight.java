package com.antaurora.apofirstlight;

import com.mojang.logging.LogUtils;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflCreativeTabs;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import org.slf4j.Logger;

@Mod(ApocalypseFirstLight.MOD_ID)
public class ApocalypseFirstLight {
    public static final String MOD_ID = "apocalypse_firstlight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApocalypseFirstLight(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        AflBlocks.BLOCKS.register(modEventBus);
        AflItems.ITEMS.register(modEventBus);
        AflBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        AflCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ((FireBlock) Blocks.FIRE).setFlammable(AflBlocks.POPLAR_LOG.get(), 5, 5));
    }
}
