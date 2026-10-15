package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Survivor Death and Succession V0: the Forge hooks (docs/gameplay/survivor_death_succession_v0.md 4). The death is
 * handled at the lowest priority, after every other mod had its say (a cancelled death is never seen).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorEvents {
    private SurvivorEvents() {
    }

    /**
     * Fires only when a world is created (Terrain V2 cancels it after choosing the spawn, hence receiveCanceled): marks
     * the world fresh and takes the mode picked on the Create World screen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onCreateWorld(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) return;
        SurvivorWorldData data = level.getDataStorage().computeIfAbsent(SurvivorWorldData::load, SurvivorWorldData::new,
                SurvivorWorldData.NAME);
        data.freshWorld = true;
        Survivors.applyCreateScreenChoice(level.getServer(), data);
        data.setDirty();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Survivors.resolveStartMode(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SurvivorCorpses.reconcileStalePlayer(player);
        Survivors.ensureSurvivor(player);
        Survivors.onLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SurvivorCorpses.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Survivors.tracked(player)) return;
        Survivors.recordDeath(player, event.getSource());
    }

    /** Every item of the death goes into the corpse; also seen when another mod cancelled the drops. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        SurvivorCorpses.onDrops(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        SurvivorCorpses.onClone(event);
    }

    /** First, so every later respawn handler (radiation, weight, terrain sync) already sees the successor's start. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Survivors.takeOver(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SurvivorCorpses.tick(event.getServer());
        Survivors.flush(event.getServer());
    }
}
