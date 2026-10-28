package com.antaurora.apofirstlight.client.cooking;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cooking.PailSlosh;
import com.antaurora.apofirstlight.cooking.WaterPailItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's side of the water pail (Cooking V1 K1a): every player's carried pail runs its own slosh model here
 * (cooking/PailSlosh, the same as the server's, which alone spills), for the surface client/cooking/WaterPailRenderer
 * draws; and the pail's prompt (client/WorldInteractionHint).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class PailClient {
    private record Surface(PailSlosh.Sim sim, double[] last) {}
    private static final Map<Integer, Surface> SURFACES = new HashMap<>();

    private PailClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            if (mc.level == null) SURFACES.clear();
            return;
        }
        var seen = new java.util.HashSet<Integer>();
        for (Player p : mc.level.players()) {
            ItemStack stack = p.getMainHandItem();
            long ml = stack.getItem() instanceof WaterPailItem ? WaterPailItem.water(stack) : 0;
            if (ml <= 0 || !p.isAlive() || p.isSpectator() || WaterPailItem.pourTicks(p, stack) >= 0) continue;
            seen.add(p.getId());
            Surface s = SURFACES.computeIfAbsent(p.getId(), k -> new Surface(new PailSlosh.Sim(), new double[2]));
            s.last[0] = s.sim.tx;
            s.last[1] = s.sim.tz;
            s.sim.step(PailSlosh.hand(p, 1), p.onGround(), (p.getY() - p.yo) * 20, ml);
        }
        SURFACES.keySet().retainAll(seen);
    }

    /** The surface gradient (world x, z; m/m) of this player's pail, between the last two ticks; zero when it has none. */
    public static double[] gradient(Player player, float partial) {
        Surface s = SURFACES.get(player.getId());
        if (s == null) return new double[2];
        return new double[]{Mth.lerp(partial, s.last[0], s.sim.tx), Mth.lerp(partial, s.last[1], s.sim.tz)};
    }

    /** The pail's prompt: scoop at water in reach while it has room; else pour it out while it holds water. */
    public static Component hint(Minecraft mc) {
        if (mc.player == null || mc.player.isSpectator()) return null;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof WaterPailItem) || WaterPailItem.pourTicks(mc.player, stack) >= 0) return null;
        long ml = WaterPailItem.water(stack);
        if (ml < WaterPailItem.CAPACITY && WaterPailItem.scoopAt(mc.level, mc.player) != null)
            return Component.translatable("hint.apocalypse_firstlight.water_pail.scoop");
        return ml > 0 ? Component.translatable("hint.apocalypse_firstlight.water_pail.pour") : null;
    }
}
