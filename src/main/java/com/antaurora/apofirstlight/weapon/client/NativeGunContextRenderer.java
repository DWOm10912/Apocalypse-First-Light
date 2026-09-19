package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Shared context policy for both configured guns and the original pistol renderer. */
public class NativeGunContextRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    private final NativeThirdPersonPose thirdPerson = new NativeThirdPersonPose();
    private final String idle;

    protected NativeGunContextRenderer(GeoModel<T> model, String idle) {
        super(model);
        this.idle = idle;
    }

    @Override public void actuallyRender(PoseStack pose, T item, BakedGeoModel baked, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, boolean reRender, float partial,
            int light, int overlay, float red, float green, float blue, float alpha) {
        boolean third = renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!(item instanceof NativeGunItem) || !third) {
            super.actuallyRender(pose,item,baked,type,buffers,buffer,reRender,partial,light,overlay,red,green,blue,alpha);
            return;
        }
        var frozen = thirdPerson.resolve(baked, getGeoModel().getAnimation(item, idle));
        // Equivalent geometry traversal without GeoItemRenderer's handleAnimations call.
        // Preserve the real reRender flag: forcing it true would also suppress sight/muzzle/FX layers.
        modelRenderTranslations = new Matrix4f(pose.last().pose());
        updateAnimatedTextureFrame(item);
        for (var bone : frozen.topLevelBones())
            renderRecursively(pose,item,bone,type,buffers,buffer,reRender,partial,light,overlay,red,green,blue,alpha);
    }
}
