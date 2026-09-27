package com.antaurora.apofirstlight.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

/** Optional asset contract shared by the main and auxiliary magazine top-round visuals. */
public record NativeMagazineRoundVisual(String anchor, ResourceLocation geometry, ResourceLocation texture,
                                        String loadedAuxiliaryAnchor, String oldMagazineAnchor,
                                        float[] localOffset, float[] localRotation) {
    public NativeMagazineRoundVisual {
        validateAnchor(anchor, "anchor");
        if (loadedAuxiliaryAnchor != null) validateAnchor(loadedAuxiliaryAnchor, "loaded_auxiliary_anchor");
        if (oldMagazineAnchor != null) validateAnchor(oldMagazineAnchor, "old_magazine_anchor");
        if (anchor.equals(loadedAuxiliaryAnchor) || anchor.equals(oldMagazineAnchor)
                || loadedAuxiliaryAnchor != null && loadedAuxiliaryAnchor.equals(oldMagazineAnchor))
            throw new IllegalArgumentException("presentation.magazine_round_visual: anchors must be distinct");
        if (geometry == null || texture == null)
            throw new IllegalArgumentException("presentation.magazine_round_visual: geometry and texture are required");
        validateVector(localOffset, "local_offset");
        validateVector(localRotation, "local_rotation");
    }

    public static NativeMagazineRoundVisual parse(JsonObject presentation) {
        if (!presentation.has("magazine_round_visual")) return null;
        if (!presentation.get("magazine_round_visual").isJsonObject())
            throw new IllegalArgumentException("presentation.magazine_round_visual: expected object");
        JsonObject visual = presentation.getAsJsonObject("magazine_round_visual");
        return new NativeMagazineRoundVisual(string(visual, "anchor"),
                resource(visual, "geometry"), resource(visual, "texture"),
                optionalAnchor(visual, "loaded_auxiliary_anchor"), optionalAnchor(visual, "old_magazine_anchor"),
                vector(visual, "local_offset"), vector(visual, "local_rotation"));
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

    private static void validateAnchor(String anchor, String key) {
        if (anchor == null || !anchor.matches("[A-Za-z0-9_./-]{1,128}"))
            throw new IllegalArgumentException("presentation.magazine_round_visual." + key + ": invalid bone name");
    }

    private static String optionalAnchor(JsonObject object, String key) {
        return object.has(key) ? string(object, key) : null;
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
