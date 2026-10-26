package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.weapon.WeaponClass;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Growth by use (docs/gameplay/survivor_background_skills_v1_plan.md section 7, P3): strength and fitness (body levels),
 * the three gun classes and cold / heat tolerance (growth levels) gain experience from what the survivor does. Each body
 * stat / growth item's file says how much experience a level takes and what each source gives
 * ({@link SurvivorCatalog.Training}). No daily cap (user 2026-10-18: practice is never limited).
 * <ul>
 * <li>Learning speed: growth_xp on everything, body_growth_xp on top for strength and fitness.</li>
 * <li>Cold / heat tolerance: each minute the body is being cooled (warmed) by the weather without being hypothermic
 * (heat-exhausted) yet (PlayerTemperature#exposure).</li>
 * <li>Repeats: a reload, a starter pull and getting the breath back count fully once, then 50 %, 25 % and nothing while
 * the same thing is done again within 60 s (an empty-handed farm of the same action). A gun hit counts on the first three
 * hits on one target.</li>
 * <li>Only a living survivor whose setup is done, in Survival or Adventure.</li>
 * <li>A level gained: body levels and growth items with a curve re-apply the attribute modifiers; the player gets a level-up notice (toast) and a fresh
 * sheet. Experience alone resends the sheet at most every 3 s (the growth page's bars).</li>
 * </ul>
 * The top is the item's max (body) or the cap its traits allow (guns, "gun shy"); levels never drop (V1).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class SurvivorGrowth {
    public static final String STRENGTH = "afl:strength", FITNESS = "afl:fitness";
    public static final String PISTOL = "afl:firearms_pistol", LONG = "afl:firearms_long", SHOTGUN = "afl:firearms_shotgun";
    public static final String COLD = "afl:cold_tolerance", HEAT = "afl:heat_tolerance";
    /** Ticks of cold / heat exposure per "exposed_minute". */
    static final int EXPOSURE_TICKS = 60 * 20;
    /** Repeat window and the share of each repeat inside it (then nothing). */
    static final long REPEAT_TICKS = 60 * 20;
    static final double[] REPEAT = {1, 0.5, 0.25};
    /** Hits on one target that count, and how long a target is remembered. */
    static final int HITS_PER_TARGET = 3;
    static final long TARGET_TICKS = 5 * 60 * 20;
    /** Stamina fraction below which getting the breath back (above RECOVERED) trains fitness. */
    static final double WINDED = 0.3, RECOVERED = 0.8;
    static final int SHEET_EVERY = 60;
    private static final double METRE_STEP = 10;

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        double lastX, lastY, lastZ;
        boolean hasLast, low;
        double loaded, sprint, swim;
        int cold, heat;
        final Map<String, Double> fraction = new HashMap<>();
        final Map<String, long[]> repeats = new HashMap<>();
        final Map<Integer, long[]> targets = new HashMap<>();
        boolean dirty;
        long lastSheet;
    }

    private SurvivorGrowth() {
    }

    /**
     * The gun class tiers (gun proficiency plan section 3; 10 levels since 2026-10-18, an ordinary survivor starts at 0):
     * 0-3 novice, 4-6 experienced, 7-10 practised. Each tier is one way of reloading.
     */
    public static int tier(int level) {
        return level <= 3 ? 0 : level <= 6 ? 1 : 2;
    }

    /** From this gun level a reload started while aiming keeps the aim (user 2026-10-18; was the practised tier). */
    public static final int ADS_RELOAD_LEVEL = 5;

    /** The first level of each tier above novice. */
    public static boolean tierStart(int level) {
        return level == 4 || level == 7;
    }

    /** Aiming sway at a gun level (user 2026-10-18: every level a little more control while aiming): x1.25 at 0, x1 at 5, x0.75 at 10. */
    public static float adsSway(int level) {
        return 1.25F - 0.05F * Math.max(0, Math.min(10, level));
    }

    /** How fast the aim comes back down after a shot at a gun level: x0.8 at 0, x1 at 5, x1.2 at 10. */
    public static float recoilRecovery(int level) {
        return 0.8F + 0.04F * Math.max(0, Math.min(10, level));
    }

    /**
     * The player's tier with a gun class on the server (reload choices): the survivor's level; Creative and Spectator count
     * as practised (as the client skips the level there), anyone without a set-up living survivor as novice.
     */
    public static int gunTier(ServerPlayer player, WeaponClass weaponClass) {
        return tier(gunLevel(player, weaponClass));
    }

    /** The player's level with a gun class on the server: Creative and Spectator 10, no set-up living survivor 0. */
    public static int gunLevel(ServerPlayer player, WeaponClass weaponClass) {
        if (player.isCreative() || player.isSpectator()) return 10;
        SurvivorRecord record = trainee(player);
        if (record == null) return 0;
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        SurvivorCatalog.GrowthDef def = catalog.growth().get(gunItem(weaponClass));
        return def == null ? 0 : SurvivorSetup.growthLevel(record, catalog, def);
    }

    /**
     * A growth item's level for the player on the server (the furniture work's carpentry): the survivor's level, 0 without
     * a set-up living survivor. No Creative override: the client's own reading (SurvivorSheetClient) is the same number.
     */
    public static int level(ServerPlayer player, String id) {
        SurvivorRecord record = trainee(player);
        if (record == null) return 0;
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        SurvivorCatalog.GrowthDef def = catalog.growth().get(id);
        return def == null ? 0 : SurvivorSetup.growthLevel(record, catalog, def);
    }

    /** The gun class's growth item (gun proficiency plan section 3). */
    public static String gunItem(WeaponClass weaponClass) {
        return switch (weaponClass) {
            case PISTOL -> PISTOL;
            case SHOTGUN -> SHOTGUN;
            default -> LONG;
        };
    }

    // ---------------------------------------------------------------- sources

    /** A gun hit that hurt a living target (once per target per shot); {@code killed} = it died of it. */
    public static void gunHit(ServerPlayer shooter, WeaponClass weaponClass, LivingEntity target, boolean killed) {
        if (target == shooter || target instanceof ArmorStand) return;
        Track track = track(shooter);
        long now = shooter.serverLevel().getGameTime();
        track.targets.values().removeIf(t -> now - t[0] > TARGET_TICKS);
        long[] t = track.targets.computeIfAbsent(target.getId(), k -> new long[]{now, 0});
        t[0] = now;
        String id = gunItem(weaponClass);
        if (t[1] < HITS_PER_TARGET) {
            t[1]++;
            gain(shooter, id, "hit", 1);
        }
        if (killed) gain(shooter, id, "kill", 1);
    }

    /** A reload that put rounds into the gun. */
    public static void gunReload(ServerPlayer player, WeaponClass weaponClass) {
        String id = gunItem(weaponClass);
        gain(player, id, "reload", repeat(player, id + "/reload"));
    }

    /** One swing into a tree (FellingServer). */
    public static void chop(ServerPlayer player) {
        gain(player, STRENGTH, "chop", 1);
    }

    /** One pull of a recoil starter (the portable generator). */
    public static void recoilPull(ServerPlayer player) {
        gain(player, STRENGTH, "recoil_pull", repeat(player, "recoil_pull"));
    }

    /** Breaking a hard block (hardness 1 and up: stone, logs, ore; not dirt, sand or plants). */
    @SubscribeEvent
    public static void blockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        float hardness = event.getState().getDestroySpeed(event.getLevel(), event.getPos());
        if (hardness >= 1) gain(player, STRENGTH, "dig", 1);
    }

    /** Distances (sprinting, swimming, walking over the comfortable load) and getting the breath back. */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        Track track = track(player);
        double dx = track.hasLast ? player.getX() - track.lastX : 0, dz = track.hasLast ? player.getZ() - track.lastZ : 0;
        track.lastX = player.getX();
        track.lastY = player.getY();
        track.lastZ = player.getZ();
        track.hasLast = true;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h > 4 || player.isPassenger() || !trains(player)) h = 0;   // a teleport or a ride, not the legs
        if (h > 0.01) {
            if (player.isSwimming()) track.swim += h;
            else if (player.isSprinting()) track.sprint += h;
            var weight = PlayerWeightRuntime.state(player);
            if (!player.isSwimming() && player.onGround() && weight != null && weight.loadGrams() > weight.comfortCapacityGrams()) {
                track.loaded += h;
            }
            track.swim = step(player, track.swim, FITNESS, "swim_10m");
            track.sprint = step(player, track.sprint, FITNESS, "sprint_10m");
            track.loaded = step(player, track.loaded, STRENGTH, "loaded_walk_10m");
        }
        if (trains(player)) {
            int exposure = com.antaurora.apofirstlight.temperature.PlayerTemperature.exposure(player);
            if (exposure < 0 && ++track.cold >= EXPOSURE_TICKS) {
                track.cold = 0;
                gain(player, COLD, "exposed_minute", 1);
            } else if (exposure > 0 && ++track.heat >= EXPOSURE_TICKS) {
                track.heat = 0;
                gain(player, HEAT, "exposed_minute", 1);
            }
        }
        double max = PlayerStamina.max(player), stamina = max <= 0 ? 1 : PlayerStamina.value(player) / max;
        if (stamina < WINDED) track.low = true;
        else if (track.low && stamina >= RECOVERED) {
            track.low = false;
            gain(player, FITNESS, "recover", repeat(player, "recover"));
        }
        if (track.dirty && player.server.getTickCount() - track.lastSheet >= SHEET_EVERY) {
            track.dirty = false;
            track.lastSheet = player.server.getTickCount();
            SurvivorSetupFlow.sendSheet(player);
        }
    }

    private static double step(ServerPlayer player, double metres, String id, String source) {
        if (metres < METRE_STEP) return metres;
        int steps = (int) (metres / METRE_STEP);
        gain(player, id, source, steps);
        return metres - steps * METRE_STEP;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKS.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- the rules

    private static Track track(ServerPlayer player) {
        return TRACKS.computeIfAbsent(player.getUUID(), k -> new Track());
    }

    /** Survival or Adventure, a living survivor whose setup is done. */
    private static boolean trains(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && trainee(player) != null;
    }

    @Nullable
    private static SurvivorRecord trainee(ServerPlayer player) {
        SurvivorRecord record = SurvivorSetupFlow.living(player);
        return record != null && SurvivorSetup.DONE.equals(record.setup) ? record : null;
    }

    /** This repeat's share (1, 0.5, 0.25, then 0 inside the window), counting it. */
    static double repeat(ServerPlayer player, String key) {
        long now = player.serverLevel().getGameTime();
        long[] r = track(player).repeats.get(key);
        int count = r == null || now - r[0] > REPEAT_TICKS ? 0 : (int) r[1];
        track(player).repeats.put(key, new long[]{now, count + 1});
        return count < REPEAT.length ? REPEAT[count] : 0;
    }

    /**
     * Adds {@code units} of a source's experience to a body stat or growth item: learning speed, level-ups.
     * Returns the experience actually added (0 when nothing counted).
     */
    public static double gain(ServerPlayer player, String id, String source, double units) {
        if (units <= 0 || player.isCreative() || player.isSpectator()) return 0;
        SurvivorRecord record = trainee(player);
        if (record == null) return 0;
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        Result result = add(record, catalog, id, source, units, multiplier(player, catalog.body().containsKey(id)), track(player).fraction);
        if (result.added() <= 0) return 0;
        SurvivorWorldData.get(player.server).setDirty();
        Track track = track(player);
        track.dirty = true;
        if (result.levelled()) {
            if (result.body() || !catalog.growth().get(id).curves().isEmpty()) SurvivorModifiers.apply(player);
            AflNetwork.survivor(player, new SurvivorPackets.LevelUp(id, result.level(), result.body()));
            track.dirty = false;
            track.lastSheet = player.server.getTickCount();
            SurvivorSetupFlow.sendSheet(player);
            ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} {} reached level {}", player.getGameProfile().getName(), id, result.level());
        }
        return result.added();
    }

    /** growth_xp, and body_growth_xp on top for a body stat. */
    static double multiplier(ServerPlayer player, boolean body) {
        double m = SurvivorStats.multiplier(player, AflAttributes.GROWTH_XP, 0);
        if (body) m *= SurvivorStats.multiplier(player, AflAttributes.BODY_GROWTH_XP, 0);
        return m;
    }

    record Result(double added, boolean levelled, int level, boolean body) {
        static final Result NONE = new Result(0, false, 0, false);
    }

    /**
     * The rule itself, on a record (GameTest): {@code units} × the source's experience × {@code multiplier}; whole points go
     * into the level's experience, the rest waits in {@code fraction}. Levels up while the experience covers the next level
     * and the top allows.
     */
    static Result add(SurvivorRecord record, SurvivorCatalog.Snapshot catalog, String id, String source, double units,
                      double multiplier, Map<String, Double> fraction) {
        SurvivorCatalog.BodyDef bodyDef = catalog.body().get(id);
        SurvivorCatalog.GrowthDef growthDef = catalog.growth().get(id);
        if (bodyDef == null && growthDef == null) return Result.NONE;
        boolean body = bodyDef != null;
        SurvivorCatalog.Training training = body ? bodyDef.training() : growthDef.training();
        int top = body ? bodyDef.max() : SurvivorSetup.growthCap(record, catalog, growthDef);
        SurvivorRecord.Progress p = body
                ? record.body.computeIfAbsent(id, k -> new SurvivorRecord.Progress(bodyDef.defaultLevel(), 0))
                : record.growth.computeIfAbsent(id, k -> new SurvivorRecord.Progress(growthDef.defaultLevel(), 0));
        if (p.level >= top || training.toNext(p.level) <= 0) return Result.NONE;
        double amount = training.xp(source) * units * multiplier;
        if (amount <= 0) return Result.NONE;
        double total = fraction.getOrDefault(id, 0.0) + amount;
        int whole = (int) Math.floor(total + 1e-9);
        fraction.put(id, total - whole);
        p.xp += whole;
        int before = p.level;
        while (p.level < top && training.toNext(p.level) > 0 && p.xp >= training.toNext(p.level)) {
            p.xp -= training.toNext(p.level);
            p.level++;
        }
        if (p.level >= top || training.toNext(p.level) <= 0) p.xp = 0;
        return new Result(amount, p.level > before, p.level, body);
    }

    /**
     * Dev command (/afl survivor growth): sets a body stat or growth item of the player's living survivor to a level (kept
     * inside 1..max for a body stat, 0..the survivor's cap for a growth item) and experience (inside the level's need).
     * Returns the level set, or -1 when there is no such item or no set-up survivor.
     */
    public static int set(ServerPlayer player, String id, int level, int xp) {
        SurvivorRecord record = trainee(player);
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        SurvivorCatalog.BodyDef bodyDef = catalog.body().get(id);
        SurvivorCatalog.GrowthDef growthDef = catalog.growth().get(id);
        if (record == null || bodyDef == null && growthDef == null) return -1;
        boolean body = bodyDef != null;
        int top = body ? bodyDef.max() : SurvivorSetup.growthCap(record, catalog, growthDef);
        int lv = Math.max(body ? 1 : 0, Math.min(top, level));
        int need = (body ? bodyDef.training() : growthDef.training()).toNext(lv);
        (body ? record.body : record.growth).put(id, new SurvivorRecord.Progress(lv, lv >= top || need <= 0 ? 0 : Math.max(0, Math.min(need - 1, xp))));
        track(player).fraction.remove(id);
        SurvivorWorldData.get(player.server).setDirty();
        SurvivorModifiers.apply(player);
        SurvivorSetupFlow.sendSheet(player);
        return lv;
    }
}
