package com.antaurora.apofirstlight.dev.wiring;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.worldgen.structure.AflTemplatePlacer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.stream.Stream;

/**
 * {@code /afl dev template} (Building Wiring V1 phase 6, docs/gameplay/building_wiring_v1.md): places a building template
 * the way AFL places buildings, development runs only, op level 2.
 * <pre>
 *   place &lt;template&gt; &lt;pos&gt; [rotation] [mirror]       with the AFL processor (worldgen/structure/AflTemplatePlacer)
 *   place_raw &lt;template&gt; &lt;pos&gt; [rotation] [mirror]   without it, as vanilla /place template places
 *   info &lt;pos&gt;                                     a panel's template wiring still to write, its packed facing
 * </pre>
 * {@code <template>}: an export's building ID (run/afl_authoring_exports/&lt;id&gt;.nbt, vanilla's /place cannot read
 * those) or a resource ID. The template's origin corner goes to {@code <pos>}; it is turned and mirrored about it. The
 * whole volume is written, air included.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class TemplateCommands {
    private TemplateCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("template").requires(s -> !FMLEnvironment.production && s.hasPermission(2));
        c.then(Commands.literal("place").then(placement(false)));
        c.then(Commands.literal("place_raw").then(placement(true)));
        c.then(Commands.literal("info").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(TemplateCommands::info)));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> placement(boolean raw) {
        return Commands.argument("template", ResourceLocationArgument.id()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(exports(), b))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(x -> place(x, raw, Rotation.NONE, Mirror.NONE))
                        .then(Commands.argument("rotation", TemplateRotationArgument.templateRotation())
                                .executes(x -> place(x, raw, TemplateRotationArgument.getRotation(x, "rotation"), Mirror.NONE))
                                .then(Commands.argument("mirror", TemplateMirrorArgument.templateMirror())
                                        .executes(x -> place(x, raw, TemplateRotationArgument.getRotation(x, "rotation"), TemplateMirrorArgument.getMirror(x, "mirror"))))));
    }

    /** The building IDs exported in this game directory. */
    private static Stream<String> exports() {
        try (Stream<java.nio.file.Path> files = Files.list(FMLPaths.GAMEDIR.get().resolve(AflTemplatePlacer.EXPORTS))) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".nbt")).map(n -> n.substring(0, n.length() - 4)).toList().stream();
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    private static int place(CommandContext<CommandSourceStack> x, boolean raw, Rotation rotation, Mirror mirror) throws CommandSyntaxException {
        ServerLevel level = x.getSource().getLevel();
        ResourceLocation name = ResourceLocationArgument.getId(x, "template");
        BlockPos at = BlockPosArgument.getLoadedBlockPos(x, "pos");
        boolean bare = !x.getInput().contains(name.getNamespace() + ":");   // typed without a namespace: an export first
        StructureTemplate t;
        try {
            t = AflTemplatePlacer.load(level, name, bare).orElse(null);
        } catch (Exception e) {
            x.getSource().sendFailure(Component.literal("Template: cannot read " + name + ": " + e.getMessage()));
            return 0;
        }
        if (t == null) {
            x.getSource().sendFailure(Component.literal("Template: no export or resource " + name));
            return 0;
        }
        if (!AflTemplatePlacer.place(level, t, at, rotation, mirror, raw)) {
            x.getSource().sendFailure(Component.literal("Template: " + name + " placed nothing"));
            return 0;
        }
        var box = t.getBoundingBox(AflTemplatePlacer.settings(rotation, mirror, raw), at);
        x.getSource().sendSuccess(() -> Component.literal("Template: " + name + (raw ? " placed raw (no AFL processor)" : " placed") + " at " + at.toShortString()
                + ", " + rotation.getSerializedName() + " / " + mirror.getSerializedName() + ", box " + box.minX() + " " + box.minY() + " " + box.minZ()
                + " .. " + box.maxX() + " " + box.maxY() + " " + box.maxZ() + "; panels write their wiring on their next tick"), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> x) throws CommandSyntaxException {
        BlockPos p = BlockPosArgument.getLoadedBlockPos(x, "pos");
        if (!(x.getSource().getLevel().getBlockEntity(p) instanceof DistributionPanelBlockEntity panel)) {
            x.getSource().sendFailure(Component.literal("Template: no distribution panel at " + p.toShortString()));
            return 0;
        }
        int left = panel.packedLeft();
        x.getSource().sendSuccess(() -> Component.literal("Template: panel " + p.toShortString() + " " + panel.mode().name().toLowerCase(java.util.Locale.ROOT)
                + (left == 0 ? ", no template wiring left" : ", " + left + " template nodes left to write, packed facing " + panel.packedFacing())), false);
        return 1;
    }
}
