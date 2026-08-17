package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialLockerBlockEntity;
import com.antaurora.apofirstlight.blockentity.MetalLockerBlockEntity;
import com.antaurora.apofirstlight.blockentity.SupermarketShelfSingleBlockEntity;
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
    public static final RegistryObject<BlockEntityType<MetalLockerBlockEntity>> METAL_LOCKER =
            BLOCK_ENTITIES.register("metal_locker", () ->
                    BlockEntityType.Builder.of(MetalLockerBlockEntity::new, AflBlocks.METAL_LOCKER.get()).build(null));
    public static final RegistryObject<BlockEntityType<SupermarketShelfSingleBlockEntity>> SUPERMARKET_SHELF_SINGLE =
            BLOCK_ENTITIES.register("supermarket_shelf_single", () ->
                    BlockEntityType.Builder.of(SupermarketShelfSingleBlockEntity::new,
                            AflBlocks.SUPERMARKET_SHELF_SINGLE.get()).build(null));

    private AflBlockEntities() {
    }
}
