package com.antaurora.apofirstlight.client.survivor;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * The survivor on the setup screen: a bare player model with a skin, drawn without an entity, so it also works before
 * the world exists (the Create World path). Turns towards the mouse like the inventory's doll; no armour, no held items.
 */
final class SurvivorDoll {
    private PlayerModel<LivingEntity> wide, slim;

    record Skin(ResourceLocation texture, boolean slim) {
        /** The account's own look (the first survivor of a world wears it). */
        static Skin account(Minecraft mc) {
            GameProfile profile = mc.getUser().getGameProfile();
            ResourceLocation texture = mc.getSkinManager().getInsecureSkinLocation(profile);
            MinecraftProfileTexture info = mc.getSkinManager().getInsecureSkinInformation(profile).get(MinecraftProfileTexture.Type.SKIN);
            boolean slim = info != null ? "slim".equals(info.getMetadata("model"))
                    : "slim".equals(DefaultPlayerSkin.getSkinModelName(profile.getId()));
            return new Skin(texture, slim);
        }

        static Skin of(AbstractClientPlayer player) {
            return new Skin(player.getSkinTextureLocation(), "slim".equals(player.getModelName()));
        }
    }

    /**
     * Draws the doll standing on (x, feet), size units per block; dx / dy = mouse offset from its head in the same units
     * (the turn follows atan(d / 40) as the inventory's does).
     */
    void render(GuiGraphics g, Skin skin, float x, float feet, float size, float dx, float dy) {
        Minecraft mc = Minecraft.getInstance();
        PlayerModel<LivingEntity> model = model(mc, skin.slim());
        // a model's young flag starts true (the entity renderer sets it each frame): without this it draws a child
        model.young = false;
        model.riding = false;
        model.attackTime = 0;
        float yaw = (float) Math.atan(dx / 40.0F), pitch = (float) Math.atan(dy / 40.0F);
        model.head.setRotation(-pitch * 20 * Mth.DEG_TO_RAD, yaw * 20 * Mth.DEG_TO_RAD, 0);
        model.hat.copyFrom(model.head);
        model.rightArm.setRotation(0, 0, 0.06F);
        model.leftArm.setRotation(0, 0, -0.06F);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        for (var part : new net.minecraft.client.model.geom.ModelPart[]{model.body, model.jacket, model.rightLeg, model.leftLeg,
                model.rightPants, model.leftPants}) {
            part.xRot = part.yRot = part.zRot = 0;
        }

        g.pose().pushPose();
        g.pose().translate(x, feet, 50);
        g.pose().mulPoseMatrix(new Matrix4f().scaling(size, size, -size));
        g.pose().mulPose(Axis.ZP.rotation((float) Math.PI));
        g.pose().mulPose(Axis.XP.rotationDegrees(pitch * 20));
        g.pose().mulPose(Axis.YP.rotationDegrees(-yaw * 20));
        // as LivingEntityRenderer: model space is upside down, feet at y = 1.5
        g.pose().scale(-1, -1, 1);
        g.pose().translate(0, -1.501F, 0);
        Lighting.setupForEntityInInventory();
        var buffer = g.bufferSource().getBuffer(model.renderType(skin.texture()));
        model.renderToBuffer(g.pose(), buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }

    private PlayerModel<LivingEntity> model(Minecraft mc, boolean slimArms) {
        if (slimArms) {
            if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }
}
