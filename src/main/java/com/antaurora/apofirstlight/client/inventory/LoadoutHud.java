package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfigManager;
import com.antaurora.apofirstlight.client.ui.AflItemStrip;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.Loadout;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import com.antaurora.apofirstlight.weapon.client.NativeGunHudLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The Survival / Adventure HUD of weapons and quick bindings (docs/ui/afl_inventory_grid_v0_plan.md 10):
 * <ul>
 * <li>the bottom bar (in place of the hotbar, AflHotbarOverlay): six quick bindings, keys 4-9, each the bound item and
 * how many of it the pockets and rig hold (with the hand); greyed when none are left; the one in the hand lifted with the
 * amber pill;</li>
 * <li>the weapon list at the bottom right, above the gun HUD: the weapon positions 1-3 top to bottom as white silhouettes
 * facing left (WeaponSilhouettes), key numbers on the right; the one in hand large and bright, the others small; a
 * weapon that may not stay where it is (no sling or holster) faint.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LoadoutHud {
    public static final ResourceLocation WEAPON_LIST = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "weapon_list");
    private static final AflItemStrip BINDS = new AflItemStrip();
    private static final float HELD_H = 12, OTHER_H = 7, ROW_GAP = 3, MAX_W = 52, KEY_COLUMN = 8;

    private LoadoutHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "weapon_list", LoadoutHud::weaponList);
    }

    /** Whether the bind bar stands in for the hotbar (Survival / Adventure). */
    public static boolean active(Player player) {
        return !PlayerStorageCapacity.unrestricted(player);
    }

    /** How many of each bound kind the pockets and rig hold, plus the stack in the hand. */
    static Map<Item, Integer> totals(Player player, Item[] binds) {
        Map<Item, Integer> totals = new HashMap<>();
        for (Item kind : binds) if (kind != null) totals.put(kind, 0);
        if (totals.isEmpty()) return totals;
        var view = PlayerGrids.view(player);
        for (var target : new PlayerGrids.Target[]{PlayerGrids.Target.POCKETS, PlayerGrids.Target.RIG}) {
            var storage = view.get(target);
            if (storage == null) continue;
            for (var e : storage.entries()) totals.computeIfPresent(e.stack().getItem(), (k, n) -> n + e.stack().getCount());
        }
        var hand = player.getInventory().items.get(HotbarRoles.HAND_SLOT);
        if (!hand.isEmpty()) totals.computeIfPresent(hand.getItem(), (k, n) -> n + hand.getCount());
        return totals;
    }

    /** The bottom bar: six bindings at the hotbar's place (cells 20 px, centred). */
    public static void bindBar(GuiGraphics g, Minecraft mc, Player player, int width, int height) {
        var state = Loadout.client();
        var binds = state.binds();
        var totals = totals(player, binds);
        var inventory = player.getInventory();
        int active = inventory.selected == HotbarRoles.HAND_SLOT && state.handBind() >= 0 ? state.handBind() : -1;
        float x0 = width / 2F - Loadout.BINDS * 10;
        BINDS.render(g, x0, height - 22, Loadout.BINDS, active, -1, true, 1, (graphics, i, x, y) -> {
            Item kind = binds[i];
            var pose = graphics.pose();
            if (kind != null) {
                int total = totals.getOrDefault(kind, 0);
                graphics.renderItem(new ItemStack(kind), x, y, 20 + i);
                pose.pushPose();
                pose.translate(0, 0, 200);
                if (total == 0) AflUiShapes.fill(graphics, x - 1, y - 1, 18, 18, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.62F);
                if (total != 1) {
                    String n = Integer.toString(total);
                    graphics.drawString(mc.font, n, x + 17 - mc.font.width(n), y + 9, total == 0 ? AflUiStyle.TEXT_DISABLED : 0xFFFFFF, true);
                }
                pose.popPose();
            }
            pose.pushPose();
            pose.translate(x - 1, y - 1, 210);
            pose.scale(0.5F, 0.5F, 1);
            graphics.drawString(mc.font, Integer.toString(4 + i), 1, 1, AflUiStyle.argb(i == active ? AflUiStyle.TEXT_DIM : AflUiStyle.TEXT_DISABLED, 1), false);
            pose.popPose();
        });
    }

    private static void weaponList(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || FieldAttachmentViewState.isActive() || !(mc.getCameraEntity() instanceof Player player)
                || !active(player) || player.isSpectator()) return;
        var inventory = player.getInventory();
        int rows = 0;
        float total = 0;
        for (int i = 0; i < HotbarRoles.HAND_SLOT; i++) {
            if (inventory.items.get(i).isEmpty()) continue;
            total += (rows > 0 ? ROW_GAP : 0) + (i == inventory.selected ? HELD_H : OTHER_H);
            rows++;
        }
        if (rows == 0) return;
        gui.setupOverlayRenderState(true, false);
        var frame = NativeGunHudLayout.frame(NativeGunHudConfigManager.get().global(), width, height);
        // always above the gun HUD's frame (HUD V2 B): it does not jump when the hand changes; without a gun the corner
        // under it stays free (the Geiger counter's panel, 24 high, 8 from the edges, takes it while in hand)
        float right = Math.min(width - 4, frame.x() + frame.width());
        float bottom = frame.y() - 4;
        float y = bottom - total;
        RenderSystem.enableBlend();
        for (int i = 0; i < HotbarRoles.HAND_SLOT; i++) {
            var stack = inventory.items.get(i);
            if (stack.isEmpty()) continue;
            boolean held = i == inventory.selected;
            boolean faint = !held && PlayerStorageCapacity.isOverflow(inventory, i);
            float h = held ? HELD_H : OTHER_H, alpha = held ? 1 : faint ? 0.3F : 0.65F;
            float iconRight = right - KEY_COLUMN;
            var shape = WeaponSilhouettes.of(stack);
            float w;
            if (shape != null && shape.height() > 0) {
                w = Math.min(MAX_W, h * shape.width() / shape.height());
                float drawH = w * shape.height() / shape.width();
                var pose = g.pose();
                pose.pushPose();
                pose.translate(iconRight - w, y + (h - drawH) / 2, 0);
                pose.scale(w, drawH, 1);
                g.setColor(1, 1, 1, alpha);
                g.blit(shape.texture(), 0, 0, 1, 1, 0, 0, 1, 1, 1, 1);
                g.setColor(1, 1, 1, 1);
                pose.popPose();
            } else {
                w = h;
                var pose = g.pose();
                pose.pushPose();
                pose.translate(iconRight - w, y, 0);
                pose.scale(h / 16, h / 16, 1);
                g.renderItem(stack, 0, 0);
                pose.popPose();
            }
            text(g, mc, Component.literal(Integer.toString(i + 1)), right - KEY_COLUMN + 3, y + h / 2 - 2.7F, 0.6F,
                    AflUiStyle.argb(held ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, held ? 1 : 0.8F));
            if (faint) {
                var why = Component.translatable(i == HotbarRoles.PISTOL_SLOT ? "hud.apocalypse_firstlight.loadout.no_holster"
                        : "hud.apocalypse_firstlight.loadout.no_sling");
                float s = 0.55F;
                text(g, mc, why, iconRight - w - 3 - mc.font.width(why) * s, y + h / 2 - 2.5F, s,
                        AflUiStyle.argb(AflUiStyle.TEXT_DISABLED, 1));
            }
            y += h + ROW_GAP;
        }
    }

    private static void text(GuiGraphics g, Minecraft mc, Component text, float x, float y, float scale, int argb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(mc.font, text, 0, 0, argb, true);
        pose.popPose();
    }
}
