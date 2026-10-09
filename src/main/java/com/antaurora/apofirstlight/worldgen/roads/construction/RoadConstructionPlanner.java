package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.roads.*;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.spatial.SpatialClaim;
import com.antaurora.apofirstlight.worldgen.structure.StructureSocketType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Read-only construction preparation on the server thread. No chunk creation, block writes or global cache.
 * A caller must independently establish explicit site authorization; natural blocks cannot prove provenance.
 */
public final class RoadConstructionPlanner {
    public static final String VERSION="north_american_roads_v1b_1";
    private RoadConstructionPlanner() {}
    public static Job begin(ServerLevel level,RoadPlan layout,RoadConstructionConfig config,List<SpatialClaim> claims) {
        return begin(level,layout,config,claims,false);
    }
    /** Optional read-only failure evidence. Does not participate in plan identity or engineering decisions. */
    public static Job begin(ServerLevel level,RoadPlan layout,RoadConstructionConfig config,List<SpatialClaim> claims,boolean diagnostics) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("SERVER_THREAD_REQUIRED");
        return new Job(level,layout,config,claims,diagnostics);
    }
    private enum Kind { ASPHALT, CURB, UTILITY, SIDEWALK, ENTRY, SHOULDER, LOT_GUARD }
    private record Cell(int x,int z,Kind kind,String owner,boolean node,long shoulderSource,int distance) {}
    private record Column(int groundY,BlockState top) {}
    private record Entry(RoadLotPlanner.Lot lot,RoadLotPlanner.Connector connector) {}

    public static final class Job {
        private final ServerLevel level;
        private final RoadPlan layout;
        private final RoadConstructionConfig cfg;
        private final List<SpatialClaim> claims;
        private final Map<Long,Cell> cells=new TreeMap<>();
        private final Map<Long,Column> terrain=new HashMap<>();
        private final Map<Long,BlockState> guards=new TreeMap<>();
        private final Map<Long,RoadConstructionPlan.BlockEdit> edits=new TreeMap<>();
        private final Map<Long,Entry> entries=new HashMap<>();
        private final Map<String,Integer> nodeG=new TreeMap<>(),lotG=new TreeMap<>();
        private final Map<String,int[]> profiles=new TreeMap<>();
        private final Map<String,RoadPlan.Edge> edges=new TreeMap<>();
        private final Map<String,Integer> unitEdits=new HashMap<>();
        private final Set<Long> chunks=new HashSet<>(),checkedChunks=new HashSet<>();
        private final List<String> issues=new ArrayList<>();
        private List<Cell> ordered=List.of();
        private BoundsXZ bounds;
        private int phase,index,maxCut,maxFill,cutCount,fillCount;
        private RoadConstructionPlan.Status status=RoadConstructionPlan.Status.PREVIEW_READY;
        private RoadConstructionPlan result;
        private final boolean diagnostics;
        private Cell observedCell;
        private Integer observedY,observedGround,observedH16;
        private final List<Map<String,Object>> failureDetails=new ArrayList<>();
        private long blockChecks;

        private Job(ServerLevel level,RoadPlan layout,RoadConstructionConfig cfg,List<SpatialClaim> claims,boolean diagnostics) {
            this.diagnostics=diagnostics;
            this.level=Objects.requireNonNull(level);this.layout=Objects.requireNonNull(layout);
            this.cfg=Objects.requireNonNull(cfg);this.claims=List.copyOf(claims);bounds=layout.candidateBounds();
            if(!layout.successful()||!layout.connected()||!RoadPlanner.VERSION.equals(layout.specVersion())) {
                reject("INVALID_OR_STALE_V1A_LAYOUT");finish();return;
            }
            try { rasterize(); } catch (PlanFailure failure) { finish(); }
        }
        public boolean done() { return result!=null; }
        public int processedColumns() { return terrain.size(); }
        public int totalColumns() { return cells.size(); }
        public List<Map<String,Object>> failureDetails() { return List.copyOf(failureDetails); }
        public long blockChecks() { return blockChecks; }
        public Set<Long> loadedChunksUsed() { return Set.copyOf(checkedChunks); }
        public String phase() { return done()?"COMPLETE":phase==0?"ACTUAL_WORLD_PREFLIGHT":phase==1?"PROFILE":"EDIT_SNAPSHOT"; }
        public RoadConstructionPlan result() {
            if(!done())throw new IllegalStateException("Construction preparation is unfinished");return result;
        }
        /** Caller invokes once per server tick; all read costs are capped by columns and bounded vertical scans. */
        public void advance(int columnBudget) {
            if(!level.getServer().isSameThread())throw new IllegalStateException("SERVER_THREAD_REQUIRED");
            if(done())return;
            int budget=Math.min(cfg.columnsPerTick(),Math.max(1,columnBudget));
            try {
                if(phase==0) {
                    for(int n=0;n<budget&&index<ordered.size();n++,index++) sample(ordered.get(index));
                    if(index==ordered.size()) { phase=1;index=0; }
                    return;
                }
                if(phase==1) { observedCell=null;observedY=null;observedGround=null;observedH16=null;planProfiles();phase=2;return; }
                for(int n=0;n<budget&&index<ordered.size();n++,index++) planColumn(ordered.get(index));
                if(index==ordered.size())finish();
            } catch(PlanFailure failure) { finish(); }
            catch(RuntimeException failure) { recordFailure("PREPARATION_EXCEPTION:"+failure.getClass().getSimpleName());unknown("PREPARATION_EXCEPTION:"+failure.getClass().getSimpleName());finish(); }
        }

        private void rasterize() {
            for(var edge:layout.edges())edges.put(edge.id(),edge);
            // Node-owned regions always take precedence; edge painting cannot overwrite the junction partition.
            for(var node:layout.nodes()) {
                var j=node.junction();
                regions(j.asphalt(),Kind.ASPHALT,node.id(),true);regions(j.curbs(),Kind.CURB,node.id(),true);
                regions(j.utilities(),Kind.UTILITY,node.id(),true);regions(j.sidewalks(),Kind.SIDEWALK,node.id(),true);
            }
            for(var edge:layout.edges()) {
                var s=edge.crossSection();
                regions(List.of(s.asphalt()),Kind.ASPHALT,edge.id(),false);regions(s.curbs(),Kind.CURB,edge.id(),false);
                regions(s.utilities(),Kind.UTILITY,edge.id(),false);regions(s.sidewalks(),Kind.SIDEWALK,edge.id(),false);
            }
            for(var lot:layout.lots())for(var connector:lot.connectors()) {
                Entry entry=new Entry(lot,connector);
                forEach(connector.roadAccess(),(x,z)-> {
                    Cell cell=cells.get(key(x,z));
                    if(cell==null||cell.node()||!cell.owner().equals(connector.roadId())) fail("CONNECTOR_OUTSIDE_OWN_EDGE");
                    addEntry(x,z,entry);
                });
                forEach(connector.entryStrip(),(x,z)-> {
                    if(!lot.fullBounds().contains(x,z)||lot.building().bounds().contains(x,z))fail("LOT_BOUNDARY_CHECK_FAILED");
                    Cell old=cells.get(key(x,z));
                    if(old==null)addCell(new Cell(x,z,Kind.ENTRY,connector.roadId(),false,0,0));
                    else if(old.kind()!=Kind.ENTRY||!old.owner().equals(connector.roadId()))fail("LOT_ENTRY_OWNERSHIP_CONFLICT");
                    addEntry(x,z,entry);
                });
            }
            // Read, but never grade, the immediately adjoining parcel border. A planned G alone is not actual ground.
            for(Cell source:new ArrayList<>(cells.values()))for(Direction d:Direction.Plane.HORIZONTAL) {
                int x=source.x()+d.getStepX(),z=source.z()+d.getStepZ();
                if(!cells.containsKey(key(x,z))&&insideLot(x,z))
                    addCell(new Cell(x,z,Kind.LOT_GUARD,source.owner(),false,0,0));
            }
            // Only a narrow external shoulder halo is inspected. It never enters a parcel except its approved mouth.
            List<Cell> frontier=new ArrayList<>();
            for(Cell cell:new ArrayList<>(cells.values())) {
                if(cell.kind()==Kind.ENTRY||cell.kind()==Kind.LOT_GUARD)continue;
                for(Direction d:Direction.Plane.HORIZONTAL) if(!cells.containsKey(key(cell.x()+d.getStepX(),cell.z()+d.getStepZ()))) {
                    frontier.add(cell);break;
                }
            }
            for(int distance=1;distance<=cfg.shoulderWidth();distance++) {
                List<Cell> next=new ArrayList<>();
                for(Cell previous:frontier)for(Direction d:Direction.Plane.HORIZONTAL) {
                    int x=previous.x()+d.getStepX(),z=previous.z()+d.getStepZ();long k=key(x,z);
                    if(cells.containsKey(k)||insideLot(x,z))continue;
                    long source=previous.kind()==Kind.SHOULDER?previous.shoulderSource():key(previous.x(),previous.z());
                    Cell cell=new Cell(x,z,Kind.SHOULDER,previous.owner(),previous.node(),source,distance);
                    addCell(cell);next.add(cell);
                }
                frontier=next;
            }
            ordered=List.copyOf(cells.values());
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(Cell cell:ordered) {minX=Math.min(minX,cell.x());minZ=Math.min(minZ,cell.z());maxX=Math.max(maxX,cell.x());maxZ=Math.max(maxZ,cell.z());}
            if(ordered.isEmpty())fail("EMPTY_CONSTRUCTION_FOOTPRINT");
            bounds=new BoundsXZ(minX,minZ,maxX+1,maxZ+1);
        }
        private void regions(List<BoundsXZ> regions,Kind kind,String owner,boolean node) {
            for(BoundsXZ bounds:regions)forEach(bounds,(x,z)-> {
                Cell previous=cells.get(key(x,z));
                if(previous!=null) {
                    if(!node&&previous.node())return;
                    if(!previous.owner().equals(owner)||previous.kind()!=kind)fail("MATERIAL_OWNERSHIP_CONFLICT");
                    return;
                }
                if(insideLot(x,z))fail("ROAD_INSIDE_LOT");
                addCell(new Cell(x,z,kind,owner,node,0,0));
            });
        }
        private void addCell(Cell cell) {
            if(cells.putIfAbsent(key(cell.x(),cell.z()),cell)!=null)fail("DUPLICATE_CONSTRUCTION_CELL");
            if(cells.size()>cfg.maxColumns())fail("COLUMN_BUDGET_EXCEEDED");
            chunks.add(chunkKey(cell.x()>>4,cell.z()>>4));
            if(chunks.size()>cfg.maxChunks())fail("CHUNK_BUDGET_EXCEEDED");
        }
        private void addEntry(int x,int z,Entry entry) {
            Entry old=entries.putIfAbsent(key(x,z),entry);
            if(old!=null&&!compatible(old,entry))fail("CONNECTOR_OVERLAP");
        }
        private boolean compatible(Entry a,Entry b) {
            var x=a.connector();var y=b.connector();
            // Catalog drive/service sockets can deliberately share one physical driveway throat.
            return a.lot().id().equals(b.lot().id())&&x.roadId().equals(y.roadId())&&x.facing()==y.facing()
                    &&(x.type()==StructureSocketType.PEDESTRIAN)==(y.type()==StructureSocketType.PEDESTRIAN)
                    &&x.roadAccess().equals(y.roadAccess())&&x.entryStrip().equals(y.entryStrip())
                    &&x.curbTransitionH16().equals(y.curbTransitionH16());
        }
        private boolean insideLot(int x,int z) { return layout.lots().stream().anyMatch(l->l.fullBounds().contains(x,z)); }

        private void sample(Cell cell) {
            observe(cell);
            int x=cell.x(),z=cell.z();
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
            if(chunk==null)failUnknown("UNLOADED_CHUNK:"+(x>>4)+","+(z>>4));
            if(!level.getWorldBorder().isWithinBounds(new BlockPos(x,level.getMinBuildHeight(),z)))fail("OUTSIDE_WORLD_BORDER");
            long ck=chunkKey(x>>4,z>>4);
            if(checkedChunks.add(ck)) {
                if(chunk.getAllStarts().values().stream().anyMatch(start->start.isValid())
                        ||chunk.getAllReferences().values().stream().anyMatch(ref->!ref.isEmpty()))fail("KNOWN_STRUCTURE_CHUNK_CONFLICT");
            }
            for(SpatialClaim claim:claims) if(claim.dimension().equals(level.dimension())
                    &&claim.boundsXZ().expand(claim.exclusionMargin()).contains(x,z))fail("PROTECTED_STRUCTURE_CONFLICT:"+claim.id());
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z),ground=top;
            if(top<=level.getMinBuildHeight()+cfg.supportDepth()+1
                    ||top+cfg.maxFillHeight()+cfg.clearanceHeight()>=level.getMaxBuildHeight())fail("BUILD_HEIGHT_LIMIT");
            for(int n=0;n<24;n++) {
                BlockState state=read(x,ground-1,z);
                if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES))fail("TREE_CLEARANCE_REQUIRES_SEPARATE_AUTHORIZATION");
                if(!state.getFluidState().isEmpty())fail("TERRAIN_WATER_OR_FLUID");
                if(state.isAir()||vegetation(state)) {ground--;continue;}
                if(!natural(state))failUnknown("UNKNOWN_SURFACE_BLOCK:"+state);
                break;
            }
            BlockState topState=read(x,ground-1,z);
            if(diagnostics)observedGround=ground;
            if(!natural(topState))failUnknown("GROUND_NOT_IDENTIFIED");
            if(top-ground>8)fail("VEGETATION_CLEARANCE_EXCEEDED");
            // Sparse base-noise comparison is a discrepancy alarm, never proof that natural-looking blocks are unmodified.
            if((x&7)==0&&(z&7)==0) {
                int baseline=level.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,
                        level,level.getChunkSource().randomState());
                if(Math.abs(ground-baseline)>cfg.maxNoiseDeviation())failUnknown("ACTUAL_NOISE_HEIGHT_MISMATCH");
            }
            int low=Math.max(level.getMinBuildHeight(),ground-cfg.maxCutDepth()-cfg.supportDepth()-1);
            int high=ground+cfg.maxFillHeight()+cfg.clearanceHeight();
            for(int y=low;y<=high;y++) {
                BlockState state=read(x,y,z);
                if(!state.getFluidState().isEmpty())fail("UNSAFE_SUBSURFACE_FLUID");
                if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES))fail("TREE_CLEARANCE_REQUIRES_SEPARATE_AUTHORIZATION");
                if(y<ground) {
                    if(state.isAir()||vegetation(state))fail("UNSAFE_SUBSURFACE_VOID");
                    if(!natural(state)&&!state.is(Blocks.BEDROCK))failUnknown("UNKNOWN_SUBSURFACE_BLOCK:"+state);
                } else if(!state.isAir()&&!vegetation(state))failUnknown("UNKNOWN_CLEARANCE_BLOCK:"+state);
            }
            terrain.put(key(x,z),new Column(ground,topState));
        }
        private BlockState read(int x,int y,int z) {
            if(diagnostics) {observedY=y;blockChecks++;}
            if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)failUnknown("CHUNK_UNLOADED_DURING_PREPARATION");
            BlockPos pos=new BlockPos(x,y,z);BlockState state=level.getBlockState(pos);
            if(state.hasBlockEntity()||level.getBlockEntity(pos)!=null)fail("BLOCK_ENTITY_CONFLICT");
            BlockState old=guards.putIfAbsent(pos.asLong(),state);
            if(old!=null&&!old.equals(state))failUnknown("WORLD_CHANGED_DURING_PREPARATION");
            if(guards.size()>cfg.maxGuards())fail("GUARD_BUDGET_EXCEEDED");
            return state;
        }

        private void planProfiles() {
            for(var node:layout.nodes()) {
                List<Integer> heights=new ArrayList<>();
                for(Cell cell:ordered)if(cell.node()&&cell.owner().equals(node.id())&&cell.kind()!=Kind.SHOULDER)
                    heights.add(terrain.get(key(cell.x(),cell.z())).groundY());
                nodeG.put(node.id(),median(heights));
            }
            for(var lot:layout.lots()) {
                List<Integer> heights=new ArrayList<>();
                for(var connector:lot.connectors())forEach(connector.entryStrip(),(x,z)->heights.add(terrain.get(key(x,z)).groundY()));
                lotG.put(lot.id(),median(heights));
            }
            Map<String,RoadPlan.Node> nodes=new HashMap<>();for(var node:layout.nodes())nodes.put(node.id(),node);
            for(var edge:layout.edges()) {
                int length=edge.length(),grade=cfg.maxGradeH16PerBlock();int[] fixed=new int[length+1];Arrays.fill(fixed,Integer.MIN_VALUE);
                int fromExtent=nodes.get(edge.from()).junction().armExtent(),toExtent=nodes.get(edge.to()).junction().armExtent();
                for(int s=0;s<=Math.min(length,fromExtent);s++)force(fixed,s,nodeG.get(edge.from())*16-3);
                for(int s=Math.max(0,length-toExtent);s<=length;s++)force(fixed,s,nodeG.get(edge.to())*16-3);
                for(var lot:layout.lots())for(var connector:lot.connectors())if(connector.roadId().equals(edge.id())) {
                    // One integer G per parcel; all connector throats force the adjoining road profile to that datum.
                    BoundsXZ access=connector.roadAccess();int lo=length,hi=0;
                    for(int x:new int[]{access.minX(),access.maxXExclusive()-1})for(int z:new int[]{access.minZ(),access.maxZExclusive()-1}) {
                        int s=station(edge,x,z);lo=Math.min(lo,s);hi=Math.max(hi,s);
                    }
                    for(int s=Math.max(0,lo);s<=Math.min(length,hi);s++)force(fixed,s,lotG.get(lot.id())*16-3);
                }
                int[] p=new int[length+1];int left=0;p[0]=fixed[0];
                while(left<length) {
                    int right=left+1;while(right<length&&fixed[right]==Integer.MIN_VALUE)right++;
                    if(Math.abs(fixed[right]-fixed[left])>(right-left)*grade) {
                        observeProfile(edge,right,fixed[right]);fail("SLOPE_TOO_STEEP:"+edge.id());
                    }
                    for(int s=left+1;s<right;s++) {
                        int x=edge.x1()+Integer.signum(edge.x2()-edge.x1())*s;
                        int z=edge.z1()+Integer.signum(edge.z2()-edge.z1())*s;
                        Column column=terrain.get(key(x,z));
                        if(column==null)fail("MISSING_EDGE_PROFILE_SAMPLE");
                        int desired=column.groundY()*16-3;
                        int lower=Math.max(p[s-1]-grade,fixed[right]-(right-s)*grade);
                        int upper=Math.min(p[s-1]+grade,fixed[right]+(right-s)*grade);
                        if(lower>upper) {observeProfile(edge,s,desired);fail("SLOPE_TOO_STEEP:"+edge.id());}
                        p[s]=Math.max(lower,Math.min(upper,desired));
                    }
                    p[right]=fixed[right];left=right;
                }
                profiles.put(edge.id(),p);
            }
            // Inspect against every parcel edge; no hidden fill or excavation is allowed to leak inside a lot.
            for(Cell cell:ordered) if(cell.kind()!=Kind.SHOULDER&&cell.kind()!=Kind.LOT_GUARD) {
                for(Direction d:Direction.Plane.HORIZONTAL) {
                    int x=cell.x()+d.getStepX(),z=cell.z()+d.getStepZ();
                    if(!insideLot(x,z)||entries.containsKey(key(x,z)))continue;
                    int top=topH16(cell);Column adjacent=terrain.get(key(x,z));
                    if(adjacent==null)fail("MISSING_PARCEL_BORDER_SAMPLE");
                    int allowedStep=cell.kind()==Kind.ENTRY?3:16;
                    if(Math.abs(top-adjacent.groundY()*16)>allowedStep)fail("LOT_BOUNDARY_GRADE_CONFLICT");
                }
            }
        }
        private void force(int[] fixed,int station,int h16) {
            if(fixed[station]!=Integer.MIN_VALUE&&fixed[station]!=h16)fail("INCOMPATIBLE_NODE_OR_LOT_DATUM");
            fixed[station]=h16;
        }
        private int topH16(Cell cell) {
            Entry entry=entries.get(key(cell.x(),cell.z()));
            if(entry!=null) {
                var c=entry.connector();int g=lotG.get(entry.lot().id())*16;
                if(c.type()==StructureSocketType.PEDESTRIAN)return g;
                BoundsXZ contact=c.asphaltContact().orElseThrow();
                int distance=switch(c.facing()) {
                    case NORTH -> cell.z()-contact.minZ();case SOUTH -> contact.minZ()-cell.z();
                    case WEST -> cell.x()-contact.minX();case EAST -> contact.minX()-cell.x();
                    default -> throw new IllegalArgumentException("Vertical connector");
                };
                BoundsXZ mouth=c.entryStrip();
                int end=switch(c.facing()) {
                    case NORTH -> mouth.minZ()-contact.minZ();case SOUTH -> contact.minZ()-mouth.minZ();
                    case WEST -> mouth.minX()-contact.minX();case EAST -> contact.minX()-mouth.minX();
                    default -> throw new IllegalArgumentException("Vertical connector");
                };
                if(end<6||c.curbTransitionH16().size()!=3)fail("VEHICLE_THROAT_TOO_SHORT");
                // Consume the relative S->G 14/15/16 transition, then return gently to parking S at the parcel mouth.
                int rise=distance<=3?distance:Math.min(3,end-distance);
                if(rise<0)fail("CONNECTOR_HEIGHT_OUTSIDE_ACCESS");
                int h=rise==0?g-3:g-3+(c.curbTransitionH16().get(rise-1)-c.roadSurfaceH16());
                return h;
            }
            if(cell.kind()==Kind.SHOULDER) {
                Cell source=cells.get(cell.shoulderSource());int edge=topH16(source),natural=terrain.get(key(cell.x(),cell.z())).groundY()*16;
                int delta=cell.distance()*16;
                return Math.max(edge-delta,Math.min(edge+delta,natural));
            }
            int asphalt=cell.node()?nodeG.get(cell.owner())*16-3:profiles.get(cell.owner())[station(edges.get(cell.owner()),cell.x(),cell.z())];
            return cell.kind()==Kind.ASPHALT?asphalt:asphalt+3;
        }

        private void planColumn(Cell cell) {
            observe(cell);
            if(cell.kind()==Kind.LOT_GUARD)return;
            Column column=terrain.get(key(cell.x(),cell.z()));int h16=topH16(cell);
            if(diagnostics) {observedGround=column.groundY();observedH16=h16;observedY=Math.floorDiv(h16-1,16);}
            int y=Math.floorDiv(h16-1,16),layers=Math.floorMod(h16-1,16)+1;
            int cut=Math.max(0,column.groundY()-1-y),fill=Math.max(0,y-(column.groundY()-1));
            maxCut=Math.max(maxCut,cut);maxFill=Math.max(maxFill,fill);
            if(cut>cfg.maxCutDepth())fail("CUT_DEPTH_EXCEEDED");if(fill>cfg.maxFillHeight())fail("FILL_HEIGHT_EXCEEDED");
            if(cell.kind()==Kind.SHOULDER&&h16==column.groundY()*16)return;
            // No more than one natural step can remain at the external end of the bounded shoulder.
            if(cell.kind()==Kind.SHOULDER&&cell.distance()==cfg.shoulderWidth()
                    &&Math.abs(h16-column.groundY()*16)>16)fail("SIDE_SLOPE_BUDGET_EXCEEDED");
            int from=Math.min(y-cfg.supportDepth(),column.groundY()-cfg.supportDepth()-1);
            int clearThrough=Math.max(column.groundY(),y+cfg.clearanceHeight());
            if(from<level.getMinBuildHeight()||clearThrough>=level.getMaxBuildHeight())fail("BUILD_HEIGHT_LIMIT");
            // Removing above first and filling below first are encoded as ordered edit phases in finish().
            for(int clear=y+1;clear<=clearThrough;clear++) {
                BlockState before=read(cell.x(),clear,cell.z());
                if(!before.isAir())addEdit(cell,clear,Blocks.AIR.defaultBlockState(),RoadConstructionPlan.EditKind.CUT);
            }
            for(int base=from;base<y;base++) {
                BlockState before=read(cell.x(),base,cell.z());
                if(before.is(Blocks.BEDROCK)) {
                    if(base<y-cfg.supportDepth())continue;
                    // Existing bedrock is safe support; never replace it.
                    continue;
                }
                BlockState material=cell.kind()==Kind.SHOULDER?soil(column.top()):Blocks.STONE.defaultBlockState();
                if(material.is(Blocks.GRASS_BLOCK))material=Blocks.DIRT.defaultBlockState();
                addEdit(cell,base,material,before.isAir()||vegetation(before)?RoadConstructionPlan.EditKind.FILL:RoadConstructionPlan.EditKind.FOUNDATION);
            }
            BlockState surface=surface(cell,layers,column);
            addEdit(cell,y,surface,cell.kind()==Kind.SHOULDER?RoadConstructionPlan.EditKind.SHOULDER:RoadConstructionPlan.EditKind.SURFACE);
        }
        private BlockState surface(Cell cell,int layers,Column column) {
            Entry entry=entries.get(key(cell.x(),cell.z()));
            if(entry!=null) {
                var connector=entry.connector();
                if(connector.type()==StructureSocketType.PEDESTRIAN)return RoadConstructionMaterials.sidewalk(layers);
                if(connector.asphaltContact().orElseThrow().contains(cell.x(),cell.z()))return RoadConstructionMaterials.asphalt(layers);
                return RoadConstructionMaterials.curb(layers,connector.facing(),RoadConstructionMaterials.CurbShape.DRIVEWAY);
            }
            if(cell.kind()==Kind.CURB) {
                List<Direction> asphalt=new ArrayList<>();
                for(Direction d:Direction.Plane.HORIZONTAL)if(isAsphalt(cell.x()+d.getStepX(),cell.z()+d.getStepZ()))asphalt.add(d);
                if(asphalt.size()==1)return RoadConstructionMaterials.curb(layers,asphalt.get(0),RoadConstructionMaterials.CurbShape.STRAIGHT);
                for(Direction facing:Direction.Plane.HORIZONTAL) {
                    Direction left=facing.getCounterClockWise();
                    if(asphalt.contains(facing)&&asphalt.contains(left))
                        return RoadConstructionMaterials.curb(layers,facing,RoadConstructionMaterials.CurbShape.OUTER);
                    if(asphalt.isEmpty()&&isAsphalt(cell.x()+facing.getStepX()+left.getStepX(),cell.z()+facing.getStepZ()+left.getStepZ()))
                        return RoadConstructionMaterials.curb(layers,facing,RoadConstructionMaterials.CurbShape.INNER);
                }
                return RoadConstructionMaterials.curb(layers,Direction.NORTH,RoadConstructionMaterials.CurbShape.STRAIGHT);
            }
            return switch(cell.kind()) {
                case ASPHALT -> RoadConstructionMaterials.asphalt(layers);
                case SIDEWALK,ENTRY -> RoadConstructionMaterials.sidewalk(layers);
                case UTILITY,SHOULDER -> layers==16?soil(column.top()):RoadConstructionMaterials.utility(layers);
                default -> throw new IllegalStateException("Unhandled surface kind");
            };
        }
        private boolean isAsphalt(int x,int z) { Cell cell=cells.get(key(x,z));return cell!=null&&cell.kind()==Kind.ASPHALT; }
        private void addEdit(Cell cell,int y,BlockState after,RoadConstructionPlan.EditKind kind) {
            BlockState before=read(cell.x(),y,cell.z());if(before.equals(after))return;
            if(!before.isAir()&&!vegetation(before)&&!natural(before))failUnknown("NON_NATURAL_EDIT_TARGET");
            if(!before.getFluidState().isEmpty()||before.is(Blocks.BEDROCK))fail("UNSAFE_EDIT_TARGET");
            BlockPos pos=new BlockPos(cell.x(),y,cell.z());
            var edit=new RoadConstructionPlan.BlockEdit(pos,before,after,cell.owner(),kind);
            if(edits.putIfAbsent(pos.asLong(),edit)!=null)fail("DUPLICATE_BLOCK_WRITE");
            if(kind==RoadConstructionPlan.EditKind.CUT)cutCount++;
            if(!after.isAir()&&(before.isAir()||vegetation(before)))fillCount++;
            if(edits.size()>cfg.maxEdits())fail("EARTHWORK_BUDGET_EXCEEDED");
            if(unitEdits.merge(cell.owner(),1,Integer::sum)>cfg.maxSegmentEdits())fail("SEGMENT_EARTHWORK_BUDGET_EXCEEDED");
        }
        private void finish() {
            if(result!=null)return;
            List<RoadConstructionPlan.BlockEdit> sorted=new ArrayList<>(edits.values());
            sorted.sort(Comparator.<RoadConstructionPlan.BlockEdit>comparingInt(e->e.kind()==RoadConstructionPlan.EditKind.CUT?0:1)
                    .thenComparingInt(e->e.pos().getX()>>4).thenComparingInt(e->e.pos().getZ()>>4)
                    .thenComparingInt(e->e.kind()==RoadConstructionPlan.EditKind.CUT?-e.pos().getY():e.pos().getY())
                    .thenComparingInt(e->e.pos().getX()).thenComparingInt(e->e.pos().getZ()));
            List<RoadConstructionPlan.Profile> elevations=new ArrayList<>();
            nodeG.forEach((id,g)->elevations.add(new RoadConstructionPlan.Profile("node:"+id,List.of(g*16-3))));
            profiles.forEach((id,values)->elevations.add(new RoadConstructionPlan.Profile(id,Arrays.stream(values).boxed().toList())));
            List<RoadConstructionPlan.Guard> snapshot=guards.entrySet().stream()
                    .map(e->new RoadConstructionPlan.Guard(BlockPos.of(e.getKey()),e.getValue())).toList();
            String id=identity(sorted,snapshot);
            if(status==RoadConstructionPlan.Status.PREVIEW_READY) {
                issues.add("ACTUAL_BLOCKS_CHECKED;SPARSE_NOISE_COMPARISON;FLUID_VOID_BE_STRUCTURE_CLAIMS_CHECKED");
                issues.add("NODE_PRIORITY;INTEGER_LOT_G;GRADE_MAX_"+cfg.maxGradeH16PerBlock()+"_H16_PER_BLOCK;BOUNDED_SHOULDERS");
                issues.add("SITE_PROVENANCE_REQUIRES_EXPLICIT_CALLER_ATTESTATION;NO_AUTOMATIC_PLAYER_NATURAL_BLOCK_DETECTION");
                issues.add("NO_BUILDING_PLACEMENT;LOT_INTERNAL_PATHS_RESERVED_ONLY;NO_TRANSACTIONAL_ROLLBACK");
            }
            result=new RoadConstructionPlan(id,layout.planId(),VERSION,level.dimension().location(),bounds,status,
                    sorted,snapshot,elevations,lotG,new RoadConstructionPlan.Summary(layout.edges().size(),layout.nodes().size(),
                    chunks.size(),maxCut,maxFill,cutCount,fillCount,sorted.size(),terrain.size()),issues);
        }
        private String identity(List<RoadConstructionPlan.BlockEdit> edits,List<RoadConstructionPlan.Guard> snapshot) {
            try {
                MessageDigest md=MessageDigest.getInstance("SHA-256");
                digest(md,VERSION+layout.planId()+level.dimension().location()+level.getSeed()+cfg);
                for(var g:snapshot)digest(md,g.pos().asLong()+":"+g.state());
                for(var e:edits)digest(md,e.pos().asLong()+":"+e.after());
                return "road_build_"+HexFormat.of().formatHex(md.digest()).substring(0,24);
            } catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
        }
        private void reject(String reason) { status=RoadConstructionPlan.Status.REJECTED;issues.add(reason); }
        private void unknown(String reason) { if(status!=RoadConstructionPlan.Status.REJECTED)status=RoadConstructionPlan.Status.UNKNOWN;issues.add(reason); }
        private void observe(Cell cell) {
            if(!diagnostics)return;
            observedCell=cell;observedY=null;observedGround=null;observedH16=null;
        }
        private void observeProfile(RoadPlan.Edge edge,int station,int targetH16) {
            if(!diagnostics)return;
            int x=edge.x1()+Integer.signum(edge.x2()-edge.x1())*station;
            int z=edge.z1()+Integer.signum(edge.z2()-edge.z1())*station;
            observe(cells.get(key(x,z)));var c=terrain.get(key(x,z));
            if(c!=null){observedGround=c.groundY();observedY=c.groundY()-1;}observedH16=targetH16;
        }
        private void recordFailure(String reason) {
            if(!diagnostics||failureDetails.size()>=8)return;
            try { collectFailure(reason); }
            catch(RuntimeException observationFailure) {
                failureDetails.add(Map.of("reason",reason,"diagnostic_error",observationFailure.getClass().getSimpleName()));
            }
        }
        private void collectFailure(String reason) {
            Map<String,Object> d=new LinkedHashMap<>();d.put("reason",reason);d.put("phase",phase());
            d.put("bounds",bounds);d.put("support_depth",cfg.supportDepth());
            d.put("scan_below_G",cfg.maxCutDepth()+cfg.supportDepth()+1);
            d.put("surface_y",observedGround==null?"NOT_AVAILABLE":observedGround);
            d.put("surface_block_y",observedGround==null?"NOT_AVAILABLE":observedGround-1);
            d.put("expected_column_top_h16",observedH16==null?"NOT_AVAILABLE_BEFORE_PROFILE":observedH16);
            d.put("expected_profile_h16","NOT_AVAILABLE_BEFORE_PROFILE");d.put("expected_G","NOT_AVAILABLE_BEFORE_PROFILE");
            if(observedCell!=null) {
                Cell c=observedCell;d.put("x",c.x());d.put("z",c.z());d.put("region",c.kind().name());
                d.put("y",observedY==null?"NOT_AVAILABLE":observedY);
                var edge=edges.get(c.owner());
                if(edge==null&&layout.edges().size()==1)edge=layout.edges().get(0);
                if(edge!=null) {
                    d.put("road_station",station(edge,c.x(),c.z()));
                    d.put("cross_offset",-(c.x()-edge.x1())*Integer.signum(edge.z2()-edge.z1())
                            +(c.z()-edge.z1())*Integer.signum(edge.x2()-edge.x1()));
                    int s=Math.max(0,Math.min(edge.length(),station(edge,c.x(),c.z())));
                    Integer roadH16=null;
                    if(profiles.containsKey(edge.id()))roadH16=profiles.get(edge.id())[s];
                    else if(phase==1)roadH16=observedH16;
                    if(roadH16!=null) {d.put("expected_profile_h16",roadH16);d.put("expected_G",(roadH16+3)/16.0);}
                }
                if(observedGround!=null&&observedY!=null)d.put("depth_below_surface_block",observedGround-1-observedY);
                var chunk=level.getChunkSource().getChunkNow(c.x()>>4,c.z()>>4);
                if(chunk!=null&&observedY!=null) {
                    blockChecks+=2;
                    d.put("block_found",chunk.getBlockState(new BlockPos(c.x(),observedY,c.z())).toString());
                    d.put("block_below",chunk.getBlockState(new BlockPos(c.x(),observedY-1,c.z())).toString());
                }
            } else d.put("coordinate_status","NOT_AVAILABLE_FOR_LAYOUT_OR_PROFILE_CONSTRAINT");
            failureDetails.add(Collections.unmodifiableMap(d));
        }
        private void fail(String reason) { recordFailure(reason);reject(reason);throw new PlanFailure(); }
        private void failUnknown(String reason) { recordFailure(reason);unknown(reason);throw new PlanFailure(); }
    }
    private static final class PlanFailure extends RuntimeException {
        private PlanFailure() {super(null,null,false,false);}
    }
    @FunctionalInterface private interface XZConsumer {void accept(int x,int z);}
    private static void forEach(BoundsXZ b,XZConsumer consumer) {
        for(int x=b.minX();x<b.maxXExclusive();x++)for(int z=b.minZ();z<b.maxZExclusive();z++)consumer.accept(x,z);
    }
    private static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    private static long chunkKey(int x,int z) {return key(x,z);}
    private static int station(RoadPlan.Edge edge,int x,int z) {
        return edge.x1()==edge.x2()?(z-edge.z1())*Integer.signum(edge.z2()-edge.z1())
                :(x-edge.x1())*Integer.signum(edge.x2()-edge.x1());
    }
    private static int median(List<Integer> heights) {
        if(heights.isEmpty())throw new IllegalArgumentException("No datum samples");heights.sort(Integer::compareTo);return heights.get(heights.size()/2);
    }
    private static void digest(MessageDigest md,String value) {md.update(value.getBytes(StandardCharsets.UTF_8));md.update((byte)'\n');}
    private static boolean vegetation(BlockState state) {return state.getBlock() instanceof BushBlock||state.is(Blocks.SNOW);}
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.SAND)
                ||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)
                ||s.is(Blocks.SNOW_BLOCK)||s.is(AflBlocks.SCORCHED_SOIL.get())||s.is(AflBlocks.FALLOUT_SOIL.get())
                ||s.is(AflBlocks.FUSED_GROUND.get())||vanillaOverworldOre(s);
    }
    /** Recognized geological support, not permission to accept arbitrary mod-tagged blocks as natural. */
    private static boolean vanillaOverworldOre(BlockState s) {
        return s.is(Blocks.COAL_ORE)||s.is(Blocks.DEEPSLATE_COAL_ORE)
                ||s.is(Blocks.IRON_ORE)||s.is(Blocks.DEEPSLATE_IRON_ORE)
                ||s.is(Blocks.COPPER_ORE)||s.is(Blocks.DEEPSLATE_COPPER_ORE)
                ||s.is(Blocks.GOLD_ORE)||s.is(Blocks.DEEPSLATE_GOLD_ORE)
                ||s.is(Blocks.REDSTONE_ORE)||s.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                ||s.is(Blocks.LAPIS_ORE)||s.is(Blocks.DEEPSLATE_LAPIS_ORE)
                ||s.is(Blocks.DIAMOND_ORE)||s.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                ||s.is(Blocks.EMERALD_ORE)||s.is(Blocks.DEEPSLATE_EMERALD_ORE);
    }
    private static BlockState soil(BlockState top) {
        if(top.is(AflBlocks.SCORCHED_SOIL.get())||top.is(AflBlocks.FALLOUT_SOIL.get())||top.is(AflBlocks.FUSED_GROUND.get()))return top;
        if(top.is(BlockTags.BASE_STONE_OVERWORLD)||top.is(Blocks.SANDSTONE)||top.is(Blocks.RED_SANDSTONE))return top;
        return top.is(Blocks.GRASS_BLOCK)?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.DIRT.defaultBlockState();
    }
}
