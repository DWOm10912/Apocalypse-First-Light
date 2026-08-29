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
    public static final RegistryObject<Item> ALUMINUM_BLOCK = ITEMS.register("aluminum_block",
            () -> new BlockItem(AflBlocks.ALUMINUM_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> BAUXITE_ORE = ITEMS.register("bauxite_ore",
            () -> new BlockItem(AflBlocks.BAUXITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> GALENA_ORE = ITEMS.register("galena_ore",
            () -> new BlockItem(AflBlocks.GALENA_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LEAD_BLOCK = ITEMS.register("lead_block",
            () -> new BlockItem(AflBlocks.LEAD_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPHALERITE_ORE = ITEMS.register("sphalerite_ore",
            () -> new BlockItem(AflBlocks.SPHALERITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ZINC_BLOCK = ITEMS.register("zinc_block",
            () -> new BlockItem(AflBlocks.ZINC_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CASSITERITE_ORE = ITEMS.register("cassiterite_ore",
            () -> new BlockItem(AflBlocks.CASSITERITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> TIN_BLOCK = ITEMS.register("tin_block",
            () -> new BlockItem(AflBlocks.TIN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> PENTLANDITE_ORE = ITEMS.register("pentlandite_ore",
            () -> new BlockItem(AflBlocks.PENTLANDITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> NICKEL_BLOCK = ITEMS.register("nickel_block",
            () -> new BlockItem(AflBlocks.NICKEL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SILVER_BLOCK = ITEMS.register("silver_block",
            () -> new BlockItem(AflBlocks.SILVER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> WOLFRAMITE_ORE = ITEMS.register("wolframite_ore",
            () -> new BlockItem(AflBlocks.WOLFRAMITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPODUMENE_ORE = ITEMS.register("spodumene_ore",
            () -> new BlockItem(AflBlocks.SPODUMENE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_BLOCK = ITEMS.register("tungsten_block",
            () -> new BlockItem(AflBlocks.TUNGSTEN_BLOCK.get(), new Item.Properties()));
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
    public static final RegistryObject<Item> STEEL_SHEET = ITEMS.register("steel_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ALUMINUM_INGOT = ITEMS.register("aluminum_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ALUMINUM_SHEET = ITEMS.register("aluminum_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BAUXITE = ITEMS.register("bauxite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ALUMINA = ITEMS.register("alumina",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GALENA = ITEMS.register("galena",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPHALERITE = ITEMS.register("sphalerite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CASSITERITE = ITEMS.register("cassiterite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LEAD_INGOT = ITEMS.register("lead_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LEAD_SHEET = ITEMS.register("lead_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ZINC_INGOT = ITEMS.register("zinc_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ZINC_SHEET = ITEMS.register("zinc_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TIN_INGOT = ITEMS.register("tin_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TIN_SHEET = ITEMS.register("tin_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PENTLANDITE = ITEMS.register("pentlandite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> NICKEL_INGOT = ITEMS.register("nickel_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> NICKEL_SHEET = ITEMS.register("nickel_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SILVER_SCRAP = ITEMS.register("silver_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SILVER_INGOT = ITEMS.register("silver_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SILVER_SHEET = ITEMS.register("silver_sheet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WOLFRAMITE = ITEMS.register("wolframite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KUNZITE = ITEMS.register("kunzite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LITHIUM_CARBONATE = ITEMS.register("lithium_carbonate",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_INGOT = ITEMS.register("tungsten_ingot",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_SHEET = ITEMS.register("tungsten_sheet",
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
    public static final RegistryObject<Item> THERMAL_GENERATOR = ITEMS.register("thermal_generator",
            () -> new BlockItem(AflBlocks.THERMAL_GENERATOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> ENERGY_CELL = ITEMS.register("energy_cell",
            () -> new BlockItem(AflBlocks.ENERGY_CELL.get(), new Item.Properties()));
    public static final RegistryObject<Item> CRUSHER = ITEMS.register("crusher",
            () -> new BlockItem(AflBlocks.CRUSHER.get(), new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_FURNACE = ITEMS.register("industrial_furnace",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_FURNACE.get(), new Item.Properties()));
    public static final RegistryObject<Item> COMPRESSOR = ITEMS.register("compressor",
            () -> new BlockItem(AflBlocks.COMPRESSOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> POWER_CABLE = ITEMS.register("power_cable",
            () -> new BlockItem(AflBlocks.POWER_CABLE.get(), new Item.Properties()));
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
