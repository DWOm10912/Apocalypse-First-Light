package com.antaurora.apofirstlight.weapon.client;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Optional Oculus/Iris queries. Missing or failing APIs never authorize a render skip. */
final class AflShaderCompat {
    private static boolean initialized, shadowFailed, phaseFailed;
    private static Object api;
    private static Method shader, shadow, phase, pack;

    private static void init() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("oculus")) return;
        try {
            var type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            shader = type.getMethod("isShaderPackInUse");
            shadow = type.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            api = null;
            shader = null;
            shadow = null;
            return;
        }
        // These two are optional implementation details used only for profiler labels.
        try {
            phase = Class.forName("net.irisshaders.iris.layer.GbufferPrograms").getMethod("getCurrentPhase");
        } catch (ReflectiveOperationException | LinkageError ignored) { phase = null; }
        try {
            pack = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
        } catch (ReflectiveOperationException | LinkageError ignored) { pack = null; }
    }

    static Boolean shaderActive() {
        init();
        if (!ModList.get().isLoaded("oculus")) return false;
        try { return shader == null ? null : (Boolean) shader.invoke(api); }
        catch (ReflectiveOperationException | LinkageError e) { return null; }
    }

    static Boolean shadowPass() {
        init();
        if (!ModList.get().isLoaded("oculus")) return false;
        try { return shadow == null ? null : (Boolean) shadow.invoke(api); }
        catch (ReflectiveOperationException | LinkageError e) { shadowFailed = true; return null; }
    }

    static boolean activeShadowPass() {
        return Boolean.TRUE.equals(shaderActive()) && Boolean.TRUE.equals(shadowPass());
    }

    static String handPhase() {
        init();
        try { return phase == null ? null : String.valueOf(phase.invoke(null)); }
        catch (ReflectiveOperationException | LinkageError e) { phaseFailed = true; return null; }
    }

    static String packName() {
        init();
        try {
            var name = pack == null ? null : pack.invoke(null);
            return name == null ? "UNCONFIRMED" : name.toString();
        } catch (ReflectiveOperationException | LinkageError e) { return "UNCONFIRMED"; }
    }

    static boolean shadowAvailable() { init(); return shadow != null && !shadowFailed; }
    static boolean phaseAvailable() { init(); return phase != null && !phaseFailed; }

    private AflShaderCompat() {}
}
