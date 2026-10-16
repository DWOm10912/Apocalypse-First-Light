package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the road network JSON (RoadNetworkPlan.toJson, C3.1) and the built network JSON (RoadNetworkBuild, C3.3a) back
 * into runtime road designs (C3.2, C3.3a; docs/worldgen/federal_highways_c3.md, 10 and 11). The built JSON is the one
 * source the server's terrain works, the chunk pass and the client's mesh are all built from (the server derives it from
 * the plan, which it builds once per seed and caches on disk, and sends it to clients), so every side has bit-identical
 * designs: control polylines and the 2 m profile at the JSON's 2 decimals, each route's trim window. Pure Java with a
 * minimal reader for this format (no Gson: the offline checks run without Minecraft).
 */
public final class RoadNetworkJson {
    private RoadNetworkJson() {}

    /**
     * One route as stored: class id, corridor and end towns, control points, 2 m profile, structures; trim: the built
     * station window {from, to} of the full curve (null: the whole route); culverts: the box culverts its CULVERT
     * structures became (group D, RoadStructures; a CULVERT without one is a slab culvert span); wings: the wing walls'
     * reach per span, {s0, s1, s0 left, s0 right, s1 left, s1 right} in the JSON's stations (RoadStructures).
     */
    public record RouteData(String id, String roadClass, String corridor, String from, String to, double[] xs, double[] zs,
                            double[] profile, List<Structure> structures, double[] trim, List<Culvert> culverts, List<double[]> wings) {
        public RouteData(String id, String roadClass, String corridor, String from, String to, double[] xs, double[] zs,
                         double[] profile, List<Structure> structures, double[] trim) {
            this(id, roadClass, corridor, from, to, xs, zs, profile, structures, trim, List.of(), List.of());
        }

        public RouteData withTrim(double[] t) { return new RouteData(id, roadClass, corridor, from, to, xs, zs, profile, structures, t, culverts, wings); }

        public RouteData withStructures(List<Culvert> c, List<double[]> w) {
            return new RouteData(id, roadClass, corridor, from, to, xs, zs, profile, structures, trim, List.copyOf(c), List.copyOf(w));
        }

        /** The box culvert built for the CULVERT structure s0 .. s1 (JSON stations), or null. */
        public Culvert culvert(double s0, double s1) {
            for (Culvert c : culverts) if (c.s >= s0 - 1 && c.s <= s1 + 1) return c;
            return null;
        }
    }

    public record Structure(String kind, double s0, double s1, double height) {}

    /** A plan node: towns, interchanges, junctions, joins (id {@code <route>/join/start|end}), crossings, passes, bridges. */
    public record Node(String id, String kind, double x, double z) {}

    /**
     * Culverts (C3.3a) are built as short concrete slab culverts on the bridge machinery: the opening is the stream's
     * stretch plus CULVERT_PAD on each side (the walls' first metre), at least CULVERT_MIN long; the top slab and its
     * beam CULVERT_DEPTH deep.
     */
    public static final double CULVERT_PAD = 1.0, CULVERT_MIN = 8.0, CULVERT_DEPTH = 0.6;

    @SuppressWarnings("unchecked")
    public static List<Node> nodes(String json) {
        Map<String, Object> root = (Map<String, Object>) new Reader(json).value();
        List<Node> out = new ArrayList<>();
        Object ns = root.get("nodes");
        if (ns == null) return out;
        for (Object o : (List<Object>) ns) {
            Map<String, Object> n = (Map<String, Object>) o;
            out.add(new Node((String) n.get("id"), (String) n.get("kind"), (Double) n.get("x"), (Double) n.get("z")));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<RouteData> routes(String json) {
        Map<String, Object> root = (Map<String, Object>) new Reader(json).value();
        List<RouteData> out = new ArrayList<>();
        for (Object o : (List<Object>) root.get("routes")) {
            Map<String, Object> r = (Map<String, Object>) o;
            List<Object> control = (List<Object>) r.get("control");
            double[] xs = new double[control.size()], zs = new double[control.size()];
            for (int i = 0; i < xs.length; i++) {
                List<Object> p = (List<Object>) control.get(i);
                xs[i] = (Double) p.get(0);
                zs[i] = (Double) p.get(1);
            }
            List<Object> prof = (List<Object>) r.get("profile");
            double[] e = new double[prof.size()];
            for (int i = 0; i < e.length; i++) e[i] = (Double) prof.get(i);
            List<Structure> ss = new ArrayList<>();
            for (Object so : (List<Object>) r.get("structures")) {
                Map<String, Object> s = (Map<String, Object>) so;
                ss.add(new Structure((String) s.get("kind"), (Double) s.get("s0"), (Double) s.get("s1"), (Double) s.get("height")));
            }
            String corridor = (String) r.get("corridor"), from = (String) r.get("from"), to = (String) r.get("to");
            double[] trim = null;
            if (r.get("trim") instanceof List<?> t) trim = new double[]{(Double) t.get(0), (Double) t.get(1)};
            List<Culvert> cs = new ArrayList<>();
            if (r.get("culverts") instanceof List<?> l) for (Object c : l) cs.add(Culvert.of(asMap(c)));
            List<double[]> ws = new ArrayList<>();
            if (r.get("wings") instanceof List<?> l)
                for (Object w : l) {
                    List<Object> q = (List<Object>) w;
                    double[] v = new double[q.size()];
                    for (int i = 0; i < v.length; i++) v[i] = (Double) q.get(i);
                    ws.add(v);
                }
            out.add(new RouteData((String) r.get("id"), (String) r.get("class"), corridor, from, to, xs, zs, e, List.copyOf(ss), trim,
                    List.copyOf(cs), List.copyOf(ws)));
        }
        return out;
    }

    /** All routes of a built network JSON (RoadNetworkBuild) as designs, in the JSON's order. */
    public static List<RoadDesign> designs(String builtJson) {
        List<RoadDesign> out = new ArrayList<>();
        for (RouteData r : routes(builtJson)) out.add(design(r));
        return out;
    }

    /** Structure kinds the stage builds (Tunnel V1 adds TUNNEL). */
    public static final List<String> BUILT_KINDS = List.of("BRIDGE", "VIADUCT", "CULVERT", "DEEP_CUT", "TUNNEL");

    /** Why C3.3a cannot build a route (an unknown class, a structure kind it does not build), or null. */
    public static String buildable(RouteData r) {
        try {
            CrossSection.of(r.roadClass());
        } catch (IllegalArgumentException e) {
            return "class " + r.roadClass() + " is not built yet";
        }
        for (Structure s : r.structures()) if (!BUILT_KINDS.contains(s.kind())) return "structure " + s.kind() + " is not built yet";
        return null;
    }

    /** Deck depth (pavement to soffit) of a bridge of a class (RoadClassSpec.deckDepth), m. */
    public static double deckDepth(String roadClass) {
        for (RoadClassSpec k : RoadClassSpec.values()) if (k.id.equals(roadClass)) return k.deckDepth();
        throw new IllegalArgumentException("unknown road class " + roadClass);
    }

    /**
     * The design of a route: its class's cross-section; bridges and viaducts as deck spans of the class's depth, with
     * piers where a span is longer than BridgeMesh.MAX_SPAN, wing walls to the JSON's reach; culverts as box culverts
     * where the JSON has one for them (group D), else as slab culvert spans; deep cuts are earthworks; tunnels as
     * RoadDesign.Tunnel (Tunnel V1). A trim window renumbers the stations from its start and drops what lies outside.
     */
    public static RoadDesign design(RouteData r) { return design(r, true); }

    /**
     * The design of a route; without culvertSpans no CULVERT becomes a span (the box culvert designer, RoadStructures,
     * reads the embankment over every stream through it).
     */
    public static RoadDesign design(RouteData r, boolean culvertSpans) {
        double a = 0, b = Double.POSITIVE_INFINITY;
        if (r.trim() != null) { a = r.trim()[0]; b = r.trim()[1]; }
        RoadCurve curve = new RoadCurve(r.xs(), r.zs(), a, b);
        double[] e = r.profile();
        int i0 = (int) Math.round(a / RoadProfile.DS), i1 = Math.min(e.length - 1, (int) Math.round(Math.min(b, (e.length - 1) * RoadProfile.DS) / RoadProfile.DS));
        if (Math.abs(i0 * RoadProfile.DS - a) > 1e-6) throw new IllegalArgumentException("trim off the 2 m lattice on " + r.id());
        RoadProfile profile = RoadProfile.of(java.util.Arrays.copyOfRange(e, i0, i1 + 1));
        double deck = deckDepth(r.roadClass());
        List<double[]> raw = new ArrayList<>();
        List<RoadDesign.Tunnel> tunnels = new ArrayList<>();
        for (Structure s : r.structures()) {
            double s0 = s.s0() - a, s1 = s.s1() - a;
            switch (s.kind()) {
                case "BRIDGE", "VIADUCT" -> {
                    if (s1 < 0 || s0 > profile.length) continue;
                    raw.add(new double[]{s0, s1, deck});
                }
                case "CULVERT" -> {
                    if (s1 < 0 || s0 > profile.length || !culvertSpans || r.culvert(s.s0(), s.s1()) != null) continue;
                    double pad = Math.max(CULVERT_PAD, (CULVERT_MIN - (s1 - s0)) / 2);
                    raw.add(new double[]{s0 - pad, s1 + pad, CULVERT_DEPTH});
                }
                case "DEEP_CUT" -> { }
                case "TUNNEL" -> {
                    if (s1 < 0 || s0 > profile.length) continue;
                    tunnels.add(new RoadDesign.Tunnel(Math.max(0, s0), Math.min(profile.length, s1)));
                }
                default -> throw new IllegalArgumentException("structure " + s.kind() + " on " + r.id() + " is not built yet");
            }
        }
        List<RoadDesign.Span> spans = new ArrayList<>();
        for (double[] q : join(raw, deck)) {
            // a deck longer than BridgeMesh.MAX_SPAN stands on equally spaced piers; a culvert slab has none
            double s0 = q[0], s1 = q[1], len = s1 - s0;
            int n = q[2] == CULVERT_DEPTH ? 1 : (int) Math.ceil(len / BridgeMesh.MAX_SPAN - 1e-9);
            double[] piers = new double[Math.max(0, n - 1)];
            for (int k = 1; k < n; k++) piers[k - 1] = s0 + len * k / n;
            double[] reach = null;
            for (double[] w : r.wings())
                if (Math.abs(w[0] - a - s0) < 0.02 && Math.abs(w[1] - a - s1) < 0.02) reach = new double[]{w[2], w[3], w[4], w[5]};
            spans.add(new RoadDesign.Span(s0, s1, q[2], piers, reach));
        }
        tunnels.sort((p, q) -> Double.compare(p.s0(), q.s0()));
        return new RoadDesign(r.id(), CrossSection.of(r.roadClass()), curve, profile, spans, r.culverts(), tunnels);
    }

    /** Spans closer than this (or overlapping: a culvert's pad over a bridge's end) become one span, m. */
    public static final double SPAN_JOIN = 6;

    /**
     * Join spans {s0, s1, depth} that overlap or lie within SPAN_JOIN of each other: one deck over both, the deeper
     * depth; a joined span longer than two culverts takes the bridge deck (deck) (2026-10-15: on other seeds culverts
     * sit beside bridges and in chains, and overlapping spans would build two decks over one station).
     */
    static List<double[]> join(List<double[]> in, double deck) {
        List<double[]> sorted = new ArrayList<>(in);
        sorted.sort((p, q) -> Double.compare(p[0], q[0]));
        List<double[]> out = new ArrayList<>();
        for (double[] q : sorted) {
            double[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && q[0] - last[1] < SPAN_JOIN) {
                last[1] = Math.max(last[1], q[1]);
                last[2] = Math.max(last[2], q[2]);
                if (last[3] < 0) last[3] = 1;   // joined
            } else out.add(new double[]{q[0], q[1], q[2], -1});
        }
        for (double[] q : out) if (q[3] > 0 && q[1] - q[0] > 2 * CULVERT_MIN) q[2] = Math.max(q[2], deck);
        return out;
    }

    /** The built network JSON of these routes (RoadNetworkBuild): numbers at 2 decimals, trim windows included. */
    public static String write(String version, String stage, List<RouteData> routes) {
        StringBuilder b = new StringBuilder(1 << 20);
        b.append("{\"version\":\"").append(version).append("\",\"stage\":\"").append(stage).append("\",\"routes\":[");
        for (int i = 0; i < routes.size(); i++) {
            RouteData r = routes.get(i);
            if (i > 0) b.append(',');
            b.append("{\"id\":\"").append(r.id()).append("\",\"corridor\":\"").append(r.corridor()).append("\",\"class\":\"").append(r.roadClass())
                    .append("\",\"from\":\"").append(r.from()).append("\",\"to\":\"").append(r.to()).append("\",\"control\":[");
            for (int q = 0; q < r.xs().length; q++) b.append(q > 0 ? "," : "").append('[').append(fmt(r.xs()[q])).append(',').append(fmt(r.zs()[q])).append(']');
            b.append("],\"profile\":[");
            for (int q = 0; q < r.profile().length; q++) b.append(q > 0 ? "," : "").append(fmt(r.profile()[q]));
            b.append("],\"structures\":[");
            for (int q = 0; q < r.structures().size(); q++) {
                Structure s = r.structures().get(q);
                b.append(q > 0 ? "," : "").append("{\"kind\":\"").append(s.kind()).append("\",\"s0\":").append(fmt(s.s0())).append(",\"s1\":")
                        .append(fmt(s.s1())).append(",\"height\":").append(fmt(s.height())).append('}');
            }
            b.append(']');
            if (r.trim() != null) b.append(",\"trim\":[").append(fmt(r.trim()[0])).append(',').append(fmt(r.trim()[1])).append(']');
            if (!r.culverts().isEmpty()) {
                b.append(",\"culverts\":[");
                for (int q = 0; q < r.culverts().size(); q++) b.append(q > 0 ? "," : "").append(r.culverts().get(q).toJson());
                b.append(']');
            }
            if (!r.wings().isEmpty()) {
                b.append(",\"wings\":[");
                for (int q = 0; q < r.wings().size(); q++) {
                    double[] w = r.wings().get(q);
                    b.append(q > 0 ? "," : "").append('[');
                    for (int k = 0; k < w.length; k++) b.append(k > 0 ? "," : "").append(fmt(w[k]));
                    b.append(']');
                }
                b.append(']');
            }
            b.append('}');
        }
        return b.append("]}").toString();
    }

    /** Two decimals, the plan's number format (RoadNetworkPlan.fmt). */
    public static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    /** v rounded to the JSON's 2 decimals (what a reader of fmt(v) gets back). */
    public static double round2(double v) { return Double.parseDouble(fmt(v)); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }

    /** Minimal JSON reader: objects (insertion order), arrays, strings, numbers (as Double), true / false / null. */
    static final class Reader {
        private final String s;
        private int i;

        Reader(String s) { this.s = s; }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    expect(':');
                    m.put(k, value());
                    ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect('}');
                    return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect(']');
                    return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> b.append(e);
                    }
                } else b.append(c);
            }
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalArgumentException("JSON: expected " + c + " at " + i);
            i++;
        }
    }
}
