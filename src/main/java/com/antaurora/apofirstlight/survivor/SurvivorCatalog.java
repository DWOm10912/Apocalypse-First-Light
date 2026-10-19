package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Survivor setup data (docs/gameplay/survivor_background_skills_v1_plan.md 4–8, 12): body stats, growth items, traits and
 * backgrounds, one JSON per entry under {@code data/<namespace>/survivor_body|survivor_growth|survivor_traits|
 * survivor_backgrounds/}. An entry's id is its file: files of this mod are {@code afl:<name>} (the record contract,
 * section 11), other namespaces keep theirs, so a data pack can add entries. Server data, read into one immutable
 * {@link Snapshot} per reload. Records store final values (section 11), so a reload never changes a living survivor's
 * body, traits or growth; only what the trait effects and body curves turn into attributes follows the data.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorCatalog {
    /** The id namespace of this mod's survivor entries. */
    public static final String AFL = "afl";
    /** Points negative traits may give at most (section 5). */
    public static final int NEGATIVE_POINT_CAP = 4;
    /** Trait groups in screen order (section 5); a trait with another group sorts after these. */
    public static final List<String> GROUPS = List.of("body", "metabolism", "climate", "noise", "hands", "growth");

    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    /** The files of the current snapshot as one JSON object ({folder: {file id: json}}), sent to clients (setup screen). */
    private static volatile String json = "{}";

    private SurvivorCatalog() {
    }

    public static Snapshot get() {
        return snapshot;
    }

    /** The loaded files, for the setup screen: the client builds the same snapshot from them ({@link #parse}). */
    public static String json() {
        return json;
    }

    /** Client: the snapshot from the files the server sent; the same rules as the server's own. */
    public static Snapshot parse(String text) {
        JsonObject root = GsonHelper.parse(text);
        Map<String, Map<ResourceLocation, JsonElement>> files = new HashMap<>();
        for (Map.Entry<String, JsonElement> folder : root.entrySet()) {
            Map<ResourceLocation, JsonElement> entries = new TreeMap<>();
            for (Map.Entry<String, JsonElement> file : folder.getValue().getAsJsonObject().entrySet()) {
                entries.put(new ResourceLocation(file.getKey()), file.getValue());
            }
            files.put(folder.getKey(), entries);
        }
        return build(files);
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    /** {@code apocalypse_firstlight:name} → {@code afl:name}; other namespaces unchanged. */
    public static String id(ResourceLocation file) {
        return (ApocalypseFirstLight.MOD_ID.equals(file.getNamespace()) ? AFL : file.getNamespace()) + ":" + file.getPath();
    }

    /** A typed id, with {@code afl:} added when there is no namespace. */
    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? AFL + ":" + id : id;
    }

    /** The translation key of an entry: {@code survivor.<mod or namespace>.<kind>.<path>}. */
    public static String key(String kind, String id) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? AFL : id.substring(0, colon);
        return "survivor." + (AFL.equals(ns) ? ApocalypseFirstLight.MOD_ID : ns) + "." + kind + "." + id.substring(colon + 1);
    }

    /** One change to an attribute: added to it, or a factor it is multiplied by (1 = no change). */
    public record Effect(Attribute attribute, boolean multiply, double value) {
        boolean neutral() {
            return multiply ? Math.abs(value - 1.0) < 1e-9 : Math.abs(value) < 1e-9;
        }
    }

    /** A body stat's value per level for one attribute: {@code values[level - 1]}. */
    public record Curve(Attribute attribute, boolean multiply, double[] values) {
        Effect at(int level) {
            return new Effect(attribute, multiply, values[Math.max(1, Math.min(values.length, level)) - 1]);
        }
    }

    /** A body stat (section 4): start range, the growth maximum, what each level does, and how it is trained. */
    public record BodyDef(String id, int order, int defaultLevel, int startMin, int startMax, int max, List<Curve> curves,
                          Training training) {
    }

    /**
     * A growth item (section 7) other than the body stats: its level for a survivor with no trait about it, its top, its
     * training, its group (the growth page's section, "firearms" for the guns; default "general"), what each level does to
     * attributes ({@code values[level - 1]}, level 0 changes nothing; optional) and its nodes ({@code perks}: level -> perk id,
     * a thing learned at that level; level 0 = what the survivor does before learning anything; optional).
     */
    public record GrowthDef(String id, int order, int defaultLevel, int max, Training training, String group, List<Curve> curves,
                            Map<Integer, String> perks) {
    }

    /**
     * How a body stat or growth item grows by use (section 7, the "training" object of its file): {@code toNext[L]} is the
     * experience from level L to L + 1 (missing = cannot grow past L), {@code xp} what each source gives (SurvivorGrowth
     * names the sources; a missing one gives nothing).
     */
    public record Training(int[] toNext, Map<String, Double> xp) {
        static final Training NONE = new Training(new int[0], Map.of());

        /** Experience from {@code level} to the next, or 0 when this level is as far as training goes. */
        public int toNext(int level) {
            return level >= 0 && level < toNext.length ? toNext[level] : 0;
        }

        public double xp(String source) {
            return xp.getOrDefault(source, 0.0);
        }
    }

    /**
     * A trait (section 5). {@code cost} > 0 spends points, < 0 gives them. {@code bodyBonus} raises a body stat's start
     * level past the chosen one ("big frame"); {@code growthStart} raises growth start levels, {@code growthCap} caps them.
     */
    public record TraitDef(String id, String group, int cost, boolean backgroundOnly, Set<String> exclusive,
                           List<Effect> effects, Map<String, Integer> bodyBonus, Map<String, Integer> growthStart,
                           Map<String, Integer> growthCap) {
        public boolean negative() {
            return cost < 0;
        }
    }

    /** A background (section 8): its locked traits, its point budget, whether successors may be rolled with it. */
    public record BackgroundDef(String id, int order, int budget, List<String> traits, boolean successorPool) {
    }

    /** Everything loaded, each map in screen order. */
    public record Snapshot(Map<String, BodyDef> body, Map<String, GrowthDef> growth, Map<String, TraitDef> traits,
                           Map<String, BackgroundDef> backgrounds) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static final class Listener extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonElement>>> {
        private static final List<String> FOLDERS = List.of("survivor_body", "survivor_growth", "survivor_traits", "survivor_backgrounds");

        @Override
        protected Map<String, Map<ResourceLocation, JsonElement>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Map<ResourceLocation, JsonElement>> out = new HashMap<>();
            for (String folder : FOLDERS) {
                Map<ResourceLocation, JsonElement> files = new TreeMap<>();
                SimpleJsonResourceReloadListener.scanDirectory(manager, folder, GSON, files);
                out.put(folder, files);
            }
            return out;
        }

        @Override
        protected void apply(Map<String, Map<ResourceLocation, JsonElement>> files, ResourceManager manager, ProfilerFiller profiler) {
            snapshot = build(files);
            JsonObject root = new JsonObject();
            files.forEach((folder, entries) -> {
                JsonObject out = new JsonObject();
                entries.forEach((file, value) -> out.add(file.toString(), value));
                root.add(folder, out);
            });
            json = GSON.toJson(root);
            Snapshot s = snapshot;
            ApocalypseFirstLight.LOGGER.info("[AFL Survivor] setup data: {} body stats, {} growth items, {} traits, {} backgrounds",
                    s.body().size(), s.growth().size(), s.traits().size(), s.backgrounds().size());
        }
    }

    static Snapshot build(Map<String, Map<ResourceLocation, JsonElement>> files) {
        List<BodyDef> body = read(files.get("survivor_body"), "body stat", SurvivorCatalog::body);
        body.sort(Comparator.comparingInt(BodyDef::order).thenComparing(BodyDef::id));
        List<GrowthDef> growth = read(files.get("survivor_growth"), "growth item", SurvivorCatalog::growth);
        growth.sort(Comparator.comparingInt(GrowthDef::order).thenComparing(GrowthDef::id));
        List<TraitDef> traits = read(files.get("survivor_traits"), "trait", SurvivorCatalog::trait);
        // exclusions go both ways, whichever file names them
        Map<String, Set<String>> exclusive = new HashMap<>();
        for (TraitDef t : traits) {
            for (String other : t.exclusive()) {
                exclusive.computeIfAbsent(t.id(), k -> new LinkedHashSet<>()).add(other);
                exclusive.computeIfAbsent(other, k -> new LinkedHashSet<>()).add(t.id());
            }
        }
        List<TraitDef> linked = new ArrayList<>();
        for (TraitDef t : traits) {
            linked.add(new TraitDef(t.id(), t.group(), t.cost(), t.backgroundOnly(), Set.copyOf(exclusive.getOrDefault(t.id(), Set.of())),
                    t.effects(), t.bodyBonus(), t.growthStart(), t.growthCap()));
        }
        linked.sort(Comparator.comparingInt((TraitDef t) -> groupIndex(t.group())).thenComparing(TraitDef::negative)
                .thenComparing(TraitDef::id));
        List<BackgroundDef> backgrounds = read(files.get("survivor_backgrounds"), "background", SurvivorCatalog::background);
        backgrounds.sort(Comparator.comparingInt(BackgroundDef::order).thenComparing(BackgroundDef::id));
        return new Snapshot(byId(body, BodyDef::id), byId(growth, GrowthDef::id), byId(linked, TraitDef::id),
                byId(backgrounds, BackgroundDef::id));
    }

    static int groupIndex(String group) {
        int i = GROUPS.indexOf(group);
        return i < 0 ? GROUPS.size() : i;
    }

    private static <T> List<T> read(Map<ResourceLocation, JsonElement> files, String what,
                                    BiFunction<String, JsonObject, T> parse) {
        List<T> out = new ArrayList<>();
        if (files == null) return out;
        files.forEach((file, json) -> {
            try {
                out.add(parse.apply(id(file), GsonHelper.convertToJsonObject(json, what)));
            } catch (RuntimeException e) {
                ApocalypseFirstLight.LOGGER.error("[AFL Survivor] invalid {} {}: {}", what, file, e.getMessage());
            }
        });
        return out;
    }

    private static <T> Map<String, T> byId(List<T> list, java.util.function.Function<T, String> id) {
        Map<String, T> out = new LinkedHashMap<>();
        for (T t : list) out.put(id.apply(t), t);
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The "levels" object: {"<attribute>": {"op": add|multiply, "values": [level 1 ... max]}}. */
    private static List<Curve> curves(JsonObject levels, int max) {
        List<Curve> curves = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : levels.entrySet()) {
            JsonObject curve = GsonHelper.convertToJsonObject(e.getValue(), "level curve");
            var values = GsonHelper.getAsJsonArray(curve, "values");
            if (values.size() != max) throw new IllegalArgumentException(e.getKey() + ": " + values.size() + " values for " + max + " levels");
            double[] v = new double[max];
            for (int i = 0; i < max; i++) v[i] = values.get(i).getAsDouble();
            curves.add(new Curve(attribute(e.getKey()), multiply(curve), v));
        }
        return List.copyOf(curves);
    }

    private static BodyDef body(String id, JsonObject json) {
        int max = GsonHelper.getAsInt(json, "max");
        List<Curve> curves = curves(GsonHelper.getAsJsonObject(json, "levels"), max);
        int startMin = GsonHelper.getAsInt(json, "start_min");
        int startMax = GsonHelper.getAsInt(json, "start_max");
        int def = GsonHelper.getAsInt(json, "default");
        if (startMin < 1 || startMin > def || def > startMax || startMax > max) {
            throw new IllegalArgumentException("levels must be 1 <= start_min <= default <= start_max <= max");
        }
        return new BodyDef(id, GsonHelper.getAsInt(json, "order", 0), def, startMin, startMax, max, List.copyOf(curves),
                training(json, max));
    }

    private static GrowthDef growth(String id, JsonObject json) {
        int def = GsonHelper.getAsInt(json, "default");
        int max = GsonHelper.getAsInt(json, "max");
        if (def < 0 || def > max) throw new IllegalArgumentException("levels must be 0 <= default <= max");
        Map<Integer, String> perks = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "perks", new JsonObject()).entrySet()) {
            int level = Integer.parseInt(e.getKey());
            if (level < 0 || level > max) throw new IllegalArgumentException("perks: level " + level + " outside 0.." + max);
            perks.put(level, e.getValue().getAsString());
        }
        return new GrowthDef(id, GsonHelper.getAsInt(json, "order", 0), def, max, training(json, max),
                GsonHelper.getAsString(json, "group", "general"), curves(GsonHelper.getAsJsonObject(json, "levels", new JsonObject()), max),
                java.util.Collections.unmodifiableMap(perks));
    }

    /** The optional "training" object: {"to_next": {"<level>": xp, ...}, "xp": {"<source>": n, ...}} (no daily cap since 2026-10-18). */
    private static Training training(JsonObject json, int max) {
        if (!json.has("training")) return Training.NONE;
        JsonObject t = GsonHelper.getAsJsonObject(json, "training");
        int[] toNext = new int[max];
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(t, "to_next").entrySet()) {
            int level = Integer.parseInt(e.getKey());
            if (level < 0 || level >= max) throw new IllegalArgumentException("to_next: level " + level + " outside 0.." + (max - 1));
            toNext[level] = Math.max(0, e.getValue().getAsInt());
        }
        Map<String, Double> xp = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(t, "xp", new JsonObject()).entrySet()) {
            xp.put(e.getKey(), e.getValue().getAsDouble());
        }
        return new Training(toNext, Map.copyOf(xp));
    }

    private static TraitDef trait(String id, JsonObject json) {
        List<Effect> effects = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "effects", new com.google.gson.JsonArray())) {
            JsonObject effect = GsonHelper.convertToJsonObject(e, "effect");
            effects.add(new Effect(attribute(GsonHelper.getAsString(effect, "attribute")), multiply(effect),
                    GsonHelper.getAsDouble(effect, "value")));
        }
        Set<String> exclusive = new LinkedHashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "exclusive", new com.google.gson.JsonArray())) {
            exclusive.add(normalize(e.getAsString()));
        }
        return new TraitDef(id, GsonHelper.getAsString(json, "group"), GsonHelper.getAsInt(json, "cost", 0),
                GsonHelper.getAsBoolean(json, "background_only", false), exclusive, List.copyOf(effects),
                levels(json, "body_bonus"), levels(json, "growth_start"), levels(json, "growth_cap"));
    }

    private static BackgroundDef background(String id, JsonObject json) {
        List<String> traits = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "traits", new com.google.gson.JsonArray())) {
            traits.add(normalize(e.getAsString()));
        }
        return new BackgroundDef(id, GsonHelper.getAsInt(json, "order", 0), GsonHelper.getAsInt(json, "budget"),
                List.copyOf(traits), GsonHelper.getAsBoolean(json, "successor_pool", true));
    }

    private static Map<String, Integer> levels(JsonObject json, String key) {
        if (!json.has(key)) return Map.of();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, key).entrySet()) {
            out.put(normalize(e.getKey()), e.getValue().getAsInt());
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static boolean multiply(JsonObject json) {
        String op = GsonHelper.getAsString(json, "op");
        if (!op.equals("add") && !op.equals("multiply")) throw new IllegalArgumentException("op must be add or multiply: " + op);
        return op.equals("multiply");
    }

    private static Attribute attribute(String id) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(id));
        if (attribute == null) throw new IllegalArgumentException("unknown attribute " + id);
        return attribute;
    }
}
