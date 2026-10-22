package com.antaurora.apofirstlight.client.building;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.building.BuildingActions;
import com.antaurora.apofirstlight.building.BuildingCatalog;
import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.building.BuildingRules;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.CarriedItems;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The building hammer in the player's hand (building hammer B0, docs/gameplay/building_hammer_b0.md): the chosen catalog
 * piece, its translucent preview on the block grid (light when it can go there, red when not, with the reason on the HUD
 * panel), the turn key, and the clicks: right click opens the catalog (no piece chosen, or with sneak) or installs the
 * piece; left click on a catalog piece takes it down. The server decides (BuildingActions); this side only asks.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class BuildingHammerClient {
    /** Active only with the building hammer in hand and no screen open: it never competes with R for reloading. */
    private static final IKeyConflictContext HAMMER = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            var mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && BuildingActions.holdingHammer(mc.player);
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) { return false; }
    };
    public static final KeyMapping ROTATE = new KeyMapping("key.apocalypse_firstlight.building_rotate", HAMMER,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.apocalypse_firstlight");
    /** In and out of edit mode; like the turn key, only with the hammer in hand (B switches fire modes only with a gun). */
    public static final KeyMapping MODE = new KeyMapping("key.apocalypse_firstlight.building_mode", HAMMER,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.apocalypse_firstlight");
    private static final long FLASH_TICKS = 40;

    /**
     * Edit mode (user 2026-10-21: installing and hanging the hammer on a peg both wanted the right click). Out of it the
     * hammer is a plain tool: no preview, the clicks go to the world as with any item (hang it on a rack or the bench,
     * pick things up, mine at hand speed). In it the hammer takes the clicks, and the block hints and ground pickups give
     * way. Kept while the hammer is put away and taken out again; off when leaving the world.
     */
    private static boolean editing;
    @Nullable private static ResourceLocation selected;
    /** The turn relative to the player: pillars 0 along the look, 1 across, 2 upright; facings in quarter steps. */
    private static int turn;
    @Nullable private static BuildingRules.Placement target;
    @Nullable private static String problem;
    @Nullable private static BlockPos removable;
    private static long ticks, lastUse = -100, lastAttack = -100, flashUntil;
    @Nullable private static Component flash;
    private static boolean rotateDown, modeDown;

    private BuildingHammerClient() {}

    /** The player holds the building hammer in edit mode: its clicks are the hammer's, not the world's. */
    public static boolean editing(@Nullable net.minecraft.world.entity.player.Player player) {
        return editing && player != null && BuildingActions.holdingHammer(player);
    }

    @Nullable
    public static BuildingCatalog.Piece selected() {
        return selected == null ? null : BuildingCatalog.byId(selected);
    }

    /** From the catalog: choose a piece (null puts it away). */
    public static void select(@Nullable BuildingCatalog.Piece piece) {
        selected = piece == null ? null : piece.id();
        turn = 0;
    }

    /** The absolute orientation index (BuildingCatalog.Piece#orientations) of the relative turn, seen by this player. */
    private static int orientation(BuildingCatalog.Piece piece, net.minecraft.world.entity.player.Player player) {
        var states = piece.orientations();
        if (states.isEmpty()) return 0;
        var first = states.get(0);
        Direction facing = player.getDirection();
        if (first.hasProperty(BlockStateProperties.AXIS)) {
            int along = facing.getAxis() == Direction.Axis.X ? 0 : 1;
            return switch (Math.floorMod(turn, 3)) { case 0 -> along; case 1 -> 1 - along; default -> 2; };
        }
        if (first.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            int towards = 0;
            for (int i = 0; i < states.size(); i++) if (states.get(i).getValue(BlockStateProperties.HORIZONTAL_FACING) == facing.getOpposite()) towards = i;
            return Math.floorMod(towards + turn, states.size());
        }
        return 0;
    }

    // ------------------------------------------------------------------------------------------- tick and clicks

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        target = null;
        problem = null;
        removable = null;
        if (player == null) editing = false;
        boolean held = player != null && mc.level != null && BuildingActions.holdingHammer(player);
        boolean modeNow = held && mc.screen == null && physicallyDown(mc, MODE);
        if (modeNow && !modeDown) {
            editing = !editing;
            flash = null;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), editing ? 1.2F : 0.9F, 0.3F));
        }
        modeDown = modeNow;
        held &= editing;
        boolean down = held && mc.screen == null && physicallyDown(mc, ROTATE);
        var piece = held ? selected() : null;
        if (down && !rotateDown && piece != null && piece.orientations().size() > 1) {
            turn = Math.floorMod(turn + 1, piece.orientations().size());
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.25F));
        }
        rotateDown = down;
        if (!held || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        var there = mc.level.getBlockState(pos);
        if (BuildingCatalog.byBlock(there.getBlock()) != null) removable = pos;
        if (piece == null) return;
        BlockPos at = there.canBeReplaced() ? pos : pos.relative(hit.getDirection());
        target = new BuildingRules.Placement(at, orientation(piece, player));
        problem = BuildingRules.placeProblem(player, piece, target);
    }

    private static boolean physicallyDown(Minecraft mc, KeyMapping mapping) {
        var key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }

    /**
     * In edit mode the hammer takes the use and attack clicks. Held buttons repeat the event (use every 4 ticks, attack
     * every tick and twice on the first): only a fresh press acts, so one click installs or takes down one piece.
     */
    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !editing(player)) return;
        if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            boolean fresh = ticks - lastUse > 5;
            lastUse = ticks;
            if (!fresh) return;
            var piece = selected();
            if (piece == null || player.isShiftKeyDown()) {
                mc.setScreen(new BuildingCatalogScreen());
                return;
            }
            if (target == null) return;
            if (problem != null) {
                flash(reason(problem, piece));
                return;
            }
            AflNetwork.buildingToServer(new BuildingPackets.Place(piece.id(), target.pos(), target.orientation()));
            player.swing(InteractionHand.MAIN_HAND);
        } else if (event.isAttack()) {
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;   // mobs: a plain hit
            event.setCanceled(true);
            event.setSwingHand(false);
            boolean fresh = ticks - lastAttack > 1;
            lastAttack = ticks;
            if (!fresh) return;
            String why = BuildingRules.removeProblem(player, hit.getBlockPos());
            if (why != null) {
                flash(reason(why, null));
                return;
            }
            AflNetwork.buildingToServer(new BuildingPackets.Remove(hit.getBlockPos()));
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static Component reason(String key, @Nullable BuildingCatalog.Piece piece) {
        var item = piece == null ? null : piece.item();
        return Component.translatable("message.apocalypse_firstlight.building." + key, item == null ? Component.empty() : new ItemStack(item).getHoverName());
    }

    private static void flash(Component text) {
        flash = text;
        flashUntil = ticks + FLASH_TICKS;
    }

    // ------------------------------------------------------------------------------------------- the preview

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || target == null) return;
        var piece = selected();
        var mc = Minecraft.getInstance();
        if (piece == null || mc.level == null) return;
        var state = BuildingRules.placed(mc.level, piece, target);
        if (state == null) return;
        boolean ok = problem == null;
        var cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(target.pos().getX() - cam.x, target.pos().getY() - cam.y, target.pos().getZ() - cam.z);
        // a hair larger about the block's centre, so the preview never fights the faces it touches
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(1.002F, 1.002F, 1.002F);
        pose.translate(-0.5, -0.5, -0.5);
        var buffers = mc.renderBuffers().bufferSource();
        var consumer = new Tinted(buffers.getBuffer(RenderType.translucent()), ok ? 0.78F : 1.0F, ok ? 0.95F : 0.42F, ok ? 0.82F : 0.38F, ok ? 0.5F : 0.45F);
        var dispatcher = mc.getBlockRenderer();
        dispatcher.getModelRenderer().renderModel(pose.last(), consumer, state, dispatcher.getBlockModel(state), 1, 1, 1,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
        pose.popPose();
        buffers.endBatch(RenderType.translucent());
    }

    /** Multiplies every vertex colour by a tint and sets its alpha: the block model drawn as a coloured glass copy. */
    private record Tinted(VertexConsumer inner, float r, float g, float b, float a) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { inner.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int red, int green, int blue, int alpha) {
            inner.color((int) (red * r), (int) (green * g), (int) (blue * b), (int) (255 * a));
            return this;
        }
        @Override public VertexConsumer uv(float u, float v) { inner.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { inner.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { inner.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { inner.normal(x, y, z); return this; }
        @Override public void endVertex() { inner.endVertex(); }
        @Override public void defaultColor(int red, int green, int blue, int alpha) { inner.defaultColor(red, green, blue, alpha); }
        @Override public void unsetDefaultColor() { inner.unsetDefaultColor(); }
    }

    // ------------------------------------------------------------------------------------------- the HUD panel

    /**
     * Above the hotbar while the hammer is in hand, clear of the action bar line (vanilla draws it 68 above the bottom).
     * Out of edit mode only the key into it. In edit mode: the chosen piece and how many the player carries, then what the
     * clicks do now (or why the piece cannot go there, in the warning colour), the take-down line over a catalog piece,
     * and the key out.
     */
    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.options.hideGui || player == null || player.isSpectator() || mc.screen != null || !BuildingActions.holdingHammer(player)
                || com.antaurora.apofirstlight.client.workbench.WorkbenchWork.active()) return;   // the work's own panel there
        if (editing) drawPanel(g, mc, player, screenWidth / 2F, screenHeight - 77);
        else drawModeHint(g, mc, screenWidth / 2F, screenHeight - 77);
    };

    private static void drawModeHint(GuiGraphics g, Minecraft mc, float cx, float bottom) {
        var font = mc.font;
        var cap = AflKeyHint.of(MODE);
        Component text = label("edit");
        float w = keyWidth(font, new AflKeyHint[]{cap}, text) + 12, h = 17;
        float x = cx - w / 2, y = bottom - h;
        AflUiDraw.panel(g, x, y, w, h, 0.85F);
        cap.draw(g, font, x + 6, y + 3, 1, System.nanoTime());
        AflUiDraw.text(g, font, text, x + 6 + cap.width(font) + 3, y + 5, AflUiStyle.TEXT_DIM, 1);
    }

    private static void drawPanel(GuiGraphics g, Minecraft mc, net.minecraft.world.entity.player.Player player, float cx, float bottom) {
        var font = mc.font;
        long now = System.nanoTime();
        var piece = selected();
        var item = piece == null ? null : piece.item();
        Component title = item == null
                ? Component.translatable("hud.apocalypse_firstlight.building.none")
                : new ItemStack(item).getHoverName();
        Component count = item == null ? null
                : Component.translatable("hud.apocalypse_firstlight.building.carried", CarriedItems.count(player, item));
        // lines: [caps + labels] pairs
        java.util.List<Object[]> keys = new java.util.ArrayList<>();
        Component warn = null;
        if (flash != null && ticks < flashUntil) warn = flash;
        else if (piece != null && target != null && problem != null) warn = reason(problem, piece);
        if (piece == null) {
            keys.add(new Object[]{new AflKeyHint[]{AflKeyHint.MOUSE_RIGHT}, "catalog"});
        } else {
            if (warn == null) keys.add(new Object[]{new AflKeyHint[]{AflKeyHint.MOUSE_RIGHT}, "install"});
            if (piece.orientations().size() > 1) keys.add(new Object[]{new AflKeyHint[]{AflKeyHint.of(ROTATE)}, "rotate"});
            keys.add(new Object[]{new AflKeyHint[]{AflKeyHint.of(mc.options.keyShift), AflKeyHint.MOUSE_RIGHT}, "catalog"});
        }
        if (removable != null) keys.add(new Object[]{new AflKeyHint[]{AflKeyHint.MOUSE_LEFT}, "remove"});
        keys.add(new Object[]{new AflKeyHint[]{AflKeyHint.of(MODE)}, "exit"});

        float keysW = 0;
        for (Object[] k : keys) keysW += keyWidth(font, (AflKeyHint[]) k[0], label((String) k[1])) + 8;
        keysW = Math.max(0, keysW - 8);
        float titleW = (item == null ? 0 : 14) + font.width(title) + (count == null ? 0 : 8 + font.width(count));
        float w = Math.max(Math.max(titleW, keysW), warn == null ? 0 : font.width(warn)) + 12;
        float h = 6 + 12 + (warn == null ? 0 : 11) + 14 + 3;
        float x = cx - w / 2, y = bottom - h;
        AflUiDraw.panel(g, x, y, w, h, 1);
        float ty = y + 5;
        float tx = x + 6;
        if (item != null) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(tx, ty - 1, 0);
            pose.scale(0.75F, 0.75F, 1);
            g.renderItem(new ItemStack(item), 0, 0);
            pose.popPose();
            tx += 14;
        }
        AflUiDraw.text(g, font, title, tx, ty + 1, item == null ? AflUiStyle.TEXT_DIM : AflUiStyle.TEXT, 1);
        if (count != null) AflUiDraw.text(g, font, count, tx + font.width(title) + 8, ty + 1, AflUiStyle.TEXT_DIM, 1);
        ty += 13;
        if (warn != null) {
            AflUiDraw.text(g, font, warn, x + 6, ty, AflUiStyle.WARN, 1);
            ty += 11;
        }
        float kx = x + 6;
        for (Object[] k : keys) {
            var caps = (AflKeyHint[]) k[0];
            Component text = label((String) k[1]);
            for (AflKeyHint cap : caps) {
                cap.draw(g, font, kx, ty, 1, now);
                kx += cap.width(font) + 2;
            }
            AflUiDraw.text(g, font, text, kx + 1, ty + 2, AflUiStyle.TEXT, 1);
            kx += 1 + font.width(text) + 8;
        }
    }

    private static Component label(String key) {
        return Component.translatable("hud.apocalypse_firstlight.building." + key);
    }

    private static float keyWidth(net.minecraft.client.gui.Font font, AflKeyHint[] caps, Component text) {
        float w = 0;
        for (AflKeyHint cap : caps) w += cap.width(font) + 2;
        return w + 1 + font.width(text);
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(MODE);
            event.register(ROTATE);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "building_hammer", OVERLAY);
        }
    }
}
