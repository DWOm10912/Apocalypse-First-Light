package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringSession;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/**
 * Controlled fixture/multiblock placement, connection reconciliation and support audit.
 * AUTHORING_SAFE_UPDATES: controlled setBlock (client sync, lighting, BlockEntity lifecycle), then canSurvive,
 * then pure shape reconciliation of shape-safe neighbours only. No neighbour notifications, drops or random ticks.
 */
final class FixtureAdapter {
    private static final Set<String> PLACE_ARGUMENTS=Set.of("block_id","pos","anchor","facing","properties","replace_policy","dry_run","target");
    private static final int REPORT_LIMIT=256;
    private final BridgeHistory history;
    FixtureAdapter(BridgeHistory history){this.history=history;}

    JsonObject call(String tool,JsonObject a,ServerPlayer p) throws Exception {
        long start=System.nanoTime();var s=AuthoringAdapter.active(p);history.bind(s);var scope=new BridgeBounds(s.origin,s.max());
        if(!string(a,"target","AUTHORING_SESSION").equals("AUTHORING_SESSION"))throw new IllegalArgumentException("REFERENCE_READ_ONLY");
        return switch(tool){
            case "place_fixture" -> place(a,p,s,scope,false,start);
            case "place_multiblock" -> place(a,p,s,scope,true,start);
            case "reconcile_shapes" -> reconcile(a,p,s,scope,start);
            case "relight_region" -> relight(a,p,scope,start);
            default -> throw new IllegalArgumentException("UNKNOWN_TOOL");
        };
    }

    private JsonObject place(JsonObject a,ServerPlayer p,BuildingAuthoringSession s,BridgeBounds scope,boolean multi,long start) throws Exception {
        for(String key:a.keySet())if(!PLACE_ARGUMENTS.contains(key))throw new IllegalArgumentException("UNSUPPORTED_ARGUMENT: "+key+" (raw NBT/state data is never accepted)");
        var f=AuthoringFixtureRegistry.require(string(a,"block_id",""));
        if(!f.allowed())throw new IllegalArgumentException("FIXTURE_NOT_ALLOWED: "+f.id()+" is "+f.authoringClass()+" ("+f.notes()+")");
        if(multi&&!f.structured())throw new IllegalArgumentException("NOT_A_MULTIBLOCK: use place_fixture for "+f.id());
        if(!multi&&f.structured())throw new IllegalArgumentException("MULTIBLOCK_REQUIRES_PLACE_MULTIBLOCK: "+f.id());
        var anchor=pos(a,multi?"anchor":"pos");
        Direction facing=null;
        if(a.has("facing")){facing=Direction.byName(string(a,"facing",""));if(facing==null)throw new IllegalArgumentException("INVALID_FACING: "+string(a,"facing",""));}
        var variants=new LinkedHashMap<String,String>();
        if(a.has("properties")){
            if(!a.get("properties").isJsonObject())throw new IllegalArgumentException("PROPERTIES_MUST_BE_OBJECT");
            for(var e:a.getAsJsonObject("properties").entrySet()){
                if(!e.getValue().isJsonPrimitive())throw new IllegalArgumentException("PROPERTY_VALUE_MUST_BE_STRING: "+e.getKey());
                variants.put(e.getKey(),e.getValue().getAsString());
            }
        }
        String policy=string(a,"replace_policy","AIR_ONLY");
        if(!Set.of("AIR_ONLY","REPLACEABLE").contains(policy))throw new IllegalArgumentException("INVALID_REPLACE_POLICY: AIR_ONLY or REPLACEABLE");
        var targets=AuthoringFixtureRegistry.targets(f,anchor,facing,variants);
        var level=p.serverLevel();var bounds=bounds(targets.keySet());bounds.inside(scope);bounds.check(level);
        for(var e:targets.entrySet()){
            var pos=e.getKey();var existing=level.getBlockState(pos);
            boolean free=existing.isAir()||(policy.equals("REPLACEABLE")&&existing.canBeReplaced()&&!existing.hasBlockEntity());
            if(!free||!existing.getFluidState().isEmpty())throw new IllegalArgumentException("TARGET_OCCUPIED: "+pos.toShortString()+" holds "+existing+" (replace_policy="+policy+")");
            if(!level.getEntities(null,new AABB(pos)).isEmpty())throw new IllegalArgumentException("ENTITY_IN_EDIT_REGION: "+pos.toShortString());
        }
        var precheck=staticSupport(level,f,targets);
        if(!precheck.isEmpty())throw new IllegalArgumentException("SUPPORT_MISSING: "+precheck+" requirement: "+f.support().text);
        if(bool(a,"dry_run")){
            var out=placementJson(f,anchor,facing,targets,level,false);out.addProperty("dry_run",true);out.addProperty("support_check","STATIC_RULE");
            out.addProperty("note","Dry run changes nothing; the final canSurvive/BlockEntity checks run on the real placement.");return out;
        }
        history.reserve(targets.size()*7L);
        var before=new LinkedHashMap<BlockPos,BlockState>();for(var pos:targets.keySet())before.put(pos,level.getBlockState(pos));
        var applied=new ArrayList<BridgeHistory.Change>();var warnings=new ArrayList<String>();
        try{
            for(var e:targets.entrySet()){
                if(!level.setBlock(e.getKey(),e.getValue(),BridgeHistory.CONTROLLED_FLAGS))throw new IllegalArgumentException("PLACEMENT_REJECTED at "+e.getKey().toShortString());
                applied.add(new BridgeHistory.Change(e.getKey(),before.get(e.getKey()),e.getValue()));
            }
            if(f.shapePolicy()==AuthoringFixtureRegistry.ShapePolicy.SELF_AND_NEIGHBORS)for(var pos:targets.keySet()){
                var placed=level.getBlockState(pos);var shaped=Block.updateFromNeighbourShapes(placed,level,pos);
                if(!shaped.is(placed.getBlock()))throw new IllegalArgumentException("SUPPORT_MISSING: "+pos.toShortString()+" requirement: "+f.support().text);
                if(!shaped.equals(placed))level.setBlock(pos,shaped,BridgeHistory.CONTROLLED_FLAGS);
            }
            var unsupported=new ArrayList<String>();
            for(var pos:targets.keySet()){var placed=level.getBlockState(pos);if(!placed.is(targets.get(pos).getBlock()))unsupported.add(pos.toShortString()+" replaced during placement");else if(!placed.canSurvive(level,pos))unsupported.add(pos.toShortString());}
            if(!unsupported.isEmpty())throw new IllegalArgumentException("SUPPORT_MISSING: "+unsupported+" requirement: "+f.support().text);
            for(var pos:targets.keySet()){
                var placed=level.getBlockState(pos);var be=level.getBlockEntity(pos);
                if(AuthoringFixtureRegistry.ownsBlockEntity(placed)&&be==null)throw new IllegalArgumentException("BLOCK_ENTITY_NOT_CREATED at "+pos.toShortString());
                if(be!=null){var problem=AuthoringRegionGuard.inventoryProblem(be);if(problem!=null)throw new IllegalArgumentException("BLOCK_ENTITY_NOT_EMPTY at "+pos.toShortString()+": "+problem);}
            }
            applied.replaceAll(c->new BridgeHistory.Change(c.pos(),c.before(),level.getBlockState(c.pos())));
            applied.addAll(neighbourShapes(level,targets.keySet(),scope,warnings));
        }catch(Exception failure){
            var reversed=new ArrayList<>(applied);Collections.reverse(reversed);
            for(var c:reversed)level.setBlock(c.pos(),c.before(),BridgeHistory.CONTROLLED_FLAGS);
            for(var e:before.entrySet())if(!level.getBlockState(e.getKey()).equals(e.getValue()))level.setBlock(e.getKey(),e.getValue(),BridgeHistory.CONTROLLED_FLAGS);
            throw new IllegalArgumentException("PLACEMENT_FAILED_ROLLED_BACK: "+failure.getMessage(),failure);
        }
        history.push(new BridgeHistory.DirectEntry(applied));s.changed();
        var out=placementJson(f,anchor,facing,targets,level,true);
        out.addProperty("support_check","CAN_SURVIVE");out.addProperty("changed_blocks",applied.size());
        out.addProperty("undo_steps",1);out.add("warnings",GSON.toJsonTree(warnings));
        out.addProperty("operation_id",UUID.randomUUID().toString());out.addProperty("elapsed_ms",(System.nanoTime()-start)/1_000_000);
        return out;
    }

    private static JsonObject placementJson(AuthoringFixtureRegistry.Fixture f,BlockPos anchor,Direction facing,Map<BlockPos,BlockState> targets,ServerLevel level,boolean placed){
        var parts=new JsonArray();boolean created=false,empty=true;
        for(var e:targets.entrySet()){
            var state=placed?level.getBlockState(e.getKey()):e.getValue();var be=placed?level.getBlockEntity(e.getKey()):null;
            if(be!=null){created=true;empty&=AuthoringRegionGuard.inventoryProblem(be)==null;}
            var part=object("pos",xyz(e.getKey()),"final_state",state.toString(),"block_entity",placed?be!=null:AuthoringFixtureRegistry.ownsBlockEntity(state));
            if(f.multiblock()!=null)part.addProperty("part",AuthoringFixtureRegistry.value(state,f.multiblock().partProperty()));
            parts.add(part);
        }
        var out=object("placed",placed,"block_id",f.id(),"anchor",xyz(anchor),"facing",facing==null?null:facing.getName(),"multiblock",f.multiblock()!=null,"parts",parts,
                "final_state",targets.size()==1?(placed?level.getBlockState(anchor):targets.get(anchor)).toString():null,
                "block_entity_created",placed&&created,"empty_inventory_confirmed",placed&&empty,"raw_nbt_accepted",false);
        return out;
    }

    /** Dry-run and early rejection for the published simplified rules; the block's canSurvive stays authoritative. */
    private static List<String> staticSupport(ServerLevel level,AuthoringFixtureRegistry.Fixture f,Map<BlockPos,BlockState> targets){
        var missing=new ArrayList<String>();
        for(var e:targets.entrySet()){
            var pos=e.getKey();var below=pos.below();
            boolean ok=switch(f.support()){
                case FLOOR -> targets.containsKey(below)||level.getBlockState(below).isFaceSturdy(level,below,Direction.UP);
                case FLOOR_OR_COLUMN -> targets.containsKey(below)||level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)
                        ||level.getBlockState(below).getBlock() instanceof com.antaurora.apofirstlight.block.FuelCanopyColumnBlock
                        ||level.getBlockState(below).getBlock() instanceof com.antaurora.apofirstlight.block.PowerCableBlock;
                case FLOOR_OR_DESK -> level.getBlockState(below).getBlock() instanceof com.antaurora.apofirstlight.block.ModernOfficeDeskBlock
                        ||level.getBlockState(below).isFaceSturdy(level,below,Direction.UP);
                case FLOOR_CLEAR_ABOVE -> level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)
                        &&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty();
                case ATTACHED_OPPOSITE_FACING -> {
                    var facing=Direction.byName(AuthoringFixtureRegistry.value(e.getValue(),f.facingProperty()));
                    var support=pos.relative(facing.getOpposite());yield level.getBlockState(support).isFaceSturdy(level,support,facing);
                }
                case NONE,BLOCK_RULE -> true;
            };
            if(!ok)missing.add(pos.toShortString());
        }
        return missing;
    }

    /** Pure updateShape on shape-safe neighbours inside the plot; blocks that would break are reported, not changed. */
    private static List<BridgeHistory.Change> neighbourShapes(ServerLevel level,Collection<BlockPos> placed,BridgeBounds scope,List<String> warnings){
        var neighbours=new LinkedHashSet<BlockPos>();
        for(var pos:placed)for(var d:Direction.values()){var n=pos.relative(d);if(!placed.contains(n))neighbours.add(n.immutable());}
        var changes=new ArrayList<BridgeHistory.Change>();
        for(var n:neighbours){
            if(!level.isLoaded(n))continue;var old=level.getBlockState(n);if(!AuthoringFixtureRegistry.shapeSafe(old))continue;
            var updated=Block.updateFromNeighbourShapes(old,level,n);if(updated.equals(old))continue;
            if(!scope.contains(n)){warnings.add("NEIGHBOR_OUTSIDE_PLOT_NOT_UPDATED "+n.toShortString());continue;}
            if(!updated.is(old.getBlock())){warnings.add("NEIGHBOR_WOULD_BREAK_NOT_CHANGED "+n.toShortString()+" "+AuthoringFixtureRegistry.id(old.getBlock()));continue;}
            changes.add(new BridgeHistory.Change(n,old,updated));
        }
        for(var c:changes)level.setBlock(c.pos(),c.after(),BridgeHistory.CONTROLLED_FLAGS);
        return changes;
    }

    /** Rechecks the light of the plot (or a crop) grown by margin (default 8): no blocks change, no history entry. */
    private JsonObject relight(JsonObject a,ServerPlayer p,BridgeBounds scope,long start){
        var level=p.serverLevel();var b=a.has("min")&&a.has("max")?new BridgeBounds(pos(a,"min"),pos(a,"max")):scope;b.inside(scope);b.check(level,BridgeBounds.PLOT_LIMIT);
        int margin=integer(a,"margin",8);if(margin<0||margin>BridgeLighting.MAX_MARGIN)throw new IllegalArgumentException("INVALID_MARGIN_0_TO_16");
        int queued=BridgeLighting.recheck(level,b,margin);
        var out=BridgeHistory.result(0,start,b,false);out.addProperty("queued",queued);out.addProperty("margin",margin);
        out.addProperty("note","Light checks run on the light thread; read light slices a moment later.");return out;
    }
    private JsonObject reconcile(JsonObject a,ServerPlayer p,BuildingAuthoringSession s,BridgeBounds scope,long start){
        var level=p.serverLevel();var b=a.has("min")&&a.has("max")?new BridgeBounds(pos(a,"min"),pos(a,"max")):scope;b.inside(scope);b.check(level,BridgeBounds.PLOT_LIMIT);
        var changes=new ArrayList<BridgeHistory.Change>();var skipped=new ArrayList<String>();
        // Jacobi pass: every new shape is computed from the unchanged world before anything is written.
        for(var pos:BlockPos.betweenClosed(b.min(),b.max())){
            var old=level.getBlockState(pos);if(!AuthoringFixtureRegistry.shapeSafe(old))continue;
            var updated=Block.updateFromNeighbourShapes(old,level,pos);if(updated.equals(old))continue;
            if(!updated.is(old.getBlock())){if(skipped.size()<REPORT_LIMIT)skipped.add(pos.toShortString()+" "+AuthoringFixtureRegistry.id(old.getBlock())+" would be removed (support) - see audit_support");continue;}
            changes.add(new BridgeHistory.Change(pos.immutable(),old,updated));
        }
        var report=new JsonArray();
        for(var c:changes.subList(0,Math.min(changes.size(),REPORT_LIMIT)))report.add(object("pos",xyz(c.pos()),"before",c.before().toString(),"after",c.after().toString()));
        boolean dry=bool(a,"dry_run");
        if(!dry&&!changes.isEmpty()){
            history.reserve(changes.size());
            for(var c:changes)level.setBlock(c.pos(),c.after(),BridgeHistory.CONTROLLED_FLAGS);
            history.push(new BridgeHistory.DirectEntry(changes));s.changed();
        }
        var out=BridgeHistory.result(dry?0:changes.size(),start,b,dry);
        out.addProperty("reconciled",changes.size());out.add("changes",report);out.addProperty("changes_truncated",changes.size()>REPORT_LIMIT);
        out.add("skipped",GSON.toJsonTree(skipped));out.addProperty("undo_steps",dry||changes.isEmpty()?0:1);
        out.addProperty("scope","Pure connection shapes only: panes/bars/railings/fences, walls, stairs, fence gates, AFL partitions, desktop 'lowered' state and the Fuel Stop A1 facade blocks (glazing, masonry base, cornice, wall panel, jamb, eyebrow canopy)");
        return out;
    }

    /** Read-only: canSurvive, multiblock completeness, BlockEntity policy/inventory and stale connection states. */
    static JsonObject audit(ServerLevel level,BridgeBounds b){
        b.check(level,BridgeBounds.PLOT_LIMIT);var issues=new JsonArray();var counts=new TreeMap<String,Integer>();var anchors=new HashSet<String>();int scanned=0;
        for(var pos:BlockPos.betweenClosed(b.min(),b.max())){
            var s=level.getBlockState(pos);if(s.isAir())continue;scanned++;
            var f=AuthoringFixtureRegistry.get(s.getBlock());String id=AuthoringFixtureRegistry.id(s.getBlock());
            if(!s.canSurvive(level,pos))issue(issues,counts,pos,id,"UNSUPPORTED",f!=null?f.support().text:"Block's own canSurvive rule");
            var be=level.getBlockEntity(pos);
            if(s.hasBlockEntity()||be!=null){
                if(f==null)issue(issues,counts,pos,id,"UNSAFE_BLOCK_ENTITY","Not in the authoring fixture registry");
                else if(!f.allowed())issue(issues,counts,pos,id,"UNSAFE_BLOCK_ENTITY",f.authoringClass().name());
                if(f!=null&&AuthoringFixtureRegistry.ownsBlockEntity(s)&&be==null)issue(issues,counts,pos,id,"MISSING_BLOCK_ENTITY","This part should own the fixture BlockEntity");
                if(be!=null){var problem=AuthoringRegionGuard.inventoryProblem(be);if(problem!=null)issue(issues,counts,pos,id,problem,"Authoring sources must be empty (no items or loot tables)");}
            }
            if(f!=null&&f.layout()!=null){
                var l=f.layout();var anchor=l.anchorOf(pos,s);var facing=l.facingOf(s);
                if(anchors.add(anchor.toShortString()+"/"+facing+"/"+id))for(var e:l.cells(s.getBlock(),anchor,facing).entrySet()){
                    if(!level.isLoaded(e.getKey()))continue;var actual=level.getBlockState(e.getKey());
                    if(!actual.is(s.getBlock()))issue(issues,counts,e.getKey(),id,"ORPHAN_PART","Missing cell of the structure anchored at "+anchor.toShortString());
                    else if(!actual.equals(e.getValue()))issue(issues,counts,e.getKey(),id,"PART_STATE_MISMATCH","Expected "+e.getValue());
                }
            }
            if(f!=null&&f.multiblock()!=null){
                var m=f.multiblock();var facing=Direction.byName(AuthoringFixtureRegistry.value(s,f.facingProperty()));var part=AuthoringFixtureRegistry.value(s,m.partProperty());
                var anchor=m.anchorOf(pos,facing,part);
                if(anchors.add(anchor.toShortString()+"/"+facing+"/"+id))for(var other:m.parts()){
                    var at=m.locator().locate(anchor,facing,other);if(!level.isLoaded(at))continue;
                    var expected=AuthoringFixtureRegistry.with(s,m.partProperty(),other);var actual=level.getBlockState(at);
                    if(!actual.is(s.getBlock()))issue(issues,counts,at,id,"ORPHAN_PART","Missing part '"+other+"' of the multiblock anchored at "+anchor.toShortString());
                    else if(!actual.equals(expected))issue(issues,counts,at,id,"PART_STATE_MISMATCH","Expected "+expected);
                }
            }
            if(AuthoringFixtureRegistry.shapeSafe(s)&&!Block.updateFromNeighbourShapes(s,level,pos).equals(s))
                issue(issues,counts,pos,id,"CONNECTION_STALE","Run reconcile_shapes");
        }
        return object("bounds",b.json(),"scanned_non_air",scanned,"issue_count",counts.values().stream().mapToInt(Integer::intValue).sum(),
                "issues",issues,"issues_truncated",issues.size()>=REPORT_LIMIT*2,"counts",counts,"read_only",true);
    }
    private static void issue(JsonArray issues,Map<String,Integer> counts,BlockPos pos,String block,String issue,String expected){
        counts.merge(issue,1,Integer::sum);
        if(issues.size()<REPORT_LIMIT*2)issues.add(object("position",xyz(pos),"block",block,"issue",issue,"expected_support",expected));
    }
    private static BridgeBounds bounds(Collection<BlockPos> cells){
        int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
        for(var c:cells){x0=Math.min(x0,c.getX());y0=Math.min(y0,c.getY());z0=Math.min(z0,c.getZ());x1=Math.max(x1,c.getX());y1=Math.max(y1,c.getY());z1=Math.max(z1,c.getZ());}
        return new BridgeBounds(new BlockPos(x0,y0,z0),new BlockPos(x1,y1,z1));
    }
}
