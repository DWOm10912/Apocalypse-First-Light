package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A pick mattock strike on a boulder (docs/gameplay/rock_r1_stones_boulders_design.md 3.4, 11.2): the server owns it, as a
 * shovel stroke (interaction/ShovelAction). The client asks ({@link PickPacket.Start}); the server checks the tool, the aim
 * (a boulder's rock within {@link #REACH} of the eyes and {@link #FLAT} of the feet) and the rate (one per
 * {@link #INTERVAL} ticks), runs the strike and at {@link #HIT_AT} lands it on the rock still aimed at: one strike on the
 * piece's count; at its count a piece with pieces of its own splits (BoulderWorld#split), the smallest break into field
 * stones (BoulderWorld#shatter). Each strike costs stamina, throws chips and is heard as far as the infected hear it.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class BoulderStrike {
    public static final int START = 0, HIT = 1, CANCEL = 2, END = 3;
    /** Ticks a strike lasts (1.1 s) and the tick the pick lands (0.6 s): tools/author-pick-actions-r1a.cjs. */
    public static final int DURATION = 22, HIT_AT = 12;
    /** One strike started per player per this many ticks at most (the server's rate limit, as the shovel's). */
    public static final int INTERVAL = 20;
    /** The rock a strike reaches (m, from the eyes; level from the feet), and how far the aim may wander during it (m). */
    public static final double REACH = 3.0, FLAT = 1.4, WANDER = 0.75;
    /** The strike's sound and the noise the infected hear, one radius (blocks): steel on rock carries. */
    private static final double RADIUS = 16;

    /** What a pick is aimed at: the rock point and the root of the rock there. */
    public record Aim(Vec3 at, BlockPos root, int node) {}

    private static final Map<UUID, Pending> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private static final class Pending {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final ItemStack tool;
        final int slot;
        final long start;
        final Aim aim;
        boolean hit;

        Pending(ServerPlayer p, Aim aim) {
            player = p;
            tool = p.getMainHandItem();
            slot = p.getInventory().selected;
            this.aim = aim;
            start = p.serverLevel().getGameTime();
        }
    }

    private BoulderStrike() {}

    public static boolean active(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    /**
     * The rock a player aims at within reach (either side: the client's index on a client), or null: the look's first hit,
     * a boulder block, and of the rocks filling that cell the one whose boxes hold the hit point.
     */
    public static Aim aim(Player p) {
        Level level = p.level();
        if (!BoulderBlocks.ready()) return null;
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (!BoulderBlocks.isBoulder(level.getBlockState(pos))) return null;
        Vec3 at = hit.getLocation();
        double dx = at.x - p.getX(), dz = at.z - p.getZ();
        if (dx * dx + dz * dz > FLAT * FLAT) return null;
        var found = new ArrayList<BoulderIndex.Root>(2);
        BoulderIndex.of(level).roots(pos.getX(), pos.getY(), pos.getZ(), found);
        Vec3 local = at.subtract(pos.getX(), pos.getY(), pos.getZ());
        BoulderIndex.Root best = null;
        for (var r : found) {
            var c = r.cellAt(pos.getX(), pos.getY(), pos.getZ());
            if (c == null) continue;
            if (best == null) best = r;
            for (AABB box : BoulderData.shape(r.node, r.turn, c).toAabbs())
                if (box.inflate(0.02).contains(local)) return new Aim(at, r.pos(), r.node.index);
        }
        return best == null ? null : new Aim(at, best.pos(), best.node.index);
    }

    public static boolean begin(ServerPlayer p) {
        if (active(p) || !p.isAlive() || p.isSpectator() || p.isSleeping() || p.containerMenu != p.inventoryMenu
                || !p.getMainHandItem().is(AflItems.PICK_MATTOCK.get())) return false;
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < INTERVAL) return false;
        Aim aim = aim(p);
        if (aim == null) return false;
        if (PlayerStamina.winded(p)) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.pick.winded"), true);
            return false;
        }
        LAST.put(p.getUUID(), now);
        Pending a = new Pending(p, aim);
        ACTIVE.put(p.getUUID(), a);
        PickPacket.send(p, a.id, START, aim.at());
        return true;
    }

    public static void cancel(ServerPlayer player, UUID id) {
        Pending a = ACTIVE.get(player.getUUID());
        if (a != null && a.id.equals(id) && !a.hit) {
            ACTIVE.remove(player.getUUID());
            PickPacket.send(player, a.id, CANCEL, a.aim.at());
        }
    }

    private static boolean valid(Pending a) {
        ServerPlayer p = a.player;
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.hasDisconnected() && p.containerMenu == p.inventoryMenu
                && p.getInventory().selected == a.slot && p.getMainHandItem() == a.tool && a.tool.is(AflItems.PICK_MATTOCK.get());
    }

    /** Public for a deterministic server probe; normal execution is the END server tick. */
    public static void advance(ServerPlayer p) {
        Pending a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        if (!valid(a)) {
            ACTIVE.remove(p.getUUID());
            PickPacket.send(p, a.id, CANCEL, a.aim.at());
            return;
        }
        long elapsed = p.serverLevel().getGameTime() - a.start;
        if (!a.hit && elapsed >= HIT_AT) {
            a.hit = true;
            Aim now = aim(p);
            // the aim rechecked: the same rock (it may have split or gone meanwhile), near where the strike began
            if (now != null && now.root().equals(a.aim.root()) && now.node() == a.aim.node() && now.at().distanceTo(a.aim.at()) <= WANDER)
                land(p, now);
            PickPacket.send(p, a.id, HIT, a.aim.at());
        }
        if (elapsed >= DURATION) {
            ACTIVE.remove(p.getUUID());
            PickPacket.send(p, a.id, END, a.aim.at());
        }
    }

    /** The pick lands: one strike on the piece, its effort, chips and sound; at the piece's count it splits or breaks. */
    private static void land(ServerPlayer p, Aim aim) {
        ServerLevel level = p.serverLevel();
        BoulderIndex.Root r = null;
        var found = new ArrayList<BoulderIndex.Root>(2);
        BoulderIndex.SERVER.roots(aim.root().getX(), aim.root().getY(), aim.root().getZ(), found);
        for (var f : found) if (f.pos().equals(aim.root())) r = f;
        if (r == null || !(level.getBlockEntity(aim.root()) instanceof BoulderBlocks.Hits hits)) return;
        PlayerStamina.spend(p, StaminaConfig.get().costs.pickStrike, false);
        Vec3 at = aim.at();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BoulderBlocks.cell().defaultBlockState()), at.x, at.y, at.z,
                10, 0.06, 0.06, 0.06, 0.12);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 3, 0.02, 0.02, 0.02, 0.2);
        // placeholder vanilla sounds (docs 11.2: the Firefly take is planned)
        if (hits.strike() < r.node.strikes) {
            RangedSound.playWithNoise(level, p, at, SoundEvents.STONE_HIT, RADIUS, 0.7F, NoiseType.INTERACTION, AflItems.PICK_MATTOCK.getId());
            return;
        }
        RangedSound.playWithNoise(level, p, at, SoundEvents.STONE_BREAK, RADIUS, r.node.leaf() ? 0.9F : 0.6F, NoiseType.INTERACTION,
                AflItems.PICK_MATTOCK.getId());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BoulderBlocks.cell().defaultBlockState()), at.x, at.y, at.z,
                40, 0.3, 0.3, 0.3, 0.15);
        if (r.node.leaf()) BoulderWorld.shatter(level, r, at, p.position());
        else BoulderWorld.split(level, r);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) for (Pending a : List.copyOf(ACTIVE.values())) advance(a.player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST.remove(e.getEntity().getUUID());
        Pending a = ACTIVE.remove(e.getEntity().getUUID());
        if (a != null) PickPacket.send(a.player, a.id, CANCEL, a.aim.at());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        ACTIVE.clear();
        LAST.clear();
    }
}
