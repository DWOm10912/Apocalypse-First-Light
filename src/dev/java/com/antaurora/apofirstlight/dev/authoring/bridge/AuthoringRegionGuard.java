package com.antaurora.apofirstlight.dev.authoring.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.regex.Pattern;

/**
 * Shared bridge safety policy. Generic WorldEdit materials never carry block entities or multiblock parts;
 * existing cells may hold only whitelisted, empty authoring fixtures. Operation-driven: no background scans.
 */
final class AuthoringRegionGuard {
    private static final Pattern HAZARD=Pattern.compile(".*(command_block|structure_block|structure_void|jigsaw|barrier|tnt|fire$|portal|piston|redstone|sculk|tripwire).*$");
    private AuthoringRegionGuard(){}

    /** Fluids, falling blocks, the invisible editor light and world-acting blocks; null when acceptable. */
    static String hazard(BlockState s){
        String id=AuthoringFixtureRegistry.id(s.getBlock());
        // Reject the invisible Vanilla editor block, not ordinary fixtures ending in "light".
        return !s.getFluidState().isEmpty()||s.getBlock() instanceof FallingBlock||s.is(Blocks.LIGHT)||HAZARD.matcher(id).matches()
                ?"UNSAFE_OR_DYNAMIC_BLOCK: "+id:null;
    }
    /** Generic WorldEdit write material. Block entities go through place_fixture, multiblocks through place_multiblock. */
    static String materialProblem(BlockState s){
        var hazard=hazard(s);if(hazard!=null)return hazard;
        String id=AuthoringFixtureRegistry.id(s.getBlock());
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.LampGlowBlock)return "UNSAFE_OR_DYNAMIC_BLOCK: "+id+" (a lit light pole's runtime light point; place the pole)";
        if(s.hasBlockEntity())return "UNSAFE_OR_DYNAMIC_BLOCK: "+id+" (block entities are placed only through place_fixture/place_multiblock)";
        if(AuthoringFixtureRegistry.structured(s))return "MULTIBLOCK_REQUIRES_PLACE_MULTIBLOCK: "+id;
        return null;
    }
    static void material(BlockState s){var problem=materialProblem(s);if(problem!=null)throw new IllegalArgumentException(problem);}
    /** we_replace match state: whitelisted single fixtures may be matched for deletion, multiblock parts may not. */
    static void match(BlockState s){
        var hazard=hazard(s);if(hazard!=null)throw new IllegalArgumentException(hazard);
        String id=AuthoringFixtureRegistry.id(s.getBlock());
        if(AuthoringFixtureRegistry.structured(s))throw new IllegalArgumentException("MULTIBLOCK_REQUIRES_WHOLE_REGION_EDIT: "+id);
        var f=AuthoringFixtureRegistry.get(s.getBlock());
        if(s.hasBlockEntity()&&(f==null||!f.allowed()))throw new IllegalArgumentException("UNSAFE_OR_DYNAMIC_BLOCK: "+id);
    }

    /** Null when the existing cell may be edited; otherwise the reason. */
    static String blockEntityProblem(Level level,BlockPos pos,BlockState s){
        var be=level.getBlockEntity(pos);
        if(!(s.getBlock() instanceof EntityBlock)&&be==null)return null;
        var f=AuthoringFixtureRegistry.get(s.getBlock());
        if(f==null)return "NOT_IN_AUTHORING_FIXTURE_REGISTRY";
        if(!f.allowed())return f.authoringClass().name();
        return be==null?null:inventoryProblem(be);
    }
    /** Serialized data is inspected before the item handler so loot tables are never unpacked. */
    static String inventoryProblem(BlockEntity be){
        var tag=be.saveWithoutMetadata();
        if(containsKey(tag,"LootTable"))return "LOOT_TABLE_PRESENT";
        if(containsItem(tag))return "NONEMPTY_INVENTORY";
        var handler=be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if(handler.isPresent())for(int i=0;i<handler.get().getSlots();i++)if(!handler.get().getStackInSlot(i).isEmpty())return "NONEMPTY_INVENTORY";
        return null;
    }
    private static boolean containsKey(Tag tag,String key){
        if(tag instanceof CompoundTag c){for(String k:c.getAllKeys())if(k.equalsIgnoreCase(key)||containsKey(c.get(k),key))return true;}
        else if(tag instanceof ListTag list)for(Tag child:list)if(containsKey(child,key))return true;
        return false;
    }
    private static boolean containsItem(Tag tag){
        if(tag instanceof CompoundTag c){
            if(c.contains("Count",Tag.TAG_ANY_NUMERIC)&&c.getInt("Count")>0&&c.contains("id",Tag.TAG_STRING))return true;
            for(String k:c.getAllKeys())if(containsItem(c.get(k)))return true;
        }else if(tag instanceof ListTag list)for(Tag child:list)if(containsItem(child))return true;
        return false;
    }

    /**
     * Existing-region check for every bridge edit. {@code wholeMultiblocks} rejects edits that would cut a
     * multiblock in two; single-cell history verification passes false because the entry already covers all parts.
     */
    static void region(ServerPlayer p,BridgeBounds b,BridgeBounds scope,boolean wholeMultiblocks){
        b.inside(scope);var level=p.serverLevel();b.check(level);
        if(!level.getEntities(null,b.aabb()).isEmpty())throw new IllegalArgumentException("ENTITY_IN_EDIT_REGION: leave the work area");
        for(var pos:BlockPos.betweenClosed(b.min(),b.max())){
            // a lit pole's light points (Site Lighting V1) are replaceable runtime light, edited over like air
            var s=level.getBlockState(pos);if(s.isAir()||s.getBlock() instanceof com.antaurora.apofirstlight.block.LampGlowBlock)continue;
            var hazard=hazard(s);if(hazard!=null)throw new IllegalArgumentException(hazard);
            var problem=blockEntityProblem(level,pos,s);
            if(problem!=null)throw new IllegalArgumentException("UNSAFE_BLOCK_ENTITY_PRESENT: "+AuthoringFixtureRegistry.id(s.getBlock())+" at "+pos.toShortString()+" reason="+problem);
            if(wholeMultiblocks&&AuthoringFixtureRegistry.structured(s))
                for(var peer:AuthoringFixtureRegistry.peers(pos,s))if(!b.contains(peer))
                    throw new IllegalArgumentException("MULTIBLOCK_SPLIT: "+AuthoringFixtureRegistry.id(s.getBlock())+" at "+pos.toShortString()+" has a part at "+peer.toShortString()+" outside the edit bounds");
        }
    }
}
