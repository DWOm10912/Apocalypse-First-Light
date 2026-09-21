package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.*;

/** Planning-only axis chain. Never submitted to the polyline renderer. */
public record OrthogonalHighwayPath(List<HighwayGeometry.Point> points) {
    public static final int RESERVED_LENGTH = 64;
    public static final int RESERVED_HALF = RESERVED_LENGTH / 2;
    public static final int MIN_TURN_SPACING = 128;
    public OrthogonalHighwayPath {
        points = List.copyOf(points);
        if(points.size()<2 || points.size()>4) throw new IllegalArgumentException("0..2 turns required");
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var b=points.get(i);
            if((a.x()==b.x())==(a.z()==b.z())) throw new IllegalArgumentException("Nonzero axis leg required");
        }
    }
    public double length() {
        double s=0;for(int i=1;i<points.size();i++) s+=distance(points.get(i-1),points.get(i));return s;
    }
    public HighwayGeometry.Point point(double station) {
        double remaining=Math.max(0,Math.min(length(),station));
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var b=points.get(i);double len=distance(a,b);
            if(remaining<=len) return new HighwayGeometry.Point(a.x()+(b.x()-a.x())*remaining/len,a.z()+(b.z()-a.z())*remaining/len);
            remaining-=len;
        }
        return points.get(points.size()-1);
    }
    public static double distance(HighwayGeometry.Point a,HighwayGeometry.Point b) { return Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z()); }
    public static BoundsXZ zone(HighwayGeometry.Point p) { return new BoundsXZ((int)p.x()-RESERVED_HALF,(int)p.z()-RESERVED_HALF,(int)p.x()+RESERVED_HALF+1,(int)p.z()+RESERVED_HALF+1); }
    /** Reserve turn/junction centers; align finite endpoints to the existing global 8-block engineering phase. */
    public List<BoundsXZ> edgeBounds(boolean junction,int width) {
        List<BoundsXZ> out=new ArrayList<>();
        for(int i=0;i<points.size()-1;i++) {
            var a=points.get(i);var b=points.get(i+1);boolean ns=a.x()==b.x();
            int from=(int)(ns?a.z():a.x()),to=(int)(ns?b.z():b.x()),sign=Integer.signum(to-from);
            if(i>0||junction)from+=sign*(RESERVED_HALF+1);
            if(i<points.size()-2)to-=sign*(RESERVED_HALF+1);
            int lo=(int)(-Math.floorDiv(-(long)Math.min(from,to),8)*8),hi=Math.floorDiv(Math.max(from,to),8)*8;
            if(hi<=lo)throw new IllegalArgumentException("Reserved zones consume edge");
            int fixed=(int)(ns?a.x():a.z());
            out.add(ns?new BoundsXZ(fixed-width,lo,fixed+width+1,hi+1):new BoundsXZ(lo,fixed-width,hi+1,fixed+width+1));
        }
        return List.copyOf(out);
    }
    public List<BoundsXZ> reservations(boolean junction) {
        List<BoundsXZ> out=new ArrayList<>();
        if(junction)out.add(zone(points.get(0)));
        for(int i=1;i<points.size()-1;i++)out.add(zone(points.get(i)));
        return List.copyOf(out);
    }
    public BoundsXZ bounds(int width) {
        int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
        for(var p:points) {minX=Math.min(minX,(int)p.x());minZ=Math.min(minZ,(int)p.z());maxX=Math.max(maxX,(int)p.x());maxZ=Math.max(maxZ,(int)p.z());}
        return new BoundsXZ(minX-width,minZ-width,maxX+width+1,maxZ+width+1);
    }
    public static List<List<HighwayGeometry.Point>> candidates(HighwayGeometry.Point a,HighwayGeometry.Point b,int dx,int dz) {
        List<List<HighwayGeometry.Point>> raw=new ArrayList<>();
        raw.add(List.of(a,b));
        raw.add(List.of(a,new HighwayGeometry.Point(a.x(),b.z()),b));
        raw.add(List.of(a,new HighwayGeometry.Point(b.x(),a.z()),b));
        double[] mids=dx!=0?new double[]{b.x()-dx*112,Math.rint((a.x()+b.x())/16)*8,a.x()+dx*128}
                :new double[]{b.z()-dz*112,Math.rint((a.z()+b.z())/16)*8,a.z()+dz*128};
        for(double mid:mids)raw.add(dx!=0?List.of(a,new HighwayGeometry.Point(mid,a.z()),new HighwayGeometry.Point(mid,b.z()),b)
                :List.of(a,new HighwayGeometry.Point(a.x(),mid),new HighwayGeometry.Point(b.x(),mid),b));
        List<List<HighwayGeometry.Point>> result=new ArrayList<>();
        for(var path:raw) {
            boolean valid=true;
            for(int i=1;i<path.size();i++) {
                var u=path.get(i-1);var v=path.get(i);
                if((u.x()==v.x())==(u.z()==v.z()) || distance(u,v)<(i==path.size()-1?112:i==1?80:MIN_TURN_SPACING)) {valid=false;break;}
                if(i>1) {var prev=path.get(i-2);if((prev.x()==u.x())==(u.x()==v.x())){valid=false;break;}}
            }
            var prev=path.get(path.size()-2);
            if(valid && Math.signum(b.x()-prev.x())==dx && Math.signum(b.z()-prev.z())==dz && !result.contains(path))result.add(path);
        }
        return List.copyOf(result);
    }
}
