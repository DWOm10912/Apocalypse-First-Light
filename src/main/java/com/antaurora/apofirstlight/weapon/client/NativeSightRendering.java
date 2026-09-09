package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** Reusable consumer of the actual animated anchor traversal, for all item contexts. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class NativeSightRendering {
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){
        e.register(new ResourceLocation("apocalypse_firstlight","item/pistol_red_dot_body"));
        e.register(new ResourceLocation("apocalypse_firstlight","item/pistol_red_dot_reticle"));
    }
    public static void render(ItemStack stack,GeoBone bone,PoseStack incoming,MultiBufferSource buffers,int light,int overlay){
        NativeMuzzleRendering.render(stack,bone,incoming,buffers,light,overlay);
        if(stack==null||!(stack.getItem() instanceof NativeGunItem gun))return;
        var mount=gun.definition().sightMount();var sight=NativeAttachments.activeSight(stack);
        if(mount==null||sight.isEmpty()||!bone.getName().equals(mount.anchor()))return;
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(sight.getItem());
        var pose=P901RenderMatrices.detachedCopy(incoming);
        RenderUtils.translateToPivotPoint(pose,bone);
        // Gecko has already converted source X into its traversal convention.
        // Runtime traversal is restored to model-render X by RenderUtils.
        pose.translate(mount.x()/16F,mount.y()/16F,mount.z()/16F);
        if(sight.getItem() instanceof NativeSightItem item&&item.usesGeoModel()){
            NativeMuzzleRendering.drawItem(sight,pose,buffers,light,overlay);
            return;
        }
        pose.translate(-.5,-.5,-.5); // Cubes are authored around (8,8,8) in item JSON.
        draw(pose,buffers,new ResourceLocation(id.getNamespace(),"item/"+id.getPath()+"_body"),light,overlay);
        draw(pose,buffers,new ResourceLocation(id.getNamespace(),"item/"+id.getPath()+"_reticle"),LightTexture.FULL_BRIGHT,overlay);
    }
    private static void draw(PoseStack pose,MultiBufferSource buffers,ResourceLocation id,int light,int overlay){
        var model=Minecraft.getInstance().getModelManager().getModel(id);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        var random=net.minecraft.util.RandomSource.create(0);
        for(var side:net.minecraft.core.Direction.values()){
            random.setSeed(0);for(var q:model.getQuads(null,side,random))out.putBulkData(pose.last(),q,1,1,1,light,overlay);
        }
        random.setSeed(0);for(var q:model.getQuads(null,null,random))out.putBulkData(pose.last(),q,1,1,1,light,overlay);
    }
    private NativeSightRendering(){}
}
