package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialLockerBlockEntity;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.blockentity.EnergyCellBlockEntity;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.CompressorBlockEntity;
import com.antaurora.apofirstlight.blockentity.AlloyFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<BlockEntityType<IndustrialLockerBlockEntity>> INDUSTRIAL_LOCKER =
            BLOCK_ENTITIES.register("industrial_locker", () ->
                    BlockEntityType.Builder.of(IndustrialLockerBlockEntity::new, AflBlocks.INDUSTRIAL_LOCKER.get()).build(null));
    public static final RegistryObject<BlockEntityType<RetailShelfSingleBlockEntity>> RETAIL_SHELF_SINGLE =
            BLOCK_ENTITIES.register("retail_shelf_single", () ->
                    BlockEntityType.Builder.of(RetailShelfSingleBlockEntity::new,
                            AflBlocks.RETAIL_SHELF_SINGLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<CommercialGlassDoubleDoorBlockEntity>> COMMERCIAL_GLASS_DOUBLE_DOOR =
            BLOCK_ENTITIES.register("commercial_glass_double_door", () ->
                    BlockEntityType.Builder.of(CommercialGlassDoubleDoorBlockEntity::new,
                            AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR.get()).build(null));
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

    private AflBlockEntities() {
    }
}
