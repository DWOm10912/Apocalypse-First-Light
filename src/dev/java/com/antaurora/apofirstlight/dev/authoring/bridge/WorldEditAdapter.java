package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringSession;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.operation.*;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.*;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Loaded only after ModList gate. Separate history and clipboard, never the user's WE history. */
final class WorldEditAdapter {
    private BuildingAuthoringSession owner;
    private ClipboardHolder clipboard;
    private final Deque<EditSession> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    static BridgeBounds selection(ServerPlayer p) throws Exception {
        var actor=ForgeAdapter.adaptPlayer(p);var local=WorldEdit.getInstance().getSessionManager().get(actor);var world=ForgeAdapter.adapt(p.serverLevel());
        if(local.getSelectionWorld()!=null&&!local.getSelectionWorld().equals(world))throw new IllegalArgumentException("DIMENSION_MISMATCH");
        var region=local.getSelection(world);if(!(region instanceof CuboidRegion))throw new IllegalArgumentException("unsupported_selection_type");
        return new BridgeBounds(ForgeAdapter.toBlockPos(region.getMinimumPoint()),ForgeAdapter.toBlockPos(region.getMaximumPoint()));
    }
    static CuboidRegion region(ServerPlayer p,BridgeBounds b){return new CuboidRegion(ForgeAdapter.adapt(p.serverLevel()),ForgeAdapter.adapt(b.min()),ForgeAdapter.adapt(b.max()));}
    private static void safeState(net.minecraft.world.level.block.state.BlockState s){
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if(s.hasBlockEntity()||!s.getFluidState().isEmpty()||s.getBlock() instanceof FallingBlock||id.matches(".*(command_block|structure_block|structure_void|jigsaw|barrier|light$|tnt|fire$|portal|piston|redstone|sculk|tripwire).*$"))throw new IllegalArgumentException("UNSAFE_OR_DYNAMIC_BLOCK: "+id);
    }
    private static BaseBlock block(ServerPlayer p,String input) throws Exception {
        // V1: one exact blockstate, no NBT, clipboard patterns, scripts or arbitrary WE expressions.
        if(!input.matches("[a-z0-9_]+:[a-z0-9_/]+(\\[[a-z0-9_=,]+\\])?"))throw new IllegalArgumentException("V1_REQUIRES_EXACT_NAMESPACED_BLOCKSTATE");
        var context=new ParserContext();context.setWorld(ForgeAdapter.adapt(p.serverLevel()));context.setActor(ForgeAdapter.adaptPlayer(p));context.setRestricted(true);context.setTryLegacy(false);
        var b=WorldEdit.getInstance().getBlockFactory().parseFromInput(input,context);safeState(ForgeAdapter.adapt(b.toImmutableState()));return b;
    }
    private static void safeRegion(ServerPlayer p,BridgeBounds b,BridgeBounds scope){
        b.inside(scope);b.check(p.serverLevel());
        if(!p.serverLevel().getEntities(null,b.aabb()).isEmpty())throw new IllegalArgumentException("ENTITY_IN_EDIT_REGION: leave the work area");
        for(var pos:BlockPos.betweenClosed(b.min(),b.max())){var s=p.serverLevel().getBlockState(pos);safeState(s);if(p.serverLevel().getBlockEntity(pos)!=null)throw new IllegalArgumentException("BLOCK_ENTITY_IN_EDIT_REGION");}
    }
    static EditSession edit(ServerPlayer p,BridgeBounds scope){
        var edit=WorldEdit.getInstance().newEditSessionBuilder().world(ForgeAdapter.adapt(p.serverLevel())).maxBlocks(BridgeBounds.LIMIT).build();
        edit.setMask(new RegionMask(region(p,scope)));
        edit.setSideEffectApplier(SideEffectSet.none().with(SideEffect.LIGHTING,SideEffect.State.ON));
        return edit;
    }
    private void bind(BuildingAuthoringSession s){if(owner!=s){undo.clear();redo.clear();clipboard=null;owner=s;}}
    JsonObject call(String tool,JsonObject a,ServerPlayer p) throws Exception {
        long start=System.nanoTime();var s=AuthoringAdapter.active(p);bind(s);var scope=new BridgeBounds(s.origin,s.max());
        if(!string(a,"target","AUTHORING_SESSION").equals("AUTHORING_SESSION"))throw new IllegalArgumentException("REFERENCE_READ_ONLY");
        if(tool.equals("we_batch_set"))return batch(a,p,s,scope,start);
        var b=a.has("min")&&a.has("max")?new BridgeBounds(pos(a,"min"),pos(a,"max")):scope;
        if(tool.equals("we_undo")||tool.equals("we_redo"))return history(tool.equals("we_redo"),a,p,scope,start);
        if(tool.equals("we_rotate_clipboard")){
            if(clipboard==null)throw new IllegalArgumentException("BRIDGE_CLIPBOARD_EMPTY");var rotated=ClipboardRotations.rotate(clipboard.getClipboard(),integer(a,"rotation",90));
            if(!bool(a,"dry_run"))clipboard=new ClipboardHolder(rotated);return result(0,start,scope,bool(a,"dry_run"));
        }
        safeRegion(p,b,scope);
        if(tool.equals("we_copy")){
            if(!bool(a,"dry_run")){var copy=new BlockArrayClipboard(region(p,b));copy.setOrigin(ForgeAdapter.adapt(b.min()));var op=new ForwardExtentCopy(ForgeAdapter.adapt(p.serverLevel()),region(p,b),copy,ForgeAdapter.adapt(b.min()));op.setCopyingEntities(false);op.setCopyingBiomes(false);Operations.complete(op);clipboard=new ClipboardHolder(copy);}
            return result(0,start,b,bool(a,"dry_run"));
        }
        BaseBlock material=null,from=null;BlockVector3 direction=null;int count=integer(a,"count",1);BridgeBounds affected=b;
        if(Set.of("we_set","we_replace","we_walls","we_faces","authoring_clear").contains(tool))material=block(p,tool.equals("authoring_clear")?"minecraft:air":string(a,"block",""));
        if(tool.equals("we_replace"))from=block(p,string(a,"from",""));
        if(tool.equals("we_paste")){
            if(clipboard==null)throw new IllegalArgumentException("BRIDGE_CLIPBOARD_EMPTY: copy from AUTHORING_SESSION first");
            var to=pos(a,"to");var size=clipboard.getClipboard().getDimensions();affected=new BridgeBounds(to,to.offset(size.getBlockX()-1,size.getBlockY()-1,size.getBlockZ()-1));safeRegion(p,affected,scope);
        }
        if(tool.equals("we_stack")||tool.equals("we_move")){
            var delta=pos(a,"offset");if(count<1||count>128||Math.abs((long)delta.getX())>128||Math.abs((long)delta.getY())>192||Math.abs((long)delta.getZ())>128||delta.equals(BlockPos.ZERO))throw new IllegalArgumentException("INVALID_OFFSET_OR_COUNT");
            if(tool.equals("we_move"))count=1;
            if(b.volume()*(count+1)>BridgeBounds.LIMIT)throw new IllegalArgumentException("MAX_EDIT_LIMIT");
            direction=ForgeAdapter.adapt(delta);var shiftedMin=b.min().offset(delta.getX()*count,delta.getY()*count,delta.getZ()*count);var shiftedMax=b.max().offset(delta.getX()*count,delta.getY()*count,delta.getZ()*count);
            affected=new BridgeBounds(new BlockPos(Math.min(b.min().getX(),shiftedMin.getX()),Math.min(b.min().getY(),shiftedMin.getY()),Math.min(b.min().getZ(),shiftedMin.getZ())),new BlockPos(Math.max(b.max().getX(),shiftedMax.getX()),Math.max(b.max().getY(),shiftedMax.getY()),Math.max(b.max().getZ(),shiftedMax.getZ())));safeRegion(p,affected,scope);
        }
        if(bool(a,"dry_run"))return result(0,start,affected,true);
        // Bound memory while keeping every accepted edit undoable until session cancel/world change.
        if(undo.size()+redo.size()>=32||undo.stream().mapToInt(EditSession::size).sum()+affected.volume()>1_000_000)throw new IllegalArgumentException("HISTORY_LIMIT: finish/review session before more edits");
        var edit=edit(p,scope);
        try {
            switch(tool){
                case "we_set","authoring_clear" -> edit.setBlocks(region(p,b),material);
                case "we_replace" -> edit.replaceBlocks(region(p,b),Set.of(from),material);
                case "we_walls" -> edit.makeCuboidWalls(region(p,b),material);
                case "we_faces" -> edit.makeCuboidFaces(region(p,b),material);
                case "we_paste" -> Operations.complete(clipboard.createPaste(edit).to(ForgeAdapter.adapt(pos(a,"to"))).copyEntities(false).copyBiomes(false).ignoreAirBlocks(bool(a,"ignore_air")).build());
                case "we_stack" -> edit.stackRegionBlockUnits(region(p,b),direction,count,false,false,null);
                case "we_move" -> edit.moveRegion(region(p,b),direction,1,false,false,null,block(p,"minecraft:air"));
                default -> throw new IllegalArgumentException("UNKNOWN_TOOL");
            }
            edit.close();undo.push(edit);redo.clear();s.changed();return result(changed(edit),start,affected,false);
        }catch(Exception failure){
            edit.close();try(var rollback=edit(p,scope)){edit.undo(rollback);}s.changed();throw new IllegalArgumentException("EDIT_FAILED_ROLLED_BACK: "+failure.getMessage(),failure);
        }
    }
    private JsonObject batch(JsonObject a,ServerPlayer p,BuildingAuthoringSession s,BridgeBounds scope,long start)throws Exception{
        if(!a.has("operations")||!a.get("operations").isJsonArray())throw new IllegalArgumentException("BATCH_OPERATIONS_REQUIRED");
        var ops=a.getAsJsonArray("operations");if(ops.size()<1||ops.size()>128)throw new IllegalArgumentException("BATCH_COUNT_LIMIT_128");
        var bounds=new ArrayList<BridgeBounds>();var materials=new ArrayList<BaseBlock>();long attempts=0;
        // Validate every operation against the original world before any mutation.
        for(var entry:ops){
            if(!entry.isJsonObject())throw new IllegalArgumentException("INVALID_BATCH_OPERATION");var op=entry.getAsJsonObject();
            if(!op.keySet().equals(Set.of("min","max","block")))throw new IllegalArgumentException("BATCH_REQUIRES_MIN_MAX_BLOCK_ONLY");
            var b=new BridgeBounds(pos(op,"min"),pos(op,"max"));attempts+=b.volume();
            if(attempts>BridgeBounds.LIMIT)throw new IllegalArgumentException("MAX_EDIT_LIMIT");
            safeRegion(p,b,scope);bounds.add(b);materials.add(block(p,string(op,"block","")));
        }
        if(undo.size()+redo.size()>=32||undo.stream().mapToLong(EditSession::size).sum()+attempts>1_000_000)throw new IllegalArgumentException("HISTORY_LIMIT: finish/review session before more edits");
        if(bool(a,"dry_run"))return result(0,start,scope,true);
        var edit=edit(p,scope);
        try{
            for(int i=0;i<bounds.size();i++)edit.setBlocks(region(p,bounds.get(i)),materials.get(i));
            edit.close();undo.push(edit);redo.clear();s.changed();return result(changed(edit),start,scope,false);
        }catch(Exception failure){edit.close();try(var rollback=edit(p,scope)){edit.undo(rollback);}s.changed();throw new IllegalArgumentException("EDIT_FAILED_ROLLED_BACK: "+failure.getMessage(),failure);}
    }
    private JsonObject history(boolean forward,JsonObject a,ServerPlayer p,BridgeBounds scope,long start){
        var source=forward?redo:undo;var target=forward?undo:redo;if(source.isEmpty())throw new IllegalArgumentException("HISTORY_EMPTY");
        var old=source.peek();var expected=new LinkedHashMap<BlockVector3,BaseBlock>();
        var it=forward?old.getChangeSet().backwardIterator():old.getChangeSet().forwardIterator();
        while(it.hasNext()){var change=it.next();if(!(change instanceof BlockChange bc))throw new IllegalArgumentException("UNSUPPORTED_HISTORY_CHANGE");expected.put(bc.getPosition(),forward?bc.getPrevious():bc.getCurrent());}
        var world=ForgeAdapter.adapt(p.serverLevel());
        for(var e:expected.entrySet()){
            var pos=ForgeAdapter.toBlockPos(e.getKey());var one=new BridgeBounds(pos,pos);safeRegion(p,one,scope);
            if(!world.getFullBlock(e.getKey()).equals(e.getValue()))throw new IllegalArgumentException("HISTORY_CONFLICT: manual/world edit preserved at "+pos.toShortString());
        }
        if(!bool(a,"dry_run")){try(var edit=edit(p,scope)){if(forward)old.redo(edit);else old.undo(edit);}source.pop();target.push(old);owner.changed();}
        return result(bool(a,"dry_run")?0:expected.size(),start,scope,bool(a,"dry_run"));
    }
    private static JsonObject result(int count,long start,BridgeBounds b,boolean dry){return object("success",true,"changed_blocks",count,"operation_id",UUID.randomUUID().toString(),"elapsed_ms",(System.nanoTime()-start)/1_000_000,"target_bounds",b.json(),"estimated_volume",b.volume(),"dry_run",dry);}
    static int changed(EditSession edit){
        var first=new HashMap<BlockVector3,BaseBlock>();var last=new HashMap<BlockVector3,BaseBlock>();var it=edit.getChangeSet().forwardIterator();
        while(it.hasNext()){var c=it.next();if(c instanceof BlockChange b){first.putIfAbsent(b.getPosition(),b.getPrevious());last.put(b.getPosition(),b.getCurrent());}}
        int count=0;for(var e:first.entrySet())if(!e.getValue().equals(last.get(e.getKey())))count++;return count;
    }
}
