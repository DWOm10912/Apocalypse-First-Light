package com.antaurora.apofirstlight.inventory.grid;

import java.util.List;

/**
 * The cells of one storage as separate rectangles (docs/ui/afl_inventory_grid_v0_plan.md 1): four 1x1 pockets, a
 * rig's pouches, a backpack's one big grid. An item lies inside one section; sections never join, so a 2x2 item does
 * not fit two neighbouring 1x2 pouches.
 */
public record GridLayout(List<Section> sections) {
    public record Section(int w, int h) {
        public Section {
            if (w < 1 || h < 1 || w > 16 || h > 32) throw new IllegalArgumentException("section " + w + "x" + h);
        }
    }

    public GridLayout {
        sections = List.copyOf(sections);
        if (sections.isEmpty() || sections.size() > 32) throw new IllegalArgumentException("sections: " + sections.size());
    }

    /** Sections from width / height pairs. */
    public static GridLayout of(int... wh) {
        if (wh.length % 2 != 0) throw new IllegalArgumentException("width / height pairs");
        Section[] out = new Section[wh.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = new Section(wh[2 * i], wh[2 * i + 1]);
        return new GridLayout(List.of(out));
    }

    public Section section(int index) { return sections.get(index); }

    public int size() { return sections.size(); }
}
