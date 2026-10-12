package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.TreeMap;

/**
 * {@code /afl dev mesh_tree forest} (docs/rendering/mesh_trees_p1_forest.md, H): read-only diagnostics of the natural
 * mesh forest. They change no block and work in any development world (the forest itself grows only in a forest test
 * world, see ForestGate).
 * <pre>
 *   forest status                             gate, prewarm, worldgen counters (chunks, trees, refusals, cells, cost)
 *   forest here                               the plan round you: biome, stand, trees of the 3 x 3 chunks, standing or not
 *   forest find <scenario>                    the nearest test spot of a scenario (ForestPlan.SCENARIOS: open_oak,
 *                                             white_pine, mixed, red_oak, hickory, red_maple, hemlock, mesophytic,
 *                                             riparian, swamp; plan search, out to 6 km)
 * </pre>
 */
final class ForestCommands {
    private ForestCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        var forest = Commands.literal("forest");
        forest.then(Commands.literal("status").executes(ForestCommands::status));
        forest.then(Commands.literal("here").executes(ForestCommands::here));
        var find = Commands.literal("find");
        for (int k = 0; k < ForestPlan.SCENARIOS.length; k++) {
            int scenario = k;
            find.then(Commands.literal(ForestPlan.SCENARIOS[k].name()).executes(x -> find(x, scenario)));
        }
        forest.then(find);
        return forest;
    }

    private static void say(CommandContext<CommandSourceStack> x, String s) {
        x.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static int status(CommandContext<CommandSourceStack> x) {
        boolean on = ForestGate.enabled();
        say(x, "Mesh forest: " + (on ? "ON" : "off") + " (" + ForestGate.reason() + "); layouts " + ForestGate.prewarm
                + "; " + TreeShape.cached() + " layouts cached");
        long chunks = ForestFeature.CHUNKS.sum(), planned = ForestFeature.PLANNED.sum(), placed = ForestFeature.PLACED.sum();
        say(x, String.format("  worldgen this session: %d chunks decorated, %d trees planned, %d standing (%.1f %%), %.1f us per chunk%s",
                chunks, planned, placed, planned == 0 ? 0 : 100.0 * placed / planned, chunks == 0 ? 0 : ForestFeature.NANOS.sum() / 1000.0 / chunks,
                ForestFeature.NO_PLAN.sum() > 0 ? "; " + ForestFeature.NO_PLAN.sum() + " chunks without a Terrain V2 plan" : ""));
        say(x, "  refused: " + (ForestFeature.REFUSED.isEmpty() ? "none" : ForestFeature.refusals().toString()));
        say(x, String.format("  cells: %d written, %d shared with another tree, %d leaf cells lost to wood, %d crown cells left out (something else there)",
                ForestFeature.CELLS.sum(), ForestFeature.SHARED.sum(), ForestFeature.LOST.sum(), ForestFeature.SKIPPED.sum()));
        say(x, "  server index: " + TreeIndex.SERVER.size() + " roots in " + TreeIndex.SERVER.chunks() + " loaded chunks (natural and sample trees)");
        return 1;
    }

    private static int here(CommandContext<CommandSourceStack> x) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ForestPlan plan = ForestGate.plan(x.getSource().getServer());
        if (plan == null) { x.getSource().sendFailure(Component.literal("Mesh forest: no Terrain V2 plan for this world's seed")); return 0; }
        int px = player.getBlockX(), pz = player.getBlockZ(), cx = px >> 4, cz = pz >> 4;
        int b = plan.biome(px, pz);
        var st = ForestPlan.stand(b);
        say(x, "Mesh forest " + (ForestGate.enabled() ? "ON" : "off (" + ForestGate.reason() + ")") + "; here " + EcologyBiomes.IDS[b] + ": "
                + (st != null ? st.name() + String.format(", target %.2f trees per chunk, mix %s", st.target(), mixText(st))
                : ForestPlan.VANILLA_TREES[b] ? "keeps its vanilla trees" : "no trees"));
        int planned = 0, standing = 0;
        var lines = new ArrayList<String>();
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            var roots = TreeIndex.SERVER.chunk(cx + dx, cz + dz);
            for (var t : plan.trees(cx + dx, cz + dz)) {
                planned++;
                boolean found = false;
                for (var r : roots) if (r.x == t.x() && r.z == t.z()) { found = true; break; }
                if (found) standing++;
                if (lines.size() < 6) lines.add(String.format("%s v%d turn %d at %d %d%s", t.species().id, t.variant(), t.turns(), t.x(), t.z(), found ? "" : " (not standing)"));
            }
        }
        say(x, "  3 x 3 chunks: " + planned + " trees planned, " + standing + " standing (server index)" + (lines.isEmpty() ? "" : ": " + String.join("; ", lines)));
        var why = new TreeMap<String, Integer>();
        String[] w = new String[1];
        for (int gz = cz * 4; gz < cz * 4 + 4; gz++) for (int gx = cx * 4; gx < cx * 4 + 4; gx++) {
            w[0] = null;
            why.merge(plan.evaluate(gx, gz, w) != null ? (plan.standing(gx, gz) != null ? "stands" : "too close to a stronger one") : w[0], 1, Integer::sum);
        }
        say(x, "  this chunk's 16 candidates: " + why);
        return 1;
    }

    /** "red_oak 30 %, red_maple 25 %, ..." in falling order. */
    static String mixText(ForestPlan.Stand st) {
        var parts = new ArrayList<String>();
        var order = new ArrayList<>(java.util.List.of(com.antaurora.apofirstlight.tree.TreeForms.Species.values()));
        order.sort((a, b) -> Double.compare(st.share(b), st.share(a)));
        for (var s : order) if (st.share(s) > 0) parts.add(String.format("%s %.0f %%", s.id, 100 * st.share(s)));
        return String.join(", ", parts);
    }

    private static int find(CommandContext<CommandSourceStack> x, int scenario) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        ForestPlan plan = ForestGate.plan(x.getSource().getServer());
        if (plan == null) { x.getSource().sendFailure(Component.literal("Mesh forest: no Terrain V2 plan for this world's seed")); return 0; }
        int[] p = plan.find(scenario, player.getBlockX(), player.getBlockZ(), 190);
        if (p == null) { x.getSource().sendFailure(Component.literal("Mesh forest: no " + ForestPlan.SCENARIOS[scenario].name() + " spot within 6 km")); return 0; }
        int y = plan.top(p[0], p[1]);
        say(x, String.format("Mesh forest %s: %d %d (planned ground y %d, %.0f m away): /tp @s %d %d %d", ForestPlan.SCENARIOS[scenario].name(), p[0], p[1], y,
                Math.hypot(p[0] - player.getX(), p[1] - player.getZ()), p[0], y + 2, p[1]));
        return 1;
    }
}
