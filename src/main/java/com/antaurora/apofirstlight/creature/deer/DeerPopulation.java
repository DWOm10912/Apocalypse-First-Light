package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflEntities;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalStateId;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Where the deer live and how many (docs/gameplay/afl_whitetail_deer_d0_design.md 12, D2a). The overworld is cut into
 * 512 m cells; each keeps a count in this level's saved data:
 * <ul>
 *   <li><b>target</b>: 45 deer a km² ({@link #DENSITY}) times the cell's area (0.262 km²) times its habitat (0..1.2: {@link
 *   #habitat}, the mean of 64 points 64 m apart): the state (S1 and S4 full, S5 0.8, S2 0.7, S3 and S6 none: mule deer
 *   country), the surface biome (woods and their edges best, open grass less, marsh little, mountains and water none) and
 *   the towns (none within 0.6 of a settlement's radius, back to full at its edge). Rounded up or down by the cell's own
 *   hash, so the totals come out right;</li>
 *   <li><b>alive</b>: deer placed for the cell and not yet dead (a deer keeps its home cell wherever it wanders;
 *   {@link #died});</li>
 *   <li><b>owed</b>: deer still to be placed: the whole target when a player first comes within 256 m of the cell, and one
 *   more for every day of game time (72 000 ticks) no player came that near, up to the target (they come back slowly; no
 *   breeding yet).</li>
 * </ul>
 * Every 5 s the cells round each player are looked at, and a cell that owes deer places one group there: a doe group of
 * 2–5 behind its lead doe, or a buck alone or two together (one group in three is bucks: autumn), on natural ground in
 * habitat, in a loaded chunk 64 blocks or more from every player, so none appears in view. A cell with nobody near is
 * never touched: its deer are saved with their chunks like any entity and never despawn. Vanilla's doMobSpawning rule
 * turns it off.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeerPopulation extends SavedData {
    private static final String NAME = "afl_deer_population";
    public static final int CELL = 512;
    /** Deer a km² at habitat 1. Decision 3 took the real 15; in game that is one deer in sight now and then (user 2026-10-28:
     *  "没有鹿？"), so three times that, about 12 to a good cell (user: "鹿 45 头/km²"). */
    public static final double DENSITY = 45.0;
    /** A player this near a cell's edge counts as there (first fill, no regrowth meanwhile). */
    static final int NEAR = 256;
    /** No deer appears nearer any player than this. */
    static final int MIN_PLAYER = 64;
    /** One deer back per cell per AFL day (60 min) nobody came near. */
    static final long REGROW_TICKS = 72_000;
    static final int EVERY = 100;
    public static final long NO_CELL = Long.MIN_VALUE;

    /** One cell's books. target -1: not worked out yet. */
    static final class Cell {
        int target = -1, alive, owed;
        long lastNear = -1;
        float habitat;
    }

    private final Map<Long, Cell> cells = new HashMap<>();

    public static DeerPopulation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DeerPopulation::load, DeerPopulation::new, NAME);
    }

    public static long cell(double x, double z) {
        return pack(Mth.floor(x / CELL), Mth.floor(z / CELL));
    }

    static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    static int cx(long key) {
        return (int) (key >> 32);
    }

    static int cz(long key) {
        return (int) key;
    }

    /** A deer of this cell died or was removed. */
    public void died(long key) {
        Cell c = cells.get(key);
        if (c == null || c.alive <= 0) return;
        c.alive--;
        setDirty();
    }

    // ---------------------------------------------------------------- the tick

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % EVERY != 0 || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        if (level.players().isEmpty()) return;
        get(level).step(level);
    }

    private void step(ServerLevel level) {
        long now = level.getGameTime();
        Habitat h = Habitat.of(level);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int pcx = Mth.floor(p.getX() / CELL), pcz = Mth.floor(p.getZ() / CELL);
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    double ex = Math.max(0, Math.max(cx * CELL - p.getX(), p.getX() - (cx + 1) * CELL));
                    double ez = Math.max(0, Math.max(cz * CELL - p.getZ(), p.getZ() - (cz + 1) * CELL));
                    if (ex * ex + ez * ez > (double) NEAR * NEAR) continue;
                    long key = pack(cx, cz);
                    if (seen.add(key)) visit(level, h, key, now);
                }
        }
    }

    /** A player is near the cell: fill it the first time, add back what grew while nobody came, place one owed group. */
    private void visit(ServerLevel level, Habitat h, long key, long now) {
        Cell c = cells.computeIfAbsent(key, k -> new Cell());
        if (c.target < 0) {
            c.habitat = (float) h.cellMean(cx(key), cz(key));
            c.target = target(level.getSeed(), key, c.habitat);
            c.owed = c.target;
            c.lastNear = now;
            setDirty();
        } else {
            // the density changed since the cell was worked out (15 -> 45 a km², 2026-10-28): a bigger target is owed at once
            int t = target(level.getSeed(), key, c.habitat);
            if (t != c.target) {
                if (t > c.target) c.owed += t - c.target;
                c.owed = Math.max(0, Math.min(c.owed, t - c.alive));
                c.target = t;
                setDirty();
            }
            if (c.lastNear >= 0 && now - c.lastNear > REGROW_TICKS) {
                int grown = (int) Math.min(c.target, (now - c.lastNear) / REGROW_TICKS);
                int room = c.target - c.alive - c.owed;
                if (grown > 0 && room > 0) c.owed += Math.min(grown, room);
            }
        }
        if (c.lastNear != now) {
            c.lastNear = now;
            setDirty();
        }
        if (c.owed > 0) placeGroup(level, h, key, c);
    }

    /** The cell's whole number of deer: the expected count rounded down or up by the cell's own hash. */
    static int target(long seed, long key, double habitat) {
        double area = (double) CELL * CELL / 1_000_000.0;
        double expected = DENSITY * area * habitat;
        return (int) Math.floor(expected + unit(seed, key, 1));
    }

    private static double unit(long seed, long key, int salt) {
        long h = seed ^ key * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    // ---------------------------------------------------------------- placing

    private void placeGroup(ServerLevel level, Habitat h, long key, Cell c) {
        Random r = new Random(level.getRandom().nextLong());
        boolean bucks = r.nextInt(3) == 0;
        int size = bucks ? (r.nextInt(4) == 0 ? 2 : 1) : 2 + r.nextInt(4);
        size = Math.min(size, c.owed);
        BlockPos at = null;
        for (int tries = 0; tries < 12 && at == null; tries++) {
            int x = cx(key) * CELL + r.nextInt(CELL), z = cz(key) * CELL + r.nextInt(CELL);
            at = spot(level, h, x, z);
        }
        if (at == null) return;
        DeerEntity lead = null;
        int placed = 0;
        for (int i = 0; i < size; i++) {
            BlockPos p = i == 0 ? at : spot(level, h, at.getX() + (int) Math.round(r.nextGaussian() * 3), at.getZ() + (int) Math.round(r.nextGaussian() * 3));
            if (p == null) continue;
            DeerEntity deer = AflEntities.WHITETAIL_DEER.get().create(level);
            if (deer == null) break;
            deer.setDoe(!bucks);
            float yaw = r.nextFloat() * 360F;
            deer.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0F);
            deer.setYHeadRot(yaw);
            deer.yBodyRot = yaw;
            deer.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.NATURAL, null, null);
            deer.setHomeCell(key);
            if (lead != null) deer.setLeader(lead);
            if (level.addFreshEntity(deer)) {
                placed++;
                if (lead == null) lead = deer;
            }
        }
        if (placed == 0) return;
        c.owed -= placed;
        c.alive += placed;
        setDirty();
    }

    /** Feet position for a deer at column (x, z): loaded, far from players, natural ground in habitat, open above. */
    @Nullable
    private static BlockPos spot(ServerLevel level, Habitat h, int x, int z) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz < (double) MIN_PLAYER * MIN_PLAYER) return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 3) return null;
        BlockPos feet = new BlockPos(x, y, z), below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!TerrainSmooth.ground(ground.getBlock()) || !ground.getFluidState().isEmpty()) return null;
        for (int up = 0; up < 2; up++) {
            BlockPos q = feet.above(up);
            BlockState s = level.getBlockState(q);
            if (!s.getFluidState().isEmpty() || !s.getCollisionShape(level, q).isEmpty()) return null;
        }
        return h.at(level.getBiome(feet), x, z) >= 0.3 ? feet : null;
    }

    // ---------------------------------------------------------------- habitat

    /** How good a place is for white-tailed deer, 0..1.2 (1: the 15 a km² of decision 3). */
    static final class Habitat {
        @Nullable final TerrainPlanSurface plan;
        @Nullable final FederalMap map;
        final ServerLevel level;

        private Habitat(ServerLevel level, @Nullable TerrainPlanSurface plan) {
            this.level = level;
            this.plan = plan;
            this.map = plan != null && plan.federal() ? FederalMap.forSeed(plan.seed) : null;
        }

        static Habitat of(ServerLevel level) {
            Object state = level.getChunkSource().randomState();
            TerrainPlanSurface plan = state instanceof RandomStateSeedAccess a && a.apocalypse$hasTerrainPlan() ? a.apocalypse$terrainPlanSurface() : null;
            return new Habitat(level, plan);
        }

        /** The mean over 8 x 8 points 64 m apart (no chunk loaded: the planned surface and the biome source). */
        double cellMean(int cx, int cz) {
            double sum = 0;
            for (int j = 0; j < 8; j++)
                for (int i = 0; i < 8; i++) {
                    int x = cx * CELL + 32 + i * 64, z = cz * CELL + 32 + j * 64;
                    int y = plan != null ? (int) Math.round(plan.heightAt(x, z)) : level.getSeaLevel() + 4;
                    sum += at(level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2), x, z);
                }
            return sum / 64.0;
        }

        double at(Holder<Biome> biome, int x, int z) {
            double w = biome(biome);
            if (w <= 0 || map == null) return w;
            FederalStateId s = map.stateAt(x, z);
            if (s == null) return 0;
            w *= switch (s) {
                case S1, S4 -> 1.0;
                case S5 -> 0.8;
                case S2 -> 0.7;
                case S3, S6 -> 0.0;
            };
            if (w <= 0) return 0;
            for (FederalMap.City c : map.cities()) {
                double r = radius(c.role()), d = Math.hypot(c.x() - x, c.z() - z);
                if (d < r) w *= Mth.clamp((d / r - 0.6) / 0.4, 0, 1);
                if (w <= 0) return 0;
            }
            return w;
        }

        private static double radius(FederalTemplate.CityRole role) {
            return switch (role) {
                case CITY, INDUSTRIAL -> 900;
                case TOWN, PORT -> 550;
                case MINING, VILLAGE -> 350;
                case OUTPOST -> 200;
            };
        }

        /** The surface biome's worth (AFL's own; a vanilla world's by its tags). */
        static double biome(Holder<Biome> b) {
            ResourceKey<Biome> key = b.unwrapKey().orElse(null);
            if (key == null) return 0;
            if (ApocalypseFirstLight.MOD_ID.equals(key.location().getNamespace())) {
                return switch (key.location().getPath()) {
                    case "riparian_forest" -> 1.2;
                    case "oak_hickory_woodland", "mixed_mesophytic_forest", "lakeshore_mixed_forest" -> 1.0;
                    case "ridge_oak_forest", "coastal_pine_oak_forest" -> 0.8;
                    case "hemlock_hollow", "coastal_swamp_forest", "temperate_grassland" -> 0.6;
                    case "coastal_sandy_clearing", "wet_prairie" -> 0.5;
                    case "lake_fen" -> 0.4;
                    case "tidal_marsh" -> 0.2;
                    default -> 0.0;   // montane conifer, alpine, steppe, badlands, barrens, lakes, sea, beach
                };
            }
            if (b.is(BiomeTags.IS_FOREST)) return 1.0;
            if (b.is(BiomeTags.IS_TAIGA)) return 0.4;
            if (key == Biomes.PLAINS || key == Biomes.MEADOW) return 0.6;
            if (key == Biomes.SWAMP || key == Biomes.RIVER) return 0.4;
            return 0.0;
        }
    }

    // ---------------------------------------------------------------- the dev census

    /** /afl dev deer census: the cells round a point, each "cx,cz: target, alive, owed, habitat", and the five nearest loaded deer (direction, distance). */
    String census(ServerLevel level, double x, double z) {
        Habitat h = Habitat.of(level);
        StringBuilder out = new StringBuilder();
        int pcx = Mth.floor(x / CELL), pcz = Mth.floor(z / CELL), targets = 0, alive = 0, owed = 0;
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                long key = pack(pcx + dx, pcz + dz);
                Cell c = cells.get(key);
                double hab = c != null && c.target >= 0 ? c.habitat : h.cellMean(pcx + dx, pcz + dz);
                int t = c != null && c.target >= 0 ? c.target : target(level.getSeed(), key, hab);
                targets += t;
                if (c != null) {
                    alive += c.alive;
                    owed += c.owed;
                }
                out.append(String.format("\n  [%d, %d]%s 目标 %d，活着 %d，待放 %d，栖息地 %.2f", pcx + dx, pcz + dz, dx == 0 && dz == 0 ? "（这格）" : "",
                        t, c == null ? 0 : c.alive, c == null ? 0 : c.owed, hab));
            }
        // the nearest loaded deer, to go and find them
        java.util.List<DeerEntity> near = level.getEntitiesOfClass(DeerEntity.class, new net.minecraft.world.phys.AABB(x - 400, -2048, z - 400, x + 400, 2048, z + 400));
        near.sort(java.util.Comparator.comparingDouble(d -> d.distanceToSqr(x, d.getY(), z)));
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < Math.min(5, near.size()); i++) {
            DeerEntity d = near.get(i);
            double dx = d.getX() - x, dz = d.getZ() - z;
            String[] dirs = {"南", "西南", "西", "西北", "北", "东北", "东", "东南"};
            int dir = Math.floorMod(Math.round((float) (Math.atan2(-dx, dz) * 4 / Math.PI)), 8);
            found.append(String.format("%s%s %d m（%s）", i == 0 ? "" : "，", dirs[dir], Math.round(Math.hypot(dx, dz)), d.doe() ? "雌" : "雄"));
        }
        return String.format("周围 9 格（每格 512 m）：目标 %d，活着 %d，待放 %d", targets, alive, owed) + out
                + (near.isEmpty() ? "\n  400 m 内已加载的鹿：没有" : "\n  最近的鹿：" + found);
    }

    // ---------------------------------------------------------------- save

    public static DeerPopulation load(CompoundTag tag) {
        DeerPopulation d = new DeerPopulation();
        ListTag list = tag.getList("Cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Cell c = new Cell();
            c.target = t.getInt("T");
            c.alive = t.getInt("A");
            c.owed = t.getInt("O");
            c.lastNear = t.getLong("N");
            c.habitat = t.getFloat("H");
            d.cells.put(t.getLong("K"), c);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            CompoundTag t = new CompoundTag();
            Cell c = e.getValue();
            t.putLong("K", e.getKey());
            t.putInt("T", c.target);
            t.putInt("A", c.alive);
            t.putInt("O", c.owed);
            t.putLong("N", c.lastNear);
            t.putFloat("H", c.habitat);
            list.add(t);
        }
        tag.put("Cells", list);
        return tag;
    }
}
