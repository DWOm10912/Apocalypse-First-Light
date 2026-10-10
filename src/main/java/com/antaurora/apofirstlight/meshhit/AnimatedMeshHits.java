package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hit meshes of AFL animated mesh blocks (AflAnimatedMeshHost: dumpsters, lockers, coolers, docs/rendering/mesh_hit_runtime_v1.md
 * P2): the block's mesh sidecar (meshes/<stem>.aflmesh.json) posed by its block mesh profile exactly as
 * AflAnimatedBlockMeshRenderer poses it (block origin, [0.5, 0, 0.5], facing, origin, scale, then per part: parent, pivot
 * offset and translation, Rz Ry Rx, scale), with every animation channel at its end, closed or open, as the block's
 * authority says (AflAnimatedMeshHost#meshChannelTarget). Read from the mod jar, so the server builds the same as the
 * client (AflBlockMeshProfiles is client only).
 * <ul>
 *   <li>A channel still moving on the client (its sample is not at the target yet) gives no hit mesh: the block's shape
 *   boxes for those ticks.</li>
 *   <li>Channels the host keeps out (AflAnimatedMeshHost#meshChannelAffectsHits: value channels of gauge needles and
 *   counter drums) stay at 0 and are never waited for.</li>
 *   <li>Contents ('goods_' parts and their children) are left out: they sit inside a shut shell, and with the shell
 *   open they come and go with the loot.</li>
 *   <li>Parts the host hides (AflAnimatedMeshHost#meshPartVisible: a vending machine's smashed glass, a lamp's other
 *   lens set) are left out while hidden.</li>
 * </ul>
 * Each part's triangles become one MeshHitModel per (profile, facing, channel ends); the shape of one host is the
 * visible ones of those.
 */
public final class AnimatedMeshHits {
    private static final String GOODS = "goods_";
    private static final Quaternionf NONE = new Quaternionf();

    private record Node(String parent, float[] pivot, float[][] rest, @Nullable String channel, float[][] target) {}

    private record Profile(boolean horizontal, float[] origin, float[] scale, List<String> channels,
                           Map<String, Node> nodes, Map<String, float[]> local) {}

    private record PoseKey(ResourceLocation profile, Direction facing, long channels) {}

    /** One pose: per part (bone) its model, and the bone with its ancestors (a hidden ancestor hides it). */
    private static final class Posed {
        final String[] bones;
        final String[][] chains;
        final MeshHitModel[] models;
        final Map<Long, MeshHitModels.Shape> shapes = new ConcurrentHashMap<>();

        Posed(String[] bones, String[][] chains, MeshHitModel[] models) {
            this.bones = bones;
            this.chains = chains;
            this.models = models;
        }
    }

    private static final Map<ResourceLocation, Optional<Profile>> PROFILES = new ConcurrentHashMap<>();
    private static final Map<PoseKey, Optional<Posed>> POSED = new ConcurrentHashMap<>();

    private AnimatedMeshHits() {
    }

    /** The hit mesh of {@code host} as it stands now, or null (no profile or sidecar, or a part still moving). */
    @Nullable
    static MeshHitModels.Shape shape(AflAnimatedMeshHost host) {
        ResourceLocation id = host.meshProfile();
        if (id == null) return null;
        Profile profile = PROFILES.computeIfAbsent(id, AnimatedMeshHits::read).orElse(null);
        if (profile == null) return null;
        Level level = host instanceof BlockEntity entity ? entity.getLevel() : null;
        boolean client = level != null && level.isClientSide;
        long ends = 0;
        for (int i = 0; i < profile.channels.size(); i++) {
            String channel = profile.channels.get(i);
            // value channels of small parts (gauge needles) keep the hit mesh at 0 and never hold it back
            if (!host.meshChannelAffectsHits(channel)) continue;
            boolean open = host.meshChannelTarget(channel);
            // the client draws the channel moving towards its target: no hit mesh until it is there
            if (client && Math.abs(host.meshAnimation().sample(channel, level.getGameTime()) - (open ? 1 : 0)) > 1e-4) return null;
            if (open) ends |= 1L << i;
        }
        Direction facing = profile.horizontal ? host.meshFacing() : Direction.NORTH;
        Posed posed = POSED.computeIfAbsent(new PoseKey(id, facing, ends), key -> pose(profile, key)).orElse(null);
        if (posed == null) return null;
        long shown = 0;
        for (int i = 0; i < posed.bones.length && i < 64; i++) {
            boolean visible = true;
            for (String bone : posed.chains[i]) visible &= host.meshPartVisible(bone);
            if (visible) shown |= 1L << i;
        }
        return posed.shapes.computeIfAbsent(shown, mask -> {
            List<MeshHitModels.Placed> parts = new ArrayList<>();
            for (int i = 0; i < posed.bones.length; i++)
                if (i >= 64 || (mask & 1L << i) != 0) parts.add(new MeshHitModels.Placed(posed.models[i], NONE, NONE));
            return new MeshHitModels.Shape(parts);
        });
    }

    private static Optional<Posed> pose(Profile profile, PoseKey key) {
        Map<String, Matrix4f> matrices = new HashMap<>();
        Matrix4f root = new Matrix4f().translate(0.5F, 0.0F, 0.5F)
                .rotateY((float) Math.toRadians(AflBlockMeshProfile.facingDegrees(key.facing)))
                .translate(profile.origin[0], profile.origin[1], profile.origin[2])
                .scale(profile.scale[0], profile.scale[1], profile.scale[2]);
        List<String> bones = new ArrayList<>();
        List<String[]> chains = new ArrayList<>();
        List<MeshHitModel> models = new ArrayList<>();
        Vector3f v = new Vector3f();
        for (Map.Entry<String, float[]> part : profile.local.entrySet()) {
            String[] chain = chain(profile, part.getKey());
            if (chain == null) continue;
            Matrix4f m = matrix(profile, key.channels, part.getKey(), root, matrices);
            float[] local = part.getValue(), tri = new float[local.length];
            for (int i = 0; i < local.length; i += 3) {
                m.transformPosition(v.set(local[i], local[i + 1], local[i + 2]));
                tri[i] = v.x;
                tri[i + 1] = v.y;
                tri[i + 2] = v.z;
            }
            MeshHitModel model = MeshHitModel.of(tri);
            if (model == null) continue;
            bones.add(part.getKey());
            chains.add(chain);
            models.add(model);
        }
        if (models.isEmpty()) return Optional.empty();
        return Optional.of(new Posed(bones.toArray(String[]::new), chains.toArray(String[][]::new), models.toArray(MeshHitModel[]::new)));
    }

    /** The bone and its ancestors, or null when any of them is contents ('goods_'). */
    @Nullable
    private static String[] chain(Profile profile, String bone) {
        List<String> chain = new ArrayList<>();
        for (String b = bone; b != null && chain.size() <= 32; b = profile.nodes.get(b).parent) {
            if (b.startsWith(GOODS) || !profile.nodes.containsKey(b)) return null;
            chain.add(b);
        }
        return chain.toArray(String[]::new);
    }

    /** The part's matrix in block-local space (AflAnimatedBlockMeshRenderer#drawPart, each channel at 0 or 1). */
    private static Matrix4f matrix(Profile profile, long ends, String bone, Matrix4f root, Map<String, Matrix4f> done) {
        Matrix4f known = done.get(bone);
        if (known != null) return known;
        Node node = profile.nodes.get(bone);
        Matrix4f parent = node.parent == null ? root : matrix(profile, ends, node.parent, root, done);
        float[] parentPivot = node.parent == null ? new float[3] : profile.nodes.get(node.parent).pivot;
        float t = node.channel != null && (ends & 1L << profile.channels.indexOf(node.channel)) != 0 ? 1.0F : 0.0F;
        float[][] r = node.rest, g = node.target;
        Matrix4f m = new Matrix4f(parent)
                .translate(node.pivot[0] - parentPivot[0] + r[0][0] + g[0][0] * t,
                        node.pivot[1] - parentPivot[1] + r[0][1] + g[0][1] * t,
                        node.pivot[2] - parentPivot[2] + r[0][2] + g[0][2] * t)
                .rotateZ((float) Math.toRadians(r[1][2] + g[1][2] * t))
                .rotateY((float) Math.toRadians(r[1][1] + g[1][1] * t))
                .rotateX((float) Math.toRadians(r[1][0] + g[1][0] * t))
                .scale(r[2][0] * (1 + (g[2][0] - 1) * t), r[2][1] * (1 + (g[2][1] - 1) * t), r[2][2] * (1 + (g[2][2] - 1) * t));
        done.put(bone, m);
        return m;
    }

    private static Optional<Profile> read(ResourceLocation id) {
        try {
            JsonObject root = json("/assets/" + id.getNamespace() + "/" + id.getPath());
            if (root == null) return Optional.empty();
            ResourceLocation geometry = new ResourceLocation(root.get("geometry").getAsString());
            String stem = geometry.getPath().substring(geometry.getPath().lastIndexOf('/') + 1).replace(".geo.json", "");
            JsonObject sidecar = json("/assets/" + geometry.getNamespace() + "/meshes/" + stem + ".aflmesh.json");
            if (sidecar == null) return Optional.empty();
            boolean horizontal = !root.has("facing") || "horizontal".equals(root.get("facing").getAsString());
            float[] origin = vector(root.get("origin"), 0.0F), scale = vector(root.get("scale"), 1.0F);

            Map<String, String> channelOf = new HashMap<>();
            Map<String, float[][]> targetOf = new HashMap<>();
            List<String> channels = new ArrayList<>();
            if (root.has("animations")) for (Map.Entry<String, JsonElement> a : root.getAsJsonObject("animations").entrySet()) {
                channels.add(a.getKey());
                for (Map.Entry<String, JsonElement> t : a.getValue().getAsJsonObject().getAsJsonObject("transforms").entrySet()) {
                    channelOf.put(t.getKey(), a.getKey());
                    targetOf.put(t.getKey(), transform(t.getValue()));
                }
            }
            Map<String, Node> nodes = new HashMap<>();
            for (Map.Entry<String, JsonElement> p : root.getAsJsonObject("parts").entrySet()) {
                JsonObject part = p.getValue().getAsJsonObject();
                nodes.put(p.getKey(), new Node(part.has("parent") ? part.get("parent").getAsString() : null,
                        vector(part.get("pivot"), 0.0F), part.has("rest") ? transform(part.get("rest")) : transform(null),
                        channelOf.get(p.getKey()), targetOf.getOrDefault(p.getKey(), transform(null))));
            }
            for (Node node : nodes.values()) if (node.parent != null && !nodes.containsKey(node.parent)) return Optional.empty();

            // every sidecar part's triangles, by bone, in that bone's pivot-local block units
            Map<String, List<Float>> byBone = new LinkedHashMap<>();
            for (JsonElement e : sidecar.getAsJsonArray("parts")) {
                JsonObject part = e.getAsJsonObject();
                String bone = part.get("bone").getAsString();
                if (!nodes.containsKey(bone)) return Optional.empty();
                JsonArray vertices = part.getAsJsonArray("vertices");
                JsonArray faces = part.has("faces") ? part.getAsJsonArray("faces") : part.getAsJsonArray("triangles");
                List<Float> out = byBone.computeIfAbsent(bone, b -> new ArrayList<>());
                for (JsonElement f : faces) {
                    JsonArray face = f.getAsJsonArray();
                    for (int k = 1; k + 1 < face.size(); k++)   // a fan: (0,1,2), (0,2,3)
                        for (int corner : new int[]{face.get(0).getAsInt(), face.get(k).getAsInt(), face.get(k + 1).getAsInt()}) {
                            JsonArray vertex = vertices.get(corner).getAsJsonArray();
                            for (int axis = 0; axis < 3; axis++) out.add(vertex.get(axis).getAsFloat());
                        }
                }
            }
            Map<String, float[]> local = new LinkedHashMap<>();
            for (Map.Entry<String, List<Float>> bone : byBone.entrySet()) {
                float[] tri = new float[bone.getValue().size()];
                for (int i = 0; i < tri.length; i++) tri[i] = bone.getValue().get(i);
                local.put(bone.getKey(), tri);
            }
            return Optional.of(new Profile(horizontal, origin, scale, List.copyOf(channels), nodes, local));
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.error("[AFL MESH HIT] could not read block mesh profile {}", id, e);
            return Optional.empty();
        }
    }

    @Nullable
    private static JsonObject json(String path) throws java.io.IOException {
        try (InputStream in = AnimatedMeshHits.class.getResourceAsStream(path)) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** {translation, rotation (degrees), scale}, each defaulting as the profile loader's. */
    private static float[][] transform(@Nullable JsonElement value) {
        JsonObject t = value == null ? new JsonObject() : value.getAsJsonObject();
        return new float[][]{vector(t.get("translation"), 0.0F), vector(t.get("rotation"), 0.0F), vector(t.get("scale"), 1.0F)};
    }

    private static float[] vector(@Nullable JsonElement value, float fallback) {
        if (value == null) return new float[]{fallback, fallback, fallback};
        JsonArray a = value.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
