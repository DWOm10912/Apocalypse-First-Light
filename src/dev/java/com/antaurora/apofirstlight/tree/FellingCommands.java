package com.antaurora.apofirstlight.tree;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;

/**
 * {@code /afl dev mesh_tree felling} (docs/rendering/mesh_trees_p2_felling.md, O): read-only diagnostics of the
 * felling. Felling itself needs no command.
 * <pre>
 *   felling here     the tree you look at (or the nearest within 4 blocks): identity, cut, work done and needed,
 *                    chops left with the tool in hand, stage and notch side, index check; falling trees within 64 blocks
 *   felling status   counters (chops, ignored chops, fells, settled drops, item stacks, times), the ledger, falling trees loaded
 * </pre>
 */
final class FellingCommands {
    private FellingCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        var f = Commands.literal("felling");
        f.then(Commands.literal("here").executes(FellingCommands::here));
        f.then(Commands.literal("status").executes(FellingCommands::status));
        return f;
    }

    private static void say(CommandContext<CommandSourceStack> x, String s) {
        x.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static int here(CommandContext<CommandSourceStack> x) {
        if (!(x.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return 0;
        TreeIndex.Root r = null;
        HitResult hit = player.pick(6, 0, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) r = FellingServer.choppable(level, b.getBlockPos(), TreeIndex.SERVER);
        if (r == null) {
            double best = 16;
            int cx = player.getBlockX() >> 4, cz = player.getBlockZ() >> 4;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (var o : TreeIndex.SERVER.chunk(cx + dx, cz + dz)) {
                double d = Math.pow(o.x + 0.5 - player.getX(), 2) + Math.pow(o.z + 0.5 - player.getZ(), 2);
                if (d < best && Math.abs(o.y - player.getBlockY()) < 4) { best = d; r = o; }
            }
        }
        if (r == null) say(x, "Felling: no standing tree here (look at a trunk base, or stand within 4 blocks of one)");
        else {
            BlockPos root = new BlockPos(r.x, r.y, r.z);
            var state = level.getBlockState(root);
            var form = TreeForms.grow(r.layout.species, r.layout.variant);
            float cut = FellingRules.cutHeight(r.layout.species, r.x, r.y, r.z), rad = FellingRules.trunkRadius(form, cut);
            float need = FellingRules.work(r.layout.species, rad);
            Float done = FellingServer.journal(level).ledger.work.get(root.asLong());
            var tool = player.getMainHandItem();
            boolean axe = FellingServer.axe(tool);
            float power = axe ? FellingServer.power(tool) : 0;
            boolean indexed = state.getBlock() == TreeBlocks.root();
            say(x, String.format("Felling: %s variant %d turn %d, root %s; cut %.2f m up, radius %.2f m; work %.1f of %.1f%s; stage %s, notch %s",
                    r.layout.species.id, r.layout.variant, r.layout.turns, root.toShortString(), cut, rad, done == null ? 0 : done, need,
                    axe ? String.format(", %d chops left with this axe (power %.2f)", FellingRules.chops(need - (done == null ? 0 : done), power), power) : ", no axe in hand",
                    indexed ? String.valueOf(state.getValue(TreeBlocks.CHOP)) : "-", indexed ? state.getValue(TreeBlocks.CUT) * 45 + " deg" : "-"));
            say(x, "  index: root block " + (indexed ? "present" : "MISSING") + ", server index " + (TreeIndex.SERVER.chunk(r.x >> 4, r.z >> 4).length > 0 ? "has this chunk" : "empty here"));
        }
        var falls = level.getEntitiesOfClass(FallingTree.class, new AABB(player.blockPosition()).inflate(64));
        var lines = new ArrayList<String>();
        var ledger = FellingServer.journal(level).ledger;
        for (var e : falls) {
            long t = level.getGameTime() - e.start;
            var c = e.curve();
            String phase = t < 0 ? "pause" : t < c.contact() ? "falling" : "landed";
            lines.add(String.format("%s v%d at %s: %s, %.0f deg now, rest %.1f deg, toward %.0f deg, contact at %.2f s%s", e.species.id, e.variant,
                    e.root.toShortString(), phase, e.angle(0), e.rest, Math.toDegrees(e.direction), c.contact() / 20F, ledger.isSettled(e.getUUID()) ? ", SETTLED" : ""));
        }
        say(x, "  falling trees within 64 blocks: " + falls.size() + (lines.isEmpty() ? "" : "; " + String.join("; ", lines)));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> x) {
        ServerLevel level = x.getSource().getLevel();
        var ledger = FellingServer.journal(level).ledger;
        long chops = FellingServer.CHOPS.sum(), fells = FellingServer.FELLS.sum();
        int loaded = 0;
        for (var e : level.getAllEntities()) if (e instanceof FallingTree) loaded++;
        say(x, String.format("Felling (this session): %d chops counted, %d ignored (too soon), %d trees felled, %d falls settled, %d item stacks dropped, %d bodies hit; %.2f ms per chop, %.2f ms per fell (direction, rest angle, cells)",
                chops, FellingServer.IGNORED.sum(), fells, FellingServer.SETTLES.sum(), FellingServer.ITEMS.sum(), FellingServer.HITS.sum(),
                chops == 0 ? 0 : FellingServer.CHOP_NANOS.sum() / 1e6 / chops, fells == 0 ? 0 : FellingServer.FELL_NANOS.sum() / 1e6 / fells));
        say(x, String.format("  ledger: %d trees partly chopped, %d falls under way, %d settled falls remembered; %d falling trees loaded now; %s",
                ledger.work.size(), ledger.falling.size(), ledger.settled.size(), loaded,
                "felling allowed in every world (P4)"));
        return 1;
    }
}
