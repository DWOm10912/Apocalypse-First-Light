package com.antaurora.apofirstlight.dev.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * DEVELOPMENT ONLY. Progressive Container Search demo crate, excluded from the published jar with the rest of
 * {@code com.antaurora.apofirstlight.dev}. No item, model, texture, lang entry or loot table: place it with
 * {@code /setblock ~ ~ ~ apocalypse_firstlight:dev_search_crate{LootTable:"minecraft:chests/simple_dungeon"}}
 * (world loot, searched) or without the tag (player storage, fully revealed). It renders as the missing model.
 * Instances are created inside the registry events, never during class loading.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DevSearchCrateRegistration {
    private static final ResourceLocation ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dev_search_crate");
    private static Block block;
    private static BlockEntityType<DevSearchCrateBlockEntity> blockEntityType;

    private DevSearchCrateRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            block = new DevSearchCrateBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.WOOD));
            helper.register(ID, block);
        });
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            blockEntityType = BlockEntityType.Builder.of(DevSearchCrateBlockEntity::new, block).build(null);
            helper.register(ID, blockEntityType);
        });
    }

    static BlockEntityType<DevSearchCrateBlockEntity> blockEntityType() {
        return blockEntityType;
    }
}
