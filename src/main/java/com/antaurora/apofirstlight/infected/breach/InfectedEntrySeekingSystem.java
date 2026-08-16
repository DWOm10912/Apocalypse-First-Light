package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

public final class InfectedEntrySeekingSystem {
    public static final int ENTRY_SEARCH_RADIUS = 10;
    private static final long SEARCH_COOLDOWN = 40L;
    private static final Map<Zombie, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private InfectedEntrySeekingSystem() {}

    public static boolean prepare(Zombie zombie, InfectedBreachContext context) {
        if (context.source() != InfectedBreachContext.Source.HIGH_INTENSITY_NOISE || zombie.getTarget() != null) return false;
        State state = STATES.computeIfAbsent(zombie, ignored -> new State());
        if (state.createdTime != context.createdGameTime()) clear(zombie, "NoiseUpdated");
        state = STATES.computeIfAbsent(zombie, ignored -> new State());
        if (state.entry != null) return true;
        long now = zombie.level().getGameTime();
        if (now - state.lastSearch < SEARCH_COOLDOWN || hasReachablePath(zombie, context.targetPosition())) return false;
        state.lastSearch = now;
        ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} SearchStarted target={}", zombie.getId(), context.targetPosition());
        Candidate best = findBest(zombie, context.targetPosition());
        if (best == null) return false;
        state.createdTime = context.createdGameTime(); state.entry = best.block; state.approach = best.approach; state.interior = best.interior;
        ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} Selected block={} pos={} score={}", zombie.getId(), zombie.level().getBlockState(best.block).getBlock(), best.block, best.score);
        return true;
    }
    public static boolean isActive(Zombie z, InfectedBreachContext c) { State s=STATES.get(z); return c.source()==InfectedBreachContext.Source.HIGH_INTENSITY_NOISE && s!=null && s.entry!=null && s.createdTime==c.createdGameTime(); }
    public static BlockPos explicitObstacle(Zombie z, InfectedBreachContext c) { State s=STATES.get(z); if(!isActive(z,c)||s.approach==null||z.position().distanceToSqr(Vec3.atCenterOf(s.approach))>9) return null; BlockState b=z.level().getBlockState(s.entry); return isClosed(b)?s.entry:null; }
    public static Vec3 approach(Zombie z){ State s=STATES.get(z); return s==null?null:Vec3.atCenterOf(s.approach); }
    public static Vec3 interior(Zombie z){ State s=STATES.get(z); return s==null?null:Vec3.atCenterOf(s.interior); }
    public static boolean isOpenOrGone(Zombie z){ State s=STATES.get(z); return s!=null && !isClosed(z.level().getBlockState(s.entry)); }
    public static void onEntryBreachCompleted(Zombie z, BlockPos broken) { State s=STATES.get(z); if(s==null||!broken.equals(s.entry)) return; s.passing=true; Vec3 interior=Vec3.atCenterOf(s.interior); z.getNavigation().moveTo(interior.x,interior.y,interior.z,1.0); ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} BreachCompleted entry={} Phase=PASSING_THROUGH interior={}",z.getId(),broken,s.interior); }
    public static void clear(Zombie z,String r){ if(STATES.remove(z)!=null) ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} Cancel reason={}",z.getId(),r); }
    private static boolean hasReachablePath(Zombie z,Vec3 t){ Path p=z.getNavigation().createPath(BlockPos.containing(t),0); return p!=null&&p.canReach(); }
    private static Candidate findBest(Zombie z,Vec3 target){ Candidate best=null; BlockPos o=z.blockPosition(); boolean grief=ForgeEventFactory.getMobGriefingEvent(z.level(),z);
        for(int x=-ENTRY_SEARCH_RADIUS;x<=ENTRY_SEARCH_RADIUS;x++) for(int zz=-ENTRY_SEARCH_RADIUS;zz<=ENTRY_SEARCH_RADIUS;zz++) for(int y=-1;y<=2;y++){
            BlockPos p=o.offset(x,y,zz); BlockState b=z.level().getBlockState(p); if(!isEntry(b)||(!grief&&isClosed(b))) continue;
            for(Direction d:Direction.Plane.HORIZONTAL){ BlockPos a=p.relative(d), in=p.relative(d.getOpposite()); if(!standable(z,a)||!standable(z,in)) continue; Path path=z.getNavigation().createPath(a,0); if(path==null||!path.canReach()) continue;
                double improve=Vec3.atCenterOf(a).distanceToSqr(target)-Vec3.atCenterOf(in).distanceToSqr(target); if(improve<=0) continue;
                double score=base(b)+Vec3.atCenterOf(a).distanceToSqr(z.position())-improve; if(best==null||score<best.score) best=new Candidate(p,a,in,score);
            }
        } return best; }
    private static boolean standable(Zombie z,BlockPos p){ return z.level().getBlockState(p).getCollisionShape(z.level(),p).isEmpty() && z.level().getBlockState(p.above()).getCollisionShape(z.level(),p.above()).isEmpty() && !z.level().getBlockState(p.below()).getCollisionShape(z.level(),p.below()).isEmpty(); }
    private static boolean isEntry(BlockState b){ if(b.is(BlockTags.LEAVES)||!InfectedBreachRules.canBreak(b)) return false; return b.is(BlockTags.WOODEN_DOORS)||b.is(BlockTags.FENCE_GATES)||b.is(BlockTags.WOODEN_TRAPDOORS)||b.is(BlockTags.WOODEN_FENCES)||b.getBlock()==Blocks.GLASS_PANE||b.getBlock() instanceof StainedGlassPaneBlock||b.getBlock() instanceof GlassBlock; }
    private static boolean isClosed(BlockState b){ return InfectedBreachRules.canBreak(b) && !(b.hasProperty(BlockStateProperties.OPEN)&&b.getValue(BlockStateProperties.OPEN)); }
    private static double base(BlockState b){ if(b.hasProperty(BlockStateProperties.OPEN)&&b.getValue(BlockStateProperties.OPEN)) return -1000; if(b.is(BlockTags.WOODEN_DOORS))return -800; if(b.is(BlockTags.FENCE_GATES))return -700; if(b.getBlock()==Blocks.GLASS_PANE||b.getBlock() instanceof StainedGlassPaneBlock)return -500; if(b.getBlock() instanceof GlassBlock)return -400; return -200; }
    private static final class State { long createdTime; long lastSearch; boolean passing; BlockPos entry,approach,interior; }
    private record Candidate(BlockPos block,BlockPos approach,BlockPos interior,double score){}
}
