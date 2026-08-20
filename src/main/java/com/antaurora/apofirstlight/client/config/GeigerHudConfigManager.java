package com.antaurora.apofirstlight.client.config;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Loads the packaged Geiger HUD layout from the client resource manager. */
public final class GeigerHudConfigManager {
    private static final ResourceLocation LAYOUT = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "gui/layout/geiger_hud.json");
    private static GeigerHudConfig current = GeigerHudConfig.defaults();

    private GeigerHudConfigManager() {
    }

    public static GeigerHudConfig get() {
        return current;
    }

    public static void load(ResourceManager resourceManager) {
        current = read(resourceManager);
    }

    public static SimplePreparableReloadListener<GeigerHudConfig> reloadListener() {
        return new SimplePreparableReloadListener<>() {
            @Override
            protected GeigerHudConfig prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return read(resourceManager);
            }

            @Override
            protected void apply(GeigerHudConfig layout, ResourceManager resourceManager, ProfilerFiller profiler) {
                current = layout;
            }
        };
    }

    private static GeigerHudConfig read(ResourceManager resourceManager) {
        try (var reader = resourceManager.getResourceOrThrow(LAYOUT).openAsReader()) {
            return sanitize(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception exception) {
            ApocalypseFirstLight.LOGGER.warn("[AFL] Could not load Geiger HUD layout {}; using defaults.", LAYOUT, exception);
            return GeigerHudConfig.defaults();
        }
    }

    private static GeigerHudConfig sanitize(JsonObject object) {
        GeigerHudConfig defaults = GeigerHudConfig.defaults();
        JsonObject symbol = object(object, "symbol");
        JsonObject text = object(object, "text");
        JsonObject rows = object(object, "rows");
        return new GeigerHudConfig(
                readBoolean(object, "enabled", defaults.enabled()),
                readAnchor(object, defaults.anchor()),
                readInteger(object, "offsetX", defaults.offsetX()),
                readInteger(object, "offsetY", defaults.offsetY()),
                readScale(object, "hudScale", defaults.hudScale()),
                new GeigerHudConfig.SymbolLayout(
                        readInteger(symbol, "x", defaults.symbol().x()),
                        readInteger(symbol, "y", defaults.symbol().y()),
                        readScale(symbol, "scale", defaults.symbol().scale())
                ),
                new GeigerHudConfig.TextLayout(
                        readInteger(text, "x", defaults.text().x()),
                        readInteger(text, "y", defaults.text().y()),
                        readScale(text, "fontScale", defaults.text().fontScale()),
                        readInteger(text, "lineSpacing", defaults.text().lineSpacing())
                ),
                new GeigerHudConfig.Rows(
                        rowOffset(rows, "radiation", defaults.rows().radiation()),
                        rowOffset(rows, "dose", defaults.rows().dose()),
                        rowOffset(rows, "zone", defaults.rows().zone())
                )
        );
    }

    private static JsonObject object(JsonObject parent, String key) {
        try {
            return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static GeigerHudConfig.RowOffset rowOffset(JsonObject rows, String key,
                                                         GeigerHudConfig.RowOffset fallback) {
        JsonObject row = object(rows, key);
        return new GeigerHudConfig.RowOffset(
                readInteger(row, "offsetX", fallback.offsetX()),
                readInteger(row, "offsetY", fallback.offsetY())
        );
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInteger(JsonObject object, String key, int fallback) {
        try {
            return Math.max(-1000, Math.min(1000, object.get(key).getAsInt()));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float readScale(JsonObject object, String key, float fallback) {
        try {
            float value = object.get(key).getAsFloat();
            return Float.isFinite(value) ? Math.max(0.5F, Math.min(2.0F, value)) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readAnchor(JsonObject object, String fallback) {
        try {
            return "bottom_right".equalsIgnoreCase(object.get("anchor").getAsString())
                    ? "bottom_right" : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
