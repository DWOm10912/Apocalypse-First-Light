package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.ChargingStationBlock;
import com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Charging Station hum: a quiet loop (sounds/charging_station/hum.ogg, a 0.6 s seamless loop, attenuation distance 8)
 * from the middle of every station within 8 blocks that is charging (ChargingStationBlockEntity#charging: powered, an
 * item on the tray, not full). Same scan as CrusherSoundController (every 5 ticks, loaded chunks around the player); each
 * sound stops itself when the station stops charging, is gone, or the player leaves the range.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChargingStationSoundController {
    private static final double AUDIBLE_RADIUS = 8.0D;
    private static final int SCAN_RADIUS_CHUNKS = 1;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final Map<BlockPos, HumSound> ACTIVE_SOUNDS = new HashMap<>();

    private static ClientLevel trackedLevel;
    private static int scanDelay;

    private ChargingStationSoundController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) reset(level);
        ACTIVE_SOUNDS.values().removeIf(HumSound::isStopped);
        if (level == null || minecraft.player == null || scanDelay-- > 0) return;
        scanDelay = SCAN_INTERVAL_TICKS - 1;
        int centerX = minecraft.player.getBlockX() >> 4, centerZ = minecraft.player.getBlockZ() >> 4;
        for (int chunkX = centerX - SCAN_RADIUS_CHUNKS; chunkX <= centerX + SCAN_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = centerZ - SCAN_RADIUS_CHUNKS; chunkZ <= centerZ + SCAN_RADIUS_CHUNKS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                chunk.getBlockEntities().forEach((position, blockEntity) -> {
                    if (!(blockEntity instanceof ChargingStationBlockEntity station) || !station.isMaster() || !station.charging()
                            || ACTIVE_SOUNDS.containsKey(position)) return;
                    Vec3 centre = centre(station);
                    if (minecraft.player.distanceToSqr(centre) > AUDIBLE_RADIUS * AUDIBLE_RADIUS) return;
                    HumSound sound = new HumSound(level, position.immutable(), centre);
                    minecraft.getSoundManager().play(sound);
                    ACTIVE_SOUNDS.put(position.immutable(), sound);
                });
            }
        }
    }

    private static Vec3 centre(ChargingStationBlockEntity station) {
        return ChargingStationBlock.sourceToWorld(station.getBlockPos(),
                station.getBlockState().getValue(ChargingStationBlock.FACING), 8.0, 8.0, 0.0);
    }

    private static void reset(ClientLevel level) {
        ACTIVE_SOUNDS.values().forEach(HumSound::stopNow);
        ACTIVE_SOUNDS.clear();
        trackedLevel = level;
        scanDelay = 0;
    }

    private static final class HumSound extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos master;

        private HumSound(ClientLevel level, BlockPos master, Vec3 centre) {
            super(AflSounds.CHARGING_STATION_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.master = master;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.x = centre.x;
            this.y = centre.y;
            this.z = centre.z;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != level || minecraft.player == null || !level.isLoaded(master)
                    || !(level.getBlockEntity(master) instanceof ChargingStationBlockEntity station) || !station.charging()
                    || minecraft.player.distanceToSqr(x, y, z) > (AUDIBLE_RADIUS + 1.0D) * (AUDIBLE_RADIUS + 1.0D)) stop();
        }

        private void stopNow() {
            stop();
        }
    }
}
