package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.world.VanillaRetirement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla Survival Rework V1, client side and cosmetic only: retired gear (VanillaRetirement.CREATIVE_HIDDEN) is left out
 * of the creative tabs and the creative search, and retired gear says so in its tooltip. The rules themselves are server
 * side (VanillaRetirementEvents); /give still hands the items out. The switch read here is this client's own COMMON
 * config, and the tabs are rebuilt on entering a world, so a changed switch shows after re-entering.
 */
public final class VanillaRetirementClient {
    private VanillaRetirementClient() {
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Tooltip {
        private Tooltip() {
        }

        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            if (VanillaRetirement.isRetiredGear(event.getItemStack())) {
                event.getToolTip().add(Component.translatable("tooltip.apocalypse_firstlight.retired_gear").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * Last, after the other mods that load before AFL have added theirs. The search tab is built from the category tabs,
     * so removing an item from every tab also takes it out of the search.
     */
    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CreativeTabs {
        private CreativeTabs() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onBuildTab(BuildCreativeModeTabContentsEvent event) {
            if (!VanillaRetirement.enabled()) return;
            var it = event.getEntries().iterator();
            while (it.hasNext()) if (it.next().getKey().is(VanillaRetirement.CREATIVE_HIDDEN)) it.remove();
        }
    }
}
