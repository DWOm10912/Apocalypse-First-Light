package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.workbench.WorkbenchRecipeView.Category;
import com.antaurora.apofirstlight.client.workbench.WorkbenchRecipeView.Material;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting;
import com.antaurora.apofirstlight.cabin.WorkbenchKnowledge;
import com.antaurora.apofirstlight.inventory.CarriedItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client: the recipes the crafting page lists, the to-do pin and the "new" marks (docs/gameplay/afl_workbench_v1_design.md
 * 16). The recipes are data on the server (18, data/&lt;ns&gt;/workbench_recipes/&lt;category&gt;/); the server sends only the ones
 * the survivor knows (BuildingPackets.Known), each with how it is known. A recipe that arrives after the first list of the
 * session is marked "new". In a dev client
 * {@code /afl_workbench preview on} fills it with a few sample recipes from existing items, only to look at the page:
 * they are marked as samples and crafting one makes nothing.
 */
public final class WorkbenchRecipes {
    private static boolean preview;
    @Nullable private static ResourceLocation pinned;
    /** Learned and not looked at yet: the list marks them "new" until the page shows their details once. */
    private static final Set<ResourceLocation> fresh = new HashSet<>();
    private static List<WorkbenchRecipeView> samples = List.of();
    private static List<WorkbenchRecipeView> real = List.of();
    /** No list received yet this session: the first one marks nothing new. */
    private static boolean first = true;

    private WorkbenchRecipes() {}

    /** What the current survivor knows, in no particular order (the page sorts by category). */
    public static List<WorkbenchRecipeView> known(Player player) {
        if (!preview) return real;
        List<WorkbenchRecipeView> all = new ArrayList<>(real);
        all.addAll(samples);
        return all;
    }

    /** The server's list of what the survivor knows (WorkbenchKnowledge#sync), as the page shows it. */
    public static void receive(List<WorkbenchKnowledge.Entry> recipes) {
        Set<ResourceLocation> before = new HashSet<>();
        for (var r : real) before.add(r.id());
        List<WorkbenchRecipeView> list = new ArrayList<>();
        for (var e : recipes) {
            var r = e.recipe();
            Item result = WorkbenchCrafting.item(r.result());
            if (result == null) continue;
            List<Material> mats = new ArrayList<>();
            for (var m : r.materials()) {
                List<ItemStack> options = new ArrayList<>();
                for (var id : m.items()) {
                    Item item = WorkbenchCrafting.item(id);
                    if (item != null) options.add(new ItemStack(item));
                }
                if (!options.isEmpty()) mats.add(new Material(List.copyOf(options), m.count()));
            }
            if (mats.size() != r.materials().size()) continue;
            Category category;
            try {
                category = Category.valueOf(r.category());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Item tool = r.tool() == null ? null : WorkbenchCrafting.item(r.tool());
            list.add(new WorkbenchRecipeView(r.id(), category, new ItemStack(result, r.count()), List.copyOf(mats),
                    tool == null ? null : new ItemStack(tool), r.ticks(), source(e.how()), false, r.work()));
            if (!first && !before.contains(r.id())) fresh.add(r.id());
        }
        real = List.copyOf(list);
        first = false;
        fresh.removeIf(id -> real.stream().noneMatch(r -> r.id().equals(id)) && !id.getPath().startsWith("sample/"));
        if (pinned != null && !pinned.getPath().startsWith("sample/") && real.stream().noneMatch(r -> r.id().equals(pinned))) pinned = null;
    }

    /** Where the recipe came from as the details show it: the book or blueprint by its item name, else by source. */
    private static Component source(WorkbenchKnowledge.How how) {
        if (how.from() != null) {
            Item from = WorkbenchCrafting.item(how.from());
            if (from != null) return from.getDescription();
        }
        return Component.translatable("screen.apocalypse_firstlight.workbench.source." + how.source());
    }

    /** Whether the survivor knows a recipe for this item (the building catalog marks what the bench can make). */
    public static boolean makes(Item item) {
        for (var r : real) if (r.result().is(item)) return true;
        return false;
    }

    /** Leaving the world: nothing known until the next server's list. */
    public static void clear() {
        real = List.of();
        first = true;
        fresh.removeIf(id -> !id.getPath().startsWith("sample/"));
        if (pinned != null && !pinned.getPath().startsWith("sample/")) pinned = null;
    }

    @Nullable
    public static WorkbenchRecipeView find(Player player, @Nullable ResourceLocation id) {
        if (id == null) return null;
        for (var recipe : known(player)) if (recipe.id().equals(id)) return recipe;
        return null;
    }

    public static boolean isFresh(WorkbenchRecipeView recipe) { return fresh.contains(recipe.id()); }

    public static void seen(WorkbenchRecipeView recipe) { fresh.remove(recipe.id()); }

    @Nullable
    public static ResourceLocation pinned() { return pinned; }

    /** Pins the recipe to the to-do panel (one at a time), or unpins it when it already is. */
    public static void togglePin(WorkbenchRecipeView recipe) {
        pinned = recipe.id().equals(pinned) ? null : recipe.id();
    }

    /**
     * How many of a material's items the player carries: hands, weapon positions and the grids. The bench's drawers and
     * nearby chests are the server's to count (their contents never reach the client); that comes with crafting itself.
     */
    public static int carried(Player player, Material material) {
        int n = 0;
        for (ItemStack option : material.options()) n += carried(player, option.getItem());
        return n;
    }

    public static int carried(Player player, Item item) {
        return CarriedItems.count(player, item);
    }

    /** How many times the recipe can be made from what the player carries. */
    public static int craftable(Player player, WorkbenchRecipeView recipe) {
        int max = Integer.MAX_VALUE;
        for (Material material : recipe.materials()) max = Math.min(max, carried(player, material) / Math.max(1, material.count()));
        return recipe.materials().isEmpty() ? 0 : max;
    }

    public static boolean toolPresent(Player player, WorkbenchRecipeView recipe) {
        return recipe.tool() == null || carried(player, recipe.tool().getItem()) > 0;
    }

    // ------------------------------------------------------------------------------------------- dev preview

    public static boolean preview() { return preview; }

    public static void setPreview(boolean on) {
        preview = on;
        samples = on ? buildSamples() : List.of();
        fresh.clear();
        if (on) fresh.add(id("sample/simple_hearing_protection"));
        if (!on) pinned = null;
    }

    private static List<WorkbenchRecipeView> buildSamples() {
        Component experience = Component.translatable("screen.apocalypse_firstlight.workbench.source.experience");
        Component tinkering = Component.translatable("screen.apocalypse_firstlight.workbench.source.tinkering");
        Component book = Component.translatable("screen.apocalypse_firstlight.workbench.sample.book");
        Component blueprint = Component.translatable("screen.apocalypse_firstlight.workbench.sample.blueprint");
        List<WorkbenchRecipeView> list = new ArrayList<>();
        add(list, "white_pine_planks", 4, Category.WOODWORK, null, 60, experience, mat(1, "white_pine_log"));
        add(list, "white_oak_planks", 4, Category.WOODWORK, null, 60, experience, mat(1, "white_oak_log"));
        add(list, "wooden_chair", 1, Category.FURNITURE, "claw_hammer", 200, book,
                mat(4, "white_pine_planks", "white_oak_planks", "red_oak_planks", "red_maple_planks"));
        add(list, "wooden_table", 1, Category.FURNITURE, "claw_hammer", 300, book,
                mat(6, "white_oak_planks", "red_oak_planks", "white_pine_planks"), mat(4, "white_oak_rounded_timber", "white_pine_rounded_timber"));
        add(list, "white_oak_board_door", 1, Category.BUILDING, "claw_hammer", 240, blueprint, mat(6, "white_oak_planks"));
        add(list, "simple_hearing_protection", 1, Category.LIVING, null, 200, tinkering, mat(2, "white_pine_planks"));
        return List.copyOf(list);
    }

    private static void add(List<WorkbenchRecipeView> list, String result, int count, Category category, @Nullable String tool,
                            int ticks, Component source, @Nullable Material... materials) {
        Item item = item(result);
        if (item == null) return;
        List<Material> mats = new ArrayList<>();
        for (Material m : materials) if (m != null) mats.add(m);
        Item toolItem = tool == null ? null : item(tool);
        list.add(new WorkbenchRecipeView(id("sample/" + result), category, new ItemStack(item, count), List.copyOf(mats),
                toolItem == null ? null : new ItemStack(toolItem), ticks, source, true, null));
    }

    @Nullable
    private static Material mat(int count, String... items) {
        List<ItemStack> options = new ArrayList<>();
        for (String name : items) {
            Item item = item(name);
            if (item != null) options.add(new ItemStack(item));
        }
        return options.isEmpty() ? null : new Material(List.copyOf(options), count);
    }

    @Nullable
    private static Item item(String path) {
        var item = ForgeRegistries.ITEMS.getValue(id(path.contains("/") ? path.substring(path.indexOf('/') + 1) : path));
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }
}
