package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Testing and audits (permission 2), read only: {@code /afl survivor} (own lineage), {@code /afl survivor player <name>},
 * {@code /afl survivor list}, {@code /afl survivor corpses}. No command changes the mode, rolls a successor or revives anyone.
 * {@code /afl survivor ragdoll [hit|shot|blast|drop|collapse|clear]} (Ragdoll V1) puts a stand-in body of no lineage in front
 * of you that falls like a death of that kind, to watch a corpse from outside; {@code clear} removes them all.
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
                .then(ragdoll())));
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
