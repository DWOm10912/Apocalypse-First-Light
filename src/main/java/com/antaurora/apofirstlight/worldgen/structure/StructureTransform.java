package com.antaurora.apofirstlight.worldgen.structure;

import java.util.Objects;
import com.antaurora.apofirstlight.worldgen.spatial.Bounds3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Same zero pivot and origin as default StructurePlaceSettings; never rotates about a building center. */
public final class StructureTransform {
    private StructureTransform() {}
    public static BlockPos local(BlockPos position, Rotation rotation) {
        Objects.requireNonNull(position); Objects.requireNonNull(rotation);
        // Vanilla int negation can wrap; reject the affected axes before calling its API.
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.CLOCKWISE_180) Math.negateExact(position.getZ());
        if (rotation == Rotation.COUNTERCLOCKWISE_90 || rotation == Rotation.CLOCKWISE_180) Math.negateExact(position.getX());
        return StructureTemplate.transform(position, Mirror.NONE, rotation, BlockPos.ZERO).immutable();
    }
    public static Direction facing(Direction facing, Rotation rotation) {
        Objects.requireNonNull(facing); Objects.requireNonNull(rotation);
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Horizontal facing required");
        return rotation.rotate(facing);
    }
    public static BlockPos world(BlockPos local, Rotation rotation, BlockPos origin) {
        Objects.requireNonNull(origin);
        BlockPos p = local(local, rotation);
        return new BlockPos(Math.addExact(origin.getX(), p.getX()), Math.addExact(origin.getY(), p.getY()),
                Math.addExact(origin.getZ(), p.getZ()));
    }
    public static int originY(int desiredSurfaceY, int groundAnchorOffsetY) {
        return Math.subtractExact(desiredSurfaceY, groundAnchorOffsetY);
    }
    public static Bounds3i bounds(Vec3i size, Rotation rotation, BlockPos origin) {
        Objects.requireNonNull(size); Objects.requireNonNull(rotation); Objects.requireNonNull(origin);
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) throw new IllegalArgumentException("Positive template size required");
        // Preflight world-coordinate arithmetic before Vanilla's unchecked integer translation.
        for (int x : new int[]{0, size.getX()-1}) for (int y : new int[]{0, size.getY()-1})
            for (int z : new int[]{0, size.getZ()-1}) world(new BlockPos(x, y, z), rotation, origin);
        return VanillaBoundsAdapter.fromVanilla(TemplateBounds.read(size, rotation, origin));
    }
    /** Narrow bridge to the exact protected size-based API used by StructureTemplate.getBoundingBox. */
    private static final class TemplateBounds extends StructureTemplate {
        private static BoundingBox read(Vec3i size, Rotation rotation, BlockPos origin) {
            return getBoundingBox(origin, rotation, BlockPos.ZERO, Mirror.NONE, size);
        }
    }
}
