package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.hearing.HearingLoad;
import com.antaurora.apofirstlight.hearing.TinnitusConfig;
import com.antaurora.apofirstlight.inventory.grid.CarriedWeaponsSync;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.item.EnergyBatteryItem;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.weapon.NativeMagazineItem;
import com.antaurora.apofirstlight.mixin.LightningBoltAccessor;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
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
 * Lightning injury V1 (docs/gameplay/lightning_injury_v1_plan.md, user 2026-10-21 "按推荐做"). Replaces Lightning V1's
 * 1000 damage. Each bolt (not a visual-only one), the tick after it joins the level, sorts every living thing near it:
 * <ul>
 * <li><b>direct</b>: the one the strike aimed at (mixin LightningTargetServerLevelMixin), or with no aim known the nearest
 * within direct_range across;</li>
 * <li><b>side flash</b>: within side_range across of the struck column, its top at most conductor_height above the feet and
 * at most side_height below them;</li>
 * <li><b>ground current</b>: out to ground_range across (water_range when the strike or the one hit is in water; animals
 * × animal_ground), on the ground or in water, the strike at most ground_height below the feet.</li>
 * </ul>
 * Under a roof (no sky over the eyes), or in the T15's cab with both doors shut, nothing reaches them; in the cab with a
 * door open a direct strike counts as a side flash. Players roll death, health, fainting, paralysed legs, a burst eardrum
 * (LightningInjuryConfig, per way); other living things lose health only (a direct strike kills).
 * <p>
 * A player's states, saved in the persistent data ({@code afl_lightning}), cleared by death:
 * <ul>
 * <li><b>fainted</b> (ticks): lying (forced crawl pose), no moving or using anything; a hurt of wake_damage wakes;</li>
 * <li><b>paralysed legs</b> (ticks, after fainting): lying, crawling × crawl, hands free; then up (FallLanding#getUp);</li>
 * <li><b>weak legs</b> (ticks, after that): moving × weak_move, no sprint or jump, steps × weak_stamina;</li>
 * <li><b>burst eardrum</b> (one side) and <b>aftermath</b> (game days, online, asleep × sleep): the world quieter
 * (client/TinnitusClient), and stamina and health regeneration slower, a shaky aim, headache blur spells.</li>
 * </ul>
 * The own player gets them as LightningPackets.State; the moment of a strike as LightningPackets.Struck (the flash).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LightningInjury {
    /** How a strike reached someone (the Struck packet sends the ordinal). */
    public enum Way { DIRECT, SIDE, GROUND }

    static final String KEY = "afl_lightning";
    private static final int VERSION = 1;
    private static final UUID MOVE_ID = UUID.fromString("c4b1e7d2-6a35-4f0e-9b8d-2f71a5c3e904");
    /** The struck column reaches at most this far down to the feet of the one beside it (a tree's height). */
    private static final double CONDUCTOR_HEIGHT = 20;
    /** An aim is forgotten after this many ticks (the bolt joins the level in the same tick). */
    private static final int AIM_TICKS = 2;
    /** Hurt within this many ticks of the strike (its own burning) does not wake the fainted. */
    private static final int STRIKE_BURN_TICKS = 100;
    /**
     * Fire the bolt lights where it struck a person (direct or side flash) is put out for this long, its re-strokes
     * included: the current went through them, not into the grass under them (people struck do not go up in flames,
     * docs/gameplay/lightning_injury_v1_plan.md 3.2), and lying there fainted or paralysed they could not get out of it.
     */
    private static final int CLEAR_FIRE_TICKS = 40;

    static final class State {
        int faint, faintTotal, paralysis, paralysisTotal, weak, weakTotal;
        double eardrum, eardrumTotal, aftermath, aftermathTotal;
        byte eardrumSide;
        /** Lightning's fern-like mark (ticks left, online, asleep × sleep), the part it starts from, its seed. */
        double fern, fernTotal;
        byte fernPart = -1;
        int fernSeed;
        /** The forced crawl pose is on (the server's own record). */
        boolean down, dirty;
        /** Why it lies, as last sent to the players tracking it (LightningPackets.Lying). */
        byte lying;
        int sinceSync;
        /** Server tick of the last strike (not saved): its own burning does not wake. */
        long struckAt = Long.MIN_VALUE / 2;

        boolean any() {
            return faint > 0 || paralysis > 0 || weak > 0 || eardrum > 0 || aftermath > 0 || fern > 0;
        }
    }

    private record Aim(ResourceKey<Level> level, BlockPos pos, UUID target, long tick) {}

    private record Strike(ResourceKey<Level> level, Vec3 at, @Nullable UUID target) {}

    private record ClearFire(ResourceKey<Level> level, UUID player, int until) {}

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Aim> AIMS = new ArrayList<>();
    private static final List<Strike> PENDING = new ArrayList<>();
    private static final List<ClearFire> CLEAR_FIRE = new ArrayList<>();

    private LightningInjury() {
    }

    // ---- the strike ----

    /** The mixin chose this living thing to strike at pos. */
    public static void aimed(ServerLevel level, BlockPos pos, Entity target) {
        AIMS.add(new Aim(level.dimension(), pos.immutable(), target.getUUID(), level.getGameTime()));
    }

    /** Living things are hurt here once per bolt (the strike's tick), never by vanilla's every-flash hit; the rest keep vanilla's. */
    @SubscribeEvent
    public static void struck(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof LivingEntity) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)
                || ((LightningBoltAccessor) bolt).afl$visualOnly()) return;
        UUID target = null;
        long now = level.getGameTime();
        for (Iterator<Aim> it = AIMS.iterator(); it.hasNext(); ) {
            Aim a = it.next();
            if (now - a.tick > AIM_TICKS) it.remove();
            else if (target == null && a.level == level.dimension() && a.pos.equals(bolt.blockPosition())) {
                target = a.target;
                it.remove();
            }
        }
        PENDING.add(new Strike(level.dimension(), bolt.position(), target));
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PENDING.isEmpty()) {
            List<Strike> strikes = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Strike s : strikes) {
                ServerLevel level = event.getServer().getLevel(s.level);
                if (level != null) strike(level, s.at, s.target);
            }
        }
        if (!CLEAR_FIRE.isEmpty()) clearFire(event.getServer());
    }

    /** CLEAR_FIRE_TICKS: no fire in or a block around the struck one's box, and they do not burn. */
    private static void clearFire(MinecraftServer server) {
        int now = server.getTickCount();
        for (Iterator<ClearFire> it = CLEAR_FIRE.iterator(); it.hasNext(); ) {
            ClearFire c = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(c.player);
            if (now > c.until || player == null || !player.isAlive() || player.level().dimension() != c.level) {
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            AABB box = player.getBoundingBox();
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX - 1, box.minY - 0.5, box.minZ - 1),
                    BlockPos.containing(box.maxX + 1, box.maxY, box.maxZ + 1))) {
                if (level.getBlockState(pos).is(BlockTags.FIRE)) level.removeBlock(pos, false);
            }
            player.clearFire();
        }
    }

    /** One bolt at `at`: everyone near it, sorted and hurt. Also the dev command's way in (target: the one it strikes). */
    static void strike(ServerLevel level, Vec3 at, @Nullable UUID target) {
        var c = LightningInjuryConfig.get();
        double reach = c.waterRange * c.animalGround + 2;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach, CONDUCTOR_HEIGHT + 2, reach), LivingEntity::isAlive);
        LivingEntity direct = null;
        if (target != null) for (LivingEntity e : near) if (e.getUUID().equals(target)) direct = e;
        if (direct == null) {
            double best = c.directRange;
            for (LivingEntity e : near) {
                double across = across(e, at), dy = e.getY() - at.y;
                if (across <= best && dy > -2 && dy < 2) {
                    best = across;
                    direct = e;
                }
            }
        }
        for (LivingEntity e : near) {
            Way way = way(level, e, at, e == direct);
            if (way == null) continue;
            if (e instanceof ServerPlayer player) hit(player, way, across(e, at), at, level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER), true);
            else hitMob(e, way, across(e, at));
        }
    }

    private static double across(Entity e, Vec3 at) {
        return Math.hypot(e.getX() - at.x, e.getZ() - at.z);
    }

    /** How the strike reaches e, after its shelter; null: not at all. */
    @Nullable
    static Way way(ServerLevel level, LivingEntity e, Vec3 at, boolean direct) {
        var c = LightningInjuryConfig.get();
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return null;
        Way way;
        double across = across(e, at), below = e.getY() - at.y;   // the strike's top above the feet: -below
        if (direct) way = Way.DIRECT;
        else if (across <= c.sideRange && below >= -CONDUCTOR_HEIGHT && below <= c.sideHeight) way = Way.SIDE;
        else {
            boolean water = e.isInWater() || level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER);
            double range = (water ? c.waterRange : c.groundRange) * (e instanceof Animal ? c.animalGround : 1);
            if (across > range || below < -CONDUCTOR_HEIGHT || below > c.groundHeight || !(e.onGround() || e.isInWater()) || e.isPassenger()) return null;
            way = Way.GROUND;
        }
        PickupTruckEntity truck = cab(e);
        if (truck != null) {
            if (!truck.doorOpen(false) && !truck.doorOpen(true)) return null;   // the metal body takes it
            return way == Way.DIRECT ? Way.SIDE : way;
        }
        if (!level.canSeeSky(BlockPos.containing(e.getEyePosition()))) return null;   // under a roof
        return way;
    }

    @Nullable
    private static PickupTruckEntity cab(Entity rider) {
        if (!(rider.getVehicle() instanceof PickupTruckEntity truck)) return null;
        int seat = truck.seatOf(rider);
        return seat >= 0 && PickupTruckLayout.SEATS[seat].cab() ? truck : null;
    }

    private static double roll(RandomSource r, double[] range) {
        return Mth.lerp(r.nextDouble(), range[0], range[1]);
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    private static void hurt(LivingEntity e, double amount) {
        e.invulnerableTime = 0;
        e.hurt(e.damageSources().lightningBolt(), (float) amount);
    }

    /** A player struck this way, `across` blocks from the strike at `at` (canDie false: the dev command's, no death roll). */
    static void hit(ServerPlayer player, Way way, double across, Vec3 at, boolean water, boolean canDie) {
        var c = LightningInjuryConfig.get();
        var hit = switch (way) { case DIRECT -> c.direct; case SIDE -> c.side; case GROUND -> c.ground; };
        RandomSource r = player.getRandom();
        boolean wet = player.isInWaterRainOrBubble();
        boolean dies = canDie && r.nextDouble() < (wet && hit.wetDeath >= 0 ? hit.wetDeath : hit.death);
        boolean faint = !dies && r.nextDouble() < hit.unconsciousChance;
        AflNetwork.lightning(player, new LightningPackets.Struck((byte) way.ordinal(), faint || dies));
        if (dies) {
            hurt(player, 1000);
            return;
        }
        double health;
        if (way == Way.GROUND) {
            double outer = water ? c.waterRange : c.groundRange;
            health = Mth.lerp(Mth.clamp((across - c.sideRange) / (outer - c.sideRange), 0, 1), hit.health[0], hit.health[1]);
        } else {
            health = roll(r, hit.health);
        }
        List<BodyPart> metal = hit.gear ? hotMetal(player) : List.of();
        hurt(player, health + metal.size() * c.metalHealth);
        if (!player.isAlive()) return;
        BodyPart entry = burns(player, way, hit, wet, across, at, r, metal);
        if (hit.gear) {
            drain(player, r);
            throwHeld(player, r);
        }
        if (hit.fire > 0) player.setSecondsOnFire((int) Math.ceil(hit.fire));
        else if (way != Way.GROUND) CLEAR_FIRE.add(new ClearFire(player.level().dimension(), player.getUUID(), player.server.getTickCount() + CLEAR_FIRE_TICKS));
        State s = state(player);
        s.struckAt = player.server.getTickCount();
        boolean paralysed = r.nextDouble() < hit.paralysisChance;
        if (faint) s.faintTotal = s.faint = Math.max(s.faint, ticks(roll(r, hit.unconscious)));
        if (paralysed) s.paralysisTotal = s.paralysis = Math.max(s.paralysis, ticks(roll(r, hit.paralysis)));
        s.weakTotal = s.weak = Math.max(s.weak, ticks(roll(r, c.weakLegs)));
        long day = WoundConfig.get().dayTicks;
        if (r.nextDouble() < hit.eardrum) {
            s.eardrumTotal = s.eardrum = c.eardrumDays * day;
            s.eardrumSide = (byte) (r.nextBoolean() ? 1 : -1);
        }
        if (way != Way.GROUND || across <= c.groundAftermath) s.aftermathTotal = s.aftermath = c.aftermathDays * day;
        if (hit.lichtenberg && entry != null) {
            s.fernTotal = s.fern = c.lichtenbergDays * day;
            s.fernPart = (byte) entry.ordinal();
            s.fernSeed = r.nextInt();
        }
        if (hit.severeTinnitus) HearingLoad.atLeast(player, TinnitusConfig.get().stages[2]);
        if (faint || paralysed) {
            lieDown(player, s);
        } else {
            FallLanding.knockDown(player);
        }
        if (!faint && paralysed) message(player, "legs_numb");
        s.dirty = true;
        save(player, s);
        sync(player, s);
        BodyMarkSync.update(player);   // the burns and the fern on the skin at once, not at the next health sync
    }

    /** The fern-like mark for the skin (HealthPackets.Marks): none, or its strength in sixteenths, start part and seed. */
    static HealthPackets.Fern fern(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null || s.fern <= 0 || s.fernPart < 0) return HealthPackets.Fern.NONE;
        return new HealthPackets.Fern(Mth.clamp((int) Math.ceil(share(s.fern, s.fernTotal) * 15), 1, 15), BodyPart.byOrdinal(s.fernPart), s.fernSeed);
    }

    /**
     * Plan 10.3 C: metal on the body where the current passed: a slung gun on the back (chest), the holstered pistol (right
     * thigh), magazines in the rig (belly). One deep burn each.
     */
    private static List<BodyPart> hotMetal(ServerPlayer player) {
        List<BodyPart> parts = new ArrayList<>();
        var shown = CarriedWeaponsSync.of(player);
        if (!shown.back().isEmpty()) parts.add(BodyPart.CHEST);
        if (!shown.thigh().isEmpty()) parts.add(BodyPart.RIGHT_LEG);
        var rig = PlayerGrids.view(player).get(PlayerGrids.Target.RIG);
        if (rig != null) for (var e : rig.entries()) if (e.stack().getItem() instanceof NativeMagazineItem) {
            parts.add(BodyPart.BELLY);
            break;
        }
        return parts;
    }

    /**
     * Plan 10.3 B: the lightning burns where the current went in and out (direct: the head or an arm, then a leg; side
     * flash: the arm toward the strike or the chest; ground current within ground_burn_range: a leg), wet_burns more on
     * wet skin (the chest or belly), then the metal burns. Wounds.burn keeps to its own slots.
     */
    private static BodyPart burns(ServerPlayer player, Way way, LightningInjuryConfig.Hit hit, boolean wet, double across, Vec3 at, RandomSource r,
                                  List<BodyPart> metal) {
        var c = LightningInjuryConfig.get();
        int n = way == Way.GROUND && across > c.groundBurnRange ? 0 : hit.burns;
        BodyPart entry = null;
        for (int i = 0; i < n; i++) {
            BodyPart part = switch (way) {
                case DIRECT -> i == 0 ? (r.nextBoolean() ? BodyPart.HEAD : arm(r.nextBoolean())) : leg(r);
                case SIDE -> r.nextInt(3) == 0 ? BodyPart.CHEST : arm(toLeft(player, at));
                case GROUND -> leg(r);
            };
            Wounds.burn(player, part, false);
            if (i == 0) entry = part;
        }
        if (n > 0 && wet) for (int i = 0; i < c.wetBurns; i++) Wounds.burn(player, r.nextBoolean() ? BodyPart.CHEST : BodyPart.BELLY, false);
        for (BodyPart part : metal) Wounds.burn(player, part, true);
        return entry;
    }

    private static BodyPart arm(boolean left) {
        return left ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
    }

    private static BodyPart leg(RandomSource r) {
        return r.nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
    }

    /** Whether the strike is on the player's left (by where the body faces). */
    private static boolean toLeft(ServerPlayer player, Vec3 at) {
        Vec3 left = Vec3.directionFromRotation(0, player.yBodyRot - 90);
        return (at.x - player.getX()) * left.x + (at.z - player.getZ()) * left.z >= 0;
    }

    /** Plan 10.3 D: the hand jerks open, the held item lands drop_range blocks away (where there is room, else at the feet). */
    private static void throwHeld(ServerPlayer player, RandomSource r) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        double far = roll(r, LightningInjuryConfig.get().dropRange), angle = r.nextDouble() * Math.PI * 2;
        ItemEntity item = new ItemEntity(player.level(), player.getX() + Math.cos(angle) * far, player.getY() + 0.25,
                player.getZ() + Math.sin(angle) * far, held, 0, 0.15, 0);
        if (!player.level().noCollision(item)) item.setPos(player.getX(), player.getY() + 0.25, player.getZ());
        item.setPickUpDelay(40);
        player.level().addFreshEntity(item);
    }

    /** Plan 10.3 E: each battery or charged tool carried (inventory, grids, Curios) emptied with drain_chance; nothing broken. */
    private static void drain(ServerPlayer player, RandomSource r) {
        double chance = LightningInjuryConfig.get().drainChance;
        var inventory = player.getInventory();
        for (var list : List.of(inventory.items, inventory.armor, inventory.offhand)) for (ItemStack stack : list) drain(stack, r, chance);
        for (ItemStack stack : AflEquipmentSlots.allEquipped(player)) drain(stack, r, chance);
        var grids = PlayerGrids.view(player);
        boolean changed = false;
        for (ItemStack stack : grids.stacks()) changed |= drain(stack, r, chance);
        if (changed) grids.commit();
    }

    private static boolean drain(ItemStack stack, RandomSource r, double chance) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof EnergyBatteryItem) {
            if (EnergyBatteryItem.energy(stack) <= 0 || r.nextDouble() >= chance) return false;
            EnergyBatteryItem.setEnergy(stack, 0);
            return true;
        }
        var storage = stack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        if (storage == null || !storage.canExtract() || storage.getEnergyStored() <= 0 || r.nextDouble() >= chance) return false;
        for (int i = 0; i < 64 && storage.getEnergyStored() > 0; i++) if (storage.extractEnergy(Integer.MAX_VALUE, false) <= 0) break;
        return true;
    }

    private static void hitMob(LivingEntity e, Way way, double across) {
        var c = LightningInjuryConfig.get();
        switch (way) {
            case DIRECT -> {
                if (c.direct.fire > 0) e.setSecondsOnFire((int) Math.ceil(c.direct.fire));
                hurt(e, 1000);
            }
            case SIDE -> hurt(e, e.getMaxHealth() * c.mobSide);
            case GROUND -> {
                double range = c.groundRange * (e instanceof Animal ? c.animalGround : 1);
                hurt(e, e.getMaxHealth() * c.mobGround * Mth.lerp(Mth.clamp((across - c.sideRange) / (range - c.sideRange), 0, 1), 1, 1 / 3.0));
            }
        }
    }

    private static void lieDown(ServerPlayer player, State s) {
        s.down = true;
        player.setForcedPose(Pose.SWIMMING);
        player.setSprinting(false);
        player.stopUsingItem();
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.lightning." + key), true);
    }

    // ---- the states ----

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> load(player));
    }

    private static State load(ServerPlayer player) {
        State s = new State();
        CompoundTag t = player.getPersistentData().getCompound(KEY);
        s.faint = s.faintTotal = Math.max(0, t.getInt("faint"));
        s.paralysis = Math.max(0, t.getInt("paralysis"));
        s.paralysisTotal = Math.max(s.paralysis, t.getInt("paralysis_total"));
        s.weak = Math.max(0, t.getInt("weak"));
        s.weakTotal = Math.max(s.weak, t.getInt("weak_total"));
        s.eardrum = Math.max(0, t.getDouble("eardrum"));
        s.eardrumTotal = Math.max(s.eardrum, t.getDouble("eardrum_total"));
        s.eardrumSide = t.getByte("eardrum_side");
        s.aftermath = Math.max(0, t.getDouble("aftermath"));
        s.aftermathTotal = Math.max(s.aftermath, t.getDouble("aftermath_total"));
        s.fern = Math.max(0, t.getDouble("fern"));
        s.fernTotal = Math.max(s.fern, t.getDouble("fern_total"));
        s.fernPart = t.contains("fern_part") ? t.getByte("fern_part") : -1;
        s.fernSeed = t.getInt("fern_seed");
        return s;
    }

    private static void save(ServerPlayer player, State s) {
        if (!s.any()) {
            player.getPersistentData().remove(KEY);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putInt("faint", s.faint);
        t.putInt("paralysis", s.paralysis);
        t.putInt("paralysis_total", s.paralysisTotal);
        t.putInt("weak", s.weak);
        t.putInt("weak_total", s.weakTotal);
        t.putDouble("eardrum", s.eardrum);
        t.putDouble("eardrum_total", s.eardrumTotal);
        t.putByte("eardrum_side", s.eardrumSide);
        t.putDouble("aftermath", s.aftermath);
        t.putDouble("aftermath_total", s.aftermathTotal);
        t.putDouble("fern", s.fern);
        t.putDouble("fern_total", s.fernTotal);
        t.putByte("fern_part", s.fernPart);
        t.putInt("fern_seed", s.fernSeed);
        player.getPersistentData().put(KEY, t);
    }

    private static void sync(ServerPlayer player, State s) {
        s.sinceSync = 0;
        s.dirty = false;
        AflNetwork.lightning(player, new LightningPackets.State(s.faint, s.faintTotal, s.paralysis, s.paralysisTotal, s.weak, s.weakTotal,
                share(s.eardrum, s.eardrumTotal), s.eardrumSide, share(s.aftermath, s.aftermathTotal)));
    }

    private static float share(double left, double total) {
        return total > 0 ? (float) Mth.clamp(left / total, 0, 1) : 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        State s = STATES.get(player.getUUID());
        if (s == null) {
            if (!player.getPersistentData().contains(KEY)) return;
            s = state(player);
            s.dirty = true;
        }
        if (!player.isAlive()) return;
        boolean wasFaint = s.faint > 0, wasParalysed = s.paralysis > 0, wasWeak = s.weak > 0;
        if (s.faint > 0) s.faint--;
        else if (s.paralysis > 0) s.paralysis--;
        else if (s.weak > 0) s.weak--;
        if (player.tickCount % 20 == 0) {
            double step = 20 * (player.isSleeping() ? LightningInjuryConfig.get().sleep : 1);
            boolean ear = s.eardrum > 0, after = s.aftermath > 0;
            s.eardrum = Math.max(0, s.eardrum - step);
            s.aftermath = Math.max(0, s.aftermath - step);
            s.fern = Math.max(0, s.fern - step);
            if (ear && s.eardrum <= 0) message(player, "eardrum_healed");
            if (after && s.aftermath <= 0) message(player, "aftermath_gone");
            s.sinceSync++;
        }
        if (wasFaint && s.faint == 0) {
            s.dirty = true;
            message(player, s.paralysis > 0 ? "woke_numb" : "woke");
        }
        if (wasParalysed && s.paralysis == 0) {
            s.dirty = true;
            message(player, "legs_feel");
        }
        if (wasWeak && s.weak == 0) {
            s.dirty = true;
            message(player, "legs_back");
        }
        boolean down = s.faint > 0 || s.paralysis > 0;
        if (down) {
            if (!s.down || player.getForcedPose() != Pose.SWIMMING) lieDown(player, s);
            if (s.faint > 0 && player.isUsingItem()) player.stopUsingItem();
        } else if (s.down) {
            s.down = false;
            player.setForcedPose(null);
            // up, unless a low ceiling keeps it crawling (it stands by itself once out from under it)
            if (player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7)))
                FallLanding.getUp(player);
        }
        if ((down || s.weak > 0) && player.isSprinting() && !player.isPassenger()) player.setSprinting(false);
        byte lying = lying(s);
        if (lying != s.lying) {
            s.lying = lying;
            AflNetwork.lightningLying(player, new LightningPackets.Lying(player.getId(), lying));
        }
        moveModifier(player, s);
        if (s.dirty || s.sinceSync >= 5) {
            save(player, s);
            sync(player, s);
        }
        if (!s.any()) {
            STATES.remove(player.getUUID());
            player.getPersistentData().remove(KEY);
        }
    }

    private static byte lying(State s) {
        return s.faint > 0 ? LightningPackets.Lying.OUT : s.paralysis > 0 ? LightningPackets.Lying.DRAG : LightningPackets.Lying.NONE;
    }

    /** A viewer starts tracking a player: why it lies (always sent: a respawned player keeps its entity id). */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        State s = STATES.get(target.getUUID());
        AflNetwork.lightningLyingTo(viewer, new LightningPackets.Lying(target.getId(), s == null ? LightningPackets.Lying.NONE : s.lying));
    }

    private static void moveModifier(ServerPlayer player, State s) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        var c = LightningInjuryConfig.get();
        double m = s.faint > 0 ? 0 : s.paralysis > 0 ? c.crawl : s.weak > 0 ? c.weakMove : 1;
        // with a broken leg the heavier of the two, not both (weak legs; crawling and fainted stand on their own)
        double broken = Fractures.moveMultiplier(Fractures.of(player));
        if (s.weak > 0 && s.faint == 0 && s.paralysis == 0 && broken < 1) m = Math.min(m, broken) / broken;
        AttributeModifier old = speed.getModifier(MOVE_ID);
        if (m >= 0.9999) {
            if (old != null) speed.removeModifier(MOVE_ID);
        } else if (old == null || Math.abs(old.getAmount() - (m - 1)) > 1e-4) {
            if (old != null) speed.removeModifier(MOVE_ID);
            speed.addTransientModifier(new AttributeModifier(MOVE_ID, "AFL lightning", m - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** After a player's damage was scaled (PlayerHealth): a hurt of wake_damage wakes the fainted (not the strike itself). */
    static void onDamage(ServerPlayer player, DamageSource source, double healthLost) {
        if (source.is(DamageTypes.LIGHTNING_BOLT) || healthLost < LightningInjuryConfig.get().wakeDamage) return;
        State s = STATES.get(player.getUUID());
        if (s == null || s.faint <= 1 || player.server.getTickCount() - s.struckAt < STRIKE_BURN_TICKS) return;
        s.faint = 1;
    }

    // ---- what they do ----

    /** The aftermath's strength now (1 full, fading to 0 over its last aftermath_fade share). */
    static double aftermath(float left) {
        double fade = LightningInjuryConfig.get().aftermathFade;
        return left <= 0 ? 0 : fade <= 0 ? 1 : Math.min(1, left / fade);
    }

    private static float aftermathLeft(Player player) {
        if (player instanceof ServerPlayer server) {
            State s = STATES.get(server.getUUID());
            return s == null ? 0 : share(s.aftermath, s.aftermathTotal);
        }
        return player.isLocalPlayer() ? ClientLightningInjury.aftermathLeft() : 0;
    }

    public static double staminaRegenMultiplier(Player player) {
        return Mth.lerp(aftermath(aftermathLeft(player)), 1, LightningInjuryConfig.get().aftermathStaminaRegen);
    }

    public static double healthRegenMultiplier(Player player) {
        return Mth.lerp(aftermath(aftermathLeft(player)), 1, LightningInjuryConfig.get().aftermathHealthRegen);
    }

    /** Steps cost × this (weak legs; with a broken leg, which PlayerStamina also applies, the heavier of the two). */
    public static double staminaCostMultiplier(Player player) {
        if (!weak(player)) return 1;
        double broken = Fractures.staminaCostMultiplier(Fractures.of(player));
        return Math.max(LightningInjuryConfig.get().weakStamina, broken) / broken;
    }

    private static boolean weak(Player player) {
        if (player instanceof ServerPlayer server) {
            State s = STATES.get(server.getUUID());
            return s != null && s.weak > 0;
        }
        return player.isLocalPlayer() && ClientLightningInjury.weakLegs();
    }

    /** Lying (fainted or paralysed): the server's record, or the own client's. */
    public static boolean down(Player player) {
        if (player instanceof ServerPlayer server) {
            State s = STATES.get(server.getUUID());
            return s != null && (s.faint > 0 || s.paralysis > 0);
        }
        return player.isLocalPlayer() && ClientLightningInjury.down();
    }

    public static boolean fainted(Player player) {
        if (player instanceof ServerPlayer server) {
            State s = STATES.get(server.getUUID());
            return s != null && s.faint > 0;
        }
        return player.isLocalPlayer() && ClientLightningInjury.fainted();
    }

    /** No jumping lying down or on weak legs. Both sides: the client moves its own player. */
    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isPassenger() || !(down(player) || weak(player))) return;
        Vec3 v = player.getDeltaMovement();
        if (v.y > 0) player.setDeltaMovement(v.x, 0, v.z);
    }

    // ---- nothing in the hands while fainted ----

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (fainted(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (fainted(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void useEntity(PlayerInteractEvent.EntityInteract event) {
        if (fainted(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (fainted(event.getEntity())) {
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (fainted(event.getEntity())) event.setCanceled(true);
    }

    // ---- life cycle ----

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(KEY)) state(player).dirty = true;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATES.remove(player.getUUID());
        if (s != null) save(player, s);
    }

    /** Death: a clean slate (the forced pose goes with the old player object); back from the End: the same states. */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATES.remove(player.getUUID());
        if (event.isWasDeath()) {
            player.getPersistentData().remove(KEY);
            sync(player, new State());
        } else {
            if (s != null) save(original, s);
            CompoundTag t = original.getPersistentData().getCompound(KEY);
            if (!t.isEmpty()) {
                player.getPersistentData().put(KEY, t.copy());
                state(player).dirty = true;
            }
        }
    }

    // ---- dev (/afl lightning) ----

    static void clear(ServerPlayer player) {
        State s = state(player);
        boolean wasDown = s.down;
        if (s.lying != LightningPackets.Lying.NONE) AflNetwork.lightningLying(player, new LightningPackets.Lying(player.getId(), LightningPackets.Lying.NONE));
        STATES.remove(player.getUUID());
        player.getPersistentData().remove(KEY);
        if (wasDown) player.setForcedPose(null);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(MOVE_ID);
        sync(player, new State());
    }

    static String describe(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null) return "no lightning injury";
        return String.format(java.util.Locale.ROOT, "fainted %.0f s, paralysed %.0f s, weak legs %.0f s, eardrum %.2f (side %d), aftermath %.2f",
                s.faint / 20.0, s.paralysis / 20.0, s.weak / 20.0, share(s.eardrum, s.eardrumTotal), s.eardrumSide, share(s.aftermath, s.aftermathTotal));
    }
}
