package com.antaurora.apofirstlight.tree;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated server tree crowns (2026-10-22): a layout built with its quads dropped (TreeShape#keepQuads false, as on a
 * dedicated server) must say which cells carry leaves exactly as one built with them, every species, variant and turn
 * (the turned layouts are rotated from the stripped base). Before the fix every crown cell there said none, so the
 * server placed canopy blocks without leaves. Prints the counts; exits 1 on a mismatch.
 */
public final class TreeStripCheck {
    public static void main(String[] args) throws Exception {
        Map<String, Boolean> kept = carries(true);
        Map<String, Boolean> stripped = carries(false);
        int cells = 0, carry = 0, wrong = 0;
        for (var e : kept.entrySet()) {
            cells++;
            if (e.getValue()) carry++;
            if (!e.getValue().equals(stripped.get(e.getKey()))) wrong++;
        }
        boolean ok = wrong == 0 && kept.size() == stripped.size() && carry > 0;
        System.out.printf("tree strip: %d cells over %d layouts, %d carry leaves; stripped differs in %d, sizes %d / %d: %s%n", cells,
                TreeForms.Species.values().length * TreeShape.VARIANTS * 4, carry, wrong, kept.size(), stripped.size(), ok ? "PASS" : "FAIL");
        if (!ok) System.exit(1);
    }

    private static Map<String, Boolean> carries(boolean keep) throws Exception {
        Field cache = TreeShape.class.getDeclaredField("LAYOUTS");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).clear();
        TreeShape.keepQuads = keep;
        Map<String, Boolean> out = new HashMap<>();
        for (var s : TreeForms.Species.values())
            for (int v = 0; v < TreeShape.VARIANTS; v++)
                for (int t = 0; t < 4; t++) {
                    var l = TreeShape.layout(s, v, t);
                    for (var c : l.cells.values()) out.put(s + "/" + v + "/" + t + "/" + c.x + "," + c.y + "," + c.z, c.carries());
                }
        return out;
    }
}
