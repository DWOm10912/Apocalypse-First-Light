package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflStaticMeshItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class BeverageCoolerBlockItem extends BlockItem {
    public BeverageCoolerBlockItem(BeverageCoolerBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((BeverageCoolerBlock) getBlock()).placeStructure(context, state);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                // Beverage Cooler V2 mesh and atlas, doors closed; the item model's views are centred by the generator
                if (renderer == null) renderer = new AflStaticMeshItemRenderer(
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/beverage_cooler.geo.json"),
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/beverage_cooler.png"), 0.0, 0.0);
                return renderer;
            }
        });
    }
}
