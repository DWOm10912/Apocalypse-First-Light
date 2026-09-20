package com.antaurora.apofirstlight.client.model;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.ElementsModel;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Preserve approved single-axis angles; Forge ElementsModel/FaceBakery bakes the exact rotations. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HearingProtectionModelLoader implements IGeometryLoader<ElementsModel> {
    @SubscribeEvent
    public static void register(ModelEvent.RegisterGeometryLoaders event) {
        event.register("hearing_protection", new HearingProtectionModelLoader());
    }

    @Override
    public ElementsModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        List<BlockElement> elements = new ArrayList<>();
        // Not "elements": BlockModel's base deserializer runs BEFORE the custom loader.
        for (JsonElement value : GsonHelper.getAsJsonArray(json, "afl_elements")) {
            JsonObject copy = value.getAsJsonObject().deepCopy();
            float angle = 0;
            if (copy.has("rotation")) {
                JsonObject rotation = copy.getAsJsonObject("rotation");
                if (rotation.has("angle")) {
                    angle = GsonHelper.getAsFloat(rotation, "angle");
                } else {
                    // Blockbench exports rotations outside vanilla's range as x/y/z.
                    // The approved asset uses one axis per cube; retain its full angle.
                    String axis = "y";
                    int axes = 0;
                    for (String candidate : List.of("x", "y", "z")) {
                        float valueOnAxis = GsonHelper.getAsFloat(rotation, candidate, 0);
                        if (valueOnAxis != 0) {
                            axes++;
                            axis = candidate;
                            angle = valueOnAxis;
                        }
                    }
                    if (axes > 1) throw new JsonParseException("Expected single-axis hearing protection cube");
                    rotation.addProperty("axis", axis);
                }
                if (!Float.isFinite(angle) || GsonHelper.getAsBoolean(rotation, "rescale", false)) {
                    throw new JsonParseException("Hearing protection requires finite, non-rescaled rotations");
                }
                // Reuse vanilla validation of axis, origin, faces and UVs. Only its angle
                // whitelist is bypassed, restoring the source angle before baking.
                rotation.addProperty("angle", 0);
            }
            BlockElement element = context.deserialize(copy, BlockElement.class);
            BlockElementRotation rotation = element.rotation == null ? null
                    : new BlockElementRotation(element.rotation.origin(), element.rotation.axis(), angle, false);
            elements.add(new BlockElement(element.from, element.to, element.faces, rotation,
                    element.shade, element.getFaceData()));
        }
        return new ElementsModel(elements);
    }
}
