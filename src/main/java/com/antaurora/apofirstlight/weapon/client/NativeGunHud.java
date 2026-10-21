package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfigManager;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimationController;

import java.util.Map;

/**
 * The gun in hand, bottom right (HUD V2 B, design/ui/hud_v2/sheets/hud_v2b_3_gear.png; docs/native_guns/
 * native_gun_hud_layout_v1.md): two right-aligned rows in the frame of gui/layout/native_gun_hud.json (its global block
 * only: place, scale, size; the weapon list, LoadoutHud, stands above it):
 * <ul>
 * <li>the fire mode in a small capsule, then the gun's short name (its model code: the hover name up to the first space,
 * or all of a name the player gave it);</li>
 * <li>the rounds in the magazine large, "/ reserve" small after them. A quarter of the magazine or less: amber. Empty:
 * red, with the reload key cap and "Reload" left of it while there are rounds to load. Reloading: "Reload" in place of
 * the count with an amber bar under it (the action clip the client plays, reload_*, against the gun's reload ticks).</li>
 * <li>a gun that loads from magazine items (weapon/NativeMagazines, the P9-01): the fitted magazine's rounds, a small
 * "+1" after them for the chambered round, and as the reserve the rounds in the spare magazines in the rig and the
 * pockets (CS2 style, user 2026-10-19); the reload prompt while a fuller one is there.</li>
 * </ul>
 * A view of vanilla-synced stack and inventory data; no ammo cache or client writes.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NativeGunHud {
    private NativeGunHud() {}
    public static final int SHOT_FLASH_TICKS = 5;
    /** Text scales (the vanilla font: digits 7 px high at 1): the count, its "/ reserve", the name, the fire mode, the reload word. */
    private static final float COUNT = 1.5F, RESERVE = 0.75F, NAME = 0.75F, MODE = 0.6F, RELOAD_WORD = 1.0F, PROMPT = 0.65F, CAP = 0.7F;
    /** Gaps: between the count and its reserve, the capsule and the name, the two rows. */
    private static final float GAP_RESERVE = 3, GAP_NAME = 4, GAP_ROWS = 4, CAPSULE_H = 7, CAPSULE_PAD = 3;
    private static final int EMPTY = 0xE8867C;

    /** The crosshair's shot kick (NativeDynamicCrosshair); the HUD itself shows no flash. */
    public static void shot(int slot, long gunId) {
        NativeDynamicCrosshair.shot(slot, gunId);
    }

    /** The one prompt drawn without the reload key cap: pressing the key does nothing there. */
    private static final String NO_KEY_PROMPT = "magazine_in_backpack";

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (FieldAttachmentViewState.isActive() || mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || !(mc.player.getMainHandItem().getItem() instanceof NativeGunItem)) return;
        ItemStack stack = mc.player.getMainHandItem();
        String reserve = reserve(stack);
        var definition = ((NativeGunItem) stack.getItem()).definition();
        // the empty gun's prompt: "Reload", or for a magazine-item gun with no fuller spare "Remove magazine" (the same key);
        // a fuller one only in the backpack: "Magazine in backpack", without the key (the key does not reach it)
        String prompt;
        if (com.antaurora.apofirstlight.weapon.NativeMagazines.system(definition))
            prompt = com.antaurora.apofirstlight.weapon.NativeMagazines.canReload(mc.player, stack, definition) ? "reload"
                    : com.antaurora.apofirstlight.weapon.NativeMagazines.inBackpack(mc.player, stack, definition) ? NO_KEY_PROMPT
                    : com.antaurora.apofirstlight.weapon.NativeMagazines.ejectable(mc.player, stack, definition) ? "unload_magazine" : null;
        else prompt = "0".equals(reserve) ? null : "reload";
        render(graphics, width, height, NativeGunHudConfigManager.get(), stack, reserve, prompt, reloadProgress(mc, stack, partialTick));
    };

    private static String reserve(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null) return "0";
        var definition = ((NativeGunItem) stack.getItem()).definition();
        // a gun that loads from magazine items: the rounds in the spare magazines in the rig and the pockets (CS2's
        // reserve), in creative too (its reloads swap real magazines)
        if (com.antaurora.apofirstlight.weapon.NativeMagazines.system(definition))
            return Integer.toString(com.antaurora.apofirstlight.weapon.NativeMagazines.spares(player, definition)[1]);
        if (NativeGunAmmo.infiniteReserve(player.getInventory())) return "∞";
        return Integer.toString(NativeGunAmmo.reserve(player.getInventory(), definition));
    }

    // ---- the reload under way, as the client sees it ----
    private static long reloadGun;
    private static String reloadClip = "";
    private static long reloadStart;

    /** 0..1 while the gun's action clip is a reload (from when this client first saw it), else -1. */
    private static float reloadProgress(Minecraft mc, ItemStack stack, float partialTick) {
        if (mc.level == null || !(stack.getItem() instanceof NativeGunItem gun)) return -1;
        long id = GeoItem.getId(stack);
        var manager = gun.getAnimatableInstanceCache().getManagerForId(id);
        var controller = manager == null ? null : manager.getAnimationControllers().get(NativeGunItem.ACTION_CONTROLLER);
        String clip = controller == null || controller.getTriggeredAnimation() == null || controller.getCurrentAnimation() == null
                || controller.getAnimationState() == AnimationController.State.STOPPED ? "" : controller.getCurrentAnimation().animation().name();
        if (!clip.equals("reload") && !clip.startsWith("reload_")) {
            reloadClip = "";
            return -1;
        }
        long now = mc.level.getGameTime();
        if (id != reloadGun || !clip.equals(reloadClip)) {
            reloadGun = id;
            reloadClip = clip;
            reloadStart = now;
        }
        // the empty reload's clip, or one of its variants (_ads, the skill tiers' _1.._4)
        boolean empty = clip.startsWith(gun.reloadClip(stack, true));
        int ticks = Math.max(1, gun.reloadTicks(stack, empty));
        return Math.max(0, Math.min(1, (now - reloadStart + partialTick) / ticks));
    }

    /** Same renderer for editor previews; never equips or mutates the held gun. */
    public static ItemStack editorStack() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().getItem() instanceof NativeGunItem) return player.getMainHandItem();
        // a new gun is empty (NativeGunAmmo#read); the layout preview's own throwaway sample shows a loaded magazine
        ItemStack sample = com.antaurora.apofirstlight.registry.AflItems.P9_01.get().getDefaultInstance();
        var definition = ((NativeGunItem) sample.getItem()).definition();
        NativeGunAmmo.set(sample, definition, NativeGunAmmo.capacity(sample, definition));
        return sample;
    }

    public static Map<String, NativeGunHudLayout.Box> preview(GuiGraphics graphics, int width, int height, NativeGunHudConfig layout) {
        ItemStack stack = editorStack();
        render(graphics, width, height, layout, stack, reserve(stack), null, -1);
        return Map.of();
    }

    /** The gun's short name: its model code (the hover name to the first space), or the whole name the player gave it. */
    static Component shortName(ItemStack stack) {
        Component name = stack.getHoverName();
        if (stack.hasCustomHoverName()) return name;
        String s = name.getString();
        int space = s.indexOf(' ');
        return space > 0 ? Component.literal(s.substring(0, space)) : name;
    }

    private static void render(GuiGraphics g, int width, int height, NativeGunHudConfig layout, ItemStack stack, String reserve, @org.jetbrains.annotations.Nullable String prompt, float reloading) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        var definition = ((NativeGunItem) stack.getItem()).definition();
        int current = NativeGunAmmo.read(stack, definition);
        boolean empty = current == 0;
        boolean low = !empty && current * 4 <= NativeGunAmmo.capacity(stack, definition);
        // magazine-item guns (NativeMagazines): the magazine's rounds, "+1" for the chambered round
        boolean magazines = com.antaurora.apofirstlight.weapon.NativeMagazines.system(definition);
        String chamber = "";
        if (magazines) {
            int inMagazine = com.antaurora.apofirstlight.weapon.NativeMagazines.magazineRounds(stack, definition);
            var fitted = com.antaurora.apofirstlight.weapon.NativeMagazines.fitted(stack, definition);
            int size = fitted.getItem() instanceof com.antaurora.apofirstlight.weapon.NativeMagazineItem m ? m.capacity() : 1;
            if (com.antaurora.apofirstlight.weapon.NativeMagazines.chamber(stack, definition) > 0) chamber = "+1";
            low = !empty && inMagazine * 4 <= size;
            current = inMagazine;
        }
        var mode = com.antaurora.apofirstlight.weapon.NativeFireModes.current(stack, definition);
        Component modeText = Component.translatable("hud.apocalypse_firstlight.fire_mode." + mode.key());

        var frame = NativeGunHudLayout.frame(layout.global(), width, height);
        g.pose().pushPose();
        try {
            // the frame's bottom-right corner is the origin; everything right-aligned to x = 0, the ammo row on y = 0
            g.pose().translate(frame.x() + frame.width(), frame.y() + frame.height(), 0);
            g.pose().scale(frame.scale(), frame.scale(), 1);
            RenderSystem.enableBlend();

            // ammo row: "/ reserve" at the right, the count (or the reload word) left of it, bottoms on the row's line
            String tail = "/ " + reserve;
            float tailW = font.width(tail) * RESERVE;
            text(g, font, Component.literal(tail), -tailW, -7 * RESERVE, RESERVE, AflUiStyle.TEXT_DIM, 1);
            float chamberW = font.width(chamber) * RESERVE;
            if (!chamber.isEmpty() && reloading < 0)
                text(g, font, Component.literal(chamber), -tailW - GAP_RESERVE - chamberW, -7 * RESERVE, RESERVE, AflUiStyle.TEXT, 1);
            float countRight = -tailW - GAP_RESERVE - (chamber.isEmpty() || reloading >= 0 ? 0 : chamberW + 1), countH = 7 * COUNT;
            if (reloading >= 0) {
                Component word = Component.translatable("hud.apocalypse_firstlight.gun.reload");
                float w = font.width(word) * RELOAD_WORD, h = 8 * RELOAD_WORD;
                text(g, font, word, countRight - w, -h - 2.5F, RELOAD_WORD, AflUiStyle.TEXT, 1);
                AflUiShapes.fill(g, countRight - w, -1.5F, w, 1.5F, 0.75F, AflUiStyle.BACK, 0.6F);
                AflUiShapes.fill(g, countRight - w, -1.5F, Math.max(1.5F, w * reloading), 1.5F, 0.75F, AflUiStyle.ACCENT, 1);
            } else {
                String count = Integer.toString(current);
                float w = font.width(count) * COUNT;
                text(g, font, Component.literal(count), countRight - w, -countH, COUNT, empty ? EMPTY : low ? AflUiStyle.ACCENT : AflUiStyle.TEXT, 1);
                if (empty && prompt != null) {
                    // the reload key cap and "Reload" (or "Remove magazine"), centred on the count's height
                    var cap = AflKeyHint.of(NativeGunInput.reloadKey());
                    Component word = Component.translatable("hud.apocalypse_firstlight.gun." + prompt);
                    float wordW = font.width(word) * PROMPT, capW = cap.width(font) * CAP, mid = -countH / 2;
                    float x = countRight - w - 5 - wordW;
                    text(g, font, word, x, mid - 4 * PROMPT, PROMPT, AflUiStyle.TEXT, 1);
                    if (!NO_KEY_PROMPT.equals(prompt)) {
                        g.pose().pushPose();
                        g.pose().translate(x - 2.5F - capW, mid - AflKeyHint.HEIGHT * CAP / 2, 0);
                        g.pose().scale(CAP, CAP, 1);
                        cap.draw(g, font, 0, 0, 1, System.nanoTime());
                        g.pose().popPose();
                    }
                }
            }

            // name row: the short name at the right, the fire mode capsule left of it
            float top = -countH - GAP_ROWS - CAPSULE_H;
            Component name = shortName(stack);
            float nameW = Math.min(font.width(name) * NAME, 60);
            float nameScale = font.width(name) * NAME > 60 ? 60F / font.width(name) : NAME;
            text(g, font, name, -nameW, top + (CAPSULE_H - 7 * nameScale) / 2, nameScale, AflUiStyle.TEXT_DIM, 1);
            float modeW = font.width(modeText) * MODE, capsuleW = modeW + 2 * CAPSULE_PAD, capsuleX = -nameW - GAP_NAME - capsuleW;
            AflUiShapes.box(g, capsuleX, top, capsuleW, CAPSULE_H, 2.5F, AflUiStyle.BACK, 0.6F, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, 0, 0);
            text(g, font, modeText, capsuleX + CAPSULE_PAD, top + (CAPSULE_H - 8 * MODE) / 2 + 0.3F, MODE, AflUiStyle.TEXT_DIM, 1);
        } finally {
            g.setColor(1, 1, 1, 1);
            g.pose().popPose();
        }
    }

    private static void text(GuiGraphics g, Font font, Component text, float x, float y, float scale, int rgb, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, alpha), true);
        g.pose().popPose();
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> NativeGunHudConfigManager.load(Minecraft.getInstance().getResourceManager()));
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(NativeGunHudConfigManager.reloadListener());
    }
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "native_gun", OVERLAY);
    }
}
