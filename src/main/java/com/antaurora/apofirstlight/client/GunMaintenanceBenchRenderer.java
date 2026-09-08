package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

public final class GunMaintenanceBenchRenderer implements BlockEntityRenderer<GunMaintenanceBenchBlockEntity> {
    public GunMaintenanceBenchRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(GunMaintenanceBenchBlockEntity bench,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(bench.isEmpty())return;
        pose.pushPose();
        var facing=bench.getBlockState().getValue(StaticWorkstationBlock.FACING);
        MaintenanceCameraController.benchTransform(pose,facing);
        MaintenanceGunRendering.render(bench.getItem(0),pose,buffers,light);pose.popPose();
    }
}
