package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringSession;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/**
 * The bridge's single undo/redo history, shared by WorldEdit edits and controlled fixture/shape edits.
 * Memory-only and bound to one authoring reservation; never the user's own WorldEdit history.
 * Deliberately free of WorldEdit classes so it loads without WorldEdit installed.
 */
final class BridgeHistory {
    interface Entry {
        long size();
        /** Checks the world still shows the side being left; returns the number of cells to change. */
        int verify(ServerPlayer p,BridgeBounds scope,boolean redo) throws Exception;
        void apply(ServerPlayer p,BridgeBounds scope,boolean redo) throws Exception;
    }
    record Change(BlockPos pos,BlockState before,BlockState after) {}
    /** Client sync only: no neighbour notifications, no neighbour shape cascade and no drops. */
    static final int CONTROLLED_FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;

    /** Exact before/after block states. Block entities are recreated empty by the chunk, never loaded from caller NBT. */
    static final class DirectEntry implements Entry {
        private final List<Change> changes;
        DirectEntry(List<Change> changes){this.changes=List.copyOf(changes);}
        @Override public long size(){return changes.size();}
        @Override public int verify(ServerPlayer p,BridgeBounds scope,boolean redo){
            var level=p.serverLevel();
            for(var c:changes){
                AuthoringRegionGuard.region(p,new BridgeBounds(c.pos(),c.pos()),scope,false);
                if(!level.getBlockState(c.pos()).equals(redo?c.before():c.after()))
                    throw new IllegalArgumentException("HISTORY_CONFLICT: manual/world edit preserved at "+c.pos().toShortString());
            }
            return changes.size();
        }
        @Override public void apply(ServerPlayer p,BridgeBounds scope,boolean redo){
            var level=p.serverLevel();var ordered=new ArrayList<>(changes);if(!redo)Collections.reverse(ordered);
            for(var c:ordered)level.setBlock(c.pos(),redo?c.after():c.before(),CONTROLLED_FLAGS);
            for(var c:changes)if(!level.getBlockState(c.pos()).equals(redo?c.after():c.before()))
                throw new IllegalArgumentException("HISTORY_APPLY_INCOMPLETE at "+c.pos().toShortString()+": inspect with audit_support");
        }
    }

    private BuildingAuthoringSession owner;
    private final Deque<Entry> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    /** A new reservation invalidates all history. */
    void bind(BuildingAuthoringSession s){if(owner!=s){undo.clear();redo.clear();owner=s;}}
    /** Bound memory while keeping every accepted edit undoable until session cancel/world change. */
    void reserve(long affected){
        if(undo.size()+redo.size()>=32||undo.stream().mapToLong(Entry::size).sum()+affected>1_000_000)
            throw new IllegalArgumentException("HISTORY_LIMIT: finish/review session before more edits");
    }
    void push(Entry entry){undo.push(entry);redo.clear();}
    JsonObject step(boolean forward,boolean dry,ServerPlayer p,BridgeBounds scope,long start) throws Exception {
        var source=forward?redo:undo;var target=forward?undo:redo;
        if(source.isEmpty())throw new IllegalArgumentException("HISTORY_EMPTY");
        var entry=source.peek();int count=entry.verify(p,scope,forward);
        if(!dry){entry.apply(p,scope,forward);source.pop();target.push(entry);owner.changed();}
        return result(dry?0:count,start,scope,dry);
    }
    static JsonObject result(int count,long start,BridgeBounds b,boolean dry){return object("success",true,"changed_blocks",count,"operation_id",UUID.randomUUID().toString(),"elapsed_ms",(System.nanoTime()-start)/1_000_000,"target_bounds",b.json(),"estimated_volume",b.volume(),"dry_run",dry);}
}
