package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import com.antaurora.apofirstlight.worldgen.geography.SatelliteIslandPolicy;
import java.util.*;
import static com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.*;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Build-time traffic selection over Macro candidates, never a terrain pathfinder. */
public final class SatelliteHighwayRouting {
    public static final int JUNCTION_SPACING = 192; // engineering halo, >= two 32-wide claims + transition
    public static final int STRAIGHT_APPROACH = 64; // two transition lengths
    public static final int BRIDGEHEAD_SEARCH_RADIUS = 512;
    public static final int SHORE_STEP = 8;
    public static final int SHORE_INSET = 192;
    public static final int BUCKET_CAP = 4;
    public static final int PAIR_CAP = 64;
    public static final int MAX_ACTUAL_BANK_SPAN = 800;
    // One nominal reserved turn length, not an unbounded preference for straight roads.
    public static final int TURN_COST_BLOCKS = OrthogonalHighwayPath.RESERVED_LENGTH;
    private static final int[][] DIRECTIONS = {{1,0},{0,1},{-1,0},{0,-1}};
    public record Connection(String id,int islandId,MacroGeography.CrossingCandidate source,
                             String routeId,ParentAttachment parent,Node mainland,Node satellite,
                             int dx,int dz,double span,OrthogonalHighwayPath mainlandGeometry,OrthogonalHighwayPath islandGeometry) {
        public double mainlandDisplacement() { return Math.hypot(mainland.x()-source.fromX(),mainland.z()-source.fromZ()); }
        public double satelliteDisplacement() { return Math.hypot(satellite.x()-source.toX(),satellite.z()-source.toZ()); }
        public double combinedDisplacement() { return mainlandDisplacement()+satelliteDisplacement(); }
        public double mainlandRouteLength() { return mainlandGeometry.length(); }
        public int turnCount() { return mainlandGeometry.points().size()-2; }
        public double extraDistance() {
            var p=mainlandGeometry.points();
            return mainlandRouteLength()-OrthogonalHighwayPath.distance(p.get(0),p.get(p.size()-1));
        }
        public double networkCost() { return mainlandRouteLength()+TURN_COST_BLOCKS*turnCount(); }
    }
    /** Applied only to complete feasible plans. Lower-tier fields never outweigh network cost. */
    record RouteCost(double networkCost,double extraDistance,int turns,double span,double displacement,double islandLength)
            implements Comparable<RouteCost> {
        public int compareTo(RouteCost b) {
            int c=Double.compare(networkCost,b.networkCost);if(c!=0)return c;
            c=Double.compare(extraDistance,b.extraDistance);if(c!=0)return c;
            c=Integer.compare(turns,b.turns);if(c!=0)return c;
            c=Double.compare(span,b.span);if(c!=0)return c;
            c=Double.compare(displacement,b.displacement);if(c!=0)return c;
            return Double.compare(islandLength,b.islandLength);
        }
    }
    static RouteCost cost(Connection c) {
        return new RouteCost(c.networkCost(),c.extraDistance(),c.turnCount(),c.span(),c.combinedDisplacement(),c.islandGeometry().length());
    }
    public record Result(List<Connection> connections,List<String> diagnostics) {
        public Result { connections=List.copyOf(connections); diagnostics=List.copyOf(diagnostics); }
    }
    record Banks(HighwayGeometry.Point a,HighwayGeometry.Point b,int direction,double displacement) {}
    enum Rejection { NO_SHORE_POINTS, NO_AXIAL_BRIDGE_PAIR, ACTUAL_SPAN_TOO_LONG, INVALID_WATER_CROSSING,
        NO_ISLAND_APPROACH, NO_MAINLAND_ROUTE, NO_VALID_JUNCTION, ROUTE_CONFLICT }
    static final class SearchStats {
        int shorePointsMainland,shorePointsSatellite,axialPairs,viablePairs,islandApproaches,junctions,landRoutes;
        final EnumMap<Rejection,Integer> rejected=new EnumMap<>(Rejection.class);
        void reject(Rejection r) { rejected.merge(r,1,Integer::sum); }
        Rejection failure() {
            if(shorePointsMainland==0||shorePointsSatellite==0)return Rejection.NO_SHORE_POINTS;
            if(axialPairs==0)return Rejection.NO_AXIAL_BRIDGE_PAIR;
            if(viablePairs==0)return rejected.containsKey(Rejection.INVALID_WATER_CROSSING)?Rejection.INVALID_WATER_CROSSING:Rejection.ACTUAL_SPAN_TOO_LONG;
            if(islandApproaches==0)return Rejection.NO_ISLAND_APPROACH;
            if(junctions==0)return Rejection.NO_VALID_JUNCTION;
            return landRoutes==0?Rejection.NO_MAINLAND_ROUTE:Rejection.ROUTE_CONFLICT;
        }
        public String toString() { return "shorePointsMainland="+shorePointsMainland+" shorePointsSatellite="+shorePointsSatellite
                +" axialPairs="+axialPairs+" viablePairs="+viablePairs+" islandApproaches="+islandApproaches
                +" junctions="+junctions+" landRoutes="+landRoutes+" rejected="+rejected; }
    }
    private record Choice(Connection connection) {}
    private SatelliteHighwayRouting() {}

    public static Result plan(HighwayRouteGraph trunks,MacroGeography macro,
                              List<MacroGeography.Island> islands,List<MacroGeography.CrossingCandidate> candidates) {
        List<Connection> chosen=new ArrayList<>(); List<String> errors=new ArrayList<>();
        for(var island:islands.stream().filter(i->i.role()==LandmassRole.SATELLITE_ISLAND)
                .filter(i -> macro.satellitePolicy(i.id()).bridgePolicy() == SatelliteIslandPolicy.BridgePolicy.BRIDGE_REQUIRED)
                .sorted(Comparator.comparingInt(MacroGeography.Island::id)).toList()) {
            Choice best=null; SearchStats stats=new SearchStats();
            for(var candidate:candidates.stream().filter(c->c.fromLandmassId()==0 && c.toLandmassId()==island.id()
                    && c.waterSpan()>=300 && c.waterSpan()<=600)
                    .sorted(Comparator.comparingInt(MacroGeography.CrossingCandidate::waterbodyId)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::fromX)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::fromZ)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::toX)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::toZ)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::waterSpan)).toList()) {
                for(Banks bank:rankedBanks(candidate,macro,island.id(),stats)) {
                    int[] d=DIRECTIONS[bank.direction];
                    var islandGeometry=islandRoad(bank.b,d,macro,island);
                    if(islandGeometry==null) { stats.reject(Rejection.NO_ISLAND_APPROACH); continue; }
                    stats.islandApproaches++;
                    for(Edge parent:trunks.edges()) {
                        if(parent.routeType()!=RouteType.NATIONAL_TRUNK) continue;
                        for(int s:junctionStations(trunks,parent,chosen.stream().map(Connection::parent).toList())) {
                            stats.junctions++;
                            int x=parent.orientation()==Orientation.NORTH_SOUTH?parent.fixedCoordinate():s;
                            int z=parent.orientation()==Orientation.NORTH_SOUTH?s:parent.fixedCoordinate();
                            var start=new HighwayGeometry.Point(x,z);
                            for(var path:OrthogonalHighwayPath.candidates(start,bank.a,d[0],d[1])) {
                                int turns=path.size()-2;
                                double length=length(path);
                                // Safe dominance bound over the full route, never over a bank alone.
                                if(best!=null && length+TURN_COST_BLOCKS*turns>best.connection.networkCost())continue;
                                // Perpendicular departure ends at a reserved ramp zone, not a built hard junction.
                                var first=path.get(1);
                                if(parent.orientation()==Orientation.NORTH_SOUTH ? first.z()!=z : first.x()!=x)continue;
                                if(!landPath(path,macro,0)) { stats.reject(Rejection.NO_MAINLAND_ROUTE); continue; }
                                stats.landRoutes++;
                                var geometry=new OrthogonalHighwayPath(path);
                                if(conflicts(geometry,islandGeometry,parent,trunks,chosen)) { stats.reject(Rejection.ROUTE_CONFLICT); continue; }
                                String route="strategic_branch/satellite_"+island.id();
                                var attachment=new ParentAttachment(parent.routeId(),parent.id(),s,parent.id()+"/junction/"+s);
                                var mainland=new Node(route+"/mainland_bridgehead",NodeKind.MAINLAND_BRIDGEHEAD,(int)bank.a.x(),(int)bank.a.z());
                                var satellite=new Node(route+"/satellite_bridgehead",NodeKind.SATELLITE_BRIDGEHEAD,(int)bank.b.x(),(int)bank.b.z());
                                var connection=new Connection("sea_crossing/satellite_"+island.id(),island.id(),candidate,route,attachment,
                                        mainland,satellite,d[0],d[1],Math.hypot(bank.b.x()-bank.a.x(),bank.b.z()-bank.a.z()),geometry,islandGeometry);
                                Choice next=new Choice(connection);
                                if(best==null || order(next,best)<0) best=next;
                            }
                        }
                    }
                }
            }
            if(best==null) errors.add("satellite="+island.id()+": firstFailureReason="+stats.failure()+" "+stats
                    +" sourceCandidates="+candidates.stream().filter(c->c.toLandmassId()==island.id())
                    .sorted(Comparator.comparing(MacroGeography.CrossingCandidate::toString)).toList());
            else chosen.add(best.connection);
        }
        return new Result(chosen,errors);
    }
    private static int order(Choice a,Choice b) {
        return compareConnections(a.connection,b.connection);
    }
    static int compareConnections(Connection a,Connection b) {
        int c=cost(a).compareTo(cost(b));if(c!=0)return c;
        c=a.routeId.compareTo(b.routeId);if(c!=0)return c;
        c=a.parent.parentEdgeId().compareTo(b.parent.parentEdgeId());if(c!=0)return c;
        c=Integer.compare(a.parent.parentStation(),b.parent.parentStation());if(c!=0)return c;
        c=Integer.compare(a.mainland.x(),b.mainland.x());if(c!=0)return c;
        c=Integer.compare(a.mainland.z(),b.mainland.z());if(c!=0)return c;
        c=Integer.compare(a.satellite.x(),b.satellite.x());if(c!=0)return c;
        c=Integer.compare(a.satellite.z(),b.satellite.z());if(c!=0)return c;
        c=a.source.toString().compareTo(b.source.toString());if(c!=0)return c;
        c=a.mainlandGeometry.points().toString().compareTo(b.mainlandGeometry.points().toString());
        return c!=0?c:a.islandGeometry.points().toString().compareTo(b.islandGeometry.points().toString());
    }
    static List<Integer> junctionStations(HighwayRouteGraph graph,Edge parent,List<ParentAttachment> occupied) {
        List<Integer> result=new ArrayList<>();
        for(int s=parent.startStation()+JUNCTION_SPACING;s<=parent.endStation()-JUNCTION_SPACING;s+=32) {
            if(Math.abs(s-parent.globalStation(graph.intersection().x(),graph.intersection().z()))<JUNCTION_SPACING) continue;
            final int station=s;
            if(occupied.stream().anyMatch(a->a.parentEdgeId().equals(parent.id())
                    &&Math.abs(a.parentStation()-station)<JUNCTION_SPACING))continue;
            result.add(s);
        }
        return List.copyOf(result);
    }
    /** Ordered immutable alternatives; only a selected route is published/claimed. */
    static List<Banks> rankedBanks(MacroGeography.CrossingCandidate c,MacroGeography macro,int island,SearchStats stats) {
        List<Banks> result=new ArrayList<>();
        for(int dir=0;dir<DIRECTIONS.length;dir++) {
            int[] d=DIRECTIONS[dir];
            if((c.toX()-c.fromX())*d[0]+(c.toZ()-c.fromZ())*d[1]<=0)continue;
            var mainland=shore(c.fromX(),c.fromZ(),d[0],d[1],macro,0);
            var satellite=shore(c.toX(),c.toZ(),-d[0],-d[1],macro,island);
            stats.shorePointsMainland+=mainland.values().stream().mapToInt(List::size).sum();
            stats.shorePointsSatellite+=satellite.values().stream().mapToInt(List::size).sum();
            for(var entry:mainland.entrySet())for(var a:entry.getValue())
                for(var b:satellite.getOrDefault(entry.getKey(),List.of())) {
                    double span=(b.x()-a.x())*d[0]+(b.z()-a.z())*d[1];
                    if(span<300)continue;
                    stats.axialPairs++;
                    if(span>MAX_ACTUAL_BANK_SPAN) { stats.reject(Rejection.ACTUAL_SPAN_TOO_LONG); continue; }
                    if(!waterLine(a,b,c,macro)) { stats.reject(Rejection.INVALID_WATER_CROSSING); continue; }
                    result.add(new Banks(a,b,dir,Math.hypot(a.x()-c.fromX(),a.z()-c.fromZ())
                            +Math.hypot(b.x()-c.toX(),b.z()-c.toZ())));
                }
        }
        stats.viablePairs+=result.size();
        return result.stream().sorted(Comparator.comparingDouble(Banks::displacement)
                .thenComparingDouble(b->Math.hypot(b.a.x()-b.b.x(),b.a.z()-b.b.z()))
                .thenComparingInt(Banks::direction).thenComparingDouble(b->b.a.x()).thenComparingDouble(b->b.a.z())
                .thenComparingDouble(b->b.b.x()).thenComparingDouble(b->b.b.z())).limit(PAIR_CAP).toList();
    }
    /** Global transverse 8-grid guarantees that opposite shores share bucket keys.
     * Detect dry/wet transitions at 8, refine the boundary at 1, then try bounded inland insets.
     * Each bucket retains at most four approach-valid points nearest the source anchor. */
    static SortedMap<Integer,List<HighwayGeometry.Point>> shore(double cx,double cz,int dx,int dz,MacroGeography macro,int id) {
        var buckets=new TreeMap<Integer,List<HighwayGeometry.Point>>();
        double cross=dx!=0?cz:cx,along=dx!=0?cx:cz;
        int sign=dx+dz;
        for(int t=(int)Math.ceil((cross-BRIDGEHEAD_SEARCH_RADIUS)/SHORE_STEP)*SHORE_STEP;
                t<=cross+BRIDGEHEAD_SEARCH_RADIUS;t+=SHORE_STEP) {
            Set<HighwayGeometry.Point> points=new HashSet<>();
            for(int s=(int)Math.ceil((along-BRIDGEHEAD_SEARCH_RADIUS)/SHORE_STEP)*SHORE_STEP;
                    s<=along+BRIDGEHEAD_SEARCH_RADIUS;s+=SHORE_STEP) {
                int x=dx!=0?s:t,z=dx!=0?t:s;
                if(!isUsableDryLandOrCoast(macro.sample(x,z),id)
                        ||isUsableDryLandOrCoast(macro.sample(x+dx*SHORE_STEP,z+dz*SHORE_STEP),id))continue;
                int boundary=s;
                for(int k=1;k<SHORE_STEP;k++) {
                    if(!isUsableDryLandOrCoast(macro.sample(x+dx*k,z+dz*k),id))break;
                    boundary=s+sign*k;
                }
                for(int inset=0;inset<=SHORE_INSET;inset+=SHORE_STEP) {
                    int p=boundary-sign*inset;
                    var bank=new HighwayGeometry.Point(dx!=0?p:t,dx!=0?t:p);
                    if(Math.hypot(bank.x()-cx,bank.z()-cz)>BRIDGEHEAD_SEARCH_RADIUS)continue;
                    var inland=new HighwayGeometry.Point(bank.x()-dx*STRAIGHT_APPROACH,bank.z()-dz*STRAIGHT_APPROACH);
                    if(landPath(List.of(inland,bank),macro,id))points.add(bank);
                }
            }
            var ranked=points.stream().sorted(Comparator.comparingDouble((HighwayGeometry.Point p)->Math.hypot(p.x()-cx,p.z()-cz))
                    .thenComparingDouble(HighwayGeometry.Point::x).thenComparingDouble(HighwayGeometry.Point::z)).limit(BUCKET_CAP).toList();
            if(!ranked.isEmpty())buckets.put(t,ranked);
        }
        return Collections.unmodifiableSortedMap(buckets);
    }
    static boolean isUsableDryLandOrCoast(MacroGeographySample s,int id) {
        return id>=0 && s.landmassId()==id && (s.surfaceClass()==SurfaceClass.LAND||s.surfaceClass()==SurfaceClass.COAST)
                && s.waterClass()==WaterClass.NONE && s.waterbodyId()==-1 && s.coastDistance()>=0;
    }
    /** Only source strait and its unnumbered coastal fringe, in mainland-water-island order.
     * No other numbered bay/strait, open ocean, third landmass, or island re-entry. */
    static boolean waterLine(HighwayGeometry.Point a,HighwayGeometry.Point b,MacroGeography.CrossingCandidate c,MacroGeography macro) {
        double span=Math.hypot(b.x()-a.x(),b.z()-a.z());
        if(span==0 || (a.x()!=b.x()&&a.z()!=b.z()))return false;
        if(!isUsableDryLandOrCoast(macro.sample((int)a.x(),(int)a.z()),c.fromLandmassId())
                ||!isUsableDryLandOrCoast(macro.sample((int)b.x(),(int)b.z()),c.toLandmassId()))return false;
        int phase=0; boolean source=false;
        for(int k=0;k<=(int)Math.ceil(span);k++) {
            double t=Math.min(k/span,1);
            var s=macro.sample((int)Math.round(a.x()+(b.x()-a.x())*t),(int)Math.round(a.z()+(b.z()-a.z())*t));
            if(isUsableDryLandOrCoast(s,c.fromLandmassId())) { if(phase!=0)return false; }
            else if(isUsableDryLandOrCoast(s,c.toLandmassId())) { if(phase==0)return false; phase=2; }
            else {
                if(phase==2||s.landmassId()!=-1||s.surfaceClass()==SurfaceClass.OPEN_OCEAN)return false;
                if(s.waterbodyId()==c.waterbodyId()&&s.waterClass()==WaterClass.STRAIT)source=true;
                else if(s.waterbodyId()!=0||s.waterClass()!=WaterClass.COASTAL_WATER)return false;
                phase=1;
            }
        }
        return source&&phase==2;
    }
    static OrthogonalHighwayPath islandRoad(HighwayGeometry.Point b,int[] d,MacroGeography macro,MacroGeography.Island island) {
        int limit=(int)Math.floor(island.majorRadius()/Math.hypot(d[0],d[1]));
        int last=0;
        for(int s=8;s<=limit;s+=8) {
            var end=new HighwayGeometry.Point(b.x()+s*d[0],b.z()+s*d[1]);
            if(!landPath(List.of(new HighwayGeometry.Point(b.x()+(s-8)*d[0],b.z()+(s-8)*d[1]),end),macro,island.id()))break;
            last=s;
        }
        double minimum=Math.max(128,island.minorRadius()/2);
        if(last>=minimum)return new OrthogonalHighwayPath(List.of(b,new HighwayGeometry.Point(b.x()+last*d[0],b.z()+last*d[1])));
        OrthogonalHighwayPath best=null;
        // Bounded one-turn alternative towards the island interior; no terrain/pathfinding search.
        for(int s=112;s<=last;s+=32) {
            var turn=new HighwayGeometry.Point(b.x()+s*d[0],b.z()+s*d[1]);
            var end=d[0]!=0?new HighwayGeometry.Point(turn.x(),Math.rint(island.z()/8)*8)
                    :new HighwayGeometry.Point(Math.rint(island.x()/8)*8,turn.z());
            if(OrthogonalHighwayPath.distance(turn,end)<80)continue;
            var path=List.of(b,turn,end);
            if(length(path)<minimum||!landPath(path,macro,island.id()))continue;
            var next=new OrthogonalHighwayPath(path);
            if(best==null||next.length()<best.length())best=next;
        }
        return best;
    }
    static boolean conflicts(OrthogonalHighwayPath mainland,OrthogonalHighwayPath island,Edge parent,
                             HighwayRouteGraph trunks,List<Connection> chosen) {
        var footprint=new ArrayList<>(mainland.edgeBounds(true,32));
        footprint.addAll(mainland.reservations(true));
        footprint.addAll(island.edgeBounds(false,32));
        footprint.addAll(island.reservations(false));
        var junction=OrthogonalHighwayPath.zone(mainland.points().get(0));
        for(var b:footprint)for(var trunk:trunks.edges()) {
            if(trunk.id().equals(parent.id()) && b.equals(junction))continue;
            if(b.intersects(trunk.bounds(32)))return true;
        }
        for(var other:chosen) {
            var occupied=new ArrayList<>(other.mainlandGeometry.edgeBounds(true,32));
            occupied.addAll(other.mainlandGeometry.reservations(true));
            occupied.addAll(other.islandGeometry.edgeBounds(false,32));
            occupied.addAll(other.islandGeometry.reservations(false));
            for(var a:footprint)for(var b:occupied)if(a.intersects(b))return true;
        }
        return false;
    }
    private static double length(List<HighwayGeometry.Point> p) {
        double n=0;for(int i=1;i<p.size();i++)n+=Math.hypot(p.get(i).x()-p.get(i-1).x(),p.get(i).z()-p.get(i-1).z());return n;
    }
    static boolean landPath(List<HighwayGeometry.Point> p,MacroGeography macro,int id) {
        for(int i=1;i<p.size()-1;i++) {
            var turn=p.get(i);
            for(int dx=-32;dx<=32;dx+=16)for(int dz=-32;dz<=32;dz+=16) {
                var s=macro.sample((int)turn.x()+dx,(int)turn.z()+dz);
                if(!isUsableDryLandOrCoast(s,id))return false;
            }
        }
        for(int i=1;i<p.size();i++) {
            var a=p.get(i-1);var b=p.get(i);double length=Math.hypot(b.x()-a.x(),b.z()-a.z());
            double nx=-(b.z()-a.z())/length,nz=(b.x()-a.x())/length;
            for(double s=0;s<=length+8;s+=8) {
                double t=Math.min(s,length)/length;
                for(int lateral=-32;lateral<=32;lateral+=16) {
                    int x=(int)Math.round(a.x()+(b.x()-a.x())*t+nx*lateral),z=(int)Math.round(a.z()+(b.z()-a.z())*t+nz*lateral);
                    var sample=macro.sample(x,z);
                    if(!isUsableDryLandOrCoast(sample,id))return false;
                    if(id==0 && Math.hypot(x,z)<=MacroGeography.STARTUP_MAINLAND_RESERVE+32)return false;
                }
            }
        }
        return true;
    }
}
