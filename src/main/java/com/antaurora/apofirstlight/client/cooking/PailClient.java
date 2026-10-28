package com.antaurora.apofirstlight.client.cooking;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FireFx;
import com.antaurora.apofirstlight.cooking.PailHeat;
import com.antaurora.apofirstlight.cooking.PailSlosh;
import com.antaurora.apofirstlight.cooking.WaterPailItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 * draws; the steam off hot water (Cooking V1 K1b: on the stove, in anyone's hand, poured, set down on the ground); and the
 * pail's prompt (client/WorldInteractionHint).
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
        // hot water steams wherever the pail is (the stove's own: client BoxStoveFire)
        long now = mc.level.getGameTime();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (Player p : mc.level.players()) {
            ItemStack stack = p.getMainHandItem();
            if (!(stack.getItem() instanceof WaterPailItem) || p.isSpectator() || p.distanceToSqr(eye) > STEAM_RANGE * STEAM_RANGE) continue;
            long ml = WaterPailItem.water(stack);
            if (ml <= 0) continue;
            double t = PailHeat.temp(stack, now);
            if (WaterPailItem.pourTicks(p, stack) >= WaterPailItem.POUR_TIP) steam(mc.level, WaterPailItem.lip(p).add(0, -0.15, 0), t, false);
            else if (WaterPailItem.pourTicks(p, stack) < 0)
                steam(mc.level, PailSlosh.hand(p, 1).add(0, -PailSlosh.GRIP_OVER_RIM - PailSlosh.H + PailSlosh.level(ml), 0), t, false);
        }
        if (now % 2 == 0)
            for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(eye, eye).inflate(STEAM_RANGE),
                    e -> e.getItem().getItem() instanceof WaterPailItem && WaterPailItem.water(e.getItem()) > 0))
                steam(mc.level, item.position().add(0, 0.125 + PailSlosh.level(WaterPailItem.water(item.getItem())), 0), PailHeat.temp(item.getItem(), now), false);
    }

    private static final double STEAM_RANGE = 32;

    /**
     * Steam off a water surface centred at {@code at} (world) at {@code celsius}: wisps from 45 °C, thicker as it heats, a
     * steady plume while it boils over a fire ({@code heated}; off the heat a boil stops in moments, only steam stays):
     * wisps off the steam sheet (FireFx#steam), not the smoke's puffs.
     */
    public static void steam(ClientLevel level, Vec3 at, double celsius, boolean heated) {
        float heat = (float) Math.max(0, Math.min(1, (celsius - 45) / 55)), boil = heated ? (float) Math.max(0, Math.min(1, (celsius - 96) / 4)) : 0;
        RandomSource r = level.random;
        for (int n = boil > 0.5F ? 2 : 1; n > 0; n--) {
            if (heat <= 0 || r.nextFloat() > 0.2F + 0.6F * heat) return;
            double rad = PailSlosh.RT * 0.75 * Math.sqrt(r.nextDouble()), a = r.nextDouble() * Math.PI * 2;
            FireFx.steam(level, at.x + Math.cos(a) * rad, at.y + 0.005, at.z + Math.sin(a) * rad, 0.04F + 0.03F * boil,
                    0.12F + 0.25F * heat + 0.15F * boil, 18 + (int) (12 * boil), 0.025 + 0.03 * boil);
        }
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
