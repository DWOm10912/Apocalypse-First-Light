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
        log.check(c.growth().size() == 5, "5 growth items (3 guns, cold, heat): " + c.growth().keySet());
        log.check(c.traits().size() == 30, "30 traits (hunter gone): " + c.traits().size());
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
        log.check(big.growth.get("afl:firearms_pistol").level == 4, "firearms training: pistol 4 (experienced)");
        var trucker = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(trucker, c, choice("trucker", Map.of(), "cold_hardy"));
        log.check(trucker.traits.equals(List.of("afl:mechanic", "afl:smoker", "afl:cold_hardy")), "traits = background's + chosen: " + trucker.traits);
        log.check(trucker.growth.get("afl:firearms_long").level == 0 && trucker.growth.get(SurvivorGrowth.COLD).level == 3
                && trucker.growth.get(SurvivorGrowth.HEAT).level == 0, "ordinary guns 0; cold hardy: cold tolerance 3, heat 0");
        log.check(!c.traits().containsKey("afl:hunter"), "hunter removed (2026-10-18)");

        // the character page's sheet (P2c): the View part a multiplayer client reads instead of the catalog
        var sheet = SurvivorSetupFlow.sheet(trucker);
        var view = sheet.getCompound("View");
        var viewBody = view.getList("Body", net.minecraft.nbt.Tag.TAG_COMPOUND);
        log.check(viewBody.size() == 2 && "afl:strength".equals(viewBody.getCompound(0).getString("Id"))
                && viewBody.getCompound(0).getInt("Max") == 10, "sheet view: body in order with its top level");
        log.check(viewBody.getCompound(0).getList("Curves", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 2,
                "sheet view: strength's two curves for the growth page");
        var viewTraits = view.getList("Traits", net.minecraft.nbt.Tag.TAG_COMPOUND);
        log.check(viewTraits.size() == 3 && viewTraits.getCompound(0).getBoolean("Fixed") && viewTraits.getCompound(1).getBoolean("Fixed")
                && !viewTraits.getCompound(2).getBoolean("Fixed"), "sheet view: background traits marked fixed");
        log.check(viewTraits.getCompound(1).getInt("Cost") < 0 && viewTraits.getCompound(2).getCompound("GrowthStart").getInt(SurvivorGrowth.COLD) == 3,
                "sheet view: smoker gives points, cold hardy starts cold tolerance at 3");
        log.check(view.getCompound("Bases").contains("apocalypse_firstlight:carry_capacity"), "sheet view: base readouts");

        // growth by use (P3): experience, level-ups, no daily cap, learning speed, fractions, tops, the sheet's growth part
        String pistol = SurvivorGrowth.PISTOL;
        var frac = new java.util.HashMap<String, Double>();
        var g1 = SurvivorGrowth.add(trucker, c, pistol, "hit", 5, 1, frac);
        log.check(g1.added() == 15 && !g1.levelled() && trucker.growth.get(pistol).level == 0 && trucker.growth.get(pistol).xp == 15,
                "5 hits: 15 experience, pistol still 0, " + g1);
        var g2 = SurvivorGrowth.add(trucker, c, pistol, "hit", 20, 1, frac);
        log.check(g2.added() == 60 && g2.levelled() && g2.level() == 2 && trucker.growth.get(pistol).xp == 0,
                "20 more hits: 0 -> 1 (30) -> 2 (45), " + g2);
        var g3 = SurvivorGrowth.add(trucker, c, pistol, "hit", 100, 1.5, frac);
        log.check(g3.added() == 450 && g3.level() == 6 && trucker.growth.get(pistol).xp == 15,
                "no daily cap: 100 hits x1.5 = 450, up to 6 with 15 over, " + g3);
        var grownSheet = SurvivorSetupFlow.sheet(trucker).getCompound("View").getList("Growth", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var pistolView = grownSheet.getCompound(0);
        log.check(grownSheet.size() == 5 && pistol.equals(pistolView.getString("Id")) && pistolView.getInt("Lv") == 6
                && pistolView.getInt("Next") == 225 && pistolView.getInt("Xp") == 15 && pistolView.getInt("Cap") == 10
                && pistolView.getInt("XpTotal") == 525 && pistolView.getInt("XpFull") == 1955
                && "firearms".equals(pistolView.getString("Group")) && !pistolView.contains("Full"),
                "sheet growth: pistol 6, 15 of 225, top 10, firearms: " + pistolView);
        log.check("reload_novice".equals(pistolView.getCompound("Perks").getString("0"))
                && "reload_trained".equals(pistolView.getCompound("Perks").getString("4"))
                && "reload_ads".equals(pistolView.getCompound("Perks").getString("5"))
                && "reload_practised".equals(pistolView.getCompound("Perks").getString("7")), "sheet growth: the gun nodes at 0, 4, 5, 7");
        var coldView = grownSheet.getCompound(3);
        log.check(SurvivorGrowth.COLD.equals(coldView.getString("Id")) && "climate".equals(coldView.getString("Group"))
                && coldView.getInt("Lv") == 3 && coldView.getList("Curves", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 1,
                "sheet growth: cold tolerance 3 in climate, with its curve: " + coldView);
        log.check(SurvivorGrowth.add(trucker, c, SurvivorGrowth.COLD, "exposed_minute", 1, 1, frac).added() == 2,
                "a minute in the cold: 2 experience");
        log.check(SurvivorGrowth.add(trucker, c, pistol, "no_such_source", 5, 1, frac).added() == 0, "unknown source: nothing");
        var walker = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(walker, c, choice("ordinary", Map.of()));
        for (int i = 0; i < 3; i++) SurvivorGrowth.add(walker, c, SurvivorGrowth.STRENGTH, "loaded_walk_10m", 1, 1, frac);
        log.check(walker.body.get(SurvivorGrowth.STRENGTH).xp == 1, "0.4 x 3 loaded walks: 1 whole point, the rest waits");
        var top = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(top, c, choice("ordinary", Map.of("afl:strength", 5), "big_frame"));
        top.body.get(SurvivorGrowth.STRENGTH).level = 10;
        log.check(SurvivorGrowth.add(top, c, SurvivorGrowth.STRENGTH, "chop", 5, 1, frac).added() == 0, "strength 10 is the top");
        var shy = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.confirm(shy, c, choice("ordinary", Map.of(), "gun_shy"));
        shy.growth.get(SurvivorGrowth.LONG).level = 5;
        shy.growth.get(SurvivorGrowth.LONG).xp = 150;
        var shyUp = SurvivorGrowth.add(shy, c, SurvivorGrowth.LONG, "hit", 40, 1, frac);
        shy.growth.get(SurvivorGrowth.LONG).xp = 1000;
        var shyMore = SurvivorGrowth.add(shy, c, SurvivorGrowth.LONG, "hit", 1, 1, frac);
        log.check(SurvivorSetup.growthCap(shy, c, c.growth().get(SurvivorGrowth.LONG)) == 6 && shyUp.level() == 6
                && shyMore.added() == 0, "gun shy: long gun stops at 6, " + shyUp);
        log.check(SurvivorGrowth.tier(3) == 0 && SurvivorGrowth.tier(4) == 1 && SurvivorGrowth.tier(6) == 1 && SurvivorGrowth.tier(7) == 2
                && SurvivorGrowth.tier(10) == 2, "gun tiers: 0-3, 4-6, 7-10");
        log.check(Math.abs(SurvivorGrowth.adsSway(0) - 1.25F) < 1e-5 && Math.abs(SurvivorGrowth.adsSway(5) - 1F) < 1e-5
                && Math.abs(SurvivorGrowth.recoilRecovery(0) - 0.8F) < 1e-5 && Math.abs(SurvivorGrowth.recoilRecovery(10) - 1.2F) < 1e-5,
                "gun levels: aiming sway x1.25 -> x0.75, recoil recovery x0.8 -> x1.2");
        var grownBack = SurvivorRecord.load(trucker.save());
        log.check(grownBack != null && grownBack.growth.get(pistol).level == 6 && grownBack.growth.get(SurvivorGrowth.COLD).level == 3,
                "growth survives save and load");

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
        log.close("sturdy: no max health bonus (health V1a: 100 for everyone)", player.getAttributeValue(Attributes.MAX_HEALTH), player.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
        log.close("previous survivor's carry capacity gone", value(player, AflAttributes.CARRY_CAPACITY), 0);
        log.close("fitness 4: max stamina +8", value(player, AflAttributes.MAX_STAMINA), 8);
        log.close("fitness 4: regen x1.08", value(player, AflAttributes.STAMINA_REGEN), 1.08);
        var choosing = record(SurvivorRecord.State.ALIVE);
        SurvivorSetup.reset(choosing);
        SurvivorModifiers.apply(player, choosing, c);
        log.close("choosing: no max health modifier", player.getAttributeValue(Attributes.MAX_HEALTH), player.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
        log.close("choosing: max stamina 0", value(player, AflAttributes.MAX_STAMINA), 0);
        SurvivorModifiers.apply(player, forest, c);
        SurvivorModifiers.apply(player, null, c);
        log.close("no survivor: cold threshold 0", value(player, AflAttributes.COLD_THRESHOLD), 0);
        SurvivorModifiers.apply(player, trucker, c);
        log.close("cold tolerance 3: cold threshold -1 (growth curve, not the trait)", value(player, AflAttributes.COLD_THRESHOLD), -1);
        log.close("cold tolerance 3: heat threshold untouched", value(player, AflAttributes.HEAT_THRESHOLD), 0);
        SurvivorModifiers.apply(player, null, c);

        // growth repeats: the same reload / pull / breath inside 60 s counts 1, 0.5, 0.25, then nothing
        double rep1 = SurvivorGrowth.repeat(player, "test"), rep2 = SurvivorGrowth.repeat(player, "test");
        double rep3 = SurvivorGrowth.repeat(player, "test"), rep4 = SurvivorGrowth.repeat(player, "test");
        log.check(rep1 == 1 && rep2 == 0.5 && rep3 == 0.25 && rep4 == 0, "repeats: " + rep1 + " " + rep2 + " " + rep3 + " " + rep4);
        log.check(SurvivorGrowth.repeat(player, "other") == 1, "repeats are counted per kind");

        // save and load
        SurvivorRecord back = SurvivorRecord.load(big.save());
        log.check(back != null && SurvivorSetup.DONE.equals(back.setup) && "afl:ordinary".equals(back.background)
                && back.traits.equals(big.traits) && back.body.get("afl:strength").level == 6
                && back.growth.get("afl:firearms_long").level == 4, "save and load keep setup, traits, body, growth");
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
        var look = new SurvivorAppearance.Look(4, "buzz", SurvivorAppearance.Garment.SHIRT.bit() | SurvivorAppearance.Garment.SHOES.bit());
        var tag = SurvivorSetupFlow.encode(made, "Ada", look);
        var decoded = SurvivorSetupFlow.decode(tag);
        log.check(decoded.background().equals(made.background()) && decoded.body().equals(made.body()) && decoded.traits().equals(made.traits())
                && "Ada".equals(tag.getString("Name")), "host setup encode / decode");
        // character creation V1: the appearance page's look rides along; an older host setup reads as the defaults
        log.check(SurvivorAppearance.Look.read(tag).equals(look), "host setup look encode / decode");
        log.check(SurvivorAppearance.Look.read(new net.minecraft.nbt.CompoundTag()).equals(SurvivorAppearance.Look.DEFAULT), "old host setup: default look");
        log.check(new SurvivorAppearance.Look(9, "mohawk", 0xFF).equals(new SurvivorAppearance.Look(SurvivorAppearance.DEFAULT_TONE,
                SurvivorAppearance.DEFAULT_HAIR, SurvivorAppearance.FULL_OUTFIT)), "a bad look is put into range");
        var rolled = new SurvivorRecord(java.util.UUID.randomUUID(), 2, "A", "B", 12345L, SurvivorRecord.State.PENDING);
        SurvivorAppearance.roll(rolled, 12345L);
        var rolledBack = SurvivorRecord.load(rolled.save());
        log.check(rolledBack.tone == rolled.tone && rolled.tone >= 1 && rolled.tone <= SurvivorAppearance.TONES
                && java.util.Objects.equals(rolledBack.hair, rolled.hair) && SurvivorAppearance.HAIR.contains(rolled.hair), "successor tone / hair roll survives save and load");
        SurvivorWorldData hosted = new SurvivorWorldData();
        hosted.hostSetup = tag;
        var hostedBack = SurvivorWorldData.load(hosted.save(new net.minecraft.nbt.CompoundTag()));
        log.check(hostedBack.hostSetup != null && hostedBack.hostSetup.equals(tag), "host setup survives save and load");
        log.check(SurvivorScenes.byId("afl:forest_home").maxPlayers() == 3 && SurvivorScenes.byId(SurvivorScenes.WILDERNESS).maxPlayers() == 0,
                "forest home takes up to 3");
        log.check(SurvivorScenes.SHOWN.get(0).id().equals(SurvivorScenes.DEFAULT) && SurvivorScenes.DEFAULT.equals("afl:forest_home"),
                "V1 test: the forest home is the default, first on the page");
        log.check(SurvivorScenes.SHOWN.stream().noneMatch(s -> s.id().equals("afl:bunker")) && SurvivorScenes.byId("afl:bunker").id().equals("afl:bunker"),
                "V1 test: the bunker is hidden but old worlds keep it");

        String result = log.fails.isEmpty() ? "PASS" : "FAIL " + log.fails;
        ApocalypseFirstLight.LOGGER.info("[AFL SURVIVOR SETUP TEST] {}\n{}", result, log.text);
        if (log.fails.isEmpty()) h.succeed();
        else h.fail(result);
    }
}
