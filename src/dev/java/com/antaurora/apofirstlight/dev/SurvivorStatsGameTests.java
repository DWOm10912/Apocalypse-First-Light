package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchSpeed;
import com.antaurora.apofirstlight.health.PlayerHealth;
import com.antaurora.apofirstlight.noise.movement.MovementNoiseEvents;
import com.antaurora.apofirstlight.radiation.RadiationSicknessManager;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import com.antaurora.apofirstlight.survivor.SurvivorStats;
import com.antaurora.apofirstlight.weight.ItemMassData;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Survivor attribute layer P1 (docs/gameplay/survivor_background_skills_v1_plan.md section 10): every attribute is on a
 * new survival player at its default, the defaults give each system its old number, a changed value reaches the system,
 * and a Creative player counts as ordinary. Only the systems a headless server can drive are checked here (comfort load,
 * stamina maximum, radiation stage, search speed, footstep radius, food exhaustion, natural healing, fall damage).
 */
@GameTestHolder("afl_survivor_stats_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class SurvivorStatsGameTests {
    private static final String NAMESPACE = "afl_survivor_stats_tests";

    private SurvivorStatsGameTests() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var tag = net.minecraft.nbt.TagParser.parseTag("{size:[1,1,1],entities:[],blocks:[{pos:[0,0,0],state:0}],palette:[{Name:\"minecraft:air\"}]}");
        level.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, "empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("survivor_stats", NAMESPACE + ":integration",
                NAMESPACE + ":empty", 100, 0L, true, SurvivorStatsGameTests::run));
    }

    private static final class Log {
        final StringBuilder text = new StringBuilder();
        final List<String> fails = new ArrayList<>();
        void check(boolean ok, String what) {
            text.append(ok ? "  ok   " : "  FAIL ").append(what).append('\n');
            if (!ok) fails.add(what);
        }
        void close(String what, double actual, double expected) {
            check(Math.abs(actual - expected) < 1e-6, String.format(Locale.ROOT, "%s: %.4f (expected %.4f)", what, actual, expected));
        }
    }

    private static ServerPlayer survivor(ServerLevel level, String name) {
        return new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
    }
    private static void base(ServerPlayer player, RegistryObject<Attribute> attribute, double value) {
        player.getAttribute(attribute.get()).setBaseValue(value);
    }

    private static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var log = new Log();

        // every attribute on a new survival player, at its default
        var plain = survivor(level, "afl-stats-plain");
        for (var attribute : AflAttributes.all()) {
            var instance = plain.getAttribute(attribute.get());
            log.check(instance != null, attribute.getId() + " on the player");
            if (instance != null) log.close(attribute.getId() + " default", SurvivorStats.get(plain, attribute), attribute.get().getDefaultValue());
        }

        // comfort load: the data value, plus the attribute (a modifier, as traits will add them)
        var data = ItemMassData.snapshot();
        long comfort = data.policy().comfortGrams();
        log.close("comfort grams, default", PlayerWeightRuntime.comfortGrams(plain, data), comfort);
        var strong = survivor(level, "afl-stats-strong");
        strong.getAttribute(AflAttributes.CARRY_CAPACITY.get()).addTransientModifier(new AttributeModifier(
                UUID.fromString("5d0c7f1e-6c1b-4e8e-9d55-2b1f0b6f3a01"), "test", 3, AttributeModifier.Operation.ADDITION));
        log.close("comfort grams, +3 kg", PlayerWeightRuntime.comfortGrams(strong, data), comfort + 3000);

        // stamina maximum
        log.close("stamina max, default", PlayerStamina.max(plain), StaminaConfig.get().max);
        base(strong, AflAttributes.MAX_STAMINA, 8);
        log.close("stamina max, +8", PlayerStamina.max(strong), StaminaConfig.get().max + 8);

        // radiation stage: the dose the stages see
        log.check(RadiationSicknessManager.getStage(plain, 1.5) == 0, "radiation stage at 1.5, default = 0");
        base(strong, AflAttributes.RADIATION_SENSITIVITY, 2);
        log.check(RadiationSicknessManager.getStage(strong, 1.5) == 1, "radiation stage at 1.5, x2 = 1");

        // container search speed
        log.close("search speed, default", AflContainerSearchSpeed.playerMultiplier(plain, null), 1);
        base(strong, AflAttributes.SEARCH_SPEED, 1.25);
        log.close("search speed, x1.25", AflContainerSearchSpeed.playerMultiplier(strong, null), 1.25);

        // footstep radius (walking)
        log.close("walk step radius, default", MovementNoiseEvents.footstepRadius(plain), 4);
        base(strong, AflAttributes.FOOTSTEP_NOISE, 0.7);
        log.close("walk step radius, x0.7", MovementNoiseEvents.footstepRadius(strong), 2.8);

        // food exhaustion (PlayerHungerRateMixin)
        float before = plain.getFoodData().getExhaustionLevel();
        plain.causeFoodExhaustion(1.0F);
        log.close("exhaustion, default", plain.getFoodData().getExhaustionLevel() - before, 1.0);
        var hungry = survivor(level, "afl-stats-hungry");
        base(hungry, AflAttributes.HUNGER_RATE, 2);
        before = hungry.getFoodData().getExhaustionLevel();
        hungry.causeFoodExhaustion(1.0F);
        log.close("exhaustion, x2", hungry.getFoodData().getExhaustionLevel() - before, 2.0);

        // natural healing (health V1a, health/PlayerHealth): vanilla's food healing is off (FoodDataThirstMixin), the
        // survivor's natural_regen scales PlayerHealth's own
        log.close("vanilla food healing off", healOnce(survivor(level, "afl-stats-heal")), 0.0);
        log.close("natural heal multiplier, default", PlayerHealth.regenMultiplier(survivor(level, "afl-stats-heal-x1")), 1.0);
        var healer = survivor(level, "afl-stats-healer");
        base(healer, AflAttributes.NATURAL_REGEN, 2);
        log.close("natural heal multiplier, x2", PlayerHealth.regenMultiplier(healer), 2.0);

        // fall damage: the event's multiplier
        log.close("fall multiplier, default", fallMultiplier(plain), 1.0);
        base(strong, AflAttributes.FALL_DAMAGE, 0.8);
        log.close("fall multiplier, x0.8", fallMultiplier(strong), 0.8);

        // Creative counts as ordinary
        var creative = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "afl-stats-creative")) {
            @Override public boolean isCreative() { return true; }
        };
        base(creative, AflAttributes.CARRY_CAPACITY, 10);
        log.close("creative carry capacity", SurvivorStats.get(creative, AflAttributes.CARRY_CAPACITY), 0);

        String result = log.fails.isEmpty() ? "PASS" : "FAIL " + log.fails;
        ApocalypseFirstLight.LOGGER.info("[AFL SURVIVOR STATS TEST] {}\n{}", result, log.text);
        if (log.fails.isEmpty()) h.succeed();
        else h.fail(result);
    }

    private static double healOnce(ServerPlayer player) {
        player.setHealth(10);
        var food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(0);
        for (int i = 0; i < 80; i++) food.tick(player);
        return player.getHealth() - 10;
    }

    private static double fallMultiplier(ServerPlayer player) {
        var event = new LivingFallEvent(player, 10, 1);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getDamageMultiplier();
    }
}
