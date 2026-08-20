package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Low-frequency, biome-driven client ambience with short gusts and a small particle cap. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EnvironmentalParticleController {
    private static final int MAX_ACTIVE_PARTICLES = 64;
    private static final int MIN_SAMPLE_RADIUS = 12;
    private static final int MAX_SAMPLE_RADIUS = 96;
    private static final int NORMAL_PARTICLE_RADIUS = 32;
    private static int activeParticles;
    private static ClientLevel trackedLevel;
    private static long nextGustStart = -1L;
    private static long gustEnd = -1L;
    private static long nextSample;
    private static long diagnosticWindowStart = -1L;
    private static long diagnosticTicks;
    private static long diagnosticSamples;
    private static long diagnosticChunkMiss;
    private static long diagnosticBiomeReject;
    private static long diagnosticBadSurface;
    private static long diagnosticFluidReject;
    private static long diagnosticNonAirReject;
    private static long diagnosticSkyReject;
    private static long diagnosticChanceReject;
    private static long diagnosticSpawnSuccess;

    private EnvironmentalParticleController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        if (trackedLevel != level) reset(level);

        long gameTime = level.getGameTime();
        beginDiagnosticWindow(gameTime);
        diagnosticTicks++;
        updateGust(gameTime, level.random);
        if (gameTime < nextSample || activeParticles >= MAX_ACTIVE_PARTICLES) return;

        boolean gusting = gameTime < gustEnd;
        nextSample = gameTime + (gusting ? 2L : 4L);
        int sampleRadius = Math.max(MIN_SAMPLE_RADIUS,
                Math.min(minecraft.options.renderDistance().get() * 16, MAX_SAMPLE_RADIUS));
        int candidateCount = gusting ? 12 : 8;
        int spawnLimit = gusting ? 2 : 1;
        int spawned = 0;
        for (int sample = 0; sample < candidateCount && spawned < spawnLimit; sample++) {
            if (spawnCandidate(level, minecraft.player.blockPosition(), sampleRadius, gusting, level.random)) {
                spawned++;
            }
        }
    }

    private static void updateGust(long gameTime, RandomSource random) {
        if (gustEnd >= gameTime) return;
        if (nextGustStart < 0L) {
            nextGustStart = gameTime + 60L + random.nextInt(81);
            return;
        }
        if (gameTime >= nextGustStart) {
            gustEnd = gameTime + 40L + random.nextInt(41);
            nextGustStart = gustEnd + 60L + random.nextInt(81);
        }
    }

    private static boolean spawnCandidate(ClientLevel level, BlockPos playerPos, int maxRadius, boolean gusting,
                                          RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = sampleDistance(maxRadius, random);
        int x = (int) Math.floor(playerPos.getX() + 0.5D + Math.cos(angle) * distance);
        int z = (int) Math.floor(playerPos.getZ() + 0.5D + Math.sin(angle) * distance);
        diagnosticSamples++;
        if (!level.hasChunkAt(new BlockPos(x, playerPos.getY(), z))) {
            diagnosticChunkMiss++;
            return false;
        }
        BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        EnvironmentalParticleProfile profile = EnvironmentalParticleProfile.at(level, surface);
        if (profile == EnvironmentalParticleProfile.NONE) {
            diagnosticBiomeReject++;
            return false;
        }
        if (level.getBlockState(surface).isAir()) {
            diagnosticBadSurface++;
            return false;
        }
        if (!level.getFluidState(surface).isEmpty()) {
            diagnosticFluidReject++;
            return false;
        }

        BlockPos candidate = surface.above(1 + random.nextInt(5));
        if (!level.getFluidState(candidate).isEmpty()) {
            diagnosticFluidReject++;
            return false;
        }
        if (!level.getBlockState(candidate).isAir()) {
            diagnosticNonAirReject++;
            return false;
        }
        if (!level.canSeeSky(candidate)) {
            diagnosticSkyReject++;
            return false;
        }

        double weight = distance <= 32.0D ? 1.0D : distance <= 64.0D ? 0.65D : 0.35D;
        double spawnChance = (gusting ? 0.72D : 0.45D) * weight;
        if (random.nextDouble() >= spawnChance) {
            diagnosticChanceReject++;
            return false;
        }

        double speed = profile == EnvironmentalParticleProfile.FALLOUT_DUST
                ? 0.003D + random.nextDouble() * 0.010D
                : 0.008D + random.nextDouble() * 0.017D;
        double driftAngle = angle + Math.PI + (random.nextDouble() - 0.5D) * 0.8D;
        double particleX = candidate.getX() + 0.5D;
        double particleY = candidate.getY() + 0.5D;
        double particleZ = candidate.getZ() + 0.5D;
        double velocityX = Math.cos(driftAngle) * speed;
        double velocityY = profile == EnvironmentalParticleProfile.FALLOUT_DUST
                ? -(0.0015D + random.nextDouble() * 0.0035D)
                : -(0.004D + random.nextDouble() * 0.008D);
        double velocityZ = Math.sin(driftAngle) * speed;
        if (distance > NORMAL_PARTICLE_RADIUS) {
            var particle = profile == EnvironmentalParticleProfile.FALLOUT_DUST
                    ? AflParticles.FALLOUT_DUST.get() : AflParticles.DEAD_LEAF_DEBRIS.get();
            level.addAlwaysVisibleParticle(particle, false,
                    particleX, particleY, particleZ, velocityX, velocityY, velocityZ);
        } else {
            var particle = profile == EnvironmentalParticleProfile.FALLOUT_DUST
                    ? AflParticles.FALLOUT_DUST.get() : AflParticles.DEAD_LEAF_DEBRIS.get();
            level.addParticle(particle,
                    particleX, particleY, particleZ, velocityX, velocityY, velocityZ);
        }
        diagnosticSpawnSuccess++;
        return true;
    }

    private static double sampleDistance(int maxRadius, RandomSource random) {
        if (maxRadius <= 32) {
            return MIN_SAMPLE_RADIUS + random.nextDouble() * (maxRadius - MIN_SAMPLE_RADIUS);
        }
        double roll = random.nextDouble();
        if (roll < 0.55D) {
            return MIN_SAMPLE_RADIUS + random.nextDouble() * 20.0D;
        }
        if (roll < 0.85D) {
            return 32.0D + random.nextDouble() * (Math.min(64, maxRadius) - 32.0D);
        }
        return 64.0D + random.nextDouble() * (maxRadius - 64.0D);
    }

    public static void debugSpawnForced() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        var origin = minecraft.player.getEyePosition().add(minecraft.player.getLookAngle().scale(2.5D));
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 10; i++) {
            minecraft.level.addParticle(AflParticles.DEAD_LEAF_DEBRIS.get(),
                    origin.x + (random.nextDouble() - 0.5D) * 0.8D,
                    origin.y + (random.nextDouble() - 0.5D) * 0.8D,
                    origin.z + (random.nextDouble() - 0.5D) * 0.8D,
                    (random.nextDouble() - 0.5D) * 0.01D, -0.003D,
                    (random.nextDouble() - 0.5D) * 0.01D);
        }
    }

    public static String debugStatus() {
        return "windowTicks=" + diagnosticTicks
                + " | ticks=" + diagnosticTicks
                + " | samples=" + diagnosticSamples
                + " | chunkMiss=" + diagnosticChunkMiss
                + " | biomeReject=" + diagnosticBiomeReject
                + " | badSurface=" + diagnosticBadSurface
                + " | fluidReject=" + diagnosticFluidReject
                + " | nonAirReject=" + diagnosticNonAirReject
                + " | skyReject=" + diagnosticSkyReject
                + " | chanceReject=" + diagnosticChanceReject
                + " | spawnSuccess=" + diagnosticSpawnSuccess
                + " | activeParticles=" + activeParticles;
    }

    public static void resetDiagnostics() {
        diagnosticWindowStart = Minecraft.getInstance().level == null
                ? -1L : Minecraft.getInstance().level.getGameTime();
        diagnosticTicks = 0L;
        diagnosticSamples = 0L;
        diagnosticChunkMiss = 0L;
        diagnosticBiomeReject = 0L;
        diagnosticBadSurface = 0L;
        diagnosticFluidReject = 0L;
        diagnosticNonAirReject = 0L;
        diagnosticSkyReject = 0L;
        diagnosticChanceReject = 0L;
        diagnosticSpawnSuccess = 0L;
    }

    static void onParticleCreated() {
        activeParticles++;
    }

    static void onParticleRemoved() {
        activeParticles = Math.max(0, activeParticles - 1);
    }

    private static void reset(ClientLevel level) {
        trackedLevel = level;
        activeParticles = 0;
        nextGustStart = -1L;
        gustEnd = -1L;
        nextSample = 0L;
        resetDiagnostics();
    }

    private static void beginDiagnosticWindow(long gameTime) {
        if (diagnosticWindowStart < 0L || gameTime - diagnosticWindowStart >= 200L) {
            resetDiagnostics();
            diagnosticWindowStart = gameTime;
        }
    }
}
