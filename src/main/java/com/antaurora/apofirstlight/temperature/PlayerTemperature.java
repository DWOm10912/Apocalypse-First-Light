package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Temperature V1, server side (docs/项目内容/01 - 设计/生存/体温.md; numbers in TemperatureConfig). Once a second per
 * player (staggered by entity id):
 * <pre>
 * T_out  = mean + amp·D(time of day) + weather + altitude        outdoor air of the biome (5-point blend) or dimension
 * T_in   = lerp(mean + altitude, rock temperature, underground)   sheltered air: the daily mean, or the rock deep down
 * T_env  = lerp(T_out, T_in, S) + Q·(0.6 + 0.4·S)                 S: TemperatureShelter cover; Q: heat / cold sources
 * felt   = T_env − wet chill + exertion heat                     in water, by how deep (submersion 0..1): toward the
 *                                                                water temperature, more chill, less insulation and
 *                                                                exertion heat
 * target = 37 − 0.15·max(0, (14 − insulation) − felt) + 0.15·max(0, felt − 24), within [25, 55]
 * core  += (target − core)·(1 − e^(−1/τ)), within [33, 41]       τ 300 s worsening (→ 100 s submerged), 120 s recovering
 * </pre>
 * The wrist thermometer shows T_env with the sources uncapped; only the core decides the stages. Cold stages (36 / 35 / 34) and
 * heat stages (38 / 39 / 40) slow stamina, speed up thirst, slow the player and stop natural healing before the last
 * one also hurts (can kill on any difficulty but Peaceful, like dehydration). Creative / Spectator: the core stays at 37, no effects.
 * Saved as {core, wet, exertion} in the player's persistent data; a death starts at 37, dry.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PlayerTemperature {
    private static final String KEY = "afl_temperature";
    public static final ResourceKey<DamageType> HYPOTHERMIA = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "hypothermia"));
    public static final ResourceKey<DamageType> HEATSTROKE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "heatstroke"));
    /** A transient MULTIPLY_TOTAL movement speed modifier, never saved; its FOV change is divided out on the client. */
    public static final UUID SPEED_MODIFIER = UUID.fromString("3c8e5a71-0d42-4b9f-8e16-7a2f9c4b1d58");
    private static final int PERIOD = 20, SYNC_KEEP_ALIVE = 40;
    private static final Map<ServerPlayer, State> STATES = new HashMap<>();

    /** One update's numbers, for /afltemp. */
    record Reading(String climate, double mean, double amp, double water, double precipitation, double day, double outdoor,
                   double sheltered, double cover, double sources, double env, double shown, double submersion, double exertionHeat,
                   double insulation, double felt, double target) {}

    private static final class State {
        double core = Double.NaN, wet, exertion, ambient = Double.NaN, target = Double.NaN, damageTimer;
        int coldStage, heatStage, lastUpdate = Integer.MIN_VALUE, lastSyncTick = Integer.MIN_VALUE;
        boolean forceSync = true;
        float sentCore = Float.NaN, sentAmbient, sentCold, sentHeat, sentDanger, sentSway, sentTrend, sentTarget;
        Reading reading;
    }
    private PlayerTemperature() {}

    private static State state(ServerPlayer player) {
        var s = STATES.computeIfAbsent(player, p -> new State());
        if (Double.isNaN(s.core)) {
            var c = TemperatureConfig.get();
            var data = player.getPersistentData();
            if (data.contains(KEY)) {
                var tag = data.getCompound(KEY);
                s.core = Mth.clamp(tag.getDouble("core"), c.coreMin, c.coreMax);
                s.wet = Mth.clamp(tag.getDouble("wet"), 0, 1);
                s.exertion = Math.max(0, tag.getDouble("exertion"));
            } else s.core = c.normal;
        }
        return s;
    }
    private static boolean enabled(Player player) { return !player.isCreative() && !player.isSpectator(); }

    private static TemperatureConfig.Stage coldStage(State s, TemperatureConfig c) {
        return s.coldStage <= 0 || s.coldStage > c.coldStages.size() ? TemperatureConfig.NONE : c.coldStages.get(s.coldStage - 1);
    }
    private static TemperatureConfig.Stage heatStage(State s, TemperatureConfig c) {
        return s.heatStage <= 0 || s.heatStage > c.heatStages.size() ? TemperatureConfig.NONE : c.heatStages.get(s.heatStage - 1);
    }

    public static double core(ServerPlayer player) { return state(player).core; }
    /** PlayerStamina: regeneration × this. */
    public static double staminaRegenMultiplier(ServerPlayer player) {
        if (!enabled(player)) return 1;
        var c = TemperatureConfig.get();
        var s = state(player);
        return coldStage(s, c).staminaRegen * heatStage(s, c).staminaRegen;
    }
    /** PlayerStamina: every cost × this. */
    public static double staminaCostMultiplier(ServerPlayer player) {
        if (!enabled(player)) return 1;
        var c = TemperatureConfig.get();
        var s = state(player);
        return coldStage(s, c).staminaCost * heatStage(s, c).staminaCost;
    }
    /** PlayerThirst: the drain × this. */
    public static double thirstMultiplier(ServerPlayer player) {
        if (!enabled(player)) return 1;
        var c = TemperatureConfig.get();
        var s = state(player);
        return coldStage(s, c).thirst * heatStage(s, c).thirst;
    }
    /** FoodDataThirstMixin: no natural regeneration from the hypothermia / heat exhaustion stage on. */
    public static boolean blocksNaturalRegen(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !enabled(player)) return false;
        var c = TemperatureConfig.get();
        var s = state(serverPlayer);
        return coldStage(s, c).noNaturalRegen || heatStage(s, c).noNaturalRegen;
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        var s = state(player);
        int now = player.server.getTickCount();
        boolean first = s.lastUpdate == Integer.MIN_VALUE;
        if (!first && (now + player.getId()) % PERIOD != 0) return;
        double dt = first ? 1 : Mth.clamp((now - s.lastUpdate) / 20.0, 0.05, 3);
        s.lastUpdate = now;
        var c = TemperatureConfig.get();
        update(player, s, c, dt);
        if (enabled(player)) effects(player, s, c, dt);
        else { s.core = c.normal; s.target = c.normal; s.coldStage = 0; s.heatStage = 0; s.damageTimer = 0; applySpeed(player, 1); }
        var tag = new CompoundTag();
        tag.putDouble("core", s.core);
        tag.putDouble("wet", s.wet);
        tag.putDouble("exertion", s.exertion);
        player.getPersistentData().put(KEY, tag);
        sync(player, s, c, now);
    }

    // ---- the model

    private static void update(ServerPlayer player, State s, TemperatureConfig c, double dt) {
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition(), head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        double y = player.getY();
        var dimension = c.dimensions.get(level.dimension().location().toString());
        double mean, amp, water, precipitation;
        String climate;
        if (dimension != null) {
            mean = dimension.mean; amp = dimension.amp; water = dimension.water; precipitation = 0;
            climate = level.dimension().location().toString();
        } else {
            double[] blend = climate(level, feet, c);
            mean = blend[0]; amp = blend[1]; water = blend[2]; precipitation = blend[3];
            climate = level.getBiome(feet).unwrapKey().map(k -> k.location().toString()).orElse("?");
        }
        boolean fixedTime = level.dimensionType().hasFixedTime();
        double day = fixedTime ? 0 : dayCurve(level.getDayTime());
        double weather = precipitation * (c.weather.rain * level.getRainLevel(1)
                + (c.weather.thunder - c.weather.rain) * level.getThunderLevel(1));
        double altitude = dimension != null ? 0 : c.altitude.perBlock * Math.max(0, y - c.altitude.startY);
        double underground = dimension != null ? 0 : Mth.clamp((c.cave.seaLevel - y) / c.cave.depthFull, 0, 1);
        double rock = c.cave.base + c.cave.perBlockBelowZero * Math.max(0, -y);
        double outdoor = mean + (fixedTime ? 0 : amp) * day + weather + altitude;
        double sheltered = Mth.lerp(underground, mean + altitude, rock);
        double waterTemperature = Mth.lerp(underground, water, rock);
        double cover = TemperatureShelter.sample(level, head).cover();
        double[] q = sources(level, player, c);
        double factor = c.outdoorSourceFactor + (1 - c.outdoorSourceFactor) * cover;
        double heat = q[0] * factor, cold = q[1] * factor;
        double submersion = submersion(player);
        double air = Mth.lerp(submersion, Mth.lerp(cover, outdoor, sheltered), waterTemperature), env = air + heat + cold;
        // what the wrist thermometer shows: the same air with the sources uncapped, only softly limited (right by a lava pool
        // it reads well over 100 °C, a large lava lake does not run into thousands);
        // the body keeps the capped value, so the heat stages stay as tuned
        double limit = c.thermometerSourceLimit, shown = air + (limit * (1 - Math.exp(-q[2] / limit)) - limit * (1 - Math.exp(q[3] / limit))) * factor;

        // wet: the water soaks the body up to a level set by the depth (fast), rain adds slowly; dries back down to that level
        var w = c.wetness;
        double soaked = Math.min(1, submersion * w.wetPerSubmersion);
        boolean raining = level.isRainingAt(head);
        if (raining) s.wet = Math.min(1, s.wet + w.rainPerSecond * dt);
        if (s.wet < soaked) s.wet = Math.min(soaked, s.wet + w.soakPerSecond * submersion * dt);
        else if (!raining && s.wet > soaked)
            s.wet = Math.max(soaked, s.wet - Math.min(w.dryMaxFactor, 1 + heat / w.dryHeatPer) / w.drySeconds * dt);

        double rate = PlayerStamina.consumeSpentForTemperature(player) / dt;
        s.exertion += (rate - s.exertion) * (1 - Math.exp(-dt / c.exertion.tauSeconds));
        double exertionHeat = Math.min(c.exertion.cap, c.exertion.perStamina * s.exertion) * Mth.lerp(submersion, 1, w.immersedExertion);
        double insulation = ThermalInsulation.total(player) * (1 - submersion) * (1 - w.insulationLoss * s.wet);
        double felt = env - w.chill * s.wet - w.immersionChill * submersion + exertionHeat;
        double target = c.normal - c.coldSlope * Math.max(0, c.comfortLow - insulation - felt) + c.heatSlope * Math.max(0, felt - c.comfortHigh);
        if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) target = Math.min(target, c.fireResistanceTargetCap);
        target = Mth.clamp(target, c.targetMin, c.targetMax);

        boolean recovering = s.core != c.normal && Math.signum(target - s.core) == Math.signum(c.normal - s.core);
        double tau = recovering ? c.tauRecover : Mth.lerp(submersion, c.tauWorsen, c.tauWorsenImmersed);
        s.core = Mth.clamp(s.core + (target - s.core) * (1 - Math.exp(-dt / tau)), c.coreMin, c.coreMax);
        s.ambient = shown;
        s.target = target;
        s.reading = new Reading(climate, mean, amp, water, precipitation, day, outdoor, sheltered, cover, heat + cold, env, shown,
                submersion, exertionHeat, insulation, felt, target);
    }

    /** −1 at 05:00, +1 at 14:00, cosine halves in between (dayTime 0 = 06:00). */
    static double dayCurve(long dayTime) {
        double h = (Math.floorMod(dayTime, 24000L) / 1000.0 + 6) % 24;
        if (h >= 5 && h < 14) return -Math.cos(Math.PI * (h - 5) / 9);
        return Math.cos(Math.PI * ((h - 14 + 24) % 24) / 15);
    }

    /** {mean, amp, water, precipitation share} averaged over the feet and ±biome_blend blocks on x and z. */
    private static double[] climate(ServerLevel level, BlockPos feet, TemperatureConfig c) {
        int b = (int)Math.round(c.biomeBlend);
        BlockPos[] points = b <= 0 ? new BlockPos[]{feet}
                : new BlockPos[]{feet, feet.offset(b, 0, 0), feet.offset(-b, 0, 0), feet.offset(0, 0, b), feet.offset(0, 0, -b)};
        double[] sum = new double[4];
        for (var pos : points) {
            var holder = level.getBiome(pos);
            var biome = holder.value();
            var entry = holder.unwrapKey().map(k -> c.biomes.get(k.location().toString())).orElse(null);
            double mean, amp, water;
            if (entry != null) { mean = entry.mean; amp = entry.amp; water = entry.water; }
            else {
                var f = c.fallback;
                mean = f.meanBase + f.meanPerTemperature * biome.getBaseTemperature();
                amp = f.ampBase + f.ampPerDryness * (1 - biome.getModifiedClimateSettings().downfall());
                water = Math.max(0, mean - f.waterBelowMean);
            }
            sum[0] += mean; sum[1] += amp; sum[2] += water; sum[3] += biome.hasPrecipitation() ? 1 : 0;
        }
        for (int i = 0; i < 4; i++) sum[i] /= points.length;
        return sum;
    }

    /**
     * {heat, cold, heat uncapped, cold uncapped} of the listed source blocks around the player: per side the strongest +
     * stacking × the rest; the first two capped for the body, the last two not (what a thermometer reads).
     */
    private static double[] sources(ServerLevel level, ServerPlayer player, TemperatureConfig c) {
        var map = c.sourceMap;
        if (map.isEmpty()) return new double[4];
        double px = player.getX(), py = player.getY() + 1.0, pz = player.getZ();
        int bx = Mth.floor(px), by = Mth.floor(player.getY()), bz = Mth.floor(pz);
        int x0 = bx - c.scan.horizontal, x1 = bx + c.scan.horizontal, z0 = bz - c.scan.horizontal, z1 = bz + c.scan.horizontal;
        int y0 = Math.max(level.getMinBuildHeight(), by - c.scan.below), y1 = Math.min(level.getMaxBuildHeight() - 1, by + c.scan.above);
        double heat = 0, cold = 0, maxHeat = 0, maxCold = 0;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                int index = chunk.getSectionIndexFromSectionY(sy);
                if (index < 0 || index >= chunk.getSectionsCount()) continue;
                LevelChunkSection section = chunk.getSection(index);
                if (section.hasOnlyAir() || !section.maybeHas(state -> map.containsKey(state.getBlock()))) continue;
                int ax = Math.max(x0, cx << 4), az = Math.max(z0, cz << 4), ay = Math.max(y0, sy << 4);
                int ex = Math.min(x1, (cx << 4) + 15), ez = Math.min(z1, (cz << 4) + 15), ey = Math.min(y1, (sy << 4) + 15);
                for (int x = ax; x <= ex; x++) for (int yy = ay; yy <= ey; yy++) for (int z = az; z <= ez; z++) {
                    var state = section.getBlockState(x & 15, yy & 15, z & 15);
                    var source = map.get(state.getBlock());
                    if (source == null) continue;
                    if (source.lit && !(state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT))) continue;
                    double dx = x + 0.5 - px, dy = yy + 0.5 - py, dz = z + 0.5 - pz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d >= source.radius) continue;
                    double k = 1 - d / source.radius, v = source.strength * (source.square ? k * k : k);
                    if (v > 0) { heat += v; maxHeat = Math.max(maxHeat, v); } else { cold += v; maxCold = Math.min(maxCold, v); }
                }
            }
        }
        heat = maxHeat + c.sourceStacking * (heat - maxHeat);
        cold = maxCold + c.sourceStacking * (cold - maxCold);
        return new double[]{Math.min(c.sourceCapHeat, heat), Math.max(c.sourceCapCold, cold), heat, cold};
    }

    /**
     * How much of the body is in water, 0..1: the water depth over the player's height (ankle-deep about 0.1, standing in
     * one block of water about 0.5); swimming or the head under water 1; a boat keeps the player dry.
     */
    private static double submersion(ServerPlayer player) {
        if (player.isSwimming() || player.isUnderWater()) return 1;
        if (!player.isInWater()) return 0;
        return Mth.clamp(player.getFluidHeight(FluidTags.WATER) / Math.max(0.1, player.getBbHeight()), 0, 1);
    }

    // ---- stages and effects

    private static void effects(ServerPlayer player, State s, TemperatureConfig c, double dt) {
        int cold = Math.min(s.coldStage, c.coldStages.size()), heat = Math.min(s.heatStage, c.heatStages.size());
        while (cold < c.coldStages.size() && s.core < c.coldStages.get(cold).below) cold++;
        while (cold > 0 && s.core >= c.coldStages.get(cold - 1).below + c.hysteresis) cold--;
        while (heat < c.heatStages.size() && s.core > c.heatStages.get(heat).above) heat++;
        while (heat > 0 && s.core <= c.heatStages.get(heat - 1).above - c.hysteresis) heat--;
        if (cold > s.coldStage) player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.temperature.cold" + cold), true);
        else if (heat > s.heatStage) player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.temperature.hot" + heat), true);
        s.coldStage = cold; s.heatStage = heat;

        var coldStage = coldStage(s, c);
        var heatStage = heatStage(s, c);
        applySpeed(player, coldStage.speed * heatStage.speed);
        double exhaustion = (coldStage.exhaustionPerSecond + heatStage.exhaustionPerSecond) * dt;
        if (exhaustion > 0) player.causeFoodExhaustion((float)exhaustion);
        if ((coldStage.damage || heatStage.damage) && player.level().getDifficulty() != Difficulty.PEACEFUL) {
            s.damageTimer += dt;
            if (s.damageTimer >= c.damageIntervalSeconds) {
                s.damageTimer -= c.damageIntervalSeconds;
                hurt(player, coldStage.damage ? HYPOTHERMIA : HEATSTROKE);
            }
        } else s.damageTimer = 0;
    }

    private static void applySpeed(ServerPlayer player, double multiplier) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double amount = multiplier - 1.0;
        var current = speed.getModifier(SPEED_MODIFIER);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) return;
        if (current != null) speed.removeModifier(SPEED_MODIFIER);
        if (Math.abs(amount) >= 1e-6) speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER,
                "AFL body temperature", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Kills on every difficulty but Peaceful (unlike vanilla starvation; the caller skips Peaceful), like dehydration. */
    private static void hurt(ServerPlayer player, ResourceKey<DamageType> key) {
        var type = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        player.hurt(new DamageSource(type), 1.0F);
    }

    // ---- client state

    private static void sync(ServerPlayer player, State s, TemperatureConfig c, int now) {
        var h = c.hud;
        double core = s.core;
        float coldness = (float)Mth.clamp((h.coldStart - core) / (h.coldStart - h.coldFull), 0, 1);
        float heat = (float)Mth.clamp((core - h.heatStart) / (h.heatFull - h.heatStart), 0, 1);
        float danger = (float)Math.max(Mth.clamp((h.dangerColdStart - core) / (h.dangerColdStart - h.dangerColdFull), 0, 1),
                Mth.clamp((core - h.dangerHeatStart) / (h.dangerHeatFull - h.dangerHeatStart), 0, 1));
        float sway = (float)(1 + c.sway.extra * Mth.clamp((c.sway.start - core) / (c.sway.start - c.sway.full), 0, 1));
        float ambient = Double.isNaN(s.ambient) ? (float)c.normal : (float)s.ambient;
        // where the core is heading: + warming, − cooling (the core moves slowly; this shows at once)
        double gap = Double.isNaN(s.target) ? 0 : s.target - core;
        float trend = (float)(Math.signum(gap) * Mth.clamp((Math.abs(gap) - h.trendDeadband) / (h.trendFull - h.trendDeadband), 0, 1));
        // the target on the dial's scale: heat − coldness of the target temperature
        double aim = Double.isNaN(s.target) ? core : s.target;
        float target = (float)(Mth.clamp((aim - h.heatStart) / (h.heatFull - h.heatStart), 0, 1)
                - Mth.clamp((h.coldStart - aim) / (h.coldStart - h.coldFull), 0, 1));
        boolean changed = s.forceSync || Float.isNaN(s.sentCore) || Math.abs((float)core - s.sentCore) >= 0.05f
                || Math.abs(ambient - s.sentAmbient) >= 0.05f || Math.abs(coldness - s.sentCold) >= 0.01f
                || Math.abs(heat - s.sentHeat) >= 0.01f || Math.abs(danger - s.sentDanger) >= 0.01f
                || Math.abs(sway - s.sentSway) >= 0.01f || Math.abs(trend - s.sentTrend) >= 0.05f || Math.abs(target - s.sentTarget) >= 0.02f || now - s.lastSyncTick >= SYNC_KEEP_ALIVE;
        if (!changed) return;
        AflNetwork.temperatureState(player, new TemperaturePackets.State((float)core, ambient, coldness, heat, danger, sway, trend, target));
        s.sentCore = (float)core; s.sentAmbient = ambient; s.sentCold = coldness; s.sentHeat = heat; s.sentDanger = danger;
        s.sentSway = sway; s.sentTrend = trend; s.sentTarget = target; s.lastSyncTick = now; s.forceSync = false;
    }

    // ---- lifecycle

    /** The next tick updates at once (a full second's step) and sends the state. */
    private static void refresh(ServerPlayer player) {
        var s = state(player);
        s.forceSync = true;
        s.lastUpdate = Integer.MIN_VALUE;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) STATES.remove(player);
    }
    /** A death starts at the normal core temperature, dry; returning from the End keeps the state. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer player)) return;
        var s = STATES.remove(original);
        if (event.isWasDeath()) player.getPersistentData().remove(KEY);
        else if (s != null) {
            var tag = new CompoundTag();
            tag.putDouble("core", s.core); tag.putDouble("wet", s.wet); tag.putDouble("exertion", s.exertion);
            player.getPersistentData().put(KEY, tag);
        }
        refresh(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.clear(); }

    // ---- /afltemp

    static String describe(ServerPlayer player) {
        var c = TemperatureConfig.get();
        var s = state(player);
        var r = s.reading;
        String model = r == null ? "no update yet" : String.format(Locale.ROOT,
                "%s mean %.1f amp %.1f water %.1f precip %.2f | D %.2f | out %.1f in %.1f S %.2f | sources %+.1f | env %.1f (thermometer %.1f)%s | wet %.2f | exertion +%.1f | insulation %.1f | felt %.1f | target %.2f",
                r.climate(), r.mean(), r.amp(), r.water(), r.precipitation(), r.day(), r.outdoor(), r.sheltered(), r.cover(),
                r.sources(), r.env(), r.shown(), r.submersion() > 0 ? String.format(Locale.ROOT, " (in water %.2f)", r.submersion()) : "", s.wet, r.exertionHeat(), r.insulation(), r.felt(), r.target());
        return String.format(Locale.ROOT, "%s: core %.2f °C | stage cold %d heat %d | stamina regen x%.2f cost x%.2f | thirst x%.2f | natural regen %s | enabled=%s\n%s",
                player.getGameProfile().getName(), s.core, s.coldStage, s.heatStage, staminaRegenMultiplier(player),
                staminaCostMultiplier(player), thirstMultiplier(player), blocksNaturalRegen(player) ? "blocked" : "normal",
                enabled(player), model);
    }
    static void setCore(ServerPlayer player, double value) {
        var c = TemperatureConfig.get();
        var s = state(player);
        s.core = Mth.clamp(value, c.coreMin, c.coreMax);
        s.forceSync = true;
    }
    static void setWet(ServerPlayer player, double value) {
        state(player).wet = Mth.clamp(value, 0, 1);
    }
}
