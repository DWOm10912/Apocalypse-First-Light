package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.ai.InfectedAiDiagnostics;
import com.antaurora.apofirstlight.infected.ai.InfectedAiScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class InfectedEntrySeekingSystem {
    public static final int ENTRY_SEARCH_RADIUS = 10;
    public static final long SEARCH_COOLDOWN_TICKS = 40L;
    public static final int INITIAL_STAGGER_SLOTS = 20;
    public static final int MAX_CANDIDATE_PATH_CHECKS = 8;

    private static final Map<Zombie, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private InfectedEntrySeekingSystem() {
    }

    public static boolean prepare(Zombie zombie, InfectedBreachContext context) {
        if (context.source() != InfectedBreachContext.Source.HIGH_INTENSITY_NOISE || zombie.getTarget() != null) {
            return false;
        }
        State state = STATES.computeIfAbsent(zombie, ignored -> new State());
        if (state.createdTime != Long.MIN_VALUE && state.createdTime != context.createdGameTime()) {
            clear(zombie, "NoiseUpdated");
            state = STATES.computeIfAbsent(zombie, ignored -> new State());
        }
        if (state.entry != null) {
            return true;
        }
        long now = zombie.level().getGameTime();
        if (state.nextSearchAt == Long.MIN_VALUE) {
            state.nextSearchAt = now + InfectedAiScheduler.stableOffset(zombie, INITIAL_STAGGER_SLOTS);
        }
        if (now < state.nextSearchAt) {
            return false;
        }
        state.nextSearchAt = now + SEARCH_COOLDOWN_TICKS;
        if (hasReachablePath(zombie, context.targetPosition())) {
            return false;
        }
        if (zombie.level() instanceof ServerLevel level) {
            InfectedAiDiagnostics.obstacleScan(level);
        }
        ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} SearchStarted target={}",
                zombie.getId(), context.targetPosition());
        Candidate best = findBest(zombie, context.targetPosition());
        if (best == null) {
            return false;
        }
        state.createdTime = context.createdGameTime();
        state.entry = best.block();
        state.approach = best.approach();
        state.interior = best.interior();
        ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} Selected block={} pos={} score={}",
                zombie.getId(), zombie.level().getBlockState(best.block()).getBlock(), best.block(), best.score());
        return true;
    }

    public static boolean isActive(Zombie zombie, InfectedBreachContext context) {
        State state = STATES.get(zombie);
        return context.source() == InfectedBreachContext.Source.HIGH_INTENSITY_NOISE
                && state != null && state.entry != null && state.createdTime == context.createdGameTime();
    }

    public static BlockPos explicitObstacle(Zombie zombie, InfectedBreachContext context) {
        State state = STATES.get(zombie);
        if (!isActive(zombie, context) || state.approach == null
                || zombie.position().distanceToSqr(Vec3.atCenterOf(state.approach)) > 9.0) {
            return null;
        }
        BlockState blockState = zombie.level().getBlockState(state.entry);
        return isClosed(blockState) ? state.entry : null;
    }

    public static Vec3 approach(Zombie zombie) {
        State state = STATES.get(zombie);
        return state == null || state.approach == null ? null : Vec3.atCenterOf(state.approach);
    }

    public static Vec3 interior(Zombie zombie) {
        State state = STATES.get(zombie);
        return state == null || state.interior == null ? null : Vec3.atCenterOf(state.interior);
    }

    public static boolean isOpenOrGone(Zombie zombie) {
        State state = STATES.get(zombie);
        return state != null && state.entry != null && !isClosed(zombie.level().getBlockState(state.entry));
    }

    public static void onEntryBreachCompleted(Zombie zombie, BlockPos broken) {
        State state = STATES.get(zombie);
        if (state == null || !broken.equals(state.entry)) {
            return;
        }
        state.passing = true;
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL ENTRY] Zombie={} BreachCompleted entry={} Phase=PASSING_THROUGH interior={}",
                zombie.getId(), broken, state.interior);
    }

    public static void clear(Zombie zombie, String reason) {
        if (STATES.remove(zombie) != null) {
            ApocalypseFirstLight.LOGGER.debug("[AFL ENTRY] Zombie={} Cancel reason={}", zombie.getId(), reason);
        }
    }

    private static boolean hasReachablePath(Zombie zombie, Vec3 target) {
        if (zombie.level() instanceof ServerLevel level) {
            InfectedAiDiagnostics.pathAttempt(level);
        }
        Path path = zombie.getNavigation().createPath(BlockPos.containing(target), 0);
        if (path == null || !path.canReach()) {
            if (zombie.level() instanceof ServerLevel level) {
                InfectedAiDiagnostics.failedPathRetry(level);
            }
            return false;
        }
        return true;
    }

    private static Candidate findBest(Zombie zombie, Vec3 target) {
        List<Candidate> candidates = new ArrayList<>();
        BlockPos origin = zombie.blockPosition();
        boolean mobGriefing = ForgeEventFactory.getMobGriefingEvent(zombie.level(), zombie);
        Set<BlockPos> claimedByOthers = InfectedBreakerClaims.claimedByOthers(zombie);
        for (int x = -ENTRY_SEARCH_RADIUS; x <= ENTRY_SEARCH_RADIUS; x++) {
            for (int z = -ENTRY_SEARCH_RADIUS; z <= ENTRY_SEARCH_RADIUS; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos position = origin.offset(x, y, z);
                    BlockState blockState = zombie.level().getBlockState(position);
                    if (!isEntry(blockState) || (!mobGriefing && isClosed(blockState))
                            || claimedByOthers.contains(position)) {
                        continue;
                    }
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos approach = position.relative(direction);
                        BlockPos interior = position.relative(direction.getOpposite());
                        if (!standable(zombie, approach) || !standable(zombie, interior)) {
                            continue;
                        }
                        double improvement = Vec3.atCenterOf(approach).distanceToSqr(target)
                                - Vec3.atCenterOf(interior).distanceToSqr(target);
                        if (improvement <= 0.0) {
                            continue;
                        }
                        double score = base(blockState) + Vec3.atCenterOf(approach).distanceToSqr(zombie.position())
                                - improvement;
                        candidates.add(new Candidate(position.immutable(), approach.immutable(),
                                interior.immutable(), score));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score));
        int pathChecks = Math.min(MAX_CANDIDATE_PATH_CHECKS, candidates.size());
        for (int index = 0; index < pathChecks; index++) {
            Candidate candidate = candidates.get(index);
            if (zombie.level() instanceof ServerLevel level) {
                InfectedAiDiagnostics.pathAttempt(level);
            }
            Path path = zombie.getNavigation().createPath(candidate.approach(), 0);
            if (path != null && path.canReach()) {
                return candidate;
            }
        }
        if (pathChecks > 0 && zombie.level() instanceof ServerLevel level) {
            InfectedAiDiagnostics.failedPathRetry(level);
        }
        return null;
    }

    private static boolean standable(Zombie zombie, BlockPos position) {
        return zombie.level().getBlockState(position).getCollisionShape(zombie.level(), position).isEmpty()
                && zombie.level().getBlockState(position.above())
                .getCollisionShape(zombie.level(), position.above()).isEmpty()
                && !zombie.level().getBlockState(position.below())
                .getCollisionShape(zombie.level(), position.below()).isEmpty();
    }

    private static boolean isEntry(BlockState blockState) {
        if (blockState.is(BlockTags.LEAVES) || !InfectedBreachRules.canBreak(blockState)) {
            return false;
        }
        return blockState.is(BlockTags.WOODEN_DOORS)
                || blockState.is(BlockTags.FENCE_GATES)
                || blockState.is(BlockTags.WOODEN_TRAPDOORS)
                || blockState.is(BlockTags.WOODEN_FENCES)
                || blockState.getBlock() == Blocks.GLASS_PANE
                || blockState.getBlock() instanceof StainedGlassPaneBlock
                || blockState.getBlock() instanceof GlassBlock;
    }

    private static boolean isClosed(BlockState blockState) {
        return InfectedBreachRules.canBreak(blockState)
                && !(blockState.hasProperty(BlockStateProperties.OPEN)
                && blockState.getValue(BlockStateProperties.OPEN));
    }

    private static double base(BlockState blockState) {
        if (blockState.hasProperty(BlockStateProperties.OPEN) && blockState.getValue(BlockStateProperties.OPEN)) {
            return -1000.0;
        }
        if (blockState.is(BlockTags.WOODEN_DOORS)) {
            return -800.0;
        }
        if (blockState.is(BlockTags.FENCE_GATES)) {
            return -700.0;
        }
        if (blockState.getBlock() == Blocks.GLASS_PANE || blockState.getBlock() instanceof StainedGlassPaneBlock) {
            return -500.0;
        }
        if (blockState.getBlock() instanceof GlassBlock) {
            return -400.0;
        }
        return -200.0;
    }

    private static final class State {
        private long createdTime = Long.MIN_VALUE;
        private long nextSearchAt = Long.MIN_VALUE;
        private boolean passing;
        private BlockPos entry;
        private BlockPos approach;
        private BlockPos interior;
    }

    private record Candidate(BlockPos block, BlockPos approach, BlockPos interior, double score) {
    }
}
