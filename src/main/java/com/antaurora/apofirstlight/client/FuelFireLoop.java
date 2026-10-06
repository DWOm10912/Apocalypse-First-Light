package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The roar of burning fuel (2026-10-05, docs/gameplay/fuel_fire_v1.md "声音"; sounds/fuel_fire/burn_loop.ogg,
 * tools/build-fuel-fire-sounds-v1.mjs): one looping sound on this client, at the fire nearest the listener: the burning
 * stains within {@link #CLUSTER} blocks of the nearest one (within {@link #RANGE}), weighted by size. Louder for a larger
 * fire (the stains' total size), a little lower when it is mostly diesel; it follows the fire as it moves and fades in
 * over {@link #FADE_IN} ticks and out over {@link #FADE_OUT} when nothing burns near. Vanilla's fire crackle still plays
 * now and then over it (FuelFlames#tick).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuelFireLoop {
    private static final double RANGE = 24.0, CLUSTER = 6.0;
    private static final int FADE_IN = 10, FADE_OUT = 20;
    @Nullable
    private static Loop active;

    private FuelFireLoop() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (active != null && active.isStopped()) active = null;
        if (level == null || minecraft.player == null) {
            if (active != null) active.stopNow();
            active = null;
            return;
        }
        Vec3 ear = minecraft.player.position();
        FuelStainIndex.Stain nearest = null;
        double best = RANGE * RANGE;
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) {
            if (!s.burning()) continue;
            double d = s.pos.distanceToSqr(ear);
            if (d < best) {
                best = d;
                nearest = s;
            }
        }
        if (nearest == null) {
            if (active != null) active.target(null, 0.0F);
            return;
        }
        double sx = 0, sy = 0, sz = 0, total = 0, diesel = 0;
        for (FuelStainIndex.Stain s : FuelStainIndex.CLIENT.all()) {
            if (!s.burning() || s.pos.distanceToSqr(nearest.pos) > CLUSTER * CLUSTER) continue;
            sx += s.pos.x * s.size;
            sy += s.pos.y * s.size;
            sz += s.pos.z * s.size;
            total += s.size;
            if (s.diesel) diesel += s.size;
        }
        Vec3 at = new Vec3(sx / total, sy / total, sz / total);
        float volume = (float) Math.min(1.0, 0.3 + total * 0.35);
        if (active == null) {
            active = new Loop(level, at, diesel > total / 2 ? 0.85F : 1.0F);
            minecraft.getSoundManager().play(active);
        }
        active.target(at, volume);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        @Nullable
        private Vec3 goal;
        private float goalVolume;

        private Loop(ClientLevel level, Vec3 at, float pitch) {
            super(AflSounds.FUEL_FIRE_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 0.0F;
            this.pitch = pitch;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.goal = at;
        }

        void target(@Nullable Vec3 at, float volume) {
            if (at != null) goal = at;
            goalVolume = at == null ? 0.0F : volume;
        }

        void stopNow() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (Minecraft.getInstance().level != level) {
                stop();
                return;
            }
            if (goal != null) {   // follow the fire smoothly
                x += (goal.x - x) * 0.3;
                y += (goal.y - y) * 0.3;
                z += (goal.z - z) * 0.3;
            }
            if (volume < goalVolume) volume = Math.min(goalVolume, volume + 1.0F / FADE_IN);
            else if (volume > goalVolume) volume = Math.max(goalVolume, volume - 1.0F / FADE_OUT);
            if (goalVolume <= 0.0F && volume <= 0.0F) stop();
        }
    }
}
