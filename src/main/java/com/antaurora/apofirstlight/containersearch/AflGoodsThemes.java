package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Goods themes (docs/gameplay/container_goods_v1.md): which kind of place a container's loot table stands for, so its
 * goods fit it. Data: {@code data/<namespace>/goods_themes/<theme>.json} = {@code {"loot_tables": [...]}}, full loot
 * table ids; an entry ending in "/" matches every table under that folder. The longest matching entry wins; no match
 * (or no loot table: placed by a player) is {@link #GENERIC}. The theme id is the file name, namespace ignored. Server
 * data; containers record their theme when their loot is rolled and sync only its name.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AflGoodsThemes {
    public static final String GENERIC = "generic";
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, String> exact = Map.of();
    /** Folder entries, longest first. */
    private static volatile List<Map.Entry<String, String>> folders = List.of();
    private static volatile List<String> themes = List.of(GENERIC);

    private AflGoodsThemes() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    public static String themeFor(@Nullable ResourceLocation lootTable) {
        if (lootTable == null) return GENERIC;
        String id = lootTable.toString();
        String theme = exact.get(id);
        if (theme != null) return theme;
        for (Map.Entry<String, String> folder : folders) if (id.startsWith(folder.getKey())) return folder.getValue();
        return GENERIC;
    }

    /** Every loaded theme, plus {@link #GENERIC}. */
    public static List<String> themes() {
        return themes;
    }

    private static final class Listener extends SimpleJsonResourceReloadListener {
        private Listener() {
            super(GSON, "goods_themes");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, String> loadedExact = new HashMap<>();
            List<Map.Entry<String, String>> loadedFolders = new ArrayList<>();
            TreeSet<String> names = new TreeSet<>(List.of(GENERIC));
            resources.forEach((file, json) -> {
                String theme = file.getPath();
                try {
                    for (JsonElement entry : GsonHelper.getAsJsonArray(GsonHelper.convertToJsonObject(json, "goods theme"), "loot_tables")) {
                        String table = GsonHelper.convertToString(entry, "loot table");
                        if (table.endsWith("/")) loadedFolders.add(Map.entry(table, theme));
                        else loadedExact.put(table, theme);
                    }
                    names.add(theme);
                } catch (RuntimeException exception) {
                    ApocalypseFirstLight.LOGGER.error("[AFL GOODS] Invalid goods theme {}: {}", file, exception.getMessage());
                }
            });
            loadedFolders.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            exact = Map.copyOf(loadedExact);
            folders = List.copyOf(loadedFolders);
            themes = List.copyOf(names);
            ApocalypseFirstLight.LOGGER.info("[AFL GOODS] Goods themes: {} ({} loot tables, {} folders)", themes,
                    loadedExact.size(), loadedFolders.size());
        }
    }
}
