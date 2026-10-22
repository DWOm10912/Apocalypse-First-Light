package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.inventory.CarriedItems;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The workbench's crafting, server side (building hammer B0, docs/gameplay/building_hammer_b0.md; the page is
 * client/workbench/AflWorkbenchScreen). The recipes are data (WorkbenchRecipeData, data/<ns>/workbench_recipes/<category>/),
 * who knows which is WorkbenchKnowledge; only a known recipe is made. Crafting one item: the page holds the button for the recipe's time and then
 * asks the server, which checks the player stands at a bench, carries the tool and the materials, takes the materials
 * once from the body (hands, weapon positions, grids; the drawers and nearby chests come with the recipe rework) and
 * gives the result. Creative takes the materials too, like any bench.
 * <p>
 * Work at the bench (2026-10-21, docs/gameplay/afl_workbench_v1_design.md 17): a recipe with a {@code work} action is not
 * made on the page. One click closes the page and the player works at the bench (client/workbench/WorkbenchWork: the
 * action, the workpiece on the top, the HUD); the client says when it starts and stops ({@link #work}), the server keeps
 * who works where and tells the players around ({@link BuildingPackets.WorkOf}), so they see the same work; an item of such
 * a recipe is only made while its player works at that bench.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WorkbenchCrafting {
    /** Items that count for one material line (any mix of them), and how many are needed. */
    public record Material(List<ResourceLocation> items, int count) {}

    /**
     * One way to learn a recipe (WorkbenchRecipeData.UNLOCKS): {@code value} is the background id (background), the
     * recipe id (after) or the item id (book, blueprint), else null.
     */
    public record Unlock(String type, @Nullable String value) {}

    /**
     * One recipe (WorkbenchRecipeData). {@code category} is the page's category name (WorkbenchRecipeView.Category);
     * {@code work}: the player action played at the bench while it is made (player_actions/<work>.json), or null:
     * made on the page; {@code unlock}: any one of them teaches it.
     */
    public record Recipe(ResourceLocation id, String category, ResourceLocation result, int count, List<Material> materials,
                         @Nullable ResourceLocation tool, int ticks, @Nullable String work, List<Unlock> unlock) {}

    /**
     * A player working at a bench: the bench's master cell, the recipe, and for the players watching (who may not know the
     * recipe, and whose client has only the recipes they know) its loop action and time per item.
     */
    public record Working(BlockPos bench, ResourceLocation recipe, String action, int ticks) {}

    private static final double REACH_SQ = 5.5 * 5.5;
    /** The last finished item per player (game time): a client may not finish faster than 3/4 of the recipe's time. */
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** Who works at a bench now (#work). */
    private static final Map<UUID, Working> WORKING = new HashMap<>();
    /**
     * Sawing at the bench is heard this far (blocks), by players (client/workbench/WorkbenchWork plays each push and pull
     * in step with the action, fading out here) and by the infected (#workNoise, once a stroke): a hand saw is quieter than
     * an axe's chop (tree/FellingServer.CHOP_RADIUS 10).
     */
    public static final double SAW_RADIUS = 8;
    /** One saw stroke, push and pull (ticks): tools/author-hand-saw-actions.mjs, tool/hand_saw_rip's length. */
    private static final int STROKE_TICKS = 16;

    private WorkbenchCrafting() {}

    @Nullable
    public static Recipe find(ResourceLocation id) {
        return WorkbenchRecipeData.find(id);
    }

    @Nullable
    public static Item item(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    /** Server: one item of the recipe, from the page at the bench at {@code bench}. */
    public static void craft(ServerPlayer player, BlockPos bench, ResourceLocation id) {
        Recipe r = find(id);
        Item result = r == null ? null : item(r.result());
        if (r == null || result == null) return;
        if (!WorkbenchKnowledge.knows(player, r)) {
            refuse(player, "unknown");
            return;
        }
        var level = player.serverLevel();
        if (!level.isLoaded(bench) || !(level.getBlockState(bench).getBlock() instanceof Workbench)
                || player.distanceToSqr(Vec3.atCenterOf(bench)) > REACH_SQ) {
            refuse(player, "too_far");
            return;
        }
        long now = level.getGameTime();
        Working at = WORKING.get(player.getUUID());
        if (r.work() != null && (at == null || !at.bench().equals(bench) || !at.recipe().equals(r.id()))) return;   // not at work
        Long last = LAST.get(player.getUUID());
        if (last != null && now >= last && now - last < r.ticks() * 3L / 4) return;   // faster than the page can hold
        Item tool = item(r.tool());
        if (r.tool() != null && (tool == null || CarriedItems.count(player, tool) == 0)) {
            refuse(player, "no_tool", tool == null ? Component.literal(r.tool().toString()) : new ItemStack(tool).getHoverName());
            return;
        }
        // check every line first, then take: nothing is taken when anything is short
        for (Material m : r.materials()) {
            int have = 0;
            for (ResourceLocation option : m.items()) { Item it = item(option); if (it != null) have += CarriedItems.count(player, it); }
            if (have < m.count()) {
                refuse(player, "short");
                return;
            }
        }
        for (Material m : r.materials()) {
            int left = m.count();
            for (ResourceLocation option : m.items()) {
                Item it = item(option);
                if (it != null && left > 0) left -= CarriedItems.take(player, it, left);
            }
        }
        LAST.put(player.getUUID(), now);
        CarriedItems.give(player, new ItemStack(result, r.count()));
        level.playSound(null, bench, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 0.9F);
    }

    /**
     * Server: the player starts ({@code start}) or stops working at the bench on a work recipe. A start is checked as a
     * craft (the bench, the reach, the tool; the materials are checked per item); the players around are told either way.
     */
    public static void work(ServerPlayer player, BlockPos bench, ResourceLocation id, boolean start) {
        if (!start) {
            stopWork(player);
            return;
        }
        Recipe r = find(id);
        if (r == null || r.work() == null) return;
        if (!WorkbenchKnowledge.knows(player, r)) {
            refuse(player, "unknown");
            return;
        }
        var level = player.serverLevel();
        if (!level.isLoaded(bench) || !(level.getBlockState(bench).getBlock() instanceof Workbench)
                || player.distanceToSqr(Vec3.atCenterOf(bench)) > REACH_SQ) {
            refuse(player, "too_far");
            return;
        }
        Item tool = item(r.tool());
        if (r.tool() != null && (tool == null || CarriedItems.count(player, tool) == 0)) {
            refuse(player, "no_tool", tool == null ? Component.literal(r.tool().toString()) : new ItemStack(tool).getHoverName());
            return;
        }
        Working w = new Working(bench.immutable(), r.id(), r.work(), r.ticks());
        WORKING.put(player.getUUID(), w);
        AflNetwork.skeletonToTracking(player, new BuildingPackets.WorkOf(player.getId(), w));
    }

    /** What the player works at now, or null. */
    @Nullable
    public static Working working(ServerPlayer player) {
        return WORKING.get(player.getUUID());
    }

    private static void stopWork(ServerPlayer player) {
        if (WORKING.remove(player.getUUID()) != null) AflNetwork.skeletonToTracking(player, new BuildingPackets.WorkOf(player.getId(), null));
    }

    /** The infected hear the saw: a noise from each working player's bench once a stroke. */
    @SubscribeEvent
    public static void workNoise(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WORKING.isEmpty() || event.getServer().getTickCount() % STROKE_TICKS != 0) return;
        for (var e : WORKING.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null) continue;
            Vec3 at = Vec3.atCenterOf(e.getValue().bench()).add(0, 0.5, 0);
            NoiseSystem.emit(new NoiseEvent(player, at, NoiseType.INTERACTION, player.serverLevel().getGameTime(), id("hand_saw"), SAW_RADIUS),
                    player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker) {
            Working w = WORKING.get(target.getUUID());
            if (w != null) AflNetwork.skeletonTo(tracker, new BuildingPackets.WorkOf(target.getId(), w));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopWork(player);
            LAST.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopWork(player);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopWork(player);
    }

    private static void refuse(ServerPlayer player, String reason, Object... args) {
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.workbench." + reason, args), true);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }
}
