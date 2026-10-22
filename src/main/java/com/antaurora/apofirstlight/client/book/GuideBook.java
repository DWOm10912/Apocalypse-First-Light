package com.antaurora.apofirstlight.client.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client: one readable book as its file describes it (data/&lt;ns&gt;/books/, sent by the server, GuideBooks): chapters,
 * sections, blocks, each text a language key under the book's prefix. GuideBookLayout sets the pages from it.
 */
record GuideBook(ResourceLocation id, ResourceLocation item, String lang, String cover, List<String> coverLanguages, String coverFallback,
                 ResourceLocation paper, int imageW, int imageH, List<Chapter> chapters) {

    record Chapter(String id, String label, String title, String tocLabel, String running, List<Section> sections) {}

    record Section(String id, boolean newPage, List<ResourceLocation> teaches, List<Block> blocks) {}

    /**
     * A block of a section. {@code type}: para (text), heading (text), title (text, a section's own title), bullet (term,
     * text), tip (term = its title, text), numbered (term, text; numbered ① ② ... within the section), figure (image,
     * w x h design units at the full column, texture texW x texH, text = caption, labels over it).
     */
    record Block(String type, @Nullable String text, @Nullable String term, @Nullable ResourceLocation image, int w, int h,
                 int texW, int texH, List<Label> labels) {}

    /** A part name laid over a figure, at (x, y) in the figure's design units (y the baseline). */
    record Label(String text, float x, float y, float size, String anchor) {}

    /** The full language key of a book text. */
    String key(String suffix) {
        return lang + "." + suffix;
    }

    static GuideBook parse(ResourceLocation id, JsonObject root) {
        ResourceLocation item = new ResourceLocation(root.get("item").getAsString());
        List<String> langs = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("cover_languages")) langs.add(e.getAsString());
        JsonArray size = root.getAsJsonArray("image_size");
        List<Chapter> chapters = new ArrayList<>();
        for (JsonElement c : root.getAsJsonArray("chapters")) {
            JsonObject chapter = c.getAsJsonObject();
            List<Section> sections = new ArrayList<>();
            for (JsonElement s : chapter.getAsJsonArray("sections")) {
                JsonObject section = s.getAsJsonObject();
                List<ResourceLocation> teaches = new ArrayList<>();
                if (section.has("teaches")) for (JsonElement r : section.getAsJsonArray("teaches")) teaches.add(new ResourceLocation(r.getAsString()));
                List<Block> blocks = new ArrayList<>();
                for (JsonElement b : section.getAsJsonArray("blocks")) blocks.add(block(b.getAsJsonObject()));
                sections.add(new Section(section.get("id").getAsString(), section.has("new_page") && section.get("new_page").getAsBoolean(),
                        List.copyOf(teaches), List.copyOf(blocks)));
            }
            chapters.add(new Chapter(chapter.get("id").getAsString(), chapter.get("label").getAsString(), chapter.get("title").getAsString(),
                    chapter.get("toc_label").getAsString(), chapter.get("running").getAsString(), List.copyOf(sections)));
        }
        return new GuideBook(id, item, root.get("lang").getAsString(), root.get("cover").getAsString(), List.copyOf(langs),
                root.get("cover_fallback").getAsString(), new ResourceLocation(root.get("paper").getAsString()),
                size.get(0).getAsInt(), size.get(1).getAsInt(), List.copyOf(chapters));
    }

    private static Block block(JsonObject b) {
        String type = b.get("type").getAsString();
        String text = str(b, "text"), term = str(b, "term");
        if (type.equals("tip")) term = str(b, "title");
        if (!type.equals("figure")) return new Block(type, text, term, null, 0, 0, 0, 0, List.of());
        JsonArray size = b.getAsJsonArray("size"), tex = b.getAsJsonArray("image_size");
        List<Label> labels = new ArrayList<>();
        if (b.has("labels")) for (JsonElement e : b.getAsJsonArray("labels")) {
            JsonObject l = e.getAsJsonObject();
            labels.add(new Label(l.get("text").getAsString(), l.get("x").getAsFloat(), l.get("y").getAsFloat(),
                    l.has("size") ? l.get("size").getAsFloat() : 30, l.has("anchor") ? l.get("anchor").getAsString() : "start"));
        }
        return new Block(type, str(b, "caption"), null, new ResourceLocation(b.get("image").getAsString()), size.get(0).getAsInt(),
                size.get(1).getAsInt(), tex.get(0).getAsInt(), tex.get(1).getAsInt(), List.copyOf(labels));
    }

    @Nullable
    private static String str(JsonObject o, String name) {
        return o.has(name) ? o.get(name).getAsString() : null;
    }
}
