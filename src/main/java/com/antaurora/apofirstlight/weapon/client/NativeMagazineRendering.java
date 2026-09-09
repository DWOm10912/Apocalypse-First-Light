package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.*;
import net.minecraft.client.renderer.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** Uses the original animated bone matrix; the independent accessory is authored around its pivot. */
public final class NativeMagazineRendering {
    public static boolean replaces(ItemStack gun,GeoBone bone){
        return gun!=null&&(bone.getName().equals("magazine")||bone.getName().equals("empty_old_magazine"))
                &&!NativeAttachments.active(gun,NativeAttachment.Slot.MAGAZINE).isEmpty();
    }
    public static void render(ItemStack gun,GeoBone bone,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!replaces(gun,bone))return;
        pose.pushPose();RenderUtils.translateToPivotPoint(pose,bone);
        NativeMuzzleRendering.drawItem(NativeAttachments.active(gun,NativeAttachment.Slot.MAGAZINE),pose,buffers,light,overlay);
        pose.popPose();
    }
    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        public ItemRenderer(){super(net.minecraft.client.Minecraft.getInstance().getBlockEntityRenderDispatcher(),net.minecraft.client.Minecraft.getInstance().getEntityModels());}
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            pose.pushPose();pose.translate(.5,.5+4.2/16,.5);
            NativeMuzzleRendering.drawItem(stack,pose,buffers,light,overlay);pose.popPose();
        }
    }
    private NativeMagazineRendering(){}
}
