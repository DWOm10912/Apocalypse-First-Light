package com.antaurora.apofirstlight.menu.layout;

import java.util.List;
import java.util.Map;

public record MachineGuiLayout(Size gui, Point title, Point inventoryLabel,
                               Grid playerInventory, Grid hotbar,
                               Map<String, Element> elements,
                               List<Element> outputSlots) {
    public MachineGuiLayout {
        elements = Map.copyOf(elements);
        outputSlots = List.copyOf(outputSlots);
    }

    public Element element(String name) {
        Element element = elements.get(name);
        if (element == null) {
            throw new IllegalStateException("Machine GUI layout is missing element " + name);
        }
        return element;
    }

    public record Size(int width, int height) {
    }

    public record Point(int x, int y) {
    }

    public record Grid(int x, int y, int columns, int rows, int spacing) {
    }

    public record Element(int x, int y, int width, int height) {
    }
}
