package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.registry.AflParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AmbientRadiationParticleManager {
    public static final int CELL_SIZE = 16;
    public static final int CELL_RADIUS = 2;
    public static final int BROADCAST_RADIUS = 64;

    private AmbientRadiationParticleManager() {
    }

    public static void tick(ServerLevel level) {
        Set<Long> activeCells = new HashSet<>();
        Map<Long, Integer> referenceHeights = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            int cellX = Math.floorDiv(player.blockPosition().getX(), CELL_SIZE);
            int cellZ = Math.floorDiv(player.blockPosition().getZ(), CELL_SIZE);
            for (int dx = -CELL_RADIUS; dx <= CELL_RADIUS; dx++) {
                for (int dz = -CELL_RADIUS; dz <= CELL_RADIUS; dz++) {
                    long key = ChunkPos.asLong(cellX + dx, cellZ + dz);
                    activeCells.add(key);
                    referenceHeights.putIfAbsent(key, player.blockPosition().getY());
                }
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (long key : activeCells) {
            spawnCell(level, ChunkPos.getX(key), ChunkPos.getZ(key),
                    referenceHeights.getOrDefault(key, 64), random);
        }
    }

    private static void spawnCell(ServerLevel level, int cellX, int cellZ, int referenceY,
                                  ThreadLocalRandom random) {
        int centerX = cellX * CELL_SIZE + CELL_SIZE / 2;
        int centerZ = cellZ * CELL_SIZE + CELL_SIZE / 2;
        RadiationSample sample = RadiationManager.getRadiationSample(level, new BlockPos(centerX, 64, centerZ));
        if (sample.finalRadiation() <= 0.0 || sample.zone() == RadiationZone.SAFE) return;

        int count = 0;
        if (sample.zone() == RadiationZone.IRRADIATED) {
            if (random.nextDouble() < 0.15) count = 1;
        } else if (sample.zone() == RadiationZone.HEAVY_FALLOUT) {
            if (random.nextDouble() < 0.38) count = 1;
            if (random.nextDouble() < 0.08) count++;
        } else if (random.nextDouble() < 0.65) {
            count = 1;
            if (random.nextDouble() < 0.12) count++;
        }

        for (int i = 0; i < count; i++) {
            double x = cellX * CELL_SIZE + random.nextDouble(0.0, CELL_SIZE);
            double y = referenceY + random.nextDouble(-1.5, 3.5);
            double z = cellZ * CELL_SIZE + random.nextDouble(0.0, CELL_SIZE);
            sendToNearbyPlayers(level, AflParticles.CONTAMINATION_MOTE.get(), x, y, z, random);
        }
    }

    private static void sendToNearbyPlayers(ServerLevel level, ParticleOptions particle,
                                             double x, double y, double z, ThreadLocalRandom random) {
        for (ServerPlayer recipient : level.players()) {
            if (recipient.isSpectator() || recipient.distanceToSqr(x, y, z) > BROADCAST_RADIUS * BROADCAST_RADIUS) continue;
            level.sendParticles(recipient, particle, true, x, y, z, 1,
                    random.nextDouble(-0.02, 0.02), random.nextDouble(0.005, 0.02),
                    random.nextDouble(-0.02, 0.02), 0.0);
        }
    }
}
