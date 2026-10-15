package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.Instant;

/** {@code /afl worldgen lock}: the overworld's generation version lock (docs/worldgen/worldgen_version_lock_v1.md). Read-only. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldgenLockCommand {
    private WorldgenLockCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("worldgen")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("lock").executes(c -> status(c.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        WorldgenLockEvents.Status s = WorldgenLockEvents.last;
        if (s == null || s.server() != source.getServer()) {
            source.sendSuccess(() -> Component.literal("AFL worldgen lock: this overworld has no AFL Terrain V2 plan; not locked."), false);
            return 0;
        }
        WorldgenLockRecord r = s.record();
        StringBuilder sb = new StringBuilder("AFL worldgen lock: ").append(s.verdict().action()).append('\n');
        sb.append("origin ").append(r.origin()).append(" by ").append(r.createdBy()).append(", ")
                .append(Instant.ofEpochMilli(r.createdMillis())).append('\n');
        sb.append("profile ").append(r.fingerprint().profile()).append(", fingerprint ").append(r.fingerprint().id())
                .append(", plan ").append(r.fingerprint().planDigest()).append('\n');
        r.fingerprint().systems().forEach((k, v) -> sb.append("  ").append(k).append(" = ").append(v).append('\n'));
        r.fingerprint().probes().forEach((k, v) -> sb.append("  probe ").append(k).append(" = ").append(v).append('\n'));
        var on = WorldgenGates.on(r.fingerprint());
        sb.append("gated systems on: ").append(on.isEmpty() ? "none" : String.join(", ", on)).append('\n');
        String findings = WorldgenLockPolicy.describe(s.verdict());
        if (!findings.isEmpty()) sb.append("at load:\n").append(findings);
        sb.append("history: ").append(r.history().size()).append(" entr").append(r.history().size() == 1 ? "y" : "ies");
        if (!r.history().isEmpty()) {
            var e = r.history().get(r.history().size() - 1);
            sb.append(", latest ").append(e.event()).append(" ").append(Instant.ofEpochMilli(e.timeMillis()));
        }
        String text = sb.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
