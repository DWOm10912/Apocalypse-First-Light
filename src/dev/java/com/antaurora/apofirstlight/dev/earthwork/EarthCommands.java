package com.antaurora.apofirstlight.dev.earthwork;

import com.antaurora.apofirstlight.earthwork.EarthChunk;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;

/**
 * {@code /afl dev earth}: Earthwork A1 (docs/worldgen/earthwork_a1.md 6), the only way to edit the height field until the
 * shovel (A2). Development runs only, op level 2, a player; aims at the ground under the crosshair (within 8 blocks), else
 * the player's feet.
 * <pre>
 *   dig [radius] [depth]     a round dig (blocks; default 1.5, 0.5); the earth goes to your carried earth
 *   fill [radius] [depth]    a round fill from your carried earth (never more than you carry)
 *   info                     the aimed column, the edited ground around you (net, loose earth), your carried earth and
 *                            the held shovel's load, and their balance (exact quanta: 0 when nothing was lost)
 *   reset [radius]           edited columns within radius (blocks, default 8) back to their first look, no longer edited
 *   check                    the edited columns around you: missing vertices, blocks not following the skin
 * </pre>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class EarthCommands {
    private EarthCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("earth")
                .requires(s -> !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer);
        c.then(Commands.literal("dig").executes(x -> edit(x, true, 1.5, 0.5))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.3, 6))
                        .executes(x -> edit(x, true, DoubleArgumentType.getDouble(x, "radius"), 0.5))
                        .then(Commands.argument("depth", DoubleArgumentType.doubleArg(0.05, 3))
                                .executes(x -> edit(x, true, DoubleArgumentType.getDouble(x, "radius"), DoubleArgumentType.getDouble(x, "depth"))))));
        c.then(Commands.literal("fill").executes(x -> edit(x, false, 1.5, 0.5))
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.3, 6))
                        .executes(x -> edit(x, false, DoubleArgumentType.getDouble(x, "radius"), 0.5))
                        .then(Commands.argument("depth", DoubleArgumentType.doubleArg(0.05, 3))
                                .executes(x -> edit(x, false, DoubleArgumentType.getDouble(x, "radius"), DoubleArgumentType.getDouble(x, "depth"))))));
        c.then(Commands.literal("info").executes(EarthCommands::info));
        c.then(Commands.literal("reset").executes(x -> reset(x, 8))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 64)).executes(x -> reset(x, IntegerArgumentType.getInteger(x, "radius")))));
        c.then(Commands.literal("check").executes(EarthCommands::check));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    private static Vec3 aim(ServerPlayer player) {
        HitResult hit = player.pick(8.0, 1.0F, false);
        return hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b.getLocation() : player.position();
    }

    private static String litres(long quanta) {
        return String.format(Locale.ROOT, "%.1f L", quanta * EarthGrid.LITRES_PER_QUANTUM);
    }

    private static int edit(CommandContext<CommandSourceStack> x, boolean dig, double radius, double depth) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return 0;
        Vec3 at = aim(player);
        Earthwork.Result r = dig ? Earthwork.dig(level, player, at.x, at.z, radius, depth) : Earthwork.fill(level, player, at.x, at.z, radius, depth);
        long carried = Earthwork.CARRIED.getOrDefault(player.getUUID(), 0L);
        x.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Earthwork %s at %.2f %.2f r %.2f d %.2f: %s moved, %d vertices, %d columns edited first, %d vertices refused (fixed ground); carrying %s",
                dig ? "dig" : "fill", at.x, at.z, radius, depth, litres(Math.abs(r.moved())), r.vertices(), r.converted(), r.refused(), litres(carried))), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> x) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return 0;
        Vec3 at = aim(player);
        int bx = (int) Math.floor(at.x), bz = (int) Math.floor(at.z);
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "Earthwork column %d %d: %s", bx, bz, Earthwork.describe(level, bx, bz)));
        int[] h = new int[EarthGrid.SIDE * EarthGrid.SIDE];
        boolean[] u = new boolean[h.length];
        if (Earthwork.heights(level, bx, bz, h, u)) {
            EarthChunk e = Earthwork.get(level, bx >> 4, bz >> 4);
            int col = EarthGrid.column(bx, bz), still = 0;
            for (boolean b : u) if (b) still++;
            s.append(String.format(Locale.ROOT, ", first top y %d (%s), skin %.3f .. %.3f, %d / 25 vertices undisturbed",
                    e.top0(col), e.topState(col), EarthGrid.min(h) / (double) EarthGrid.Q, EarthGrid.max(h) / (double) EarthGrid.Q, still));
        }
        int columns = 0, vertices = 0;
        long net = 0, loose = 0;
        int pcx = player.blockPosition().getX() >> 4, pcz = player.blockPosition().getZ() >> 4;
        for (int cz = pcz - 4; cz <= pcz + 4; cz++) for (int cx = pcx - 4; cx <= pcx + 4; cx++) {
            EarthChunk e = Earthwork.get(level, cx, cz);
            if (e == null) continue;
            columns += e.columns();
            vertices += e.vertices();
            net += e.net();
            loose += e.looseTotal();
        }
        // A2 (docs/worldgen/earthwork_a2.md 7): exact quanta; the earth balances when net + carried + the shovel's load is 0
        long carried = Earthwork.CARRIED.getOrDefault(player.getUUID(), 0L);
        var shovel = player.getMainHandItem().is(com.antaurora.apofirstlight.registry.AflItems.ROUND_POINT_SHOVEL.get())
                ? com.antaurora.apofirstlight.item.ShovelItem.load(player.getMainHandItem()) : null;
        long load = shovel == null ? 0 : shovel.stored();
        s.append(String.format(Locale.ROOT, "; within 4 chunks: %d edited columns, %d vertices, net %s (%d q), loose %s (%d q); carrying %s (%d q)",
                columns, vertices, (net < 0 ? "-" : "+") + litres(Math.abs(net)), net, litres(loose), loose, litres(carried), carried));
        if (shovel != null) s.append(String.format(Locale.ROOT, "; shovel %s %s (%d q)", litres(load), shovel.material() == null ? "empty" : shovel.material(), load));
        s.append(String.format(Locale.ROOT, "; balance net + carried + shovel = %d q; snapshot mismatches %d", net + carried + load, Earthwork.mismatches));
        x.getSource().sendSuccess(() -> Component.literal(s.toString()), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> x, int radius) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return 0;
        int n = Earthwork.reset(level, player.blockPosition().getX(), player.blockPosition().getZ(), radius);
        x.getSource().sendSuccess(() -> Component.literal("Earthwork reset: " + n + " columns back to their first look"), false);
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> x) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return 0;
        int[] r = Earthwork.check(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, 4);
        x.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Earthwork check (4 chunks): %d edited columns, %d with a missing vertex, %d cells not following the skin, %d out of range",
                r[0], r[1], r[2], r[3])), r[1] + r[2] + r[3] > 0);
        return 1;
    }
}
