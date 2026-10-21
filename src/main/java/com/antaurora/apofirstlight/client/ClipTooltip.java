package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.grid.RigClips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clips (docs/gameplay/equipment_slots_v1.md, user 2026-10-21): "Clips on a chest rig" under everything the clippable tag takes. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClipTooltip {
    private ClipTooltip() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(RigClips.CLIPPABLE))
            event.getToolTip().add(Component.translatable("tooltip.apocalypse_firstlight.clip_on").withStyle(ChatFormatting.GRAY));
    }
}
