package com.antaurora.apofirstlight.weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;

/** All native weapons share this full Vanilla skin/sleeve renderer and size contract. */
public final class NativePlayerArmRenderer {
    // Framework viewmodel style, not a weapon/action setting. 16 model units = 1 render unit.
    public static final float PLAYER_ARM_SCALE = 1F;
    // One first-person presentation rule for every native weapon, hand and action.
    public static final float PRESENTATION_X = .62F;
    public static final float PRESENTATION_Y = .78F;
    public static final float PRESENTATION_Z = .62F;
    private static java.util.function.Predicate<Boolean> handFilter = right -> true;

    private NativePlayerArmRenderer() {}

    public static void setHandFilter(java.util.function.Predicate<Boolean> filter) {
        handFilter = java.util.Objects.requireNonNull(filter);
    }

    /** Supported authoring input is a rigid frame times a positive uniform scale
     * (a reflected frame is also valid). Validate; never repair shear or rebuild
     * rotation with Gram-Schmidt/quaternion decomposition. */
    public static float inheritedUniformScale(PoseStack locator) {
        var m = locator.last().pose();
        if (!m.isFinite()) return Float.NaN;
        float s = (float)Math.cbrt(Math.abs(m.determinant3x3()));
        if (!Float.isFinite(s) || s < 1e-6F) return Float.NaN;
        var x = new org.joml.Vector3f(m.m00(), m.m01(), m.m02());
        var y = new org.joml.Vector3f(m.m10(), m.m11(), m.m12());
        var z = new org.joml.Vector3f(m.m20(), m.m21(), m.m22());
        float tolerance = s * s * 1e-4F;
        if (Math.abs(x.lengthSquared()-s*s)>tolerance || Math.abs(y.lengthSquared()-s*s)>tolerance
                || Math.abs(z.lengthSquared()-s*s)>tolerance || Math.abs(x.dot(y))>tolerance
                || Math.abs(x.dot(z))>tolerance || Math.abs(y.dot(z))>tolerance) return Float.NaN;
        return s;
    }

    /** Preserve the evaluated contact and rotation exactly. Cancel only the
     * supported uniform parent factor, then scale in canonical cap coordinates.
     * M_arm = M_evaluated * S(1/s) * S_presentation * B_skin.
     * Applying scale after B_skin instead would move the distal cap. */
    public static PoseStack canonicalPose(PoseStack evaluatedLocator) {
        float inherited = inheritedUniformScale(evaluatedLocator);
        if (!Float.isFinite(inherited)) return null;
        var result = P901RenderMatrices.detachedCopy(evaluatedLocator);
        result.scale(PRESENTATION_X / inherited, PRESENTATION_Y / inherited, PRESENTATION_Z / inherited);
        return result;
    }

    /** The caller supplies a fully animated canonical locator, never gun geometry. */
    public static void render(PoseStack evaluatedLocator, boolean right, MultiBufferSource buffers,
                              int light, int overlay) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || player.isInvisible() || !handFilter.test(right)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        var pose = canonicalPose(evaluatedLocator);
        if (pose == null) return;
        NativeHandBinding.apply(pose, right, player.getModelName().equals("slim"));
        var model = renderer.getModel();
        var skin = player.getSkinTextureLocation();
        renderPart(right ? model.rightArm : model.leftArm, pose,
                buffers.getBuffer(RenderType.entitySolid(skin)), light, overlay);
        if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE))
            renderPart(right ? model.rightSleeve : model.leftSleeve, pose,
                    buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay);
    }

    private static void renderPart(ModelPart part, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        PartPose original = part.storePose();
        boolean visible = part.visible, skipDraw = part.skipDraw;
        float x = part.xScale, y = part.yScale, z = part.zScale;
        try {
            part.loadPose(PartPose.ZERO);
            part.xScale = part.yScale = part.zScale = 1;
            part.visible = true;
            part.skipDraw = false;
            part.render(pose, buffer, light, overlay);
        } finally {
            part.loadPose(original);
            part.visible = visible; part.skipDraw = skipDraw;
            part.xScale = x; part.yScale = y; part.zScale = z;
        }
    }
}
