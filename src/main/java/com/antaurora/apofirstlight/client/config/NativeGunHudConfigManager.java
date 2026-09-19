package com.antaurora.apofirstlight.client.config;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonParser;

/** Client resource-pack layout, using the same lifecycle as GeigerHudConfigManager. */
public final class NativeGunHudConfigManager {
    private static final ResourceLocation LAYOUT = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "gui/layout/native_gun_hud.json");
    private static volatile NativeGunHudConfig current = NativeGunHudConfig.defaults();
    private NativeGunHudConfigManager() {}
    public static NativeGunHudConfig get() { return current; }
    public static void apply(NativeGunHudConfig layout) { current = layout; }
    private static NativeGunHudConfig read(ResourceManager manager) {
        try (var reader = com.antaurora.apofirstlight.client.hudlayout.HudLayoutDevResources.open(manager,LAYOUT)) {
            return NativeGunHudConfig.parse(JsonParser.parseReader(reader).toString(),
                    message -> ApocalypseFirstLight.LOGGER.warn("[AFL Native Gun HUD] {}", message));
        } catch (Exception exception) {
            ApocalypseFirstLight.LOGGER.warn("[AFL Native Gun HUD] Cannot load {}; using defaults", LAYOUT, exception);
            return NativeGunHudConfig.defaults();
        }
    }
    public static void load(ResourceManager manager) { current = read(manager); }
    public static SimplePreparableReloadListener<NativeGunHudConfig> reloadListener() {
        return new SimplePreparableReloadListener<>() {
            @Override protected NativeGunHudConfig prepare(ResourceManager manager, ProfilerFiller profiler) { return read(manager); }
            @Override protected void apply(NativeGunHudConfig layout, ResourceManager manager, ProfilerFiller profiler) { current = layout; }
        };
    }
}
