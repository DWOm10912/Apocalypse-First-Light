package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.*;

/** Bounded derived-geometry cache; immutable spatial index, not another route planner. */
public final class HighwayRampModules {
    private static final Map<HighwayRouteGraph,HighwayRampModules> CACHE=new LinkedHashMap<>(16,.75f,true);
    private final Map<Long,List<HighwayRampGeometry.Module>> tiles;
    private HighwayRampModules(HighwayRouteGraph graph) {
        Map<Long,List<HighwayRampGeometry.Module>> map=new HashMap<>();
        for(var zone:graph.reservedZones()) {
            var m=HighwayRampGeometry.build(graph,zone);var b=m.queryBounds();
            for(int x=Math.floorDiv(b.minX(),128);x<=Math.floorDiv(b.maxXExclusive()-1,128);x++)
                for(int z=Math.floorDiv(b.minZ(),128);z<=Math.floorDiv(b.maxZExclusive()-1,128);z++)
                    map.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(m);
        }
        Map<Long,List<HighwayRampGeometry.Module>> frozen=new HashMap<>();map.forEach((k,v)->frozen.put(k,List.copyOf(v)));
        tiles=Map.copyOf(frozen);
    }
    public static synchronized HighwayRampModules forGraph(HighwayRouteGraph graph) {
        var result=CACHE.get(graph);
        if(result==null){result=new HighwayRampModules(graph);CACHE.put(graph,result);if(CACHE.size()>16)CACHE.remove(CACHE.keySet().iterator().next());}
        return result;
    }
    public List<HighwayRampGeometry.Module> query(BoundsXZ area) {
        Set<HighwayRampGeometry.Module> result=new LinkedHashSet<>();
        if(area.isEmpty())return List.of();
        for(int x=Math.floorDiv(area.minX(),128);x<=Math.floorDiv(area.maxXExclusive()-1,128);x++)
            for(int z=Math.floorDiv(area.minZ(),128);z<=Math.floorDiv(area.maxZExclusive()-1,128);z++)
                for(var m:tiles.getOrDefault(key(x,z),List.of()))if(m.queryBounds().intersects(area))result.add(m);
        return List.copyOf(result);
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
