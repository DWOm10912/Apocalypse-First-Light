package com.antaurora.apofirstlight.worldgen.highway;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** DEV-only entry point for the Regional Highway V1A engineering prototype. */
public final class HighwayDebugCommand {
    private static final int DEFAULT_LENGTH = 1024;
    private static final int MIN_LENGTH = 256;
    private static final int MAX_LENGTH = 2048;
    private static final Map<ServerLevel, HighwayEditSession> SESSIONS = new WeakHashMap<>();

    private HighwayDebugCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("highway_test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear").executes(HighwayDebugCommand::clear))
                .executes(context -> generate(context, DEFAULT_LENGTH, null))
                .then(Commands.argument("length", IntegerArgumentType.integer(MIN_LENGTH, MAX_LENGTH))
                        .executes(context -> generate(context, IntegerArgumentType.getInteger(context, "length"), null))
                        .then(Commands.argument("heading", StringArgumentType.word())
                                .executes(context -> generate(context,
                                        IntegerArgumentType.getInteger(context, "length"),
                                        StringArgumentType.getString(context, "heading")))));
    }

    private static int generate(CommandContext<CommandSourceStack> context, int length, String headingName) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        Heading heading = headingName == null ? Heading.fromYaw(player.getYRot()) : Heading.parse(headingName);
        if (heading == null) {
            context.getSource().sendFailure(Component.literal("Heading must be N, NE, E, SE, S, SW, W, or NW."));
            return 0;
        }
        HighwayEditSession old = SESSIONS.remove(level);
        if (old != null) old.restore();
        BlockPos start = player.blockPosition();
        HighwayPlan main = HighwayPlan.main(start, heading.x, heading.z, length, level.getSeed());
        HighwayEditSession edit = new HighwayEditSession(level);
        ensureChunks(level, main);
        long started = System.nanoTime();
        HighwayProfile profile = HighwayProfile.sample(level, main);
        HighwayRenderStats stats = HighwayRenderer.render(level, profile, edit);
        HighwayRenderStats interchange = HighwayInterchangeRenderer.render(level, main, edit);
        stats.add(interchange);
        SESSIONS.put(level, edit);
        String message = String.format("[AFL HIGHWAY V1A] start=(%d,%d,%d) end=(%d,%d) length=%d heading=%s sampleCount=%d actualWidth=%d profileMinY=%d profileMaxY=%d maxGradeObserved=%d GROUND segments=%d CUT segments=%d FILL segments=%d VIADUCT segments=%d blocksPlaced=%d blocksCleared=%d fillBlocks=%d cutBlocks=%d viaductBlocks=%d piersPlaced=%d interchangeGenerated=AT_GRADE_DIAMOND elapsedMs=%d",
                start.getX(), start.getY(), start.getZ(), (int) Math.round(main.sample(main.length()).x()),
                (int) Math.round(main.sample(main.length()).z()), length, heading.name(), profile.samples().size(),
                main.width(), profile.minRoadY(), profile.maxRoadY(), profile.observedMaxGrade(), stats.groundSegments,
                stats.cutSegments, stats.fillSegments, stats.viaductSegments, stats.blocksPlaced, stats.blocksCleared,
                stats.fillBlocks, stats.cutBlocks, stats.viaductBlocks, stats.piersPlaced,
                (System.nanoTime() - started) / 1_000_000L);
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        HighwayEditSession session = SESSIONS.remove(level);
        if (session == null || session.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No AFL highway test session is recorded in this dimension."));
            return 0;
        }
        int restored = session.restore();
        context.getSource().sendSuccess(() -> Component.literal("[AFL HIGHWAY V1A] clear restoredBlocks=" + restored), true);
        return 1;
    }

    private static void ensureChunks(ServerLevel level, HighwayPlan plan) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (HighwayPlan.Point point : plan.controlPoints()) {
            minX = Math.min(minX, (int) Math.floor(point.x()) - plan.width() - 8);
            maxX = Math.max(maxX, (int) Math.ceil(point.x()) + plan.width() + 8);
            minZ = Math.min(minZ, (int) Math.floor(point.z()) - plan.width() - 8);
            maxZ = Math.max(maxZ, (int) Math.ceil(point.z()) + plan.width() + 8);
        }
        // The command's per-block edits also load intermediate chunks. This bounded
        // pre-touch prevents the first few terrain samples from racing chunk IO.
        for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
            for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) level.getChunk(cx, cz);
        }
    }

    private enum Heading {
        N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0), NW(-1, -1);
        private final double x, z;
        Heading(double x, double z) { this.x = x; this.z = z; }
        static Heading parse(String value) { try { return value == null ? null : valueOf(value.toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException ignored) { return null; } }
        static Heading fromYaw(float yaw) {
            double radians = Math.toRadians(yaw);
            double x = Math.sin(radians), z = Math.cos(radians);
            Heading best = S;
            double score = -Double.MAX_VALUE;
            for (Heading candidate : values()) {
                double length = Math.hypot(candidate.x, candidate.z);
                double next = (x * candidate.x + z * candidate.z) / length;
                if (next > score) { score = next; best = candidate; }
            }
            return best;
        }
    }
}
