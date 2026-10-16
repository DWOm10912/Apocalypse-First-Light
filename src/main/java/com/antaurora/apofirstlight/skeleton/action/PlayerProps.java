package com.antaurora.apofirstlight.skeleton.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Player actions V1 S2 (docs/dev/player_actions_v1_plan.md 10): a held prop the skeleton places itself instead of hanging
 * the item on the hand, {@code assets/apocalypse_firstlight/player_props/<id>.json}:
 * {@code {"items": [item paths], "scale": s, "bounds": [[x0, y0, z0], [x1, y1, z1]], "symmetry": "x" | "y" | "z",
 * "open_block": block path, "points": {"name": [x, y, z]}}}. Points and bounds are in the item model's own block units (0..1, y up, as its OBJ / JSON
 * geometry); the prop is drawn as the item model at {@code scale}. {@code symmetry}: the item axis across which the prop
 * looks the same (a jerry can's thickness), so a left-side player's placement can be mirrored as a turn (optional: without
 * it the mirrored placement only reflects the turn). {@code open_block}: the block (same geometry, facing north) drawn instead
 * of the item while the action shows it open (its {@code open} state: the jerry can's cap swung off on its hinge). Pure (read from the mod jar on both sides, as PlayerActions).
 * <p>
 * The prop's frame in the model (Java model px: y down, front -z) is the item's turned half round x (y and z flipped, as a
 * model part turns an item up), scaled to px: {@link #local}.
 */
public final class PlayerProps {
    private static final String ROOT = "assets/apocalypse_firstlight/player_props/";
    private static final Map<String, Optional<Prop>> PROPS = new HashMap<>();

    public record Prop(String id, List<String> items, float scale, Vector3f min, Vector3f max, int symmetry, String openBlock,
                       Map<String, Vector3f> points) {
        /** A point (item block units) in the prop's model frame (px, before its placement), or null when unknown. */
        public Vector3f local(String point) {
            Vector3f p = points.get(point);
            return p == null ? null : local(p);
        }

        public Vector3f local(Vector3f item) {
            float k = 16F * scale;
            return new Vector3f(item.x * k, -item.y * k, -item.z * k);
        }

        /**
         * The prop's own reflection (its model frame, px) across its symmetry plane through the bounds' centre, or null
         * without one.
         */
        public Matrix4f reflection() {
            if (symmetry < 0) return null;
            Vector3f c = local(new Vector3f(min).add(max).mul(0.5F));
            Vector3f s = new Vector3f(1, 1, 1);
            s.setComponent(symmetry, -1);
            return new Matrix4f().translation(c).scale(s).translate(-c.x, -c.y, -c.z);
        }

        public boolean holds(String itemPath) {
            return items.contains(itemPath);
        }

        /** The item model's block units to the prop's model frame (px), as {@link #local}. */
        public Matrix4f itemToLocal() {
            float k = 16F * scale;
            return new Matrix4f().scale(k, -k, -k);
        }
    }

    private PlayerProps() {
    }

    public static synchronized Prop get(String id) {
        return PROPS.computeIfAbsent(id, k -> {
            try (InputStream in = PlayerProps.class.getClassLoader().getResourceAsStream(ROOT + k + ".json")) {
                if (in == null) return Optional.empty();
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String, Vector3f> points = new HashMap<>();
                for (var e : json.getAsJsonObject("points").entrySet()) {
                    points.put(e.getKey(), vec(e.getValue().getAsJsonArray()));
                }
                List<String> items = new java.util.ArrayList<>();
                for (var e : json.getAsJsonArray("items")) items.add(e.getAsString());
                JsonArray b = json.getAsJsonArray("bounds");
                Vector3f min = vec(b.get(0).getAsJsonArray()), max = vec(b.get(1).getAsJsonArray());
                int symmetry = json.has("symmetry") ? "xyz".indexOf(json.get("symmetry").getAsString()) : -1;
                String open = json.has("open_block") ? json.get("open_block").getAsString() : null;
                return Optional.of(new Prop(k, List.copyOf(items), json.get("scale").getAsFloat(), min, max, symmetry, open, Map.copyOf(points)));
            } catch (IOException e) {
                throw new IllegalStateException("player prop file " + k, e);
            }
        }).orElse(null);
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
}
