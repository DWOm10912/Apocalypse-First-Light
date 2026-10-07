package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.structure.StructureSocketType;
import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import net.minecraft.core.Direction;

/** Bounded, immutable planning recipes, deliberately separate from real StructureDefinition/NBT metadata. */
public final class RoadLotCatalog {
    private static final String PATH = "/data/apocalypse_firstlight/afl_worldgen/roads/lot_catalog_v1.json";
    public record Building(BoundsXZ bounds, int height, int groundAnchor, int entranceX) {}
    public record Entrance(String name, StructureSocketType type, Direction side, int offset, int width,
                           List<BoundsXZ> paths) {
        public Entrance { paths = List.copyOf(paths); }
    }
    public record Variant(String id, String use, int width, int depth, int weight, Set<RoadType> roads,
                          boolean corner, Building building, List<BoundsXZ> parking,
                          List<BoundsXZ> service, List<Entrance> entrances) {
        public Variant { roads = Set.copyOf(roads); parking = List.copyOf(parking);
            service = List.copyOf(service); entrances = List.copyOf(entrances); }
    }
    public record Catalog(String revision, String fingerprint, int gap, int nodeClearance,
                          int terrainStep, int maxHeightDifference, List<Variant> variants) {
        public Catalog { variants = List.copyOf(variants); }
    }
    private static volatile Catalog cached;
    public static Catalog load() {
        Catalog value=cached;
        if(value==null) synchronized(RoadLotCatalog.class) {
            value=cached;if(value==null) cached=value=read();
        }
        return value;
    }
    public static String fingerprint() { return load().fingerprint(); }
    private RoadLotCatalog() {}

    private static Catalog read() {
        try (InputStream input = RoadLotCatalog.class.getResourceAsStream(PATH)) {
            if (input == null) throw new IllegalStateException("Missing road lot catalog: " + PATH);
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IllegalArgumentException("Road lot catalog exceeds 128 KiB");
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            fields(root, "schema_version", "revision", "lot_gap", "node_clearance", "terrain_step", "max_height_difference", "variants");
            if (integer(root,"schema_version",1,1) != 1) throw new IllegalArgumentException("Unsupported lot schema");
            List<Variant> variants = new ArrayList<>(); Set<String> ids = new HashSet<>();
            for (JsonElement element : array(root,"variants",32)) {
                JsonObject value = element.getAsJsonObject();
                fields(value,"id","use","size","weight","roads","corner","building","parking","service","entrances");
                String id = text(value,"id"), use = text(value,"use");
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate lot variant: " + id);
                int[] size = numbers(value.getAsJsonArray("size"),2,1,128);
                BoundsXZ lot = new BoundsXZ(0,0,size[0],size[1]);
                EnumSet<RoadType> roads = EnumSet.noneOf(RoadType.class);
                for (JsonElement road : array(value,"roads",3)) roads.add(RoadType.valueOf(road.getAsString()));
                if (roads.isEmpty()) throw new IllegalArgumentException("Lot has no compatible roads");
                JsonObject b = value.getAsJsonObject("building"); fields(b,"rect","height","ground_anchor","entrance_x");
                BoundsXZ body = rect(b.getAsJsonArray("rect"));
                if (!contains(lot,body)) throw new IllegalArgumentException("Building outside lot");
                Building building = new Building(body, integer(b,"height",2,64), integer(b,"ground_anchor",0,16),
                        integer(b,"entrance_x",0,(int)body.width()-1));
                if(building.groundAnchor()>=building.height()) throw new IllegalArgumentException("Ground anchor outside building volume");
                List<BoundsXZ> parking = rectangles(value,"parking"), service = rectangles(value,"service");
                List<BoundsXZ> occupied = new ArrayList<>(); occupied.add(body);
                for (BoundsXZ zone : concat(parking, service)) {
                    if (!contains(lot,zone) || occupied.stream().anyMatch(zone::intersects))
                        throw new IllegalArgumentException("Overlapping/outside functional zone in " + id);
                    occupied.add(zone);
                }
                List<Entrance> entrances = new ArrayList<>(); Set<String> names = new HashSet<>();
                for (JsonElement e : array(value,"entrances",8)) {
                    JsonObject ent = e.getAsJsonObject(); fields(ent,"name","type","side","offset","width","paths");
                    String name = text(ent,"name"); if (!names.add(name)) throw new IllegalArgumentException("Duplicate entrance");
                    Direction side = Direction.valueOf(text(ent,"side"));
                    if (!side.getAxis().isHorizontal()) throw new IllegalArgumentException("Vertical lot entrance");
                    int length = side.getAxis() == Direction.Axis.Z ? size[0] : size[1];
                    int width = integer(ent,"width",1,16), offset = integer(ent,"offset",0,length-width);
                    List<BoundsXZ> paths = rectangles(ent,"paths");
                    if (paths.isEmpty()) throw new IllegalArgumentException("Entrance without internal path");
                    for (BoundsXZ path : paths) if (!contains(lot,path) || occupied.stream().anyMatch(path::intersects))
                        throw new IllegalArgumentException("Entrance path obstructed in " + id + "/" + name);
                    BoundsXZ mouth = switch (side) {
                        case NORTH -> new BoundsXZ(offset,0,offset+width,1);
                        case SOUTH -> new BoundsXZ(offset,size[1]-1,offset+width,size[1]);
                        case WEST -> new BoundsXZ(0,offset,1,offset+width);
                        case EAST -> new BoundsXZ(size[0]-1,offset,size[0],offset+width);
                        default -> throw new IllegalArgumentException("Vertical entrance");
                    };
                    if (!contains(paths.get(0),mouth)) throw new IllegalArgumentException("Path misses entrance mouth");
                    for (int p=1;p<paths.size();p++) if (!touches(paths.get(p-1),paths.get(p)))
                        throw new IllegalArgumentException("Disconnected internal path");
                    entrances.add(new Entrance(name,StructureSocketType.valueOf(text(ent,"type")),side,offset,width,paths));
                }
                if (entrances.stream().noneMatch(e->e.type()==StructureSocketType.PEDESTRIAN))
                    throw new IllegalArgumentException("Missing pedestrian path");
                if (entrances.stream().noneMatch(e->e.type()!=StructureSocketType.PEDESTRIAN))
                    throw new IllegalArgumentException("Missing vehicle path");
                int entranceX = body.minX()+building.entranceX();
                if (entrances.stream().filter(e->e.type()==StructureSocketType.PEDESTRIAN)
                        .flatMap(e->e.paths().stream()).noneMatch(p->p.contains(entranceX,body.minZ()-1)))
                    throw new IllegalArgumentException("Pedestrian path does not reach building socket");
                boolean corner = value.get("corner").getAsBoolean();
                if (corner && entrances.stream().noneMatch(e->e.side()==Direction.WEST))
                    throw new IllegalArgumentException("Corner variant lacks secondary frontage");
                variants.add(new Variant(id,use,size[0],size[1],integer(value,"weight",1,100),roads,corner,
                        building,parking,service,entrances));
            }
            variants.sort(Comparator.comparing(Variant::id));
            if (variants.isEmpty()) throw new IllegalArgumentException("Empty road lot catalog");
            return new Catalog(text(root,"revision"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    integer(root,"lot_gap",0,16),integer(root,"node_clearance",24,64),integer(root,"terrain_step",1,8),
                    integer(root,"max_height_difference",0,3),variants);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            throw new IllegalStateException("Invalid road lot catalog " + PATH,e);
        }
    }
    static boolean contains(BoundsXZ outer, BoundsXZ inner) {
        return !inner.isEmpty() && inner.minX()>=outer.minX() && inner.minZ()>=outer.minZ()
                && inner.maxXExclusive()<=outer.maxXExclusive() && inner.maxZExclusive()<=outer.maxZExclusive();
    }
    private static boolean touches(BoundsXZ a, BoundsXZ b) {
        return a.intersects(b) || ((a.maxXExclusive()==b.minX() || b.maxXExclusive()==a.minX())
                && a.minZ()<b.maxZExclusive() && b.minZ()<a.maxZExclusive())
                || ((a.maxZExclusive()==b.minZ() || b.maxZExclusive()==a.minZ())
                && a.minX()<b.maxXExclusive() && b.minX()<a.maxXExclusive());
    }
    private static List<BoundsXZ> concat(List<BoundsXZ> a,List<BoundsXZ> b) { List<BoundsXZ> c=new ArrayList<>(a);c.addAll(b);return c; }
    private static List<BoundsXZ> rectangles(JsonObject o,String key) { List<BoundsXZ> result=new ArrayList<>();
        for(JsonElement e:array(o,key,8)) result.add(rect(e.getAsJsonArray())); return List.copyOf(result); }
    private static BoundsXZ rect(JsonArray a) { int[] r=numbers(a,4,0,128);
        if(r[2]<1||r[3]<1) throw new IllegalArgumentException("Empty rectangle");
        return new BoundsXZ(r[0],r[1],r[0]+r[2],r[1]+r[3]); }
    private static int[] numbers(JsonArray a,int length,int min,int max) {
        if(a==null||a.size()!=length) throw new IllegalArgumentException("Wrong numeric tuple size"); int[] n=new int[length];
        for(int i=0;i<length;i++) { n[i]=a.get(i).getAsBigDecimal().intValueExact();
            if(n[i]<min||n[i]>max) throw new IllegalArgumentException("Numeric tuple out of bounds"); } return n; }
    private static int integer(JsonObject o,String key,int min,int max) { int n=o.get(key).getAsBigDecimal().intValueExact();
        if(n<min||n>max) throw new IllegalArgumentException("Out of bounds: "+key); return n; }
    private static String text(JsonObject o,String key) { String s=o.get(key).getAsString();
        if(!s.matches("[a-zA-Z0-9_./:-]{1,96}")) throw new IllegalArgumentException("Invalid identifier: "+key); return s; }
    private static JsonArray array(JsonObject o,String key,int max) { JsonArray a=o.getAsJsonArray(key);
        if(a==null||a.size()>max) throw new IllegalArgumentException("Oversized/missing array: "+key); return a; }
    private static void fields(JsonObject o,String... allowed) { Set<String> keys=Set.of(allowed);
        if(!o.keySet().equals(keys)) throw new IllegalArgumentException("Missing or unknown catalog fields: "+o.keySet()); }
}
