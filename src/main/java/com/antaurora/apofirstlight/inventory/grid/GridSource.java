package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A grid entry named by one int, for requests that say where an item comes from by "source slot" (attachments at the
 * maintenance bench and in the field, docs/ui/afl_inventory_grid_v0_plan.md 13). Vanilla inventory indices stay as
 * they are (0..35); a grid entry is {@link #FLAG} plus its storage, section and top-left cell. The server finds the
 * entry again and the request's expected stack must still match it.
 */
public final class GridSource {
    public static final int FLAG = 1 << 24;

    private GridSource() {}

    public static int encode(PlayerGrids.Target target, int section, int x, int y) {
        return FLAG | target.ordinal() << 16 | section << 10 | y << 5 | x;
    }

    public static boolean is(int source) {
        return (source & FLAG) != 0;
    }

    /** The place an encoded source names: storage, section and top-left cell. */
    public record Cell(PlayerGrids.Target target, int section, int x, int y) {}

    /** The entry and the view it lives in (change its stack, then commit the view). */
    public record Found(PlayerGrids.View view, GridStorage.Entry entry) {}

    /** The place a source names, or null for a vanilla index or an unknown storage. */
    @Nullable
    public static Cell cell(int source) {
        if (!is(source)) return null;
        var target = PlayerGrids.Target.of(source >> 16 & 0xFF);
        return target == null ? null : new Cell(target, source >> 10 & 0x3F, source & 0x1F, source >> 5 & 0x1F);
    }

    /** The entry an encoded source names in a fresh view of the player's grids, or null. */
    @Nullable
    public static Found find(Player player, int source) {
        var cell = cell(source);
        if (cell == null) return null;
        var view = PlayerGrids.view(player);
        var storage = view.get(cell.target());
        if (storage == null || cell.section() >= storage.layout().size()) return null;
        var entry = storage.at(cell.section(), cell.x(), cell.y());
        return entry == null || entry.x() != cell.x() || entry.y() != cell.y() ? null : new Found(view, entry);
    }
}
