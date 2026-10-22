package com.antaurora.apofirstlight.client.book;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.book.GuideBookPackets;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.workbench.WorkbenchRecipes;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reader, version A (docs/gameplay/field_survival_guide_v1_design.md 4): the open book as a flat two-page spread
 * over the dimmed world (no blur, the game keeps running). The cover alone first, then spreads: the inside cover and
 * the contents, then the text, even pages on the left. Turning: A / D or the arrow keys, Page Up / Down, the wheel, a
 * click on the left or right page; Tab the contents, a contents row its chapter; Esc shuts it. Being hurt, sprinting or
 * no longer holding the book shuts it too. Staying three seconds on a spread with a section that teaches a recipe the
 * survivor does not know reports it to the server (GuideBookPackets.Read), once per section per opening.
 */
final class GuideBookScreen extends Screen {
    private static final int[] OVERSAMPLE = {2, 3, 4, 6, 10};
    private static final ResourceLocation[] FONTS = new ResourceLocation[OVERSAMPLE.length];
    static {
        for (int i = 0; i < OVERSAMPLE.length; i++) FONTS[i] = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "book_" + OVERSAMPLE[i]);
    }
    /** Ticks on a spread before its recipes count as read. */
    private static final int READ_TICKS = 60;
    private static final int SHADE = 0x5A4A30;
    /** GUI px kept free at the bottom for the hotbar. */
    private static final int HOTBAR = 26;
    /** The hint bar's size against the HUD's key hints. */
    private static final float HINT_SCALE = 0.65F;

    private final GuideBook book;
    private final Item item;
    private List<GuideBookLayout.Page> pages = List.of();
    private String language = "";
    private int spread, dwell;
    private final Set<String> sent = new HashSet<>();
    private final Set<ResourceLocation> filtered = new HashSet<>();
    private float health = -1;
    /** Spread placement this frame: page scale (GUI px per page unit), left edge of the left page, top. */
    private float u, ox, oy;

    GuideBookScreen(GuideBook book, Item item) {
        super(item.getDescription());
        this.book = book;
        this.item = item;
    }

    @Override
    protected void init() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (pages.isEmpty() || !lang.equals(language)) {
            language = lang;
            pages = GuideBookLayout.lay(book, font, lang);
            spread = Math.min(spread, lastSpread());
        }
    }

    private int lastSpread() {
        return pages.size() / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ turning

    private void turnTo(int s) {
        s = Math.max(0, Math.min(lastSpread(), s));
        if (s == spread) return;
        spread = s;
        dwell = 0;
        GuideBookClient.pageTurn(0.9F + (float) Math.random() * 0.1F);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        var o = Minecraft.getInstance().options;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP || o.keyLeft.matches(key, scan)) {
            turnTo(spread - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN || o.keyRight.matches(key, scan)) {
            turnTo(spread + 1);
            return true;
        }
        if (o.keyPlayerList.matches(key, scan)) {
            turnTo(1);
            return true;
        }
        if (o.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        AflKeyHint.scrolled();
        turnTo(spread + (delta > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (spread == 0) {
            turnTo(1);
            return true;
        }
        if (mx < ox || mx > ox + 2 * GuideBookLayout.PW * u || my < oy || my > oy + GuideBookLayout.PH * u) return true;
        boolean right = mx >= ox + GuideBookLayout.PW * u;
        GuideBookLayout.Page page = page(right ? 2 * spread : 2 * spread - 1);
        if (page != null) {
            float py = (float) (my - oy) / u;
            for (GuideBookLayout.Link l : page.links) if (py >= l.y0() && py <= l.y1()) {
                turnTo((l.page() + 1) / 2);
                return true;
            }
        }
        turnTo(spread + (right ? 1 : -1));
        return true;
    }

    @Override
    public void onClose() {
        GuideBookClient.pageTurn(0.75F);
        super.onClose();
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || !player.getMainHandItem().is(item) || player.isSprinting()
                || health >= 0 && player.getHealth() < health) {
            onClose();
            return;
        }
        health = player.getHealth();
        if (player.isCreative()) return;   // creative knows every recipe already
        Set<ResourceLocation> known = new HashSet<>();
        for (var r : WorkbenchRecipes.known(player)) known.add(r.id());
        Map<String, GuideBook.Section> teaching = new LinkedHashMap<>();
        for (int i : new int[]{2 * spread - 1, 2 * spread}) {
            GuideBookLayout.Page page = page(i);
            if (page == null) continue;
            for (GuideBook.Section s : page.sections)
                if (!sent.contains(s.id()) && s.teaches().stream().anyMatch(r -> !known.contains(r))) teaching.put(s.id(), s);
        }
        if (teaching.isEmpty()) {
            dwell = 0;
            return;
        }
        if (++dwell < READ_TICKS) return;
        for (String id : teaching.keySet()) {
            sent.add(id);
            AflNetwork.buildingToServer(new GuideBookPackets.Read(book.item(), id));
        }
    }

    private GuideBookLayout.Page page(int index) {
        return index >= 0 && index < pages.size() ? pages.get(index) : null;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
        float pw = GuideBookLayout.PW, ph = GuideBookLayout.PH;
        u = Math.min(0.80F * height / ph, 0.86F * width / (2 * pw));
        ox = (width - 2 * pw * u) / 2;
        // room under the spread for the hint bar, and under that the hotbar, which stays on screen
        oy = Math.max(6, (height - HOTBAR - 18 - ph * u) / 2);

        if (spread == 0) {
            // the shut book: the cover alone, centred
            float cx = (width - pw * u) / 2;
            shadow(g, cx, oy, pw * u, ph * u);
            image(g, cover(), cx, oy, pw * u, ph * u, false);
        } else {
            shadow(g, ox, oy, 2 * pw * u, ph * u);
            drawPage(g, page(2 * spread - 1), ox, true);
            drawPage(g, page(2 * spread), ox + pw * u, false);
        }
        hints(g);
        GuideBookClient.drawNote(g, font, width);
    }

    private ResourceLocation cover() {
        String lang = book.coverLanguages().contains(language) ? language : book.coverFallback();
        return new ResourceLocation(book.cover().replace("{lang}", lang));
    }

    private void drawPage(GuiGraphics g, GuideBookLayout.Page page, float x, boolean left) {
        float pw = GuideBookLayout.PW, ph = GuideBookLayout.PH;
        image(g, book.paper(), x, oy, pw * u, ph * u, left);
        // the gutter: the paper darkens towards the spine
        for (int i = 0; i < 14; i++) {
            float d = i * 10, a = 0.30F * (1 - i / 14F) * (1 - i / 14F);
            rect(g, left ? x + (pw - d - 10) * u : x + d * u, oy, 10 * u, ph * u, SHADE, a);
        }
        if (page == null) return;
        for (GuideBookLayout.Op op : page.ops) {
            if (op instanceof GuideBookLayout.Rect r) {
                rect(g, x + r.x() * u, oy + r.y() * u, r.w() * u, r.h() * u, r.rgb(), r.alpha());
            } else if (op instanceof GuideBookLayout.Image im) {
                image(g, im.texture(), x + im.x() * u, oy + im.y() * u, im.w() * u, im.h() * u, false);
            } else if (op instanceof GuideBookLayout.Text t) {
                text(g, t, x);
            }
        }
    }

    /** A text run: the book font whose oversampling matches the size on screen, scaled to its em in page units. */
    private void text(GuiGraphics g, GuideBookLayout.Text t, float pageX) {
        if (t.text().isEmpty()) return;
        float k = t.size() * u / GuideBookLayout.EM;
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        int f = FONTS.length - 1;
        for (int i = 0; i < OVERSAMPLE.length; i++) if (OVERSAMPLE[i] >= k * gui * 0.92) { f = i; break; }
        Component c = Component.literal(t.text()).withStyle(Style.EMPTY.withFont(FONTS[f]).withBold(t.bold()));
        float w = font.width(c) * k;
        float x = pageX + t.x() * u - (t.anchor().equals("end") ? w : t.anchor().equals("middle") ? w / 2 : 0);
        g.pose().pushPose();
        g.pose().translate(x, oy + t.y() * u - GuideBookLayout.BASELINE * k, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, c, 0, 0, 0xFF000000 | t.rgb(), false);
        g.pose().popPose();
    }

    private void rect(GuiGraphics g, float x, float y, float w, float h, int rgb, float alpha) {
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        int r = rgb >> 16 & 255, gr = rgb >> 8 & 255, b = rgb & 255, a = Math.round(alpha * 255);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, x, y, 0).color(r, gr, b, a).endVertex();
        buf.vertex(m, x, y + h, 0).color(r, gr, b, a).endVertex();
        buf.vertex(m, x + w, y + h, 0).color(r, gr, b, a).endVertex();
        buf.vertex(m, x + w, y, 0).color(r, gr, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /** A texture stretched over a GUI rectangle, smoothly filtered (the pages are drawn far from 1:1); mirrored on request. */
    private void image(GuiGraphics g, ResourceLocation texture, float x, float y, float w, float h, boolean mirror) {
        g.flush();
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (filtered.add(texture)) tex.setFilter(true, false);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float u0 = mirror ? 1 : 0, u1 = mirror ? 0 : 1;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(m, x, y, 0).uv(u0, 0).endVertex();
        buf.vertex(m, x, y + h, 0).uv(u0, 1).endVertex();
        buf.vertex(m, x + w, y + h, 0).uv(u1, 1).endVertex();
        buf.vertex(m, x + w, y, 0).uv(u1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /** A soft shadow round the book: a few widening translucent frames. */
    private void shadow(GuiGraphics g, float x, float y, float w, float h) {
        for (int i = 6; i >= 1; i--) {
            float d = i * 1.5F;
            rect(g, x - d, y - d + 2, w + 2 * d, h + 2 * d, 0x000000, 0.06F);
        }
    }

    /** The bar under the book: where you are, and the keys. */
    private void hints(GuiGraphics g) {
        var o = Minecraft.getInstance().options;
        long now = System.nanoTime();
        Component where;
        int last = pages.size() - 2;
        if (spread == 0) where = Component.translatable("screen.apocalypse_firstlight.book.cover");
        else {
            int a = 2 * spread - 2, b = Math.min(2 * spread - 1, last);
            where = a < 1 || a == b ? Component.translatable("screen.apocalypse_firstlight.book.page", Math.max(1, b), last)
                    : Component.translatable("screen.apocalypse_firstlight.book.pages", a, b, last);
        }
        Object[][] keys = {
                {AflKeyHint.of(o.keyLeft), "previous"}, {AflKeyHint.of(o.keyRight), "next"},
                {AflKeyHint.of(o.keyPlayerList), "contents"}, {AflKeyHint.fixed(GLFW.GLFW_KEY_ESCAPE, "Esc"), "close"}};
        float w = font.width(where) + 14;
        for (Object[] k : keys) w += ((AflKeyHint) k[0]).width(font) + 4 + font.width(label((String) k[1])) + 10;
        // the bar is drawn at HINT_SCALE: a quiet line under the book, smaller than the HUD's own key hints
        float top = Math.min(height - HOTBAR - 11 * HINT_SCALE - 3, oy + GuideBookLayout.PH * u + 5);
        g.pose().pushPose();
        g.pose().translate((width - w * HINT_SCALE) / 2, top, 0);
        g.pose().scale(HINT_SCALE, HINT_SCALE, 1);
        float x = 0, y = 0;
        AflUiDraw.text(g, font, where, x, y + 2, AflUiStyle.TEXT_DIM, 1);
        x += font.width(where) + 14;
        for (Object[] k : keys) {
            AflKeyHint cap = (AflKeyHint) k[0];
            cap.draw(g, font, x, y, 1, now);
            x += cap.width(font) + 4;
            Component l = label((String) k[1]);
            AflUiDraw.text(g, font, l, x, y + 2, AflUiStyle.TEXT, 1);
            x += font.width(l) + 10;
        }
        g.pose().popPose();
    }

    private static Component label(String key) {
        return Component.translatable("screen.apocalypse_firstlight.book." + key);
    }
}
