package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.ChargingStationBlock;
import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.block.IntakePumpBlock;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity;
import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.antaurora.apofirstlight.blockentity.IntakePumpBlockEntity;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Quiet block loops driven by a block entity's synced state (seamless loops from tools/sound-mix-lib.mjs buildLoop; their
 * attenuation distance, 8, is set in sounds.json): the charging station hum while it charges, the beverage cooler's and
 * the chest freezer's compressor while it runs (the freezer's the same loop, deeper), the intake pump's motor while it pumps (its own loop; it waits for the start sound's wind-up and fades in). Same scan as CrusherSoundController (every 5 ticks, loaded chunks around the player); each
 * sound stops itself when its condition ends, the block entity is gone, or the player leaves the range.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockLoopSoundController {
    private static final double AUDIBLE_RADIUS = 8.0D;
    private static final int SCAN_RADIUS_CHUNKS = 1;
    private static final int SCAN_INTERVAL_TICKS = 5;

    private record Source<T extends BlockEntity>(Class<T> type, Predicate<T> active, Function<T, Vec3> position,
                                                 Supplier<SoundEvent> sound, float pitch, int delayTicks, int fadeTicks) {
        Source(Class<T> type, Predicate<T> active, Function<T, Vec3> position, Supplier<SoundEvent> sound, float pitch) {
            this(type, active, position, sound, pitch, 0, 0);
        }

        boolean activeOn(BlockEntity entity) {
            return type.isInstance(entity) && active.test(type.cast(entity));
        }

        Vec3 positionOf(BlockEntity entity) {
            return position.apply(type.cast(entity));
        }
    }

    private static final List<Source<?>> SOURCES = List.of(
            new Source<>(ChargingStationBlockEntity.class, station -> station.isMaster() && station.charging(),
                    station -> ChargingStationBlock.sourceToWorld(station.getBlockPos(),
                            station.getBlockState().getValue(ChargingStationBlock.FACING), 8.0, 8.0, 0.0),
                    AflSounds.CHARGING_STATION_HUM, 1.0F),
            new Source<>(BeverageCoolerBlockEntity.class, BeverageCoolerBlockEntity::compressorRunning,
                    cooler -> BeverageCoolerBlock.compressorPosition(cooler.getBlockPos(),
                            cooler.getBlockState().getValue(BeverageCoolerBlock.FACING)),
                    AflSounds.BEVERAGE_COOLER_COMPRESSOR_LOOP, 1.0F),
            new Source<>(ChestFreezerBlockEntity.class, ChestFreezerBlockEntity::compressorRunning,
                    freezer -> ChestFreezerBlock.compressorPosition(freezer.getBlockPos(),
                            freezer.getBlockState().getValue(ChestFreezerBlock.FACING)),
                    AflSounds.BEVERAGE_COOLER_COMPRESSOR_LOOP, ChestFreezerBlockEntity.COMPRESSOR_PITCH),
            // the intake pump's motor while it pumps (the status lamp green): the cooler compressor loop, higher, until it has its own
            new Source<>(IntakePumpBlockEntity.class,
                    pump -> pump.getBlockState().getBlock() instanceof IntakePumpBlock
                            && pump.getBlockState().getValue(IntakePumpBlock.LAMP) == IntakePumpBlock.Lamp.RUN,
                    pump -> IntakePumpBlock.world(pump.getBlockPos(), pump.getBlockState().getValue(IntakePumpBlock.FACING),
                            IntakePumpBlockEntity.MOTOR[0], IntakePumpBlockEntity.MOTOR[1], IntakePumpBlockEntity.MOTOR[2]),
                    AflSounds.INTAKE_PUMP_LOOP, 1.0F, IntakePumpBlockEntity.LOOP_FADE_IN[0], IntakePumpBlockEntity.LOOP_FADE_IN[1]),
            // the diesel generator's engine while it runs (silent until its recording; then it will want a longer range than 8)
            new Source<>(com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity.class,
                    com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity::running,
                    com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity::engineWorld,
                    AflSounds.DIESEL_GENERATOR_RUN, 1.0F));
    private static final Map<BlockPos, LoopSound> ACTIVE_SOUNDS = new HashMap<>();

    private static ClientLevel trackedLevel;
    private static int scanDelay;

    private BlockLoopSoundController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) reset(level);
        ACTIVE_SOUNDS.values().removeIf(LoopSound::isStopped);
        if (level == null || minecraft.player == null || scanDelay-- > 0) return;
        scanDelay = SCAN_INTERVAL_TICKS - 1;
        int centerX = minecraft.player.getBlockX() >> 4, centerZ = minecraft.player.getBlockZ() >> 4;
        for (int chunkX = centerX - SCAN_RADIUS_CHUNKS; chunkX <= centerX + SCAN_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = centerZ - SCAN_RADIUS_CHUNKS; chunkZ <= centerZ + SCAN_RADIUS_CHUNKS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                chunk.getBlockEntities().forEach((position, blockEntity) -> {
                    if (ACTIVE_SOUNDS.containsKey(position)) return;
                    for (Source<?> source : SOURCES) {
                        if (!source.activeOn(blockEntity)) continue;
                        Vec3 at = source.positionOf(blockEntity);
                        if (minecraft.player.distanceToSqr(at) > AUDIBLE_RADIUS * AUDIBLE_RADIUS) return;
                        LoopSound sound = new LoopSound(level, position.immutable(), source, at);
                        minecraft.getSoundManager().play(sound);
                        ACTIVE_SOUNDS.put(position.immutable(), sound);
                        return;
                    }
                });
            }
        }
    }

    private static void reset(ClientLevel level) {
        ACTIVE_SOUNDS.values().forEach(LoopSound::stopNow);
        ACTIVE_SOUNDS.clear();
        trackedLevel = level;
        scanDelay = 0;
    }

    private static final class LoopSound extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos position;
        private final Source<?> source;
        private int age;

        private LoopSound(ClientLevel level, BlockPos position, Source<?> source, Vec3 at) {
            super(source.sound().get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.position = position;
            this.source = source;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = source.fadeTicks() > 0 ? 0.0F : 1.0F;
            this.pitch = source.pitch();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (source.fadeTicks() > 0) volume = Math.max(0.0F, Math.min(1.0F, (float) (++age - source.delayTicks()) / source.fadeTicks()));
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != level || minecraft.player == null || !level.isLoaded(position)
                    || !source.activeOn(level.getBlockEntity(position))
                    || minecraft.player.distanceToSqr(x, y, z) > (AUDIBLE_RADIUS + 1.0D) * (AUDIBLE_RADIUS + 1.0D)) stop();
        }

        private void stopNow() {
            stop();
        }
    }
}
