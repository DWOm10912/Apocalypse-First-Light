package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.List;

/**
 * Optional asset contract shared by the main and auxiliary magazine top-round visuals.
 * Every anchor field is a bone name or an ordered list of bone names: entry i is the (i + 1)-th round from the top
 * (a double-stack magazine shows its top two rounds), drawn when that many rounds are present.
 */
public record NativeMagazineRoundVisual(List<String> anchors, ResourceLocation geometry, ResourceLocation texture,
                                        List<String> loadedAuxiliaryAnchors, List<String> oldMagazineAnchors,
                                        float[] localOffset, float[] localRotation) {
    public NativeMagazineRoundVisual {
        anchors = List.copyOf(anchors);
        loadedAuxiliaryAnchors = List.copyOf(loadedAuxiliaryAnchors);
        oldMagazineAnchors = List.copyOf(oldMagazineAnchors);
        if (anchors.isEmpty()) throw new IllegalArgumentException("presentation.magazine_round_visual.anchor: required");
        var seen = new HashSet<String>();
        for (var list : List.of(anchors, loadedAuxiliaryAnchors, oldMagazineAnchors))
            for (String anchor : list) {
                validateAnchor(anchor);
                if (!seen.add(anchor)) throw new IllegalArgumentException("presentation.magazine_round_visual: anchors must be distinct");
            }
        if (geometry == null || texture == null)
            throw new IllegalArgumentException("presentation.magazine_round_visual: geometry and texture are required");
        validateVector(localOffset, "local_offset");
        validateVector(localRotation, "local_rotation");
    }

    /** Position of this bone among the main magazine's round anchors (0 = top round), or -1. */
    public int mainIndex(String bone) { return anchors.indexOf(bone); }
    /** Position among the loaded auxiliary (new / inspected) magazine's round anchors, or -1. */
    public int loadedAuxiliaryIndex(String bone) { return loadedAuxiliaryAnchors.indexOf(bone); }
    /** Position among the old (tactically removed) magazine's round anchors, or -1. */
    public int oldMagazineIndex(String bone) { return oldMagazineAnchors.indexOf(bone); }

    public static NativeMagazineRoundVisual parse(JsonObject presentation) {
        if (!presentation.has("magazine_round_visual")) return null;
        if (!presentation.get("magazine_round_visual").isJsonObject())
            throw new IllegalArgumentException("presentation.magazine_round_visual: expected object");
        JsonObject visual = presentation.getAsJsonObject("magazine_round_visual");
        return new NativeMagazineRoundVisual(anchors(visual, "anchor", true),
                resource(visual, "geometry"), resource(visual, "texture"),
                anchors(visual, "loaded_auxiliary_anchor", false), anchors(visual, "old_magazine_anchor", false),
                vector(visual, "local_offset"), vector(visual, "local_rotation"));
    }

    /** A bone name, or an array of bone names ordered from the top round down. */
    private static List<String> anchors(JsonObject object, String key, boolean required) {
        if (!object.has(key)) {
            if (required) throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": required string or array");
            return List.of();
        }
        var element = object.get(key);
        if (element.isJsonArray()) {
            var out = new java.util.ArrayList<String>();
            for (var entry : element.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || entry.getAsString().isBlank())
                    throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": expected bone names");
                out.add(entry.getAsString());
            }
            if (out.isEmpty()) throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": empty array");
            return out;
        }
        return List.of(string(object, key));
    }

    private static float[] vector(JsonObject object, String key) {
        if (!object.has(key)) return new float[3];
        if (!object.get(key).isJsonArray() || object.getAsJsonArray(key).size() != 3)
            throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": expected three numbers");
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            var entry = object.getAsJsonArray(key).get(i);
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": expected three numbers");
            result[i] = entry.getAsFloat();
        }
        return result;
    }

    private static void validateVector(float[] vector, String key) {
        if (vector == null || vector.length != 3)
            throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": expected three numbers");
        for (float value : vector)
            if (!Float.isFinite(value))
                throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": must be finite");
    }

    private static void validateAnchor(String anchor) {
        if (anchor == null || !anchor.matches("[A-Za-z0-9_./-]{1,128}"))
            throw new IllegalArgumentException("presentation.magazine_round_visual: invalid bone name " + anchor);
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive())
            throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": required string");
        JsonPrimitive value = object.getAsJsonPrimitive(key);
        if (!value.isString() || value.getAsString().isBlank())
            throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": required string");
        return value.getAsString();
    }

    private static ResourceLocation resource(JsonObject object, String key) {
        String value = string(object, key);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null)
            throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": invalid resource location");
        return id;
    }
}
