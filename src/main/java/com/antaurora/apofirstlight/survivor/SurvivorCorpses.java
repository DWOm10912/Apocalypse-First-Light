package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.registry.AflEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corpses, server side (docs/gameplay/survivor_death_succession_v0.md 6): creation at the death, the death's items into
 * the body, the body's entity kept in the world, search access, and the stale-player-file check.
 *
 * <p>Item rule (announced on the mode screen and in the doc): everything the survivor carried goes into the body, the
 * inventory (36), armour, off hand and every Curios slot, cosmetic ones too, whatever keepInventory or the Curios
 * keep-rules say; a successor starts with nothing. Curse of vanishing items vanish as in vanilla. The ender chest is
 * not touched (see 8).
 */
public final class SurvivorCorpses {
    private static final int RECONCILE_TICKS = 20;
    /** Player -> the corpse of the death being processed: filled at the death, read by the drops and the clone. */
    private static final Map<UUID, UUID> PENDING = new HashMap<>();
    private static int reconcileClock;

    private SurvivorCorpses() {
    }

    /**
     * At the death (Survivors.recordDeath): the body record and its entity at once. A body that can fall (Ragdoll V1,
     * SurvivorRagdolls#canFall) starts where the survivor stood and falls; any other lies down at the resting place.
     */
    static void create(ServerPlayer player, SurvivorRecord record, @Nullable net.minecraft.world.damagesource.DamageSource source) {
        ServerLevel level = player.serverLevel();
        SurvivorWorldData data = SurvivorWorldData.get(player.server);
        // a thrown living body (LiveRagdolls) falls on as the corpse, with the death's push on top
        com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody thrown = LiveRagdolls.take(player);
        boolean falls = thrown != null || SurvivorRagdolls.canFall(player);
        Vec3 at = falls ? player.position() : restingPlace(level, player);
        SurvivorCorpseRecord corpse = new SurvivorCorpseRecord(UUID.randomUUID(), player.getUUID(), record.id,
                record.fullName(), record.lifeIndex, level.dimension().location().toString(), at.x, at.y, at.z,
                player.yBodyRot, record.deathGameTime, record.deathDayTime);
        corpse.skin = record.skin;
        corpse.arms = record.arms;
        data.addCorpse(corpse);
        record.corpseId = corpse.id;
        PENDING.put(player.getUUID(), corpse.id);
        SurvivorCorpse entity = spawn(level, corpse);
        if (falls && entity != null) {
            if (thrown != null && source != null)
                SurvivorRagdolls.push(thrown, source, com.antaurora.apofirstlight.vehicle.VehicleImpact.thrown(player));
            entity.fall(thrown != null ? thrown : SurvivorRagdolls.start(player, source));
        }
    }

    /**
     * Lowest priority, also when cancelled: vanilla's drops and the stacks Curios added go into the body, nothing is
     * spawned in the world. Whatever is still on the player (keepInventory, Curios keep-rules, cosmetics) follows.
     */
    static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SurvivorCorpseRecord corpse = pendingCorpse(player);
        if (corpse == null) return;
        List<ItemStack> taken = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) taken.add(stack.copy());
            drop.setItem(ItemStack.EMPTY);
        }
        event.getDrops().clear();
        event.setCanceled(true);
        sweepInventory(player.getInventory(), taken);
        sweepCurios(player, taken);
        sweepPockets(player, taken);
        store(player.serverLevel(), corpse, taken);
        Survivors.markFlush();
    }

    /** Lowest priority, after Curios copied its slots: anything that still came over to the new body goes to the corpse. */
    static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) return;
        SurvivorCorpseRecord corpse = pendingCorpse(player);
        PENDING.remove(player.getUUID());
        if (corpse == null) return;
        List<ItemStack> left = new ArrayList<>();
        sweepInventory(player.getInventory(), left);
        sweepCurios(player, left);
        sweepPockets(player, left);
        if (left.isEmpty()) return;
        ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] {} stacks reached {}'s new body; moved to corpse {}", left.size(),
                player.getGameProfile().getName(), corpse.id);
        store(player.serverLevel(), corpse, left);
        Survivors.markFlush();
    }

    static void forget(UUID player) {
        PENDING.remove(player);
    }

    @Nullable
    private static SurvivorCorpseRecord pendingCorpse(ServerPlayer player) {
        UUID id = PENDING.get(player.getUUID());
        return id == null ? null : SurvivorWorldData.get(player.server).corpse(id);
    }

    private static void store(ServerLevel level, SurvivorCorpseRecord corpse, List<ItemStack> stacks) {
        List<ItemStack> overflow = corpse.addItems(stacks);
        for (ItemStack stack : overflow) {
            // only with more than 54 stacks (a mod adding many Curios slots): next to the body, never destroyed
            Containers.dropItemStack(level, corpse.x, corpse.y + 0.2, corpse.z, stack);
        }
        if (!overflow.isEmpty()) {
            ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] corpse {} is full; {} stacks dropped beside it", corpse.id,
                    overflow.size());
        }
    }

    /** Takes every stack out of the inventory (main, armour, off hand); curse-of-vanishing items are destroyed. */
    private static void sweepInventory(Inventory inventory, List<ItemStack> out) {
        for (List<ItemStack> list : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (stack.isEmpty()) continue;
                list.set(i, ItemStack.EMPTY);
                if (!EnchantmentHelper.hasVanishingCurse(stack)) out.add(stack);
            }
        }
        inventory.setChanged();
    }

    /** Takes every Curios stack, cosmetic slots included. */
    private static void sweepCurios(ServerPlayer player, List<ItemStack> out) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (ICurioStacksHandler slots : handler.getCurios().values()) {
                take(slots.getStacks(), out);
                take(slots.getCosmeticStacks(), out);
            }
        });
    }

    /** Takes everything out of the pockets (Grid Inventory V0; the rig's and backpack's contents travel inside them). */
    private static void sweepPockets(ServerPlayer player, List<ItemStack> out) {
        var pockets = com.antaurora.apofirstlight.inventory.grid.Pockets.of(player);
        if (pockets == null || pockets.items().isEmpty()) return;
        var storage = com.antaurora.apofirstlight.inventory.grid.GridStorage.load(
                com.antaurora.apofirstlight.inventory.grid.Pockets.LAYOUT, pockets.items(), false);
        for (ItemStack stack : storage.stacks()) {
            if (!stack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(stack)) out.add(stack);
        }
        pockets.set(new net.minecraft.nbt.ListTag());
        com.antaurora.apofirstlight.inventory.grid.GridPackets.sendPockets(player);
    }

    private static void take(IDynamicStackHandler handler, List<ItemStack> out) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            handler.setStackInSlot(i, ItemStack.EMPTY);
            if (!EnchantmentHelper.hasVanishingCurse(stack)) out.add(stack);
        }
    }

    /**
     * Where the body lies: where the survivor stood when on the ground (the true surface, slopes included), otherwise
     * dropped onto the first solid surface below (falls, water, ladders), or on top of the column after a void death.
     */
    static Vec3 restingPlace(ServerLevel level, ServerPlayer player) {
        Vec3 at = player.position();
        if (player.onGround() && at.y >= level.getMinBuildHeight()) return at;
        return ground(level, at);
    }

    /** A body let go at {@code at}: on the first solid surface below, or on top of the column when below the world. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        int min = level.getMinBuildHeight();
        if (at.y < min) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
            return new Vec3(at.x, Math.max(min + 1, top), at.z);
        }
        return surfaceBelow(level, at);
    }

    /** The first solid surface at or below a point (64 blocks at most), or the point itself (inside a block, none found). */
    static Vec3 surfaceBelow(ServerLevel level, Vec3 at) {
        int min = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = BlockPos.containing(at).mutable();
        for (int i = 0; i < 64 && pos.getY() >= min; i++, pos.move(Direction.DOWN)) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            if (top <= at.y + 1.0E-3) return new Vec3(at.x, top, at.z);
            if (i == 0) return at;   // inside a block (suffocation): stay
        }
        return at;
    }

    /**
     * Server tick: puts missing corpse entities back into loaded chunks (one UUID lookup per corpse per second). A body
     * that stopped in the air when its chunk unloaded (record unsettled) is put on the ground first.
     */
    static void tick(MinecraftServer server) {
        if (++reconcileClock < RECONCILE_TICKS) return;
        reconcileClock = 0;
        SurvivorWorldData data = SurvivorWorldData.get(server);
        if (data.corpses.isEmpty()) return;
        for (SurvivorCorpseRecord corpse : data.corpses.values()) {
            ServerLevel level = level(server, corpse.dimension);
            if (level == null) continue;
            long chunk = ChunkPos.asLong(Mth.floor(corpse.x) >> 4, Mth.floor(corpse.z) >> 4);
            if (!level.areEntitiesLoaded(chunk) || level.getEntity(corpse.id) != null) continue;
            if (corpse.unsettled) {
                Vec3 g = ground(level, new Vec3(corpse.x, corpse.y, corpse.z));
                corpse.rest(g.x, g.y, g.z, null);
                Survivors.markFlush();
            }
            spawn(level, corpse);
        }
    }

    @Nullable
    static SurvivorCorpse spawnForTest(ServerLevel level, SurvivorCorpseRecord corpse) {
        return spawn(level, corpse);
    }

    @Nullable
    private static SurvivorCorpse spawn(ServerLevel level, SurvivorCorpseRecord corpse) {
        SurvivorCorpse entity = AflEntities.SURVIVOR_CORPSE.get().create(level);
        if (entity == null) return null;
        entity.setup(corpse);
        entity.setSearched(container(corpse).aflSearchState().isComplete(container(corpse)));
        return level.addFreshEntity(entity) ? entity : null;
    }

    /** The search finished: the body's prompt changes from search to view. */
    static void refreshEntity(ServerLevel level, SurvivorCorpseRecord corpse) {
        if (level.getEntity(corpse.id) instanceof SurvivorCorpse entity) entity.setSearched(true);
    }

    static SurvivorCorpseContainer container(SurvivorCorpseRecord corpse) {
        if (corpse.container == null) corpse.container = new SurvivorCorpseContainer(corpse);
        return corpse.container;
    }

    /** A click on a body: the server checks the record, reach and the viewer, then opens the shared search. */
    static boolean open(ServerPlayer player, SurvivorCorpse entity) {
        if (!player.isAlive() || player.isSpectator()) return false;
        SurvivorCorpseRecord corpse = SurvivorWorldData.get(player.server).corpse(entity.getUUID());
        if (corpse == null) {
            entity.discard();   // a body without a record (cannot normally happen): nothing to search
            return false;
        }
        SurvivorCorpseContainer container = container(corpse);
        if (!container.stillValid(player)) return false;
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> AflContainerSearch.createMenu(id, inventory, container),
                Component.translatable("container.apocalypse_firstlight.survivor_corpse", corpse.survivorName)));
        return true;
    }

    /**
     * Login: a player file that still belongs to a survivor the world records as dead is older than the death (the
     * server stopped between writing the survivor data and the player file). Its items are already in the corpse, so
     * the stale copies are removed and the player is put back into the dead state; the normal death screen and
     * succession follow. Returns true when it acted.
     */
    static boolean reconcileStalePlayer(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.hasUUID(Survivors.PLAYER_SURVIVOR_KEY) || player.isDeadOrDying()) return false;
        SurvivorLineage lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        if (lineage == null) return false;
        SurvivorRecord record = lineage.find(persistent.getUUID(Survivors.PLAYER_SURVIVOR_KEY));
        if (record == null || record.state != SurvivorRecord.State.DEAD) return false;
        List<ItemStack> stale = new ArrayList<>();
        sweepInventory(player.getInventory(), stale);
        sweepCurios(player, stale);
        sweepPockets(player, stale);
        player.setHealth(0.0F);
        ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] {}'s player file predates the death of survivor #{} {}; "
                        + "removed {} stale stacks (they are in corpse {}) and restored the dead state",
                player.getGameProfile().getName(), record.lifeIndex, record.fullName(), stale.size(), record.corpseId);
        Survivors.markFlush();
        return true;
    }

    @Nullable
    static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** For the status command: the number of bodies and stacks in this world. */
    static String describe(MinecraftServer server) {
        SurvivorWorldData data = SurvivorWorldData.get(server);
        int stacks = 0;
        for (SurvivorCorpseRecord corpse : data.corpses.values()) stacks += corpse.itemCount();
        double[] cost = SurvivorRagdolls.cost();
        return "corpses=" + data.corpses.size() + " stacks=" + stacks + " falling=" + SurvivorRagdolls.active()
                + String.format(java.util.Locale.ROOT, " ragdoll_ms_per_tick=%.2f max=%.2f over %d ticks", cost[1], cost[2], (int) cost[0]);
    }
}
