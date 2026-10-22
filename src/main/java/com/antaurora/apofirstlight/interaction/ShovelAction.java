package com.antaurora.apofirstlight.interaction;

import com.antaurora.apofirstlight.earthwork.BulkHolder;
import com.antaurora.apofirstlight.earthwork.BulkMaterials;
import com.antaurora.apofirstlight.earthwork.EarthGrid;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.item.ShovelItem;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import com.antaurora.apofirstlight.weight.PlayerWeightRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A shovel stroke (docs/worldgen/earthwork_a2.md 5, docs/gameplay/sandbox_v1_design.md 5): the server owns it. The client
 * asks for a mode ({@link com.antaurora.apofirstlight.network.ShovelPacket.Start}); the server checks the tool, the aim (loose ground within {@link #REACH})
 * and the rate (one start per {@link #INTERVAL} ticks), runs the stroke, and at its commit tick changes the ground and
 * the load in one go. Let go before the commit (client Cancel) and nothing changes.
 * <ul>
 *   <li>DIG: one scoop (Earthwork#scoop) into the room left on the blade; a full blade digs nothing.</li>
 *   <li>POUR: the whole load poured where it is aimed (Earthwork#pour); only what went down leaves the blade.</li>
 *   <li>SCRAPE (sneak): the ground there towards the player's feet: higher ground cut into the blade, lower ground
 *       raised from its load.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class ShovelAction {
    public static final int DIG = 0, POUR = 1, SCRAPE = 2;
    public static final int START = 0, COMMIT = 1, CANCEL = 2, END = 3;
    /** Ticks a stroke lasts and the tick it changes the ground, by mode (dig ~1.1 s, pour ~0.8 s, scrape 1 s). */
    public static final int[] DURATION = {22, 16, 20}, COMMIT_AT = {16, 9, 14};
    /** One stroke started per player per this many ticks at most (the server's rate limit). */
    public static final int INTERVAL = 20;
    /** The ground a stroke reaches (m, from the eyes), and how far the aim may wander during it (m). */
    public static final double REACH = 2.5, WANDER = 1.0;
    /** The ground a shovel works lies at most this far from the feet, level (m): the blade reaches no further. */
    public static final double FLAT = 1.2;
    /** The stroke's sound and the noise the infected hear, one radius (blocks) by mode. */
    private static final double[] RADIUS = {10, 8, 10};

    private static final Map<UUID, Pending> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private static final class Pending {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final ItemStack tool;
        final int slot, mode;
        final long start;
        final Vec3 target;
        boolean committed;

        Pending(ServerPlayer p, int mode, Vec3 target) {
            player = p;
            tool = p.getMainHandItem();
            slot = p.getInventory().selected;
            this.mode = mode;
            this.target = target;
            start = p.serverLevel().getGameTime();
        }
    }

    public static boolean active(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    /** The loose ground the player aims at within reach (the hit point on the skin), or null. */
    public static Vec3 target(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        if (!Earthwork.soil(p.level().getBlockState(hit.getBlockPos()).getBlock())) return null;
        return near(p, hit.getLocation()) ? hit.getLocation() : null;
    }

    /** Whether a ground point is within the blade's level reach of the player's feet (both sides). */
    public static boolean near(net.minecraft.world.entity.player.Player p, Vec3 at) {
        double dx = at.x - p.getX(), dz = at.z - p.getZ();
        return dx * dx + dz * dz <= FLAT * FLAT;
    }

    public static boolean begin(ServerPlayer p, int mode) {
        if (mode < DIG || mode > SCRAPE || active(p)) return false;
        if (!p.isAlive() || p.isSpectator() || p.isSleeping() || p.containerMenu != p.inventoryMenu
                || !p.getMainHandItem().is(AflItems.ROUND_POINT_SHOVEL.get())) return false;
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < INTERVAL) return false;
        Vec3 at = target(p);
        if (at == null) return false;
        if (PlayerStamina.winded(p)) {
            tell(p, "winded");
            return false;
        }
        BulkHolder load = ShovelItem.load(p.getMainHandItem());
        if (mode == DIG && load.room() <= 0) {
            tell(p, "full");
            return false;
        }
        if (mode == POUR && load.stored() <= 0) {
            tell(p, "empty");
            return false;
        }
        LAST.put(p.getUUID(), now);
        Pending a = new Pending(p, mode, at);
        ACTIVE.put(p.getUUID(), a);
        notify(a, START);
        return true;
    }

    public static void cancel(ServerPlayer player, UUID id) {
        Pending a = ACTIVE.get(player.getUUID());
        if (a != null && a.id.equals(id) && !a.committed) {
            ACTIVE.remove(player.getUUID());
            notify(a, CANCEL);
        }
    }

    private static void notify(Pending a, int phase) { AflNetwork.shovel(a.player, a.id, a.mode, phase, a.target); }

    private static void tell(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.shovel." + key), true);
    }

    private static boolean valid(Pending a) {
        ServerPlayer p = a.player;
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.hasDisconnected() && p.containerMenu == p.inventoryMenu
                && p.getInventory().selected == a.slot && p.getMainHandItem() == a.tool && a.tool.is(AflItems.ROUND_POINT_SHOVEL.get());
    }

    /** Public for a deterministic server probe; normal execution is the END server tick. */
    public static void advance(ServerPlayer p) {
        Pending a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        if (!valid(a)) {
            ACTIVE.remove(p.getUUID());
            notify(a, CANCEL);
            return;
        }
        long elapsed = p.serverLevel().getGameTime() - a.start;
        if (!a.committed && elapsed >= COMMIT_AT[a.mode]) {
            Vec3 at = target(p);
            // the aim rechecked: still loose ground within reach, near where the stroke began
            if (at == null || at.distanceTo(a.target) > WANDER) {
                ACTIVE.remove(p.getUUID());
                notify(a, CANCEL);
                return;
            }
            a.committed = true;
            commit(p, a.mode, at);
            notify(a, COMMIT);
        }
        if (elapsed >= DURATION[a.mode]) {
            ACTIVE.remove(p.getUUID());
            notify(a, END);
        }
    }

    /** The stroke's change: the ground and the blade's load together, then its effort, sound and noise. */
    private static void commit(ServerPlayer p, int mode, Vec3 at) {
        ServerLevel level = p.serverLevel();
        BulkMaterials.Table table = BulkMaterials.table();
        BulkHolder load = ShovelItem.load(p.getMainHandItem());
        long moved;
        int material;
        var costs = StaminaConfig.get().costs;
        double cost;
        switch (mode) {
            case DIG -> {
                Earthwork.Result r = Earthwork.scoop(level, at.x, at.z, (int) load.room());
                moved = keep(load, table, r);
                material = main(r, true);
                cost = costs.shovelDig;
                // a boulder's rock under the blade (Rock R1a): it says so (the pick strikes it, the shovel digs round it)
                if (moved <= 0) tell(p, load.room() <= 0 ? "full" : com.antaurora.apofirstlight.boulder.BoulderEarth.under(level,
                        net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z), (int) Math.round(at.y * com.antaurora.apofirstlight.earthwork.EarthGrid.Q)) ? "rock" : "hard");
            }
            case POUR -> {
                String m = load.material();
                material = m == null ? -1 : table.index(m);
                if (material < 0) { tell(p, "empty"); return; }
                Earthwork.Result r = Earthwork.pour(level, at.x, at.y, at.z, (int) load.stored(), material);
                moved = put(r);
                load.extract(moved, false);
                cost = costs.shovelPour;
                if (moved <= 0) tell(p, "blocked");
            }
            default -> {
                int feet = (int) Math.round(p.getY() * EarthGrid.Q);
                double s = Earthwork.surfaceAt(level, at.x, at.z);
                double ground = Double.isNaN(s) ? at.y : s;
                boolean cut = ground * EarthGrid.Q > feet;
                cost = costs.shovelScrape;
                if (cut) {
                    Earthwork.Result r = Earthwork.scrape(level, at.x, at.y, at.z, feet, true, (int) load.room(), -1);
                    moved = keep(load, table, r);
                    material = main(r, true);
                    if (moved <= 0) tell(p, load.room() <= 0 ? "full" : "level");
                } else {
                    String m = load.material();
                    if (m == null) { tell(p, "empty"); return; }
                    material = table.index(m);
                    Earthwork.Result r = Earthwork.scrape(level, at.x, at.y, at.z, feet, false, (int) load.stored(), material);
                    moved = put(r);
                    load.extract(moved, false);
                    if (moved <= 0) tell(p, "level");
                }
            }
        }
        if (moved <= 0) return;
        PlayerStamina.spend(p, cost, false);
        PlayerWeightRuntime.dirty(p);
        var block = table.get(material).block();
        var sound = block.getSoundType();
        var event = mode == POUR ? sound.getPlaceSound() : mode == DIG ? sound.getBreakSound() : sound.getHitSound();
        RangedSound.playWithNoise(level, p, at, event, RADIUS[mode], sound.getPitch() * 0.8F, NoiseType.INTERACTION,
                AflItems.ROUND_POINT_SHOVEL.getId());
    }

    /** The earth a cut took, onto the blade (two materials mix into backfill there); the quanta taken. */
    private static long keep(BulkHolder load, BulkMaterials.Table table, Earthwork.Result r) {
        long sum = 0;
        for (var e : r.taken().int2LongEntrySet()) {
            if (e.getLongValue() <= 0) continue;
            load.insert(table.get(e.getIntKey()).id(), e.getLongValue(), false);
            sum += e.getLongValue();
        }
        return sum;
    }

    /** The earth an edit put down (quanta). */
    private static long put(Earthwork.Result r) {
        long sum = 0;
        for (long q : r.taken().values()) if (q < 0) sum -= q;
        return sum;
    }

    /** The material an edit moved most of (taken: dug &gt; 0), the sound's; backfill when none. */
    private static int main(Earthwork.Result r, boolean dug) {
        int best = BulkMaterials.table().backfill;
        long most = 0;
        for (var e : r.taken().int2LongEntrySet()) {
            long q = dug ? e.getLongValue() : -e.getLongValue();
            if (q > most) { most = q; best = e.getIntKey(); }
        }
        return best;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) for (Pending a : List.copyOf(ACTIVE.values())) advance(a.player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST.remove(e.getEntity().getUUID());
        Pending a = ACTIVE.remove(e.getEntity().getUUID());
        if (a != null) notify(a, CANCEL);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        ACTIVE.clear();
        LAST.clear();
    }

    private ShovelAction() {}
}
