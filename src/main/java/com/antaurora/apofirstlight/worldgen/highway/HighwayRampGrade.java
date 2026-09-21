package com.antaurora.apofirstlight.worldgen.highway;

/** Endpoint-pinned local engineering plane. Parent profile is immutable and never resampled from placed blocks. */
public record HighwayRampGrade(HighwayRampGeometry.Module module, HighwayProfile parentProfile,
                               int startY, int endY) {
    public int at(double station, int x, int z) {
        if (module.type() == HighwayRampGeometry.Type.TURN_RAMP)
            return interpolate(startY, endY, station / module.ribbon().length());
        var parent = module.incoming().edge();
        double parentStation = parent.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH ? z : x;
        int base = parentProfile.sampleAt(parentProfile.plan().localDistance(parentStation)).roadY();
        double lateral = Math.abs(parent.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH
                ? x-parent.fixedCoordinate() : z-parent.fixedCoordinate());
        double exit = Math.abs(parent.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH
                ? module.outgoing().x()-parent.fixedCoordinate() : module.outgoing().z()-parent.fixedCoordinate());
        // Preserve the complete parent carriageway/edge plane before blending out to the branch.
        return interpolate(base,endY,(lateral-14)/(exit-14));
    }
    private static int interpolate(int a,int b,double t) {
        t=Math.max(0,Math.min(1,t));
        return (int)Math.round(a+(b-a)*t*t*(3-2*t));
    }
    /** Conservative existing debug-profile policy: <=1 block per 8 station units. */
    public boolean feasible() {
        double available = module.type()==HighwayRampGeometry.Type.TURN_RAMP ? module.ribbon().length()
                : Math.abs(module.outgoing().x()-module.zone().node().x())
                +Math.abs(module.outgoing().z()-module.zone().node().z())-14;
        if (1.5*Math.abs(endY-startY) > available/HighwayProfile.SAMPLE_SPACING) return false;
        // Parent grade can vary across the merge even when the two endpoint heights match.
        for(double s=0;s<module.ribbon().length();s+=.5) {
            double next=Math.min(module.ribbon().length(),s+HighwayProfile.SAMPLE_SPACING);
            var a=module.ribbon().point(s);var b=module.ribbon().point(next);
            if(Math.abs(at(s,(int)Math.round(a.x()),(int)Math.round(a.z()))
                    -at(next,(int)Math.round(b.x()),(int)Math.round(b.z())))>1)return false;
        }
        for(var c:module.ribbon().raster(module.ribbon().bounds(14.5),14.5)) {
            int y=at(c.sample().station(),c.x(),c.z());
            for(int[] d:new int[][]{{1,0},{0,1}}) {
                var n=module.ribbon().query(c.x()+d[0],c.z()+d[1],14.5);
                if(n!=null && Math.abs(at(n.station(),c.x()+d[0],c.z()+d[1])-y)>1)return false;
            }
        }
        return true;
    }
}
