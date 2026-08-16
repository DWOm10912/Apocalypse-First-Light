package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.IndustrialUtilityLightBlock;
import com.antaurora.apofirstlight.block.IndustrialElectricalBoxBlock;
import com.antaurora.apofirstlight.block.SteelDoorBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IndustrialMaterialExplosionDrops {
    private IndustrialMaterialExplosionDrops() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Set<BlockPos> salvagedDoors = new HashSet<>();
        Set<BlockPos> salvagedLights = new HashSet<>();
        Set<BlockPos> salvagedBoxes = new HashSet<>();
        for (BlockPos position : event.getAffectedBlocks()) {
            Block block = level.getBlockState(position).getBlock();
            if (block == AflBlocks.STEEL_BLOCK.get()) {
                drop(level, position, AflItems.STEEL_SCRAP.get(), 2, 4);
            } else if (block == AflBlocks.STEEL_PLATE.get()) {
                drop(level, position, AflItems.STEEL_SCRAP.get(), 1, 3);
            } else if (block == AflBlocks.STEEL_GRATE.get()) {
                drop(level, position, AflItems.STEEL_SCRAP.get(), 1, 2);
            } else if (block == AflBlocks.REINFORCED_CONCRETE.get()) {
                drop(level, position, AflItems.CONCRETE_RUBBLE.get(), 2, 4);
            } else if (block == AflBlocks.STEEL_DOOR.get()) {
                if (salvagedDoors.add(SteelDoorBlock.canonicalPosition(position, level.getBlockState(position)))) {
                    drop(level, position, AflItems.STEEL_SCRAP.get(), 1, 2);
                    SteelDoorBlock.markExplosion(position, level.getBlockState(position));
                }
            }
        }
        for (BlockPos supportPosition : event.getAffectedBlocks()) {
            for (Direction facing : Direction.values()) {
                if (facing == Direction.UP) {
                    continue;
                }
                BlockPos lightPosition = supportPosition.relative(facing);
                if (level.getBlockState(lightPosition).getBlock() == AflBlocks.INDUSTRIAL_UTILITY_LIGHT.get()
                        && level.getBlockState(lightPosition).getValue(IndustrialUtilityLightBlock.FACING) == facing
                        && salvagedLights.add(lightPosition.immutable())) {
                    IndustrialUtilityLightBlock.markExplosion(lightPosition);
                    dropChance(level, lightPosition, AflItems.STEEL_SCRAP.get(), 0.50F);
                }
            }
        }
        for (BlockPos position : event.getAffectedBlocks()) {
            if (level.getBlockState(position).getBlock() == AflBlocks.INDUSTRIAL_ELECTRICAL_BOX.get()
                    && salvagedBoxes.add(position.immutable())) {
                IndustrialElectricalBoxBlock.markExplosion(position);
                drop(level, position, AflItems.STEEL_SCRAP.get(), 0, 3);
            }
            if (level.getBlockState(position).getBlock() == AflBlocks.INDUSTRIAL_UTILITY_LIGHT.get()
                    && salvagedLights.add(position.immutable())) {
                IndustrialUtilityLightBlock.markExplosion(position);
                dropChance(level, position, AflItems.STEEL_SCRAP.get(), 0.50F);
            }
        }
        for (BlockPos supportPosition : event.getAffectedBlocks()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos boxPosition = supportPosition.relative(facing);
                if (level.getBlockState(boxPosition).getBlock() == AflBlocks.INDUSTRIAL_ELECTRICAL_BOX.get()
                        && level.getBlockState(boxPosition).getValue(IndustrialElectricalBoxBlock.FACING) == facing
                        && salvagedBoxes.add(boxPosition.immutable())) {
                    IndustrialElectricalBoxBlock.markExplosion(boxPosition);
                    drop(level, boxPosition, AflItems.STEEL_SCRAP.get(), 0, 3);
                }
            }
        }
        level.getServer().execute(SteelDoorBlock::clearExplosionMarks);
        level.getServer().execute(IndustrialUtilityLightBlock::clearExplosionMarks);
        level.getServer().execute(IndustrialElectricalBoxBlock::clearExplosionMarks);
    }

    private static void drop(Level level, BlockPos position, Item item, int minimum, int maximum) {
        int count = minimum + level.getRandom().nextInt(maximum - minimum + 1);
        Block.popResource(level, position, new ItemStack(item, count));
    }

    private static void dropChance(Level level, BlockPos position, Item item, float chance) {
        if (level.getRandom().nextFloat() < chance) {
            Block.popResource(level, position, new ItemStack(item, 1));
        }
    }
}
