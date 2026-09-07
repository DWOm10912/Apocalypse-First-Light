package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ServicePistolFirstPerson {
    // Camera-relative translation of this item's complete rig, not player-arm scale.
    // Kept outside item Display; Reload cancels it in the authored fp_root track.
    public static final float COMPOSITION_X = .10F;
    public static final float COMPOSITION_Y = .045F;
    private ServicePistolFirstPerson() {}

    @SubscribeEvent
    public static void renderHands(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof ServicePistolItem)) return;
        // This two-handed item owns both first-person hand passes while equipped.
        // No other item (including TaCZ) is intercepted when it is in the main hand.
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND || player.isSpectator() || player.isScoping()) return;
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        // Weapon owns its stack; neither renderer branch can alter the caller's camera stack.
        var pose = ServicePistolRenderMatrices.detachedCopy(event.getPoseStack());
        pose.pushPose();
        try {
            pose.translate(right ? COMPOSITION_X : -COMPOSITION_X, COMPOSITION_Y, 0);
            // Display owns static composition; only the upstream equip transition remains.
            ServicePistolPresentation.applyEquip(pose, event.getEquipProgress());
            // Use the authoritative current stack, not ItemInHandRenderer's equip-
            // interpolated old stack which can still carry the pre-GeckoLibID NBT.
            mc.getItemRenderer().renderStatic(player, player.getMainHandItem(),
                    right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !right, pose, event.getMultiBufferSource(), player.level(), event.getPackedLight(),
                    OverlayTexture.NO_OVERLAY, player.getId());
        } finally { pose.popPose(); }
    }
}
