package com.antaurora.apofirstlight.worldgen.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.*;

/** Mechanical asset checks only; never loads a world or repairs metadata/NBT. */
public final class StructureDefinitionValidator {
    private StructureDefinitionValidator() {}
    public static StructureValidationResult validate(StructureDefinition definition, StructureNbtReader.Inspection nbt) {
        Objects.requireNonNull(definition); Objects.requireNonNull(nbt);
        var issues = new ArrayList<>(nbt.validation().issues());
        var names = new HashSet<String>();
        for (var socket : definition.sockets()) if (!names.add(socket.name()))
            issues.add(error(Code.DUPLICATE_SOCKET_NAME,"sockets."+socket.name(),"Socket name must be unique"));
        if (nbt.info().isEmpty()) return new StructureValidationResult(issues);
        var info=nbt.info().orElseThrow();
        var bounds=info.localBounds();
        if (definition.groundAnchorOffsetY()<0 || definition.groundAnchorOffsetY()>bounds.height())
            issues.add(error(Code.INVALID_GROUND_ANCHOR,"ground_anchor_offset_y","Expected 0..sizeY inclusive"));
        for (var socket : definition.sockets()) {
            BlockPos p=socket.localPosition();
            String path="sockets."+socket.name();
            if (!bounds.contains(p.getX(),p.getY(),p.getZ())) {
                issues.add(error(Code.SOCKET_OUT_OF_BOUNDS,path,"Position must be inside full NBT size"));
                continue;
            }
            boolean n=p.getZ()==0, s=p.getZ()==bounds.maxZExclusive()-1;
            boolean w=p.getX()==0, e=p.getX()==bounds.maxXExclusive()-1;
            if (!(n||s||w||e)) issues.add(error(Code.SOCKET_NOT_ON_BOUNDARY,path,"External socket must be on X/Z edge"));
            else if (!((n&&socket.facing()==Direction.NORTH)||(s&&socket.facing()==Direction.SOUTH)
                    ||(w&&socket.facing()==Direction.WEST)||(e&&socket.facing()==Direction.EAST)))
                issues.add(error(Code.SOCKET_FACING_INWARD,path,"Facing must point through its outside edge"));
            if (info.nonAir().contains(p)) issues.add(error(Code.INVALID_FIELD,path,"Entrance air cell is occupied in at least one palette"));
        }
        for (var rotation : definition.allowedRotations()) {
            try {
                var rotated=StructureTransform.bounds(info.size(),rotation,BlockPos.ZERO);
                for (var socket : definition.sockets()) {
                    var p=StructureTransform.local(socket.localPosition(),rotation);
                    if (bounds.contains(socket.localPosition().getX(),socket.localPosition().getY(),socket.localPosition().getZ())
                            && !rotated.contains(p.getX(),p.getY(),p.getZ()))
                        issues.add(error(Code.ROTATION_TRANSFORM_MISMATCH,"rotation."+rotation,"Socket left rotated template bounds"));
                }
            } catch (ArithmeticException | IllegalArgumentException invalid) {
                issues.add(error(Code.ROTATION_TRANSFORM_MISMATCH,"rotation."+rotation,invalid.toString()));
            }
        }
        return new StructureValidationResult(issues);
    }
}
