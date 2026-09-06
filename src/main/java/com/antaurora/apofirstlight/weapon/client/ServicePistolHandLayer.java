package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** V0.4.2 reference hand mapping, isolated from all weapon and camera matrix ownership. */
public final class ServicePistolHandLayer extends GeoRenderLayer<ServicePistolItem> {
    // Exact V0.4.2 effective arm scale, now explicit and independent of gun scale.
    // Temporary accepted smaller baseline; further arm calibration is a separate task.
    public static final float ARM_VISUAL_SCALE = 0.30F;
    private final ServicePistolRenderer pistolRenderer;
    // Restored verbatim from the documented V0.4.2 mapping, not newly tuned directions.
    private static final Quaternionf RIGHT_ORIENTATION = new Quaternionf().rotationTo(
            new Vector3f(0, -1, 0), new Vector3f(0.24F, -0.69F, 0.69F).normalize());
    private static final Quaternionf LEFT_ORIENTATION = new Quaternionf().rotationTo(
            new Vector3f(0, -1, 0), new Vector3f(-0.24F, -0.87F, 0.44F).normalize());

    public ServicePistolHandLayer(ServicePistolRenderer renderer) {
        super(renderer);
        this.pistolRenderer = renderer;
    }

    public static float handCenterX(boolean right, boolean slim) {
        return (right ? -1 : 1) * (slim ? 0.5F : 1F);
    }

    public static float palmY(boolean right) { return right ? 8F : 9.5F; }

    public static float palmContactX(boolean right, boolean slim) {
        return handCenterX(right, slim);
    }

    public static Vector3f contactOffset(boolean right) {
        return right ? new Vector3f(-1.55F, 0.20F, 0.75F) : new Vector3f(2.45F, -0.55F, -1.65F);
    }

    public static void orientAtHandTip(PoseStack pose, boolean right, boolean slim) {
        Vector3f contact = contactOffset(right);
        // V0.4.2 offsets; only read the already evaluated anchor transform.
        pose.translate(contact.x / 16, contact.y / 16, contact.z / 16);
        // Resolve the contact through the full scaled gun hierarchy FIRST. Only
        // then retain its camera-space translation and orthonormal orientation.
        // No inverse hard-coded gun scale, no change to the shared gun matrix.
        retainRigidContact(pose);
        pose.mulPose(right ? RIGHT_ORIENTATION : LEFT_ORIENTATION);
        pose.scale(ARM_VISUAL_SCALE, ARM_VISUAL_SCALE, ARM_VISUAL_SCALE);
        pose.translate(-palmContactX(right, slim) / 16F, -palmY(right) / 16F, 0);
    }

    public static void retainRigidContact(PoseStack pose) {
        var matrix = pose.last().pose();
        var x = new Vector3f(matrix.m00(), matrix.m01(), matrix.m02()).normalize();
        var y = new Vector3f(matrix.m10(), matrix.m11(), matrix.m12());
        y.sub(new Vector3f(x).mul(x.dot(y))).normalize();
        var z = new Vector3f(x).cross(y);
        // Preserve handedness if an item context carries a mirrored basis.
        if (z.dot(new Vector3f(matrix.m20(), matrix.m21(), matrix.m22())) < 0) z.negate();
        matrix.m00(x.x).m01(x.y).m02(x.z)
                .m10(y.x).m11(y.y).m12(y.z)
                .m20(z.x).m21(z.y).m22(z.z);
        // Rigid/reflected orthonormal basis: inverse-transpose equals the basis.
        pose.last().normal().set(matrix);
    }

    @Override
    public void renderForBone(PoseStack pose, ServicePistolItem item, GeoBone bone,
                              RenderType gunType, MultiBufferSource buffers, VertexConsumer gunBuffer,
                              float partialTick, int light, int overlay) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (!pistolRenderer.isFirstPersonPass() || player == null || player.isInvisible()
                || !(player.getMainHandItem().getItem() instanceof ServicePistolItem)) return;
        boolean right = bone.getName().equals("right_hand_anchor");
        if (!right && !bone.getName().equals("left_hand_anchor")) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer skinRenderer)) return;
        var model = skinRenderer.getModel();
        boolean slim = player.getModelName().equals("slim");
        // Never pass the weapon-owned stack to arm calibration or ModelPart rendering.
        // Even accidental direct matrix mutation in that code cannot affect the gun.
        var armPose = ServicePistolRenderMatrices.detachedCopy(pose);
        armPose.pushPose();
        try {
            // The layer already inherits weapon_root AND this anchor's animation.
            RenderUtils.translateToPivotPoint(armPose, bone);
            orientAtHandTip(armPose, right, slim);
            var skin = player.getSkinTextureLocation();
            renderPart(right ? model.rightArm : model.leftArm, armPose,
                    buffers.getBuffer(RenderType.entitySolid(skin)), light, overlay);
            if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE))
                renderPart(right ? model.rightSleeve : model.leftSleeve, armPose,
                        buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay);
        } finally {
            armPose.popPose();
            buffers.getBuffer(gunType);
        }
    }

    private static void renderPart(ModelPart part, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        PartPose original = part.storePose();
        boolean visible = part.visible;
        boolean skipDraw = part.skipDraw;
        float scaleX = part.xScale, scaleY = part.yScale, scaleZ = part.zScale;
        try {
            part.loadPose(PartPose.ZERO);
            part.visible = true;
            part.skipDraw = false;
            part.render(pose, buffer, light, overlay);
        } finally {
            part.loadPose(original);
            part.visible = visible;
            part.skipDraw = skipDraw;
            part.xScale = scaleX;
            part.yScale = scaleY;
            part.zScale = scaleZ;
        }
    }
}
