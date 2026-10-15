package com.antaurora.apofirstlight.client.font;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The game-wide smooth font (docs/ui/afl_font_v1.md). Any {@code "ttf"} font provider whose file lives in the AFL
 * namespace is loaded as an AflSmoothFontProvider, and its JSON fields mean: {@code size} = em size in GUI units,
 * {@code shift} = [unused, baseline below the line top in GUI units]; {@code oversample} is ignored (the raster follows
 * the GUI scale). assets/minecraft/font/default.json and uniform.json put AFL Sans SC in front of the vanilla fonts,
 * which stay as the fallback for characters it lacks.
 *
 * <p>When the GUI scale changes, every font set holding such a provider is rescaled: the providers switch resolution and
 * the set drops its glyph caches and atlas textures (the same reset a resource reload does, without reloading).
 */
public final class AflSmoothFonts {
    private static final Set<FontSet> SETS = Collections.newSetFromMap(new WeakHashMap<>());

    private AflSmoothFonts() {
    }

    /** Screen pixels per GUI unit; 1 before the window exists. */
    public static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc == null ? null : mc.getWindow();
        return window == null ? 1 : Math.max(1, (int) Math.round(window.getGuiScale()));
    }

    /**
     * The raster for text drawn with this pose (Font.StringRenderOutput): 0 = the normal GUI-scale glyphs. GUI text
     * scaled up (vanilla titles x4, subtitles x2, big headings) gets its own screen pixels per GUI unit, capped at
     * AflSmoothFontProvider#MAX_RASTER. World text (signs, name tags) has a pose scale far below 1 and keeps the
     * normal glyphs.
     */
    public static int rasterFor(Matrix4f pose) {
        float scale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01() + pose.m02() * pose.m02());
        if (scale <= 1.05F) return 0;
        int gui = guiScale();
        int raster = Math.min(AflSmoothFontProvider.MAX_RASTER, Math.round(gui * scale));
        return raster > gui ? raster : 0;
    }

    public static boolean claims(ResourceLocation file) {
        return ApocalypseFirstLight.MOD_ID.equals(file.getNamespace());
    }

    public static GlyphProvider load(ResourceManager resources, ResourceLocation file, float em, float baseline) {
        STBTTFontinfo info = null;
        ByteBuffer memory = null;
        try (InputStream in = resources.open(file.withPrefix("font/"))) {
            info = STBTTFontinfo.malloc();
            memory = TextureUtil.readResource(in);
            memory.flip();
            if (!STBTruetype.stbtt_InitFont(info, memory)) throw new IOException("Invalid ttf " + file);
            AflSmoothFontProvider provider = new AflSmoothFontProvider(memory, info, em, baseline);
            ApocalypseFirstLight.LOGGER.info("[AFL Font] {} em {} baseline {} at GUI scale {}, {} glyphs", file, em, baseline,
                    provider.scale(), provider.getSupportedGlyphs().size());
            return provider;
        } catch (IOException e) {
            if (info != null) info.free();
            MemoryUtil.memFree(memory);
            throw new UncheckedIOException(e);
        }
    }

    /** A font set took AFL providers (FontSet#reload): rescale it with the rest from now on. Render thread. */
    public static void track(FontSet set) {
        SETS.add(set);
        rescale(set, guiScale());
    }

    public static void untrack(FontSet set) {
        SETS.remove(set);
    }

    /** Window#setGuiScale, render thread, before the screen re-lays out. */
    public static void onGuiScale() {
        int scale = guiScale();
        for (FontSet set : new ArrayList<>(SETS)) rescale(set, scale);
    }

    private static void rescale(FontSet set, int scale) {
        AflFontSetAccess access = (AflFontSetAccess) set;
        boolean changed = false;
        List<GlyphProvider> providers = access.afl$providers();
        for (GlyphProvider provider : providers) {
            if (provider instanceof AflSmoothFontProvider smooth && smooth.scale() != scale) {
                smooth.rescale(scale);
                changed = true;
            }
        }
        if (changed) access.afl$flush();
    }
}
