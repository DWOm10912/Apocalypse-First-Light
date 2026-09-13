package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerDoorRaycast;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Routes right-clicks on an open leaf outside its owner block to the server. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class BeverageCoolerDoorInput {
    private BeverageCoolerDoorInput() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                || minecraft.screen != null || !minecraft.isWindowActive()
                || !minecraft.player.isAlive() || minecraft.player.isSpectator()) return;

        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F)
                .scale(minecraft.gameMode.getPickRange()));
        BeverageCoolerDoorRaycast.DoorHit hit = BeverageCoolerDoorRaycast.find(
                minecraft.level, minecraft.player, eye, end);
        if (hit == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        // Forge emits one use-key event per hand even if the main hand was cancelled.
        if (event.getHand() == InteractionHand.MAIN_HAND)
            AflNetwork.requestBeverageCoolerDoor(hit.master(), hit.left());
    }
}
