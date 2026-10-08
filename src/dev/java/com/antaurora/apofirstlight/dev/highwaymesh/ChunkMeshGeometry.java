package com.antaurora.apofirstlight.dev.highwaymesh;

import java.util.*;

/** World-independent triangle clipping. One canonical triangle owns its attributes before any chunk split. */
public final class ChunkMeshGeometry {
    public record Vertex(double x, double y, double z, double u, double v) {
        Vertex lerp(Vertex b, double t) { return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t); }
    }
    public record Normal(double x,double y,double z) {}
    public record Triangle(String material, Vertex a, Vertex b, Vertex c, Normal sourceNormal) {
        public Triangle(String material,Vertex a,Vertex b,Vertex c){this(material,a,b,c,calculate(a,b,c));}
        public double areaXZ() { return Math.abs((b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x))*.5; }
        private static Normal calculate(Vertex a,Vertex b,Vertex c) {
            double x=(b.y-a.y)*(c.z-a.z)-(b.z-a.z)*(c.y-a.y);
            double y=(b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z);
            double z=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x), n=Math.sqrt(x*x+y*y+z*z);
            return new Normal(x/n,y/n,z/n);
        }
        public double[] normal(){return new double[]{sourceNormal.x,sourceNormal.y,sourceNormal.z};}
        public double[] barycentric(double x,double z) {
            double d=(b.z-c.z)*(a.x-c.x)+(c.x-b.x)*(a.z-c.z);
            if(Math.abs(d)<1e-12)return null;
            double l=((b.z-c.z)*(x-c.x)+(c.x-b.x)*(z-c.z))/d;
            double m=((c.z-a.z)*(x-c.x)+(a.x-c.x)*(z-c.z))/d;
            return l>=-1e-8&&m>=-1e-8&&l+m<=1+1e-8?new double[]{l,m,1-l-m}:null;
        }
        public boolean pavement() { return !material.equals("white")&&!material.equals("yellow"); }
    }
    public record Tile(int x,int z) implements Comparable<Tile> {
        @Override public int compareTo(Tile b){int c=Integer.compare(x,b.x);return c!=0?c:Integer.compare(z,b.z);}
    }
    public static List<Vertex> clip(Triangle t,double x0,double z0,double x1,double z1) {
        List<Vertex> p=List.of(t.a,t.b,t.c);
        p=plane(p,true,x0,true);p=plane(p,true,x1,false);
        p=plane(p,false,z0,true);return plane(p,false,z1,false);
    }
    private static List<Vertex> plane(List<Vertex> input,boolean x,double edge,boolean lower) {
        if(input.isEmpty())return input;
        List<Vertex> out=new ArrayList<>(6);Vertex a=input.get(input.size()-1);
        double da=(x?a.x:a.z)-edge;boolean ia=lower?da>=0:da<=0;
        for(Vertex b:input){double db=(x?b.x:b.z)-edge;boolean ib=lower?db>=0:db<=0;
            if(ia!=ib){Vertex v=a.lerp(b,da/(da-db));out.add(x?new Vertex(edge,v.y,v.z,v.u,v.v):new Vertex(v.x,v.y,edge,v.u,v.v));}
            if(ib)out.add(b);a=b;da=db;ia=ib;
        }
        return out;
    }
    public static List<Triangle> triangulate(String material,List<Vertex> polygon) {
        List<Triangle> out=new ArrayList<>();
        for(int i=1;i+1<polygon.size();i++){Triangle t=new Triangle(material,polygon.get(0),polygon.get(i),polygon.get(i+1));if(t.areaXZ()>1e-10)out.add(t);}
        return out;
    }
    public static List<Triangle> triangulate(Triangle source,List<Vertex> polygon){
        return triangulate(source.material,polygon).stream().map(t->new Triangle(t.material,t.a,t.b,t.c,source.sourceNormal)).toList();
    }
    public static SortedMap<Tile,List<Triangle>> tiles(List<Triangle> triangles) {
        SortedMap<Tile,List<Triangle>> out=new TreeMap<>();
        for(Triangle t:triangles){
            int x0=(int)Math.floor(Math.min(t.a.x,Math.min(t.b.x,t.c.x))/16),x1=(int)Math.floor(Math.max(t.a.x,Math.max(t.b.x,t.c.x))/16);
            int z0=(int)Math.floor(Math.min(t.a.z,Math.min(t.b.z,t.c.z))/16),z1=(int)Math.floor(Math.max(t.a.z,Math.max(t.b.z,t.c.z))/16);
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                var clipped=triangulate(t,clip(t,x*16,z*16,x*16+16,z*16+16));
                if(!clipped.isEmpty())out.computeIfAbsent(new Tile(x,z),k->new ArrayList<>()).addAll(clipped);
            }
        }
        out.replaceAll((k,v)->List.copyOf(v));return Collections.unmodifiableSortedMap(out);
    }
    private ChunkMeshGeometry() {}
}
