package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.block.SteelGrateBlock;
import com.antaurora.apofirstlight.block.JointedPavementBlock;
import com.antaurora.apofirstlight.block.CurbBlock;
import com.antaurora.apofirstlight.block.PavementArrowBlock;
import com.antaurora.apofirstlight.block.PavementHatchBlock;
import com.antaurora.apofirstlight.block.PavementMarkingBlock;
import com.antaurora.apofirstlight.block.PavementPaintBlock;
import net.minecraft.world.level.material.PushReaction;
import com.antaurora.apofirstlight.block.AxisSteelStructureBlock;
import com.antaurora.apofirstlight.block.SteelStructureBlock;
import com.antaurora.apofirstlight.block.SteelDoorBlock;
import com.antaurora.apofirstlight.block.IndustrialUtilityLightBlock;
import com.antaurora.apofirstlight.block.IndustrialLockerBlock;
import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.block.CashRegisterBlock;
import com.antaurora.apofirstlight.block.CheckoutCounterBlock;
import com.antaurora.apofirstlight.block.CheckoutCounterGateBlock;
import com.antaurora.apofirstlight.block.BackBarShelfBlock;
import com.antaurora.apofirstlight.block.WaterDispenserBlock;
import com.antaurora.apofirstlight.block.MetalTrashCanBlock;
import com.antaurora.apofirstlight.block.CommercialDumpsterBlock;
import com.antaurora.apofirstlight.block.CommercialGlassDoubleDoorBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.block.ModernOfficeDeskBlock;
import com.antaurora.apofirstlight.block.ModernOfficeChairBlock;
import com.antaurora.apofirstlight.block.ModernLcdMonitorBlock;
import com.antaurora.apofirstlight.block.OfficeDesktopDecorationBlock;
import com.antaurora.apofirstlight.block.OfficeCubiclePartitionBlock;
import com.antaurora.apofirstlight.block.RestroomPartitionBlock;
import com.antaurora.apofirstlight.block.CommercialFlushometerToiletBlock;
import com.antaurora.apofirstlight.block.CommercialWallMountedSinkBlock;
import com.antaurora.apofirstlight.block.LowFilingCabinetBlock;
import com.antaurora.apofirstlight.block.TallFilingCabinetBlock;
import com.antaurora.apofirstlight.block.OfficeMultifunctionPrinterBlock;
import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.block.EnergyCellBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.block.FluidPipeBlock;
import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.block.CrusherBlock;
import com.antaurora.apofirstlight.block.IndustrialFurnaceBlock;
import com.antaurora.apofirstlight.block.CompressorBlock;
import com.antaurora.apofirstlight.block.AlloyFurnaceBlock;
import com.antaurora.apofirstlight.block.ChemicalReactorBlock;
import com.antaurora.apofirstlight.block.LeadChestBlock;
import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.block.RoadMarkingStepConnectorBlock;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<LiquidBlock> INDUSTRIAL_WASTE = BLOCKS.register("industrial_waste",
            () -> new LiquidBlock(AflFluids.INDUSTRIAL_WASTE, BlockBehaviour.Properties.copy(Blocks.WATER)));
    public static final RegistryObject<LiquidBlock> GASOLINE = BLOCKS.register("gasoline",
            () -> new LiquidBlock(AflFluids.GASOLINE, BlockBehaviour.Properties.copy(Blocks.WATER)));
    public static final RegistryObject<LiquidBlock> DIESEL = BLOCKS.register("diesel",
            () -> new LiquidBlock(AflFluids.DIESEL, BlockBehaviour.Properties.copy(Blocks.WATER)));

    public static final RegistryObject<Block> REINFORCED_CONCRETE = BLOCKS.register("reinforced_concrete",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(6.0F, 15.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> REINFORCED_CONCRETE_SLAB = BLOCKS.register("reinforced_concrete_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(REINFORCED_CONCRETE.get())
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> REINFORCED_CONCRETE_STAIRS = BLOCKS.register("reinforced_concrete_stairs",
            () -> new StairBlock(REINFORCED_CONCRETE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(REINFORCED_CONCRETE.get()).requiresCorrectToolForDrops()));
    /** Interlocking cast lead bricks: the dense radiation shielding block (RadiationShielding.LEAD_TRANSMISSION). */
    /** Quartz Glass V1 (tools/build-quartz-glass-v1.mjs): a fused quartz glass block, a little tougher than glass; drops itself. */
    public static final RegistryObject<Block> QUARTZ_GLASS = BLOCKS.register("quartz_glass",
            () -> new net.minecraft.world.level.block.GlassBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).strength(0.6F, 1.5F)));
    public static final RegistryObject<Block> LEAD_SHIELDING_BRICKS = BLOCKS.register("lead_shielding_bricks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_BLOCK = BLOCKS.register("steel_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_CABLE = BLOCKS.register("steel_cable",
            () -> new com.antaurora.apofirstlight.block.SteelCableBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> STEEL_BEAM = BLOCKS.register("steel_beam",
            () -> new AxisSteelStructureBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> STEEL_BRACE = BLOCKS.register("steel_brace",
            () -> new AxisSteelStructureBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> BAUXITE_ORE = BLOCKS.register("bauxite_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> GALENA_ORE = BLOCKS.register("galena_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> LEAD_CHEST = BLOCKS.register("lead_chest",
            () -> new LeadChestBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.NETHERITE_BLOCK)   // heavy, dead metal: steel skin over a lead core
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<com.antaurora.apofirstlight.block.StaticWorkstationBlock> GUN_MAINTENANCE_BENCH = BLOCKS.register("gun_maintenance_bench",
            () -> new com.antaurora.apofirstlight.block.GunMaintenanceBenchBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<com.antaurora.apofirstlight.block.StaticWorkstationBlock> PRECISION_FABRICATION_STATION = BLOCKS.register("precision_fabrication_station",
            () -> new com.antaurora.apofirstlight.block.StaticWorkstationBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(), true));
    public static final RegistryObject<Block> SPHALERITE_ORE = BLOCKS.register("sphalerite_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CASSITERITE_ORE = BLOCKS.register("cassiterite_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> PENTLANDITE_ORE = BLOCKS.register("pentlandite_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> WOLFRAMITE_ORE = BLOCKS.register("wolframite_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SPODUMENE_ORE = BLOCKS.register("spodumene_ore",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_BLOCK_SLAB = BLOCKS.register("steel_block_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(STEEL_BLOCK.get()).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_BLOCK_STAIRS = BLOCKS.register("steel_block_stairs",
            () -> new StairBlock(STEEL_BLOCK.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(STEEL_BLOCK.get()).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_GRATE = BLOCKS.register("steel_grate",
            () -> new SteelGrateBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> STEEL_RAILING = BLOCKS.register("steel_railing",
            () -> new IronBarsBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BARS)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_PLATE = BLOCKS.register("steel_plate",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(6.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_PLATE_SLAB = BLOCKS.register("steel_plate_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(STEEL_PLATE.get()).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_PLATE_STAIRS = BLOCKS.register("steel_plate_stairs",
            () -> new StairBlock(STEEL_PLATE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(STEEL_PLATE.get()).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STEEL_DOOR = BLOCKS.register("steel_door",
            () -> new SteelDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(6.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), AflBlockSetTypes.AFL_STEEL));
    /** Steel-frame doors V1 (docs/models/steel_frame_doors_v1.md): the charcoal steel frame with a maple veneer leaf; wood rules (axe, no tier). */
    public static final RegistryObject<Block> COMMERCIAL_WOOD_DOOR = BLOCKS.register("commercial_wood_door",
            () -> new com.antaurora.apofirstlight.block.CommercialWoodDoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_DOOR)
                    .strength(3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion(), net.minecraft.world.level.block.state.properties.BlockSetType.OAK));
    /** Building Lights V1 (docs/models/building_lights_v1.md): the square LED panel light under its old ID; lit while the building's lighting circuit has power. */
    public static final RegistryObject<Block> INDUSTRIAL_UTILITY_LIGHT = BLOCKS.register("industrial_utility_light",
            () -> new IndustrialUtilityLightBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.BuildingLightBlock.LIT) ? 15 : 0)));
    /** Building Lights V1: an aluminium fixture (pickaxe + diamond), sections join into rows. */
    public static final RegistryObject<Block> LINEAR_LIGHT = BLOCKS.register("linear_light",
            () -> new com.antaurora.apofirstlight.block.LinearLightBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.BuildingLightBlock.LIT) ? 15 : 0)));
    /** Building Lights V1: a small plastic battery unit, hand-breakable like the wall outlet; light 10 on its battery. */
    public static final RegistryObject<Block> EMERGENCY_LIGHT = BLOCKS.register("emergency_light",
            () -> new com.antaurora.apofirstlight.block.EmergencyLightBlock(BlockBehaviour.Properties.of().strength(0.5F, 1.0F).sound(SoundType.STONE).noCollission().noOcclusion()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.EmergencyLightBlock.MODE) == com.antaurora.apofirstlight.block.EmergencyLightBlock.Mode.ON ? 10 : 0)));
    /**
     * Site Lighting V1 (docs/models/site_lighting_v1.md): parking-lot poles (base, segments, head cell), the wall pack and the
     * canopy downlight are galvanized / die-cast steel and aluminium fixtures: pickaxe + diamond tier. The hidden light point
     * is no item and drops nothing.
     */
    public static final RegistryObject<Block> LIGHT_POLE_BASE = BLOCKS.register("light_pole_base",
            () -> new com.antaurora.apofirstlight.block.LightPoleBaseBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> LIGHT_POLE = BLOCKS.register("light_pole",
            () -> new com.antaurora.apofirstlight.block.LightPoleBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> AREA_LIGHT = BLOCKS.register("area_light",
            () -> new com.antaurora.apofirstlight.block.AreaLightBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.AreaLightBlock.LIT) ? 15 : 0)));
    public static final RegistryObject<Block> WALL_PACK = BLOCKS.register("wall_pack",
            () -> new com.antaurora.apofirstlight.block.WallPackBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.BuildingLightBlock.LIT) ? 15 : 0)));
    public static final RegistryObject<Block> CANOPY_DOWNLIGHT = BLOCKS.register("canopy_downlight",
            () -> new com.antaurora.apofirstlight.block.CanopyDownlightBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().noCollission()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.BuildingLightBlock.LIT) ? 15 : 0)));
    public static final RegistryObject<Block> LAMP_GLOW = BLOCKS.register("lamp_glow",
            () -> new com.antaurora.apofirstlight.block.LampGlowBlock(BlockBehaviour.Properties.of().replaceable().noCollission()
                    .noOcclusion().noLootTable().instabreak().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(state -> com.antaurora.apofirstlight.block.LampGlowBlock.LIGHT_LEVEL)));
    /**
     * Fuel Stop A1 details V1 (docs/models/fuel_stop_a1_details_v1.md): the PRAIRIE channel letters (aluminium) and the
     * roadside price sign (aluminium cabinet on a masonry base): pickaxe + diamond tier.
     */
    public static final RegistryObject<Block> CHANNEL_LETTER = BLOCKS.register("channel_letter",
            () -> new com.antaurora.apofirstlight.block.ChannelLetterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.BuildingLightBlock.LIT) ? com.antaurora.apofirstlight.block.ChannelLetterBlock.LIGHT_LEVEL : 0)));
    public static final RegistryObject<Block> PRICE_SIGN = BLOCKS.register("price_sign",
            () -> new com.antaurora.apofirstlight.block.PriceSignBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(com.antaurora.apofirstlight.block.PriceSignBlock::lightLevel)));
    /**
     * Fuel Stop A1 details V1: the TPO roof (a membrane over a steel deck: a steel-based building structure, pickaxe +
     * diamond, as the metal wall panel) and the rooftop unit (a machine casing, pickaxe + diamond).
     */
    public static final RegistryObject<Block> ROOF_TPO = BLOCKS.register("roof_tpo",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    /** Trash enclosure V1: the steel gate (steel structure, pickaxe + diamond). */
    public static final RegistryObject<Block> ENCLOSURE_GATE = BLOCKS.register("enclosure_gate",
            () -> new com.antaurora.apofirstlight.block.EnclosureGateBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> ROOFTOP_UNIT = BLOCKS.register("rooftop_unit",
            () -> new com.antaurora.apofirstlight.block.RooftopUnitBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    /** Building Power V1 (docs/models/building_power_v1.md): industrial infrastructure, pickaxe + diamond tier. */
    public static final RegistryObject<Block> DISTRIBUTION_PANEL = BLOCKS.register("distribution_panel",
            () -> new com.antaurora.apofirstlight.block.DistributionPanelBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> SERVICE_METER_BOX = BLOCKS.register("service_meter_box",
            () -> new com.antaurora.apofirstlight.block.ServiceMeterBoxBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    /** Power Outlets V1 (docs/models/power_outlets_v1.md): small plastic fittings, hand-breakable (as the office keyboard), no collision. */
    public static final RegistryObject<Block> WALL_OUTLET = BLOCKS.register("wall_outlet",
            () -> new com.antaurora.apofirstlight.block.WallOutletBlock(BlockBehaviour.Properties.of().strength(0.3F, 0.8F).sound(SoundType.STONE).noCollission().noOcclusion()));
    public static final RegistryObject<Block> POWER_STRIP_3 = BLOCKS.register("power_strip_3",
            () -> new com.antaurora.apofirstlight.block.PowerStripBlock(BlockBehaviour.Properties.of().strength(0.3F, 0.8F).sound(SoundType.STONE).noCollission().noOcclusion(), 3));
    public static final RegistryObject<Block> POWER_STRIP_6 = BLOCKS.register("power_strip_6",
            () -> new com.antaurora.apofirstlight.block.PowerStripBlock(BlockBehaviour.Properties.of().strength(0.3F, 0.8F).sound(SoundType.STONE).noCollission().noOcclusion(), 6));
    public static final RegistryObject<Block> INDUSTRIAL_LOCKER = BLOCKS.register("industrial_locker",
            () -> new IndustrialLockerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F)
                    .sound(AflSoundTypes.SHEET_METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> RETAIL_SHELF_SINGLE = BLOCKS.register("retail_shelf_single",
            () -> new RetailShelfSingleBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 4.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> CASH_REGISTER = BLOCKS.register("cash_register",
            () -> new CashRegisterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.5F, 4.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    // Checkout Counter V1 (docs/models/checkout_counter_v1.md): laminate-on-steel retail furniture, iron-tier pickaxe
    public static final RegistryObject<Block> CHECKOUT_COUNTER = BLOCKS.register("checkout_counter",
            () -> new CheckoutCounterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 4.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), false));
    public static final RegistryObject<Block> CHECKOUT_COUNTER_DISPLAY = BLOCKS.register("checkout_counter_display",
            () -> new CheckoutCounterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 4.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), true));
    public static final RegistryObject<Block> CHECKOUT_COUNTER_GATE = BLOCKS.register("checkout_counter_gate",
            () -> new CheckoutCounterGateBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 4.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> BACK_BAR_SHELF = BLOCKS.register("back_bar_shelf",
            () -> new BackBarShelfBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 4.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> STORAGE_RACK = BLOCKS.register("storage_rack",
            () -> new com.antaurora.apofirstlight.block.StorageRackBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> WATER_DISPENSER = BLOCKS.register("water_dispenser",
            () -> new WaterDispenserBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> METAL_TRASH_CAN = BLOCKS.register("metal_trash_can",
            () -> new MetalTrashCanBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F)
                    .sound(AflSoundTypes.SHEET_METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    // Commercial Dumpster V2 (2026-10-02): one block per body colour, same class, mesh and block entity type
    public static final RegistryObject<Block> COMMERCIAL_DUMPSTER = BLOCKS.register("commercial_dumpster", () -> dumpster("green"));
    public static final RegistryObject<Block> COMMERCIAL_DUMPSTER_BLUE = BLOCKS.register("commercial_dumpster_blue", () -> dumpster("blue"));
    public static final RegistryObject<Block> COMMERCIAL_DUMPSTER_BROWN = BLOCKS.register("commercial_dumpster_brown", () -> dumpster("brown"));
    public static final RegistryObject<Block> COMMERCIAL_DUMPSTER_GRAY = BLOCKS.register("commercial_dumpster_gray", () -> dumpster("gray"));
    // Fuel Dispenser V1 (tools/build-fuel-dispenser-v1.mjs): industrial infrastructure, diamond-tier pickaxe
    public static final RegistryObject<Block> FUEL_DISPENSER = BLOCKS.register("fuel_dispenser",
            () -> new com.antaurora.apofirstlight.block.FuelDispenserBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.FuelDispenserBlock.LIT)
                            && state.getValue(com.antaurora.apofirstlight.block.FuelDispenserBlock.CELL).dy == 2
                            ? com.antaurora.apofirstlight.block.FuelDispenserBlock.LIGHT_LEVEL : 0)
                    .requiresCorrectToolForDrops().noOcclusion()));
    // Fuel Island Kit V1 (tools/build-fuel-island-v1.mjs): concrete curb pieces (any pickaxe), the steel bollard (diamond tier)
    public static final RegistryObject<Block> FUEL_ISLAND_CURB = BLOCKS.register("fuel_island_curb",
            () -> new com.antaurora.apofirstlight.block.FuelIslandCurbBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(1.8F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_ISLAND_END = BLOCKS.register("fuel_island_end",
            () -> new com.antaurora.apofirstlight.block.FuelIslandEndBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(1.8F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_ISLAND_BOLLARD = BLOCKS.register("fuel_island_bollard",
            () -> new com.antaurora.apofirstlight.block.FuelIslandBollardBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_CANOPY_COLUMN = BLOCKS.register("fuel_canopy_column",
            () -> new com.antaurora.apofirstlight.block.FuelCanopyColumnBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_CANOPY_CEILING = BLOCKS.register("fuel_canopy_ceiling",
            () -> new com.antaurora.apofirstlight.block.FuelCanopyCeilingBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_CANOPY_LIGHT = BLOCKS.register("fuel_canopy_light",
            () -> new com.antaurora.apofirstlight.block.FuelCanopyLightBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.FuelCanopyLightBlock.LIT)
                            ? com.antaurora.apofirstlight.block.FuelCanopyLightBlock.LIGHT_LEVEL : 0)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_CANOPY_FASCIA = BLOCKS.register("fuel_canopy_fascia",
            () -> new com.antaurora.apofirstlight.block.FuelCanopyFasciaBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> UNDERGROUND_FUEL_TANK_GASOLINE = BLOCKS.register("underground_fuel_tank_gasoline",
            () -> new com.antaurora.apofirstlight.block.UndergroundFuelTankBlock(AflFluids.GASOLINE, tankProperties()));
    public static final RegistryObject<Block> UNDERGROUND_FUEL_TANK_DIESEL = BLOCKS.register("underground_fuel_tank_diesel",
            () -> new com.antaurora.apofirstlight.block.UndergroundFuelTankBlock(AflFluids.DIESEL, tankProperties()));

    // the fuel station sump set (docs/models/fuel_station_sump_v1.md)
    public static final RegistryObject<Block> SUBMERSIBLE_FUEL_PUMP = BLOCKS.register("submersible_fuel_pump",
            () -> new com.antaurora.apofirstlight.block.SubmersibleFuelPumpBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> PUMP_MANHOLE_COVER = BLOCKS.register("pump_manhole_cover",
            () -> new com.antaurora.apofirstlight.block.FuelSumpCoverBlock(com.antaurora.apofirstlight.block.FuelSumpCoverBlock.Kind.MANHOLE,
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5.0F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_FILL_COVER_GASOLINE = BLOCKS.register("fuel_fill_cover_gasoline",
            () -> new com.antaurora.apofirstlight.block.FuelSumpCoverBlock(com.antaurora.apofirstlight.block.FuelSumpCoverBlock.Kind.FILL,
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5.0F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FUEL_FILL_COVER_DIESEL = BLOCKS.register("fuel_fill_cover_diesel",
            () -> new com.antaurora.apofirstlight.block.FuelSumpCoverBlock(com.antaurora.apofirstlight.block.FuelSumpCoverBlock.Kind.FILL,
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5.0F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));

    // fuel containers and the hand pump (docs/models/fuel_containers_v1.md): low-tier steel, any pickaxe or by hand
    public static final RegistryObject<Block> JERRY_CAN = BLOCKS.register("jerry_can",
            () -> new com.antaurora.apofirstlight.block.FuelCanBlock(com.antaurora.apofirstlight.block.FuelCanBlock.Size.JERRY_CAN,
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(1.0F, 2.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> SMALL_FUEL_DRUM = BLOCKS.register("small_fuel_drum",
            () -> new com.antaurora.apofirstlight.block.FuelCanBlock(com.antaurora.apofirstlight.block.FuelCanBlock.Size.SMALL_DRUM,
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(1.5F, 3.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> FUEL_DRUM = BLOCKS.register("fuel_drum",
            () -> new com.antaurora.apofirstlight.block.FuelCanBlock(com.antaurora.apofirstlight.block.FuelCanBlock.Size.DRUM,
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(2.0F, 3.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> HAND_FUEL_PUMP = BLOCKS.register("hand_fuel_pump",
            () -> new com.antaurora.apofirstlight.block.HandFuelPumpBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(1.0F, 2.0F).sound(SoundType.METAL).noOcclusion().noCollission()));

    public static final RegistryObject<Block> FUEL_DISPENSER_SUMP = BLOCKS.register("fuel_dispenser_sump",
            () -> new com.antaurora.apofirstlight.block.FuelDispenserSumpBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> INTAKE_PUMP = BLOCKS.register("intake_pump",
            () -> new com.antaurora.apofirstlight.block.IntakePumpBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    private static BlockBehaviour.Properties tankProperties() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5.0F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion();
    }

    private static CommercialDumpsterBlock dumpster(String colour) {
        return new CommercialDumpsterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                .strength(3.5F, 6.0F)
                .sound(AflSoundTypes.SHEET_METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion(), colour);
    }
    public static final RegistryObject<Block> COMMERCIAL_GLASS_DOUBLE_DOOR = BLOCKS.register("commercial_glass_double_door",
            () -> new CommercialGlassDoubleDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));
    /** The same door in a black anodized frame, to go with Storefront Glazing (docs/models/storefront_glazing_v1.md). */
    public static final RegistryObject<Block> COMMERCIAL_GLASS_DOUBLE_DOOR_BLACK = BLOCKS.register("commercial_glass_double_door_black",
            () -> new CommercialGlassDoubleDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));
    // Facade Brick V1 (docs/models/facade_brick_v1.md): plain full blocks, mined like vanilla bricks
    public static final RegistryObject<Block> FACE_BRICK_WARM_GRAY = BLOCKS.register("face_brick_warm_gray",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRICKS)));
    public static final RegistryObject<Block> FACE_BRICK_CHARCOAL = BLOCKS.register("face_brick_charcoal",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRICKS)));
    // Facade Masonry Base V1 (docs/models/facade_masonry_base_v1.md): ground-face block base course with a cast-stone cap
    public static final RegistryObject<Block> GROUND_FACE_BLOCK = BLOCKS.register("ground_face_block",
            () -> new com.antaurora.apofirstlight.block.MasonryBaseBlock(BlockBehaviour.Properties.copy(Blocks.BRICKS)));
    // Trash enclosure V1 (docs/models/fuel_stop_a1_details_v1.md): ordinary masonry as the ground face block (pickaxe, any tier)
    public static final RegistryObject<Block> CMU_SCREEN_WALL = BLOCKS.register("cmu_screen_wall",
            () -> new com.antaurora.apofirstlight.block.CmuScreenWallBlock(BlockBehaviour.Properties.copy(Blocks.BRICKS)));
    // Aluminum Cornice V1 (docs/models/aluminum_cornice_v1.md): aluminium commercial structure, Diamond-tier like the glazing
    public static final RegistryObject<Block> ALUMINUM_CORNICE = BLOCKS.register("aluminum_cornice",
            () -> new com.antaurora.apofirstlight.block.AluminumCorniceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    // Metal Wall Panel V1 (docs/models/metal_wall_panel_v1.md): coated aluminium cladding and its jamb plate, Diamond-tier like the cornice
    public static final RegistryObject<Block> METAL_WALL_PANEL = BLOCKS.register("metal_wall_panel",
            () -> new com.antaurora.apofirstlight.block.MetalWallPanelBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> METAL_PANEL_JAMB = BLOCKS.register("metal_panel_jamb",
            () -> new com.antaurora.apofirstlight.block.MetalPanelJambBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    // Metal Eyebrow Canopy V1 (docs/models/metal_eyebrow_canopy_v1.md): coated aluminium hanger-rod canopy, Diamond-tier like the panel
    public static final RegistryObject<Block> METAL_EYEBROW_CANOPY = BLOCKS.register("metal_eyebrow_canopy",
            () -> new com.antaurora.apofirstlight.block.MetalEyebrowCanopyBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STOREFRONT_GLAZING = BLOCKS.register("storefront_glazing",
            () -> new com.antaurora.apofirstlight.block.StorefrontGlazingBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F).sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops().noOcclusion()
                    .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)));
    public static final RegistryObject<Block> BEVERAGE_COOLER = BLOCKS.register("beverage_cooler",
            () -> new BeverageCoolerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(BeverageCoolerBlock.LIT) ? BeverageCoolerBlock.LIGHT_LEVEL : 0)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> VENDING_MACHINE = BLOCKS.register("vending_machine",
            () -> new com.antaurora.apofirstlight.block.VendingMachineBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F,5.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.antaurora.apofirstlight.block.VendingMachineBlock.LIT)
                            ? com.antaurora.apofirstlight.block.VendingMachineBlock.LIGHT_LEVEL : 0)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> CHEST_FREEZER = BLOCKS.register("chest_freezer",
            () -> new ChestFreezerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> MODERN_OFFICE_DESK = BLOCKS.register("modern_office_desk",
            () -> new ModernOfficeDeskBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> MODERN_OFFICE_CHAIR = BLOCKS.register("modern_office_chair",
            () -> new ModernOfficeChairBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.5F, 4.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> MODERN_LCD_MONITOR = BLOCKS.register("modern_lcd_monitor",
            () -> new ModernLcdMonitorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> OFFICE_COMPUTER_STATION = BLOCKS.register("office_computer_station",
            () -> new OfficeDesktopDecorationBlock(BlockBehaviour.Properties.of()
                    .strength(0.7F, 1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion(), OfficeDesktopDecorationBlock.computerStationShape(),
                    OfficeDesktopDecorationBlock.computerStationCollision()));
    public static final RegistryObject<Block> OFFICE_KEYBOARD = BLOCKS.register("office_keyboard",
            () -> new OfficeDesktopDecorationBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F, 0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion(), OfficeDesktopDecorationBlock.keyboardShape()));
    public static final RegistryObject<Block> OFFICE_MOUSE = BLOCKS.register("office_mouse",
            () -> new OfficeDesktopDecorationBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F, 0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion(), OfficeDesktopDecorationBlock.mouseShape()));
    public static final RegistryObject<Block> OFFICE_CUBICLE_PARTITION = BLOCKS.register("office_cubicle_partition",
            () -> new OfficeCubiclePartitionBlock(BlockBehaviour.Properties.of()
                    .strength(0.8F, 1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final RegistryObject<Block> RESTROOM_PARTITION = BLOCKS.register("restroom_partition",
            () -> new RestroomPartitionBlock(BlockBehaviour.Properties.of()
                    .strength(0.8F, 1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final RegistryObject<Block> RESTROOM_STALL_DOOR = BLOCKS.register("restroom_stall_door",
            () -> new com.antaurora.apofirstlight.block.RestroomStallDoorBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> COMMERCIAL_FLUSHOMETER_TOILET = BLOCKS.register("commercial_flushometer_toilet",
            () -> new CommercialFlushometerToiletBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 4.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> COMMERCIAL_WALL_MOUNTED_SINK = BLOCKS.register("commercial_wall_mounted_sink",
            () -> new CommercialWallMountedSinkBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 4.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    /** Restroom Fixtures V2 wall mirror (docs/models/restroom_fixtures_v2.md): any pickaxe; by hand it breaks without dropping. */
    public static final RegistryObject<Block> WALL_MIRROR = BLOCKS.register("wall_mirror",
            () -> new com.antaurora.apofirstlight.block.WallMirrorBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F, 2.0F).sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> LOW_FILING_CABINET = BLOCKS.register("low_filing_cabinet",
            () -> new LowFilingCabinetBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.5F, 4.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> TALL_FILING_CABINET = BLOCKS.register("tall_filing_cabinet",
            () -> new TallFilingCabinetBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 5.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> OFFICE_MULTIFUNCTION_PRINTER = BLOCKS.register("office_multifunction_printer",
            () -> new OfficeMultifunctionPrinterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final RegistryObject<Block> FALLOUT_SOIL = BLOCKS.register("fallout_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COARSE_DIRT)));
    public static final RegistryObject<Block> SCORCHED_SOIL = BLOCKS.register("scorched_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COARSE_DIRT).strength(1.2F, 4.0F)));
    public static final RegistryObject<Block> FUSED_GROUND = BLOCKS.register("fused_ground",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).strength(3.0F, 6.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ASPHALT = BLOCKS.register("asphalt",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).requiresCorrectToolForDrops()));
    // Ground Materials V1 (2026-10-08, docs/models/ground_materials_v1.md): pavement and floor finishes. Ordinary building
    // materials, like vanilla stone and the facade brick: any pickaxe, no salvage tier.
    public static final RegistryObject<Block> CONCRETE_SIDEWALK = BLOCKS.register("concrete_sidewalk",
            () -> new JointedPavementBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops(), 2));
    public static final RegistryObject<Block> CONCRETE_PAVEMENT = BLOCKS.register("concrete_pavement",
            () -> new JointedPavementBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops(), 4));
    // Curbs V1 (2026-10-09, docs/models/curbs_v1.md): a sidewalk or grass ground block with a 15 cm concrete curb on every edge
    // that meets a road surface; the shape follows the neighbours (dynamic), the block lets light through (the curb faces are
    // lit from it). Ground material like the walk: any pickaxe.
    public static final RegistryObject<Block> CURB_SIDEWALK = BLOCKS.register("curb_sidewalk",
            () -> new CurbBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion().dynamicShape(), CurbBlock.Back.SIDEWALK));
    public static final RegistryObject<Block> CURB_GRASS = BLOCKS.register("curb_grass",
            () -> new CurbBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion().dynamicShape(), CurbBlock.Back.GRASS));
    public static final RegistryObject<Block> SEALED_CONCRETE_FLOOR = BLOCKS.register("sealed_concrete_floor",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> PORCELAIN_FLOOR_TILE = BLOCKS.register("porcelain_floor_tile",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).strength(1.5F, 6.0F).sound(SoundType.DEEPSLATE_TILES).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> RESTROOM_FLOOR_TILE = BLOCKS.register("restroom_floor_tile",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).strength(1.5F, 6.0F).sound(SoundType.DEEPSLATE_TILES).requiresCorrectToolForDrops()));
    // The North American roads V1-B quantized surfaces (road_asphalt_surface, road_sidewalk_surface, road_utility_surface,
    // road_curb) were removed on 2026-10-08 at the user's request: placeholder textures, unused outside V1-B construction
    // (user FAIL); road surfaces come back as proper meshes (docs/worldgen/road_surface_assets_v1.md).
    public static final RegistryObject<Block> THERMAL_GENERATOR = BLOCKS.register("thermal_generator",
            () -> new ThermalGeneratorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .lightLevel(state -> state.getValue(ThermalGeneratorBlock.LIT) ? 9 : 0)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ENERGY_CELL = BLOCKS.register("energy_cell",
            () -> new EnergyCellBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));
    // Charging Station V1: two-cell Pure Mesh charging bench, cable-fed (machine_balance/charging_station.json)
    public static final RegistryObject<Block> CHARGING_STATION = BLOCKS.register("charging_station",
            () -> new com.antaurora.apofirstlight.block.ChargingStationBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> CRUSHER = BLOCKS.register("crusher",
            () -> new CrusherBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> INDUSTRIAL_FURNACE = BLOCKS.register("industrial_furnace",
            () -> new IndustrialFurnaceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ALLOY_FURNACE = BLOCKS.register("alloy_furnace",
            () -> new AlloyFurnaceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> COMPRESSOR = BLOCKS.register("compressor",
            () -> new CompressorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CHEMICAL_REACTOR = BLOCKS.register("chemical_reactor",
            () -> new ChemicalReactorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> POWER_CABLE = BLOCKS.register("power_cable",
            () -> new PowerCableBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FLUID_PIPE = BLOCKS.register("fluid_pipe",
            () -> new FluidPipeBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> FLUID_TANK = BLOCKS.register("fluid_tank",
            () -> new FluidTankBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    // Heat-resistant fluid set (2026-10-05, docs/models/heat_resistant_fluid_set_v1.md): hot liquids (lava) without melting
    public static final RegistryObject<Block> HEAT_RESISTANT_FLUID_PIPE = BLOCKS.register("heat_resistant_fluid_pipe",
            () -> new FluidPipeBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops().noOcclusion(), true));
    public static final RegistryObject<Block> HEAT_RESISTANT_FLUID_TANK = BLOCKS.register("heat_resistant_fluid_tank",
            () -> new FluidTankBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(), true));
    public static final RegistryObject<Block> HEAT_RESISTANT_INTAKE_PUMP = BLOCKS.register("heat_resistant_intake_pump",
            () -> new com.antaurora.apofirstlight.block.IntakePumpBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(), true));
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
    // Pavement Markings V1 (2026-10-08, docs/models/pavement_markings_v1.md): paint sheets like the road markings above:
    // break at once, no collision, no drops (paint is not recovered), no tool or tier.
    public static final RegistryObject<Block> EDGE_LANE_BLUE = BLOCKS.register("edge_lane_blue",
            () -> new RoadMarkingBlock(BlockBehaviour.Properties.of()
                    .strength(0.1F)
                    .sound(SoundType.STONE)
                    .noCollission()
                    .noOcclusion(), RoadMarkingBlock.MarkingType.EDGE));
    public static final RegistryObject<Block> PAVEMENT_HATCH = BLOCKS.register("pavement_hatch",
            () -> new PavementHatchBlock(BlockBehaviour.Properties.of().strength(0.1F).sound(SoundType.STONE).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY), false));
    public static final RegistryObject<Block> PAVEMENT_CROSSHATCH = BLOCKS.register("pavement_crosshatch",
            () -> new PavementHatchBlock(BlockBehaviour.Properties.of().strength(0.1F).sound(SoundType.STONE).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY), true));
    public static final RegistryObject<Block> PAVEMENT_BAR = BLOCKS.register("pavement_bar",
            () -> new PavementPaintBlock(BlockBehaviour.Properties.of().strength(0.1F).sound(SoundType.STONE).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY), PavementPaintBlock.Paint.WHITE));
    public static final RegistryObject<Block> PAVEMENT_ARROW = BLOCKS.register("pavement_arrow",
            () -> new PavementArrowBlock(BlockBehaviour.Properties.of().strength(0.1F).sound(SoundType.STONE).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> PAVEMENT_ACCESSIBLE_SYMBOL = BLOCKS.register("pavement_accessible_symbol",
            () -> new PavementMarkingBlock(BlockBehaviour.Properties.of().strength(0.1F).sound(SoundType.STONE).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
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
    private AflBlocks() {
    }
}
