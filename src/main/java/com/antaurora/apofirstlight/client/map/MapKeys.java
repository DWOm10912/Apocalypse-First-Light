package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The map's key (AFL Map V1, docs/ui/afl_map_v1_plan.md 7): the minimap's, unbound by default, shows or hides it. The
 * world map has no key: it opens by using the map in hand (AflMapItem; user 2026-10-21, an M key left the item no part
 * to play).
 */
public final class MapKeys {
    private MapKeys() {}

    private static final String CATEGORY = "key.categories.apocalypse_firstlight";
    public static final KeyMapping MINIMAP = new KeyMapping("key.apocalypse_firstlight.minimap", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(MINIMAP);
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        private Input() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (MINIMAP.consumeClick()) FirstPersonBodyConfig.toggleMinimap();
        }
    }
}
