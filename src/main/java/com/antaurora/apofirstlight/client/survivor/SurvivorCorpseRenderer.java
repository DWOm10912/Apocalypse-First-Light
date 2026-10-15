package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.survivor.SurvivorCorpse;
import com.antaurora.apofirstlight.survivor.SurvivorSkins;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Survivor Death and Succession V0: the placeholder body (docs/gameplay/survivor_death_succession_v0.md 6). The vanilla
 * player model in its bind pose, lying on its back along the death yaw, centred on the corpse position, darkened. Skin:
 * the dead survivor's own look (section 13), whatever the owner wears now; a first survivor's body shows the owner's
 * account skin (the default skin of the owner's UUID while the owner is not in the tab list). No animation, no decay;
 * the mesh character replaces this later.
 */
public final class SurvivorCorpseRenderer extends EntityRenderer<SurvivorCorpse> {
    private static final float MODEL_SCALE = 0.9375F;
    /** Lifts the back (2 px half depth, head 4 px) so the torso rests on the ground and the head barely sinks. */
    private static final float LIFT = 0.2F;
    private static final float TINT_R = 0.62F, TINT_G = 0.60F, TINT_B = 0.58F;

    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    public SurvivorCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = bindPose(new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false));
        this.slim = bindPose(new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true));
        this.shadowRadius = 0.6F;
        this.shadowStrength = 0.6F;
    }

    private static PlayerModel<LivingEntity> bindPose(PlayerModel<LivingEntity> model) {
        model.young = false;
        model.riding = false;
        model.crouching = false;
        model.attackTime = 0.0F;
        model.setAllVisible(true);
        return model;
    }

    @Override
    public void render(SurvivorCorpse corpse, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean slimArms;
        if (accountLook(corpse)) {
            PlayerInfo info = playerInfo(corpse);
            slimArms = info != null ? "slim".equals(SurvivorIdentityClient.account(info::getModelName))
                    : "slim".equals(DefaultPlayerSkin.getSkinModelName(corpse.ownerPlayer()));
        } else {
            slimArms = corpse.arms() == SurvivorSkins.Arms.SLIM;
        }
        PlayerModel<LivingEntity> model = slimArms ? slim : wide;
        pose.pushPose();
        pose.translate(0.0F, LIFT, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - corpse.bodyYaw()));
        // the body (feet at the origin after the flip below, 32 px to the top of the head) lies along +Z once tipped
        // back: shift it by half its length so it is centred on the corpse
        pose.translate(0.0F, 0.0F, -1.0F * MODEL_SCALE);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(getTextureLocation(corpse))), light,
                OverlayTexture.NO_OVERLAY, TINT_R, TINT_G, TINT_B, 1.0F);
        pose.popPose();
        super.render(corpse, yaw, partialTick, pose, buffers, light);
    }

    /**
     * The dead survivor's own look: a pool skin, or for a first survivor the owner's account skin read past the living
     * successor's look (never the current survivor's).
     */
    @Override
    public ResourceLocation getTextureLocation(SurvivorCorpse corpse) {
        if (!accountLook(corpse)) return SurvivorSkins.texture(corpse.skin());
        PlayerInfo info = playerInfo(corpse);
        return info != null ? SurvivorIdentityClient.account(info::getSkinLocation)
                : DefaultPlayerSkin.getDefaultSkin(corpse.ownerPlayer());
    }

    private static boolean accountLook(SurvivorCorpse corpse) {
        return SurvivorSkins.ACCOUNT.equals(corpse.skin());
    }

    private static PlayerInfo playerInfo(SurvivorCorpse corpse) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getPlayerInfo(corpse.ownerPlayer());
    }
}
