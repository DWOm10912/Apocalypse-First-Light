package com.antaurora.apofirstlight.meshhit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ray stopped on a block's hit mesh (meshhit/MeshHitClip): the point on the model's surface and its true normal (unit,
 * facing back toward the ray). {@link #getDirection()} is the nearest axis of that normal, for code that only knows faces.
 */
public final class MeshBlockHitResult extends BlockHitResult {
    private final Vec3 normal;

    public MeshBlockHitResult(Vec3 location, Vec3 normal, BlockPos pos) {
        super(location, Direction.getNearest(normal.x, normal.y, normal.z), pos, false);
        this.normal = normal;
    }

    public Vec3 normal() {
        return normal;
    }

    /** The surface normal of any block hit: the mesh's own, else its face's. */
    public static Vec3 normalOf(BlockHitResult hit) {
        return hit instanceof MeshBlockHitResult mesh ? mesh.normal : Vec3.atLowerCornerOf(hit.getDirection().getNormal());
    }
}
