package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.dev.highwaymesh.RoadAlignment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Everything that defines one R4 test segment, small enough to send to clients (they rebuild the same mesh from it) and to
 * keep in the saved data: start, heading (degrees, 0 = +X east, 90 = +Z south), length, the bend at the midpoint (degrees,
 * positive turns right), the leg length of the polyline, and the designed profile. The version is the SHA-256 of the JSON.
 */
public record R4Recipe(int schema, double x0, double z0, double heading, double length, double bend, double leg, double[] profile) {
    public static final int SCHEMA = 1;

    public R4Recipe {
        if (schema != SCHEMA) throw new IllegalArgumentException("R4_RECIPE_SCHEMA");
        if (!(length >= 128 && length <= 512) || Math.abs(length / R4Profile.DS - Math.rint(length / R4Profile.DS)) > 1e-9)
            throw new IllegalArgumentException("R4_LENGTH 128..512, multiple of " + R4Profile.DS);
        if (!(Math.abs(bend) <= 15)) throw new IllegalArgumentException("R4_BEND |bend| <= 15 degrees");
        if (!(leg >= length / 2) || !Double.isFinite(x0) || !Double.isFinite(z0) || !Double.isFinite(heading)) throw new IllegalArgumentException("R4_RECIPE");
        profile = profile == null ? null : profile.clone();
    }

    /** The polyline legs are a little longer than half the segment: the corner transition shortens the arc. */
    public static double defaultLeg(double length) { return length / 2 + 24; }

    public List<RoadAlignment.Point> controls() {
        double h0 = Math.toRadians(heading), h1 = Math.toRadians(heading + bend);
        var p0 = new RoadAlignment.Point(x0, z0);
        var p1 = new RoadAlignment.Point(x0 + Math.cos(h0) * leg, z0 + Math.sin(h0) * leg);
        if (Math.abs(bend) < 1e-9) return List.of(p0, new RoadAlignment.Point(x0 + Math.cos(h0) * 2 * leg, z0 + Math.sin(h0) * 2 * leg));
        var p2 = new RoadAlignment.Point(p1.x() + Math.cos(h1) * leg, p1.z() + Math.sin(h1) * leg);
        return List.of(p0, p1, p2);
    }

    public RoadAlignment alignment() {
        RoadAlignment a;
        try {
            a = R4Design.alignment(controls());
        } catch (IllegalArgumentException e) {
            // the corner transition takes 0.4 of a leg: about 8.8 degrees at 128 m, 15 at 256 m keep the 300 m radius
            if (String.valueOf(e.getMessage()).startsWith("RESTRICTED: radius"))
                throw new IllegalArgumentException("R4_BEND_TOO_SHARP for " + Math.round(length) + " m (" + e.getMessage() + "); use a smaller bend or a longer segment");
            throw e;
        }
        if (a.length < length) throw new IllegalArgumentException("R4_ALIGNMENT_SHORTER_THAN_SEGMENT " + a.length);
        return a;
    }

    public R4Design design() {
        if (profile == null) throw new IllegalStateException("R4_RECIPE_WITHOUT_PROFILE");
        return new R4Design(alignment(), R4Profile.of(profile, length));
    }

    public R4Recipe withProfile(double[] e) { return new R4Recipe(schema, x0, z0, heading, length, bend, leg, e); }

    public String json() {
        var j = new JsonObject();
        j.addProperty("schema", schema);
        j.addProperty("x0", x0);
        j.addProperty("z0", z0);
        j.addProperty("heading", heading);
        j.addProperty("length", length);
        j.addProperty("bend", bend);
        j.addProperty("leg", leg);
        var p = new JsonArray();
        // millimetre precision: the recipe the client rebuilds is exactly the one the server saved
        if (profile != null) for (double v : profile) p.add(Math.rint(v * 1000) / 1000);
        j.add("profile", p);
        return j.toString();
    }

    public static R4Recipe decode(String json) {
        var j = JsonParser.parseString(json).getAsJsonObject();
        var p = j.getAsJsonArray("profile");
        double[] e = new double[p.size()];
        for (int i = 0; i < e.length; i++) e[i] = p.get(i).getAsDouble();
        return new R4Recipe(j.get("schema").getAsInt(), j.get("x0").getAsDouble(), j.get("z0").getAsDouble(), j.get("heading").getAsDouble(),
                j.get("length").getAsDouble(), j.get("bend").getAsDouble(), j.get("leg").getAsDouble(), e.length == 0 ? null : e);
    }

    /** The canonical form (rounded profile) and its digest. */
    public R4Recipe canonical() { return decode(json()); }

    public String version() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String describe() {
        return String.format(Locale.ROOT, "start (%.1f, %.1f) heading %.1f deg, %.0f m, bend %.1f deg", x0, z0, heading, length, bend);
    }
}
