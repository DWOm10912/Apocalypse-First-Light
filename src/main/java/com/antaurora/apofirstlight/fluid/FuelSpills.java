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
import net.minecraft.world.level.block.Block;
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
    /** Cells where a fire put a light block (saved: a fire that was burning when the level was saved still cleans up). */
    private final java.util.Set<Long> lights = new java.util.HashSet<>();

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
     * floor at the same height and not on top of another stain. Returns the stain wetted where it landed.
     */
    public FuelStainIndex.Stain wet(ServerLevel level, Vec3 at, Direction face, boolean diesel) {
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
        return stain;
    }

    /** A stain of {@code size} at {@code at} (or the one there grown): fuel thrown out by a burst container (FuelLeaks). */
    public FuelStainIndex.Stain spill(ServerLevel level, Vec3 at, Direction face, boolean diesel, float size) {
        return wetOne(level, at, face, diesel, size);
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

    // ---- fire (docs/gameplay/fuel_fire_v1.md) ----

    /** Flame sources that set fuel alight: fire, a lit campfire, a torch, lava (the stain's cell and the six round it). */
    static boolean flameAt(ServerLevel level, BlockPos p) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p);
        if (state.is(net.minecraft.tags.BlockTags.FIRE)) return true;
        if (state.is(net.minecraft.tags.BlockTags.CAMPFIRES) && state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
                && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) return true;
        if (state.is(net.minecraft.world.level.block.Blocks.TORCH) || state.is(net.minecraft.world.level.block.Blocks.WALL_TORCH)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_TORCH) || state.is(net.minecraft.world.level.block.Blocks.SOUL_WALL_TORCH)) return true;
        return level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static boolean flameNear(ServerLevel level, FuelStainIndex.Stain stain) {
        BlockPos cell = BlockPos.of(stain.cell());
        if (!level.isLoaded(cell) || flameAt(level, cell)) return level.isLoaded(cell);
        for (Direction d : Direction.values()) if (flameAt(level, cell.relative(d))) return true;
        return false;
    }

    /**
     * A flame on this stain for {@code ticks}: gasoline catches at once; diesel only once it has had DIESEL_HEAT ticks of
     * flame (it gives off too little vapour at ordinary temperatures). A spark is a flame of 0 ticks: gasoline only.
     */
    public void expose(ServerLevel level, FuelStainIndex.Stain stain, int ticks) {
        if (stain.burning()) return;
        if (!stain.diesel) ignite(level, stain);
        else if ((stain.heat += ticks) >= FuelStainIndex.DIESEL_HEAT) ignite(level, stain);
    }

    public void ignite(ServerLevel level, FuelStainIndex.Stain stain) {
        if (stain.burning()) return;
        long now = level.getGameTime();
        stain.ignite = now;
        stain.igniteAt = Long.MAX_VALUE;
        BlockPos cell = BlockPos.of(stain.cell());
        if (level.getBlockState(cell).isAir()) {
            level.setBlock(cell, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, stain.diesel ? 12 : 14), Block.UPDATE_ALL);
            lights.add(cell.asLong());
        }
        if (stain.size >= 0.2F || level.random.nextFloat() < 0.25F) {
            level.playSound(null, stain.pos.x, stain.pos.y, stain.pos.z, com.antaurora.apofirstlight.registry.AflSounds.FUEL_IGNITE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, stain.diesel ? 0.35F : 0.6F, stain.diesel ? 0.8F : 1.1F);
        }
        sync(level, stain, now);
        setDirty();
    }

    private void sync(ServerLevel level, FuelStainIndex.Stain stain, long now) {
        stain.syncedSize = stain.size;
        stain.syncedWet = now;
        AflNetwork.sendFuelStains(level, new ChunkPos(stain.chunk()), List.of(stain), List.of());
    }

    /** The light a fire set in this cell goes when nothing burns there any more. */
    private void releaseLight(ServerLevel level, long cell) {
        if (!lights.contains(cell)) return;
        for (FuelStainIndex.Stain s : index.inCell(cell)) if (s.burning()) return;
        lights.remove(cell);
        BlockPos p = BlockPos.of(cell);
        if (level.isLoaded(p) && level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LIGHT)) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * Every other tick: pending ignitions come due; every fourth tick unburnt stains are checked for flame sources round
     * them; burning stains burn their fuel down (gone below BURNT_OUT), reach their neighbours (gasoline after the time the
     * fire takes to run the distance, diesel after enough heat) and now and then set flammable blocks next to them alight
     * (vanilla fire, when fire spreads in this world: doFireTick).
     */
    private void fireTick(ServerLevel level) {
        long now = level.getGameTime();
        boolean sources = now % 4 == 0, blocks = now % 10 == 0 && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
        List<FuelStainIndex.Stain> catching = new ArrayList<>(), burnt = new ArrayList<>(), burning = new ArrayList<>();
        for (FuelStainIndex.Stain s : index.all()) {
            if (s.burning()) burning.add(s);
            else if (s.igniteAt <= now) catching.add(s);
            else if (sources && flameNear(level, s)) {
                if (!s.diesel || (s.heat += 4) >= FuelStainIndex.DIESEL_HEAT) catching.add(s);
            }
        }
        for (FuelStainIndex.Stain s : burning) {
            s.size -= s.burnRate() * 2;
            if (s.size < FuelStainIndex.BURNT_OUT) {
                burnt.add(s);
                continue;
            }
            for (FuelStainIndex.Stain o : index.around(s)) {
                if (o.burning()) continue;
                double d = s.pos.distanceTo(o.pos);
                if (d > s.size / 2 + o.size / 2 + FuelStainIndex.FIRE_GAP) continue;
                if (o.diesel) {
                    if ((o.heat += 2) >= FuelStainIndex.DIESEL_HEAT) catching.add(o);
                } else o.igniteAt = Math.min(o.igniteAt, now + 1 + (long) (d / FuelStainIndex.GASOLINE_SPREAD));
            }
            if (blocks && level.random.nextFloat() < 0.25F) igniteFlammable(level, s);
            if (s.syncedSize - s.size >= 0.04F) sync(level, s, now);
        }
        for (FuelStainIndex.Stain s : catching) ignite(level, s);
        if (!burnt.isEmpty()) {
            Map<Long, List<Long>> removed = new HashMap<>();
            for (FuelStainIndex.Stain s : burnt) {
                index.remove(s.id);
                removed.computeIfAbsent(s.chunk(), k -> new ArrayList<>()).add(s.id);
            }
            for (FuelStainIndex.Stain s : burnt) releaseLight(level, s.cell());
            removed.forEach((chunk, ids) -> AflNetwork.sendFuelStains(level, new ChunkPos(chunk), List.of(), ids));
        }
        if (!burning.isEmpty() || !catching.isEmpty()) setDirty();
    }

    /** A flammable block next to a burning stain catches: vanilla fire in the stain's cell (where it can stand). */
    private void igniteFlammable(ServerLevel level, FuelStainIndex.Stain stain) {
        BlockPos cell = BlockPos.of(stain.cell());
        net.minecraft.world.level.block.state.BlockState here = level.getBlockState(cell);
        if (!here.isAir() && !here.is(net.minecraft.world.level.block.Blocks.LIGHT)) return;
        for (Direction d : Direction.values()) {
            BlockPos p = cell.relative(d);
            if (!level.getBlockState(p).isFlammable(level, p, d.getOpposite())) continue;
            net.minecraft.world.level.block.state.BlockState fire = net.minecraft.world.level.block.BaseFireBlock.getState(level, cell);
            if (fire.canSurvive(level, cell)) {
                level.setBlock(cell, fire, Block.UPDATE_ALL);
                lights.remove(cell.asLong());   // the fire's own light took its place
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        FuelSpills spills = get(level);
        if (spills.index.isEmpty()) return;
        Vec3 centre = event.getExplosion().getPosition();
        ChunkPos at = new ChunkPos(BlockPos.containing(centre));
        List<FuelStainIndex.Stain> near = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (FuelStainIndex.Stain s : spills.index.inChunk(ChunkPos.asLong(at.x + dx, at.z + dz))) if (s.pos.distanceToSqr(centre) < 25) near.add(s);
        for (FuelStainIndex.Stain s : near) spills.expose(level, s, 80);   // a blast is a big flame: diesel too
    }

    private void tick(ServerLevel level) {
        if (index.isEmpty()) return;
        Map<Long, List<Long>> removed = new HashMap<>();
        index.expire(level.getGameTime(), s -> removed.computeIfAbsent(s.chunk(), k -> new ArrayList<>()).add(s.id));
        // a stain whose block was dug out or blown away goes with it (2026-10-05: they were left hanging in the air)
        List<FuelStainIndex.Stain> unsupported = new ArrayList<>();
        for (FuelStainIndex.Stain s : index.all()) {
            BlockPos under = BlockPos.containing(s.pos.subtract(Vec3.atLowerCornerOf(s.face.getNormal()).scale(0.01)));
            if (level.isLoaded(under) && level.getBlockState(under).getCollisionShape(level, under).isEmpty()) unsupported.add(s);
        }
        for (FuelStainIndex.Stain s : unsupported) {
            index.remove(s.id);
            removed.computeIfAbsent(s.chunk(), k -> new ArrayList<>()).add(s.id);
            releaseLight(level, s.cell());
        }
        if (removed.isEmpty()) return;
        removed.forEach((chunk, ids) -> AflNetwork.sendFuelStains(level, new ChunkPos(chunk), List.of(), ids));
        setDirty();
    }

    // ---- events ----

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        FuelSpills spills = get(level);
        if (level.getGameTime() % 20 == 0) spills.tick(level);
        if (level.getGameTime() % 2 == 0 && !spills.index.isEmpty()) spills.fireTick(level);
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
        FuelSpills spills = get(level);
        boolean soaked = entity.hasEffect(AflMobEffects.GASOLINE_SOAKED.get()) || entity.hasEffect(AflMobEffects.DIESEL_SOAKED.get());
        // fire: a burning stain sets things alight (soaked ones longer); soaked and burning hurts twice as much
        if (spills.index.burningNear(entity.getBoundingBox()) != null) entity.setSecondsOnFire(soaked ? 10 : 5);
        if (soaked && entity.isOnFire()) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100));
            if (entity.tickCount % 20 == 0) entity.hurt(level.damageSources().onFire(), 1.0F);
        }
        FuelStainIndex.Stain stain = spills.index.under(entity);
        if (stain == null) return;
        if (entity.isOnFire()) spills.expose(level, stain, 5);   // a burning thing walking into fuel
        entity.addEffect(new MobEffectInstance((stain.diesel ? AflMobEffects.DIESEL_SOAKED : AflMobEffects.GASOLINE_SOAKED).get(),
                stain.diesel ? DIESEL_SOAK : GASOLINE_SOAK, 0, false, false, true));
    }

    // ---- saved data ----

    private static FuelSpills load(CompoundTag tag) {
        FuelSpills spills = new FuelSpills();
        spills.nextId = tag.getLong("NextId");
        for (long cell : tag.getLongArray("Lights")) spills.lights.add(cell);
        ListTag list = tag.getList("Stains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            FuelStainIndex.Stain stain = new FuelStainIndex.Stain(s.getLong("Id"), new Vec3(s.getDouble("X"), s.getDouble("Y"), s.getDouble("Z")),
                    Direction.from3DDataValue(s.getByte("Face")), s.getBoolean("Diesel"), s.getFloat("Size"), s.getLong("Wet"));
            stain.syncedSize = stain.size;
            stain.syncedWet = stain.wet;
            if (s.contains("Born")) stain.born = s.getLong("Born");
            if (s.contains("Ignite")) stain.ignite = s.getLong("Ignite");
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
        tag.putLongArray("Lights", lights.stream().mapToLong(Long::longValue).toArray());
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
            if (stain.burning()) s.putLong("Ignite", stain.ignite);
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
