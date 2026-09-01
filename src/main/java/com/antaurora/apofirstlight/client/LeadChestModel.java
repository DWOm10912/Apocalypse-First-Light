package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime form of the user-authored 64x64 Lead Chest model.
 *
 * <p>The source uses explicit per-face UV rectangles, including deliberate flips. Mojang's
 * {@code CubeListBuilder} only exposes box-unwrapped UVs, so this model submits the same six
 * face quads directly instead of approximating the atlas layout.</p>
 */
public final class LeadChestModel {
    public static final float MAX_OPEN_ANGLE_DEGREES = 70.0F;
    private static final float MAX_OPEN_ANGLE_RADIANS = MAX_OPEN_ANGLE_DEGREES * Mth.DEG_TO_RAD;
    private static final float MODEL_SCALE = 1.0F / 16.0F;
    // Java block-model UV coordinates always span 0..16, regardless of the PNG resolution.
    private static final float UV_SCALE = 1.0F / 16.0F;

    private final Part root = new Part("root", 8.0F, 0.0F, 8.0F);
    private final Part base = new Part("base", 8.0F, 0.0F, 8.0F);
    private final Part lid = new Part("lid", 8.0F, 10.0F, 14.0F);
    private final Part latch = new Part("latch", 8.0F, 10.0F, 14.0F);

    public LeadChestModel() {
        root.children.add(base);
        root.children.add(lid);
        lid.children.add(latch);

        base.cubes.add(box("base_body_rear", 2, 2, 13, 14, 9.75F, 14,
                uv(0, 0, 3, 2), uv(1.5F, 6.25F, 1.75F, 8.25F),
                uv(0, 0, 3, 2), uv(1.75F, 6.25F, 2, 8.25F),
                uv(0, 0, 3, 2), uv(3, 5.75F, 0, 6)));
        base.cubes.add(box("base_body_front", 2, 2, 2, 14, 9.75F, 3,
                uv(0, 0, 3, 2), uv(2, 6.25F, 2.25F, 8.25F),
                uv(0, 0, 3, 2), uv(2.25F, 6.25F, 2.5F, 8.25F),
                uv(0, 0, 3, 2), uv(3, 6, 0, 6.25F)));
        base.cubes.add(box("base_left_reinforcement", 1, 0.5F, 1, 2.5F, 10, 15,
                uv(0, 0, 3, 2), uv(0, 0, 3, 2), uv(4.5F, 5.5F, 5, 8),
                uv(0, 0, 3, 2), uv(0, 0, 3, 2), uv(0, 0, 3, 2)));
        base.cubes.add(box("base_right_reinforcement", 13.5F, 0.5F, 1, 15, 10, 15,
                uv(5, 5.5F, 5.5F, 8), uv(0, 0, 3, 2), uv(5.5F, 5.5F, 6, 8),
                uv(0, 0, 3, 2), uv(3.5F, 6.75F, 3, 3.25F), uv(4, 3, 3.5F, 6.5F)));
        base.cubes.add(box("base_front_lower_frame", 2.5F, 0.5F, 1, 13.5F, 2, 13.5F,
                uv(0, 4.25F, 2.75F, 4.75F), uv(4, 3, 7.25F, 3.5F),
                uv(4.5F, 4, 7.25F, 4.5F), uv(4, 3.5F, 7.25F, 4),
                uv(0, 0, 3, 2), uv(0, 4.25F, 2.75F, 4.75F)));
        base.cubes.add(box("base_rear_lower_frame", 2.5F, 0.5F, 13.5F, 13.5F, 2, 15,
                uv(4.5F, 4.5F, 7.25F, 5), uv(2.5F, 6.25F, 3, 6.75F),
                uv(0, 4.75F, 2.75F, 5.25F), uv(6.5F, 0, 7, 0.5F),
                uv(7.25F, 5.5F, 4.5F, 5), uv(2.75F, 5.25F, 0, 5.75F)));

        latch.cubes.add(box("latch_body", 7, 10.75F, 0.25F, 9, 14, 1.25F,
                uv(6, 1.25F, 6.5F, 2), uv(2.75F, 4.25F, 3, 5),
                uv(6.25F, 2, 6.75F, 2.75F), uv(2.75F, 5, 3, 5.75F),
                uv(6.75F, 3, 6.25F, 2.75F), uv(7, 0.5F, 6.5F, 0.75F)));
        latch.cubes.add(box("latch_face", 7.35F, 11.25F, 0, 8.65F, 13.5F, 0.5F,
                uv(6.5F, 0.75F, 6.75F, 1.25F), uv(6.5F, 1.25F, 6.75F, 1.75F),
                uv(3.5F, 6.5F, 3.75F, 7), uv(3.75F, 6.5F, 4, 7),
                uv(3.5F, 3.25F, 3.25F, 3), uv(6.75F, 1.75F, 6.5F, 2)));

        lid.cubes.add(box("lid_body", 2, 10.25F, 2, 14, 13.75F, 14,
                uv(3, 0, 6, 1), uv(3, 1, 6, 2), uv(0, 3.25F, 3, 4.25F),
                uv(3.25F, 2, 6.25F, 3), uv(0, 0, 3, 2), uv(0, 0, 3, 2)));
        lid.cubes.add(box("lid_front_rail", 1, 10, 1, 15, 14.5F, 2.5F,
                uv(0, 0, 3, 2), uv(6, 0, 6.5F, 1.25F), uv(4.5F, 5.5F, 5, 8),
                uv(0, 0, 3, 2), uv(0, 0, 3, 2), uv(0, 0, 3, 2)));
        lid.cubes.add(box("lid_left_rail", 1, 10, 2.5F, 2.5F, 14.5F, 15,
                uv(6, 5.5F, 6.5F, 6.75F), uv(4.5F, 5.5F, 5, 8), uv(0, 0, 3, 2),
                uv(0, 0, 3, 2), uv(0, 0, 3, 2), uv(0, 0, 3, 2)));
        lid.cubes.add(box("lid_right_rail", 13.5F, 10, 2.5F, 15, 14.5F, 15,
                uv(0, 6.25F, 0.5F, 7.5F), uv(0, 0, 3, 2), uv(0, 0, 3, 2),
                uv(0, 2, 3.25F, 3.25F), uv(4.5F, 7.25F, 4, 4), uv(0, 0, 3, 2)));
        lid.cubes.add(box("lid_hinge_bar", 2.5F, 10, 13.5F, 13.5F, 14.5F, 15,
                uv(0, 0, 3, 2), uv(0.5F, 6.25F, 1, 7.5F), uv(0, 0, 3, 2),
                uv(1, 6.25F, 1.5F, 7.5F), uv(0, 0, 3, 2), uv(0, 0, 3, 2)));
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay,
                       float openness) {
        float clamped = Mth.clamp(openness, 0.0F, 1.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - clamped, 3.0D);
        lid.xRot = -MAX_OPEN_ANGLE_RADIANS * eased;
        root.render(poseStack, consumer, packedLight, packedOverlay);
    }

    private static Box box(String name, float x1, float y1, float z1, float x2, float y2, float z2,
                           Uv north, Uv east, Uv south, Uv west, Uv up, Uv down) {
        Map<Direction, Uv> faces = new EnumMap<>(Direction.class);
        faces.put(Direction.NORTH, north);
        faces.put(Direction.EAST, east);
        faces.put(Direction.SOUTH, south);
        faces.put(Direction.WEST, west);
        faces.put(Direction.UP, up);
        faces.put(Direction.DOWN, down);
        return new Box(name, x1, y1, z1, x2, y2, z2, faces);
    }

    private static Uv uv(float u1, float v1, float u2, float v2) {
        return new Uv(u1, v1, u2, v2);
    }

    private static final class Part {
        private final String name;
        private final float pivotX;
        private final float pivotY;
        private final float pivotZ;
        private final List<Box> cubes = new ArrayList<>();
        private final List<Part> children = new ArrayList<>();
        private float xRot;

        private Part(String name, float pivotX, float pivotY, float pivotZ) {
            this.name = name;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
        }

        private void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
            poseStack.pushPose();
            if (xRot != 0.0F) {
                poseStack.translate(pivotX * MODEL_SCALE, pivotY * MODEL_SCALE, pivotZ * MODEL_SCALE);
                // Mojang chest parts store opening as negative X rotation. The source vertices are Y-up,
                // so applying the inverse here preserves that convention while opening toward the rear hinge.
                poseStack.mulPose(Axis.XP.rotation(-xRot));
                poseStack.translate(-pivotX * MODEL_SCALE, -pivotY * MODEL_SCALE, -pivotZ * MODEL_SCALE);
            }
            for (Box cube : cubes) cube.render(poseStack, consumer, packedLight, packedOverlay);
            for (Part child : children) child.render(poseStack, consumer, packedLight, packedOverlay);
            poseStack.popPose();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private record Uv(float u1, float v1, float u2, float v2) {
    }

    private record Box(String name, float x1, float y1, float z1, float x2, float y2, float z2,
                       Map<Direction, Uv> faces) {
        private void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
            emitFace(poseStack, consumer, packedLight, packedOverlay, Direction.NORTH, faces.get(Direction.NORTH),
                    x2, y2, z1, x2, y1, z1, x1, y1, z1, x1, y2, z1);
            emitFace(poseStack, consumer, packedLight, packedOverlay, Direction.EAST, faces.get(Direction.EAST),
                    x2, y2, z2, x2, y1, z2, x2, y1, z1, x2, y2, z1);
            emitFace(poseStack, consumer, packedLight, packedOverlay, Direction.SOUTH, faces.get(Direction.SOUTH),
                    x1, y2, z2, x1, y1, z2, x2, y1, z2, x2, y2, z2);
            emitFace(poseStack, consumer, packedLight, packedOverlay, Direction.WEST, faces.get(Direction.WEST),
                    x1, y2, z1, x1, y1, z1, x1, y1, z2, x1, y2, z2);
            emitFace(poseStack, consumer, packedLight, packedOverlay, Direction.UP, faces.get(Direction.UP),
                    x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
            emitFace(poseStack, consumer, packedLight, packedOverlay, Direction.DOWN, faces.get(Direction.DOWN),
                    x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static void emitFace(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay,
                                 Direction direction, Uv uv,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3) {
        PoseStack.Pose pose = poseStack.last();
        vertex(pose, consumer, packedLight, packedOverlay, direction, x0, y0, z0, uv.u1, uv.v1);
        vertex(pose, consumer, packedLight, packedOverlay, direction, x1, y1, z1, uv.u1, uv.v2);
        vertex(pose, consumer, packedLight, packedOverlay, direction, x2, y2, z2, uv.u2, uv.v2);
        vertex(pose, consumer, packedLight, packedOverlay, direction, x3, y3, z3, uv.u2, uv.v1);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int packedOverlay,
                               Direction normal, float x, float y, float z, float u, float v) {
        consumer.vertex(pose.pose(), x * MODEL_SCALE, y * MODEL_SCALE, z * MODEL_SCALE)
                .color(255, 255, 255, 255)
                .uv(u * UV_SCALE, v * UV_SCALE)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(pose.normal(), normal.getStepX(), normal.getStepY(), normal.getStepZ())
                .endVertex();
    }
}
