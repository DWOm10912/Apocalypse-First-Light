package com.antaurora.apofirstlight.skeleton.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Player actions V1 (docs/dev/player_actions_v1_plan.md 2.1): the action definitions and the item mapping, read from the
 * mod jar on both sides (not a data pack: the clients draw them, and data packs do not reach the clients), on first use.
 * <ul>
 * <li>{@code assets/apocalypse_firstlight/player_actions/<group>/<name>.json}, id {@code <group>/<name>};</li>
 * <li>{@code assets/apocalypse_firstlight/item_actions/<item path>.json}: {@code {"use": "<action id>"}}, the action
 * played while the item is in use (vanilla already tells every client who uses what, and for how long).</li>
 * </ul>
 * Pure (no Minecraft classes), so the offline checks read the same files.
 */
public final class PlayerActions {
    private static final String ROOT = "assets/apocalypse_firstlight/";
    private static final Map<String, Optional<PlayerAction>> ACTIONS = new HashMap<>();
    private static final Map<String, Optional<String>> USE = new HashMap<>();
    /**
     * Actions made for an item rather than read from a file (ids with a {@code #}: held carry V1's {@code item/carry_hug#<item
     * path>}, client/skeleton/CarryHug): the definition's JSON, or null.
     */
    public static java.util.function.Function<String, JsonObject> made;

    private PlayerActions() {
    }

    /** An action by id, or null when there is no such file. A broken file throws (found by the checks). */
    public static synchronized PlayerAction get(String id) {
        return ACTIONS.computeIfAbsent(id, k -> {
            JsonObject json = k.indexOf('#') >= 0 ? (made == null ? null : made.apply(k)) : read(ROOT + "player_actions/" + k + ".json");
            return Optional.ofNullable(json == null ? null : PlayerAction.parse(k, json));
        }).orElse(null);
    }

    /** The action an item plays while in use (item id path, apocalypse_firstlight items), or null. */
    public static synchronized String useAction(String itemPath) {
        return USE.computeIfAbsent(itemPath, k -> {
            JsonObject json = read(ROOT + "item_actions/" + k + ".json");
            return Optional.ofNullable(json != null && json.has("use") ? json.get("use").getAsString() : null);
        }).orElse(null);
    }

    private static JsonObject read(String path) {
        try (InputStream in = PlayerActions.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("player action file " + path, e);
        }
    }
}
