package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunDefinition;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.WeaponClass;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * How many grid cells an item takes (docs/ui/afl_inventory_grid_v0_plan.md 2), from the item_size data:
 * {@code data/<namespace>/item_size/*.json}, written like item_mass. An item's own entry wins, then the highest-priority
 * matching tag rule, then (native guns) its weapon class's default, else 1x1. A stack takes its item's size. The server
 * resolves every item once per reload and sends the non-1x1 sizes to each client (GridPackets.Sizes); the client asks
 * its own copy.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ItemSizes {
    /** Cells across and down, unrotated. */
    public record Size(int w, int h) {
        public static final Size ONE = new Size(1, 1);

        public Size {
            if (w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE) throw new IllegalArgumentException("size " + w + "x" + h);
        }

        public Size turned(boolean rotated) { return rotated && w != h ? new Size(h, w) : this; }

        public static Size parse(String text) {
            var parts = text.toLowerCase(Locale.ROOT).split("x");
            if (parts.length != 2) throw new IllegalArgumentException("size must be WxH: " + text);
            return new Size(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }

        @Override public String toString() { return w + "×" + h; }
    }

    public static final int MAX_SIDE = 8;

    private record TagRule(ResourceLocation tag, int priority, Size size) {}
    private record Rules(Map<ResourceLocation, Size> items, List<TagRule> tags, Map<WeaponClass, Size> guns) {}

    private static volatile Rules pending = new Rules(Map.of(), List.of(), Map.of());
    private static volatile Map<ResourceLocation, Size> server = Map.of();
    private static volatile Map<ResourceLocation, Size> client = Map.of();
    private static boolean needsPublish = true;

    private ItemSizes() {}

    /** The stack's size on this side (client: the server's sizes as last received). */
    public static Size of(ItemStack stack, boolean clientSide) {
        if (stack.isEmpty()) return Size.ONE;
        if (!clientSide && needsPublish) publish();   // a server nobody joined yet (GameTest): resolve on first use
        return (clientSide ? client : server).getOrDefault(ForgeRegistries.ITEMS.getKey(stack.getItem()), Size.ONE);
    }

    /** The non-1x1 sizes the server resolved, for the sync packet. */
    static Map<ResourceLocation, Size> serverSizes() { return server; }

    /** Client: the sizes from the server replace the old copy. */
    static void receive(Map<ResourceLocation, Size> sizes) { client = Map.copyOf(sizes); }

    /** Client: back to 1x1 for everything (leaving a world). */
    public static void clearClient() { client = Map.of(); }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "item_size") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
                Map<ResourceLocation, Size> items = new HashMap<>();
                List<TagRule> tags = new ArrayList<>();
                Map<WeaponClass, Size> guns = new EnumMap<>(WeaponClass.class);
                for (var id : new TreeSet<>(input.keySet())) {
                    try {
                        var root = input.get(id).getAsJsonObject();
                        if (root.get("format_version").getAsInt() != 1) throw new IllegalArgumentException("format_version must be 1");
                        // each file all or nothing
                        var nextItems = new HashMap<>(items);
                        var nextTags = new ArrayList<>(tags);
                        var nextGuns = new EnumMap<>(guns);
                        if (root.has("items")) for (var entry : root.getAsJsonObject("items").entrySet()) {
                            var item = new ResourceLocation(entry.getKey());
                            if (!ForgeRegistries.ITEMS.containsKey(item)) throw new IllegalArgumentException("unknown item " + item);
                            if (nextItems.putIfAbsent(item, Size.parse(entry.getValue().getAsString())) != null)
                                throw new IllegalArgumentException("duplicate item " + item);
                        }
                        if (root.has("weapon_classes")) for (var entry : root.getAsJsonObject("weapon_classes").entrySet())
                            nextGuns.put(WeaponClass.parse(entry.getKey()), Size.parse(entry.getValue().getAsString()));
                        if (root.has("tag_defaults")) for (var entry : root.getAsJsonArray("tag_defaults")) {
                            JsonObject t = entry.getAsJsonObject();
                            nextTags.add(new TagRule(new ResourceLocation(t.get("tag").getAsString()),
                                    t.has("priority") ? t.get("priority").getAsInt() : 0, Size.parse(t.get("size").getAsString())));
                        }
                        items = nextItems;
                        tags = nextTags;
                        guns = nextGuns;
                    } catch (RuntimeException ex) {
                        ApocalypseFirstLight.LOGGER.error("[AFL GRID] Rejected item size file {}: {}", id, ex.getMessage());
                    }
                }
                pending = new Rules(Map.copyOf(items), List.copyOf(tags), Map.copyOf(guns));
                needsPublish = true;
            }
        });
    }

    /** After tags are bound: every item's size, keeping only the ones that are not 1x1. */
    private static void publish() {
        Rules rules = pending;
        Map<ResourceLocation, Size> resolved = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            var id = ForgeRegistries.ITEMS.getKey(item);
            Size size = rules.items().get(id);
            if (size == null) {
                TagRule best = null;
                for (var rule : rules.tags()) {
                    if (!item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, rule.tag()))) continue;
                    if (best == null || rule.priority() > best.priority()
                            || rule.priority() == best.priority() && rule.tag().toString().compareTo(best.tag().toString()) < 0)
                        best = rule;
                }
                if (best != null) size = best.size();
            }
            if (size == null && item instanceof NativeGunItem gun) size = gunSize(gun, rules.guns());
            if (size != null && !size.equals(Size.ONE)) resolved.put(id, size);
        }
        server = Map.copyOf(resolved);
        needsPublish = false;
    }

    private static Size gunSize(NativeGunItem gun, Map<WeaponClass, Size> guns) {
        try {
            NativeGunDefinition definition = gun.definition();
            return definition == null ? null : guns.get(definition.weaponClass());
        } catch (RuntimeException ex) {
            return null;   // its gun data did not load; the gun keeps 1x1 and the data log says why
        }
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        if (needsPublish || event.getPlayer() == null) publish();
        for (var player : event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer()))
            GridPackets.sendSizes(player);
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        server = Map.of();
        pending = new Rules(Map.of(), List.of(), Map.of());
        needsPublish = true;
    }
}
