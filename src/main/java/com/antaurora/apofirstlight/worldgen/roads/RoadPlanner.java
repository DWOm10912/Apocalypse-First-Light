package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.*;
import com.antaurora.apofirstlight.worldgen.terrain.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Finite axis-aligned graph -> explicit junctions -> lots; no chunk access or block placement. */
public final class RoadPlanner {
    public static final String VERSION = "north_american_roads_v1a_2";
    private static final ResourceLocation OWNER = new ResourceLocation("apocalypse_firstlight", "local_roads");
    private RoadPlanner() {}

    public static BoundsXZ candidateBounds(int x, int z) { return candidateBounds(x,z,RoadPlanningConfig.DEFAULT); }
    private static BoundsXZ candidateBounds(int x,int z,RoadPlanningConfig c) {
        if (Math.abs((long)x)>29_999_000 || Math.abs((long)z)>29_999_000)
            throw new IllegalArgumentException("Candidate outside safe world coordinates");
        int h=c.candidateSize()/2;
        return new BoundsXZ(x-h,z-h,x+h,z+h);
    }
    public static RoadPlan plan(long seed,String candidateId,int centerX,int centerZ,RoadPlan.Layout layout,
            TerrainQuery terrain,TerrainSource source,List<SpatialClaim> blockers) {
        return plan(seed,candidateId,centerX,centerZ,layout,terrain,source,blockers,RoadPlanningConfig.DEFAULT);
    }
    public static RoadPlan plan(long seed,String candidateId,int centerX,int centerZ,RoadPlan.Layout layout,
            TerrainQuery terrain,TerrainSource source,List<SpatialClaim> blockers,RoadPlanningConfig config) {
        Objects.requireNonNull(layout); Objects.requireNonNull(terrain); Objects.requireNonNull(source);
        Objects.requireNonNull(blockers); Objects.requireNonNull(config);
        if(layout==RoadPlan.Layout.SEGMENT)throw new IllegalArgumentException("SEGMENT requires the development preset adapter");
        if (candidateId==null || !candidateId.matches("[a-zA-Z0-9_:/.,-]{1,128}"))
            throw new IllegalArgumentException("Invalid candidate ID");
        BoundsXZ area=candidateBounds(centerX,centerZ,config);
        String key=seed+":"+candidateId+":"+centerX+":"+centerZ+":"+layout+":"+VERSION+":"+config
                +":"+source+":"+RoadLotCatalog.fingerprint();
        String id="roads:"+digest(key);
        long salt=Long.parseUnsignedLong(digest(key).substring(0,16),16);
        if(blockers.size()>4096) return rejected(id,candidateId,layout,area,List.of("BLOCKER_CLAIM_BUDGET"),0,0);
        // Normalize through the public generic publication API; these claims cannot certify world coverage.
        var ordered=new ClaimQueryResult(blockers,ClaimQueryCompleteness.UNKNOWN,List.of(),blockers.size());
        if(!ordered.failures().isEmpty()) return rejected(id,candidateId,layout,area,List.of("INVALID_CLAIM_SET"),0,0);
        List<SpatialClaim> obstacles=ordered.claims().stream().filter(c->c.dimension().equals(Level.OVERWORLD)).toList();
        var samples=new Samples(terrain,source,config.terrainBudget());
        List<String> reasons=new ArrayList<>();
        for(int attempt=0;attempt<config.attempts();attempt++) {
            int dx=(int)Math.floorMod(salt+attempt*13L,3L)*16-16;
            int dz=(int)Math.floorMod(Long.rotateLeft(salt,17)+attempt*7L,3L)*16-16;
            int turn=(int)Math.floorMod(salt+attempt,4L);
            try {
                Graph graph=graph(id,segments(layout,centerX+dx,centerZ+dz,turn));
                if(layout==RoadPlan.Layout.T && (graph.edges.size()!=3 || graph.nodes.size()!=4
                        || graph.nodes.stream().filter(n->n.kind()==RoadPlan.NodeKind.T_JUNCTION).count()!=1
                        || graph.nodes.stream().filter(n->n.kind()==RoadPlan.NodeKind.END).count()!=3))
                    throw new Rejected("T_PRESET_INVALID_GRAPH");
                if(graph.edges.size()>config.maxEdges()) throw new Rejected("EDGE_BUDGET");
                BoundsXZ network=new BoundsXZ(centerX-config.networkSize()/2,centerZ-config.networkSize()/2,
                        centerX+config.networkSize()/2,centerZ+config.networkSize()/2);
                for(var e:graph.edges) if(!contains(network,e.corridor())) throw new Rejected("NETWORK_BOUNDS");
                for(var n:graph.nodes) for(var b:n.footprint()) if(!contains(network,b)) throw new Rejected("NODE_BOUNDS");
                List<BoundsXZ> surfaces=new ArrayList<>();
                graph.edges.forEach(e->surfaces.add(e.corridor())); graph.nodes.forEach(n->surfaces.addAll(n.footprint()));
                for(var surface:surfaces) for(var obstacle:obstacles)
                    if(surface.intersects(obstacle.boundsXZ().expand(obstacle.exclusionMargin())))
                        throw new Rejected("CLAIM_CONFLICT:"+obstacle.owner()+":"+obstacle.id());
                List<Integer> heights=new ArrayList<>();
                for(var bounds:surfaces) scan(bounds,config.sampleStep(),samples,heights);
                if(heights.isEmpty()) throw new Rejected("TERRAIN_EMPTY");
                heights.sort(Integer::compareTo);
                int g=heights.get(heights.size()/2);
                if((long)g-heights.get(0)>config.maxCutFill()
                        ||(long)heights.get(heights.size()-1)-g>config.maxCutFill())
                    throw new Rejected("NETWORK_RELIEF_EXCEEDS_FLAT_PLAN_BUDGET");
                List<RoadPlan.Node> nodes=graph.nodes.stream().map(n->new RoadPlan.Node(n.id(),n.x(),n.z(),g,n.kind(),n.arms(),n.footprint())).toList();
                List<RoadPlan.Edge> edges=graph.edges.stream().map(e->new RoadPlan.Edge(e.id(),e.from(),e.to(),e.type(),e.x1(),e.z1(),e.x2(),e.z2(),g,e.corridor())).toList();
                var lots=RoadLotPlanner.allocate(seed,id,area,nodes,edges,samples,source,obstacles,config.maxLots(),
                        config.sampleStep(),config.maxCutFill());
                if(lots.lots().isEmpty()) throw new Rejected("NO_LEGAL_LOTS:"+lots.rejections());
                List<SpatialClaim> claims=new ArrayList<>();
                // Graph surfaces overlap at junctions. Claims partition that union into disjoint tiles.
                var tiles=unionTiles(surfaces);
                int i=0;for(var tile:tiles) claims.add(claim(id+":road:"+(i++),tile.bounds(),SpatialClaimType.INFRASTRUCTURE));
                // Lots publish their reservations through the same generic claim value as infrastructure.
                for(var lot:lots.lots()) claims.add(claim(lot.id(),lot.fullBounds(),SpatialClaimType.BUILDING));
                List<String> notes=new ArrayList<>(reasons);
                notes.add("TERRAIN_SOURCE="+source);
                notes.add("TERRAIN_QUALIFICATION=SAMPLED_BASE_TERRAIN_ONLY");
                notes.add("HEIGHT_MODE=FLAT_NETWORK_G;S_H16=16G-3");
                notes.add("CROSS_SECTION=ASPHALT_PLUS_CURB_UTILITY_SIDEWALK;TOTAL_WIDTH_IS_RIGHT_OF_WAY");
                notes.add("GEOMETRY=FULL_ROW;NODE_PARTITIONS;NO_NODE_OVERLAP;LOT_ROTATION_AND_ACCESS_CHECKED");
                notes.add("BLOCKER_CLAIMS="+obstacles.size());
                notes.add("PROTECTION=KNOWN_CLAIMS_CHECKED;UNPUBLISHED_WORLD_CONTENT_UNVERIFIED");
                notes.add("CONSTRUCTION=NOT_IMPLEMENTED;WORLDGEN_REGISTRATION=NONE");
                notes.add("LOT_REJECTIONS="+lots.rejections());
                return new RoadPlan(VERSION,id,candidateId,layout,RoadPlan.Status.PLANNED,area,nodes,edges,
                        lots.lots(),claims,notes,samples.size(),attempt+1,true);
            } catch(Rejected rejected) { reasons.add("ATTEMPT_"+(attempt+1)+":"+rejected.getMessage()); }
        }
        return rejected(id,candidateId,layout,area,reasons,samples.size(),config.attempts());
    }
    private static RoadPlan rejected(String id,String candidate,RoadPlan.Layout layout,BoundsXZ area,
                                      List<String> reasons,int samples,int attempts) {
        return new RoadPlan(VERSION,id,candidate,layout,RoadPlan.Status.REJECTED,area,List.of(),List.of(),
                List.of(),List.of(),reasons,samples,attempts,false);
    }
    private static SpatialClaim claim(String id,BoundsXZ b,SpatialClaimType type) {
        return new SpatialClaim(id,OWNER,Level.OVERWORLD,VERSION,b,Optional.empty(),type,SpatialClaimStrength.HARD,
                ClaimPriorityPolicy.defaultPriorityFor(type,SpatialClaimStrength.HARD),0,List.of());
    }
    private static void scan(BoundsXZ b,int step,Samples source,List<Integer> result) {
        for(int x=b.minX();;x=Math.min(x+step,b.maxXExclusive()-1)) {
            for(int z=b.minZ();;z=Math.min(z+step,b.maxZExclusive()-1)) {
                TerrainSample s=source.sample(x,z,source.source);
                if(s.validity()!=TerrainValidity.VALID || s.surfaceY().isEmpty()) throw new Rejected("TERRAIN_UNKNOWN_OR_INVALID");
                if(!s.hasKnownNoFluid() || s.surfaceType()!=SurfaceType.SOLID) throw new Rejected("TERRAIN_WATER_OR_NON_SOLID");
                if(s.protection()==ProtectionKnowledge.KNOWN_PROTECTED) throw new Rejected("TERRAIN_PROTECTED");
                result.add(s.surfaceY().getAsInt());
                if(z==b.maxZExclusive()-1)break;
            }
            if(x==b.maxXExclusive()-1)break;
        }
    }
    private static boolean contains(BoundsXZ outer,BoundsXZ b) {
        return b.minX()>=outer.minX()&&b.minZ()>=outer.minZ()&&b.maxXExclusive()<=outer.maxXExclusive()&&b.maxZExclusive()<=outer.maxZExclusive();
    }
    private static List<Rect> unionTiles(List<BoundsXZ> surfaces) {
        TreeSet<Integer> cuts=new TreeSet<>();
        for(var b:surfaces) { cuts.add(b.minX()); cuts.add(b.maxXExclusive()); }
        List<Integer> xs=List.copyOf(cuts);
        List<Rect> result=new ArrayList<>();
        for(int i=1;i<xs.size();i++) {
            int x=xs.get(i-1),ex=xs.get(i);
            List<BoundsXZ> spans=surfaces.stream().filter(b->b.minX()<ex&&b.maxXExclusive()>x)
                    .sorted(Comparator.comparingInt(BoundsXZ::minZ).thenComparingInt(BoundsXZ::maxZExclusive)).toList();
            if(spans.isEmpty())continue;
            int z=spans.get(0).minZ(),ez=spans.get(0).maxZExclusive();
            for(int j=1;j<spans.size();j++) {
                var b=spans.get(j);
                if(b.minZ()<=ez)ez=Math.max(ez,b.maxZExclusive());
                else { result.add(new Rect(x,z,ex,ez)); z=b.minZ(); ez=b.maxZExclusive(); }
            }
            result.add(new Rect(x,z,ex,ez));
        }
        return List.copyOf(result);
    }
    static String digest(String s) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private record Point(int x,int z) implements Comparable<Point> {
        public int compareTo(Point p){int c=Integer.compare(x,p.x);return c!=0?c:Integer.compare(z,p.z);}
    }
    private record Segment(Point a,Point b,RoadType type) {
        boolean horizontal(){return a.z==b.z;}
        boolean has(Point p){return p.x>=Math.min(a.x,b.x)&&p.x<=Math.max(a.x,b.x)
                &&p.z>=Math.min(a.z,b.z)&&p.z<=Math.max(a.z,b.z);}
    }
    private record Rect(int x,int z,int ex,int ez) implements Comparable<Rect> {
        BoundsXZ bounds(){return new BoundsXZ(x,z,ex,ez);}
        public int compareTo(Rect r){int c=Integer.compare(x,r.x);if(c==0)c=Integer.compare(z,r.z);if(c==0)c=Integer.compare(ex,r.ex);return c==0?Integer.compare(ez,r.ez):c;}
    }
    private record Graph(List<RoadPlan.Node> nodes,List<RoadPlan.Edge> edges) {}
    private static List<Segment> segments(RoadPlan.Layout layout,int cx,int cz,int turn) {
        List<Segment> s=new ArrayList<>();
        if(layout==RoadPlan.Layout.RESIDENTIAL) {
            add(s,-144,0,144,0,RoadType.R12);add(s,0,-144,0,144,RoadType.R12);
            add(s,-96,-96,96,-96,RoadType.R12);add(s,-96,-96,-96,0,RoadType.R12);
            add(s,96,-96,96,96,RoadType.R12);add(s,0,96,96,96,RoadType.R12);
        } else if(layout==RoadPlan.Layout.INDUSTRIAL) {
            add(s,-144,0,144,0,RoadType.I12);add(s,0,0,0,144,RoadType.I12);
            add(s,-144,-144,-144,0,RoadType.I12);
        } else if(layout==RoadPlan.Layout.T) {
            // Three connected directions, three widths, no fourth arm. A diagnostic fixture, not a city layout.
            add(s,0,0,152,0,RoadType.C14); add(s,-152,0,0,0,RoadType.I12);
            add(s,0,-152,0,0,RoadType.R12);
        } else {
            // 152 leaves legal entrance space after wider ROW nodes + unchanged 24-block entrance setbacks.
            add(s,-152,0,152,0,RoadType.C14);add(s,0,-152,0,152,RoadType.R12);
            add(s,0,-152,152,-152,RoadType.R12);add(s,152,-152,152,0,layout==RoadPlan.Layout.MIXED?RoadType.I12:RoadType.C14);
            add(s,-152,152,0,152,layout==RoadPlan.Layout.MIXED?RoadType.I12:RoadType.C14);add(s,-152,0,-152,152,RoadType.C14);
        }
        return s.stream().map(e->new Segment(move(e.a,cx,cz,turn),move(e.b,cx,cz,turn),e.type)).toList();
    }
    private static void add(List<Segment>s,int x,int z,int ex,int ez,RoadType t){s.add(new Segment(new Point(x,z),new Point(ex,ez),t));}
    private static Point move(Point p,int x,int z,int r) {
        return switch(r){case 0->new Point(x+p.x,z+p.z);case 1->new Point(x-p.z,z+p.x);case 2->new Point(x-p.x,z-p.z);default->new Point(x+p.z,z-p.x);};
    }
    private static Graph graph(String id,List<Segment> segments) {
        TreeSet<Point> points=new TreeSet<>();
        segments.forEach(s->{points.add(s.a);points.add(s.b);});
        for(Segment a:segments)for(Segment b:segments) if(a.horizontal()!=b.horizontal()) {
            Point cross=a.horizontal()?new Point(b.a.x,a.a.z):new Point(a.a.x,b.a.z);
            if(a.has(cross)&&b.has(cross))points.add(cross);
        }
        List<RoadPlan.Edge> edges=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(Segment s:segments) {
            List<Point> cuts=points.stream().filter(s::has).toList();
            for(int i=1;i<cuts.size();i++) {
                Point a=cuts.get(i-1),b=cuts.get(i);String eid=id+":e:"+a.x+","+a.z+":"+b.x+","+b.z;
                if(!seen.add(eid))throw new Rejected("DUPLICATE_EDGE");
                BoundsXZ bounds=RoadCrossSection.of(a.x,a.z,b.x,b.z,s.type).corridorBounds();
                edges.add(new RoadPlan.Edge(eid,nodeId(id,a),nodeId(id,b),s.type,a.x,a.z,b.x,b.z,0,bounds));
            }
        }
        edges.sort(Comparator.comparing(RoadPlan.Edge::id));
        List<RoadPlan.Node> nodes=new ArrayList<>();
        for(Point p:points) {
            List<RoadPlan.Arm> arms=new ArrayList<>();
            for(var e:edges) {
                if(e.x1()==p.x&&e.z1()==p.z)arms.add(new RoadPlan.Arm(direction(e.x2()-p.x,e.z2()-p.z),e.type(),e.id()));
                else if(e.x2()==p.x&&e.z2()==p.z)arms.add(new RoadPlan.Arm(direction(e.x1()-p.x,e.z1()-p.z),e.type(),e.id()));
            }
            arms.sort(Comparator.comparing(a->a.direction().name()));
            if(arms.isEmpty()||arms.stream().map(RoadPlan.Arm::direction).distinct().count()!=arms.size())throw new Rejected("INVALID_NODE_ARMS");
            RoadPlan.NodeKind kind=switch(arms.size()) {
                case 1->RoadPlan.NodeKind.END;
                case 2->arms.get(0).direction().getOpposite()==arms.get(1).direction()?RoadPlan.NodeKind.STRAIGHT:RoadPlan.NodeKind.TURN;
                case 3->RoadPlan.NodeKind.T_JUNCTION;case 4->RoadPlan.NodeKind.CROSS;default->throw new Rejected("NODE_DEGREE");};
            RoadJunction junction=RoadJunction.of(p.x,p.z,arms);
            nodes.add(new RoadPlan.Node(nodeId(id,p),p.x,p.z,0,kind,arms,junction.footprint()));
        }
        for(int i=0;i<nodes.size();i++) for(int j=i+1;j<nodes.size();j++)
            for(var a:nodes.get(i).footprint()) for(var b:nodes.get(j).footprint())
                if(a.intersects(b))throw new Rejected("NODE_FOOTPRINT_OVERLAP");
        for(var node:nodes) for(var edge:edges)
            if(!edge.from().equals(node.id())&&!edge.to().equals(node.id()))
                for(var part:node.footprint()) if(part.intersects(edge.corridor()))
                    throw new Rejected("NODE_NONINCIDENT_EDGE_OVERLAP");
        Set<String> visited=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(nodes.get(0).id());
        while(!queue.isEmpty()){String n=queue.remove();if(!visited.add(n))continue;for(var e:edges){if(e.from().equals(n))queue.add(e.to());if(e.to().equals(n))queue.add(e.from());}}
        if(visited.size()!=nodes.size())throw new Rejected("DISCONNECTED_GRAPH");
        return new Graph(List.copyOf(nodes),List.copyOf(edges));
    }
    private static Direction direction(int x,int z){return x<0?Direction.WEST:x>0?Direction.EAST:z<0?Direction.NORTH:Direction.SOUTH;}
    private static String nodeId(String id,Point p){return id+":n:"+p.x+","+p.z;}
    private static final class Rejected extends RuntimeException { Rejected(String reason){super(reason);} }
    private static final class Samples implements TerrainQuery {
        final TerrainQuery delegate;final TerrainSource source;final int limit;final Map<Long,TerrainSample> cache=new HashMap<>();
        Samples(TerrainQuery delegate,TerrainSource source,int limit){this.delegate=delegate;this.source=source;this.limit=limit;}
        public TerrainSample sample(int x,int z,TerrainSource requested) {
            if(requested!=source)return TerrainSample.unknown(requested);
            long key=((long)x<<32)^(z&0xffffffffL);TerrainSample old=cache.get(key);if(old!=null)return old;
            if(cache.size()>=limit)return TerrainSample.unknown(source);
            TerrainSample s=delegate.sample(x,z,source);
            if(s==null||s.source()!=source)s=TerrainSample.unknown(source);
            cache.put(key,s);return s;
        }
        int size(){return cache.size();}
    }
}
