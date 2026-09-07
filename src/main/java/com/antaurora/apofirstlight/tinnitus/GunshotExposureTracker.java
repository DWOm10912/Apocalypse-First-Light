package com.antaurora.apofirstlight.tinnitus;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunshotExposureTracker {
    private static final Map<ServerLevel, Map<UUID, GunshotExposureAccumulator>> EXPOSURE_BY_LEVEL
            = new WeakHashMap<>();

    private GunshotExposureTracker() {
    }

    /**
     * Internal acoustic-input seam for native successful shots; no external fire listener.
     * Caller supplies acoustic radius and suppression policy; this method does not emit NoiseEvents.
     * Returns the number of tinnitus impulses sent; BR51 uses this seam, P9 opts out.
     */
    public static int onGunshot(ServerLevel level, ServerPlayer shooter, Vec3 sourcePosition,
                                double acousticRadius, boolean trueSuppressor) {
        return accumulateGunshot(level, shooter, sourcePosition, acousticRadius, trueSuppressor,
                AflNetwork::sendTinnitusImpulse);
    }

    /** Package-private seam for deterministic DEV tests whose mock players have no Netty channel. */
    static int accumulateGunshot(ServerLevel level, ServerPlayer shooter, Vec3 sourcePosition,
                                 double acousticRadius, boolean trueSuppressor,
                                 BiConsumer<ServerPlayer, Float> impulseSink) {
        if (trueSuppressor) {
            return 0;
        }
        if (!shooter.isAlive() || shooter.isRemoved() || shooter.isSpectator()
                || !Double.isFinite(acousticRadius)) {
            return 0;
        }

        AABB listenerBounds = new AABB(sourcePosition, sourcePosition)
                .inflate(GunshotTinnitusProfile.LISTENER_RANGE);
        List<ServerPlayer> listeners = level.getEntitiesOfClass(ServerPlayer.class, listenerBounds,
                player -> player.isAlive() && !player.isRemoved() && !player.isSpectator());
        return accumulateListeners(level, shooter, sourcePosition, acousticRadius,
                false, listeners, impulseSink);
    }

    /** Package-private pure listener pass shared with the multi-listener DEV test. */
    static int accumulateListeners(ServerLevel level, ServerPlayer shooter, Vec3 sourcePosition,
                                   double acousticRadius, boolean trueSuppressor,
                                   Iterable<ServerPlayer> listeners,
                                   BiConsumer<ServerPlayer, Float> impulseSink) {
        if (trueSuppressor) {
            return 0;
        }
        long serverTick = level.getServer().getTickCount();
        int impulses = 0;
        for (ServerPlayer listener : listeners) {
            if (listener.level() != level || !listener.isAlive() || listener.isRemoved()
                    || listener.isSpectator()) {
                continue;
            }
            double distance = listener == shooter
                    ? 0.0
                    : listener.getEyePosition().distanceTo(sourcePosition);
            double shotExposure = GunshotTinnitusProfile.shotExposure(acousticRadius, distance);
            if (shotExposure <= 0.0) {
                continue;
            }

            Map<UUID, GunshotExposureAccumulator> levelExposure = EXPOSURE_BY_LEVEL.computeIfAbsent(
                    level, ignored -> new HashMap<>());
            GunshotExposureAccumulator accumulator = levelExposure.computeIfAbsent(
                    listener.getUUID(), ignored -> new GunshotExposureAccumulator());
            float severity = accumulator.recordShot(shotExposure, serverTick);
            if (severity > 0.0F) {
                impulseSink.accept(listener, severity);
                impulses++;
            }
        }
        return impulses;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        long serverTick = event.getServer().getTickCount();
        List<ServerLevel> emptyLevels = new ArrayList<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<UUID, GunshotExposureAccumulator> levelExposure = EXPOSURE_BY_LEVEL.get(level);
            if (levelExposure == null) {
                continue;
            }
            levelExposure.entrySet().removeIf(entry -> {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
                return player == null || player.level() != level || !player.isAlive() || player.isRemoved()
                        || !entry.getValue().activeAt(serverTick);
            });
            if (levelExposure.isEmpty()) {
                emptyLevels.add(level);
            }
        }
        emptyLevels.forEach(EXPOSURE_BY_LEVEL::remove);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EXPOSURE_BY_LEVEL.remove(level);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }

    private static void clearPlayer(UUID playerId) {
        EXPOSURE_BY_LEVEL.values().forEach(levelExposure -> levelExposure.remove(playerId));
        EXPOSURE_BY_LEVEL.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
