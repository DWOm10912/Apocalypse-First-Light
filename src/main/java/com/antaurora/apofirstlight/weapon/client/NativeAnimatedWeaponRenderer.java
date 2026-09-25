package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** Uses the unchanged AFL arm renderer; weapon-specific offsets belong in model resources. */
public final class NativeAnimatedWeaponRenderer<T extends net.minecraft.world.item.Item & software.bernie.geckolib.animatable.GeoItem> extends NativeGunContextRenderer<T> {
    private String actionClip(T item) {
        var manager = item.getAnimatableInstanceCache().getManagerForId(getInstanceId(item));
        var controller = manager.getAnimationControllers().get("action");
        return controller == null || controller.getTriggeredAnimation() == null
                || controller.getAnimationState() == software.bernie.geckolib.core.animation.AnimationController.State.STOPPED
                || controller.getCurrentAnimation() == null ? "" : controller.getCurrentAnimation().animation().name();
    }

    @Override public void renderRecursively(PoseStack pose,T item,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer buffer,
            boolean reRender,float partial,int light,int overlay,float red,float green,float blue,float alpha){
        if (item instanceof com.antaurora.apofirstlight.weapon.NativeGunItem gun
                && gun.definition().actionType() == com.antaurora.apofirstlight.weapon.NativeActionType.BREAK_ACTION) {
            String name = bone.getName();
            // Source-only loose ammunition is part of the unmodified artist export, never the live gun.
            if (name.equals("source_only_reference")) return;
            if (name.equals("live_shell_upper") || name.equals("live_shell_lower")
                    || name.equals("spent_shell_upper") || name.equals("spent_shell_lower")) {
                boolean authoredReload = renderPerspective != null && renderPerspective.firstPerson()
                        && actionClip(item).startsWith("reload_");
                boolean hidden = false;
                if (!authoredReload) {
                    var chambers = com.antaurora.apofirstlight.weapon.NativeBreakActionChambers.read(currentItemStack, gun.definition());
                    hidden = switch (name) {
                        case "live_shell_upper" -> chambers.upper() != com.antaurora.apofirstlight.weapon.NativeBreakActionChambers.State.LIVE;
                        case "live_shell_lower" -> chambers.lower() != com.antaurora.apofirstlight.weapon.NativeBreakActionChambers.State.LIVE;
                        case "spent_shell_upper" -> chambers.upper() != com.antaurora.apofirstlight.weapon.NativeBreakActionChambers.State.SPENT;
                        default -> chambers.lower() != com.antaurora.apofirstlight.weapon.NativeBreakActionChambers.State.SPENT;
                    };
                }
                bone.setHidden(hidden);
                bone.setChildrenHidden(hidden);
            }
        }
        if(renderPerspective!=null&&renderPerspective.firstPerson()&&!reRender)
            FieldAttachmentHotspots.capture(currentItemStack,bone,pose);
        if(NativeMagazineRendering.replaces(currentItemStack,bone)){
            pose.pushPose();RenderUtils.prepMatrixForBone(pose,bone);
            if(!bone.isHidden())NativeMagazineRendering.render(currentItemStack,bone,pose,buffers,light,overlay);
            if(!NativeMagazineRendering.replacesSubtree(currentItemStack)&&!bone.isHidingChildren())
                for(var child:bone.getChildBones())renderRecursively(pose,item,child,type,buffers,buffers.getBuffer(type),reRender,partial,light,overlay,red,green,blue,alpha);
            pose.popPose();buffers.getBuffer(type);return;
        }
        super.renderRecursively(pose,item,bone,type,buffers,buffer,reRender,partial,light,overlay,red,green,blue,alpha);
    }
    public NativeAnimatedWeaponRenderer(NativeAnimatedWeaponItem.Profile profile) {
        super(new GeoModel<>() {
            @Override public void handleAnimations(T item, long id,
                    software.bernie.geckolib.core.animation.AnimationState<T> state) {
                NativeCameraBoneConsumer.shareFrameTick(this, id, state);
                NativeChamberGasFx.bind(item, id, state);
                super.handleAnimations(item, id, state);
            }
            @Override public ResourceLocation getModelResource(T i) { return profile.resource("geo", ".geo.json"); }
            @Override public ResourceLocation getTextureResource(T i) { return profile.resource("textures/item", ".png"); }
            @Override public ResourceLocation getAnimationResource(T i) { return profile.resource("animations", ".animation.json"); }
            // Source shoot already has two orphan channels; never invent substitute geometry.
            @Override public boolean crashIfBoneMissing() { return false; }
        }, profile.idle());
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override public void renderForBone(PoseStack pose, T item, GeoBone bone,
                    RenderType type, MultiBufferSource buffers, VertexConsumer buffer, float partial, int light, int overlay) {
                if (renderPerspective == null) return;
                if (renderPerspective.firstPerson() && (bone.getName().equals("upper_chamber_fx")
                        || bone.getName().equals("lower_chamber_fx"))) {
                    var anchor = P901RenderMatrices.detachedCopy(pose);
                    RenderUtils.translateToPivotPoint(anchor, bone);
                    try { NativeChamberGasFx.anchor(getInstanceId(item), actionClip(item), bone.getName(), anchor, buffers, partial, light); }
                    finally { buffers.getBuffer(type); }
                }
                try{NativeSightRendering.render(currentItemStack,bone,pose,buffers,light,overlay);}
                finally{buffers.getBuffer(type);}
                if (item instanceof com.antaurora.apofirstlight.weapon.NativeGunItem
                        && (renderPerspective.firstPerson() || renderPerspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                            || renderPerspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                    var definition=((com.antaurora.apofirstlight.weapon.NativeGunItem)item).definition();
                    boolean hasMuzzle=!com.antaurora.apofirstlight.weapon.NativeAttachments.active(currentItemStack,
                            com.antaurora.apofirstlight.weapon.NativeAttachment.Slot.MUZZLE).isEmpty();
                    String muzzleAnchor=hasMuzzle&&definition.muzzleMount()!=null?definition.muzzleMount().anchor():profile.muzzleAnchor();
                    if (definition.actionType() == com.antaurora.apofirstlight.weapon.NativeActionType.BREAK_ACTION) {
                        int count = com.antaurora.apofirstlight.weapon.NativeGunAmmo.read(currentItemStack, definition);
                        // The accepted shot already debited ammo: while shoot plays, count 1 still means LOWER fired.
                        muzzleAnchor = count == 2 || count == 1 && actionClip(item).equals("shoot")
                                ? "muzzle_lower_anchor" : "muzzle_upper_anchor";
                    }
                    String fx = bone.getName().equals(profile.ejectionAnchor()) ? "ejection_anchor"
                            : bone.getName().equals(muzzleAnchor) ? "muzzle_anchor" : null;
                    if (fx != null) {
                        var matrix = P901RenderMatrices.detachedCopy(pose);
                        RenderUtils.translateToPivotPoint(matrix, bone);
                        boolean attachedExit=fx.equals("muzzle_anchor")&&NativeMuzzleRendering.applyExit(currentItemStack,matrix);
                        boolean suppressed=com.antaurora.apofirstlight.weapon.NativeGunNoise.resolve(currentItemStack,
                                ((com.antaurora.apofirstlight.weapon.NativeGunItem)item).definition()).suppressed();
                        try { NativeGunFx.anchor(getInstanceId(item), renderPerspective.firstPerson(), fx, matrix, buffers, partial,
                                new ResourceLocation(((com.antaurora.apofirstlight.weapon.NativeGunItem)item).definition().casing().getNamespace(),
                                        "item/"+((com.antaurora.apofirstlight.weapon.NativeGunItem)item).definition().casing().getPath()),
                                attachedExit?0:profile.barrelExitOffset(),suppressed); }
                        finally { buffers.getBuffer(type); }
                    }
                }
                if (!renderPerspective.firstPerson()) return;
                boolean right = bone.getName().equals(profile.rightAnchor());
                if (!right && !bone.getName().equals(profile.leftAnchor())) return;
                PoseStack anchor = P901RenderMatrices.detachedCopy(pose);
                RenderUtils.translateToPivotPoint(anchor, bone);
                try { NativePlayerArmRenderer.render(anchor, right, buffers, light, overlay); }
                finally { buffers.getBuffer(type); }
            }
        });
    }
}
