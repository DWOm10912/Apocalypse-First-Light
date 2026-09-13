package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

public final class VendingMachineBlockItem extends BlockItem {
    private static final String BROKEN_GLASS_TAG = "AflBrokenGlass";
    public VendingMachineBlockItem(Block block,Properties properties) { super(block,properties); }
    public static boolean hasBrokenGlass(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(BROKEN_GLASS_TAG);
    }
    public static void setBrokenGlass(ItemStack stack, boolean broken) {
        if (broken) stack.getOrCreateTag().putBoolean(BROKEN_GLASS_TAG,true);
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if(renderer==null) renderer=new BlockEntityWithoutLevelRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()) {
                    private final VendingMachineBlockEntity intact=new VendingMachineBlockEntity(BlockPos.ZERO,AflBlocks.VENDING_MACHINE.get().defaultBlockState());
                    private final VendingMachineBlockEntity broken=new VendingMachineBlockEntity(BlockPos.ZERO,
                            AflBlocks.VENDING_MACHINE.get().defaultBlockState().setValue(VendingMachineBlock.BROKEN,true));
                    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,
                            net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay) {
                        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(
                                hasBrokenGlass(stack)?broken:intact,pose,buffers,light,overlay);
                    }
                };
                return renderer;
            }
        });
    }
}
