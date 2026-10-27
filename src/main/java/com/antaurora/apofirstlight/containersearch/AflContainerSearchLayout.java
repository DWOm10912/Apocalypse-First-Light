package com.antaurora.apofirstlight.containersearch;

import com.antaurora.apofirstlight.inventory.grid.GridLayout;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid sections of a searchable container (docs/ui/afl_container_search_page_v1.md 12, user 2026-10-27: "容器分区改成
 * 网格"): named rectangles of cells, at most 8 across (the search page's right column), each like a grid section of the
 * player's own storages. An item lies inside one section and takes its item_size footprint.
 * <p>The container keeps one slot per cell, sections in order and each row by row: an item's stack sits in the slot of
 * its top-left cell (its anchor), the other cells it covers stay empty, and whether it lies turned is kept in the search
 * state (AflContainerGrid). The cell count is the container size. The layout travels with the menu's open data.
 */
public final class AflContainerSearchLayout {
    /** Widest section: 8 cells of 18 fill the search page's right column. */
    public static final int MAX_W = 8, MAX_H = 16;

    /** One rectangle of cells; {@code name} names its lang key (container.apocalypse_firstlight.section.NAME), "" for none. */
    public record Section(String name, int w, int h) {
        public Section {
            if (w < 1 || h < 1 || w > MAX_W || h > MAX_H) throw new IllegalArgumentException("section " + w + "x" + h);
        }

        public int size() { return w * h; }

        public Component title() { return Component.translatable("container.apocalypse_firstlight.section." + name); }
    }

    public static final AflContainerSearchLayout GRID_3X3 = single(3, 3);
    public static final AflContainerSearchLayout GRID_4X3 = single(4, 3);
    public static final AflContainerSearchLayout GRID_6X2 = single(6, 2);
    public static final AflContainerSearchLayout GRID_6X3 = single(6, 3);
    public static final AflContainerSearchLayout GRID_7X4 = single(7, 4);

    private final List<Section> sections;
    private final int[] first;
    private final int size;
    private final GridLayout grid;

    private AflContainerSearchLayout(List<Section> sections) {
        if (sections.isEmpty() || sections.size() > 8) throw new IllegalArgumentException("sections: " + sections.size());
        this.sections = List.copyOf(sections);
        first = new int[sections.size()];
        int n = 0;
        List<GridLayout.Section> cells = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            first[i] = n;
            n += sections.get(i).size();
            cells.add(new GridLayout.Section(sections.get(i).w(), sections.get(i).h()));
        }
        size = n;
        grid = new GridLayout(cells);
    }

    public static AflContainerSearchLayout of(Section... sections) {
        return new AflContainerSearchLayout(List.of(sections));
    }

    /** One unnamed section (the container's title says what it is). */
    public static AflContainerSearchLayout single(int w, int h) {
        return of(new Section("", w, h));
    }

    /** {@code rows} rows of 8 in one unnamed section (a survivor's body, which grows by rows). */
    public static AflContainerSearchLayout rows(int rows) {
        return single(MAX_W, rows);
    }

    public List<Section> sections() { return sections; }

    public Section section(int index) { return sections.get(index); }

    /** Cells, the container's slot count. */
    public int size() { return size; }

    /** The same sections as player-grid sections, for GridStorage. */
    public GridLayout grid() { return grid; }

    /** The slot of a cell. */
    public int slot(int section, int x, int y) {
        return first[section] + y * sections.get(section).w() + x;
    }

    /** The section a slot's cell lies in. */
    public int sectionOf(int slot) {
        for (int i = sections.size() - 1; i >= 0; i--) if (slot >= first[i]) return i;
        return 0;
    }

    public int cellX(int slot) {
        int s = sectionOf(slot);
        return (slot - first[s]) % sections.get(s).w();
    }

    public int cellY(int slot) {
        int s = sectionOf(slot);
        return (slot - first[s]) / sections.get(s).w();
    }

    // ---- the vanilla-style Creative screen (AflContainerSearchScreen): sections one under another, 18 px cells

    /** Gap between sections on the Creative screen. */
    private static final int GAP = 4;

    public int slotX(int slot) {
        return 8 + cellX(slot) * 18;
    }

    public int slotY(int slot) {
        int s = sectionOf(slot), y = 18;
        for (int i = 0; i < s; i++) y += sections.get(i).h() * 18 + GAP;
        return y + cellY(slot) * 18;
    }

    /** Height of the container's cells on the Creative screen. */
    public int gridHeight() {
        int h = 0;
        for (Section s : sections) h += s.h() * 18;
        return h + (sections.size() - 1) * GAP;
    }

    /** The player inventory's top row on the Creative screen (the hotbar sits 58 px lower). */
    public int inventoryY() {
        return 18 + gridHeight() + 14;
    }

    // ---- open data

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(sections.size());
        for (Section s : sections) {
            buf.writeUtf(s.name(), 64);
            buf.writeByte(s.w());
            buf.writeByte(s.h());
        }
    }

    public static AflContainerSearchLayout read(FriendlyByteBuf buf) {
        int n = buf.readByte();
        List<Section> sections = new ArrayList<>(n);
        for (int i = 0; i < n; i++) sections.add(new Section(buf.readUtf(64), buf.readByte(), buf.readByte()));
        return new AflContainerSearchLayout(sections);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AflContainerSearchLayout other && sections.equals(other.sections);
    }

    @Override
    public int hashCode() {
        return sections.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (Section s : sections)
            out.append(out.isEmpty() ? "" : " + ").append(s.name().isEmpty() ? "" : s.name() + " ").append(s.w()).append('x').append(s.h());
        return out.toString();
    }
}
