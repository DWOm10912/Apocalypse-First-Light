package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shot and burning fuel containers (2026-10-05, docs/gameplay/fuel_fire_v1.md "第二阶段"), one level's, saved with it.
 * <ul>
 *   <li><b>Holes</b>: a bullet into a container holding fuel (FuelContainers) leaves a 9 mm hole. Below the fuel's surface it
 *   leaks: out at Torricelli's speed (sqrt(2 g h), h the fuel above the hole) and at the flow a hole that size lets through
 *   (discharge coefficient 0.62), taken from the container. A server-side jet (fluid/LiquidJet) lays the stains where it
 *   lands; clients draw the stream from the synced holes (ClientFuelLeaks). Above the surface a hole only lets vapour out.</li>
 *   <li><b>Sparks</b>: a bullet off steel sometimes strikes a spark; the spark sometimes sets gasoline alight (stains and
 *   vapour at holes near it). Never diesel.</li>
 *   <li><b>Heat</b>: fire against a container (a burning stain next to one of its blocks) heats it; a flame at one of its
 *   holes heats it much faster. Gasoline catches as soon as a flame reaches a hole; otherwise a container catches after
 *   {@link #GASOLINE_CATCH} / {@link #DIESEL_CATCH} ticks of heat.</li>
 *   <li><b>Catching</b>: a container that is mostly vapour (filled below {@link #VAPOUR_FILL}) explodes; a fuller one burns
 *   (its holes burn and the fuel they shed burns where it lands) and, after burning for {@link #GASOLINE_RUPTURE} /
 *   {@link #DIESEL_RUPTURE} ticks, may burst ({@link #RUPTURE_CHANCE} a second): an explosion and its fuel thrown round
 *   burning. Once burnt down to vapour it explodes.</li>
 *   <li><b>Explosions</b> break blocks (vanilla explosion with fire), sized by the fuel: a vapour explosion by the vapour
 *   volume, a burst by the fuel left; diesel 0.75 of gasoline. A blast holes and sets alight the containers near it, so a
 *   station goes up in a chain.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FuelLeaks extends SavedData {
    private static final String NAME = "afl_fuel_leaks";
    /** A 9 mm bullet hole: area (m^2), discharge coefficient. */
    private static final double HOLE_AREA = Math.PI * 0.0045 * 0.0045, DISCHARGE = 0.62;
    public static final int MAX_HOLES = 64, MAX_HOLES_PER_CONTAINER = 12;
    /** Every how many ticks the server's jet sends a parcel that wets a stain where it lands. */
    private static final int PARCEL_EVERY = 5;
    public static final double VAPOUR_FILL = 0.15;
    public static final float SPARK_CHANCE = 0.25F, SPARK_IGNITES = 0.25F;
    public static final double SPARK_REACH = 0.75;
    public static final int GASOLINE_CATCH = 200, DIESEL_CATCH = 400, HOLE_HEAT = 4;
    public static final int GASOLINE_RUPTURE = 400, DIESEL_RUPTURE = 800;
    public static final float RUPTURE_CHANCE = 0.05F;
    /** How far a blast reaches containers (blocks from its centre to their box). */
    public static final double BLAST_REACH = 6.0;

    public static final class Hole {
        public final long id;
        final BlockPos block;
        public final Vec3 at;
        public final Direction face;
        /** The surface's normal at the hole (a hit mesh's, docs/rendering/mesh_hit_runtime_v1.md; else the face's): the stream leaves along it. */
        public final Vec3 normal;
        public final boolean diesel;
        final LiquidJet jet = new LiquidJet(LiquidJet.EARTH_GRAVITY, 3.0);
        double carry;
        int timer;
        /** Synced: leaking, at what speed (blocks a second), and the container burning (the hole is a torch). */
        public boolean flowing, burning;
        public float speed;

        public Hole(long id, BlockPos block, Vec3 at, Direction face, Vec3 normal, boolean diesel) {
            this.id = id;
            this.block = block;
            this.at = at;
            this.face = face;
            this.normal = normal;
            this.diesel = diesel;
        }
    }

    private static final class Heat {
        int heat;
        long burning = -1, touched, blowAt = Long.MAX_VALUE;
        boolean rupture;
    }

    private final Map<Long, Hole> holes = new LinkedHashMap<>();
    private final Map<FuelContainers.Key, Heat> heats = new HashMap<>();
    private long nextId;

    public static FuelLeaks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FuelLeaks::load, FuelLeaks::new, NAME);
    }

    public Iterable<Hole> holes() {
        return holes.values();
    }

    /** What Jade shows for a container (by its master block; a dispenser's two lines together). */
    public record Status(int holes, int leaking, boolean burning) {}

    public Status status(ServerLevel level, BlockPos master) {
        int count = 0, leaking = 0;
        for (Hole h : holes.values()) {
            FuelContainers.Container c = FuelContainers.at(level, h.block);
            if (c == null || !c.master().equals(master)) continue;
            count++;
            if (h.flowing) leaking++;
        }
        boolean burning = false;
        for (Map.Entry<FuelContainers.Key, Heat> e : heats.entrySet()) {
            if (e.getValue().burning < 0) continue;
            FuelContainers.Container c = FuelContainers.of(level, e.getKey());
            if (c != null && c.master().equals(master)) burning = true;
        }
        return new Status(count, leaking, burning);
    }

    // ---- bullets ----

    /**
     * A bullet stopped by a block (weapon/BulletImpacts): a hole if it is a fuel container. True when a hole of a fuel
     * container is there now (the client draws it from the synced leak, not as a passing decal). Sparks: BulletImpacts.
     */
    public static boolean bullet(ServerPlayer shooter, BlockHitResult hit) {
        ServerLevel level = shooter.serverLevel();
        BlockPos pos = hit.getBlockPos();
        if (!level.isLoaded(pos)) return false;
        FuelLeaks leaks = get(level);
        FuelContainers.Container c = FuelContainers.at(level, pos);
        return c != null && c.amount() > 0 && c.box().inflate(0.02).contains(hit.getLocation())
                && leaks.puncture(level, c, pos, hit.getLocation(), hit.getDirection(), com.antaurora.apofirstlight.meshhit.MeshBlockHitResult.normalOf(hit));
    }

    /** Steel and the like (by sound type): a bullet off it can strike sparks. */
    public static boolean metal(SoundType sound) {
        return sound == SoundType.METAL || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.ANVIL || sound == SoundType.COPPER
                || sound == SoundType.CHAIN || sound == SoundType.LANTERN;
    }

    /** A spark at {@code p}: gasoline stains and the vapour at gasoline holes within reach catch. */
    public void spark(ServerLevel level, Vec3 p) {
        FuelSpills spills = FuelSpills.get(level);
        for (FuelStainIndex.Stain s : spills.index().within(p, SPARK_REACH)) if (!s.diesel) spills.expose(level, s, 0);
        for (Hole h : new ArrayList<>(holes.values())) {
            if (h.diesel || h.at.distanceTo(p) > SPARK_REACH) continue;
            FuelContainers.Container c = FuelContainers.at(level, h.block);
            if (c != null) heat(level, c, 0, true);
        }
    }

    /** A hole in {@code c} at {@code at} on {@code face} of block {@code pos}; true if there is one there now. */
    public boolean puncture(ServerLevel level, FuelContainers.Container c, BlockPos pos, Vec3 at, Direction face, Vec3 normal) {
        int count = 0;
        for (Hole h : holes.values()) {
            if (h.at.distanceToSqr(at) < 0.0025) return true;   // the same hole again
            FuelContainers.Container other = h.block.equals(pos) ? c : FuelContainers.at(level, h.block);
            if (other != null && other.key().equals(c.key())) count++;
        }
        if (holes.size() >= MAX_HOLES || count >= MAX_HOLES_PER_CONTAINER) return false;
        Hole hole = new Hole(nextId++, pos.immutable(), at, face, normal, c.diesel());
        holes.put(hole.id, hole);
        Heat heat = heats.get(c.key());
        hole.burning = heat != null && heat.burning >= 0;
        AflNetwork.sendFuelLeaks(level, List.of(hole), List.of());
        setDirty();
        return true;
    }

    // ---- heat, fire, bursts ----

    /**
     * Heat on {@code c} for {@code ticks}: at a hole (a flame there) gasoline catches at once and diesel heats
     * {@link #HOLE_HEAT} times as fast; against its walls it catches after GASOLINE_CATCH / DIESEL_CATCH ticks.
     */
    public void heat(ServerLevel level, FuelContainers.Container c, int ticks, boolean atHole) {
        if (c.amount() <= 0) return;
        long now = level.getGameTime();
        Heat heat = heats.computeIfAbsent(c.key(), k -> new Heat());
        heat.touched = now;
        if (heat.burning >= 0 || heat.blowAt != Long.MAX_VALUE) return;
        if (atHole && !c.diesel()) {
            light(level, c, heat, now);
            return;
        }
        heat.heat += atHole ? ticks * HOLE_HEAT : ticks;
        if (heat.heat >= (c.diesel() ? DIESEL_CATCH : GASOLINE_CATCH)) light(level, c, heat, now);
        setDirty();
    }

    /**
     * A blast at a container it can reach (a line from the blast's centre to the container's middle meets the container
     * first; earth or a wall between shields it): holed where that line strikes it, and set alight, diesel too.
     */
    private void blast(ServerLevel level, FuelContainers.Container c, Vec3 centre) {
        BlockHitResult line = com.antaurora.apofirstlight.meshhit.MeshHitClip.clip(level, new ClipContext(centre, c.centre(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));   // on the container's surface (its hit mesh)
        if (line.getType() == HitResult.Type.BLOCK) {
            FuelContainers.Container hit = FuelContainers.at(level, line.getBlockPos());
            if (hit == null || !hit.master().equals(c.master())) return;
            if (!line.isInside() && hit.amount() > 0) puncture(level, hit, line.getBlockPos(), line.getLocation(), line.getDirection(), com.antaurora.apofirstlight.meshhit.MeshBlockHitResult.normalOf(line));
        }
        Heat heat = heats.computeIfAbsent(c.key(), k -> new Heat());
        heat.touched = level.getGameTime();
        if (heat.burning < 0 && heat.blowAt == Long.MAX_VALUE) light(level, c, heat, level.getGameTime());
    }

    /** {@code c} catches: mostly vapour, it goes up (a few ticks later); fuller, it burns. */
    private void light(ServerLevel level, FuelContainers.Container c, Heat heat, long now) {
        if (c.fill() < VAPOUR_FILL) {
            heat.blowAt = now + 2 + level.random.nextInt(8);
            heat.rupture = false;
        } else {
            heat.burning = now;
            Vec3 p = c.centre();
            level.playSound(null, p.x, p.y, p.z, com.antaurora.apofirstlight.registry.AflSounds.FUEL_IGNITE.get(), SoundSource.BLOCKS, 1.0F, c.diesel() ? 0.7F : 0.9F);
        }
        setDirty();
    }

    /**
     * {@code c} goes up: its blocks and its fuel (with a dispenser, the other line's too) are gone, a vanilla explosion
     * with fire breaks the blocks round it, and the fuel left is thrown round it burning.
     */
    private void blow(ServerLevel level, FuelContainers.Container c, boolean rupture) {
        List<FuelContainers.Container> all = FuelContainers.together(level, c);
        double vapour = Math.max(0, c.capacity() - c.amount()) / 1000.0, fuel = c.amount() / 1000.0;   // m^3 (1 mB = 1 L)
        float power = rupture ? (float) Mth.clamp(1.5 + 1.2 * Math.cbrt(fuel), 1.5, 6.0) : (float) Mth.clamp(2.0 + 1.6 * Math.cbrt(vapour), 2.0, 7.0);
        if (c.kind() == FuelContainers.Kind.CAN) {   // a can or drum: a small blast (20 L of vapour about 1.2, an empty 200 L drum 2)
            power = rupture ? (float) Mth.clamp(0.5 + 1.8 * Math.cbrt(fuel), 0.8, 2.5) : (float) Mth.clamp(0.6 + 2.4 * Math.cbrt(vapour), 1.0, 3.0);
        }
        if (c.diesel()) power *= 0.75F;
        Vec3 centre = c.centre();
        // the containers go first: nothing of them is left to find, so the blast does not set them off again
        List<Long> gone = new ArrayList<>();
        Set<FuelContainers.Key> keys = new HashSet<>();
        for (FuelContainers.Container one : all) keys.add(one.key());
        for (Hole h : new ArrayList<>(holes.values())) {
            FuelContainers.Container other = FuelContainers.at(level, h.block);
            if (other != null && keys.contains(other.key())) {
                holes.remove(h.id);
                gone.add(h.id);
            }
        }
        keys.forEach(heats::remove);
        if (!gone.isEmpty()) AflNetwork.sendFuelLeaks(level, List.of(), gone);
        for (FuelContainers.Container one : all) FuelContainers.destroy(level, one);
        level.explode(null, centre.x, centre.y, centre.z, power, true, Level.ExplosionInteraction.BLOCK);
        AflNetwork.sendFuelBlast(level, centre, power, c.diesel());   // the smoke burst and column (client/FireFx)
        // over vanilla's blast, heard as far as the blast's noise (noise/ExplosionNoiseEvents plays vanilla's at the same radius)
        com.antaurora.apofirstlight.noise.RangedSound.play(level, centre, com.antaurora.apofirstlight.registry.AflSounds.FUEL_EXPLODE.get(),
                SoundSource.BLOCKS, com.antaurora.apofirstlight.noise.ExplosionNoiseProfile.radius(power), 1.0F,
                (c.diesel() ? 0.85F : 1.0F) * (0.92F + 0.16F * level.random.nextFloat()));
        FuelSpills spills = FuelSpills.get(level);
        for (FuelContainers.Container one : all) {
            int stains = rupture || one != c ? Mth.clamp(one.amount() / 25, one.amount() > 0 ? 3 : 0, 40) : Mth.clamp(one.amount() / 25, 0, 12);
            double reach = Math.max(2.0, power * 0.9);
            for (int i = 0; i < stains; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2, d = reach * Math.sqrt(level.random.nextDouble());
                double x = centre.x + Math.cos(angle) * d, z = centre.z + Math.sin(angle) * d;
                BlockHitResult floor = level.clip(new ClipContext(new Vec3(x, centre.y + reach, z), new Vec3(x, centre.y - reach - 2, z),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
                if (floor.getType() != HitResult.Type.BLOCK || floor.isInside() || floor.getDirection() != Direction.UP
                        || !level.getFluidState(floor.getBlockPos()).isEmpty()) continue;
                FuelStainIndex.Stain s = spills.spill(level, floor.getLocation(), Direction.UP, one.diesel(), 0.3F + 0.25F * level.random.nextFloat());
                spills.ignite(level, s);
            }
        }
        setDirty();
    }

    // ---- tick ----

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        FuelSpills spills = FuelSpills.get(level);
        List<Hole> changed = new ArrayList<>();
        List<Long> gone = new ArrayList<>();
        boolean scan = now % 4 == 0;
        Set<FuelContainers.Key> heated = new HashSet<>();
        for (Hole h : new ArrayList<>(holes.values())) {
            if (!level.isLoaded(h.block)) continue;
            FuelContainers.Container c = FuelContainers.at(level, h.block);
            if (c == null || c.diesel() != h.diesel) {   // the container is gone (or holds the other fuel now)
                holes.remove(h.id);
                gone.add(h.id);
                continue;
            }
            Heat heat = heats.get(c.key());
            boolean burning = heat != null && heat.burning >= 0;
            double head = c.surface() - h.at.y;
            boolean flowing = c.amount() > 0 && head > 0.02;
            float speed = flowing ? (float) Math.sqrt(2 * LiquidJet.EARTH_GRAVITY * head) : 0.0F;
            Vec3 n = h.normal;
            if (flowing) {
                h.carry += DISCHARGE * HOLE_AREA * speed * 1000.0 / 20.0;   // litres (mB) a tick
                if (h.carry >= 1.0) {
                    int want = (int) h.carry;
                    h.carry -= want;
                    FuelContainers.drain(level, c, want);
                }
                if (--h.timer <= 0) {
                    h.timer = PARCEL_EVERY;
                    h.jet.emit(h.at.add(n.scale(0.03)), n.scale(speed), 0.0);
                }
            }
            h.jet.tick(level, null, (parcel, hit) -> {
                if (hit.isInside() || !level.getFluidState(hit.getBlockPos()).isEmpty()) return;
                FuelStainIndex.Stain s = spills.wet(level, hit.getLocation(), hit.getDirection(), h.diesel);
                if (burning) spills.ignite(level, s);   // a burning hole's stream burns where it lands
                else if (s.burning() && !h.diesel) heat(level, c, 0, true);   // gasoline: the fire runs back up the stream
            });
            if (scan && !burning && !heated.contains(c.key())) {   // a flame at the hole
                Vec3 out = h.at.add(n.scale(0.3));
                if (spills.index().burningNear(new AABB(out, out).inflate(0.2)) != null || FuelSpills.flameAt(level, BlockPos.containing(out))) {
                    heated.add(c.key());
                    heat(level, c, 4, true);
                }
            }
            if (flowing != h.flowing || Math.abs(speed - h.speed) > 0.25F || burning != h.burning) {
                h.flowing = flowing;
                h.speed = speed;
                h.burning = burning;
                changed.add(h);
            }
        }
        if (scan) {   // fire against a container's walls: burning stains next to its blocks
            for (FuelStainIndex.Stain s : spills.index().all()) {
                if (!s.burning()) continue;
                BlockPos cell = BlockPos.of(s.cell());
                for (Direction d : Direction.values()) {
                    BlockPos p = cell.relative(d);
                    if (!level.isLoaded(p)) continue;
                    FuelContainers.Container c = FuelContainers.at(level, p);
                    if (c != null && c.amount() > 0 && heated.add(c.key())) heat(level, c, 4, false);
                }
            }
        }
        if (now % 20 == 0) {   // burning containers: burst or burnt to vapour; cooled ones forget
            for (Map.Entry<FuelContainers.Key, Heat> e : new ArrayList<>(heats.entrySet())) {
                Heat heat = e.getValue();
                if (!level.isLoaded(e.getKey().probe())) continue;
                FuelContainers.Container c = FuelContainers.of(level, e.getKey());
                if (c == null) {
                    heats.remove(e.getKey());
                    continue;
                }
                if (heat.burning >= 0 && heat.blowAt == Long.MAX_VALUE) {
                    boolean holed = false;
                    for (Hole h : holes.values()) {
                        FuelContainers.Container other = FuelContainers.at(level, h.block);
                        if (other != null && other.key().equals(e.getKey())) holed = true;
                    }
                    if (c.fill() < VAPOUR_FILL) {
                        heat.blowAt = now + 2 + level.random.nextInt(8);
                    } else if (now - heat.burning > (c.diesel() ? DIESEL_RUPTURE : GASOLINE_RUPTURE) && level.random.nextFloat() < RUPTURE_CHANCE) {
                        heat.blowAt = now + 1;
                        heat.rupture = true;
                    } else if (!holed && now - heat.touched > 100) {   // a sealed one whose fire went out
                        heat.burning = -1;
                        heat.heat = 0;
                    }
                } else if (heat.burning < 0 && heat.blowAt == Long.MAX_VALUE && now - heat.touched > 100) {
                    heats.remove(e.getKey());
                }
            }
            setDirty();
        }
        for (Map.Entry<FuelContainers.Key, Heat> e : new ArrayList<>(heats.entrySet())) {
            if (e.getValue().blowAt > now || !heats.containsKey(e.getKey())) continue;
            FuelContainers.Container c = FuelContainers.of(level, e.getKey());
            if (c == null) heats.remove(e.getKey());
            else blow(level, c, e.getValue().rupture);
        }
        changed.removeIf(h -> !holes.containsKey(h.id));
        if (!changed.isEmpty() || !gone.isEmpty()) AflNetwork.sendFuelLeaks(level, changed, gone);
        if (!changed.isEmpty() || !gone.isEmpty()) setDirty();
    }

    // ---- events ----

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        FuelLeaks leaks = get(level);
        if (leaks.holes.isEmpty() && leaks.heats.isEmpty() && (level.getGameTime() % 4 != 0 || FuelSpills.get(level).index().isEmpty())) return;
        leaks.tick(level);
    }

    @SubscribeEvent
    public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Vec3 centre = event.getExplosion().getPosition();
        FuelLeaks leaks = get(level);
        for (FuelContainers.Container c : FuelContainers.near(level, centre, BLAST_REACH)) leaks.blast(level, c, centre);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AflNetwork.sendFuelLeaksTo(player, get(player.serverLevel()).holes.values());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AflNetwork.sendFuelLeaksTo(player, get(player.serverLevel()).holes.values());
    }

    // ---- save ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Hole h : holes.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", h.id);
            t.putLong("Block", h.block.asLong());
            t.putDouble("X", h.at.x);
            t.putDouble("Y", h.at.y);
            t.putDouble("Z", h.at.z);
            t.putByte("Face", (byte) h.face.get3DDataValue());
            t.putDouble("NX", h.normal.x);
            t.putDouble("NY", h.normal.y);
            t.putDouble("NZ", h.normal.z);
            t.putBoolean("Diesel", h.diesel);
            list.add(t);
        }
        tag.put("Holes", list);
        ListTag hot = new ListTag();
        heats.forEach((key, heat) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("Probe", key.probe().asLong());
            t.putInt("Heat", heat.heat);
            t.putLong("Burning", heat.burning);
            t.putLong("Touched", heat.touched);
            t.putLong("BlowAt", heat.blowAt);
            t.putBoolean("Rupture", heat.rupture);
            hot.add(t);
        });
        tag.put("Heats", hot);
        tag.putLong("NextId", nextId);
        return tag;
    }

    private static FuelLeaks load(CompoundTag tag) {
        FuelLeaks leaks = new FuelLeaks();
        for (Tag raw : tag.getList("Holes", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Direction face = Direction.from3DDataValue(t.getByte("Face"));
            Vec3 normal = t.contains("NX") ? new Vec3(t.getDouble("NX"), t.getDouble("NY"), t.getDouble("NZ")) : Vec3.atLowerCornerOf(face.getNormal());
            Hole h = new Hole(t.getLong("Id"), BlockPos.of(t.getLong("Block")), new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")),
                    face, normal, t.getBoolean("Diesel"));
            leaks.holes.put(h.id, h);
        }
        for (Tag raw : tag.getList("Heats", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Heat heat = new Heat();
            heat.heat = t.getInt("Heat");
            heat.burning = t.getLong("Burning");
            heat.touched = t.getLong("Touched");
            heat.blowAt = t.getLong("BlowAt");
            heat.rupture = t.getBoolean("Rupture");
            leaks.heats.put(new FuelContainers.Key(BlockPos.of(t.getLong("Probe"))), heat);
        }
        leaks.nextId = tag.getLong("NextId");
        return leaks;
    }
}
