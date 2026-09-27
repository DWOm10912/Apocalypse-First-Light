package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.EnumMap;
import java.util.Locale;

/** Temporary, opt-in CPU submission probe. It never changes a draw decision or render state. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunRenderProfile {
    private static final boolean ENABLED = !FMLEnvironment.production && Boolean.getBoolean("afl.debug.renderProfile");
    private static final int SAMPLE_FRAMES = 120;
    private static String currentKey;
    private static String completedKey;
    private static Sample sample;
    private static boolean frameOpen;

    private enum Pass {
        MAIN_FIRST_PERSON_HAND, HAND_TRANSLUCENT, FIRST_PERSON_UNCONFIRMED,
        SHADOW, THIRD_PERSON, WORLD_ENTITY, OTHER
    }

    private static final class Counts {
        long calls, parts, triangles, vertices, cpuNs, quadFaces, triangleFaces;
        long hiddenTriangles, zeroScaleTriangles, invalidNormalTriangles;

        void add(AflMeshRenderer.Metrics metrics, long ns) {
            calls++;
            parts += metrics.parts;
            triangles += metrics.triangles;
            vertices += metrics.vertices;
            quadFaces += metrics.quadFaces;
            triangleFaces += metrics.triangleFaces;
            cpuNs += ns;
            hiddenTriangles += metrics.hiddenTriangles;
            zeroScaleTriangles += metrics.zeroScaleTriangles;
            invalidNormalTriangles += metrics.invalidNormalTriangles;
        }
    }

    private static final class Sample {
        final String weapon, shader, pack;
        final EnumMap<Pass, Counts> passes = new EnumMap<>(Pass.class);
        final Counts total = new Counts();
        int frames, formatVersion;
        long firstPersonShadowCalls, thirdPersonShadowCalls;
        boolean shadowUnconfirmed;

        Sample(String weapon, String shader, String pack) {
            this.weapon = weapon;
            this.shader = shader;
            this.pack = pack;
            for (var pass : Pass.values()) passes.put(pass, new Counts());
        }

        void add(Pass pass, ItemDisplayContext perspective, AflMeshRenderer.Metrics metrics, long ns,
                 boolean shadowKnown) {
            passes.get(pass).add(metrics, ns);
            total.add(metrics, ns);
            if (pass == Pass.SHADOW) {
                if (firstPerson(perspective)) firstPersonShadowCalls++;
                if (thirdPerson(perspective)) thirdPersonShadowCalls++;
            }
            if (!shadowKnown) shadowUnconfirmed = true;
        }

        void log() {
            ApocalypseFirstLight.LOGGER.info("[AFL-RENDER-PROFILE] weapon={} shader_active={} shader_pack={} frames={} frame_boundary=Forge_RenderTickEvent_START_END shadow_api={} hand_phase={}",
                    weapon, shader, pack, frames, AflShaderCompat.shadowAvailable() ? "AVAILABLE" : "UNAVAILABLE",
                    AflShaderCompat.phaseAvailable() ? "AVAILABLE" : "UNAVAILABLE");
            logCounts("total", total);
            for (var pass : Pass.values()) logCounts(pass.name().toLowerCase(Locale.ROOT), passes.get(pass));
            String firstShadow = shadowUnconfirmed ? "UNCONFIRMED" : Boolean.toString(firstPersonShadowCalls > 0);
            ApocalypseFirstLight.LOGGER.info("[AFL-RENDER-PROFILE] weapon={} first_person_gun_in_shadow_pass={} first_person_shadow_calls={} third_person_shadow_calls={} hidden_mesh_early_skip=YES hidden_skip_scope=invoked_bones_only skipped_hidden_triangles={} skipped_zero_scale_triangles={} skipped_invalid_normal_triangles={}",
                    weapon, firstShadow, firstPersonShadowCalls, thirdPersonShadowCalls,
                    total.hiddenTriangles, total.zeroScaleTriangles, total.invalidNormalTriangles);
        }

        void logCounts(String pass, Counts counts) {
            ApocalypseFirstLight.LOGGER.info("[AFL-RENDER-PROFILE] weapon={} pass={} format_version={} calls_per_frame={} parts_per_frame={} triangles_per_frame={} quad_faces_per_frame={} triangle_faces_per_frame={} triangle_equivalent_per_frame={} vertices_per_frame={} cpu_ms_per_frame={} cpu_ms_per_call={}",
                    weapon, pass, formatVersion, ratio(counts.calls, frames), ratio(counts.parts, frames),
                    ratio(counts.triangles, frames), ratio(counts.quadFaces, frames), ratio(counts.triangleFaces, frames),
                    ratio(counts.triangles, frames), ratio(counts.vertices, frames),
                    ratio(counts.cpuNs / 1_000_000.0, frames), ratio(counts.cpuNs / 1_000_000.0, counts.calls));
        }
    }

    private static String ratio(double value, long divisor) {
        return divisor == 0 ? "0" : String.format(Locale.ROOT, "%.4f", value / divisor);
    }

    private static String weapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace())) return null;
        String name = id.getPath();
        return name.equals("p9_01") || name.equals("blackridge_50") ? name : null;
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!ENABLED) return;
        if (event.phase == TickEvent.Phase.START) {
            frameOpen = false;
            var mc = Minecraft.getInstance();
            String selected = mc.player == null || mc.level == null || mc.screen != null
                    ? null : weapon(mc.player.getMainHandItem());
            if (selected == null) {
                currentKey = null;
                completedKey = null;
                sample = null;
                return;
            }
            Boolean shaderActive = AflShaderCompat.shaderActive();
            String shader = shaderActive == null ? "UNCONFIRMED" : shaderActive ? "YES" : "NO";
            String pack = Boolean.TRUE.equals(shaderActive) ? AflShaderCompat.packName() : "none";
            String key = selected + '|' + shader + '|' + pack + '|' + mc.options.getCameraType()
                    + '|' + AflMeshCache.snapshot().generation();
            if (!key.equals(currentKey)) {
                currentKey = key;
                sample = new Sample(selected, shader, pack);
            }
            if (!key.equals(completedKey)) frameOpen = true;
        } else if (frameOpen && sample != null) {
            frameOpen = false;
            if (++sample.frames == SAMPLE_FRAMES) {
                sample.log();
                completedKey = currentKey;
                sample = null;
            }
        }
    }

    public static void render(AflMeshModel mesh, GeoBone bone, PoseStack pose, VertexConsumer buffer,
                              int light, int overlay, float red, float green, float blue, float alpha,
                              ItemStack stack, ItemDisplayContext perspective) {
        if (!ENABLED || !frameOpen || sample == null || !sample.weapon.equals(weapon(stack))) {
            AflMeshRenderer.render(mesh, bone, pose, buffer, light, overlay, red, green, blue, alpha);
            return;
        }
        Boolean shadow = AflShaderCompat.shadowPass();
        String handPhase = firstPerson(perspective) && "YES".equals(sample.shader)
                ? AflShaderCompat.handPhase() : null;
        Pass pass = classify(perspective, shadow, handPhase, sample.shader);
        if (sample.formatVersion == 0) sample.formatVersion = mesh.formatVersion();
        else if (sample.formatVersion != mesh.formatVersion()) sample.formatVersion = -1;
        var metrics = new AflMeshRenderer.Metrics();
        long start = System.nanoTime();
        AflMeshRenderer.render(mesh, bone, pose, buffer, light, overlay, red, green, blue, alpha, metrics);
        long elapsed = System.nanoTime() - start;
        sample.add(pass, perspective, metrics, elapsed, shadow != null);
    }

    private static Pass classify(ItemDisplayContext perspective, Boolean shadow, String handPhase, String shader) {
        if (Boolean.TRUE.equals(shadow)) return Pass.SHADOW;
        if (shadow == null) return firstPerson(perspective) ? Pass.FIRST_PERSON_UNCONFIRMED : Pass.OTHER;
        if (firstPerson(perspective)) {
            if ("NO".equals(shader) || "HAND_SOLID".equals(handPhase)) return Pass.MAIN_FIRST_PERSON_HAND;
            if ("HAND_TRANSLUCENT".equals(handPhase)) return Pass.HAND_TRANSLUCENT;
            return Pass.FIRST_PERSON_UNCONFIRMED;
        }
        if (thirdPerson(perspective)) return Pass.THIRD_PERSON;
        if (perspective == ItemDisplayContext.GROUND || perspective == ItemDisplayContext.FIXED)
            return Pass.WORLD_ENTITY;
        return Pass.OTHER;
    }

    private static boolean firstPerson(ItemDisplayContext perspective) {
        return perspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    private static boolean thirdPerson(ItemDisplayContext perspective) {
        return perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private NativeGunRenderProfile() {}
}
