package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflStaticMeshItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class LeadChestBlockItem extends BlockItem {
    public LeadChestBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                // Lead Chest V3 Pure Mesh (closed pose), same geo / sidecar / atlas as the block
                if (renderer == null) renderer = new AflStaticMeshItemRenderer(
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/lead_chest.geo.json"),
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/lead_chest.png"), 0.0, 0.0);
                return renderer;
            }
        });
    }
}
