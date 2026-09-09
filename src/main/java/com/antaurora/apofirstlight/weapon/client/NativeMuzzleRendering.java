package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtils;

/** Independent immutable accessory rig; shared by held guns, world items and maintenance. */
public final class NativeMuzzleRendering implements GeoRenderer<GeoItem> {
    private static final NativeMuzzleRendering DRAWER=new NativeMuzzleRendering();
    private static ResourceLocation resource(ItemStack item,String prefix,String suffix){
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        return new ResourceLocation(id.getNamespace(),prefix+id.getPath()+suffix);
    }
    private static BakedGeoModel model(ItemStack item){
        return item.isEmpty()?null:GeckoLibCache.getBakedModels().get(resource(item,"geo/",".geo.json"));
    }
    public static void render(ItemStack gun,GeoBone anchor,PoseStack incoming,MultiBufferSource buffers,int light,int overlay){
        if(gun==null||!(gun.getItem() instanceof NativeGunItem g))return;
        var mount=g.definition().muzzleMount();var item=NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE);
        if(mount==null||item.isEmpty()||!anchor.getName().equals(mount.anchor()))return;
        var pose=P901RenderMatrices.detachedCopy(incoming);
        RenderUtils.translateToPivotPoint(pose,anchor);
        drawItem(item,pose,buffers,light,overlay);
    }
    public static void drawItem(ItemStack item,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var model=model(item);if(model==null)return;
        var type=RenderType.entityCutoutNoCull(resource(item,"textures/item/",".png"));
        for(var bone:model.topLevelBones())draw(bone,pose,buffers,type,light,overlay);
    }
    private static void draw(GeoBone bone,PoseStack pose,MultiBufferSource buffers,RenderType type,int light,int overlay){
        // Opt-in emissive locator group shared by independent Geo sights; never brighten the housing.
        if(bone.getName().equals("reticle"))light=LightTexture.FULL_BRIGHT;
        pose.pushPose();RenderUtils.prepMatrixForBone(pose,bone);
        for(var cube:bone.getCubes()){
            pose.pushPose();DRAWER.renderCube(pose,cube,buffers.getBuffer(type),light,overlay,1,1,1,1);pose.popPose();
        }
        for(var child:bone.getChildBones())draw(child,pose,buffers,type,light,overlay);
        pose.popPose();
    }
    /** Compose the actual accessory locator, never substitute a P9-specific length. */
    public static boolean applyExit(ItemStack gun,PoseStack pose){
        var model=model(NativeAttachments.active(gun,NativeAttachment.Slot.MUZZLE));
        if(model==null)return false;
        for(var bone:model.topLevelBones())if(exit(bone,pose))return true;
        return false;
    }
    private static boolean exit(GeoBone bone,PoseStack pose){
        pose.pushPose();RenderUtils.prepMatrixForBone(pose,bone);
        if(bone.getName().equals("muzzle_exit_anchor")){
            RenderUtils.translateToPivotPoint(pose,bone);return true;
        }
        for(var child:bone.getChildBones())if(exit(child,pose))return true;
        pose.popPose();return false;
    }
    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        public ItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
        @Override public void renderByItem(ItemStack item,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            pose.pushPose();pose.translate(.5,.5,.5);
            // Origin is the rear mount; center standalone presentation on the authored exit.
            var exit=new PoseStack();
            var model=model(item);
            if(model!=null)for(var bone:model.topLevelBones())if(exit(bone,exit)){
                var v=exit.last().pose().transformPosition(new org.joml.Vector3f());
                pose.translate(-v.x/2,-v.y/2,-v.z/2);break;
            }
            drawItem(item,pose,buffers,light,overlay);pose.popPose();
        }
    }
    @Override public GeoModel<GeoItem> getGeoModel(){return null;}
    @Override public GeoItem getAnimatable(){return null;}
    @Override public void fireCompileRenderLayersEvent(){}
    @Override public boolean firePreRenderEvent(PoseStack p,BakedGeoModel m,MultiBufferSource b,float t,int l){return true;}
    @Override public void firePostRenderEvent(PoseStack p,BakedGeoModel m,MultiBufferSource b,float t,int l){}
    @Override public void updateAnimatedTextureFrame(GeoItem item){}
}
