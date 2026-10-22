package com.antaurora.apofirstlight.dev.earthwork;

import com.antaurora.apofirstlight.boulder.BoulderBlocks;
import com.antaurora.apofirstlight.boulder.BoulderData;
import com.antaurora.apofirstlight.boulder.BoulderIndex;
import com.antaurora.apofirstlight.boulder.BoulderWorld;
import com.antaurora.apofirstlight.boulder.LooseStones;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * {@code /afl dev rock}: Rock R1a / R1b (docs/gameplay/rock_r1_stones_boulders_design.md 6, 11.2, 11.3), boulders and
 * loose stones before worldgen places them (C, design 7). Development runs only, op level 2, a player, the overworld (the
 * boulder index's).
 * <pre>
 *   boulder &lt;form&gt; [turn]   a whole boulder of that form (12 ids) on the ground under the crosshair (within 16 blocks):
 *                            its origin cell the one the aimed point lies in, its ground that cell's floor; turn 0..3
 *                            quarter turns (default: the way you face)
 *   info                     every rock filling the aimed cell: form, node, stage, volume, strikes taken of its strikes,
 *                            what its last strike does, turn, root
 *   scatter &lt;radius&gt; [field|river] [flint]
 *                            loose stones on the ground around you (radius 1..32 blocks): field on open ground, river on
 *                            the bed of water too (waterlogged); flint the share of patches with a flint, 0..1 (default
 *                            1/8, design 2.4's limestone country)
 * </pre>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class RockCommands {
    private RockCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("rock")
                .requires(s -> !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer);
        c.then(Commands.literal("boulder").then(Commands.argument("form", StringArgumentType.word())
                .suggests((x, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BoulderData.get().forms).map(f -> f.id), b))
                .executes(x -> boulder(x, -1))
                .then(Commands.argument("turn", IntegerArgumentType.integer(0, 3)).executes(x -> boulder(x, IntegerArgumentType.getInteger(x, "turn"))))));
        c.then(Commands.literal("info").executes(RockCommands::info));
        c.then(Commands.literal("scatter").then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                .executes(x -> scatter(x, LooseStones.Kind.FIELD, 1 / 8F))
                .then(Commands.literal("field").executes(x -> scatter(x, LooseStones.Kind.FIELD, 1 / 8F))
                        .then(Commands.argument("flint", FloatArgumentType.floatArg(0, 1)).executes(x -> scatter(x, LooseStones.Kind.FIELD, FloatArgumentType.getFloat(x, "flint")))))
                .then(Commands.literal("river").executes(x -> scatter(x, LooseStones.Kind.RIVER, 1 / 8F))
                        .then(Commands.argument("flint", FloatArgumentType.floatArg(0, 1)).executes(x -> scatter(x, LooseStones.Kind.RIVER, FloatArgumentType.getFloat(x, "flint")))))));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    private static BlockHitResult aim(ServerPlayer player) {
        HitResult hit = player.pick(16.0, 1.0F, false);
        return hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    private static boolean ready(CommandContext<CommandSourceStack> x, ServerPlayer player) {
        if (!BoulderBlocks.ready()) { x.getSource().sendFailure(Component.literal("Boulder blocks are not registered")); return false; }
        if (player.level().dimension() != Level.OVERWORLD) { x.getSource().sendFailure(Component.literal("Boulders are overworld only")); return false; }
        return true;
    }

    private static int boulder(CommandContext<CommandSourceStack> x, int turn) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || !ready(x, player)) return 0;
        String id = StringArgumentType.getString(x, "form");
        int form = BoulderData.form(id);
        if (form < 0) {
            x.getSource().sendFailure(Component.literal("No boulder form " + id + "; forms: "
                    + String.join(", ", Arrays.stream(BoulderData.get().forms).map(f -> f.id).toList())));
            return 0;
        }
        BlockHitResult hit = aim(player);
        if (hit == null) { x.getSource().sendFailure(Component.literal("Aim at the ground within 16 blocks")); return 0; }
        Vec3 at = hit.getLocation();
        BlockPos origin = BlockPos.containing(at.x, at.y + 0.05, at.z);
        int t = turn >= 0 ? turn : player.getDirection().get2DDataValue();
        int pieces = BoulderWorld.place(level, form, origin, t);
        x.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Boulder %s (%d piece%s) at %d %d %d, turn %d", id, pieces, pieces == 1 ? "" : "s", origin.getX(), origin.getY(), origin.getZ(), t)), true);
        return pieces;
    }

    private static int scatter(CommandContext<CommandSourceStack> x, LooseStones.Kind kind, float flint) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return 0;
        if (!LooseStones.ready()) { x.getSource().sendFailure(Component.literal("Loose stones are not registered")); return 0; }
        int radius = IntegerArgumentType.getInteger(x, "radius");
        int n = LooseStones.scatter(level, player.blockPosition(), radius, kind, flint);
        x.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "%d patch%s of %s stones within %d blocks, flint %.2f", n, n == 1 ? "" : "es", kind.getSerializedName(), radius, flint)), true);
        return n;
    }

    private static int info(CommandContext<CommandSourceStack> x) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || !ready(x, player)) return 0;
        BlockHitResult hit = aim(player);
        if (hit == null) { x.getSource().sendFailure(Component.literal("Aim at a boulder within 16 blocks")); return 0; }
        BlockPos p = hit.getBlockPos();
        var found = new ArrayList<BoulderIndex.Root>(2);
        BoulderIndex.SERVER.roots(p.getX(), p.getY(), p.getZ(), found);
        found.removeIf(r -> r.cellAt(p.getX(), p.getY(), p.getZ()) == null);
        if (found.isEmpty()) {
            x.getSource().sendFailure(Component.literal("No rock fills " + p.toShortString()
                    + (BoulderBlocks.isBoulder(level.getBlockState(p)) ? " (a boulder block without a rock: stale)" : "")));
            return 0;
        }
        for (var r : found) {
            String line = BoulderWorld.describe(level, r);
            x.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return found.size();
    }
}
