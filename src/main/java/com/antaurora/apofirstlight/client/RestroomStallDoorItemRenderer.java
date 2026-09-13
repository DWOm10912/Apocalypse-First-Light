package com.antaurora.apofirstlight.client;
import com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
public final class RestroomStallDoorItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final RestroomStallDoorBlockEntity door=new RestroomStallDoorBlockEntity(BlockPos.ZERO,AflBlocks.RESTROOM_STALL_DOOR.get().defaultBlockState());
    public RestroomStallDoorItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack s,ItemDisplayContext c,PoseStack p,MultiBufferSource b,int l,int o){Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(door,p,b,l,o);}
}
