package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ServicePistolAnimationController;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer;
import com.antaurora.apofirstlight.weapon.client.ServicePistolModel;
import com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation;
import com.antaurora.apofirstlight.weapon.client.ServicePistolRenderMatrices;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.util.RenderUtils;

/** One-shot DEV verification of traced V0.4.2 values, isolated stack ownership and repeated action return. */
public final class NativeGunMatrixChecks {
    private NativeGunMatrixChecks() {}

    public static void verify(Minecraft mc,ServicePistolItem item) {
        NativeGunArmChecks.require(ServicePistolPresentation.BASE_X==.60F
                && ServicePistolPresentation.BASE_Y==-.54F && ServicePistolPresentation.BASE_Z==-.64F,
                "documented V0.4.2 camera baseline");
        var expectedDisplay=new PoseStack();
        expectedDisplay.translate(-6/16F,1/16F,0);
        expectedDisplay.scale(.3F,.3F,.3F);
        var actualDisplay=new PoseStack();
        mc.getModelManager().getModel(new ModelResourceLocation(ApocalypseFirstLight.MOD_ID,"service_pistol","inventory"))
                .applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,actualDisplay,false);
        close(expectedDisplay.last().pose(),actualDisplay.last().pose(),"V0.4.2 display matrix");

        // Simulate the former shared push/pop to audit, not merely assume, a leak.
        var input=base(actualDisplay);
        var initialMatrix=new Matrix4f(input.last().pose());
        var initialNormal=new Matrix3f(input.last().normal());
        input.pushPose();
        input.last().pose().scale(3.33F).translate(7,8,9);
        input.last().normal().scale(2);
        input.popPose();
        close(initialMatrix,input.last().pose(),"prior push/pop restores gun exactly");
        NativeGunArmChecks.require(initialNormal.equals(input.last().normal()),"prior normal scope balanced");
        var independent=ServicePistolRenderMatrices.detachedCopy(input);
        NativeGunArmChecks.require(independent.last().pose()!=input.last().pose()
                && independent.last().normal()!=input.last().normal(),"independent matrix objects");
        // Even deliberately unbalanced/direct arm edits cannot touch the weapon stack.
        independent.pushPose();
        independent.last().pose().scale(99).translate(-7,4,10);
        independent.last().normal().zero();
        close(initialMatrix,input.last().pose(),"detached hostile arm edit cannot leak");
        NativeGunArmChecks.require(initialNormal.equals(input.last().normal()) && input.clear(),"weapon normal/depth isolated");

        var model=new ServicePistolModel();
        model.getBakedModel(model.getModelResource(item));
        var processor=model.getAnimationProcessor();
        var manager=new AnimatableManager<ServicePistolItem>(item);
        double tick=0;
        int actions=0,samples=0;
        var expectedReady=weaponMatrix(base(actualDisplay),model);
        try {
            for (int cycle=0;cycle<8;cycle++) {
                for (String action:new String[]{"fire","fire","reload","fire","reload"}) {
                    manager.tryTriggerAnimation(ServicePistolItem.CONTROLLER,action);
                    // A last sampled frame can stop just before the source's zero endpoint.
                    // GeckoLib then uses its existing 5-tick bone reset. Include that
                    // natural settle period; do not reset bones between actions or
                    // change runtime timing merely to make this audit pass.
                    int steps=action.equals("reload") ? 320 : 90;
                    for (int step=0;step<=steps;step++,tick+=.1) {
                        processor.tickAnimation(item,model,manager,tick,new AnimationState<>(item,0,0,0,false),true);
                        var controller=(ServicePistolAnimationController)manager.getAnimationControllers().get(ServicePistolItem.CONTROLLER);
                        double seconds=controller.getReloadSeconds();
                        if (action.equals("fire")) NativeGunArmChecks.require(seconds<0,"fire cannot retain reload presentation");
                        var frame=base(actualDisplay);
                        ServicePistolPresentation.applyReload(frame,ServicePistolPresentation.reloadWeight(seconds));
                        var gunBefore=weaponMatrix(frame,model);
                        var root=model.getBone("weapon_root").orElseThrow();
                        var rootValues=new float[]{root.getPosX(),root.getPosY(),root.getPosZ(),root.getRotX(),root.getRotY(),root.getRotZ()};
                        for (boolean right:new boolean[]{false,true}) for (boolean slim:new boolean[]{false,true}) {
                            var anchor=ServicePistolRenderMatrices.detachedCopy(frame);
                            String anchorName=right ? "right_hand_anchor" : "left_hand_anchor";
                            for (String name:new String[]{"root","weapon_root",anchorName})
                                RenderUtils.prepMatrixForBone(anchor,model.getBone(name).orElseThrow());
                            RenderUtils.translateToPivotPoint(anchor,model.getBone(anchorName).orElseThrow());
                            var baseline=ServicePistolRenderMatrices.detachedCopy(anchor);
                            NativeGunArmChecks.legacyMapping(baseline,right,slim);
                            var arm=ServicePistolRenderMatrices.detachedCopy(anchor);
                            arm.pushPose();
                            try {
                                ServicePistolHandLayer.orientAtHandTip(arm,right,slim);
                                close(baseline.last().pose(),arm.last().pose(),"exact V0.4.2 effective hand mapping");
                            } finally { arm.popPose(); }
                            NativeGunArmChecks.require(arm.clear(),"balanced independent arm scope");
                        }
                        close(gunBefore,weaponMatrix(frame,model),"arm pass does not modify gun matrix");
                        NativeGunArmChecks.require(java.util.Arrays.equals(rootValues,new float[]{root.getPosX(),root.getPosY(),root.getPosZ(),root.getRotX(),root.getRotY(),root.getRotZ()}),"arm mapping never writes weapon_root");
                        samples++;
                    }
                    var controller=(ServicePistolAnimationController)manager.getAnimationControllers().get(ServicePistolItem.CONTROLLER);
                    NativeGunArmChecks.require(ServicePistolPresentation.reloadWeight(controller.getReloadSeconds())==0,"reload correction exactly zero after action");
                    close(expectedReady,weaponMatrix(base(actualDisplay),model),"settled action returns exact ready matrix cycle="+cycle+" action="+action);
                    actions++;
                }
            }
            ApocalypseFirstLight.LOGGER.info("[AFL MATRIX V044] PASS: V042 READY/display and hand mapping; current reload preserved; {} actions / {} samples; no root writes, matrix leak or cumulative drift; traces off by default",actions,samples);
        } finally {
            for (var bone:processor.getRegisteredBones()) {
                var initial=bone.getInitialSnapshot();
                bone.updateRotation(initial.getRotX(),initial.getRotY(),initial.getRotZ());
                bone.updatePosition(initial.getOffsetX(),initial.getOffsetY(),initial.getOffsetZ());
                bone.updateScale(initial.getScaleX(),initial.getScaleY(),initial.getScaleZ());
            }
        }
    }

    private static PoseStack base(PoseStack display) {
        var pose=new PoseStack();
        pose.translate(ServicePistolPresentation.BASE_X,ServicePistolPresentation.BASE_Y,ServicePistolPresentation.BASE_Z);
        pose.last().pose().mul(display.last().pose());
        pose.last().normal().mul(display.last().normal());
        // ItemRenderer (-.5,-.5,-.5) plus GeoItemRenderer (.5,.51,.5).
        pose.translate(0,.01,0);
        return pose;
    }

    private static Matrix4f weaponMatrix(PoseStack base,ServicePistolModel model) {
        var pose=ServicePistolRenderMatrices.detachedCopy(base);
        for (String name:new String[]{"root","weapon_root","gun"})
            RenderUtils.prepMatrixForBone(pose,model.getBone(name).orElseThrow());
        return new Matrix4f(pose.last().pose());
    }

    private static void close(Matrix4f expected,Matrix4f actual,String label) {
        float[] a=new float[16],b=new float[16]; expected.get(a); actual.get(b);
        for (int i=0;i<16;i++) NativeGunArmChecks.require(Math.abs(a[i]-b[i])<.00001F,label+" element="+i+" expected="+a[i]+" actual="+b[i]);
    }
}
