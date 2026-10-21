package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.FlipLighterItem;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Flip lighter V1 (docs/gameplay/flip_lighter_v1.md 3; tools/build-equipment-meshes-v1.mjs flip_lighter): the Pure Mesh
 * with its lid on the hinge, the flint wheel and the flame, all from the stack's tag (item/FlipLighterItem), so the held
 * lighter, the skeleton's prop (ItemDisplayContext.NONE) and one on the ground look the same.
 * <ul>
 *   <li>the lid (the parts named lid*) turns about the hinge (z axis at {@link #HINGE}) up to
 *   {@link FlipLighterItem#LID_OPEN_DEG}, flicked open with a little overshoot, snapped shut;</li>
 *   <li>the wheel turns about its axle at {@link #WHEEL} while the thumb rolls it;</li>
 *   <li>the flame: a quad standing on the wick, turned about the lighter's up to face the camera, the flipbook
 *   textures/effect/lighter_flame (tools/build-lighter-flame-v1.mjs: 16 frames) drawn additively at full bright
 *   ({@link LiquidRenderTypes#LIGHTER_FLAME}).</li>
 * </ul>
 */
public final class FlipLighterRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/flip_lighter.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/flip_lighter_mesh.png");
    /** 0.5 - mesh height 11.4 / 32 (the build's verticalOffset). */
    public static final double VERTICAL_OFFSET = 0.14375;
    /** The hinge pin and the wheel's axle (mesh px, baked: the case centred on x). */
    private static final float[] HINGE = {3.7891F, 7.3F}, WHEEL = {1.8391F, 10.45F};
    /** The wick's top (mesh px), where the flame stands. */
    private static final float WICK_X = 0.6891F, WICK_Y = 10.42F;
    /** The flame's length (mesh px, 1 px = 5 mm: 3.5 cm), the texture's flame 46 of its 64 px rows, rooted 4 px up. */
    private static final float FLAME_LENGTH = 7.0F, FLAME_ROWS = 46F / 64F, FLAME_ROOT = 4F / 64F;
    private static final int FRAMES = 16, COLS = 4;
    /** Flipbook frames a second. */
    private static final float FPS = 24F;
    /** The wheel's turn under one stroke of the thumb (deg). */
    private static final float WHEEL_TURN = 150F;

    private record Parts(MeshModel mesh, List<MeshPart> fixed, List<MeshPart> lid, List<MeshPart> wheel) {}
    private Parts parts;

    public FlipLighterRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        var level = Minecraft.getInstance().level;
        float partial = Minecraft.getInstance().getFrameTime();
        String act = FlipLighterItem.act(stack);
        float t = level == null ? 1e6F : (float) FlipLighterItem.actSeconds(stack, level, partial);
        VertexConsumer cutout = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
            pose.translate(0.5, VERTICAL_OFFSET, 0.5);
            MeshRenderer.renderParts(p.fixed, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            turned(p.lid, pose, cutout, light, overlay, HINGE, -lidAngle(stack, act, t));
            turned(p.wheel, pose, cutout, light, overlay, WHEEL, -wheelAngle(act, t));
            float flame = flame(stack, act, t, level == null ? 0 : level.getGameTime() + partial);
            if (flame > 0.01F && context != ItemDisplayContext.GUI) flame(pose, buffers, flame, level == null ? 0 : (level.getGameTime() + partial) / 20F);
        } finally {
            pose.popPose();
        }
    }

    private static void turned(List<MeshPart> list, PoseStack pose, VertexConsumer out, int light, int overlay, float[] axle, float deg) {
        if (list.isEmpty()) return;
        pose.pushPose();
        pose.translate(axle[0] / 16F, axle[1] / 16F, 0);
        if (deg != 0) pose.mulPose(Axis.ZP.rotationDegrees(deg));
        pose.translate(-axle[0] / 16F, -axle[1] / 16F, 0);
        MeshRenderer.renderParts(list, pose, out, light, overlay, 1, 1, 1, 1, null);
        pose.popPose();
    }

    /** The lid's open angle (deg) at t s into the last act. */
    static float lidAngle(ItemStack stack, String act, float t) {
        float open = FlipLighterItem.LID_OPEN_DEG;
        if (act == null) return FlipLighterItem.isOpen(stack) ? open : 0;
        return switch (act) {
            case "open" -> {
                if (t < FlipLighterItem.LID_OPEN_FROM) yield 0;
                float u = (t - FlipLighterItem.LID_OPEN_FROM) / (FlipLighterItem.LID_OPEN_TO - FlipLighterItem.LID_OPEN_FROM);
                if (u < 1) yield (open + 10) * (1 - (1 - u) * (1 - u));   // flicked: fast, past the stop
                yield open + 10 * (float) Math.exp(-(t - FlipLighterItem.LID_OPEN_TO) * 18) * Mth.cos((t - FlipLighterItem.LID_OPEN_TO) * 40);   // back on the cam
            }
            case "close", "close_lit" -> {
                float u = Mth.clamp((t - FlipLighterItem.LID_CLOSE_FROM) / (FlipLighterItem.LID_CLOSE_TO - FlipLighterItem.LID_CLOSE_FROM), 0, 1);
                yield open * (1 - u * u);   // pushed shut, fastest as it snaps
            }
            default -> open;
        };
    }

    /** The wheel's turn (deg) at t s into the last act. */
    static float wheelAngle(String act, float t) {
        if (!"strike".equals(act) && !"spark".equals(act)) return 0;
        float u = Mth.clamp((t - FlipLighterItem.WHEEL_FROM) / (FlipLighterItem.WHEEL_TO - FlipLighterItem.WHEEL_FROM), 0, 1);
        return WHEEL_TURN * u * (2 - u);
    }

    /** The flame's size (0: none) at t s into the last act; {@code ticks}: the game time, for the flicker. */
    static float flame(ItemStack stack, String act, float t, float ticks) {
        float size;
        if ("out".equals(act)) size = Math.max(0, 1 - t / FlipLighterItem.OUT_TIME);
        else if ("close_lit".equals(act)) size = 1 - Mth.clamp((t - FlipLighterItem.LID_CLOSE_FROM - 0.04F) / (FlipLighterItem.LID_CLOSE_TO - FlipLighterItem.LID_CLOSE_FROM - 0.04F), 0, 1);
        else if (!FlipLighterItem.isLit(stack)) return 0;
        else if ("strike".equals(act)) {
            float c = t - FlipLighterItem.CATCH_AT;
            if (c < 0) return 0;
            // catches small, flares up past its size as the vapour over the wick goes, settles
            size = c < 0.06F ? 0.3F + 0.95F * (c / 0.06F) : 1 + 0.25F * (float) Math.exp(-(c - 0.06F) * 12);
        } else size = 1;
        // a lighter flame's own flicker: a slow sway of size and a quick one
        return size * (1 + 0.04F * Mth.sin(ticks * 0.9F) + 0.025F * Mth.sin(ticks * 2.3F + 1.7F));
    }

    /** The flame quad on the wick, turned about the lighter's up toward the camera (the pose's origin). */
    private static void flame(PoseStack pose, MultiBufferSource buffers, float size, float seconds) {
        Matrix4f m = pose.last().pose();
        float bx = WICK_X / 16F, by = WICK_Y / 16F;
        Vector4f cam = new Matrix4f(m).invert().transform(new Vector4f(0, 0, 0, 1));
        float dx = cam.x / cam.w - bx, dz = cam.z / cam.w - 0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1e-5F) { dx = 0; dz = 1; len = 1; }
        dx /= len;
        dz /= len;
        float h = FLAME_LENGTH / FLAME_ROWS / 16F * size, w = h * 0.5F;
        float y0 = by - FLAME_ROOT * h, y1 = y0 + h;
        float rx = dz * w / 2, rz = -dx * w / 2;
        int frame = Math.floorMod((int) (seconds * FPS), FRAMES);
        float u0 = (frame % COLS) / (float) COLS, v0 = (frame / COLS) / (float) (FRAMES / COLS);
        float u1 = u0 + 1F / COLS, v1 = v0 + 1F / (FRAMES / COLS);
        int alpha = (int) (255 * Mth.clamp(size, 0, 1));
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.LIGHTER_FLAME);
        Matrix3f n = pose.last().normal();
        vertex(out, m, n, bx - rx, y0, -rz, u0, v1, alpha, dx, dz);
        vertex(out, m, n, bx + rx, y0, rz, u1, v1, alpha, dx, dz);
        vertex(out, m, n, bx + rx, y1, rz, u1, v0, alpha, dx, dz);
        vertex(out, m, n, bx - rx, y1, -rz, u0, v0, alpha, dx, dz);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int alpha, float nx, float nz) {
        out.vertex(m, x, y, z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(n, nx, 0, nz).endVertex();
    }

    /** One bone with its pivot at the origin (the equipment bake): the lid's parts, the wheel and the rest. */
    private Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> fixed = new ArrayList<>(), lid = new ArrayList<>(), wheel = new ArrayList<>();
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) {
                String name = part.name();
                if (name.startsWith("lid")) lid.add(part);
                else if (name.equals("wheel")) wheel.add(part);
                else fixed.add(part);
            }
        }
        parts = new Parts(mesh, List.copyOf(fixed), List.copyOf(lid), List.copyOf(wheel));
        return parts;
    }
}
