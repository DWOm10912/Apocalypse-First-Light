package com.antaurora.apofirstlight.vegetation.client;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The mesh plants' wind without a shader pack (AFL vegetation P1a, docs/gameplay/vegetation_v1_design.md 4; user
 * 2026-10-23: "无光影时，尽量提供低成本中距离风动"): Embeddium's terrain vertex stage moves the vertices of the vegetation
 * sprite (by their texture coordinates: inside its atlas rectangle) along the wind, more towards the tips (the card's v,
 * its base on the sprite's bottom row), further for the tall species' columns (PlantShape#WIND), in patches of gusts drifting downwind (smooth noise, no bands), a slow swell and a
 * fine flutter. On the
 * GPU, every distance, no rebuild. The wind is the regional weather's where the camera is (ClientWeather), a light
 * breeze without it.
 * <ul>
 * <li>Source: mixin client.VegetationWindEmbeddiumShaderMixin, the vertex stage's source as Embeddium hands it to GL (the
 * dynamic lights' hook, which patches other lines); compiled alone first, left as it was when it does not compile. The
 * code is shaders/include/afl_veg_wind.glsl, which the near layer's programs import too (13.10).</li>
 * <li>Uniforms: mixin client.VegetationWindEmbeddiumStateMixin, after each pass set the program up.</li>
 * </ul>
 * The near layer (VegetationDynamics) draws with its own programs carrying the same code; VegetationWindMath keeps a CPU
 * copy for offline checks.
 * With a shader pack Embeddium's programs are not used: the pack waves the turf as its own short grass (TreeShaderIds).
 * Without Embeddium (vanilla chunk programs) there is no wind.
 */
public final class VegetationWind {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MAIN = "void main() {";
    private static final String GL_POSITION = "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);";
    /** The wind's code (uniforms and afl_vegWind), shared with the near layer's programs (core/afl_vegetation_near.vsh). */
    private static final String LIBRARY_PATH = "/assets/apocalypse_firstlight/shaders/include/afl_veg_wind.glsl";
    private static String library;
    private static int patched, failed;
    private static volatile float[] rect;
    /** Each terrain program's uniform locations: rect, wind, scale, camera (render thread; cleared when programs are made again). */
    private static final Int2ObjectOpenHashMap<int[]> LOCATIONS = new Int2ObjectOpenHashMap<>();

    private VegetationWind() {}

    /** The atlas was stitched again: the sprite's rectangle is read afresh. */
    static void spriteChanged() {
        rect = null;
    }

    static String status() {
        return "terrain vertex stages patched " + patched + ", failed " + failed + (rect == null ? "; sprite not read yet" : "");
    }

    /** An Embeddium stage's source as handed to GL: the terrain vertex stage gets the wind. */
    public static CharSequence embeddium(int handle, CharSequence source) {
        if (GL20.glGetShaderi(handle, GL20.GL_SHADER_TYPE) != GL20.GL_VERTEX_SHADER) return source;
        String src = source.toString();
        LOCATIONS.clear();   // programs are being made again: their names may be reused
        int main = src.indexOf(MAIN);
        if (main < 0 || !src.contains(GL_POSITION) || !src.contains("_vert_tex_diffuse_coord") || src.contains("afl_vegWind")) return source;
        String lib = library();
        if (lib == null) return source;
        String out = src.substring(0, main) + lib + "\n" + src.substring(main)
                .replace(GL_POSITION, "position = afl_vegWind(position, _vert_tex_diffuse_coord);\n    " + GL_POSITION);
        if (!compiles(out)) {
            failed++;
            return source;
        }
        patched++;
        return out;
    }

    /** The uniforms of a terrain program that a pass just set up (render thread). */
    public static void bind(int program) {
        if (program == 0) return;
        // the locations asked once per program (a glGet waits for the driver thread under NVIDIA's threaded optimization)
        int[] at = LOCATIONS.computeIfAbsent(program, p -> new int[]{GL20.glGetUniformLocation(p, "afl_VegRect"),
                GL20.glGetUniformLocation(p, "afl_VegWind"), GL20.glGetUniformLocation(p, "afl_VegScale"), GL20.glGetUniformLocation(p, "afl_VegCam")});
        int lr = at[0];
        if (lr < 0) return;
        float[] r = rect;
        if (r == null) {
            TextureAtlasSprite s = PlantModel.sprite();
            r = new float[]{s.getU0(), s.getV0(), s.getU1(), s.getV1()};
            rect = r;
        }
        GL20.glUniform4f(lr, r[0], r[1], r[2], r[3]);
        float[] wind = VegetationWindMath.now();
        int lw = at[1];
        if (lw >= 0) GL20.glUniform4f(lw, wind[0], wind[1], wind[2], wind[3]);
        int ls = at[2];
        if (ls >= 0) GL20.glUniform4fv(ls, PlantShape.WIND);
        int lc = at[3];
        var mc = Minecraft.getInstance();
        if (lc >= 0 && mc.gameRenderer != null) {
            var cam = mc.gameRenderer.getMainCamera().getPosition();
            GL20.glUniform3f(lc, (float) (cam.x % 4096), (float) (cam.y % 4096), (float) (cam.z % 4096));
        }
    }

    private static String library() {
        if (library == null) {
            try (InputStream in = VegetationWind.class.getResourceAsStream(LIBRARY_PATH)) {
                if (in == null) throw new IOException("missing " + LIBRARY_PATH);
                library = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("AFL vegetation wind: cannot read the wind's code, the terrain stays still: {}", e.toString());
                failed++;
                return null;
            }
        }
        return library;
    }

    private static boolean compiles(String src) {
        int shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        try {
            GL20.glShaderSource(shader, src);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_TRUE) return true;
            LOGGER.warn("AFL vegetation wind: the terrain vertex stage does not compile with the wind, left as it was:\n{}", GL20.glGetShaderInfoLog(shader));
            return false;
        } finally {
            GL20.glDeleteShader(shader);
        }
    }
}
