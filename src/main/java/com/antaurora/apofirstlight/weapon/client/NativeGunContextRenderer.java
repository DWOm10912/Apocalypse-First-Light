package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Shared context policy for both configured guns and the original pistol renderer. */
public class NativeGunContextRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    private final NativeThirdPersonPose thirdPerson = new NativeThirdPersonPose();
    private final String idle;
    private AflMeshModel mesh;
    private boolean skipMeshShadow;

    protected NativeGunContextRenderer(GeoModel<T> model, String idle) {
        super(model);
        this.idle = idle;
    }

    @Override public void actuallyRender(PoseStack pose, T item, BakedGeoModel baked, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, boolean reRender, float partial,
            int light, int overlay, float red, float green, float blue, float alpha) {
        var previousMesh = mesh;
        boolean previousSkip = skipMeshShadow;
        mesh = AflMeshCache.snapshot().get(getGeoModel().getModelResource(item));
        skipMeshShadow = mesh != null && NativeGunShadowSkip.shouldSkip(currentItemStack, renderPerspective, item);
        // first person: the eye sits at the pose origin, so the closed parts' back faces (and those of the rounds and
        // attachments drawn in this traversal) can be skipped on the CPU (docs/native_guns/hybrid_mesh_runtime_v1.md)
        boolean firstPerson = renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean previousCull = com.antaurora.apofirstlight.client.mesh.AflMeshRenderer.cullBackFaces(firstPerson && !AflShaderCompat.activeShadowPass());
        try {
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
        } finally {
            mesh = previousMesh;
            skipMeshShadow = previousSkip;
            com.antaurora.apofirstlight.client.mesh.AflMeshRenderer.cullBackFaces(previousCull);
        }
    }

    @Override public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer,
            int light, int overlay, float red, float green, float blue, float alpha) {
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, red, green, blue, alpha);
        // This per-bone geometry hook also runs on reRender. GeoRenderLayers do not.
        // Parent hiding and NativeAnimatedWeaponRenderer's replacement/early-return policy
        // already control entry here. The backend checks the bone's own hidden flag.
        if (mesh != null && !skipMeshShadow) NativeGunRenderProfile.render(mesh, bone, pose, buffer, light, overlay,
                red, green, blue, alpha, currentItemStack, renderPerspective);
    }
}
