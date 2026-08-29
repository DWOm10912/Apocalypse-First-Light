package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CrusherBlock;
import com.antaurora.apofirstlight.block.IndustrialFurnaceBlock;
import com.antaurora.apofirstlight.block.CompressorBlock;
import com.antaurora.apofirstlight.block.AlloyFurnaceBlock;
import com.antaurora.apofirstlight.blockentity.CrusherBlockEntity;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.blockentity.CompressorBlockEntity;
import com.antaurora.apofirstlight.blockentity.AlloyFurnaceBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
    private static final float CRUSHER_VOLUME = 0.40F;
    private static final float INDUSTRIAL_FURNACE_VOLUME = 0.35F;
    private static final float COMPRESSOR_VOLUME = 0.40F;
    private static final float ALLOY_FURNACE_VOLUME = 0.35F;
    private static final Map<BlockPos, MachineRunningSound> ACTIVE_SOUNDS = new HashMap<>();

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
                    BlockState state = level.getBlockState(position);
                    if (!MachineRunningSound.isInAudibleRange(
                            minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), position)) {
                        return;
                    }
                    if (blockEntity instanceof CrusherBlockEntity
                            && state.is(AflBlocks.CRUSHER.get())
                            && state.getValue(CrusherBlock.LIT)) {
                        startIfAbsent(minecraft, level, position,
                                AflSounds.CRUSHER_RUNNING.get(), AflBlocks.CRUSHER.get(),
                                CrusherBlock.LIT, CRUSHER_VOLUME);
                    } else if (blockEntity instanceof IndustrialFurnaceBlockEntity
                            && state.is(AflBlocks.INDUSTRIAL_FURNACE.get())
                            && state.getValue(IndustrialFurnaceBlock.LIT)) {
                        startIfAbsent(minecraft, level, position,
                                AflSounds.INDUSTRIAL_FURNACE_RUNNING.get(), AflBlocks.INDUSTRIAL_FURNACE.get(),
                                IndustrialFurnaceBlock.LIT, INDUSTRIAL_FURNACE_VOLUME);
                    } else if (blockEntity instanceof CompressorBlockEntity
                            && state.is(AflBlocks.COMPRESSOR.get())
                            && state.getValue(CompressorBlock.LIT)) {
                        startIfAbsent(minecraft, level, position,
                                AflSounds.COMPRESSOR_RUNNING.get(), AflBlocks.COMPRESSOR.get(),
                                CompressorBlock.LIT, COMPRESSOR_VOLUME);
                    } else if (blockEntity instanceof AlloyFurnaceBlockEntity
                            && state.is(AflBlocks.ALLOY_FURNACE.get())
                            && state.getValue(AlloyFurnaceBlock.LIT)) {
                        startIfAbsent(minecraft, level, position,
                                AflSounds.ALLOY_FURNACE_RUNNING.get(), AflBlocks.ALLOY_FURNACE.get(),
                                AlloyFurnaceBlock.LIT, ALLOY_FURNACE_VOLUME);
                    }
                });
            }
        }
    }

    private static void startIfAbsent(Minecraft minecraft, ClientLevel level, BlockPos position,
                                      SoundEvent soundEvent, Block machineBlock,
                                      BooleanProperty litProperty, float volume) {
        BlockPos key = position.immutable();
        if (ACTIVE_SOUNDS.containsKey(key)) {
            return;
        }
        MachineRunningSound sound = new MachineRunningSound(
                level, key, soundEvent, machineBlock, litProperty, volume);
        minecraft.getSoundManager().play(sound);
        ACTIVE_SOUNDS.put(key, sound);
    }

    private static void reset(ClientLevel level) {
        ACTIVE_SOUNDS.values().forEach(MachineRunningSound::stopNow);
        ACTIVE_SOUNDS.clear();
        trackedLevel = level;
        scanDelay = 0;
    }
}
