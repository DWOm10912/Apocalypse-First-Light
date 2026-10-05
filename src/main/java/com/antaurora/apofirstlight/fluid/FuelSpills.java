package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fuel stains of one level, server side and saved with it (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"): the
 * fuel nozzles' server-side jets (FuelDispenserBlockEntity) land here ({@link #wet}). Every second, stains dry for longer than
 * their life go. Changes go to the players tracking the stain's chunk (AflNetwork#sendFuelStains, throttled while one is
 * wetted continuously: a size step of 0.04 or 2 s), and a player starting to track a chunk gets its stains. Living things
 * standing on a floor stain get soaked in that fuel (FuelSoakedEffect: gasoline 30 s, diesel 90 s after they step off); they slip on it
 * through the LivingEntity mixin (FuelStainIndex#under).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FuelSpills extends SavedData {
    private static final String NAME = "afl_fuel_spills";
    public static final int GASOLINE_SOAK = 600, DIESEL_SOAK = 1800;

    final FuelStainIndex index = new FuelStainIndex();
    private long nextId;

    public static FuelSpills get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FuelSpills::load, FuelSpills::new, NAME);
    }

    public FuelStainIndex index() {
        return index;
    }

    /** Splatter beside a floor landing, a full pool spreading past its edge, fuel dripping off a wall's edge or a ceiling. */
    private static final float SPLATTER = 0.3F, SPREAD = 0.35F, DRIP_DOWN = 0.35F;
    /** Surface probing (blocks): step, and how far round a stain (a wall run reaches further down). */
    private static final double PROBE_STEP = 0.05, PROBE_SIDE = 0.6, PROBE_DOWN = 4.0;

    /**
     * Fuel lands at {@code at} on a block's {@code face}: wets the stain there (it spreads a little), or starts one. On a
     * floor, a drop sometimes splatters beside it, and a stain already at its full size spreads into a new one just past
     * its edge (a pool grows while the stream stays on it). On a wall whose run has reached the lower edge of its surface,
     * and on a ceiling, the fuel sometimes drips down onto whatever floor is below (up to 4 blocks). Spread stains only go on a real
     * floor at the same height and not on top of another stain.
     */
    public void wet(ServerLevel level, Vec3 at, Direction face, boolean diesel) {
        RandomSource random = level.random;
        FuelStainIndex.Stain stain = wetOne(level, at, face, diesel, FuelStainIndex.FIRST * (0.8F + 0.4F * random.nextFloat()));
        if (face == Direction.UP) {
            if (random.nextFloat() < SPLATTER) {
                Vec3 p = floorAt(level, around(at, 0.25 + 0.35 * random.nextDouble(), random));
                if (p != null && !index.crowded(p, Direction.UP, 0.12)) wetOne(level, p, Direction.UP, diesel, 0.06F + 0.05F * random.nextFloat());
            }
            if (stain.size >= stain.maxSize() - 1e-3F && random.nextFloat() < SPREAD) {
                Vec3 p = floorAt(level, around(stain.pos, stain.size * 0.45 + 0.12, random));
                if (p != null && !index.crowded(p, Direction.UP, 0.2)) wetOne(level, p, Direction.UP, diesel, FuelStainIndex.FIRST);
            }
        } else if (random.nextFloat() < DRIP_DOWN) {
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal()), from = null;
            if (face == Direction.DOWN) from = stain.pos.add(0, -0.03, 0);
            else if (stain.runAtEdge(level.getGameTime())) from = stain.pos.add(FuelStainIndex.axisV(face).scale(stain.v1)).add(n.scale(0.03));
            if (from != null) {
                BlockHitResult below = level.clip(new ClipContext(from, from.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
                if (below.getType() == HitResult.Type.BLOCK && below.getDirection() == Direction.UP && !below.isInside()
                        && level.getFluidState(below.getBlockPos()).isEmpty()) wetOne(level, below.getLocation(), Direction.UP, diesel, FuelStainIndex.FIRST * 0.8F);
            }
        }
    }

    /** How far the stain's surface reaches round it, in its face plane (FuelStainIndex.axisU / axisV). */
    private static void probe(ServerLevel level, FuelStainIndex.Stain stain) {
        Vec3 u = FuelStainIndex.axisU(stain.face), v = FuelStainIndex.axisV(stain.face);
        stain.u1 = (float) reach(level, stain, u, PROBE_SIDE);
        stain.u0 = (float) -reach(level, stain, u.scale(-1), PROBE_SIDE);
        stain.v1 = (float) reach(level, stain, v, stain.wall() ? PROBE_DOWN : PROBE_SIDE);
        stain.v0 = (float) -reach(level, stain, v.scale(-1), PROBE_SIDE);
    }

    private static double reach(ServerLevel level, FuelStainIndex.Stain stain, Vec3 direction, double max) {
        for (double d = PROBE_STEP; d <= max + 1e-6; d += PROBE_STEP) {
            if (!surfaceAt(level, stain.pos.add(direction.scale(d)), stain)) return d - PROBE_STEP * 0.5;
        }
        return max;
    }

    /** The stain's surface goes on at {@code p}: a short ray into it meets the same face in the same plane, from open air. */
    private static boolean surfaceAt(ServerLevel level, Vec3 p, FuelStainIndex.Stain stain) {
        Vec3 n = Vec3.atLowerCornerOf(stain.face.getNormal());
        BlockHitResult hit = level.clip(new ClipContext(p.add(n.scale(0.08)), p.subtract(n.scale(0.08)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && hit.getDirection() == stain.face
                && Math.abs(hit.getLocation().subtract(stain.pos).dot(n)) < 0.02;
    }

    private static Vec3 around(Vec3 at, double distance, RandomSource random) {
        double angle = random.nextDouble() * 2 * Math.PI;
        return at.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    /** The point on the floor right under {@code p} (a block's top face within 0.1 of p's height, not a liquid), or null. */
    @Nullable
    private static Vec3 floorAt(ServerLevel level, Vec3 p) {
        if (!level.isLoaded(BlockPos.containing(p))) return null;
        BlockHitResult hit = level.clip(new ClipContext(p.add(0, 0.25, 0), p.add(0, -0.25, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside() || hit.getDirection() != Direction.UP) return null;
        if (!level.getFluidState(hit.getBlockPos()).isEmpty() || Math.abs(hit.getLocation().y - p.y) > 0.1) return null;
        return hit.getLocation();
    }

    /** Wets the stain at {@code at} or starts one of {@code size}; syncs it (throttled while it is wetted again and again). */
    private FuelStainIndex.Stain wetOne(ServerLevel level, Vec3 at, Direction face, boolean diesel, float size) {
        long now = level.getGameTime();
        FuelStainIndex.Stain stain = index.near(at, face, diesel);
        if (stain == null) {
            stain = new FuelStainIndex.Stain(nextId++, at, face, diesel, size, now);
            probe(level, stain);
            index.put(stain);
            List<Long> trimmed = new ArrayList<>();
            index.trim(s -> trimmed.add(s.id));
            if (!trimmed.isEmpty()) AflNetwork.sendFuelStainsRemovedEverywhere(level, trimmed);
        } else {
            stain.wet = now;
            stain.size = Math.min(stain.maxSize(), stain.size + FuelStainIndex.GROW);
            boolean unprobed = stain.v1 >= FuelStainIndex.UNPROBED - 1e-3F;   // saved before stains knew their surface
            if (unprobed) probe(level, stain);
            if (!unprobed && stain.size - stain.syncedSize < 0.04F && now - stain.syncedWet < 40) {
                setDirty();
                return stain;
            }
        }
        stain.syncedSize = stain.size;
        stain.syncedWet = now;
        AflNetwork.sendFuelStains(level, new ChunkPos(stain.chunk()), List.of(stain), List.of());
        setDirty();
        return stain;
    }

    private void tick(ServerLevel level) {
        if (index.isEmpty()) return;
        Map<Long, List<Long>> removed = new HashMap<>();
        index.expire(level.getGameTime(), s -> removed.computeIfAbsent(s.chunk(), k -> new ArrayList<>()).add(s.id));
        if (removed.isEmpty()) return;
        removed.forEach((chunk, ids) -> AflNetwork.sendFuelStains(level, new ChunkPos(chunk), List.of(), ids));
        setDirty();
    }

    // ---- events ----

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % 20 == 0) get(level).tick(level);
    }

    /** A player starting to see a chunk gets its stains. */
    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        List<FuelStainIndex.Stain> stains = get(event.getLevel()).index.inChunk(event.getPos().toLong());
        if (!stains.isEmpty()) AflNetwork.sendFuelStainsTo(event.getPlayer(), stains);
    }

    /** Standing on a floor stain soaks: the effect keeps going for a while after stepping off (diesel longer). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 5 != 0 || entity.isSpectator()) return;
        if (entity instanceof ServerPlayer player && player.isSpectator()) return;
        FuelStainIndex.Stain stain = get(level).index.under(entity);
        if (stain == null) return;
        entity.addEffect(new MobEffectInstance((stain.diesel ? AflMobEffects.DIESEL_SOAKED : AflMobEffects.GASOLINE_SOAKED).get(),
                stain.diesel ? DIESEL_SOAK : GASOLINE_SOAK, 0, false, false, true));
    }

    // ---- saved data ----

    private static FuelSpills load(CompoundTag tag) {
        FuelSpills spills = new FuelSpills();
        spills.nextId = tag.getLong("NextId");
        ListTag list = tag.getList("Stains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            FuelStainIndex.Stain stain = new FuelStainIndex.Stain(s.getLong("Id"), new Vec3(s.getDouble("X"), s.getDouble("Y"), s.getDouble("Z")),
                    Direction.from3DDataValue(s.getByte("Face")), s.getBoolean("Diesel"), s.getFloat("Size"), s.getLong("Wet"));
            stain.syncedSize = stain.size;
            stain.syncedWet = stain.wet;
            if (s.contains("Born")) stain.born = s.getLong("Born");
            if (s.contains("U0")) {
                stain.u0 = s.getFloat("U0");
                stain.u1 = s.getFloat("U1");
                stain.v0 = s.getFloat("V0");
                stain.v1 = s.getFloat("V1");
            }
            spills.index.put(stain);
            spills.nextId = Math.max(spills.nextId, stain.id + 1);
        }
        return spills;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("NextId", nextId);
        ListTag list = new ListTag();
        for (FuelStainIndex.Stain stain : index.all()) {
            CompoundTag s = new CompoundTag();
            s.putLong("Id", stain.id);
            s.putDouble("X", stain.pos.x);
            s.putDouble("Y", stain.pos.y);
            s.putDouble("Z", stain.pos.z);
            s.putByte("Face", (byte) stain.face.get3DDataValue());
            s.putBoolean("Diesel", stain.diesel);
            s.putFloat("Size", stain.size);
            s.putLong("Wet", stain.wet);
            s.putLong("Born", stain.born);
            s.putFloat("U0", stain.u0);
            s.putFloat("U1", stain.u1);
            s.putFloat("V0", stain.v0);
            s.putFloat("V1", stain.v1);
            list.add(s);
        }
        tag.put("Stains", list);
        return tag;
    }
}
