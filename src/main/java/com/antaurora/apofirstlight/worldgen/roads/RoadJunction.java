package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Junction planning partitions, not curb meshes. Only connected arms reach the road/node boundaries. */
public record RoadJunction(int armExtent, List<BoundsXZ> footprint, List<BoundsXZ> asphalt,
                           List<BoundsXZ> curbs, List<BoundsXZ> utilities, List<BoundsXZ> sidewalks) {
    public static final int ARM_TRANSITION = 8;
    public RoadJunction {
        footprint = List.copyOf(footprint); asphalt = List.copyOf(asphalt); curbs = List.copyOf(curbs);
        utilities = List.copyOf(utilities); sidewalks = List.copyOf(sidewalks);
    }

    public static RoadJunction of(int x,int z,List<RoadPlan.Arm> arms) {
        if (arms.isEmpty() || arms.size()>4 || arms.stream().anyMatch(a->!a.direction().getAxis().isHorizontal())
                || arms.stream().map(RoadPlan.Arm::direction).distinct().count()!=arms.size())
            throw new IllegalArgumentException("Invalid junction arms");
        int extent=arms.stream().mapToInt(a->a.type().rightOfWayWidth()/2).max().orElseThrow()+ARM_TRANSITION;
        List<List<BoundsXZ>> envelopes=new ArrayList<>();
        for(int layer=0;layer<4;layer++) {
            final int current=layer;
            int half=arms.stream().mapToInt(a->half(a.type(),current)).max().orElseThrow();
            List<BoundsXZ> pieces=new ArrayList<>();
            pieces.add(new BoundsXZ(x-half,z-half,x+half,z+half));
            for(var arm:arms) {
                int h=half(arm.type(),layer);
                pieces.add(switch(arm.direction()) {
                    case NORTH -> new BoundsXZ(x-h,z-extent,x+h,z);
                    case SOUTH -> new BoundsXZ(x-h,z,x+h,z+extent);
                    case WEST -> new BoundsXZ(x-extent,z-h,x,z+h);
                    case EAST -> new BoundsXZ(x,z-h,x+extent,z+h);
                    default -> throw new IllegalArgumentException("Vertical junction arm");
                });
            }
            envelopes.add(pieces);
        }
        // Classify a finite rectangle arrangement by the innermost envelope. This is an exact partition,
        // including inside corners and mixed-width throats, without material overlaps or unreserved holes.
        TreeSet<Integer> xCuts=new TreeSet<>(),zCuts=new TreeSet<>();
        for(var layer:envelopes) for(var b:layer) {
            xCuts.add(b.minX()); xCuts.add(b.maxXExclusive());
            zCuts.add(b.minZ()); zCuts.add(b.maxZExclusive());
        }
        List<Integer> xs=List.copyOf(xCuts),zs=List.copyOf(zCuts);
        List<List<BoundsXZ>> regions=new ArrayList<>();
        for(int i=0;i<4;i++)regions.add(new ArrayList<>());
        for(int i=1;i<xs.size();i++) {
            int x0=xs.get(i-1),x1=xs.get(i),run=-1,start=0,end=0;
            for(int j=1;j<zs.size();j++) {
                int z0=zs.get(j-1),z1=zs.get(j),kind=-1;
                for(int k=0;k<4;k++) if(envelopes.get(k).stream().anyMatch(b->b.contains(x0,z0))) { kind=k; break; }
                if(kind!=run) {
                    if(run>=0)regions.get(run).add(new BoundsXZ(x0,start,x1,end));
                    run=kind; start=z0;
                }
                end=z1;
            }
            if(run>=0)regions.get(run).add(new BoundsXZ(x0,start,x1,end));
        }
        return new RoadJunction(extent,envelopes.get(3),regions.get(0),regions.get(1),regions.get(2),regions.get(3));
    }

    private static int half(RoadType type,int layer) {
        int h=type.asphaltWidth()/2;
        if(layer>=1)h+=type.curbReservation();
        if(layer>=2)h+=type.utilityBand();
        if(layer>=3)h+=type.sidewalkWidth();
        return h;
    }
}
