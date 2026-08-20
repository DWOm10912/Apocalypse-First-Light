package com.antaurora.apofirstlight;

import com.mojang.logging.LogUtils;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflCreativeTabs;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflParticles;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.registry.AflFeatures;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.world.biome.AflOverworldRegion;
import terrablender.api.Regions;
import net.minecraft.resources.ResourceLocation;
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
        AflNetwork.register();

        AflBlocks.BLOCKS.register(modEventBus);
        AflItems.ITEMS.register(modEventBus);
        AflBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        AflParticles.PARTICLE_TYPES.register(modEventBus);
        AflSounds.SOUND_EVENTS.register(modEventBus);
        AflFeatures.FEATURES.register(modEventBus);
        AflCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> Regions.register(new AflOverworldRegion(
                new ResourceLocation(MOD_ID, "overworld"), 2)));
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            fire.setFlammable(AflBlocks.POPLAR_LOG.get(), 5, 5);
            fire.setFlammable(AflBlocks.STRIPPED_POPLAR_LOG.get(), 5, 5);
            fire.setFlammable(AflBlocks.POPLAR_WOOD.get(), 5, 5);
            fire.setFlammable(AflBlocks.STRIPPED_POPLAR_WOOD.get(), 5, 5);
            fire.setFlammable(AflBlocks.POPLAR_PLANKS.get(), 5, 20);
            fire.setFlammable(AflBlocks.POPLAR_STAIRS.get(), 5, 20);
            fire.setFlammable(AflBlocks.POPLAR_SLAB.get(), 5, 20);
            fire.setFlammable(AflBlocks.POPLAR_DOOR.get(), 5, 20);
            fire.setFlammable(AflBlocks.POPLAR_TRAPDOOR.get(), 5, 20);
            fire.setFlammable(AflBlocks.POPLAR_LEAVES.get(), 30, 60);
            fire.setFlammable(AflBlocks.POPLAR_SAPLING.get(), 60, 80);
        });
    }
}
