package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.client.ChestFreezerItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class ChestFreezerBlockItem extends BlockItem {
    public ChestFreezerBlockItem(ChestFreezerBlock block, Properties properties) { super(block, properties); }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((ChestFreezerBlock) getBlock()).placeStructure(context, state);
    }

    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ChestFreezerItemRenderer();
                return renderer;
            }
        });
    }
}
