package com.antaurora.apofirstlight.integration.tacz;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.debug.SoundDataDebug;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczNoiseEvents {
    private TaczNoiseEvents() {
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getShooter() instanceof ServerPlayer player)) {
            return;
        }

        IGun gun = IGun.getIGunOrNull(event.getGunItemStack());
        ResourceLocation gunId = gun == null ? null : gun.getGunId(event.getGunItemStack());
        if (gunId != null) {
            SoundDataDebug.logGunSound(gunId, event.getGunItemStack());
        }

        NoiseSystem.emit(new NoiseEvent(
                player,
                player.position(),
                NoiseType.GUNSHOT,
                player.level().getGameTime(),
                gunId
        ));
    }
}
