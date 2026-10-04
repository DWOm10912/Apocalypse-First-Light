package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Office chair rolling sound (tools/build-office-chair-sounds-v1.mjs, a seamless 1.4 s loop): loops at every office chair
 * entity within earshot while it moves, whoever moves it (the sitter's client, a pushed chair, someone else's chair).
 * Volume follows the speed (0 at rest, full at the chair's top speed) and fades in / out over a few ticks; the pitch
 * rises from 0.85 to 1.1 with the speed. On wool and carpet it is quieter and lower. The loop stops itself once the chair
 * has stood still and faded out, and starts again when it moves.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OfficeChairRollSound extends AbstractTickableSoundInstance {
    private static final double HEAR_RADIUS = 16.0;
    /** Blocks per tick: slower than this does not start a loop. */
    private static final double START_SPEED = 0.01;
    private static final float PITCH_SLOW = 0.85F, PITCH_FAST = 1.1F;
    private static final float SOFT_VOLUME = 0.45F, SOFT_PITCH = 0.85F;
    private static final float FADE_IN = 0.5F, FADE_OUT = 0.35F;
    private static final int QUIET_TICKS = 5;
    private static final Map<OfficeChairEntity, OfficeChairRollSound> PLAYING = new WeakHashMap<>();

    private final OfficeChairEntity chair;
    private int quietTicks;

    private OfficeChairRollSound(OfficeChairEntity chair) {
        super(AflSounds.OFFICE_CHAIR_ROLL.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.chair = chair;
        looping = true;
        delay = 0;
        volume = 0.0F;
        pitch = PITCH_SLOW;
        attenuation = SoundInstance.Attenuation.LINEAR;
        relative = false;
        follow();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        PLAYING.values().removeIf(AbstractTickableSoundInstance::isStopped);
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) return;
        for (OfficeChairEntity chair : minecraft.level.getEntitiesOfClass(OfficeChairEntity.class,
                minecraft.player.getBoundingBox().inflate(HEAR_RADIUS))) {
            if (PLAYING.containsKey(chair) || speed(chair) < START_SPEED || chair.isSilent()) continue;
            var sound = new OfficeChairRollSound(chair);
            PLAYING.put(chair, sound);
            minecraft.getSoundManager().play(sound);
        }
    }

    /** Horizontal distance the chair moved this tick (blocks). */
    private static double speed(OfficeChairEntity chair) {
        return Math.hypot(chair.getX() - chair.xo, chair.getZ() - chair.zo);
    }

    private void follow() {
        x = chair.getX();
        y = chair.getY() + 0.2;
        z = chair.getZ();
    }

    /** Wool and carpet (the carpet in the chair's own cell, or a wool block under it). */
    private boolean softFloor() {
        var level = chair.level();
        BlockPos feet = chair.blockPosition();
        return level.getBlockState(feet).getSoundType(level, feet, chair) == SoundType.WOOL
                || level.getBlockState(feet.below()).getSoundType(level, feet.below(), chair) == SoundType.WOOL;
    }

    @Override
    public void tick() {
        if (chair.isRemoved()) {
            stop();
            return;
        }
        follow();
        float t = (float)Mth.clamp(speed(chair) / OfficeChairEntity.TOP_SPEED, 0.0, 1.0);
        boolean soft = softFloor();
        float target = t * (soft ? SOFT_VOLUME : 1.0F);
        volume += (target - volume) * (target > volume ? FADE_IN : FADE_OUT);
        pitch = (PITCH_SLOW + (PITCH_FAST - PITCH_SLOW) * t) * (soft ? SOFT_PITCH : 1.0F);
        if (target < 0.01F && volume < 0.01F) {
            if (++quietTicks > QUIET_TICKS) stop();
        } else {
            quietTicks = 0;
        }
    }

    /** Starts at volume 0 and fades in. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !chair.isSilent();
    }
}
