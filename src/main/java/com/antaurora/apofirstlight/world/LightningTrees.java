package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.LightningInjuryConfig;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.tree.FellingServer;
import com.antaurora.apofirstlight.tree.TreeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 5): what a strike does to a tree (world/LightningAim, kind TREE). The
 * current runs down the trunk: bark and splinters burst from it every time, with the crack of the wood. With strike.fell
 * the trunk shatters and the tree falls (FellingServer#fellStruck: the felling's falling tree, its stump left), any way;
 * otherwise its crown catches fire with strike.fire_wet where it rains, strike.fire_dry in a dry storm: a few fires in the
 * open cells of the crown round the struck top, kept burning there for strike.fire_seconds at most (mixin
 * FireBlockTreeCrownMixin; vanilla fire would not stay by a crown). They age and go out in the rain as any fire, and catch
 * what burns beside them, but not the crown: it neither burns away nor carries the fire to the next tree. No lasting mark
 * on the trunk yet (later).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LightningTrees {
    /** Fires lit in a crown. */
    private static final int CROWN_FIRES = 5;

    private record Hit(ResourceKey<Level> level, BlockPos root, Vec3 at) {}

    private record Fire(Set<Long> cells, long until) {}

    private static final List<Hit> PENDING = new ArrayList<>();
    private static final Map<ResourceKey<Level>, List<Fire>> FIRES = new HashMap<>();

    private LightningTrees() {
    }

    /** LightningAim: a bolt on a tree joined the level (its effects come at the tick's end, not inside the join). */
    static void struck(ServerLevel level, LightningAim.Aim aim) {
        if (aim.root() != null) PENDING.add(new Hit(level.dimension(), aim.root(), aim.at()));
    }

    /** Mixin FireBlockTreeCrownMixin: a crown fire's cell, still burning. */
    public static boolean burning(Level level, BlockPos pos) {
        List<Fire> fires = FIRES.get(level.dimension());
        if (fires == null) return false;
        long key = pos.asLong();
        for (Fire f : fires) if (f.cells.contains(key)) return true;
        return false;
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PENDING.isEmpty()) {
            List<Hit> hits = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Hit h : hits) {
                ServerLevel level = event.getServer().getLevel(h.level);
                if (level != null && level.isLoaded(h.root)) hit(level, h);
            }
        }
        if (!FIRES.isEmpty()) {
            long now = event.getServer().overworld().getGameTime();
            for (List<Fire> fires : FIRES.values()) fires.removeIf(f -> now > f.until);
            FIRES.values().removeIf(List::isEmpty);
        }
    }

    private static void hit(ServerLevel level, Hit h) {
        BlockState rootState = level.getBlockState(h.root);
        if (!TreeBlocks.ready() || rootState.getBlock() != TreeBlocks.root()) return;
        var c = LightningInjuryConfig.get().strike;
        // splinters down the trunk, from under the crown to the foot
        BlockState chips = FellingServer.chips(TreeBlocks.species(rootState));
        double x = h.root.getX() + 0.5, z = h.root.getZ() + 0.5, top = Math.min(h.at.y - 1, h.root.getY() + 12);
        for (double y = h.root.getY() + 0.5; y <= top; y += 1.5)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chips), x, y, z, 10, 0.3, 0.4, 0.3, 0.3);
        RangedSound.playWithNoise(level, null, new Vec3(x, h.root.getY() + 2, z), AflSounds.TREE_CRACK.get(), FellingServer.CRACK_RADIUS,
                0.85F + 0.1F * level.random.nextFloat(), NoiseType.BLOCK_BREAK, null);
        if (level.random.nextDouble() < c.fell && FellingServer.fellStruck(level, h.root, level.random.nextDouble() * Math.PI * 2)) return;
        boolean wet = level.isRainingAt(BlockPos.containing(h.at).above());
        if (level.random.nextDouble() < (wet ? c.fireWet : c.fireDry) && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) ignite(level, h);
    }

    /** Fires in the crown's open cells near the struck top: air beside one of its crown blocks. */
    private static void ignite(ServerLevel level, Hit h) {
        BlockPos at = BlockPos.containing(h.at);
        List<BlockPos> open = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -5, -3), at.offset(3, 0, 3))) {
            if (!level.getBlockState(p).isAir()) continue;
            for (Direction d : Direction.values())
                if (level.getBlockState(p.relative(d)).getBlock() instanceof TreeBlocks.Foliage) {
                    open.add(p.immutable());
                    break;
                }
        }
        if (open.isEmpty()) return;
        // the struck top's own cell first, then the nearest others, a little shuffled
        open.sort((a, b) -> Double.compare(a.distSqr(at) + level.random.nextDouble() * 4, b.distSqr(at) + level.random.nextDouble() * 4));
        double[] s = LightningInjuryConfig.get().strike.fireSeconds;
        long until = level.getGameTime() + Math.round((s[0] + level.random.nextDouble() * (s[1] - s[0])) * 20);
        Set<Long> cells = new HashSet<>();
        for (BlockPos p : open.subList(0, Math.min(CROWN_FIRES, open.size()))) cells.add(p.asLong());
        FIRES.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(new Fire(cells, until));
        for (long cell : cells) level.setBlockAndUpdate(BlockPos.of(cell), Blocks.FIRE.defaultBlockState());
    }
}
