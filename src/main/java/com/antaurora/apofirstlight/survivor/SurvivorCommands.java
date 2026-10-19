package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Testing and audits (permission 2): {@code /afl survivor} (own lineage), {@code /afl survivor player <name>},
 * {@code /afl survivor list}, {@code /afl survivor corpses}. No command changes the mode, rolls a successor or revives anyone; only setup writes (below).
 * {@code /afl survivor ragdoll [hit|shot|blast|drop|collapse|clear]} (Ragdoll V1) puts a stand-in body of no lineage in front
 * of you that falls like a death of that kind, to watch a corpse from outside; {@code clear} removes them all.
 *
 * <p>Survivor setup (docs/gameplay/survivor_background_skills_v1_plan.md, P2a; stands in for the setup screen until P2b):
 * {@code /afl survivor sheet [player]} shows background, body, traits, growth and the attributes they change;
 * {@code /afl survivor catalog} lists the loaded data; {@code /afl survivor setup <background> [strength=4 fitness=2
 * <trait> ...]} checks a choice by the screen's rules and, if it passes, writes it to your living survivor (also when its
 * setup is already done: a dev override) and applies it; {@code /afl survivor setup reset} puts it back to choosing,
 * which holds the player and opens the setup screen again (to test the screen).
 * {@code /afl survivor growth <item> <level> [xp]} (P3, testing) sets a body stat or growth item of your living survivor.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorCommands {
    private SurvivorCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("survivor")
                .requires(source -> source.hasPermission(2))
                .executes(c -> status(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.literal("player").then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("corpses").executes(c -> corpses(c.getSource())))
                .then(Commands.literal("sheet").executes(c -> sheet(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> sheet(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("catalog").executes(c -> catalog(c.getSource())))
                .then(setup())
                .then(growth())
                .then(ragdoll())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> setup() {
        return Commands.literal("setup")
                .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
                .then(Commands.argument("background", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SurvivorCatalog.get().backgrounds().keySet(), b))
                        .executes(c -> setup(c.getSource(), background(c), ""))
                        .then(Commands.argument("picks", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    String typed = b.getRemaining();
                                    var last = b.createOffset(b.getStart() + typed.lastIndexOf(' ') + 1);
                                    List<String> options = new ArrayList<>();
                                    SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
                                    for (var def : catalog.body().values()) {
                                        for (int lv = def.startMin(); lv <= def.startMax(); lv++) options.add(shortId(def.id()) + "=" + lv);
                                    }
                                    for (var def : catalog.traits().values()) if (!def.backgroundOnly()) options.add(shortId(def.id()));
                                    return SharedSuggestionProvider.suggest(options, last);
                                })
                                .executes(c -> setup(c.getSource(), background(c), StringArgumentType.getString(c, "picks")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> growth() {
        return Commands.literal("growth").then(Commands.argument("item", StringArgumentType.word())
                .suggests((c, b) -> {
                    List<String> ids = new ArrayList<>();
                    SurvivorCatalog.get().body().keySet().forEach(id -> ids.add(shortId(id)));
                    SurvivorCatalog.get().growth().keySet().forEach(id -> ids.add(shortId(id)));
                    return SharedSuggestionProvider.suggest(ids, b);
                })
                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99))
                        .executes(c -> growth(c.getSource(), StringArgumentType.getString(c, "item"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level"), 0))
                        .then(Commands.argument("xp", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(c -> growth(c.getSource(), StringArgumentType.getString(c, "item"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "xp"))))));
    }

    private static int growth(CommandSourceStack source, String item, int level, int xp) throws CommandSyntaxException {
        String id = SurvivorCatalog.normalize(item);
        int set = SurvivorGrowth.set(source.getPlayerOrException(), id, level, xp);
        if (set < 0) {
            source.sendFailure(Component.literal("no such item, or no set-up living survivor: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(id + " -> Lv " + set), false);
        return 1;
    }

    private static String background(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "background");
        return "minecraft".equals(id.getNamespace()) ? SurvivorCatalog.AFL + ":" + id.getPath() : id.toString();
    }

    private static String shortId(String id) {
        return id.startsWith(SurvivorCatalog.AFL + ":") ? id.substring(SurvivorCatalog.AFL.length() + 1) : id;
    }

    private static int setup(CommandSourceStack source, String background, String picks) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SurvivorRecord record = living(player);
        if (record == null) {
            source.sendFailure(Component.literal("no living survivor"));
            return 0;
        }
        Map<String, Integer> body = new LinkedHashMap<>();
        List<String> traits = new ArrayList<>();
        for (String token : picks.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            int eq = token.indexOf('=');
            if (eq < 0) {
                traits.add(SurvivorCatalog.normalize(token));
                continue;
            }
            try {
                body.put(SurvivorCatalog.normalize(token.substring(0, eq)), Integer.parseInt(token.substring(eq + 1)));
            } catch (NumberFormatException e) {
                source.sendFailure(Component.literal("not a level: " + token));
                return 0;
            }
        }
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        SurvivorSetup.Choice choice = new SurvivorSetup.Choice(background, body, traits);
        SurvivorSetup.Check check = SurvivorSetup.check(catalog, choice);
        if (!check.ok()) {
            source.sendFailure(Component.literal("refused (" + check.pointsLeft() + " points left): "
                    + String.join("; ", check.problems())));
            return 0;
        }
        SurvivorSetup.confirm(record, catalog, choice);
        SurvivorWorldData.get(player.server).setDirty();
        Survivors.markFlush();
        SurvivorModifiers.apply(player);
        SurvivorSetupFlow.sendSheet(player);
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] {} set up survivor #{} by command: {} body {} traits {}",
                player.getGameProfile().getName(), record.lifeIndex, record.background, body, record.traits);
        int left = check.pointsLeft();
        source.sendSuccess(() -> Component.literal("setup done, " + left + " points unused\n" + describe(player, record)), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SurvivorRecord record = living(player);
        if (record == null) {
            source.sendFailure(Component.literal("no living survivor"));
            return 0;
        }
        SurvivorSetup.reset(record);
        SurvivorWorldData.get(player.server).setDirty();
        Survivors.markFlush();
        SurvivorModifiers.apply(player);
        SurvivorSetupFlow.reopened(player);
        source.sendSuccess(() -> Component.literal("setup reset to choosing (the setup screen opens)\n" + describe(player, record)), false);
        return 1;
    }

    private static SurvivorRecord living(ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        SurvivorRecord record = lineage == null ? null : lineage.current();
        return record != null && record.state == SurvivorRecord.State.ALIVE && lineage.status == SurvivorLineage.Status.ACTIVE
                ? record : null;
    }

    private static int sheet(CommandSourceStack source, ServerPlayer player) {
        SurvivorLineage lineage = SurvivorWorldData.get(source.getServer()).lineage(player.getUUID());
        SurvivorRecord record = lineage == null ? null : lineage.current();
        if (record == null) {
            source.sendFailure(Component.literal(player.getGameProfile().getName() + ": no survivor yet"));
            return 0;
        }
        String text = describe(player, record);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Setup, background, body, traits, growth, and every attribute a survivor modifier changes (with its value now). */
    private static String describe(ServerPlayer player, SurvivorRecord r) {
        SurvivorCatalog.Snapshot catalog = SurvivorCatalog.get();
        StringBuilder out = new StringBuilder();
        out.append('#').append(r.lifeIndex).append(' ').append(r.fullName()).append(' ').append(r.state)
                .append(" setup=").append(r.setup).append(" background=").append(r.background);
        out.append("\n body:");
        for (var def : catalog.body().values()) out.append(' ').append(shortId(def.id())).append('=').append(SurvivorSetup.bodyLevel(r, def));
        out.append("\n traits: ").append(r.traits.isEmpty() ? "-" : String.join(", ", r.traits.stream().map(SurvivorCommands::shortId).toList()));
        out.append("\n growth:");
        for (var def : catalog.growth().values()) {
            SurvivorRecord.Progress p = r.growth.get(def.id());
            out.append(' ').append(shortId(def.id())).append('=').append(p == null ? def.defaultLevel() : p.level);
        }
        out.append("\n attributes:");
        int changed = 0;
        for (Attribute attribute : net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null || instance.getModifiers().stream().noneMatch(m -> m.getName().startsWith(SurvivorModifiers.PREFIX))) continue;
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getKey(attribute);
            out.append(String.format(Locale.ROOT, " %s=%.3f", key.getPath(), instance.getValue()));
            changed++;
        }
        if (changed == 0) out.append(" none (ordinary)");
        return out.toString();
    }

    private static int catalog(CommandSourceStack source) {
        SurvivorCatalog.Snapshot c = SurvivorCatalog.get();
        StringBuilder out = new StringBuilder("backgrounds:");
        for (var b : c.backgrounds().values()) {
            out.append("\n ").append(shortId(b.id())).append(" budget=").append(b.budget())
                    .append(b.traits().isEmpty() ? "" : " traits=" + String.join(",", b.traits().stream().map(SurvivorCommands::shortId).toList()))
                    .append(b.successorPool() ? "" : " (not for successors)");
        }
        out.append("\nbody:");
        for (var b : c.body().values()) {
            out.append(' ').append(shortId(b.id())).append(' ').append(b.startMin()).append('-').append(b.startMax())
                    .append(" (").append(b.defaultLevel()).append(", max ").append(b.max()).append(')');
        }
        String group = null;
        for (var t : c.traits().values()) {
            if (!t.group().equals(group)) {
                group = t.group();
                out.append("\n ").append(group).append(':');
            }
            out.append(' ').append(shortId(t.id())).append(t.backgroundOnly() ? "(bg)" : String.format(Locale.ROOT, "(%+d)", -t.cost()));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return c.traits().size();
    }

    /** {@code /afl survivor ragdoll [hit|shot|blast|drop|collapse|clear]}: a stand-in body falls in front of you (Ragdoll V1). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> ragdoll() {
        var node = Commands.literal("ragdoll").executes(c -> standIn(c.getSource(), SurvivorRagdolls.StandIn.HIT));
        for (SurvivorRagdolls.StandIn kind : SurvivorRagdolls.StandIn.values()) {
            node.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT)).executes(c -> standIn(c.getSource(), kind)));
        }
        return node.then(Commands.literal("clear").executes(c -> {
            int n = SurvivorRagdolls.clearStandIns(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Component.literal("removed " + n + " stand-in bodies"), false);
            return n;
        }));
    }

    private static int standIn(CommandSourceStack source, SurvivorRagdolls.StandIn kind)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SurvivorCorpse body = SurvivorRagdolls.standIn(player, kind);
        if (body == null) {
            source.sendFailure(Component.literal("no stand-in: " + SurvivorRagdolls.MAX_ACTIVE + " bodies are falling already"));
            return 0;
        }
        Vec3 at = body.position();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "stand-in body (%s) at %.1f %.1f %.1f",
                kind.name().toLowerCase(Locale.ROOT), at.x, at.y, at.z)), false);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        SurvivorWorldData data = SurvivorWorldData.get(source.getServer());
        SurvivorLineage lineage = data.lineage(player.getUUID());
        if (lineage == null) {
            source.sendSuccess(() -> Component.literal("mode=" + data.mode.id() + " " + player.getGameProfile().getName()
                    + ": no survivor yet"), false);
            return 0;
        }
        long day = player.level().getDayTime();
        StringBuilder out = new StringBuilder();
        out.append("mode=").append(data.mode.id()).append(" player=").append(lineage.accountName)
                .append(" lineage=").append(lineage.status);
        for (SurvivorRecord r : lineage.lives) {
            out.append("\n #").append(r.lifeIndex).append(' ').append(r.fullName()).append(r.named ? "" : " (unnamed)")
                    .append(' ').append(r.state)
                    .append(" look=").append(r.skin).append('/').append(r.arms.id())
                    .append(r.sex != null ? " sex=" + r.sex.name().toLowerCase(Locale.ROOT) : "")
                    .append(String.format(Locale.ROOT, " days=%.2f", r.daysSurvived(day)));
            if (r.deathPos != null) out.append(" died=").append(r.deathCauseId).append(" @ ").append(r.deathDimension)
                    .append(' ').append(r.deathPos.toShortString());
            if (r.corpseId != null) out.append(" corpse=").append(r.corpseId);
            if (r.spawnPos != null) out.append(" start=").append(r.spawnSource).append(" @ ").append(r.spawnPos.toShortString());
            else if (r.hasSpawnPlan) out.append(" plan=").append(r.spawnPlanX).append(',').append(r.spawnPlanZ);
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return lineage.lives.size();
    }

    private static int list(CommandSourceStack source) {
        SurvivorWorldData data = SurvivorWorldData.get(source.getServer());
        StringBuilder out = new StringBuilder("mode=" + data.mode.id() + " fresh=" + data.freshWorld
                + " lineages=" + data.lineages.size() + " " + SurvivorCorpses.describe(source.getServer()));
        for (SurvivorLineage lineage : data.lineages.values()) {
            SurvivorRecord current = lineage.current();
            out.append("\n ").append(lineage.accountName).append(' ').append(lineage.status).append(" lives=")
                    .append(lineage.lives.size());
            if (current != null) out.append(" current=#").append(current.lifeIndex).append(' ').append(current.fullName());
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return data.lineages.size();
    }

    private static int corpses(CommandSourceStack source) {
        SurvivorWorldData data = SurvivorWorldData.get(source.getServer());
        StringBuilder out = new StringBuilder(SurvivorCorpses.describe(source.getServer()));
        for (SurvivorCorpseRecord corpse : data.corpses.values()) {
            SurvivorCorpseContainer container = SurvivorCorpses.container(corpse);
            out.append("\n ").append(corpse.survivorName).append(" #").append(corpse.lifeIndex)
                    .append(" look=").append(corpse.skin).append('/').append(corpse.arms.id()).append(" @ ")
                    .append(corpse.dimension).append(String.format(Locale.ROOT, " %.1f %.1f %.1f", corpse.x, corpse.y, corpse.z))
                    .append(corpse.pose != null ? " pose=ragdoll" : " pose=lying")
                    .append(" stacks=").append(corpse.itemCount()).append('/').append(corpse.items.size())
                    .append(" search: ").append(com.antaurora.apofirstlight.containersearch.AflContainerSearch.debugSummary(container));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return data.corpses.size();
    }
}
