package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.VanillaRetirementEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla Survival Rework V1.8 (docs/gameplay/vanilla_survival_rework_v1.md 4.12, user 2026-10-23): with no tool in the main
 * hand a left click on a block does nothing at all, the first click and the held key alike: no breaking begins (no crack, no
 * hit sound, no particles), the arm does not swing, nothing goes to the server. A break already under way (the tool swapped
 * out with the key held) is aborted. Last, so whatever takes the click for itself first (guns, doors, the crowbar's smash,
 * the orbit camera's turn) still has it; entities and empty air are struck as before. The server refuses the same
 * ({@link VanillaRetirementEvents#cannotDig}).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NoToolDigClient {
    private NoToolDigClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || mc.level == null || mc.gameMode == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || mc.level.getBlockState(hit.getBlockPos()).isAir() || !VanillaRetirementEvents.cannotDig(mc.player)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
    }
}
