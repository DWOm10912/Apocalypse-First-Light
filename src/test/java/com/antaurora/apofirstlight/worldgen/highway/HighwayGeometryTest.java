package com.antaurora.apofirstlight.worldgen.highway;

import java.util.*;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayGeometry.*;

/** No world, renderer bootstrap, images or block writes. Queries the production geometry. */
public final class HighwayGeometryTest {
    private record Pixel(int x, int z) {}
    private static int checks;
    public static void main(String[] args) {
        List<List<Point>> cases = List.of(
                points(0,0, 64,0, 128,-64), // A E -> NE
                points(0,0, 64,-64, 128,-64), // B NE -> E
                points(0,0, 64,0, 128,-64, 192,-64), // C
                points(0,0, 64,0, 128,64, 192,64), // D
                points(0,0, 0,-64, -64,-128, -64,-192), // E
                points(-49,-17, 15,-17, 79,-81), // F boundary crossing
                points(-48,16, 16,16, 80,-48), // G exact chunk corner
                points(0,0, 96,96)); // diagonal
        int count = 0;
        for (List<Point> points : cases) for (int rotation = 0; rotation < 4; rotation++) {
            List<Point> rotated = new ArrayList<>();
            for (Point p : points) {
                double x = p.x(), z = p.z();
                for (int i = 0; i < rotation; i++) { double previous = x; x = -z; z = previous; }
                rotated.add(new Point(x, z));
            }
            verify(new HighwayGeometry(rotated), "case=" + count++);
        }
        reject(() -> new HighwayGeometry(points(0,0, 64,0, 64,64)), "90 degree");
        reject(() -> new HighwayGeometry(points(0,0, 64,23)), "arbitrary angle");
        reject(() -> new HighwayGeometry(points(0,0, 2,0, 34,-32)), "short transition leg");
        System.out.println("HighwayGeometryTest PASS: " + count + " cases, " + checks + " checks; 4-connected pavement, no holes, exact chunk union");
    }
    private static List<Point> points(int... values) {
        List<Point> result = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) result.add(new Point(values[i], values[i+1]));
        return result;
    }
    private static void verify(HighwayGeometry geometry, String name) {
        BoundsXZ bounds = geometry.bounds(ROAD_HALF_WIDTH);
        List<Cell> raster = geometry.raster(bounds, ROAD_HALF_WIDTH);
        Set<Pixel> global = pixels(raster);
        check(!global.isEmpty() && connected(global, false), name + " pavement connected");
        check(noHoles(global, bounds.expand(1)), name + " internal holes");
        check(raster.equals(new HighwayGeometry(geometry.points()).raster(bounds, ROAD_HALF_WIDTH)), name + " deterministic");
        for (Cell cell : raster) {
            check(bounds.contains(cell.x(), cell.z()) && Math.abs(cell.sample().lateral()) <= ROAD_HALF_WIDTH + 1e-8, name + " nominal width/bounds");
        }
        double previous = -1;
        for (double s = 0; s <= geometry.length(); s += .5) {
            Point p = geometry.point(s);
            Sample projected = geometry.query((int) Math.round(p.x()), (int) Math.round(p.z()), ROAD_HALF_WIDTH);
            check(projected != null && projected.station() + 1e-7 >= previous, name + " monotonic station");
            previous = projected.station();
        }
        for (int band : new int[] {-10, -6, -2, 0, 2, 6, 10}) {
            Set<Pixel> line = new HashSet<>();
            for (Pixel p : global) if (geometry.inDetailBand(p.x,p.z,band)) line.add(p);
            check(connected(line, false), name + " detail band " + band);
        }
        Set<Pixel> left = new HashSet<>(), right = new HashSet<>();
        for (Cell c : raster) if (geometry.outerEdge(c.x(), c.z())) (c.sample().lateral() < 0 ? left : right).add(new Pixel(c.x(),c.z()));
        check(connected(left, true) && connected(right, true), name + " separate outer edges");
        Set<Pixel> union = new HashSet<>();
        for (int cx = Math.floorDiv(bounds.minX(),16); cx <= Math.floorDiv(bounds.maxXExclusive()-1,16); cx++)
            for (int cz = Math.floorDiv(bounds.minZ(),16); cz <= Math.floorDiv(bounds.maxZExclusive()-1,16); cz++) {
                BoundsXZ chunk = new BoundsXZ(cx*16,cz*16,cx*16+16,cz*16+16);
                // Independent query, not splitting an already computed global array.
                for (Cell c : geometry.raster(chunk, ROAD_HALF_WIDTH)) {
                    check(union.add(new Pixel(c.x(),c.z())), name + " duplicate ownership");
                    check(c.equals(rasterCell(geometry,c.x(),c.z())), name + " station seam");
                }
            }
        check(union.equals(global), name + " chunk union equals global");
        for (Pixel p : global) {
            Detail detail = geometry.marking(p.x,p.z);
            if (detail == null) continue;
            check(Math.abs(detail.band()) != 6 || dash(geometry.query(p.x,p.z,ROAD_HALF_WIDTH).station()), name + " station dash");
            int[][] directions = {{0,-1},{1,0},{0,1},{-1,0}};
            for (int i=0;i<4;i++) if ((detail.connections() & (1<<i)) != 0) {
                Detail next=geometry.marking(p.x+directions[i][0],p.z+directions[i][1]);
                check(next != null && next.band()==detail.band() && (next.connections() & (1<<((i+2)%4)))!=0, name+" reciprocal paint arms");
            }
        }
    }
    private static Cell rasterCell(HighwayGeometry geometry, int x, int z) { return new Cell(x,z,geometry.query(x,z,ROAD_HALF_WIDTH)); }
    private static Set<Pixel> pixels(List<Cell> cells) {
        Set<Pixel> result = new HashSet<>(); for (Cell c : cells) result.add(new Pixel(c.x(), c.z())); return result;
    }
    private static boolean connected(Set<Pixel> cells, boolean diagonal) {
        if (cells.isEmpty()) return false;
        Set<Pixel> seen = new HashSet<>(); ArrayDeque<Pixel> todo = new ArrayDeque<>();
        Pixel first = cells.iterator().next(); todo.add(first); seen.add(first);
        while (!todo.isEmpty()) {
            Pixel p = todo.removeFirst();
            for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0&&dz==0 || !diagonal && Math.abs(dx)+Math.abs(dz)!=1) continue;
                Pixel next = new Pixel(p.x+dx,p.z+dz); if (cells.contains(next)&&seen.add(next)) todo.add(next);
            }
        }
        return seen.size()==cells.size();
    }
    private static boolean noHoles(Set<Pixel> road, BoundsXZ bounds) {
        Set<Pixel> air=new HashSet<>();
        for(int x=bounds.minX();x<bounds.maxXExclusive();x++) for(int z=bounds.minZ();z<bounds.maxZExclusive();z++) {
            Pixel p=new Pixel(x,z); if(!road.contains(p)) air.add(p);
        }
        return connected(air,false);
    }
    private static void reject(Runnable action,String name) {
        try { action.run(); } catch(IllegalArgumentException expected) {checks++;return;} throw new AssertionError(name);
    }
    private static void check(boolean pass,String message) { if(!pass) throw new AssertionError(message); checks++; }
}
