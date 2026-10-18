package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.weight.ItemMassData;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Survivor setup P2a (docs/gameplay/survivor_background_skills_v1_plan.md 4–6, 10, 11): the shipped data loads, the point
 * rules accept and refuse what the design says, a confirmed setup stores final values, those values become attribute
 * modifiers (and go away again), records survive save and load, V0 records count as ordinary, and successor rolls are
 * reproducible and inside their budget. Runs with SurvivorStatsGameTests (same namespace and template).
 */
@GameTestHolder("afl_survivor_stats_tests")
@PrefixGameTestTemplate(false)
public final class SurvivorSetupGameTests {
    private static final String NAMESPACE = "afl_survivor_stats_tests";

    private SurvivorSetupGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("survivor_setup", NAMESPACE + ":integration", NAMESPACE + ":empty", 100, 0L, true,
                SurvivorSetupGameTests::run));
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

    private static SurvivorSetup.Choice choice(String background, Map<String, Integer> body, String... traits) {
        List<String> ids = new ArrayList<>();
        for (String t : traits) ids.add("afl:" + t);
        return new SurvivorSetup.Choice("afl:" + background, body, ids);
    }

    private static SurvivorRecord record(SurvivorRecord.State state) {
        return new SurvivorRecord(UUID.randomUUID(), 1, "Test", "", 1L, state);
    }

    private static double value(ServerPlayer player, net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> a) {
        return player.getAttributeValue(a.get());
    }

    private static void run(GameTestHelper h) {
        var log = new Log();
        var c = SurvivorCatalog.get();

        // data
        log.check(c.body().size() == 2, "2 body stats: " + c.body().keySet());
        log.check(c.growth().size() == 3, "3 growth items: " + c.growth().keySet());
        log.check(c.traits().size() == 31, "31 traits: " + c.traits().size());
        log.check(c.backgrounds().size() == 4, "4 backgrounds: " + c.backgrounds().keySet());
        log.check("afl:ordinary".equals(c.backgrounds().keySet().iterator().next()), "ordinary listed first");
        var coldHardy = c.traits().get("afl:cold_hardy");
        log.check(coldHardy != null && coldHardy.exclusive().contains("afl:cold_sensitive"), "exclusions go both ways");
        log.check("afl:strength".equals(c.body().keySet().iterator().next()), "strength before fitness");

        // point rules
        var plain = SurvivorSetup.check(c, choice("ordinary", Map.of()));
        log.check(plain.ok() && plain.pointsLeft() == 6, "ordinary 3/3 no traits: 6 left, " + plain);
        var full = SurvivorSetup.check(c, choice("ordinary", Map.of("afl:strength", 5, "afl:fitness", 5), "athletic"));
        log.check(full.ok() && full.pointsLeft() == 0, "ordinary 5/5 + athletic: 0 left, " + full);
        var over = SurvivorSetup.check(c, choice("ordinary", Map.of("afl:strength", 5, "afl:fitness", 5), "athletic", "light_step"));
        log.check(!over.ok() && over.pointsLeft() == -1, "one point over is refused, " + over);
        var paid = SurvivorSetup.check(c, choice("ordinary", Map.of("afl:strength", 5, "afl:fitness", 5), "athletic", "light_step", "smoker"));
        log.check(paid.ok() && paid.pointsLeft() == 0, "a negative trait pays for it, " + paid);
        log.check(!SurvivorSetup.check(c, choice("ordinary", Map.of(), "cold_hardy", "cold_sensitive")).ok(), "exclusive pair refused");
        var cap = SurvivorSetup.check(c, choice("ordinary", Map.of(), "frail", "smoker", "thirsty", "heavy_footed"));
        log.check(!cap.ok() && cap.negativePoints() == 5, "negative traits over 4 points refused, " + cap);
        log.check(SurvivorSetup.check(c, choice("ordinary", Map.of(), "frail", "smoker", "thirsty")).ok(), "negative traits at 4 points pass");
        log.check(!SurvivorSetup.check(c, choice("ordinary", Map.of(), "veteran")).ok(), "background-only trait refused");
        log.check(!SurvivorSetup.check(c, choice("forest_resident", Map.of(), "cold_sensitive")).ok(), "excluded by a background trait");
        log.check(!SurvivorSetup.check(c, choice("forest_resident", Map.of(), "cold_hardy")).ok(), "background trait picked again refused");
        log.check(!SurvivorSetup.check(c, choice("ordinary", Map.of("afl:strength", 6))).ok(), "strength 6 at the start refused");
        log.check(!SurvivorSetup.check(c, choice("ordinary", Map.of("afl:fitness", 0))).ok(), "fitness 0 refused");
        log.check(!SurvivorSetup.check(c, choice("ordinary", Map.of(), "no_such_trait")).ok(), "unknown trait refused");
        log.check(!SurvivorSetup.check(c, choice("no_such_background", Map.of())).ok(), "unknown background refused");
        var low = SurvivorSetup.check(c, choice("forest_resident", Map.of("afl:strength", 1, "afl:fitness", 2)));
        log.check(low.ok() && low.pointsLeft() == 6, "levels down give points (forest 3 + 3), " + low);

        // confirm: final values
        var big = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(big, c, choice("ordinary", Map.of("afl:strength", 5), "big_frame", "firearms_trained"));
        log.check(SurvivorSetup.DONE.equals(big.setup), "confirm marks done");
        log.check(big.body.get("afl:strength").level == 6, "big frame: strength 5 + 1 = 6");
        log.check(big.body.get("afl:fitness").level == 3, "fitness default 3");
        log.check(big.growth.get("afl:firearms_pistol").level == 3, "firearms training: pistol 3");
        var trucker = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(trucker, c, choice("trucker", Map.of(), "hunter"));
        log.check(trucker.traits.equals(List.of("afl:mechanic", "afl:smoker", "afl:hunter")), "traits = background's + chosen: " + trucker.traits);
        log.check(trucker.growth.get("afl:firearms_long").level == 3 && trucker.growth.get("afl:firearms_pistol").level == 1,
                "hunter: long gun 3, pistol 1");

        // the character page's sheet (P2c): the View part a multiplayer client reads instead of the catalog
        var sheet = SurvivorSetupFlow.sheet(trucker);
        var view = sheet.getCompound("View");
        var viewBody = view.getList("Body", net.minecraft.nbt.Tag.TAG_COMPOUND);
        log.check(viewBody.size() == 2 && "afl:strength".equals(viewBody.getCompound(0).getString("Id"))
                && viewBody.getCompound(0).getInt("Max") == 7, "sheet view: body in order with its top level");
        var viewTraits = view.getList("Traits", net.minecraft.nbt.Tag.TAG_COMPOUND);
        log.check(viewTraits.size() == 3 && viewTraits.getCompound(0).getBoolean("Fixed") && viewTraits.getCompound(1).getBoolean("Fixed")
                && !viewTraits.getCompound(2).getBoolean("Fixed"), "sheet view: background traits marked fixed");
        log.check(viewTraits.getCompound(1).getInt("Cost") < 0 && viewTraits.getCompound(2).getBoolean("GunStart"),
                "sheet view: smoker gives points, hunter has gun experience");
        log.check(view.getCompound("Bases").contains("apocalypse_firstlight:carry_capacity"), "sheet view: base readouts");

        // modifiers
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "afl-setup"));
        var forest = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(forest, c, choice("forest_resident", Map.of("afl:strength", 5)));
        SurvivorModifiers.apply(player, forest, c);
        log.close("strength 5: carry capacity +6", value(player, AflAttributes.CARRY_CAPACITY), 6);
        log.close("cold hardy: cold threshold -1", value(player, AflAttributes.COLD_THRESHOLD), -1);
        log.close("strength 5 x manual labor: work cost 0.8 x 0.85", value(player, AflAttributes.WORK_STAMINA_COST), 0.68);
        var data = ItemMassData.snapshot();
        log.close("comfort grams follow", PlayerWeightRuntime.comfortGrams(player, data), data.policy().comfortGrams() + 6000);
        SurvivorModifiers.apply(player, forest, c);
        log.close("applying twice changes nothing", value(player, AflAttributes.WORK_STAMINA_COST), 0.68);
        var sturdy = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(sturdy, c, choice("ordinary", Map.of("afl:fitness", 4), "sturdy"));
        SurvivorModifiers.apply(player, sturdy, c);
        log.close("sturdy: max health 22", player.getAttributeValue(Attributes.MAX_HEALTH), 22);
        log.close("previous survivor's carry capacity gone", value(player, AflAttributes.CARRY_CAPACITY), 0);
        log.close("fitness 4: max stamina +8", value(player, AflAttributes.MAX_STAMINA), 8);
        log.close("fitness 4: regen x1.08", value(player, AflAttributes.STAMINA_REGEN), 1.08);
        var choosing = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.reset(choosing);
        SurvivorModifiers.apply(player, choosing, c);
        log.close("choosing: max health 20", player.getAttributeValue(Attributes.MAX_HEALTH), 20);
        log.close("choosing: max stamina 0", value(player, AflAttributes.MAX_STAMINA), 0);
        SurvivorModifiers.apply(player, forest, c);
        SurvivorModifiers.apply(player, null, c);
        log.close("no survivor: cold threshold 0", value(player, AflAttributes.COLD_THRESHOLD), 0);

        // save and load
        SurvivorRecord back = SurvivorRecord.load(big.save());
        log.check(back != null && SurvivorSetup.DONE.equals(back.setup) && "afl:ordinary".equals(back.background)
                && back.traits.equals(big.traits) && back.body.get("afl:strength").level == 6
                && back.growth.get("afl:firearms_long").level == 3, "save and load keep setup, traits, body, growth");
        CompoundTag v0 = record(SurvivorRecord.State.ALIVE).save();
        v0.putString("Body", "reserved");
        SurvivorRecord old = SurvivorRecord.load(v0);
        log.check(old != null && SurvivorSetup.DONE.equals(old.setup) && SurvivorSetup.ORDINARY.equals(old.background)
                && old.body.isEmpty(), "V0 living record: done, ordinary, string Body ignored");
        SurvivorRecord oldPending = SurvivorRecord.load(record(SurvivorRecord.State.PENDING).save());
        log.check(oldPending != null && oldPending.setup == null, "V0 pending successor: not set up yet");
        SurvivorRecord fresh = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.reset(fresh);
        SurvivorRecord freshBack = SurvivorRecord.load(fresh.save());
        log.check(freshBack != null && SurvivorSetup.CHOOSING.equals(freshBack.setup), "choosing survives save and load");

        // successors
        var r1 = record(SurvivorRecord.State.PENDING);
        var r2 = record(SurvivorRecord.State.PENDING);
        SurvivorSetup.rollSuccessor(r1, c, 42L);
        SurvivorSetup.rollSuccessor(r2, c, 42L);
        log.check(r1.background.equals(r2.background) && r1.body.get("afl:strength").level == r2.body.get("afl:strength").level
                && r1.body.get("afl:fitness").level == r2.body.get("afl:fitness").level, "same seed, same successor");
        int bad = 0;
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (long seed = 0; seed < 400; seed++) {
            var r = record(SurvivorRecord.State.PENDING);
            SurvivorSetup.rollSuccessor(r, c, seed * 7919L);
            var bg = c.backgrounds().get(r.background);
            int spent = 0;
            boolean ok = bg != null && SurvivorSetup.DONE.equals(r.setup) && r.traits.equals(bg.traits());
            for (var def : c.body().values()) {
                int lv = SurvivorSetup.bodyLevel(r, def);
                ok &= lv >= SurvivorSetup.SUCCESSOR_MIN && lv <= SurvivorSetup.SUCCESSOR_MAX;
                spent += lv - def.defaultLevel();
            }
            ok &= bg != null && spent <= bg.budget();
            if (!ok) bad++;
            if (r.background != null) seen.add(r.background);
        }
        log.check(bad == 0, "400 successors inside the rules (bad " + bad + ")");
        log.check(seen.size() == 4, "every background rolled: " + seen);

        // P2b: the client builds the same catalog from the files the server sends
        SurvivorCatalog.Snapshot parsed = SurvivorCatalog.parse(SurvivorCatalog.json());
        log.check(parsed.backgrounds().keySet().equals(c.backgrounds().keySet()) && parsed.traits().keySet().equals(c.traits().keySet())
                && parsed.body().keySet().equals(c.body().keySet()) && parsed.growth().keySet().equals(c.growth().keySet()),
                "catalog JSON round trip: same entries");
        var sample = choice("afl:trucker", Map.of("afl:strength", 4), "afl:frail");
        log.check(SurvivorSetup.check(parsed, sample).equals(SurvivorSetup.check(c, sample)), "catalog JSON round trip: same check");
        // start scenes
        log.check(SurvivorScenes.byId("afl:nowhere").id().equals(SurvivorScenes.WILDERNESS), "unknown scene falls back to the wilderness");
        for (var scene : SurvivorScenes.ALL) {
            log.check(scene.recommended() == null || c.backgrounds().containsKey(scene.recommended()), "scene " + scene.id() + " suits a real background");
        }
        SurvivorWorldData world = new SurvivorWorldData();
        log.check(world.scene().equals(SurvivorScenes.WILDERNESS), "no scene = wilderness");
        world.scene = "afl:roadside";
        log.check("afl:roadside".equals(SurvivorWorldData.load(world.save(new net.minecraft.nbt.CompoundTag())).scene), "scene survives save and load");
        log.check(SurvivorWorldData.load(new SurvivorWorldData().save(new net.minecraft.nbt.CompoundTag())).scene == null, "old world: no scene");

        // the host's setup made before the world: stored, saved, read back the same
        var made = choice("afl:forest_resident", Map.of("afl:strength", 4, "afl:fitness", 2), "afl:light_step", "afl:heat_sensitive");
        var tag = SurvivorSetupFlow.encode(made, "Ada");
        var decoded = SurvivorSetupFlow.decode(tag);
        log.check(decoded.background().equals(made.background()) && decoded.body().equals(made.body()) && decoded.traits().equals(made.traits())
                && "Ada".equals(tag.getString("Name")), "host setup encode / decode");
        SurvivorWorldData hosted = new SurvivorWorldData();
        hosted.hostSetup = tag;
        var hostedBack = SurvivorWorldData.load(hosted.save(new net.minecraft.nbt.CompoundTag()));
        log.check(hostedBack.hostSetup != null && hostedBack.hostSetup.equals(tag), "host setup survives save and load");
        log.check(SurvivorScenes.byId("afl:forest_home").maxPlayers() == 3 && SurvivorScenes.byId(SurvivorScenes.WILDERNESS).maxPlayers() == 0,
                "forest home takes up to 3");

        String result = log.fails.isEmpty() ? "PASS" : "FAIL " + log.fails;
        ApocalypseFirstLight.LOGGER.info("[AFL SURVIVOR SETUP TEST] {}\n{}", result, log.text);
        if (log.fails.isEmpty()) h.succeed();
        else h.fail(result);
    }
}
