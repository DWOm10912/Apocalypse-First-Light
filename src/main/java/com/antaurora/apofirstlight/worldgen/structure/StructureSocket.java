package com.antaurora.apofirstlight.worldgen.structure;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.*;

/** First entrance air cell INSIDE the template edge, facing outward. Geometry checked against NBT. */
public record StructureSocket(String name, BlockPos localPosition, Direction facing, StructureSocketType type) {
    public StructureSocket {
        Objects.requireNonNull(name); Objects.requireNonNull(localPosition);
        Objects.requireNonNull(facing); Objects.requireNonNull(type);
        if (name.isBlank()) throw new Invalid(Code.INVALID_FIELD, "socket.name", "Must not be blank");
        if (!facing.getAxis().isHorizontal()) throw new Invalid(Code.INVALID_FRONT, "socket.facing", "Must be horizontal");
        localPosition = localPosition.immutable();
    }
}
