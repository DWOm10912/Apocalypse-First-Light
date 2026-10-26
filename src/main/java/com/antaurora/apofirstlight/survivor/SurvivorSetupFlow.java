package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import com.antaurora.apofirstlight.temperature.TemperatureConfig;
import com.antaurora.apofirstlight.weight.ItemMassData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The first survivor's setup screen, server side (docs/gameplay/survivor_background_skills_v1_plan.md 9, P2b). A living
 * first survivor whose setup is {@link SurvivorSetup#CHOOSING} gets the screen (with the catalog, so the client checks
 * with the same rules) on every login until it is confirmed, and is held meanwhile: no movement, no damage, invisible,
 * no mob may target it. The confirm is checked here (name by SurvivorIdentity's rules, the rest by SurvivorSetup):
 * the name, setup and scene are written, the survivor's start time is set now (time spent choosing does not count),
 * the hold ends and the modifiers apply. Successors never see the screen (rolled at the death).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorSetupFlow {
    /** Where each held player is kept (server thread only). */
    private static final Map<UUID, Vec3> HELD = new HashMap<>();

    private SurvivorSetupFlow() {
    }

    @Nullable
    static SurvivorRecord living(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        SurvivorRecord record = lineage == null ? null : lineage.current();
        return record != null && record.state == SurvivorRecord.State.ALIVE && lineage.status == SurvivorLineage.Status.ACTIVE
                ? record : null;
    }

    /** The player's living survivor is still choosing its setup. */
    public static boolean choosing(ServerPlayer player) {
        SurvivorRecord record = living(player);
        return record != null && SurvivorSetup.CHOOSING.equals(record.setup);
    }

    /**
     * Login (after the identities): the host's setup made before the world existed is confirmed now (the usual case in
     * single player); otherwise the screen opens while still choosing. Then the player's own sheet.
     */
    static void onLogin(ServerPlayer player) {
        if (choosing(player) && !applyHostSetup(player)) sendPrompt(player);
        sendSheet(player);
    }

    /** The waiting host setup, used once: true when it was confirmed; false (screen instead) when absent or refused. */
    private static boolean applyHostSetup(ServerPlayer player) {
        SurvivorWorldData data = SurvivorWorldData.get(player.server);
        CompoundTag tag = data.hostSetup;
        if (tag == null || player.server.isDedicatedServer() || !player.server.isSingleplayerOwner(player.getGameProfile())) {
            return false;
        }
        data.hostSetup = null;
        data.setDirty();
        List<String> problems = confirm(player, decode(tag), tag.getString("Name"), SurvivorAppearance.Look.read(tag));
        if (!problems.isEmpty()) {
            ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] host setup made before the world refused ({}); opening the screen", problems);
        }
        return problems.isEmpty();
    }

    /**
     * A setup as stored for the host (Background, Body, Traits, Name, and the appearance page's Tone, Hair, Outfit): the
     * client writes it on the Create World path.
     */
    public static CompoundTag encode(SurvivorSetup.Choice choice, String name, SurvivorAppearance.Look look) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Background", choice.background());
        CompoundTag body = new CompoundTag();
        choice.body().forEach(body::putInt);
        tag.put("Body", body);
        net.minecraft.nbt.ListTag traits = new net.minecraft.nbt.ListTag();
        for (String id : choice.traits()) traits.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put("Traits", traits);
        tag.putString("Name", name);
        look.write(tag);
        return tag;
    }

    static SurvivorSetup.Choice decode(CompoundTag tag) {
        Map<String, Integer> body = new java.util.LinkedHashMap<>();
        CompoundTag b = tag.getCompound("Body");
        for (String key : b.getAllKeys()) body.put(key, b.getInt(key));
        List<String> traits = new ArrayList<>();
        for (net.minecraft.nbt.Tag t : tag.getList("Traits", net.minecraft.nbt.Tag.TAG_STRING)) traits.add(t.getAsString());
        return new SurvivorSetup.Choice(tag.getString("Background"), body, traits);
    }

    /** The base values the screen's readouts add to (comfort load kg, stamina, the comfort band, health). */
    public static CompoundTag bases() {
        CompoundTag bases = new CompoundTag();
        bases.putDouble("apocalypse_firstlight:carry_capacity", ItemMassData.snapshot().policy().comfortGrams() / 1000.0);
        bases.putDouble("apocalypse_firstlight:max_stamina", StaminaConfig.get().max);
        bases.putDouble("apocalypse_firstlight:cold_threshold", TemperatureConfig.get().comfortLow);
        bases.putDouble("apocalypse_firstlight:heat_threshold", TemperatureConfig.get().comfortHigh);
        bases.putDouble("minecraft:generic.max_health", 20);
        return bases;
    }

    /**
     * Opens the screen: the catalog files, the base values the readouts add to (comfort load kg, stamina, the comfort
     * band), the world's scene and the account name.
     */
    static void sendPrompt(ServerPlayer player) {
        CompoundTag info = new CompoundTag();
        info.put("Bases", bases());
        info.putString("Scene", SurvivorWorldData.get(player.server).scene());
        info.putString("Account", player.getGameProfile().getName());
        SurvivorRecord record = living(player);
        // a survivor named already (a dev reset) keeps its name: the screen shows it locked
        if (record != null && record.named) info.putString("Name", record.fullName());
        AflNetwork.survivor(player, new SurvivorPackets.SetupPrompt(true, SurvivorCatalog.json(), info));
    }

    /** The player's own survivor (background, traits, body, growth) for its client: login, takeover, confirm, commands. */
    static void sendSheet(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        SurvivorRecord record = lineage == null ? null : lineage.current();
        AflNetwork.survivor(player, new SurvivorPackets.Sheet(record == null ? new CompoundTag() : sheet(record)));
    }

    static CompoundTag sheet(SurvivorRecord record) {
        CompoundTag full = record.save();
        CompoundTag out = new CompoundTag();
        for (String key : List.of("First", "Last", "Life", "State", "Setup", "Scene", "Background", "Traits", "Body", "Growth")) {
            if (full.contains(key)) out.put(key, full.get(key).copy());
        }
        out.put("View", view(record));
        return out;
    }

    /**
     * What the inventory's character page needs besides the record, from the server's catalog (a multiplayer client has
     * none): the base readouts, the body items in order with their default and top level, and per trait its points, whether
     * the background brings it, and what it does (effects, body bonus, growth starts and caps). For the growth page, per body
     * stat and growth item: level, experience, experience to the next level (0 = at the top), top, what each level does
     * (Curves) and, for growth items, the nodes (Perks: level -> perk id). Display only.
     */
    private static CompoundTag view(SurvivorRecord record) {
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        CompoundTag view = new CompoundTag();
        view.put("Bases", bases());
        ListTag body = new ListTag();
        for (SurvivorCatalog.BodyDef def : catalog.body().values()) {
            CompoundTag b = new CompoundTag();
            b.putString("Id", def.id());
            b.putInt("Default", def.defaultLevel());
            b.putInt("Max", def.max());
            SurvivorRecord.Progress p = record.body.get(def.id());
            int level = SurvivorSetup.bodyLevel(record, def);
            training(b, def.training(), level, p == null ? 0 : p.xp, def.max());
            b.put("Curves", curves(def.curves()));
            body.add(b);
        }
        view.put("Body", body);
        ListTag growth = new ListTag();
        for (SurvivorCatalog.GrowthDef def : catalog.growth().values()) {
            CompoundTag g = new CompoundTag();
            g.putString("Id", def.id());
            g.putString("Group", def.group());
            g.putInt("Default", def.defaultLevel());
            g.putInt("Max", def.max());
            SurvivorRecord.Progress p = record.growth.get(def.id());
            int cap = SurvivorSetup.growthCap(record, catalog, def);
            training(g, def.training(), SurvivorSetup.growthLevel(record, catalog, def), p == null ? 0 : p.xp, cap);
            g.put("Curves", curves(def.curves()));
            CompoundTag perks = new CompoundTag();
            def.perks().forEach((level, perk) -> perks.putString(Integer.toString(level), perk));
            g.put("Perks", perks);
            growth.add(g);
        }
        view.put("Growth", growth);
        SurvivorCatalog.BackgroundDef background = record.background == null ? null : catalog.backgrounds().get(record.background);
        ListTag traits = new ListTag();
        for (String id : record.traits) {
            SurvivorCatalog.TraitDef def = catalog.traits().get(id);
            CompoundTag t = new CompoundTag();
            t.putString("Id", id);
            t.putBoolean("Fixed", background != null && background.traits().contains(id));
            if (def != null) {
                t.putInt("Cost", def.cost());
                ListTag effects = new ListTag();
                for (SurvivorCatalog.Effect e : def.effects()) {
                    var key = ForgeRegistries.ATTRIBUTES.getKey(e.attribute());
                    if (key == null) continue;
                    CompoundTag ef = new CompoundTag();
                    ef.putString("A", key.toString());
                    ef.putBoolean("M", e.multiply());
                    ef.putDouble("V", e.value());
                    effects.add(ef);
                }
                t.put("Effects", effects);
                CompoundTag bonus = new CompoundTag();
                def.bodyBonus().forEach(bonus::putInt);
                t.put("BodyBonus", bonus);
                CompoundTag starts = new CompoundTag(), caps = new CompoundTag();
                def.growthStart().forEach(starts::putInt);
                def.growthCap().forEach(caps::putInt);
                t.put("GrowthStart", starts);
                t.put("GrowthCap", caps);
            }
            traits.add(t);
        }
        view.put("Traits", traits);
        return view;
    }

    private static void training(CompoundTag out, SurvivorCatalog.Training training, int level, int xp, int cap) {
        int next = level < cap ? training.toNext(level) : 0;
        int held = next > 0 ? Math.min(xp, next) : 0;
        out.putInt("Lv", level);
        out.putInt("Cap", cap);
        out.putInt("Xp", held);
        out.putInt("Next", next);
        // the whole way: experience counted from the bottom level, and what reaching this survivor's top takes
        int total = held, full = 0;
        for (int l = 0; l < cap; l++) {
            full += training.toNext(l);
            if (l < level) total += training.toNext(l);
        }
        out.putInt("XpTotal", total);
        out.putInt("XpFull", full);
    }

    /** {A: attribute, M: multiply, V: [level 1 ... max]} per curve. */
    private static ListTag curves(List<SurvivorCatalog.Curve> list) {
        ListTag curves = new ListTag();
        for (SurvivorCatalog.Curve curve : list) {
            var key = ForgeRegistries.ATTRIBUTES.getKey(curve.attribute());
            if (key == null) continue;
            CompoundTag c = new CompoundTag();
            c.putString("A", key.toString());
            c.putBoolean("M", curve.multiply());
            ListTag values = new ListTag();
            for (double v : curve.values()) values.add(net.minecraft.nbt.DoubleTag.valueOf(v));
            c.put("V", values);
            curves.add(c);
        }
        return curves;
    }

    /** The screen's confirm (packet): checked, then written, or refused with the reasons (the screen stays open). */
    static void onChoice(ServerPlayer player, SurvivorPackets.SetupChoice packet) {
        SurvivorRecord record = living(player);
        if (record == null || !SurvivorSetup.CHOOSING.equals(record.setup)) {
            AflNetwork.survivor(player, new SurvivorPackets.SetupPrompt(false, "", new CompoundTag()));
            return;
        }
        List<String> problems = confirm(player, new SurvivorSetup.Choice(packet.background(), packet.body(), packet.traits()), packet.name(),
                new SurvivorAppearance.Look(packet.tone(), packet.hair(), packet.outfit()));
        if (!problems.isEmpty()) AflNetwork.survivor(player, new SurvivorPackets.SetupResult(problems));
    }

    /**
     * Checks a setup (name by SurvivorIdentity's rules, the rest by SurvivorSetup) for the player's choosing survivor and,
     * when it passes, writes it: name, setup, scene, start time, the body's tone and hair; the starting outfit is put on;
     * the hold ends, the modifiers apply, the screen closes. Returns the problems (empty = done).
     */
    static List<String> confirm(ServerPlayer player, SurvivorSetup.Choice choice, String rawName, SurvivorAppearance.Look look) {
        SurvivorRecord record = living(player);
        if (record == null || !SurvivorSetup.CHOOSING.equals(record.setup)) return List.of("not choosing");
        List<String> problems = new ArrayList<>();
        SurvivorIdentity.NameCheck name = record.named ? new SurvivorIdentity.NameCheck(record.fullName(), "")
                : SurvivorIdentity.checkName(player, rawName);
        if (name.name() == null) problems.add("name." + name.error());
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        SurvivorSetup.Check check = SurvivorSetup.check(catalog, choice);
        problems.addAll(check.problems());
        if (!problems.isEmpty()) return problems;
        SurvivorWorldData data = SurvivorWorldData.get(player.server);
        if (!record.named) SurvivorIdentity.applyName(player, record, name.name());
        SurvivorSetup.confirm(record, catalog, choice);
        record.scene = data.scene();
        record.tone = look.tone();
        record.hair = look.hair();
        SurvivorAppearance.dress(player, look.outfit());
        Survivors.start(player, record);
        data.setDirty();
        Survivors.markFlush();
        release(player);
        SurvivorModifiers.apply(player);
        SurvivorIdentity.changed(player);
        AflNetwork.survivor(player, new SurvivorPackets.SetupPrompt(false, "", new CompoundTag()));
        sendSheet(player);
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} set up survivor #{} {}: {} body {} traits {} scene {} ({} points unused), look {}",
                player.getGameProfile().getName(), record.lifeIndex, record.fullName(), record.background, choice.body(),
                record.traits, record.scene, check.pointsLeft(), look);
        return List.of();
    }

    /** A dev reset back to choosing: hold and screen again. */
    static void reopened(ServerPlayer player) {
        sendPrompt(player);
        sendSheet(player);
    }

    // ------------------------------------------------------------------ the hold

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!choosing(player)) {
            if (HELD.containsKey(player.getUUID())) release(player);
            return;
        }
        Vec3 at = HELD.computeIfAbsent(player.getUUID(), id -> player.position());
        if (player.position().distanceToSqr(at) > 1.0E-4) {
            player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        if (!player.isInvisible()) player.setInvisible(true);
    }

    private static void release(ServerPlayer player) {
        if (HELD.remove(player.getUUID()) != null) player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HELD.containsKey(player.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player && HELD.containsKey(player.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HELD.remove(player.getUUID()) != null) {
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        }
    }

    /** Whether this player is held right now (other systems: noise, vision). */
    public static boolean held(UUID player) {
        return HELD.containsKey(player);
    }
}
