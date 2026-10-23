package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.vextris.client.MeshGpuCache;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshLayer;
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
import software.bernie.geckolib.util.RenderUtils;

/** Shared context policy for both configured guns and the original pistol renderer. */
public class NativeGunContextRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    private final NativeThirdPersonPose thirdPerson = new NativeThirdPersonPose();
    private final String idle;
    private MeshModel mesh;
    private boolean skipMeshShadow;
    /** A fitted sling's strap (client/sling/SlingStraps): each drawn bone's mesh frame this traversal, null without one. */
    private java.util.Map<String, Matrix4f> strapBones;
    /**
     * Player skeleton (docs/dev/player_skeleton_v1_design.md 13): the gun it draws in third person plays its clips (it
     * is placed against the eye as in first person, so the clips fit; the hands follow its anchors), and one bone may be
     * held at its bind pose (the draw / put-away clips' whole-gun move, which the skeleton replaces with its own path).
     */
    private static boolean skeletonAnimated;
    private static String skeletonStill;
    private static java.util.Map<String, org.joml.Matrix4f> skeletonParts = java.util.Map.of();

    /** Set around one skeleton draw of a gun; {@link #skeletonDrawEnd} after. */
    public static void skeletonDraw(boolean animated, String still) {
        skeletonDraw(animated, still, java.util.Map.of());
    }

    /**
     * As {@link #skeletonDraw(boolean, String)}, with bones the skeleton's action places itself (docs/dev/p9_reload_inspect_v1.md
     * 3: a reload's magazines as props): bone name to its frame in the gun's GeckoLib frame (blocks, about the root's
     * origin), drawn there at its bind pose with its subtree unscaled; null hides the bone and its subtree.
     */
    public static void skeletonDraw(boolean animated, String still, java.util.Map<String, org.joml.Matrix4f> parts) {
        skeletonAnimated = animated;
        skeletonStill = still;
        skeletonParts = parts;
    }

    public static void skeletonDrawEnd() {
        skeletonAnimated = false;
        skeletonStill = null;
        skeletonParts = java.util.Map.of();
    }

    /** The bone held still for the skeleton's draw, or null. */
    protected static String skeletonStill() { return skeletonStill; }

    /** The bones the skeleton's action places itself (#skeletonDraw), empty when none. */
    protected static java.util.Map<String, org.joml.Matrix4f> skeletonParts() { return skeletonParts; }

    protected NativeGunContextRenderer(GeoModel<T> model, String idle) {
        super(model);
        this.idle = idle;
    }

    @Override public void actuallyRender(PoseStack pose, T item, BakedGeoModel baked, RenderType type,
            MultiBufferSource buffers, VertexConsumer buffer, boolean reRender, float partial,
            int light, int overlay, float red, float green, float blue, float alpha) {
        var previousMesh = mesh;
        boolean previousSkip = skipMeshShadow;
        var previousStrap = strapBones;
        mesh = VextrisMeshes.byGeometry(getGeoModel().getModelResource(item));
        skipMeshShadow = mesh != null && NativeGunShadowSkip.shouldSkip(currentItemStack, renderPerspective, item);
        // first person: the eye sits at the pose origin, so the closed parts' back faces (and those of the rounds and
        // attachments drawn in this traversal) can be skipped on the CPU (docs/native_guns/hybrid_mesh_runtime_v1.md)
        boolean firstPerson = renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean third = renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean strap = !reRender && mesh != null && (third || firstPerson && !"HAND_TRANSLUCENT".equals(AflShaderCompat.handPhase()))
                && currentItemStack != null && !com.antaurora.apofirstlight.weapon.NativeAttachments.active(currentItemStack,
                com.antaurora.apofirstlight.weapon.NativeAttachment.Slot.SLING).isEmpty();
        strapBones = strap ? new java.util.HashMap<>() : null;
        Matrix4f root = new Matrix4f(pose.last().pose());
        boolean previousCull = com.antaurora.vextris.client.MeshRenderer.cullBackFaces(firstPerson && !AflShaderCompat.activeShadowPass());
        try {
            if (!(item instanceof NativeGunItem) || !third || skeletonAnimated) {
                super.actuallyRender(pose,item,baked,type,buffers,buffer,reRender,partial,light,overlay,red,green,blue,alpha);
            } else {
                var frozen = thirdPerson.resolve(baked, getGeoModel().getAnimation(item, idle));
                // Equivalent geometry traversal without GeoItemRenderer's handleAnimations call.
                // Preserve the real reRender flag: forcing it true would also suppress sight/muzzle/FX layers.
                modelRenderTranslations = new Matrix4f(pose.last().pose());
                updateAnimatedTextureFrame(item);
                for (var bone : frozen.topLevelBones())
                    renderRecursively(pose,item,bone,type,buffers,buffer,reRender,partial,light,overlay,red,green,blue,alpha);
            }
            if (strap && firstPerson)
                com.antaurora.apofirstlight.client.sling.SlingStraps.firstPerson(currentItemStack, mesh, root, strapBones, buffers, light);
            else if (strap)
                com.antaurora.apofirstlight.client.sling.SlingStraps.thirdPerson(currentItemStack, mesh, root, strapBones);
        } finally {
            mesh = previousMesh;
            skipMeshShadow = previousSkip;
            strapBones = previousStrap;
            com.antaurora.vextris.client.MeshRenderer.cullBackFaces(previousCull);
        }
    }

    /** A drawn bone's mesh frame for the sling's colliders (pose: the bone's matrix, before its pivot). */
    protected void strapBone(PoseStack pose, GeoBone bone) {
        if (strapBones == null) return;
        pose.pushPose();
        RenderUtils.translateToPivotPoint(pose, bone);
        strapBones.put(bone.getName(), new Matrix4f(pose.last().pose()));
        pose.popPose();
    }

    @Override public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer,
            int light, int overlay, float red, float green, float blue, float alpha) {
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, red, green, blue, alpha);
        strapBone(pose, bone);
        // This per-bone geometry hook also runs on reRender. GeoRenderLayers do not.
        // Parent hiding and NativeAnimatedWeaponRenderer's replacement/early-return policy
        // already control entry here. The backend checks the bone's own hidden flag.
        if (mesh != null && !skipMeshShadow && !drawCached(pose, bone, light, overlay, red, green, blue, alpha))
            NativeGunRenderProfile.render(mesh, bone, pose, buffer, light, overlay, red, green, blue, alpha, currentItemStack, renderPerspective);
    }

    /**
     * P9 V3 game model (docs/native_guns/p9_01_v3_game_model_v1.md 5): the gun mesh is drawn from the Vextris GPU cache in
     * first and third person. Under a shader pack the cache keeps a format 3 mesh's smooth parts as TRIANGLES, while
     * GeckoLib's QUADS consumer would let Oculus replace their normals per face (the curved parts would look faceted against
     * their baked normal map). It also stops resubmitting the mesh vertex by vertex every frame: since 2026-10-17 every
     * format (the BR-51's format 1 / 2 mesh in hand cost about 2 ms a frame on the CPU path, JFR: the frame's largest CPU
     * part in rain). Other display contexts, Oculus' translucent hand phase and -Dvextris.gpuCache=false keep the CPU path.
     * Returns whether the bone's parts are drawn.
     */
    private boolean drawCached(PoseStack pose, GeoBone bone, int light, int overlay, float red, float green, float blue, float alpha) {
        if (!MeshGpuCache.enabled() || renderPerspective == null || animatable == null) return false;
        boolean firstPerson = renderPerspective.firstPerson();
        boolean thirdPerson = renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!firstPerson && !thirdPerson) return false;
        if (firstPerson && "HAND_TRANSLUCENT".equals(AflShaderCompat.handPhase())) return false;
        if (bone.isHidden() || mesh.parts(bone.getName(), MeshLayer.CUTOUT).isEmpty()) return true;
        pose.pushPose();
        try {
            RenderUtils.translateToPivotPoint(pose, bone);
            return MeshGpuCache.drawBone(mesh, bone.getName(), MeshLayer.CUTOUT, MeshGpuCache.ALL_PARTS,
                    getGeoModel().getTextureResource(animatable), pose, light, overlay, red, green, blue, alpha);
        } finally {
            pose.popPose();
        }
    }
}
