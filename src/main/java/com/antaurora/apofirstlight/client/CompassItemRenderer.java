package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pocket compass (tools/build-equipment-meshes-v1.mjs compass; user 2026-10-21: "PBR+表盘真实转动（类似原版指南针）"): the
 * mesh with its card (parts card_*) turned about the pivot so the card's N points to the world's north (-Z) wherever the
 * compass is drawn: in the hand, on a chest rig's clip, on the ground, in a frame, on other players. The card's angle
 * comes from where the drawn compass really faces: the pose's rotation times the inverse view rotation gives the model's
 * axes in the world; world north brought into the model and laid on the card's plane is where N must point. Held on edge
 * (the card's plane nearly upright and facing north or south) the card keeps its last angle, like a real card resting
 * on its pivot. In the inventory the card reads as if the compass lay level with its bail toward where the player looks.
 * The card swings to its angle on a lightly damped spring (it overshoots a little and settles), each stack and display
 * context on its own; the shadow pass draws the last angle without moving it.
 */
public final class CompassItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation GEOMETRY = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/compass.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/compass_mesh.png");
    /** 0.5 - mesh height 1.385 / 32 (the build's verticalOffset). */
    private static final double VERTICAL_OFFSET = 0.456719;
    /** The card's pivot in the baked mesh (the build's pivot [0, 0, 0.66] px), blocks. */
    private static final float PIVOT_Z = 0.66F / 16;
    /** Below this length of world north laid on the card, the card is too steep to settle and keeps its angle. */
    static final float LEVEL_MIN = 0.15F;
    /** The card's spring: natural frequency (rad/s) and damping ratio. */
    static final float OMEGA = 9F, DAMPING = 0.38F;

    /** The card's angle (degrees about the model's +Y) and its speed, for one stack in one display context. */
    private static final class Swing {
        float angle, speed;
        long last;
        boolean set;
    }
    private static final Map<ItemStack, EnumMap<ItemDisplayContext, Swing>> SWINGS = new WeakHashMap<>();

    /** fixed: case, bezel, glass's frame...; ring lies flat (in the hand, on the ground), ringUp stands (hanging from a clip). */
    private record Parts(MeshModel mesh, List<MeshPart> fixed, List<MeshPart> card, MeshPart ring, MeshPart ringUp) {}
    private Parts parts;

    public CompassItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(GEOMETRY);
        if (mesh == null) return;
        Parts p = parts(mesh);
        VertexConsumer cutout = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
        pose.pushPose();
        try {
            // ItemRenderer already applied the display transform and the -0.5 shift; the mesh is centred on X / Z, rests on y = 0
            pose.translate(0.5, VERTICAL_OFFSET, 0.5);
            float angle = cardAngle(stack, context, pose);
            MeshRenderer.renderParts(p.fixed, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            // display context NONE: drawn by ClipRenderer, hanging by its ring
            MeshPart ring = context == ItemDisplayContext.NONE ? p.ringUp : p.ring;
            if (ring != null) MeshRenderer.renderParts(List.of(ring), pose, cutout, light, overlay, 1, 1, 1, 1, null);
            pose.pushPose();
            pose.translate(0, 0, PIVOT_Z);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0, 0, -PIVOT_Z);
            MeshRenderer.renderParts(p.card, pose, cutout, light, overlay, 1, 1, 1, 1, null);
            pose.popPose();
            if (mesh.hasTranslucent()) {
                VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
                for (MeshBone bone : mesh.roots()) {
                    if (bone.neverRender()) continue;
                    pose.pushPose();
                    pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
                    MeshRenderer.renderParts(mesh.parts(bone.name(), MeshLayer.TRANSLUCENT), pose, glass, light, overlay, 1, 1, 1, 1, null);
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    /** Where the card points this frame, after its spring. */
    private static float cardAngle(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
        Swing s = SWINGS.computeIfAbsent(stack, k -> new EnumMap<>(ItemDisplayContext.class)).computeIfAbsent(context, k -> new Swing());
        if (AflShaderCompat.activeShadowPass()) return s.angle;
        Float target = target(context, pose);
        long now = Util.getMillis();
        if (!s.set) {
            s.angle = target != null ? target : 0;
            s.set = true;
        } else if (target != null) {
            float dt = Mth.clamp((now - s.last) / 1000F, 0, 0.1F);
            // the spring on the shortest way round: a' = -w^2 (angle - target) - 2 z w v
            float off = Mth.wrapDegrees(s.angle - target);
            s.speed += (-OMEGA * OMEGA * off - 2 * DAMPING * OMEGA * s.speed) * dt;
            s.angle = Mth.wrapDegrees(s.angle + s.speed * dt);
        } else {
            s.speed = 0;
        }
        s.last = now;
        return s.angle;
    }

    /** The card angle that points N north, or null when the card stands too steep to settle. */
    static Float target(ItemDisplayContext context, PoseStack pose) {
        if (context == ItemDisplayContext.GUI) {
            var player = Minecraft.getInstance().player;
            return player == null ? 0F : Mth.wrapDegrees(player.getYRot() + 180);
        }
        // model -> world: the inverse view rotation (camera -> world) times the pose's rotation (model -> camera)
        Matrix3f world = new Matrix3f(RenderSystem.getInverseViewRotationMatrix()).mul(pose.last().normal());
        return angleFor(world);
    }

    /** The card angle for a model -> world rotation (columns may carry scale), null when the card is too steep. */
    static Float angleFor(Matrix3f world) {
        Vector3f x = world.getColumn(0, new Vector3f()), y = world.getColumn(1, new Vector3f()), z = world.getColumn(2, new Vector3f());
        if (x.lengthSquared() < 1e-8F || y.lengthSquared() < 1e-8F || z.lengthSquared() < 1e-8F) return null;
        x.normalize();
        z.normalize();
        // world north (0, 0, -1) in the model: its x and z (the card's plane)
        float nx = -x.z, nz = -z.z;
        if (nx * nx + nz * nz < LEVEL_MIN * LEVEL_MIN) return null;
        // Ry(a) takes the card's N (-Z) to (-sin a, 0, -cos a)
        return (float) Math.toDegrees(Math.atan2(-nx, -nz));
    }

    /** One bone with its pivot at the origin (the equipment bake): parts are drawn without a bone transform. */
    private Parts parts(MeshModel mesh) {
        if (parts != null && parts.mesh == mesh) return parts;
        List<MeshPart> fixed = new ArrayList<>(), card = new ArrayList<>();
        MeshPart ring = null, ringUp = null;
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) {
                String name = part.name();
                if (name.equals("ring")) ring = part;
                else if (name.equals("ring_up")) ringUp = part;
                else (name.startsWith("card_") ? card : fixed).add(part);
            }
        }
        parts = new Parts(mesh, List.copyOf(fixed), List.copyOf(card), ring, ringUp);
        return parts;
    }
}
