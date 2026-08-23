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
    public static final RegistryObject<Item> STEEL_INGOT = ITEMS.register("steel_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_UTILITY_LIGHT = ITEMS.register("industrial_utility_light",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_UTILITY_LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_ELECTRICAL_BOX = ITEMS.register("industrial_electrical_box",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_ELECTRICAL_BOX.get(), new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_LOCKER = ITEMS.register("industrial_locker",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_LOCKER.get(), new Item.Properties()));
    public static final RegistryObject<Item> RETAIL_SHELF_SINGLE = ITEMS.register("retail_shelf_single",
            () -> new BlockItem(AflBlocks.RETAIL_SHELF_SINGLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> COMMERCIAL_GLASS_DOUBLE_DOOR = ITEMS.register("commercial_glass_double_door",
            () -> new BlockItem(AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> FALLOUT_SOIL = ITEMS.register("fallout_soil",
            () -> new BlockItem(AflBlocks.FALLOUT_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> SCORCHED_SOIL = ITEMS.register("scorched_soil",
            () -> new BlockItem(AflBlocks.SCORCHED_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUSED_GROUND = ITEMS.register("fused_ground",
            () -> new BlockItem(AflBlocks.FUSED_GROUND.get(), new Item.Properties()));
    public static final RegistryObject<Item> ASPHALT = ITEMS.register("asphalt",
            () -> new BlockItem(AflBlocks.ASPHALT.get(), new Item.Properties()));
    public static final RegistryObject<Item> EDGE_LANE_WHITE = ITEMS.register("edge_lane_white",
            () -> new BlockItem(AflBlocks.EDGE_LANE_WHITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> EDGE_LANE_YELLOW = ITEMS.register("edge_lane_yellow",
            () -> new BlockItem(AflBlocks.EDGE_LANE_YELLOW.get(), new Item.Properties()));
    public static final RegistryObject<Item> WHITE_LANE_DIVIDER = ITEMS.register("white_lane_divider",
            () -> new BlockItem(AflBlocks.WHITE_LANE_DIVIDER.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_LOG = ITEMS.register("poplar_log",
            () -> new BlockItem(AflBlocks.POPLAR_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> STRIPPED_POPLAR_LOG = ITEMS.register("stripped_poplar_log",
            () -> new BlockItem(AflBlocks.STRIPPED_POPLAR_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_WOOD = ITEMS.register("poplar_wood",
            () -> new BlockItem(AflBlocks.POPLAR_WOOD.get(), new Item.Properties()));
    public static final RegistryObject<Item> STRIPPED_POPLAR_WOOD = ITEMS.register("stripped_poplar_wood",
            () -> new BlockItem(AflBlocks.STRIPPED_POPLAR_WOOD.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_PLANKS = ITEMS.register("poplar_planks",
            () -> new BlockItem(AflBlocks.POPLAR_PLANKS.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_STAIRS = ITEMS.register("poplar_stairs",
            () -> new BlockItem(AflBlocks.POPLAR_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_SLAB = ITEMS.register("poplar_slab",
            () -> new BlockItem(AflBlocks.POPLAR_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_DOOR = ITEMS.register("poplar_door",
            () -> new BlockItem(AflBlocks.POPLAR_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> POPLAR_TRAPDOOR = ITEMS.register("poplar_trapdoor",
            () -> new BlockItem(AflBlocks.POPLAR_TRAPDOOR.get(), new Item.Properties()));
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
    public static final RegistryObject<Item> GEIGER_COUNTER = ITEMS.register("geiger_counter",
            () -> new Item(new Item.Properties().stacksTo(1)));

    private AflItems() {
    }
}
