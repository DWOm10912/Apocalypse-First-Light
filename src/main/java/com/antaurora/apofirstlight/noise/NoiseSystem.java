package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.entity.player.Player;

public final class NoiseSystem {
    private NoiseSystem() {
    }

    public static void emit(NoiseEvent event) {
        String sourceName = event.source() instanceof Player player
                ? player.getGameProfile().getName()
                : event.source() == null ? "none" : event.source().getName().getString();
        String sourceId = event.sourceId() == null ? "none" : event.sourceId().toString();
        String sourceLabel = event.type() == NoiseType.GUNSHOT ? "Gun" : "Block";

        ApocalypseFirstLight.LOGGER.debug(
                "[AFL NOISE] Type={} Radius={} Player={} {}={} Pos=({}, {}, {})",
                event.type(), event.radius(), sourceName, sourceLabel, sourceId,
                event.position().x(), event.position().y(), event.position().z()
        );
    }
}
