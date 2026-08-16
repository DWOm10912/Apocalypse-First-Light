package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaProgressionEvents {
    private VanillaProgressionEvents() {}

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getEntity() instanceof ServerPlayer && event.getAmount() > 0) event.setAmount(0);
    }

    @SubscribeEvent
    public static void onXpOrbPickup(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMobExperience(LivingExperienceDropEvent event) {
        event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        event.setExpToDrop(0);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.isCreative() && !player.isSpectator()) {
            player.setExperiencePoints(0);
            player.setExperienceLevels(0);
        }
    }

    @SubscribeEvent
    public static void onDisabledWorkstation(PlayerInteractEvent.RightClickBlock event) {
        var state = event.getLevel().getBlockState(event.getPos());
        boolean enchanting = state.is(Blocks.ENCHANTING_TABLE);
        boolean anvil = state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL);
        if (!enchanting && !anvil) return;
        event.setUseBlock(Event.Result.DENY);
        event.setCanceled(true);
    }
}
