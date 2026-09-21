package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import java.util.*;
import static com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph.*;

/** Real Macro topology, no Minecraft bootstrap or world. */
public final class HighwaySatelliteRoutingTest {
    private static int checks;
    public static void main(String[] args) {
        Set<String> parents=new HashSet<>();
        for(long seed:new long[]{0,2,42,-645704099691625981L,-4332662446239654818L}) {
            var macro=MacroGeography.forSeed(seed);var graph=HighwayRouteGraph.build(seed);
            var base=HighwayRouteGraph.buildTrunks(seed);
            check(graph.getNationalTrunks().equals(base.getNationalTrunks()),"Phase1 exact trunks");
            check(graph.intersection().equals(base.intersection()),"Phase1 exact intersection");
            check(graph.seaCrossings().size()+graph.routingDiagnostics().size()==macro.islands().size(),"each island has explicit result");
            check(graph.edges().stream().allMatch(e->e.geometry()==null),"live axial edges only");
            var islands=new ArrayList<>(macro.islands());Collections.reverse(islands);
            var candidates=new ArrayList<>(macro.crossingCandidates());Collections.reverse(candidates);
            var reverse=SatelliteHighwayRouting.plan(base,macro,islands,candidates);
            check(signature(graph.seaCrossings()).equals(signature(reverse.connections())),"insertion-order independence");
            for(var c:graph.seaCrossings()) {
                parents.add(c.parent().parentRouteId());
                check(macro.sample(c.mainland().x(),c.mainland().z()).landmassId()==0,"mainland identity");
                check(macro.sample(c.satellite().x(),c.satellite().z()).landmassId()==c.islandId(),"island identity");
                int dx=c.satellite().x()-c.mainland().x(),dz=c.satellite().z()-c.mainland().z();
                check((dx==0)!=(dz==0),"cardinal bridge axis");
                check(c.span()>=300&&c.span()<=SatelliteHighwayRouting.MAX_ACTUAL_BANK_SPAN,"actual bank span bounded");
                check(Math.hypot(c.mainland().x()-c.source().fromX(),c.mainland().z()-c.source().fromZ())<=96,"bounded mainland adjustment");
                check(Math.hypot(c.satellite().x()-c.source().toX(),c.satellite().z()-c.source().toZ())<=96,"bounded island adjustment");
                double n=Math.hypot(c.dx(),c.dz());
                var g=c.mainlandGeometry();var endpoint=g.point(g.length());var approach=g.point(g.length()-64);
                check(Math.abs(endpoint.x()-approach.x()-64*c.dx()/n)<1e-6
                        &&Math.abs(endpoint.z()-approach.z()-64*c.dz()/n)<1e-6,"straight coastal approach");
                var parent=base.getEdgeById(c.parent().parentEdgeId()).orElseThrow();int s=c.parent().parentStation();
                check(s>=parent.startStation()+192 && s<=parent.endStation()-192,"endpoint spacing");
                check(Math.abs(s-parent.globalStation(base.intersection().x(),base.intersection().z()))>=192,"intersection spacing");
                for(var other:graph.seaCrossings())if(c.islandId()!=other.islandId()&&parent.id().equals(other.parent().parentEdgeId()))
                    check(Math.abs(s-other.parent().parentStation())>=192,"junction conflict exclusion");
                check(graph.getRouteById(c.routeId()).orElseThrow().edges().size()==g.points().size()+c.islandGeometry().points().size()-2,"multi-edge family");
                var islandEnd=c.islandGeometry().point(c.islandGeometry().length());
                check(macro.sample((int)Math.round(islandEnd.x()),(int)Math.round(islandEnd.z())).landmassId()==c.islandId(),"island endpoint");
                check(c.islandGeometry().length()>=128,"substantial island road");
                check(graph.query(g.bounds(0),20).stream().anyMatch(e->e.id().startsWith(c.routeId()+"/mainland_")),"live query sees branch");
            }
            System.out.println("seed="+seed+" islands="+macro.islands().size()+" connected="+graph.seaCrossings().stream().map(c->c.islandId()+":"+c.span()).toList()+" turns="+graph.turns().size()+" unconnected="+graph.routingDiagnostics());
        }
        check(!parents.isEmpty(),"representative seeds publish routes");
        var macro=MacroGeography.forSeed(2);var candidates=new ArrayList<>(macro.crossingCandidates());
        var c=HighwayRouteGraph.build(2).seaCrossings().get(0).source();
        candidates.add(new MacroGeography.CrossingCandidate(c.waterbodyId()+1000,0,c.toLandmassId(),c.fromX(),c.fromZ(),c.toX(),c.toZ(),c.waterSpan()));
        var first=SatelliteHighwayRouting.plan(buildTrunks(2),macro,macro.islands(),candidates);
        Collections.reverse(candidates);
        var second=SatelliteHighwayRouting.plan(buildTrunks(2),macro,macro.islands(),candidates);
        check(signature(first.connections()).equals(signature(second.connections())),"multiple candidates stable tie");
        check(!first.connections().isEmpty()&&first.connections().get(0).source().waterbodyId()==c.waterbodyId(),"semantic candidate tie-break (connected fixture)");
        var fixture=buildTrunks(42);var parent=fixture.edge(Orientation.EAST_WEST);
        var available=SatelliteHighwayRouting.junctionStations(fixture,parent,List.of());
        int near=available.stream().min(Comparator.comparingInt(s->Math.abs(s-fixture.intersection().x()))).orElseThrow();
        check(Math.abs(near-fixture.intersection().x())>=192,"Case F: closest legal alternative excludes intersection");
        int preferred=available.get(available.size()/3);
        var occupied=new ParentAttachment(parent.routeId(),parent.id(),preferred,"synthetic-junction");
        var alternatives=SatelliteHighwayRouting.junctionStations(fixture,parent,List.of(occupied));
        int alternative=alternatives.stream().min(Comparator.comparingInt(s->Math.abs(s-preferred))).orElseThrow();
        check(!alternatives.contains(preferred)&&Math.abs(alternative-preferred)>=192,"Case G: conflicting optimum has legal alternative");
        check(alternatives.equals(SatelliteHighwayRouting.junctionStations(fixture,parent,List.of(occupied))),"conflict reconstruction stable");
        System.out.println("HighwaySatelliteRoutingTest PASS: "+checks+" checks; no world");
    }
    private static String signature(List<SatelliteHighwayRouting.Connection> cs) {
        return cs.stream().map(c->c.id()+c.source()+c.parent()+c.mainland()+c.satellite()+c.mainlandGeometry().points()+c.islandGeometry().points()).toList().toString();
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
}
