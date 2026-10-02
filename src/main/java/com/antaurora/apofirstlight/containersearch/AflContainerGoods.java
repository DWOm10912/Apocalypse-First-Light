package com.antaurora.apofirstlight.containersearch;

import net.minecraft.world.item.ItemStack;

/**
 * Goods: the anonymous props a searchable container draws inside while it holds anything (docs/gameplay/container_goods_v1.md).
 * The shared rule: how many of a container's spots show, from its occupied slots (hidden ones included: the goods show
 * how full it is, never what it holds). Which spots and which props are each container's own business.
 */
public final class AflContainerGoods {
    private AflContainerGoods() {
    }

    /** Spots shown for this many occupied slots: none when empty, then about two spots for every three stacks. */
    public static int shown(int occupied, int spots) {
        return Math.max(0, Math.min(spots, (occupied * 2 + 2) / 3));
    }

    public static int occupied(Iterable<ItemStack> items) {
        int occupied = 0;
        for (ItemStack stack : items) if (!stack.isEmpty()) occupied++;
        return occupied;
    }

    /** A container whose goods come in themes (AflGoodsThemes); the theme normally follows its loot table. */
    public interface Themed {
        /** Server: sets the theme outright (development spawning), persisted and synced. */
        void setGoodsTheme(String theme);
    }
}
