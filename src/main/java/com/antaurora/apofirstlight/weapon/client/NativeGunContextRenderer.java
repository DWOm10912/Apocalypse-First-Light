package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
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
    private MeshModel mesh;
    private boolean skipMeshShadow;
    /**
     * Player skeleton (docs/dev/player_skeleton_v1_design.md 13): the gun it draws in third person plays its clips (it
     * is placed against the eye as in first person, so the clips fit; the hands follow its anchors), and one bone may be
     * held at its bind pose (the draw / put-away clips' whole-gun move, which the skeleton replaces with its own path).
     */
    private static boolean skeletonAnimated;
    private static String skeletonStill;

    /** Set around one skeleton draw of a gun; {@link #skeletonDrawEnd} after. */
    public static void skeletonDraw(boolean animated, String still) {
        skeletonAnimated = animated;
        skeletonStill = still;
    }

    public static void skeletonDrawEnd() {
        skeletonAnimated = false;
        skeletonStill = null;
    }

    /** The bone held still for the skeleton's draw, or null. */
    protected static String skeletonStill() { return skeletonStill; }

    protected NativeGunContextRenderer(GeoModel<T> model, String idle) {
        super(model);
        this.idle = idle;
    }

    @Override public void actuallyRender(PoseStack pose, T item, BakedGeoModel baked, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, boolean reRender, float partial,
            int light, int overlay, float red, float green, float blue, float alpha) {
        var previousMesh = mesh;
        boolean previousSkip = skipMeshShadow;
        mesh = VextrisMeshes.byGeometry(getGeoModel().getModelResource(item));
        skipMeshShadow = mesh != null && NativeGunShadowSkip.shouldSkip(currentItemStack, renderPerspective, item);
        // first person: the eye sits at the pose origin, so the closed parts' back faces (and those of the rounds and
        // attachments drawn in this traversal) can be skipped on the CPU (docs/native_guns/hybrid_mesh_runtime_v1.md)
        boolean firstPerson = renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean previousCull = com.antaurora.vextris.client.MeshRenderer.cullBackFaces(firstPerson && !AflShaderCompat.activeShadowPass());
        try {
            boolean third = renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            if (!(item instanceof NativeGunItem) || !third || skeletonAnimated) {
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
            com.antaurora.vextris.client.MeshRenderer.cullBackFaces(previousCull);
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
