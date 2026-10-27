package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.client.skeleton.BlockMeshBody;
import com.antaurora.apofirstlight.client.skeleton.BlockMeshWardrobe;
import com.antaurora.apofirstlight.client.skeleton.SkeletonBodyRenderer;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.survivor.SurvivorAppearance;
import com.antaurora.apofirstlight.survivor.SurvivorRecord;
import com.antaurora.apofirstlight.survivor.SurvivorSkins;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The body on the appearance and setup pages (character creation V1, docs/ui/character_creation_v1.md), drawn without an
 * entity, so it also works before the world exists; turned by the caller (drag), lit like the inventory's doll.
 * <ul>
 * <li>The old body (the default): the skin mesh at rest (SkeletonBodyRenderer) in the sex's starting skin
 * (SurvivorSkins#starter), the female torso and chest for a woman (female body V1).</li>
 * <li>The B5 body (dev switch): its layers for a look and outfit (BlockMeshWardrobe) in the mesh's rest pose (every bone
 * where it was modelled: standing, arms down).</li>
 * </ul>
 */
final class BodyPreview {
    private SkeletonPose pose, oldPose;
    private PlayerModel<?> wide, slim;

    /**
     * Draws the body standing on (x, feet), size canvas units per block, turned {@code yaw} degrees (0: facing the viewer)
     * and tipped {@code pitch} degrees. False when the body cannot be drawn (its rig or mesh failed to load).
     */
    boolean render(GuiGraphics g, SurvivorAppearance.Look look, float x, float feet, float size, float yaw, float pitch) {
        boolean b5 = BlockMeshBody.active();
        try {
            if (b5 && pose == null) pose = new SkeletonPose(SkeletonRig.blockMeshV2());
            if (!b5 && oldPose == null) oldPose = new SkeletonPose(SkeletonRig.get());
        } catch (RuntimeException e) {
            return false;
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
        boolean drawn = true;
        if (b5) {
            drawn = BlockMeshBody.draw(pose, null, g.pose(), g.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1,
                    Float.NaN, BlockMeshWardrobe.layers(look.tone(), look.hair(), look.outfit()));
        } else {
            SurvivorSkins.Skin skin = SurvivorSkins.starter(look.female() ? SurvivorRecord.Sex.FEMALE : SurvivorRecord.Sex.MALE);
            boolean thin = skin.arms() == SurvivorSkins.Arms.SLIM;
            PlayerModel<?> model = model(thin);
            ResourceLocation texture = SurvivorSkins.texture(skin.id());
            SkeletonBodyRenderer.draw(model, oldPose, thin, look.female(), true, true, g.pose(), g.bufferSource().getBuffer(model.renderType(texture)),
                    0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
        return drawn;
    }

    private PlayerModel<?> model(boolean thin) {
        var models = Minecraft.getInstance().getEntityModels();
        if (thin) return slim != null ? slim : (slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true));
        return wide != null ? wide : (wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false));
    }
}
