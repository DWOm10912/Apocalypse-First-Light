package com.antaurora.apofirstlight.menu.layout;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MachineGuiLayouts {
    private static final String BASE_PATH = "assets/" + ApocalypseFirstLight.MOD_ID + "/machine_layout/";
    private static final MachineGuiLayout THERMAL_GENERATOR = load(
            "thermal_generator", Set.of("fuel_slot", "fire_icon", "progress_arrow", "energy_bar", "energy_fill"), 0);
    private static final MachineGuiLayout ENERGY_CELL = load(
            "energy_cell", Set.of("storage_bar", "storage_fill"), 0);
    private static final MachineGuiLayout CRUSHER = load(
            "crusher", Set.of("input_slot", "progress_arrow", "energy_bar", "energy_fill"), 6);

    private MachineGuiLayouts() {
    }

    public static MachineGuiLayout thermalGenerator() {
        return THERMAL_GENERATOR;
    }

    public static MachineGuiLayout energyCell() {
        return ENERGY_CELL;
    }

    public static MachineGuiLayout crusher() {
        return CRUSHER;
    }

    private static MachineGuiLayout load(String name, Set<String> requiredElements, int outputSlotCount) {
        String resourcePath = BASE_PATH + name + ".json";
        try (InputStream stream = MachineGuiLayouts.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing built-in machine GUI layout " + resourcePath);
            }
            JsonObject root = requireObject(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)), resourcePath);
            MachineGuiLayout layout = new MachineGuiLayout(
                    readSize(requireObject(root.get("gui"), resourcePath + " gui"), resourcePath),
                    readPoint(requireObject(root.get("title"), resourcePath + " title"), resourcePath + " title"),
                    readPoint(requireObject(root.get("inventory_label"), resourcePath + " inventory_label"),
                            resourcePath + " inventory_label"),
                    readGrid(requireObject(root.get("player_inventory"), resourcePath + " player_inventory"),
                            resourcePath + " player_inventory"),
                    readGrid(requireObject(root.get("hotbar"), resourcePath + " hotbar"),
                            resourcePath + " hotbar"),
                    readElements(requireObject(root.get("elements"), resourcePath + " elements"), resourcePath),
                    readOutputSlots(requireObject(root.get("elements"), resourcePath + " elements"), resourcePath));
            validate(layout, name, requiredElements, outputSlotCount);
            return layout;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load machine GUI layout " + resourcePath + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static MachineGuiLayout.Size readSize(JsonObject object, String context) {
        return new MachineGuiLayout.Size(
                requirePositiveInt(object, "width", context),
                requirePositiveInt(object, "height", context));
    }

    private static MachineGuiLayout.Point readPoint(JsonObject object, String context) {
        return new MachineGuiLayout.Point(requireInt(object, "x", context), requireInt(object, "y", context));
    }

    private static MachineGuiLayout.Grid readGrid(JsonObject object, String context) {
        return new MachineGuiLayout.Grid(
                requireInt(object, "x", context),
                requireInt(object, "y", context),
                requirePositiveInt(object, "columns", context),
                requirePositiveInt(object, "rows", context),
                requirePositiveInt(object, "spacing", context));
    }

    private static Map<String, MachineGuiLayout.Element> readElements(JsonObject object, String context) {
        Map<String, MachineGuiLayout.Element> elements = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals("output_slots")) {
                continue;
            }
            JsonObject element = requireObject(entry.getValue(), context + " element " + entry.getKey());
            elements.put(entry.getKey(), readElement(element, context + " element " + entry.getKey()));
        }
        return elements;
    }

    private static List<MachineGuiLayout.Element> readOutputSlots(JsonObject object, String context) {
        JsonElement outputSlotsElement = object.get("output_slots");
        if (outputSlotsElement == null) {
            return List.of();
        }
        if (!outputSlotsElement.isJsonArray()) {
            throw new IllegalArgumentException(context + " output_slots must be an array");
        }
        JsonArray array = outputSlotsElement.getAsJsonArray();
        List<MachineGuiLayout.Element> slots = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonObject slot = requireObject(array.get(index), context + " output_slots[" + index + "]");
            slots.add(readElement(slot, context + " output_slots[" + index + "]"));
        }
        return slots;
    }

    private static MachineGuiLayout.Element readElement(JsonObject object, String context) {
        return new MachineGuiLayout.Element(
                requireInt(object, "x", context),
                requireInt(object, "y", context),
                requirePositiveInt(object, "width", context),
                requirePositiveInt(object, "height", context));
    }

    private static void validate(MachineGuiLayout layout, String name, Set<String> requiredElements,
                                 int outputSlotCount) {
        if (layout.playerInventory().columns() != 9 || layout.playerInventory().rows() != 3) {
            throw new IllegalArgumentException(name + " player_inventory must be a 9x3 grid");
        }
        if (layout.hotbar().columns() != 9 || layout.hotbar().rows() != 1) {
            throw new IllegalArgumentException(name + " hotbar must be a 9x1 grid");
        }
        for (String required : requiredElements) {
            if (!layout.elements().containsKey(required)) {
                throw new IllegalArgumentException(name + " layout is missing required element " + required);
            }
        }
        if (layout.outputSlots().size() != outputSlotCount) {
            throw new IllegalArgumentException(name + " layout requires " + outputSlotCount
                    + " output slots but defines " + layout.outputSlots().size());
        }
        validateGrid(layout, layout.playerInventory(), "player_inventory");
        validateGrid(layout, layout.hotbar(), "hotbar");
        layout.elements().forEach((elementName, element) -> validateElement(layout, element, elementName));
        for (int index = 0; index < layout.outputSlots().size(); index++) {
            validateElement(layout, layout.outputSlots().get(index), "output_slots[" + index + "]");
        }
    }

    private static void validateGrid(MachineGuiLayout layout, MachineGuiLayout.Grid grid, String name) {
        int maxX = grid.x() + (grid.columns() - 1) * grid.spacing() + 16;
        int maxY = grid.y() + (grid.rows() - 1) * grid.spacing() + 16;
        if (grid.x() < 0 || grid.y() < 0 || maxX > layout.gui().width() || maxY > layout.gui().height()) {
            throw new IllegalArgumentException(name + " grid exceeds GUI bounds");
        }
    }

    private static void validateElement(MachineGuiLayout layout, MachineGuiLayout.Element element, String name) {
        if (element.x() < 0 || element.y() < 0
                || element.x() + element.width() > layout.gui().width()
                || element.y() + element.height() > layout.gui().height()) {
            throw new IllegalArgumentException(name + " exceeds GUI bounds");
        }
    }

    private static JsonObject requireObject(JsonElement element, String context) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(context + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static int requirePositiveInt(JsonObject object, String field, String context) {
        int value = requireInt(object, field, context);
        if (value <= 0) {
            throw new IllegalArgumentException(context + " field " + field + " must be > 0");
        }
        return value;
    }

    private static int requireInt(JsonObject object, String field, String context) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(context + " missing integer field " + field);
        }
        try {
            return new BigDecimal(element.getAsString()).intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(context + " field " + field + " must be a 32-bit integer");
        }
    }
}
