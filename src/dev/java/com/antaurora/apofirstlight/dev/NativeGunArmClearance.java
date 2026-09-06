package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.client.ServicePistolModel;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.List;

/** DEV-only OBB separating-axis check against animated upper gun cubes, including sleeve inflation. */
public final class NativeGunArmClearance {
    private NativeGunArmClearance() {}

    public static int upperGunIntersections(ServicePistolModel model,PoseStack base,PoseStack arm,
                                           List<Vector3f> sleeve) {
        int intersections=0;
        for (String part : new String[]{"slide","barrel","front_sight","rear_sight"}) {
            var bone=model.getBone(part).orElseThrow();
            var bonePose=NativeGunArmChecks.copy(base);
            for (String name : new String[]{"root","weapon_root","gun",part})
                RenderUtils.prepMatrixForBone(bonePose,model.getBone(name).orElseThrow());
            for (var cube : bone.getCubes()) {
                var cubePose=NativeGunArmChecks.copy(bonePose);
                RenderUtils.translateToPivotPoint(cubePose,cube);
                RenderUtils.rotateMatrixAroundCube(cubePose,cube);
                RenderUtils.translateAwayFromPivotPoint(cubePose,cube);
                var points=new ArrayList<Vector3f>(24);
                for (var quad : cube.quads()) {
                    if (quad==null) continue;
                    for (var vertex : quad.vertices())
                        points.add(cubePose.last().pose().transformPosition(new Vector3f(vertex.position())));
                }
                if (!separated(sleeve,arm.last().pose(),points,cubePose.last().pose())) intersections++;
            }
        }
        return intersections;
    }

    private static Vector3f[] axes(Matrix4f matrix) {
        return new Vector3f[]{new Vector3f(matrix.m00(),matrix.m01(),matrix.m02()).normalize(),
                new Vector3f(matrix.m10(),matrix.m11(),matrix.m12()).normalize(),
                new Vector3f(matrix.m20(),matrix.m21(),matrix.m22()).normalize()};
    }

    private static boolean separated(List<Vector3f> a,Matrix4f am,List<Vector3f> b,Matrix4f bm) {
        if (a.isEmpty() || b.isEmpty()) return true;
        var aa=axes(am); var ba=axes(bm);
        for (var axis : aa) if (gap(a,b,axis)) return true;
        for (var axis : ba) if (gap(a,b,axis)) return true;
        for (var x : aa) for (var y : ba) {
            var cross=new Vector3f(x).cross(y);
            if (cross.lengthSquared()>1E-10F && gap(a,b,cross.normalize())) return true;
        }
        return false;
    }

    private static boolean gap(List<Vector3f> a,List<Vector3f> b,Vector3f axis) {
        float amin=Float.POSITIVE_INFINITY,amax=Float.NEGATIVE_INFINITY;
        float bmin=Float.POSITIVE_INFINITY,bmax=Float.NEGATIVE_INFINITY;
        for (var p:a) { float d=p.dot(axis); amin=Math.min(amin,d); amax=Math.max(amax,d); }
        for (var p:b) { float d=p.dot(axis); bmin=Math.min(bmin,d); bmax=Math.max(bmax,d); }
        // Numerical contact tolerance is 0.00016 model pixels, not a visual gap allowance.
        return amax<=bmin+1E-5F || bmax<=amin+1E-5F;
    }
}
