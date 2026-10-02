package com.antaurora.apofirstlight.containersearch;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The goods of one retail container drawn from the shared goods library (docs/gameplay/container_goods_v1.md; shelf,
 * beverage cooler, vending machine): its theme (recorded from the loot table when the loot is rolled, persisted), how many
 * of its cells show (from the occupied slots, AflContainerGoods#shown with fullAt; synced), and, on the client, which
 * cell holds which product in which colour (drawn from the position and theme, stable everywhere). The owner block
 * entity forwards load / save / sync / setChanged to it; no items ever reach the client.
 */
public final class AflGoodsState {
    /** Library colour slots (client/goods/AflGoodsLibrary.TINTS). */
    public static final int TINTS = 9;
    private static final String THEME_KEY = "GoodsTheme";
    private static final String COUNT_KEY = "Goods";

    /** One shown cell: the library product, its colour slot and a small turn (degrees). */
    public record Spot(int cell, String product, int tint, float yaw) {}

    private final int cells;
    private final int fullAt;
    private final Map<String, List<String>> products;
    @Nullable
    private String theme;
    private int count;
    @Nullable
    private List<Spot> layout;

    /** products: the library products each theme may show ({@link AflGoodsThemes#GENERIC} required, the fallback). */
    public AflGoodsState(int cells, int fullAt, Map<String, List<String>> products) {
        this.cells = cells;
        this.fullAt = fullAt;
        this.products = products;
    }

    public String theme() {
        return theme != null ? theme : AflGoodsThemes.GENERIC;
    }

    public int count() {
        return count;
    }

    /** Development: the theme outright. */
    public void setTheme(String theme) {
        this.theme = theme;
        layout = null;
    }

    /** Server, just before the loot is rolled: the theme follows the loot table (once). */
    public void recordTheme(@Nullable ResourceLocation lootTable) {
        if (lootTable != null && theme == null) setTheme(AflGoodsThemes.themeFor(lootTable));
    }

    /** Cells that show for these contents. */
    public int countFor(Iterable<ItemStack> items) {
        return AflContainerGoods.shown(AflContainerGoods.occupied(items), cells, fullAt);
    }

    /** Server: recount after a change of the contents; true when the shown count moved (the owner then syncs). */
    public boolean refresh(Iterable<ItemStack> items) {
        int now = countFor(items);
        if (now == count) return false;
        count = now;
        return true;
    }

    public void load(CompoundTag tag, Iterable<ItemStack> items) {
        theme = tag.contains(THEME_KEY) ? tag.getString(THEME_KEY) : null;
        layout = null;
        count = countFor(items);
    }

    public void save(CompoundTag tag) {
        if (theme != null) tag.putString(THEME_KEY, theme);
    }

    /** pendingLoot: the loot table is not rolled yet, so nothing shows. */
    public void writeSync(CompoundTag tag, Iterable<ItemStack> items, boolean pendingLoot) {
        tag.putString(THEME_KEY, theme());
        tag.putInt(COUNT_KEY, pendingLoot ? 0 : countFor(items));
    }

    public void readSync(CompoundTag tag) {
        String synced = tag.getString(THEME_KEY);
        if (!synced.equals(theme())) setTheme(synced);
        count = Math.max(0, Math.min(cells, tag.getInt(COUNT_KEY)));
    }

    /** Client: the shown cells, in fill order. */
    public List<Spot> shown(BlockPos pos) {
        if (layout == null) layout = layout(pos, theme(), cells, products.getOrDefault(theme(), products.get(AflGoodsThemes.GENERIC)));
        return layout.subList(0, Math.min(count, layout.size()));
    }

    /** Cells shuffled, each with a product of the theme, a colour slot and a turn of up to +-4 degrees. */
    static List<Spot> layout(BlockPos pos, String theme, int cells, List<String> products) {
        RandomSource random = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L + theme.hashCode() * 31L + cells);
        int[] order = new int[cells];
        for (int i = 0; i < cells; i++) order[i] = i;
        for (int i = cells - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        List<Spot> spots = new ArrayList<>(cells);
        for (int cell : order)
            spots.add(new Spot(cell, products.get(random.nextInt(products.size())), random.nextInt(TINTS), (random.nextFloat() - 0.5F) * 8.0F));
        return List.copyOf(spots);
    }
}
