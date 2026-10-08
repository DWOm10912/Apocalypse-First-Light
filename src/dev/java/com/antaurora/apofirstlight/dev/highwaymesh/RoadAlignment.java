package com.antaurora.apofirstlight.dev.highwaymesh;

import java.util.*;

/**
 * Pure, immutable global alignment. No chunk, world, random or graph mutation.
 * Quintic corner patches have zero endpoint curvature; station is numerically integrated arc length.
 */
public final class RoadAlignment {
    public record Point(double x,double z) {
        public Point {if(!Double.isFinite(x)||!Double.isFinite(z))throw new IllegalArgumentException("NON_FINITE_POINT");}
        Point add(Point b){return new Point(x+b.x,z+b.z);}
        Point sub(Point b){return new Point(x-b.x,z-b.z);}
        Point mul(double s){return new Point(x*s,z*s);}
        double length(){return Math.hypot(x,z);}
        Point unit(){double l=length();if(l<1e-8)throw new IllegalArgumentException("ZERO_LEG");return mul(1/l);}
    }
    public record Frame(double station,Point point,Point tangent,double curvature) {}
    /** Convex source corridor. Checking every Bezier control proves hull + lateral margin containment. */
    public record Corridor(Point a,Point b,double halfWidth) {
        boolean contains(Point p,double margin) {
            Point d=b.sub(a).unit(),v=p.sub(a);double s=v.x*d.x+v.z*d.z;
            return halfWidth>=margin&&s>=-halfWidth+margin-1e-8&&s<=b.sub(a).length()+halfWidth-margin+1e-8
                    &&Math.abs(v.z*d.x-v.x*d.z)<=halfWidth-margin+1e-8;
        }
    }
    private static final class Span {
        final Point[] p;
        final double[] arc=new double[513];
        final double length;
        Span(Point... controls) {
            p=controls;
            for(int i=1;i<arc.length;i++)arc[i]=arc[i-1]+integral((i-1)/512.0,i/512.0);
            length=arc[512];if(length<1e-8)throw new IllegalArgumentException("ZERO_SPAN");
        }
        Point evaluate(Point[] controls,double t) {
            Point[] a=controls.clone();
            for(int n=a.length-1;n>0;n--)for(int i=0;i<n;i++)a[i]=a[i].mul(1-t).add(a[i+1].mul(t));
            return a[0];
        }
        Point[] derivative(Point[] controls) {
            Point[] d=new Point[controls.length-1];for(int i=0;i<d.length;i++)d[i]=controls[i+1].sub(controls[i]).mul(d.length);return d;
        }
        Point velocity(double t){return evaluate(derivative(p),t);}
        double integral(double a,double b){return (b-a)/6*(velocity(a).length()+4*velocity((a+b)/2).length()+velocity(b).length());}
        double parameter(double s) {
            s=Math.max(0,Math.min(length,s));int at=Arrays.binarySearch(arc,s);
            if(at>=0)return at/512.0;
            int i=Math.max(0,Math.min(511,-at-2));double a=i/512.0,lo=a,hi=(i+1)/512.0;
            double t=a+(s-arc[i])/(arc[i+1]-arc[i])/512;
            for(int n=0;n<7;n++){double error=arc[i]+integral(a,t)-s;if(Math.abs(error)<1e-10)break;if(error>0)hi=t;else lo=t;
                double next=t-error/velocity(t).length();t=next>lo&&next<hi?next:(lo+hi)/2;}
            return t;
        }
        Frame frame(double station,double local) {
            double t=parameter(local);Point v=velocity(t),a=p.length>2?evaluate(derivative(derivative(p)),t):new Point(0,0);
            double speed=v.length(),k=(v.x*a.z-v.z*a.x)/(speed*speed*speed);
            return new Frame(station,evaluate(p,t),v.unit(),k);
        }
    }
    private final List<Span> spans=new ArrayList<>();
    private final double[] starts;
    public final double length;
    public final List<Point> controls;
    public final double maxCurvature;
    public RoadAlignment(List<Point> points,List<Corridor> allowed,double margin,double trim,double minimumRadius) {
        controls=List.copyOf(points);
        if(points.size()<2||points.size()>128||!(margin>0)||!(trim>0)||!(minimumRadius>0))throw new IllegalArgumentException("ALIGNMENT_PARAMETERS");
        Point cursor=points.get(0);
        for(int i=1;i<points.size()-1;i++){
            Point corner=points.get(i),in=corner.sub(points.get(i-1)).unit(),out=points.get(i+1).sub(corner).unit();
            double dot=in.x*out.x+in.z*out.z;
            if(dot<-.001)throw new IllegalArgumentException("RESTRICTED: reversing or >90 degree corner");
            if(dot>1-1e-10)continue;
            double cut=Math.min(trim,Math.min(corner.sub(points.get(i-1)).length(),points.get(i+1).sub(corner).length())*.4);
            Point a=corner.sub(in.mul(cut)),b=corner.add(out.mul(cut));double d=cut*.4;
            add(new Span(cursor,a),allowed,margin);
            add(new Span(a,a.add(in.mul(d)),a.add(in.mul(2*d)),b.sub(out.mul(2*d)),b.sub(out.mul(d)),b),allowed,margin);
            cursor=b;
        }
        add(new Span(cursor,points.get(points.size()-1)),allowed,margin);
        starts=new double[spans.size()+1];double max=0;
        for(int i=0;i<spans.size();i++){Span span=spans.get(i);starts[i+1]=starts[i]+span.length;
            for(int j=0;j<=512;j++){Frame f=span.frame(0,span.arc[j]);max=Math.max(max,Math.abs(f.curvature));}}
        length=starts[spans.size()];maxCurvature=max;
        if(max>1/minimumRadius+1e-10)throw new IllegalArgumentException("RESTRICTED: radius "+(1/max)+" < "+minimumRadius);
        if(max*margin>=.65)throw new IllegalArgumentException("RESTRICTED: offset ribbon fold risk");
    }
    private void add(Span s,List<Corridor> allowed,double margin){
        if(allowed.stream().noneMatch(c->Arrays.stream(s.p).allMatch(p->c.contains(p,margin))))
            throw new IllegalArgumentException("RESTRICTED: curve hull + protection envelope exceeds verified source corridor");
        spans.add(s);
    }
    public Frame frame(double s) {
        if(!Double.isFinite(s)||s< -1e-8||s>length+1e-8)throw new IllegalArgumentException("STATION_OUTSIDE_ALIGNMENT");
        s=Math.max(0,Math.min(length,s));int i=Arrays.binarySearch(starts,s);i=i>=0?Math.min(i,spans.size()-1):-i-2;
        return spans.get(i).frame(s,s-starts[i]);
    }
    public List<Double> joins(){return Arrays.stream(starts).boxed().toList();}
}