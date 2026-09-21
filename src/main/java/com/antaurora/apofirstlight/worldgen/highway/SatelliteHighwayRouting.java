package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import java.util.*;
import static com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.*;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Build-time traffic selection over Macro candidates, never a terrain pathfinder. */
public final class SatelliteHighwayRouting {
    public static final int JUNCTION_SPACING = 192; // engineering halo, >= two 32-wide claims + transition
    public static final int STRAIGHT_APPROACH = 64; // two transition lengths
    public static final int BANK_ADJUSTMENT = 96; // candidate's documented strait half-width
    public static final int MAX_ACTUAL_BANK_SPAN = 800;
    private static final int[][] DIRECTIONS = {{1,0},{0,1},{-1,0},{0,-1}};
    public record Connection(String id,int islandId,MacroGeography.CrossingCandidate source,
                             String routeId,ParentAttachment parent,Node mainland,Node satellite,
                             int dx,int dz,double span,OrthogonalHighwayPath mainlandGeometry,OrthogonalHighwayPath islandGeometry) {}
    public record Result(List<Connection> connections,List<String> diagnostics) {
        public Result { connections=List.copyOf(connections); diagnostics=List.copyOf(diagnostics); }
    }
    private record Banks(HighwayGeometry.Point a,HighwayGeometry.Point b,int direction,double displacement) {}
    private record Choice(Connection connection,int turns,double length,double displacement) {}
    private SatelliteHighwayRouting() {}

    public static Result plan(HighwayRouteGraph trunks,MacroGeography macro,
                              List<MacroGeography.Island> islands,List<MacroGeography.CrossingCandidate> candidates) {
        List<Connection> chosen=new ArrayList<>(); List<String> errors=new ArrayList<>();
        for(var island:islands.stream().filter(i->i.role()==LandmassRole.SATELLITE_ISLAND)
                .sorted(Comparator.comparingInt(MacroGeography.Island::id)).toList()) {
            Choice best=null;
            for(var candidate:candidates.stream().filter(c->c.fromLandmassId()==0 && c.toLandmassId()==island.id()
                    && c.waterSpan()>=300 && c.waterSpan()<=600)
                    .sorted(Comparator.comparingInt(MacroGeography.CrossingCandidate::waterbodyId)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::fromX)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::fromZ)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::toX)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::toZ)
                            .thenComparingDouble(MacroGeography.CrossingCandidate::waterSpan)).toList()) {
                for(Banks bank:banks(candidate,macro,island.id())) {
                    int[] d=DIRECTIONS[bank.direction];
                    var islandGeometry=islandRoad(bank.b,d,macro,island);
                    if(islandGeometry==null) continue;
                    for(Edge parent:trunks.edges()) {
                        if(parent.routeType()!=RouteType.NATIONAL_TRUNK) continue;
                        for(int s:junctionStations(trunks,parent,chosen.stream().map(Connection::parent).toList())) {
                            int x=parent.orientation()==Orientation.NORTH_SOUTH?parent.fixedCoordinate():s;
                            int z=parent.orientation()==Orientation.NORTH_SOUTH?s:parent.fixedCoordinate();
                            var start=new HighwayGeometry.Point(x,z);
                            for(var path:OrthogonalHighwayPath.candidates(start,bank.a,d[0],d[1])) {
                                int turns=path.size()-2;
                                double length=length(path);
                                if(best!=null && (turns>best.turns || (turns==best.turns && length>best.length))) continue;
                                // Perpendicular departure ends at a reserved ramp zone, not a built hard junction.
                                var first=path.get(1);
                                if(parent.orientation()==Orientation.NORTH_SOUTH ? first.z()!=z : first.x()!=x)continue;
                                if(!landPath(path,macro,0)) continue;
                                var geometry=new OrthogonalHighwayPath(path);
                                if(conflicts(geometry,islandGeometry,parent,trunks,chosen))continue;
                                String route="strategic_branch/satellite_"+island.id();
                                var attachment=new ParentAttachment(parent.routeId(),parent.id(),s,parent.id()+"/junction/"+s);
                                var mainland=new Node(route+"/mainland_bridgehead",NodeKind.MAINLAND_BRIDGEHEAD,(int)bank.a.x(),(int)bank.a.z());
                                var satellite=new Node(route+"/satellite_bridgehead",NodeKind.SATELLITE_BRIDGEHEAD,(int)bank.b.x(),(int)bank.b.z());
                                var connection=new Connection("sea_crossing/satellite_"+island.id(),island.id(),candidate,route,attachment,
                                        mainland,satellite,d[0],d[1],Math.hypot(bank.b.x()-bank.a.x(),bank.b.z()-bank.a.z()),geometry,islandGeometry);
                                Choice next=new Choice(connection,turns,length,bank.displacement);
                                if(best==null || order(next,best)<0) best=next;
                            }
                        }
                    }
                }
            }
            if(best==null) errors.add("satellite="+island.id()+": NO_VALID_ORTHOGONAL_ROUTE_WITHIN_V1_LIMITS (axial banks, actual span 300..800, land approach, reservations and 0-2 turns)");
            else chosen.add(best.connection);
        }
        return new Result(chosen,errors);
    }
    private static int order(Choice a,Choice b) {
        int c=Integer.compare(a.turns,b.turns); if(c!=0)return c;
        c=Double.compare(a.length,b.length); if(c!=0)return c;
        c=Double.compare(a.connection.span,b.connection.span); if(c!=0)return c;
        c=Double.compare(a.displacement,b.displacement); if(c!=0)return c;
        c=Double.compare(a.connection.source.waterSpan(),b.connection.source.waterSpan()); if(c!=0)return c;
        c=Integer.compare(a.connection.source.waterbodyId(),b.connection.source.waterbodyId()); if(c!=0)return c;
        c=a.connection.parent.parentEdgeId().compareTo(b.connection.parent.parentEdgeId()); if(c!=0)return c;
        c=Integer.compare(a.connection.parent.parentStation(),b.connection.parent.parentStation()); if(c!=0)return c;
        c=Integer.compare(a.connection.mainland.x(),b.connection.mainland.x());
        return c!=0?c:Integer.compare(a.connection.mainland.z(),b.connection.mainland.z());
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
    private static List<Banks> banks(MacroGeography.CrossingCandidate c,MacroGeography macro,int island) {
        List<Banks> result=new ArrayList<>();
        double vx=c.toX()-c.fromX(),vz=c.toZ()-c.fromZ();
        for(int dir=0;dir<DIRECTIONS.length;dir++) {
            int[] d=DIRECTIONS[dir]; double norm=Math.hypot(d[0],d[1]);
            if(vx*d[0]+vz*d[1]<=0) continue;
            int steps=(int)Math.round((vx*d[0]+vz*d[1])/(norm*norm));
            int ax=(int)Math.round((c.fromX()+c.toX()-steps*d[0])/2);
            int az=(int)Math.round((c.fromZ()+c.toZ()-steps*d[1])/2);
            // Local translations only; endpoints stay within 96 blocks of the authoritative bank points.
            for(int offset=-32;offset<=32;offset+=8) for(int fromShift=-32;fromShift<=32;fromShift+=8)
                for(int toShift=-32;toShift<=32;toShift+=8) {
                int x=ax-offset*d[1]+fromShift*d[0],z=az+offset*d[0]+fromShift*d[1];
                int bx=ax-offset*d[1]+(steps+toShift)*d[0],bz=az+offset*d[0]+(steps+toShift)*d[1];
                double da=Math.hypot(x-c.fromX(),z-c.fromZ()),db=Math.hypot(bx-c.toX(),bz-c.toZ());
                if(Math.max(da,db)>BANK_ADJUSTMENT)continue;
                double actual=Math.hypot(bx-x,bz-z);
                if(actual<300 || actual>MAX_ACTUAL_BANK_SPAN)continue;
                var a=new HighwayGeometry.Point(x,z); var b=new HighwayGeometry.Point(bx,bz);
                if(landPath(List.of(new HighwayGeometry.Point(x-d[0]*64,z-d[1]*64),a),macro,0)
                        &&landPath(List.of(b,new HighwayGeometry.Point(bx+d[0]*64,bz+d[1]*64)),macro,island))
                    result.add(new Banks(a,b,dir,da+db));
            }
        }
        return result.stream().sorted(Comparator.comparingDouble((Banks b)->Math.hypot(b.a.x()-b.b.x(),b.a.z()-b.b.z())).thenComparingDouble(Banks::displacement)
                .thenComparingDouble(b->b.a.x()).thenComparingDouble(b->b.a.z())
                .thenComparingDouble(b->b.b.x()).thenComparingDouble(b->b.b.z())).limit(8).toList();
    }
    private static OrthogonalHighwayPath islandRoad(HighwayGeometry.Point b,int[] d,MacroGeography macro,MacroGeography.Island island) {
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
    private static boolean landPath(List<HighwayGeometry.Point> p,MacroGeography macro,int id) {
        for(int i=1;i<p.size()-1;i++) {
            var turn=p.get(i);
            for(int dx=-32;dx<=32;dx+=16)for(int dz=-32;dz<=32;dz+=16) {
                var s=macro.sample((int)turn.x()+dx,(int)turn.z()+dz);
                if(s.landmassId()!=id||s.surfaceClass()!=SurfaceClass.LAND)return false;
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
                    if(sample.landmassId()!=id||sample.surfaceClass()!=SurfaceClass.LAND)return false;
                    if(id==0 && Math.hypot(x,z)<=MacroGeography.STARTUP_MAINLAND_RESERVE+32)return false;
                }
            }
        }
        return true;
    }
}
