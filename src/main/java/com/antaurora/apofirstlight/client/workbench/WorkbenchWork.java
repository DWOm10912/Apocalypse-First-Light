package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.RangedSoundInstance;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.network.RangedSoundS2CPacket;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Work at the workbench (docs/gameplay/afl_workbench_v1_design.md 17): a recipe made at the bench (WorkbenchRecipeView#work)
 * is not made on the page. One click on 制作 closes the page and the player works on the top until the items are made: a
 * white oak log lies on the top over the drawers (WorkbenchRenderer, {@link #workpiece}), the body steps to it and saws it
 * (the skeleton action {@code tool/hand_saw_set}, then the loop {@code tool/hand_saw_rip}, at {@link #at}'s anchors), the
 * kerf runs into the log as the item comes on, sawdust falls; a panel over the hotbar shows the item, how many and the
 * progress. Moving, jumping, a left click, opening any screen, leaving the bench, no tool or no materials stop it.
 * <p>
 * The own work is kept here and told to the server (BuildingPackets.Work), which tells the players who see this one
 * (BuildingPackets.WorkOf, {@link #remote}): they see the same action, log and sawdust. The anchors' numbers are those of
 * tools/author-hand-saw-actions.mjs WORK (which checks the reach and the clearances offline).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchWork {
    // ---- the work on the top (blocks, the bench's north frame: the master cell's corner, the player at z < 0 facing +z, +x
    // the player's left); tools/author-hand-saw-actions.mjs WORK
    /** The log: across the master cell's middle, half its width, from its front end (over the edge) to its far end. */
    public static final double LOG_X = 0.5, LOG_R = 0.09, TOP = 0.9, LOG_END = -0.30, LOG_FAR = 0.54;
    /** The kerf on the log's top: from just in at the front end to the bench's front edge as an item is made. */
    public static final double KERF_FROM = -0.27, KERF_TO = -0.02;
    private static final double STAND_LEFT = 0.36, STAND_BACK = 0.42, YAW = -15;
    private static final double HOLD_LEFT = 0.13, HOLD_BACK = 0.1, HOLD_UP = 0.15;
    /** tool/hand_saw_set's length (s): the strokes start after it. */
    private static final double SET = 0.7, STROKE = 0.8;
    /** Each loop action and the one played once before it. */
    private static final Map<String, String> STARTS = Map.of("tool/hand_saw_rip", "tool/hand_saw_set");
    /** How far from the stand point a work may start (the action's approach steps at most 2 blocks), and may drift. */
    private static final double START_REACH = 1.8, DRIFT = 0.25;
    private static final ResourceLocation LOG = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "white_oak_log");

    /** What a player's skeleton plays now (SkeletonActions): the action, when it started (s, game time), its anchors (world). */
    public record At(String action, double started, Map<String, Vec3> anchors) {}

    private static final class Session {
        BlockPos bench;
        WorkbenchRecipeView recipe;
        int quantity, done;
        long started, itemStarted;
        Vec3 at;
    }

    @Nullable private static Session own;
    /** Other players at work (entity id → what and since when, game time). */
    private static final Map<Integer, Remote> REMOTE = new HashMap<>();

    private record Remote(WorkbenchCrafting.Working work, long started) {}

    private WorkbenchWork() {}

    // ------------------------------------------------------------------------------------------------ own work

    /** The page's 制作 on a recipe made at the bench: close the page and start (or say where to stand). */
    public static void start(BlockPos bench, WorkbenchRecipeView recipe, int quantity) {
        var mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || quantity < 1) return;
        BlockState state = mc.level.getBlockState(bench);
        if (!(state.getBlock() instanceof Workbench)) return;
        Direction facing = state.getValue(Workbench.FACING);
        Vec3 stand = Workbench.local(bench, facing, LOG_X + STAND_LEFT, 0, KERF_FROM - STAND_BACK);
        Vec3 front = Workbench.local(bench, facing, 1, 0, 0.04);
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 p = player.position();
        if (p.subtract(front).dot(out) < 0 || Math.hypot(p.x - stand.x, p.z - stand.z) > START_REACH) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.workbench.stand_front"), true);
            return;
        }
        mc.setScreen(null);
        Session s = new Session();
        s.bench = bench.immutable();
        s.recipe = recipe;
        s.quantity = quantity;
        s.started = mc.level.getGameTime();
        s.itemStarted = s.started + (STARTS.containsKey(recipe.work()) ? Math.round(SET * 20) : 0);
        s.at = p;
        own = s;
        AflNetwork.buildingToServer(new BuildingPackets.Work(s.bench, recipe.id(), true));
    }

    /** Working at a bench now (the building hammer's panel keeps out of the way). */
    public static boolean active() { return own != null; }

    private static void stop() {
        if (own == null) return;
        AflNetwork.buildingToServer(new BuildingPackets.Work(own.bench, own.recipe.id(), false));
        own = null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            own = null;
            REMOTE.clear();
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (own == null) return;
            // the hands are busy: a left click stops the work, a right click does nothing (no block broken or used meanwhile)
            boolean attack = false;
            while (mc.options.keyAttack.consumeClick()) attack = true;
            while (mc.options.keyUse.consumeClick()) { }
            if (attack) stop();
            return;
        }
        REMOTE.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player));
        dust(mc.level);
        strokes(mc.level);
        Session s = own;
        if (s == null) return;
        Player player = mc.player;
        var input = mc.player.input;
        if (!player.isAlive() || player.isSpectator() || mc.screen != null || !WorkbenchClient.atBench(player, s.bench)
                || player.position().distanceTo(s.at) > DRIFT
                || Math.abs(input.forwardImpulse) > 0.01F || Math.abs(input.leftImpulse) > 0.01F || input.jumping) {
            stop();
            return;
        }
        if (!WorkbenchRecipes.toolPresent(player, s.recipe)) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.workbench.no_tool",
                    s.recipe.tool() == null ? "" : s.recipe.tool().getHoverName()), true);
            stop();
            return;
        }
        long now = mc.level.getGameTime();
        if (now - s.itemStarted < s.recipe.ticks()) return;
        AflNetwork.buildingToServer(new BuildingPackets.Craft(s.bench, s.recipe.id()));
        s.done++;
        s.itemStarted = now;
        // the next one: the server's word on the last may still be on its way, so one more than is carried now
        if (s.done >= s.quantity || WorkbenchRecipes.craftable(player, s.recipe) < 2) stop();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        own = null;
        REMOTE.clear();
        HALVES.clear();
        WorkbenchRecipes.clear();
    }

    // ------------------------------------------------------------------------------------------------ others' work

    /** BuildingPackets.WorkOf: another player started (work) or stopped (null) working at a bench. */
    public static void remote(int entity, @Nullable WorkbenchCrafting.Working work) {
        var mc = Minecraft.getInstance();
        if (work == null || mc.level == null) REMOTE.remove(entity);
        else REMOTE.put(entity, new Remote(work, mc.level.getGameTime()));
    }

    // ------------------------------------------------------------------------------------------------ looks

    /**
     * A player's work as the looks need it: the bench, its facing, the loop action, the start (game time), the item's progress,
     * the recipe (the log on the bench top is its log).
     */
    private record Job(BlockPos bench, Direction facing, String work, long started, float progress, ResourceLocation recipe) {}

    @Nullable
    private static Job job(Player p, float partial) {
        Level level = p.level();
        BlockPos bench;
        String work;
        long started;
        float progress;
        ResourceLocation recipe;
        double now = level.getGameTime() + partial;
        Session s = own;
        if (p == Minecraft.getInstance().player) {
            if (s == null || s.recipe.work() == null) return null;
            bench = s.bench;
            work = s.recipe.work();
            recipe = s.recipe.id();
            started = s.started;
            progress = (float) Math.max(0, Math.min(1, (now - s.itemStarted) / Math.max(1, s.recipe.ticks())));
        } else {
            Remote r = REMOTE.get(p.getId());
            if (r == null) return null;
            // the action and time come with WorkOf: whoever watches may not know the recipe
            bench = r.work().bench();
            work = r.work().action();
            recipe = r.work().recipe();
            started = r.started();
            // the others' item clock is not sent: the recipe's time, round and round from the first stroke
            double since = now - started - (STARTS.containsKey(work) ? SET * 20 : 0);
            int ticks = Math.max(1, r.work().ticks());
            progress = since < 0 ? 0 : (float) (since % ticks / ticks);
        }
        BlockState state = level.getBlockState(bench);
        if (!(state.getBlock() instanceof Workbench)) return null;
        return new Job(bench, state.getValue(Workbench.FACING), work, started, progress, recipe);
    }

    /** SkeletonActions: what this player plays at a bench now, or null. */
    @Nullable
    public static At at(Player p, float partial) {
        Job j = job(p, partial);
        if (j == null) return null;
        double now = (p.level().getGameTime() + partial) / 20.0, t0 = j.started() / 20.0;
        String first = STARTS.get(j.work());
        boolean opening = first != null && now - t0 < SET;
        return new At(opening ? first : j.work(), opening || first == null ? t0 : t0 + SET, anchors(j));
    }

    private static Map<String, Vec3> anchors(Job j) {
        double kerf = kerfZ(j.progress());
        double yaw = Math.toRadians(YAW);
        double sx = LOG_X + STAND_LEFT, sz = kerf - STAND_BACK;
        return Map.of(
                "stand", Workbench.local(j.bench(), j.facing(), sx, 0, sz),
                // the body faces YAW to the left of the cut (+z; +x is the player's left)
                "face", Workbench.local(j.bench(), j.facing(), sx + 3 * Math.sin(yaw), 0, sz + 3 * Math.cos(yaw)),
                "kerf", Workbench.local(j.bench(), j.facing(), LOG_X, TOP + 2 * LOG_R, kerf),
                "hold", Workbench.local(j.bench(), j.facing(), LOG_X + HOLD_LEFT, TOP + HOLD_UP, Math.max(kerf - HOLD_BACK, LOG_END + 0.03)));
    }

    public static double kerfZ(float progress) {
        return KERF_FROM + (KERF_TO - KERF_FROM) * progress;
    }

    /** WorkbenchRenderer: the progress of the item being made on this bench's top (0..1; the log and its kerf), or -1. */
    public static float workpiece(BlockPos master, float partial) {
        Job j = jobAt(master, partial);
        return j == null ? -1 : j.progress();
    }

    /** WorkbenchRenderer: the log being sawn on this bench's top (#logState of its recipe), or null. */
    @Nullable
    public static BlockState workLog(BlockPos master, float partial) {
        Job j = jobAt(master, partial);
        return j == null ? null : logState(j.recipe());
    }

    @Nullable
    private static Job jobAt(BlockPos master, float partial) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (own != null && own.bench.equals(master) && mc.player != null) {
            Job j = job(mc.player, partial);
            if (j != null) return j;
        }
        for (var e : REMOTE.entrySet()) {
            if (!e.getValue().work().bench().equals(master)) continue;
            if (mc.level.getEntity(e.getKey()) instanceof Player p) {
                Job j = job(p, partial);
                if (j != null) return j;
            }
        }
        return null;
    }

    /** Sawdust on the push of each stroke: out of the kerf under the log's front end, now and then off its top. */
    private static void dust(Level level) {
        var mc = Minecraft.getInstance();
        RandomSource random = level.random;
        java.util.List<Player> working = new java.util.ArrayList<>();
        if (own != null && mc.player != null) working.add(mc.player);
        for (int id : REMOTE.keySet()) if (level.getEntity(id) instanceof Player p) working.add(p);
        for (Player p : working) {
            Job j = job(p, 0);
            if (j == null) continue;
            double since = (level.getGameTime() - j.started()) / 20.0 - (STARTS.containsKey(j.work()) ? SET : 0);
            if (since < 0 || since % STROKE >= STROKE / 2 || random.nextFloat() > 0.6F) continue;
            BlockState log = logState(j.recipe());
            if (log == null) continue;
            double kerf = kerfZ(j.progress());
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, log);
            Vec3 under = Workbench.local(j.bench(), j.facing(), LOG_X + (random.nextDouble() - 0.5) * 0.03, TOP - 0.01,
                    LOG_END + random.nextDouble() * (Math.min(kerf, 0.0) - LOG_END));
            level.addParticle(dust, under.x, under.y, under.z, 0, 0, 0);
            if (random.nextFloat() < 0.25F) {
                Vec3 top = Workbench.local(j.bench(), j.facing(), LOG_X + (random.nextDouble() - 0.5) * 0.04, TOP + 2 * LOG_R + 0.01, kerf);
                level.addParticle(dust, top.x, top.y, top.z, 0, 0, 0);
            }
        }
    }

    /** Each working player's last half stroke sounded (entity id → half strokes since the first push). */
    private static final Map<Integer, Long> HALVES = new HashMap<>();

    /**
     * The saw heard in step with the action each viewer sees: the push at each stroke's start, the pull at its half, from
     * the kerf, fading out at WorkbenchCrafting.SAW_RADIUS (client/RangedSoundInstance, as noise/RangedSound plays a
     * server's sound; the server sends the infected's noise itself).
     */
    private static void strokes(Level level) {
        var mc = Minecraft.getInstance();
        java.util.List<Player> working = new java.util.ArrayList<>();
        if (own != null && mc.player != null) working.add(mc.player);
        for (int id : REMOTE.keySet()) if (level.getEntity(id) instanceof Player p) working.add(p);
        HALVES.keySet().removeIf(id -> working.stream().noneMatch(p -> p.getId() == id));
        for (Player p : working) {
            Job j = job(p, 0);
            if (j == null) continue;
            double since = (level.getGameTime() - j.started()) / 20.0 - (STARTS.containsKey(j.work()) ? SET : 0);
            if (since < 0) continue;
            long half = (long) Math.floor(since / (STROKE / 2));
            Long last = HALVES.put(p.getId(), half);
            if (last != null && last == half) continue;
            Vec3 at = Workbench.local(j.bench(), j.facing(), LOG_X, TOP + 2 * LOG_R, kerfZ(j.progress()));
            var sound = (half % 2 == 0 ? AflSounds.HAND_SAW_PUSH : AflSounds.HAND_SAW_PULL).get();
            RangedSoundInstance.play(new RangedSoundS2CPacket(sound.getLocation(), SoundSource.PLAYERS, at.x, at.y, at.z,
                    (float) WorkbenchCrafting.SAW_RADIUS, 1F, 1F, level.random.nextLong(), false));
        }
    }

    /**
     * The log a recipe saws, standing (its end grain's colour for the dust): the recipe's first log material when this
     * client knows the recipe (a species' boards saw that species' log); else, or for a recipe without a log, the white oak
     * log; null where none is registered.
     */
    @Nullable
    public static BlockState logState(@Nullable ResourceLocation recipe) {
        var mc = Minecraft.getInstance();
        WorkbenchRecipeView view = mc.player == null ? null : WorkbenchRecipes.find(mc.player, recipe);
        if (view != null) for (var m : view.materials())
            if (m.shown().getItem() instanceof net.minecraft.world.item.BlockItem b && b.getBlock() instanceof RotatedPillarBlock)
                return b.getBlock().defaultBlockState();
        var block = ForgeRegistries.BLOCKS.getValue(LOG);
        if (!(block instanceof RotatedPillarBlock)) return null;
        return block.defaultBlockState();
    }

    // ------------------------------------------------------------------------------------------------ panel

    /** Over the hotbar: the item, which of how many, the progress, and the key that stops. */
    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        Session s = own;
        if (s == null || mc.options.hideGui || mc.player == null || mc.screen != null || mc.level == null) return;
        var font = mc.font;
        long nanos = System.nanoTime();
        Component title = Component.translatable("hud.apocalypse_firstlight.workbench.work", s.recipe.result().getHoverName(),
                Math.min(s.done + 1, s.quantity), s.quantity);
        Component stopText = Component.translatable("hud.apocalypse_firstlight.workbench.stop");
        AflKeyHint cap = AflKeyHint.MOUSE_LEFT;
        float keyW = cap.width(font) + 3 + font.width(stopText);
        float w = Math.max(14 + font.width(title), keyW) + 12, h = 6 + 12 + 6 + 14 + 3;
        float x = screenWidth / 2F - w / 2, y = screenHeight - 77 - h;
        AflUiDraw.panel(g, x, y, w, h, 1);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 6, y + 4, 0);
        pose.scale(0.75F, 0.75F, 1);
        g.renderItem(s.recipe.result(), 0, 0);
        pose.popPose();
        AflUiDraw.text(g, font, title, x + 20, y + 6, AflUiStyle.TEXT, 1);
        double now = mc.level.getGameTime() + partialTick;
        float progress = (float) Math.max(0, Math.min(1, (now - s.itemStarted) / Math.max(1, s.recipe.ticks())));
        float by = y + 19, bw = w - 12;
        AflUiShapes.fill(g, x + 6, by, bw, 2, 1, AflUiStyle.LINE, 0.18F);
        if (progress > 0) AflUiShapes.fill(g, x + 6, by, bw * progress, 2, 1, AflUiStyle.ACCENT, 1);
        float ky = by + 6;
        cap.draw(g, font, x + 6, ky, 1, nanos);
        AflUiDraw.text(g, font, stopText, x + 6 + cap.width(font) + 3, ky + 2, AflUiStyle.TEXT, 1);
    };

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        // not "overlays": Forge names the generated handler __Registration_overlays_RegisterGuiOverlaysEvent per package, and
        // WorkbenchClient.Registration#overlays in this package already has it (it would run twice, this one never)
        @SubscribeEvent
        public static void workOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "workbench_work", OVERLAY);
        }
    }
}
