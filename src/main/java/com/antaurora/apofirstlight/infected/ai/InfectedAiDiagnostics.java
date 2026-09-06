package com.antaurora.apofirstlight.infected.ai;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.InfectedEntityRules;
import com.antaurora.apofirstlight.infected.breach.InfectedBreakerClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Development-only counters. Production builds keep the hooks disabled and emit no log output. */
public final class InfectedAiDiagnostics {
    private static final boolean ENABLED = !FMLEnvironment.production;
    private static final Map<ServerLevel, MutableCounters> COUNTERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Long> LAST_LOGGED_TICK =
            Collections.synchronizedMap(new WeakHashMap<>());

    private InfectedAiDiagnostics() {
    }

    public static void obstacleScan(ServerLevel level) {
        if (!ENABLED) return;
        counters(level).obstacleScans++;
    }

    public static void pathAttempt(ServerLevel level) {
        if (!ENABLED) return;
        counters(level).pathAttempts++;
    }

    public static void breakTargetAcquired(ServerLevel level) {
        if (!ENABLED) return;
        counters(level).breakTargetAcquisitions++;
    }

    public static void failedPathRetry(ServerLevel level) {
        if (!ENABLED) return;
        counters(level).failedPathRetries++;
    }

    public static void noiseTargetUpdate(ServerLevel level, boolean duplicateSuppressed) {
        if (!ENABLED) return;
        MutableCounters counters = counters(level);
        counters.noiseTargetUpdates++;
        if (duplicateSuppressed) {
            counters.duplicateNoiseRefreshesSuppressed++;
        }
    }

    public static Snapshot snapshot(ServerLevel level) {
        if (!ENABLED) {
            return Snapshot.EMPTY;
        }
        MutableCounters counters = COUNTERS.get(level);
        if (counters == null) {
            return Snapshot.EMPTY;
        }
        return counters.snapshot();
    }

    public static void reset(ServerLevel level) {
        if (ENABLED) {
            COUNTERS.put(level, new MutableCounters());
        }
    }

    public static void maybeLog(ServerLevel level) {
        if (!ENABLED || level.getGameTime() % 200L != 0L
                || LAST_LOGGED_TICK.getOrDefault(level, Long.MIN_VALUE) == level.getGameTime()) {
            return;
        }
        LAST_LOGGED_TICK.put(level, level.getGameTime());
        int infectedCount = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living
                    && InfectedEntityRules.isInfected(living)) {
                infectedCount++;
            }
        }
        Snapshot snapshot = snapshot(level);
        ApocalypseFirstLight.LOGGER.info(
                "[AFL AI PERF] infected={} scans={} pathAttempts={} acquisitions={} activeClaims={} failedPathRetries={} noiseUpdates={} duplicateRefreshSuppressed={}",
                infectedCount, snapshot.obstacleScans(), snapshot.pathAttempts(),
                snapshot.breakTargetAcquisitions(), InfectedBreakerClaims.activeClaimCount(level),
                snapshot.failedPathRetries(), snapshot.noiseTargetUpdates(),
                snapshot.duplicateNoiseRefreshesSuppressed()
        );
    }

    public static void clear(ServerLevel level) {
        COUNTERS.remove(level);
        LAST_LOGGED_TICK.remove(level);
    }

    private static MutableCounters counters(ServerLevel level) {
        return COUNTERS.computeIfAbsent(level, ignored -> new MutableCounters());
    }

    private static final class MutableCounters {
        private long obstacleScans;
        private long pathAttempts;
        private long breakTargetAcquisitions;
        private long failedPathRetries;
        private long noiseTargetUpdates;
        private long duplicateNoiseRefreshesSuppressed;

        private Snapshot snapshot() {
            return new Snapshot(obstacleScans, pathAttempts, breakTargetAcquisitions,
                    failedPathRetries, noiseTargetUpdates, duplicateNoiseRefreshesSuppressed);
        }
    }

    public record Snapshot(long obstacleScans, long pathAttempts, long breakTargetAcquisitions,
                           long failedPathRetries, long noiseTargetUpdates,
                           long duplicateNoiseRefreshesSuppressed) {
        private static final Snapshot EMPTY = new Snapshot(0L, 0L, 0L, 0L, 0L, 0L);
    }
}
