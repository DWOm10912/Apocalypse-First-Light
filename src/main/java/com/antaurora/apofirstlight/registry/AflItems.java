package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<Item> REINFORCED_CONCRETE = ITEMS.register("reinforced_concrete",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE.get(), new Item.Properties()));
    public static final RegistryObject<Item> REINFORCED_CONCRETE_SLAB = ITEMS.register("reinforced_concrete_slab",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> REINFORCED_CONCRETE_STAIRS = ITEMS.register("reinforced_concrete_stairs",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK = ITEMS.register("steel_block",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK_SLAB = ITEMS.register("steel_block_slab",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK_STAIRS = ITEMS.register("steel_block_stairs",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_GRATE = ITEMS.register("steel_grate",
            () -> new BlockItem(AflBlocks.STEEL_GRATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE = ITEMS.register("steel_plate",
            () -> new BlockItem(AflBlocks.STEEL_PLATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE_SLAB = ITEMS.register("steel_plate_slab",
            () -> new BlockItem(AflBlocks.STEEL_PLATE_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE_STAIRS = ITEMS.register("steel_plate_stairs",
            () -> new BlockItem(AflBlocks.STEEL_PLATE_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_DOOR = ITEMS.register("steel_door",
            () -> new BlockItem(AflBlocks.STEEL_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_UTILITY_LIGHT = ITEMS.register("industrial_utility_light",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_UTILITY_LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_ELECTRICAL_BOX = ITEMS.register("industrial_electrical_box",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_ELECTRICAL_BOX.get(), new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_LOCKER = ITEMS.register("industrial_locker",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_LOCKER.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_LOCKER = ITEMS.register("metal_locker",
            () -> new BlockItem(AflBlocks.METAL_LOCKER.get(), new Item.Properties()));
    public static final RegistryObject<Item> RETAIL_SHELF_SINGLE = ITEMS.register("retail_shelf_single",
            () -> new BlockItem(AflBlocks.RETAIL_SHELF_SINGLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_LOG = ITEMS.register("poplar_log",
            () -> new BlockItem(AflBlocks.POPLAR_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> STRIPPED_POPLAR_LOG = ITEMS.register("stripped_poplar_log",
            () -> new BlockItem(AflBlocks.STRIPPED_POPLAR_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_PLANKS = ITEMS.register("poplar_planks",
            () -> new BlockItem(AflBlocks.POPLAR_PLANKS.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_LEAVES = ITEMS.register("poplar_leaves",
            () -> new BlockItem(AflBlocks.POPLAR_LEAVES.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_SAPLING = ITEMS.register("poplar_sapling",
            () -> new BlockItem(AflBlocks.POPLAR_SAPLING.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_SCRAP = ITEMS.register("steel_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RUBBLE = ITEMS.register("concrete_rubble",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLASTIC_SCRAP = ITEMS.register("plastic_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLASTIC_PELLETS = ITEMS.register("plastic_pellets",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLASTIC_SHEET = ITEMS.register("plastic_sheet",
            () -> new Item(new Item.Properties()));

    private AflItems() {
    }
}
