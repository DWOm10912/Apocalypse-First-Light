package com.antaurora.apofirstlight.client.book;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.book.GuideBookPackets;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client: the readable books the server sent (GuideBookPackets.Books), opening one in the reader, and the "recipe
 * learned" note (docs/gameplay/field_survival_guide_v1_design.md 5): top right, about four seconds, then it fades; the
 * reader draws it itself while open, the HUD overlay otherwise.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuideBookClient {
    private static final Map<ResourceLocation, GuideBook> BY_ITEM = new HashMap<>();
    private static final long NOTE_NANOS = 4_000_000_000L, FADE_NANOS = 600_000_000L;
    @Nullable private static Component noteItem;
    private static long noteAt;

    private GuideBookClient() {}

    public static void receive(List<GuideBookPackets.Books.Entry> books) {
        BY_ITEM.clear();
        for (var e : books) {
            try {
                JsonObject root = JsonParser.parseString(e.json()).getAsJsonObject();
                GuideBook book = GuideBook.parse(e.id(), root);
                BY_ITEM.put(book.item(), book);
            } catch (RuntimeException ex) {
                ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] Could not read book {}: {}", e.id(), ex.getMessage());
            }
        }
    }

    /** Use on a book: opens the reader at the cover. */
    public static void open(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        GuideBook book = BY_ITEM.get(ForgeRegistries.ITEMS.getKey(item));
        if (book == null) {
            ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] No book file for {}", ForgeRegistries.ITEMS.getKey(item));
            return;
        }
        mc.setScreen(new GuideBookScreen(book, item));
        pageTurn(0.9F);
    }

    /** The page turn, vanilla's book sound at the map's settings; a lower pitch when the book shuts. */
    static void pageTurn(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, 0.8F));
    }

    /** The server taught a recipe from the book: the note names its result. */
    public static void learned(ResourceLocation result) {
        Item item = ForgeRegistries.ITEMS.getValue(result);
        noteItem = item == null || item == Items.AIR ? Component.literal(result.toString()) : item.getDescription();
        noteAt = System.nanoTime();
    }

    /** The note at the top right of a screen this wide; nothing once it has faded. */
    static void drawNote(GuiGraphics g, Font font, int width) {
        if (noteItem == null) return;
        long age = System.nanoTime() - noteAt;
        if (age > NOTE_NANOS) {
            noteItem = null;
            return;
        }
        float alpha = Math.min(1, Math.min(age / 150_000_000F, (NOTE_NANOS - age) / (float) FADE_NANOS));
        Component head = Component.translatable("message.apocalypse_firstlight.book.learned");
        Component where = Component.translatable("message.apocalypse_firstlight.book.learned_where");
        float w = Math.max(font.width(head), Math.max(font.width(noteItem), font.width(where))) + 16, h = 42;
        float x = width - w - 8, y = 8;
        AflUiDraw.panel(g, x, y, w, h, alpha);
        AflUiDraw.text(g, font, head, x + 8, y + 6, AflUiStyle.ACCENT, alpha);
        AflUiDraw.text(g, font, noteItem, x + 8, y + 18, AflUiStyle.TEXT, alpha);
        AflUiDraw.text(g, font, where, x + 8, y + 30, AflUiStyle.TEXT_DIM, alpha);
    }

    private static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof GuideBookScreen) return;
        drawNote(g, mc.font, width);
    };

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BY_ITEM.clear();
        noteItem = null;
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "guide_book_learned", OVERLAY);
        }
    }
}
