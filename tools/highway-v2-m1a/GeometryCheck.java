import com.antaurora.apofirstlight.dev.highwaymesh.*;
import com.antaurora.apofirstlight.client.mesh.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.*;

/** Targeted offline contract checks. Runs the real asset reader, clipper, collision sampler and Loader. */
class GeometryCheck {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Triangle at(List<Triangle> ts,double x,double z){for(var t:ts)if(t.pavement()&&t.barycentric(x,z)!=null)return t;return null;}
    static double height(Triangle t,double x,double z){var b=t.barycentric(x,z);return b[0]*t.a().y()+b[1]*t.b().y()+b[2]*t.c().y();}
    static double uv(Triangle t,double x,double z,boolean u){var b=t.barycentric(x,z);return b[0]*(u?t.a().u():t.a().v())+b[1]*(u?t.b().u():t.b().v())+b[2]*(u?t.c().u():t.c().v());}
    static String key(int x,int z){return x+","+z;}
    public static void main(String[] args)throws Exception{
        Path resources=Path.of(args[0]);var a=RoadMeshAsset.load(name->Files.newBufferedReader(resources.resolve(name)));
        require(a.sourceStoredVertices==14864&&a.sourceTriangleEquivalent==7432,"V2-0 source changed");
        SortedMap<Tile,List<Triangle>> baked=new TreeMap<>();int triangles=0,faces=0,corners=0,dropped=0;double bakedArea=0,minNormalY=1,minRenderedNormalY=1,maxBakeHeightError=0;long begin=System.nanoTime();
        for(var tile:a.tiles.keySet()){
            var b=a.bake(tile);triangles+=b.triangles();faces+=b.faces();corners+=b.corners();dropped+=b.tinyFacesDropped();List<Triangle> ts=new ArrayList<>();
            int faceIndex=0;for(var p:b.model().parts("road"))for(int f=0;f<p.faceCount();f++){
                List<Vertex> polygon=new ArrayList<>();for(int i=0;i<p.faceSize(f);i++){int n=p.faceStart(f)+i;
                    require(p.value(n,0)>=-1e-5&&p.value(n,0)<=16.00001&&p.value(n,2)>=-1e-5&&p.value(n,2)<=16.00001,"tile bounds");
                    for(int c=0;c<8;c++)require(Float.isFinite(p.value(n,c)),"nonfinite baked value");
                    require(p.value(n,3)>=0&&p.value(n,3)<=1&&p.value(n,4)>=0&&p.value(n,4)<=1,"UV range");
                    double length=0;for(int c=5;c<8;c++)length+=p.value(n,c)*p.value(n,c);require(Math.abs(length-1)<1e-5,"normal length");
                    minNormalY=Math.min(minNormalY,p.value(n,6));
                    polygon.add(new Vertex(tile.x()*16+p.value(n,0),p.value(n,1),tile.z()*16+p.value(n,2),p.value(n,3),p.value(n,4)));
                }
                var normal=b.sourceNormals().get(faceIndex++);
                minRenderedNormalY=Math.min(minRenderedNormalY,normal.y());
                for(var t:triangulate(p.name(),polygon))ts.add(new Triangle(t.material(),t.a(),t.b(),t.c(),normal));
            }
            baked.put(tile,List.copyOf(ts));
            for(var t:ts){bakedArea+=t.areaXZ();if(t.pavement()){
                double x=(t.a().x()+t.b().x()+t.c().x())/3,z=(t.a().z()+t.b().z()+t.c().z())/3;
                var ref=at(a.tiles.get(tile),x,z);if(ref!=null)maxBakeHeightError=Math.max(maxBakeHeightError,Math.abs(height(t,x,z)-height(ref,x,z)));
            }}
        }
        double bakeMs=(System.nanoTime()-begin)/1e6,sourceArea=a.triangles.stream().mapToDouble(Triangle::areaXZ).sum(),clipArea=a.tiles.values().stream().flatMap(List::stream).mapToDouble(Triangle::areaXZ).sum();
        require(Math.abs(sourceArea-clipArea)<1e-6,"clipped area conservation");require(Math.abs(sourceArea-bakedArea)<.01,"baked area conservation");
        require(minNormalY>.9,"flipped/unstable normal");require(maxBakeHeightError<1e-4,"baked height distortion");
        int seamSamples=0,missing=0;double seamY=0,seamNormal=0,canonicalSeamNormal=0,maxNormalDrift=0,maxSeamUvDrift=0;
        for(var tile:a.tiles.keySet())for(boolean xAxis:new boolean[]{true,false}){
            Tile other=new Tile(tile.x()+(xAxis?1:0),tile.z()+(xAxis?0:1));if(!a.tiles.containsKey(other))continue;
            // Quarter-metre probes offset from integer vertices avoid ambiguous material/crown ownership.
            for(int i=0;i<64;i++){
                double x=xAxis?(tile.x()+1)*16:tile.x()*16+(i+.5)*.25,z=xAxis?tile.z()*16+(i+.5)*.25:(tile.z()+1)*16;
                var r1=at(a.tiles.get(tile),x,z);var r2=at(a.tiles.get(other),x,z);if(r1==null||r2==null)continue;
                var t1=at(baked.get(tile),x,z);var t2=at(baked.get(other),x,z);if(t1==null||t2==null){missing++;continue;}
                seamSamples++;seamY=Math.max(seamY,Math.abs(height(t1,x,z)-height(t2,x,z)));
                double[] n=t1.normal(),m=t2.normal();seamNormal=Math.max(seamNormal,Math.sqrt(Math.pow(n[0]-m[0],2)+Math.pow(n[1]-m[1],2)+Math.pow(n[2]-m[2],2)));
                double[] rn=r1.normal(),rm=r2.normal();canonicalSeamNormal=Math.max(canonicalSeamNormal,distance(rn,rm));maxNormalDrift=Math.max(maxNormalDrift,Math.max(distance(n,rn),distance(m,rm)));
                for(boolean u:new boolean[]{true,false})maxSeamUvDrift=Math.max(maxSeamUvDrift,Math.max(Math.abs(uv(t1,x,z,u)-uv(r1,x,z,u)),Math.abs(uv(t2,x,z,u)-uv(r2,x,z,u))));
            }
        }
        require(seamSamples>2000&&missing==0,"actual 16m seam coverage missing="+missing);require(seamY<1e-5,"actual 16m seam height "+seamY);
        require(maxNormalDrift<1e-10,"source normal preservation");
        require(maxSeamUvDrift<1e-4,"seam UV differs from original station mapping");
        Map<String,RoadMeshAsset.Cell> cells=new HashMap<>();for(var c:a.collision){require(c.layers()>=1&&c.layers()<=32,"collision state");require(cells.put(key(c.x(),c.z()),c)==null,"duplicate column");}
        double maxError=0,maxStep=0,seamStep=0;int seamCellPairs=0;
        for(var c:a.collision){maxError=Math.max(maxError,c.error());for(boolean xAxis:new boolean[]{true,false}){
            var n=cells.get(key(c.x()+(xAxis?1:0),c.z()+(xAxis?0:1)));if(n==null)continue;double step=Math.abs(c.top()-n.top());maxStep=Math.max(maxStep,step);
            if(Math.floorDiv(c.x(),16)!=Math.floorDiv(n.x(),16)||Math.floorDiv(c.z(),16)!=Math.floorDiv(n.z(),16)){seamStep=Math.max(seamStep,step);seamCellPairs++;}
        }}
        for(var t:a.triangles)if(t.pavement()){double x=(t.a().x()+t.b().x()+t.c().x())/3,z=(t.a().z()+t.b().z()+t.c().z())/3;require(cells.containsKey(key((int)Math.floor(x),(int)Math.floor(z))),"unsupported surface centroid");require(a.sample(x,z).isPresent(),"surface query missing");}
        require(maxStep<.6,"step exceeds player nominal step height");require(maxError<.20,"collision envelope error budget");
        // Negative chunk flooring uses the same clipper, not a duplicated arithmetic implementation.
        List<Triangle> shifted=new ArrayList<>();for(var t:a.triangles)shifted.add(new Triangle(t.material(),shift(t.a()),shift(t.b()),shift(t.c())));
        var negative=ChunkMeshGeometry.tiles(shifted);require(negative.size()==a.tiles.size(),"negative tile count");
        for(var tile:a.tiles.keySet())require(negative.containsKey(new Tile(tile.x()-20,tile.z()-4)),"negative tile key");
        // Deterministic model submission fingerprint across independent bakes, including normals and UV.
        String hash=fingerprint(a);require(hash.equals(fingerprint(RoadMeshAsset.load(name->Files.newBufferedReader(resources.resolve(name))))),"nonrepeatable geometry");
        JsonObject result=new JsonObject();result.addProperty("status","PASS_OFFLINE_ONLY");result.addProperty("assetVersion",a.version);result.addProperty("modelFingerprint",hash);
        result.addProperty("sourceVertices",a.sourceStoredVertices);result.addProperty("sourceTriangleEquivalent",a.sourceTriangleEquivalent);result.addProperty("tiles",a.tiles.size());result.addProperty("bakedTriangles",triangles);result.addProperty("bakedFaces",faces);result.addProperty("bakedCorners",corners);result.addProperty("submittedCornersAllTiles",faces*4);result.addProperty("cornerFloatBytes",corners*32);result.addProperty("tinyFacesDropped",dropped);
        result.addProperty("clipAreaErrorM2",Math.abs(sourceArea-clipArea));result.addProperty("bakedAreaErrorM2",Math.abs(sourceArea-bakedArea));result.addProperty("minimumLoaderNormalY",minNormalY);result.addProperty("minimumRenderedNormalY",minRenderedNormalY);result.addProperty("maxBakeHeightErrorM",maxBakeHeightError);
        result.addProperty("actual16mSeamSamples",seamSamples);result.addProperty("missingSeamSamples",missing);result.addProperty("maxSeamHeightErrorM",seamY);result.addProperty("maxSeamNormalVectorDifference",seamNormal);
        result.addProperty("canonicalSeamNormalVectorDifference",canonicalSeamNormal);result.addProperty("maxNormalDriftAtSeam",maxNormalDrift);
        result.addProperty("maxSeamUvDrift",maxSeamUvDrift);
        result.addProperty("collisionColumns",a.collision.size());result.addProperty("maxCollisionHeightErrorM",maxError);result.addProperty("maxAdjacentStepM",maxStep);result.addProperty("actual16mCollisionPairs",seamCellPairs);result.addProperty("maxCollisionSeamStepM",seamStep);
        result.addProperty("negativeChunkCheck",true);result.addProperty("repeatability",true);result.addProperty("clientRun",false);result.addProperty("saveReloadInGameTested",false);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.printf(Locale.ROOT,"M1A_OFFLINE_PASS tiles=%d triangles=%d columns=%d seam=%.9fm collisionError=%.6fm maxStep=%.6fm assetLoad=%.1fms allTilesBake=%.1fms%n",a.tiles.size(),triangles,a.collision.size(),seamY,maxError,maxStep,a.loadNanos/1e6,bakeMs);
    }
    static Vertex shift(Vertex p){return new Vertex(p.x()-320,p.y(),p.z()-64,p.u(),p.v());}
    static double distance(double[] a,double[] b){return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));}
    static String fingerprint(RoadMeshAsset a)throws Exception{var digest=java.security.MessageDigest.getInstance("SHA-256");var bytes=java.nio.ByteBuffer.allocate(4);for(var t:a.tiles.keySet()){var b=a.bake(t);for(var p:b.model().parts("road"))for(int n=0;n<p.cornerCount();n++)for(int i=0;i<8;i++){bytes.clear();bytes.putFloat(p.value(n,i));digest.update(bytes.array());}for(var normal:b.sourceNormals())for(double v:new double[]{normal.x(),normal.y(),normal.z()}){bytes.clear();bytes.putFloat((float)v);digest.update(bytes.array());}}return HexFormat.of().formatHex(digest.digest());}
}
