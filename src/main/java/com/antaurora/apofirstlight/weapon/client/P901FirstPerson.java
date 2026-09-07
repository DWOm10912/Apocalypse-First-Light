package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.P901Item;
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
public final class P901FirstPerson {
    // Camera-relative translation of this item's complete rig, not player-arm scale.
    // Kept outside item Display; the authored handling carrier is gun-local.
    public static final float COMPOSITION_X = .10F;
    public static final float COMPOSITION_Y = .045F;
    private P901FirstPerson() {}

    @SubscribeEvent
    public static void renderHands(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        boolean currentPistol = player.getMainHandItem().getItem() instanceof P901Item;
        boolean oldPistol = event.getItemStack().getItem() instanceof P901Item;
        boolean outgoing = !currentPistol && oldPistol && event.getHand() == InteractionHand.MAIN_HAND;
        if (!currentPistol && !outgoing) return;
        // Let vanilla finish lowering the previous non-P9 item before drawing P9.
        if (currentPistol && event.getHand() == InteractionHand.MAIN_HAND && !oldPistol && !event.getItemStack().isEmpty()) return;
        // This two-handed item owns both first-person hand passes while equipped.
        // No other item (including TaCZ) is intercepted when it is in the main hand.
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND || player.isSpectator() || player.isScoping()) return;
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        // Weapon owns its stack; neither renderer branch can alter the caller's camera stack.
        var pose = P901RenderMatrices.detachedCopy(event.getPoseStack());
        pose.pushPose();
        try {
            NativeGunRecoil.applyViewmodel(pose);
            NativeGunAds.apply(pose, right, event.getPartialTick());
            pose.translate(right ? COMPOSITION_X : -COMPOSITION_X, COMPOSITION_Y, 0);
            // Display owns static composition; only the upstream equip transition remains.
            P901Presentation.begin(event.getEquipProgress(), outgoing);
            // Use the authoritative current stack, not ItemInHandRenderer's equip-
            // interpolated old stack which can still carry the pre-GeckoLibID NBT.
            mc.getItemRenderer().renderStatic(player, outgoing ? event.getItemStack() : player.getMainHandItem(),
                    right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !right, pose, event.getMultiBufferSource(), player.level(), event.getPackedLight(),
                    OverlayTexture.NO_OVERLAY, player.getId());
        } finally { P901Presentation.end(); pose.popPose(); }
    }
}
