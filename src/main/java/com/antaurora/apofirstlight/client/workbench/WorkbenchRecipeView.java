package com.antaurora.apofirstlight.client.workbench;

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

    /** One material line: how many, and the items that count for it (the first one names and draws it). */
    public record Material(List<ItemStack> options, int count) {
        public ItemStack shown() { return options.get(0); }
    }
}
