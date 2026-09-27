package com.antaurora.apofirstlight.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import java.util.List;

/** CPU backend: consumes the current traversal pose and existing QUADS buffer, never changes render state. */
public final class AflMeshRenderer {
    /** Optional per-invocation counters for the gated render probe. Null leaves the normal path untouched. */
    public static final class Metrics {
        public int parts, triangles, vertices, quadFaces, triangleFaces;
        public int hiddenTriangles, zeroScaleTriangles, invalidNormalTriangles;
    }

    public static void render(AflMeshModel model, GeoBone bone, PoseStack pose, VertexConsumer buffer,
                              int light, int overlay, float red, float green, float blue, float alpha) {
        render(model, bone, pose, buffer, light, overlay, red, green, blue, alpha, null);
    }

    public static void render(AflMeshModel model, GeoBone bone, PoseStack pose, VertexConsumer buffer,
                              int light, int overlay, float red, float green, float blue, float alpha,
                              Metrics metrics) {
        if (model == null || (metrics == null && bone.isHidden())) return;
        var parts = model.parts(bone.getName());
        if (parts.isEmpty()) return;
        if (bone.isHidden()) {
            if (metrics != null) metrics.hiddenTriangles = triangleCount(parts);
            return;
        }
        pose.pushPose();
        try {
            RenderUtils.translateToPivotPoint(pose, bone);
            var matrix = pose.last().pose();
            var normal = pose.last().normal();
            // Zero-scale animation hides geometry; singular normal transforms must never submit NaNs.
            float determinant = matrix.determinant3x3();
            if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) {
                if (metrics != null) metrics.zeroScaleTriangles = triangleCount(parts);
                return;
            }
            boolean mirrored = determinant < 0;
            float m00=matrix.m00(),m10=matrix.m10(),m20=matrix.m20(),m30=matrix.m30();
            float m01=matrix.m01(),m11=matrix.m11(),m21=matrix.m21(),m31=matrix.m31();
            float m02=matrix.m02(),m12=matrix.m12(),m22=matrix.m22(),m32=matrix.m32();
            float n00=normal.m00(),n10=normal.m10(),n20=normal.m20();
            float n01=normal.m01(),n11=normal.m11(),n21=normal.m21();
            float n02=normal.m02(),n12=normal.m12(),n22=normal.m22();
            // PoseStack negates its normal matrix for three negative scale axes.
            // Compute the actual inverse transpose once per invocation for reflections.
            if (mirrored) {
                n00=(m11*m22-m21*m12)/determinant; n10=(m21*m02-m01*m22)/determinant; n20=(m01*m12-m11*m02)/determinant;
                n01=(m20*m12-m10*m22)/determinant; n11=(m00*m22-m20*m02)/determinant; n21=(m10*m02-m00*m12)/determinant;
                n02=(m10*m21-m20*m11)/determinant; n12=(m20*m01-m00*m21)/determinant; n22=(m00*m11-m10*m01)/determinant;
            }
            for (int p = 0; p < parts.size(); p++) {
                var part = parts.get(p);
                boolean submittedPart = false;
                for (int face = 0; face < part.faceCount(); face++) {
                int start = part.faceStart(face), size = part.faceSize(face);
                float nx = part.value(start, 5), ny = part.value(start, 6), nz = part.value(start, 7);
                float x = n00*nx + n10*ny + n20*nz;
                float y = n01*nx + n11*ny + n21*nz;
                float z = n02*nx + n12*ny + n22*nz;
                float length = (float)Math.sqrt(x*x + y*y + z*z);
                if (!Float.isFinite(length) || length < 1e-12f) {
                    if (metrics != null) metrics.invalidNormalTriangles += size - 2;
                    continue;
                }
                x /= length; y /= length; z /= length;
                // QUADS: ABCD for native quads, ABCC for true/legacy triangles. Reflections
                // reverse the boundary while retaining the quad AC diagonal and outward normal.
                float px=0,py=0,pz=0,u=0,v=0;
                for (int corner = 0; corner < 4; corner++) {
                    // The degenerate fourth corner reuses the already transformed third corner.
                    if (corner < size) {
                        int index = start + (mirrored && corner > 0 ? size - corner : corner);
                        float vx=part.value(index,0),vy=part.value(index,1),vz=part.value(index,2);
                        px=m00*vx+m10*vy+m20*vz+m30; py=m01*vx+m11*vy+m21*vz+m31; pz=m02*vx+m12*vy+m22*vz+m32;
                        u=part.value(index,3); v=part.value(index,4);
                    }
                    buffer.vertex(px,py,pz,red,green,blue,alpha,u,v,overlay,light,x,y,z);
                }
                if (metrics != null) {
                    metrics.triangles += size - 2;
                    if (size == 4) metrics.quadFaces++; else metrics.triangleFaces++;
                    metrics.vertices += 4;
                    submittedPart = true;
                }
                }
                if (metrics != null && submittedPart) metrics.parts++;
            }
        } finally { pose.popPose(); }
    }

    private static int triangleCount(List<AflMeshPart> parts) {
        int total = 0;
        for (int i = 0; i < parts.size(); i++) total += parts.get(i).triangleEquivalent();
        return total;
    }

    /** Bind-pose framing uses the same pivot-local geometry as rendering; no triangle picking. */
    public static void collectBounds(AflMeshModel model, GeoBone bone, PoseStack pose, List<AABB> output) {
        if (model == null || bone.isHidden()) return;
        var parts = model.parts(bone.getName());
        if (parts.isEmpty()) return;
        pose.pushPose();
        try {
            RenderUtils.translateToPivotPoint(pose, bone);
            var m = pose.last().pose();
            for (var part : parts) {
                var b = part.bounds();
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
                for (int i=0;i<8;i++) {
                    double x=(i&1)==0?b.minX():b.maxX(),y=(i&2)==0?b.minY():b.maxY(),z=(i&4)==0?b.minZ():b.maxZ();
                    double px=m.m00()*x+m.m10()*y+m.m20()*z+m.m30();
                    double py=m.m01()*x+m.m11()*y+m.m21()*z+m.m31();
                    double pz=m.m02()*x+m.m12()*y+m.m22()*z+m.m32();
                    minX=Math.min(minX,px);minY=Math.min(minY,py);minZ=Math.min(minZ,pz);
                    maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);maxZ=Math.max(maxZ,pz);
                }
                output.add(new AABB(minX,minY,minZ,maxX,maxY,maxZ));
            }
        } finally { pose.popPose(); }
    }
    private AflMeshRenderer() {}
}
