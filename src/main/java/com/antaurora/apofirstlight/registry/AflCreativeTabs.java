package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AflCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BLOCKS = CREATIVE_MODE_TABS.register("blocks", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(AflItems.STEEL_BLOCK.get()))
                    .title(Component.translatable("itemGroup.apocalypse_firstlight.blocks"))
                    .displayItems((parameters, output) -> {
                        output.accept(AflItems.REINFORCED_CONCRETE.get());
                        output.accept(AflItems.REINFORCED_CONCRETE_SLAB.get());
                        output.accept(AflItems.REINFORCED_CONCRETE_STAIRS.get());
                        output.accept(AflItems.STEEL_BLOCK.get());
                        output.accept(AflItems.STEEL_BLOCK_SLAB.get());
                        output.accept(AflItems.STEEL_BLOCK_STAIRS.get());
                        output.accept(AflItems.STEEL_PLATE.get());
                        output.accept(AflItems.STEEL_PLATE_SLAB.get());
                        output.accept(AflItems.STEEL_PLATE_STAIRS.get());
                        output.accept(AflItems.STEEL_GRATE.get());
                        output.accept(AflItems.STEEL_DOOR.get());
                        output.accept(AflItems.INDUSTRIAL_UTILITY_LIGHT.get());
                        output.accept(AflItems.INDUSTRIAL_ELECTRICAL_BOX.get());
                        output.accept(AflItems.INDUSTRIAL_LOCKER.get());
                        output.accept(AflItems.METAL_LOCKER.get());
                        output.accept(AflItems.RETAIL_SHELF_SINGLE.get());
                        output.accept(AflItems.POPLAR_LOG.get());
                        output.accept(AflItems.STRIPPED_POPLAR_LOG.get());
                        output.accept(AflItems.POPLAR_WOOD.get());
                        output.accept(AflItems.STRIPPED_POPLAR_WOOD.get());
                        output.accept(AflItems.POPLAR_PLANKS.get());
                        output.accept(AflItems.POPLAR_LEAVES.get());
                        output.accept(AflItems.POPLAR_SAPLING.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ITEMS = CREATIVE_MODE_TABS.register("items", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(AflItems.STEEL_SCRAP.get()))
                    .title(Component.translatable("itemGroup.apocalypse_firstlight.items"))
                    .displayItems((parameters, output) -> {
                        output.accept(AflItems.STEEL_SCRAP.get());
                        output.accept(AflItems.CONCRETE_RUBBLE.get());
                        output.accept(AflItems.PLASTIC_SCRAP.get());
                        output.accept(AflItems.PLASTIC_PELLETS.get());
                        output.accept(AflItems.PLASTIC_SHEET.get());
                    })
                    .build());

    private AflCreativeTabs() {
    }
}
