package com.antaurora.apofirstlight.worldgen.structure;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.*;

/**
 * V1 asset identity, not a placement policy or settlement recipe. Geometry comes from real NBT size.
 * Metadata id is its resource key, distinct from structureNbt. Mirror is always NONE.
 * originY = desiredGroundSurfaceY - groundAnchorOffsetY (surface is the first cell above ground).
 * Duplicate names and size-dependent socket/anchor rules are checked by the validator.
 */
public record StructureDefinition(ResourceLocation id, ResourceLocation structureNbt, ResourceLocation category,
        Direction front, int groundAnchorOffsetY, Set<Rotation> allowedRotations, List<StructureSocket> sockets,
        Set<ResourceLocation> tags, String assetRevision, int schemaVersion) {
    public StructureDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(structureNbt); Objects.requireNonNull(category);
        Objects.requireNonNull(front); Objects.requireNonNull(assetRevision);
        allowedRotations = Set.copyOf(allowedRotations);
        sockets = List.copyOf(sockets);
        tags = Set.copyOf(tags);
        if (schemaVersion != 1) throw new Invalid(Code.UNSUPPORTED_SCHEMA, "schema_version", "Only schema 1 is supported");
        if (assetRevision.isBlank()) throw new Invalid(Code.ASSET_REVISION_MISSING, "asset_revision", "Must not be blank");
        if (!front.getAxis().isHorizontal()) throw new Invalid(Code.INVALID_FRONT, "front", "Must be horizontal");
        if (allowedRotations.isEmpty()) throw new Invalid(Code.EMPTY_ROTATION_SET, "allowed_rotations", "Must not be empty");
    }
}
