package com.antaurora.apofirstlight.client.mesh;

import com.google.gson.*;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Strict V1/V2 decoder/baker; no Minecraft state, no topology work at render time. */
public final class AflMeshLoader {
    public static final int MAX_CHARACTERS = 4 * 1024 * 1024;
    public static final int MAX_PARTS = 128;
    public static final int MAX_TRIANGLES = 16384;
    private static final int MAX_VERTICES = 65536;

    public static AflMeshModel load(String resource, Reader sidecar, Reader geometry) throws IOException {
        try {
            return bake(read(sidecar), read(geometry));
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException e) {
            throw new IllegalArgumentException(resource + ": " + e.getMessage(), e);
        } catch (StackOverflowError e) {
            throw new IllegalArgumentException(resource + ": excessive JSON nesting", e);
        }
    }

    private static JsonObject read(Reader reader) throws IOException {
        var text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            require(text.length() + count <= MAX_CHARACTERS, "JSON exceeds 4 MiB character limit");
            text.append(buffer, 0, count);
        }
        return object(JsonParser.parseString(text.toString()), "root");
    }

    private static AflMeshModel bake(JsonObject root, JsonObject geometry) {
        int version = root.has("format_version") ? integer(root.get("format_version"), "format_version") : 1;
        require(version == 1 || version == 2, "unsupported format_version");
        keys(root, root.has("format_version")
                ? Set.of("format_version", "coordinate_space", "uv_origin", "winding", "texture_size", "parts")
                : Set.of("coordinate_space", "uv_origin", "winding", "texture_size", "parts"), "root");
        require(string(root.get("coordinate_space"), "coordinate_space").equals("bone_pivot_local_blocks"), "unsupported coordinate_space");
        require(string(root.get("uv_origin"), "uv_origin").equals("top_left"), "unsupported uv_origin");
        require(string(root.get("winding"), "winding").equals("ccw"), "unsupported winding");
        var texture = array(root.get("texture_size"), 2, "texture_size");
        int width = integer(texture.get(0), "texture width"), height = integer(texture.get(1), "texture height");
        require(width > 0 && height > 0 && width <= 4096 && height <= 4096, "invalid texture_size");

        var geometries = array(geometry.get("minecraft:geometry"), 1, "minecraft:geometry");
        var geo = object(geometries.get(0), "geometry");
        var description = object(geo.get("description"), "geometry.description");
        require(integer(description.get("texture_width"), "geometry.texture_width") == width
                && integer(description.get("texture_height"), "geometry.texture_height") == height, "sidecar/geometry texture_size mismatch");
        var boneNames = new HashSet<String>();
        for (var entry : array(geo.get("bones"), -1, "geometry.bones")) {
            String name = string(object(entry, "geometry bone").get("name"), "geometry bone name");
            require(boneNames.add(name), "duplicate geometry bone " + name);
        }
        var parts = array(root.get("parts"), -1, "parts");
        require(!parts.isEmpty() && parts.size() <= MAX_PARTS, "parts count must be 1.." + MAX_PARTS);
        var result = new LinkedHashMap<String, List<AflMeshPart>>();
        var names = new HashSet<String>();
        int totalTriangles = 0, totalVertices = 0;
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            String context = "part[" + partIndex + "]";
            try {
                var part = object(parts.get(partIndex), context);
                String name = string(part.get("name"), "name"), bone = string(part.get("bone"), "bone");
                context = "bone=" + bone + " part=" + name;
                String faceKey = version == 1 ? "triangles" : "faces";
                keys(part, Set.of("name", "bone", "vertices", faceKey), context);
                require(names.add(name), "duplicate part name");
                require(boneNames.contains(bone), "unknown parent bone");
                var vertices = array(part.get("vertices"), -1, "vertices");
                totalVertices += vertices.size();
                require(vertices.size() >= 3 && totalVertices <= MAX_VERTICES, "invalid/excessive vertices count");
                float[][] data = new float[vertices.size()][5];
                for (int i = 0; i < data.length; i++) {
                    var vertex = array(vertices.get(i), 5, "vertex[" + i + "] x,y,z,u,v");
                    for (int j = 0; j < 5; j++) {
                        double value = number(vertex.get(j), "vertex[" + i + "][" + j + "]");
                        require(j < 3 ? Math.abs(value) <= 256 : value >= 0 && value <= 1, "position or UV out of V1 range");
                        data[i][j] = (float)value;
                    }
                }
                var faces = array(part.get(faceKey), -1, faceKey);
                require(!faces.isEmpty() && faces.size() <= MAX_TRIANGLES, "invalid/excessive face count");
                int[] offsets = new int[faces.size() + 1];
                for (int f = 0; f < faces.size(); f++) {
                    int size = array(faces.get(f), -1, "face[" + f + "]").size();
                    require(size == 3 || version == 2 && size == 4, "face[" + f + "] requires 3/4 corners (V1: 3)");
                    totalTriangles += size - 2;
                    offsets[f + 1] = offsets[f] + size;
                }
                require(totalTriangles <= MAX_TRIANGLES, "excessive triangle-equivalent count");
                float[] baked = new float[offsets[faces.size()] * AflMeshPart.STRIDE];
                double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
                double maxX = -minX, maxY = -minX, maxZ = -minX;
                int at = 0;
                for (int t = 0; t < faces.size(); t++) {
                    var indices = faces.get(t).getAsJsonArray();
                    int[] ids = new int[indices.size()];
                    for (int j = 0; j < ids.length; j++) {
                        ids[j] = integer(indices.get(j), "face[" + t + "] index");
                        require(ids[j] >= 0 && ids[j] < data.length, "face[" + t + "] invalid index");
                    }
                    float[] a = data[ids[0]], b = data[ids[1]], c = data[ids[2]];
                    double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
                    double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
                    double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    require(Double.isFinite(length) && length > 1e-10, "triangle[" + t + "] zero-area/degenerate triangle");
                    if (ids.length == 4) validateQuad(data, ids, nx / length, ny / length, nz / length, t);
                    for (int id : ids) {
                        var v = data[id];
                        for (float value : v) baked[at++] = value;
                        baked[at++] = (float)(nx / length); baked[at++] = (float)(ny / length); baked[at++] = (float)(nz / length);
                        minX = Math.min(minX, v[0]); minY = Math.min(minY, v[1]); minZ = Math.min(minZ, v[2]);
                        maxX = Math.max(maxX, v[0]); maxY = Math.max(maxY, v[1]); maxZ = Math.max(maxZ, v[2]);
                    }
                }
                result.computeIfAbsent(bone, ignored -> new ArrayList<>()).add(new AflMeshPart(name, baked, offsets,
                        new AflMeshPart.Bounds(minX, minY, minZ, maxX, maxY, maxZ)));
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException(context + ": " + e.getMessage(), e);
            }
        }
        return new AflMeshModel(version, result);
    }

    private static void validateQuad(float[][] data, int[] ids, double nx, double ny, double nz, int face) {
        // Exporter owns authoring topology. This is a corruption check with a
        // decimal (1e-10) + float-ULP error budget, not a second authoring classifier.
        double error = 0, extent = 0;
        for (int id : ids) for (int axis = 0; axis < 3; axis++)
            error = Math.max(error, Math.ulp(data[id][axis]) * 0.5 + 5e-11);
        for (int a : ids) for (int b : ids) {
            double x = (double)data[a][0] - data[b][0], y = (double)data[a][1] - data[b][1], z = (double)data[a][2] - data[b][2];
            extent = Math.max(extent, Math.sqrt(x*x+y*y+z*z));
        }
        double edgeError = 2 * Math.sqrt(3) * error;
        double[] errors = new double[4], deviations = new double[4];
        for (int i = 0; i < 4; i++) {
            float[] a = data[ids[i]], b = data[ids[(i + 1) % 4]], c = data[ids[(i + 2) % 4]];
            double ux = (double)b[0] - a[0], uy = (double)b[1] - a[1], uz = (double)b[2] - a[2];
            double vx = (double)c[0] - a[0], vy = (double)c[1] - a[1], vz = (double)c[2] - a[2];
            double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            double length = Math.sqrt(x*x + y*y + z*z);
            require(Double.isFinite(length) && length > 1e-10, "face[" + face + "] degenerate quad");
            double dx = x/length-nx, dy = y/length-ny, dz = z/length-nz;
            require((x*nx+y*ny+z*nz)>0, "face[" + face + "] quad must be convex and planar");
            errors[i] = 2 * (edgeError * (Math.sqrt(ux*ux+uy*uy+uz*uz)
                    + Math.sqrt(vx*vx+vy*vy+vz*vz)) + edgeError*edgeError) / length;
            deviations[i] = Math.sqrt(dx*dx+dy*dy+dz*dz);
        }
        for (int i=0;i<4;i++) require(deviations[i] <= Math.min(0.00202, 2e-5+errors[0]+errors[i]),
                "face[" + face + "] quad must be convex and planar");
        float[] a=data[ids[0]], d=data[ids[3]];
        double distance=Math.abs(((double)d[0]-a[0])*nx+((double)d[1]-a[1])*ny+((double)d[2]-a[2])*nz);
        require(distance <= extent*(2e-5+errors[0])+edgeError,
                "face[" + face + "] quad must be convex and planar");
    }

    private static void keys(JsonObject object, Set<String> allowed, String where) {
        require(object.keySet().equals(allowed), where + " requires exactly " + allowed + "; got " + object.keySet());
    }
    private static JsonObject object(JsonElement value, String where) {
        require(value != null && value.isJsonObject(), where + " must be an object"); return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value, int length, String where) {
        require(value != null && value.isJsonArray(), where + " must be an array");
        var result = value.getAsJsonArray();
        require(length < 0 || result.size() == length, where + " must have " + length + " entries"); return result;
    }
    private static String string(JsonElement value, String where) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), where + " must be a string");
        String text = value.getAsString(); require(!text.isBlank() && text.length() <= 128, where + " has invalid length"); return text;
    }
    private static double number(JsonElement value, String where) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), where + " must be numeric");
        double result = value.getAsDouble(); require(Double.isFinite(result), where + " must be finite"); return result;
    }
    private static int integer(JsonElement value, String where) {
        double result = number(value, where); require(result == Math.rint(result) && Math.abs(result) <= Integer.MAX_VALUE, where + " must be integer"); return (int)result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private AflMeshLoader() {}
}
