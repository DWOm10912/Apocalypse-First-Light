package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla Progression Pruning V1 (no experience) and the retired workstations of Vanilla Survival Rework V1: both follow
 * the retirement switch (VanillaRetirement). The workstations are the block tag apocalypse_firstlight:retired/workstations
 * (enchanting table, anvils, brewing stand, grindstone, smithing table, ender chest); the blocks stay placeable and
 * breakable as decoration, only their use is refused in survival and adventure. Creative and spectator may still open
 * them to check old builds.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaProgressionEvents {
    private VanillaProgressionEvents() {}

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (VanillaRetirement.enabled() && event.getEntity() instanceof ServerPlayer && event.getAmount() > 0) event.setAmount(0);
    }

    @SubscribeEvent
    public static void onXpOrbPickup(PlayerXpEvent.PickupXp event) {
        if (VanillaRetirement.enabled() && event.getEntity() instanceof ServerPlayer) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMobExperience(LivingExperienceDropEvent event) {
        if (VanillaRetirement.enabled()) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (VanillaRetirement.enabled()) event.setExpToDrop(0);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (VanillaRetirement.enabled() && event.getEntity() instanceof ServerPlayer player
                && !player.isCreative() && !player.isSpectator()) {
            player.setExperiencePoints(0);
            player.setExperienceLevels(0);
        }
    }

    @SubscribeEvent
    public static void onDisabledWorkstation(PlayerInteractEvent.RightClickBlock event) {
        var state = event.getLevel().getBlockState(event.getPos());
        if (!VanillaRetirement.refusesWorkstation(state, event.getEntity())) return;
        event.setUseBlock(Event.Result.DENY);
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND)
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.retired_workstation"), true);
    }
}
