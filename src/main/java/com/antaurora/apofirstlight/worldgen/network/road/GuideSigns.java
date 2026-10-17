package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The interchange guide signs (C3.3b group 2 package B, 2026-10-17; docs/worldgen/federal_interchanges_c3_3b.md 10):
 * green panels with a white inset border and a white legend, the route shields of the user's shield A, on two posts;
 * placed by RoadSigns#guides as the fixture road_guide_sign (block/TunnelFixtureBlock), the legend in its bone:
 * <pre>type|exit|shield|cardinal|destination/destination|extra|foot,foot</pre>
 * <ul>
 *   <li>adv: the advance sign (1 MILE or 1/2 MILE before the exit; extra 1 or h), with the EXIT tab;</li>
 *   <li>dir: the exit direction sign at the taper (extra the arrow, ur or ul), with the EXIT tab;</li>
 *   <li>gore: EXIT and its number over the arrow, in the gore past the physical nose;</li>
 *   <li>ent: the entrance guide on the crossroad before an entrance ramp (the interstate's shield, its direction and
 *   control city, the arrow of the turn).</li>
 * </ul>
 * shield: a GuideSignAtlas tile (F40, R61; empty: none), cardinal N, S, E, W or empty, the destinations the towns'
 * English names (RoadNames), the feet the posts' foot heights (the left one's, the right one's, as the traffic sees
 * them) over the part's origin. The panel is as wide as its longest line needs; every line is a row of the sheet's
 * character cells (as wide as their advances, as high as a line) laid edge to edge, so no cell ever covers another or
 * the border. Frame: x across (the viewer's right), y up from the origin, z out of the face (the face at z 0).
 * Shared by the server (the boxes, TunnelFixtures) and the client (the drawn sign, client/road/TunnelParts).
 */
public final class GuideSigns {
    private GuideSigns() {}

    public enum Type {
        ADVANCE("adv"), DIRECTION("dir"), GORE("gore"), ENTRANCE("ent");

        public final String key;

        Type(String key) { this.key = key; }

        static Type of(String key) {
            for (Type t : values()) if (t.key.equals(key)) return t;
            return ENTRANCE;
        }
    }

    /** Cap heights: destinations and directions (about 14 in), distances, exit numbers and the tab's EXIT, m. */
    public static final double CAP = 0.36, CAP_SMALL = 0.30;
    /** Legend margins (side, top, bottom: a descender's cell clears the border), between rows, between a shield and its word, m. */
    public static final double MARGIN_X = 0.3, MARGIN_TOP = 0.22, MARGIN_BOTTOM = 0.25, ROW_GAP = 0.2, SHIELD_GAP = 0.15;
    /** Least panel width (guide signs, the gore sign), the arrow's size, the tab's padding beside its text and its height, m. */
    public static final double MIN_WIDTH = 2.4, GORE_MIN = 1.4, ARROW = 0.6, TAB_PAD = 0.22, TAB_HEIGHT = 0.7;
    /** The panels' corner radius, the white border's inset from the edge and its width, m. */
    public static final double RADIUS = 0.15, BORDER_INSET = 0.035, BORDER = 0.05;
    /** The panel's bottom over the higher post foot: on the interstate (7 ft), on the crossroad (5 ft), m. */
    public static final double BOTTOM = 2.1, BOTTOM_LOW = 1.5;
    /** Panel thickness behind its face; the square posts' side and their front and back (behind the panel), m. */
    public static final double PANEL = 0.03, POST = 0.12, POST_FRONT = -0.07, POST_BACK = -0.19;
    /** The legend printed over the face (the client's overlay; its cells cover the panel's green with their own), m. */
    public static final double FACE = 0.004;
    /** The tab's corner radius; how far under the panel's top the posts end and how deep they go under their feet, m. */
    public static final double TAB_RADIUS = 0.1, POST_TOP = 0.05, POST_SINK = 0.3;
    /** The two stringers behind the panel (between it and the posts): height, over the panel's bottom, under its top, m. */
    public static final double STRINGER = 0.08, STRINGER_LOW = 0.2, STRINGER_HIGH = 0.25;
    /** A legend without a bone (an item from the creative tab). */
    public static final String DEFAULT = "ent||F40|E|Bellhaven|ur|0,0";

    /** A sign's legend and feet. */
    public record Spec(Type type, String exit, String shield, String cardinal, List<String> dests, String extra, double[] feet) {
        public String bone() {
            return String.join("|", type.key, exit, shield, cardinal, String.join("/", dests), extra,
                    String.format(Locale.ROOT, "%.2f,%.2f", feet[0], feet[1]));
        }

        public static Spec parse(String bone) {
            String[] f = (bone == null ? DEFAULT : bone).split("\\|", -1);
            String at = f.length > 0 ? f[0] : "";
            List<String> dests = new ArrayList<>();
            if (f.length > 4) for (String d : f[4].split("/")) if (!d.isBlank()) dests.add(d.trim());
            double[] feet = {0, 0};
            if (f.length > 6) {
                String[] p = f[6].split(",");
                for (int i = 0; i < Math.min(2, p.length); i++) {
                    try {
                        feet[i] = Math.max(-6, Math.min(6, Double.parseDouble(p[i])));
                    } catch (NumberFormatException e) {
                        feet[i] = 0;
                    }
                }
            }
            return new Spec(Type.of(at), f.length > 1 ? f[1] : "", f.length > 2 ? f[2] : "", f.length > 3 ? f[3] : "", dests,
                    f.length > 5 ? f[5] : "", feet);
        }

        /** The same legend standing level (an item taken down: placed again on any floor). */
        public Spec level() { return new Spec(type, exit, shield, cardinal, dests, extra, new double[]{0, 0}); }
    }

    /** One piece of the legend on the face: a character's cell (tile null) or a tile, its rect, m. */
    public record Piece(char ch, String tile, double x0, double y0, double x1, double y1) {}

    /** A laid out sign: its panel and tab {x0, y0, x1, y1} (tab null: none), the posts' x and feet, the legend. */
    public record Layout(double[] panel, double[] tab, double[] posts, double[] feet, List<Piece> pieces) {}

    /** A row's run: text at a cap height, or a tile of a size. */
    private record Run(String text, double cap, String tile, double w, double h) {
        static Run text(String s, double cap) { return new Run(s, cap, null, width(s, cap), cap); }

        static Run tile(String name, double w, double h) { return new Run(null, 0, name, w, h); }
    }

    private record Row(List<Run> runs, boolean right) {
        double width() {
            double w = 0;
            for (Run r : runs) w += r.w;
            return w + SHIELD_GAP * Math.max(0, runs.size() - 1);
        }

        double height() {
            double h = 0;
            for (Run r : runs) h = Math.max(h, r.h);
            return h;
        }
    }

    /** A text's width at cap height cap (characters without a cell take no room). */
    public static double width(String s, double cap) {
        double w = 0;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == ' ') { w += GuideSignAtlas.SPACE * cap; continue; }
            int i = GuideSignAtlas.index(c);
            if (i >= 0) w += GuideSignAtlas.advance(i) * cap;
        }
        return w;
    }

    /** A cardinal letter's word (empty: none). */
    public static String cardinal(String c) {
        return switch (c) {
            case "N" -> "NORTH";
            case "S" -> "SOUTH";
            case "E" -> "EAST";
            case "W" -> "WEST";
            default -> "";
        };
    }

    private static String distance(String extra) { return "h".equals(extra) ? "½ MILE" : "1 MILE"; }

    private static String arrow(String extra) { return "ul".equals(extra) ? "arrow_ul" : "arrow_ur"; }

    /** The shield's tile and the direction word as one row's runs (none where both are empty). */
    private static List<Run> routeRuns(Spec s) {
        List<Run> runs = new ArrayList<>();
        int t = GuideSignAtlas.tile(s.shield);
        if (t >= 0) {
            double[] size = GuideSignAtlas.tileSize(t);
            runs.add(Run.tile(s.shield, size[0], size[1]));
        } else if (!s.shield.isEmpty()) {
            // a route without a drawn shield: its number as text
            RoadNames.Route r = RoadNames.Route.of(s.shield);
            if (r != null) runs.add(Run.text(r.label(), CAP));
        }
        String word = cardinal(s.cardinal);
        if (!word.isEmpty()) runs.add(Run.text(word, CAP));
        return runs;
    }

    public static Layout layout(String bone) { return layout(Spec.parse(bone)); }

    public static Layout layout(Spec s) {
        List<Row> rows = new ArrayList<>();
        String tab = null;
        switch (s.type) {
            case ADVANCE, DIRECTION -> {
                if (!s.exit.isEmpty()) tab = "EXIT " + s.exit;
                List<Run> route = routeRuns(s);
                if (!route.isEmpty()) rows.add(new Row(route, false));
                for (String d : s.dests) rows.add(new Row(List.of(Run.text(d, CAP)), false));
                rows.add(s.type == Type.ADVANCE ? new Row(List.of(Run.text(distance(s.extra), CAP_SMALL)), true)
                        : new Row(List.of(Run.tile(arrow(s.extra), ARROW, ARROW)), true));
            }
            case GORE -> {
                rows.add(new Row(List.of(Run.text(s.exit.isEmpty() ? "EXIT" : "EXIT " + s.exit, CAP_SMALL)), false));
                rows.add(new Row(List.of(Run.tile(arrow(s.extra), ARROW, ARROW)), false));
            }
            case ENTRANCE -> {
                List<Run> route = routeRuns(s);
                if (!route.isEmpty()) rows.add(new Row(route, false));
                for (String d : s.dests) rows.add(new Row(List.of(Run.text(d, CAP)), false));
                rows.add(new Row(List.of(Run.tile(arrow(s.extra), ARROW, ARROW)), false));
            }
        }
        double inner = 0, h = MARGIN_TOP + MARGIN_BOTTOM + ROW_GAP * (rows.size() - 1);
        for (Row r : rows) {
            inner = Math.max(inner, r.width());
            h += r.height();
        }
        double tabW = tab == null ? 0 : width(tab, CAP_SMALL) + 2 * TAB_PAD;
        double w = Math.max(Math.max(s.type == Type.GORE ? GORE_MIN : MIN_WIDTH, inner + 2 * MARGIN_X), tabW);
        double y0 = (s.type == Type.ENTRANCE ? BOTTOM_LOW : BOTTOM) + Math.max(s.feet[0], s.feet[1]), y1 = y0 + h;
        List<Piece> pieces = new ArrayList<>();
        double y = y1 - MARGIN_TOP;
        for (Row r : rows) {
            double rh = r.height(), mid = y - rh / 2, rw = r.width();
            double x = r.right ? w / 2 - MARGIN_X - rw : -rw / 2;
            for (Run run : r.runs) {
                if (run.tile != null) pieces.add(new Piece('\0', run.tile, x, mid - run.h / 2, x + run.w, mid + run.h / 2));
                else set(pieces, run.text, run.cap, x, mid - run.cap / 2);
                x += run.w + SHIELD_GAP;
            }
            y -= rh + ROW_GAP;
        }
        double[] tabRect = null;
        if (tab != null) {
            // over the panel's right end; as wide as the panel where too little of its top would be left beside it
            double tx0 = w / 2 - tabW < -w / 2 + RADIUS + 0.05 ? -w / 2 : w / 2 - tabW;
            tabRect = new double[]{tx0, y1, w / 2, y1 + TAB_HEIGHT};
            set(pieces, tab, CAP_SMALL, (tx0 + w / 2) / 2 - width(tab, CAP_SMALL) / 2, y1 + TAB_HEIGHT / 2 - CAP_SMALL / 2);
        }
        double inset = Math.max(0.3, Math.min(0.9, 0.2 * w));
        return new Layout(new double[]{-w / 2, y0, w / 2, y1}, tabRect, new double[]{-w / 2 + inset, w / 2 - inset}, s.feet.clone(), pieces);
    }

    /** A text's cells from (x, baseline). */
    private static void set(List<Piece> out, String text, double cap, double x, double baseline) {
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == ' ') { x += GuideSignAtlas.SPACE * cap; continue; }
            int i = GuideSignAtlas.index(c);
            if (i < 0) continue;
            double a = GuideSignAtlas.advance(i) * cap;
            out.add(new Piece(c, null, x, baseline + GuideSignAtlas.CELL_BOTTOM * cap, x + a, baseline + GuideSignAtlas.CELL_TOP * cap));
            x += a;
        }
    }

    /**
     * The boxes (TunnelFixtures#boxes; the panel first: the master cell): the panel and the tab, each 0.24 m through
     * (TunnelFixtures.MIN, the face 0.1 m before them), the posts from their feet up to the panel.
     */
    public static double[][] boxes(String bone) {
        Layout l = layout(bone);
        List<double[]> out = new ArrayList<>();
        double[] p = l.panel();
        out.add(new double[]{p[0], p[1], -0.14, p[2], p[3], 0.10});
        if (l.tab() != null) out.add(new double[]{l.tab()[0], l.tab()[1], -0.14, l.tab()[2], l.tab()[3], 0.10});
        for (int i = 0; i < 2; i++) {
            double x = l.posts()[i];
            out.add(new double[]{x - 0.12, Math.min(l.feet()[i], p[1] - 0.1), -0.25, x + 0.12, p[1], -0.01});
        }
        return out.toArray(new double[0][]);
    }

    /** The drawn solids as boxes (the hit mesh, meshhit/TunnelFixtureHits): the panel and tab with the legend, the posts, the stringers. */
    public static double[][] drawn(String bone) {
        Layout l = layout(bone);
        List<double[]> out = new ArrayList<>();
        double[] p = l.panel();
        out.add(new double[]{p[0], p[1], -PANEL, p[2], p[3], FACE});
        if (l.tab() != null) out.add(new double[]{l.tab()[0], l.tab()[1], -PANEL, l.tab()[2], l.tab()[3], FACE});
        for (int i = 0; i < 2; i++) {
            double x = l.posts()[i];
            out.add(new double[]{x - POST / 2, Math.min(l.feet()[i], p[1]), POST_BACK, x + POST / 2, p[3] - POST_TOP, POST_FRONT});
        }
        for (double y : stringers(p)) out.add(new double[]{p[0] + 0.1, y, POST_FRONT, p[2] - 0.1, y + STRINGER, -PANEL});
        return out.toArray(new double[0][]);
    }

    /** The stringers' bottoms behind a panel {x0, y0, x1, y1}. */
    public static double[] stringers(double[] panel) {
        return new double[]{panel[1] + STRINGER_LOW, panel[3] - STRINGER_HIGH - STRINGER};
    }

    /** The legend in one line (Jade, the item's tooltip): EXIT 4 | RT 61 SOUTH | Redfield | 1 MILE. */
    public static String describe(String bone) {
        Spec s = Spec.parse(bone);
        List<String> parts = new ArrayList<>();
        if (!s.exit.isEmpty()) parts.add("EXIT " + s.exit);
        RoadNames.Route r = RoadNames.Route.of(s.shield);
        String word = cardinal(s.cardinal);
        if (r != null) parts.add(word.isEmpty() ? r.label() : r.label() + " " + word);
        else if (!word.isEmpty()) parts.add(word);
        parts.addAll(s.dests);
        if (s.type == Type.ADVANCE) parts.add("h".equals(s.extra) ? "1/2 MILE" : "1 MILE");
        return String.join(" | ", parts);
    }

    /** The bone an item taken down keeps: the legend, standing level. */
    public static String itemBone(String bone) { return Spec.parse(bone).level().bone(); }
}
