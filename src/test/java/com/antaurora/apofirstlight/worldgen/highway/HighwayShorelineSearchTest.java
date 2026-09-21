package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.dev.HighwayNetworkExport;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import java.util.*;
import static com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.*;
import static com.antaurora.apofirstlight.worldgen.highway.SatelliteHighwayRouting.*;

/** Pure seeded geography/planning contracts; no chunk, renderer, or engineering context. */
public final class HighwayShorelineSearchTest {
    private static int checks;
    public static void main(String[] args) {
        check(isUsableDryLandOrCoast(sample(SurfaceClass.LAND,0,-1,WaterClass.NONE,80),0),"LAND accepted");
        check(isUsableDryLandOrCoast(sample(SurfaceClass.COAST,0,-1,WaterClass.NONE,12),0),"dry COAST accepted");
        check(!isUsableDryLandOrCoast(sample(SurfaceClass.COAST,-1,0,WaterClass.COASTAL_WATER,-12),0),"wet COAST rejected");
        check(!isUsableDryLandOrCoast(sample(SurfaceClass.OPEN_OCEAN,-1,0,WaterClass.OPEN_OCEAN,-500),0),"ocean rejected");
        check(!isUsableDryLandOrCoast(sample(SurfaceClass.LAND,2,-1,WaterClass.NONE,80),1),"wrong island rejected");
        check(!isUsableDryLandOrCoast(sample(SurfaceClass.COAST,0,100,WaterClass.STRAIT,12),0),"water identity required");
        boolean sameX=false,sameZ=false,coast=false,land=false;
        for(long seed:new long[]{0,2,42,-645704099691625981L,-4332662446239654818L}) {
            var macro=MacroGeography.forSeed(seed);
            for(var c:macro.crossingCandidates()) {
                var stats=new SearchStats(); var pairs=rankedBanks(c,macro,c.toLandmassId(),stats);
                check(pairs.size()<=PAIR_CAP,"bounded alternatives");
                check(pairs.equals(rankedBanks(c,macro,c.toLandmassId(),new SearchStats())),"deterministic ordered alternatives");
                double last=-1;
                for(var p:pairs) {
                    check(p.displacement()>=last,"displacement takes precedence"); last=p.displacement();
                    sameX|=p.a().x()==p.b().x(); sameZ|=p.a().z()==p.b().z();
                    check((p.a().x()==p.b().x())!=(p.a().z()==p.b().z()),"no diagonal pair");
                    check(Math.hypot(p.a().x()-c.fromX(),p.a().z()-c.fromZ())<=BRIDGEHEAD_SEARCH_RADIUS,"mainland radius");
                    check(Math.hypot(p.b().x()-c.toX(),p.b().z()-c.toZ())<=BRIDGEHEAD_SEARCH_RADIUS,"satellite radius");
                    check(Math.hypot(p.a().x()-p.b().x(),p.a().z()-p.b().z())<=800,"actual span");
                    check(waterLine(p.a(),p.b(),c,macro),"source water continuity");
                    var wrong=new MacroGeography.CrossingCandidate(c.waterbodyId()+1000,0,c.toLandmassId(),c.fromX(),c.fromZ(),c.toX(),c.toZ(),c.waterSpan());
                    check(!waterLine(p.a(),p.b(),wrong,macro),"wrong waterbody rejected");
                    var wrongIsland=new MacroGeography.CrossingCandidate(c.waterbodyId(),0,999,c.fromX(),c.fromZ(),c.toX(),c.toZ(),c.waterSpan());
                    check(!waterLine(p.a(),p.b(),wrongIsland,macro),"third landmass rejected");
                    var sa=macro.sample((int)p.a().x(),(int)p.a().z());
                    var sb=macro.sample((int)p.b().x(),(int)p.b().z());
                    coast|=sa.surfaceClass()==SurfaceClass.COAST||sb.surfaceClass()==SurfaceClass.COAST;
                    land|=sa.surfaceClass()==SurfaceClass.LAND||sb.surfaceClass()==SurfaceClass.LAND;
                }
                System.out.println("shoreline seed="+seed+" island="+c.toLandmassId()+" "+stats+" retained="+pairs.size());
            }
            var graph=HighwayRouteGraph.build(seed);
            check(graph.seaCrossings().size()==macro.islands().size(),"all representative islands connected");
            for(var c:graph.seaCrossings()) {
                System.out.println("selected seed="+seed+" island="+c.islandId()+" banks="+c.mainland().x()+","+c.mainland().z()
                        +" -> "+c.satellite().x()+","+c.satellite().z()+" displacement="+c.mainlandDisplacement()+","+c.satelliteDisplacement()
                        +" turns="+(c.mainlandGeometry().points().size()-2)+" islandTurns="+(c.islandGeometry().points().size()-2));
                check(c.mainlandGeometry().points().size()<=4,"mainland max two turns");
                check(c.islandGeometry().points().size()<=3,"island max one turn");
                check(landPath(c.mainlandGeometry().points(),macro,0),"mainland dry path");
                check(landPath(c.islandGeometry().points(),macro,c.islandId()),"island dry path");
            }
            String text=HighwayNetworkExport.report(graph,macro);
            check(text.contains("mainlandDisplacement = ")&&text.contains("satelliteDisplacement = ")
                    &&text.contains("combinedDisplacement = ")&&text.contains("bridgeApproachEngineeringStatus = UNKNOWN"),"export selected metadata");
            for(String failure:graph.routingDiagnostics())check(text.contains(failure)&&failure.contains("axialPairs="),"export retains stage counts");
        }
        check(sameX&&sameZ,"both axial pairing orientations");check(coast&&land,"real collected dry COAST and LAND");
        // Audit witnesses: both approaches share the same dry-side predicate, not LAND-only.
        var m=MacroGeography.forSeed(0);
        var sourceBank=m.crossingCandidates().get(0);
        var shorePoints=shore(sourceBank.fromX(),sourceBank.fromZ(),0,1,m,0);
        check(shorePoints.size()<=129,"bounded transverse buckets");
        for(var entry:shorePoints.entrySet()) {
            check(entry.getValue().size()<=BUCKET_CAP,"per-bucket candidate cap");
            for(var p:entry.getValue()) {
                check(p.x()==entry.getKey(),"shared global transverse bucket");
                check(Math.hypot(p.x()-sourceBank.fromX(),p.z()-sourceBank.fromZ())<=BRIDGEHEAD_SEARCH_RADIUS,"every retained shore point bounded");
                check(isUsableDryLandOrCoast(m.sample((int)p.x(),(int)p.z()),0),"every retained shore point dry mainland");
            }
        }
        var bank=new HighwayGeometry.Point(7403,4316);
        check(m.sample(7403,4316).surfaceClass()==SurfaceClass.COAST,"dry-coast island witness");
        check(landPath(List.of(bank,new HighwayGeometry.Point(7403,4380)),m,1),"dry coast island approach");
        check(islandRoad(bank,new int[]{0,1},m,m.islands().get(0))!=null,"islandRoad accepts dry coast start");
        check(landPath(List.of(new HighwayGeometry.Point(7403,3529),new HighwayGeometry.Point(7403,3593)),m,0),"dry coast mainland approach");
        check(m.sample(7403,3593).surfaceClass()==SurfaceClass.COAST,"dry-coast mainland witness");
        check(!waterLine(new HighwayGeometry.Point(30000,30000),new HighwayGeometry.Point(30000,30600),m.crossingCandidates().get(0),m),"open ocean crossing rejected");
        var source=m.crossingCandidates().get(0);
        var fake=new MacroGeography.CrossingCandidate(source.waterbodyId()+1000,0,1,source.fromX(),source.fromZ(),source.toX(),source.toZ(),source.waterSpan());
        var failed=plan(HighwayRouteGraph.buildTrunks(0),m,m.islands(),List.of(fake));
        check(failed.connections().isEmpty(),"wrong-water candidate never published");
        var failureText=HighwayNetworkExport.report(HighwayRouteGraph.publish(HighwayRouteGraph.buildTrunks(0),failed),m);
        check(failureText.contains("failureReason = INVALID_WATER_CROSSING")&&failureText.contains("shorePointsMainland=")
                &&failureText.contains("shorePointsSatellite=")&&failureText.contains("axialPairs=")
                &&failureText.contains("firstFailureReason=INVALID_WATER_CROSSING"),"real failure export stage and counters");
        for(var r:Rejection.values()) {
            var stats=new SearchStats();stats.reject(r);check(stats.rejected.get(r)==1,"rejection histogram "+r);
        }
        var stages=new SearchStats();
        check(stages.failure()==Rejection.NO_SHORE_POINTS,"missing shore stage");
        stages.shorePointsMainland=stages.shorePointsSatellite=1;
        check(stages.failure()==Rejection.NO_AXIAL_BRIDGE_PAIR,"no common axis stage");
        stages.axialPairs=1; stages.reject(Rejection.ACTUAL_SPAN_TOO_LONG);
        check(stages.failure()==Rejection.ACTUAL_SPAN_TOO_LONG,"span stage");
        stages.reject(Rejection.INVALID_WATER_CROSSING);
        check(stages.failure()==Rejection.INVALID_WATER_CROSSING,"water stage");
        stages.viablePairs=1;check(stages.failure()==Rejection.NO_ISLAND_APPROACH,"island stage");
        stages.islandApproaches=1;check(stages.failure()==Rejection.NO_VALID_JUNCTION,"junction stage");
        stages.junctions=1;check(stages.failure()==Rejection.NO_MAINLAND_ROUTE,"mainland stage");
        stages.landRoutes=1;check(stages.failure()==Rejection.ROUTE_CONFLICT,"conflict stage");
        System.out.println("HighwayShorelineSearchTest PASS: "+checks+" checks; no world");
    }
    private static MacroGeographySample sample(SurfaceClass surface,int land,int waterbody,WaterClass water,double distance) {
        return new MacroGeographySample(surface,NationId.MAIN_NATION,1,land,LandmassRole.MAINLAND,waterbody,water,distance,64);
    }
    private static void check(boolean ok,String why) {checks++;if(!ok)throw new AssertionError(why);}
}
