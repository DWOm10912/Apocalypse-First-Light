package com.antaurora.apofirstlight.dev.highwaymesh;

import java.util.*;
import static com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.*;

/** Shared ribbon, surface/normal/anchor query and triangles. Positive lateral = right in Minecraft XZ. */
public final class RouteRoadGeometry {
    public record Plan(int version,long seed,int graphVersion,String routeId,String edgeId,String startNode,String endNode,
                       double authorityStart,List<RoadAlignment.Point> controls,List<RoadAlignment.Corridor> corridors,
                       double start,double length,RoadSection from,RoadSection to,double transitionStart,double transitionLength,
                       double profileSpan,double grade,List<String> limitations) {
        public Plan {
            controls=List.copyOf(controls);corridors=List.copyOf(corridors);limitations=List.copyOf(limitations);
            if(version!=1||length<16||length>1024||start<0||profileSpan<512||profileSpan>2048||Math.abs(grade)>.05
                    ||transitionLength<128||from==null||to==null)throw new IllegalArgumentException("PLAN_LIMIT");
            for(double n:new double[]{authorityStart,start,length,transitionStart,transitionLength,profileSpan,grade})if(!Double.isFinite(n))throw new IllegalArgumentException("NON_FINITE_PLAN");
        }
    }
    public record Range(double min,double max) {}
    public record SectionState(String from,String to,double blend,double laneWidth,double driving,double inner,double outer,
                               double median,double width,double paved,double formation,double construction,double protection,double crossfall) {
        public Range medianRange(){return new Range(-median/2,median/2);}
        public Range leftInner(){return new Range(-median/2-inner,-median/2);}
        public Range rightInner(){return new Range(median/2,median/2+inner);}
        public Range leftOuter(){return new Range(-width/2,-width/2+outer);}
        public Range rightOuter(){return new Range(width/2-outer,width/2);}
    }
    public record Surface(double station,double lateral,double x,double y,double z,double nx,double ny,double nz,SectionState section) {}
    public final Plan plan;
    public final RoadAlignment alignment;
    public final double originX,originZ,originY;
    public RouteRoadGeometry(Plan plan) {
        this.plan=plan;
        double margin=Math.max(plan.from.protection(),plan.to.protection())/2;
        alignment=new RoadAlignment(plan.controls,plan.corridors,margin,240,150);
        if(plan.start+plan.length>alignment.length+1e-7)throw new IllegalArgumentException("WINDOW_OUTSIDE_FINITE_EDGE");
        var p=alignment.frame(plan.start).point();originX=p.x();originZ=p.z();originY=elevation(plan.start);
    }
    public static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*t*(10+t*(-15+6*t));}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
    public SectionState section(double s){
        double t=ease((s-plan.transitionStart)/plan.transitionLength);RoadSection a=plan.from,b=plan.to;
        double lane=mix(a.laneWidth(),b.laneWidth(),t),driving=mix(a.carriageway(),b.carriageway(),t),inner=mix(a.innerShoulder(),b.innerShoulder(),t),
            outer=mix(a.outerShoulder(),b.outerShoulder(),t),median=mix(a.median(),b.median(),t),paved=2*(driving+inner+outer),w=paved+median;
        return new SectionState(a.id(),b.id(),t,lane,driving,inner,outer,median,w,paved,w+mix(a.formationExtra(),b.formationExtra(),t),
            w+mix(a.constructionExtra(),b.constructionExtra(),t),w+mix(a.protectionExtra(),b.protectionExtra(),t),mix(a.crossfall(),b.crossfall(),t));
    }
    /** Explicit design demonstration datum, NOT current terrain or legacy engineered road height. */
    public double elevation(double s){return plan.grade*plan.profileSpan/1.875*ease(s/plan.profileSpan);}
    public double bank(double s){double k=alignment.frame(s).curvature();return Math.copySign(.04*ease(Math.min(1,Math.abs(k)*150)),k);}
    private double[] position(double s,double u){
        var f=alignment.frame(s);var c=section(s);
        return new double[]{f.point().x()-f.tangent().z()*u,elevation(s)-c.crossfall*Math.max(0,Math.abs(u)-c.median/2)+bank(s)*u,f.point().z()+f.tangent().x()*u};
    }
    public Surface surface(double s,double u){
        if(!Double.isFinite(u)||Math.abs(u)>section(s).width/2+1e-7)throw new IllegalArgumentException("OUTSIDE_RIBBON");
        double[] p=position(s,u),a=position(Math.max(0,s-.001),u),b=position(Math.min(alignment.length,s+.001),u),
            l=position(s,u-.001),r=position(s,u+.001);
        double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],ux=r[0]-l[0],uy=r[1]-l[1],uz=r[2]-l[2];
        double nx=uy*dz-uz*dy,ny=uz*dx-ux*dz,nz=ux*dy-uy*dx,n=Math.sqrt(nx*nx+ny*ny+nz*nz);
        return new Surface(s,u,p[0],p[1],p[2],nx/n,ny/n,nz/n,section(s));
    }
    /** Bounded window projection in source-world XZ; y is design datum, caller supplies preview translation. */
    public Optional<Surface> query(double x,double z){
        if(!Double.isFinite(x)||!Double.isFinite(z))return Optional.empty();
        double best=Double.POSITIVE_INFINITY,station=plan.start;
        for(double s=plan.start;s<=plan.start+plan.length;s+=4){
            var p=alignment.frame(s).point();double d=Math.pow(x-p.x(),2)+Math.pow(z-p.z(),2);if(d<best){best=d;station=s;}}
        for(int i=0;i<10;i++){var f=alignment.frame(station);double along=(x-f.point().x())*f.tangent().x()+(z-f.point().z())*f.tangent().z();
            double next=Math.max(plan.start,Math.min(plan.start+plan.length,station+along));if(Math.abs(next-station)<1e-9)break;station=next;}
        var f=alignment.frame(station);double along=(x-f.point().x())*f.tangent().x()+(z-f.point().z())*f.tangent().z();
        double u=-(x-f.point().x())*f.tangent().z()+(z-f.point().z())*f.tangent().x();
        if(Math.abs(along)>.01||Math.abs(u)>section(station).width/2)return Optional.empty();return Optional.of(surface(station,u));
    }
    /** Semantic attachment anchors are coordinates only; they do not claim Vanilla support or block ownership. */
    public Surface anchor(double s,String name){
        var c=section(s);double u=switch(name){
            case "left_edge" -> -c.width/2;case "right_edge" -> c.width/2;
            case "left_median" -> -c.median/2;case "right_median" -> c.median/2;
            case "left_lane_outer" -> -c.width/2+c.outer;case "right_lane_outer" -> c.width/2-c.outer;
            default -> throw new IllegalArgumentException("UNKNOWN_ANCHOR");};
        return surface(s,u);
    }
    private Vertex vertex(double s,double u,String material,double across,double tile,double raised){
        double[] p=position(s,u);int slot=List.of("asphalt","shoulder","median","white","yellow").indexOf(material);
        return new Vertex(p[0]-originX,p[1]-originY+raised,p[2]-originZ,slot*.2+.02+.16*across,.0625+.875*(s-tile*8)/8);
    }
    private void patch(List<Triangle> out,double s,double e,double a,double b,double c,double d,String material,double raised) {
        double tile=Math.floor((s+1e-8)/8);
        Vertex p=vertex(s,a,material,0,tile,raised),q=vertex(s,b,material,1,tile,raised),r=vertex(e,d,material,1,tile,raised),t=vertex(e,c,material,0,tile,raised);
        if(Math.abs(b-a)>1e-8&&Math.abs(d-c)>1e-8){out.add(new Triangle(material,p,q,r));out.add(new Triangle(material,p,r,t));}
    }
    public List<Triangle> triangles(){
        List<Triangle> out=new ArrayList<>();double end=plan.start+plan.length;
        // Global station lattice, independent of preview-window/chunk order; includes every UV/dash boundary.
        for(double s=plan.start;s<end-1e-9;){
            double e=Math.min(end,(Math.floor((s+1e-8)/2)+1)*2);var a=section(s);var b=section(e);
            patch(out,s,e,-a.median/2,a.median/2,-b.median/2,b.median/2,"median",0);
            for(int sign:new int[]{-1,1}){
                double[] aa={a.median/2,a.median/2+a.inner,a.width/2-a.outer,a.width/2};
                double[] bb={b.median/2,b.median/2+b.inner,b.width/2-b.outer,b.width/2};
                for(int i=0;i<3;i++){double u=sign*aa[i],v=sign*aa[i+1],w=sign*bb[i],x=sign*bb[i+1];
                    patch(out,s,e,Math.min(u,v),Math.max(u,v),Math.min(w,x),Math.max(w,x),i==1?"asphalt":"shoulder",0);}
                line(out,s,e,sign*aa[1],sign*bb[1],.15,"yellow");
                line(out,s,e,sign*aa[2],sign*bb[2],.15,"white");
                if(Math.floor((s+1e-8)%12)<4)for(int lane=1;lane<Math.max(plan.from.lanes(),plan.to.lanes());lane++){
                    double excessA=a.driving-lane*a.laneWidth,excessB=b.driving-lane*b.laneWidth;
                    // A developing outer lane starts with zero line width; schematic taper, not interchange design.
                    double width=.15*ease(Math.min(excessA,excessB)/a.laneWidth);
                    if(width>1e-6)line(out,s,e,sign*(aa[1]+lane*a.laneWidth),sign*(bb[1]+lane*b.laneWidth),width,"white");
                }
            }
            s=e;
        }
        return List.copyOf(out);
    }
    private void line(List<Triangle> out,double s,double e,double u,double v,double w,String material){patch(out,s,e,u-w/2,u+w/2,v-w/2,v+w/2,material,.003);}
    public RoadMeshAsset asset(String version) {
        var triangles=triangles();List<double[]> centers=new ArrayList<>();
        for(double s=plan.start;s<plan.start+plan.length;s+=2){var f=alignment.frame(s);centers.add(new double[]{s,f.point().x()-originX,elevation(s)-originY,f.point().z()-originZ});}
        var f=alignment.frame(plan.start+plan.length);centers.add(new double[]{f.station(),f.point().x()-originX,elevation(f.station())-originY,f.point().z()-originZ});
        return RoadMeshAsset.generated(version,triangles,centers,plan.length,Math.max(plan.from.width(),plan.to.width()));
    }
}