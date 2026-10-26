package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.skeleton.BlockMeshBody;
import com.antaurora.apofirstlight.client.skeleton.BlockMeshWardrobe;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.survivor.SurvivorAppearance;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * The B5 body on the appearance and setup pages (character creation V1, docs/ui/character_creation_v1.md): its layers for a
 * look and outfit (BlockMeshWardrobe) in the mesh's rest pose (every bone where it was modelled: standing, arms down),
 * drawn without an entity, so it also works before the world exists. Turned by the caller (drag), lit like the inventory's
 * doll.
 */
final class BodyPreview {
    private SkeletonPose pose;

    /**
     * Draws the body standing on (x, feet), size canvas units per block, turned {@code yaw} degrees (0: facing the viewer)
     * and tipped {@code pitch} degrees. False when the B body cannot be drawn (its rig or mesh failed to load).
     */
    boolean render(GuiGraphics g, SurvivorAppearance.Look look, float x, float feet, float size, float yaw, float pitch) {
        if (pose == null) {
            try {
                pose = new SkeletonPose(SkeletonRig.blockMeshV2());
            } catch (RuntimeException e) {
                return false;
            }
        }
        g.pose().pushPose();
        g.pose().translate(x, feet, 200);
        g.pose().mulPoseMatrix(new Matrix4f().scaling(size, size, -size));
        g.pose().mulPose(Axis.ZP.rotation((float) Math.PI));
        g.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        g.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        // as LivingEntityRenderer: model space is upside down, feet at y = 1.5; the player's 0.9375 (PlayerRenderer)
        g.pose().scale(-0.9375F, -0.9375F, 0.9375F);
        g.pose().translate(0, -1.501F, 0);
        Lighting.setupForEntityInInventory();
        boolean drawn = BlockMeshBody.draw(pose, null, g.pose(), g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1,
                Float.NaN, BlockMeshWardrobe.layers(look.tone(), look.hair(), look.outfit()));
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
        return drawn;
    }
}
