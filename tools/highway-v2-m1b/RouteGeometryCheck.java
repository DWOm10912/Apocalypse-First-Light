import com.antaurora.apofirstlight.dev.highwaymesh.*;
import com.antaurora.apofirstlight.worldgen.highway.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.*;

public class RouteGeometryCheck {
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static String signature(HighwayRouteGraph g){return g.edges().stream().map(e->e.id()+":"+e.startStation()+":"+e.endStation()+":"+e.fixedCoordinate()+":"+e.startNode()+":"+e.endNode()).toList().toString();}
    static JsonObject check(String name,RouteRoadGeometry g)throws Exception{
        long begin=System.nanoTime();var asset=g.asset(name);double setup=(System.nanoTime()-begin)/1e6;
        double widthError=0,normalError=0,projectionError=0,maxGrade=0;
        for(double s=g.plan.start();s<=g.plan.start()+g.plan.length();s+=1){
            var a=g.anchor(s,"left_edge");var b=g.anchor(s,"right_edge");double w=Math.hypot(a.x()-b.x(),a.z()-b.z());
            widthError=Math.max(widthError,Math.abs(w-g.section(s).width()));
            var p=g.surface(s,g.section(s).median()/2+2);normalError=Math.max(normalError,Math.abs(p.nx()*p.nx()+p.ny()*p.ny()+p.nz()*p.nz()-1));
            var q=g.query(p.x(),p.z()).orElseThrow();projectionError=Math.max(projectionError,Math.abs(q.y()-p.y()));
            if(s>g.plan.start())maxGrade=Math.max(maxGrade,Math.abs(g.elevation(s)-g.elevation(s-1)));
        }
        require(widthError<1e-8&&normalError<1e-10&&projectionError<1e-5,"surface contract "+name);
        require(maxGrade<=.030001,"grade");
        Map<Tile,String> tileHashes=new TreeMap<>();SortedMap<Tile,List<Triangle>> baked=new TreeMap<>();int tris=0,corners=0,dropped=0;double minNormal=1,area=0;
        for(var tile:asset.tiles.keySet()){
            var b=asset.bake(tile);tileHashes.put(tile,tileHash(b));tris+=b.triangles();corners+=b.corners();dropped+=b.tinyFacesDropped();List<Triangle> ts=new ArrayList<>();int face=0;
            for(var p:b.model().parts("road"))for(int f=0;f<p.faceCount();f++){
                List<Vertex> vs=new ArrayList<>();
                for(int i=0;i<p.faceSize(f);i++){int n=p.faceStart(f)+i;
                    for(int c=0;c<8;c++)require(Float.isFinite(p.value(n,c)),"finite loader data");
                    require(p.value(n,3)>=0&&p.value(n,3)<=1&&p.value(n,4)>=0&&p.value(n,4)<=1,"UV range");
                    vs.add(new Vertex(tile.x()*16+p.value(n,0),p.value(n,1),tile.z()*16+p.value(n,2),p.value(n,3),p.value(n,4)));
                }
                var normal=b.sourceNormals().get(face++);minNormal=Math.min(minNormal,normal.y());
                for(var t:triangulate(p.name(),vs))ts.add(new Triangle(t.material(),t.a(),t.b(),t.c(),normal));
            }
            baked.put(tile,ts);area+=ts.stream().mapToDouble(Triangle::areaXZ).sum();
        }
        require(minNormal>.9,"flipped rendered normals");
        double sourceArea=asset.triangles.stream().mapToDouble(Triangle::areaXZ).sum(),clipArea=asset.tiles.values().stream().flatMap(List::stream).mapToDouble(Triangle::areaXZ).sum();
        require(Math.abs(sourceArea-clipArea)<1e-6&&Math.abs(sourceArea-area)<.03,"tile area coverage");
        double seam=0,uvError=0,normalDrift=0;int samples=0,missing=0;
        for(var tile:asset.tiles.keySet())for(boolean xAxis:new boolean[]{true,false}){
            Tile other=new Tile(tile.x()+(xAxis?1:0),tile.z()+(xAxis?0:1));if(!asset.tiles.containsKey(other))continue;
            for(int i=0;i<64;i++){
                double x=xAxis?(tile.x()+1)*16:tile.x()*16+(i+.5)*.25,z=xAxis?tile.z()*16+(i+.5)*.25:(tile.z()+1)*16;
                var a=GeometryCheck.at(asset.tiles.get(tile),x,z);var b=GeometryCheck.at(asset.tiles.get(other),x,z);if(a==null||b==null)continue;
                var c=GeometryCheck.at(baked.get(tile),x,z);var d=GeometryCheck.at(baked.get(other),x,z);if(c==null||d==null){missing++;continue;}
                samples++;seam=Math.max(seam,Math.abs(GeometryCheck.height(c,x,z)-GeometryCheck.height(d,x,z)));
                normalDrift=Math.max(normalDrift,Math.max(GeometryCheck.distance(a.normal(),c.normal()),GeometryCheck.distance(b.normal(),d.normal())));
                for(boolean u:new boolean[]{true,false})uvError=Math.max(uvError,Math.max(Math.abs(GeometryCheck.uv(a,x,z,u)-GeometryCheck.uv(c,x,z,u)),Math.abs(GeometryCheck.uv(b,x,z,u)-GeometryCheck.uv(d,x,z,u))));
            }
        }
        require(samples>5000&&missing==0&&seam<1e-5&&uvError<1e-4&&normalDrift<1e-10,"actual 16m seam "+name+" "+seam+" missing="+missing);
        Map<String,RoadMeshAsset.Cell> columns=new HashMap<>();double collisionError=0,step=0,seamStep=0;int collisionPairs=0;
        for(var c:asset.collision){require(columns.put(c.x()+","+c.z(),c)==null,"duplicate collision");collisionError=Math.max(collisionError,c.error());}
        for(var c:asset.collision)for(boolean xAxis:new boolean[]{true,false}){
            var n=columns.get((c.x()+(xAxis?1:0))+","+(c.z()+(xAxis?0:1)));if(n==null)continue;
            double delta=Math.abs(c.top()-n.top());step=Math.max(step,delta);
            if(Math.floorDiv(c.x(),16)!=Math.floorDiv(n.x(),16)||Math.floorDiv(c.z(),16)!=Math.floorDiv(n.z(),16)){seamStep=Math.max(seamStep,delta);collisionPairs++;}
        }
        require(collisionError<.2&&step<.6,"finite collision budget");
        for(var t:asset.triangles)if(t.pavement()){
            double x=(t.a().x()+t.b().x()+t.c().x())/3,z=(t.a().z()+t.b().z()+t.c().z())/3;
            require(columns.containsKey((int)Math.floor(x)+","+(int)Math.floor(z)),"missing collision support");
        }
        String hash=GeometryCheck.fingerprint(asset);require(hash.equals(GeometryCheck.fingerprint(g.asset(name))),"deterministic model data");
        // Rebuild tiles out of order. Hash each tile's full model data against the original.
        List<Tile> order=new ArrayList<>(asset.tiles.keySet());Collections.shuffle(order,new Random(1984));
        for(var tile:order){require(tileHashes.get(tile).equals(tileHash(asset.bake(tile))),"tile order dependency");}
        JsonObject r=new JsonObject();r.addProperty("case",name);r.addProperty("length",g.plan.length());r.addProperty("sourceTriangles",asset.triangles.size());r.addProperty("sourceVertices",asset.sourceStoredVertices);
        r.addProperty("tiles",asset.tiles.size());r.addProperty("bakedTriangles",tris);r.addProperty("bakedCorners",corners);r.addProperty("tinyFacesDropped",dropped);r.addProperty("cornerFloatBytes",corners*32);
        r.addProperty("widthErrorM",widthError);r.addProperty("maxGrade",maxGrade);r.addProperty("queryErrorM",projectionError);r.addProperty("normalLengthError",normalError);
        r.addProperty("actual16mSeamSamples",samples);r.addProperty("missingSeamSamples",missing);r.addProperty("maxSeamHeightErrorM",seam);r.addProperty("maxUvDrift",uvError);r.addProperty("canonicalNormalDrift",normalDrift);
        r.addProperty("collisionColumns",asset.collision.size());r.addProperty("collisionErrorM",collisionError);r.addProperty("maxStepM",step);r.addProperty("collisionSeamStepM",seamStep);r.addProperty("collisionSeamPairs",collisionPairs);
        r.addProperty("modelFingerprint",hash);r.addProperty("repeatability",true);
        System.out.printf(Locale.ROOT,"%s source=%d baked=%d tiles=%d seam=%.9fm collision=%.6fm build=%.1fms allChecks=%.1fms%n",name,asset.triangles.size(),tris,asset.tiles.size(),seam,collisionError,setup,(System.nanoTime()-begin)/1e6);
        return r;
    }
    static String tileHash(RoadMeshAsset.BakedTile tile)throws Exception {
        var d=java.security.MessageDigest.getInstance("SHA-256");var buffer=java.nio.ByteBuffer.allocate(8);
        for(var part:tile.model().parts("road"))for(int i=0;i<part.cornerCount();i++)for(int c=0;c<8;c++){buffer.clear();buffer.putDouble(part.value(i,c));d.update(buffer.array());}
        for(var n:tile.sourceNormals())for(double value:new double[]{n.x(),n.y(),n.z()}){buffer.clear();buffer.putDouble(value);d.update(buffer.array());}
        return HexFormat.of().formatHex(d.digest());
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("--meshes")){
            for(String id:List.of("m1b_rural4_export_fixture","m1b_suburban6_export_fixture")){
                Path dir=Path.of(args[1]);try(var mesh=Files.newBufferedReader(dir.resolve(id+".aflmesh.json"));var geo=Files.newBufferedReader(dir.resolve(id+".geo.json"))){
                    var model=com.antaurora.apofirstlight.client.mesh.AflMeshLoader.load(id,mesh,geo);
                    int count=model.parts("road").stream().mapToInt(com.antaurora.apofirstlight.client.mesh.AflMeshPart::triangleEquivalent).sum();
                    require(count==(id.contains("rural4")?376:400),"existing exporter -> existing Java Loader");
                }
            }
            System.out.println("M1B_EXPORTED_LOADER_PASS");return;
        }
        Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        Map<String,RoadSection> sections;try(var r=Files.newBufferedReader(root.resolve("src/dev/highway_mesh_resources/assets/afl_highway_demo/route/sections.json"))){sections=RoadSection.read(r);}
        var four=sections.get("rural4");var six=sections.get("suburban6");
        require(four.width()==44&&four.paved()==25&&four.protection()==60,"four width contract");
        require(six.width()==48&&six.paved()==36&&six.protection()==64,"six width contract");
        var graph=HighwayRouteGraph.forSeed(20261007);String before=signature(graph);require(graph.getNationalTrunks().size()==2,"national graph");
        var p=RouteGraphMeshAdapter.select(graph,"national_trunk_a/main",128,768,four,four);
        require(JSON.toJson(p).equals(JSON.toJson(RouteRoadCodec.decode(JSON.toJson(p)))),"recipe codec roundtrip");
        var q=RouteGraphMeshAdapter.select(graph,"national_trunk_a/main",128,768,six,six);
        require(p.routeId().equals("national_trunk_a")&&p.graphVersion()==HighwayRouteGraph.VERSION,"reference");
        require(p.controls().get(0).x()==graph.getEdgeById(p.edgeId()).orElseThrow().startStation()+.5,"authority coordinate");
        require(JSON.toJson(p).equals(JSON.toJson(RouteGraphMeshAdapter.select(HighwayRouteGraph.build(20261007),p.edgeId(),128,768,four,four))),"graph recipe determinism");
        var pts=List.of(new RoadAlignment.Point(-512,-512),new RoadAlignment.Point(0,-512),new RoadAlignment.Point(512,0));
        var broad=List.of(new RoadAlignment.Corridor(pts.get(0),pts.get(2),600));
        var fixture=new RouteRoadGeometry.Plan(1,0,0,"OFFLINE_CURVE_FIXTURE","not_a_national_route","fixture_start","fixture_end",0,pts,broad,64,1024,four,six,256,256,1024,.03,List.of("OFFLINE_APPROVED_EMPTY_GEOMETRY_DOMAIN_ONLY"));
        var curved=new RouteRoadGeometry(fixture);
        double curvatureJump=0,tangentJump=0;
        for(double join:curved.alignment.joins())if(join>1e-4&&join<curved.alignment.length-1e-4){
            var a=curved.alignment.frame(join-1e-5);var b=curved.alignment.frame(join+1e-5);
            curvatureJump=Math.max(curvatureJump,Math.abs(a.curvature()-b.curvature()));
            tangentJump=Math.max(tangentJump,Math.hypot(a.tangent().x()-b.tangent().x(),a.tangent().z()-b.tangent().z()));
        }
        require(curvatureJump<1e-7&&tangentJump<1e-7&&curved.alignment.maxCurvature>0,"C2 curvature transition");
        boolean restricted=false;try{new RoadAlignment(pts,List.of(new RoadAlignment.Corridor(pts.get(0),pts.get(1),32),new RoadAlignment.Corridor(pts.get(1),pts.get(2),32)),30,240,150);}catch(IllegalArgumentException e){restricted=e.getMessage().contains("RESTRICTED");}
        require(restricted,"unsafe corner must reject");
        JsonObject report=new JsonObject();report.addProperty("status","PASS_OFFLINE_ONLY");report.addProperty("clientRun",false);report.addProperty("seed",graph.seed());report.addProperty("graphVersion",HighwayRouteGraph.VERSION);
        report.addProperty("nationalTrunks",graph.getNationalTrunks().size());report.addProperty("graphEdges",graph.edges().size());report.addProperty("seaCrossings",graph.seaCrossings().size());report.addProperty("reservedZones",graph.reservedZones().size());
        report.addProperty("actualTrunksStraight",true);report.addProperty("unsafeCornerRejected",restricted);report.addProperty("curvatureJoinDelta",curvatureJump);report.addProperty("tangentJoinDelta",tangentJump);report.addProperty("fixtureMinRadiusM",1/curved.alignment.maxCurvature);
        JsonArray cases=new JsonArray();cases.add(check("real_trunk_four_768",new RouteRoadGeometry(p)));cases.add(check("real_trunk_six_768",new RouteRoadGeometry(q)));cases.add(check("offline_curve_transition_1024",curved));report.add("cases",cases);
        require(before.equals(signature(graph)),"route graph mutated");
        require(RoadPlanningContext.UNAVAILABLE.sample(0,0).protection()==RoadPlanningContext.Protection.UNKNOWN,"unknown protection");
        Files.writeString(out.resolve("validation.json"),JSON.toJson(report)+"\n");
        Files.writeString(out.resolve("real-route-plan.json"),JSON.toJson(p)+"\n");
        // Only two 32m exporter fixtures, never an asset catalogue per kilometre/chunk.
        JsonArray exports=new JsonArray();
        for(var config:List.of(four,six)){
            var shortPlan=RouteGraphMeshAdapter.select(graph,p.edgeId(),128,32,config,config);
            var g=new RouteRoadGeometry(shortPlan);JsonObject sample=new JsonObject();sample.addProperty("id","m1b_"+config.id()+"_export_fixture");sample.add("plan",JSON.toJsonTree(shortPlan));sample.add("triangles",JSON.toJsonTree(g.triangles()));exports.add(sample);
        }
        Files.writeString(out.resolve("export-input.json"),JSON.toJson(exports)+"\n");
        System.out.println("M1B_OFFLINE_PASS");
    }
}