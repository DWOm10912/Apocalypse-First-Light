package com.antaurora.apofirstlight.world;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.LightningRodTip;
import com.antaurora.apofirstlight.health.LightningInjuryConfig;
import com.antaurora.apofirstlight.mixin.LightningBoltAccessor;
import com.antaurora.apofirstlight.tree.TreeBlocks;
import com.antaurora.apofirstlight.tree.TreeIndex;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lightning V2 (docs/gameplay/lightning_v2_plan.md 2-3): where a strike lands, on the server. Every real strike's target
 * (vanilla's findLightningTargetAround: regional weather's storms, vanilla's own; mixin LightningTargetServerLevelMixin)
 * comes from the rolling sphere (StrikePoint) over the loaded columns within the strike distance (their highest block,
 * crowns included; never a chunk loaded for it), the living things under the open sky and the T15s. A lightning rod's tip
 * (LightningRodTip) counts strike.rod_reach higher. No warning before: the risk is where one stands.
 * <p>
 * The aim is kept for the bolt that comes of it (matched by its block within AIM_TICKS): as it joins the level the bolt
 * is moved to the struck point itself (a rod's tip, a truck's roof, a crown's top) and the aim stays with it for
 * LightningInjury (who was struck; where the current reaches the ground: a tree's trunk foot) and LightningTrees (what it
 * does to a tree). A rod, a truck or a tree lights no fire of vanilla's (mixin LightningBoltFireMixin): a tree's crown
 * catches fire by its own chance (LightningTrees).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LightningAim {
    /** What was struck. */
    public enum Kind { GROUND, LIVING, VEHICLE, TREE, ROD }

    /**
     * One strike's aim: the block its bolt is made at (column), the struck point (at), where its current reaches the
     * ground for the injuries (ground), the living thing struck (target), a tree's root, the strike distance (m).
     */
    public record Aim(ResourceKey<Level> level, BlockPos column, Vec3 at, Vec3 ground, Kind kind, @Nullable UUID target,
                      @Nullable BlockPos root, double distance, long tick) {}

    private static final int AIM_TICKS = 2, KEEP_TICKS = 60;
    private static final List<Aim> PENDING = new ArrayList<>();
    private static final Map<UUID, Aim> BOLTS = new HashMap<>();

    private LightningAim() {
    }

    /** The mixin: the struck block for a strike around pos, its aim kept for the bolt; null: vanilla's (strike.enabled off). */
    @Nullable
    public static BlockPos target(ServerLevel level, BlockPos pos) {
        var c = LightningInjuryConfig.get().strike;
        if (!c.enabled) return null;
        Aim aim = pick(level, pos.getX() + 0.5, pos.getZ() + 0.5, StrikePoint.distance(level.random.nextDouble(), c.distance));
        if (aim == null) return null;
        PENDING.add(aim);
        return aim.column;
    }

    /** The rolling sphere for a leader over (x0, z0) at strike distance d (nothing kept); null where nothing near is loaded. */
    @Nullable
    public static Aim pick(ServerLevel level, double x0, double z0, double d) {
        double reach = LightningInjuryConfig.get().strike.rodReach;
        LevelChunk[] last = new LevelChunk[1];
        StrikePoint.Columns columns = (x, z, out) -> {
            LevelChunk chunk = last[0];
            if (chunk == null || chunk.getPos().x != x >> 4 || chunk.getPos().z != z >> 4) {
                chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) return false;
                last[0] = chunk;
            }
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);   // the highest block that is not air
            out[0] = y + 1;
            out[1] = 0;
            BlockState s = chunk.getBlockState(new BlockPos(x, y, z));
            if (s.getBlock() instanceof LightningRodTip rod) {
                double tip = rod.tip(s);
                if (!Double.isNaN(tip)) {
                    out[0] = y + tip;
                    out[1] = reach;
                }
            }
            return true;
        };
        List<StrikePoint.Thing> things = new ArrayList<>();
        AABB box = new AABB(x0 - d, level.getMinBuildHeight(), z0 - d, x0 + d, level.getMaxBuildHeight(), z0 + d);
        for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isPassenger())) {
            if (e instanceof PickupTruckEntity) things.add(new StrikePoint.Thing(e.getX(), e.getZ(), e.getBoundingBox().maxY, e));
            else if (e instanceof LivingEntity && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                    && level.canSeeSky(BlockPos.containing(e.getX(), e.getBoundingBox().maxY, e.getZ())))
                things.add(new StrikePoint.Thing(e.getX(), e.getZ(), e.getBoundingBox().maxY, e));
        }
        StrikePoint.Pick p = StrikePoint.pick(x0, z0, d, columns, things);
        if (p == null) return null;
        long now = level.getGameTime();
        if (p.thing() instanceof PickupTruckEntity truck) {
            Vec3 roof = new Vec3(p.x(), p.y(), p.z());
            return new Aim(level.dimension(), BlockPos.containing(roof), roof, roof, Kind.VEHICLE, null, null, d, now);
        }
        if (p.thing() instanceof LivingEntity e) {
            Vec3 feet = e.position();
            return new Aim(level.dimension(), BlockPos.containing(feet), feet, feet, Kind.LIVING, e.getUUID(), null, d, now);
        }
        return column(level, (int) Math.floor(p.x()), (int) Math.floor(p.z()), d);
    }

    /** A strike on this column's highest block: a rod's tip, a tree's crown (its current down the trunk), else the ground. */
    public static Aim column(ServerLevel level, int x, int z, double d) {
        long now = level.getGameTime();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos top = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(top);
        if (s.getBlock() instanceof LightningRodTip rod && !Double.isNaN(rod.tip(s))) {
            Vec3 tip = new Vec3(x + 0.5, y + rod.tip(s), z + 0.5);
            return new Aim(level.dimension(), top, tip, tip, Kind.ROD, null, null, d, now);
        }
        Vec3 at = new Vec3(x + 0.5, y + 1, z + 0.5);
        if (TreeBlocks.ready() && TreeBlocks.isTree(s)) {
            var found = new ArrayList<TreeIndex.Root>(2);
            TreeIndex.SERVER.roots(x, y, z, found);
            for (var r : found)
                if (r.cellAt(x, y, z) != null)
                    return new Aim(level.dimension(), top.above(), at, new Vec3(r.x + 0.5, r.y, r.z + 0.5), Kind.TREE, null, new BlockPos(r.x, r.y, r.z), d, now);
        }
        return new Aim(level.dimension(), top.above(), at, at, Kind.GROUND, null, null, d, now);
    }

    /** A command's bolt on this aim (made at aim.at). */
    public static void expect(Aim aim) {
        PENDING.add(aim);
    }

    /** First of the bolt's listeners: its aim found, it is moved to the struck point; a tree hears of it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)
                || ((LightningBoltAccessor) bolt).afl$visualOnly()) return;
        long now = level.getGameTime();
        Aim found = null;
        for (Iterator<Aim> it = PENDING.iterator(); it.hasNext(); ) {
            Aim a = it.next();
            if (now - a.tick > AIM_TICKS) it.remove();
            else if (found == null && a.level == level.dimension() && a.column.equals(bolt.blockPosition())) {
                found = a;
                it.remove();
            }
        }
        if (found == null) return;
        bolt.moveTo(found.at.x, found.at.y, found.at.z);
        BOLTS.put(bolt.getUUID(), found);
        if (found.kind == Kind.TREE) LightningTrees.struck(level, found);
    }

    /** The aim a bolt came from; null: none (a command's, a trident's, vanilla's with strike.enabled off). */
    @Nullable
    public static Aim of(Entity bolt) {
        return BOLTS.get(bolt.getUUID());
    }

    /** Mixin LightningBoltFireMixin: a rod, a truck or a tree lights no vanilla fire. */
    public static boolean noFire(LightningBolt bolt) {
        Aim a = BOLTS.get(bolt.getUUID());
        return a != null && (a.kind == Kind.ROD || a.kind == Kind.VEHICLE || a.kind == Kind.TREE);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BOLTS.isEmpty()) return;
        long now = event.getServer().overworld().getGameTime();
        BOLTS.values().removeIf(a -> now - a.tick > KEEP_TICKS || now < a.tick);
    }
}
