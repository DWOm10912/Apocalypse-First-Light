package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic first-person collimated reticle for Geo sights that ship {@code assets/<ns>/optics/<item>.json}.
 * The dot marks the hitscan ray (camera centre = view -Z). It sits where that ray crosses the optic's lens plane
 * ({@code lens_center} bone, local +Z = lens normal) and shows only while the crossing is inside the effective window
 * ({@code lens_aperture} bone = window half extents in the lens frame; a rectangle, or an ellipse for round tube optics
 * with {@code aperture_shape: "ellipse"}) and the optic axis (sight -Z) is within the
 * spec's off-axis limit of the view. Its screen size is a fixed angle, never a model size. The already drawn housing
 * occludes it through depth; it writes colour only, never runs in a shadow pass or outside the first-person hand draw,
 * and never feeds aiming or hitscan. The model carries no reticle geometry.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NativeCollimatedReticleRendering {
    private static final String PREFIX = "optics/", SUFFIX = ".json";
    // Vanilla hand projection near plane; a crossing closer than this cannot be on screen.
    private static final float NEAR = .05F;

    /** Angles in radians; colour 0..1; bone names inside the sight's own geo; ellipse = round window (tube optics). */
    public record Spec(ResourceLocation texture, float red, float green, float blue, float alpha,
                       float angularDiameter, float maxOffAxis, String lensCenter, String lensAperture, boolean ellipse) {}

    private static volatile Map<ResourceLocation, Spec> specs = Map.of();
    // Render thread only: armed around one first-person gun draw, filled by the sight mount of that draw.
    private static boolean armed;
    private static Spec pending;
    private static ResourceLocation pendingGeometry;
    private static final Matrix4f sightPose = new Matrix4f();

    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Map<ResourceLocation, Spec>>() {
            @Override protected Map<ResourceLocation, Spec> prepare(ResourceManager manager, ProfilerFiller profiler) {
                var loaded = new HashMap<ResourceLocation, Spec>();
                manager.listResources("optics", id -> id.getPath().endsWith(SUFFIX)).forEach((file, resource) -> {
                    String path = file.getPath();
                    try (var reader = resource.openAsReader()) {
                        var spec = parse(JsonParser.parseReader(reader).getAsJsonObject());
                        if (spec == null) return; // optic data without a collimated reticle
                        if (manager.getResource(spec.texture()).isEmpty())
                            throw new IllegalArgumentException("missing texture " + spec.texture());
                        loaded.put(new ResourceLocation(file.getNamespace(),
                                path.substring(PREFIX.length(), path.length() - SUFFIX.length())), spec);
                    } catch (Exception e) {
                        ApocalypseFirstLight.LOGGER.error("Rejected AFL optic data {}: {}", file, e.getMessage());
                    }
                });
                return Map.copyOf(loaded);
            }
            @Override protected void apply(Map<ResourceLocation, Spec> loaded, ResourceManager manager, ProfilerFiller profiler) {
                specs = loaded;
            }
        });
    }

    static Spec parse(JsonObject root) {
        if (!root.has("collimated_reticle")) return null;
        var o = root.getAsJsonObject("collimated_reticle");
        var texture = new ResourceLocation(o.get("texture").getAsString());
        var color = o.getAsJsonArray("color");
        if (color.size() != 3 && color.size() != 4) throw new IllegalArgumentException("color needs [r,g,b] or [r,g,b,a]");
        float[] rgba = {1, 1, 1, 1};
        for (int i = 0; i < color.size(); i++) {
            int c = color.get(i).getAsInt();
            if (c < 0 || c > 255) throw new IllegalArgumentException("color channel outside 0..255");
            rgba[i] = c / 255F;
        }
        float diameter = degrees(o, "angular_diameter_degrees", Float.NaN, .01F, 5F);
        float offAxis = degrees(o, "max_off_axis_degrees", 12F, 0F, 45F);
        String shape = o.has("aperture_shape") ? o.get("aperture_shape").getAsString() : "rectangle";
        if (!shape.equals("rectangle") && !shape.equals("ellipse"))
            throw new IllegalArgumentException("aperture_shape must be rectangle or ellipse");
        return new Spec(texture, rgba[0], rgba[1], rgba[2], rgba[3], diameter, offAxis,
                bone(o, "lens_center_bone", "lens_center"), bone(o, "lens_aperture_bone", "lens_aperture"),
                shape.equals("ellipse"));
    }

    private static float degrees(JsonObject o, String key, float fallback, float min, float max) {
        float value = o.has(key) ? o.get(key).getAsFloat() : fallback;
        if (!Float.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be within " + min + ".." + max);
        return (float) Math.toRadians(value);
    }

    private static String bone(JsonObject o, String key, String fallback) {
        String name = o.has(key) ? o.get(key).getAsString() : fallback;
        if (name.isBlank()) throw new IllegalArgumentException(key + " is empty");
        return name;
    }

    /** First-person hand renderer: arm before its single gun draw. */
    public static void begin() { armed = true; pending = null; }

    /** Always paired with {@link #begin()}, also when the gun draw fails. */
    public static void end() { armed = false; pending = null; }

    /** Sight mount: the Geo sight was just drawn with its origin at {@code pose}. Ignored unless armed. */
    public static void capture(ItemStack sight, PoseStack pose) {
        if (!armed) return;
        var id = ForgeRegistries.ITEMS.getKey(sight.getItem());
        var spec = id == null ? null : specs.get(id);
        if (spec == null) return;
        pending = spec;
        pendingGeometry = new ResourceLocation(id.getNamespace(), "geo/" + id.getPath() + ".geo.json");
        sightPose.set(pose.last().pose());
    }

    /** After the whole gun is submitted, so every housing/gun depth value is in place before the dot. */
    public static void draw(MultiBufferSource buffers) {
        var spec = pending;
        pending = null;
        if (!armed || spec == null || AflShaderCompat.activeShadowPass()) return;
        var geo = GeckoLibCache.getBakedModels().get(pendingGeometry);
        if (geo == null) return;
        // Lens and aperture frames relative to the sight origin, via the same bone traversal that draws the sight.
        var lens = new Matrix4f();
        var aperture = new Matrix4f();
        if (!locate(geo.topLevelBones(), spec.lensCenter(), lens) || !locate(geo.topLevelBones(), spec.lensAperture(), aperture)) return;
        var corner = new Matrix4f(lens).invert().mul(aperture).transformPosition(new Vector3f());
        float halfWidth = Math.abs(corner.x), halfHeight = Math.abs(corner.y);

        // Hand pose space is view space: camera at the origin, hitscan centre along -Z.
        var forward = sightPose.transformDirection(new Vector3f(0, 0, -1));
        if (-forward.z < forward.length() * Mth.cos(spec.maxOffAxis())) return;
        var frame = new Matrix4f(sightPose).mul(lens);
        var c = frame.transformPosition(new Vector3f());
        var u = frame.transformDirection(new Vector3f(1, 0, 0));
        var v = frame.transformDirection(new Vector3f(0, 1, 0));
        var n = new Vector3f(u).cross(v);
        // Ray t*(0,0,-1) against the plane (x - c).n = 0.
        float along = -n.z;
        if (Math.abs(along) < 1e-6F * n.length()) return;
        float t = c.dot(n) / along;
        if (!(t > NEAR)) return;
        // Crossing in lens coordinates; u and v stay orthogonal with equal length (rigid pose, uniform scale).
        var r = new Vector3f(-c.x, -c.y, -t - c.z);
        float x = r.dot(u) / u.lengthSquared() / halfWidth, y = r.dot(v) / v.lengthSquared() / halfHeight;
        if (spec.ellipse() ? x * x + y * y > 1 : Math.abs(x) > 1 || Math.abs(y) > 1) return;

        float half = t * (float) Math.tan(spec.angularDiameter() * .5F);
        float z = -t * (1 - 1e-3F); // a hair toward the eye; never coplanar with the lens
        var type = ReticleType.of(spec.texture());
        VertexConsumer out = buffers.getBuffer(type);
        vertex(out, spec, -half, -half, z, 0, 1);
        vertex(out, spec, half, -half, z, 1, 1);
        vertex(out, spec, half, half, z, 1, 0);
        vertex(out, spec, -half, half, z, 0, 0);
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
    }

    private static void vertex(VertexConsumer out, Spec spec, float x, float y, float z, float u, float v) {
        out.vertex(x, y, z, spec.red(), spec.green(), spec.blue(), spec.alpha(), u, v,
                OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 0, 1);
    }

    private static boolean locate(Iterable<GeoBone> bones, String name, Matrix4f out) {
        var pose = new PoseStack();
        for (var bone : bones) if (locate(bone, pose, name, out)) return true;
        return false;
    }

    private static boolean locate(GeoBone bone, PoseStack pose, String name, Matrix4f out) {
        pose.pushPose();
        try {
            RenderUtils.prepMatrixForBone(pose, bone);
            if (bone.getName().equals(name)) {
                RenderUtils.translateToPivotPoint(pose, bone);
                out.set(pose.last().pose());
                return true;
            }
            for (var child : bone.getChildBones()) if (locate(child, pose, name, out)) return true;
            return false;
        } finally {
            pose.popPose();
        }
    }

    /** Emissive, alpha blended, depth tested, colour-only; linear filtering keeps a few-pixel dot round and steady. */
    private static final class ReticleType extends RenderType {
        private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(ReticleType::build);
        private static RenderType build(ResourceLocation texture) {
            return create("afl_collimated_reticle", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                            .createCompositeState(false));
        }
        static RenderType of(ResourceLocation texture) { return TYPES.apply(texture); }
        private ReticleType() { super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {}); }
    }

    private NativeCollimatedReticleRendering() {}
}
