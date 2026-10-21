package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Vanilla Survival Rework V1: reads the vanilla_retirement list (its "closed_dimensions" go to VanillaRetirement) and
 * removes retired recipes from the server's RecipeManager after every data load. A recipe goes
 * when, with the retirement switch on (VanillaRetirement):
 * <ul>
 *   <li>its id is in "remove_recipes" of data/&lt;ns&gt;/vanilla_retirement/*.json (anvil, enchanting table, brewing stand,
 *   grindstone, smithing table, ender chest, shield decoration, armour dye);</li>
 *   <li>its JSON "type" (serializer) is in "remove_recipe_types" (minecraft:smithing_trim);</li>
 *   <li>its output item is in apocalypse_firstlight:retired/unobtainable, whatever the recipe's namespace.</li>
 * </ul>
 * Tags are only bound after the reload listeners have applied, so the pruning runs on TagsUpdatedEvent (server data
 * load) against the RecipeManager of that reload, and again on ServerStartedEvent as a fallback. Clients receive the
 * pruned list through the normal recipe sync, so JEI and the recipe book lose them too. With the switch off nothing is
 * removed and a /reload restores whatever an earlier run pruned.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaRecipePruningEvents {
    private static volatile Set<ResourceLocation> removeIds = Set.of();
    private static volatile Set<ResourceLocation> removeTypes = Set.of();
    private static RecipeManager pendingManager;
    private static List<Recipe<?>> prunedRecipes = List.of();

    private VanillaRecipePruningEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        pendingManager = event.getServerResources().getRecipeManager();
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "vanilla_retirement") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Set<ResourceLocation> ids = new HashSet<>();
                Set<ResourceLocation> types = new HashSet<>();
                Set<ResourceLocation> dimensions = new HashSet<>();
                for (var file : new TreeSet<>(input.keySet())) {
                    try {
                        var root = input.get(file).getAsJsonObject();
                        if (root.has("remove_recipes")) for (var e : root.getAsJsonArray("remove_recipes")) ids.add(new ResourceLocation(e.getAsString()));
                        if (root.has("remove_recipe_types")) for (var e : root.getAsJsonArray("remove_recipe_types")) types.add(new ResourceLocation(e.getAsString()));
                        if (root.has("closed_dimensions")) for (var e : root.getAsJsonArray("closed_dimensions")) dimensions.add(new ResourceLocation(e.getAsString()));
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL RETIREMENT] skipped vanilla_retirement file {}: {}", file, ex.getMessage());
                    }
                }
                removeIds = Set.copyOf(ids);
                removeTypes = Set.copyOf(types);
                VanillaRetirement.setClosedDimensions(dimensions);
                ApocalypseFirstLight.LOGGER.info("[AFL RETIREMENT] list: {} recipe ids, {} recipe types, closed dimensions {}",
                        ids.size(), types.size(), new TreeSet<>(dimensions));
            }
        });
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        RecipeManager manager = pendingManager;
        pendingManager = null;
        if (manager != null) prune(manager, event.getRegistryAccess());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        prune(server.getRecipeManager(), server.registryAccess());
        if (!prunedRecipes.isEmpty()) for (ServerPlayer player : server.getPlayerList().getPlayers()) player.resetRecipes(prunedRecipes);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !prunedRecipes.isEmpty()) {
            player.resetRecipes(prunedRecipes);
        }
    }

    /** Removes the retired recipes from the manager; returns what it removed this time (empty when the switch is off). */
    public static List<Recipe<?>> prune(RecipeManager manager, RegistryAccess access) {
        if (!VanillaRetirement.enabled()) {
            prunedRecipes = List.of();
            return List.of();
        }
        List<Recipe<?>> keep = new ArrayList<>();
        List<Recipe<?>> removed = new ArrayList<>();
        List<ResourceLocation> foreign = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (retired(recipe, access)) {
                removed.add(recipe);
                if (!"minecraft".equals(recipe.getId().getNamespace())) foreign.add(recipe.getId());
            } else {
                keep.add(recipe);
            }
        }
        if (!removed.isEmpty()) {
            manager.replaceRecipes(keep);
            prunedRecipes = List.copyOf(removed);
            ApocalypseFirstLight.LOGGER.info("[AFL RETIREMENT] removed {} recipes ({} outside minecraft: {})",
                    removed.size(), foreign.size(), foreign);
        }
        return removed;
    }

    /** Whether the recipe is retired by id, by JSON type or by its output item. */
    public static boolean retired(Recipe<?> recipe, RegistryAccess access) {
        if (removeIds.contains(recipe.getId())) return true;
        ResourceLocation type = ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer());
        if (type != null && removeTypes.contains(type)) return true;
        ItemStack output;
        try {
            output = recipe.getResultItem(access);
        } catch (RuntimeException ex) {
            return false;
        }
        return output != null && !output.isEmpty() && output.is(VanillaRetirement.UNOBTAINABLE);
    }
}
