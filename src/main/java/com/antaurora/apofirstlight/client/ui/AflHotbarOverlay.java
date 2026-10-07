package com.antaurora.apofirstlight.client.ui;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflGauge;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The hotbar in the overlay style (docs/ui/afl_overlay_ui_style_v1.md 4.1), replacing vanilla's (AflHudEvents cancels
 * VanillaGuiOverlay.HOTBAR whenever {@link #replaces()}; spectators keep vanilla's spectator bar). Same footprint and
 * item positions as vanilla (182 x 22 at the bottom centre, items at x = centre - 88 + 20 i; one GUI px higher), so the
 * survival cluster, the load bar and the item name keep their places. The selected item lifts and the amber pill
 * follows it ({@link AflItemStrip}); the off-hand item gets its own tile on the off-hand side; the attack indicator, when
 * set to "hotbar", is a small ring gauge where vanilla draws its sword. Items keep vanilla's pick-up pop.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflHotbarOverlay {
    public static final ResourceLocation ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "hotbar");
    private static final AflItemStrip STRIP = new AflItemStrip();
    public static final IGuiOverlay OVERLAY = AflHotbarOverlay::render;

    private AflHotbarOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "hotbar", OVERLAY);
    }

    /** True while this hotbar stands in for vanilla's (every game mode but spectator). */
    public static boolean replaces() {
        var mc = Minecraft.getInstance();
        return mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || !replaces() || !(mc.getCameraEntity() instanceof Player player)) return;
        gui.setupOverlayRenderState(true, false);
        int centre = width / 2, seedBase = 1;
        STRIP.render(g, centre - 90, height - 22, 9, player.getInventory().selected, -1, true, 1,
                (graphics, i, x, y) -> slot(graphics, mc, player, player.getInventory().items.get(i), x, y, partial, seedBase + i));
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm side = player.getMainArm().getOpposite();
        if (!offhand.isEmpty()) {
            float x = side == HumanoidArm.LEFT ? centre - 120 : centre + 98;
            AflUiShapes.box(g, x, height - 23, 22, 22, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA,
                    AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
            slot(g, mc, player, offhand, (int) x + 3, height - 20, partial, 10);
        }
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && mc.player != null) {
            float strength = mc.player.getAttackStrengthScale(0);
            if (strength < 1) {
                float cx = side == HumanoidArm.RIGHT ? centre - 91 - 13 : centre + 91 + 15;
                AflGauge.draw(g, "hotbar_attack", cx, height - 11, 9.5,
                        List.of(AflGauge.Band.gauge(7, 1.6, 0, 0, strength, AflUiStyle.LINE, 0.9)), null);
            }
        }
    }

    /** Vanilla's Gui#renderSlot: the item (with its pick-up pop) and its decorations. */
    private static void slot(GuiGraphics g, Minecraft mc, Player player, ItemStack stack, int x, int y, float partial, int seed) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - partial;
        if (pop > 0) {
            float k = 1 + pop / 5;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0);
            g.pose().scale(1 / k, (k + 1) / 2, 1);
            g.pose().translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0) g.pose().popPose();
        g.renderItemDecorations(mc.font, stack, x, y);
    }
}
