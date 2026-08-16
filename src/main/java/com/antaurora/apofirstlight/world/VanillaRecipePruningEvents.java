package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaRecipePruningEvents {
    private static final Set<ResourceLocation> PRUNED_RECIPE_IDS = Set.of(
            new ResourceLocation("minecraft", "anvil"),
            new ResourceLocation("minecraft", "enchanting_table")
    );
    private static List<Recipe<?>> prunedRecipes = List.of();

    private VanillaRecipePruningEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RecipePruningReloadListener(event.getServerResources().getRecipeManager()));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        pruneRecipes(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !prunedRecipes.isEmpty()) {
            player.resetRecipes(prunedRecipes);
        }
    }

    private static void pruneRecipes(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        List<Recipe<?>> recipesToPrune = recipeManager.getRecipes().stream()
                .filter(recipe -> PRUNED_RECIPE_IDS.contains(recipe.getId()))
                .toList();

        if (!recipesToPrune.isEmpty()) {
            recipeManager.replaceRecipes(recipeManager.getRecipes().stream()
                    .filter(recipe -> !PRUNED_RECIPE_IDS.contains(recipe.getId()))
                    .toList());
            prunedRecipes = recipesToPrune;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.resetRecipes(prunedRecipes);
            }
        }

        logRecipePresence(recipeManager);
    }

    private static void logRecipePresence(RecipeManager recipeManager) {
        for (ResourceLocation recipeId : PRUNED_RECIPE_IDS) {
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL PROGRESSION] recipe {} present={}",
                    recipeId,
                    recipeManager.byKey(recipeId).isPresent()
            );
        }
    }

    private static final class RecipePruningReloadListener extends SimplePreparableReloadListener<Void> {
        private final RecipeManager recipeManager;

        private RecipePruningReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
            List<Recipe<?>> recipesToPrune = recipeManager.getRecipes().stream()
                    .filter(recipe -> PRUNED_RECIPE_IDS.contains(recipe.getId()))
                    .toList();
            if (!recipesToPrune.isEmpty()) {
                recipeManager.replaceRecipes(recipeManager.getRecipes().stream()
                        .filter(recipe -> !PRUNED_RECIPE_IDS.contains(recipe.getId()))
                        .toList());
                prunedRecipes = recipesToPrune;
            }
            logRecipePresence(recipeManager);
        }
    }
}
