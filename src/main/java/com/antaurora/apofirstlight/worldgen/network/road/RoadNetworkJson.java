package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the road network JSON (RoadNetworkPlan.toJson, C3.1) back into runtime road designs (C3.2,
 * docs/worldgen/federal_highways_c3.md, 10). The JSON is the one source the server's terrain works, the chunk pass and
 * the client's mesh are all built from (the server builds it once per seed and plan, caches it on disk and sends the
 * built routes to clients), so every side has bit-identical designs: control polylines and the 2 m profile at the JSON's
 * 2 decimals. Pure Java with a minimal reader for this format (no Gson: the offline checks run without Minecraft).
 */
public final class RoadNetworkJson {
    private RoadNetworkJson() {}

    /** One route as stored: class id, control points, 2 m profile, structures. */
    public record RouteData(String id, String roadClass, double[] xs, double[] zs, double[] profile, List<Structure> structures) {}

    public record Structure(String kind, double s0, double s1, double height) {}

    /** Deck depth (pavement to soffit) of an interstate bridge, m: RoadClassSpec.INTERSTATE.deckDepth(). */
    public static final double INTERSTATE_DECK = 1.8;

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
            out.add(new RouteData((String) r.get("id"), (String) r.get("class"), xs, zs, e, List.copyOf(ss)));
        }
        return out;
    }

    /**
     * The built routes' designs, in the JSON's order. Only interstates are built in C3.2 (the R4 cross-section); a listed
     * id of another class is an error. Bridges and viaducts become deck spans; culverts and tunnels are refused (C3.3).
     */
    public static List<RoadDesign> designs(String json, Set<String> built) {
        List<RoadDesign> out = new ArrayList<>();
        for (RouteData r : routes(json)) {
            if (!built.contains(r.id())) continue;
            if (!r.roadClass().equals("interstate")) throw new IllegalArgumentException("C3.2 builds interstates only: " + r.id());
            out.add(design(r));
        }
        if (out.size() != built.size()) throw new IllegalArgumentException("built routes missing from the network: " + built);
        return out;
    }

    public static RoadDesign design(RouteData r) {
        RoadCurve curve = new RoadCurve(r.xs(), r.zs());
        RoadProfile profile = RoadProfile.of(r.profile());
        List<RoadDesign.Span> spans = new ArrayList<>();
        for (Structure s : r.structures()) {
            switch (s.kind()) {
                case "BRIDGE", "VIADUCT" -> spans.add(new RoadDesign.Span(s.s0(), s.s1(), INTERSTATE_DECK));
                case "DEEP_CUT" -> { }
                default -> throw new IllegalArgumentException("structure " + s.kind() + " on " + r.id() + " is C3.3 work");
            }
        }
        return new RoadDesign(r.id(), curve, profile, spans);
    }

    /** The JSON of only the listed routes (no nodes): what the server sends to clients. */
    public static String subset(String json, Set<String> ids) {
        Map<String, Object> root = asMap(new Reader(json).value());
        StringBuilder b = new StringBuilder();
        b.append("{\"version\":\"").append(root.get("version")).append("\",\"routes\":[");
        boolean first = true;
        // re-emit each kept route verbatim (its source text), so numbers are never re-rounded
        for (String raw : rawRoutes(json)) {
            String id = asMap(new Reader(raw).value()).get("id").toString();
            if (!ids.contains(id)) continue;
            if (!first) b.append(',');
            b.append(raw);
            first = false;
        }
        return b.append("]}").toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }

    /** The source text of each element of the top-level "routes" array. */
    static List<String> rawRoutes(String json) {
        int i = json.indexOf("\"routes\":[");
        if (i < 0) throw new IllegalArgumentException("no routes");
        i += "\"routes\":[".length();
        List<String> out = new ArrayList<>();
        while (json.charAt(i) != ']') {
            if (json.charAt(i) == ',') i++;
            int start = i, depth = 0;
            boolean str = false;
            for (; ; i++) {
                char c = json.charAt(i);
                if (str) { if (c == '\\') i++; else if (c == '"') str = false; continue; }
                if (c == '"') str = true;
                else if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') { depth--; if (depth == 0) { i++; break; } }
            }
            out.add(json.substring(start, i));
        }
        return out;
    }

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
