package com.antaurora.apofirstlight.client.workbench;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.Workbench;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the workbench's crafting page (docs/gameplay/afl_workbench_v1_design.md 16): opening it from the bench's
 * top, the bench the inventory page is linked to (its "背包 / 制作" tabs and the inventory key going back), the to-do key
 * and the to-do panel at the top right of the HUD, and the dev command for the sample recipes.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchClient {
    /** F on the crafting page: add the selected recipe to the to-do panel (or take it off). */
    public static final KeyMapping TODO = new KeyMapping("key.apocalypse_firstlight.workbench_todo", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.apocalypse_firstlight");
    /** How far the player may stand from the bench's master cell with the page open (squared, from its centre). */
    private static final double REACH_SQ = 5.5 * 5.5;

    @Nullable private static BlockPos bench;

    private WorkbenchClient() {}

    /** From Workbench#use on the client: the crafting page for this bench. */
    public static void open(BlockPos master) {
        bench = master.immutable();
        Minecraft.getInstance().setScreen(new AflWorkbenchScreen(bench));
    }

    /** The bench the open pages belong to, while the player still stands at it; else null (and the link is dropped). */
    @Nullable
    public static BlockPos linked() {
        var mc = Minecraft.getInstance();
        if (bench == null || mc.player == null || mc.level == null) return null;
        if (!atBench(mc.player, bench)) bench = null;
        return bench;
    }

    public static boolean atBench(Player player, BlockPos master) {
        return player.level().getBlockState(master).getBlock() instanceof Workbench
                && player.distanceToSqr(Vec3.atCenterOf(master)) <= REACH_SQ;
    }

    /** The crafting page's "背包" tab: the inventory page, still linked to the bench. */
    public static void toInventory() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
    }

    /** The inventory page's "制作" tab (or the inventory key there): back to the crafting page. */
    public static boolean toCrafting() {
        BlockPos at = linked();
        if (at == null) return false;
        Minecraft.getInstance().setScreen(new AflWorkbenchScreen(at));
        return true;
    }

    /** Leaving the pages for the world. */
    public static void unlink() { bench = null; }

    // ------------------------------------------------------------------------------------------- the to-do panel

    /**
     * Top right of the HUD: "待做：<item>" and the materials it still needs, have / need, short ones in the warning colour.
     * Hidden with the HUD, in spectator, and while a screen covers it.
     */
    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator() || mc.screen != null) return;
        var recipe = WorkbenchRecipes.find(mc.player, WorkbenchRecipes.pinned());
        if (recipe == null) return;
        drawTodo(g, mc.player, recipe, screenWidth - 6, 6, 1);
    };

    /** The panel with its top-right corner at (right, top); also the crafting page's preview of it. Returns its height. */
    public static float drawTodo(GuiGraphics g, Player player, WorkbenchRecipeView recipe, float right, float top, float alpha) {
        var font = Minecraft.getInstance().font;
        Component title = Component.translatable("screen.apocalypse_firstlight.workbench.todo.title", recipe.result().getHoverName());
        float small = 0.75F, w = 20 + font.width(title);
        var lines = new java.util.ArrayList<Component>();
        var counts = new java.util.ArrayList<Component>();
        var short_ = new java.util.ArrayList<Boolean>();
        for (var m : recipe.materials()) {
            int have = WorkbenchRecipes.carried(player, m);
            lines.add(m.name());
            counts.add(Component.literal(have + " / " + m.count()));
            short_.add(have < m.count());
            w = Math.max(w, 14 + (font.width(lines.get(lines.size() - 1)) + 10 + font.width(counts.get(counts.size() - 1))) * small);
        }
        w += 8;
        float h = 18 + lines.size() * 8 + 3, x = right - w;
        AflUiDraw.panel(g, x, top, w, h, alpha);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 4, top + 3, 0);
        pose.scale(0.75F, 0.75F, 1);
        g.renderItem(recipe.result(), 0, 0);
        pose.popPose();
        AflUiDraw.text(g, font, title, x + 18, top + 6, AflUiStyle.TEXT, alpha);
        for (int i = 0; i < lines.size(); i++) {
            float y = top + 19 + i * 8;
            int rgb = short_.get(i) ? AflUiStyle.WARN : AflUiStyle.TEXT_DIM;
            pose.pushPose();
            pose.translate(x + 18, y, 0);
            pose.scale(small, small, 1);
            g.drawString(font, lines.get(i), 0, 0, AflUiStyle.argb(rgb, alpha), false);
            g.drawString(font, counts.get(i), (int) ((w - 22) / small - font.width(counts.get(i))), 0, AflUiStyle.argb(rgb, alpha), false);
            pose.popPose();
        }
        return h;
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("afl_workbench").then(Commands.literal("preview")
                .then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    boolean on = "on".equals(StringArgumentType.getString(c, "value"));
                    WorkbenchRecipes.setPreview(on);
                    c.getSource().sendSuccess(() -> Component.literal("workbench preview = " + (on ? "on" : "off")), false);
                    return 1;
                }))));
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(TODO); }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "workbench_todo", OVERLAY);
        }
    }
}
