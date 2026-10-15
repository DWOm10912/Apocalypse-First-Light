package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * One vanilla difficulty for every survivor mode (docs/gameplay/survivor_death_succession_v0.md 2): NORMAL, HARD and the
 * later story mode differ in what a death means, never in game values, so every world runs on Normal, locked. There is
 * no Peaceful (it would remove the hostile mobs). Enforced at server start and whenever vanilla tries to change it
 * (mixin/AflDifficultyLockMixin); {@code /difficulty} with a value is refused with a message, the plain query still works.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AflDifficulty {
    public static final Difficulty FIXED = Difficulty.NORMAL;

    private AflDifficulty() {
    }

    /** The world on the fixed difficulty, locked; spawn flags and every player's client follow. */
    public static void enforce(MinecraftServer server) {
        WorldData data = server.getWorldData();
        if (data.getDifficulty() == FIXED && data.isDifficultyLocked()) return;
        Difficulty before = data.getDifficulty();
        data.setDifficulty(FIXED);
        data.setDifficultyLocked(true);
        for (ServerLevel level : server.getAllLevels()) {
            level.setSpawnSettings(server.isSpawningMonsters(), server.isSpawningAnimals());
        }
        if (server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(new ClientboundChangeDifficultyPacket(FIXED, true));
            }
        }
        ApocalypseFirstLight.LOGGER.info("[AFL Survivor] difficulty {} -> {}, locked", before.getKey(), FIXED.getKey());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        enforce(event.getServer());
    }

    /** {@code /difficulty <value>} (also behind {@code /execute ... run}): refused with the reason. */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        for (CommandContextBuilder<CommandSourceStack> context = event.getParseResults().getContext(); context != null;
             context = context.getChild()) {
            List<ParsedCommandNode<CommandSourceStack>> nodes = context.getNodes();
            if (nodes.size() >= 2 && nodes.get(0).getNode() instanceof LiteralCommandNode<CommandSourceStack> literal
                    && literal.getLiteral().equals("difficulty")) {
                event.setException(new CommandRuntimeException(Component.translatable(
                        "survivor.apocalypse_firstlight.difficulty.fixed", FIXED.getDisplayName())));
                event.setCanceled(true);
                return;
            }
        }
    }
}
