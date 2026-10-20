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

/**
 * The dimensions AFL does not use ("closed_dimensions" of the vanilla_retirement list, read by VanillaRetirement: the
 * Nether and the End). Nether portals never light, and nothing (players, mobs, items) travels into a closed dimension
 * through a portal placed by command. The dimensions themselves still exist in the world preset.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldAccessEvents {
    private WorldAccessEvents() {
    }

    @SubscribeEvent
    public static void onNetherPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (!VanillaRetirement.isClosed(Level.NETHER)) return;
        event.setCanceled(true);
        ApocalypseFirstLight.LOGGER.debug("[AFL WORLD] Blocked Nether portal activation");
    }

    @SubscribeEvent
    public static void onTravelToClosedDimension(EntityTravelToDimensionEvent event) {
        ResourceKey<Level> target = event.getDimension();
        if (!VanillaRetirement.isClosed(target)) return;
        event.setCanceled(true);
        if (!(event.getEntity() instanceof Player player)) return;
        if (target.equals(Level.NETHER)) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.nether_disabled"), true);
        } else if (target.equals(Level.END)) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.end_disabled"), true);
        }
        ApocalypseFirstLight.LOGGER.debug("[AFL WORLD] Blocked portal travel Player={} Target={}",
                player.getGameProfile().getName(), target.location());
    }
}
