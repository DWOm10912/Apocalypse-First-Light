package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.block.SteelGrateBlock;
import com.antaurora.apofirstlight.block.SteelDoorBlock;
import com.antaurora.apofirstlight.block.IndustrialUtilityLightBlock;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
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
    public static final RegistryObject<Block> STEEL_BLOCK = BLOCKS.register("steel_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(7.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
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

    private AflBlocks() {
    }
}
