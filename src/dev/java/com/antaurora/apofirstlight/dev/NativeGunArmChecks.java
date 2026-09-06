package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.ServicePistolHandLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** DEV-only actual vanilla vertex probes; no drawing, image capture or model mutation persists. */
public final class NativeGunArmChecks {
    private static boolean reportedBaselineIntersection;
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

    public static void legacyMapping(PoseStack pose, boolean right, boolean slim) {
        var contact = (right ? new Vector3f(-1.55F,.20F,.75F) : new Vector3f(2.45F,-.55F,-1.65F)).div(16);
        pose.translate(contact.x, contact.y, contact.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0), (right
                ? new Vector3f(.24F,-.69F,.69F) : new Vector3f(-.24F,-.87F,.44F)).normalize()));
        pose.translate(-ServicePistolHandLayer.handCenterX(right, slim)/16F,
                -(right ? 8F : 9.5F)/16F, 0);
    }

    public static void checkMapping(PoseStack anchor, boolean right, boolean slim) {
        var expected = anchor.last().pose().transformPosition(ServicePistolHandLayer.contactOffset(right).div(16));
        var mapped = copy(anchor);
        ServicePistolHandLayer.orientAtHandTip(mapped, right, slim);
        var matrix = mapped.last().pose();
        var actual = matrix.transformPosition(new Vector3f(ServicePistolHandLayer.palmContactX(right, slim)/16F,
                ServicePistolHandLayer.palmY(right)/16F, 0));
        require(actual.distance(expected) < 0.00001F, "animated camera-space palm contact preserved");
        float scale = ServicePistolHandLayer.ARM_VISUAL_SCALE;
        for (var axis : new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)})
            require(Math.abs(matrix.transformDirection(axis).length()-scale) < 0.00001F, "independent arm visual scale");
        require(Math.abs(Math.abs(new org.joml.Matrix3f(matrix).determinant())-scale*scale*scale) < .00001F,
                "uniform arm presentation without shear");
        var normal = mapped.last().normal().transform(new Vector3f(0,1,0));
        require(Math.abs(normal.length()-1) < .00001F, "unit arm lighting normal");
    }

    public static void checkGeometry(Minecraft mc) {
        for (boolean slim : new boolean[]{false,true}) {
            var root = mc.getEntityModels().bakeLayer(slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM
                    : net.minecraft.client.model.geom.ModelLayers.PLAYER);
            for (boolean right : new boolean[]{false,true}) {
                for (boolean sleeve : new boolean[]{false,true}) {
                    var part = root.getChild((right ? "right" : "left") + (sleeve ? "_sleeve" : "_arm"));
                    var bounds = vertices(part, new PoseStack());
                    float inflation = sleeve ? .5F : 0;
                    require(Math.abs((bounds.maxX-bounds.minX)*16 - ((slim ? 3 : 4)+inflation)) < .0001,
                            "vanilla arm/sleeve width");
                    require(Math.abs((bounds.maxY-bounds.minY)*16 - (12+inflation)) < .0001,
                            "full vanilla arm/sleeve length");
                    require(Math.abs((bounds.maxZ-bounds.minZ)*16 - (4+inflation)) < .0001,
                            "vanilla arm/sleeve depth");
                    require(bounds.vertices == 24, "all six vanilla cuboid faces emitted");
                }
                // Scale is derived from the incoming basis, not a magic 1/0.3 multiplier.
                for (float scale : new float[]{.2F,.3F,.6F,1F}) {
                    var anchor = new PoseStack();
                    anchor.translate(.21,-.3,-.7);
                    anchor.mulPose(new Quaternionf().rotationXYZ(.2F,-.3F,.1F));
                    anchor.scale(scale,scale,scale);
                    checkMapping(anchor,right,slim);
                }
            }
        }
        ApocalypseFirstLight.LOGGER.info("[AFL ARM V043B GEOMETRY] PASS: Classic 4x12x4, Slim 3x12x4; sleeves +0.25px per face; 24 vertices each; independent arm visual scale={} at gun scales 0.2/0.3/0.6/1.0; palm-surface contacts preserved", ServicePistolHandLayer.ARM_VISUAL_SCALE);
    }

    public static Bounds vertices(ModelPart part, PoseStack pose) {
        var saved = part.storePose();
        boolean visible = part.visible, skip = part.skipDraw;
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        var bounds = new Bounds();
        try {
            part.loadPose(PartPose.ZERO);
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

    public static float[] animatedNearZ(com.antaurora.apofirstlight.weapon.client.ServicePistolModel model,
                                       double reloadSeconds, ModelPart[] skinRoots) {
        float oldNear = Float.NEGATIVE_INFINITY, newNear = Float.NEGATIVE_INFINITY;
        for (int skin=0; skin<2; skin++) {
            for (boolean right : new boolean[]{false,true}) {
                var pose = new PoseStack();
                pose.translate(com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_X,
                        com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_Y,
                        com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.BASE_Z);
                pose.translate(-6/16F,1/16F,0);
                pose.scale(.3F,.3F,.3F);
                pose.translate(0,.01,0);
                com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.applyReload(pose,
                        com.antaurora.apofirstlight.weapon.client.ServicePistolPresentation.reloadWeight(reloadSeconds));
                var gunBase=copy(pose);
                String anchor = right ? "right_hand_anchor" : "left_hand_anchor";
                for (String name : new String[]{"root","weapon_root",anchor})
                    software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(pose,model.getBone(name).orElseThrow());
                software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(pose,model.getBone(anchor).orElseThrow());
                checkMapping(pose,right,skin==1);
                var legacy=copy(pose);
                legacyMapping(legacy,right,skin==1);
                ServicePistolHandLayer.orientAtHandTip(pose,right,skin==1);
                var sleeve=skinRoots[skin].getChild(right ? "right_sleeve" : "left_sleeve");
                var legacyVertices=vertices(sleeve,legacy);
                oldNear=Math.max(oldNear,legacyVertices.maxZ);
                var sleeveVertices=vertices(sleeve,pose);
                newNear=Math.max(newNear,sleeveVertices.maxZ);
                int currentIntersections=NativeGunArmClearance.upperGunIntersections(model,gunBase,pose,sleeveVertices.points);
                int baselineIntersections=NativeGunArmClearance.upperGunIntersections(model,gunBase,legacy,legacyVertices.points);
                require(currentIntersections==baselineIntersections,"restored arm clearance matches V042 baseline, not newly tuned");
                if (baselineIntersections>0 && !reportedBaselineIntersection) {
                    reportedBaselineIntersection=true;
                    ApocalypseFirstLight.LOGGER.warn("[AFL V042 ARM BASELINE LIMIT] upper-gun intersections={} match current={} right={} slim={}; baseline limitation retained, not a zero-intersection claim",baselineIntersections,currentIntersections,right,skin==1);
                }
            }
        }
        return new float[]{oldNear,newNear};
    }

    public static final class Bounds implements VertexConsumer {
        public final java.util.List<Vector3f> points=new java.util.ArrayList<>(24);
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
        public VertexConsumer uv(float u,float v) { return this; }
        public VertexConsumer overlayCoords(int u,int v) { return this; }
        public VertexConsumer uv2(int u,int v) { return this; }
        public VertexConsumer normal(float x,float y,float z) { return this; }
        public void endVertex() { vertices++; }
        public void defaultColor(int r,int g,int b,int a) {}
        public void unsetDefaultColor() {}
    }
}
