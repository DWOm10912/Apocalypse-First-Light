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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
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
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.antaurora.apofirstlight.survivor.SurvivorGrowth;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
 * <p>
 * A furniture piece (FurnitureWork, docs/gameplay/furniture_bench_work_v1.md, 2026-10-22) is made step by step: the bench
 * keeps the piece (WorkbenchBlockEntity#job), the server sends the player the actions to play until its next word
 * ({@link #act}: a timed step played to its end, a press on the nail's or screw's bar), checks that no word comes sooner
 * than its actions take, keeps the count, and when the piece is done takes the materials once, gives it and the carpentry
 * experience. A stop leaves the piece on the bench; anyone who knows the recipe goes on from it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WorkbenchCrafting {
    /**
     * Items that count for one material line (any mix of them), and how many are needed: items (COUNT), or what is left in
     * metered supplies (MeteredSupplyItem: ML of a can, PIECES of a box), summed over all carried (furniture bench work 5.2).
     */
    public record Material(List<ResourceLocation> items, int count, Unit unit) {
        public Material(List<ResourceLocation> items, int count) {
            this(items, count, Unit.COUNT);
        }
    }

    public enum Unit { COUNT, ML, PIECES }

    /**
     * One way to learn a recipe (WorkbenchRecipeData.UNLOCKS): {@code value} is the background id (background), the
     * recipe id (after) or the item id (book, blueprint), else null.
     */
    public record Unlock(String type, @Nullable String value) {}

    /**
     * One recipe (WorkbenchRecipeData). {@code category} is the page's category name (WorkbenchRecipeView.Category);
     * {@code tools}: every one must be carried (none: made by hand); {@code work}: the player action played at the bench while it is made (player_actions/<work>.json), or null:
     * made on the page; {@code unlock}: any one of them teaches it. {@code steps}: a furniture piece's work (FurnitureWork,
     * docs/gameplay/furniture_bench_work_v1.md; {@code work} is then its first step and {@code ticks} the novice's estimate),
     * else empty.
     */
    public record Recipe(ResourceLocation id, String category, ResourceLocation result, int count, List<Material> materials,
                         List<ResourceLocation> tools, int ticks, @Nullable String work, List<Unlock> unlock, List<FurnitureWork.Step> steps) {
        public boolean furniture() { return !steps.isEmpty(); }
    }

    /**
     * A player working at a bench: the bench's master cell, the recipe, and for the players watching (who may not know the
     * recipe, and whose client has only the recipes they know) its loop action and time per item.
     */
    public record Working(BlockPos bench, ResourceLocation recipe, String action, int ticks) {}

    private static final double REACH_SQ = 5.5 * 5.5;
    /** The last finished item per player (game time): a client may not finish faster than 3/4 of the recipe's time. */
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** Who works at a bench now (#work), and since when (game time). */
    private static final Map<UUID, Working> WORKING = new HashMap<>();
    private static final Map<UUID, Long> SINCE = new HashMap<>();
    /**
     * Sawing at the bench is heard this far (blocks), by players (client/workbench/WorkbenchWork plays each push and pull
     * in step with the action, fading out here) and by the infected (#workNoise, once a stroke): a hand saw is quieter than
     * an axe's chop (tree/FellingServer.CHOP_RADIUS 10).
     */
    public static final double SAW_RADIUS = 8;
    /** One saw stroke, push and pull (ticks): tools/author-hand-saw-actions.mjs, tool/hand_saw_rip's length. */
    private static final int STROKE_TICKS = 16;
    /**
     * The wooden knob's work (tools/author-wooden-knob-actions.mjs, one 10 s loop): the saw's three pushes (ticks into the
     * loop), heard as far as the rip; the knife's cuts (a second apart while it whittles) only close by.
     */
    public static final String KNOB_WORK = "tool/wooden_knob_carve";
    private static final int KNOB_LOOP = 200, KNOB_WHITTLE_FROM = 98, KNOB_WHITTLE_TO = 179;
    private static final int[] KNOB_PUSHES = {14, 30, 46};
    public static final double WHITTLE_RADIUS = 4;

    /** A player's furniture work: the bench, the plan sent last and when (game time), the carpentry level it was made for. */
    private record Furn(BlockPos bench, FurnitureWork.Plan plan, long last, int level) {}

    private static final Map<UUID, Furn> FURN = new HashMap<>();
    private static final TagKey<Item> BOARDS = ItemTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "boards"));
    private static final TagKey<Item> KNOBS = ItemTags.create(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "cabinet_knobs"));

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
        ResourceLocation missing = missingTool(player, r);
        if (missing != null) {
            Item tool = item(missing);
            refuse(player, "no_tool", tool == null ? Component.literal(missing.toString()) : new ItemStack(tool).getHoverName());
            return;
        }
        if (r.furniture()) return;   // made by #act
        // check every line first, then take: nothing is taken when anything is short
        if (!enough(player, r)) {
            refuse(player, "short");
            return;
        }
        take(player, r);
        LAST.put(player.getUUID(), now);
        CarriedItems.give(player, new ItemStack(result, r.count()));
        level.playSound(null, bench, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 0.9F);
    }

    /** Whether the player carries every material line (items counted, metered supplies by what is left). */
    private static boolean enough(ServerPlayer player, Recipe r) {
        for (Material m : r.materials()) if (carried(player, m) < m.count()) return false;
        return true;
    }

    private static int carried(ServerPlayer player, Material m) {
        int have = 0;
        for (ResourceLocation option : m.items()) {
            Item it = item(option);
            if (it != null) have += m.unit() == Unit.COUNT ? CarriedItems.count(player, it) : CarriedItems.amount(player, it);
        }
        return have;
    }

    /** Takes every material line once (check {@link #enough} first): the options in order, metered ones by the amount. */
    private static void take(ServerPlayer player, Recipe r) {
        for (Material m : r.materials()) {
            int left = m.count();
            for (ResourceLocation option : m.items()) {
                Item it = item(option);
                if (it == null || left <= 0) continue;
                left -= m.unit() == Unit.COUNT ? CarriedItems.take(player, it, left) : CarriedItems.takeAmount(player, it, left);
            }
        }
    }

    /**
     * Server: the player starts ({@code start}) or stops working at the bench on a work recipe. A start is checked as a
     * craft (the bench, the reach, the tool; the materials are checked per item); the players around are told either way.
     * A furniture recipe checks the materials up front, and another recipe's piece on the bench is thrown away only with
     * {@code discard} (the page asked).
     */
    public static void work(ServerPlayer player, BlockPos bench, ResourceLocation id, boolean start, boolean discard) {
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
        ResourceLocation missing = missingTool(player, r);
        if (missing != null) {
            Item tool = item(missing);
            refuse(player, "no_tool", tool == null ? Component.literal(missing.toString()) : new ItemStack(tool).getHoverName());
            return;
        }
        for (var e : WORKING.entrySet())
            if (!e.getKey().equals(player.getUUID()) && e.getValue().bench().equals(bench)) {
                refuse(player, "busy");
                return;
            }
        if (r.furniture()) {
            startFurniture(player, bench.immutable(), r, discard);
            return;
        }
        Working w = new Working(bench.immutable(), r.id(), r.work(), r.ticks());
        WORKING.put(player.getUUID(), w);
        SINCE.put(player.getUUID(), level.getGameTime());
        AflNetwork.skeletonToTracking(player, new BuildingPackets.WorkOf(player.getId(), w));
    }

    // ---------------------------------------------------------------- furniture (FurnitureWork)

    private static void startFurniture(ServerPlayer player, BlockPos bench, Recipe r, boolean discard) {
        if (!(player.serverLevel().getBlockEntity(bench) instanceof WorkbenchBlockEntity be)) return;
        if (!be.finished().isEmpty()) {
            // the last piece still stands on the top (doc 12.3): taken off first
            refuse(player, "occupied");
            return;
        }
        if (!enough(player, r)) {
            refuse(player, "short");
            return;
        }
        FurnitureWork.Job job = be.job();
        if (job != null && !job.recipe().equals(r.id())) {
            if (!discard) {
                Recipe other = find(job.recipe());
                Item made = other == null ? null : item(other.result());
                refuse(player, "half_built_other", made == null ? Component.literal(job.recipe().toString()) : new ItemStack(made).getHoverName());
                return;
            }
            job = null;
        }
        job = job == null ? FurnitureWork.Job.begin(r.id(), r.steps(), carriedOf(player, r, BOARDS), carriedOf(player, r, KNOBS)) : job.resume();
        int lv = SurvivorGrowth.level(player, FurnitureWork.GROWTH);
        be.setJob(job, be.canOpen());
        // left done (the materials were short at its end): finished now, then on as after any piece
        if (job.complete()) finish(player, be, r, job);
        send(player, new Furn(bench, FurnitureWork.plan(job, be.canOpen(), lv, null, null), player.serverLevel().getGameTime(), lv), r);
    }

    /** The first carried item of the recipe's line that takes items of the tag (the wood of the boards, the knob), or null. */
    @Nullable
    private static ResourceLocation carriedOf(ServerPlayer player, Recipe r, TagKey<Item> tag) {
        for (Material m : r.materials()) {
            if (m.unit() != Unit.COUNT) continue;
            boolean line = false;
            for (ResourceLocation option : m.items()) { Item it = item(option); if (it != null && new ItemStack(it).is(tag)) line = true; }
            if (!line) continue;
            for (ResourceLocation option : m.items()) { Item it = item(option); if (it != null && CarriedItems.count(player, it) > 0) return option; }
        }
        return null;
    }

    /** The plan to the player and the players around (WorkOf carries it as the action). */
    private static void send(ServerPlayer player, Furn f, Recipe r) {
        FURN.put(player.getUUID(), f);
        Working w = new Working(f.bench(), r.id(), f.plan().encode(), r.ticks());
        WORKING.put(player.getUUID(), w);
        SINCE.put(player.getUUID(), f.last());
        AflNetwork.skeletonTo(player, new BuildingPackets.WorkOf(player.getId(), w));
        AflNetwork.skeletonToTracking(player, new BuildingPackets.WorkOf(player.getId(), w));
    }

    /** Stops the player's furniture work and tells the own client too (it plays on until told). */
    private static void stopFurniture(ServerPlayer player) {
        stopWork(player);
        AflNetwork.skeletonTo(player, new BuildingPackets.WorkOf(player.getId(), null));
    }

    /**
     * Server: a furniture piece's word from its player. Waiting on a timed step: {@code press} false, it was played to its
     * end. Waiting on the ring: a press, {@code good} in the zone, {@code perfect} in its middle third (two blows; a miss
     * only costs time). The piece done: it stays on the top (doc 12.3), the can is shut and the work stops ({@code press}
     * false, a new piece, only while the top is clear: a client of before the round 2). Each word comes no sooner than 3/4
     * of the plan's actions take, or the work stops.
     */
    public static void act(ServerPlayer player, BlockPos bench, ResourceLocation id, boolean press, boolean good, boolean perfect) {
        Furn f = FURN.get(player.getUUID());
        Working w = WORKING.get(player.getUUID());
        Recipe r = find(id);
        if (f == null || w == null || r == null || !f.bench().equals(bench) || !w.recipe().equals(id)) return;
        var level = player.serverLevel();
        if (!(level.getBlockEntity(bench) instanceof WorkbenchBlockEntity be) || player.distanceToSqr(Vec3.atCenterOf(bench)) > REACH_SQ) {
            stopFurniture(player);
            return;
        }
        FurnitureWork.Plan plan = f.plan();
        if ((plan.waits() == FurnitureWork.Wait.PRESS) != press && plan.waits() != FurnitureWork.Wait.END) return;
        long now = level.getGameTime();
        if (now - f.last() < plan.ticks() * 3L / 4 - 2) {
            stopFurniture(player);
            return;
        }
        int lv = f.level();
        if (plan.waits() == FurnitureWork.Wait.END) {
            if (FurnitureWork.CLOSING.equals(plan.qte())) {
                stopFurniture(player);
                return;
            }
            if (press || !be.finished().isEmpty()) {
                // the batch is done: the can shut (if it stands open), then the client stops
                boolean open = be.canOpen();
                be.setJob(be.job(), false);
                send(player, new Furn(bench, FurnitureWork.closing(open, lv), now, lv), r);
                return;
            }
            ResourceLocation missing = missingTool(player, r);
            if (missing != null || !enough(player, r)) {
                Item tool = item(missing);
                if (missing != null) refuse(player, "no_tool", tool == null ? Component.literal(missing.toString()) : new ItemStack(tool).getHoverName());
                else refuse(player, "short");
                stopFurniture(player);
                return;
            }
            FurnitureWork.Job next = FurnitureWork.Job.begin(r.id(), r.steps(), carriedOf(player, r, BOARDS), carriedOf(player, r, KNOBS));
            be.setJob(next, be.canOpen());
            send(player, new Furn(bench, FurnitureWork.plan(next, be.canOpen(), lv, null, null), now, lv), r);
            return;
        }
        FurnitureWork.Job job = be.job();
        if (job == null || !job.recipe().equals(id) || job.complete()) {
            stopFurniture(player);
            return;
        }
        ResourceLocation missing = missingTool(player, r);
        if (missing != null) {
            Item tool = item(missing);
            refuse(player, "no_tool", tool == null ? Component.literal(missing.toString()) : new ItemStack(tool).getHoverName());
            stopFurniture(player);
            return;
        }
        FurnitureWork.Job before = job;
        String reaction = press ? FurnitureWork.reaction(job, good, perfect, lv) : null;
        boolean canOpen = be.canOpen();
        FurnitureWork.Step step = job.current();
        if (!press && step != null && FurnitureWork.WIPE.equals(step.step())) canOpen = true;   // pried open and stirred before it
        job = FurnitureWork.worked(FurnitureWork.advance(job, press, good, perfect, lv), now - f.last(), plan);
        be.setJob(job, canOpen);
        if (job.complete()) {
            if (!enough(player, r)) {
                // kept done on the bench: finished as soon as someone carries the materials again
                refuse(player, "short");
                stopFurniture(player);
                return;
            }
            finish(player, be, r, job);
        }
        send(player, new Furn(bench, FurnitureWork.plan(job, canOpen, lv, before, reaction), now, lv), r);
    }

    /**
     * The piece done: the materials taken once, the carpentry experience (doc 3.7), the piece left standing on the top (doc
     * 12.3: one at a time, taken off with the use key, WorkbenchBlockEntity#takeFinished), the work cleared.
     */
    private static void finish(ServerPlayer player, WorkbenchBlockEntity be, Recipe r, FurnitureWork.Job job) {
        Item result = item(r.result());
        if (result == null) return;
        take(player, r);
        be.setFinished(new ItemStack(result));
        player.serverLevel().playSound(null, be.getBlockPos(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 0.9F);
        int nails = 0, screws = 0;
        for (FurnitureWork.Step s : job.steps()) {
            if (FurnitureWork.kind(s.step()) == FurnitureWork.Kind.NAIL) nails += s.times();
            if (FurnitureWork.kind(s.step()) == FurnitureWork.Kind.FIT) screws += s.times() * FurnitureWork.screws(s.step());
        }
        SurvivorGrowth.gain(player, FurnitureWork.GROWTH, "nail", nails);
        SurvivorGrowth.gain(player, FurnitureWork.GROWTH, "screw", screws);
        SurvivorGrowth.gain(player, FurnitureWork.GROWTH, "piece", 1);
        be.setJob(null, be.canOpen());
    }

    /** The first of the recipe's tools the player does not carry, or null: all there. */
    @Nullable
    private static ResourceLocation missingTool(ServerPlayer player, Recipe r) {
        for (ResourceLocation id : r.tools()) {
            Item tool = item(id);
            if (tool == null || CarriedItems.count(player, tool) == 0) return id;
        }
        return null;
    }

    /** What the player works at now, or null. */
    @Nullable
    public static Working working(ServerPlayer player) {
        return WORKING.get(player.getUUID());
    }

    private static void stopWork(ServerPlayer player) {
        SINCE.remove(player.getUUID());
        Furn f = FURN.remove(player.getUUID());
        // a stop puts the stain can away (doc 3.3: not shut on the bench, just no longer drawn); the piece stays
        if (f != null && player.serverLevel().getBlockEntity(f.bench()) instanceof WorkbenchBlockEntity be) be.setJob(be.job(), false);
        if (WORKING.remove(player.getUUID()) != null) AflNetwork.skeletonToTracking(player, new BuildingPackets.WorkOf(player.getId(), null));
    }

    /**
     * The infected hear the work: a noise from each working player's bench once a saw stroke; at the wooden knob only on
     * the saw's pushes, and the knife's cuts close by (WHITTLE_RADIUS).
     */
    @SubscribeEvent
    public static void workNoise(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WORKING.isEmpty()) return;
        boolean stroke = event.getServer().getTickCount() % STROKE_TICKS == 0;
        for (var e : WORKING.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null) continue;
            long now = player.serverLevel().getGameTime();
            Furn f = FURN.get(e.getKey());
            if (f != null) {
                furnitureNoise(player, f, now);
                continue;
            }
            double radius = stroke ? SAW_RADIUS : 0;
            String sound = "hand_saw";
            if (KNOB_WORK.equals(e.getValue().action())) {
                int t = (int) Math.floorMod(now - SINCE.getOrDefault(e.getKey(), now), (long) KNOB_LOOP);
                radius = 0;
                for (int push : KNOB_PUSHES) if (t == push) radius = SAW_RADIUS;
                if (t >= KNOB_WHITTLE_FROM && t <= KNOB_WHITTLE_TO && (t - KNOB_WHITTLE_FROM) % 20 == 0) {
                    radius = WHITTLE_RADIUS;
                    sound = "folding_knife";
                }
            }
            if (radius <= 0) continue;
            Vec3 at = Vec3.atCenterOf(e.getValue().bench()).add(0, 0.5, 0);
            NoiseSystem.emit(new NoiseEvent(player, at, NoiseType.INTERACTION, now, id(sound), radius), player.serverLevel());
        }
    }

    /** A furniture action's noise (its events' {@code noise}: blocks; the sound's id with it), at its share of the action. */
    private record Noise(double at, double radius, String sound) {}

    private static final Map<String, List<Noise>> NOISES = new HashMap<>();

    private static List<Noise> noises(String action) {
        return NOISES.computeIfAbsent(action, k -> {
            List<Noise> list = new ArrayList<>();
            PlayerAction a = PlayerActions.get(k);
            if (a == null || a.events == null || Float.isNaN(a.length) || a.length <= 0) return list;
            for (JsonElement el : a.events) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("noise") || !o.has("at")) continue;
                list.add(new Noise(o.get("at").getAsDouble() / a.length, o.get("noise").getAsDouble(),
                        o.has("sound") ? o.get("sound").getAsString() : "furniture"));
            }
            return list;
        });
    }

    /** The infected hear the furniture work: the play running now, its events' noise at their (scaled) tick. */
    private static void furnitureNoise(ServerPlayer player, Furn f, long now) {
        long t = now - f.last();
        for (FurnitureWork.Play p : f.plan().plays()) {
            if (p.ticks() <= 0) return;   // the hold on the bar: quiet
            if (t >= p.ticks()) {
                t -= p.ticks();
                continue;
            }
            for (Noise n : noises(p.action())) {
                if (Math.round(n.at() * p.ticks()) != t) continue;
                Vec3 at = Vec3.atCenterOf(f.bench()).add(0, 0.5, 0);
                NoiseSystem.emit(new NoiseEvent(player, at, NoiseType.INTERACTION, now, id(n.sound()), n.radius()), player.serverLevel());
            }
            return;
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
