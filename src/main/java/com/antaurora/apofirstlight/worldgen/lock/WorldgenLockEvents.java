package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * World generation version lock (docs/worldgen/worldgen_version_lock_v1.md). Runs when the overworld loads
 * (LevelEvent.Load, fired in MinecraftServer#createLevels BEFORE a new world's spawn is set and before any chunk of
 * the world is generated or loaded) and applies {@link WorldgenLockPolicy}:
 * <ul>
 *   <li>new world: writes the record at once (data/apocalypse_firstlight_worldgen_lock.dat);</li>
 *   <li>world from before the lock: adopts the running rules as its baseline (origin adopted_legacy), logs a warning;</li>
 *   <li>same output: nothing is written;</li>
 *   <li>SOFT change: logged, recorded in the history, baseline moved;</li>
 *   <li>HARD change: the world is NOT opened (WorldgenLockException ends the load; the crash report and the log carry
 *   the reason and the token); nothing is written, no chunk is generated, the save is not migrated or deleted (C2:
 *   the stop that follows saves nothing either, WorldgenLockSaveMixin; before it, the crash path rewrote level.dat
 *   and rotated level.dat_old. Not preventable here: the singleplayer client rewrites level.dat (LastPlayed only) when
 *   it opens any world, before the server starts; the original stays as level.dat_old). The token in the
 *   world's serverconfig/apocalypse_firstlight-worldgen.toml accepts exactly that change.</li>
 * </ul>
 * Only AFL Terrain V2 overworlds are locked; another generator (flat, a data-pack world) is left alone.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldgenLockEvents {
    private WorldgenLockEvents() {}

    /** The last load's outcome, for /afl worldgen lock. */
    static volatile Status last;

    record Status(MinecraftServer server, WorldgenLockRecord record, WorldgenFingerprint current, WorldgenLockPolicy.Verdict verdict) {}

    /** The server whose world was refused (weak: the dead server is not kept). */
    private static volatile java.lang.ref.WeakReference<MinecraftServer> blocked = new java.lang.ref.WeakReference<>(null);

    /** True for a server whose world the lock refused: it must not save anything while it stops (WorldgenLockSaveMixin). */
    public static boolean blocked(MinecraftServer server) {
        return server != null && blocked.get() == server;
    }

    private static void refuse(MinecraftServer server) {
        blocked = new java.lang.ref.WeakReference<>(server);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) return;
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        if (!access.apocalypse$hasTerrainPlan()) {
            ApocalypseFirstLight.LOGGER.info("[AFL worldgen lock] overworld has no AFL Terrain V2 plan; not locked");
            last = null;
            return;
        }
        long seed = access.apocalypse$getSeed();
        // the plan the router bound a moment ago, and its profile (C2: r1, or the federal experimental preset)
        TerrainPlanSurface plan = access.apocalypse$terrainPlanSurface();
        var profile = access.apocalypse$terrainProfile();
        long t0 = System.nanoTime();
        WorldgenFingerprint current = WorldgenProbe.current(level, seed, plan, profile);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        boolean initialized = ((ServerLevelData) level.getLevelData()).isInitialized();
        MinecraftServer server = level.getServer();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(WorldgenLockData.NAME + ".dat");
        WorldgenLockData data = level.getDataStorage().get(WorldgenLockData::load, WorldgenLockData.NAME);
        if (data == null && Files.exists(file)) {
            refuse(level.getServer());
            throw block("the world's lock record " + file + " exists but cannot be read; it was left untouched");
        }
        WorldgenLockRecord stored = data == null ? null : data.record();
        List<WorldgenSystem> rules = WorldgenRules.current(profile, forestEnabled());
        WorldgenLockPolicy.Verdict v = WorldgenLockPolicy.evaluate(stored, current, rules, initialized, WorldgenLockConfig.acceptToken());
        long now = System.currentTimeMillis();
        String report = WorldgenLockPolicy.describe(v);
        WorldgenLockRecord next = switch (v.action()) {
            case CREATE -> new WorldgenLockRecord(WorldgenLockRecord.SCHEMA, WorldgenLockRecord.CREATED, now, builtBy(), current,
                    List.of(new WorldgenLockRecord.HistoryEntry(now, "created", "profile " + current.profile() + ", fingerprint " + current.id())));
            case ADOPT_LEGACY -> new WorldgenLockRecord(WorldgenLockRecord.SCHEMA, WorldgenLockRecord.ADOPTED, now, builtBy(), current,
                    List.of(new WorldgenLockRecord.HistoryEntry(now, "adopted", "world from before the lock; " + legacyEvidence(file.getParent())
                            + "; baseline fingerprint " + current.id())));
            case UPDATE_SOFT -> stored.withBaseline(current, new WorldgenLockRecord.HistoryEntry(now, "soft_change",
                    stored.fingerprint().id() + " -> " + current.id() + "\n" + report));
            case ACCEPT_HARD -> stored.withBaseline(current, new WorldgenLockRecord.HistoryEntry(now, "accepted_change",
                    "token " + v.token() + ": " + stored.fingerprint().id() + " -> " + current.id() + " by " + builtBy() + "\n" + report));
            case MATCH, BLOCK -> stored;
        };
        switch (v.action()) {
            case CREATE -> ApocalypseFirstLight.LOGGER.info("[AFL worldgen lock] new world: recorded profile {} fingerprint {} ({} ms)",
                    current.profile(), current.id(), ms);
            case ADOPT_LEGACY -> ApocalypseFirstLight.LOGGER.warn("[AFL worldgen lock] this world was created before the version lock: "
                    + "its existing chunks have unknown generation rules ({}). From now on it is locked to fingerprint {}; "
                    + "chunks generated earlier with other rules may not fit new ones (that was already so).", legacyEvidence(file.getParent()), current.id());
            case MATCH -> ApocalypseFirstLight.LOGGER.info("[AFL worldgen lock] world matches its recorded rules (fingerprint {}, {} ms){}",
                    current.id(), ms, v.notes().isEmpty() ? "" : "\n" + report);
            case UPDATE_SOFT -> ApocalypseFirstLight.LOGGER.warn("[AFL worldgen lock] decoration rules changed since this world's last load; "
                    + "new chunks differ in these only (recorded in the world):\n{}", report);
            case ACCEPT_HARD -> ApocalypseFirstLight.LOGGER.warn("[AFL worldgen lock] terrain rules changed and the change was ACCEPTED by token {}"
                    + " (serverconfig). New chunks will be generated with the new rules and may not fit the old ones; recorded in the world:\n{}",
                    v.token(), report);
            case BLOCK -> {
                last = new Status(server, stored, current, v);
                refuse(server);
                throw block(blockMessage(stored, current, v, server));
            }
        }
        last = new Status(server, next, current, v);
        if (next != stored) write(level, file, next);
    }

    private static boolean forestEnabled() { return com.antaurora.apofirstlight.tree.ForestGate.enabled(); }

    /** Writes the record now (not at the next autosave), so a crash right after creation cannot lose the baseline. */
    private static void write(ServerLevel level, Path file, WorldgenLockRecord record) {
        WorldgenLockData data = new WorldgenLockData(record);
        level.getDataStorage().set(WorldgenLockData.NAME, data);
        try {
            Files.createDirectories(file.getParent());
            data.setDirty();
            data.save(file.toFile());
        } catch (Exception e) {
            // the record stays dirty in the data storage and is written with the next save
            data.setDirty();
            ApocalypseFirstLight.LOGGER.warn("[AFL worldgen lock] could not write {} now ({}); it is written with the next save", file, e.toString());
        }
    }

    private static WorldgenLockException block(String message) {
        ApocalypseFirstLight.LOGGER.error("[AFL worldgen lock] WORLD NOT OPENED\n{}", message);
        return new WorldgenLockException(message);
    }

    static String blockMessage(WorldgenLockRecord stored, WorldgenFingerprint current, WorldgenLockPolicy.Verdict v, MinecraftServer server) {
        Path config = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("apocalypse_firstlight-worldgen.toml");
        return "AFL world generation lock: this world was generated with other terrain rules than this build of Apocalypse: First Light.\n"
                + "Opening it would generate new chunks that do not fit the old ones (cliffs, cut rivers, biome seams). "
                + "The world was not opened and nothing was written to it.\n"
                + "Recorded: profile " + stored.fingerprint().profile() + ", fingerprint " + stored.fingerprint().id() + " ("
                + stored.origin() + " by " + stored.createdBy() + ", " + Instant.ofEpochMilli(stored.createdMillis()) + ")\n"
                + "This build: fingerprint " + current.id() + "\n"
                + "Changes:\n" + WorldgenLockPolicy.describe(v)
                + "To keep the world as it is, open it with the AFL build it was made with.\n"
                + "To open it anyway, back it up first, then set  acceptWorldgenChange = \"" + v.token() + "\"  in\n  " + config + "\n"
                + "and open it again. Only this exact change is accepted, and it is recorded in the world.";
    }

    static String builtBy() {
        String mod = ModList.get().getModContainerById(ApocalypseFirstLight.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        return "AFL " + mod + " / Minecraft " + SharedConstants.getCurrentVersion().getName();
    }

    /** What an unlocked world's data folder says about its age (never decisive, only for the record and the log). */
    static String legacyEvidence(Path dataDir) {
        StringBuilder sb = new StringBuilder();
        if (Files.exists(dataDir.resolve("apocalypse_firstlight_bunker.dat"))) sb.append("bunker data: predates the Terrain V2 retirement of 2026-10-10");
        if (Files.exists(dataDir.resolve("afl_mesh_forest.dat"))) sb.append(sb.length() > 0 ? "; " : "").append("mesh forest record present");
        return sb.length() == 0 ? "no older AFL markers" : sb.toString();
    }
}
