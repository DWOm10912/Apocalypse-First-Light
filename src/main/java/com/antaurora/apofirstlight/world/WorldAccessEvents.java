package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldAccessEvents {
    private static final ResourceKey<Level> NETHER = Level.NETHER;
    private static final ResourceKey<Level> END = Level.END;

    private WorldAccessEvents() {
    }

    @SubscribeEvent
    public static void onNetherPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
        ApocalypseFirstLight.LOGGER.debug("[AFL WORLD] Blocked Nether portal activation");
    }

    @SubscribeEvent
    public static void onTravelToRestrictedDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ResourceKey<Level> target = event.getDimension();
        if (!target.equals(NETHER) && !target.equals(END)) {
            return;
        }
        event.setCanceled(true);
        String messageKey = target.equals(NETHER)
                ? "message.apocalypse_firstlight.nether_disabled"
                : "message.apocalypse_firstlight.end_disabled";
        player.displayClientMessage(Component.translatable(messageKey), true);
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL WORLD] Blocked portal travel Player={} Target={}",
                player.getGameProfile().getName(), target.location()
        );
    }
}
