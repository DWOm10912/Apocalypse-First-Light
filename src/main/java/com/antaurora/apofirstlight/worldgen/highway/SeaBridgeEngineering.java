package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Small adapter into the existing axis viaduct; contains no world/chunk references. */
public record SeaBridgeEngineering(SeaBridgeGeometry geometry, HighwayProfile profile,
        HighwayCorridor corridor, List<Support> abutments, int pierCount, int foundationFailures,
        String status, BridgePylonGeometry landmark, BridgeCableGeometry cables) {
    public record Support(int x,int z,int bottom,int top,boolean mainland,boolean found) {}
    public boolean ready() { return corridor!=null && !corridor.cells().isEmpty(); }

    public static SeaBridgeEngineering build(WorldGenLevel level,HighwayRouteGraph graph,
            SeaBridgeGeometry geometry,HighwayTerrainSampler terrain,NaturalHighwayCacheManager.WorldCache cache) {
        var c=geometry.crossing();
        int mainlandY=endpointY(level,graph,c.mainland(),terrain,cache);
        int islandY=endpointY(level,graph,c.satellite(),terrain,cache);
        int a=geometry.mainlandFirst()?mainlandY:islandY,b=geometry.mainlandFirst()?islandY:mainlandY;
        // Match the established maximum highway engineering slope (one block per eight).
        if(Math.abs(b-a)>geometry.plan().length()/8.0)
            return new SeaBridgeEngineering(geometry,null,null,List.of(),0,0,"GRADE_INFEASIBLE",
                    BridgePylonGeometry.disabled(LandmarkMainSpan.of(geometry),"GRADE_INFEASIBLE","NOT_SAMPLED"),BridgeCableGeometry.empty());
        return plan(level,geometry,a,b,terrain::pierFoundation);
    }

    @FunctionalInterface
    interface FoundationSampler { HighwayTerrainSampler.PierFoundation sample(int x,int z,int deckBottom); }

    /** Production planning seam also used by the one-crossing bounded contract fixture. */
    static SeaBridgeEngineering plan(WorldGenLevel level,SeaBridgeGeometry geometry,int startY,int endY,
                                     FoundationSampler sampler) {
        var plan=geometry.plan();
        var anchors=HighwayProfile.seaBridge(plan,startY,endY,java.util.Map.of());
        var landmark=BridgePylonGeometry.plan(geometry,anchors,sampler,
                level==null?-64:level.getMinBuildHeight(),level==null?320:level.getMaxBuildHeight());
        var foundations=new HashMap<Long,HighwayTerrainSampler.PierFoundation>();
        int failures=0,piers=0;
        for(long station:geometry.pierStations()) {
            if(landmark.enabled() && landmark.span().suppresses(plan,station))continue;
            double local=plan.localDistance(station);var p=plan.sample(local);
            var f=sampler.sample((int)Math.round(p.x()),(int)Math.round(p.z()),anchors.sampleAt(local).roadY()-3);
            foundations.put(station,f);
            if(f.found())piers++;else failures++;
        }
        List<Support> supports=new ArrayList<>();
        var tangent=plan.tangent(0);
        for(int end=0;end<2;end++)for(int depth=0;depth<geometry.abutmentLength(end==0);depth++) {
            double local=end==0?depth:plan.length()-depth;
            var p=plan.sample(local);int y=anchors.sampleAt(local).roadY();
            for(int lateral=-12;lateral<=12;lateral++) {
                int x=(int)Math.round(p.x()-tangent.z()*lateral),z=(int)Math.round(p.z()+tangent.x()*lateral);
                var f=sampler.sample(x,z,y-3);
                supports.add(new Support(x,z,f.y(),y-4,(end==0)==geometry.mainlandFirst(),f.found()));
                if(!f.found())failures++;
            }
        }
        var profile=HighwayProfile.seaBridge(plan,startY,endY,foundations);
        var corridor=HighwayCorridor.buildNatural(level,plan,profile,geometry.bounds());
        return new SeaBridgeEngineering(geometry,profile,corridor,List.copyOf(supports),piers,failures,
                failures==0?"READY":"PARTIAL_FOUNDATION_FAILED",landmark,BridgeCableGeometry.plan(geometry,profile,landmark));
    }

    private static int endpointY(WorldGenLevel level,HighwayRouteGraph graph,HighwayRouteGraph.Node node,
            HighwayTerrainSampler terrain,NaturalHighwayCacheManager.WorldCache cache) {
        var edge=graph.edges().stream().filter(e->e.startNode().equals(node)||e.endNode().equals(node))
                .findFirst().orElseThrow(()->new IllegalStateException("NO_BRIDGEHEAD_ROAD: "+node.id()));
        var segment=NaturalHighwayGenerationAdapter.segmentForChunk(new ChunkPos(node.x()>>4,node.z()>>4),
                graph,edge,terrain,cache,level);
        double local=segment.plan().localDistance(edge.clampStation(edge.globalStation(node.x(),node.z())));
        return segment.profile().roadYAt(local,node.x(),node.z());
    }

    public String abutmentStatus(boolean mainland) {
        if(!ready())return "NOT_BUILT";
        return abutments.stream().filter(s->s.mainland()==mainland).allMatch(Support::found)?"READY":"FOUNDATION_FAILED";
    }

    public void render(WorldGenLevel level,HighwayBlockWriter chunkWriter) {
        if(!ready())return;
        var writer=new FiniteRouteHighwayWriter(chunkWriter,geometry.bounds());
        // Includes the existing core road headroom clearance at the tunnel/bridge interface.
        HighwayRenderer.renderNatural(level,profile,corridor,deckWriter(writer));
        for(var support:abutments) {
            if(!support.found())continue;
            if(!writer.mayAffectHorizontal(support.x(),support.z(),0))continue;
            for(int y=support.bottom();y<=support.top();y++)
                writer.set(new BlockPos(support.x(),y,support.z()),HighwayPalette.REINFORCED_CONCRETE);
        }
        landmark.render(writer);
        cables.render(writer);
    }

    HighwayBlockWriter deckWriter(HighwayBlockWriter writer) {
        return landmark.enabled()?PylonZoneGeometry.deckWriter(writer,geometry.plan(),profile,landmark.span()):writer;
    }

    public List<Long> suppressedPierStations() {
        return geometry.pierStations().stream().filter(s->landmark.enabled()
                && landmark.span().suppresses(geometry.plan(),s)).toList();
    }

    public List<Long> retainedPierStations() {
        return geometry.pierStations().stream().filter(s->!landmark.enabled()
                || !landmark.span().suppresses(geometry.plan(),s)).toList();
    }

    public String landmarkDescription() {
        return landmark.description(geometry.plan())+" suppressedPierStations="+suppressedPierStations()
                +" retainedPierStations="+retainedPierStations()+cables.description();
    }
}
