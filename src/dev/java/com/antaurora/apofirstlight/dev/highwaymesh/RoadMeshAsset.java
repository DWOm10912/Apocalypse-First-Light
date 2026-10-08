package com.antaurora.apofirstlight.dev.highwaymesh;

import com.antaurora.apofirstlight.client.mesh.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.*;

/** Immutable prototype data, surface query and chunk clips. No Level, entity, registry or physics hooks. */
public final class RoadMeshAsset {
    public static final String PREFIX="assets/afl_highway_demo/prototype/";
    @FunctionalInterface public interface Resources { Reader open(String name) throws IOException; }
    public record Surface(double height,double nx,double ny,double nz,double station,String material) {}
    public record Cell(int x,int y,int z,int layers,double minHeight,double maxHeight) {
        public double top(){return y+layers/32.0;}
        public double error(){return Math.max(Math.abs(top()-minHeight),Math.abs(top()-maxHeight));}
    }
    public record BakedTile(Tile tile,AflMeshModel model,List<Normal> sourceNormals,int triangles,int faces,int corners,double minY,double maxY,int tinyFacesDropped) {}
    private record Column(int x,int z) implements Comparable<Column> {
        @Override public int compareTo(Column b){int c=Integer.compare(x,b.x);return c!=0?c:Integer.compare(z,b.z);}
    }
    public final String version;
    public final List<Triangle> triangles;
    public final SortedMap<Tile,List<Triangle>> tiles;
    public final List<Cell> collision;
    public final int sourceStoredVertices,sourceTriangleEquivalent;
    public final double length,width,minX,minY,minZ,maxX,maxY,maxZ;
    public final long loadNanos;
    private final List<double[]> centerline;

    public static RoadMeshAsset load(Resources resources) throws IOException {
        long begin=System.nanoTime();
        JsonObject root=JsonParser.parseString(read(resources,"scene.json")).getAsJsonObject();
        String descriptor=root.get("descriptor_json").getAsString();JsonObject d=JsonParser.parseString(descriptor).getAsJsonObject();
        String version=root.get("version").getAsString();
        if(d.get("schema").getAsInt()!=1||!d.get("runtimeContract").getAsString().equals("highway-v2-m1a-1")||!version.equals(sha(descriptor)))throw new IOException("Unsupported road scene schema/version");
        Map<String,String> text=new HashMap<>();
        for(var value:d.getAsJsonArray("entries")){var entry=value.getAsJsonObject();String name=entry.get("name").getAsString();
            if(!name.matches("highway_v2_0_demo_[0-9]{3}\\.(aflmesh|geo)\\.json"))throw new IOException("Unexpected asset path");
            String content=read(resources,name);if(!sha(content).equals(entry.get("sha256").getAsString()))throw new IOException("Road resource digest mismatch: "+name);text.put(name,content);
        }
        if(d.getAsJsonArray("modules").size()!=4)throw new IOException("Expected bounded four-window demo");
        List<Triangle> triangles=new ArrayList<>();int stored=0,equivalents=0;
        for(var value:d.getAsJsonArray("modules")){var module=value.getAsJsonObject();String id=module.get("id").getAsString();var origin=module.getAsJsonArray("origin");
            double ox=origin.get(0).getAsDouble(),oy=origin.get(1).getAsDouble(),oz=origin.get(2).getAsDouble();
            String mesh=text.get(id+".aflmesh.json"),geo=text.get(id+".geo.json");
            var loaded=AflMeshLoader.load(id,new StringReader(mesh),new StringReader(geo));
            for(var part:JsonParser.parseString(mesh).getAsJsonObject().getAsJsonArray("parts"))stored+=part.getAsJsonObject().getAsJsonArray("vertices").size();
            for(var part:loaded.parts("road")){equivalents+=part.triangleEquivalent();
                for(int f=0;f<part.faceCount();f++){List<Vertex> points=new ArrayList<>(4);
                    for(int i=0;i<part.faceSize(f);i++){int n=part.faceStart(f)+i;points.add(new Vertex(ox+part.value(n,0),oy+part.value(n,1),oz+part.value(n,2),part.value(n,3),part.value(n,4)));}
                    triangles.addAll(triangulate(part.name(),points));
                }
            }
        }
        List<double[]> center=new ArrayList<>();for(var value:d.getAsJsonArray("centerline")){var row=value.getAsJsonArray();center.add(new double[]{row.get(0).getAsDouble(),row.get(1).getAsDouble(),row.get(2).getAsDouble(),row.get(3).getAsDouble()});}
        return new RoadMeshAsset(version,triangles,center,stored,equivalents,d.get("length").getAsDouble(),d.get("width").getAsDouble(),begin);
    }
    private RoadMeshAsset(String version,List<Triangle> triangles,List<double[]> center,int stored,int equivalents,double length,double width,long begin) {
        this.version=version;this.triangles=List.copyOf(triangles);this.centerline=List.copyOf(center);this.sourceStoredVertices=stored;this.sourceTriangleEquivalent=equivalents;this.length=length;this.width=width;
        this.tiles=ChunkMeshGeometry.tiles(triangles);this.collision=collision(triangles);
        var vertices=triangles.stream().flatMap(t->java.util.stream.Stream.of(t.a(),t.b(),t.c())).toList();
        minX=vertices.stream().mapToDouble(Vertex::x).min().orElseThrow();maxX=vertices.stream().mapToDouble(Vertex::x).max().orElseThrow();
        minY=vertices.stream().mapToDouble(Vertex::y).min().orElseThrow();maxY=vertices.stream().mapToDouble(Vertex::y).max().orElseThrow();
        minZ=vertices.stream().mapToDouble(Vertex::z).min().orElseThrow();maxZ=vertices.stream().mapToDouble(Vertex::z).max().orElseThrow();loadNanos=System.nanoTime()-begin;
        if(tiles.size()>128||collision.size()>16000)throw new IllegalArgumentException("Demo exceeds bounded budget");
    }
    private static String read(Resources r,String name) throws IOException {try(Reader reader=r.open(name)){StringWriter out=new StringWriter();reader.transferTo(out);String s=out.toString().replace("\r\n","\n");if(s.length()>4*1024*1024)throw new IOException("Asset too large");return s;}}
    private static String sha(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public Optional<Surface> sample(double x,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(z))return Optional.empty();
        var ts=tiles.get(new Tile((int)Math.floor(x/16),(int)Math.floor(z/16)));if(ts==null)return Optional.empty();
        for(var t:ts)if(t.pavement()){var w=t.barycentric(x,z);if(w!=null){double[] n=t.normal();return Optional.of(new Surface(w[0]*t.a().y()+w[1]*t.b().y()+w[2]*t.c().y(),n[0],n[1],n[2],station(x,z),t.material()));}}
        return Optional.empty();
    }
    private double station(double x,double z){double best=Double.POSITIVE_INFINITY,result=0;
        for(int i=0;i+1<centerline.size();i++){double[] a=centerline.get(i),b=centerline.get(i+1);double dx=b[1]-a[1],dz=b[3]-a[3],t=Math.max(0,Math.min(1,((x-a[1])*dx+(z-a[3])*dz)/(dx*dx+dz*dz)));
            double dist=Math.pow(x-a[1]-t*dx,2)+Math.pow(z-a[3]-t*dz,2);if(dist<best){best=dist;result=a[0]+t*(b[0]-a[0]);}}
        return result;
    }
    private static List<Cell> collision(List<Triangle> triangles){
        SortedMap<Column,double[]> columns=new TreeMap<>();
        for(var t:triangles)if(t.pavement()){
            int x0=(int)Math.floor(Math.min(t.a().x(),Math.min(t.b().x(),t.c().x()))),x1=(int)Math.floor(Math.max(t.a().x(),Math.max(t.b().x(),t.c().x())));
            int z0=(int)Math.floor(Math.min(t.a().z(),Math.min(t.b().z(),t.c().z()))),z1=(int)Math.floor(Math.max(t.a().z(),Math.max(t.b().z(),t.c().z())));
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                var polygon=clip(t,x,z,x+1,z+1);if(triangulate(t.material(),polygon).isEmpty())continue;
                double[] range=columns.computeIfAbsent(new Column(x,z),k->new double[]{Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY});
                for(var v:polygon){range[0]=Math.min(range[0],v.y());range[1]=Math.max(range[1],v.y());}
            }
        }
        List<Cell> out=new ArrayList<>(columns.size());columns.forEach((column,range)->{int top=(int)Math.round((range[0]+range[1])*.5*32),y=Math.floorDiv(top-1,32);out.add(new Cell(column.x,y,column.z,top-y*32,range[0],range[1]));});return List.copyOf(out);
    }
    public BakedTile bake(Tile tile) throws IOException {
        var source=tiles.get(tile);if(source==null)throw new IllegalArgumentException("Unknown tile");
        JsonObject root=new JsonObject();root.addProperty("format_version",2);root.addProperty("coordinate_space","bone_pivot_local_blocks");root.addProperty("uv_origin","top_left");root.addProperty("winding","ccw");root.add("texture_size",JsonParser.parseString("[128,128]"));
        JsonArray parts=new JsonArray();List<Normal> normals=new ArrayList<>();Map<String,List<Triangle>> grouped=new TreeMap<>();for(var t:source)grouped.computeIfAbsent(t.material(),k->new ArrayList<>()).add(t);int dropped=0;
        for(var entry:grouped.entrySet()){
            JsonObject part=new JsonObject();part.addProperty("name",entry.getKey());part.addProperty("bone","road");JsonArray vertices=new JsonArray(),faces=new JsonArray();
            for(var t:entry.getValue()){
                var points=List.of(t.a(),t.b(),t.c());float[][] local=new float[3][5];
                for(int i=0;i<3;i++){var v=points.get(i);local[i]=new float[]{(float)(v.x()-tile.x()*16),(float)v.y(),(float)(v.z()-tile.z()*16),(float)v.u(),(float)v.v()};}
                double det=((double)local[1][0]-local[0][0])*(local[2][2]-local[0][2])-((double)local[1][2]-local[0][2])*(local[2][0]-local[0][0]);
                if(Math.abs(det)<=1e-9){dropped++;continue;} // sub-micrometre slivers can collapse after float conversion
                JsonArray indices=new JsonArray();for(var v:local){indices.add(vertices.size());JsonArray row=new JsonArray();for(float f:v)row.add(f);vertices.add(row);}faces.add(indices);normals.add(t.sourceNormal());
            }
            if(!faces.isEmpty()){part.add("vertices",vertices);part.add("faces",faces);parts.add(part);}
        }
        root.add("parts",parts);
        String geo="{\"minecraft:geometry\":[{\"description\":{\"texture_width\":128,\"texture_height\":128},\"bones\":[{\"name\":\"road\"}]}]}";
        var model=AflMeshLoader.load("road-tile-"+tile,new StringReader(root.toString()),new StringReader(geo));
        int tris=0,faces=0,corners=0;double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(var p:model.parts("road")){tris+=p.triangleEquivalent();faces+=p.faceCount();corners+=p.cornerCount();low=Math.min(low,p.bounds().minY());high=Math.max(high,p.bounds().maxY());}
        if(faces!=normals.size()||tris!=faces)throw new IOException("Road clip topology changed; cannot safely map canonical normals");
        return new BakedTile(tile,model,List.copyOf(normals),tris,faces,corners,low,high,dropped);
    }
}
