package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Material;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Recipe;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Unlock;
import com.antaurora.apofirstlight.item.MeteredSupplyItem;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The workbench's recipes as data (docs/gameplay/afl_workbench_v1_design.md 18, user 2026-10-21: one folder per category,
 * each recipe its time, materials and how it is learned, so books and survival guides can be made from them later):
 * {@code data/<namespace>/workbench_recipes/<category>/<name>.json}, the recipe id {@code <namespace>:<category>/<name>}.
 * The folder is the category (one of {@link #CATEGORIES}). Each file stands alone: a bad one is logged and left out, the
 * others load. Tags in a material line are read once the tags are bound (the first use after a reload), like item_size.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WorkbenchRecipeData {
    /** The page's categories in list order (client/workbench/WorkbenchRecipeView.Category, the same names). */
    public static final List<String> CATEGORIES = List.of("woodwork", "furniture", "tools", "building", "living", "repair", "salvage");
    /** How a recipe is learned (WorkbenchKnowledge; V1 reads everyone and background, the others wait for their systems). */
    public static final Set<String> UNLOCKS = Set.of("everyone", "background", "materials", "after", "book", "blueprint");

    /** A material line as written: items and tags, read into items at publish; its unit (count, litres as mL, pieces). */
    private record Line(List<ResourceLocation> items, List<ResourceLocation> tags, int count, WorkbenchCrafting.Unit unit) {}

    private record Raw(ResourceLocation id, String category, ResourceLocation result, int count, List<Line> materials,
                       List<ResourceLocation> tools, int ticks, @Nullable String work, List<Unlock> unlock, List<FurnitureWork.Step> steps) {}

    private static volatile List<Raw> pending = List.of();
    private static volatile List<Recipe> server = List.of();
    private static boolean needsPublish = true;
    /** Goes up with every publish: the knowledge sync resends when it changed (WorkbenchKnowledge). */
    private static int generation;

    private WorkbenchRecipeData() {}

    /** Every loaded recipe, server side, in id order. */
    public static List<Recipe> all() {
        if (needsPublish) publish();
        return server;
    }

    @Nullable
    public static Recipe find(ResourceLocation id) {
        for (Recipe r : all()) if (r.id().equals(id)) return r;
        return null;
    }

    static int generation() {
        if (needsPublish) publish();
        return generation;
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "workbench_recipes") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                List<Raw> raws = new ArrayList<>();
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        raws.add(parse(id, input.get(id).getAsJsonObject()));
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL WORKBENCH] Rejected recipe file {}: {}", id, ex.getMessage());
                    }
                }
                pending = List.copyOf(raws);
                needsPublish = true;
            }
        });
    }

    private static Raw parse(ResourceLocation id, JsonObject root) {
        if (root.get("format_version").getAsInt() != 1) throw new IllegalArgumentException("format_version must be 1");
        int slash = id.getPath().indexOf('/');
        String category = slash < 0 ? "" : id.getPath().substring(0, slash);
        if (!CATEGORIES.contains(category) || id.getPath().indexOf('/', slash + 1) >= 0)
            throw new IllegalArgumentException("must sit directly in a category folder " + CATEGORIES);
        JsonObject result = root.getAsJsonObject("result");
        ResourceLocation item = known(result.get("item").getAsString());
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        if (count < 1 || count > 64) throw new IllegalArgumentException("result count must be 1..64");
        List<Line> lines = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("materials")) {
            JsonObject m = e.getAsJsonObject();
            List<ResourceLocation> items = new ArrayList<>(), tags = new ArrayList<>();
            if (m.has("item")) items.add(known(m.get("item").getAsString()));
            if (m.has("items")) for (JsonElement i : m.getAsJsonArray("items")) items.add(known(i.getAsString()));
            if (m.has("tag")) tags.add(new ResourceLocation(m.get("tag").getAsString()));
            if (items.isEmpty() && tags.isEmpty()) throw new IllegalArgumentException("a material line needs item, items or tag");
            // one of count, litres (a can's mL), pieces (a box's nails or screws): the unit is in the field's name
            int fields = (m.has("count") ? 1 : 0) + (m.has("litres") ? 1 : 0) + (m.has("pieces") ? 1 : 0);
            if (fields > 1) throw new IllegalArgumentException("a material line takes one of count, litres, pieces");
            WorkbenchCrafting.Unit unit = m.has("litres") ? WorkbenchCrafting.Unit.ML : m.has("pieces") ? WorkbenchCrafting.Unit.PIECES : WorkbenchCrafting.Unit.COUNT;
            int n;
            if (unit == WorkbenchCrafting.Unit.COUNT) n = m.has("count") ? m.get("count").getAsInt() : 1;
            else {
                if (!m.has("item") || m.has("items") || m.has("tag")) throw new IllegalArgumentException("litres and pieces lines take one item only");
                if (!(ForgeRegistries.ITEMS.getValue(items.get(0)) instanceof MeteredSupplyItem metered))
                    throw new IllegalArgumentException(unit.name().toLowerCase(Locale.ROOT) + " on " + items.get(0) + ", which is not a metered supply");
                if (metered.litres() != (unit == WorkbenchCrafting.Unit.ML))
                    throw new IllegalArgumentException(unit == WorkbenchCrafting.Unit.ML ? "litres on a pieces item " + items.get(0) : "pieces on a litres item " + items.get(0));
                n = unit == WorkbenchCrafting.Unit.ML ? (int) Math.max(1, Math.round(m.get("litres").getAsDouble() * 1000)) : m.get("pieces").getAsInt();
            }
            if (n < 1) throw new IllegalArgumentException("material count must be at least 1");
            lines.add(new Line(List.copyOf(items), List.copyOf(tags), n, unit));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("no materials");
        List<ResourceLocation> tools = new ArrayList<>();   // "tool": one, "tools": several, all carried
        if (root.has("tool")) tools.add(known(root.get("tool").getAsString()));
        if (root.has("tools")) for (JsonElement t : root.getAsJsonArray("tools")) tools.add(known(t.getAsString()));
        String work = null;
        int ticks;
        List<FurnitureWork.Step> steps = new ArrayList<>();
        if (root.has("work") && root.get("work").isJsonArray()) {
            // a furniture piece's steps (FurnitureWork): the time is the actions' (with the nails and screws at the novice's)
            if (root.has("seconds")) throw new IllegalArgumentException("work as steps takes no seconds: the time is the steps' actions");
            String piece = null;
            for (JsonElement e : root.getAsJsonArray("work")) {
                JsonObject s = e.getAsJsonObject();
                String step = s.get("step").getAsString();
                if (FurnitureWork.kind(step) == null) throw new IllegalArgumentException("unknown work step " + step);
                if (PlayerActions.get(step) == null) throw new IllegalArgumentException("no player action " + step);
                int times = s.has("times") ? s.get("times").getAsInt() : 1;
                if (times < 1 || times > 64) throw new IllegalArgumentException("step times must be 1..64");
                String p = s.has("piece") ? s.get("piece").getAsString() : null;
                if (p != null && !FurnitureWork.PIECES.contains(p)) throw new IllegalArgumentException("unknown piece " + p + ", one of " + new TreeSet<>(FurnitureWork.PIECES));
                if (p != null) piece = p;
                if (FurnitureWork.kind(step) != FurnitureWork.Kind.TIMED && piece == null && !FurnitureWork.CROSSCUT.equals(step))
                    throw new IllegalArgumentException(step + " before any piece");
                steps.add(new FurnitureWork.Step(step, times, p));
            }
            if (steps.isEmpty()) throw new IllegalArgumentException("work as steps needs at least one step");
            work = steps.get(0).step();
            ticks = FurnitureWork.estimate(steps, 0);
        } else {
            double seconds = root.get("seconds").getAsDouble();
            if (!(seconds > 0) || seconds > 3600) throw new IllegalArgumentException("seconds must be over 0 and at most 3600");
            ticks = (int) Math.round(seconds * 20);
            work = root.has("work") ? root.get("work").getAsString() : null;
        }
        List<Unlock> unlock = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("unlock")) {
            JsonObject u = e.getAsJsonObject();
            String type = u.get("type").getAsString().toLowerCase(Locale.ROOT);
            if (!UNLOCKS.contains(type)) throw new IllegalArgumentException("unknown unlock type " + type + ", one of " + new TreeSet<>(UNLOCKS));
            String value = switch (type) {
                case "background" -> background(u.get("background").getAsString());
                case "after" -> new ResourceLocation(u.get("recipe").getAsString()).toString();
                case "book", "blueprint" -> new ResourceLocation(u.get("item").getAsString()).toString();   // the item may come later
                default -> null;
            };
            unlock.add(new Unlock(type, value));
        }
        if (unlock.isEmpty()) throw new IllegalArgumentException("no unlock: nobody could ever make it");
        return new Raw(id, category.toUpperCase(Locale.ROOT), item, count, List.copyOf(lines), List.copyOf(tools), ticks, work, List.copyOf(unlock), List.copyOf(steps));
    }

    /** A survivor background id as the survivor records keep it ("afl:forest_resident"); a bare name gets "afl:". */
    private static String background(String name) {
        return name.contains(":") ? name : "afl:" + name;
    }

    private static ResourceLocation known(String name) {
        ResourceLocation id = new ResourceLocation(name);
        if (!ForgeRegistries.ITEMS.containsKey(id)) throw new IllegalArgumentException("unknown item " + id);
        return id;
    }

    /** After tags are bound: the material lines' tags read into items; a line left with no item drops its recipe. */
    private static synchronized void publish() {
        if (!needsPublish) return;
        List<Recipe> out = new ArrayList<>();
        Set<ResourceLocation> ids = new TreeSet<>();
        for (Raw raw : pending) ids.add(raw.id());
        for (Raw raw : pending) {
            List<Material> materials = new ArrayList<>();
            boolean ok = true;
            for (Line line : raw.materials()) {
                Set<ResourceLocation> items = new LinkedHashSet<>(line.items());
                for (ResourceLocation tag : line.tags()) {
                    var tags = ForgeRegistries.ITEMS.tags();
                    if (tags != null) for (var item : tags.getTag(TagKey.create(Registries.ITEM, tag))) items.add(ForgeRegistries.ITEMS.getKey(item));
                }
                if (items.isEmpty()) {
                    ApocalypseFirstLight.LOGGER.error("[AFL WORKBENCH] Recipe {}: material tags {} hold no items, recipe left out", raw.id(), line.tags());
                    ok = false;
                    break;
                }
                materials.add(new Material(List.copyOf(items), line.count(), line.unit()));
            }
            if (!ok) continue;
            for (Unlock u : raw.unlock())
                if (u.type().equals("after") && !ids.contains(new ResourceLocation(u.value())))
                    ApocalypseFirstLight.LOGGER.warn("[AFL WORKBENCH] Recipe {}: unlock after {}, which is not a recipe", raw.id(), u.value());
            out.add(new Recipe(raw.id(), raw.category(), raw.result(), raw.count(), List.copyOf(materials), raw.tools(), raw.ticks(),
                    raw.work(), raw.unlock(), raw.steps()));
        }
        server = List.copyOf(out);
        generation++;
        needsPublish = false;
        ApocalypseFirstLight.LOGGER.info("[AFL WORKBENCH] {} workbench recipes", server.size());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        server = List.of();
        pending = List.of();
        needsPublish = true;
    }
}
