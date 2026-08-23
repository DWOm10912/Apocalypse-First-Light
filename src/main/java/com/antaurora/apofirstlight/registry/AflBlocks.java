package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.block.SteelGrateBlock;
import com.antaurora.apofirstlight.block.SteelDoorBlock;
import com.antaurora.apofirstlight.block.IndustrialUtilityLightBlock;
import com.antaurora.apofirstlight.block.IndustrialElectricalBoxBlock;
import com.antaurora.apofirstlight.block.IndustrialLockerBlock;
import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.block.CommercialGlassDoubleDoorBlock;
import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.block.RoadMarkingStepConnectorBlock;
import com.antaurora.apofirstlight.block.StrippableRotatedPillarBlock;
import com.antaurora.apofirstlight.world.PoplarTreeGrower;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<Block> REINFORCED_CONCRETE = BLOCKS.register("reinforced_concrete",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(6.0F, 15.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> REINFORCED_CONCRETE_SLAB = BLOCKS.register("reinforced_concrete_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(REINFORCED_CONCRETE.get())));
    public static final RegistryObject<Block> REINFORCED_CONCRETE_STAIRS = BLOCKS.register("reinforced_concrete_stairs",
            () -> new StairBlock(REINFORCED_CONCRETE.get().defaultBlockState(), BlockBehaviour.Properties.copy(REINFORCED_CONCRETE.get())));
    public static final RegistryObject<Block> STEEL_BLOCK = BLOCKS.register("steel_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ALUMINUM_BLOCK = BLOCKS.register("aluminum_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> BAUXITE_ORE = BLOCKS.register("bauxite_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE)));
    public static final RegistryObject<Block> STEEL_BLOCK_SLAB = BLOCKS.register("steel_block_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(STEEL_BLOCK.get())));
    public static final RegistryObject<Block> STEEL_BLOCK_STAIRS = BLOCKS.register("steel_block_stairs",
            () -> new StairBlock(STEEL_BLOCK.get().defaultBlockState(), BlockBehaviour.Properties.copy(STEEL_BLOCK.get())));
    public static final RegistryObject<Block> STEEL_GRATE = BLOCKS.register("steel_grate",
            () -> new SteelGrateBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> STEEL_PLATE = BLOCKS.register("steel_plate",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(6.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_PLATE_SLAB = BLOCKS.register("steel_plate_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(STEEL_PLATE.get())));
    public static final RegistryObject<Block> STEEL_PLATE_STAIRS = BLOCKS.register("steel_plate_stairs",
            () -> new StairBlock(STEEL_PLATE.get().defaultBlockState(), BlockBehaviour.Properties.copy(STEEL_PLATE.get())));
    public static final RegistryObject<Block> STEEL_DOOR = BLOCKS.register("steel_door",
            () -> new SteelDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(6.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), AflBlockSetTypes.AFL_STEEL));
    public static final RegistryObject<Block> INDUSTRIAL_UTILITY_LIGHT = BLOCKS.register("industrial_utility_light",
            () -> new IndustrialUtilityLightBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 14)));
    public static final RegistryObject<Block> INDUSTRIAL_ELECTRICAL_BOX = BLOCKS.register("industrial_electrical_box",
            () -> new IndustrialElectricalBoxBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> INDUSTRIAL_LOCKER = BLOCKS.register("industrial_locker",
            () -> new IndustrialLockerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> RETAIL_SHELF_SINGLE = BLOCKS.register("retail_shelf_single",
            () -> new RetailShelfSingleBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final RegistryObject<Block> COMMERCIAL_GLASS_DOUBLE_DOOR = BLOCKS.register("commercial_glass_double_door",
            () -> new CommercialGlassDoubleDoorBlock(BlockBehaviour.Properties.copy(Blocks.GLASS)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()));
    public static final RegistryObject<Block> FALLOUT_SOIL = BLOCKS.register("fallout_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COARSE_DIRT)));
    public static final RegistryObject<Block> SCORCHED_SOIL = BLOCKS.register("scorched_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COARSE_DIRT).strength(1.2F, 4.0F)));
    public static final RegistryObject<Block> FUSED_GROUND = BLOCKS.register("fused_ground",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).strength(3.0F, 6.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ASPHALT = BLOCKS.register("asphalt",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<Block> EDGE_LANE_WHITE = BLOCKS.register("edge_lane_white",
            () -> new RoadMarkingBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion(), RoadMarkingBlock.MarkingType.EDGE));
    public static final RegistryObject<Block> EDGE_LANE_YELLOW = BLOCKS.register("edge_lane_yellow",
            () -> new RoadMarkingBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion(), RoadMarkingBlock.MarkingType.EDGE));
    public static final RegistryObject<Block> WHITE_LANE_DIVIDER = BLOCKS.register("white_lane_divider",
            () -> new RoadMarkingBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion(), RoadMarkingBlock.MarkingType.DIVIDER));
    public static final RegistryObject<Block> EDGE_LANE_WHITE_STEP_CONNECTOR = BLOCKS.register(
            "edge_lane_white_step_connector",
            () -> new RoadMarkingStepConnectorBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion()));
    public static final RegistryObject<Block> EDGE_LANE_YELLOW_STEP_CONNECTOR = BLOCKS.register(
            "edge_lane_yellow_step_connector",
            () -> new RoadMarkingStepConnectorBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion()));
    public static final RegistryObject<Block> WHITE_LANE_DIVIDER_STEP_CONNECTOR = BLOCKS.register(
            "white_lane_divider_step_connector",
            () -> new RoadMarkingStepConnectorBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion(), true));
    public static final RegistryObject<Block> STRIPPED_POPLAR_LOG = BLOCKS.register("stripped_poplar_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.STRIPPED_OAK_LOG)));
    public static final RegistryObject<Block> POPLAR_LOG = BLOCKS.register("poplar_log",
            () -> new StrippableRotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG), STRIPPED_POPLAR_LOG));
    public static final RegistryObject<Block> STRIPPED_POPLAR_WOOD = BLOCKS.register("stripped_poplar_wood",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.STRIPPED_OAK_WOOD)));
    public static final RegistryObject<Block> POPLAR_WOOD = BLOCKS.register("poplar_wood",
            () -> new StrippableRotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_WOOD), STRIPPED_POPLAR_WOOD));
    public static final RegistryObject<Block> POPLAR_PLANKS = BLOCKS.register("poplar_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> POPLAR_STAIRS = BLOCKS.register("poplar_stairs",
            () -> new StairBlock(POPLAR_PLANKS.get().defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.OAK_STAIRS)));
    public static final RegistryObject<Block> POPLAR_SLAB = BLOCKS.register("poplar_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.OAK_SLAB)));
    public static final RegistryObject<Block> POPLAR_DOOR = BLOCKS.register("poplar_door",
            () -> new DoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_DOOR), AflBlockSetTypes.AFL_POPLAR));
    public static final RegistryObject<Block> POPLAR_TRAPDOOR = BLOCKS.register("poplar_trapdoor",
            () -> new net.minecraft.world.level.block.TrapDoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_TRAPDOOR), AflBlockSetTypes.AFL_POPLAR));
    public static final RegistryObject<Block> POPLAR_LEAVES = BLOCKS.register("poplar_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.BIRCH_LEAVES)));
    public static final RegistryObject<Block> POPLAR_SAPLING = BLOCKS.register("poplar_sapling",
            () -> new SaplingBlock(new PoplarTreeGrower(), BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)));

    private AflBlocks() {
    }
}
