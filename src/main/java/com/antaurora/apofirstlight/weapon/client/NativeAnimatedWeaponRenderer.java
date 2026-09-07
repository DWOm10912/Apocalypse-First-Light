package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

/** Uses the unchanged AFL arm renderer; weapon-specific offsets belong in model resources. */
public final class NativeAnimatedWeaponRenderer<T extends net.minecraft.world.item.Item & software.bernie.geckolib.animatable.GeoItem> extends GeoItemRenderer<T> {
    public NativeAnimatedWeaponRenderer(NativeAnimatedWeaponItem.Profile profile) {
        super(new GeoModel<>() {
            @Override public ResourceLocation getModelResource(T i) { return profile.resource("geo", ".geo.json"); }
            @Override public ResourceLocation getTextureResource(T i) { return profile.resource("textures/item", ".png"); }
            @Override public ResourceLocation getAnimationResource(T i) { return profile.resource("animations", ".animation.json"); }
            // Source shoot already has two orphan channels; never invent substitute geometry.
            @Override public boolean crashIfBoneMissing() { return false; }
        });
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override public void renderForBone(PoseStack pose, T item, GeoBone bone,
                    RenderType type, MultiBufferSource buffers, VertexConsumer buffer, float partial, int light, int overlay) {
                if (renderPerspective == null) return;
                if (item instanceof com.antaurora.apofirstlight.weapon.NativeGunItem
                        && (renderPerspective.firstPerson() || renderPerspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                            || renderPerspective == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                    String fx = bone.getName().equals(profile.ejectionAnchor()) ? "ejection_anchor"
                            : bone.getName().equals(profile.muzzleAnchor()) ? "muzzle_anchor" : null;
                    if (fx != null) {
                        var matrix = P901RenderMatrices.detachedCopy(pose);
                        RenderUtils.translateToPivotPoint(matrix, bone);
                        try { NativeGunFx.anchor(getInstanceId(item), renderPerspective.firstPerson(), fx, matrix, buffers, partial,
                                profile.id().equals("br51_01") ? NativeGunFx.RIFLE_CASING_MODEL : NativeGunFx.CASING_MODEL); }
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
