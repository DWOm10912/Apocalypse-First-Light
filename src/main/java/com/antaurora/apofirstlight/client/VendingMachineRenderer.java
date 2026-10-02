package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
import com.antaurora.apofirstlight.client.goods.AflGoodsLibrary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Body, then the goods behind the glass (2026-10-01: the shared goods library, one product per lane that shows, at 0.85
 * of the nominal cell: a lane is 3.6 px apart and 4.3 px clear), then the intact or broken glass (translucent, last).
 */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class VendingMachineRenderer implements BlockEntityRenderer<VendingMachineBlockEntity> {
    private static final float GOODS_SCALE = 0.85F;
    private static final AflGoodsLibrary.CellPosition LANES = lane -> new double[]{
            VendingMachineBlockEntity.laneX(lane), VendingMachineBlockEntity.laneY(lane), VendingMachineBlockEntity.LANE_FRONT_Z};
    private static ResourceLocation model(String part) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID,"block/vending_machine_"+part); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for (String part : new String[]{"body","intact_glass","broken_glass"}) event.register(model(part));
    }
    public VendingMachineRenderer(BlockEntityRendererProvider.Context context) {}
    private static void draw(String part, VendingMachineBlockEntity be, PoseStack pose, MultiBufferSource buffers, RenderType type,int light,int overlay) {
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(type),be.getBlockState(),
                mc.getModelManager().getModel(model(part)),1,1,1,light,overlay);
    }
    @Override public void render(VendingMachineBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();pose.translate(.5,0,.5);
        float angle=switch(be.getBlockState().getValue(VendingMachineBlock.FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle));pose.translate(-.5,0,-.5);
        draw("body",be,pose,buffers,RenderType.cutout(),light,overlay);
        AflGoodsLibrary.draw(be.shownGoods(),LANES,GOODS_SCALE,pose,buffers,light,overlay);
        draw(be.getBlockState().getValue(VendingMachineBlock.BROKEN)?"broken_glass":"intact_glass",be,pose,buffers,RenderType.translucent(),light,overlay);
        pose.popPose();
    }
}
