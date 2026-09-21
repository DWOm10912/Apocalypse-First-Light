package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;

/** Same endpoint engineering as finite axial roads. Failure is explicit and never emits a partial ramp. */
public record HighwayRampEngineering(HighwayRampGeometry.Module module, HighwayRampGrade grade,
                                     HighwayProfile profile, HighwayCorridor corridor, String status) {
    public boolean ready(){return status.equals("READY");}
    public static HighwayRampEngineering build(WorldGenLevel level,HighwayRouteGraph graph,
            HighwayRampGeometry.Module module,HighwayTerrainSampler terrain,NaturalHighwayCacheManager.WorldCache cache) {
        var start=endpoint(level,graph,module.incoming(),terrain,cache);
        var end=endpoint(level,graph,module.outgoing(),terrain,cache);
        var a=start.profile().sampleAt(start.plan().localDistance(module.incoming().station()));
        var b=end.profile().sampleAt(end.plan().localDistance(module.outgoing().station()));
        var grade=new HighwayRampGrade(module,start.profile(),a.roadY(),b.roadY());
        if(!grade.feasible())return new HighwayRampEngineering(module,grade,null,null,"GRADE_INFEASIBLE");
        var profile=HighwayProfile.ramp(grade,terrain,a.mode()==HighwayTerrainMode.VIADUCT||b.mode()==HighwayTerrainMode.VIADUCT);
        var corridor=HighwayCorridor.buildNatural(level,profile.plan(),profile);
        return new HighwayRampEngineering(module,grade,profile,corridor,corridor.geometryDeferred()?"GEOMETRY_DEFERRED":"READY");
    }
    private static CorridorEngineeringSegment endpoint(WorldGenLevel level,HighwayRouteGraph graph,
            HighwayRampGeometry.Port p,HighwayTerrainSampler terrain,NaturalHighwayCacheManager.WorldCache cache) {
        return NaturalHighwayGenerationAdapter.segmentForChunk(new ChunkPos(p.x()>>4,p.z()>>4),graph,p.edge(),terrain,cache,level);
    }
}
