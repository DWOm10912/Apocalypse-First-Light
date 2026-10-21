package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creative inventory tab layout (docs/gameplay/vanilla_survival_rework_v1.md section 7.1): AFL's tabs come first, tabs
 * with nothing to show take no place (no gaps where retired vanilla tabs were), and a page holds 15 category tabs:
 * 5 along the top, 5 down the left side, 5 along the bottom. Forge's fixed tabs (search, hotbar, inventory, operator)
 * keep their place on the right. Applied by mixin/client/CreativeTabLayoutMixin.
 */
public final class CreativeTabLayout {
    public static final int PER_SIDE = 5, PER_PAGE = 3 * PER_SIDE;
    /** Left tabs: how far they stick out of the panel, and their pitch down the side. */
    private static final int LEFT_OUT = 28, LEFT_PITCH = 27, LEFT_Y0 = 1;
    /** Slot of each page tab when the screen last built its pages: 0-4 top, 5-9 left, 10-14 bottom. */
    private static final Map<CreativeModeTab, Integer> SLOTS = new IdentityHashMap<>();

    private CreativeTabLayout() {
    }

    /** Replaces Forge's sorted tab list when the screen builds its pages: displayed tabs only, AFL's first. */
    public static List<CreativeModeTab> order(List<CreativeModeTab> sorted) {
        List<CreativeModeTab> afl = new ArrayList<>(), rest = new ArrayList<>();
        for (CreativeModeTab tab : sorted) {
            if (!tab.shouldDisplay()) continue;
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            (id != null && ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) ? afl : rest).add(tab);
        }
        afl.addAll(rest);
        SLOTS.clear();
        for (int i = 0; i < afl.size(); i++) SLOTS.put(afl.get(i), i % PER_PAGE);
        return afl;
    }

    /** The tab's slot, or -1 for Forge's fixed tabs and tabs that turned up after the pages were built (vanilla places those). */
    public static int slot(CreativeModeTab tab) {
        Integer slot = SLOTS.get(tab);
        return slot == null || tab.isAlignedRight() ? -1 : slot;
    }

    private static boolean left(int slot) {
        return slot >= PER_SIDE && slot < 2 * PER_SIDE;
    }

    private static boolean top(int slot) {
        return slot < PER_SIDE;
    }

    /** Clickable box relative to the panel's top-left corner, as vanilla's getTabX / getTabY (26 x 32 above and below). */
    public static int x(int slot) {
        return left(slot) ? -LEFT_OUT : 27 * (slot % PER_SIDE);
    }

    public static int y(int slot, int imageHeight) {
        if (left(slot)) return LEFT_Y0 + LEFT_PITCH * (slot - PER_SIDE);
        return top(slot) ? -32 : imageHeight;
    }

    public static int width(int slot) {
        return left(slot) ? LEFT_OUT : 26;
    }

    public static int height(int slot) {
        return left(slot) ? 26 : 32;
    }

    /**
     * Draws the tab as vanilla's renderTabButton does. A left tab is the top tab's sprite mirrored across its diagonal:
     * the open end faces the panel and the light edge stays on top, like the other tabs' light left edge.
     */
    public static void render(GuiGraphics g, Font font, CreativeModeTab tab, int slot, boolean selected, int leftPos, int topPos, int imageHeight) {
        ResourceLocation image = tab.getTabsImage();
        int v = selected ? 32 : 0, x, y;
        RenderSystem.enableBlend();
        if (left(slot)) {
            x = leftPos - LEFT_OUT;
            y = topPos + y(slot, imageHeight);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
            g.pose().scale(-1, 1, 1);                       // with the turn: (u, v) -> (v, u)
            RenderSystem.disableCull();
            g.blit(image, 0, 0, 26, v, 26, 32);             // column 1: a plain tab, not the corner piece of column 0
            RenderSystem.enableCull();
            g.pose().popPose();
            x += 9;
            y += 5;
        } else {
            int column = slot % PER_SIDE;
            boolean top = top(slot);
            x = leftPos + 27 * column;
            y = top ? topPos - 28 : topPos + imageHeight - 4;
            g.blit(image, x, y, column * 26, v + (top ? 0 : 64), 26, 32);
            x += 5;
            y += top ? 9 : 7;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        ItemStack icon = tab.getIconItem();
        g.renderItem(icon, x, y);
        g.renderItemDecorations(font, icon, x, y);
        g.pose().popPose();
    }

    /** JEI keeps out of the left tab column while the page shows one. */
    public static List<Rect2i> extraAreas(Screen screen) {
        if (!(screen instanceof CreativeModeInventoryScreen creative)) return List.of();
        for (CreativeModeTab tab : creative.getCurrentPage().getVisibleTabs()) {
            int slot = slot(tab);
            if (slot >= 0 && left(slot)) return List.of(new Rect2i(creative.getGuiLeft() - LEFT_OUT, creative.getGuiTop(), LEFT_OUT, creative.getYSize()));
        }
        return List.of();
    }
}
