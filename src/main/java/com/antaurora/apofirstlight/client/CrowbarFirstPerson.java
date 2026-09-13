package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.interaction.CrowbarSmashTimeline;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import com.antaurora.apofirstlight.weapon.client.P901RenderMatrices;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.*;

/** Independent FP object: source-authored smash_glass + Native's canonical skinned right-arm locator. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class CrowbarFirstPerson {
    private static final ViewModel VIEW=new ViewModel();
    private static Renderer renderer;
    private static ResourceLocation resource(String path){return new ResourceLocation("apocalypse_firstlight",path);}
    private static final class ViewModel implements GeoAnimatable {
        final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
        double ticks;boolean right;
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
        public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
        public double getTick(Object context){return ticks;}
    }
    private static final class Model extends GeoModel<ViewModel> {
        public ResourceLocation getModelResource(ViewModel v){return resource("geo/crowbar_first_person.geo.json");}
        public ResourceLocation getTextureResource(ViewModel v){return resource("textures/item/crowbar.png");}
        public ResourceLocation getAnimationResource(ViewModel v){return resource("animations/crowbar_first_person.animation.json");}
        @Override public void handleAnimations(ViewModel v,long id,AnimationState<ViewModel> state){
            // Evaluate the authored linear tracks at the sound's clock, even if the first render was delayed.
            // No Gecko wall-clock trigger, gun controller, recoil state, or second animation timer.
            for(String name:new String[]{"smash_motion","right_hand_anchor"}){
                var bone=getAnimationProcessor().getBone(name);if(bone==null)continue;
                var p=CrowbarSmashTimeline.sample(name,"position",v.ticks);
                var r=CrowbarSmashTimeline.sample(name,"rotation",v.ticks);
                bone.setPosX(p[0]);bone.setPosY(p[1]);bone.setPosZ(p[2]);
                bone.setRotX((float)Math.toRadians(-r[0]));bone.setRotY((float)Math.toRadians(-r[1]));bone.setRotZ((float)Math.toRadians(r[2]));
            }
        }
    }
    private static final class Renderer extends GeoObjectRenderer<ViewModel> {
        Renderer(){super(new Model());addRenderLayer(new GeoRenderLayer<>(this){
            @Override public void renderForBone(PoseStack pose,ViewModel v,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer buffer,float partial,int light,int overlay){
                if(!bone.getName().equals("right_hand_anchor"))return;
                var locator=P901RenderMatrices.detachedCopy(pose);RenderUtils.translateToPivotPoint(locator,bone);
                try{NativePlayerArmRenderer.renderFullSize(locator,v.right,buffers,light,overlay);}finally{buffers.getBuffer(type);}
            }
        });}
        @Override public void preRender(PoseStack pose,ViewModel v,BakedGeoModel model,MultiBufferSource buffers,VertexConsumer buffer,boolean reRender,float partial,int light,int overlay,float r,float g,float b,float a){
            super.preRender(pose,v,model,buffers,buffer,reRender,partial,light,overlay,r,g,b,a);
            pose.translate(-.5,-.51,-.5); // GeoObject's world-object centering is not a camera-space rig offset.
        }
    }
    @SubscribeEvent public static void render(RenderHandEvent e){
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||!player.getMainHandItem().is(AflItems.CROWBAR.get()))return;
        if(e.getHand()!=InteractionHand.MAIN_HAND){if(CrowbarSmashClient.active())e.setCanceled(true);return;}
        if(player.isSpectator()||player.isScoping()||player.isSleeping())return;
        e.setCanceled(true);
        if(renderer==null)renderer=new Renderer();
        var pose=P901RenderMatrices.detachedCopy(e.getPoseStack());
        VIEW.right=player.getMainArm()==HumanoidArm.RIGHT;
        if(!VIEW.right)pose.scale(-1,1,1);
        VIEW.ticks=CrowbarSmashClient.elapsed(e.getPartialTick());
        if(!CrowbarSmashClient.active()){
            // Ordinary melee retains its own swing and gameplay; the special interaction never reads this.
            float swing=(float)Math.sin(Math.sqrt(e.getSwingProgress())*Math.PI);
            pose.translate(-.12*swing,0,-.16*swing);pose.mulPose(Axis.XP.rotationDegrees(-22*swing));
            pose.translate(0,-.25*e.getEquipProgress(),0);
        }
        var type=RenderType.entityCutoutNoCull(resource("textures/item/crowbar.png"));
        renderer.render(pose,VIEW,e.getMultiBufferSource(),type,e.getMultiBufferSource().getBuffer(type),e.getPackedLight());
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();
        if(!CrowbarSmashClient.active()||!mc.options.getCameraType().isFirstPerson()||e.getCamera().getEntity()!=mc.player)return;
        double age=CrowbarSmashClient.elapsed((float)e.getPartialTick())-CrowbarSmashTimeline.IMPACT;
        if(age>=0&&age<3){float kick=(float)(Math.sin(age/3*Math.PI)*(1-age/3));e.setPitch(e.getPitch()+kick*.8f);e.setRoll(e.getRoll()+kick*.25f);}
    }
    private CrowbarFirstPerson(){}
}
