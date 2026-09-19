package com.antaurora.apofirstlight.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.function.Consumer;

/** Local presentation only. Missing/invalid fields independently fall back to safe defaults. */
public record NativeGunHudConfig(String anchor, Global global, Silhouette silhouette, Divider divider,
                                 Text weaponName, Ammo ammo, Mode fireMode) {
    public record Global(float offsetX, float offsetY, float scale, float width, float height,
                         float rightMargin, float bottomMargin) {}
    /** Offsets denote the silhouette's centre, not its top-left corner. */
    public record Silhouette(float offsetX, float offsetY, float scale, float alpha, String color, String emptyColor) {}
    public record Divider(float offsetX, float offsetY, float width, float height, String color, float alpha) {}
    /** Text rows are centred on offsetX; offsetY denotes the row top. */
    public record Text(float offsetX, float offsetY, float scale, float maxWidth, String color) {}
    public record Ammo(float offsetX, float offsetY, float currentScale, float reserveScale,
                       float separatorScale, float maxWidth, float gap, String currentColor,
                       String reserveColor, String separatorColor, String emptyColor, String flashColor) {}
    public record Mode(float offsetX, float offsetY, float scale, float maxWidth,
                       String semiColor, String burstColor, String autoColor) {}

    public static NativeGunHudConfig defaults() {
        return new NativeGunHudConfig("bottom_right",
                new Global(0, 0, 1, 84, 38, 10, 61),
                new Silhouette(18, 19, .65F, 1, "#FFFFFF", "#FF2626"),
                new Divider(39, 3, 1, 32, "#FFFFFF", .9F),
                new Text(64, 2, .75F, 38, "#FFFFFF"),
                new Ammo(64, 14, 1.15F, 1, .9F, 38, 2,
                        "#FFFFFF", "#D0D0D0", "#B8B8B8", "#FF3333", "#FF3333"),
                new Mode(64, 28, .78F, 38, "#9FC7D9", "#D6A15F", "#D97878"));
    }

    public static NativeGunHudConfig parse(String json, Consumer<String> warning) {
        var d = defaults();
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("anchor") && (!root.get("anchor").isJsonPrimitive()
                    || !"bottom_right".equals(root.get("anchor").getAsString())))
                warning.accept("anchor: only bottom_right is supported; using default");
            var g = new Reader(root, "global", warning);
            var s = new Reader(root, "silhouette", warning);
            var v = new Reader(root, "divider", warning);
            var n = new Reader(root, "weapon_name", warning);
            var a = new Reader(root, "ammo", warning);
            var m = new Reader(root, "fire_mode", warning);
            return new NativeGunHudConfig(d.anchor,
                    new Global(g.offset("offset_x", d.global.offsetX), g.offset("offset_y", d.global.offsetY),
                            g.number("scale", d.global.scale, .25F, 2),
                            g.number("width", d.global.width, 40, 160), g.number("height", d.global.height, 24, 100),
                            g.number("right_margin", d.global.rightMargin, 0, 500),
                            g.number("bottom_margin", d.global.bottomMargin, 0, 500)),
                    new Silhouette(s.offset("offset_x", d.silhouette.offsetX), s.offset("offset_y", d.silhouette.offsetY),
                            s.scale("scale", d.silhouette.scale), s.number("alpha", d.silhouette.alpha, 0, 1),
                            s.color("color", d.silhouette.color), s.color("empty_color", d.silhouette.emptyColor)),
                    new Divider(v.offset("offset_x", d.divider.offsetX), v.offset("offset_y", d.divider.offsetY),
                            v.number("width", d.divider.width, .5F, 4), v.number("height", d.divider.height, 1, 100),
                            v.color("color", d.divider.color), v.number("alpha", d.divider.alpha, 0, 1)),
                    new Text(n.offset("offset_x", d.weaponName.offsetX), n.offset("offset_y", d.weaponName.offsetY),
                            n.scale("scale", d.weaponName.scale), n.width(d.weaponName.maxWidth), n.color("color", d.weaponName.color)),
                    new Ammo(a.offset("offset_x", d.ammo.offsetX), a.offset("offset_y", d.ammo.offsetY),
                            a.scale("current_scale", d.ammo.currentScale), a.scale("reserve_scale", d.ammo.reserveScale),
                            a.scale("separator_scale", d.ammo.separatorScale), a.width(d.ammo.maxWidth),
                            a.number("gap", d.ammo.gap, 0, 12), a.color("current_color", d.ammo.currentColor),
                            a.color("reserve_color", d.ammo.reserveColor), a.color("separator_color", d.ammo.separatorColor),
                            a.color("empty_color", d.ammo.emptyColor), a.color("flash_color", d.ammo.flashColor)),
                    new Mode(m.offset("offset_x", d.fireMode.offsetX), m.offset("offset_y", d.fireMode.offsetY),
                            m.scale("scale", d.fireMode.scale), m.width(d.fireMode.maxWidth),
                            m.color("semi_color", d.fireMode.semiColor), m.color("burst_color", d.fireMode.burstColor),
                            m.color("auto_color", d.fireMode.autoColor)));
        } catch (RuntimeException e) {
            warning.accept("Invalid native_gun_hud.json: " + e.getMessage() + "; using defaults");
            return d;
        }
    }

    public static int argb(String color, float alpha) {
        return (Math.round(alpha * 255) << 24) | Integer.parseInt(color.substring(1), 16);
    }

    private static final class Reader {
        private final JsonObject object;
        private final String section;
        private final Consumer<String> warning;
        Reader(JsonObject root, String section, Consumer<String> warning) {
            this.object = section(root, section, warning);
            this.section = section;
            this.warning = warning;
        }
        private static JsonObject section(JsonObject root, String key, Consumer<String> warning) {
            if (!root.has(key)) return new JsonObject();
            if (root.get(key).isJsonObject()) return root.getAsJsonObject(key);
            warning.accept(key + ": expected object; using section defaults");
            return new JsonObject();
        }
        float offset(String key, float fallback) { return number(key, fallback, -500, 500); }
        float scale(String key, float fallback) { return number(key, fallback, .25F, 3); }
        float width(float fallback) { return number("max_width", fallback, 8, 160); }
        float number(String key, float fallback, float min, float max) {
            if (!object.has(key)) return fallback;
            try {
                var value = object.getAsJsonPrimitive(key);
                if (!value.isNumber()) throw new IllegalArgumentException("expected number");
                float number = value.getAsFloat();
                if (Float.isFinite(number) && number >= min && number <= max) return number;
            } catch (RuntimeException ignored) { }
            warning.accept(section + "." + key + ": expected finite number in [" + min + ", " + max + "]; using default");
            return fallback;
        }
        String color(String key, String fallback) {
            if (!object.has(key)) return fallback;
            try {
                var value = object.getAsJsonPrimitive(key);
                if (value.isString() && value.getAsString().matches("#[0-9a-fA-F]{6}")) return value.getAsString();
            } catch (RuntimeException ignored) { }
            warning.accept(section + "." + key + ": expected #RRGGBB; using default");
            return fallback;
        }
    }
}
