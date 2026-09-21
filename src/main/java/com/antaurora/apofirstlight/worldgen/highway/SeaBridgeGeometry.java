package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.ArrayList;
import java.util.List;

/** Derived consumer of a frozen crossing; never adds or moves graph nodes/edges. */
public record SeaBridgeGeometry(SatelliteHighwayRouting.Connection crossing, HighwayPlan plan,
                                BoundsXZ bounds, boolean mainlandFirst) {
    public static final int HALF_ENVELOPE = 14;
    public static final int ABUTMENT_LENGTH = 4;

    public static SeaBridgeGeometry of(SatelliteHighwayRouting.Connection c) {
        var a=c.mainland(); var b=c.satellite();
        if (Math.abs(c.dx())+Math.abs(c.dz())!=1 || (a.x()!=b.x() && a.z()!=b.z())
                || (b.x()-a.x())*c.dx()+(b.z()-a.z())*c.dz()!=c.span())
            throw new IllegalArgumentException("SEA_BRIDGE_AXIS_UNSUPPORTED: "+c.id());
        boolean ns=c.dx()==0;
        int bankFrom=ns?Math.min(a.z(),b.z()):Math.min(a.x(),b.x());
        int bankTo=ns?Math.max(a.z(),b.z()):Math.max(a.x(),b.x());
        // Orthogonal roads clip their shore endpoints inward to the global eight-grid.
        // Consume that <=7-block gap as abutment, without moving either bridgehead.
        int from=Math.floorDiv(bankFrom,8)*8;
        int to=-Math.floorDiv(-bankTo,8)*8;
        if(to<=from)throw new IllegalArgumentException("Empty sea crossing");
        var start=new HighwayPlan.Point(ns?a.x():from,ns?from:a.z());
        var end=new HighwayPlan.Point(ns?a.x():to,ns?to:a.z());
        var bounds=ns?new BoundsXZ(a.x()-HALF_ENVELOPE,from,a.x()+HALF_ENVELOPE+1,to+1)
                :new BoundsXZ(from,a.z()-HALF_ENVELOPE,to+1,a.z()+HALF_ENVELOPE+1);
        return new SeaBridgeGeometry(c,HighwayPlan.linear(start,end,HighwayPlan.MAIN_WIDTH,from),bounds,
                (ns?a.z():a.x())==bankFrom);
    }

    public int abutmentLength(boolean first) {
        var node=first==mainlandFirst?crossing.mainland():crossing.satellite();
        double bank=plan.tangent(0).z()!=0?node.z():node.x();
        return ABUTMENT_LENGTH+(int)Math.abs(bank-plan.globalStation(first?0:plan.length()));
    }

    /** Same global 32-grid and eight-block end exclusion as the axis viaduct writer. */
    public List<Long> pierStations() {
        List<Long> stations=new ArrayList<>();
        long first=(long)Math.ceil((plan.globalStation(0)+abutmentLength(true)+4)/HighwayPierGeometry.SPACING)*HighwayPierGeometry.SPACING;
        for(long s=first;s<=plan.globalStation(plan.length())-abutmentLength(false)-4;s+=HighwayPierGeometry.SPACING)stations.add(s);
        return List.copyOf(stations);
    }

    public static List<SeaBridgeGeometry> query(HighwayRouteGraph graph,BoundsXZ area) {
        return graph.seaCrossings().stream().map(SeaBridgeGeometry::of).filter(b->b.bounds.intersects(area)).toList();
    }
}
