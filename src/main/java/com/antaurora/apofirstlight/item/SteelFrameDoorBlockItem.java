package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.client.SteelFrameDoorItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Steel-frame doors V1 item: places both halves (as vanilla door items) and draws the door's Pure Mesh. */
public class SteelFrameDoorBlockItem extends DoubleHighBlockItem {
    private final String meshId;

    public SteelFrameDoorBlockItem(Block block, Properties properties, String meshId) {
        super(block, properties);
        this.meshId = meshId;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = SteelFrameDoorItemRenderer.create(meshId);
                return renderer;
            }
        });
    }
}
