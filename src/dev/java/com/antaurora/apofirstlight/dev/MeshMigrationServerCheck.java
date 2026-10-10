package com.antaurora.apofirstlight.dev;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Opt-in (-Dafl.meshMigrationServerCheck=true, src/dev/mesh-migration-server.init.gradle) dedicated-server smoke check
 * for the Vextris migration: logs the loaded mod versions once the dedicated server has started, then stops it.
 * Excluded from the production jar.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class MeshMigrationServerCheck {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private MeshMigrationServerCheck() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("afl.meshMigrationServerCheck") || !event.getServer().isDedicatedServer()) return;
        var mods = ModList.get();
        LOGGER.info("[MeshMigrationServerCheck] dedicated server started on {}: vextris {}, geckolib {}, apocalypse_firstlight {}",
                FMLEnvironment.dist, version(mods, "vextris"), version(mods, "geckolib"), version(mods, "apocalypse_firstlight"));
        event.getServer().halt(false);
    }

    private static String version(ModList mods, String id) {
        return mods.getModContainerById(id).map(c -> c.getModInfo().getVersion().toString()).orElse("MISSING");
    }
}
