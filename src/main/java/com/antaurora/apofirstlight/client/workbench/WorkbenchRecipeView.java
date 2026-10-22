package com.antaurora.apofirstlight.client.workbench;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One learned workbench recipe as the crafting page shows it (docs/gameplay/afl_workbench_v1_design.md 16): what it makes,
 * its category, the materials (each with the items that count for it), the tool, the time and where the survivor learned
 * it. The page only reads these; the recipe data and its server side come with the recipe rework. {@code work}: the
 * player action of a recipe made at the bench (WorkbenchWork), or null: made on the page.
 */
public record WorkbenchRecipeView(ResourceLocation id, Category category, ItemStack result, List<Material> materials,
                                  @Nullable ItemStack tool, int ticks, Component source, boolean sample, @Nullable String work) {

    /** The page's fixed categories, in list order; a category with nothing learned still shows, greyed. */
    public enum Category {
        WOODWORK, FURNITURE, TOOLS, BUILDING, LIVING, REPAIR, SALVAGE;

        public String key() { return "screen.apocalypse_firstlight.workbench.category." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** One material line: how many, and the items that count for it (the first one stands for it where one is enough). */
    public record Material(List<ItemStack> options, int count) {
        public ItemStack shown() { return options.get(0); }

        /** The option drawn at this moment: several take turns, a second each, so every one that counts is seen. */
        public ItemStack shown(long millis) { return options.get((int) (millis / 1000 % options.size())); }

        /**
         * The line's name: with several options, the name of an item tag every option carries, when the lang file names
         * it ({@code tag.item.<namespace>.<path>}, e.g. "板材" for any species' board), else the first option's name.
         */
        public Component name() {
            if (options.size() > 1) {
                for (var tag : (Iterable<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>) options.get(0).getTags()::iterator) {
                    String key = "tag.item." + tag.location().getNamespace() + "." + tag.location().getPath().replace('/', '.');
                    if (I18n.exists(key) && options.stream().allMatch(o -> o.is(tag))) return Component.translatable(key);
                }
            }
            return shown().getHoverName();
        }
    }
}
