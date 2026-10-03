package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only client mirror for future consumers. No HUD and no local inventory scan. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientWeightState {
    private static WeightPackets.Policy policy;
    private static EncumbranceState state;
    private static ItemMassData.Snapshot data;
    private static long sequence = -1;
    private ClientWeightState() {}
    public static EncumbranceState state() { return state; }
    public static WeightPackets.Policy policy() { return policy; }
    /** Server-resolved table for hovered-stack presentation only, never the server's static snapshot. */
    public static ItemMassData.Snapshot data() { return data; }
    static void accept(WeightPackets.Policy incoming) {
        if (policy != null && incoming.revision() < policy.revision()) return;
        if (state != null && incoming.revision() < state.dataRevision()) return;
        policy = incoming;
        if (state != null && state.dataRevision() != incoming.revision()) state = null;
        if (data != null && data.revision() != incoming.revision()) data = null;
    }
    static void accept(WeightPackets.State incoming) {
        if (incoming.sequence() <= sequence) return;
        if (policy != null && incoming.state().dataRevision() < policy.revision()) return;
        if (state != null && incoming.state().dataRevision() < state.dataRevision()) return;
        sequence = incoming.sequence(); state = incoming.state();
        if (data != null && data.revision() < state.dataRevision()) data = null;
    }
    static void accept(WeightPackets.Data incoming) {
        var snapshot = incoming.snapshot();
        if (policy != null && snapshot.revision() < policy.revision()) return;
        if (state != null && snapshot.revision() < state.dataRevision()) return;
        if (data != null && snapshot.revision() < data.revision()) return;
        accept(new WeightPackets.Policy(snapshot.revision(), snapshot.policy()));
        data = snapshot;
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        policy = null; state = null; data = null; sequence = -1;
    }
}
