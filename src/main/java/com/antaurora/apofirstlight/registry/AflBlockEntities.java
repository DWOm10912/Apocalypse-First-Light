package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialLockerBlockEntity;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.CompressorBlockEntity;
import com.antaurora.apofirstlight.blockentity.AlloyFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ApocalypseFirstLight.MOD_ID);
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity>> RESTROOM_STALL_DOOR =
            BLOCK_ENTITIES.register("restroom_stall_door", () -> BlockEntityType.Builder.of(
                    com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity::new, AflBlocks.RESTROOM_STALL_DOOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity>> GUN_MAINTENANCE_BENCH =
            BLOCK_ENTITIES.register("gun_maintenance_bench",()->BlockEntityType.Builder.of(
                    com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity::new,AflBlocks.GUN_MAINTENANCE_BENCH.get()).build(null));

    public static final RegistryObject<BlockEntityType<IndustrialLockerBlockEntity>> INDUSTRIAL_LOCKER =
            BLOCK_ENTITIES.register("industrial_locker", () ->
                    BlockEntityType.Builder.of(IndustrialLockerBlockEntity::new, AflBlocks.INDUSTRIAL_LOCKER.get()).build(null));
    public static final RegistryObject<BlockEntityType<LeadChestBlockEntity>> LEAD_CHEST =
            BLOCK_ENTITIES.register("lead_chest", () ->
                    BlockEntityType.Builder.of(LeadChestBlockEntity::new, AflBlocks.LEAD_CHEST.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.IndustrialElectricalBoxBlockEntity>> INDUSTRIAL_ELECTRICAL_BOX =
            BLOCK_ENTITIES.register("industrial_electrical_box", () -> BlockEntityType.Builder.of(
                    com.antaurora.apofirstlight.blockentity.IndustrialElectricalBoxBlockEntity::new, AflBlocks.INDUSTRIAL_ELECTRICAL_BOX.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.CashRegisterBlockEntity>> CASH_REGISTER =
            BLOCK_ENTITIES.register("cash_register", () -> BlockEntityType.Builder.of(
                    com.antaurora.apofirstlight.blockentity.CashRegisterBlockEntity::new, AflBlocks.CASH_REGISTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<CommercialGlassDoubleDoorBlockEntity>> COMMERCIAL_GLASS_DOUBLE_DOOR =
            BLOCK_ENTITIES.register("commercial_glass_double_door", () ->
                    BlockEntityType.Builder.of(CommercialGlassDoubleDoorBlockEntity::new,
                            AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR.get()).build(null));
    /** Both cells of the Charging Station carry one (the right cell only forwards its power port to the master). */
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity>> CHARGING_STATION =
            BLOCK_ENTITIES.register("charging_station", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity::new,
                            AflBlocks.CHARGING_STATION.get()).build(null));
    public static final RegistryObject<BlockEntityType<BeverageCoolerBlockEntity>> BEVERAGE_COOLER =
            BLOCK_ENTITIES.register("beverage_cooler", () ->
                    BlockEntityType.Builder.of(BeverageCoolerBlockEntity::new,
                            AflBlocks.BEVERAGE_COOLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity>> VENDING_MACHINE =
            BLOCK_ENTITIES.register("vending_machine",()->BlockEntityType.Builder.of(
                    com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity::new,AflBlocks.VENDING_MACHINE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.CommercialDumpsterBlockEntity>> COMMERCIAL_DUMPSTER =
            BLOCK_ENTITIES.register("commercial_dumpster", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.CommercialDumpsterBlockEntity::new,
                            AflBlocks.COMMERCIAL_DUMPSTER.get(), AflBlocks.COMMERCIAL_DUMPSTER_BLUE.get(), AflBlocks.COMMERCIAL_DUMPSTER_BROWN.get(), AflBlocks.COMMERCIAL_DUMPSTER_GRAY.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity>> FUEL_DISPENSER =
            BLOCK_ENTITIES.register("fuel_dispenser", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity::new,
                            AflBlocks.FUEL_DISPENSER.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.MetalTrashCanBlockEntity>> METAL_TRASH_CAN =
            BLOCK_ENTITIES.register("metal_trash_can", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.MetalTrashCanBlockEntity::new,
                            AflBlocks.METAL_TRASH_CAN.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.WaterDispenserBlockEntity>> WATER_DISPENSER =
            BLOCK_ENTITIES.register("water_dispenser", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.WaterDispenserBlockEntity::new,
                            AflBlocks.WATER_DISPENSER.get()).build(null));
    public static final RegistryObject<BlockEntityType<ChestFreezerBlockEntity>> CHEST_FREEZER =
            BLOCK_ENTITIES.register("chest_freezer", () ->
                    BlockEntityType.Builder.of(ChestFreezerBlockEntity::new,
                            AflBlocks.CHEST_FREEZER.get()).build(null));
    public static final RegistryObject<BlockEntityType<RetailShelfSingleBlockEntity>> RETAIL_SHELF_SINGLE =
            BLOCK_ENTITIES.register("retail_shelf_single", () ->
                    BlockEntityType.Builder.of(RetailShelfSingleBlockEntity::new,
                            AflBlocks.RETAIL_SHELF_SINGLE.get()).build(null));
    /** Checkout Counter V1: one type for the plain and the display counter. */
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.CheckoutCounterBlockEntity>> CHECKOUT_COUNTER =
            BLOCK_ENTITIES.register("checkout_counter", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.CheckoutCounterBlockEntity::new,
                            AflBlocks.CHECKOUT_COUNTER.get(), AflBlocks.CHECKOUT_COUNTER_DISPLAY.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.CheckoutCounterGateBlockEntity>> CHECKOUT_COUNTER_GATE =
            BLOCK_ENTITIES.register("checkout_counter_gate", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.CheckoutCounterGateBlockEntity::new,
                            AflBlocks.CHECKOUT_COUNTER_GATE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.antaurora.apofirstlight.blockentity.BackBarShelfBlockEntity>> BACK_BAR_SHELF =
            BLOCK_ENTITIES.register("back_bar_shelf", () ->
                    BlockEntityType.Builder.of(com.antaurora.apofirstlight.blockentity.BackBarShelfBlockEntity::new,
                            AflBlocks.BACK_BAR_SHELF.get()).build(null));
    public static final RegistryObject<BlockEntityType<ThermalGeneratorBlockEntity>> THERMAL_GENERATOR =
            BLOCK_ENTITIES.register("thermal_generator", () ->
                    BlockEntityType.Builder.of(ThermalGeneratorBlockEntity::new,
                            AflBlocks.THERMAL_GENERATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
            BLOCK_ENTITIES.register("energy_cell", () ->
                    BlockEntityType.Builder.of(EnergyCellBlockEntity::new,
                            AflBlocks.ENERGY_CELL.get()).build(null));
    public static final RegistryObject<BlockEntityType<CrusherBlockEntity>> CRUSHER =
            BLOCK_ENTITIES.register("crusher", () ->
                    BlockEntityType.Builder.of(CrusherBlockEntity::new,
                            AflBlocks.CRUSHER.get()).build(null));
    public static final RegistryObject<BlockEntityType<IndustrialFurnaceBlockEntity>> INDUSTRIAL_FURNACE =
            BLOCK_ENTITIES.register("industrial_furnace", () ->
                    BlockEntityType.Builder.of(IndustrialFurnaceBlockEntity::new,
                            AflBlocks.INDUSTRIAL_FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<AlloyFurnaceBlockEntity>> ALLOY_FURNACE =
            BLOCK_ENTITIES.register("alloy_furnace", () ->
                    BlockEntityType.Builder.of(AlloyFurnaceBlockEntity::new,
                            AflBlocks.ALLOY_FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<CompressorBlockEntity>> COMPRESSOR =
            BLOCK_ENTITIES.register("compressor", () ->
                    BlockEntityType.Builder.of(CompressorBlockEntity::new,
                            AflBlocks.COMPRESSOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
            BLOCK_ENTITIES.register("fluid_tank", () ->
                    BlockEntityType.Builder.of(FluidTankBlockEntity::new,
                            AflBlocks.FLUID_TANK.get()).build(null));
    public static final RegistryObject<BlockEntityType<ChemicalReactorBlockEntity>> CHEMICAL_REACTOR =
            BLOCK_ENTITIES.register("chemical_reactor", () ->
                    BlockEntityType.Builder.of(ChemicalReactorBlockEntity::new,
                            AflBlocks.CHEMICAL_REACTOR.get()).build(null));

    private AflBlockEntities() {
    }
}
