package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** One CPU-only capture when a Field/maintenance resource or attachment snapshot changes. */
record GunInspectionGeometry(Vec3 pivot, AABB bounds) {
    static GunInspectionGeometry capture(ItemStack stack, Vec3 retainedPivot, boolean field) {
        var boxes = MaintenanceGunRendering.bounds(stack);
        if (boxes.isEmpty()) return null;
        var base = new PoseStack();
        MaintenanceGunRendering.transform(stack, base);
        var capturePose = new PoseStack();
        if (field) {
            boolean right = net.minecraft.client.Minecraft.getInstance().player.getMainArm()
                    == net.minecraft.world.entity.HumanoidArm.RIGHT;
            var target = com.antaurora.apofirstlight.weapon.client.FieldAttachmentTransform.target(stack, right);
            // Capture in Field camera space, cancelling only the maintenance presentation matrix.
            // Bone and attachment local transforms remain the existing shared native bind pose.
            capturePose.mulPoseMatrix(new org.joml.Matrix4f(target).mul(new org.joml.Matrix4f(base.last().pose()).invert()));
            base = new PoseStack(); base.mulPoseMatrix(target);
        }
        var body = new Capture(null);
        for (var box : boxes) for (int i = 0; i < 8; i++) {
            var p = base.last().pose().transformPosition(new Vector3f(
                    (float)((i & 1) == 0 ? box.minX : box.maxX),
                    (float)((i & 2) == 0 ? box.minY : box.maxY),
                    (float)((i & 4) == 0 ? box.minZ : box.maxZ)));
            body.vertex(p.x, p.y, p.z);
        }
        // Choose an actual gun surface vertex near its center as the stationary inspection pivot.
        // Keeping this point in the viewport prevents panning the entire body out of view.
        // Accessory vertices never choose this pivot, so fitting does not shift on installation.
        var gun = new Capture(body.box().getCenter());
        var attachments = new Capture(null);
        RenderType gunType = MaintenanceGunRendering.renderType(stack);
        MaintenanceGunRendering.render(stack, capturePose,
                type -> type == gunType ? gun : attachments, 0);
        if (!gun.valid()) return null;
        AABB all = attachments.valid() ? gun.box().minmax(attachments.box()) : gun.box();
        // Field keeps the existing live idle pose; reserve margin for its small root/follower deltas.
        return new GunInspectionGeometry(retainedPivot == null ? gun.closest : retainedPivot, all.inflate(field ? .04 : .002));
    }

    /** Discards everything except positions; no GPU buffer, draw call, texture or asset writes. */
    private static final class Capture implements VertexConsumer {
        private final Vec3 center;
        private Vec3 closest;
        private double nearest = Double.POSITIVE_INFINITY;
        private double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        private double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;

        Capture(Vec3 center) { this.center = center; }
        boolean valid() { return Double.isFinite(minX); }
        AABB box() { return new AABB(minX, minY, minZ, maxX, maxY, maxZ); }
        @Override public VertexConsumer vertex(double x, double y, double z) {
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            if (center != null) {
                double distance = center.distanceToSqr(x, y, z);
                if (distance < nearest) { nearest = distance; closest = new Vec3(x, y, z); }
            }
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }
}
