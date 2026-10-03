package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Progressive Container Search: the search sound (a seamless loop, tools/build-container-search-sounds-v2.mjs) looping
 * at a container while its search runs, for everyone within 8 blocks (the attenuation distance in sounds.json). Driven by
 * ContainerSearchSoundS2CPacket: "running" starts the loop (or keeps it alive; the server repeats it every second, so a
 * player walking up later hears it too), "stopped" ends it. Each loop fades in and out over 3 ticks (no click) and ends
 * itself when the reminders stop, the container is gone, or the player leaves the range.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContainerSearchSoundController {
    private static final double AUDIBLE_RADIUS = 8.0D;
    /** The server repeats "running" every 20 ticks; after this long without one, the loop fades out by itself. */
    private static final int TIMEOUT_TICKS = 40;
    private static final float FADE_PER_TICK = 1.0F / 3.0F;
    private static final Map<BlockPos, Loop> LOOPS = new HashMap<>();
    private static ClientLevel trackedLevel;

    private ContainerSearchSoundController() {
    }

    public static void receive(BlockPos pos, @Nullable ResourceLocation soundId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        if (level != trackedLevel) reset(level);
        Loop loop = LOOPS.get(pos);
        if (soundId == null) {
            if (loop != null) loop.fadeOut();
            return;
        }
        if (loop != null && loop.alive()) {
            loop.heard(level.getGameTime());
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
        Vec3 at = Vec3.atCenterOf(pos);
        if (sound == null || minecraft.player.distanceToSqr(at) > AUDIBLE_RADIUS * AUDIBLE_RADIUS) return;
        loop = new Loop(level, pos.immutable(), sound, at);
        minecraft.getSoundManager().play(loop);
        LOOPS.put(pos.immutable(), loop);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != trackedLevel) reset(level);
        LOOPS.values().removeIf(Loop::isStopped);
    }

    private static void reset(@Nullable ClientLevel level) {
        LOOPS.values().forEach(Loop::stopNow);
        LOOPS.clear();
        trackedLevel = level;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos pos;
        private long heardAt;
        private boolean fading;

        private Loop(ClientLevel level, BlockPos pos, SoundEvent sound, Vec3 at) {
            super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.pos = pos;
            this.heardAt = level.getGameTime();
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        private void heard(long gameTime) {
            heardAt = gameTime;
        }

        private boolean alive() {
            return !isStopped() && !fading;
        }

        private void fadeOut() {
            fading = true;
        }

        private void stopNow() {
            stop();
        }

        /** Starts at volume 0 and fades in. */
        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != level || minecraft.player == null) {
                stop();
                return;
            }
            if (!level.isLoaded(pos) || level.getBlockEntity(pos) == null || level.getGameTime() - heardAt > TIMEOUT_TICKS
                    || minecraft.player.distanceToSqr(x, y, z) > (AUDIBLE_RADIUS + 1.0D) * (AUDIBLE_RADIUS + 1.0D)) fading = true;
            volume = Math.max(0.0F, Math.min(1.0F, volume + (fading ? -FADE_PER_TICK : FADE_PER_TICK)));
            if (fading && volume <= 0.0F) stop();
        }
    }
}
