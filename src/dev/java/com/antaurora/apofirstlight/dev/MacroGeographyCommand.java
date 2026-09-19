package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Read-only, bounded metadata query. Never reads or generates chunks at the requested coordinates. */
public final class MacroGeographyCommand {
    private MacroGeographyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("macro_geography")
                .executes(context -> show(context.getSource(), (int) Math.floor(context.getSource().getPosition().x),
                        (int) Math.floor(context.getSource().getPosition().z)))
                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                        .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                .executes(context -> show(context.getSource(), IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "z")))));
    }

    private static int show(CommandSourceStack source, int x, int z) {
        var level = source.getLevel();
        if (!Level.OVERWORLD.equals(level.dimension())
                || !((RandomStateSeedAccess) (Object) level.getChunkSource().randomState()).apocalypse$hasMacroGeography()) {
            source.sendFailure(Component.literal("Macro Geography requires the AFL normal Overworld noise settings."));
            return 0;
        }
        var geography = MacroGeography.forSeed(level.getSeed());
        source.sendSuccess(() -> Component.literal("[AFL MACRO GEO] seed=" + level.getSeed()
                + " axes=" + geography.majorAxis() + "x" + geography.minorAxis()
                + " inlandSeas=" + geography.inlandSeaCount() + " bays=" + geography.bayCount()), false);
        source.sendSuccess(() -> Component.literal("(" + x + "," + z + ") " + geography.sample(x, z)), false);
        for (var island : geography.islands()) source.sendSuccess(() -> Component.literal(island.toString()), false);
        for (var crossing : geography.crossingCandidates()) source.sendSuccess(() -> Component.literal(crossing.toString()), false);
        return 1;
    }
}
