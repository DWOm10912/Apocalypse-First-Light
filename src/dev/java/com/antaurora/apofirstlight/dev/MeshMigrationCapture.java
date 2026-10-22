package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Opt-in visual regression sequence for the Vextris migration (docs/dev/vextris_migration_v1.md), excluded from the
 * production jar. Launch with src/dev/mesh-migration-capture.init.gradle and -Dafl.meshMigrationCapture=&lt;label&gt;.
 * <p>
 * It only uses gameplay-level APIs (items, attachment NBT, ammo, GeoItem animation triggers, block placement), never
 * the mesh API, so the same file runs unchanged on the pre-migration baseline and on the migrated build: the two
 * screenshot sets (run/screenshots/&lt;label&gt;_NN_*.png) are compared pair by pair. A fresh flat creative world, fixed
 * noon light, fixed camera angles: every gun at idle, mid tactical reload, empty idle, mid empty reload, the shoot clip
 * (slide/bolt), ADS, third person back and front; sight and drum-magazine fits; the mesh machines from outside and with
 * the camera inside them (spectator); then a resource reload (F3+T path) and a repeat of a few shots.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class MeshMigrationCapture {
    private static final String LABEL = System.getProperty("afl.meshMigrationCapture");
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private record Step(String shot, int delay, Consumer<Minecraft> client, Consumer<ServerPlayer> server) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, countdown, shots, groundY = Integer.MIN_VALUE;
    private static boolean worldRequested, pendingShot, done;
    private static String shotName;
    private static float yaw, pitch;
    private static CompletableFuture<Void> reload;

    private MeshMigrationCapture() {}

    private static boolean active() {
        return LABEL != null && !LABEL.isBlank() && !done;
    }

    // ---- the sequence ----------------------------------------------------------------------------------------------

    private static void pause(int ticks) { STEPS.add(new Step(null, ticks, null, null)); }
    private static void shot(String name) { STEPS.add(new Step(name, 0, null, null)); }
    private static void client(int wait, Consumer<Minecraft> action) { STEPS.add(new Step(null, wait, action, null)); }
    private static void server(int wait, Consumer<ServerPlayer> action) { STEPS.add(new Step(null, wait, null, action)); }

    private static void build() {
        server(40, MeshMigrationCapture::setupWorld);
        server(60, MeshMigrationCapture::placeMachines);
        machineShots("");
        gun("p9", AflItems.P9_01, null, null, "reload_empty");
        gun("br51", AflItems.BR51_01, null, null, "reload_empty");
        gun("hr55", AflItems.HR55, null, null, "reload_empty");
        gun("silverwood12", AflItems.SILVERWOOD_12, null, null, "reload_empty");
        gun("blackridge50", AflItems.BLACKRIDGE_50, null, null, "reload_empty");
        gun("br51_reddot_drum", AflItems.BR51_01, AflItems.RIFLE_RED_DOT_01, AflItems.BR51_DRUM_MAGAZINE_50, "reload_empty");
        gun("p9_reddot", AflItems.P9_01, AflItems.PISTOL_RED_DOT, null, "reload_empty");
        gun("hr55_reddot", AflItems.HR55, AflItems.RIFLE_RED_DOT_01, null, "reload_empty");
        client(0, mc -> {
            LOGGER.info("[MeshMigrationCapture] reloading resources (Minecraft#reloadResourcePacks, the F3+T path)");
            reload = mc.reloadResourcePacks();
        });
        STEPS.add(new Step("#await-reload", 40, null, null));
        quickGun("after_reload_br51_reddot_drum", AflItems.BR51_01, AflItems.RIFLE_RED_DOT_01, AflItems.BR51_DRUM_MAGAZINE_50);
        machineShots("after_reload_");
        client(20, mc -> {
            LOGGER.info("[MeshMigrationCapture] done: {} screenshots, mesh state {}", shots, meshState());
            done = true;
            mc.stop();
        });
    }

    private record Machine(String name, Supplier<Block> block, int x, boolean wall) {}

    private static final List<Machine> MACHINES = List.of(
            new Machine("diesel_generator", AflBlocks.DIESEL_GENERATOR, -12, false),
            new Machine("portable_diesel_generator", AflBlocks.PORTABLE_DIESEL_GENERATOR, -6, false),
            new Machine("lead_chest", AflBlocks.LEAD_CHEST, -2, false),
            new Machine("metal_trash_can", AflBlocks.METAL_TRASH_CAN, 1, false),
            new Machine("cash_register", AflBlocks.CASH_REGISTER, 4, false),
            new Machine("distribution_panel", AflBlocks.DISTRIBUTION_PANEL, 8, true),
            new Machine("service_meter_box", AflBlocks.SERVICE_METER_BOX, 12, true));

    private static void machineShots(String prefix) {
        for (Machine m : MACHINES) {
            float eyeY = m.wall() ? 1.5F : 0.9F;
            server(10, p -> look(p, GameType.CREATIVE, m.x() + 0.5, eyeY + 0.6, -3.2, 180, 18));
            shot(prefix + "machine_" + m.name());
            // camera inside the model: the closed-part back-face case (docs: closedSolid regression)
            server(10, p -> look(p, GameType.SPECTATOR, m.x() + 0.5, eyeY, m.wall() ? -6.75 : -5.6, 180, 10));
            shot(prefix + "inside_" + m.name());
            server(6, p -> look(p, GameType.SPECTATOR, m.x() + 0.5, eyeY, m.wall() ? -6.75 : -5.6, 0, 10));
            shot(prefix + "inside_back_" + m.name());
        }
        server(5, p -> look(p, GameType.CREATIVE, 0.5, 0, 6.5, 0, 0));
    }

    private static void gun(String name, Supplier<Item> gun, Supplier<Item> sight, Supplier<Item> magazine, String emptyReload) {
        server(70, p -> give(p, gun, sight, magazine, -1));
        shot(name + "_fp_idle");
        server(12, p -> trigger(p, "reload_tactical"));
        shot(name + "_fp_reload_tactical_a");
        pause(14);
        shot(name + "_fp_reload_tactical_b");
        server(70, p -> setAmmo(p, 0));
        shot(name + "_fp_empty_idle");
        server(16, p -> trigger(p, emptyReload));
        shot(name + "_fp_" + emptyReload);
        server(80, p -> setAmmo(p, Integer.MAX_VALUE));
        server(2, p -> trigger(p, "shoot"));
        shot(name + "_fp_shoot");
        client(25, mc -> mc.options.keyUse.setDown(true));
        shot(name + "_fp_ads");
        client(12, mc -> mc.options.keyUse.setDown(false));
        client(12, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        shot(name + "_tp_back");
        client(12, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shot(name + "_tp_front");
        client(8, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static void quickGun(String name, Supplier<Item> gun, Supplier<Item> sight, Supplier<Item> magazine) {
        server(70, p -> give(p, gun, sight, magazine, -1));
        shot(name + "_fp_idle");
        client(25, mc -> mc.options.keyUse.setDown(true));
        shot(name + "_fp_ads");
        client(12, mc -> mc.options.keyUse.setDown(false));
    }

    // ---- server-side actions ----------------------------------------------------------------------------------------

    private static void setupWorld(ServerPlayer p) {
        var level = p.serverLevel();
        var server = level.getServer();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setWeatherParameters(1_000_000, 0, false, false);
        groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        // a stone wall behind the machine row, the open flat world behind the gun spot
        for (int x = -16; x <= 16; x++) for (int y = 0; y < 4; y++) level.setBlock(new BlockPos(x, groundY + y, -8), Blocks.STONE.defaultBlockState(), 3);
        look(p, GameType.CREATIVE, 0.5, 0, 6.5, 0, 0);
        LOGGER.info("[MeshMigrationCapture] {}: world ready, ground y {}, mesh state {}", LABEL, groundY, meshState());
    }

    private static void placeMachines(ServerPlayer p) {
        var level = p.serverLevel();
        for (Machine m : MACHINES) {
            var stack = new ItemStack(m.block().get().asItem());
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            p.teleportTo(level, m.x() + 0.5, groundY, -3.5, Set.of(), 180, 30);
            BlockHitResult hit = m.wall()
                    ? new BlockHitResult(new Vec3(m.x() + 0.5, groundY + 1.5, -7.0), Direction.SOUTH, new BlockPos(m.x(), groundY + 1, -8), false)
                    : new BlockHitResult(new Vec3(m.x() + 0.5, groundY, -5.5), Direction.UP, new BlockPos(m.x(), groundY - 1, -6), false);
            var result = p.gameMode.useItemOn(p, level, stack, InteractionHand.MAIN_HAND, hit);
            BlockPos placed = m.wall() ? new BlockPos(m.x(), groundY + 1, -7) : new BlockPos(m.x(), groundY, -6);
            LOGGER.info("[MeshMigrationCapture] placed {}: {} -> {}", m.name(), result, level.getBlockState(placed));
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Game mode, feet position (x, groundY + dy, z) or, in spectator, the eye there; fixed view direction. */
    private static void look(ServerPlayer p, GameType mode, double x, double dy, double z, float yawDeg, float pitchDeg) {
        if (p.gameMode.getGameModeForPlayer() != mode) p.setGameMode(mode);
        double y = groundY + dy - (mode == GameType.SPECTATOR ? p.getEyeHeight() : 0);
        p.teleportTo(p.serverLevel(), x, y, z, Set.of(), yawDeg, pitchDeg);
        yaw = yawDeg;
        pitch = pitchDeg;
    }

    private static void give(ServerPlayer p, Supplier<Item> gunItem, Supplier<Item> sight, Supplier<Item> magazine, int ammo) {
        var stack = new ItemStack(gunItem.get());
        var attachments = new CompoundTag();
        if (sight != null) attachments.put("SIGHT", new ItemStack(sight.get()).save(new CompoundTag()));
        if (magazine != null) attachments.put("MAGAZINE", new ItemStack(magazine.get()).save(new CompoundTag()));
        if (!attachments.isEmpty()) stack.getOrCreateTag().put("AflAttachments", attachments);
        var definition = ((NativeGunItem) stack.getItem()).definition();
        NativeGunAmmo.set(stack, definition, ammo < 0 ? NativeGunAmmo.capacity(stack, definition) : ammo);
        GeoItem.getOrAssignId(stack, p.serverLevel());
        look(p, GameType.CREATIVE, 0.5, 0, 6.5, 0, 0);
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, stack);
        p.connection.send(new ClientboundSetCarriedItemPacket(0));
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
    }

    private static void setAmmo(ServerPlayer p, int count) {
        var stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof NativeGunItem gun)) return;
        NativeGunAmmo.set(stack, gun.definition(), count == Integer.MAX_VALUE ? NativeGunAmmo.capacity(stack, gun.definition()) : count);
        p.inventoryMenu.broadcastChanges();
    }

    private static void trigger(ServerPlayer p, String clip) {
        var stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof NativeGunItem gun)) return;
        long id = GeoItem.getOrAssignId(stack, p.serverLevel());
        p.inventoryMenu.broadcastChanges();
        gun.triggerAnim(p, id, NativeGunItem.ACTION_CONTROLLER, clip);
    }

    /** Which mesh cache is live (reflection: the same file compiles before and after the migration). */
    private static String meshState() {
        try {
            var vextris = Class.forName("com.antaurora.vextris.client.VextrisMeshes");
            var snapshot = vextris.getMethod("snapshot").invoke(null);
            var models = (java.util.Map<?, ?>) snapshot.getClass().getMethod("models").invoke(snapshot);
            var errors = (java.util.Map<?, ?>) snapshot.getClass().getMethod("errors").invoke(snapshot);
            return "Vextris generation " + vextris.getMethod("generation").invoke(null) + ", " + models.size() + " meshes, " + errors.size() + " rejected";
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        try {
            var afl = Class.forName("com.antaurora.apofirstlight.client.mesh.AflMeshCache");
            var snapshot = afl.getMethod("snapshot").invoke(null);
            var models = (java.util.Map<?, ?>) snapshot.getClass().getMethod("models").invoke(snapshot);
            return "AflMeshCache generation " + snapshot.getClass().getMethod("generation").invoke(snapshot) + ", " + models.size() + " meshes";
        } catch (ReflectiveOperationException | LinkageError e) {
            return "unknown (" + e + ")";
        }
    }

    /**
     * Run-to-run environment that is not under test: OS window focus (ADS is gated on Minecraft#isWindowActive, and an
     * active window grabs the mouse, whose deltas feed the weapon sway) and the player skin (fetched online or not).
     * Every run gets: window treated as active, mouse released, Steve skin.
     */
    private static void normaliseEnvironment(Minecraft mc) {
        mc.setWindowActive(true);
        if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
        var connection = mc.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(mc.player.getUUID());
        if (info == null) return;
        try {
            var textures = net.minecraft.client.multiplayer.PlayerInfo.class.getDeclaredField("textureLocations");
            textures.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<com.mojang.authlib.minecraft.MinecraftProfileTexture.Type, net.minecraft.resources.ResourceLocation>) textures.get(info);
            map.put(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN, net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin());
            var model = net.minecraft.client.multiplayer.PlayerInfo.class.getDeclaredField("skinModel");
            model.setAccessible(true);
            model.set(info, "default");
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warnedSkin) { warnedSkin = true; LOGGER.warn("[MeshMigrationCapture] cannot pin the skin: {}", e.toString()); }
        }
    }

    private static boolean warnedSkin;

    // ---- driver -----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!active() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                worldRequested = true;
                build();
                var settings = new LevelSettings("afl_mesh_capture", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("afl_mesh_capture_" + LABEL, settings, new WorldOptions(20261010L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null || mc.getOverlay() != null) return;
        normaliseEnvironment(mc);
        // hold the camera on the fixture despite mouse deltas
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        if (pendingShot) return;
        if (countdown > 0) { countdown--; return; }
        if (index >= 0 && index < STEPS.size() && "#await-reload".equals(STEPS.get(index).shot()) && reload != null && !reload.isDone()) return;
        if (++index >= STEPS.size()) return;
        Step step = STEPS.get(index);
        countdown = step.delay();
        if (step.client() != null) step.client().accept(mc);
        if (step.server() != null) {
            var uuid = mc.player.getUUID();
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(uuid);
                if (p != null) step.server().accept(p);
            });
        }
        if (step.shot() != null && !step.shot().startsWith("#")) {
            shotName = String.format("%s_%02d_%s.png", LABEL, ++shots, step.shot());
            pendingShot = true;
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!active() || event.phase != TickEvent.Phase.END || !pendingShot) return;
        pendingShot = false;
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, shotName, mc.getMainRenderTarget(), m -> {});
        LOGGER.info("[MeshMigrationCapture] shot {} (held {}, camera {})", shotName,
                mc.player == null ? "-" : mc.player.getMainHandItem().getItem(), mc.options.getCameraType());
    }
}
