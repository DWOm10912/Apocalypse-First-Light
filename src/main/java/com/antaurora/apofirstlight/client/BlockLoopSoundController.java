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
import com.antaurora.apofirstlight.cabin.BoxStove;
import com.antaurora.apofirstlight.cabin.BoxStoveBlockEntity;
import com.antaurora.apofirstlight.cabin.EmergencyRadio;
import com.antaurora.apofirstlight.cabin.EmergencyRadioBlockEntity;
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
 * the chest freezer's compressor while it runs (the freezer's the same loop, deeper), the intake pump's motor while it pumps (its own loop; it waits for the start sound's wind-up and fades in), the diesel generator's engine while it runs (heard to 24), the box stove's fire / boiling / frying and the crank radio's static (2026-10-14; a block can play several sources at once). Same scan as CrusherSoundController (every 5 ticks, loaded chunks around the player); each
 * sound stops itself when its condition ends, the block entity is gone, or the player leaves the range.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockLoopSoundController {
    private static final double AUDIBLE_RADIUS = 8.0D;
    /** The diesel generator's engine is heard further (its loop's attenuation distance in sounds.json is 24 too). */
    private static final double GENERATOR_RADIUS = 24.0D;
    /** The portable diesel generator: an open frame, louder (sounds.json attenuation 32 too). */
    private static final double PORTABLE_RADIUS = 32.0D;
    private static final int SCAN_RADIUS_CHUNKS = 3;   // covers the longest radius (32) from anywhere in the player's chunk
    private static final int SCAN_INTERVAL_TICKS = 5;

    private record Source<T extends BlockEntity>(Class<T> type, Predicate<T> active, Function<T, Vec3> position,
                                                 Supplier<SoundEvent> sound, float pitch, int delayTicks, int fadeTicks,
                                                 double radius) {
        Source(Class<T> type, Predicate<T> active, Function<T, Vec3> position, Supplier<SoundEvent> sound, float pitch) {
            this(type, active, position, sound, pitch, 0, 0, AUDIBLE_RADIUS);
        }

        Source(Class<T> type, Predicate<T> active, Function<T, Vec3> position, Supplier<SoundEvent> sound, float pitch,
               int delayTicks, int fadeTicks) {
            this(type, active, position, sound, pitch, delayTicks, fadeTicks, AUDIBLE_RADIUS);
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
            // Wood Furniture V2: the vintage chest freezer's compressor (the same loop, deeper)
            new Source<>(com.antaurora.apofirstlight.cabin.VintageFreezerBlockEntity.class,
                    com.antaurora.apofirstlight.cabin.VintageFreezerBlockEntity::compressorRunning,
                    com.antaurora.apofirstlight.cabin.VintageFreezerBlockEntity::compressorPosition,
                    AflSounds.BEVERAGE_COOLER_COMPRESSOR_LOOP, com.antaurora.apofirstlight.cabin.VintageFreezerBlockEntity.COMPRESSOR_PITCH),
            // the intake pump's motor while it pumps (the status lamp green): the cooler compressor loop, higher, until it has its own
            new Source<>(IntakePumpBlockEntity.class,
                    pump -> pump.getBlockState().getBlock() instanceof IntakePumpBlock
                            && pump.getBlockState().getValue(IntakePumpBlock.LAMP) == IntakePumpBlock.Lamp.RUN,
                    pump -> IntakePumpBlock.world(pump.getBlockPos(), pump.getBlockState().getValue(IntakePumpBlock.FACING),
                            IntakePumpBlockEntity.MOTOR[0], IntakePumpBlockEntity.MOTOR[1], IntakePumpBlockEntity.MOTOR[2]),
                    AflSounds.INTAKE_PUMP_LOOP, 1.0F, IntakePumpBlockEntity.LOOP_FADE_IN[0], IntakePumpBlockEntity.LOOP_FADE_IN[1]),
            // the diesel generator's engine while it runs, heard to 24 blocks; it waits for the start sound's flare to settle
            // and fades in under its fade-out (all cut from one take: tools/build-diesel-generator-sounds-v1.mjs)
            new Source<>(com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity.class,
                    com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity::running,
                    com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity::engineWorld,
                    AflSounds.DIESEL_GENERATOR_RUN, 1.0F, com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity.LOOP_FADE_IN[0],
                    com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity.LOOP_FADE_IN[1], GENERATOR_RADIUS),
            // the portable diesel generator's engine while it runs, heard as far as it is loud; it waits for the start sound
            // and fades in under its fade-out (all cut from one take: tools/build-portable-generator-sounds-v1.mjs)
            new Source<>(com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity.class,
                    com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity::running,
                    com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity::engineWorld,
                    AflSounds.PORTABLE_GENERATOR_RUN, 1.0F, com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity.LOOP_FADE_IN[0],
                    com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity.LOOP_FADE_IN[1], PORTABLE_RADIUS),
            // the start home (forest home sounds V1, 2026-10-14), each heard as far as its noise: the box stove's fire while
            // it burns, over it a pot boiling or a pan frying while something cooks (a stove can play all three); the crank
            // radio's static while it plays. Each fades in over half a second.
            new Source<>(BoxStoveBlockEntity.class, stove -> stove.getBlockState().getValue(BoxStove.Stove.LIT),
                    stove -> BoxStove.Stove.doorPoint(stove.getBlockState(), stove.getBlockPos()),
                    AflSounds.BOX_STOVE_FIRE_LOOP, 1.0F, 0, 10, BoxStove.FIRE_RADIUS),
            new Source<>(BoxStoveBlockEntity.class, BoxStoveBlockEntity::boiling, BlockLoopSoundController::stoveTop,
                    AflSounds.BOX_STOVE_BOIL_LOOP, 1.0F, 0, 10, BoxStove.BOIL_RADIUS),
            new Source<>(BoxStoveBlockEntity.class, BoxStoveBlockEntity::frying, BlockLoopSoundController::stoveTop,
                    AflSounds.BOX_STOVE_FRY_LOOP, 1.0F, 0, 10, BoxStove.FRY_RADIUS),
            new Source<>(EmergencyRadioBlockEntity.class, radio -> radio.getBlockState().getBlock() instanceof EmergencyRadio.Radio
                            && radio.getBlockState().getValue(EmergencyRadio.Radio.LIT), radio -> radio.getBlockPos().getCenter(),
                    AflSounds.EMERGENCY_RADIO_STATIC_LOOP, 1.0F, 0, 10, EmergencyRadio.STATIC_RADIUS));
    /** One loop per block and source (a stove plays its fire and its cooking together). */
    private record Key(BlockPos position, Source<?> source) {
    }

    private static final Map<Key, LoopSound> ACTIVE_SOUNDS = new HashMap<>();

    private static Vec3 stoveTop(BoxStoveBlockEntity stove) {
        return stove.getBlockPos().getCenter().add(0, BoxStove.TOP - 0.5 + 0.1, 0);
    }

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
                    for (Source<?> source : SOURCES) {
                        Key key = new Key(position.immutable(), source);
                        if (ACTIVE_SOUNDS.containsKey(key) || !source.activeOn(blockEntity)) continue;
                        Vec3 at = source.positionOf(blockEntity);
                        if (minecraft.player.distanceToSqr(at) > source.radius() * source.radius()) continue;
                        LoopSound sound = new LoopSound(level, key.position(), source, at);
                        minecraft.getSoundManager().play(sound);
                        ACTIVE_SOUNDS.put(key, sound);
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
                    || minecraft.player.distanceToSqr(x, y, z) > (source.radius() + 1.0D) * (source.radius() + 1.0D)) stop();
        }

        private void stopNow() {
            stop();
        }
    }
}
