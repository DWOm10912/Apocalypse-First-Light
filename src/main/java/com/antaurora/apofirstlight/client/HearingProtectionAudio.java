package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.HearingProtectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Listener-only filtering. Relative ambient loops are world ambience, unlike forUI sounds. */
public final class HearingProtectionAudio {
    private static final ResourceLocation TINNITUS =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "explosion_tinnitus");

    private HearingProtectionAudio() {}

    public static float listenerMultiplier() {
        return HearingProtectionManager.getWorldSoundMultiplier(Minecraft.getInstance().player);
    }

    public static float tinnitusVolumeMultiplier() {
        return HearingProtectionManager.getImpulseProtectionMultiplier(Minecraft.getInstance().player);
    }

    public static boolean isWorldSound(SoundInstance sound) {
        SoundSource source = sound.getSource();
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS
                || TINNITUS.equals(sound.getLocation()) || sound instanceof ExplosionTinnitusSound) {
            return false;
        }
        // Vanilla UI factory: relative + NONE. Do not assume all MASTER sounds are UI.
        if (sound.getLocation().getNamespace().equals("minecraft")
                && sound.getLocation().getPath().startsWith("ui.")) {
            return false;
        }
        if (sound.isRelative() && sound.getAttenuation() == SoundInstance.Attenuation.NONE) {
            // SimpleSoundInstance.forLocalAmbience / ambient additions use this combination too.
            return source == SoundSource.AMBIENT;
        }
        return true;
    }

    public static float attenuate(SoundInstance sound, float finalVolume) {
        return isWorldSound(sound) ? finalVolume * listenerMultiplier() : finalVolume;
    }
}
