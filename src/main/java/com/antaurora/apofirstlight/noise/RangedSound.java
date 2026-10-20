package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.network.RangedSoundS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A sound heard exactly as far as the noise it goes with (audio pass 2026-10-09, docs "声音与感染者听觉" section 14): it fades
 * linearly to silence at {@code radius} blocks, the radius the same action's NoiseEvent reaches the infected at. So what a
 * player hears is what the infected hear.
 * <p>
 * Vanilla cannot do it. A server sound reaches only the players within 16 x max(volume, 1) blocks and fades over
 * max(volume, 1) x the sounds.json attenuation distance (16 unless set). So a gunshot at volume 1 died at 16 blocks while
 * the infected heard it at 64-128, a suppressed shot (3-6 blocks of noise) still carried 16, and nothing could carry less
 * than 16. Here the server sends it to the players within the radius, and the client plays it with the radius as its
 * attenuation distance (client/RangedSoundInstance). The volume (at most 1) only scales the loudness, and the sounds.json
 * attenuation distance of the event is not used.
 */
public final class RangedSound {
    private RangedSound() {
    }

    public static void play(ServerLevel level, Vec3 at, SoundEvent sound, SoundSource source, double radius, float volume, float pitch) {
        play(level, at, sound, source, radius, volume, pitch, null);
    }

    /**
     * As above; {@code maker} (a gun's shooter) hears it at the ear instead (client/RangedSoundInstance: not placed, not
     * faded, the event's {@code _near} variant if there is one): a sound placed at the eyes swings from ear to ear as the
     * camera moves (view bob, the orbit camera 4 blocks back) and a mono one comes out 3 dB lower in each ear.
     */
    public static void play(ServerLevel level, Vec3 at, SoundEvent sound, SoundSource source, double radius, float volume, float pitch,
                            @org.jetbrains.annotations.Nullable ServerPlayer maker) {
        float r = (float) Math.max(1.0, radius);
        long seed = level.getRandom().nextLong();
        RangedSoundS2CPacket packet = new RangedSoundS2CPacket(sound.getLocation(), source, at.x, at.y, at.z, r, volume, pitch, seed, false);
        for (ServerPlayer player : level.players()) {
            if (player == maker) AflNetwork.rangedSound(player, new RangedSoundS2CPacket(sound.getLocation(), source, at.x, at.y, at.z, r,
                    volume, pitch, seed, true));
            else if (player.distanceToSqr(at) <= (double) r * r) AflNetwork.rangedSound(player, packet);
        }
    }

    /**
     * The sound and its noise in one call, both {@code radius} blocks (forest home sounds V1, 2026-10-14): what the player
     * hears is what the infected hear. {@code source} is who made it (null for a machine), {@code id} the block or item.
     */
    public static void playWithNoise(ServerLevel level, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity source,
                                     Vec3 at, SoundEvent sound, double radius, float pitch, NoiseType type,
                                     @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation id) {
        play(level, at, sound, SoundSource.BLOCKS, radius, 1.0F, pitch);
        NoiseSystem.emit(new NoiseEvent(source, at, type, level.getGameTime(), id, radius), level);
    }
}
