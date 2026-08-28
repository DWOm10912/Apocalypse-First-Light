package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CrusherBlock;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrusherSoundController {
    private static final int SCAN_RADIUS_CHUNKS = 2;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final Map<BlockPos, CrusherRunningSound> ACTIVE_SOUNDS = new HashMap<>();

    private static ClientLevel trackedLevel;
    private static int scanDelay;

    private CrusherSoundController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            reset(level);
        }
        ACTIVE_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (level == null || minecraft.player == null || scanDelay-- > 0) {
            return;
        }
        scanDelay = SCAN_INTERVAL_TICKS - 1;

        int centerChunkX = minecraft.player.getBlockX() >> 4;
        int centerChunkZ = minecraft.player.getBlockZ() >> 4;
        for (int chunkX = centerChunkX - SCAN_RADIUS_CHUNKS;
             chunkX <= centerChunkX + SCAN_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = centerChunkZ - SCAN_RADIUS_CHUNKS;
                 chunkZ <= centerChunkZ + SCAN_RADIUS_CHUNKS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                chunk.getBlockEntities().forEach((position, blockEntity) -> {
                    if (blockEntity instanceof CrusherBlockEntity
                            && level.getBlockState(position).is(AflBlocks.CRUSHER.get())
                            && level.getBlockState(position).getValue(CrusherBlock.LIT)
                            && CrusherRunningSound.isInAudibleRange(
                            minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), position)) {
                        startIfAbsent(minecraft, level, position);
                    }
                });
            }
        }
    }

    private static void startIfAbsent(Minecraft minecraft, ClientLevel level, BlockPos position) {
        BlockPos key = position.immutable();
        if (ACTIVE_SOUNDS.containsKey(key)) {
            return;
        }
        CrusherRunningSound sound = new CrusherRunningSound(level, key);
        minecraft.getSoundManager().play(sound);
        ACTIVE_SOUNDS.put(key, sound);
    }

    private static void reset(ClientLevel level) {
        ACTIVE_SOUNDS.values().forEach(CrusherRunningSound::stopNow);
        ACTIVE_SOUNDS.clear();
        trackedLevel = level;
        scanDelay = 0;
    }
}
