package com.antaurora.apofirstlight.blockmesh;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable client resource data, with no client-only types (also safe for BE bounds queries). */
public final class AflBlockMeshProfile {
    public record Transform(Vec3 translation, Vec3 rotation, Vec3 scale) {
        public static final Transform IDENTITY = new Transform(Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1));
    }
    public enum Easing {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT;
        public double apply(double t) {
            return switch (this) {
                case LINEAR -> t;
                case EASE_IN -> t * t;
                case EASE_OUT -> 1 - (1 - t) * (1 - t);
                case EASE_IN_OUT -> t * t * (3 - 2 * t);
            };
        }
    }
    public record Animation(double durationTicks, Easing easing) {}
    public record Motion(String channel, Transform target) {}
    /** Absolute model-space pivot; mesh corners are already relative to this pivot. */
    public record Part(String bone, Vec3 pivot, Transform rest, Motion motion, List<Part> children) {
        public Part { children = List.copyOf(children); }
    }

    private final ResourceLocation geometry, texture;
    private final Vec3 origin, scale;
    private final boolean horizontalFacing;
    private final List<Part> roots;
    private final Map<String, Animation> animations;
    private final Map<Direction, AABB> bounds;

    public AflBlockMeshProfile(ResourceLocation geometry, ResourceLocation texture, Vec3 origin, Vec3 scale,
                               boolean horizontalFacing, List<Part> roots, Map<String, Animation> animations,
                               AABB northBounds) {
        this.geometry = geometry;
        this.texture = texture;
        this.origin = origin;
        this.scale = scale;
        this.horizontalFacing = horizontalFacing;
        this.roots = List.copyOf(roots);
        this.animations = Map.copyOf(animations);
        var rotated = new EnumMap<Direction, AABB>(Direction.class);
        AABB box = northBounds;
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            rotated.put(direction, horizontalFacing ? box : northBounds);
            box = new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
        }
        bounds = Map.copyOf(rotated);
    }

    public ResourceLocation geometry() { return geometry; }
    public ResourceLocation texture() { return texture; }
    public Vec3 origin() { return origin; }
    public Vec3 scale() { return scale; }
    public boolean horizontalFacing() { return horizontalFacing; }
    public List<Part> roots() { return roots; }
    public Map<String, Animation> animations() { return animations; }
    public AABB bounds(Direction facing) { return bounds.getOrDefault(facing, bounds.get(Direction.NORTH)); }
    public static float facingDegrees(Direction facing) {
        return switch (facing) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; };
    }
}
