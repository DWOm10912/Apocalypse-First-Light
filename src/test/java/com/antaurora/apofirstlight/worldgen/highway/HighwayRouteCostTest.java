package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.dev.HighwayNetworkExport;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import java.util.*;
import static com.antaurora.apofirstlight.worldgen.highway.SatelliteHighwayRouting.*;

/** Full-plan scoring and real graph quality; no world, chunks, or timing benchmark. */
public final class HighwayRouteCostTest {
    private static int checks;
    private static RouteCost score(double length,int turns,double extra,double span,double displacement) {
        return new RouteCost(length+TURN_COST_BLOCKS*turns,extra,turns,span,displacement,512);
    }
    public static void main(String[] args) {
        check(score(2215,1,0,642,87).compareTo(score(7861,0,0,642,87))<0,"short one-turn beats long straight");
        check(score(1020,0,0,650,90).compareTo(score(1000,1,0,650,90))<0,"near lengths prefer fewer turns within 64-block equivalent cost");
        check(score(1000,0,0,650,90).compareTo(score(936,1,0,650,90))<0,"equal network cost then fewer turns");
        check(score(1000,1,0,600,90).compareTo(score(1000,1,0,650,80))<0,"equal road and turns: shorter span");
        check(score(1000,1,0,600,80).compareTo(score(1000,1,0,600,90))<0,"equal span: lower displacement");
        check(score(1000,1,0,600,80).compareTo(score(1000,1,100,600,80))<0,"equal cost: less extra distance");
        check(score(1000,1,0,790,900).compareTo(score(5000,0,0,310,1))<0,"bank preferences cannot outweigh thousands of road blocks");
        int total=0;
        for(long seed:new long[]{0,2,42,-645704099691625981L,-4332662446239654818L}) {
            var macro=MacroGeography.forSeed(seed); var trunks=HighwayRouteGraph.buildTrunks(seed);
            var graph=HighwayRouteGraph.build(seed); total+=graph.seaCrossings().size();
            check(graph.seaCrossings().size()==macro.islands().size(),"all islands connected "+seed);
            check(graph.getNationalTrunks().equals(trunks.getNationalTrunks()),"exactly original two trunks");
            var islands=new ArrayList<>(macro.islands());Collections.reverse(islands);
            var candidates=new ArrayList<>(macro.crossingCandidates());Collections.reverse(candidates);
            var reversed=plan(trunks,macro,islands,candidates);
            check(graph.seaCrossings().equals(reversed.connections()),"complete connections deterministic under reversed input "+seed);
            check(graph.routingDiagnostics().equals(reversed.diagnostics()),"diagnostics deterministic");
            var preceding=new ArrayList<Connection>();
            for(var c:graph.seaCrossings()) {
                var parent=trunks.getEdgeById(c.parent().parentEdgeId()).orElseThrow();
                check(junctionStations(trunks,parent,preceding.stream().map(Connection::parent).toList()).contains(c.parent().parentStation()),"selected junction legal");
                check(!conflicts(c.mainlandGeometry(),c.islandGeometry(),parent,trunks,preceding),"all selected branches conflict-free");
                check(c.span()<=800&&c.span()>=300,"shorter road cannot admit overlong bridge");
                check(c.turnCount()<=2,"no extra turn template");
                check(c.extraDistance()>=0,"Manhattan lower bound");
                check(c.networkCost()==c.mainlandRouteLength()+64*c.turnCount(),"documented cost formula");
                check(graph.edges().stream().allMatch(e->e.geometry()==null),"no diagonal fallback");
                preceding.add(c);
                System.out.println("QUALITY seed="+seed+" island="+c.islandId()+" parent="+c.parent().parentRouteId()
                        +" junction="+c.mainlandGeometry().points().get(0)+" turns="+c.turnCount()+" mainland="+c.mainlandRouteLength()
                        +" islandLength="+c.islandGeometry().length()+" span="+c.span()+" displacement="+c.combinedDisplacement()
                        +" extraDistance="+c.extraDistance()+" cost="+c.networkCost());
            }
            var text=HighwayNetworkExport.report(graph,macro);
            check(text.contains("selectedParentTrunk = ")&&text.contains("mainlandRouteLength = ")&&text.contains("turnCount = ")
                    &&text.contains("extraDistance = ")&&text.contains("networkCost = "),"quality export");
        }
        check(total==10,"10/10 retained");
        var macro=MacroGeography.forSeed(-4332662446239654818L);
        var graph=HighwayRouteGraph.build(-4332662446239654818L);var selected=graph.seaCrossings().get(0);
        check(selected.parent().parentRouteId().equals("national_trunk_b"),"control naturally picks B");
        check(selected.mainlandRouteLength()==2138&&selected.turnCount()==1,"control full-route optimum from probe");
        check(selected.span()==627,"control actual span");
        var stats=new SearchStats();var banks=rankedBanks(selected.source(),macro,1,stats);
        check(selected.combinedDisplacement()>banks.get(0).displacement(),"no early bank displacement lock");
        check(stats.rejected.getOrDefault(Rejection.ACTUAL_SPAN_TOO_LONG,0)>0,"overlong bridge candidates actually rejected before route scoring");
        var base=HighwayRouteGraph.buildTrunks(graph.seed());
        var parent=base.getEdgeById(selected.parent().parentEdgeId()).orElseThrow();
        int invalid=Math.toIntExact(parent.globalStation(base.intersection().x(),base.intersection().z()));
        check(!junctionStations(base,parent,List.of()).contains(invalid),"invalid intersection junction excluded regardless of shorter route");
        int endpoint=parent.startStation();
        var endpointStart=new HighwayGeometry.Point(parent.fixedCoordinate(),endpoint);
        var bankPoint=new HighwayGeometry.Point(selected.mainland().x(),selected.mainland().z());
        var shorterButInvalid=OrthogonalHighwayPath.candidates(endpointStart,bankPoint,selected.dx(),selected.dz());
        check(shorterButInvalid.stream().anyMatch(p->new OrthogonalHighwayPath(p).length()<selected.mainlandRouteLength()),
                "endpoint fixture really offers a shorter geometric route");
        check(!junctionStations(base,parent,List.of()).contains(endpoint),"shorter invalid endpoint junction cannot reach cost comparison");
        var alternateParent=new HighwayRouteGraph.ParentAttachment(selected.parent().parentRouteId(),selected.parent().parentEdgeId(),
                selected.parent().parentStation()+32,selected.parent().junctionNodeId()+"/tie");
        var tie=new Connection(selected.id(),1,selected.source(),selected.routeId(),alternateParent,selected.mainland(),selected.satellite(),
                selected.dx(),selected.dz(),selected.span(),selected.mainlandGeometry(),selected.islandGeometry());
        check(compareConnections(selected,tie)<0&&compareConnections(tie,selected)>0,"stable parent-station tie-break");
        check(compareConnections(selected,selected)==0,"comparator identity");
        System.out.println("HighwayRouteCostTest PASS: "+checks+" checks; no world");
    }
    private static void check(boolean ok,String why) {checks++;if(!ok)throw new AssertionError(why);}
}
