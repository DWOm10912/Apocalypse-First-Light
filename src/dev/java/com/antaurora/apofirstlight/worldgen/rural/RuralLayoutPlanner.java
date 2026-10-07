package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Asset-independent lot orientation helpers; the retired building candidate recipe is removed. */
public final class RuralLayoutPlanner {
    private RuralLayoutPlanner() {
    }

    public static Rotation rotationFor(Direction templateFront, Direction roadFacing) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(templateFront) == roadFacing) return rotation;
        }
        throw new IllegalArgumentException("No horizontal rotation from " + templateFront + " to " + roadFacing);
    }

    public static boolean facesRoad(RuralStructurePool.Definition definition, Rotation rotation,
                                    Direction roadFacing) {
        return rotation.rotate(definition.frontDirection()) == roadFacing;
    }

    static BoundingBox boundsAt(StructureTemplate template, Rotation rotation, BlockPos anchor) {
        return template.getBoundingBox(new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation),
                new BlockPos(anchor.getX(), 0, anchor.getZ()));
    }
}
