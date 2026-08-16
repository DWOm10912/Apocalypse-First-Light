package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.SteelDoorBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
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
        level.getServer().execute(SteelDoorBlock::clearExplosionMarks);
    }

    private static void drop(Level level, BlockPos position, Item item, int minimum, int maximum) {
        int count = minimum + level.getRandom().nextInt(maximum - minimum + 1);
        Block.popResource(level, position, new ItemStack(item, count));
    }
}
