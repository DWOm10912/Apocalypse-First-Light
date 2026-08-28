package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public final class MachineRunningSound extends AbstractTickableSoundInstance {
    public static final double AUDIBLE_RADIUS_BLOCKS = 10.0D;
    public static final double AUDIBLE_RADIUS_SQR = AUDIBLE_RADIUS_BLOCKS * AUDIBLE_RADIUS_BLOCKS;

    private final ClientLevel level;
    private final BlockPos position;
    private final Block machineBlock;
    private final BooleanProperty litProperty;

    public MachineRunningSound(ClientLevel level, BlockPos position, SoundEvent soundEvent,
                               Block machineBlock, BooleanProperty litProperty, float volume) {
        super(soundEvent, SoundSource.BLOCKS, RandomSource.create());
        this.level = level;
        this.position = position.immutable();
        this.machineBlock = machineBlock;
        this.litProperty = litProperty;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.volume = volume;
        this.pitch = 1.0F;
        this.x = position.getX() + 0.5D;
        this.y = position.getY() + 0.5D;
        this.z = position.getZ() + 0.5D;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level
                || minecraft.player == null
                || !level.hasChunkAt(position)
                || !isInAudibleRange(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), position)) {
            stop();
            return;
        }

        BlockState state = level.getBlockState(position);
        if (!state.is(machineBlock) || !state.hasProperty(litProperty) || !state.getValue(litProperty)) {
            stop();
        }
    }

    public void stopNow() {
        stop();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return soundBuffers.getStream(sound.getPath(), looping).thenApply(stream ->
                stream.getFormat().getChannels() == 1 ? stream : new StereoToMonoAudioStream(stream));
    }

    public static boolean isInAudibleRange(double playerX, double playerY, double playerZ, BlockPos position) {
        double soundX = position.getX() + 0.5D;
        double soundY = position.getY() + 0.5D;
        double soundZ = position.getZ() + 0.5D;
        double offsetX = playerX - soundX;
        double offsetY = playerY - soundY;
        double offsetZ = playerZ - soundZ;
        return offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ <= AUDIBLE_RADIUS_SQR;
    }

    private static final class StereoToMonoAudioStream implements AudioStream {
        private final AudioStream source;
        private final AudioFormat monoFormat;

        private StereoToMonoAudioStream(AudioStream source) {
            this.source = source;
            AudioFormat sourceFormat = source.getFormat();
            if (sourceFormat.getChannels() != 2 || sourceFormat.getSampleSizeInBits() != 16) {
                throw new IllegalArgumentException("Machine running sound must be mono or 16-bit stereo");
            }
            this.monoFormat = new AudioFormat(sourceFormat.getSampleRate(), 16, 1, true, false);
        }

        @Override
        public AudioFormat getFormat() {
            return monoFormat;
        }

        @Override
        public ByteBuffer read(int requestedBytes) throws IOException {
            int monoBytes = requestedBytes & ~1;
            ByteBuffer stereo = source.read(monoBytes * 2);
            int frameCount = stereo.remaining() / 4;
            ByteBuffer mono = BufferUtils.createByteBuffer(frameCount * 2);
            for (int frame = 0; frame < frameCount; frame++) {
                int left = stereo.getShort();
                int right = stereo.getShort();
                mono.putShort((short) ((left + right) / 2));
            }
            return mono.flip();
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }
}
