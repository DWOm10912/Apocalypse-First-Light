package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflStaticMeshItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * A block item drawn with its block's Pure Mesh (geo/&lt;id&gt;.geo.json) and atlas (textures/block/&lt;id&gt;.png) through the AFL
 * Mesh item renderer; the item model's views come from the block's generator. Used by the Building Power V1 blocks.
 */
public class AflMeshBlockItem extends BlockItem {
    private final String meshId;

    public AflMeshBlockItem(Block block, Properties properties, String meshId) {
        super(block, properties);
        this.meshId = meshId;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new AflStaticMeshItemRenderer(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + meshId + ".geo.json"),
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/" + meshId + ".png"), 0.0, 0.0);
                return renderer;
            }
        });
    }
}
