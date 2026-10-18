package com.antaurora.apofirstlight.client.light;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Puts the dynamic light code (shaders/include/afl_dynamic_light.glsl) into the world programs drawn without a shader pack
 * (docs/rendering/dynamic_lights_v1.md):
 * <ul>
 *   <li>Embeddium's terrain program (sodium:blocks/block_layer_opaque, every pass): its vertex stage multiplies the light
 *   map into the vertex colour, so it gets three outputs more (the camera-relative position, the vertex colour, the light
 *   map colour) and its fragment stage takes the colour from them, the lights added to the light map
 *   (mixin client.DynamicLightEmbeddiumShaderMixin; the source as Embeddium hands it to GL, imports and defines in);</li>
 *   <li>the vanilla entity programs (rendertype_entity_solid / cutout / cutout_no_cull / cutout_no_cull_z_offset /
 *   smooth_cutout / translucent): the fragment stage only, its light map multiply replaced, the position from the
 *   fragment's depth; no new varyings, so Vextris's twins (their own vertex stage, the vanilla fragment) still link
 *   (mixin client.DynamicLightProgramMixin, after vanilla's preprocessor);</li>
 *   <li>AFL's own programs written for it (the road's apocalypse_firstlight:afl_dynlit_solid, the rain's afl_rain vertex
 *   stage): the code goes in before main when the stage has the marker; a vertex stage defines AFL_VERTEX_STAGE first
 *   (no fragment-only functions) and guards its calls with AFL_DYNAMIC_LIGHT (still compiles if the library is not in).</li>
 * </ul>
 * Each patched stage is compiled once on its own first; a stage that does not compile keeps its source as it was (logged),
 * and terrain that is not patched keeps the lights off ({@link #chunkReady}).
 */
public final class DynamicLightShaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LIBRARY_PATH = "/assets/apocalypse_firstlight/shaders/include/afl_dynamic_light.glsl";
    private static final String MARKER = "@afl:dynamic_light";
    private static final Set<String> ENTITY = Set.of("rendertype_entity_solid", "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset", "rendertype_entity_smooth_cutout", "rendertype_entity_translucent");
    private static final String OWN = "apocalypse_firstlight:";

    /** Embeddium terrain: the vertex stage's light map multiply and output, the fragment stage's input. */
    private static final String CHUNK_V_OUT = "out vec4 v_Color;",
            CHUNK_V_COLOR = "v_Color = _vert_color * _sample_lightmap(u_LightTex, _vert_tex_light_coord);",
            CHUNK_F_IN = "in vec4 v_Color;", MAIN = "void main() {";
    private static final String ENTITY_LIGHT = "color *= lightMapColor;";

    private static String library;
    private static boolean libraryFailed;
    /** Embeddium terrain stages patched and failed since start; whether the last vertex stage was patched. */
    private static int chunkPatched, chunkFailed;
    private static boolean lastChunkVertex;
    /** Vanilla stages patched since start (diagnostics). */
    public static int vanillaPatched, vanillaFailed;

    private DynamicLightShaders() {
    }

    /** Embeddium's terrain program carries the lights (some stage patched, none failed). */
    public static boolean chunkReady() {
        return chunkPatched > 0 && chunkFailed == 0;
    }

    public static String status() {
        return "terrain stages patched " + chunkPatched + ", failed " + chunkFailed + "; vanilla stages patched " + vanillaPatched + ", failed " + vanillaFailed;
    }

    /**
     * A vanilla program stage's preprocessed source ({@code name} as its JSON names it, GL stage type), patched when it is
     * one of ours; else as it was.
     */
    public static String vanilla(int glType, String name, String src) {
        String bare = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        String out = null;
        if (glType == GL20.GL_FRAGMENT_SHADER && ENTITY.contains(bare) && src.contains(ENTITY_LIGHT) && src.contains(MAIN)) {
            String lib = library();
            if (lib == null) return src;
            out = insertBeforeMain(src, lib)
                    .replace(MAIN, MAIN + "\n    vec3 afl_P = afl_fragPos();\n    vec3 afl_N = afl_normal(afl_P);")
                    .replace(ENTITY_LIGHT, "color *= vec4(afl_lit(lightMapColor.rgb, afl_P, afl_N), lightMapColor.a);");
        } else if (bare.startsWith(OWN) && src.contains(MARKER)) {
            String lib = library();
            if (lib == null) return src;
            out = insertBeforeMain(src, lib);
        }
        if (out == null) return src;
        DynamicLights.programsChanged();
        if (!compiles(glType, out, name)) {
            vanillaFailed++;
            return src;
        }
        vanillaPatched++;
        return out;
    }

    /** An Embeddium stage's source as handed to GL (stage type from its shader object): the terrain stages patched. */
    public static CharSequence embeddium(int handle, CharSequence source) {
        int type = GL20.glGetShaderi(handle, GL20.GL_SHADER_TYPE);
        String src = source.toString();
        if (type == GL20.GL_VERTEX_SHADER) {
            lastChunkVertex = false;
            if (!src.contains(CHUNK_V_OUT) || !src.contains(CHUNK_V_COLOR)) return source;
            DynamicLights.programsChanged();
            String out = src.replace(CHUNK_V_OUT, CHUNK_V_OUT + "\nout vec3 afl_Pos;\nout vec4 afl_Tint;\nout vec3 afl_Lm;")
                    .replace(CHUNK_V_COLOR, CHUNK_V_COLOR + "\n    afl_Pos = position;\n    afl_Tint = _vert_color;"
                            + "\n    afl_Lm = _sample_lightmap(u_LightTex, _vert_tex_light_coord).rgb;");
            if (!compiles(type, out, "embeddium terrain vertex")) {
                chunkFailed++;
                return source;
            }
            lastChunkVertex = true;
            return out;
        }
        if (type != GL20.GL_FRAGMENT_SHADER || !src.contains(CHUNK_F_IN) || !src.contains("u_BlockTex")) return source;
        boolean vertex = lastChunkVertex;
        lastChunkVertex = false;
        String lib = library();
        int main = src.indexOf(MAIN);
        if (!vertex || lib == null || main < 0) {
            chunkFailed++;
            return source;
        }
        DynamicLights.programsChanged();
        String head = src.substring(0, main).replace(CHUNK_F_IN, CHUNK_F_IN + "\nin vec3 afl_Pos;\nin vec4 afl_Tint;\nin vec3 afl_Lm;");
        // the colour the stage applies: the vertex colour times the light map with the lights in (the light map's alpha is 1)
        String body = src.substring(main + MAIN.length()).replace("v_Color", "afl_Color");
        String out = head + lib + "\n" + MAIN
                + "\n    vec4 afl_Color = vec4(afl_Tint.rgb * afl_lit(afl_Lm, afl_Pos, afl_normal(afl_Pos)), afl_Tint.a);" + body;
        if (!compiles(type, out, "embeddium terrain fragment")) {
            chunkFailed++;
            return source;
        }
        chunkPatched++;
        return out;
    }

    private static String insertBeforeMain(String src, String lib) {
        int main = src.indexOf(MAIN);
        return src.substring(0, main) + lib + "\n" + src.substring(main);
    }

    private static synchronized String library() {
        if (library != null || libraryFailed) return library;
        try (InputStream in = DynamicLightShaders.class.getResourceAsStream(LIBRARY_PATH)) {
            if (in == null) throw new IOException("missing " + LIBRARY_PATH);
            library = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            libraryFailed = true;
            LOGGER.warn("AFL dynamic lights: no light code ({}); lights stay off", e.toString());
        }
        return library;
    }

    /** Compiles a stage on its own; false (and the log) when it does not. */
    private static boolean compiles(int type, String src, String what) {
        int shader = GL20.glCreateShader(type);
        try {
            GL20.glShaderSource(shader, src);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_TRUE) return true;
            LOGGER.warn("AFL dynamic lights: {} does not compile with the lights, left as it was:\n{}", what, GL20.glGetShaderInfoLog(shader));
            return false;
        } finally {
            GL20.glDeleteShader(shader);
        }
    }
}
