package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ragdoll V1 R-C (docs/dev/ragdoll_v1_plan.md 4): draws a humanoid body in any {@link SkeletonPose} with any player skin,
 * outside a living player's own render (corpses now, later other skeleton bodies). The pose stack must be at the model
 * origin, as LivingEntityRenderer leaves it before renderToBuffer (model px / 16).
 * <ul>
 *   <li>Skeleton on: the C-min skin mesh (SkinMesh; elbows and knees bend) and vanilla's head and hat set from the head
 *   bone, the same as a living player (PlayerSkeleton#draw).</li>
 *   <li>Skeleton off: vanilla's six parts set from the pose's head, chest, upper arms and thighs, rigid (forearms and
 *   shins follow the upper bone).</li>
 * </ul>
 * The model's own part values are put back afterwards (the shared model is drawn by others too).
 */
public final class SkeletonBodyRenderer {
    private SkeletonBodyRenderer() {
    }

    /**
     * @param skinMesh the skeleton's skin mesh (PlayerSkeleton#enabled), else vanilla's rigid parts
     * @param head     draw the head and hat (off for a camera inside the head)
     */
    public static void draw(PlayerModel<?> model, SkeletonPose pose, boolean slim, boolean skinMesh, boolean head,
                            PoseStack stack, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        float[] saved = new float[72];
        save(model, saved);
        boolean headVisible = model.head.visible, hatVisible = model.hat.visible;
        try {
            if (skinMesh) {
                PoseStack.Pose last = stack.last();
                Matrix4f mp = last.pose();
                Matrix3f mn = last.normal();
                Vector3f p = new Vector3f(), n = new Vector3f();
                SkinMesh.Sink sink = (x, y, z, u, v, nx, ny, nz) -> {
                    mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
                    mn.transform(nx, ny, nz, n);
                    consumer.vertex(p.x, p.y, p.z, r, g, b, a, u, v, overlay, light, n.x, n.y, n.z);
                };
                SkeletonRig rig = pose.rig;
                for (SkeletonRig.SkinPart part : rig.skin) {
                    if (PlayerSkeleton.base(model, part.part()).visible) SkinMesh.emit(pose, part, slim, false, sink);
                    if (PlayerSkeleton.layer(model, part.overlay()).visible) SkinMesh.emit(pose, part, slim, true, sink);
                }
                if (head) {
                    PlayerSkeleton.set(model.head, pose.world[rig.head], PlayerSkeleton.HEAD);
                    model.hat.copyFrom(model.head);
                    model.head.render(stack, consumer, light, overlay, r, g, b, a);
                    model.hat.render(stack, consumer, light, overlay, r, g, b, a);
                }
            } else {
                PlayerSkeleton.write(model, pose);
                model.head.visible = head && headVisible;
                model.hat.visible = head && hatVisible;
                model.renderToBuffer(stack, consumer, light, overlay, r, g, b, a);
            }
        } finally {
            model.head.visible = headVisible;
            model.hat.visible = hatVisible;
            restore(model, saved);
        }
    }

    private static net.minecraft.client.model.geom.ModelPart[] parts(PlayerModel<?> m) {
        return new net.minecraft.client.model.geom.ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve,
                m.leftArm, m.leftSleeve, m.rightLeg, m.rightPants, m.leftLeg, m.leftPants};
    }

    private static void save(PlayerModel<?> m, float[] to) {
        var parts = parts(m);
        for (int i = 0; i < parts.length; i++) {
            var p = parts[i];
            int o = i * 6;
            to[o] = p.x; to[o + 1] = p.y; to[o + 2] = p.z; to[o + 3] = p.xRot; to[o + 4] = p.yRot; to[o + 5] = p.zRot;
        }
    }

    private static void restore(PlayerModel<?> m, float[] from) {
        var parts = parts(m);
        for (int i = 0; i < parts.length; i++) {
            var p = parts[i];
            int o = i * 6;
            p.x = from[o]; p.y = from[o + 1]; p.z = from[o + 2]; p.xRot = from[o + 3]; p.yRot = from[o + 4]; p.zRot = from[o + 5];
        }
    }
}
