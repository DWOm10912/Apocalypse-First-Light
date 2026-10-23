package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Every item's one-line description (docs/gameplay/item_inspect_v1_design.md 13.1, user 2026-10-22): the hover tooltip
 * shows it under the name for any item that has one, unless the item already adds it itself; the item inspect view
 * reads the same line.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ItemDescriptionTooltip {
    private ItemDescriptionTooltip() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty()) return;
        String key = key(stack);
        if (key == null) return;
        var lines = event.getToolTip();
        for (var line : lines) if (mentions(line, key)) return;
        lines.add(Math.min(1, lines.size()), Component.translatable(key).withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)));
    }

    /** The description as a component, null when the item has none. */
    @Nullable
    public static Component description(ItemStack stack) {
        String key = key(stack);
        return key == null ? null : Component.translatable(key);
    }

    /**
     * The description's key: a variant's own (its name's key + .desc, the map's kinds), else the item's
     * (tooltip.<namespace>.<id>.description); null when neither is in the language file.
     */
    @Nullable
    private static String key(ItemStack stack) {
        if (stack.getHoverName().getContents() instanceof TranslatableContents t && I18n.exists(t.getKey() + ".desc"))
            return t.getKey() + ".desc";
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        String key = "tooltip." + id.getNamespace() + "." + id.getPath() + ".description";
        return I18n.exists(key) ? key : null;
    }

    /** Whether the line, or anything in it, is that translation already. */
    private static boolean mentions(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key)) return true;
            for (Object arg : t.getArgs()) if (arg instanceof Component c && mentions(c, key)) return true;
        }
        for (var sibling : line.getSiblings()) if (mentions(sibling, key)) return true;
        return false;
    }
}
