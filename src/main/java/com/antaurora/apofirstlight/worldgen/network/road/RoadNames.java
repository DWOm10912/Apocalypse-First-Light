package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.Map;

/**
 * What the federal network's signs call its roads and towns (C3.3b group 2 package B; the naming the user took on
 * 2026-10-17, docs/worldgen/federal_interchanges_c3_3b.md 10): the interstates are FEDERAL routes (F-40 east-west, F-65
 * north-south), the state highways national ROUTE numbers kept across states; the towns their English names. Signs in
 * the world are English in every game language (user 2026-10-17); the Chinese names wait for the story's localisation.
 * A corridor or town missing here has no shield or no name on a sign.
 */
public final class RoadNames {
    private RoadNames() {}

    /** A route's sign: its network (F: a FEDERAL interstate, R: a ROUTE) and number; even numbers run east-west. */
    public record Route(char net, int number) {
        /** Its shield's tile on the guide sign sheet (GuideSignAtlas). */
        public String tile() { return "" + net + number; }

        public boolean eastWest() { return number % 2 == 0; }

        /** As written in text: F-40, RT 61. */
        public String label() { return (net == 'F' ? "F-" : "RT ") + number; }

        /** A tile name back to its route (null: not one). */
        public static Route of(String tile) {
            if (tile == null || tile.length() < 2 || (tile.charAt(0) != 'F' && tile.charAt(0) != 'R')) return null;
            try {
                return new Route(tile.charAt(0), Integer.parseInt(tile.substring(1)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** The routes by corridor (one continuous line one number; RT 61 and RT 81 each join two corridors). */
    static final Map<String, Route> ROUTES = Map.of(
            "interstate_ew", new Route('F', 40), "interstate_ns", new Route('F', 65),
            "sh_lakes", new Route('R', 10), "sh_mountain", new Route('R', 30), "sh_hills", new Route('R', 31),
            "sh_farm_north", new Route('R', 61), "sh_farm_belt", new Route('R', 61), "sh_gulf", new Route('R', 80),
            "sh_range_front", new Route('R', 81), "sh_canyon", new Route('R', 81));

    /** The towns' English names (their Chinese names in the docs' table, for the story's localisation). */
    static final Map<String, String> TOWNS = Map.ofEntries(
            Map.entry("river_city", "Bellhaven"), Map.entry("valley_town", "Linden"), Map.entry("hill_village", "Laurel Ridge"),
            Map.entry("county_seat", "Sutton"), Map.entry("farm_town_north", "Elmwood"), Map.entry("farm_town_south", "Redfield"),
            Map.entry("mining_town", "Cobalt"), Map.entry("pass_village", "Tamarack"), Map.entry("industrial_city", "Kessler"),
            Map.entry("port_town", "Gull Harbor"), Map.entry("mill_town", "Birch Mills"), Map.entry("port_city", "Port Marais"),
            Map.entry("bayou_village", "Bayou Noir"), Map.entry("shore_town", "Pelican Point"), Map.entry("desert_town", "Mesa Roja"),
            Map.entry("canyon_outpost", "Fort Ocotillo"));

    /**
     * A road id's corridor: the text before its first ':' (a crossroad merged of two roads: its first's), of a merged
     * corridor (a+b:...) its first; null: none.
     */
    public static String corridor(String roadId) {
        if (roadId == null) return null;
        int c = roadId.indexOf(':');
        if (c <= 0) return null;
        int p = roadId.indexOf('+');
        return p > 0 && p < c ? roadId.substring(0, p) : roadId.substring(0, c);
    }

    /** The route a road belongs to (by its corridor), or null (a ramp, an unnamed corridor). */
    public static Route route(String roadId) { return ROUTES.get(corridor(roadId)); }

    /** A town's English name, or null. */
    public static String town(String id) { return id == null ? null : TOWNS.get(id); }
}
