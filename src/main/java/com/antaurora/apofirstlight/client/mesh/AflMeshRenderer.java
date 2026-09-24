package com.antaurora.apofirstlight.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import java.util.List;

/** CPU backend: consumes the current traversal pose and existing QUADS buffer, never changes render state. */
public final class AflMeshRenderer {
    public static void render(AflMeshModel model, GeoBone bone, PoseStack pose, VertexConsumer buffer,
                              int light, int overlay, float red, float green, float blue, float alpha) {
        if (model == null || bone.isHidden()) return;
        var parts = model.parts(bone.getName());
        if (parts.isEmpty()) return;
        pose.pushPose();
        try {
            RenderUtils.translateToPivotPoint(pose, bone);
            var matrix = pose.last().pose();
            var normal = pose.last().normal();
            // Zero-scale animation hides geometry; singular normal transforms must never submit NaNs.
            float determinant = matrix.determinant3x3();
            if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) return;
            boolean mirrored = determinant < 0;
            for (var part : parts) for (int triangle = 0; triangle < part.cornerCount(); triangle += 3) {
                float nx = part.value(triangle, 5), ny = part.value(triangle, 6), nz = part.value(triangle, 7);
                float x = normal.m00()*nx + normal.m10()*ny + normal.m20()*nz;
                float y = normal.m01()*nx + normal.m11()*ny + normal.m21()*nz;
                float z = normal.m02()*nx + normal.m12()*ny + normal.m22()*nz;
                // PoseStack negates its normal matrix for three negative scale axes. Use the actual
                // inverse transpose of the position matrix for reflections instead.
                if (mirrored) {
                    x = ((matrix.m11()*matrix.m22()-matrix.m21()*matrix.m12())*nx
                            +(matrix.m21()*matrix.m02()-matrix.m01()*matrix.m22())*ny
                            +(matrix.m01()*matrix.m12()-matrix.m11()*matrix.m02())*nz) / determinant;
                    y = ((matrix.m20()*matrix.m12()-matrix.m10()*matrix.m22())*nx
                            +(matrix.m00()*matrix.m22()-matrix.m20()*matrix.m02())*ny
                            +(matrix.m10()*matrix.m02()-matrix.m00()*matrix.m12())*nz) / determinant;
                    z = ((matrix.m10()*matrix.m21()-matrix.m20()*matrix.m11())*nx
                            +(matrix.m20()*matrix.m01()-matrix.m00()*matrix.m21())*ny
                            +(matrix.m00()*matrix.m11()-matrix.m10()*matrix.m01())*nz) / determinant;
                }
                float length = (float)Math.sqrt(x*x + y*y + z*z);
                if (!Float.isFinite(length) || length < 1e-12f) continue;
                x /= length; y /= length; z /= length;
                // Vanilla entity RenderTypes consume QUADS. A,B,C,C gives one real triangle.
                // Reverse the real triangle on reflections while retaining outward transformed normals.
                for (int corner = 0; corner < 4; corner++) {
                    int index = triangle + (corner == 0 ? 0 : mirrored ? (corner == 1 ? 2 : 1) : Math.min(corner, 2));
                    float px=part.value(index,0), py=part.value(index,1), pz=part.value(index,2);
                    buffer.vertex(matrix.m00()*px+matrix.m10()*py+matrix.m20()*pz+matrix.m30(),
                            matrix.m01()*px+matrix.m11()*py+matrix.m21()*pz+matrix.m31(),
                            matrix.m02()*px+matrix.m12()*py+matrix.m22()*pz+matrix.m32(),
                            red, green, blue, alpha, part.value(index,3), part.value(index,4), overlay, light, x, y, z);
                }
            }
        } finally { pose.popPose(); }
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
