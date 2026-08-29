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
                        output.accept(AflItems.ALUMINUM_BLOCK.get());
                        output.accept(AflItems.LEAD_BLOCK.get());
                        output.accept(AflItems.ZINC_BLOCK.get());
                        output.accept(AflItems.TIN_BLOCK.get());
                        output.accept(AflItems.NICKEL_BLOCK.get());
                        output.accept(AflItems.SILVER_BLOCK.get());
                        output.accept(AflItems.TUNGSTEN_BLOCK.get());
                        output.accept(AflItems.BAUXITE_ORE.get());
                        output.accept(AflItems.GALENA_ORE.get());
                        output.accept(AflItems.SPHALERITE_ORE.get());
                        output.accept(AflItems.CASSITERITE_ORE.get());
                        output.accept(AflItems.PENTLANDITE_ORE.get());
                        output.accept(AflItems.WOLFRAMITE_ORE.get());
                        output.accept(AflItems.SPODUMENE_ORE.get());
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
                        output.accept(AflItems.RETAIL_SHELF_SINGLE.get());
                        output.accept(AflItems.COMMERCIAL_GLASS_DOUBLE_DOOR.get());
                        output.accept(AflItems.FALLOUT_SOIL.get());
                        output.accept(AflItems.SCORCHED_SOIL.get());
                        output.accept(AflItems.FUSED_GROUND.get());
                        output.accept(AflItems.ASPHALT.get());
                        output.accept(AflItems.THERMAL_GENERATOR.get());
                        output.accept(AflItems.ENERGY_CELL.get());
                        output.accept(AflItems.CRUSHER.get());
                        output.accept(AflItems.INDUSTRIAL_FURNACE.get());
                        output.accept(AflItems.ALLOY_FURNACE.get());
                        output.accept(AflItems.COMPRESSOR.get());
                        output.accept(AflItems.POWER_CABLE.get());
                        output.accept(AflItems.POPLAR_LOG.get());
                        output.accept(AflItems.STRIPPED_POPLAR_LOG.get());
                        output.accept(AflItems.POPLAR_WOOD.get());
                        output.accept(AflItems.STRIPPED_POPLAR_WOOD.get());
                        output.accept(AflItems.POPLAR_PLANKS.get());
                        output.accept(AflItems.POPLAR_STAIRS.get());
                        output.accept(AflItems.POPLAR_SLAB.get());
                        output.accept(AflItems.POPLAR_DOOR.get());
                        output.accept(AflItems.POPLAR_TRAPDOOR.get());
                        output.accept(AflItems.POPLAR_LEAVES.get());
                        output.accept(AflItems.POPLAR_SAPLING.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ITEMS = CREATIVE_MODE_TABS.register("items", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(AflItems.STEEL_SCRAP.get()))
                    .title(Component.translatable("itemGroup.apocalypse_firstlight.items"))
                    .displayItems((parameters, output) -> {
                        output.accept(AflItems.STEEL_INGOT.get());
                        output.accept(AflItems.STEEL_SHEET.get());
                        output.accept(AflItems.ALUMINUM_INGOT.get());
                        output.accept(AflItems.ALUMINUM_SHEET.get());
                        output.accept(AflItems.BAUXITE.get());
                        output.accept(AflItems.ALUMINA.get());
                        output.accept(AflItems.GALENA.get());
                        output.accept(AflItems.SPHALERITE.get());
                        output.accept(AflItems.CASSITERITE.get());
                        output.accept(AflItems.PENTLANDITE.get());
                        output.accept(AflItems.WOLFRAMITE.get());
                        output.accept(AflItems.KUNZITE.get());
                        output.accept(AflItems.LITHIUM_CARBONATE.get());
                        output.accept(AflItems.LEAD_INGOT.get());
                        output.accept(AflItems.LEAD_SHEET.get());
                        output.accept(AflItems.ZINC_INGOT.get());
                        output.accept(AflItems.ZINC_SHEET.get());
                        output.accept(AflItems.TIN_INGOT.get());
                        output.accept(AflItems.TIN_SHEET.get());
                        output.accept(AflItems.NICKEL_INGOT.get());
                        output.accept(AflItems.NICKEL_SHEET.get());
                        output.accept(AflItems.SILVER_SCRAP.get());
                        output.accept(AflItems.SILVER_INGOT.get());
                        output.accept(AflItems.SILVER_SHEET.get());
                        output.accept(AflItems.TUNGSTEN_INGOT.get());
                        output.accept(AflItems.TUNGSTEN_SHEET.get());
                        output.accept(AflItems.STEEL_SCRAP.get());
                        output.accept(AflItems.CONCRETE_RUBBLE.get());
                        output.accept(AflItems.PLASTIC_SCRAP.get());
                        output.accept(AflItems.PLASTIC_PELLETS.get());
                        output.accept(AflItems.PLASTIC_SHEET.get());
                        output.accept(AflItems.GEIGER_COUNTER.get());
                    })
                    .build());

    private AflCreativeTabs() {
    }
}
