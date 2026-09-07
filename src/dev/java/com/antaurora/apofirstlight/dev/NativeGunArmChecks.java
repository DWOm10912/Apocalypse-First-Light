package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** DEV-only actual vanilla vertex probes; no drawing, image capture or model mutation persists. */
public final class NativeGunArmChecks {
    private NativeGunArmChecks() {}

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static PoseStack copy(PoseStack input) {
        var result = new PoseStack();
        result.last().pose().set(input.last().pose());
        result.last().normal().set(input.last().normal());
        return result;
    }

    public static Bounds vertices(ModelPart part, PoseStack pose) {
        var saved = part.storePose();
        boolean visible = part.visible, skip = part.skipDraw;
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        var bounds = new Bounds();
        try {
            part.loadPose(PartPose.ZERO);
            part.xScale = part.yScale = part.zScale = 1;
            part.visible = true;
            part.skipDraw = false;
            part.render(pose, bounds, 0, 0);
        } finally {
            part.loadPose(saved);
            part.visible = visible;
            part.skipDraw = skip;
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
        }
        return bounds;
    }

    public static final class Bounds implements VertexConsumer {
        public final java.util.List<Vector3f> points=new java.util.ArrayList<>(24);
        public final java.util.List<org.joml.Vector2f> uvs=new java.util.ArrayList<>(24);
        public final java.util.List<Vector3f> normals=new java.util.ArrayList<>(24);
        public float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        public float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        public int vertices;
        public VertexConsumer vertex(double x,double y,double z) {
            points.add(new Vector3f((float)x,(float)y,(float)z));
            minX=Math.min(minX,(float)x); minY=Math.min(minY,(float)y); minZ=Math.min(minZ,(float)z);
            maxX=Math.max(maxX,(float)x); maxY=Math.max(maxY,(float)y); maxZ=Math.max(maxZ,(float)z);
            return this;
        }
        public VertexConsumer color(int r,int g,int b,int a) { return this; }
        public VertexConsumer uv(float u,float v) { uvs.add(new org.joml.Vector2f(u,v)); return this; }
        public VertexConsumer overlayCoords(int u,int v) { return this; }
        public VertexConsumer uv2(int u,int v) { return this; }
        public VertexConsumer normal(float x,float y,float z) { normals.add(new Vector3f(x,y,z)); return this; }
        public void endVertex() { vertices++; }
        public void defaultColor(int r,int g,int b,int a) {}
        public void unsetDefaultColor() {}
    }
}
