package com.antaurora.apofirstlight.item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import java.util.function.Consumer;
public final class RestroomStallDoorBlockItem extends BlockItem {
    public RestroomStallDoorBlockItem(Block b,Properties p){super(b,p);}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(new IClientItemExtensions(){
        private BlockEntityWithoutLevelRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){
            if(renderer==null)renderer=new com.antaurora.apofirstlight.client.RestroomStallDoorItemRenderer();return renderer;
        }
    });}
}
