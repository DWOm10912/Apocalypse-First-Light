package com.antaurora.apofirstlight.book;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Recipe;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Unlock;
import com.antaurora.apofirstlight.cabin.WorkbenchKnowledge;
import com.antaurora.apofirstlight.cabin.WorkbenchRecipeData;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Readable books (docs/gameplay/field_survival_guide_v1_design.md): {@code data/<namespace>/books/<name>.json}, the book's
 * chapters, sections and blocks by language key (the words are in the language files, the client sets the pages). A
 * section may teach workbench recipes: staying on its pages teaches them (the client reports it, {@link #read} checks
 * it). The server sends every player each book's file whole when the data loads or reloads and at login, so the pages a
 * player reads and the sections the server checks come from the same file. A bad file is logged and left out.
 * <p>Checked once per load, after the recipes are read: a taught recipe must exist and list {@code {"type": "book"}} with
 * this book's item; a recipe that lists a book which does not teach it is logged too (nobody could learn it).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class GuideBooks {
    /** One loaded book: what each section teaches, and the file as it is sent to the clients. */
    public record Book(ResourceLocation id, ResourceLocation item, Map<String, List<ResourceLocation>> teaches, String json) {}

    private static volatile Map<ResourceLocation, Book> byItem = Map.of();
    private static boolean checked;

    private GuideBooks() {}

    @Nullable
    public static Book byItem(ResourceLocation item) {
        return byItem.get(item);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "books") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Book> books = new LinkedHashMap<>();
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        Book book = parse(id, input.get(id).getAsJsonObject());
                        if (books.containsKey(book.item())) throw new IllegalArgumentException("a second book for " + book.item());
                        books.put(book.item(), book);
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL BOOK] Rejected book file {}: {}", id, ex.getMessage());
                    }
                }
                byItem = Map.copyOf(books);
                checked = false;
                ApocalypseFirstLight.LOGGER.info("[AFL BOOK] {} books", books.size());
            }
        });
    }

    private static Book parse(ResourceLocation id, JsonObject root) {
        if (root.get("format_version").getAsInt() != 1) throw new IllegalArgumentException("format_version must be 1");
        ResourceLocation item = new ResourceLocation(root.get("item").getAsString());
        if (!ForgeRegistries.ITEMS.containsKey(item)) throw new IllegalArgumentException("unknown item " + item);
        if (!root.has("lang")) throw new IllegalArgumentException("no lang prefix");
        Map<String, List<ResourceLocation>> teaches = new LinkedHashMap<>();
        Set<String> chapters = new HashSet<>();
        for (JsonElement c : root.getAsJsonArray("chapters")) {
            JsonObject chapter = c.getAsJsonObject();
            if (!chapters.add(chapter.get("id").getAsString())) throw new IllegalArgumentException("chapter id twice: " + chapter.get("id"));
            for (JsonElement s : chapter.getAsJsonArray("sections")) {
                JsonObject section = s.getAsJsonObject();
                String sid = section.get("id").getAsString();
                if (teaches.containsKey(sid)) throw new IllegalArgumentException("section id twice: " + sid);
                List<ResourceLocation> recipes = new ArrayList<>();
                if (section.has("teaches")) for (JsonElement r : section.getAsJsonArray("teaches")) recipes.add(new ResourceLocation(r.getAsString()));
                if (!section.has("blocks") || section.getAsJsonArray("blocks").isEmpty()) throw new IllegalArgumentException("section " + sid + " has no blocks");
                teaches.put(sid, List.copyOf(recipes));
            }
        }
        if (teaches.isEmpty()) throw new IllegalArgumentException("no sections");
        return new Book(id, item, teaches, root.toString());
    }

    /** The cross-check against the recipes (once per load, the first time a player is sent the books). */
    private static void check() {
        if (checked) return;
        checked = true;
        for (Book book : byItem.values()) for (var e : book.teaches().entrySet()) for (ResourceLocation id : e.getValue()) {
            Recipe r = WorkbenchRecipeData.find(id);
            if (r == null) ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] {} section {} teaches {}, which is not a recipe", book.id(), e.getKey(), id);
            else if (!taughtBy(r, book.item()))
                ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] {} section {} teaches {}, whose unlock does not name the book {}", book.id(), e.getKey(), id, book.item());
        }
        for (Recipe r : WorkbenchRecipeData.all()) for (Unlock u : r.unlock()) {
            if (!u.type().equals("book")) continue;
            Book book = byItem.get(new ResourceLocation(u.value()));
            if (book == null || book.teaches().values().stream().noneMatch(list -> list.contains(r.id())))
                ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] Recipe {} is learned from the book {}, but no section of it teaches the recipe", r.id(), u.value());
        }
    }

    private static boolean taughtBy(Recipe r, ResourceLocation item) {
        for (Unlock u : r.unlock()) if (u.type().equals("book") && item.toString().equals(u.value())) return true;
        return false;
    }

    /**
     * The client stayed on a section's pages long enough: it teaches its recipes, if the player holds the book in the main
     * hand, the section is in that book and each recipe names the book in its unlock. Each one newly learned is told back
     * to the player (GuideBookPackets.Learned).
     */
    static void read(ServerPlayer player, ResourceLocation item, String section) {
        if (!item.equals(ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem()))) return;
        Book book = byItem.get(item);
        List<ResourceLocation> recipes = book == null ? null : book.teaches().get(section);
        if (recipes == null) return;
        for (ResourceLocation id : recipes) {
            Recipe r = WorkbenchRecipeData.find(id);
            if (r == null || !taughtBy(r, item)) continue;
            if (WorkbenchKnowledge.learn(player, r, new WorkbenchKnowledge.How("book", item)))
                AflNetwork.skeletonTo(player, new GuideBookPackets.Learned(r.result()));
        }
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        check();
        List<GuideBookPackets.Books.Entry> books = byItem.values().stream().map(b -> new GuideBookPackets.Books.Entry(b.id(), b.json())).toList();
        for (var player : event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer()))
            AflNetwork.skeletonTo(player, new GuideBookPackets.Books(books));
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        byItem = Map.of();
        checked = false;
    }
}
