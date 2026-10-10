package com.antaurora.apofirstlight;

import com.mojang.logging.LogUtils;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.registry.AflCreativeTabs;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflLootModifiers;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.registry.AflMenus;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import com.antaurora.apofirstlight.registry.AflRecipes;
import com.antaurora.apofirstlight.registry.AflParticles;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.registry.AflFeatures;
import com.antaurora.apofirstlight.registry.AflDensityFunctions;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.world.biome.AflOverworldRegion;
import terrablender.api.Regions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(ApocalypseFirstLight.MOD_ID)
public class ApocalypseFirstLight {
    public static final String MOD_ID = "apocalypse_firstlight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApocalypseFirstLight(FMLJavaModLoadingContext context) {
        LOGGER.info("[AFL BUILD] version=1.0.0 fix=startup-parameterlist-context-v1");
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        AflNetwork.register();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.antaurora.apofirstlight.authoring.BuildingAuthoringConfig.SPEC, "apocalypse_firstlight-authoring.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.antaurora.apofirstlight.weapon.NativeHeadshots.SPEC, "apocalypse_firstlight-guns.toml");

        AflBlocks.BLOCKS.register(modEventBus);
        AflFluids.FLUID_TYPES.register(modEventBus);
        AflFluids.FLUIDS.register(modEventBus);
        AflItems.ITEMS.register(modEventBus);
        AflLootModifiers.SERIALIZERS.register(modEventBus);
        AflBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        AflEntities.ENTITY_TYPES.register(modEventBus);
        AflMenus.MENUS.register(modEventBus);
        AflMobEffects.MOB_EFFECTS.register(modEventBus);
        AflRecipes.RECIPE_TYPES.register(modEventBus);
        AflRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        AflParticles.PARTICLE_TYPES.register(modEventBus);
        AflSounds.SOUND_EVENTS.register(modEventBus);
        AflFeatures.FEATURES.register(modEventBus);
        AflDensityFunctions.TYPES.register(modEventBus);
        com.antaurora.apofirstlight.registry.AflStructureProcessors.PROCESSORS.register(modEventBus);
        AflCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> Regions.register(new AflOverworldRegion(
                new ResourceLocation(MOD_ID, "overworld"), AflOverworldRegion.REGION_WEIGHT)));
        event.enqueueWork(com.antaurora.apofirstlight.weight.AflCarriedContents::register);
    }
}
