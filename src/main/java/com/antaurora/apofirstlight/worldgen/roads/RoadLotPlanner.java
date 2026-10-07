package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.spatial.SpatialClaim;
import com.antaurora.apofirstlight.worldgen.structure.StructureSocket;
import com.antaurora.apofirstlight.worldgen.structure.StructureSocketType;
import com.antaurora.apofirstlight.worldgen.structure.StructureTransform;
import com.antaurora.apofirstlight.worldgen.terrain.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;

/** Allocates real, nonoverlapping planning parcels. Never loads chunks, NBT, or writes world blocks. */
public final class RoadLotPlanner {
    public record Setbacks(int front,int left,int right,int rear) {}
    public record PlannedBuilding(BoundsXZ bounds, Vec3i size, int groundAnchorOffsetY,
            BlockPos placementOrigin, BlockPos lotLocalOffset, Direction assetLocalFront, Direction worldFront,
            Rotation rotation, StructureSocket mainSocket, BlockPos mainSocketWorld) {}
    public record Connector(String name, StructureSocketType type, String roadId, Direction facing,
            int width, BoundsXZ entryStrip, List<BoundsXZ> internalPaths, int roadSurfaceH16,
            int lotSurfaceH16, List<Integer> curbTransitionH16, BoundsXZ roadAccess,
            BoundsXZ sidewalkContact, Optional<BoundsXZ> asphaltContact) {
        public Connector {
            internalPaths=List.copyOf(internalPaths);curbTransitionH16=List.copyOf(curbTransitionH16);
            Objects.requireNonNull(roadAccess);Objects.requireNonNull(sidewalkContact);Objects.requireNonNull(asphaltContact);
        }
    }
    public record Lot(String id, String use, String variantId, String generationVersion, BoundsXZ fullBounds,
            PlannedBuilding building, List<BoundsXZ> parking, List<BoundsXZ> service, String roadId,
            int groundY, int surfaceH16, Setbacks setbacks, List<Connector> connectors) {
        public Lot { parking=List.copyOf(parking);service=List.copyOf(service);connectors=List.copyOf(connectors); }
    }
    public record Result(List<Lot> lots, Map<String,Integer> rejections) {
        public Result { lots=List.copyOf(lots);rejections=Collections.unmodifiableMap(new TreeMap<>(rejections)); }
    }
    private record Candidate(String id, RoadLotCatalog.Variant variant, BoundsXZ bounds,
                             Direction front, RoadPlan.Edge road) {}
    /** Reserved X/Z connection, not a built ramp or an approved full driveway height profile. */
    private record Access(BoundsXZ bounds, BoundsXZ sidewalk, Optional<BoundsXZ> asphalt) {}
    private static final int MAX_CANDIDATES=2048;
    private RoadLotPlanner() {}

    public static Result allocate(long seed, String planId, BoundsXZ candidateBounds, List<RoadPlan.Node> nodes,
            List<RoadPlan.Edge> edges, TerrainQuery terrain, TerrainSource source,
            List<SpatialClaim> protectionClaims, int maxLots) {
        RoadLotCatalog.Catalog catalog=RoadLotCatalog.load();
        return allocate(seed,planId,candidateBounds,nodes,edges,terrain,source,protectionClaims,maxLots,
                catalog.terrainStep(),catalog.maxHeightDifference());
    }
    public static Result allocate(long seed, String planId, BoundsXZ candidateBounds, List<RoadPlan.Node> nodes,
            List<RoadPlan.Edge> edges, TerrainQuery terrain, TerrainSource source,
            List<SpatialClaim> protectionClaims, int maxLots, int sampleStep, int maxCutFill) {
        if(maxLots<0||maxLots>64||edges.size()>64||nodes.size()>128) throw new IllegalArgumentException("Lot planning budget exceeded");
        if(sampleStep<1||sampleStep>8||maxCutFill<0||maxCutFill>3) throw new IllegalArgumentException("Lot terrain budget invalid");
        Objects.requireNonNull(terrain);Objects.requireNonNull(source);
        RoadLotCatalog.Catalog bundled=RoadLotCatalog.load();
        RoadLotCatalog.Catalog catalog=new RoadLotCatalog.Catalog(bundled.revision(),bundled.fingerprint(),bundled.gap(),
                bundled.nodeClearance(),Math.min(sampleStep,bundled.terrainStep()),
                Math.min(maxCutFill,bundled.maxHeightDifference()),bundled.variants());
        List<RoadPlan.Edge> sortedEdges=edges.stream().sorted(Comparator.comparing(RoadPlan.Edge::id)).toList();
        List<RoadPlan.Node> sortedNodes=nodes.stream().sorted(Comparator.comparing(RoadPlan.Node::id)).toList();
        List<Lot> lots=new ArrayList<>();Map<String,Integer> rejected=new TreeMap<>();int attempts=0;
        // Corner recipes are evaluated at the two actual perpendicular roads, never on an imagined side street.
        for(RoadPlan.Node node:sortedNodes) for(Rotation rotation:Rotation.values()) {
            if(lots.size()>=maxLots) return new Result(lots,rejected);
            RoadPlan.Edge primary=armEdge(node,rotation.rotate(Direction.EAST),sortedEdges);
            RoadPlan.Edge secondary=armEdge(node,rotation.rotate(Direction.SOUTH),sortedEdges);
            if(primary==null||secondary==null||primary.groundY()!=secondary.groundY()) continue;
            for(RoadLotCatalog.Variant variant:catalog.variants()) {
                if(lots.size()>=maxLots) return new Result(lots,rejected);
                if(attempts>=MAX_CANDIDATES) { reject(rejected,"LOT_CANDIDATE_BUDGET");return new Result(lots,rejected); }
                if(!variant.corner()||!variant.roads().contains(primary.type())
                        ||(secondary.type()!=RoadType.R12&&secondary.type()!=RoadType.C14)) continue;
                Direction front=rotation.rotate(Direction.NORTH);
                int p=primary.type().rightOfWayWidth()/2,s=secondary.type().rightOfWayWidth()/2;
                BoundsXZ bounds=switch(front) {
                    case NORTH -> new BoundsXZ(node.x()+s,node.z()+p,node.x()+s+variant.width(),node.z()+p+variant.depth());
                    case EAST -> new BoundsXZ(node.x()-p-variant.depth(),node.z()+s,node.x()-p,node.z()+s+variant.width());
                    case SOUTH -> new BoundsXZ(node.x()-s-variant.width(),node.z()-p-variant.depth(),node.x()-s,node.z()-p);
                    case WEST -> new BoundsXZ(node.x()+p,node.z()-s-variant.width(),node.x()+p+variant.depth(),node.z()-s);
                    default -> throw new IllegalStateException("Vertical frontage");
                };
                Candidate candidate=new Candidate(planId+"/corner/"+node.id()+"/"+rotation+"/"+variant.id(),variant,bounds,front,primary);
                Lot lot=accept(candidate,catalog,candidateBounds,sortedNodes,sortedEdges,lots,terrain,source,protectionClaims,rejected);
                attempts++;if(lot!=null) lots.add(lot);
            }
        }
        for(RoadPlan.Edge edge:sortedEdges) {
            boolean horizontal=edge.z1()==edge.z2();
            int low=horizontal?Math.min(edge.x1(),edge.x2()):Math.min(edge.z1(),edge.z2());
            int high=horizontal?Math.max(edge.x1(),edge.x2()):Math.max(edge.z1(),edge.z2());
            for(Direction front:horizontal?List.of(Direction.NORTH,Direction.SOUTH):List.of(Direction.EAST,Direction.WEST)) {
                int cursor=low;
                while(cursor<high && lots.size()<maxLots && attempts<MAX_CANDIDATES) {
                    final int station=cursor;
                    List<RoadLotCatalog.Variant> choices=catalog.variants().stream()
                            .filter(v->!v.corner()&&v.roads().contains(edge.type()))
                            .sorted(Comparator.comparingDouble(v->rank(seed,planId,edge.id(),front,station,v)))
                            .toList();
                    boolean placed=false;
                    for(RoadLotCatalog.Variant variant:choices) {
                        if(cursor+variant.width()>high) continue;
                        BoundsXZ bounds=beside(edge,front,cursor,variant.width(),variant.depth());
                        Candidate candidate=new Candidate(planId+"/lot/"+edge.id()+"/"+front+"/"+cursor+"/"+variant.id(),variant,bounds,front,edge);
                        attempts++;
                        Lot lot=accept(candidate,catalog,candidateBounds,sortedNodes,sortedEdges,lots,terrain,source,protectionClaims,rejected);
                        if(lot!=null) { lots.add(lot);cursor+=variant.width()+catalog.gap();placed=true;break; }
                        if(attempts>=MAX_CANDIDATES) break;
                    }
                    if(!placed) cursor+=4;
                }
            }
        }
        if(attempts>=MAX_CANDIDATES) reject(rejected,"LOT_CANDIDATE_BUDGET");
        return new Result(lots,rejected);
    }

    private static Lot accept(Candidate c,RoadLotCatalog.Catalog catalog,BoundsXZ candidateBounds,
            List<RoadPlan.Node> nodes,List<RoadPlan.Edge> edges,List<Lot> lots,TerrainQuery terrain,
            TerrainSource source,List<SpatialClaim> protectionClaims,Map<String,Integer> rejected) {
        if(!RoadLotCatalog.contains(candidateBounds,c.bounds())) return reject(rejected,"LOT_OUTSIDE_CANDIDATE");
        for(RoadPlan.Edge edge:edges) if(c.bounds().intersects(edge.corridor())) return reject(rejected,"LOT_ROAD_OVERLAP");
        for(RoadPlan.Node node:nodes) for(BoundsXZ part:node.footprint())
            if(c.bounds().intersects(part)) return reject(rejected,"LOT_NODE_OVERLAP");
        for(SpatialClaim claim:protectionClaims) if(c.bounds().intersects(claim.boundsXZ().expand(claim.exclusionMargin())))
            return reject(rejected,"LOT_PROTECTED_CLAIM");
        for(Lot lot:lots) if(c.bounds().intersects(lot.fullBounds().expand(catalog.gap()))) return reject(rejected,"LOT_PARCEL_OVERLAP");
        RoadLotCatalog.Variant v=c.variant();Rotation rotation=rotation(c.front());
        BlockPos lotOrigin=origin(c.bounds(),v.width(),v.depth(),0,rotation);
        List<Connector> connectors=new ArrayList<>();
        for(RoadLotCatalog.Entrance entrance:v.entrances()) {
            BoundsXZ mouth=transform(mouth(v,entrance),rotation,lotOrigin);
            Direction facing=rotation.rotate(entrance.side());
            BoundsXZ outside=shift(mouth,facing.getStepX(),facing.getStepZ());
            RoadPlan.Edge road=edges.stream().filter(e->RoadLotCatalog.contains(e.corridor(),outside)
                    && ((facing.getAxis()==Direction.Axis.Z)==(e.z1()==e.z2())))
                    .findFirst().orElse(null);
            if(road==null||road.groundY()!=c.road().groundY()) return reject(rejected,"LOT_ENTRANCE_NO_ROAD");
            if(entrance.side()==Direction.NORTH && !road.id().equals(c.road().id()))
                return reject(rejected,"LOT_FRONTAGE_OWNER_MISMATCH");
            for(RoadPlan.Node node:nodes) if(node.arms().stream().anyMatch(a->a.edgeId().equals(road.id()))) {
                for(BoundsXZ part:node.footprint()) if(axisGap(mouth,part,facing.getAxis()==Direction.Axis.Z)<catalog.nodeClearance())
                    return reject(rejected,"LOT_ENTRANCE_NODE_CLEARANCE");
            }
            boolean pedestrian=entrance.type()==StructureSocketType.PEDESTRIAN;
            Access access=access(mouth,facing,road,pedestrian);
            if(access==null) return reject(rejected,"LOT_ENTRANCE_CROSS_SECTION_MISMATCH");
            for(RoadPlan.Node node:nodes) for(BoundsXZ part:node.footprint())
                if(access.bounds().intersects(part)) return reject(rejected,"LOT_ACCESS_NODE_OVERLAP");
            for(SpatialClaim claim:protectionClaims)
                if(access.bounds().intersects(claim.boundsXZ().expand(claim.exclusionMargin())))
                    return reject(rejected,"LOT_ACCESS_PROTECTED_CLAIM");
            int g16=Math.multiplyExact(road.groundY(),16),s16=road.surfaceH16();
            connectors.add(new Connector(entrance.name(),entrance.type(),road.id(),facing,entrance.width(),mouth,
                    entrance.paths().stream().map(p->transform(p,rotation,lotOrigin)).toList(),pedestrian?g16:s16,
                    pedestrian?g16:s16,pedestrian?List.of():List.of(s16+1,s16+2,g16),
                    access.bounds(),access.sidewalk(),access.asphalt()));
        }
        String terrainFailure=terrainFailure(c.bounds(),c.road().groundY(),catalog,terrain,source);
        if(terrainFailure!=null) return reject(rejected,terrainFailure);
        RoadLotCatalog.Building b=v.building();BoundsXZ body=transform(b.bounds(),rotation,lotOrigin);
        Vec3i size=new Vec3i((int)b.bounds().width(),b.height(),(int)b.bounds().depth());
        int groundY=c.road().groundY();
        BlockPos placement=origin(body,size.getX(),size.getZ(),StructureTransform.originY(groundY,b.groundAnchor()),rotation);
        StructureSocket socket=new StructureSocket("planned_main",new BlockPos(b.entranceX(),b.groundAnchor(),0),
                Direction.NORTH,StructureSocketType.PEDESTRIAN);
        PlannedBuilding building=new PlannedBuilding(body,size,b.groundAnchor(),placement,
                new BlockPos(b.bounds().minX(),-b.groundAnchor(),b.bounds().minZ()),Direction.NORTH,c.front(),rotation,
                socket,StructureTransform.world(socket.localPosition(),rotation,placement));
        Setbacks setbacks=new Setbacks(b.bounds().minZ(),b.bounds().minX(),v.width()-b.bounds().maxXExclusive(),
                v.depth()-b.bounds().maxZExclusive());
        Lot result=new Lot(c.id(),v.use(),v.id(),RoadPlanner.VERSION,c.bounds(),building,
                v.parking().stream().map(p->transform(p,rotation,lotOrigin)).toList(),
                v.service().stream().map(p->transform(p,rotation,lotOrigin)).toList(),c.road().id(),groundY,
                c.road().surfaceH16(),setbacks,connectors);
        return rotatedLotConstraints(result)?result:reject(rejected,"LOT_ROTATED_GEOMETRY_CONSTRAINT");
    }

    /** Connect to the correct outer sidewalk; vehicles additionally reserve a crossing through every side band. */
    private static Access access(BoundsXZ mouth,Direction facing,RoadPlan.Edge road,boolean pedestrian) {
        RoadCrossSection section=road.crossSection();
        if(!section.corridorBounds().equals(road.corridor())) return null;
        BoundsXZ outside=shift(mouth,facing.getStepX(),facing.getStepZ());
        BoundsXZ sidewalk=section.sidewalks().stream().filter(b->RoadLotCatalog.contains(b,outside)).findFirst().orElse(null);
        if(sidewalk==null) return null;
        BoundsXZ sidewalkContact=alongMouth(mouth,sidewalk,facing);
        if(!RoadLotCatalog.contains(sidewalk,sidewalkContact)) return null;
        if(pedestrian) return new Access(sidewalkContact,sidewalkContact,Optional.empty());
        BoundsXZ asphalt=section.asphalt();
        BoundsXZ asphaltContact=switch(facing) {
            case NORTH -> new BoundsXZ(mouth.minX(),asphalt.maxZExclusive()-1,mouth.maxXExclusive(),asphalt.maxZExclusive());
            case SOUTH -> new BoundsXZ(mouth.minX(),asphalt.minZ(),mouth.maxXExclusive(),asphalt.minZ()+1);
            case EAST -> new BoundsXZ(asphalt.minX(),mouth.minZ(),asphalt.minX()+1,mouth.maxZExclusive());
            case WEST -> new BoundsXZ(asphalt.maxXExclusive()-1,mouth.minZ(),asphalt.maxXExclusive(),mouth.maxZExclusive());
            default -> throw new IllegalArgumentException("Vertical frontage");
        };
        BoundsXZ roadAccess=new BoundsXZ(Math.min(outside.minX(),asphaltContact.minX()),
                Math.min(outside.minZ(),asphaltContact.minZ()),Math.max(outside.maxXExclusive(),asphaltContact.maxXExclusive()),
                Math.max(outside.maxZExclusive(),asphaltContact.maxZExclusive()));
        if(!RoadLotCatalog.contains(asphalt,asphaltContact)||!RoadLotCatalog.contains(section.corridorBounds(),roadAccess)
                ||!RoadLotCatalog.contains(roadAccess,sidewalkContact)
                ||section.curbs().stream().noneMatch(roadAccess::intersects)
                ||section.utilities().stream().noneMatch(roadAccess::intersects)) return null;
        return new Access(roadAccess,sidewalkContact,Optional.of(asphaltContact));
    }
    private static BoundsXZ alongMouth(BoundsXZ mouth,BoundsXZ band,Direction facing) {
        return facing.getAxis()==Direction.Axis.Z
                ?new BoundsXZ(mouth.minX(),band.minZ(),mouth.maxXExclusive(),band.maxZExclusive())
                :new BoundsXZ(band.minX(),mouth.minZ(),band.maxXExclusive(),mouth.maxZExclusive());
    }
    /** Check the emitted world geometry too; a valid local catalog must remain valid under all four rotations. */
    private static boolean rotatedLotConstraints(Lot lot) {
        PlannedBuilding building=lot.building();
        if(!RoadLotCatalog.contains(lot.fullBounds(),building.bounds())
                ||!StructureTransform.bounds(building.size(),building.rotation(),building.placementOrigin()).xz().equals(building.bounds())
                ||building.mainSocketWorld().getY()!=lot.groundY()
                ||StructureTransform.facing(building.mainSocket().facing(),building.rotation())!=building.worldFront()
                ||lot.setbacks().front()<0||lot.setbacks().left()<0||lot.setbacks().right()<0||lot.setbacks().rear()<0)
            return false;
        List<BoundsXZ> occupied=new ArrayList<>();occupied.add(building.bounds());
        List<BoundsXZ> zones=new ArrayList<>(lot.parking());zones.addAll(lot.service());
        for(BoundsXZ zone:zones) {
            if(!RoadLotCatalog.contains(lot.fullBounds(),zone)||occupied.stream().anyMatch(zone::intersects)) return false;
            occupied.add(zone);
        }
        for(Connector connector:lot.connectors()) {
            BoundsXZ mouth=connector.entryStrip();
            long width=connector.facing().getAxis()==Direction.Axis.Z?mouth.width():mouth.depth();
            if(width!=connector.width()||!RoadLotCatalog.contains(lot.fullBounds(),mouth)
                    ||!RoadLotCatalog.contains(connector.roadAccess(),shift(mouth,connector.facing().getStepX(),connector.facing().getStepZ()))
                    ||connector.roadAccess().intersects(lot.fullBounds())) return false;
            for(BoundsXZ path:connector.internalPaths())
                if(!RoadLotCatalog.contains(lot.fullBounds(),path)||occupied.stream().anyMatch(path::intersects)) return false;
            if(connector.internalPaths().isEmpty()||!RoadLotCatalog.contains(connector.internalPaths().get(0),mouth)) return false;
        }
        BlockPos outsideDoor=building.mainSocketWorld().relative(building.worldFront());
        return lot.connectors().stream().filter(c->c.type()==StructureSocketType.PEDESTRIAN)
                .flatMap(c->c.internalPaths().stream()).anyMatch(p->p.contains(outsideDoor.getX(),outsideDoor.getZ()));
    }

    private static String terrainFailure(BoundsXZ bounds,int groundY,RoadLotCatalog.Catalog catalog,TerrainQuery query,TerrainSource source) {
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int x:stations(bounds.minX(),bounds.maxXExclusive()-1,catalog.terrainStep()))
            for(int z:stations(bounds.minZ(),bounds.maxZExclusive()-1,catalog.terrainStep())) {
                TerrainSample sample=query.sample(x,z,source);
                if(sample==null||sample.source()!=source||sample.validity()!=TerrainValidity.VALID
                        ||sample.surfaceY().isEmpty()||sample.surfaceType()==SurfaceType.UNKNOWN)
                    return "LOT_TERRAIN_UNKNOWN";
                if(!sample.hasKnownNoFluid()||sample.surfaceType()!=SurfaceType.SOLID) return "LOT_TERRAIN_WATER_OR_ICE";
                if(sample.protection()==ProtectionKnowledge.KNOWN_PROTECTED) return "LOT_TERRAIN_PROTECTED";
                int y=sample.surfaceY().getAsInt();min=Math.min(min,y);max=Math.max(max,y);
                if(Math.abs((long)y-groundY)>catalog.maxHeightDifference()||(long)max-min>catalog.maxHeightDifference())
                    return "LOT_TERRAIN_CUT_FILL_BUDGET";
            }
        return null;
    }
    private static List<Integer> stations(int min,int max,int step) { List<Integer> result=new ArrayList<>();
        for(int value=min;value<max;value+=step) result.add(value);result.add(max);return result; }
    private static RoadPlan.Edge armEdge(RoadPlan.Node n,Direction direction,List<RoadPlan.Edge> edges) {
        String id=n.arms().stream().filter(a->a.direction()==direction).map(RoadPlan.Arm::edgeId).findFirst().orElse(null);
        return edges.stream().filter(e->e.id().equals(id)).findFirst().orElse(null);
    }
    private static BoundsXZ beside(RoadPlan.Edge e,Direction front,int station,int width,int depth) {
        return switch(front) {
            case NORTH -> new BoundsXZ(station,e.corridor().maxZExclusive(),station+width,e.corridor().maxZExclusive()+depth);
            case SOUTH -> new BoundsXZ(station,e.corridor().minZ()-depth,station+width,e.corridor().minZ());
            case WEST -> new BoundsXZ(e.corridor().maxXExclusive(),station,e.corridor().maxXExclusive()+depth,station+width);
            case EAST -> new BoundsXZ(e.corridor().minX()-depth,station,e.corridor().minX(),station+width);
            default -> throw new IllegalArgumentException("Vertical frontage");
        };
    }
    private static BoundsXZ mouth(RoadLotCatalog.Variant v,RoadLotCatalog.Entrance e) {
        return switch(e.side()) {
            case NORTH -> new BoundsXZ(e.offset(),0,e.offset()+e.width(),1);
            case SOUTH -> new BoundsXZ(e.offset(),v.depth()-1,e.offset()+e.width(),v.depth());
            case WEST -> new BoundsXZ(0,e.offset(),1,e.offset()+e.width());
            case EAST -> new BoundsXZ(v.width()-1,e.offset(),v.width(),e.offset()+e.width());
            default -> throw new IllegalArgumentException("Vertical entrance");
        };
    }
    private static Rotation rotation(Direction front) { return switch(front) {
        case NORTH -> Rotation.NONE;case EAST -> Rotation.CLOCKWISE_90;case SOUTH -> Rotation.CLOCKWISE_180;
        case WEST -> Rotation.COUNTERCLOCKWISE_90;default -> throw new IllegalArgumentException("Vertical frontage"); }; }
    private static BlockPos origin(BoundsXZ bounds,int width,int depth,int y,Rotation r) { return switch(r) {
        case NONE -> new BlockPos(bounds.minX(),y,bounds.minZ());
        case CLOCKWISE_90 -> new BlockPos(bounds.minX()+depth-1,y,bounds.minZ());
        case CLOCKWISE_180 -> new BlockPos(bounds.minX()+width-1,y,bounds.minZ()+depth-1);
        case COUNTERCLOCKWISE_90 -> new BlockPos(bounds.minX(),y,bounds.minZ()+width-1);
    }; }
    private static BoundsXZ transform(BoundsXZ local,Rotation rotation,BlockPos origin) {
        BlockPos first=StructureTransform.world(new BlockPos(local.minX(),0,local.minZ()),rotation,origin);
        BlockPos last=StructureTransform.world(new BlockPos(local.maxXExclusive()-1,0,local.maxZExclusive()-1),rotation,origin);
        return new BoundsXZ(Math.min(first.getX(),last.getX()),Math.min(first.getZ(),last.getZ()),
                Math.max(first.getX(),last.getX())+1,Math.max(first.getZ(),last.getZ())+1);
    }
    private static BoundsXZ shift(BoundsXZ b,int x,int z) {
        return new BoundsXZ(b.minX()+x,b.minZ()+z,b.maxXExclusive()+x,b.maxZExclusive()+z); }
    private static int axisGap(BoundsXZ a,BoundsXZ b,boolean xAxis) {
        int a0=xAxis?a.minX():a.minZ(),a1=xAxis?a.maxXExclusive():a.maxZExclusive();
        int b0=xAxis?b.minX():b.minZ(),b1=xAxis?b.maxXExclusive():b.maxZExclusive();
        return Math.max(0,Math.max(a0-b1,b0-a1));
    }
    private static Lot reject(Map<String,Integer> rejections,String reason) { rejections.merge(reason,1,Integer::sum);return null; }
    private static double rank(long seed,String planId,String edge,Direction side,int station,RoadLotCatalog.Variant variant) {
        long h=seed;String key=planId+"/"+edge+"/"+side+"/"+station+"/"+variant.id();
        for(int i=0;i<key.length();i++) h=(h^key.charAt(i))*0x100000001b3L;
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;h=(h^(h>>>27))*0x94d049bb133111ebL;h^=h>>>31;
        double u=((h>>>11)+1.0)*0x1.0p-53;
        return -StrictMath.log(u)/variant.weight();
    }
}
