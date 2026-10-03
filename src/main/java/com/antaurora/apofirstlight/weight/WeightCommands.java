package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Operator diagnostics, including another player's open-menu sources without closing their GUI. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WeightCommands {
    private WeightCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aflweight").requires(s -> s.hasPermission(2))
                .executes(c -> summary(c.getSource(), c.getSource().getPlayerOrException(), false))
                .then(Commands.literal("breakdown").executes(c -> summary(c.getSource(), c.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("held").executes(c -> held(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("player").then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> summary(c.getSource(), EntityArgument.getPlayer(c, "target"), true))
                        .then(Commands.literal("held").executes(c -> held(c.getSource(), EntityArgument.getPlayer(c, "target"))))))
                .then(Commands.literal("coverage").executes(c -> coverage(c.getSource(), 1, false))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> coverage(c.getSource(), IntegerArgumentType.getInteger(c, "page"), false)))
                        .then(Commands.literal("missing").executes(c -> coverage(c.getSource(), 1, true))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> coverage(c.getSource(), IntegerArgumentType.getInteger(c, "page"), true))))));
    }
    private static void say(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
    private static int summary(CommandSourceStack source, ServerPlayer player, boolean detail) {
        var s = PlayerWeightRuntime.state(player);
        if (s == null) { say(source, "Weight pending: wait for server tick END."); return 0; }
        say(source, String.format(Locale.ROOT,
                "%s: mass %d g, load %d g / comfort %d g | ratio %.4f | severity %.4f | %s | speed x%.3f jump x%.3f sprintBlocked=%s"
                        + " | %s | penaltiesEnabled=%s | revision=%d (cached, fallback check <=10 ticks)",
                player.getGameProfile().getName(), s.carriedMassGrams(), s.loadGrams(), s.comfortCapacityGrams(), s.encumbranceRatio(),
                s.severity(), s.tier(), s.speedMultiplier(), s.jumpMultiplier(), s.sprintBlocked(), s.quality(),
                s.penaltiesEnabled(), s.dataRevision()));
        if (detail) {
            dump(source, PlayerWeightRuntime.breakdown(player));
            var carried = PlayerWeightRuntime.carried(player);
            // load = mass x source factor (armor) x item carry factor (bulky furniture)
            if (carried != null) carried.loadBySource().forEach((key, grams) -> say(source, "load " + key + " = " + grams + " g"));
        }
        return 1;
    }
    private static int held(CommandSourceStack source, ServerPlayer player) {
        var stack = player.getMainHandItem();
        say(source, "Held " + ForgeRegistries.ITEMS.getKey(stack.getItem()) + " x" + stack.getCount());
        dump(source, StackMassCalculator.mass(stack)); return 1;
    }
    private static void dump(CommandSourceStack source, MassResult result) {
        if (result == null) return;
        say(source, "total=" + result.grams() + " g; quality=" + result.quality());
        new TreeMap<>(result.breakdown()).forEach((key, value) -> say(source, key + " = " + value + " g"));
        // Issues are deduplicated; explicit estimates and missing-rule reasons remain distinguishable.
        for (String issue : new TreeSet<>(result.issues())) say(source, issue);
    }
    /** Every AFL item needs its own rule (items / native_guns); one covered only by a tag or the fallback is missing. */
    private static int coverage(CommandSourceStack source, int page, boolean missingOnly) {
        var data = ItemMassData.snapshot();
        List<String> rows = new ArrayList<>();
        int items = 0, fallback = 0, tagOnly = 0;
        for (var id : new TreeSet<>(ForgeRegistries.ITEMS.getKeys())) {
            if (!id.getNamespace().equals(ApocalypseFirstLight.MOD_ID)) continue;
            items++;
            var item = ForgeRegistries.ITEMS.getValue(id);
            String row;
            boolean explicit;
            if (item instanceof NativeGunItem gun) {
                explicit = data.guns().containsKey(gun.definition().id());
                if (!explicit) fallback++;
                row = id + " -> " + (explicit ? "EXPLICIT native_guns (receiver + magazine + actual ammo)" : "FALLBACK missing native_guns rule");
            } else {
                var unit = data.unit(id);
                explicit = unit.source().startsWith("item:");
                if (unit.source().startsWith("fallback:")) fallback++;
                else if (!explicit) tagOnly++;
                row = id + " -> " + unit.grams() + " g " + unit.source() + (unit.estimated() ? " ESTIMATED" : " EXACT");
            }
            if (!explicit) row += " MISSING_EXPLICIT";
            if (!missingOnly || !explicit) rows.add(row);
        }
        int pages = Math.max(1, (rows.size() + 19) / 20);
        say(source, "AFL mass coverage revision=" + data.revision() + " items=" + items + " missing_explicit=" + (fallback + tagOnly)
                + " (fallback=" + fallback + " tag_only=" + tagOnly + ")" + (missingOnly ? " [missing only]" : "")
                + " page=" + page + "/" + pages);
        if (page > pages) return 0;
        for (int i = (page - 1) * 20; i < Math.min(rows.size(), page * 20); i++) say(source, rows.get(i));
        return 1;
    }
}
