package com.antaurora.apofirstlight.weather;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.LightningPackets;
import com.antaurora.apofirstlight.mixin.ServerLevelLightningInvoker;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.world.LightningStrikes;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Regional weather V1 in the game (docs/gameplay/regional_weather_v1_plan.md 5, 10): the overworld's weather is
 * {@link RegionalWeather}'s, on the day time (sleeping moves it on). The server builds it from the world seed and the
 * federal map's climate (any other world: one mild climate) and sends the seed, the climate grid and what was set by hand
 * to every player (WeatherPackets.Sync); the client runs the same model.
 * <ul>
 * <li>vanilla's own weather (one switch for the whole world) stays off: mixin ServerLevelWeatherMixin;</li>
 * <li>{@code Level#isRainingAt} asks the place's weather (mixin LevelWeatherMixin), on both sides: getting wet, fire put
 * out (mixin FireBlockRainMixin), mobs, the T15;</li>
 * <li>the client sets its rain and thunder level to the camera's place each tick (client/weather/ClientWeather): the sky,
 * rain V1, the shader pack's rain, the T15's glass and sounds follow it;</li>
 * <li>thunderstorms strike (here): real bolts where the world is loaded (lightning V1, the injury, the tallest-first
 * target), farther only seen and heard (LightningPackets.FarStrike).</li>
 * </ul>
 * Lookups by place go through a cache of 32 m tiles, sampled afresh every second.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class AflWeather {
    /** Indices into a tile's values. */
    public static final int RAIN = 0, THUNDER = 1, AIR = 2, WIND_X = 3, WIND_Z = 4, CLOUD = 5, GUST = 6;
    private static final int TILE = 32;
    /** Lightning strikes this far inside a storm's radius. */
    private static final double STRIKE_REACH = 0.8;

    /** One side's weather: the model and its tiles. */
    static final class Field {
        final RegionalWeather model;
        final Level level;
        final TerrainPlanSurface plan;
        private final Long2ObjectOpenHashMap<float[]> tiles = new Long2ObjectOpenHashMap<>();
        private final RegionalWeather.Sample sample = new RegionalWeather.Sample();
        private long stamp = Long.MIN_VALUE;

        Field(RegionalWeather model, Level level, TerrainPlanSurface plan) {
            this.model = model;
            this.level = level;
            this.plan = plan;
        }

        synchronized float[] at(double x, double z, long t) {
            long s = Math.floorDiv(t, 20);
            if (s != stamp) {
                tiles.clear();
                stamp = s;
            }
            int tx = Math.floorDiv((int) Math.floor(x), TILE), tz = Math.floorDiv((int) Math.floor(z), TILE);
            long key = (long) tx << 32 ^ tz & 0xFFFFFFFFL;
            float[] v = tiles.get(key);
            if (v == null) {
                model.sample((tx + 0.5) * TILE, (tz + 0.5) * TILE, t, sample);
                v = new float[]{(float) sample.rain, (float) sample.thunder, (float) sample.temperature, (float) sample.windX,
                        (float) sample.windZ, (float) sample.cloud, (float) sample.gust};
                tiles.put(key, v);
            }
            return v;
        }
    }

    private static volatile Field server, client;

    private AflWeather() {
    }

    // ------------------------------------------------------------------ asking

    /** The weather model of this level (the overworld), or null (vanilla's weather goes on there). */
    public static RegionalWeather model(Level level) {
        Field f = field(level);
        return f == null ? null : f.model;
    }

    public static boolean active(Level level) {
        return field(level) != null;
    }

    /** This tile's values (see the indices) now, or null where the level has no AFL weather. */
    public static float[] at(Level level, double x, double z) {
        Field f = field(level);
        return f == null ? null : f.at(x, z, level.getDayTime());
    }

    /** It rains here (the weather alone: the sky, the roof and the biome are isRainingAt's). */
    public static boolean wet(Level level, double x, double z) {
        float[] v = at(level, x, z);
        return v != null && v[RAIN] >= RegionalWeather.WET;
    }

    /** Vanilla's rain level (0..1) here: the place's rain, or vanilla's one for the world. */
    public static float rainLevel(Level level, double x, double z) {
        float[] v = at(level, x, z);
        return v == null ? level.getRainLevel(1) : level(v[RAIN]);
    }

    /** Vanilla's thunder level (0..1) here. */
    public static float thunderLevel(Level level, double x, double z) {
        float[] v = at(level, x, z);
        return v == null ? level.getThunderLevel(1) : v[THUNDER];
    }

    /** How much of the sky the clouds cover here (0..1); vanilla's rain level without AFL weather. */
    public static float cloud(Level level, double x, double z) {
        float[] v = at(level, x, z);
        return v == null ? level.getRainLevel(1) : v[CLOUD];
    }

    /** The weather's change to the air (°C: the regime, a storm's cold outflow); 0 without AFL weather. */
    public static double airChange(Level level, double x, double z) {
        float[] v = at(level, x, z);
        return v == null ? 0 : v[AIR];
    }

    /**
     * Rain (mm/h) as vanilla's rain level: drizzle (0.3 mm/h) at 0.2, where vanilla starts to call it rain, a downpour
     * (10 mm/h and more) at 1.
     */
    public static float level(double rain) {
        if (rain <= 0) return 0;
        if (rain < RegionalWeather.WET) return (float) (0.2 * rain / RegionalWeather.WET);
        return (float) Math.min(1, 0.2 + 0.8 * Math.log(rain / RegionalWeather.WET) / Math.log(10 / RegionalWeather.WET));
    }

    private static Field field(Level level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return null;
        if (level.isClientSide) return client;
        if (!(level instanceof ServerLevel s)) return null;
        // asked on every isRainingAt: no lock once built
        Field f = server;
        return f != null && f.level == s ? f : server(s);
    }

    // ------------------------------------------------------------------ the server

    private static synchronized Field server(ServerLevel level) {
        Field f = server;
        if (f != null && f.level == level) return f;
        ClimateGrid grid = ClimateGrid.uniform();
        TerrainPlanSurface plan = null;
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        if (access.apocalypse$hasTerrainPlan()) plan = access.apocalypse$terrainPlanSurface();
        if (plan != null && plan.federal()) grid = ClimateGrid.federal(FederalMap.forSeed(plan.seed)).quantized();
        RegionalWeather model = new RegionalWeather(PlanNoise.mix(level.getSeed() ^ 0x5745415448455231L), grid);
        WeatherData.get(level).applyTo(model);
        server = f = new Field(model, level, plan);
        ApocalypseFirstLight.LOGGER.info("[AFL weather] regional weather on ({}x{} climate grid)", grid.nx, grid.nz);
        return f;
    }

    /** What the server has, for a player (on joining, after a change by hand). */
    public static WeatherPackets.Sync sync(ServerLevel overworld) {
        Field f = server(overworld);
        ClimateGrid g = f.model.climate();
        return new WeatherPackets.Sync(f.model.seed(), g.originX, g.originZ, g.step, g.nx, g.nz, g.bytes(), f.model.forces(),
                f.model.extra(overworld.getDayTime()));
    }

    /** Tells every player (after a change by hand). */
    public static void broadcast(ServerLevel overworld) {
        WeatherPackets.Sync packet = sync(overworld);
        for (ServerPlayer p : overworld.getServer().getPlayerList().getPlayers()) AflNetwork.weather(p, packet);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ServerLevel overworld = p.server.overworld();
            AflNetwork.weather(p, sync(overworld));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        server = null;
    }

    /** Thunderstorms strike: each as often as its cell says, somewhere under it. */
    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) return;
        Field f = server(level);
        long t = level.getDayTime();
        for (RegionalWeather.Cell c : f.model.active(t)) {
            double perMinute = c.strikesPerMinute(t);
            if (perMinute <= 0 || level.random.nextDouble() >= perMinute / 1200) continue;
            double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * STRIKE_REACH * c.radiusNow(t);
            strike(level, f, c.x(t) + Math.cos(a) * d, c.z(t) + Math.sin(a) * d);
        }
    }

    /** A strike at (x, z): a real bolt where the world ticks there, otherwise only its flash and thunder for those in range. */
    private static void strike(ServerLevel level, Field f, double x, double z) {
        BlockPos column = BlockPos.containing(x, level.getSeaLevel(), z);
        if (level.isPositionEntityTicking(column)) {
            BlockPos target = ((ServerLevelLightningInvoker) level).afl$findLightningTargetAround(column);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return;
            bolt.moveTo(Vec3.atBottomCenterOf(target));
            level.addFreshEntity(bolt);
            return;
        }
        double y = f.plan != null ? Math.max(level.getSeaLevel(), f.plan.heightAt(x, z)) : level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
        LightningPackets.FarStrike packet = null;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz > LightningStrikes.FAR_RANGE * LightningStrikes.FAR_RANGE) continue;
            if (packet == null) packet = new LightningPackets.FarStrike(UUID.randomUUID(), x, y, z);
            AflNetwork.lightning(p, packet);
        }
    }

    // ------------------------------------------------------------------ the client

    /** The server's weather (client thread). */
    public static void accept(WeatherPackets.Sync s) {
        RegionalWeather model = new RegionalWeather(s.seed(), ClimateGrid.of(s.originX(), s.originZ(), s.step(), s.nx(), s.nz(), s.grid()));
        for (RegionalWeather.Force force : s.forces()) model.force(force);
        for (RegionalWeather.Cell cell : s.cells()) model.add(cell);
        client = new Field(model, null, null);
    }

    /** Leaving the server. */
    public static void clientClear() {
        client = null;
    }
}
