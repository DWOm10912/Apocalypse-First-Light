package com.antaurora.apofirstlight.client.book;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client: sets a book's pages in the reader's language with the mod's font (docs/gameplay/field_survival_guide_v1_design.md
 * 4), in page units: a page is 1150 x 1800 (the 11.5 x 18 cm page at 100 per cm, the concept's own units, fsg_pages.py),
 * the type sizes are em sizes in those units. Every chapter starts a new page, so does a section marked new_page; the
 * blocks flow down the column and break to the next page (paragraphs and lists line by line, a heading with at least one
 * line after it, tips and figures whole). Page numbers and the contents follow from the layout: the cover, the inside
 * cover, the contents (page 1, its number not printed), then the text from page 2, even pages on the left.
 */
final class GuideBookLayout {
    static final int PW = 1150, PH = 1800, BOTTOM = PH - 150, TOP = 170;
    static final int INK = 0x23211D, GREY = 0x6E675C, ORANGE = 0xD9632B;
    /**
     * The font's em in font pixels: the ttf provider maps the em to the size (8.5), not ascent to descent (in game
     * 2026-10-22, 8.5 * 1000 / 1448 drew every glyph 1.45 times too big and the lines ran into each other).
     */
    static final float EM = 8.5F;
    /** The baseline below a line's top, font pixels (the font's shift). */
    static final float BASELINE = 7;
    private static final ResourceLocation MEASURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "book_2");

    enum Kind { COVER, INSIDE, CONTENTS, TEXT }

    sealed interface Op permits Text, Image, Rect {}

    /** A run of text, its baseline at y; anchor start, middle or end. */
    record Text(float x, float y, String text, float size, int rgb, boolean bold, String anchor) implements Op {}

    record Image(ResourceLocation texture, float x, float y, float w, float h, int texW, int texH) implements Op {}

    record Rect(float x, float y, float w, float h, int rgb, float alpha) implements Op {}

    /** A contents row: clicking between y0 and y1 turns to that page index. */
    record Link(float y0, float y1, int page) {}

    static final class Page {
        final Kind kind;
        /** Printed number (0: none), the page's index in the book, the chapter whose running head it shows. */
        final int number, index;
        @Nullable final GuideBook.Chapter chapter;
        final List<Op> ops = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        /** The sections with text on this page. */
        final Set<GuideBook.Section> sections = new LinkedHashSet<>();

        Page(Kind kind, int number, int index, @Nullable GuideBook.Chapter chapter) {
            this.kind = kind;
            this.number = number;
            this.index = index;
            this.chapter = chapter;
        }

        boolean left() {
            return index % 2 == 1;
        }

        /** The text column's left edge (the inner margin is the wider one). */
        float x() {
            return left() ? 110 : 120;
        }
    }

    private final GuideBook book;
    private final Font font;
    private final boolean cjk;
    private final List<Page> pages = new ArrayList<>();
    private final Set<String> missing = new HashSet<>();
    private Page page;
    private float y;

    private GuideBookLayout(GuideBook book, Font font, String language) {
        this.book = book;
        this.font = font;
        this.cjk = language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko");
    }

    /** The whole book: page index 0 the cover, 1 the inside cover, 2 the contents, the text from 3 (printed page 2). */
    static List<Page> lay(GuideBook book, Font font, String language) {
        GuideBookLayout l = new GuideBookLayout(book, font, language);
        l.pages.add(new Page(Kind.COVER, 0, 0, null));
        l.pages.add(new Page(Kind.INSIDE, 0, 1, null));
        Page contents = new Page(Kind.CONTENTS, 0, 2, null);
        l.pages.add(contents);
        List<Integer> starts = new ArrayList<>();
        for (GuideBook.Chapter chapter : book.chapters()) {
            l.newPage(chapter);
            starts.add(l.page.number);
            l.opener(chapter);
            boolean first = true;
            for (GuideBook.Section section : chapter.sections()) {
                if (section.newPage() && !first) l.newPage(chapter);
                first = false;
                l.page.sections.add(section);
                int n = 0;
                for (GuideBook.Block b : section.blocks()) l.block(chapter, section, b, b.type().equals("numbered") ? ++n : 0);
            }
        }
        l.contents(contents, starts);
        for (Page p : l.pages) if (p.kind == Kind.TEXT) l.running(p);
        return List.copyOf(l.pages);
    }

    // ------------------------------------------------------------------------------------------------ text

    String tr(@Nullable String suffix) {
        if (suffix == null) return "";
        String key = book.key(suffix);
        if (!I18n.exists(key) && missing.add(key)) ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] {}: missing language key {}", book.id(), key);
        return I18n.get(key);
    }

    /** Width in page units of a run at this em size. */
    float width(String s, float size, boolean bold) {
        return font.width(Component.literal(s).withStyle(Style.EMPTY.withFont(MEASURE).withBold(bold))) * size / EM;
    }

    private static boolean cjkChar(int c) {
        return c >= 0x2E80 && c <= 0x9FFF || c >= 0xF900 && c <= 0xFAFF || c >= 0xFF00 && c <= 0xFFEF || c >= 0x3000 && c <= 0x303F;
    }

    /** No line starts with these (they stay on the line before), none ends with the openers. */
    private static final String NO_START = "，。、；：？！）》」』”’…—·%,.;:?!)]", NO_END = "（《「『“‘([";

    /**
     * Line breaks: a CJK character is a unit of its own, a Latin word is never split; closing punctuation stays with the
     * unit before it, opening punctuation with the unit after it. {@code first} and {@code rest}: the first and the other
     * lines' widths.
     */
    List<String> wrap(String s, float size, boolean bold, float first, float rest) {
        List<String> units = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean openerPending = false;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            String ch = new String(Character.toChars(c));
            i += ch.length();
            if (c == ' ') {
                if (cur.length() > 0) { units.add(cur.toString()); cur.setLength(0); }
                units.add(" ");
                continue;
            }
            if (NO_START.contains(ch) && !units.isEmpty() && cur.length() == 0) {
                // close punctuation: onto the unit before (past a space it stays a unit of its own)
                String last = units.get(units.size() - 1);
                if (!last.equals(" ")) { units.set(units.size() - 1, last + ch); continue; }
            }
            if (cjkChar(c) && !NO_START.contains(ch)) {
                if (cur.length() > 0 && !openerPending) { units.add(cur.toString()); cur.setLength(0); }
                cur.append(ch);
                openerPending = NO_END.contains(ch);
                if (!openerPending) { units.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(ch);
            openerPending = NO_END.contains(ch);
        }
        if (cur.length() > 0) units.add(cur.toString());

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String u : units) {
            float max = lines.isEmpty() ? first : rest;
            if (u.equals(" ")) {
                if (line.length() > 0) line.append(' ');
                continue;
            }
            String tryLine = line + u;
            if (line.length() > 0 && width(tryLine, size, bold) > max) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
                line.append(u);
            } else {
                line.append(u);
            }
        }
        if (line.length() > 0) lines.add(line.toString().stripTrailing());
        return lines;
    }

    // ------------------------------------------------------------------------------------------------ flow

    private float col() {
        return PW - 230;
    }

    private void newPage(GuideBook.Chapter chapter) {
        int index = pages.size();
        page = new Page(Kind.TEXT, index - 1, index, chapter);
        pages.add(page);
        y = TOP;
    }

    private void ensure(GuideBook.Chapter chapter, GuideBook.Section section, float h) {
        if (y + h <= BOTTOM) return;
        newPage(chapter);
        page.sections.add(section);
    }

    /**
     * Where a run of n lines of height lh breaks to the next page: never a lone first line at the foot of a page, nor a
     * lone last line at the head of the next (the whole run moves instead when a page would keep fewer than two). -1:
     * it all fits here.
     */
    private int breakAt(int n, float lh) {
        int fit = (int) Math.floor((BOTTOM - y) / lh);
        if (fit >= n) return -1;
        int k = n - fit == 1 ? fit - 1 : fit;
        return k < 2 ? 0 : k;
    }

    /** Before line i of a run that breaks at brk: a new page there, else one if the line does not fit. */
    private void line(GuideBook.Chapter chapter, GuideBook.Section section, int i, int brk, float lh) {
        if (i == brk) {
            if (y > TOP) {
                newPage(chapter);
                page.sections.add(section);
            }
        } else {
            ensure(chapter, section, lh);
        }
    }

    private void text(float x, float baseline, String s, float size, int rgb, boolean bold) {
        page.ops.add(new Text(x, baseline, s, size, rgb, bold, "start"));
    }

    private void opener(GuideBook.Chapter chapter) {
        float x = page.x();
        text(x, 230, tr(chapter.label()), 34, ORANGE, true);
        text(x - 4, 360, tr(chapter.title()), 120, INK, true);
        page.ops.add(new Rect(x, 398, 120, 8, ORANGE, 1));
        y = 448;
    }

    private void block(GuideBook.Chapter chapter, GuideBook.Section section, GuideBook.Block b, int number) {
        float x0 = page.x(), w = col();
        float size = cjk ? 38 : 35, lh = cjk ? 66 : 50;
        switch (b.type()) {
            case "title" -> {
                ensure(chapter, section, 92 + lh);
                text(page.x(), y + 60, tr(b.text()), 64, INK, true);
                y += 92;
            }
            case "heading" -> {
                ensure(chapter, section, 72 + 2 * lh);
                y += 8;
                text(page.x(), y + 42, tr(b.text()), 42, INK, true);
                y += 64;
            }
            case "para" -> {
                float indent = cjk ? 2 * size : 0;
                List<String> lines = wrap(tr(b.text()), size, false, w - indent, w);
                int brk = breakAt(lines.size(), lh);
                for (int i = 0; i < lines.size(); i++) {
                    line(chapter, section, i, brk, lh);
                    text(page.x() + (i == 0 ? indent : 0), y + size, lines.get(i), size, INK, false);
                    y += lh;
                }
                y += 16;
            }
            case "bullet" -> {
                String lead = tr(b.term()) + (cjk ? "　" : " — ");
                float lw = width(lead, size, true);
                List<String> lines = wrap(tr(b.text()), size, false, w - 30 - lw, w - 30);
                int brk = breakAt(lines.size(), lh);
                line(chapter, section, 0, brk, lh);
                float x = page.x();
                page.ops.add(new Rect(x + 4, y + size - 18, 10, 10, INK, 1));
                text(x + 30, y + size, lead, size, INK, true);
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) line(chapter, section, i, brk, lh);
                    text(page.x() + 30 + (i == 0 ? lw : 0), y + size, lines.get(i), size, INK, false);
                    y += lh;
                }
                y += 10;
            }
            case "numbered" -> {
                float z = cjk ? 34 : 32, zl = cjk ? 58 : 46;
                String lead = new String(Character.toChars(0x2460 + number - 1)) + " " + tr(b.term()) + (cjk ? "　" : " — ");
                float lw = width(lead, z, true);
                List<String> lines = wrap(tr(b.text()), z, false, w - lw, w - 52);
                int brk = breakAt(lines.size(), zl);
                line(chapter, section, 0, brk, zl);
                text(page.x(), y + z, lead, z, INK, true);
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) line(chapter, section, i, brk, zl);
                    text(page.x() + (i == 0 ? lw : 52), y + z, lines.get(i), z, INK, false);
                    y += zl;
                }
                y += 6;
            }
            case "tip" -> {
                float z = cjk ? 34 : 32, zl = cjk ? 56 : 46;
                List<String> lines = wrap(tr(b.text()), z, false, w - 70, w - 70);
                float h = 34 + 46 + lines.size() * zl + 24;
                ensure(chapter, section, h + 12);
                y += 12;
                float x = page.x();
                page.ops.add(new Rect(x, y, w, h, 0x5A4A30, 0.08F));
                page.ops.add(new Rect(x, y, 8, h, ORANGE, 0.85F));
                text(x + 40, y + 64, tr(b.term()), 36, INK, true);
                float yy = y + 80;
                for (String line : lines) {
                    text(x + 40, yy + z, line, z, INK, false);
                    yy += zl;
                }
                y += h + 20;
            }
            case "figure" -> {
                // CJK labels are short, the figure takes the full column; Latin ones run longer, it gives them room
                float fw = cjk ? w : w * 0.9F, s = fw / b.w(), fh = b.h() * s;
                ensure(chapter, section, fh + 6 + 62);
                float ox = page.x() + (w - fw) / 2, oy = y;
                page.ops.add(new Image(b.image(), ox, oy, fw, fh, b.texW(), b.texH()));
                for (GuideBook.Label l : b.labels())
                    page.ops.add(new Text(ox + l.x() * s, oy + l.y() * s, tr(l.text()), l.size(), INK, false, l.anchor()));
                float fy = oy + fh + 6;
                page.ops.add(new Text(page.x() + w / 2, fy + 30, tr(b.text()), 28, GREY, false, "middle"));
                y = fy + 62;
            }
            default -> ApocalypseFirstLight.LOGGER.warn("[AFL BOOK] {}: unknown block type {}", book.id(), b.type());
        }
    }

    /** The running heads and the page number: the book's title on the left page, the chapter on the right. */
    private void running(Page p) {
        boolean left = p.left();
        float inner = left ? 110 : 120, outer = left ? PW - 120 : PW - 110;
        if (left) {
            p.ops.add(new Text(inner, 96, tr("running"), 26, GREY, false, "start"));
            p.ops.add(new Text(inner, PH - 86, Integer.toString(p.number), 30, GREY, false, "start"));
        } else {
            p.ops.add(new Text(outer, 96, p.chapter == null ? "" : tr(p.chapter.running()), 26, GREY, false, "end"));
            p.ops.add(new Text(outer, PH - 86, Integer.toString(p.number), 30, GREY, false, "end"));
        }
        p.ops.add(new Rect(inner, 117, outer - inner, 1.5F, GREY, 0.6F));
    }

    /** The contents: a row per chapter, its label, title, a dotted leader and the page it starts on; a row turns to it. */
    private void contents(Page p, List<Integer> starts) {
        float x = 140, w = PW - 280, size = 38;
        p.ops.add(new Text(x, 300, tr("contents"), 72, INK, true, "start"));
        p.ops.add(new Rect(x, 336, 120, 8, ORANGE, 1));
        float row = 470;
        for (int i = 0; i < book.chapters().size(); i++) {
            GuideBook.Chapter c = book.chapters().get(i);
            String label = tr(c.tocLabel()), title = tr(c.title()), num = Integer.toString(starts.get(i));
            float lw = cjk ? width(label, size, false) + 24 : 70;
            p.ops.add(new Text(x, row, label, size, cjk ? GREY : ORANGE, false, "start"));
            p.ops.add(new Text(x + lw, row, title, size, INK, false, "start"));
            p.ops.add(new Text(x + w, row, num, size, INK, false, "end"));
            float from = x + lw + width(title, size, false) + 16, to = x + w - width(num, size, false) - 16;
            for (float dx = from; dx + 3 <= to; dx += 14) p.ops.add(new Rect(dx, row - 10, 3, 3, GREY, 1));
            p.links.add(new Link(row - 52, row + 30, starts.get(i) + 1));
            row += 104;
        }
    }
}
